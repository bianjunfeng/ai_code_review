# 成员 B 阶段任务实现方案

## 1. 文档目的

本文档基于当前仓库代码、测试结果和分工文档，重新整理成员 B 后续任务实现方案。

当前项目是 **AI PR Review 助手**。成员 B 的主责仍是 **AI Review 与报告展示闭环**：

```text
后端拿到 PR diff
→ 构造 AI Review Prompt
→ 调用 OpenAI Compatible API
→ 清洗并解析模型 JSON
→ 保存结构化 Review 建议
→ 聚合 Review 报告
→ 前端展示报告、风险项、测试建议和最终结论
```

本次文档只做任务规划，不要求重构项目，也不引入 OAuth、Webhook、RAG、Agent、微服务等非 MVP 内容。

---

## 2. 当前代码基线

更新时间：2026-05-30

### 2.1 当前分支和工作区

| 项目 | 当前状态 |
| --- | --- |
| 当前分支 | `feature/ai-review-service` |
| 后端测试 | `mvn test` 已通过 |
| 后端测试结果 | `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0` |
| 前端构建 | `npm run build` 已通过 |
| 前端构建提示 | 有 chunk 体积警告，不影响 MVP 功能 |
| 当前未提交/未跟踪 | `ReviewTaskService.java` 有本地修改；`backend/src/test/resources/schema-test.sql`、`backend/src/test/.gitignore`、本文档未跟踪或未提交 |

注意：当前存在本地未提交改动，后续实现前必须先确认这些改动是否属于当前可测基线，避免覆盖他人修改。

### 2.2 后端已具备能力

| 模块 | 当前实现 | 证据文件 |
| --- | --- | --- |
| 统一返回与异常 | `Result<T>`、`BusinessException`、`GlobalExceptionHandler` 已实现 | `backend/src/main/java/com/example/aipr/common/*` |
| PR URL 解析 | 支持 GitHub PR URL 解析 | `service/github/PrUrlParser.java` |
| GitHub API 调用 | 已封装 PR detail 和 files API | `service/github/GitHubClient.java` |
| PR 预览 | `POST /api/github/preview` 返回 PR 信息和 files | `controller/GitHubController.java`、`GitHubPullRequestService.java` |
| Review 任务创建 | 创建 `review_task`，拉取 PR 信息和 changed files，保存 `review_file`，触发异步执行 | `ReviewTaskService.java` |
| 任务执行 | 异步调用 `AiReviewService`，保存 `review_file.ai_summary` 和 `review_comment` | `ReviewTaskExecutor.java` |
| 任务查询 | 已提供详情、文件列表、评论列表、报告接口 | `ReviewTaskController.java` |
| 报告聚合 | 已按数据库任务、文件、评论聚合 `ReviewReportVO` | `ReviewReportService.java` |
| AI Client | 已有 `LlmClient`、`OpenAiCompatibleClient`、`RetryInterceptor` | `service/ai/*` |
| Prompt 和 Parser | 已有 `PromptRenderer`、`AiReviewOutputParser` | `service/prompt/*` |
| 后端测试 | 当前有 30 个测试通过 | `AiPrReviewBackendApplicationTests`、`PromptRendererTest`、`AiReviewOutputParserTest` |

### 2.3 前端已具备能力

| 模块 | 当前实现 | 证据文件 |
| --- | --- | --- |
| PR 输入 | 首页可输入 PR URL，支持格式校验 | `frontend/src/views/HomeView.vue`、`PrInputCard.vue` |
| Mock 报告 | 保留 Mock 报告用于开发兜底，默认 `useMock=false` | `frontend/src/api/review.js` |
| 真实接口创建任务 | 已调用 `POST /api/review-tasks` | `frontend/src/api/review.js` |
| 查询报告 | 已调用 `GET /api/review-tasks/{taskId}/report` | `frontend/src/api/review.js` |
| 报告展示 | PR 信息、风险评分、总结、风险项、测试建议、最终结论组件已实现 | `frontend/src/components/*` |
| 前端构建 | `npm run build` 通过 | `frontend/package.json` |

### 2.4 当前仍存在的问题

