# V1：原始 MVP 版设计方案

## 一、版本定位

V1 是 AI PR Review 助手项目的最小可行版本，目标是先跑通完整主链路：

```text
输入 GitHub PR 链接
→ 获取 PR 信息
→ 获取 PR Diff
→ 调用 AI 模型生成评审结果
→ 保存任务和报告
→ 前端轮询状态
→ 展示 AI Review 报告
```

V1 不追求复杂架构，不做 Skill、不做 Agent、不做复杂评测，重点是证明系统具备基本可用能力。

V1 的核心目标是：

```text
让用户输入一个 GitHub Pull Request 链接后，系统能够自动生成一份 AI 代码评审报告。
```

------

## 二、V1 核心目标

V1 需要完成以下能力：

1. 支持用户输入 GitHub PR 链接；
2. 后端解析 PR URL，提取 owner、repo、prNumber；
3. 后端调用 GitHub API 获取 PR 基本信息；
4. 后端调用 GitHub API 获取 PR 变更文件和 Diff；
5. 后端将评审任务保存到 `review_task`；
6. 后端将变更文件保存到 `review_file`；
7. 后端调用 AI 模型生成评审建议；
8. 后端解析 AI 输出并保存到 `review_comment`；
9. 前端轮询任务状态；
10. 前端展示 Review 报告。

------

## 三、V1 不包含的内容

V1 阶段暂不实现以下能力：

```text
1. Skill 化专项评审
2. ReviewAgent 智能编排
3. Agent 评测闭环
4. Redis 限流
5. 数据库缓存复用
6. 模型 Token 监控
7. GitHub PR 列表工作台
8. 自动提交 GitHub Review 评论
9. 多模型对比
10. LoRA / 微调
```

这些功能放到后续版本中实现：

```text
V2：工程增强
V3：Skill 化
V4：Agent 化
V5：评测闭环
```

------

## 四、V1 总体架构

### 4.1 系统架构

```text
前端 Vue3
  ↓
Spring Boot 后端
  ↓
GitHub API
  ↓
AI 模型 API
  ↓
MySQL 数据库
```

### 4.2 核心模块

```text
V1 后端核心模块
├── ReviewTask 模块
│   ├── 创建评审任务
│   ├── 查询任务状态
│   └── 查询评审报告
│
├── GitHub 模块
│   ├── 解析 PR URL
│   ├── 获取 PR 基本信息
│   └── 获取 PR 变更文件
│
├── AI Review 模块
│   ├── 构造 Prompt
│   ├── 调用 AI 模型
│   ├── 解析 AI 输出
│   └── 生成结构化 Review 结果
│
├── 数据持久化模块
│   ├── review_task
│   ├── review_file
│   └── review_comment
│
└── 前端展示模块
    ├── 输入 PR 链接
    ├── 创建任务
    ├── 轮询任务状态
    └── 展示评审报告
```

------

## 五、V1 核心流程

### 5.1 用户操作流程

```text
1. 用户打开前端页面；
2. 用户输入 GitHub PR 链接；
3. 用户点击“开始分析”；
4. 前端调用后端创建 Review 任务接口；
5. 后端返回 taskId；
6. 前端根据 taskId 轮询任务状态；
7. 后端完成 AI Review 后更新任务状态为 SUCCESS；
8. 前端加载报告并展示。
```

------

### 5.2 后端处理流程

```text
POST /api/review-tasks

1. 接收 prUrl；
2. 解析 PR URL；
3. 创建 review_task，状态为 PENDING；
4. 异步执行 Review 任务；
5. 获取 GitHub PR 信息；
6. 获取 PR changed files；
7. 保存 review_file；
8. 构造 AI Review Prompt；
9. 调用 AI 模型；
10. 解析 AI 输出；
11. 保存 review_comment；
12. 更新 review_task summary、finalReview、riskScore、riskLevel；
13. 更新 review_task.status = SUCCESS。
```

失败时：

```text
任意阶段异常
→ 记录 error_message
→ review_task.status = FAILED
→ 前端展示失败原因
```

------

## 六、任务状态设计

V1 可以先使用简单状态：

```text
PENDING     等待执行
REVIEWING   正在评审
SUCCESS     评审成功
FAILED      评审失败
```

如果希望前端展示更细，可以扩展为：

