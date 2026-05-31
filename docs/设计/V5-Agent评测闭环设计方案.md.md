# V5：Agent 评测闭环设计方案

## 一、版本定位

V5 的目标是将 AI PR Review 项目从 **Agent 化智能编排系统** 升级为 **可评测、可反馈、可持续优化的 Agentic PR Review 平台**。

前面版本关系如下：

```text
V1 原始 MVP：
PR 链接 → GitHub Diff → AI Review → 报告展示

V2 工程增强：
日志优化、数据库缓存、Redis 限流、模型用量监控、固定评分

V3 Skill 化：
JavaReviewSkill、FrontendReviewSkill、SecurityReviewSkill、SqlReviewSkill 等专项能力

V4 Agent 化：
ReviewAgent 统一编排 PR 获取、缓存判断、Skill 选择、结果合并、风险评分和报告生成

V5 评测闭环：
对 ReviewAgent 的执行过程、输出质量、Token 成本和人工反馈进行评测，形成持续优化闭环
```

V5 的核心不是继续增加更多模型调用，而是解决一个更重要的问题：

```text
ReviewAgent 生成的评审结果到底好不好？
```

因此，V5 重点建设：

```text
AgentEvaluator + 人工反馈 + 质量指标 + 成本指标 + 评测看板
```

------

## 二、V5 核心目标

V5 主要实现以下目标：

1. 为每次 ReviewAgent 执行生成评测结果；
2. 统计任务是否成功、执行耗时、失败步骤；
3. 统计 Review 建议数量、风险等级分布；
4. 统计模型调用 Token、响应耗时和估算成本；
5. 支持用户对 Review 建议进行人工反馈；
6. 统计建议是否有用、是否误报、风险等级是否合理；
7. 为后续 Prompt 优化、Skill 优化、Agent 路由优化提供数据；
8. 为未来 SFT / DPO 微调数据构建提供基础。

------

## 三、为什么需要 V5 评测闭环

AI PR Review 项目如果只做到 V4，会具备：

```text
能获取 PR
能调度 Skill
能生成报告
能展示执行轨迹
```

但仍然存在问题：

```text
1. AI 建议是否准确不可量化；
2. 是否存在误报不可统计；
3. 是否存在漏报缺少反馈入口；
4. 风险等级是否合理无法沉淀；
5. 哪些 Skill 有用、哪些 Skill 噪声大不清楚；
6. Token 成本和 Review 质量之间缺少对比；
7. 后续优化 Prompt 没有依据；
8. 后续想做微调时缺少训练数据。
```

所以 V5 要引入评测闭环，让系统从：

```text
能生成 Review
```

升级为：

```text
能评价 Review，并根据反馈持续优化 Review
```

------

## 四、V5 总体架构

V5 在 V4 ReviewAgent 基础上增加评测层：

```text
ReviewAgent
├── GitHubTool
├── CacheTool
├── SkillEngine
├── SkillResultMerger
├── RiskScoreCalculator
├── ReportBuilder
├── AgentTraceRecorder
└── AgentEvaluator
    ├── ExecutionMetricEvaluator
    ├── ReviewQualityEvaluator
    ├── ModelUsageEvaluator
    ├── FeedbackEvaluator
    └── EvalSummaryBuilder
```

整体数据流：

```text
ReviewAgent 执行任务
→ 生成 review_task / review_file / review_comment
→ 记录 review_skill_result
→ 记录 model_usage_log
→ 记录 agent_trace
→ AgentEvaluator 汇总评测指标
→ 写入 agent_eval_result
→ 用户提交人工反馈
→ 写入 agent_feedback
→ 前端展示 Agent 评测结果
```

------

## 五、V5 核心模块

### 5.1 AgentEvaluator

`AgentEvaluator` 是 V5 的核心服务。

职责：

```text
1. 读取 review_task；
2. 读取 review_comment；
3. 读取 review_skill_result；
4. 读取 model_usage_log；
5. 读取 agent_trace；
6. 计算 Agent 执行指标；
7. 计算 Review 质量指标；
8. 计算模型成本指标；
9. 生成评测摘要；
10. 保存 agent_eval_result。
```

### 5.2 AgentFeedbackService

用于记录人工反馈。

职责：

```text
1. 接收用户对 Review 建议的反馈；
2. 保存反馈类型和反馈值；
3. 支持按 taskId / commentId 查询反馈；
4. 支持后续统计有用率、误报率、风险等级偏差。
```

### 5.3 AgentEvalDashboardService

用于聚合展示整体评测数据。

职责：

```text
1. 统计 Agent 成功率；
2. 统计平均建议数；
3. 统计平均 Token 消耗；
4. 统计平均耗时；
5. 统计人工反馈有用率；
6. 统计误报率；
7. 统计各 Skill 表现。
```

