# 成员 B 任务清单 - AI Review 与报告展示闭环

## 1. 文档目的

本文档根据当前仓库实际代码状态，重新整理成员 B 的任务清单。

成员 B 的目标不是重构整个项目，而是在现有 MVP 骨架上，补齐以下闭环：

```text
后端拿到 PR diff
→ 构造 AI Review Prompt
→ 调用 OpenAI Compatible API
→ 清洗并解析模型 JSON
→ 生成结构化 Review 报告
→ 前端展示报告、风险项、测试建议和最终结论
```

本文档只规划成员 B 负责的 AI Review 与报告展示链路。GitHub API 真实调用、任务持久化、review_task / review_file 数据落库主要依赖成员 A 的任务闭环，但成员 B 需要清楚这些依赖点，并提前做好接口和数据结构适配。

---

## 2. 当前项目实际状态

### 2.1 后端当前已完成

| 模块 | 文件 | 当前状态 |
| --- | --- | --- |
| 启动类 | `backend/src/main/java/com/example/aipr/AiPrReviewBackendApplication.java` | 已存在 |
| 统一返回 | `common/Result.java` | 已实现 `code/message/data` |
| 业务异常 | `common/BusinessException.java` | 已实现 |
| 全局异常 | `common/GlobalExceptionHandler.java` | 已处理参数异常、业务异常、系统异常 |
| 健康检查 | `controller/HealthController.java` | `GET /api/health` 已可用 |
| PR URL 解析 | `service/github/PrUrlParser.java` | 已支持 `https://github.com/{owner}/{repo}/pull/{pullNumber}` |
| PR URL 结果 | `service/github/ParsedPrUrl.java` | 已存在 record |
| GitHub 预览接口 | `controller/GitHubController.java` | `POST /api/github/preview` 已接通 |
| GitHub 预览服务 | `service/github/GitHubPullRequestService.java` | 只返回占位数据，尚未真实调用 GitHub API |
| 创建任务接口 | `controller/ReviewTaskController.java` | `POST /api/review-tasks` 已接通 |
| 创建任务服务 | `service/review/ReviewTaskService.java` | 使用 `AtomicLong` 生成内存 taskId，只返回 `PENDING` |
| 报告接口 | `controller/ReviewTaskController.java` | `GET /api/review-tasks/{taskId}/report` 已接通 |
| 报告服务 | `service/report/ReviewReportService.java` | 返回写死 Mock 报告 |
| 枚举 | `ReviewTaskStatus` / `RiskType` / `Severity` | 已存在，并与 AGENTS.md 基本一致 |
| 测试 | `AiPrReviewBackendApplicationTests.java` | 已覆盖健康检查、PR URL 解析、创建任务、Mock 报告 |

### 2.2 后端当前未完成

| 模块 | 当前缺口 | 对成员 B 的影响 |
| --- | --- | --- |
| `domain` 包 | 不存在 `ReviewTask`、`ReviewFile`、`ReviewComment` 实体 | 暂时无法从数据库聚合真实报告 |
| `mapper` 包 | 不存在 MyBatis-Plus Mapper | 暂时无法查询/保存 AI Review 结果 |
| `service.ai` 包 | 不存在 `LlmClient`、`OpenAiCompatibleClient` | 成员 B 需要实现模型调用基础能力 |
| `service.prompt` 包 | 不存在 `PromptRenderer`、输出解析器 | 成员 B 需要实现 Prompt 和 JSON 解析 |
| AI Review 编排 | 不存在 `AiReviewService` 或类似服务 | 成员 B 需要补齐模型调用与结果转换 |
| 任务状态查询接口 | 不存在 `GET /api/review-tasks/{taskId}` | 前端暂时不能轮询真实状态 |
| 文件列表接口 | 不存在 `GET /api/review-tasks/{taskId}/files` | 前端暂时不能展示真实文件级结果 |
| 评论列表接口 | 不存在 `GET /api/review-tasks/{taskId}/comments` | 前端暂时只能从 report 展示风险项 |
| GitHub API 真实调用 | `GitHubPullRequestService` 只返回占位数据 | AI Review 暂时没有真实 patch 输入 |
| 报告 VO | `ReviewReportVO` 缺少 `prInfo` | 前端 `PrInfoCard` 需要该字段，必须补齐或前端兼容 |

### 2.3 前端当前已完成

| 模块 | 文件 | 当前状态 |
| --- | --- | --- |
| Vue 入口 | `frontend/src/main.js` | 已引入 Vue、Element Plus、图标 |
| 根组件 | `frontend/src/App.vue` | 直接渲染 `HomeView`，暂未使用 Vue Router |
| 首页 | `frontend/src/views/HomeView.vue` | 已有 PR 输入、Mock/真实接口开关、报告展示区 |
| API 文件 | `frontend/src/api/review.js` | 已有 Axios 实例、`mockReport`、`analyzeReview` |
| 输入组件 | `components/PrInputCard.vue` | 已有 PR 输入框、Mock 开关、开始分析按钮 |
| 报告组件 | `PrInfoCard`、`RiskScoreCard`、`SummaryCard`、`RiskItemCard`、`TestSuggestionCard`、`FinalReviewCard` | 页面展示骨架基本完成 |
| Vite 代理 | `frontend/vite.config.js` | `/api` 已代理到 `http://localhost:8080` |

### 2.4 前端当前未完成或不匹配

