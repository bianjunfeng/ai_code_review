# AGENTS.md

## 1. 项目说明

本仓库是 **AI PR Review 助手** 项目，用于开发一个 AI 辅助代码评审平台。

项目核心目标：

```text
用户输入 GitHub Pull Request 链接
→ 系统解析 PR 地址
→ 系统获取 PR 基本信息和 changed files
→ 系统获取 diff patch
→ 系统调用大模型进行代码评审
→ 系统生成 PR 变更总结、风险代码识别和 Review 建议
→ 前端展示结构化 Review 报告
```

本项目为两人短周期协作项目，优先完成 MVP 核心链路，不追求复杂企业级架构。

------

## 2. Codex 总体约束

Codex / AI 编码工具修改本仓库时，必须遵守以下规则：

```text
1. 不要重构整个项目，除非用户明确要求。
2. 不要引入不必要的复杂架构。
3. 不要新增 OAuth、Webhook、RAG、Agent、多模型投票、微服务、Kubernetes，除非用户明确要求。
4. 不要修改已有 API 响应格式，除非用户明确要求。
5. 不要在代码中写死 GitHub Token、AI API Key、数据库密码或任何密钥。
6. 不要直接把数据库 Entity 返回给前端，必须使用 VO。
7. 不要为了完成一个任务而修改无关模块。
8. 所有代码应保持简单、清晰、可在两人 MVP 项目中落地。
9. 修改代码后，必须说明改了哪些文件，以及如何测试。
10. 如果需求不明确，优先遵守当前文档和已有代码结构。
```

------

## 3. 技术栈

### 3.1 后端

```text
Java 17
Spring Boot 3
Spring Web
MyBatis-Plus
MySQL 8
Jackson
Lombok
OkHttp 或 WebClient
Spring Async，可选
```

### 3.2 前端

```text
Vue 3
Vite
Element Plus
Axios
Vue Router
Pinia，可选
```

### 3.3 AI 模型接入

后端通过 **OpenAI Compatible API** 风格调用大模型。

可支持：

```text
DeepSeek
Qwen
OpenAI
其他兼容 OpenAI API 的模型服务
```

前端禁止直接调用大模型 API。

------

## 4. 仓库结构约束

项目根目录建议结构：

```text
ai-pr-review/
├── AGENTS.md
├── README.md
├── backend/
├── frontend/
└── docs/
    ├── 01-产品需求文档-PRD.md
    ├── 02-技术设计文档-TDD.md
    ├── 03-数据库设计.md
    ├── 04-接口设计.md
    ├── 05-Prompt设计.md
    ├── 06-开发计划.md
    ├── 07-成员分工与协作计划.md
    └── 规范/
        └── 项目开发协作规范.md
```

除非用户明确要求，否则不要新建额外顶层目录。

------

## 5. 后端包结构约束

后端包结构应遵循：

```text
backend/src/main/java/com/example/aipr
├── common
├── config
├── controller
├── service
│   ├── github
│   ├── review
│   ├── ai
│   ├── prompt
│   └── report
├── domain
├── dto
├── vo
├── mapper
└── enums
```

### 5.1 各包职责

| 包名           | 职责                                 |
| -------------- | ------------------------------------ |
| common         | 通用返回、异常、工具类               |
| config         | 配置类                               |
| controller     | REST API 控制器                      |
| service.github | GitHub PR URL 解析和 GitHub API 调用 |
| service.review | Review 任务创建、执行和状态流转      |
| service.ai     | LLM Client 和模型调用                |
| service.prompt | Prompt 渲染和模板逻辑                |
| service.report | Review 报告聚合                      |
| domain         | 数据库实体                           |
| dto            | 请求参数对象                         |
| vo             | 响应结果对象                         |
| mapper         | MyBatis-Plus Mapper                  |
| enums          | 业务枚举                             |

------

## 6. 前端目录结构约束

前端目录应遵循：

```text
frontend/src
├── api
├── views
├── components
├── router
├── utils
└── assets
```

### 6.1 目录职责

| 目录       | 职责            |
| ---------- | --------------- |
| api        | Axios 请求封装  |
| views      | 页面级 Vue 组件 |
| components | 可复用组件      |
| router     | Vue Router 配置 |
| utils      | 工具函数        |
| assets     | 静态资源        |

推荐页面：

```text
HomeView.vue
ReviewTaskDetail.vue
HistoryView.vue，可选
```

推荐组件：

```text
PrInfoCard.vue
TaskStatusTag.vue
RiskOverviewCard.vue
RiskTag.vue
ReviewCommentCard.vue
```

------

## 7. MVP 范围约束

MVP 必须优先实现：