```text
PENDING        已创建任务
FETCHING_PR    正在获取 PR 信息
PARSING_DIFF   正在解析 Diff
REVIEWING      正在调用 AI Review
SUMMARIZING    正在生成总结
SUCCESS        成功
FAILED         失败
```

V1 建议先使用：

```text
PENDING
REVIEWING
SUCCESS
FAILED
```

后续 V2 / V4 再扩展细粒度状态。

------

## 七、数据库设计

V1 需要三张核心表：

```text
review_task
review_file
review_comment
```

------

### 7.1 review_task 表

用于保存一次 PR Review 任务。

```sql
CREATE TABLE IF NOT EXISTS review_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    pr_url VARCHAR(512) NOT NULL COMMENT 'PR 链接',
    owner_name VARCHAR(100) DEFAULT NULL COMMENT 'GitHub owner',
    repo_name VARCHAR(100) DEFAULT NULL COMMENT 'GitHub repo',
    pr_number INT DEFAULT NULL COMMENT 'PR 编号',

    pr_title VARCHAR(512) DEFAULT NULL COMMENT 'PR 标题',
    pr_author VARCHAR(100) DEFAULT NULL COMMENT 'PR 作者',
    source_branch VARCHAR(255) DEFAULT NULL COMMENT '源分支',
    target_branch VARCHAR(255) DEFAULT NULL COMMENT '目标分支',

    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    risk_score INT DEFAULT NULL COMMENT '风险分数',
    risk_level VARCHAR(32) DEFAULT NULL COMMENT '风险等级',

    summary TEXT DEFAULT NULL COMMENT 'AI 总结',
    final_review TEXT DEFAULT NULL COMMENT '最终评审建议',
    result_json MEDIUMTEXT DEFAULT NULL COMMENT 'AI 原始结构化结果',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_status (status),
    INDEX idx_repo_pr (owner_name, repo_name, pr_number),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Review 任务表';
```

------

### 7.2 review_file 表

用于保存 PR 中每个变更文件。

```sql
CREATE TABLE IF NOT EXISTS review_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',

    file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
    file_status VARCHAR(50) DEFAULT NULL COMMENT '文件状态，如 added/modified/removed',
    language VARCHAR(50) DEFAULT NULL COMMENT '文件语言',

    additions INT DEFAULT 0 COMMENT '新增行数',
    deletions INT DEFAULT 0 COMMENT '删除行数',
    changes INT DEFAULT 0 COMMENT '总变更行数',

    patch MEDIUMTEXT DEFAULT NULL COMMENT 'GitHub diff patch',
    ai_summary TEXT DEFAULT NULL COMMENT '该文件 AI 总结',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_task_id (task_id),
    INDEX idx_file_path (file_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Review 文件表';
```

------

### 7.3 review_comment 表

用于保存 AI 生成的具体 Review 建议。

```sql
CREATE TABLE IF NOT EXISTS review_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',
    file_id BIGINT DEFAULT NULL COMMENT 'Review 文件ID',

    file_path VARCHAR(512) DEFAULT NULL COMMENT '文件路径',
    line_number INT DEFAULT NULL COMMENT '行号',

    risk_type VARCHAR(50) DEFAULT NULL COMMENT '风险类型',
    risk_level VARCHAR(32) DEFAULT NULL COMMENT '风险等级',

    title VARCHAR(255) DEFAULT NULL COMMENT '问题标题',
    description TEXT DEFAULT NULL COMMENT '问题描述',
    reason TEXT DEFAULT NULL COMMENT '原因说明',
    suggestion TEXT DEFAULT NULL COMMENT '修改建议',

    confidence DECIMAL(4,2) DEFAULT NULL COMMENT '置信度',
    need_human_check TINYINT(1) DEFAULT 0 COMMENT '是否需要人工复核',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_task_id (task_id),
    INDEX idx_file_id (file_id),
    INDEX idx_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Review 评论表';
```

------

## 八、后端接口设计

### 8.1 创建 Review 任务

```http
POST /api/review-tasks
```

请求：

```json
{
  "prUrl": "https://github.com/owner/repo/pull/1"
}
```

返回：

```json
{
  "taskId": 1,
  "status": "PENDING"
}
```

说明：

```text
V1 只需要支持 prUrl。
forceRefresh、cached 等字段放到 V2。
```

------

### 8.2 查询任务详情

```http
GET /api/review-tasks/{taskId}
```