| 文件 | 当前问题 | 处理方向 |
| --- | --- | --- |
| `frontend/src/api/review.js` | `analyzeReview` 请求 `/api/reviews/analyze`，后端没有该接口 | 改为 `POST /api/review-tasks` + `GET /api/review-tasks/{taskId}/report` |
| `frontend/src/api/review.js` | `mockReport.riskItems[].riskType` 有 `SECURITY`，不符合后端枚举 | 改成 `SECURITY_RISK` |
| `HomeView.vue` | 默认 `useMock=true`，真实链路不是默认路径 | 联调阶段改为真实接口优先，Mock 仅保留开发兜底或移除 |
| `HomeView.vue` | 没有任务状态轮询 | 等成员 A 提供 `GET /api/review-tasks/{taskId}` 后补齐 |
| `PrInfoCard.vue` | 依赖 `report.prInfo` | 后端 report 需要补齐 `prInfo`，或前端根据 task/report 缺省兼容 |
| `RiskItemCard.vue` | 主要展示 `riskLevel`、`reason`、`comment` | 后端当前 `RiskItemVO` 没有 `reason/comment`，需要前端兜底或后端扩展字段 |

---

## 3. 成员 B 角色边界

### 3.1 成员 B 主责

```text
1. AI Review Prompt 设计与落地。
2. OpenAI Compatible API 调用封装。
3. 模型返回 JSON 清洗、解析、校验。
4. 文件级 Review 结果转换为项目内部结构。
5. Review 报告聚合结构设计。
6. 前端报告展示与真实接口对接。
7. AI Review 异常、空状态、解析失败的用户提示。
8. Prompt、AI Review、报告展示相关文档维护。
```

### 3.2 成员 B 不负责或只配合

```text
1. 不负责 GitHub OAuth、Webhook、自动评论到 GitHub。
2. 不负责完整 Skill 平台、RAG、Agent、多模型投票。
3. 不负责复杂权限系统和管理后台。
4. 不单独大改项目目录结构。
5. 不直接把数据库 Entity 返回给前端。
6. 不在前端调用 AI API。
7. 不在代码中硬编码 GitHub Token、AI API Key、数据库密码。
```

### 3.3 与成员 A 的依赖点

| 依赖 | 成员 A 交付后成员 B 才能完成的内容 |
| --- | --- |
| 真实 GitHub PR 信息 | Prompt 中填充 PR 标题、描述、作者、分支 |
| 真实 changed files | Prompt 中填充文件路径、状态、语言、patch |
| `review_task` 持久化 | 报告按 taskId 查询真实任务 |
| `review_file` 持久化 | 文件级 Review 能按任务遍历 |
| `review_comment` 持久化 | 风险建议能保存并聚合 |
| 任务状态查询接口 | 前端可以轮询 `PENDING -> SUCCESS/FAILED` |

如果成员 A 的持久化链路暂未完成，成员 B 可以先用 Mock LlmClient 和内存数据完成 Prompt、解析器、前端展示改造，但最终必须接入真实任务数据。

---

## 4. 最终目标接口契约

成员 B 前后端对接时，以当前已有后端接口为基础，不新增复杂接口。

### 4.1 创建任务

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
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 1,
    "status": "PENDING"
  }
}
```

### 4.2 查询报告

```http
GET /api/review-tasks/{taskId}/report
```

建议 `data` 结构保持前端当前报告组件可直接使用：

```json
{
  "taskId": 1,
  "prInfo": {
    "title": "fix login token validation",
    "author": "demo-user",
    "url": "https://github.com/owner/repo/pull/12",
    "sourceBranch": "feature/login",
    "targetBranch": "main",
    "changedFiles": 3,
    "additions": 120,
    "deletions": 30
  },
  "summary": "本次 PR 主要修改登录认证逻辑。",
  "riskScore": 78,
  "riskLevel": "HIGH",
  "mainChanges": [
    "新增 token 校验逻辑"
  ],
  "riskItems": [
    {
      "filePath": "src/main/java/com/demo/AuthService.java",
      "line": null,
      "riskLevel": "HIGH",
      "riskType": "SECURITY_RISK",
      "title": "登录校验缺少失败次数限制",
      "description": "当前 diff 中未看到失败次数限制。",
      "suggestion": "建议增加失败次数限制或短时间锁定策略。",
      "confidence": 0.78,
      "needHumanCheck": true
    }
  ],
  "testSuggestions": [
    "建议补充登录失败和 token 过期场景测试。"
  ],
  "finalReview": "建议修改高风险问题后再合并。"
}
```

### 4.3 后续状态轮询接口

成员 A 补齐后，前端再接入：

```http
GET /api/review-tasks/{taskId}
```

返回至少包含：

```json
{
  "taskId": 1,
  "status": "REVIEWING",
  "errorMessage": null
}
```

---

## 5. 执行顺序总览

成员 B 按以下顺序执行，避免一开始就陷入完整平台化设计。

```text
Phase 0：确认当前基线和接口契约
Phase 1：修复前端真实接口路径
Phase 2：实现 Prompt 渲染和模型输出 DTO
Phase 3：实现模型返回 JSON 清洗与解析
Phase 4：实现 LLM Client 和 OpenAI Compatible 调用
Phase 5：实现 AiReviewService 文件级 Review
Phase 6：替换 Mock Report 为真实报告聚合
Phase 7：完善前端报告展示和空/错状态
Phase 8：补测试和文档
Phase 9：文档同步
Phase 10：端到端联调验收
```

---

## 6. Phase 0：确认当前基线

### 6.1 后端基线确认

执行：

```bash
cd backend
mvn test
```

确认以下测试通过：

```text
1. /api/health 返回 UP。
2. /api/github/preview 可以解析正确 PR URL。
3. /api/github/preview 可以拒绝错误 PR URL。
4. /api/review-tasks 可以创建 PENDING 任务。
5. /api/review-tasks/{taskId}/report 当前返回 Mock 报告。
```

如果测试失败，先不要实现 AI 链路，优先修复当前基线。

### 6.2 前端基线确认

执行：

```bash
cd frontend
npm run build
```

确认当前页面可以正常构建。

当前真实接口按钮会请求不存在的 `/api/reviews/analyze`，这是成员 B 第一优先级要修复的问题。

### 6.3 不要改动的内容

Phase 0 不做以下事情：

```text
1. 不引入 Vue Router，除非后续明确需要详情页。
2. 不新增 OAuth、Webhook、RAG、Agent。
3. 不把 Mock 报告误认为真实链路。
4. 不改统一 Result 响应格式。
5. 不把 AI Key 写入 application.yml。
```

---

## 7. Phase 1：前端真实接口对接

目标：先让前端不再请求不存在的 `/api/reviews/analyze`。

### 7.1 修改 `frontend/src/api/review.js`

新增或替换为以下 API 方法：

```js
export async function createReviewTask(prUrl) {
  const response = await http.post('/api/review-tasks', { prUrl })
  return unwrapResult(response.data)
}

