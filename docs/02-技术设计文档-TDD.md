# AI PR Review 助手技术设计文档 TDD

## 1. 文档说明

本文档用于描述“AI PR Review 助手”的技术设计方案，包括系统架构、技术选型、核心模块、数据库设计、接口设计、AI Review 流程、Prompt 设计、Skill 扩展机制、部署方案和后续扩展方向。

本项目目标是开发一个 AI 代码评审工具，用户指定 GitHub Pull Request 后，系统自动获取代码变更并调用大模型进行智能分析，生成 PR 变更总结、风险代码识别和 Review 建议，帮助开发者提升代码评审效率与质量。

------

# 2. 项目目标

## 2.1 核心目标

系统需要实现以下核心能力：

1. 用户输入 GitHub PR 链接。
2. 系统自动解析 PR 地址，提取 owner、repo、pullNumber。
3. 系统调用 GitHub API 获取 PR 基本信息。
4. 系统获取 PR changed files 和 diff patch。
5. 系统对 diff 进行过滤、解析和切分。
6. 系统调用大模型进行代码评审。
7. 系统生成 PR 变更总结、风险代码识别和 Review 建议。
8. 系统将 Review 结果保存到数据库。
9. 前端页面展示结构化 Review 报告。
10. 系统预留 Skill、RAG、Agent、Webhook、CI/CD 等扩展能力。

## 2.2 MVP 目标

MVP 阶段只实现最核心链路：

```text
输入 GitHub PR 链接
→ 获取 PR 信息
→ 获取 PR 代码变更
→ 调用大模型分析
→ 生成 Review 报告
→ 页面展示
→ 保存历史记录
```

## 2.3 非 MVP 功能

以下能力不放在第一版，但在架构上预留扩展点：

1. GitHub OAuth 登录。
2. GitHub Webhook 自动触发 Review。
3. 自动评论到 GitHub PR。
4. 仓库级 RAG 上下文检索。
5. Agent 自动选择相关文件。
6. 多模型评审和投票机制。
7. 团队自定义 Review 规则。
8. 完整 Skill 平台。
9. CI/CD 强制质量门禁。
10. 企业级多租户权限管理。

------

# 3. 技术选型

## 3.1 后端技术栈

| 技术                                  | 用途                       | 是否 MVP 必须 |
| ------------------------------------- | -------------------------- | ------------- |
| Java 17                               | 后端开发语言               | 是            |
| Spring Boot 3                         | 后端主框架                 | 是            |
| Spring Web                            | REST API 开发              | 是            |
| MyBatis-Plus                          | 数据库访问                 | 是            |
| MySQL 8                               | 存储任务、文件、评论、配置 | 是            |
| Redis                                 | 缓存、任务状态、限流       | 可选          |
| Spring Async / ThreadPoolTaskExecutor | 异步执行 Review 任务       | 是            |
| OkHttp / WebClient                    | 调用 GitHub API 和模型 API | 是            |
| Jackson                               | JSON 序列化与反序列化      | 是            |
| Lombok                                | 简化 Java 实体类代码       | 可选          |
| Knife4j / Swagger                     | 接口文档                   | 可选          |
| Docker                                | 容器化部署                 | 建议          |
| Nginx                                 | 前端部署和反向代理         | 建议          |

## 3.2 前端技术栈

| 技术         | 用途         | 是否 MVP 必须 |
| ------------ | ------------ | ------------- |
| Vue 3        | 前端框架     | 是            |
| Vite         | 前端构建工具 | 是            |
| Element Plus | UI 组件库    | 是            |
| Axios        | HTTP 请求    | 是            |
| Vue Router   | 页面路由     | 是            |
| Pinia        | 状态管理     | 可选          |

## 3.3 AI 能力技术选型

| 技术 / 模块            | 用途                                        | 是否 MVP 必须 |
| ---------------------- | ------------------------------------------- | ------------- |
| OpenAI Compatible API  | 统一接入 DeepSeek、Qwen、OpenAI 等模型      | 是            |
| Prompt Template        | 管理不同 Review 场景的提示词模板            | 是            |
| Review Pipeline        | 编排 PR 获取、Diff 解析、模型调用、结果汇总 | 是            |
| Structured JSON Output | 让模型输出结构化结果，便于解析和展示        | 是            |
| Skill Engine           | 可插拔专项代码评审能力                      | 二期扩展      |
| RAG                    | 检索仓库上下文、README、接口文档            | 后期扩展      |
| Agent                  | 自动选择工具、检索上下文、执行多步分析      | 后期扩展      |

## 3.4 Skill 定位说明

本项目可以在技术选型中加入 Skill，但不建议作为 MVP 必做功能。

Skill 在本系统中表示一种“可插拔专项代码评审能力”，例如：

1. Java 后端代码审查 Skill。
2. SQL 安全审查 Skill。
3. Spring Boot 最佳实践 Skill。
4. 前端代码审查 Skill。
5. 安全漏洞审查 Skill。
6. 性能优化审查 Skill。
7. 单元测试建议 Skill。
8. 代码风格规范 Skill。

MVP 阶段使用固定 Prompt 完成基础 Review 流程；二期将不同 Prompt 和评审策略抽象为 Skill，由 Skill Router 根据文件类型、语言和风险类型自动选择对应 Skill。

------

# 4. 系统总体架构

## 4.1 MVP 架构

