# V4：基于 Spring AI 的 Agent 化智能编排设计方案

## 一、版本定位

V4 的目标是将项目从 **Skill 化专项评审系统** 升级为 **基于 Spring AI 的 ReviewAgent 智能编排系统**。

前面版本关系：

```
V1 原始 MVP：
PR 链接 → GitHub Diff → AI Review → 报告展示

V2 工程增强：
日志优化、数据库缓存、Redis 限流、模型用量监控、固定评分

V3 Skill 化：
JavaReviewSkill、FrontendReviewSkill、SecurityReviewSkill、SqlReviewSkill 等专项能力

V4 Spring AI Agent 化：
Spring AI ChatClient + Tool Calling + Structured Output + ReviewAgent 编排
```

V4 不是把所有业务逻辑都交给大模型自由决定，而是采用：

```
Spring Boot 业务编排
+ Spring AI 模型调用
+ Spring AI Tool Calling
+ 自研 ReviewAgent
+ 自研 SkillEngine
+ 自研 AgentTrace / AgentEvaluator
```

这样既能体现 Agent 化，又能保证系统稳定、可控、可追踪。

------

## 二、为什么 V4 可以引入 Spring AI

Spring AI 的定位比较适合你这个 Java / Spring Boot 项目。它提供 ChatClient API、Tool Calling、Structured Output、Advisors、Chat Memory 和 Observability 等能力，其中 Tool Calling 可以让模型请求执行客户端工具函数，Structured Output 可以把模型输出转换为 Java 对象，Advisors 可以用于 Chat Memory、RAG 等能力，Spring AI 也提供 AI 相关操作的可观测能力。

但需要注意：

```
Spring AI 适合做模型调用层、工具调用层、结构化输出层；
不建议完全替代你的业务流程编排。
```

也就是说，V4 的核心仍然是你自己的 `ReviewAgent`，Spring AI 主要负责：

```
1. 统一模型调用：ChatClient
2. 提供工具调用能力：Tool Calling
3. 提供结构化输出：StructuredOutputConverter / BeanOutputConverter
4. 后续支持 Chat Memory / RAG / Advisors
5. 后续接入 Observability
```

------

## 三、V4 总体设计原则

### 1. 单 ReviewAgent + 多 Skill

当前项目不建议做复杂多 Agent，推荐：

```
一个 ReviewAgent
+ 多个 Tool
+ 多个 Skill
```

结构：

```
ReviewAgent
├── SpringAiReviewPlanner
├── GitHubTool
├── CacheTool
├── SkillTool
├── RiskTool
├── ReportTool
├── SkillEngine
│   ├── GeneralReviewSkill
│   ├── FrontendReviewSkill
│   ├── JavaReviewSkill
│   ├── SecurityReviewSkill
│   └── SqlReviewSkill
├── SkillResultMerger
├── RiskScoreCalculator
├── ReportBuilder
├── AgentTraceRecorder
└── AgentEvaluator
```

------

### 2. Spring AI 参与“决策点”，不接管完整流程

V4 仍然采用确定性主流程：

```
loadTask
→ checkCache
→ fetchPrInfo
→ fetchChangedFiles
→ planSkills
→ executeSkills
→ mergeResults
→ calculateRiskScore
→ buildReport
→ saveResult
→ recordTrace
```

Spring AI 主要参与：

```
1. PR 类型识别
2. Skill 执行计划生成
3. 文件级 Review Prompt 调用
4. 最终报告总结
5. 可选工具调用
```

不建议让模型自由决定：

```
是否保存数据库
是否修改任务状态
是否跳过安全检查
是否覆盖 riskScore
是否直接访问敏感配置
```

这些必须由后端确定性代码控制。

------

## 四、V4 Spring AI 架构

### 4.1 总体架构

```
ReviewTaskExecutor
  ↓
ReviewAgent
  ↓
ReviewAgentState
  ↓
SpringAiReviewPlanner
  ↓
ReviewPlan
  ↓
SkillEngine
  ↓
SpringAiSkillExecutor / SpringAiLlmClient
  ↓
Structured Output Parser
  ↓
SkillResultMerger
  ↓
RiskScoreCalculator
  ↓
ReportBuilder
  ↓
AgentTraceRecorder
```

------

### 4.2 Spring AI 在 V4 中的位置

```
Spring AI
├── ChatClient
│   ├── PR 类型识别
│   ├── Skill 计划生成
│   ├── 文件级 Review
│   └── 最终总结生成
│
├── Tool Calling
│   ├── GitHubTool
│   ├── SkillTool
│   ├── RiskTool
│   └── ReportTool
│
├── Structured Output
│   ├── ReviewPlan 输出
│   ├── FileReviewResult 输出
│   └── FinalReviewResult 输出
│
└── Advisors，可选
    ├── Chat Memory
    └── RAG / 项目规范检索
```