export async function getReviewReport(taskId) {
  const response = await http.get(`/api/review-tasks/${taskId}/report`)
  return unwrapResult(response.data)
}

export function unwrapResult(payload) {
  if (!payload) {
    return null
  }
  if (Object.hasOwn(payload, 'code')) {
    if (payload.code !== 0) {
      throw new Error(payload.message || '请求失败')
    }
    return payload.data
  }
  return payload
}
```

保留 `mockReport` 时必须满足：

```text
1. riskType 使用后端枚举：SECURITY_RISK、BUG_RISK、TEST_RISK 等。
2. confidence 使用数字 0-1，而不是 HIGH/MEDIUM 字符串。
3. 字段尽量和 ReviewReportVO / RiskItemVO 一致。
4. Mock 只用于前端开发兜底，不作为默认真实流程。
```

### 7.2 修改 `frontend/src/views/HomeView.vue`

当前逻辑：

```text
点击开始分析
→ 如果 useMock=true，直接展示 mockReport
→ 否则调用 analyzeReview('/api/reviews/analyze')
```

改造为：

```text
点击开始分析
→ 校验 PR URL
→ 调用 createReviewTask(prUrl)
→ 取得 taskId
→ 当前阶段直接调用 getReviewReport(taskId)
→ 展示报告
```

在成员 A 提供任务状态查询接口后，再升级为：

```text
点击开始分析
→ createReviewTask
→ pollReviewTaskStatus(taskId)
→ status=SUCCESS 时 getReviewReport(taskId)
→ status=FAILED 时展示 errorMessage
```

### 7.3 修改 `frontend/src/components/PrInputCard.vue`

推荐处理：

```text
1. 联调阶段移除可见 Mock 开关。
2. 如果必须保留 Mock，默认值改为 false。
3. 按钮 loading 时禁止重复提交。
4. 用户可见文案统一使用“开始评审”或“开始分析”，避免多个说法混用。
```

### 7.4 Phase 1 验收

后端启动后，前端关闭 Mock，输入：

```text
https://github.com/owner/repo/pull/12
```

应满足：

```text
1. 前端先请求 POST /api/review-tasks。
2. 再请求 GET /api/review-tasks/1/report。
3. 页面可以展示当前后端 Mock 报告。
4. 错误 PR URL 显示“PR 链接格式错误，请输入 GitHub Pull Request 地址”。
5. 不再出现 /api/reviews/analyze 404。
```

---

## 8. Phase 2：Prompt 渲染与模型输出结构

目标：先把 AI 输入和输出结构固定下来，后续 LLM Client 和任务执行都围绕这个结构实现。

### 8.1 新建 Prompt 相关包

新增目录：

```text
backend/src/main/java/com/example/aipr/service/prompt/
```

新增类：

```text
PromptRenderer.java
AiReviewOutputParser.java
```

### 8.2 新建 AI Review 上下文 DTO

推荐新增：

```text
backend/src/main/java/com/example/aipr/dto/AiReviewContext.java
backend/src/main/java/com/example/aipr/dto/FileReviewResult.java
backend/src/main/java/com/example/aipr/dto/FileReviewCommentResult.java
```

`AiReviewContext` 至少包含：

```java
private String prTitle;
private String prDescription;
private String prAuthor;
private String sourceBranch;
private String targetBranch;
private String filePath;
private String fileStatus;
private String language;
private Integer additions;
private Integer deletions;
private Integer changes;
private String patch;
private Boolean truncated;
```

`FileReviewResult` 对应模型文件级输出：

```java
private String filePath;
private String summary;
private List<FileReviewCommentResult> comments;
```

`FileReviewCommentResult` 对应单条建议：

```java
private Integer line;
private String riskType;
private String severity;
private String title;
private String description;
private String suggestion;
private Double confidence;
private Boolean needHumanCheck;
```

说明：

```text
1. 模型输出字段叫 severity，后端展示字段可以映射为 riskLevel。
2. riskType 必须校验在 RiskType 枚举中。
3. severity 必须校验在 Severity 枚举中。
4. confidence 必须归一化到 0-1。
5. comments 为空时返回空数组，不返回 null。
```

### 8.3 实现 `PromptRenderer`

建议方法：

```java
public String renderFileReviewPrompt(AiReviewContext context)
```

Prompt 必须遵守：

```text
1. 只基于提供的 PR 信息和 diff 分析。
2. 不要编造未提供的业务背景。
3. 不确定时设置 needHumanCheck=true。
4. 每条建议必须包含 confidence。
5. 不要输出泛泛而谈的建议。
6. 只输出合法 JSON。
7. 不要输出 Markdown。
```

模板变量使用双大括号风格：

```text
{{prTitle}}
{{prDescription}}
{{filePath}}
{{fileStatus}}
{{language}}
{{patch}}
```

Prompt 输出结构固定为：

```json
{
  "filePath": "src/main/java/UserService.java",
  "summary": "该文件主要新增用户登录校验逻辑。",
  "comments": [
    {
      "line": null,
      "riskType": "SECURITY_RISK",
      "severity": "HIGH",
      "title": "密码明文比较存在安全风险",
      "description": "当前代码直接比较明文密码。",
      "suggestion": "建议使用 BCryptPasswordEncoder。",
      "confidence": 0.92,
      "needHumanCheck": true
    }
  ]
}
```

### 8.4 Prompt 渲染细节

实现时需要处理：

```text
1. 空 PR 描述填充为“未提供”。
2. 空 patch 返回清晰提示，不直接调用模型。
3. patch 超过 12000 字符时截断，并在 Prompt 标记“diff 已截断”。
4. 文件语言根据后缀识别：.java、.vue、.js、.ts、.xml、.yml、.json、.sql、.md。
5. 跳过二进制、构建产物、锁文件和 dist/target/node_modules 目录。
```

### 8.5 Phase 2 验收

新增单元测试，至少覆盖：

```text
1. 正常 context 可以渲染 Prompt。
2. Prompt 中包含 PR 标题、文件路径、patch。
3. 空字段不会渲染出 null。
4. 超长 patch 会被截断。
5. Prompt 明确要求只输出合法 JSON。
```

---

## 9. Phase 3：模型输出清洗与 JSON 解析

目标：模型返回不稳定时，后端能清洗、解析、校验，并输出友好错误。

### 9.1 实现 `AiReviewOutputParser`

建议方法：

```java
public FileReviewResult parseFileReview(String rawOutput)
```

处理流程：

```text
1. 判断 rawOutput 是否为空。
2. 去掉 Markdown 代码块标记。
3. 提取首个 JSON 对象。
4. 使用 Jackson 解析为 FileReviewResult。
5. 校验 filePath、summary、comments。
6. 遍历 comments，校验 riskType、severity、confidence、needHumanCheck。
7. 对可修复的字段做默认值兜底。
8. 对不可解析的内容抛出业务异常。
```

### 9.2 Markdown 代码块清洗规则

需要支持：

~~~text
```json
{...}
```
~~~

以及：

~~~text
```
{...}
```
~~~

清理后只保留 JSON 文本。

### 9.3 字段校验规则

| 字段 | 规则 |
| --- | --- |
| `filePath` | 为空时使用 context 的 filePath |
| `summary` | 为空时使用 `该文件暂无明确总结` |
| `comments` | 为空时使用空数组 |
| `riskType` | 不在枚举中时降级为 `MAINTAINABILITY` 或抛出解析异常，按实现取舍 |
| `severity` | 不在枚举中时降级为 `INFO` |
| `title` | 为空时使用 `代码评审建议` |
| `description` | 为空时使用 `模型未返回详细描述，请人工确认` |
| `suggestion` | 为空时使用 `建议人工复核该变更` |
| `confidence` | 小于 0 改为 0，大于 1 改为 1，缺失时设为 0.5 |
| `needHumanCheck` | 缺失时设为 true |

### 9.4 错误处理

需要新增或复用错误码。

建议在 `ErrorCode.java` 增加：

```java
AI_SERVICE_ERROR(50201, "AI 服务调用失败，请稍后重试"),
AI_RESPONSE_PARSE_ERROR(50202, "模型返回格式异常，请重新评审")
```

注意：

```text
1. 不要把完整 rawOutput 直接返回前端。
2. 日志只记录 rawOutput 摘要。
3. rawOutput 可能包含敏感代码片段，日志长度要限制。
```

### 9.5 Phase 3 验收

单元测试至少覆盖：

```text
1. 纯 JSON 可以解析。
2. ```json 包裹的 JSON 可以解析。
3. comments 为空时解析成功。
4. confidence 超出范围会被归一化。
5. riskType 非法时按约定处理。
6. 非 JSON 返回业务异常：模型返回格式异常，请重新评审。
```