```text
┌──────────────────────────┐
│        前端 Vue3          │
│ PR 输入 / 报告展示 / 配置 │
└─────────────┬────────────┘
              │ HTTP
┌─────────────▼────────────┐
│      Spring Boot API      │
│ Review Controller         │
└─────────────┬────────────┘
              │
┌─────────────▼────────────┐
│    Review Task Service    │
│ 创建任务 / 状态流转 / 编排 │
└─────────────┬────────────┘
              │
      ┌───────┴────────┐
      │                │
┌─────▼──────┐  ┌──────▼────────┐
│GitHub服务  │  │ AI Review服务  │
│获取PR/Diff │  │ 调用大模型分析 │
└─────┬──────┘  └──────┬────────┘
      │                │
      └───────┬────────┘
              │
┌─────────────▼────────────┐
│          MySQL            │
│ 任务 / 文件 / 评论 / 配置 │
└──────────────────────────┘
```

## 4.2 加入 Skill 后的扩展架构

```text
前端 Vue3
  ↓
Spring Boot API
  ↓
Review Task Service
  ↓
Review Pipeline
  ↓
Skill Router
  ↓
不同 Review Skill
  ├── General Review Skill
  ├── Java Review Skill
  ├── SQL Review Skill
  ├── Security Review Skill
  ├── Performance Review Skill
  ├── Test Suggestion Skill
  └── Frontend Review Skill
  ↓
LLM Client
  ↓
大模型 API
```

## 4.3 架构设计原则

1. MVP 阶段采用单体架构，降低开发复杂度。
2. AI 模型调用统一封装，便于切换不同模型。
3. Prompt 模板配置化，便于后续优化 Review 效果。
4. Review 流程使用 Pipeline 思路，便于后续加入 Skill、RAG、Agent。
5. 文件级 Review 与 PR 级汇总分离，提高分析准确性。
6. 异步任务执行，避免用户长时间等待。
7. 数据库保存完整 Review 记录，便于历史查询和效果分析。

------

# 5. 后端模块设计

## 5.1 项目目录结构

```text
com.example.aipr
├── common
│   ├── Result.java
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
│
├── config
│   ├── AsyncConfig.java
│   ├── WebConfig.java
│   └── ModelConfig.java
│
├── controller
│   ├── ReviewTaskController.java
│   ├── ModelConfigController.java
│   ├── PromptTemplateController.java
│   └── ReviewSkillController.java
│
├── service
│   ├── review
│   │   ├── ReviewTaskService.java
│   │   ├── ReviewTaskExecutor.java
│   │   ├── ReviewPipeline.java
│   │   └── ReviewResultAggregator.java
│   │
│   ├── github
│   │   ├── GitHubUrlParser.java
│   │   ├── GitHubClient.java
│   │   └── GitHubPullRequestService.java
│   │
│   ├── diff
│   │   ├── DiffParser.java
│   │   ├── DiffChunker.java
│   │   └── DiffFileFilter.java
│   │
│   ├── ai
│   │   ├── LlmClient.java
│   │   ├── OpenAiCompatibleClient.java
│   │   ├── LlmRequest.java
│   │   └── LlmResponse.java
│   │
│   ├── prompt
│   │   ├── PromptTemplateService.java
│   │   └── PromptRenderer.java
│   │
│   └── skill
│       ├── ReviewSkill.java
│       ├── SkillRouter.java
│       ├── GeneralReviewSkill.java
│       ├── JavaReviewSkill.java
│       ├── SqlReviewSkill.java
│       └── SecurityReviewSkill.java
│
├── domain
│   ├── ReviewTask.java
│   ├── ReviewFile.java
│   ├── ReviewComment.java
│   ├── ModelConfig.java
│   ├── PromptTemplate.java
│   └── ReviewSkillConfig.java
│
├── mapper
│   ├── ReviewTaskMapper.java
│   ├── ReviewFileMapper.java
│   ├── ReviewCommentMapper.java
│   ├── ModelConfigMapper.java
│   ├── PromptTemplateMapper.java
│   └── ReviewSkillConfigMapper.java
│
├── dto
│   ├── CreateReviewTaskRequest.java
│   ├── ReviewTaskDetailVO.java
│   ├── ReviewFileVO.java
│   ├── ReviewCommentVO.java
│   └── ModelConfigRequest.java
│
└── enums
    ├── ReviewTaskStatusEnum.java
    ├── RiskTypeEnum.java
    ├── SeverityEnum.java
    └── FileStatusEnum.java
```

------

# 6. 核心业务流程

## 6.1 创建 Review 任务流程

```text
1. 用户输入 GitHub PR 链接
2. 后端校验 PR URL 格式
3. 解析 owner、repo、pullNumber
4. 创建 review_task 记录，状态为 PENDING
5. 异步执行 Review 任务
6. 返回 taskId 给前端
7. 前端轮询任务状态
```

## 6.2 Review 任务执行流程

```text
1. 更新任务状态为 FETCHING_PR
2. 调用 GitHub API 获取 PR 基本信息
3. 调用 GitHub API 获取 changed files
4. 保存 PR 文件信息到 review_file 表
5. 更新任务状态为 PARSING_DIFF
6. 过滤不需要分析的文件
7. 对超长 diff 进行切分
8. 更新任务状态为 REVIEWING
9. 对每个文件调用 AI 进行文件级 Review
10. 保存文件级 Review 总结和评论
11. 更新任务状态为 SUMMARIZING
12. 汇总所有文件级结果，生成 PR 总结
13. 统计高、中、低风险数量
14. 更新任务状态为 SCORING
15. 计算风险评分和风险等级
16. 更新任务状态为 SUCCESS
17. 如果任一步骤失败，更新任务状态为 FAILED
```

