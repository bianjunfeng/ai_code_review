# 成员 A 任务清单 - PR 获取与任务闭环

## 1. 文档目的

本文档根据当前仓库实际代码状态，更新成员 A 的任务清单和完成情况。

成员 A 负责的主链路是：

```text
用户输入 GitHub PR 链接
→ 后端解析 owner / repo / pullNumber
→ 调用 GitHub API 获取 PR 基本信息
→ 获取 changed files 和 patch
→ 创建并持久化 Review 任务
→ 保存 PR 文件变更
→ 编排 AI Review 执行
→ 保存文件总结和风险建议
→ 聚合任务详情、文件列表、建议列表和报告数据
```

本文档只跟踪成员 A 的 PR 获取、任务管理、数据持久化、任务状态流转和后端接口闭环。Prompt 设计、模型输出解析和前端报告展示仍以成员 B 任务为主。

---

## 2. 当前进度快照

| 项目 | 状态 |
| --- | --- |
| 工作分支 | `feature/pr-task-chain` |
| 最新基线 | 已从 `dev` 拉取并合并到当前工作分支 |
| 后端测试 | 已通过 `.\mvnw.cmd test "-Dsurefire.useFile=false"` |
| 测试结果 | `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0` |
| 当前性质 | 代码层 P0 闭环已完成，真实 GitHub / MySQL / AI Key 环境联调待做 |
| 前端联调 | 暂不处理，后续由成员 B 接入轮询和页面状态 |

当前仓库采用 MVC 分层组织，不再走 `domain` 包方案。持久化对象使用已有 `entity` 包，Mapper 接口在 `mapper` 包，SQL 映射在 `resources/mapper`。

---

## 3. 当前项目实际状态

### 3.1 后端已具备能力

| 模块 | 文件 | 当前状态 |
| --- | --- | --- |
| 启动类 | `backend/src/main/java/com/example/aipr/AiPrReviewBackendApplication.java` | 已存在，已启用 Mapper 扫描 |
| 统一返回 | `common/Result.java` | 已实现 `code/message/data` |
| 业务异常 | `common/BusinessException.java` | 已实现 |
| 全局异常 | `common/GlobalExceptionHandler.java` | 已处理参数异常、业务异常、系统异常 |
| 健康检查 | `controller/HealthController.java` | `GET /api/health` 可用 |
| PR URL 解析 | `service/github/PrUrlParser.java` | 已支持 GitHub PR URL 解析 |
| GitHub 配置 | `config/GitHubProperties.java` | 已支持 `GITHUB_TOKEN`、API 地址和超时配置 |
| GitHub Client | `service/github/GitHubClient.java` | 已封装 PR 详情和 changed files 调用 |
| GitHub 预览 | `service/github/GitHubPullRequestService.java` | 已改为真实 GitHub API 数据映射 |
| 任务创建 | `service/review/ReviewTaskService.java` | 已改为数据库任务和文件持久化 |
| 任务执行 | `service/review/ReviewTaskExecutor.java` | 已异步编排 AI Review、保存总结和建议 |
| 异步配置 | `config/AsyncConfig.java` | 已提供 `reviewAsyncExecutor` |
| 任务详情接口 | `controller/ReviewTaskController.java` | 已提供 `GET /api/review-tasks/{taskId}` |
| 文件列表接口 | `controller/ReviewTaskController.java` | 已提供 `GET /api/review-tasks/{taskId}/files` |
| 建议列表接口 | `controller/ReviewTaskController.java` | 已提供 `GET /api/review-tasks/{taskId}/comments` |
| 报告接口 | `service/report/ReviewReportService.java` | 已从数据库聚合，不再返回固定 Mock |
| 实体 | `entity/ReviewTask.java`、`ReviewFile.java`、`ReviewComment.java` | 已存在 |
| Mapper | `mapper/*Mapper.java` | 已存在并补齐任务链路所需方法 |
| Mapper XML | `resources/mapper/*Mapper.xml` | 已作为自定义 SQL 来源 |
| 数据库脚本 | `resources/sql/V1__init_review_schema.sql` | 已存在 |
| 测试配置 | `src/test/resources/application-test.yml`、`schema-test.sql` | 已支持 H2 测试环境 |

