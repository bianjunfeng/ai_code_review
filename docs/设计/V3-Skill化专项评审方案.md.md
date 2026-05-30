# V3：Skill 化专项评审设计方案

## 一、版本定位

V3 的目标是将当前 AI PR Review 系统从“单一 AI Review 调用”升级为“可插拔专项评审能力”。

V1 解决的是主链路跑通：

```text
PR 链接 → GitHub Diff → AI Review → 报告展示
```

V2 解决的是工程稳定性：

```text
日志优化、数据库缓存、Redis 限流、模型用量监控、固定评分
```

V3 要解决的是评审能力扩展问题：

```text
不同语言、不同文件类型、不同风险类型，应该使用不同的专项评审策略。
```

因此，V3 的核心是：

```text
Review Skill 化
```

也就是将原来统一的 AI Review 能力拆分为多个可插拔的 Skill。

------

## 二、V3 核心目标

V3 主要实现以下目标：

1. 将单一 `AiReviewService` 拆分为多个专项 Review Skill；
2. 支持根据文件类型、语言、路径、风险特征选择不同 Skill；
3. 每个 Skill 可以拥有独立 Prompt；
4. 所有 Skill 输出统一结构，避免前端和数据库复杂化；
5. 支持 Skill 执行结果记录；
6. 支持多个 Skill 输出合并和去重；
7. 为后续 ReviewAgent 调度 Skill 打基础。

------

## 三、为什么要做 Skill 化

当前原始方式通常是：

```text
所有文件 → 同一个 Prompt → 同一个 AI Review 流程
```

这种方式存在问题：

1. Java 后端代码、Vue 前端代码、SQL 文件、安全相关文件的审查重点不同；
2. 单一 Prompt 很难覆盖所有场景；
3. Review 建议容易泛化；
4. 后续扩展安全审查、性能审查、测试建议时会越来越混乱；
5. 不利于后续 Agent 做能力调度。

Skill 化之后，系统可以变成：

```text
Vue 文件 → FrontendReviewSkill
Java 文件 → JavaReviewSkill
SQL / Mapper 文件 → SqlReviewSkill
auth / token / password 相关文件 → SecurityReviewSkill
其他文件 → GeneralReviewSkill
```

这样每种文件都能走更适合的审查逻辑。

------

## 四、Skill 的定义

在本项目中，Skill 定义为：

```text
Skill = 路由条件 + Prompt 模板 + LLM 调用 + JSON 解析 + 结构化结果输出
```

Skill 不是 Agent。

Skill 只负责某一类专项能力，例如：

```text
我能审查 Java 文件
我能审查 Vue 文件
我能审查安全风险
我能审查 SQL 风险
```

Agent 是后续版本中负责任务规划和 Skill 调度的组件。

V3 阶段先做 Skill，不直接做复杂 Agent。

------

## 五、整体架构设计

### 5.1 V2 当前结构

```text
ReviewTaskExecutor
  ↓
AiReviewService
  ↓
LlmClient
  ↓
AiReviewOutputParser
  ↓
review_comment
```

### 5.2 V3 Skill 化结构

```text
ReviewTaskExecutor
  ↓
ReviewPipeline
  ↓
SkillEngine
  ↓
SkillRouter
  ↓
CodeReviewSkill[]
  ↓
PromptRenderer
  ↓
LlmClient
  ↓
AiReviewOutputParser
  ↓
SkillResultMerger
  ↓
review_skill_result / review_comment / review_file / review_task
```

### 5.3 核心模块说明

| 模块                 | 作用                          |
| -------------------- | ----------------------------- |
| ReviewPipeline       | 编排一次 PR Review 的固定流程 |
| SkillEngine          | Skill 执行入口                |
| SkillRouter          | 根据文件信息选择 Skill        |
| CodeReviewSkill      | Skill 统一接口                |
| SkillContext         | Skill 执行上下文              |
| SkillResult          | Skill 结构化输出              |
| SkillResultMerger    | 合并多个 Skill 的结果         |
| PromptRenderer       | 渲染不同 Skill 的 Prompt      |
| LlmClient            | 调用模型 API                  |
| AiReviewOutputParser | 解析模型 JSON 输出            |

------

## 六、后端包结构设计

建议新增包：

```text
backend/src/main/java/com/example/aipr/service/skill
├── CodeReviewSkill.java
├── SkillContext.java
├── SkillResult.java
├── SkillPhase.java
├── SkillEngine.java
├── SkillRouter.java
├── SkillResultMerger.java
└── impl
    ├── GeneralReviewSkill.java
    ├── FrontendReviewSkill.java
    ├── JavaReviewSkill.java
    ├── SecurityReviewSkill.java
    ├── SqlReviewSkill.java
    ├── TestSuggestionSkill.java
    └── ReviewSummarySkill.java
```