| 优先级 | 问题 | 说明 |
| --- | --- | --- |
| P0 | 前端未接入任务状态轮询 | 当前创建任务后立即请求 report，不适配后端异步执行流程 |
| P0 | 真实端到端联调待确认 | 还需要用真实 MySQL、GitHub Token、AI API Key 跑完整链路 |
| P1 | `AiReviewService` 缺少单测 | 当前只测 Prompt 和 Parser，缺少 AI Review 服务串联测试 |
| P1 | AI 日志安全需要复查 | `AiReviewService` 当前仍存在 debug 打印 rawOutput 的风险，应改成长度/摘要日志 |
| P1 | 数据库初始化文件交付状态需确认 | `backend/src/main/resources/db/schema.sql` 存在，但需确认是否被 Git 忽略或未提交 |
| P1 | 文档与代码仍不完全一致 | `docs/03`、`docs/04`、`docs/05` 和 README 中仍有旧接口、旧字段或旧表名描述 |
| P2 | 大 PR 限制和 skipped 标记还不完整 | 文件数量、总 patch 长度、超限提示可后续增强 |
| P2 | 前端无详情页/历史页 | 当前只有首页展示，不支持刷新后按 taskId 恢复页面 |

---

## 3. 总体目标

当前项目已经从旧的：

```text
输入 PR URL
→ 创建内存任务
→ 返回 Mock 报告
→ 前端展示 Mock 报告
```

推进到当前的：

```text
输入 PR URL
→ 创建数据库 Review 任务
→ 获取 PR 信息和 changed files / patch
→ 异步调用 AiReviewService
→ 解析模型 JSON
→ 保存 review_file / review_comment
→ 聚合 ReviewReport
→ 前端展示报告
```

成员 B 后续目标不是重新实现后端主链路，而是补齐以下缺口：

```text
前端轮询任务状态
→ SUCCESS 后拉取真实报告
→ FAILED 时展示错误原因
→ 补齐 AiReviewService 测试和日志安全
→ 配合真实环境联调
→ 同步接口、Prompt、数据库和 README 文档
```

---

## 4. 执行原则

1. 不重构整个项目。
2. 不引入 OAuth、Webhook、RAG、Agent、微服务等非 MVP 内容。
3. 不修改统一接口返回格式。
4. 不硬编码 GitHub Token、AI API Key、数据库密码。
5. 不直接将数据库 Entity 返回给前端。
6. 前端不直接调用 AI API。
7. 优先保证 MVP 主链路跑通，再做样式、历史页和高级优化。
8. 每个阶段完成后至少运行对应测试命令，并记录结果。

---

## 5. Phase 0：确认并固化当前可测基线

### 5.1 目标

在继续开发前，先确认当前本地工作区的可测基线，并避免覆盖已有未提交改动。

### 5.2 当前状态

当前已验证：

```text
cd backend
mvn test

结果：Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
```

```text
cd frontend
npm run build

结果：构建成功，有 chunk size 警告
```

当前需要注意的工作区文件：

```text
M  backend/src/main/java/com/example/aipr/service/review/ReviewTaskService.java
?? backend/src/test/.gitignore
?? backend/src/test/resources/schema-test.sql
?? docs/成员B阶段任务实现方案.md
```

### 5.3 后续执行要求

```md
请先确认当前本地修改是否需要提交或保留：

1. 不要覆盖 `ReviewTaskService.java` 当前可测版本。
2. 确认 `backend/src/test/resources/schema-test.sql` 是否应纳入版本控制。
3. 确认 `backend/src/test/.gitignore` 是否是为了放行测试 schema 文件。
4. 保持后端 `mvn test` 通过。
5. 保持前端 `npm run build` 通过。
```

### 5.4 完成标准

- 当前可测代码不会被后续任务覆盖。
- 后端测试保持通过。
- 前端构建保持通过。
- 数据库测试初始化文件状态明确。

---

## 6. Phase 1：补齐 AiReviewService 最小可测闭环

### 6.1 目标

为成员 B 主责的 AI Review 服务补齐单元测试和边界保护。

当前已有：

```text
AiReviewContext
→ PromptRenderer
→ LlmClient
→ OpenAiCompatibleClient
→ AiReviewOutputParser
→ FileReviewResult
```

但当前测试主要覆盖 `PromptRenderer` 和 `AiReviewOutputParser`，还缺少 `AiReviewService` 自身的串联测试。

### 6.2 执行内容