```text
1. 输入 GitHub PR URL。
2. 解析 owner、repo、pullNumber。
3. 获取 GitHub PR 基本信息。
4. 获取 changed files 和 diff patch。
5. 创建 Review 任务。
6. 调用大模型进行代码评审。
7. 解析大模型 JSON 输出。
8. 保存 Review 建议。
9. 前端展示 Review 报告。
```

除非用户明确要求，否则不要实现：

```text
GitHub OAuth 登录
GitHub Webhook
自动评论到 GitHub PR
完整 Skill 平台
RAG
Agent
多模型投票
复杂权限系统
复杂管理后台
完整 CI/CD 平台
微服务架构
Kubernetes 部署
```

这些功能只能写入未来扩展方向。

------

## 8. 统一接口返回格式

所有后端接口必须返回统一格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败响应格式：

```json
{
  "code": 40001,
  "message": "PR 链接格式错误",
  "data": null
}
```

禁止 Controller 直接返回：

```text
字符串
原始异常
数据库 Entity
未包装的 Map
未包装的 List
```

------

## 9. API 设计约束

接口使用 REST 风格。

推荐核心接口：

```http
POST /api/github/preview
POST /api/review-tasks
GET /api/review-tasks/{taskId}
GET /api/review-tasks/{taskId}/files
GET /api/review-tasks/{taskId}/comments
GET /api/review-tasks/{taskId}/report
POST /api/review-tasks/{taskId}/rerun
```

请求和响应字段使用 camelCase。

请求示例：

```json
{
  "prUrl": "https://github.com/example/demo/pull/12"
}
```

响应 data 示例：

```json
{
  "taskId": 1,
  "status": "PENDING"
}
```

如果新增或修改接口，必须同步更新：

```text
docs/04-接口设计.md
README.md 中的核心接口说明
```

------

## 10. GitHub PR URL 解析规则

支持的 GitHub PR URL 格式：

```text
https://github.com/{owner}/{repo}/pull/{pullNumber}
```

示例：

```text
https://github.com/example/demo/pull/12
```

解析结果：

```json
{
  "owner": "example",
  "repo": "demo",
  "pullNumber": 12
}
```

错误链接必须返回明确业务异常：

```text
PR 链接格式错误，请输入 GitHub Pull Request 地址
```

------

## 11. GitHub API 规则

使用 GitHub REST API。

必须支持：

```http
GET https://api.github.com/repos/{owner}/{repo}/pulls/{pull_number}
GET https://api.github.com/repos/{owner}/{repo}/pulls/{pull_number}/files
```

请求头：

```text
Authorization: Bearer ${GITHUB_TOKEN}
Accept: application/vnd.github+json
```

需要解析的 PR 字段：

```text
title
body
user.login
head.ref
base.ref
state
additions
deletions
changed_files
```

需要解析的 changed file 字段：

```text
filename
status
additions
deletions
changes
patch
```

必须处理以下情况：

```text
401：GitHub Token 无效
403：无权限或 API 限流
404：仓库或 PR 不存在
422：PR 编号无效
5xx：GitHub 服务异常
```

------

## 12. 数据库设计规则

数据库表名和字段名使用小写下划线。

核心表：

```text
review_task
review_file
review_comment
model_config，可选
prompt_template，可选
review_skill，可选，未来扩展
```

每张核心表统一使用：

```sql
id BIGINT PRIMARY KEY AUTO_INCREMENT
```

推荐时间字段：