注意：

如果当前项目已经有实体类 `ReviewSkill`，不要再把接口命名为 `ReviewSkill`，避免和数据库实体冲突。

推荐接口命名为：

```text
CodeReviewSkill
```

------

## 七、核心接口设计

### 7.1 CodeReviewSkill

```java
public interface CodeReviewSkill {

    String getSkillCode();

    String getSkillName();

    SkillPhase getPhase();

    boolean supports(SkillContext context);

    SkillResult execute(SkillContext context);
}
```

### 7.2 方法说明

| 方法         | 说明                                 |
| ------------ | ------------------------------------ |
| getSkillCode | Skill 唯一编码，例如 FRONTEND_REVIEW |
| getSkillName | Skill 展示名称                       |
| getPhase     | Skill 执行阶段                       |
| supports     | 判断当前文件或任务是否适合该 Skill   |
| execute      | 执行 Skill，返回结构化结果           |

------

## 八、SkillPhase 设计

```java
public enum SkillPhase {
    FILE_REVIEW,
    PR_SUMMARY,
    TEST_SUGGESTION,
    FINAL_REVIEW
}
```

### 阶段说明

| 阶段            | 说明         |
| --------------- | ------------ |
| FILE_REVIEW     | 文件级审查   |
| PR_SUMMARY      | PR 总结      |
| TEST_SUGGESTION | 测试建议     |
| FINAL_REVIEW    | 最终合并建议 |

V3 MVP 阶段优先实现：

```text
FILE_REVIEW
```

其他阶段可以后续扩展。

------

## 九、SkillContext 设计

`SkillContext` 是传给每个 Skill 的上下文对象。

```java
public class SkillContext {

    private Long taskId;
    private Long fileId;

    private String prUrl;
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

    private List<ReviewFile> changedFiles;
    private List<ReviewComment> existingComments;
}
```

### 字段说明

| 字段             | 说明                   |
| ---------------- | ---------------------- |
| taskId           | 当前 Review 任务 ID    |
| fileId           | 当前文件 ID            |
| prTitle          | PR 标题                |
| sourceBranch     | 来源分支               |
| targetBranch     | 目标分支               |
| filePath         | 文件路径               |
| language         | 文件语言               |
| patch            | 当前文件 Diff          |
| changedFiles     | 当前 PR 的所有变更文件 |
| existingComments | 已有 Review 建议       |

V3 初始阶段可以只使用：

```text
taskId
fileId
filePath
language
additions
deletions
changes
patch
```

------

## 十、SkillResult 设计

Skill 统一输出结构：

```java
public class SkillResult {

    private String skillCode;

    private String skillName;

    private SkillPhase phase;

    private Long taskId;

    private Long fileId;

    private String filePath;

    private boolean success;

    private String summary;

    private List<FileReviewCommentResult> comments;

    private String rawOutput;

    private String errorMessage;
}
```

### 输出要求

所有 Skill 必须输出统一结构：

```json
{
  "filePath": "frontend/src/views/HomeView.vue",
  "summary": "该文件新增 loadingStatus 加载状态展示逻辑。",
  "comments": [
    {
      "lineNumber": 102,
      "riskType": "MAINTAINABILITY",
      "riskLevel": "LOW",
      "title": "loadingStatus 文案硬编码",
      "description": "状态文案散落在 handleAnalyze 中。",
      "reason": "后续维护和国际化不方便。",
      "suggestion": "建议抽取为常量。",
      "confidence": 0.85,
      "needHumanCheck": false
    }
  ]
}
```

不要让不同 Skill 输出不同 JSON 格式。

------

## 十一、推荐 Skill 列表

### 11.1 GeneralReviewSkill

通用兜底 Skill。

触发条件：

```text
所有可分析文件
```

职责：

```text
发现通用代码质量问题
识别明显 BUG 风险
给出基础 Review 建议
```

适合：

```text
所有语言文件
兜底场景
```

------

### 11.2 FrontendReviewSkill

前端专项 Skill。

触发条件：

```text
.vue
.js
.ts
.jsx
tsx
```

审查重点：

```text
组件状态管理
异步请求状态
loading / error / empty 状态
用户交互
组件拆分
可维护性
样式命名
重复逻辑
```

示例问题：

```text
loading 状态未在异常分支清理
多个 v-if 互斥关系不清晰
状态文案硬编码
组件职责过重
```