Spring AI 的 ChatClient 只有在调用 `content()`、`chatResponse()`、`responseEntity()` 等方法时才真正触发模型执行；Tool Calling 是阻塞式工作流；StructuredOutputConverter 可将文本模型输出转换为 Java 类或集合结构。

------

## 五、依赖与配置设计

### 5.1 Maven 依赖建议

具体版本需要以你项目当前 Spring Boot 版本和 Spring AI 官方 BOM 为准。建议让 Claude / Codex 根据当前 `pom.xml` 统一加 BOM，不要手写不兼容版本。

示意：

```
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

如果你的模型 API 是 OpenAI-compatible，例如本地 vLLM、部分云厂商兼容接口，可以考虑：

```
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

如果 MiniMax 当前接口不能直接被 Spring AI OpenAI Starter 兼容，可以保留自定义 `MiniMaxLlmClient`，Spring AI 先用于可兼容模型或后续扩展。

------

### 5.2 application.yml 配置建议

```
spring:
  ai:
    openai:
      api-key: ${AI_API_KEY:}
      base-url: ${AI_BASE_URL:}
      chat:
        options:
          model: ${AI_MODEL_NAME:}
          temperature: ${AI_TEMPERATURE:0.2}
          max-tokens: ${AI_MAX_TOKENS:3000}

review-agent:
  enabled: ${REVIEW_AGENT_ENABLED:true}
  planner-mode: ${REVIEW_AGENT_PLANNER_MODE:HYBRID}
  max-skills-per-file: ${REVIEW_AGENT_MAX_SKILLS_PER_FILE:3}
  max-files-per-task: ${REVIEW_AGENT_MAX_FILES_PER_TASK:30}
  max-patch-length: ${REVIEW_AGENT_MAX_PATCH_LENGTH:12000}
  agent-version: ${REVIEW_AGENT_VERSION:v1}
```

`planner-mode` 建议支持：

```
RULE_ONLY：只用规则规划
AI_ONLY：只用 Spring AI 规划
HYBRID：规则先生成候选，AI 辅助修正，推荐
```

------

## 六、后端包结构设计

建议新增：

```
backend/src/main/java/com/example/aipr/service/agent
├── ReviewAgent.java
├── ReviewAgentState.java
├── ReviewAgentStep.java
├── ReviewPlan.java
├── ReviewPlanItem.java
├── ReviewPlanner.java
├── RuleBasedReviewPlanner.java
├── SpringAiReviewPlanner.java
├── HybridReviewPlanner.java
├── AgentTraceRecorder.java
└── AgentExecutionException.java
```

新增 Spring AI 相关包：

```
backend/src/main/java/com/example/aipr/service/ai/springai
├── SpringAiConfig.java
├── SpringAiLlmClient.java
├── SpringAiStructuredOutputService.java
├── SpringAiPromptTemplates.java
└── SpringAiToolConfig.java
```

Tool 包：

```
backend/src/main/java/com/example/aipr/service/agent/tool
├── GitHubAgentTool.java
├── CacheAgentTool.java
├── SkillAgentTool.java
├── RiskAgentTool.java
└── ReportAgentTool.java
```

------

## 七、ReviewAgentState 设计

```
public class ReviewAgentState {

    private Long taskId;

    private String prUrl;

    private String ownerName;

    private String repoName;

    private Integer prNumber;

    private GitHubPrInfo prInfo;

    private List<ReviewFile> files;

    private List<SkillResult> skillResults;

    private List<ReviewComment> comments;

    private ReviewPlan reviewPlan;

    private Integer riskScore;

    private String riskLevel;

    private String summary;

    private String finalReview;

    private Boolean cacheHit;

    private Long cachedFromTaskId;

    private String currentStep;

    private String errorMessage;

    private Long startedAt;

    private Long finishedAt;
}
```

------

## 八、ReviewAgentStep 设计

```
public enum ReviewAgentStep {

    LOAD_TASK,

    FETCH_PR_INFO,

    CHECK_CACHE,

    FETCH_CHANGED_FILES,

    PLAN_SKILLS,

    EXECUTE_SKILLS,

    MERGE_RESULTS,

    CALCULATE_RISK_SCORE,

    BUILD_REPORT,

    SAVE_RESULT,

    COMPLETED,

    FAILED
}
```

------

## 九、ReviewPlan 设计

```
public class ReviewPlan {

    private Long taskId;

    private String prType;

    private String planSummary;

    private List<ReviewPlanItem> items;
}
public class ReviewPlanItem {

    private Long fileId;

    private String filePath;

    private String language;

    private Boolean skipped;

    private String skipReason;

    private List<String> skillCodes;

    private String reason;
}
```

示例：