返回：

```json
{
  "id": 1,
  "prUrl": "https://github.com/owner/repo/pull/1",
  "ownerName": "owner",
  "repoName": "repo",
  "prNumber": 1,
  "prTitle": "feat: add review service",
  "status": "SUCCESS",
  "riskScore": 38,
  "riskLevel": "MEDIUM",
  "summary": "该 PR 新增了 AI Review 服务。",
  "finalReview": "整体可合并，但建议补充异常处理。",
  "errorMessage": null
}
```

------

### 8.3 查询任务文件列表

```http
GET /api/review-tasks/{taskId}/files
```

返回：

```json
[
  {
    "id": 1,
    "taskId": 1,
    "filePath": "backend/src/main/java/com/example/aipr/service/AiReviewService.java",
    "fileStatus": "modified",
    "language": "Java",
    "additions": 120,
    "deletions": 20,
    "changes": 140,
    "aiSummary": "该文件新增 AI Review 调用和结果解析逻辑。"
  }
]
```

------

### 8.4 查询 Review 建议

```http
GET /api/review-tasks/{taskId}/comments
```

返回：

```json
[
  {
    "id": 1,
    "taskId": 1,
    "filePath": "backend/src/main/java/com/example/aipr/service/AiReviewService.java",
    "lineNumber": 85,
    "riskType": "BUG_RISK",
    "riskLevel": "MEDIUM",
    "title": "AI 响应解析缺少异常处理",
    "description": "当前代码假设 AI 一定返回合法 JSON。",
    "reason": "如果模型返回非 JSON 文本，可能导致任务失败。",
    "suggestion": "建议增加 JSON 解析异常捕获，并将任务状态标记为 FAILED。",
    "confidence": 0.86,
    "needHumanCheck": true
  }
]
```

------

### 8.5 查询完整报告

```http
GET /api/review-tasks/{taskId}/report
```

返回：

```json
{
  "task": {},
  "files": [],
  "comments": []
}
```

说明：

该接口用于前端一次性加载报告详情。

------

## 九、后端模块设计

### 9.1 Controller 层

```text
controller/
├── ReviewTaskController.java
```

职责：

```text
1. 创建 Review 任务；
2. 查询任务状态；
3. 查询文件列表；
4. 查询 Review 评论；
5. 查询完整报告。
```

------

### 9.2 Service 层

```text
service/
├── review/
│   ├── ReviewTaskService.java
│   ├── ReviewTaskExecutor.java
│   └── ReviewReportService.java
│
├── github/
│   ├── GitHubClient.java
│   ├── GitHubPrInfo.java
│   └── GitHubChangedFile.java
│
└── ai/
    ├── AiReviewService.java
    ├── LlmClient.java
    ├── PromptRenderer.java
    └── AiReviewOutputParser.java
```

------

### 9.3 Mapper 层

```text
mapper/
├── ReviewTaskMapper.java
├── ReviewFileMapper.java
└── ReviewCommentMapper.java
```

------

### 9.4 Entity 层

```text
entity/
├── ReviewTask.java
├── ReviewFile.java
└── ReviewComment.java
```

------

### 9.5 DTO / VO

```text
dto/
├── CreateReviewTaskRequest.java
├── ReviewTaskCreatedVO.java
├── FileReviewResult.java
├── FileReviewCommentResult.java
└── ReviewReportVO.java
```

------

## 十、GitHub 模块设计

### 10.1 PR URL 解析

输入：

```text
https://github.com/owner/repo/pull/1
```

解析结果：

```json
{
  "owner": "owner",
  "repo": "repo",
  "pullNumber": 1
}
```

建议新增：

```text
PrUrlParser
ParsedPrUrl
```

------

### 10.2 GitHub API 获取 PR 信息

接口：

```http
GET https://api.github.com/repos/{owner}/{repo}/pulls/{pull_number}
```

需要解析字段：

```text
title
user.login
head.ref
base.ref
html_url
```

V1 可以暂时不处理：

```text
head.sha
base.sha
```

这些放到 V2 数据库缓存时再使用。

------

### 10.3 GitHub API 获取 PR 文件

接口：

```http
GET https://api.github.com/repos/{owner}/{repo}/pulls/{pull_number}/files
```

需要解析字段：

```text
filename
status
additions
deletions
changes
patch
```

------

