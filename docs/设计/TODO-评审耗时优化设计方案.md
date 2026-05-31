# AI PR Review 评审耗时优化设计方案

## 一、背景说明

当前 AI PR Review 项目的核心链路如下：

```text
用户选择或输入 GitHub PR
→ 创建 Review 任务
→ 获取 PR 信息
→ 获取 Diff 文件
→ 过滤与预处理 Diff
→ 调用 AI 模型进行文件级 Review
→ 聚合 Review 结果
→ 计算风险分
→ 生成最终报告
→ 前端展示报告详情
```

在这个链路中，耗时主要来自：

```text
1. GitHub API 网络请求；
2. PR Diff 文件数量较多；
3. 单个 patch 内容过大；
4. AI 模型调用耗时不可控；
5. 同一个 PR 被重复分析；
6. 前端等待任务完成后才进入报告页；
7. 文件级分析串行执行；
8. 某个文件失败或超时拖慢整个任务。
```

因此，评审耗时优化不是单点优化，而是一个工程化组合方案。

------

## 二、这些技术手段是否都要使用

结论：

```text
不需要全部一次性使用。
```

原因：

```text
1. 当前项目仍以比赛演示和主链路稳定为第一目标。
2. 过早引入消息队列、WebSocket、复杂熔断会增加实现成本。
3. 部分优化属于用户体验优化，部分优化属于真实性能优化。
4. 应优先做收益最大、改动最小、风险最低的方案。
```

推荐采用分层落地策略：

```text
P0：必须优先做，直接改善演示体验和主链路稳定性。
P1：建议做，改善真实耗时和任务可恢复性。
P2：后续扩展，面向多用户、多实例和生产级场景。
```

当前最推荐优先使用：

```text
1. 创建任务立即返回 taskId；
2. 报告详情页轮询任务状态；
3. 数据库缓存复用历史报告；
4. Diff 过滤、跳过和截断；
5. 单文件超时后允许部分结果完成。
```

暂时不必优先使用：

```text
1. 完整消息队列；
2. WebSocket；
3. 多模型分层调度；
4. 复杂熔断框架；
5. Agent 自主规划；
6. Prometheus / Grafana 生产级监控。
```

------

## 三、优化目标

评审耗时优化的目标分为四类：

```text
1. 体验目标：
   用户点击开始评审后立即看到任务页面和进度，不感觉页面卡住。

2. 性能目标：
   减少不必要的 AI 调用，缩短单次 Review 的真实执行时间。

3. 稳定目标：
   单个文件超时或失败不拖垮整个任务。

4. 成本目标：
   避免重复分析同一 PR，减少 Token 浪费。
```

衡量指标：

| 指标 | 说明 |
| --- | --- |
| task_create_latency_ms | 创建任务接口耗时 |
| pr_fetch_latency_ms | GitHub PR 信息和 Diff 获取耗时 |
| ai_review_latency_ms | AI 文件级分析耗时 |
| total_review_latency_ms | 从任务创建到完成的总耗时 |
| cache_hit_rate | Review 缓存命中率 |
| skipped_file_count | 被跳过文件数 |
| timeout_file_count | AI 调用超时文件数 |
| failed_file_count | 文件级分析失败数 |

------

## 四、整体优化思路

总体思路：

```text
前端不等待
后端异步跑
重复任务走缓存
大 Diff 先裁剪
文件分析可并发
失败文件不中断
过程状态可观察
```

优化后的链路：

```text
POST /api/review-tasks
→ 解析 PR URL
→ 获取 PR headSha
→ 检查数据库缓存
→ 命中缓存：立即返回历史 taskId
→ 未命中：创建新任务并立即返回 taskId
→ 后台异步执行 ReviewPipeline

前端：
→ 立即进入 /report/{taskId}
→ 轮询 GET /api/review-tasks/{taskId}
→ 展示阶段进度
→ SUCCESS 后加载报告
→ FAILED 或 PARTIAL_SUCCESS 时展示失败原因和部分结果
```