### 3.2 已完成的成员 A P0 项

| 优先级 | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| P0 | 接入真实 GitHub PR 预览 | 已完成 | `POST /api/github/preview` 已调用 GitHub PR 和 files API |
| P0 | 创建 Review 任务持久化 | 已完成 | 不再使用 `AtomicLong` 内存 taskId |
| P0 | 保存 PR 文件变更 | 已完成 | changed files 和 patch 保存到 `review_file` |
| P0 | 任务状态流转 | 已完成 | 支持 `FETCHING_PR/PARSING_DIFF/REVIEWING/SUMMARIZING/SUCCESS/FAILED` |
| P0 | 异步执行 Review | 已完成 | `ReviewTaskExecutor` 调用已有 `AiReviewService` |
| P0 | 保存 AI 文件总结 | 已完成 | `FileReviewResult.summary` 写入 `review_file.ai_summary` |
| P0 | 保存 AI 风险建议 | 已完成 | 模型结构化结果写入 `review_comment` |
| P0 | 任务详情查询 | 已完成 | 前端可轮询任务状态 |
| P0 | 文件列表查询 | 已完成 | 前端可展示文件级结果 |
| P0 | 评论列表查询 | 已完成 | 支持按 `riskLevel`、`riskType` 过滤 |
| P0 | 报告真实聚合 | 已完成 | 从 `review_task/review_file/review_comment` 聚合报告 |
| P0 | GitHub 异常映射 | 已完成 | 增加 GitHub 权限、限流、PR 不存在等错误码 |
| P0 | 后端测试基线 | 已完成 | 30 个测试通过 |

### 3.3 当前仍需处理

| 优先级 | 任务 | 当前状态 | 说明 |
| --- | --- | --- | --- |
| P0 | 更新成员 A 任务清单 | 进行中 | 本文档即为更新结果 |
| P1 | 真实 GitHub Token 联调 | 待做 | 需要本机配置 `GITHUB_TOKEN` 后用真实 PR 验证 |
| P1 | 真实 MySQL 联调 | 待做 | 需要执行初始化 SQL 并配置数据库连接 |
| P1 | 真实 AI Key 联调 | 待做 | 需要配置 `AI_API_KEY`、`AI_BASE_URL` 等环境变量 |
| P1 | 前端轮询接入 | 待成员 B | 后端接口已具备，前端暂不在本阶段处理 |
| P1 | README / 接口文档同步 | 待做 | 需要把实际接口和配置写回核心文档 |
| P2 | 大 PR 文件数和 patch 总量限制 | 待增强 | 当前可跑 MVP，后续补跳过策略和统计提示 |
| P2 | GitHub files 分页完整处理 | 待增强 | MVP 可先按单页/限制处理，后续补 Link next |
| P2 | 任务取消能力 | 待增强 | 当前枚举有 `CANCELLED`，尚未提供取消接口 |

---

## 4. 成员 A 角色边界

### 4.1 成员 A 主责

```text
1. GitHub PR URL 解析和校验。
2. GitHub Token 配置读取与安全使用。
3. GitHub PR 基本信息获取。
4. GitHub changed files / patch 获取。
5. review_task / review_file / review_comment 数据落地。
6. Review 任务创建、查询和状态流转。
7. Review 任务执行编排。
8. 将 GitHub 数据转换为 AI Review 所需上下文。
9. 调用成员 B 已提供的 AiReviewService。
10. 保存文件级 AI Review 结果和风险建议。
11. 为前端提供任务详情、文件列表、评论列表和报告数据。
12. GitHub、数据库、任务状态相关异常处理。
13. PR 获取、任务流程和后端接口文档维护。
```

### 4.2 成员 A 不负责或只配合