------

## 六、评测指标设计

V5 评测指标分为五类。

------

### 6.1 执行质量指标

用于衡量 Agent 是否稳定完成任务。

| 指标            | 说明             |
| --------------- | ---------------- |
| taskSuccess     | 任务是否成功     |
| executionTimeMs | Agent 总执行耗时 |
| stepCount       | Agent 执行步骤数 |
| failedStep      | 失败步骤         |
| retryCount      | 重试次数         |
| toolCallCount   | 工具调用次数     |
| skillCallCount  | Skill 调用次数   |

适合回答：

```text
Agent 是否稳定？
任务失败通常发生在哪一步？
执行是否太慢？
是否调用了过多工具？
```

------

### 6.2 Review 质量指标

用于衡量生成的 Review 建议质量。

| 指标               | 说明            |
| ------------------ | --------------- |
| commentCount       | Review 建议总数 |
| highCount          | 高风险建议数    |
| mediumCount        | 中风险建议数    |
| lowCount           | 低风险建议数    |
| infoCount          | 提示类建议数    |
| duplicateRate      | 重复建议比例    |
| actionableRate     | 可执行建议比例  |
| invalidCommentRate | 无效建议比例    |

其中：

```text
commentCount 太少 → 可能漏报
commentCount 太多 → 可能误报或噪声大
duplicateRate 高 → SkillResultMerger 需要优化
actionableRate 低 → Prompt 或 Skill 需要优化
```

------

### 6.3 准确性指标

V5 初期不直接做复杂自动准确率计算，因为缺少人工标注基准。

初期通过人工反馈统计：

| 指标                   | 来源                       |
| ---------------------- | -------------------------- |
| usefulRate             | 用户点击“有用”的比例       |
| falsePositiveRate      | 用户标记“误报”的比例       |
| riskTooHighRate        | 用户标记“风险过高”的比例   |
| riskTooLowRate         | 用户标记“风险过低”的比例   |
| actionableFeedbackRate | 用户标记“建议可执行”的比例 |

后续如果有人工标注评测集，可以增加：

```text
precision
recall
f1Score
falsePositive
falseNegative
```

------

### 6.4 稳定性指标

用于衡量同一 PR 在相同输入下是否稳定。

| 指标                    | 说明                         |
| ----------------------- | ---------------------------- |
| cacheHitRate            | 缓存命中率                   |
| samePrRiskLevelChanged  | 同一 PR 多次风险等级是否变化 |
| samePrScoreVariance     | 同一 PR 多次评分方差         |
| samePrCommentSimilarity | 同一 PR 多次建议相似度       |

V5 初期可以只实现：

```text
cacheHitRate
samePrRiskLevelChanged
```

复杂相似度计算可以后续再做。

------

### 6.5 成本指标

直接复用 V2 的 `model_usage_log`。

| 指标             | 说明                |
| ---------------- | ------------------- |
| totalTokens      | 总 Token 消耗       |
| promptTokens     | 输入 Token          |
| completionTokens | 输出 Token          |
| estimatedCost    | 估算成本            |
| avgLatencyMs     | 平均模型响应耗时    |
| modelCallCount   | 模型调用次数        |
| skillTokenUsage  | 各 Skill Token 消耗 |

适合回答：

```text
这次 Review 花了多少 Token？
哪个 Skill 最耗 Token？
是否存在过度调用模型？
Agent 成本是否可控？
```

------

## 七、数据库设计

V5 新增两张核心表：

```text
agent_eval_result
agent_feedback
```

------

### 7.1 agent_eval_result 表

用于保存每次 Agent 执行后的评测结果。