------

### 11.3 JavaReviewSkill

Java / Spring Boot 专项 Skill。

触发条件：

```text
.java
```

审查重点：

```text
Controller / Service / Mapper 分层
异常处理
事务边界
空指针风险
参数校验
MyBatis-Plus 使用
并发安全
日志规范
```

示例问题：

```text
Controller 直接写业务逻辑
Service 方法缺少事务
外部 API 调用未处理异常
未校验请求参数
```

------

### 11.4 SecurityReviewSkill

安全专项 Skill。

触发条件：

路径或内容包含：

```text
auth
security
token
password
secret
config
login
permission
jwt
```

审查重点：

```text
硬编码密钥
Token 泄露
密码明文处理
权限校验缺失
敏感日志输出
SQL 注入风险
接口越权风险
```

示例问题：

```text
日志中打印 token
配置文件中出现明文 API Key
接口缺少权限校验
密码直接比较
```

------

### 11.5 SqlReviewSkill

SQL / Mapper 专项 Skill。

触发条件：

```text
.sql
.xml 且路径包含 mapper
代码中明显包含 SQL 字符串
```

审查重点：

```text
慢查询风险
索引使用
全表扫描
分页性能
SQL 注入
字段名不一致
逻辑删除条件
```

示例问题：

```text
SELECT * 查询大表
缺少 WHERE 条件
LIKE '%xxx' 无法使用索引
Mapper 字段与实体字段不一致
```

------

### 11.6 TestSuggestionSkill

测试建议 Skill。

触发条件：

```text
PR 级别执行
```

职责：

```text
根据本次 PR 改动生成测试建议
```

输出示例：

```text
建议补充 ReviewTaskService 的缓存命中测试
建议补充 forceRefresh=true 的重复分析测试
建议补充 AI API 调用失败的异常测试
```

V3 初期可以先不实现，作为 V3.2 扩展。

------

### 11.7 ReviewSummarySkill

PR 总结 Skill。

触发条件：

```text
所有文件分析完成后执行
```

职责：

```text
汇总文件级 Review 结果
生成 PR 总结
生成最终 Review 建议
```

V3 初期可以继续使用现有总结逻辑，后续再 Skill 化。

------

## 十二、SkillRouter 设计

### 12.1 职责

`SkillRouter` 负责根据文件信息选择需要执行的 Skill。

输入：

```text
SkillContext
```

输出：

```text
List<CodeReviewSkill>
```

### 12.2 路由规则

```text
1. 获取所有启用的 Skill
2. 调用 skill.supports(context)
3. 按优先级排序
4. 控制单个文件最多执行 2-3 个 Skill
5. GeneralReviewSkill 作为兜底
```

### 12.3 路由示例

| 文件                     | 执行 Skill                               |
| ------------------------ | ---------------------------------------- |
| `HomeView.vue`           | FrontendReviewSkill + GeneralReviewSkill |
| `ReviewTaskService.java` | JavaReviewSkill + GeneralReviewSkill     |
| `AuthController.java`    | JavaReviewSkill + SecurityReviewSkill    |
| `UserMapper.xml`         | SqlReviewSkill + GeneralReviewSkill      |
| `schema.sql`             | SqlReviewSkill                           |
| `README.md`              | GeneralReviewSkill 或跳过                |

------

## 十三、SkillEngine 设计

### 13.1 职责

`SkillEngine` 是 Skill 执行入口。

主要负责：

```text
构造 SkillContext
调用 SkillRouter
执行多个 Skill
捕获单个 Skill 异常
保存 SkillResult
调用 SkillResultMerger 合并结果
```

### 13.2 执行流程

```text
SkillEngine.reviewFile(context)

1. 调用 SkillRouter 选择 Skill
2. 遍历执行 Skill
3. 每个 Skill 独立 try-catch
4. 保存 review_skill_result
5. 合并所有 SkillResult
6. 返回最终文件级 Review 结果
```

### 13.3 失败处理

单个 Skill 失败时：

```text
记录 review_skill_result.success=false
记录 errorMessage
不影响其他 Skill
不导致整个任务失败
```

只有全部 Skill 失败时，才考虑将任务标记为失败。

------

## 十四、SkillResultMerger 设计

多个 Skill 可能输出重复建议，因此需要合并。

### 14.1 去重规则

以下字段相近则认为重复：

```text
filePath 相同
riskType 相同
lineNumber 相同或都为空
title 相似
description 相似
```

### 14.2 保留规则

重复项保留：