---

## 10. Phase 4：LLM Client 与 OpenAI Compatible 调用

目标：后端统一通过 `LlmClient` 调用模型，Controller 不直接接触模型 API。

### 10.1 新建 AI 包

新增目录：

```text
backend/src/main/java/com/example/aipr/service/ai/
```

新增类：

```text
LlmClient.java
LlmMessage.java
LlmRequest.java
LlmResponse.java
OpenAiCompatibleClient.java
AiReviewService.java
```

如需配置类，新增：

```text
backend/src/main/java/com/example/aipr/config/AiProperties.java
```

### 10.2 `LlmClient` 接口

```java
public interface LlmClient {
    LlmResponse chat(LlmRequest request);
}
```

### 10.3 `LlmRequest`

建议字段：

```java
private String model;
private List<LlmMessage> messages;
private Double temperature;
private Integer maxTokens;
```

`LlmMessage`：

```java
private String role;
private String content;
```

角色只使用：

```text
system
user
assistant
```

### 10.4 `LlmResponse`

建议字段：

```java
private String content;
private String finishReason;
```

### 10.5 `AiProperties`

读取当前 `application.yml` 中已有配置：

```yaml
ai:
  base-url: ${AI_BASE_URL:https://api.deepseek.com}
  api-key: ${AI_API_KEY:}
  model-name: ${AI_MODEL_NAME:deepseek-chat}
  temperature: ${AI_TEMPERATURE:0.2}
  max-tokens: 3000
```