```
{
  "taskId": 11,
  "prType": "FRONTEND_CHANGE",
  "planSummary": "该 PR 主要修改前端页面，优先执行前端专项审查和通用审查。",
  "items": [
    {
      "fileId": 21,
      "filePath": "frontend/src/views/HomeView.vue",
      "language": "Vue",
      "skipped": false,
      "skillCodes": ["FRONTEND_REVIEW", "GENERAL_REVIEW"],
      "reason": "Vue 文件，涉及页面状态和交互逻辑"
    }
  ]
}
```

------

## 十、Spring AI Planner 设计

### 10.1 规划方式选择

推荐使用 `HybridReviewPlanner`：

```
RuleBasedReviewPlanner 先生成基础计划
→ SpringAiReviewPlanner 对计划进行解释、补充、修正
→ 后端校验 skillCodes 是否合法
→ 后端限制每个文件最多执行 2-3 个 Skill
→ 生成最终 ReviewPlan
```

这样做的好处：

```
1. 避免模型乱选不存在的 Skill
2. 避免模型让所有文件都执行所有 Skill
3. 保留确定性兜底
4. 可以体现 Spring AI 参与智能规划
```

------

### 10.2 SpringAiReviewPlanner 输入

```
PR 标题
PR 描述
源分支 / 目标分支
文件列表
每个文件的 filePath / language / additions / deletions / changes
候选 Skill 列表
规则生成的初始计划
```

不要把完整 Diff 都给 Planner。Planner 只需要文件元信息，避免消耗 Token。

------

### 10.3 SpringAiReviewPlanner Prompt 示例

```
你是一个 AI PR Review Agent 的规划器。

你的任务是根据 PR 信息和变更文件列表，为每个文件选择合适的 Review Skill。

可用 Skill：
1. GENERAL_REVIEW：通用代码审查
2. FRONTEND_REVIEW：前端 Vue/JS/TS 审查
3. JAVA_REVIEW：Java/Spring Boot 审查
4. SECURITY_REVIEW：安全风险审查
5. SQL_REVIEW：SQL/Mapper 审查

约束：
1. 每个文件最多选择 3 个 Skill。
2. 文档文件可以跳过。
3. 大文件可以跳过或只执行 GENERAL_REVIEW。
4. 如果文件路径包含 auth/token/password/security/config，优先考虑 SECURITY_REVIEW。
5. 不允许输出不存在的 Skill。
6. 输出必须是严格 JSON。

请输出：
{
  "taskId": 0,
  "prType": "",
  "planSummary": "",
  "items": [
    {
      "fileId": 0,
      "filePath": "",
      "language": "",
      "skipped": false,
      "skipReason": "",
      "skillCodes": [],
      "reason": ""
    }
  ]
}

PR 信息：
{{prInfo}}

变更文件：
{{files}}

规则初始计划：
{{rulePlan}}
```

------

### 10.4 结构化输出

可以使用 Spring AI Structured Output，将模型输出映射到 `ReviewPlan`。Spring AI 的 StructuredOutputConverter 接口可以把文本输出转换成 Java 类型，并提供格式化指令能力。

伪代码示例：

```
@Service
@RequiredArgsConstructor
public class SpringAiReviewPlanner {

    private final ChatClient chatClient;

    public ReviewPlan plan(ReviewAgentState state, ReviewPlan rulePlan) {
        BeanOutputConverter<ReviewPlan> converter =
                new BeanOutputConverter<>(ReviewPlan.class);

        String format = converter.getFormat();

        String prompt = buildPlannerPrompt(state, rulePlan, format);

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        ReviewPlan aiPlan = converter.convert(content);

        return validateAndNormalize(aiPlan, rulePlan);
    }
}
```

注意：

```
模型输出必须经过 validateAndNormalize。
不能直接相信模型返回的 skillCodes。
```

------

## 十一、Spring AI Tool Calling 设计

Spring AI Tool Calling 可以定义工具、解析模型的工具调用请求并执行工具函数。它适合后续让模型在有限范围内请求执行工具，但当前 V4 不建议让模型完全自由调用所有业务工具。

### 11.1 Tool 使用边界

V4 推荐两种模式：

```
模式一：后端主动调用工具，Spring AI 不参与工具调用
模式二：Spring AI 只在规划或总结阶段允许调用只读工具
```

当前更推荐模式一。

如果要使用 Tool Calling，工具必须受限：

```
允许：
- 查询任务摘要
- 查询文件元信息
- 查询 Skill 列表
- 生成报告 Markdown

不允许：
- 直接写数据库
- 直接修改任务状态
- 读取 API Key
- 读取数据库密码
- 读取完整敏感配置
```

------

### 11.2 Tool 示例

```
@Component
public class ReviewAgentTools {

    @Tool(description = "List available review skills")
    public List<SkillInfo> listAvailableSkills() {
        return skillService.listEnabledSkills();
    }

    @Tool(description = "Calculate risk score from review comments")
    public RiskScoreResult calculateRiskScore(List<ReviewComment> comments) {
        return riskScoreCalculator.calculate(comments);
    }
}
```