```text
1. 不重新设计 Prompt。
2. 不重复实现 LlmClient / OpenAiCompatibleClient。
3. 不负责模型返回 JSON 的清洗和解析主逻辑。
4. 不负责前端报告页面 UI。
5. 不实现 GitHub OAuth、Webhook、自动评论到 GitHub PR。
6. 不实现完整 Skill 平台、RAG、Agent、多模型投票。
7. 不在代码中硬编码 GitHub Token、AI API Key、数据库密码。
8. 不直接把数据库 Entity 返回给前端。
```

### 4.3 与成员 B 的依赖关系

| 依赖 | 当前状态 | 说明 |
| --- | --- | --- |
| `AiReviewContext` | 已存在 | 成员 A 执行器已构造上下文 |
| `AiReviewService` | 已存在 | 成员 A 执行器已调用 |
| `FileReviewResult` | 已存在 | 已映射到 `review_file` 和 `review_comment` |
| `PromptRenderer` | 已存在 | 成员 A 仅修复必要截断状态，不改 Prompt 方向 |
| 前端轮询逻辑 | 待成员 B | 后端已提供任务状态接口 |

---

## 5. 当前接口契约

成员 A 统一使用 `/api/review-tasks` 任务式接口，不新增 `/api/reviews/analyze` 旧路径。

### 5.1 GitHub PR 预览

```http
POST /api/github/preview
```

请求：

```json
{
  "prUrl": "https://github.com/owner/repo/pull/12"
}
```

响应核心字段：

```json
{
  "owner": "owner",
  "repo": "repo",
  "pullNumber": 12,
  "title": "feat: add login api",
  "author": "zhangsan",
  "sourceBranch": "feature/login",
  "targetBranch": "main",
  "state": "OPEN",
  "additions": 120,
  "deletions": 30,
  "changedFiles": 3,
  "files": []
}
```

### 5.2 创建 Review 任务

```http
POST /api/review-tasks
```

请求：

```json
{
  "prUrl": "https://github.com/owner/repo/pull/12"
}
```

响应：

```json
{
  "taskId": 1,
  "status": "PENDING"
}
```

创建任务时已经完成：

```text
1. 解析 PR URL。
2. 创建 review_task。
3. 获取 GitHub PR 信息。
4. 获取 changed files。
5. 保存 review_file。
6. 触发 ReviewTaskExecutor 异步执行。
```

### 5.3 查询任务详情

```http
GET /api/review-tasks/{taskId}
```

用于前端轮询：

```json
{
  "taskId": 1,
  "prUrl": "https://github.com/owner/repo/pull/12",
  "prTitle": "feat: add login api",
  "author": "zhangsan",
  "sourceBranch": "feature/login",
  "targetBranch": "main",
  "status": "REVIEWING",
  "riskScore": null,
  "riskLevel": null,
  "errorMessage": null,
  "createdAt": "2026-05-30 16:00:00",
  "updatedAt": "2026-05-30 16:01:00"
}
```

### 5.4 查询任务文件列表

```http
GET /api/review-tasks/{taskId}/files
```

返回 `ReviewFileVO` 列表，用于展示文件路径、文件状态、增删行、AI summary 和 skipped 信息。

### 5.5 查询 Review 建议列表

```http
GET /api/review-tasks/{taskId}/comments?riskLevel=HIGH&riskType=SECURITY_RISK
```

返回 `ReviewCommentVO` 列表，用于单独展示风险建议，也供报告聚合复用。

### 5.6 查询 Review 报告

```http
GET /api/review-tasks/{taskId}/report
```

当前已从数据库聚合：

```text
1. prInfo：PR 标题、作者、URL、分支、文件数、增删行。
2. summary：任务总结。
3. riskScore / riskLevel：风险评分和等级。
4. mainChanges：文件级 summary 聚合。
5. riskItems：review_comment 风险建议。
6. testSuggestions：基于测试风险生成建议。
7. finalReview：最终评审结论。
```

---

## 6. 当前 MVC 目录结构约定

### 6.1 成员 A 相关目录

```text
backend/src/main/java/com/example/aipr/config
backend/src/main/java/com/example/aipr/controller
backend/src/main/java/com/example/aipr/entity
backend/src/main/java/com/example/aipr/mapper
backend/src/main/java/com/example/aipr/service/github
backend/src/main/java/com/example/aipr/service/review
backend/src/main/java/com/example/aipr/service/report
backend/src/main/java/com/example/aipr/vo
backend/src/main/resources/mapper
backend/src/main/resources/sql
```