注意：

```text
1. AI_API_KEY 为空时应抛出明确业务异常。
2. 不要在日志中打印 apiKey。
3. baseUrl 末尾是否有 / 要统一处理。
4. OpenAI Compatible 请求路径使用 /chat/completions。
```

### 10.6 `OpenAiCompatibleClient`

实现要点：

```text
1. 使用 OkHttp 构造 POST 请求。
2. URL = baseUrl + "/chat/completions"。
3. Header:
   - Authorization: Bearer ${AI_API_KEY}
   - Content-Type: application/json
4. Body:
   - model
   - messages
   - temperature
   - max_tokens
5. 解析 choices[0].message.content。
6. 非 2xx 响应转为 AI_SERVICE_ERROR。
7. 请求超时转为 AI_SERVICE_ERROR。
```

### 10.7 Phase 4 验收

测试方式：

```text
1. 使用 MockWebServer 或 mock LlmClient 测试请求/响应解析。
2. AI_API_KEY 为空时返回友好错误。
3. 模型返回空 choices 时返回 AI_SERVICE_ERROR。
4. 不在日志中输出密钥。
```

---

## 11. Phase 5：AiReviewService 文件级 Review

目标：将 PromptRenderer、LlmClient、AiReviewOutputParser 串起来。

### 11.1 新增 `AiReviewService`

建议职责：

```text
1. 接收 AiReviewContext。
2. 判断文件是否应跳过。
3. 渲染文件级 Prompt。
4. 调用 LlmClient。
5. 解析模型 JSON。
6. 返回 FileReviewResult。
```

建议方法：

```java
public FileReviewResult reviewFile(AiReviewContext context)
```

### 11.2 文件跳过规则

默认跳过：

```text
*.png
*.jpg
*.jpeg
*.gif
*.svg
*.ico
*.zip
*.jar
*.class
*.min.js
package-lock.json
yarn.lock
pnpm-lock.yaml
dist/*
target/*
node_modules/*
```

跳过时不调用模型，返回：

```text
summary = "该文件类型暂不进行 AI Review"
comments = []
```

### 11.3 与任务执行链路衔接

成员 A 的 `ReviewTaskExecutor` 或 `ReviewTaskService` 补齐后，成员 B 需要提供以下协作方式：

```text
1. 成员 A 按 taskId 查出 PR 信息和 review_file 列表。
2. 成员 A 对每个可分析文件构造 AiReviewContext。
3. 成员 A 调用成员 B 的 AiReviewService.reviewFile(context)。
4. 成员 A 保存 FileReviewResult.summary 到 review_file.ai_summary。
5. 成员 A 保存 comments 到 review_comment。
6. 成员 A 更新任务状态为 SUMMARIZING / SUCCESS / FAILED。
```

### 11.4 Phase 5 验收

使用 Mock LlmClient 验收：

```text
1. 输入一个 Java patch。
2. Mock 模型返回合法 JSON。
3. AiReviewService 返回 FileReviewResult。
4. comments 中 riskType、severity、confidence 正确。
5. Mock 模型返回 Markdown 包裹 JSON 时仍可解析。
6. Mock 模型返回非 JSON 时抛出 AI_RESPONSE_PARSE_ERROR。
```

---

## 12. Phase 6：Review 报告聚合

目标：把当前 `ReviewReportService` 的写死 Mock 数据替换为真实聚合结果。

### 12.1 当前问题

当前文件：

```text
backend/src/main/java/com/example/aipr/service/report/ReviewReportService.java
```

现状：

```text
1. 不查询数据库。
2. 不校验 taskId 是否存在。
3. 不返回真实 PR 信息。
4. 风险项固定为 JWT 硬编码示例。
5. report 与当前前端 prInfo 展示不完全匹配。
```

### 12.2 推荐报告聚合输入

等成员 A 完成持久化后，报告聚合应读取：

```text
review_task：PR 基本信息、summary、riskScore、riskLevel、finalReview、status
review_file：文件列表、文件级 summary
review_comment：风险建议列表
```

### 12.3 修改 `ReviewReportVO`

当前字段：

```java
private Long taskId;
private String summary;
private Integer riskScore;
private String riskLevel;
private List<String> mainChanges;
private List<RiskItemVO> riskItems;
private List<String> testSuggestions;
private String finalReview;
```

建议补充：

```java
private PrInfoVO prInfo;
```

或复用已有 `GitHubPrPreviewVO` 的一部分字段，但更推荐新建轻量 VO：

```text
backend/src/main/java/com/example/aipr/vo/PrInfoVO.java
```

字段：

```java
private String title;
private String author;
private String url;
private String sourceBranch;
private String targetBranch;
private Integer changedFiles;
private Integer additions;
private Integer deletions;
```