然后在 ChatClient 中注册工具：

```
String result = chatClient.prompt()
        .user(prompt)
        .tools(reviewAgentTools)
        .call()
        .content();
```

注意：这里的写法需要以你引入的 Spring AI 版本为准，因为 Tool API 在不同版本中可能有变化。官方文档也提示旧的 FunctionCallback 已迁移到 ToolCallback API。

------

## 十二、Spring AI 文件级 Review 设计

V3 的 Skill 可以继续保留，每个 Skill 内部调用 Spring AI。

例如：

```
FrontendReviewSkill
→ SpringAiLlmClient.reviewFile(...)
→ Structured Output → FileReviewResult
```

### 12.1 FileReviewResult 结构

```
public class FileReviewResult {

    private String filePath;

    private String summary;

    private List<FileReviewCommentResult> comments;
}
```

### 12.2 SpringAiLlmClient

```
@Service
@RequiredArgsConstructor
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;

    @Override
    public FileReviewResult reviewFile(FileReviewPrompt prompt, LlmCallContext context) {
        BeanOutputConverter<FileReviewResult> converter =
                new BeanOutputConverter<>(FileReviewResult.class);

        String userPrompt = prompt.render(converter.getFormat());

        long start = System.currentTimeMillis();

        try {
            ChatResponse response = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .chatResponse();

            String content = response.getResult().getOutput().getText();
            FileReviewResult result = converter.convert(content);

            // 记录 model_usage_log
            recordUsage(response, context, System.currentTimeMillis() - start, true, null);

            return result;
        } catch (Exception e) {
            recordUsage(null, context, System.currentTimeMillis() - start, false, e.getMessage());
            throw e;
        }
    }
}
```

实际获取文本和 usage 的 API 需要按你所用 Spring AI 版本确认。Spring AI ChatClient 可返回 `chatResponse()`，而 Structured Output 需要结合 Converter 解析输出。

------

## 十三、ReviewAgent 主流程设计

```
@Service
@RequiredArgsConstructor
public class ReviewAgent {

    private final ReviewTaskService reviewTaskService;
    private final GitHubAgentTool gitHubTool;
    private final CacheAgentTool cacheTool;
    private final HybridReviewPlanner reviewPlanner;
    private final SkillEngine skillEngine;
    private final SkillResultMerger skillResultMerger;
    private final RiskScoreCalculator riskScoreCalculator;
    private final ReportBuilder reportBuilder;
    private final AgentTraceRecorder traceRecorder;

    public void run(Long taskId) {
        ReviewAgentState state = new ReviewAgentState();
        state.setTaskId(taskId);
        state.setStartedAt(System.currentTimeMillis());

        try {
            traceRecorder.success(taskId, ReviewAgentStep.LOAD_TASK, "加载 Review 任务", null);
            ReviewTask task = reviewTaskService.loadTask(taskId);
            state.setPrUrl(task.getPrUrl());

            traceRecorder.success(taskId, ReviewAgentStep.FETCH_PR_INFO, "获取 PR 信息", null);
            GitHubPrInfo prInfo = gitHubTool.getPullRequest(task);
            state.setPrInfo(prInfo);

            traceRecorder.success(taskId, ReviewAgentStep.CHECK_CACHE, "检查数据库缓存", null);
            Optional<ReviewTask> cached = cacheTool.findCachedReport(task, prInfo);
            if (cached.isPresent()) {
                handleCacheHit(state, cached.get());
                traceRecorder.success(taskId, ReviewAgentStep.COMPLETED, "命中缓存，直接返回历史报告", null);
                return;
            }

            traceRecorder.success(taskId, ReviewAgentStep.FETCH_CHANGED_FILES, "获取 PR 变更文件", null);
            List<ReviewFile> files = gitHubTool.getChangedFiles(task, prInfo);
            state.setFiles(files);

            traceRecorder.success(taskId, ReviewAgentStep.PLAN_SKILLS, "使用 Spring AI 生成 Skill 执行计划", null);
            ReviewPlan plan = reviewPlanner.plan(state);
            state.setReviewPlan(plan);

            traceRecorder.success(taskId, ReviewAgentStep.EXECUTE_SKILLS, "执行 Skill", null);
            List<SkillResult> skillResults = skillEngine.executePlan(state, plan);
            state.setSkillResults(skillResults);

            traceRecorder.success(taskId, ReviewAgentStep.MERGE_RESULTS, "合并 Skill 输出", null);
            List<ReviewComment> comments = skillResultMerger.merge(skillResults);
            state.setComments(comments);

            traceRecorder.success(taskId, ReviewAgentStep.CALCULATE_RISK_SCORE, "计算风险评分", null);
            RiskScoreResult score = riskScoreCalculator.calculate(comments);
            state.setRiskScore(score.getRiskScore());
            state.setRiskLevel(score.getRiskLevel());

            traceRecorder.success(taskId, ReviewAgentStep.BUILD_REPORT, "生成最终报告", null);
            ReviewReport report = reportBuilder.build(state);

            traceRecorder.success(taskId, ReviewAgentStep.SAVE_RESULT, "保存最终结果", null);
            reviewTaskService.saveResult(state, report);

            traceRecorder.success(taskId, ReviewAgentStep.COMPLETED, "Agent 执行完成", null);
        } catch (Exception e) {
            traceRecorder.failed(taskId, ReviewAgentStep.FAILED, e.getMessage(), null);
            reviewTaskService.markFailed(taskId, e);
        } finally {
            state.setFinishedAt(System.currentTimeMillis());
        }
    }
}
```