------

## 五、P0 必须实现

### 5.1 创建任务立即返回

当前不应该让 `POST /api/review-tasks` 等待完整 AI Review 完成。

接口行为：

```http
POST /api/review-tasks
```

请求：

```json
{
  "prUrl": "https://github.com/owner/repo/pull/12",
  "forceRefresh": false
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 12,
    "status": "PENDING",
    "cached": false,
    "cachedFromTaskId": null
  }
}
```

前端处理：

```text
1. 收到 taskId 后立即进入报告详情页。
2. 报告页展示任务进度。
3. 不在按钮 loading 状态里等待完整 Review 完成。
```

收益：

```text
用户马上看到反馈，评审慢不会表现为页面卡住。
```

### 5.2 报告详情页轮询任务状态

前端报告页每隔 2 秒查询：

```http
GET /api/review-tasks/{taskId}
```

状态流转：

```text
PENDING
FETCHING_PR
PARSING_DIFF
REVIEWING
SUMMARIZING
SCORING
SUCCESS
FAILED
```

页面展示：

```text
创建任务
获取 PR 信息
解析 Diff
执行 AI Review
生成报告
计算风险评分
完成
```

轮询停止条件：

```text
SUCCESS
FAILED
CANCELLED
PARTIAL_SUCCESS
```

说明：

```text
当前项目可以先使用轮询，不必一开始使用 SSE 或 WebSocket。
```

### 5.3 数据库缓存复用

缓存 key：

```text
owner_name
repo_name
pr_number
head_sha
model_name
prompt_version
status = SUCCESS
```

命中缓存时：

```text
1. 不重新调用 GitHub Diff；
2. 不重新调用 AI；
3. 直接返回历史成功 taskId；
4. 响应 cached=true；
5. 前端提示"已命中历史报告"。
```

示例：

```json
{
  "taskId": 12,
  "status": "SUCCESS",
  "cached": true,
  "cachedFromTaskId": 12
}
```

收益：

```text
同一个 PR 多次演示或多人重复查看时，耗时从几十秒降到毫秒级。
```

### 5.4 Diff 过滤和跳过

不适合进入 AI Review 的文件应该提前跳过。

推荐跳过：

```text
node_modules/**
dist/**
build/**
target/**
.git/**
package-lock.json
yarn.lock
pnpm-lock.yaml
*.min.js
*.map
*.class
*.jar
*.png
*.jpg
*.jpeg
*.gif
*.svg
*.ico
*.pdf
```

推荐跳过原因：

```text
GENERATED_FILE
LOCK_FILE
BINARY_FILE
PATCH_EMPTY
PATCH_TOO_LARGE
UNSUPPORTED_FILE_TYPE
```

数据记录：

```text
review_file.skipped = true
review_file.skip_reason = 跳过原因
```

收益：

```text
减少无价值 AI 调用，避免大文件拖慢任务。
```

### 5.5 Patch 截断

单个文件 patch 过大时，不应完整送入模型。

建议阈值：

```text
单文件 patch 最大字符数：12000
单个任务总 patch 最大字符数：60000
单个任务最多分析文件数：20
```

处理策略：

```text
1. 超过阈值的 patch 截断；
2. 截断后在 Prompt 中明确说明；
3. 文件记录 skipReason 或 truncated 标记；
4. 报告中提示“部分内容已截断”。
```

当前如果不想新增字段，可以先复用：

```text
review_file.skip_reason
review_file.ai_summary
```

后续可新增：

```text
review_file.truncated
review_file.original_patch_length
review_file.analyzed_patch_length
```

------

## 六、P1 建议实现

### 6.1 文件级并发评审

当前如果逐个文件串行调用 AI：

```text
file1 → AI
file2 → AI
file3 → AI
```

耗时会接近所有文件耗时总和。

建议改成有限并发：

```text
file1 ┐
file2 ├─ 并发执行，最多 3 到 5 个
file3 ┘
```

Java 实现方式：