当前 dev 状态：后端已经具备文件级并发 Review、单文件超时、缓存复用和 `SCORING` 状态；但 PR 信息获取和 Diff 获取仍在创建任务接口中同步完成，`FETCHING_PR`、`PARSING_DIFF` 尚未真正作为异步状态落地。后续优化评审耗时时，应优先把这两步纳入任务状态流。

## 6.3 伪代码

```java
public Long createReviewTask(CreateReviewTaskRequest request) {
    GitHubPrInfo prInfo = gitHubUrlParser.parse(request.getPrUrl());

    ReviewTask task = new ReviewTask();
    task.setPrUrl(request.getPrUrl());
    task.setOwner(prInfo.getOwner());
    task.setRepo(prInfo.getRepo());
    task.setPullNumber(prInfo.getPullNumber());
    task.setStatus("PENDING");
    reviewTaskMapper.insert(task);

    reviewTaskExecutor.executeAsync(task.getId());

    return task.getId();
}
@Async("reviewAsyncExecutor")
public void executeAsync(Long taskId) {
    try {
        updateStatus(taskId, "FETCHING_PR");

        PullRequestInfo prInfo = gitHubService.getPullRequest(taskId);
        List<ChangedFile> files = gitHubService.getChangedFiles(taskId);

        updateStatus(taskId, "PARSING_DIFF");

        List<ReviewFile> reviewFiles = diffParser.parseAndFilter(files);
        saveReviewFiles(taskId, reviewFiles);

        updateStatus(taskId, "REVIEWING");

        for (ReviewFile file : reviewFiles) {
            if (file.isSkipped()) {
                continue;
            }
            FileReviewResult result = aiReviewService.reviewFile(prInfo, file);
            saveFileReviewResult(taskId, file, result);
        }

        updateStatus(taskId, "SUMMARIZING");

        FinalReviewResult finalResult = aiReviewService.summarize(taskId);
        saveFinalResult(taskId, finalResult);

        updateStatus(taskId, "SUCCESS");
    } catch (Exception e) {
        markFailed(taskId, e.getMessage());
    }
}
```

------

# 7. GitHub API 对接设计

## 7.1 PR URL 解析

支持格式：

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

## 7.2 需要调用的 GitHub API

### 获取 PR 基本信息

```http
GET /repos/{owner}/{repo}/pulls/{pull_number}
```

需要字段：

1. title
2. body
3. user.login
4. head.ref
5. base.ref
6. state
7. additions
8. deletions
9. changed_files
10. commits

### 获取 PR 变更文件

```http
GET /repos/{owner}/{repo}/pulls/{pull_number}/files
```

需要字段：

1. filename
2. status
3. additions
4. deletions
5. changes
6. patch
7. raw_url
8. blob_url

## 7.3 private 仓库访问

访问 private 仓库需要 GitHub Token。

MVP 阶段可以在后端配置文件或环境变量中配置：

```yaml
github:
  token: ${GITHUB_TOKEN}
```

后续可支持用户级 GitHub Token 配置。

## 7.4 GitHub API 异常处理

需要处理：

1. 401：Token 无效。
2. 403：无权限或 API 限流。
3. 404：仓库不存在或无权限。
4. 422：PR 编号无效。
5. 5xx：GitHub 服务异常。

------

# 8. Diff 解析设计

## 8.1 Diff 数据来源

MVP 阶段使用 GitHub changed files API 返回的 `patch` 字段作为分析对象。

## 8.2 文件过滤规则

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

## 8.3 文件语言识别

根据文件后缀识别语言：

| 后缀         | 语言       |
| ------------ | ---------- |
| .java        | Java       |
| .xml         | XML        |
| .yml / .yaml | YAML       |
| .sql         | SQL        |
| .js          | JavaScript |
| .ts          | TypeScript |
| .vue         | Vue        |
| .py          | Python     |
| .md          | Markdown   |
| .json        | JSON       |

## 8.4 Diff 长度限制

建议 MVP 阶段配置：

```text
单文件 patch 最大字符数：12000
单次模型输入最大字符数：20000
单个 PR 最大 Review 文件数：30
单个 PR 最大总 patch 字符数：100000
```

## 8.5 超长 Diff 处理策略

如果单文件 Diff 过长：

1. 优先按 hunk 切分。
2. 每个 chunk 单独调用模型。
3. 汇总 chunk 结果为文件级结果。
4. 如果仍然过长，截断并提示“部分内容未分析”。

------

# 9. AI Review 设计

## 9.1 AI Review 分层流程

系统采用两阶段 Review：

```text
第一阶段：文件级 Review
对每个变更文件进行独立分析，生成文件总结和问题建议。

第二阶段：PR 级汇总
综合所有文件级结果，生成 PR 总结、风险概览和合并建议。
```

## 9.2 文件级 Review 输入

```json
{
  "prTitle": "feat: add login api",
  "prDescription": "新增用户登录接口",
  "filePath": "src/main/java/com/example/UserService.java",
  "fileStatus": "modified",
  "language": "Java",
  "patch": "@@ -10,6 +10,10 @@ ..."
}
```

## 9.3 文件级 Review 输出