------

## 十四、agent_trace 表设计

```
CREATE TABLE IF NOT EXISTS agent_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',

    step_code VARCHAR(100) NOT NULL COMMENT '步骤编码',
    step_name VARCHAR(100) NOT NULL COMMENT '步骤名称',
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/RUNNING/SKIPPED',

    message VARCHAR(500) DEFAULT NULL COMMENT '步骤说明',
    detail_json TEXT DEFAULT NULL COMMENT '步骤详情 JSON',

    started_at DATETIME DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME DEFAULT NULL COMMENT '结束时间',
    duration_ms BIGINT DEFAULT NULL COMMENT '耗时',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_task_id (task_id),
    INDEX idx_step_code (step_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 执行轨迹表';
```

------

## 十五、ReviewPlan 校验规则

Spring AI 输出的 `ReviewPlan` 必须经过后端校验。

### 15.1 合法 Skill 白名单

```
GENERAL_REVIEW
FRONTEND_REVIEW
JAVA_REVIEW
SECURITY_REVIEW
SQL_REVIEW
```

如果模型输出未知 Skill：

```
直接丢弃
或替换为 GENERAL_REVIEW
```

------

### 15.2 单文件 Skill 数量限制

```
单文件最多 3 个 Skill
```

防止模型选择过多 Skill 导致 Token 爆炸。

------

### 15.3 文件必须存在

`ReviewPlanItem.fileId` 必须存在于当前 task 的 `review_file` 列表中。

------

### 15.4 安全 Skill 强制规则

即使 Spring AI 没选，如果路径包含：

```
auth
token
password
secret
security
jwt
permission
config
```

后端也应强制追加：

```
SECURITY_REVIEW
```

------

### 15.5 兜底规则

如果某个文件没有任何 Skill：

```
追加 GENERAL_REVIEW
```

如果是文档或图片等不可分析文件，可以跳过。

------

## 十六、Prompt 模板管理

建议新增：

```
SpringAiPromptTemplates
```

包括：

```
planner-prompt-v1
frontend-review-v1
java-review-v1
security-review-v1
sql-review-v1
final-summary-v1
```

### 16.1 Prompt Version

继续复用 V2 的：

```
review:
  prompt-version: ${REVIEW_PROMPT_VERSION:v1}
```

在 `review_task` 中保存：

```
prompt_version
```

这样数据库缓存仍然有效：

```
owner + repo + prNumber + headSha + modelName + promptVersion
```

------

## 十七、模型用量监控接入

V4 使用 Spring AI 后，仍然要写入：

```
model_usage_log
```

每次 Spring AI 调用记录：

```
task_id
file_id
skill_code
provider
model_name
call_type
prompt_tokens
completion_tokens
total_tokens
latency_ms
success
error_message
```

call_type 建议：

```
AGENT_PLANNING
SKILL_REVIEW
FINAL_REVIEW
```

如果某些模型或接口没有返回 usage：

```
token 字段填 0
不影响主流程
```

------

## 十八、Spring AI Advisors 的使用边界

Spring AI Advisors 可用于 Chat Memory、RAG 等场景，官方文档也建议在构建 ChatClient 时注册默认 Advisors。Advisors 还会参与 Observability 栈，便于查看相关指标和 trace。

但 V4 阶段不建议强依赖 Chat Memory，因为 PR Review 是一次性任务，不是长对话任务。

推荐：

```
V4 不启用 Chat Memory
V4.1 可启用 RAG Advisor，用于注入项目规范
V5 可结合评测反馈做长期优化
```

如果后续启用带工具调用的 Agent Memory，要注意 Spring AI 文档中对工具访问和用户内容隔离的提醒：有工具访问的 Agent 配置中，应优先把用户内容保留在 typed Message 对象中，而不是随意拼入系统提示词，以降低 Prompt Injection 风险。

------

## 十九、前端设计