```text
ThreadPoolTaskExecutor
CompletableFuture
ExecutorService
```

推荐配置：

```yaml
review:
  ai:
    file-review-concurrency: 3
    file-review-timeout-seconds: 60
```

注意：

```text
1. 不要无限并发，避免触发模型 API 限流。
2. 并发数建议从 3 开始。
3. forceRefresh 不应该绕过并发保护。
```

### 6.2 单文件超时控制

每个文件 AI 调用应设置超时。

建议：

```text
单文件 AI 超时：60 秒
PR 总任务软超时：5 分钟
PR 总任务硬超时：10 分钟
```

单文件超时后：

```text
1. 记录该文件失败；
2. 继续处理其他文件；
3. 最终报告提示部分文件未完成分析；
4. 不直接让整个任务失败。
```

### 6.3 部分结果可用

新增任务状态：

```text
PARTIAL_SUCCESS
```

适用场景：

```text
1. 大部分文件分析成功；
2. 少数文件超时；
3. 少数文件 AI 返回格式解析失败；
4. GitHub 某些文件 patch 缺失。
```

前端展示：

```text
报告已生成，但存在部分文件未完成分析。
```

说明：

```text
比赛阶段如果不想新增状态，也可以先使用 SUCCESS，并在 finalReview 或 summary 中提示部分文件跳过。
```

### 6.4 AI 调用失败重试

建议只做轻量重试：

```text
单文件调用失败后最多重试 1 次。
只对网络超时、502、503、504 重试。
AI 返回格式错误不盲目重试太多。
```

重试间隔：

```text
1 秒到 3 秒
```

不建议：

```text
无限重试
用户无感知长时间重试
所有错误都重试
```

### 6.5 Map-Reduce Review 结构

推荐把 Review 拆成两阶段：

```text
Map：每个文件独立生成 summary + comments
Reduce：汇总文件结果，生成 PR 总结、测试建议和最终结论
```

优势：

```text
1. 文件级 Map 可以并发；
2. 单文件失败不会影响其他文件；
3. 文件结果可以缓存；
4. Reduce 阶段 Prompt 更短；
5. 更适合后续 Skill / Agent 扩展。
```

------

## 七、P2 后续扩展

### 7.1 SSE 进度推送

轮询足够支撑当前版本。后续可以用 SSE 替代轮询。

接口：

```http
GET /api/review-tasks/{taskId}/events
```

事件：

```text
TASK_CREATED
STATUS_CHANGED
FILE_REVIEW_STARTED
FILE_REVIEW_FINISHED
FILE_REVIEW_FAILED
TASK_FINISHED
```

为什么优先 SSE 而不是 WebSocket：

```text
1. 任务进度是服务端单向推送；
2. 实现比 WebSocket 简单；
3. 更适合当前场景；
4. 前端接入成本低。
```

### 7.2 消息队列

当任务量变大后，可以引入队列：

```text
Redis Stream
RabbitMQ
Kafka
```

当前阶段不建议优先引入完整 MQ。

适合引入 MQ 的条件：

```text
1. 多用户同时创建任务；
2. 需要削峰填谷；
3. 需要任务失败后可靠重试；
4. 后端多实例部署；
5. AI 调用成本需要严格控制。
```

### 7.3 多模型分层策略

后续可以采用：

```text
规则扫描：快速识别敏感路径和明显风险；
轻量模型：文件摘要、低风险建议；
强模型：高风险文件、安全相关文件、最终总结。
```

当前阶段不建议过早做多模型调度，原因是：

```text
1. 配置复杂；
2. 成本不易估算；
3. 输出一致性更难保证；
4. 答辩演示不一定体现收益。
```

### 7.4 熔断保护

当 AI 服务连续失败时：

```text
1. 暂停新的 AI 调用；
2. 快速返回失败提示；
3. 避免大量任务堆积；
4. 前端提示 AI 服务不可用。
```

可选技术：

```text
Resilience4j
Sentinel
自定义计数器
Redis 全局失败计数
```