```md
请新增 `AiReviewServiceTest`：

路径：
backend/src/test/java/com/example/aipr/service/ai/AiReviewServiceTest.java

测试内容至少包括：

1. 输入 Java 文件 patch，Fake LlmClient 返回合法 JSON，AiReviewService 返回 FileReviewResult。
2. Fake LlmClient 返回 Markdown 包裹 JSON，仍可解析。
3. Fake LlmClient 返回非 JSON，抛出 BusinessException，错误码为 AI_RESPONSE_PARSE_ERROR。
4. `.png`、`.jpg`、`.svg`、`package-lock.json`、`dist/`、`target/`、`node_modules/` 文件被跳过，不调用 LlmClient。
5. patch 为空时不调用 LlmClient，返回空 comments。
6. context 为 null 或 filePath 为空时不产生 NPE，返回明确业务异常或安全兜底。
```

同时检查 `AiReviewService`：

```md
1. 不要在日志中打印完整 rawOutput。
2. 可以记录 filePath、输出长度、最多 200 字符摘要。
3. 不要记录 API Key、GitHub Token、完整模型输出。
4. 不要连接真实 AI API 做单元测试。
5. 不新增复杂测试依赖，优先使用简单 Fake LlmClient。
```

### 6.3 涉及文件

```text
backend/src/main/java/com/example/aipr/service/ai/AiReviewService.java
backend/src/test/java/com/example/aipr/service/ai/AiReviewServiceTest.java
```

### 6.4 验证命令

```bash
cd backend
mvn test
```

### 6.5 完成标准

- 新增 `AiReviewServiceTest` 通过。
- 后端全部测试通过。
- AI 原始输出不会完整写入日志。
- AI Review 服务边界清晰，不因 null 输入导致 NPE。

---

## 7. Phase 2：前端接入任务状态轮询

### 7.1 目标

前端真实接口流程需要从“创建任务后立即取 report”改为“轮询状态后再取 report”。

当前流程：

```text
createReviewTask
→ getReviewReport
```

目标流程：

```text
createReviewTask
→ poll getReviewTask
→ SUCCESS 后 getReviewReport
→ FAILED / CANCELLED 展示 errorMessage
```

### 7.2 执行内容

```md
请改造前端真实接口流程：

1. 在 `frontend/src/api/review.js` 新增：
   - getReviewTask(taskId)

2. 在 `HomeView.vue` 中新增：
   - pollReviewTaskStatus(taskId)
   - 每 2 秒轮询一次
   - 最多轮询 90 次
   - 轮询状态包括：
     PENDING
     FETCHING_PR
     PARSING_DIFF
     REVIEWING
     SUMMARIZING

3. `handleAnalyze` 流程调整为：
   - 校验 PR URL
   - loading=true
   - createReviewTask(prUrl)
   - 获取 taskId
   - pollReviewTaskStatus(taskId)
   - status=SUCCESS 时 getReviewReport(taskId)
   - status=FAILED 时展示后端 errorMessage
   - status=CANCELLED 时展示任务已取消
   - 超时后提示：AI 分析耗时较长，请稍后刷新报告
   - finally loading=false

4. Mock 保留为开发兜底：
   - 默认 `useMock=false`
   - 真实接口仍是演示主路径
```

### 7.3 用户提示要求

```text
PR 链接格式错误，请输入 GitHub Pull Request 地址
AI 正在分析中，请稍候
AI 分析失败，请稍后重试
任务执行失败，请检查 PR 链接或模型配置
暂无 Review 建议，可能该 PR 风险较低
```

### 7.4 涉及文件

```text
frontend/src/api/review.js
frontend/src/views/HomeView.vue
frontend/src/components/PrInputCard.vue，按需
```

### 7.5 验证命令

```bash
cd frontend
npm run build
```

### 7.6 完成标准

- 前端构建通过。
- 真实接口不再创建任务后立即请求最终报告。
- 后端任务执行中、成功、失败状态都能在页面上正确处理。
- `riskItems=[]`、`testSuggestions=[]`、`report.prInfo` 缺失时页面不崩溃。

---

## 8. Phase 3：前端报告展示与错误状态完善

### 8.1 目标

提升演示稳定性，让页面能清楚展示执行中、成功、失败、空结果状态。

### 8.2 执行内容

```md
请在现有报告页面基础上补齐以下状态：

1. loading 状态：
   - 创建任务中
   - 获取 PR 信息中
   - AI Review 执行中
   - 汇总报告中

2. failed 状态：
   - 展示后端 errorMessage
   - 不展示空白报告

3. empty 状态：
   - riskItems 为空时展示“暂无 Review 建议，可能该 PR 风险较低”
   - testSuggestions 为空时展示“暂无测试建议”

4. report 字段兜底：
   - report.prInfo 缺失时不崩溃
   - riskScore 缺失时按 0 处理
   - riskLevel 缺失时按 INFO 或 LOW 展示
```