### 6.2 分层职责

| 层 | 职责 | 约束 |
| --- | --- | --- |
| `controller` | 暴露 HTTP 接口 | 只接收 DTO、返回 VO，不写业务细节 |
| `service/github` | PR URL 解析和 GitHub API 调用 | 不处理数据库写入 |
| `service/review` | 任务创建、状态流转、AI Review 编排 | 不拼 Prompt、不解析模型原始 JSON |
| `service/report` | 从数据库聚合报告 | 不返回 Entity，不返回 raw output |
| `entity` | 数据库表对象 | 不直接暴露给前端 |
| `mapper` | MyBatis-Plus Mapper 接口 | 自定义 SQL 放 XML |
| `resources/mapper` | Mapper XML | 作为复杂查询和批量操作来源 |
| `vo` | 前端响应对象 | 字段保持接口稳定 |

---

## 7. 后端文件清单

### 7.1 本阶段新增文件

```text
backend/src/main/java/com/example/aipr/config/GitHubProperties.java
backend/src/main/java/com/example/aipr/config/AsyncConfig.java

backend/src/main/java/com/example/aipr/service/github/GitHubClient.java
backend/src/main/java/com/example/aipr/service/github/GitHubPrInfo.java
backend/src/main/java/com/example/aipr/service/github/GitHubChangedFile.java

backend/src/main/java/com/example/aipr/service/review/ReviewTaskExecutor.java

backend/src/main/java/com/example/aipr/vo/ReviewTaskDetailVO.java
backend/src/main/java/com/example/aipr/vo/ReviewFileVO.java
backend/src/main/java/com/example/aipr/vo/ReviewCommentVO.java

backend/src/test/resources/application-test.yml
backend/src/test/resources/schema-test.sql
```

### 7.2 本阶段修改文件

```text
backend/pom.xml
backend/src/main/java/com/example/aipr/controller/ReviewTaskController.java
backend/src/main/java/com/example/aipr/enums/ErrorCode.java
backend/src/main/java/com/example/aipr/mapper/ReviewTaskMapper.java
backend/src/main/java/com/example/aipr/mapper/ReviewFileMapper.java
backend/src/main/java/com/example/aipr/mapper/ReviewCommentMapper.java
backend/src/main/java/com/example/aipr/mapper/ReviewSkillMapper.java
backend/src/main/java/com/example/aipr/mapper/ReviewSkillResultMapper.java
backend/src/main/java/com/example/aipr/service/github/GitHubPullRequestService.java
backend/src/main/java/com/example/aipr/service/prompt/PromptRenderer.java
backend/src/main/java/com/example/aipr/service/report/ReviewReportService.java
backend/src/main/java/com/example/aipr/service/review/ReviewTaskService.java
backend/src/main/resources/application.yml
backend/src/main/resources/application-example.yml
backend/src/test/java/com/example/aipr/AiPrReviewBackendApplicationTests.java
```

### 7.3 已由 dev 基线提供并复用的文件

```text
backend/src/main/java/com/example/aipr/entity/ReviewTask.java
backend/src/main/java/com/example/aipr/entity/ReviewFile.java
backend/src/main/java/com/example/aipr/entity/ReviewComment.java
backend/src/main/java/com/example/aipr/entity/ReviewSkill.java
backend/src/main/java/com/example/aipr/entity/ReviewSkillResult.java

backend/src/main/resources/mapper/ReviewTaskMapper.xml
backend/src/main/resources/mapper/ReviewFileMapper.xml
backend/src/main/resources/mapper/ReviewCommentMapper.xml
backend/src/main/resources/mapper/ReviewSkillMapper.xml
backend/src/main/resources/mapper/ReviewSkillResultMapper.xml

backend/src/main/resources/sql/V1__init_review_schema.sql
```

---

## 8. 任务状态流转

### 8.1 标准流程