### 19.1 报告详情页新增执行轨迹 Tab

在 `ReviewReportView` 中增加：

```
执行轨迹
```

展示：

```
✅ 加载任务
✅ 获取 PR 信息
✅ 检查缓存：未命中
✅ 获取变更文件：3 个文件
✅ Spring AI 规划 Skill：FRONTEND_CHANGE
✅ 执行 FrontendReviewSkill
✅ 执行 GeneralReviewSkill
✅ 合并结果
✅ 计算风险评分：LOW / 28
✅ 生成最终报告
```

------

### 19.2 AgentTracePanel

新增：

```
src/components/review/AgentTracePanel.vue
```

字段：

```
stepName
status
message
durationMs
createdAt
detailJson
```

------

### 19.3 前端 API

新增：

```
src/api/agent.js
import request from './request'

export function listAgentTraces(taskId) {
  return request.get(`/api/review-tasks/${taskId}/agent-traces`)
}
```

------

## 二十、后端接口设计

### 20.1 获取执行轨迹

```
GET /api/review-tasks/{taskId}/agent-traces
```

返回：

```
[
  {
    "stepCode": "PLAN_SKILLS",
    "stepName": "制定 Skill 执行计划",
    "status": "SUCCESS",
    "message": "Spring AI 识别为 FRONTEND_CHANGE，选择 FrontendReviewSkill 和 GeneralReviewSkill",
    "durationMs": 800,
    "createdAt": "2026-05-30 21:30:14"
  }
]
```

------

### 20.2 获取 ReviewPlan，可选

```
GET /api/review-tasks/{taskId}/review-plan
```

V4 MVP 可以不单独做，先把计划摘要放入 `agent_trace.detail_json`。

------

## 二十一、异常处理设计

### 21.1 Spring AI Planner 失败

如果 Spring AI 规划失败：

```
1. 记录 agent_trace: PLAN_SKILLS FAILED
2. 回退到 RuleBasedReviewPlanner
3. 继续执行任务
```

不要因为规划失败导致整个任务失败。

------

### 21.2 Spring AI Review 失败

单个 Skill 调用失败：

```
1. review_skill_result.success = false
2. 记录 errorMessage
3. 继续执行其他 Skill
```

所有 Skill 都失败：

```
任务 FAILED
```

------

### 21.3 Structured Output 解析失败

处理方式：

```
1. 尝试 JSON 修复或提取 JSON 片段
2. 仍失败则标记当前 Skill 失败
3. 不影响其他 Skill
```

------

### 21.4 敏感信息保护

禁止记录：

```
API Key
GitHub Token
数据库密码
Redis 密码
完整 Prompt
完整 AI Response
完整 Diff
```

允许记录：

```
taskId
filePath
skillCode
callType
responseLength
promptLength
totalTokens
latencyMs
```

------

## 二十二、V4 MVP 实现范围

### P0：必须实现

```
1. 引入 Spring AI ChatClient
2. SpringAiLlmClient
3. SpringAiReviewPlanner
4. HybridReviewPlanner
5. ReviewAgent
6. agent_trace 表
7. AgentTraceRecorder
8. GET /api/review-tasks/{taskId}/agent-traces
9. 前端执行轨迹 Tab
```

------

### P1：建议实现

```
1. Structured Output 映射 ReviewPlan
2. Structured Output 映射 FileReviewResult
3. model_usage_log 记录 Spring AI 调用
4. Planner 失败回退规则规划
5. SkillResultMerger 增强去重
```

------

### P2：后续扩展

```
1. Spring AI Tool Calling
2. Spring AI Advisors / RAG 项目规范注入
3. Spring AI Observability 接入 Micrometer
4. LLM-as-a-Judge
5. 多模型切换
```

------

## 二十三、测试方案

### 23.1 Spring AI 调用测试

```
1. 配置 AI_BASE_URL / AI_API_KEY / AI_MODEL_NAME
2. 启动后端
3. 调用一个简单 SpringAiLlmClient 测试方法
4. 确认模型返回内容
```

------

### 23.2 ReviewPlan 生成测试

准备文件：

```
frontend/src/views/HomeView.vue
backend/src/main/java/com/example/aipr/service/ReviewTaskService.java
backend/src/main/resources/mapper/ReviewTaskMapper.xml
```

预期：

```
HomeView.vue → FRONTEND_REVIEW + GENERAL_REVIEW
ReviewTaskService.java → JAVA_REVIEW + GENERAL_REVIEW
ReviewTaskMapper.xml → SQL_REVIEW + GENERAL_REVIEW
```

------

### 23.3 Planner 失败回退测试

让 Spring AI 返回非法 JSON。

预期：

```
1. SpringAiReviewPlanner 失败
2. HybridReviewPlanner 回退到 RuleBasedReviewPlanner
3. 任务继续执行
4. agent_trace 记录回退信息
```