```sql
CREATE TABLE IF NOT EXISTS agent_eval_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',
    pr_url VARCHAR(512) DEFAULT NULL COMMENT 'PR URL',

    agent_name VARCHAR(100) DEFAULT 'ReviewAgent' COMMENT 'Agent 名称',
    agent_version VARCHAR(50) DEFAULT 'v1' COMMENT 'Agent 版本',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    prompt_version VARCHAR(50) DEFAULT NULL COMMENT 'Prompt 版本',

    task_success TINYINT(1) DEFAULT 1 COMMENT '任务是否成功',
    execution_time_ms BIGINT DEFAULT NULL COMMENT '执行耗时',
    step_count INT DEFAULT 0 COMMENT '执行步骤数',
    tool_call_count INT DEFAULT 0 COMMENT '工具调用次数',
    skill_call_count INT DEFAULT 0 COMMENT 'Skill 调用次数',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    failed_step VARCHAR(100) DEFAULT NULL COMMENT '失败步骤',

    comment_count INT DEFAULT 0 COMMENT 'Review 建议数',
    high_count INT DEFAULT 0 COMMENT '高风险数量',
    medium_count INT DEFAULT 0 COMMENT '中风险数量',
    low_count INT DEFAULT 0 COMMENT '低风险数量',
    info_count INT DEFAULT 0 COMMENT '提示数量',

    duplicate_rate DECIMAL(5,2) DEFAULT NULL COMMENT '重复建议比例',
    actionable_rate DECIMAL(5,2) DEFAULT NULL COMMENT '可执行建议比例',
    invalid_comment_rate DECIMAL(5,2) DEFAULT NULL COMMENT '无效建议比例',

    risk_score INT DEFAULT NULL COMMENT '最终风险分',
    risk_level VARCHAR(32) DEFAULT NULL COMMENT '最终风险等级',

    total_tokens INT DEFAULT 0 COMMENT '总 token',
    prompt_tokens INT DEFAULT 0 COMMENT '输入 token',
    completion_tokens INT DEFAULT 0 COMMENT '输出 token',
    estimated_cost DECIMAL(10,4) DEFAULT NULL COMMENT '估算成本',
    avg_latency_ms BIGINT DEFAULT NULL COMMENT '平均模型响应耗时',
    model_call_count INT DEFAULT 0 COMMENT '模型调用次数',

    cache_hit TINYINT(1) DEFAULT 0 COMMENT '是否命中缓存',
    cached_from_task_id BIGINT DEFAULT NULL COMMENT '缓存来源任务ID',

    eval_summary TEXT COMMENT '评测摘要',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_task_id (task_id),
    INDEX idx_agent_name (agent_name),
    INDEX idx_created_at (created_at),
    INDEX idx_risk_level (risk_level),
    INDEX idx_task_success (task_success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 评测结果表';
```

------

### 7.2 agent_feedback 表

用于保存用户对每条 Review 建议的人工反馈。

```sql
CREATE TABLE IF NOT EXISTS agent_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',
    comment_id BIGINT DEFAULT NULL COMMENT 'Review 评论ID',

    feedback_type VARCHAR(50) NOT NULL COMMENT '反馈类型',
    feedback_value VARCHAR(50) NOT NULL COMMENT '反馈值',
    human_note TEXT DEFAULT NULL COMMENT '人工备注',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_task_id (task_id),
    INDEX idx_comment_id (comment_id),
    INDEX idx_feedback_type (feedback_type),
    INDEX idx_feedback_value (feedback_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 人工反馈表';
```

------

## 八、人工反馈类型设计

### 8.1 feedback_type

| 类型           | 含义                         |
| -------------- | ---------------------------- |
| USEFUL         | 是否有用                     |
| FALSE_POSITIVE | 是否误报                     |
| RISK_LEVEL     | 风险等级是否合理             |
| ACTIONABLE     | 建议是否可执行               |
| FALSE_NEGATIVE | 是否存在漏报，通常任务级反馈 |

------

### 8.2 feedback_value

| 类型           | 可选值                       |
| -------------- | ---------------------------- |
| USEFUL         | YES / NO                     |
| FALSE_POSITIVE | YES / NO                     |
| RISK_LEVEL     | CORRECT / TOO_HIGH / TOO_LOW |
| ACTIONABLE     | YES / NO                     |
| FALSE_NEGATIVE | YES / NO                     |

示例：

```json
{
  "taskId": 11,
  "commentId": 14,
  "feedbackType": "USEFUL",
  "feedbackValue": "YES",
  "humanNote": "这条建议比较准确"
}
```

------

## 九、后端包结构设计

新增包：

```text
backend/src/main/java/com/example/aipr/service/eval
├── AgentEvaluator.java
├── EvalSummaryBuilder.java
├── FeedbackService.java
├── AgentEvalDashboardService.java
└── metric
    ├── ExecutionMetricEvaluator.java
    ├── ReviewQualityEvaluator.java
    ├── ModelUsageMetricEvaluator.java
    └── FeedbackMetricEvaluator.java
```

新增 Controller：

```text
backend/src/main/java/com/example/aipr/controller
├── AgentEvalController.java
└── AgentFeedbackController.java
```

新增实体和 Mapper：

```text
entity/
├── AgentEvalResult.java
└── AgentFeedback.java

mapper/
├── AgentEvalResultMapper.java
└── AgentFeedbackMapper.java
```

------

## 十、AgentEvaluator 设计

### 10.1 核心职责

```text
AgentEvaluator.evaluate(taskId)
```

执行流程：

```text
1. 读取 ReviewTask；
2. 读取 ReviewComment；
3. 读取 AgentTrace；
4. 读取 ReviewSkillResult；
5. 读取 ModelUsageLog；
6. 统计执行指标；
7. 统计质量指标；
8. 统计成本指标；
9. 生成 evalSummary；
10. 写入 agent_eval_result。
```