```text
riskLevel 更高的
confidence 更高的
suggestion 更完整的
needHumanCheck=true 的优先
```

### 14.3 数量限制

建议限制：

```text
单文件最多保留 10 条建议
单 PR 最多展示 30 条建议
HIGH / MEDIUM 优先
LOW / INFO 可折叠
```

### 14.4 低置信度处理

```text
confidence < 0.5
→ 降级为 INFO
或不进入主报告，只保存 rawOutput
```

------

## 十五、数据库设计

### 15.1 review_skill 表

用于配置 Skill。

```sql
CREATE TABLE IF NOT EXISTS review_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    skill_code VARCHAR(100) NOT NULL COMMENT 'Skill 编码',
    skill_name VARCHAR(100) NOT NULL COMMENT 'Skill 名称',
    skill_type VARCHAR(50) NOT NULL COMMENT 'Skill 类型',
    description VARCHAR(500) DEFAULT NULL COMMENT '说明',

    supported_languages VARCHAR(255) DEFAULT NULL COMMENT '支持语言',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    priority INT DEFAULT 100 COMMENT '优先级，数字越小优先级越高',

    prompt_template_code VARCHAR(100) DEFAULT NULL COMMENT 'Prompt 模板编码',
    max_comments INT DEFAULT 8 COMMENT '最大建议数',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_skill_code (skill_code),
    INDEX idx_enabled (enabled),
    INDEX idx_skill_type (skill_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Review Skill 配置表';
```

------

### 15.2 review_skill_result 表

用于记录 Skill 执行结果。

```sql
CREATE TABLE IF NOT EXISTS review_skill_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',
    file_id BIGINT DEFAULT NULL COMMENT 'Review 文件ID',
    file_path VARCHAR(512) DEFAULT NULL COMMENT '文件路径',

    skill_code VARCHAR(100) NOT NULL COMMENT 'Skill 编码',
    skill_name VARCHAR(100) NOT NULL COMMENT 'Skill 名称',
    phase VARCHAR(50) NOT NULL COMMENT '执行阶段',

    success TINYINT(1) DEFAULT 1 COMMENT '是否成功',
    summary TEXT DEFAULT NULL COMMENT 'Skill 输出摘要',
    raw_output MEDIUMTEXT DEFAULT NULL COMMENT 'Skill 原始输出',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',

    comment_count INT DEFAULT 0 COMMENT '输出建议数',
    latency_ms BIGINT DEFAULT NULL COMMENT '执行耗时',
    total_tokens INT DEFAULT 0 COMMENT 'Token 消耗，可从 model_usage_log 汇总',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_task_id (task_id),
    INDEX idx_file_id (file_id),
    INDEX idx_skill_code (skill_code),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Review Skill 执行结果表';
```

------

### 15.3 初始化 Skill 数据

```sql
INSERT INTO review_skill
(skill_code, skill_name, skill_type, description, supported_languages, enabled, priority, prompt_template_code, max_comments)
VALUES
('GENERAL_REVIEW', '通用代码审查', 'GENERAL', '适用于所有可分析文件的通用 Review Skill', 'ALL', 1, 100, 'general-review-v1', 8),
('FRONTEND_REVIEW', '前端专项审查', 'FRONTEND', '审查 Vue/JS/TS 文件中的状态管理、交互和可维护性问题', 'Vue,JavaScript,TypeScript', 1, 20, 'frontend-review-v1', 8),
('JAVA_REVIEW', 'Java 后端专项审查', 'BACKEND', '审查 Java/Spring Boot 代码中的分层、异常、事务和可维护性问题', 'Java', 1, 30, 'java-review-v1', 8),
('SECURITY_REVIEW', '安全专项审查', 'SECURITY', '审查 token、密码、权限、敏感信息泄露等安全问题', 'ALL', 1, 10, 'security-review-v1', 8),
('SQL_REVIEW', 'SQL 专项审查', 'SQL', '审查 SQL、Mapper XML、索引和慢查询风险', 'SQL,XML', 1, 40, 'sql-review-v1', 8);
```

------

## 十六、Prompt 设计原则

### 16.1 统一输出格式

每个 Skill 可以有不同 Prompt，但输出 JSON 必须一致。

统一输出：

```json
{
  "filePath": "",
  "summary": "",
  "comments": [
    {
      "lineNumber": null,
      "riskType": "",
      "riskLevel": "",
      "title": "",
      "description": "",
      "reason": "",
      "suggestion": "",
      "confidence": 0.0,
      "needHumanCheck": false
    }
  ]
}
```

### 16.2 风险等级统一