### 10.4 GitHub Token 配置

`application.yml`：

```yaml
github:
  token: ${GITHUB_TOKEN:}
  api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
  connect-timeout-seconds: ${GITHUB_CONNECT_TIMEOUT_SECONDS:10}
  read-timeout-seconds: ${GITHUB_READ_TIMEOUT_SECONDS:30}
```

请求 GitHub API 时：

```text
如果 token 不为空，携带 Authorization: Bearer xxx。
如果 token 为空，也可以访问公开仓库，但频率限制更低。
```

------

## 十一、AI Review 模块设计

### 11.1 AI 配置

`application.yml`：

```yaml
ai:
  base-url: ${AI_BASE_URL:}
  api-key: ${AI_API_KEY:}
  model-name: ${AI_MODEL_NAME:}
  temperature: ${AI_TEMPERATURE:0.2}
  max-tokens: ${AI_MAX_TOKENS:3000}
```

V1 中可以调用：

```text
MiniMax API
DeepSeek API
本地 vLLM OpenAI-compatible API
```

建议后端抽象为：

```text
LlmClient
```

避免业务代码绑定某个模型供应商。

------

### 11.2 Prompt 输入

Prompt 至少包含：

```text
PR 标题
文件路径
文件语言
变更行数
Diff patch
输出格式要求
```

------

### 11.3 Prompt 示例

```text
你是一个资深代码审查专家，现在需要对 GitHub Pull Request 中的单个文件变更进行代码评审。

请重点关注：
1. 潜在 BUG
2. 安全风险
3. 性能问题
4. 可维护性问题
5. 测试建议

请只基于给定 Diff 分析，不要臆测未出现的代码。

请输出严格 JSON，格式如下：

{
  "filePath": "...",
  "summary": "...",
  "comments": [
    {
      "lineNumber": 10,
      "riskType": "BUG_RISK",
      "riskLevel": "MEDIUM",
      "title": "...",
      "description": "...",
      "reason": "...",
      "suggestion": "...",
      "confidence": 0.85,
      "needHumanCheck": true
    }
  ]
}

风险等级只能使用：
HIGH / MEDIUM / LOW / INFO

风险类型只能使用：
BUG_RISK / SECURITY / PERFORMANCE / MAINTAINABILITY / TEST_RISK / STYLE / INFO

PR 标题：
{{prTitle}}

文件路径：
{{filePath}}

Diff：
{{patch}}
```

------

### 11.4 AI 输出解析

AI 输出需要解析为：

```text
FileReviewResult
├── filePath
├── summary
└── comments
    ├── lineNumber
    ├── riskType
    ├── riskLevel
    ├── title
    ├── description
    ├── reason
    ├── suggestion
    ├── confidence
    └── needHumanCheck
```

解析失败时：

```text
1. 捕获异常；
2. 记录 errorMessage；
3. 将任务标记为 FAILED；
4. 不让后端直接崩溃。
```

------

## 十二、风险评分设计

V1 可以先用简单规则。

### 12.1 简单评分规则

```text
HIGH = 40
MEDIUM = 20
LOW = 8
INFO = 0
```

最终：

```text
riskScore = min(100, 所有 comment 风险分之和)
```

风险等级：

```text
0 - 30   LOW
31 - 60  MEDIUM
61 - 100 HIGH
```

V2 可以再升级为固定评分规则，加入风险类型加分和人工复查加分。

------

## 十三、前端设计

### 13.1 V1 页面目标

V1 前端只需要一个简单页面：

```text
输入 PR 链接
点击开始分析
展示任务状态
展示报告结果
```

------

### 13.2 页面结构

```text
AI PR Review Assistant

[输入 GitHub PR 链接]
[开始分析]

任务状态：
PENDING / REVIEWING / SUCCESS / FAILED

报告区域：
- PR 标题
- 风险等级
- 风险分数
- AI 总结
- 最终建议
- 文件列表
- Review 建议列表
```

------

### 13.3 前端状态

```text
idle        初始状态
creating    正在创建任务
polling     正在轮询任务
success     成功
failed      失败
```

------

### 13.4 前端轮询逻辑

```text
1. 调用 POST /api/review-tasks；
2. 获取 taskId；
3. 每 2 秒调用 GET /api/review-tasks/{taskId}；
4. 如果 status 是 PENDING / REVIEWING，继续轮询；
5. 如果 status 是 SUCCESS，停止轮询并加载 report；
6. 如果 status 是 FAILED，停止轮询并展示 errorMessage。
```