------

### 10.2 伪代码

```java
@Service
@RequiredArgsConstructor
public class AgentEvaluator {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewCommentMapper reviewCommentMapper;
    private final AgentTraceMapper agentTraceMapper;
    private final ReviewSkillResultMapper reviewSkillResultMapper;
    private final ModelUsageLogMapper modelUsageLogMapper;
    private final AgentEvalResultMapper agentEvalResultMapper;
    private final EvalSummaryBuilder evalSummaryBuilder;

    public void evaluate(Long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        List<ReviewComment> comments = reviewCommentMapper.findByTaskId(taskId);
        List<AgentTrace> traces = agentTraceMapper.findByTaskId(taskId);
        List<ReviewSkillResult> skillResults = reviewSkillResultMapper.findByTaskId(taskId);
        ModelUsageSummary usage = modelUsageLogMapper.summaryByTaskId(taskId);

        AgentEvalResult result = new AgentEvalResult();
        result.setTaskId(taskId);
        result.setPrUrl(task.getPrUrl());
        result.setAgentName("ReviewAgent");
        result.setAgentVersion("v1");
        result.setModelName(task.getModelName());
        result.setPromptVersion(task.getPromptVersion());

        result.setTaskSuccess("SUCCESS".equals(task.getStatus()));
        result.setExecutionTimeMs(calculateExecutionTime(traces, task));
        result.setStepCount(traces.size());
        result.setFailedStep(findFailedStep(traces));
        result.setSkillCallCount(skillResults.size());

        result.setCommentCount(comments.size());
        result.setHighCount(countByRiskLevel(comments, "HIGH"));
        result.setMediumCount(countByRiskLevel(comments, "MEDIUM"));
        result.setLowCount(countByRiskLevel(comments, "LOW"));
        result.setInfoCount(countByRiskLevel(comments, "INFO"));

        result.setRiskScore(task.getRiskScore());
        result.setRiskLevel(task.getRiskLevel());

        result.setTotalTokens(usage.getTotalTokens());
        result.setPromptTokens(usage.getPromptTokens());
        result.setCompletionTokens(usage.getCompletionTokens());
        result.setAvgLatencyMs(usage.getAvgLatencyMs());
        result.setModelCallCount(usage.getTotalCalls());
        result.setEstimatedCost(usage.getEstimatedCost());

        result.setCacheHit(Boolean.TRUE.equals(task.getCached()));
        result.setCachedFromTaskId(task.getCachedFromTaskId());

        result.setEvalSummary(evalSummaryBuilder.build(result));

        agentEvalResultMapper.insert(result);
    }
}
```

------

## 十一、评测摘要生成规则

`EvalSummaryBuilder` 根据指标生成摘要。

示例规则：

### 11.1 成功且低风险

```text
本次 Review 成功完成，生成 3 条建议，整体风险较低，未发现高风险问题。
```

### 11.2 存在高风险

```text
本次 Review 发现高风险问题，建议人工重点复核后再合并。
```

### 11.3 建议数量为 0

```text
本次 Review 未生成具体建议，建议人工确认是否存在漏报。
```

### 11.4 Token 消耗较高

```text
本次 Review Token 消耗较高，建议检查 Prompt 长度、Diff 截断策略或 Skill 路由策略。
```

### 11.5 任务失败

```text
本次 Review 执行失败，失败步骤为 FETCH_CHANGED_FILES，建议检查 GitHub API 或 PR 链接权限。
```

------

## 十二、评测触发时机

### 12.1 自动触发，推荐

在 ReviewAgent 执行完成后触发：

```text
ReviewAgent.run(taskId)
→ markSuccess(taskId)
→ agentEvaluator.evaluate(taskId)
```

如果任务失败，也可以触发失败评测：

```text
markFailed(taskId, error)
→ agentEvaluator.evaluate(taskId)
```

要求：

```text
评测失败不能影响主流程。
```

示例：

```java
try {
    agentEvaluator.evaluate(taskId);
} catch (Exception e) {
    log.warn("Agent evaluation failed, taskId={}, message={}", taskId, e.getMessage());
}
```

------

### 12.2 手动触发

提供接口：

```http
POST /api/agent-evals/tasks/{taskId}/run
```

用途：

```text
1. 修改评测规则后重新评测；
2. 修复历史数据；
3. 调试评测模块。
```

------

## 十三、后端接口设计

### 13.1 获取某任务评测结果

```http
GET /api/agent-evals/tasks/{taskId}
```

返回示例：