### 12.4 `RiskItemVO` 字段对齐

当前字段：

```java
private String filePath;
private Integer line;
private String riskLevel;
private String riskType;
private String title;
private String description;
private String suggestion;
private Double confidence;
private Boolean needHumanCheck;
```

建议保持这些字段，不必为了前端当前的 `reason/comment` 立即扩展数据库。

前端需要：

```text
1. 有 title 时展示 title。
2. 没有 reason 时隐藏原因分析块。
3. 没有 comment 时基于 title/description/suggestion 拼接复制文本。
```

### 12.5 风险评分计算

MVP 可使用简单规则：

```text
HIGH 数量 >= 1：riskLevel = HIGH，riskScore = 70 + min(30, highCount * 10 + mediumCount * 5)
MEDIUM 数量 >= 1：riskLevel = MEDIUM，riskScore = 40 + min(29, mediumCount * 8 + lowCount * 3)
LOW 数量 >= 1：riskLevel = LOW，riskScore = 20 + min(19, lowCount * 4)
无风险：riskLevel = INFO 或 LOW，riskScore = 0
```

注意：如果前端暂不支持 `INFO` 总风险等级，可以总风险无风险时返回 `LOW`，单条建议仍可支持 `INFO`。

### 12.6 `mainChanges` 和 `testSuggestions`

MVP 有两种方式：

```text
方式 A：由模型 PR 级汇总生成，并保存到 review_task.result_json。
方式 B：先从文件级 summary 和风险类型简单生成。
```

短周期推荐：

```text
1. 第一版使用文件级 summary 聚合 mainChanges。
2. 如果风险项包含 TEST_RISK，则生成测试建议。
3. 后续再增加 PR 级总结模型调用。
```

### 12.7 Phase 6 验收

```text
1. taskId 不存在时返回明确业务错误。
2. 无风险项时 riskItems=[]，页面显示空状态。
3. 有 HIGH 风险时 riskLevel=HIGH。
4. riskItems 字段与前端展示一致。
5. 不返回数据库 Entity。
6. 不返回 raw LLM 输出。
```

---

## 13. Phase 7：前端报告展示完善

目标：前端从真实 report 接口展示结构化报告，并能处理空数据、错误数据和刷新场景。

### 13.1 `HomeView.vue`

需要改造：

```text
1. 真实接口作为默认流程。
2. loading 时禁用提交按钮。
3. 创建任务成功后保存 currentTaskId。
4. 当前阶段直接 getReviewReport。
5. 后续补 task status 轮询。
6. catch 中优先展示后端 message。
7. finally 中恢复 loading。
```

推荐流程：

```js
async function handleAnalyze() {
  const value = prUrl.value.trim()

  if (!value) {
    ElMessage.warning('请输入 GitHub Pull Request 链接')
    return
  }

  if (!isValidPrUrl(value)) {
    ElMessage.warning('PR 链接格式错误，请输入 GitHub Pull Request 地址')
    return
  }

  loading.value = true
  report.value = null

  try {
    const created = await createReviewTask(value)
    const taskId = created?.taskId
    report.value = await getReviewReport(taskId)
    ElMessage.success('评审报告已生成')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || 'AI 分析失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
```

### 13.2 状态轮询函数预留

等后端提供 `GET /api/review-tasks/{taskId}` 后，增加：

```js
async function pollTaskStatus(taskId) {
  for (let i = 0; i < 90; i += 1) {
    const task = await getReviewTask(taskId)

    if (task.status === 'SUCCESS') {
      return task
    }

    if (task.status === 'FAILED' || task.status === 'CANCELLED') {
      throw new Error(task.errorMessage || 'AI 分析失败，请稍后重试')
    }

    await sleep(2000)
  }

  throw new Error('AI 分析耗时较长，请稍后刷新报告')
}
```

### 13.3 `RiskItemCard.vue`

需要兼容后端当前字段：

```text
1. 使用 item.riskLevel 展示等级。
2. 使用 item.riskType 展示风险类型。
3. 使用 item.title 展示标题。
4. 使用 item.line 展示代码行号，可为空。
5. confidence 是数字时展示百分比，例如 92%。
6. 没有 item.comment 时，复制内容由 title + description + suggestion 拼接。
7. 没有 item.reason 时不展示原因分析 section。
```

### 13.4 `PrInfoCard.vue`

需要兼容：

```text
1. report.prInfo 为空时不崩溃。
2. sourceBranch / targetBranch 有值时展示。
3. url 有值时可以点击跳转。
4. changedFiles/additions/deletions 缺失时展示 0。
```

### 13.5 `SummaryCard.vue`

需要兼容：

```text
1. summary 为空时展示“暂无 PR 总结”。
2. mainChanges 为空时展示空状态。
3. 不要因为 mainChanges 不是数组导致页面报错。
```

### 13.6 Phase 7 验收

```text
1. 后端返回 Mock report 时页面正常展示。
2. riskItems=[] 时展示“暂无风险建议，可能该 PR 风险较低”。
3. testSuggestions=[] 时展示“暂无测试建议。”。
4. 后端返回错误 Result 时前端展示 message。
5. 网络错误时展示“AI 分析失败，请稍后重试”。
6. 复制建议按钮在没有 comment 字段时仍可复制可读内容。
```

---

## 14. Phase 8：测试补充

### 14.1 后端测试

在 `backend/src/test/java/com/example/aipr/` 下补充测试。