------

### 13.5 前端 API 封装

`src/api/review.js`：

```js
import request from './request'

export function createReviewTask(data) {
  return request.post('/api/review-tasks', data)
}

export function getReviewTask(taskId) {
  return request.get(`/api/review-tasks/${taskId}`)
}

export function getReviewReport(taskId) {
  return request.get(`/api/review-tasks/${taskId}/report`)
}
```

------

## 十四、V1 前端目录建议

```text
frontend/src/
├── api/
│   ├── request.js
│   └── review.js
│
├── views/
│   └── HomeView.vue
│
├── components/
│   ├── ReviewStatusTag.vue
│   ├── RiskLevelTag.vue
│   ├── ReviewSummaryCard.vue
│   └── ReviewCommentList.vue
│
└── router/
    └── index.js
```

------

## 十五、配置文件设计

### 15.1 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: ai-pr-review-backend

  datasource:
    url: ${DB_URL:jdbc:mysql://127.0.0.1:3306/ai_code_review?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:ai_review_user}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath:/mapper/*.xml
  type-aliases-package: com.example.aipr.entity
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

github:
  token: ${GITHUB_TOKEN:}
  api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
  connect-timeout-seconds: ${GITHUB_CONNECT_TIMEOUT_SECONDS:10}
  read-timeout-seconds: ${GITHUB_READ_TIMEOUT_SECONDS:30}

ai:
  base-url: ${AI_BASE_URL:}
  api-key: ${AI_API_KEY:}
  model-name: ${AI_MODEL_NAME:}
  temperature: ${AI_TEMPERATURE:0.2}
  max-tokens: ${AI_MAX_TOKENS:3000}
```

------

## 十六、环境变量示例

PowerShell：

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/ai_code_review?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="ai_review_user"
$env:DB_PASSWORD="你的数据库密码"

$env:GITHUB_TOKEN="你的 GitHub Token"

$env:AI_BASE_URL="你的模型 API Base URL"
$env:AI_API_KEY="你的模型 API Key"
$env:AI_MODEL_NAME="你的模型名称"
$env:AI_TEMPERATURE="0.2"
```

------

## 十七、异常处理设计

V1 需要处理以下异常：

```text
PR URL 格式错误
GitHub PR 不存在
GitHub Token 无效
GitHub API 超时
PR 文件 patch 为空
AI API Key 未配置
AI API 调用失败
AI 输出不是合法 JSON
数据库写入失败
```

统一错误返回：

```json
{
  "code": 500001,
  "message": "AI Review 任务执行失败",
  "data": null
}
```

任务失败时：

```text
review_task.status = FAILED
review_task.error_message = 具体错误摘要
```

前端展示：

```text
任务执行失败：xxx
```

------

## 十八、安全要求

V1 虽然是 MVP，但仍需要注意：

```text
1. 不要在日志中打印 GitHub Token；
2. 不要在日志中打印 AI API Key；
3. 不要把数据库密码写死到配置文件；
4. Prompt 中不要加入无关敏感信息；
5. AI 原始响应如果很长，不要全部打印；
6. PR Diff 可能包含业务代码，日志中不要大量输出。
```

------

## 十九、V1 测试方案

### 19.1 PR URL 解析测试

输入：

```text
https://github.com/owner/repo/pull/1
```

预期：

```text
owner = owner
repo = repo
pullNumber = 1
```

------

### 19.2 GitHub API 测试

测试：

```text
1. 配置 GitHub Token；
2. 输入公开或私有仓库 PR；
3. 确认能获取 PR 标题、作者、分支；
4. 确认能获取 changed files。
```

------

### 19.3 AI Review 测试

测试：

```text
1. 配置 AI_BASE_URL、AI_API_KEY、AI_MODEL_NAME；
2. 提交一个小 PR；
3. 确认 AI 能返回 JSON；
4. 确认 review_comment 正常入库。
```

------

### 19.4 任务状态测试

测试：

```text
1. 创建任务后 status = PENDING；
2. 执行过程中 status = REVIEWING；
3. 成功后 status = SUCCESS；
4. 失败后 status = FAILED；
5. FAILED 时 error_message 不为空。
```

------

### 19.5 前端轮询测试

测试：

```text
1. 输入 PR 链接；
2. 点击开始分析；
3. 页面显示加载状态；
4. 任务成功后停止轮询；
5. 页面展示报告；
6. 任务失败后展示错误信息。
```

------

### 19.6 构建测试

后端：

```bash
mvn clean package -DskipTests
```

前端：

```bash
npm run build
```

------

## 二十、V1 验收标准

V1 完成标准：

```text
1. 可以输入 GitHub PR 链接；
2. 后端可以解析 owner/repo/prNumber；
3. 后端可以获取 PR 信息；
4. 后端可以获取 changed files；
5. 后端可以调用 AI 模型；
6. AI 输出可以解析为结构化评论；
7. review_task / review_file / review_comment 可以入库；
8. 前端可以轮询任务状态；
9. 前端可以展示风险分数、总结和评论；
10. 失败时可以展示错误原因。
```

------

## 二十一、V1 已知限制

V1 存在以下限制：

```text
1. 同一个 PR 多次分析会重复调用 AI；
2. 多次生成报告可能评分不一致；
3. 没有数据库缓存；
4. 没有 Redis 限流；
5. 没有模型 Token 用量监控；
6. 没有 Skill 专项审查；
7. 没有 Agent 执行轨迹；
8. 没有人工反馈和评测闭环；
9. 前端页面偏简单，更像 Demo；
10. 不支持 GitHub PR 列表工作台。
```

这些限制将在 V2-V5 中逐步解决。

------

## 二十二、后续版本演进

```text
V1：原始 MVP
- 跑通 PR Review 主链路

V2：工程增强
- 日志优化
- 固定评分
- 数据库缓存
- Redis 限流
- 模型用量监控
- 配置状态检查

V3：Skill 化
- FrontendReviewSkill
- JavaReviewSkill
- SecurityReviewSkill
- SqlReviewSkill
- SkillResult

V4：Agent 化
- ReviewAgent
- ReviewPlanner
- AgentTrace
- Skill 调度

V5：评测闭环
- AgentEvaluator
- 人工反馈
- 质量指标
- 成本指标
- 后续微调数据沉淀
```

------

## 二十三、Claude Code 编码提示词

```text
你现在是我的 Java Spring Boot + Vue3 项目开发助手。当前项目是 AI PR Review 助手，现在需要实现 V1：原始 MVP 版。

V1 的目标是跑通最小主链路：

用户输入 GitHub PR 链接
→ 后端解析 PR URL
→ 调用 GitHub API 获取 PR 信息
→ 调用 GitHub API 获取 changed files
→ 保存 review_task 和 review_file
→ 调用 AI 模型生成 Review
→ 解析 AI 输出
→ 保存 review_comment
→ 前端轮询任务状态
→ 展示 Review 报告

请按最小可行方案实现，不要做复杂架构，不要实现 Skill、Agent、缓存、限流和评测。

一、数据库

请创建三张表：

1. review_task
字段包括：
- id
- pr_url
- owner_name
- repo_name
- pr_number
- pr_title
- pr_author
- source_branch
- target_branch
- status
- risk_score
- risk_level
- summary
- final_review
- result_json
- error_message
- created_at
- updated_at

2. review_file
字段包括：
- id
- task_id
- file_path
- file_status
- language
- additions
- deletions
- changes
- patch
- ai_summary
- created_at

3. review_comment
字段包括：
- id
- task_id
- file_id
- file_path
- line_number
- risk_type
- risk_level
- title
- description
- reason
- suggestion
- confidence
- need_human_check
- created_at

请同步更新 entity、mapper、XML 和初始化 SQL。

二、后端接口

实现以下接口：

1. POST /api/review-tasks
请求：
{
  "prUrl": "https://github.com/owner/repo/pull/1"
}

返回：
{
  "taskId": 1,
  "status": "PENDING"
}

2. GET /api/review-tasks/{taskId}
查询任务状态和基础信息。

3. GET /api/review-tasks/{taskId}/files
查询任务变更文件列表。

4. GET /api/review-tasks/{taskId}/comments
查询任务 Review 建议。

5. GET /api/review-tasks/{taskId}/report
一次性返回 task、files、comments。

三、GitHub 模块

新增：
- PrUrlParser
- ParsedPrUrl
- GitHubClient
- GitHubPrInfo
- GitHubChangedFile

支持：
1. 解析 PR URL；
2. 获取 PR 基本信息；
3. 获取 PR changed files；
4. 支持 GitHub Token；
5. 不打印 GitHub Token。

GitHub 配置：

github:
  token: ${GITHUB_TOKEN:}
  api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
  connect-timeout-seconds: ${GITHUB_CONNECT_TIMEOUT_SECONDS:10}
  read-timeout-seconds: ${GITHUB_READ_TIMEOUT_SECONDS:30}

四、AI Review 模块

新增：
- AiReviewService
- LlmClient
- PromptRenderer
- AiReviewOutputParser

AI 配置：

ai:
  base-url: ${AI_BASE_URL:}
  api-key: ${AI_API_KEY:}
  model-name: ${AI_MODEL_NAME:}
  temperature: ${AI_TEMPERATURE:0.2}
  max-tokens: ${AI_MAX_TOKENS:3000}

要求：
1. 根据文件 path、language、patch 构造 Prompt；
2. 调用 AI 模型；
3. 要求 AI 输出严格 JSON；
4. 解析为 FileReviewResult；
5. 将 comments 保存到 review_comment；
6. 将 summary 保存到 review_file.ai_summary；
7. 解析失败时任务标记为 FAILED。

五、任务执行流程

创建任务后：
1. review_task.status = PENDING；
2. 异步执行任务；
3. 执行中 status = REVIEWING；
4. 成功后 status = SUCCESS；
5. 失败后 status = FAILED，并保存 error_message。

六、风险评分

V1 使用简单规则：
- HIGH = 40
- MEDIUM = 20
- LOW = 8
- INFO = 0

riskScore = min(100, 所有评论风险分之和)

riskLevel：
0-30 LOW
31-60 MEDIUM
61-100 HIGH

七、前端

使用 Vue3 + Vite + Element Plus。

实现 HomeView：

1. PR 链接输入框；
2. 开始分析按钮；
3. 创建任务；
4. 根据 taskId 轮询任务状态；
5. PENDING / REVIEWING 时展示加载状态；
6. SUCCESS 时加载 report 并展示；
7. FAILED 时展示 errorMessage。

前端 API：
- createReviewTask(data)
- getReviewTask(taskId)
- getReviewReport(taskId)

展示内容：
- PR 标题
- 状态
- riskScore
- riskLevel
- summary
- finalReview
- 文件列表
- Review 建议列表

八、异常处理

需要处理：
1. PR URL 格式错误；
2. GitHub API 调用失败；
3. AI API 调用失败；
4. AI 输出 JSON 解析失败；
5. 数据库写入失败。

失败时：
- review_task.status = FAILED；
- review_task.error_message 保存简短错误；
- 前端展示失败原因。

九、安全要求

1. 不要打印 GitHub Token；
2. 不要打印 AI API Key；
3. 不要把数据库密码写死到配置文件；
4. 不要在日志中打印完整 Diff；
5. 不要在日志中打印完整 AI Response。

十、测试

请给出测试步骤：
1. 执行初始化 SQL；
2. 配置数据库环境变量；
3. 配置 GitHub Token；
4. 配置 AI API；
5. 启动后端；
6. 启动前端；
7. 输入一个真实 PR 链接；
8. 确认 review_task 入库；
9. 确认 review_file 入库；
10. 确认 review_comment 入库；
11. 确认前端报告展示正常；
12. 执行 mvn clean package -DskipTests；
13. 执行 npm run build。

十一、输出要求

完成后输出：
1. 修改文件清单；
2. 数据库 SQL；
3. 新增接口说明；
4. 后端模块说明；
5. 前端页面说明；
6. 测试步骤；
7. 已知限制。

请只实现 V1 MVP，不要实现 V2/V3/V4/V5 功能。
```

------

## 二十四、总结

V1 的核心就是：

```text
跑通 AI PR Review 最小主链路。
```

最终效果：

```text
1. 用户能输入 PR 链接；
2. 系统能获取 GitHub PR Diff；
3. 系统能调用 AI 生成 Review；
4. 结果能保存到数据库；
5. 前端能展示评审报告。
```

V1 不追求复杂，但必须保证主链路完整。后续所有 V2 工程增强、V3 Skill、V4 Agent、V5 评测，都是建立在 V1 主链路稳定的基础上。