建议统一使用：

```text
HIGH
MEDIUM
LOW
INFO
```

不要一部分 Prompt 使用 `CRITICAL`，一部分使用 `HIGH`，否则前后端枚举会混乱。

### 16.3 风险类型建议

```text
BUG_RISK
SECURITY
PERFORMANCE
MAINTAINABILITY
TEST_RISK
STYLE
INFO
```

------

## 十七、Prompt 模板示例

### 17.1 FrontendReviewSkill Prompt

```text
你是一个前端代码 Review 专家，正在审查一个 GitHub Pull Request 中的前端文件。

请重点关注：
1. Vue/JS/TS 状态管理是否清晰；
2. loading/error/empty 状态是否完整；
3. 异步请求是否正确处理异常；
4. 组件结构是否清晰；
5. 是否存在重复代码；
6. 样式命名是否清晰；
7. 是否存在影响用户体验的问题。

请只基于给定 diff 分析，不要臆测未出现的代码。

请输出严格 JSON，格式如下：
{
  "filePath": "...",
  "summary": "...",
  "comments": [
    {
      "lineNumber": 102,
      "riskType": "MAINTAINABILITY",
      "riskLevel": "LOW",
      "title": "...",
      "description": "...",
      "reason": "...",
      "suggestion": "...",
      "confidence": 0.85,
      "needHumanCheck": false
    }
  ]
}

文件路径：
{{filePath}}

Diff：
{{patch}}
```

------

### 17.2 JavaReviewSkill Prompt

```text
你是一个 Java Spring Boot 代码 Review 专家，正在审查一个 GitHub Pull Request 中的 Java 文件。

请重点关注：
1. Controller / Service / Mapper 分层是否合理；
2. 参数校验是否充分；
3. 异常处理是否完整；
4. 事务边界是否合理；
5. 是否存在空指针风险；
6. 是否存在并发安全问题；
7. 日志是否规范；
8. MyBatis-Plus 使用是否正确。

请只基于给定 diff 分析，不要臆测未出现的代码。

请输出严格 JSON，风险等级只能是 HIGH、MEDIUM、LOW、INFO。

文件路径：
{{filePath}}

Diff：
{{patch}}
```

------

### 17.3 SecurityReviewSkill Prompt

```text
你是一个安全代码审查专家，正在审查一个 Pull Request 中可能涉及安全风险的文件。

请重点关注：
1. 是否存在硬编码 token、secret、password；
2. 是否存在敏感信息日志输出；
3. 是否存在权限校验缺失；
4. 是否存在认证绕过风险；
5. 是否存在 SQL 注入风险；
6. 是否存在不安全的配置默认值；
7. 是否存在过度暴露错误信息。

如果没有明确安全风险，请不要强行输出 HIGH 风险。

请输出严格 JSON。
```

------

## 十八、ReviewPipeline 改造方案

### 18.1 当前流程

```text
ReviewTaskExecutor
→ 获取文件
→ AiReviewService.reviewFile
→ 保存评论
```

### 18.2 V3 改造后

```text
ReviewTaskExecutor
→ ReviewPipeline
→ 获取 review_file 列表
→ 对每个文件构造 SkillContext
→ skillEngine.reviewFile(context)
→ 保存 review_skill_result
→ 合并 comments
→ 保存 review_comment
→ 更新 review_file.ai_summary
→ 计算 riskScore
→ 更新 review_task
```

------

## 十九、后端接口设计

### 19.1 获取 Skill 配置

```http
GET /api/review-skills
```

返回：

```json
[
  {
    "id": 1,
    "skillCode": "FRONTEND_REVIEW",
    "skillName": "前端专项审查",
    "skillType": "FRONTEND",
    "supportedLanguages": "Vue,JavaScript,TypeScript",
    "enabled": true,
    "priority": 20,
    "description": "审查 Vue/JS/TS 文件中的状态管理、交互和可维护性问题"
  }
]
```

------

### 19.2 启用 / 禁用 Skill

```http
POST /api/review-skills/{id}/enable
POST /api/review-skills/{id}/disable
```

V3 初期可以先不做编辑，只做查询和展示。

------

### 19.3 获取任务 Skill 执行结果

```http
GET /api/review-tasks/{taskId}/skill-results
```

返回：

```json
[
  {
    "skillCode": "FRONTEND_REVIEW",
    "skillName": "前端专项审查",
    "phase": "FILE_REVIEW",
    "success": true,
    "filePath": "frontend/src/views/HomeView.vue",
    "summary": "发现 loading 状态维护性问题。",
    "commentCount": 3,
    "latencyMs": 3200,
    "totalTokens": 5980,
    "createdAt": "2026-05-30 20:10:12"
  }
]
```