```json
{
  "taskId": 11,
  "agentName": "ReviewAgent",
  "agentVersion": "v1",
  "taskSuccess": true,
  "executionTimeMs": 21500,
  "stepCount": 8,
  "skillCallCount": 2,
  "commentCount": 3,
  "highCount": 0,
  "mediumCount": 0,
  "lowCount": 2,
  "infoCount": 1,
  "riskScore": 28,
  "riskLevel": "LOW",
  "totalTokens": 9800,
  "avgLatencyMs": 3200,
  "modelCallCount": 2,
  "cacheHit": false,
  "evalSummary": "本次 Review 成功完成，生成 3 条建议，整体风险较低。"
}
```

------

### 13.2 手动运行评测

```http
POST /api/agent-evals/tasks/{taskId}/run
```

返回：

```json
{
  "taskId": 11,
  "evaluated": true
}
```

------

### 13.3 提交人工反馈

```http
POST /api/agent-feedback
```

请求：

```json
{
  "taskId": 11,
  "commentId": 14,
  "feedbackType": "USEFUL",
  "feedbackValue": "YES",
  "humanNote": "这条建议比较准确"
}
```

------

### 13.4 查询任务反馈

```http
GET /api/agent-feedback/tasks/{taskId}
```

------

### 13.5 Agent 评测总览

```http
GET /api/agent-evals/summary
```

返回示例：

```json
{
  "totalTasks": 100,
  "successRate": 96.5,
  "avgCommentCount": 4.2,
  "avgRiskScore": 38.6,
  "avgTokens": 9200,
  "avgLatencyMs": 3100,
  "cacheHitRate": 24.0,
  "feedbackUsefulRate": 82.5,
  "falsePositiveRate": 8.3
}
```

------

## 十四、前端设计

### 14.1 报告详情页增加 Agent 评测 Tab

在 `ReviewReportView` 中新增：

```text
Agent 评测
```

展示内容：

```text
任务是否成功
执行耗时
执行步骤数
Skill 调用次数
Review 建议数量
风险等级分布
riskScore / riskLevel
Token 消耗
平均模型耗时
缓存命中状态
评测摘要
```

页面示例：

```text
Agent 评测结果

任务状态：成功
执行耗时：21.5s
执行步骤：8 步
Skill 调用：2 次
模型调用：2 次
生成建议：3 条
风险分布：LOW 2 / INFO 1
Token 消耗：9800
平均响应耗时：3200ms
缓存状态：未命中

评测结论：
本次 Review 成功完成，生成 3 条建议，整体风险较低，未发现高风险问题。
```

------

### 14.2 ReviewCommentItem 增加反馈按钮

每条 Review 建议下方增加：

```text
[有用] [无用] [误报] [风险过高] [风险过低]
```

点击后调用：

```http
POST /api/agent-feedback
```

反馈成功后提示：

```text
反馈已记录
```

------

### 14.3 AgentEvalDashboard，可选

后续可以新增页面：

```text
/agent-evals
```

展示：

```text
Agent 总体成功率
平均 Review 建议数
平均风险分
平均 Token 消耗
平均耗时
缓存命中率
人工反馈有用率
误报率
```

V5 初期可以先不单独做总览页，只在报告详情页展示。

------

## 十五、前端 API 设计

新增：

```text
src/api/agentEval.js
import request from './request'

export function getAgentEvalByTask(taskId) {
  return request.get(`/api/agent-evals/tasks/${taskId}`)
}

export function runAgentEval(taskId) {
  return request.post(`/api/agent-evals/tasks/${taskId}/run`)
}

export function getAgentEvalSummary(params) {
  return request.get('/api/agent-evals/summary', { params })
}

export function submitAgentFeedback(data) {
  return request.post('/api/agent-feedback', data)
}

export function listTaskFeedback(taskId) {
  return request.get(`/api/agent-feedback/tasks/${taskId}`)
}
```

------

## 十六、与 V2 / V3 / V4 的关系

### 16.1 依赖 V2 模型用量监控

V5 读取：

```text
model_usage_log
```

用于统计：

```text
totalTokens
promptTokens
completionTokens
avgLatencyMs
modelCallCount
estimatedCost
```

### 16.2 依赖 V3 Skill 结果

V5 读取：

```text
review_skill_result
```

用于统计：

```text
skillCallCount
Skill 成功率
Skill 输出建议数
Skill 失败情况
```

### 16.3 依赖 V4 Agent 执行轨迹

V5 读取：

```text
agent_trace
```

用于统计：

```text
stepCount
failedStep
executionTimeMs
```

所以 V5 是前面版本数据的汇总层。

------

## 十七、是否需要 LLM-as-a-Judge

V5 初期不建议直接加入 LLM-as-a-Judge。

### 17.1 当前阶段使用规则评测

优点：

```text
稳定
便宜
可解释
不消耗额外 Token
结果确定
```

适合评测：

```text
任务成功率
建议数量
风险分布
Token 消耗
耗时
人工反馈
```

### 17.2 后续可增加 LLM-as-a-Judge