当前阶段可以先用简单错误计数和日志，不必引入完整框架。

------

## 八、后端模块设计

### 8.1 ReviewTaskService

职责：

```text
1. 校验 PR URL；
2. 解析 owner / repo / prNumber；
3. 获取 PR 基本信息和 headSha；
4. 查询缓存；
5. 创建 review_task；
6. 提交异步执行；
7. 立即返回 taskId。
```

### 8.2 ReviewTaskExecutor

职责：

```text
1. 后台执行任务；
2. 捕获异常；
3. 更新任务状态；
4. 控制任务级超时；
5. 写入失败原因。
```

### 8.3 ReviewPipeline

职责：

```text
1. FETCHING_PR：获取 PR 信息；
2. PARSING_DIFF：获取并预处理 Diff；
3. REVIEWING：执行文件级 AI Review；
4. SUMMARIZING：生成 PR 总结；
5. SCORING：计算风险分；
6. SUCCESS / FAILED：结束任务。
```

### 8.4 DiffPreprocessor

职责：

```text
1. 根据路径跳过文件；
2. 根据后缀跳过二进制和生成文件；
3. 处理空 patch；
4. 截断超大 patch；
5. 计算实际送入模型的 patch 长度。
```

### 8.5 FileReviewDispatcher

职责：

```text
1. 按文件分发 AI Review；
2. 控制并发；
3. 控制单文件超时；
4. 收集成功和失败结果；
5. 输出可合并的文件级结果。
```

### 8.6 ReviewReportCacheService

职责：

```text
1. 根据缓存 key 查询历史成功任务；
2. 判断是否可以复用；
3. 返回 cachedFromTaskId；
4. 记录缓存命中信息。
```

------

## 九、数据库设计补充

当前已有字段：

```text
review_task.head_sha
review_task.base_sha
review_task.model_name
review_task.prompt_version
review_task.cached_from_task_id
review_file.skipped
review_file.skip_reason
```

P0 可继续复用这些字段，不必新增表。

P1 建议补充：

```sql
ALTER TABLE review_file
ADD COLUMN truncated TINYINT NOT NULL DEFAULT 0 COMMENT '是否截断：0否，1是',
ADD COLUMN original_patch_length INT DEFAULT 0 COMMENT '原始patch长度',
ADD COLUMN analyzed_patch_length INT DEFAULT 0 COMMENT '实际分析patch长度',
ADD COLUMN review_status VARCHAR(30) DEFAULT 'PENDING' COMMENT '文件级评审状态',
ADD COLUMN error_message TEXT DEFAULT NULL COMMENT '文件级评审失败原因';
```

P1 / P2 可选新增任务耗时字段：

```sql
ALTER TABLE review_task
ADD COLUMN started_at DATETIME DEFAULT NULL COMMENT '任务开始时间',
ADD COLUMN finished_at DATETIME DEFAULT NULL COMMENT '任务结束时间',
ADD COLUMN total_latency_ms INT DEFAULT NULL COMMENT '任务总耗时',
ADD COLUMN analyzed_file_count INT DEFAULT 0 COMMENT '实际分析文件数',
ADD COLUMN skipped_file_count INT DEFAULT 0 COMMENT '跳过文件数',
ADD COLUMN failed_file_count INT DEFAULT 0 COMMENT '失败文件数';
```

如果已经实现 `model_usage_log`，可记录：

```text
latency_ms
success
error_message
call_type
task_id
file_id
model_name
```

------

## 十、接口设计

### 10.1 创建任务

```http
POST /api/review-tasks
```

要求：

```text
必须快速返回。
不等待完整 AI Review。
命中缓存时直接返回历史 taskId。
```

### 10.2 查询任务状态

```http
GET /api/review-tasks/{taskId}
```

建议补充字段：