```json
{
  "filePath": "src/main/java/com/example/UserService.java",
  "summary": "该文件主要新增用户登录校验逻辑。",
  "comments": [
    {
      "line": 35,
      "riskType": "SECURITY_RISK",
      "severity": "HIGH",
      "title": "密码明文比较存在安全风险",
      "description": "当前代码直接比较明文密码，存在安全风险。",
      "suggestion": "建议使用 BCryptPasswordEncoder 对密码进行哈希校验。",
      "confidence": 0.92,
      "needHumanCheck": true
    }
  ]
}
```

## 9.4 PR 级汇总输出

```json
{
  "summary": "本次 PR 新增用户登录接口，涉及 Controller、Service 和 Token 工具类。",
  "riskOverview": {
    "high": 1,
    "medium": 2,
    "low": 3,
    "info": 1
  },
  "mainRisks": [
    "密码校验方式存在安全风险",
    "Token 过期时间未配置",
    "缺少登录失败次数限制"
  ],
  "recommendation": "建议优先修复高风险安全问题，并补充登录相关单元测试。",
  "mergeSuggestion": "REQUEST_CHANGES"
}
```

## 9.5 风险类型

| 风险类型         | 说明               |
| ---------------- | ------------------ |
| BUG_RISK         | 可能导致运行时错误 |
| SECURITY_RISK    | 可能导致安全漏洞   |
| PERFORMANCE_RISK | 可能导致性能问题   |
| MAINTAINABILITY  | 可维护性问题       |
| STYLE            | 代码规范问题       |
| TEST_RISK        | 测试不足           |
| COMPATIBILITY    | 兼容性风险         |

## 9.6 风险等级

| 等级   | 含义                     |
| ------ | ------------------------ |
| HIGH   | 高风险，建议必须修改     |
| MEDIUM | 中风险，建议优先修改     |
| LOW    | 低风险，可根据情况优化   |
| INFO   | 提示信息，不一定需要修改 |

------

# 10. Prompt 设计

## 10.1 文件级 Review System Prompt

```text
你是一名资深代码评审专家，擅长发现代码变更中的 Bug 风险、安全风险、性能问题、可维护性问题和测试不足。

你必须遵守以下规则：
1. 只基于用户提供的 PR 信息和 Diff 内容进行分析。
2. 不要编造未提供的业务背景。
3. 不要对未出现在 Diff 中的代码做确定性判断。
4. 不确定的问题必须标记 needHumanCheck=true。
5. 不要输出泛泛而谈的建议。
6. 每条建议必须包含明确的风险类型、风险等级、问题描述、修改建议和置信度。
7. 输出必须是合法 JSON，不要输出 Markdown。
```

## 10.2 文件级 Review User Prompt

```text
请对下面这个 Pull Request 中的单个文件变更进行代码评审。

PR 标题：
{{prTitle}}

PR 描述：
{{prDescription}}

文件路径：
{{filePath}}

文件状态：
{{fileStatus}}

语言：
{{language}}

Diff 内容：
{{patch}}

请重点检查：
1. Bug 风险
2. 空指针风险
3. 异常处理
4. 安全问题
5. 性能问题
6. 并发问题
7. 数据库访问问题
8. 可维护性问题
9. 测试不足

请严格按照以下 JSON 格式输出：

{
  "filePath": "文件路径",
  "summary": "该文件变更总结",
  "comments": [
    {
      "line": 代码行号，如果无法判断则为 null,
      "riskType": "BUG_RISK | SECURITY_RISK | PERFORMANCE_RISK | MAINTAINABILITY | STYLE | TEST_RISK | COMPATIBILITY",
      "severity": "HIGH | MEDIUM | LOW | INFO",
      "title": "问题标题",
      "description": "问题描述",
      "suggestion": "修改建议",
      "confidence": 0.0到1.0之间的小数,
      "needHumanCheck": true或false
    }
  ]
}
```

## 10.3 PR 汇总 Prompt

```text
你是一名资深技术负责人。下面是一个 Pull Request 的多个文件级 AI Review 结果，请你生成最终 PR Review 报告。

要求：
1. 总结本次 PR 的主要变更。
2. 汇总高风险和中风险问题。
3. 给出整体修改建议。
4. 判断是否建议合并。
5. 输出必须是合法 JSON。

输入：
{{fileReviewResults}}

输出格式：
{
  "summary": "PR整体总结",
  "riskOverview": {
    "high": 0,
    "medium": 0,
    "low": 0,
    "info": 0
  },
  "mainRisks": [
    "主要风险1",
    "主要风险2"
  ],
  "recommendation": "整体修改建议",
  "mergeSuggestion": "APPROVE | COMMENT | REQUEST_CHANGES"
}
```

------

# 11. Skill 机制设计

## 11.1 Skill 设计目标

Skill 机制用于将不同专项 Review 能力封装成可插拔模块，提高系统扩展性和专业性。

MVP 阶段不强制实现完整 Skill 平台，但建议在代码结构上预留 Skill 接口。

## 11.2 Skill 使用场景

| 文件 / 场景                | 对应 Skill             |
| -------------------------- | ---------------------- |
| Java 文件                  | JavaReviewSkill        |
| SQL 文件                   | SqlReviewSkill         |
| Controller / Security 配置 | SecurityReviewSkill    |
| 大循环 / 批量处理          | PerformanceReviewSkill |
| 测试文件缺失               | TestSuggestionSkill    |
| Vue / TS 文件              | FrontendReviewSkill    |