```text
PENDING
→ FETCHING_PR
→ PARSING_DIFF
→ REVIEWING
→ SUMMARIZING
→ SUCCESS
```

### 8.2 失败流程

```text
任意执行状态
→ FAILED
```

失败时需要保存：

```text
1. taskId。
2. 当前阶段。
3. errorMessage。
4. 不暴露 GitHub Token、AI API Key、完整敏感 patch。
```

### 8.3 后续增强

```text
任意未完成状态
→ CANCELLED
```

当前只保留枚举，不实现取消接口。

---

## 9. 异常处理清单

### 9.1 已补充错误码

```text
REVIEW_TASK_NOT_FOUND
GITHUB_API_ERROR
GITHUB_TOKEN_INVALID
GITHUB_REPOSITORY_NO_PERMISSION
GITHUB_PR_NOT_FOUND
GITHUB_RATE_LIMITED
```

### 9.2 已覆盖场景

```text
1. PR 链接格式错误。
2. GitHub 认证失败。
3. GitHub 仓库无权限。
4. GitHub PR 不存在或无访问权限。
5. GitHub API 限流。
6. taskId 不存在。
7. AI Review 执行失败后任务进入 FAILED。
```

### 9.3 待增强场景

```text
1. 大 PR 文件数限制。
2. 单文件 patch 超长跳过。
3. 二进制文件、锁文件、构建产物的统一 skipped 标记。
4. GitHub files API 分页完整拉取。
```

---

## 10. 测试与验证

### 10.1 已完成验证

```powershell
cd backend
.\mvnw.cmd test "-Dsurefire.useFile=false"
```

结果：

```text
Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 10.2 当前测试覆盖重点

```text
1. 健康检查。
2. PR URL 解析。
3. GitHub preview 映射。
4. 创建 Review 任务。
5. 查询任务详情。
6. 查询文件列表。
7. 查询报告。
8. H2 测试库初始化。
9. GitHubClient 和 ReviewTaskExecutor 通过 Mock 隔离外部依赖。
```

### 10.3 后续真实联调验证

```text
1. 配置 GITHUB_TOKEN，使用真实 public/private PR 验证 preview。
2. 配置 MySQL，验证 review_task/review_file/review_comment 落库。
3. 配置 AI_API_KEY，验证 ReviewTaskExecutor 到 SUCCESS。
4. 前端接入轮询后验证完整页面流程。
```

---

## 11. 环境变量与配置

### 11.1 GitHub 配置

```yaml
github:
  token: ${GITHUB_TOKEN:}
  api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
  connect-timeout-seconds: ${GITHUB_CONNECT_TIMEOUT_SECONDS:10}
  read-timeout-seconds: ${GITHUB_READ_TIMEOUT_SECONDS:30}