```sql
created_at DATETIME DEFAULT CURRENT_TIMESTAMP
updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

禁止直接将数据库 Entity 返回给前端，必须转换为 VO。

------

## 13. Review 任务状态规则

Review 任务状态只能使用以下值：

```text
PENDING
FETCHING_PR
PARSING_DIFF
REVIEWING
SUMMARIZING
SCORING
SUCCESS
FAILED
CANCELLED
```

状态流转：

```text
PENDING
→ FETCHING_PR
→ PARSING_DIFF
→ REVIEWING
→ SUMMARIZING
→ SCORING
→ SUCCESS
```

失败时：

```text
任意未完成状态 → FAILED
```

不要随意新增状态值。新增状态必须同步修改：

```text
后端枚举
接口文档
前端状态展示
```

------

## 14. 风险类型规则

AI Review 风险类型只能使用以下枚举：

```text
BUG_RISK
SECURITY_RISK
PERFORMANCE_RISK
MAINTAINABILITY
STYLE
TEST_RISK
COMPATIBILITY
```

不要随意新增风险类型。新增类型必须同步修改：

```text
Prompt
后端枚举
前端展示
docs/05-Prompt设计.md
```

------

## 15. 风险等级规则

AI Review 风险等级只能使用以下值：

```text
HIGH
MEDIUM
LOW
INFO
```

含义：

| Severity | 含义                     |
| -------- | ------------------------ |
| HIGH     | 高风险，建议必须修改     |
| MEDIUM   | 中风险，建议优先检查     |
| LOW      | 低风险，可选优化         |
| INFO     | 提示信息，不一定需要修改 |

------

## 16. LLM 调用规则

所有大模型调用必须通过统一接口：

```text
LlmClient
OpenAiCompatibleClient
```

禁止在 Controller 中直接调用模型 API。

推荐配置项：

```text
AI_BASE_URL
AI_API_KEY
AI_MODEL_NAME
AI_TEMPERATURE
```

代码评审任务推荐：

```text
AI_TEMPERATURE=0.2
```

禁止硬编码 API Key。

禁止把 API Key 返回给前端。

------

## 17. Prompt 规则

Prompt 文档存放在：

```text
docs/05-Prompt设计.md
```

如果代码中实现 Prompt 渲染，使用：

```text
PromptRenderer
```

Prompt 变量使用双大括号：

```text
{{prTitle}}
{{prDescription}}
{{filePath}}
{{fileStatus}}
{{language}}
{{patch}}
```

Prompt 必须要求模型：

```text
1. 只基于提供的 PR 信息和 diff 分析。
2. 不要编造未提供的业务背景。
3. 不确定时设置 needHumanCheck=true。
4. 每条建议必须包含 confidence。
5. 不要输出泛泛而谈的建议。
6. 只输出合法 JSON。
7. 不要输出 Markdown。
```

------

## 18. AI Review JSON 输出契约

文件级 AI Review 输出必须遵守以下结构：

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

字段规则：

```text
filePath: string
summary: string
comments: array
line: number or null
riskType: allowed risk type
severity: allowed severity
title: string
description: string
suggestion: string
confidence: number between 0 and 1
needHumanCheck: boolean
```

如果模型返回 Markdown 包裹 JSON，例如：

~~~text
```json
{...}
```
~~~

必须先清理代码块标记，再解析 JSON。

如果 JSON 解析失败：

```text
1. 记录模型返回内容摘要。
2. 将任务或文件 Review 标记为失败。
3. 返回友好错误：模型返回格式异常，请重新评审。
```

------

## 19. Diff 处理规则

默认跳过以下文件：

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

MVP 推荐限制：

```text
单文件 patch 最大长度：12000 characters
单次模型输入最大长度：20000 characters
单个 PR 最大 Review 文件数：30
单个 PR 最大总 patch 长度：100000 characters
```

如果 patch 过大：

```text
1. 优先截断或按 diff hunk 切分。
2. 在结果中标记“仅分析部分 diff”。
3. 不允许让任务直接崩溃。
```

------

## 20. 后端编码规则

### 20.1 Controller 规则

Controller 只负责：

```text
接收请求
参数校验
调用 Service
返回 Result<T>
```

Controller 禁止包含：

```text
GitHub API 实现细节
LLM API 调用细节
复杂数据库逻辑
Prompt 拼接逻辑
JSON 解析逻辑
```

### 20.2 Service 规则

业务逻辑放在 Service。

推荐 Service：

```text
GitHubPullRequestService
ReviewTaskService
ReviewTaskExecutor
AiReviewService
PromptRenderer
ReviewReportService
```

### 20.3 DTO / VO / Entity 规则

```text
DTO：请求参数对象
VO：响应对象
Entity / Domain：数据库映射对象
```

禁止直接返回 Entity 给前端。

------

## 21. 前端编码规则

所有 API 请求放在：

```text
frontend/src/api/
```

禁止在 Vue template 中写复杂请求逻辑。

页面必须处理：

```text
loading 状态
success 状态
error 状态
empty 状态
```

用户可见错误信息必须清晰。

推荐：

```text
PR 链接格式错误，请输入 GitHub Pull Request 地址
AI 分析失败，请稍后重试
GitHub 仓库无权限，请检查 Token 或仓库访问权限
暂无 Review 建议，可能该 PR 风险较低
```

不推荐：

```text
error
undefined
500
Network Error
```

------

## 22. 环境变量规则

统一使用以下环境变量：

```text
GITHUB_TOKEN
AI_BASE_URL
AI_API_KEY
AI_MODEL_NAME
AI_TEMPERATURE
MYSQL_HOST
MYSQL_PORT
MYSQL_DATABASE
MYSQL_USERNAME
MYSQL_PASSWORD
```

本地私有配置文件：

```text
application-local.yml
.env
```

不得提交到 Git。

------

## 23. 安全规则

禁止提交：

```text
GitHub Token
AI API Key
数据库密码
服务器密码
SSH 私钥
.env
application-local.yml
```

`.gitignore` 必须包含：

```text
.env
*.env
application-local.yml
target/
node_modules/
dist/
.idea/
.vscode/
*.log
```

禁止在日志中打印密钥。

如果调试时必须显示，应脱敏：

```text
ghp_abcd****1234
sk-****abcd
```

------

## 24. 日志规则

使用规范日志，不要保留临时打印。

避免提交：

```text
System.out.println
console.log 调试输出
临时测试接口
```

必须记录的日志点：

```text
任务创建
PR URL 解析结果
GitHub API 调用开始和结束
changed files 数量
LLM 调用开始和结束
LLM JSON 解析结果
任务最终状态
异常原因
```

禁止日志记录：

```text
GitHub Token
AI API Key
数据库密码
完整密钥
```

------

## 25. 测试规则

功能完成前至少测试：

```text
1. 正确 GitHub PR URL。
2. 错误 PR URL。
3. GitHub Token 无效或缺失。
4. PR 不存在。
5. changed files 为空。
6. AI API Key 无效或缺失。
7. LLM 返回非 JSON。
8. Review 报告页刷新。
```

最小端到端测试流程：

```text
1. 启动后端。
2. 启动前端。
3. 输入测试 PR URL。
4. 创建 Review 任务。
5. 获取 PR 信息和 diff。
6. 调用 LLM。
7. 保存 Review 建议。
8. 展示 Review 报告。
9. 刷新页面后确认报告仍可查看。
```

------

## 26. 本地运行命令

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

如果测试命令存在：

```bash
cd backend
mvn test
cd frontend
npm run build
```

最终交付前至少运行：

```bash
cd backend
mvn test
```

以及：

```bash
cd frontend
npm run build
```

如果命令暂不可用，必须在回复中明确说明。

------

## 27. 文档更新规则

如果修改行为、接口、数据库或 Prompt，必须更新对应文档。

重要文档：

```text
README.md
docs/02-技术设计文档-TDD.md
docs/03-数据库设计.md
docs/04-接口设计.md
docs/05-Prompt设计.md
docs/规范/项目开发协作规范.md
```

以下变化必须更新文档：

```text
API path
Request fields
Response fields
Database table
Prompt output JSON
Risk type
Severity value
Environment variable
Startup command
```

------

## 28. Git 与提交规则

使用 feature 分支开发。

推荐分支：

```text
main
dev
feature/pr-task-chain
feature/ai-report-chain
```

Commit message 格式：

```text
type: message
```

允许类型：

```text
feat
fix
docs
refactor
test
chore
style
perf
```

示例：

```text
feat: add github pr preview api
feat: add ai review prompt renderer
fix: handle github api unauthorized error
docs: update api design document
chore: add gitignore config
```

不要提交构建产物或密钥。

------

## 29. Pull Request / Merge 规则

合并到 `dev` 或 `main` 前必须确认：

```text
1. 后端可以启动。
2. 前端可以启动。
3. 核心 API 可以调用。
4. 没有提交密钥。
5. 没有修改无关文件。
6. 必要文档已更新。
7. 修改功能已手动测试。
```

如果发生冲突：

```text
1. 由受影响模块负责人优先解决。
2. 无法判断时共同解决。
3. 解决后必须重新启动并测试项目。
```

------

## 30. 演示规则

演示前准备：

```text
稳定可用的测试 PR URL
GitHub Token
AI API Key
初始化后的数据库
可运行的后端
可运行的前端
备用截图
README.md
```

固定演示流程：

```text
1. 打开前端首页。
2. 输入 GitHub PR URL。
3. 点击开始评审。
4. 展示任务状态。
5. 展示 PR 基本信息。
6. 等待 AI Review。
7. 展示 AI 总结。
8. 展示风险统计。
9. 展示 Review 建议。
10. 说明误报和漏报控制。
11. 说明未来扩展：Skill、RAG、Agent、Webhook。
```

演示前不要临时大改代码、换模型、换 PR 或改 Prompt。

------

## 31. 禁止事项

除非用户明确要求，否则禁止：

```text
重构整个项目结构
替换 Spring Boot
替换 Vue
引入微服务
引入 Kubernetes
引入 OAuth
引入 Webhook
引入完整 RAG
引入 Agent 工作流
引入完整 Skill 管理平台
引入复杂用户权限系统
提交密钥
修改 API 契约但不更新文档
直接将原始 LLM 输出返回前端
忽略 JSON 解析错误
```

------

## 32. 最终交付标准

最低可接受交付：

```text
1. 用户可以输入 GitHub PR URL。
2. 后端可以解析 PR URL。
3. 后端可以获取 PR 基本信息和 changed files。
4. 后端可以调用大模型。
5. 后端可以生成结构化 Review 结果。
6. 前端可以展示 PR 总结、风险统计和 Review 建议。
7. README 和核心文档可用。
```

最重要的 MVP 链路：

```text
Input PR URL
→ Fetch PR Diff
→ Call AI Review
→ Generate Report
→ Display on Page
```

所有代码修改都应优先服务这条核心链路。