## 11.3 Skill 接口设计

```java
public interface ReviewSkill {

    String getSkillCode();

    String getSkillName();

    boolean supports(ReviewContext context);

    SkillReviewResult execute(ReviewContext context);
}
```

## 11.4 ReviewContext

```java
public class ReviewContext {

    private String prTitle;

    private String prDescription;

    private String filePath;

    private String fileStatus;

    private String language;

    private String patch;

    private Long taskId;

    private Long fileId;
}
```

## 11.5 Skill Router

```java
public class SkillRouter {

    private final List<ReviewSkill> skills;

    public List<ReviewSkill> route(ReviewContext context) {
        return skills.stream()
                .filter(skill -> skill.supports(context))
                .sorted(Comparator.comparing(ReviewSkill::getSkillCode))
                .toList();
    }
}
```

## 11.6 JavaReviewSkill 示例

```java
public class JavaReviewSkill implements ReviewSkill {

    @Override
    public String getSkillCode() {
        return "JAVA_REVIEW";
    }

    @Override
    public String getSkillName() {
        return "Java 后端代码审查";
    }

    @Override
    public boolean supports(ReviewContext context) {
        return "Java".equalsIgnoreCase(context.getLanguage());
    }

    @Override
    public SkillReviewResult execute(ReviewContext context) {
        // 1. 获取 Java 专项 Prompt
        // 2. 渲染 Prompt
        // 3. 调用 LLM Client
        // 4. 解析 JSON 输出
        // 5. 返回 SkillReviewResult
        return new SkillReviewResult();
    }
}
```

## 11.7 Skill 与 Prompt 的关系

每个 Skill 可以绑定一个 Prompt 模板。

例如：

| Skill              | Prompt                  |
| ------------------ | ----------------------- |
| GENERAL_REVIEW     | 通用文件 Review Prompt  |
| JAVA_REVIEW        | Java 专项 Review Prompt |
| SQL_REVIEW         | SQL 安全 Review Prompt  |
| SECURITY_REVIEW    | 安全漏洞 Review Prompt  |
| PERFORMANCE_REVIEW | 性能问题 Review Prompt  |
| TEST_REVIEW        | 测试建议 Prompt         |

## 11.8 Skill 的阶段规划

第一版：

```text
固定 Prompt + 通用 Review 流程
```

第二版：

```text
ReviewSkill 接口 + SkillRouter + JavaReviewSkill + SqlReviewSkill
```

第三版：

```text
Skill 配置化 + Skill 启用禁用 + Skill 优先级 + Skill 执行结果合并
```

------

# 12. 模型调用设计

## 12.1 模型调用抽象

定义统一模型调用接口：

```java
public interface LlmClient {

    LlmResponse chat(LlmRequest request);
}
```

## 12.2 OpenAI Compatible 实现

```java
public class OpenAiCompatibleClient implements LlmClient {

    @Override
    public LlmResponse chat(LlmRequest request) {
        // 1. 构造 OpenAI Compatible 格式请求
        // 2. 调用 /chat/completions
        // 3. 解析响应
        // 4. 返回 LlmResponse
        return response;
    }
}
```

## 12.3 模型参数建议

```text
temperature = 0.2
maxTokens = 4096
timeoutSeconds = 60
```

代码评审任务需要稳定和准确，因此 temperature 不建议过高。

## 12.4 模型选择原则

选择模型时重点考虑：

1. 代码理解能力。
2. 长上下文能力。
3. JSON 输出稳定性。
4. 响应速度。
5. 调用成本。
6. 中文解释能力。
7. 多语言代码支持能力。

------

# 13. 误报与漏报控制设计

## 13.1 降低误报

1. Prompt 中要求模型只基于 Diff 内容输出。
2. 要求每条建议提供置信度 confidence。
3. 不确定的问题标记 needHumanCheck=true。
4. 低置信度建议降低展示优先级。
5. 过滤空泛建议。
6. 不允许模型编造业务背景。
7. 对模型输出 JSON 做字段校验。

## 13.2 降低漏报

1. 对每个文件分别 Review。
2. 按风险清单逐项检查。
3. 对 Java、SQL、安全等场景使用专项 Prompt 或 Skill。
4. 对高风险文件二次分析。
5. PR 级汇总时再次检查是否存在遗漏风险。
6. 后续引入 RAG 补充上下文。

## 13.3 上下文增强策略

MVP 阶段上下文：

1. PR 标题。
2. PR 描述。
3. 文件路径。
4. 文件状态。
5. Diff patch。
6. 新增行数和删除行数。

进阶阶段上下文：

1. 完整文件内容。
2. 同目录相关文件。
3. import 依赖类。
4. README。
5. 数据库表结构。
6. 接口文档。
7. 历史 Review 记录。
8. 向量数据库检索结果。

------

# 14. 异步任务设计

## 14.1 为什么需要异步

AI Review 需要调用 GitHub API 和大模型 API，耗时较长。如果同步执行，用户会长时间等待，体验较差。因此系统采用异步任务。