```json
{
  "taskId": 12,
  "status": "REVIEWING",
  "progressPercent": 60,
  "currentStep": "正在执行 AI Review",
  "analyzedFileCount": 6,
  "totalFileCount": 10,
  "skippedFileCount": 2,
  "failedFileCount": 0,
  "totalLatencyMs": 45000
}
```

如果暂时不想改 VO，可以前端先根据 `status` 推导固定进度。

### 10.3 查询文件视图

```http
GET /api/review-tasks/{taskId}/files
```

建议补充：

```json
{
  "fileId": 31,
  "filePath": "src/main/java/AuthService.java",
  "skipped": false,
  "skipReason": null,
  "truncated": false,
  "reviewStatus": "SUCCESS",
  "errorMessage": null
}
```

### 10.4 查询执行轨迹

后续可选：

```http
GET /api/review-tasks/{taskId}/trace
```

当前可由前端根据 `status` 推导，不一定立即落表。

------

## 十一、前端设计

### 11.1 创建任务后立即进入报告页

当前前端应避免：

```text
点击按钮
→ 一直 loading
→ 等任务完成
→ 再跳转报告页
```

推荐：

```text
点击按钮
→ 创建任务成功
→ 立即进入报告详情页
→ 报告页展示进度
→ 成功后自动加载报告
```

### 11.2 报告页状态展示

报告页展示：

```text
任务状态
当前阶段
已分析文件数
跳过文件数
失败文件数
耗时
缓存命中提示
```

### 11.3 任务中心展示耗时

任务中心新增列：

```text
任务耗时
分析文件数
跳过文件数
失败文件数
是否缓存命中
```

### 11.4 超时提示

如果任务长时间处于 `REVIEWING`：

```text
当前 PR 文件较多，AI Review 仍在执行。可以离开页面，稍后从任务中心查看结果。
```

------

## 十二、配置设计

建议配置：

```yaml
review:
  task:
    poll-timeout-minutes: 10
    total-soft-timeout-minutes: 5
    total-hard-timeout-minutes: 10
  diff:
    max-files: 20
    max-file-patch-chars: 12000
    max-total-patch-chars: 60000
    skip-path-patterns:
      - "node_modules/**"
      - "dist/**"
      - "build/**"
      - "target/**"
    skip-file-patterns:
      - "package-lock.json"
      - "yarn.lock"
      - "pnpm-lock.yaml"
  ai:
    file-review-concurrency: 3
    file-review-timeout-seconds: 60
    retry-times: 1
    retry-interval-ms: 1500
```

------

## 十三、与 Redis 限流的关系

评审耗时优化和 Redis 限流不是同一个问题。

```text
评审耗时优化：
让一次合法评审更快、更稳定、更有进度感。

Redis 限流：
防止用户或系统频繁触发高成本评审。
```

推荐顺序：

```text
1. 数据库缓存；
2. Diff 过滤；
3. 创建任务立即返回；
4. 前端进度展示；
5. Redis 限流；
6. 文件级并发；
7. SSE / MQ。
```

------

## 十四、与监控功能的关系

评审耗时优化需要监控数据支撑。

监控指标：

```text
任务总耗时
GitHub API 耗时
Diff 解析耗时
AI 调用耗时
单文件 AI 平均耗时
AI 调用成功率
缓存命中率
跳过文件数量
超时文件数量
```

这些指标可以进入：

```text
model_usage_log
review_task
review_file
后续 task_trace
```

前端监控页可以展示：

```text
平均评审耗时
最长评审耗时
缓存命中率
AI 平均响应耗时
超时次数
失败次数
```

------

## 十五、测试方案

### 15.1 缓存命中测试

步骤：

```text
1. 对同一个 PR 创建一次 Review；
2. 等待任务 SUCCESS；
3. 再次创建相同 PR Review；
4. 断言返回 cached=true；
5. 断言不会重新调用 AI。
```

### 15.2 Diff 跳过测试

准备文件：

```text
package-lock.json
dist/index.js
src/main/java/AuthService.java
```

断言：

```text
lock 文件被跳过；
dist 文件被跳过；
Java 文件进入分析。
```