------

### 23.4 Agent 主流程测试

```
1. 创建 PR Review 任务
2. ReviewTaskExecutor 调用 ReviewAgent.run(taskId)
3. 等待任务 SUCCESS
4. 查询 review_comment
5. 查询 review_skill_result
6. 查询 agent_trace
```

------

### 23.5 前端测试

```
1. 打开 /tasks/{taskId}
2. 切换到“执行轨迹”Tab
3. 查看 Spring AI 规划步骤
4. 查看 Skill 执行步骤
```

------

## 二十四、Claude Code 编码提示词

```
你现在是我的 Java Spring Boot + Vue3 项目开发助手。当前项目是 AI PR Review 助手，已经完成 V1 主链路、V2 工程增强和 V3 Skill 化专项评审。现在需要实现 V4：基于 Spring AI 的 ReviewAgent 智能编排能力。

请按最小可行方案实现，不要大规模重构。

一、目标

引入 Spring AI，用于：
1. ChatClient 模型调用；
2. ReviewPlan 结构化生成；
3. 文件级 Review 结构化输出；
4. 后续 Tool Calling 扩展预留。

但业务主流程仍由自研 ReviewAgent 控制，不要让模型自由控制整个流程。

二、依赖

请根据当前 Spring Boot 版本添加 Spring AI BOM 和合适的 starter。

如果当前 AI 服务是 OpenAI-compatible，则优先使用 spring-ai-starter-model-openai。

如果 MiniMax 当前接口不兼容 Spring AI OpenAI Starter，则保留现有 MiniMaxLlmClient，并新增 SpringAiLlmClient 作为可选实现。

三、配置

在 application.yml 中新增或合并：

spring:
  ai:
    openai:
      api-key: ${AI_API_KEY:}
      base-url: ${AI_BASE_URL:}
      chat:
        options:
          model: ${AI_MODEL_NAME:}
          temperature: ${AI_TEMPERATURE:0.2}
          max-tokens: ${AI_MAX_TOKENS:3000}

review-agent:
  enabled: ${REVIEW_AGENT_ENABLED:true}
  planner-mode: ${REVIEW_AGENT_PLANNER_MODE:HYBRID}
  max-skills-per-file: ${REVIEW_AGENT_MAX_SKILLS_PER_FILE:3}
  max-files-per-task: ${REVIEW_AGENT_MAX_FILES_PER_TASK:30}
  max-patch-length: ${REVIEW_AGENT_MAX_PATCH_LENGTH:12000}
  agent-version: ${REVIEW_AGENT_VERSION:v1}

四、后端新增包

新增：
com.example.aipr.service.agent

包含：
1. ReviewAgent
2. ReviewAgentState
3. ReviewAgentStep
4. ReviewPlan
5. ReviewPlanItem
6. ReviewPlanner
7. RuleBasedReviewPlanner
8. SpringAiReviewPlanner
9. HybridReviewPlanner
10. AgentTraceRecorder

新增：
com.example.aipr.service.ai.springai

包含：
1. SpringAiConfig
2. SpringAiLlmClient
3. SpringAiStructuredOutputService
4. SpringAiPromptTemplates

五、ReviewAgentStep

定义：
LOAD_TASK
FETCH_PR_INFO
CHECK_CACHE
FETCH_CHANGED_FILES
PLAN_SKILLS
EXECUTE_SKILLS
MERGE_RESULTS
CALCULATE_RISK_SCORE
BUILD_REPORT
SAVE_RESULT
COMPLETED
FAILED

六、ReviewPlan

ReviewPlan 字段：
- taskId
- prType
- planSummary
- items

ReviewPlanItem 字段：
- fileId
- filePath
- language
- skipped
- skipReason
- skillCodes
- reason

七、Planner 设计

实现三种 Planner：

1. RuleBasedReviewPlanner
基于规则生成计划：
.vue/.js/.ts → FRONTEND_REVIEW + GENERAL_REVIEW
.java → JAVA_REVIEW + GENERAL_REVIEW
auth/token/password/security/config → SECURITY_REVIEW
.sql/mapper.xml → SQL_REVIEW + GENERAL_REVIEW
文档文件可跳过或 GENERAL_REVIEW

2. SpringAiReviewPlanner
使用 Spring AI ChatClient 生成 ReviewPlan。
使用结构化输出映射为 ReviewPlan。
输入只包含 PR 信息和文件元信息，不要输入完整 Diff。

3. HybridReviewPlanner
先调用 RuleBasedReviewPlanner 生成基础计划；
再调用 SpringAiReviewPlanner 修正计划；
最后 validateAndNormalize：
- 移除不存在的 skillCode；
- 每个文件最多 3 个 Skill；
- 安全路径强制追加 SECURITY_REVIEW；
- 没有 Skill 的代码文件追加 GENERAL_REVIEW；
- 文件 ID 必须存在。

如果 Spring AI 规划失败，回退到规则计划。

八、SpringAiLlmClient

实现使用 Spring AI ChatClient 调用模型。

功能：
1. 文件级 Review；
2. 结构化输出为 FileReviewResult；
3. 调用成功 / 失败都记录 model_usage_log；
4. 如果 usage 不可获取，token 记为 0；
5. 不打印完整 Prompt、完整 Response、API Key。

九、agent_trace 表

新增表：

CREATE TABLE IF NOT EXISTS agent_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',
    step_code VARCHAR(100) NOT NULL COMMENT '步骤编码',
    step_name VARCHAR(100) NOT NULL COMMENT '步骤名称',
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/RUNNING/SKIPPED',
    message VARCHAR(500) DEFAULT NULL COMMENT '步骤说明',
    detail_json TEXT DEFAULT NULL COMMENT '步骤详情 JSON',
    started_at DATETIME DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME DEFAULT NULL COMMENT '结束时间',
    duration_ms BIGINT DEFAULT NULL COMMENT '耗时',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_step_code (step_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 执行轨迹表';

新增 AgentTrace entity / mapper / XML。

十、ReviewAgent 主流程

实现 ReviewAgent.run(taskId)：

1. LOAD_TASK：加载任务
2. FETCH_PR_INFO：获取 PR 信息
3. CHECK_CACHE：检查数据库缓存
4. FETCH_CHANGED_FILES：获取变更文件
5. PLAN_SKILLS：HybridReviewPlanner 生成计划
6. EXECUTE_SKILLS：SkillEngine 按计划执行 Skill
7. MERGE_RESULTS：SkillResultMerger 合并结果
8. CALCULATE_RISK_SCORE：RiskScoreCalculator 计算分数
9. BUILD_REPORT：ReportBuilder 生成报告
10. SAVE_RESULT：保存结果
11. COMPLETED：成功
12. FAILED：失败

要求：
- 每一步记录 agent_trace；
- 缓存命中时不调用 AI；
- 单个 Skill 失败不导致整个任务失败；
- 所有 Skill 失败时任务失败；
- 不破坏现有前端轮询。

十一、接口

新增：
GET /api/review-tasks/{taskId}/agent-traces

返回某个任务的执行轨迹。

十二、前端

在 ReviewReportView 中新增 Tab：

执行轨迹

新增组件：
AgentTracePanel.vue

展示：
- stepName
- status
- message
- durationMs
- createdAt
- detailJson 可折叠

新增：
src/api/agent.js

方法：
listAgentTraces(taskId)

十三、安全要求

1. 不打印 API Key
2. 不打印 GitHub Token
3. 不打印完整 Prompt
4. 不打印完整 AI Response
5. 不打印完整 Diff
6. Spring AI 输出必须校验，不能直接信任
7. Tool Calling 只允许只读工具，暂不允许模型直接写数据库

十四、测试

请给出测试步骤：
1. Spring AI 配置是否可用；
2. SpringAiReviewPlanner 能否生成 ReviewPlan；
3. 非法 JSON 时是否回退 RuleBasedReviewPlanner；
4. 创建 PR Review 任务是否能 SUCCESS；
5. agent_trace 是否有记录；
6. ReviewReportView 执行轨迹 Tab 是否正常展示；
7. model_usage_log 是否记录 Spring AI 调用；
8. mvn clean package -DskipTests 是否通过；
9. npm run build 是否通过。

十五、输出要求

完成后输出：
1. 修改文件清单；
2. 新增数据库 SQL；
3. Spring AI 配置说明；
4. 新增类说明；
5. Agent 执行流程说明；
6. 前端页面说明；
7. 测试步骤；
8. 已知限制。

请先实现 V4 MVP：
Spring AI ChatClient + HybridReviewPlanner + ReviewAgent + agent_trace + 执行轨迹 Tab。
不要实现复杂多 Agent。
不要让模型自由写数据库。
```

------

## 二十五、最终建议

这个 V4 方案的重点是：

```
Spring AI 做模型能力和工具调用能力；
ReviewAgent 做业务编排；
SkillEngine 做专项评审；
AgentTrace 做可观测；
RiskScoreCalculator 做确定性评分。
```

最终架构可以概括为：

```
Spring Boot 自研 ReviewAgent
+ Spring AI ChatClient
+ Spring AI Structured Output
+ 可选 Spring AI Tool Calling
+ 自研 SkillEngine
+ 自研评测闭环
```

一句话总结：

**V4 可以用 Spring AI，但不要把整个 Agent 交给 Spring AI。最稳的做法是：Spring AI 负责 ChatClient、结构化输出和可选 Tool Calling，ReviewAgent 仍由 Spring Boot 业务代码确定性编排。**