建议测试类：

```text
PromptRendererTest.java
AiReviewOutputParserTest.java
AiReviewServiceTest.java
OpenAiCompatibleClientTest.java
```

必须覆盖：

```text
1. Prompt 渲染包含必要字段。
2. JSON 代码块清洗。
3. 非 JSON 输出报错。
4. riskType / severity 枚举校验。
5. confidence 边界值。
6. AI_API_KEY 缺失。
7. 模型接口非 2xx。
8. ReviewReportService 聚合空风险项。
```

运行：

```bash
cd backend
mvn test
```

### 14.2 前端测试

当前前端没有单元测试框架，至少执行构建验证：

```bash
cd frontend
npm run build
```

手动验证：

```text
1. 正确 PR URL。
2. 错误 PR URL。
3. 后端未启动。
4. 后端返回 code != 0。
5. report.riskItems 为空。
6. report.prInfo 缺失。
7. 复制建议按钮。
8. 移动端宽度下文本不溢出。
```

---

## 15. Phase 9：文档同步

成员 B 修改以下内容时，必须同步文档。

### 15.1 修改 Prompt

更新：

```text
docs/05-Prompt设计.md
```

需要同步：

```text
1. Prompt 变量。
2. 文件级 JSON 输出结构。
3. 风险类型。
4. 风险等级。
5. 置信度和 needHumanCheck 规则。
6. Markdown 代码块清理策略。
```

### 15.2 修改 API 响应字段

更新：

```text
docs/04-接口设计.md
README.md
```

需要同步：

```text
1. /api/review-tasks/{taskId}/report 返回字段。
2. prInfo 结构。
3. riskItems 结构。
4. 错误码和错误 message。
```

### 15.3 修改数据库字段

如果成员 B 配合新增 `review_comment` 字段或 `result_json` 结构，更新：

```text
docs/03-数据库设计.md
```

### 15.4 修改环境变量

如果修改 AI 配置项，更新：

```text
README.md
docs/02-技术设计文档-TDD.md
```

当前应使用：

```text
AI_BASE_URL
AI_API_KEY
AI_MODEL_NAME
AI_TEMPERATURE
```

---

## 16. 详细文件清单

### 16.1 成员 B 后端新增文件

```text
backend/src/main/java/com/example/aipr/config/AiProperties.java

backend/src/main/java/com/example/aipr/service/ai/LlmClient.java
backend/src/main/java/com/example/aipr/service/ai/LlmMessage.java
backend/src/main/java/com/example/aipr/service/ai/LlmRequest.java
backend/src/main/java/com/example/aipr/service/ai/LlmResponse.java
backend/src/main/java/com/example/aipr/service/ai/OpenAiCompatibleClient.java
backend/src/main/java/com/example/aipr/service/ai/AiReviewService.java

backend/src/main/java/com/example/aipr/service/prompt/PromptRenderer.java
backend/src/main/java/com/example/aipr/service/prompt/AiReviewOutputParser.java

backend/src/main/java/com/example/aipr/dto/AiReviewContext.java
backend/src/main/java/com/example/aipr/dto/FileReviewResult.java
backend/src/main/java/com/example/aipr/dto/FileReviewCommentResult.java

backend/src/main/java/com/example/aipr/vo/PrInfoVO.java
```

### 16.2 成员 B 后端修改文件

```text
backend/src/main/java/com/example/aipr/enums/ErrorCode.java
backend/src/main/java/com/example/aipr/vo/ReviewReportVO.java
backend/src/main/java/com/example/aipr/vo/RiskItemVO.java，按需
backend/src/main/java/com/example/aipr/service/report/ReviewReportService.java
backend/src/main/java/com/example/aipr/AiPrReviewBackendApplication.java，按需启用配置属性扫描
backend/src/test/java/com/example/aipr/AiPrReviewBackendApplicationTests.java，按需调整 Mock 报告断言
```

### 16.3 成员 B 前端修改文件

```text
frontend/src/api/review.js
frontend/src/views/HomeView.vue
frontend/src/components/PrInputCard.vue
frontend/src/components/PrInfoCard.vue
frontend/src/components/RiskItemCard.vue
frontend/src/components/SummaryCard.vue，按需
frontend/src/components/TestSuggestionCard.vue，按需
frontend/src/components/FinalReviewCard.vue，按需
```

### 16.4 成员 B 文档修改文件

```text
docs/05-Prompt设计.md
docs/04-接口设计.md，若 report 字段变更
README.md，若接口、启动方式或环境变量变更
docs/02-技术设计文档-TDD.md，若 AI Review 流程变更
```

---

## 17. Phase 10：端到端联调顺序

### 17.1 本地后端

```bash
cd backend
mvn spring-boot:run
```

先验证：

```text
GET http://localhost:8080/api/health
POST http://localhost:8080/api/review-tasks
GET http://localhost:8080/api/review-tasks/1/report
```

### 17.2 本地前端

```bash
cd frontend
npm run dev
```

访问：

```text
http://localhost:5173
```

### 17.3 使用真实 AI 前的检查

```text
1. AI_API_KEY 已通过环境变量配置。
2. application.yml 中没有硬编码真实 key。
3. GITHUB_TOKEN 不由前端传入。
4. 模型返回 JSON 可被解析器处理。
5. 失败时不会把 rawOutput 暴露给前端。
```

### 17.4 端到端流程