------

## 二十、前端设计

### 20.1 报告详情页增加 Skill 结果 Tab

在 `ReviewReportView` 中增加：

```text
Skill 结果
```

展示内容：

```text
Skill 名称
执行阶段
执行状态
文件路径
输出建议数
耗时
Token 消耗
摘要
错误信息
```

示例：

```text
FrontendReviewSkill
状态：成功
文件：frontend/src/views/HomeView.vue
建议数：3
耗时：3200ms
摘要：发现 loading 状态维护性问题。

GeneralReviewSkill
状态：成功
文件：frontend/src/views/HomeView.vue
建议数：2
耗时：2800ms
摘要：整体风险较低，建议优化可维护性。
```

------

### 20.2 SkillCenterView

新增页面：

```text
/skills
```

功能：

```text
展示 Skill 列表
展示 Skill 是否启用
展示支持语言
展示优先级
展示说明
后续支持启用/禁用
```

表格字段：

```text
Skill 名称
Skill Code
类型
支持语言
启用状态
优先级
说明
操作
```

------

### 20.3 前端 API

新增：

```text
src/api/skill.js
import request from './request'

export function listSkills() {
  return request.get('/api/review-skills')
}

export function listSkillResults(taskId) {
  return request.get(`/api/review-tasks/${taskId}/skill-results`)
}

export function enableSkill(id) {
  return request.post(`/api/review-skills/${id}/enable`)
}

export function disableSkill(id) {
  return request.post(`/api/review-skills/${id}/disable`)
}
```

------

## 二十一、与 V2 监控功能的关系

V3 Skill 化可以复用 V2 的模型用量监控。

`model_usage_log` 中已有字段：

```text
task_id
file_id
skill_code
call_type
prompt_tokens
completion_tokens
total_tokens
latency_ms
```

V3 中每个 Skill 调用 LLM 时，应填充：

```text
skill_code = FRONTEND_REVIEW / JAVA_REVIEW / SECURITY_REVIEW
call_type = SKILL_REVIEW
```

这样后续可以统计：

```text
哪个 Skill 最耗 Token
哪个 Skill 响应最慢
哪个 Skill 失败率最高
哪个 Skill 输出建议最多
```

------

## 二十二、与 V4 Agent 的关系

V3 是 V4 Agent 的基础。

V4 中：

```text
ReviewAgent 不直接审查代码
ReviewAgent 负责任务编排和 Skill 调度
具体审查能力由 Skill 提供
```

也就是：

```text
Agent = 调度者
Skill = 能力单元
```

如果没有 V3 Skill 化，V4 Agent 就只能调用一个统一的 AI Review 服务，Agent 的价值不明显。

------

## 二十三、落地优先级

### P0：V3 MVP

```text
CodeReviewSkill 接口
SkillContext
SkillResult
SkillRouter
SkillEngine
GeneralReviewSkill
FrontendReviewSkill
review_skill_result 表
报告详情页 Skill 结果 Tab
```

------

### P1：后端专项增强

```text
JavaReviewSkill
SecurityReviewSkill
SqlReviewSkill
SkillResultMerger
Skill 失败容错
model_usage_log 填充 skill_code
```

------

### P2：配置化

```text
review_skill 表
SkillCenterView
启用 / 禁用 Skill
优先级配置
Prompt 模板绑定
```

------

### P3：PR 级 Skill

```text
TestSuggestionSkill
ReviewSummarySkill
FinalReviewSkill
```

------

## 二十四、测试方案

### 24.1 Skill 路由测试

测试文件：

```text
frontend/src/views/HomeView.vue
```

预期：

```text
命中 FrontendReviewSkill + GeneralReviewSkill
```

测试文件：

```text
backend/src/main/java/com/example/aipr/service/ReviewTaskService.java
```

预期：

```text
命中 JavaReviewSkill + GeneralReviewSkill
```

测试文件：

```text
backend/src/main/resources/mapper/ReviewTaskMapper.xml
```

预期：

```text
命中 SqlReviewSkill + GeneralReviewSkill
```

------

### 24.2 Skill 执行测试

步骤：

```text
1. 创建一个 PR Review 任务
2. 等待任务执行完成
3. 查询 review_skill_result
4. 确认每个文件至少有一个 Skill 执行记录
5. 确认 review_comment 正常生成
6. 确认 review_file.ai_summary 正常更新
```

SQL：