后续可以让模型评价 Review 报告质量：

```text
准确性
完整性
可执行性
简洁性
是否有误报
是否存在漏报
```

但 LLM-as-a-Judge 有风险：

```text
本身也可能误判
会消耗额外 Token
结果也可能不稳定
不能完全替代人工反馈
```

所以推荐路线：

```text
第一阶段：规则评测 + 人工反馈
第二阶段：人工标注评测集
第三阶段：LLM-as-a-Judge 辅助评测
第四阶段：SFT / DPO 数据构建
```

------

## 十八、与微调数据闭环的关系

V5 人工反馈可以沉淀为微调数据。

例如：

```text
原始输入：
PR Diff + 文件路径 + Skill 类型

模型输出：
AI Review 建议

人工反馈：
有用 / 无用 / 误报 / 风险过高 / 风险过低

人工修正：
更好的 Review 建议
```

可以构造成：

### 18.1 SFT 数据

```json
{
  "instruction": "请对以下 PR Diff 进行代码评审",
  "input": "diff 内容",
  "output": "人工修正后的高质量 Review 建议"
}
```

### 18.2 DPO 数据

```json
{
  "prompt": "PR Diff + Review 任务说明",
  "chosen": "人工认为更好的 Review 建议",
  "rejected": "原始模型生成的低质量建议"
}
```

因此，V5 是未来模型优化和微调的基础。

------

## 十九、安全与稳定性要求

1. 不展示 API Key；
2. 不展示 GitHub Token；
3. 不展示数据库密码；
4. 不展示完整 Prompt；
5. 不展示完整 AI Response；
6. 人工备注需要限制长度；
7. 评测失败不能影响 Review 主任务；
8. 反馈接口需要参数校验；
9. 不允许重复提交完全相同反馈，或需要做幂等处理；
10. 所有接口返回统一 `Result<T>`。

------

## 二十、V5 MVP 实现范围

### P0：基础评测

```text
agent_eval_result 表
AgentEvaluator
EvalSummaryBuilder
任务完成后自动评测
GET /api/agent-evals/tasks/{taskId}
ReviewReportView 增加 Agent 评测 Tab
```

### P1：人工反馈

```text
agent_feedback 表
POST /api/agent-feedback
ReviewCommentItem 增加反馈按钮
GET /api/agent-feedback/tasks/{taskId}
```

### P2：评测总览

```text
GET /api/agent-evals/summary
AgentEvalDashboard
有用率
误报率
平均 Token
平均耗时
```

### P3：高级评测

```text
LLM-as-a-Judge
人工标注评测集
SFT / DPO 数据导出
Skill 级评测
Prompt 版本对比评测
```

------

## 二十一、测试方案

### 21.1 Agent 评测自动生成测试

步骤：

```text
1. 执行 agent_eval_result 建表 SQL；
2. 启动后端；
3. 创建一个 PR Review 任务；
4. 等待任务 SUCCESS；
5. 查询 agent_eval_result；
6. 确认 taskId 对应评测记录生成。
```

SQL：

```sql
SELECT * FROM agent_eval_result ORDER BY created_at DESC LIMIT 10;
```

------

### 21.2 获取任务评测结果测试

请求：

```http
GET /api/agent-evals/tasks/{taskId}
```

预期：

```text
返回任务成功状态、建议数量、风险分布、Token 消耗、评测摘要。
```

------

### 21.3 手动重新评测测试

请求：

```http
POST /api/agent-evals/tasks/{taskId}/run
```

预期：

```text
重新生成或更新 agent_eval_result。
```

------

### 21.4 人工反馈测试

请求：

```http
POST /api/agent-feedback
```

请求体：

```json
{
  "taskId": 11,
  "commentId": 14,
  "feedbackType": "USEFUL",
  "feedbackValue": "YES",
  "humanNote": "这条建议比较准确"
}
```

预期：

```text
agent_feedback 表新增记录。
```

------

### 21.5 前端测试

步骤：

```text
1. 打开 /tasks/{taskId}；
2. 切换到 Agent 评测 Tab；
3. 确认评测结果展示正常；
4. 在 Review 建议中点击“有用 / 误报”等反馈按钮；
5. 确认前端提示反馈成功。
```

------

### 21.6 构建测试

后端：

```bash
mvn clean package -DskipTests
```

前端：

```bash
npm run build
```

------

## 二十二、Claude Code 编码提示词