### 8.3 涉及文件

```text
frontend/src/views/HomeView.vue
frontend/src/components/PrInfoCard.vue
frontend/src/components/RiskScoreCard.vue
frontend/src/components/RiskItemCard.vue
frontend/src/components/TestSuggestionCard.vue
frontend/src/components/FinalReviewCard.vue
```

### 8.4 验证命令

```bash
cd frontend
npm run build
```

### 8.5 完成标准

- 页面不会因为后端字段为空崩溃。
- 失败原因清晰展示。
- 无风险建议时有明确空状态。
- 复制 Review 建议功能仍可用。

---

## 9. Phase 4：配合真实环境端到端联调

### 9.1 目标

确认 MVP 主流程在真实环境中可跑通。

端到端流程：

```text
启动 MySQL
→ 初始化数据库
→ 配置 GITHUB_TOKEN
→ 配置 AI_BASE_URL / AI_API_KEY / AI_MODEL_NAME
→ 启动后端
→ 启动前端
→ 输入真实 GitHub PR URL
→ 创建 Review 任务
→ 轮询任务状态
→ AI Review 执行完成
→ 前端展示报告
```

### 9.2 成员 B 负责内容

```md
成员 B 重点验证：

1. 前端是否按状态轮询。
2. SUCCESS 后是否展示真实 report。
3. FAILED 后是否展示 errorMessage。
4. riskItems 是否按后端字段正常展示。
5. confidence 是否显示为百分比。
6. testSuggestions 和 finalReview 是否正常展示。
7. 复制建议按钮是否可用。
8. 不在前端暴露 AI_API_KEY 或 GITHUB_TOKEN。
```

### 9.3 需要 A/B 协作确认

```text
1. MySQL 是否已创建数据库和表。
2. `backend/src/main/resources/db/schema.sql` 是否纳入交付。
3. GITHUB_TOKEN 是否可访问演示 PR。
4. AI API Key 和模型服务是否可用。
5. 后端日志是否没有密钥和完整 rawOutput。
```

### 9.4 验证命令

```bash
cd backend
mvn test

cd frontend
npm run build
```

手动验证：

```text
1. 启动后端。
2. 启动前端。
3. 输入测试 PR URL。
4. 创建 Review 任务。
5. 等待任务 SUCCESS。
6. 查看 Review 报告。
7. 刷新页面或重新请求 report，确认结果仍可查看。
```

### 9.5 完成标准

- 真实 PR 可创建任务。
- 后端任务最终进入 SUCCESS 或明确 FAILED。
- 前端可展示成功报告或失败原因。
- 数据库中有 `review_task`、`review_file`、`review_comment` 记录。

---

## 10. Phase 5：同步文档与接口契约

### 10.1 目标

修正文档和当前实现不一致的问题，避免后续联调误判。

### 10.2 必须同步的内容

```md
请同步更新：

1. README.md
2. docs/03-数据库设计.md
3. docs/04-接口设计.md
4. docs/05-Prompt设计.md
5. docs/02-技术设计文档-TDD.md，必要时
```

### 10.3 重点修正项

```text
1. 核心接口应以当前代码为准：
   POST /api/github/preview
   POST /api/review-tasks
   GET /api/review-tasks/{taskId}
   GET /api/review-tasks/{taskId}/files
   GET /api/review-tasks/{taskId}/comments
   GET /api/review-tasks/{taskId}/report

2. 不再把 `/api/reviews/analyze` 写成 MVP 主链路。

3. 数据库表以当前代码为准：
   review_task
   review_file
   review_comment
   review_skill
   review_skill_result

4. 不再使用旧表名：
   review_risk_item
   review_file_change

5. Prompt 文件级输出以当前 Parser 支持字段为准：
   filePath
   summary
   comments[].line
   comments[].riskType
   comments[].severity
   comments[].title
   comments[].description
   comments[].suggestion
   comments[].confidence
   comments[].needHumanCheck

6. RiskItemVO 前端展示字段以当前代码为准：
   filePath
   line
   riskLevel
   riskType
   title
   description
   suggestion
   confidence
   needHumanCheck

7. confidence 使用 number，范围 0 到 1，不使用 "HIGH" / "MEDIUM" 字符串。
```