```sql
SELECT * FROM review_skill_result ORDER BY created_at DESC LIMIT 10;
```

------

### 24.3 前端测试

步骤：

```text
1. 打开报告详情页
2. 切换到 Skill 结果 Tab
3. 查看 Skill 执行结果
4. 打开 SkillCenterView
5. 查看 Skill 列表
```

------

### 24.4 构建测试

后端：

```bash
mvn clean package -DskipTests
```

前端：

```bash
npm run build
```

------

## 二十五、风险与注意事项

### 25.1 不要一开始做过度复杂

V3 初期不要做完整插件平台。

不建议一开始实现：

```text
在线编辑 Prompt
复杂 Skill 编排图
多模型投票
复杂权限系统
```

先实现：

```text
固定 Skill 类
固定 Prompt
可记录结果
可前端展示
```

------

### 25.2 输出结构必须统一

所有 Skill 必须输出同一个 JSON 格式。

否则会导致：

```text
Parser 复杂
数据库字段混乱
前端展示混乱
结果合并困难
```

------

### 25.3 Skill 失败不能影响整个任务

单个 Skill 失败时：

```text
记录失败
继续执行其他 Skill
报告中展示失败信息
```

不能因为一个 SecurityReviewSkill 失败就导致整个 PR Review 失败。

------

### 25.4 控制 Token 成本

单个文件不要执行过多 Skill。

建议：

```text
单文件最多执行 2-3 个 Skill
大文件限制 patch 长度
低价值文件直接跳过
```

------

## 二十六、Claude Code 编码提示词