```text
你现在是我的 Java Spring Boot + Vue3 项目开发助手。当前项目是 AI PR Review 助手，已经完成 V1 主链路、V2 工程增强能力、V3 Skill 化专项评审能力和 V4 ReviewAgent 智能编排能力。现在需要实现 V5：Agent 评测闭环能力。

请按最小可行方案实现，不要大规模重构。

一、目标

为 ReviewAgent 增加评测闭环能力：

1. 每次 Review 任务完成后自动生成 Agent 评测结果；
2. 评测任务是否成功、执行耗时、步骤数量、失败步骤；
3. 评测 Review 建议数量和风险等级分布；
4. 统计 Token 消耗、模型调用次数和平均响应耗时；
5. 支持用户对 Review 建议提交人工反馈；
6. 前端报告详情页展示 Agent 评测结果；
7. 当前阶段只做规则评测，不做 LLM-as-a-Judge。

二、数据库

新增 agent_eval_result 表：

CREATE TABLE IF NOT EXISTS agent_eval_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',
    pr_url VARCHAR(512) DEFAULT NULL COMMENT 'PR URL',
    agent_name VARCHAR(100) DEFAULT 'ReviewAgent' COMMENT 'Agent 名称',
    agent_version VARCHAR(50) DEFAULT 'v1' COMMENT 'Agent 版本',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    prompt_version VARCHAR(50) DEFAULT NULL COMMENT 'Prompt 版本',
    task_success TINYINT(1) DEFAULT 1 COMMENT '任务是否成功',
    execution_time_ms BIGINT DEFAULT NULL COMMENT '执行耗时',
    step_count INT DEFAULT 0 COMMENT '执行步骤数',
    tool_call_count INT DEFAULT 0 COMMENT '工具调用次数',
    skill_call_count INT DEFAULT 0 COMMENT 'Skill 调用次数',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    failed_step VARCHAR(100) DEFAULT NULL COMMENT '失败步骤',
    comment_count INT DEFAULT 0 COMMENT 'Review 建议数',
    high_count INT DEFAULT 0 COMMENT '高风险数量',
    medium_count INT DEFAULT 0 COMMENT '中风险数量',
    low_count INT DEFAULT 0 COMMENT '低风险数量',
    info_count INT DEFAULT 0 COMMENT '提示数量',
    duplicate_rate DECIMAL(5,2) DEFAULT NULL COMMENT '重复建议比例',
    actionable_rate DECIMAL(5,2) DEFAULT NULL COMMENT '可执行建议比例',
    invalid_comment_rate DECIMAL(5,2) DEFAULT NULL COMMENT '无效建议比例',
    risk_score INT DEFAULT NULL COMMENT '最终风险分',
    risk_level VARCHAR(32) DEFAULT NULL COMMENT '最终风险等级',
    total_tokens INT DEFAULT 0 COMMENT '总 token',
    prompt_tokens INT DEFAULT 0 COMMENT '输入 token',
    completion_tokens INT DEFAULT 0 COMMENT '输出 token',
    estimated_cost DECIMAL(10,4) DEFAULT NULL COMMENT '估算成本',
    avg_latency_ms BIGINT DEFAULT NULL COMMENT '平均模型响应耗时',
    model_call_count INT DEFAULT 0 COMMENT '模型调用次数',
    cache_hit TINYINT(1) DEFAULT 0 COMMENT '是否命中缓存',
    cached_from_task_id BIGINT DEFAULT NULL COMMENT '缓存来源任务ID',
    eval_summary TEXT COMMENT '评测摘要',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_agent_name (agent_name),
    INDEX idx_created_at (created_at),
    INDEX idx_risk_level (risk_level),
    INDEX idx_task_success (task_success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 评测结果表';

新增 agent_feedback 表：

CREATE TABLE IF NOT EXISTS agent_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT 'Review 任务ID',
    comment_id BIGINT DEFAULT NULL COMMENT 'Review 评论ID',
    feedback_type VARCHAR(50) NOT NULL COMMENT '反馈类型',
    feedback_value VARCHAR(50) NOT NULL COMMENT '反馈值',
    human_note TEXT DEFAULT NULL COMMENT '人工备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_comment_id (comment_id),
    INDEX idx_feedback_type (feedback_type),
    INDEX idx_feedback_value (feedback_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 人工反馈表';

三、后端实体与 Mapper

新增：
- AgentEvalResult entity
- AgentEvalResultMapper
- AgentFeedback entity
- AgentFeedbackMapper

如果项目使用 XML Mapper，请补充对应 XML。

四、AgentEvaluator

新增 package：

com.example.aipr.service.eval

新增：
- AgentEvaluator
- EvalSummaryBuilder
- FeedbackService

AgentEvaluator.evaluate(taskId) 需要：
1. 读取 ReviewTask；
2. 读取 ReviewComment；
3. 读取 AgentTrace；
4. 读取 ReviewSkillResult；
5. 读取 ModelUsageLog；
6. 计算指标；
7. 生成 evalSummary；
8. 保存 agent_eval_result。

如果 model_usage_log 或 agent_trace 暂时没有数据，相关指标填 0，不影响主流程。

五、触发时机

在 ReviewAgent 或 ReviewTaskExecutor 中：

1. 任务 SUCCESS 后调用 agentEvaluator.evaluate(taskId)
2. 任务 FAILED 后也可以调用 agentEvaluator.evaluate(taskId)

要求：
评测失败不能影响主任务状态，只记录 warn 日志。

六、评测规则

当前阶段使用规则评测：

1. taskSuccess = review_task.status == SUCCESS
2. commentCount = review_comment 数量
3. highCount / mediumCount / lowCount / infoCount 按 riskLevel 统计
4. stepCount = agent_trace 数量
5. failedStep = agent_trace 中 status=FAILED 的步骤
6. skillCallCount = review_skill_result 数量
7. totalTokens / avgLatencyMs / modelCallCount 从 model_usage_log 汇总
8. riskScore / riskLevel 读取 review_task
9. evalSummary 根据规则生成

不要调用额外大模型进行评测。

七、接口

新增 AgentEvalController：

GET /api/agent-evals/tasks/{taskId}
获取某个任务的评测结果。

POST /api/agent-evals/tasks/{taskId}/run
手动重新评测。

GET /api/agent-evals/summary
获取整体评测统计，可选。

新增 AgentFeedbackController：

POST /api/agent-feedback
提交人工反馈。

GET /api/agent-feedback/tasks/{taskId}
查询某个任务的反馈。

八、人工反馈

支持以下 feedbackType：

USEFUL
FALSE_POSITIVE
RISK_LEVEL
ACTIONABLE
FALSE_NEGATIVE

支持以下 feedbackValue：

YES
NO
CORRECT
TOO_HIGH
TOO_LOW

humanNote 需要限制长度，例如最多 500 字。

九、前端

在 ReviewReportView 中新增 Tab：

Agent 评测

展示：
- taskSuccess
- executionTimeMs
- stepCount
- skillCallCount
- commentCount
- highCount / mediumCount / lowCount / infoCount
- riskScore / riskLevel
- totalTokens
- avgLatencyMs
- modelCallCount
- cacheHit
- evalSummary

在 ReviewCommentItem 中新增反馈按钮：
- 有用
- 无用
- 误报
- 风险过高
- 风险过低

点击后调用 POST /api/agent-feedback。

新增前端 API：
src/api/agentEval.js

包含：
- getAgentEvalByTask(taskId)
- runAgentEval(taskId)
- getAgentEvalSummary(params)
- submitAgentFeedback(data)
- listTaskFeedback(taskId)

十、安全与稳定性要求

1. 不展示 API Key
2. 不展示 GitHub Token
3. 不展示完整 Prompt
4. 不展示完整 AI Response
5. 不展示完整 Diff
6. 评测失败不能影响 Review 主流程
7. 人工反馈参数必须校验
8. 所有接口返回统一 Result<T>

十一、测试步骤

请给出测试步骤：

1. 执行 agent_eval_result 和 agent_feedback 建表 SQL
2. 启动后端
3. 创建一个 PR Review 任务
4. 等待任务 SUCCESS
5. 查询 agent_eval_result 是否生成记录
6. 调用 GET /api/agent-evals/tasks/{taskId}
7. 打开前端 /tasks/{taskId} 查看 Agent 评测 Tab
8. 对某条 Review 建议提交反馈
9. 查询 agent_feedback 表是否有记录
10. 执行 mvn clean package -DskipTests
11. 执行 npm run build

十二、输出要求

请输出：
1. 修改文件清单
2. 新增数据库 SQL
3. 新增后端类说明
4. 新增接口说明
5. 前端页面说明
6. 测试步骤
7. 已知限制

请先实现 V5 MVP：AgentEvaluator + agent_eval_result + agent_feedback + Agent 评测 Tab + 人工反馈按钮。
不要实现 LLM-as-a-Judge。
```

------

## 二十三、总结

V5 的核心是：

```text
让 ReviewAgent 具备可评测、可反馈、可持续优化能力。
```

最终效果：

```text
1. 每次 Agent 执行后都有评测结果；
2. 可以看到任务成功率、耗时、风险建议数量；
3. 可以看到 Token 消耗和模型调用成本；
4. 用户可以对每条建议反馈“有用 / 无用 / 误报”；
5. 系统可以沉淀优化 Prompt、Skill 和 Agent 的数据；
6. 后续可以进一步构建 SFT / DPO 微调数据。
```

推荐落地顺序：

```text
第一步：agent_eval_result 表
第二步：AgentEvaluator
第三步：报告详情页 Agent 评测 Tab
第四步：agent_feedback 表
第五步：Review 建议反馈按钮
第六步：Agent 评测总览页面
第七步：LLM-as-a-Judge 和微调数据导出
```