## 14.2 线程池配置

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("reviewAsyncExecutor")
    public Executor reviewAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("review-task-");
        executor.initialize();
        return executor;
    }
}
```

当前 dev 已拆分为两个执行器：

```text
reviewAsyncExecutor：任务级异步执行器，一个 PR Review 任务对应一次异步执行。
fileReviewExecutor：文件级并发执行器，单个任务内多个文件并行调用 AI，并配合单文件超时控制。
```

## 14.3 任务状态流转

```text
PENDING
→ FETCHING_PR
→ PARSING_DIFF
→ REVIEWING
→ SUMMARIZING
→ SCORING
→ SUCCESS
```

当前 dev 状态：`REVIEWING`、`SUMMARIZING`、`SCORING`、`SUCCESS` 已由执行器实际更新；`FETCHING_PR`、`PARSING_DIFF` 已在枚举和前端展示中预留，但创建任务阶段仍同步获取 PR 与 Diff，后续需要迁入异步执行器或显式更新状态。

失败时：

```text
任意状态 → FAILED
```

取消时：

```text
任意未完成状态 → CANCELLED
```

## 14.4 前端轮询

前端创建任务后，每 2 秒请求一次：

```http
GET /api/review-tasks/{taskId}
```

直到任务状态为：

```text
SUCCESS / FAILED / CANCELLED
```

------

# 15. 数据库设计

## 15.1 review_task 表

```sql
CREATE TABLE review_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    pr_url VARCHAR(500) NOT NULL COMMENT 'GitHub Pull Request链接',
    owner_name VARCHAR(100) DEFAULT NULL COMMENT 'GitHub仓库owner',
    repo_name VARCHAR(150) DEFAULT NULL COMMENT 'GitHub仓库名称',
    pr_number INT DEFAULT NULL COMMENT 'Pull Request编号',
    pr_title VARCHAR(500) DEFAULT NULL COMMENT 'PR标题',
    pr_author VARCHAR(100) DEFAULT NULL COMMENT 'PR作者',
    source_branch VARCHAR(200) DEFAULT NULL COMMENT '源分支',
    target_branch VARCHAR(200) DEFAULT NULL COMMENT '目标分支',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/FETCHING_PR/PARSING_DIFF/REVIEWING/SUMMARIZING/SCORING/SUCCESS/FAILED/CANCELLED',
    risk_score INT DEFAULT NULL COMMENT '风险评分，范围0到100',
    risk_level VARCHAR(20) DEFAULT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/INFO',
    summary TEXT DEFAULT NULL COMMENT 'PR总结',
    final_review TEXT DEFAULT NULL COMMENT '最终Review结论',
    result_json LONGTEXT DEFAULT NULL COMMENT '完整AI Review结果JSON',
    error_message TEXT DEFAULT NULL COMMENT '任务失败原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_pr_url (pr_url),
    INDEX idx_repo_pr (owner_name, repo_name, pr_number),
    INDEX idx_created_at (created_at),
    INDEX idx_risk_level (risk_level),
    INDEX idx_status (status)
);
```

## 15.2 review_file 表

```sql
CREATE TABLE review_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '关联review_task.id',
    file_path VARCHAR(600) DEFAULT NULL COMMENT '文件路径',
    file_status VARCHAR(30) DEFAULT NULL COMMENT '文件状态：added/modified/removed/renamed',
    language VARCHAR(50) DEFAULT NULL COMMENT '语言类型',
    additions INT NOT NULL DEFAULT 0 COMMENT '新增行数',
    deletions INT NOT NULL DEFAULT 0 COMMENT '删除行数',
    changes INT NOT NULL DEFAULT 0 COMMENT '总变更行数',
    patch LONGTEXT DEFAULT NULL COMMENT 'Diff patch内容',
    ai_summary TEXT DEFAULT NULL COMMENT 'AI文件级总结',
    skipped TINYINT NOT NULL DEFAULT 0 COMMENT '是否跳过AI分析：0否，1是',
    skip_reason VARCHAR(500) DEFAULT NULL COMMENT '跳过原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_file_path (file_path),
    INDEX idx_skipped (skipped)
);
```

## 15.3 review_comment 表

```sql
CREATE TABLE review_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '关联review_task.id',
    file_path VARCHAR(600) DEFAULT NULL COMMENT '风险所在文件路径',
    line_number INT DEFAULT NULL COMMENT '代码行号',
    risk_type VARCHAR(50) DEFAULT NULL COMMENT '风险类型：BUG_RISK/SECURITY_RISK/PERFORMANCE_RISK/MAINTAINABILITY/STYLE/TEST_RISK/COMPATIBILITY',
    risk_level VARCHAR(20) DEFAULT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/INFO',
    title VARCHAR(300) DEFAULT NULL COMMENT '问题标题',
    description TEXT DEFAULT NULL COMMENT '问题描述',
    reason TEXT DEFAULT NULL COMMENT '风险原因说明',
    suggestion TEXT DEFAULT NULL COMMENT '修改建议',
    confidence DECIMAL(4,2) DEFAULT NULL COMMENT '置信度：0到1之间的小数',
    need_human_check TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要人工确认：0否，1是',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_risk_level (risk_level),
    INDEX idx_risk_type (risk_type),
    INDEX idx_task_level (task_id, risk_level)
);
```

## 15.4 model_config 表

```sql
CREATE TABLE model_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(100) NOT NULL COMMENT '供应商编码',
    provider_name VARCHAR(100) COMMENT '供应商名称',
    base_url VARCHAR(500) NOT NULL COMMENT '模型API地址',
    api_key_encrypted TEXT COMMENT '加密后的API Key',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    temperature DECIMAL(3,2) DEFAULT 0.20,
    max_tokens INT DEFAULT 4096,
    timeout_seconds INT DEFAULT 60,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 15.5 prompt_template 表