```

注意：

```text
1. `GITHUB_TOKEN` 可以为空，public 仓库可尝试匿名访问。
2. private 仓库需要配置 token。
3. 真实 token 不写入 application.yml、README、提交记录和日志。
```

### 11.2 AI 配置

成员 A 不新增 AI Client，但执行器依赖已有 AI 配置：

```text
AI_BASE_URL
AI_API_KEY
AI_MODEL_NAME
AI_TEMPERATURE
```

真实联调前需要确认这些变量可用。

---

## 12. 与前端和成员 B 的交接点

### 12.1 已交付给前端的接口

```text
POST /api/github/preview
POST /api/review-tasks
GET /api/review-tasks/{taskId}
GET /api/review-tasks/{taskId}/files
GET /api/review-tasks/{taskId}/comments
GET /api/review-tasks/{taskId}/report
```

### 12.2 前端后续建议流程

```text
createReviewTask(prUrl)
→ poll GET /api/review-tasks/{taskId}
→ status=SUCCESS 后 GET /api/review-tasks/{taskId}/report
→ status=FAILED 时展示 errorMessage
```

### 12.3 成员 B 可直接使用的数据

```text
1. ReviewTaskDetailVO：任务状态和错误信息。
2. ReviewFileVO：文件变更和 AI summary。
3. ReviewCommentVO：结构化风险建议。
4. ReviewReportVO：报告聚合结果。
```

---

## 13. 当前风险点

| 风险 | 表现 | 处理 |
| --- | --- | --- |
| 未配置 GitHub Token | private PR 无法访问 | 配置 `GITHUB_TOKEN`，public PR 可匿名尝试 |
| GitHub API 限流 | preview 或创建任务失败 | 返回 `GITHUB_RATE_LIMITED` 友好错误 |
| 未配置 MySQL | 本地启动无法落库 | 执行 `V1__init_review_schema.sql` 并配置 DB 环境变量 |
| 未配置 AI Key | 任务执行失败 | 配置 AI 环境变量，失败时任务进入 `FAILED` |
| 大 PR 超出模型上下文 | 分析慢或失败 | 后续补文件跳过和 patch 截断策略 |
| 前端未轮询状态 | 创建后不能等待异步结果 | 后端接口已提供，由成员 B 接入 |
| 文档仍有旧接口描述 | 前后端理解不一致 | 后续同步 README、接口设计、数据库设计 |

---

## 14. 下一步任务表

### 14.1 成员 A 下一步

| 优先级 | 任务 | 验收方式 |
| --- | --- | --- |
| P0 | 完成本文档更新 | `docs/成员A任务清单.md` 与当前代码状态一致 |
| P1 | 用真实 GitHub PR 验证 preview | 返回真实 title、author、branch、files |
| P1 | 用 MySQL 验证任务落库 | `review_task/review_file/review_comment` 有真实数据 |
| P1 | 用真实 AI Key 验证任务执行 | 任务最终进入 `SUCCESS` 或明确 `FAILED` |
| P1 | 同步核心 docs | README、接口设计、数据库设计不再写旧 Mock 状态 |
| P2 | 增加大 PR 限制策略 | 超限文件被 skipped，接口仍能响应 |
| P2 | 补 GitHub files 分页 | 超过 100 个文件时能继续拉取或明确限制 |

### 14.2 交给成员 B / 前端的任务

| 优先级 | 任务 | 依赖 |
| --- | --- | --- |
| P0 | 前端接入任务轮询 | `GET /api/review-tasks/{taskId}` 已提供 |
| P0 | 前端在 `SUCCESS` 后拉报告 | `GET /api/review-tasks/{taskId}/report` 已提供 |
| P1 | 前端展示文件和评论列表 | `/files`、`/comments` 已提供 |
| P1 | 失败状态展示 `errorMessage` | 任务详情接口已提供 |

---

## 15. 最小可交付状态

当前代码层已经达到成员 A 最小可交付：

```text
1. /api/github/preview 返回真实 GitHub PR 信息和文件列表。
2. /api/review-tasks 创建数据库任务，不再使用 AtomicLong。
3. review_task 和 review_file 能保存真实数据。
4. GET /api/review-tasks/{taskId} 可以查询任务状态。
5. GET /api/review-tasks/{taskId}/files 可以查询文件列表。
6. ReviewTaskExecutor 可以调用 AiReviewService。
7. 执行结果可以保存 review_comment。
8. GET /api/review-tasks/{taskId}/report 不再返回固定 Mock。
9. GitHub 异常有明确错误提示。
10. 后端测试通过。
```

最低演示链路：

```text
输入 PR URL
→ 获取真实 PR 信息和 diff
→ 创建 Review 任务
→ 保存任务和文件
→ 执行 AI Review
→ 保存建议
→ 查询任务状态和报告
```

真正演示前还需要完成真实环境联调：

```text
GITHUB_TOKEN
DB_URL / DB_USERNAME / DB_PASSWORD
AI_BASE_URL / AI_API_KEY / AI_MODEL_NAME
```

---

## 16. 推荐提交说明

本阶段推荐提交说明：

```text
feat: 完成成员A PR 获取与任务闭环

- 接入 GitHub PR 详情和 changed files 获取
- 持久化 Review 任务、文件变更和评审建议
- 新增任务详情、文件列表、评论列表和真实报告接口
- 增加异步 Review 执行器和 GitHub 异常处理
- 补充 H2 测试配置并通过后端测试
```