### 15.3 Patch 截断测试

准备超长 patch。

断言：

```text
originalPatchLength > analyzedPatchLength；
truncated=true；
Prompt 中包含截断提示。
```

### 15.4 文件级超时测试

Mock 某个文件 AI 调用超时。

断言：

```text
该文件 reviewStatus=FAILED 或 skipped=true；
其他文件继续分析；
任务最终 SUCCESS 或 PARTIAL_SUCCESS；
报告中提示部分文件未完成。
```

### 15.5 前端体验测试

断言：

```text
创建任务成功后立即进入报告详情页；
报告详情页展示状态步骤；
任务 SUCCESS 后自动加载报告；
任务 FAILED 后展示失败原因；
长任务时提示可稍后从任务中心查看。
```

------

## 十六、落地优先级

### P0：必须实现

```text
1. 创建任务接口快速返回 taskId；
2. 前端报告详情页轮询状态；
3. 缓存命中直接复用历史报告；
4. Diff 跳过规则；
5. Patch 截断策略；
6. 文件跳过原因展示；
```

### P1：建议实现

```text
1. 文件级有限并发；
2. 单文件 AI 超时控制；
3. 失败文件不中断整体任务；
4. PARTIAL_SUCCESS 状态；
5. 任务耗时字段；
6. 文件级 reviewStatus；
```

### P2：后续扩展

```text
1. SSE 任务进度推送；
2. Redis / MQ 任务队列；
3. 多模型分层策略；
4. 熔断保护；
5. Agent Trace 级别耗时统计；
6. 成本与耗时趋势分析；
```

------

## 十七、当前项目推荐下一步

结合当前项目代码状态，下一步最推荐做：

```text
1. 前端 startReviewTask 不再等任务结束，创建成功立即进入报告详情页。
2. ReviewReportView 增加状态轮询，SUCCESS 后自动加载 report/files/comments。
3. 后端确认 createTask 已异步执行，并保证 cached=true 时立即返回。
4. ReviewFile 过滤规则再补一轮测试。
5. 任务详情 VO 补 progressPercent/currentStep/skippedFileCount。
```

这组改动性价比最高：

```text
不需要引入新中间件；
不破坏现有接口；
能明显改善用户体验；
也能给后续并发、监控、限流打基础。
```

------

## 十八、编码提示词

可以给后续编码工具使用：

```text
请基于当前 AI PR Review 项目实现评审耗时优化的 P0 任务。

要求：
1. POST /api/review-tasks 创建任务后必须快速返回 taskId，不等待完整 AI Review。
2. 前端创建任务成功后立即进入 ReviewReportView。
3. ReviewReportView 根据 taskId 轮询 GET /api/review-tasks/{taskId}。
4. 当任务状态为 SUCCESS 时自动加载 report/files/comments。
5. 当任务状态为 FAILED 时展示 errorMessage。
6. 保留 cached=true 的提示逻辑，缓存命中时直接加载历史报告。
7. 补充 Diff 跳过和 patch 截断策略，跳过原因写入 review_file.skip_reason。
8. 不引入消息队列、WebSocket、复杂熔断框架。
9. 不打印完整 patch、完整 AI 响应、token、apiKey。
10. 更新相关测试和文档。

优先保证现有测试通过，改动范围控制在 review task、diff 预处理、前端报告页和任务创建流程。
```

------

## 十九、总结

评审耗时优化不等于一次性堆满所有技术。

当前项目最合理的路线是：

```text
先解决“用户等得难受”
再解决“AI 调用真的慢”
最后解决“多用户高并发和生产级稳定性”
```

推荐路线：

```text
P0：异步体验 + 缓存复用 + Diff 过滤
P1：文件级并发 + 超时控制 + 部分结果
P2：SSE / MQ / 熔断 / 多模型调度
```

这样既符合当前项目阶段，也方便后续和 Redis 限流、V2 监控、Skill Pipeline、Agent Trace 等工程增强能力衔接。