```sql
CREATE TABLE prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prompt_type VARCHAR(100) NOT NULL COMMENT 'Prompt类型',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    system_prompt TEXT NOT NULL,
    user_prompt TEXT NOT NULL,
    enabled TINYINT DEFAULT 1,
    version INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_prompt_type (prompt_type)
);
```

## 15.6 review_skill 表

```sql
CREATE TABLE review_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_code VARCHAR(100) NOT NULL COMMENT 'Skill编码',
    skill_name VARCHAR(100) NOT NULL COMMENT 'Skill名称',
    skill_type VARCHAR(50) NOT NULL COMMENT 'Skill类型',
    description VARCHAR(500) COMMENT 'Skill描述',
    supported_languages VARCHAR(200) COMMENT '支持语言，如 Java,SQL,Vue',
    prompt_template_id BIGINT COMMENT '关联Prompt模板ID',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    priority INT DEFAULT 100 COMMENT '执行优先级',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_code (skill_code)
);
```

------

# 16. REST API 设计

## 16.1 创建 Review 任务

```http
POST /api/review-tasks
```

请求：

```json
{
  "prUrl": "https://github.com/example/demo/pull/12"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 10001,
    "status": "PENDING"
  }
}
```

## 16.2 查询任务详情

```http
GET /api/review-tasks/{taskId}
```

响应：

```json
{
  "code": 0,
  "data": {
    "id": 10001,
    "prTitle": "feat: add login api",
    "status": "SUCCESS",
    "summary": "本次 PR 新增了用户登录能力。",
    "riskOverview": {
      "high": 1,
      "medium": 2,
      "low": 3,
      "info": 1
    },
    "mergeSuggestion": "REQUEST_CHANGES"
  }
}
```

## 16.3 查询文件级 Review

```http
GET /api/review-tasks/{taskId}/files
```

响应：

```json
{
  "code": 0,
  "data": [
    {
      "fileId": 1,
      "filePath": "src/main/java/com/example/UserService.java",
      "language": "Java",
      "summary": "新增用户登录校验逻辑。",
      "commentCount": 2
    }
  ]
}
```

## 16.4 查询 Review 建议

```http
GET /api/review-tasks/{taskId}/comments
```

请求参数：

```text
severity=HIGH
riskType=SECURITY_RISK
```

响应：

```json
{
  "code": 0,
  "data": [
    {
      "filePath": "src/main/java/com/example/UserService.java",
      "lineNumber": 35,
      "severity": "HIGH",
      "riskType": "SECURITY_RISK",
      "title": "密码明文比较存在安全风险",
      "description": "当前代码直接比较明文密码。",
      "suggestion": "建议使用 BCryptPasswordEncoder。",
      "confidence": 0.92,
      "needHumanCheck": true
    }
  ]
}
```

## 16.5 重新 Review

```http
POST /api/review-tasks/{taskId}/rerun
```

响应：

```json
{
  "code": 0,
  "data": {
    "newTaskId": 10002,
    "status": "PENDING"
  }
}
```

## 16.6 保存模型配置

```http
POST /api/model-configs
```

请求：

```json
{
  "providerCode": "deepseek",
  "providerName": "DeepSeek",
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-xxx",
  "modelName": "deepseek-chat",
  "temperature": 0.2,
  "maxTokens": 4096
}
```

## 16.7 测试模型连接

```http
POST /api/model-configs/{id}/test
```

## 16.8 Prompt 模板管理

```http
GET /api/prompt-templates
POST /api/prompt-templates
PUT /api/prompt-templates/{id}
DELETE /api/prompt-templates/{id}
```

## 16.9 Skill 管理，二期功能

```http
GET /api/review-skills
POST /api/review-skills
PUT /api/review-skills/{id}
POST /api/review-skills/{id}/enable
POST /api/review-skills/{id}/disable
```

------

# 17. 前端页面设计

## 17.1 页面列表

| 页面          | 路由              | 功能                           |
| ------------- | ----------------- | ------------------------------ |
| 首页          | /                 | 输入 PR 链接，创建 Review 任务 |
| 任务详情页    | /review-tasks/:id | 查看 Review 报告               |
| 历史记录页    | /history          | 查看历史 Review 任务           |
| 模型配置页    | /settings/model   | 配置模型供应商                 |
| Prompt 配置页 | /settings/prompt  | 管理 Prompt 模板               |
| Skill 配置页  | /settings/skill   | 管理 Review Skill，二期功能    |

## 17.2 Review 报告页展示内容

1. PR 基本信息。
2. 任务状态。
3. AI 总结。
4. 风险统计。
5. 高风险问题列表。
6. 文件级变更列表。
7. Review 建议详情。
8. 复制建议按钮。
9. 重新 Review 按钮。

------

# 18. 安全设计

## 18.1 GitHub Token 安全

1. Token 不返回给前端。
2. Token 不打印到日志。
3. MVP 阶段优先使用环境变量配置。
4. 如需入库，必须加密存储。
5. 错误提示中不暴露 Token 内容。

## 18.2 模型 API Key 安全

1. API Key 使用环境变量或加密存储。
2. 前端不直接调用模型 API。
3. 后端统一代理模型调用。
4. 配置页面只展示掩码。
5. 日志中脱敏处理。

## 18.3 输入校验

1. 校验 PR URL 格式。
2. 限制单个 PR 最大文件数。
3. 限制单个 patch 最大长度。
4. 防止超大请求。
5. 对前端展示内容做 XSS 防护。