### 10.4 完成标准

- README 的启动方式和环境变量与 `application.yml` 一致。
- `docs/04-接口设计.md` 中的接口示例可以直接用于联调。
- `docs/05-Prompt设计.md` 的文件级 JSON 与 `AiReviewOutputParser` 一致。
- 数据库文档不再描述旧表名作为 MVP 当前实现。

---

## 11. Phase 6：演示准备与日报整理

### 11.1 目标

确保项目可以用于 MVP 演示和阶段复盘。

### 11.2 成员 B 交付内容

```text
1. 前端真实流程演示说明。
2. AI Review 和 Prompt 设计说明。
3. 风险类型、风险等级、confidence、needHumanCheck 说明。
4. 误报和漏报控制说明。
5. 一份可放入开发记录的成员 B 进度总结。
6. 演示用截图或备用 Mock 数据说明。
```

### 11.3 演示前检查

```text
1. 后端 `mvn test` 通过。
2. 前端 `npm run build` 通过。
3. 数据库已初始化。
4. GitHub Token 可用，或明确 public PR 匿名访问可用。
5. AI API Key 可用。
6. 前端默认走真实接口。
7. Mock 仅作为兜底，不作为主演示路径。
8. README 和核心 docs 与实际代码一致。
```

---

## 12. 优先级任务表

| 优先级 | 任务 | 负责人建议 | 涉及文件 | 完成标准 |
| --- | --- | --- | --- | --- |
| P0 | 固化当前可测基线，避免覆盖未提交改动 | A/B 协作 | `ReviewTaskService.java`、测试 schema、本文档 | 后端测试和前端构建保持通过 |
| P0 | 前端接入任务状态轮询 | 成员 B | `review.js`、`HomeView.vue` | SUCCESS 后拉报告，FAILED 展示错误 |
| P0 | 真实端到端联调 | A/B 协作 | 前后端、数据库、环境变量 | 真实 PR 可跑通或明确失败 |
| P1 | 补 `AiReviewServiceTest` | 成员 B | `AiReviewService.java`、测试文件 | AI Review 服务可测 |
| P1 | 修正 AI rawOutput 日志 | 成员 B | `AiReviewService.java` | 不打印完整模型输出 |
| P1 | 同步 README 和核心 docs | A/B 协作 | README、docs/03、docs/04、docs/05 | 文档与代码一致 |
| P1 | 前端错误、执行中、空状态完善 | 成员 B | `HomeView.vue`、组件 | 页面状态清晰稳定 |
| P2 | 增加详情页/历史页 | 成员 B | 前端 router/views | 刷新后可按 taskId 查看 |
| P2 | 大 PR 限制和 skipped 展示 | A/B 协作 | 后端 review/ai、前端文件列表 | 超限不崩溃 |

---

## 13. 建议提交顺序

当前建议按以下提交拆分：

```text
fix: keep review task persistence baseline stable
test: add ai review service unit tests
fix: sanitize ai review raw output logging
feat: add frontend review task polling
fix: improve review report frontend states
docs: update member b implementation plan
docs: align api database and prompt documents
```

如果当前 `ReviewTaskService.java`、`schema-test.sql`、`backend/src/test/.gitignore` 是同一批修复，建议先由当前修改者确认后单独提交，避免后续成员 B 前端改动混在一起。

---

## 14. 最小可交付标准

如果时间不够，成员 B 至少完成：

```text
1. 前端接入任务状态轮询。
2. FAILED 状态可以展示 errorMessage。
3. SUCCESS 后展示真实 report。
4. `AiReviewService` 不打印完整 rawOutput。
5. 后端 `mvn test` 通过。
6. 前端 `npm run build` 通过。
7. README、docs/04、docs/05 与当前接口和 Parser 字段一致。
```

最低演示链路应达到：

```text
输入 PR URL
→ 创建数据库任务
→ 后端获取 PR diff
→ 后端执行 AI Review
→ 保存结构化建议
→ 聚合 ReviewReport
→ 前端轮询到 SUCCESS
→ 前端展示总结、风险项、测试建议和最终结论
```

---

## 15. 当前一句话结论

当前代码已经具备 MVP 后端主链路和前端报告展示基础，且后端测试、前端构建均已通过；成员 B 下一步最优先应完成 **前端任务状态轮询**，同时补齐 `AiReviewService` 测试和 AI rawOutput 日志安全。