```text
1. 输入 GitHub PR URL。
2. 前端调用 POST /api/review-tasks。
3. 后端创建任务。
4. 成员 A 链路获取 PR 信息和 changed files。
5. 成员 B 链路对文件 patch 调用 AI Review。
6. 解析并保存 Review 建议。
7. 后端聚合报告。
8. 前端展示 PR 信息、总结、风险评分、风险项、测试建议和最终结论。
9. 点击复制建议。
10. 刷新页面后仍可通过 taskId 查看报告，前提是成员 A 已完成持久化和详情页/路由。
```

---

## 18. 验收清单

### 18.1 后端验收

| # | 验收项 | 验证方式 |
| --- | --- | --- |
| 1 | PromptRenderer 可生成文件级 Review Prompt | 单元测试 |
| 2 | AiReviewOutputParser 可解析纯 JSON | 单元测试 |
| 3 | AiReviewOutputParser 可解析 Markdown 包裹 JSON | 单元测试 |
| 4 | 非 JSON 输出返回友好错误 | 单元测试 / Mock |
| 5 | OpenAiCompatibleClient 可解析 choices[0].message.content | MockWebServer 或 mock |
| 6 | AI_API_KEY 缺失时不发起请求并返回明确错误 | 单元测试 |
| 7 | AiReviewService 能串联 Prompt、LLM、Parser | Mock LlmClient |
| 8 | ReviewReportService 不再返回固定 Mock 数据 | 接口测试 |
| 9 | `/api/review-tasks/{taskId}/report` 返回统一 Result | MockMvc |
| 10 | 不直接返回 Entity，不返回 raw LLM 输出 | 代码检查 |

### 18.2 前端验收

| # | 验收项 | 验证方式 |
| --- | --- | --- |
| 1 | 不再请求 `/api/reviews/analyze` | 浏览器 Network |
| 2 | 可以创建任务并获取 report | 手动联调 |
| 3 | loading 状态正常 | 手动联调 |
| 4 | 错误 PR URL 提示清晰 | 手动联调 |
| 5 | 后端错误 message 可展示 | 手动联调 |
| 6 | 空风险项有空状态 | Mock / 手动 |
| 7 | 风险项 title、description、suggestion 展示完整 | 手动 |
| 8 | 复制建议可用 | 手动 |
| 9 | 移动端布局不重叠 | 浏览器响应式视图 |
| 10 | `npm run build` 成功 | 构建 |

### 18.3 共同验收

```text
1. 后端 `mvn test` 通过。
2. 前端 `npm run build` 通过。
3. 不提交 `.env`、`application-local.yml`、日志、密钥。
4. 不引入 OAuth、Webhook、RAG、Agent 等非 MVP 功能。
5. README 和核心 docs 与实际接口保持一致。
```

---

## 19. 风险点与处理方案

| 风险 | 表现 | 处理 |
| --- | --- | --- |
| 后端尚无真实 GitHub patch | AI Review 无输入 | 先用 Mock LlmClient 和固定 patch 单测，等待成员 A 链路 |
| 后端尚无数据库实体和 Mapper | 报告无法真实聚合 | 先保留接口契约，聚合逻辑等成员 A 持久化完成后接入 |
| 模型返回 Markdown | JSON 解析失败 | Parser 清理代码块后再解析 |
| 模型输出字段不完整 | 前端展示缺字段 | Parser 默认值兜底，前端也做兼容 |
| 模型输出枚举非法 | 风险标签错误 | Parser 校验 RiskType / Severity |
| AI API Key 缺失 | 模型调用失败 | 后端返回 `AI 服务调用失败，请稍后重试` 或更明确配置错误 |
| 前端接口路径不一致 | 404 | 优先修复 `review.js` |
| 报告没有 prInfo | PR 信息卡为空 | 补 `ReviewReportVO.prInfo` 或前端空状态兼容 |
| 大 patch 超限 | 模型失败或成本过高 | Prompt 前截断并标记 `truncated=true` |
| rawOutput 泄露 | 可能暴露敏感代码 | 不返回前端，日志只记摘要 |

---

## 20. 推荐提交拆分

成员 B 不建议一次提交所有内容。推荐拆分：

```text
1. fix: align frontend review api with backend task endpoints
2. feat: add ai review prompt renderer
3. feat: add ai review output parser
4. feat: add openai compatible llm client
5. feat: add ai review service
6. feat: aggregate review report data
7. fix: improve review report frontend empty and error states
8. test: add ai review parser and prompt tests
9. docs: update prompt and api documents
```

---

## 21. 最小可交付版本

如果时间紧，成员 B 至少需要完成：

```text
1. 前端不再请求不存在的 /api/reviews/analyze。
2. 前端可以通过 /api/review-tasks 和 /report 展示后端报告。
3. PromptRenderer 已实现。
4. AiReviewOutputParser 已实现并有测试。
5. AiReviewService 能用 Mock LlmClient 生成结构化 FileReviewResult。
6. ReviewReportVO 与前端字段对齐。
7. npm run build 通过。
8. mvn test 通过。
9. docs/05-Prompt设计.md 和 docs/04-接口设计.md 与实现一致。
```

最低演示链路：

```text
输入 PR URL
→ 创建任务
→ 获取报告
→ 展示总结、风险评分、风险项、测试建议、最终结论
```

完整 MVP 链路：

```text
输入 PR URL
→ 获取真实 GitHub PR 信息和 diff
→ 调用 AI Review
→ 解析 JSON
→ 保存建议
→ 聚合报告
→ 前端展示
```