------

# 19. 可观测性设计

## 19.1 MVP 日志

需要记录：

1. 任务创建日志。
2. PR URL 解析结果。
3. GitHub API 调用耗时。
4. 获取文件数量。
5. 文件过滤数量。
6. 模型调用耗时。
7. 模型调用失败原因。
8. JSON 解析失败原因。
9. 任务完成总耗时。

## 19.2 后续增强

后续可以接入：

1. Prometheus。
2. Grafana。
3. Langfuse。
4. ELK。
5. 链路追踪。

------

# 20. 部署设计

## 20.1 MVP 部署架构

```text
云服务器
├── Nginx
│   ├── 前端静态资源
│   └── 反向代理 /api 到后端
├── Spring Boot 后端
├── MySQL
└── Redis，可选
```

## 20.2 环境变量

```text
GITHUB_TOKEN=ghp_xxx
AI_BASE_URL=https://api.deepseek.com/v1
AI_API_KEY=sk-xxx
AI_MODEL_NAME=deepseek-chat
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=ai_pr_review
MYSQL_USERNAME=root
MYSQL_PASSWORD=xxx
```

## 20.3 Docker Compose 服务

建议包含：

```text
frontend
backend
mysql
redis
nginx
```

------

# 21. 开发阶段规划

## 21.1 第一阶段：项目初始化

目标：

1. 创建 Spring Boot 项目。
2. 创建 Vue3 项目。
3. 配置 MySQL。
4. 建立基础目录结构。
5. 跑通前后端 hello 接口。

验收标准：

1. 后端可以启动。
2. 前端可以启动。
3. 前端可以调用后端接口。

## 21.2 第二阶段：GitHub PR 获取

目标：

1. 实现 PR URL 解析。
2. 调用 GitHub API 获取 PR 信息。
3. 获取 changed files。

验收标准：

1. 输入 PR URL 可以获取 PR 标题。
2. 可以获取文件列表和 patch。

## 21.3 第三阶段：Review 任务管理

目标：

1. 创建 review_task 表。
2. 创建 review_file 表。
3. 实现任务创建接口。
4. 实现任务状态流转。

验收标准：

1. 可以创建 Review 任务。
2. 数据库保存任务和文件信息。
3. 可以查询任务状态。

## 21.4 第四阶段：AI Review 接入

目标：

1. 实现模型配置。
2. 实现 OpenAI Compatible API 调用。
3. 编写文件级 Review Prompt。
4. 解析模型 JSON 输出。

验收标准：

1. 可以成功调用模型。
2. 可以生成文件级 Review。
3. Review 评论可以保存数据库。

## 21.5 第五阶段：Review 报告展示

目标：

1. 前端展示 PR 信息。
2. 展示 AI 总结。
3. 展示风险统计。
4. 展示文件级建议。

验收标准：

1. 用户可以看到完整 Review 报告。
2. 建议可以按风险等级展示。
3. 可以复制 Review 建议。

## 21.6 第六阶段：优化与包装

目标：

1. 增加错误处理。
2. 增加文件过滤。
3. 增加任务历史。
4. 增加模型配置页面。
5. 编写 README 和答辩材料。

验收标准：

1. 系统可以稳定演示。
2. README 中包含项目介绍、架构图、启动方式。
3. 可以录制完整演示流程。

## 21.7 第七阶段：Skill 扩展

目标：

1. 增加 ReviewSkill 接口。
2. 增加 SkillRouter。
3. 增加 JavaReviewSkill。
4. 增加 SqlReviewSkill。
5. 支持 Skill 配置化。

验收标准：

1. Java 文件可以走 JavaReviewSkill。
2. SQL 文件可以走 SqlReviewSkill。
3. Skill 可以启用和禁用。

------

# 22. 项目亮点

本项目可总结为以下技术亮点：

1. 基于 GitHub PR 的自动化代码变更获取。
2. 基于 Diff 的大模型代码评审。
3. 文件级 Review 和 PR 级汇总的两阶段分析流程。
4. 结构化 JSON 输出，便于前端展示和后续处理。
5. 支持风险类型、风险等级和置信度标注。
6. 通过 Prompt 约束降低误报和模型幻觉。
7. 异步任务执行，提高用户体验。
8. 模型供应商抽象，支持 DeepSeek、Qwen、OpenAI 等模型切换。
9. Prompt Template 配置化，便于持续优化评审效果。
10. Skill 机制预留专项评审扩展能力。
11. 后续可扩展 RAG 和 Agent，提升上下文理解能力。
12. 可接入 GitHub Webhook 和 CI/CD，实现自动化 Review 流程。

------

# 23. 总结

本系统采用“GitHub PR 获取 + Diff 解析 + Prompt 驱动的大模型 Review + 结构化报告展示”的设计方案，优先保证 MVP 可快速落地。

在 MVP 阶段，系统重点完成：

```text
输入 PR 链接
→ 获取 Diff
→ 调用 AI
→ 生成 Review 报告
→ 前端展示
```

在扩展阶段，系统通过 Skill、RAG、Agent、Webhook 和 CI/CD 集成，逐步演进为更加智能和工程化的 AI 代码评审平台。

最终系统既能满足题目要求中的 PR 变更总结、风险代码识别、Review 建议生成，也能体现模型选择、上下文获取、误报漏报控制、响应速度优化和未来扩展方向等设计思路。