```text
你现在是我的 Java Spring Boot + Vue3 项目开发助手。当前项目是 AI PR Review 助手，已经完成 V1 主链路和 V2 工程增强能力。现在需要实现 V3：Skill 化专项评审能力。

目标：
将当前单一 AiReviewService 的文件审查逻辑升级为可插拔 Skill 机制，根据文件类型、语言和路径选择不同专项 Review Skill。

请按最小可行方案实现，不要大规模重构。

一、后端核心设计

新增 package：

com.example.aipr.service.skill

新增以下类：

1. CodeReviewSkill
- Skill 统一接口
- 方法：
  - getSkillCode()
  - getSkillName()
  - getPhase()
  - supports(SkillContext context)
  - execute(SkillContext context)

2. SkillPhase
- FILE_REVIEW
- PR_SUMMARY
- TEST_SUGGESTION
- FINAL_REVIEW

3. SkillContext
字段至少包括：
- taskId
- fileId
- prTitle
- sourceBranch
- targetBranch
- filePath
- fileStatus
- language
- additions
- deletions
- changes
- patch

4. SkillResult
字段至少包括：
- skillCode
- skillName
- phase
- taskId
- fileId
- filePath
- success
- summary
- comments
- rawOutput
- errorMessage

5. SkillRouter
- 根据 supports(context) 选择 Skill
- GeneralReviewSkill 兜底
- 单个文件最多执行 2-3 个 Skill

6. SkillEngine
- 构造执行流程
- 调用 SkillRouter
- 执行多个 Skill
- 保存 Skill 执行结果
- 合并 comments

7. SkillResultMerger
- 合并多个 Skill 输出
- 对重复建议去重
- 保留风险等级更高、confidence 更高的建议

二、实现 Skill

先实现以下两个 Skill：

1. GeneralReviewSkill
- 所有可分析文件兜底执行
- 复用现有 AiReviewService 的通用 Prompt 或通用审查逻辑

2. FrontendReviewSkill
- 支持 .vue / .js / .ts
- 重点审查：
  - 状态管理
  - loading/error/empty 状态
  - 异步请求异常处理
  - 组件结构
  - 重复逻辑
  - 可维护性

如果时间允许，再实现 JavaReviewSkill：
- 支持 .java
- 重点审查 Spring Boot 分层、异常处理、事务、参数校验、空指针风险

三、数据库

新增 review_skill_result 表：

CREATE TABLE IF NOT EXISTS review_skill_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',
    file_id BIGINT DEFAULT NULL COMMENT 'Review 文件ID',
    file_path VARCHAR(512) DEFAULT NULL COMMENT '文件路径',
    skill_code VARCHAR(100) NOT NULL COMMENT 'Skill 编码',
    skill_name VARCHAR(100) NOT NULL COMMENT 'Skill 名称',
    phase VARCHAR(50) NOT NULL COMMENT '执行阶段',
    success TINYINT(1) DEFAULT 1 COMMENT '是否成功',
    summary TEXT DEFAULT NULL COMMENT 'Skill 输出摘要',
    raw_output MEDIUMTEXT DEFAULT NULL COMMENT 'Skill 原始输出',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    comment_count INT DEFAULT 0 COMMENT '输出建议数',
    latency_ms BIGINT DEFAULT NULL COMMENT '执行耗时',
    total_tokens INT DEFAULT 0 COMMENT 'Token 消耗',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_file_id (file_id),
    INDEX idx_skill_code (skill_code),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Review Skill 执行结果表';

新增：
- ReviewSkillResult entity
- ReviewSkillResultMapper
- ReviewSkillResultMapper.xml，如项目使用 XML

四、执行链路改造

请检查当前 ReviewTaskExecutor 或 ReviewTaskService 中调用 AiReviewService 的位置。

将原来的：

aiReviewService.reviewFile(...)

调整为：

skillEngine.reviewFile(context)

要求：
1. 不破坏现有主流程
2. SkillEngine 返回合并后的 summary 和 comments
3. comments 继续写入 review_comment
4. summary 继续写入 review_file.ai_summary
5. riskScore 仍由后端固定规则计算
6. Skill 执行结果写入 review_skill_result

五、Prompt 和输出

所有 Skill 输出必须统一成当前可解析结构：

{
  "filePath": "...",
  "summary": "...",
  "comments": [
    {
      "lineNumber": null,
      "riskType": "MAINTAINABILITY",
      "riskLevel": "LOW",
      "title": "...",
      "description": "...",
      "reason": "...",
      "suggestion": "...",
      "confidence": 0.85,
      "needHumanCheck": false
    }
  ]
}

风险等级统一使用：
HIGH / MEDIUM / LOW / INFO

不要引入 CRITICAL，避免前端枚举不一致。

六、接口

新增接口：

GET /api/review-tasks/{taskId}/skill-results

返回某个任务的 Skill 执行结果。

可选新增：

GET /api/review-skills

如果当前不做 review_skill 配置表，可以先返回代码中内置的 Skill 列表。

七、前端

在 ReviewReportView 中新增 Tab：

Skill 结果

展示：
- skillName
- skillCode
- phase
- success
- filePath
- summary
- commentCount
- latencyMs
- totalTokens
- errorMessage
- createdAt

如果接口不存在或没有数据，显示：
当前任务暂无 Skill 执行结果。

新增 API：

src/api/skill.js

包含：
- listSkillResults(taskId)
- listSkills()

可选新增页面：
SkillCenterView
用于展示 Skill 列表，不要求复杂管理。

八、与模型用量监控联动

如果当前已有 model_usage_log，请在 Skill 调用模型时传入：
- skillCode
- callType = SKILL_REVIEW

如果当前暂时不好接入，可以先不强制，但保留字段。

九、异常处理

1. 单个 Skill 失败，不应导致整个任务失败
2. 记录 review_skill_result.success=false
3. 保存 errorMessage
4. 继续执行其他 Skill
5. 所有 Skill 都失败时，才考虑任务失败

十、测试

请给出测试步骤：

1. 创建一个包含 Vue 文件变更的 PR Review 任务
2. 确认命中 FrontendReviewSkill
3. 确认 review_skill_result 有记录
4. 确认 review_comment 正常生成
5. 确认 ReviewReportView 的 Skill 结果 Tab 能展示数据
6. 测试 Skill 失败时，任务主流程不被中断
7. 执行 mvn clean package -DskipTests
8. 执行 npm run build

十一、输出要求

请输出：
1. 修改文件清单
2. 新增数据库 SQL
3. 新增类说明
4. 执行流程说明
5. 前端页面说明
6. 测试步骤
7. 已知限制

请先实现 V3 MVP：GeneralReviewSkill + FrontendReviewSkill + review_skill_result + Skill 结果 Tab。
不要一次性实现复杂插件平台。
```

------

## 二十七、总结

V3 Skill 化的核心目标是：

```text
把单一 AI Review 拆成多个专项能力模块。
```

最终效果：

```text
不同文件走不同 Skill
每个 Skill 有独立 Prompt
每个 Skill 输出统一结构
每个 Skill 执行结果可追踪
多个 Skill 输出可合并
为后续 ReviewAgent 调度打基础
```

推荐落地顺序：

```text
第一步：CodeReviewSkill 接口
第二步：SkillContext / SkillResult
第三步：GeneralReviewSkill / FrontendReviewSkill
第四步：SkillEngine / SkillRouter
第五步：review_skill_result 入库
第六步：报告详情页展示 Skill 结果
第七步：Java / Security / SQL Skill 扩展
```