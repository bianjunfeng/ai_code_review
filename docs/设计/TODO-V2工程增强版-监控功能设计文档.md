# V2 工程增强版：监控功能设计文档

## 一、文档背景

当前 AI PR Review 项目已经完成基础主链路：

```text
输入 GitHub PR 链接
→ 获取 PR 信息
→ 获取 Diff 文件
→ 调用 AI 模型生成 Review
→ 保存评审结果
→ 前端轮询状态
→ 展示报告
```

在 V1 MVP 阶段，系统重点是“跑通功能”。但如果要让项目更像真实工程系统，需要在 V2 工程增强版中补充监控能力。

V2 监控功能主要解决以下问题：

1. 模型调用是否成功；
2. 每次 Review 消耗了多少 Token；
3. AI 调用耗时是否过长；
4. 哪些任务失败了；
5. 失败原因是什么；
6. 缓存是否命中；
7. Redis 限流是否触发；
8. 系统配置是否完整；
9. 当前后端、数据库、AI 服务是否可用；
10. 后续 Skill / Agent 执行是否可观测。

因此，V2 监控功能不是单纯的“页面展示”，而是项目工程化能力的一部分。

------

## 二、V2 监控功能定位

V2 监控功能定位为：

```text
面向 AI PR Review 工具的轻量级运行监控与模型用量监控系统。
```

它不追求一开始就做到 Prometheus + Grafana + ELK 的完整生产级方案，而是优先通过数据库表、后端接口和前端监控页面实现项目内部可观测能力。

后续如果系统继续升级，可以再接入：

```text
Prometheus
Grafana
ELK
Langfuse
OpenTelemetry
```

但当前阶段优先实现轻量、可演示、可解释的监控能力。

------

## 三、监控功能总体目标

V2 监控功能包括五类：

```text
1. 模型用量监控
2. Review 任务监控
3. 系统配置状态监控
4. 缓存命中监控
5. Redis 限流监控
```

其中 P0 优先实现：

```text
模型用量监控
Review 任务监控
系统配置状态监控
```

P1 再补充：

```text
缓存命中监控
Redis 限流监控
```

P2 后续扩展：

```text
Skill 执行监控
Agent 执行轨迹监控
Agent 评测监控
```

------

## 四、监控功能整体架构

### 4.1 后端架构

```text
AI PR Review Backend
│
├── ReviewTask 模块
│   ├── 记录任务状态
│   ├── 记录任务成功 / 失败
│   └── 提供任务统计接口
│
├── ModelUsage 模块
│   ├── 记录模型调用次数
│   ├── 记录 Token 消耗
│   ├── 记录调用耗时
│   └── 提供模型用量统计接口
│
├── ConfigStatus 模块
│   ├── 检查 GitHub Token
│   ├── 检查 AI Key
│   ├── 检查数据库连接
│   └── 检查 Redis 连接
│
├── CacheMonitor 模块
│   ├── 记录缓存命中
│   ├── 记录缓存未命中
│   └── 统计缓存命中率
│
└── RateLimitMonitor 模块
    ├── 记录限流触发次数
    ├── 记录限流类型
    └── 统计被拦截请求
```

------

### 4.2 前端架构

前端新增一个一级菜单：

```text
模型监控 / Model Usage
```

并在工作台 Dashboard 中增加监控摘要。

前端页面结构：

```text
DashboardView
├── 今日任务统计
├── 最近任务状态
├── 配置状态卡片
├── 缓存命中摘要
└── 模型用量摘要

ModelUsageView
├── 筛选栏
├── 模型调用统计卡片
├── Token 用量趋势
├── 调用明细表
└── 任务维度用量查询

SettingsView
└── 系统配置状态检查

ReviewReportView
└── 当前任务模型用量卡片
```

------

## 五、监控模块一：模型用量监控

### 5.1 功能目标

模型用量监控用于记录和展示每一次 AI 模型调用情况，包括：

```text
调用了哪个模型
属于哪个任务
属于哪个文件
属于哪个调用阶段
消耗了多少 Token
调用耗时多久
调用是否成功
失败原因是什么
```

该功能可以解决：

```text
Token 成本不可见
模型调用失败不好排查
不同任务耗时无法比较
后续 Skill / Agent 成本无法统计
```

------

### 5.2 数据库表设计

新增表：

```sql
CREATE TABLE IF NOT EXISTS model_usage_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT DEFAULT NULL COMMENT 'Review 任务ID',
    file_id BIGINT DEFAULT NULL COMMENT 'Review 文件ID',
    skill_code VARCHAR(100) DEFAULT NULL COMMENT 'Skill 编码，V2 可为空',

    provider VARCHAR(50) DEFAULT NULL COMMENT '模型供应商，如 minimax/deepseek/openai/local',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',

    call_type VARCHAR(50) NOT NULL COMMENT '调用类型，如 FILE_REVIEW/PR_SUMMARY/FINAL_REVIEW',
    prompt_tokens INT DEFAULT 0 COMMENT '输入 token 数',
    completion_tokens INT DEFAULT 0 COMMENT '输出 token 数',
    total_tokens INT DEFAULT 0 COMMENT '总 token 数',

    latency_ms BIGINT DEFAULT NULL COMMENT '响应耗时毫秒',
    success TINYINT(1) DEFAULT 1 COMMENT '是否成功',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',

    estimated_cost DECIMAL(10, 4) DEFAULT NULL COMMENT '估算成本，V2 可为空',
    request_id VARCHAR(128) DEFAULT NULL COMMENT '模型平台请求ID',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_task_id (task_id),
    INDEX idx_file_id (file_id),
    INDEX idx_model_name (model_name),
    INDEX idx_created_at (created_at),
    INDEX idx_call_type (call_type),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用用量日志表';
```

------

### 5.3 字段说明

| 字段              | 说明                                     |
| ----------------- | ---------------------------------------- |
| task_id           | 对应一次 Review 任务                     |
| file_id           | 对应某个变更文件                         |
| skill_code        | 后续 Skill 化后记录哪个 Skill 调用了模型 |
| provider          | 模型供应商，例如 minimax                 |
| model_name        | 模型名称，例如 MiniMax-M2.7              |
| call_type         | 调用类型，例如 FILE_REVIEW               |
| prompt_tokens     | 输入 Token 数                            |
| completion_tokens | 输出 Token 数                            |
| total_tokens      | 总 Token 数                              |
| latency_ms        | 模型响应耗时                             |
| success           | 调用是否成功                             |
| error_message     | 失败原因                                 |
| estimated_cost    | 估算成本，后续扩展                       |
| request_id        | 模型平台请求 ID                          |

------

### 5.4 调用类型设计

当前 V2 建议支持：

```text
FILE_REVIEW       文件级 Review
PR_SUMMARY        PR 总结
FINAL_REVIEW      最终建议生成
```

后续 Skill / Agent 版本可扩展：

```text
SKILL_REVIEW
AGENT_PLANNING
AGENT_REFLECTION
AGENT_FINALIZE
```

------

### 5.5 后端记录方式

推荐在统一模型调用入口记录，例如：

```text
LlmClient
OpenAiCompatibleClient
AiReviewService
```

最推荐放在统一的 `LlmClient` 中，避免遗漏。

调用流程：

```text
1. 构造 LlmCallContext
2. 记录 startTime
3. 调用模型 API
4. 解析 usage 字段
5. 计算 latencyMs
6. 写入 model_usage_log
7. 返回模型结果
```

失败时：

```text
1. 捕获异常
2. 记录 latencyMs
3. success=false
4. errorMessage 保存简短错误信息
5. 重新抛出异常或返回业务错误
```

注意：

```text
不记录完整 Prompt
不记录完整 AI Response
不记录 API Key
不记录 GitHub Token
```

------

### 5.6 LlmCallContext 设计

```java
public class LlmCallContext {

    private Long taskId;

    private Long fileId;

    private String skillCode;

    private String callType;
}
```

示例：

```java
LlmCallContext context = new LlmCallContext();
context.setTaskId(taskId);
context.setFileId(fileId);
context.setCallType("FILE_REVIEW");
```

------

### 5.7 后端接口设计

#### 5.7.1 模型用量总览

```http
GET /api/model-usage/summary?startDate=2026-05-30&endDate=2026-05-30
```

返回示例：

```json
{
  "totalCalls": 32,
  "successCalls": 31,
  "failedCalls": 1,
  "totalPromptTokens": 98200,
  "totalCompletionTokens": 30250,
  "totalTokens": 128450,
  "estimatedCost": 3.21,
  "avgLatencyMs": 2860,
  "successRate": 96.8
}
```

------

#### 5.7.2 模型调用明细

```http
GET /api/model-usage/logs?page=1&pageSize=20&taskId=9&success=true
```

返回示例：

```json
{
  "total": 2,
  "records": [
    {
      "id": 1,
      "taskId": 9,
      "fileId": 6,
      "skillCode": null,
      "provider": "minimax",
      "modelName": "MiniMax-M2.7",
      "callType": "FILE_REVIEW",
      "promptTokens": 5120,
      "completionTokens": 860,
      "totalTokens": 5980,
      "latencyMs": 3200,
      "success": true,
      "estimatedCost": null,
      "createdAt": "2026-05-30 19:44:48"
    }
  ]
}
```

------

#### 5.7.3 单任务模型用量

```http
GET /api/model-usage/tasks/{taskId}
```

返回示例：

```json
{
  "taskId": 9,
  "totalCalls": 2,
  "successCalls": 2,
  "failedCalls": 0,
  "totalPromptTokens": 7720,
  "totalCompletionTokens": 1280,
  "totalTokens": 9000,
  "estimatedCost": null,
  "avgLatencyMs": 3200,
  "logs": []
}
```

------

#### 5.7.4 Token 趋势，可选

```http
GET /api/model-usage/trend?range=today&groupBy=hour
```

V2 可先不实现复杂图表，预留接口即可。

------

### 5.8 前端页面设计：ModelUsageView

路由：

```text
/model-usage
```

左侧菜单名称：

```text
模型监控
```

页面结构：

```text
模型用量监控

筛选条件：
[时间范围] [模型] [调用类型] [状态] [查询]

统计卡片：
调用次数 / Token 总消耗 / Prompt Tokens / Completion Tokens / 平均耗时 / 成功率

Token 趋势：
按小时或按天展示 Token 消耗

调用明细：
模型调用记录表
```

------

### 5.9 前端组件设计

```text
src/views/ModelUsageView.vue

src/components/model-usage/
├── ModelUsageFilterBar.vue
├── ModelUsageSummaryCards.vue
├── ModelUsageTrendPanel.vue
└── ModelUsageLogTable.vue
```

------

### 5.10 前端 API 封装

新增：

```text
src/api/modelUsage.js
import request from './request'

export function getModelUsageSummary(params) {
  return request.get('/api/model-usage/summary', { params })
}

export function listModelUsageLogs(params) {
  return request.get('/api/model-usage/logs', { params })
}

export function getTaskModelUsage(taskId) {
  return request.get(`/api/model-usage/tasks/${taskId}`)
}

export function getModelUsageTrend(params) {
  return request.get('/api/model-usage/trend', { params })
}
```

------

## 六、监控模块二：Review 任务监控

### 6.1 功能目标

Review 任务监控用于展示系统中 Review 任务的运行状态，包括：

```text
任务总数
成功任务数
失败任务数
运行中任务数
不同风险等级数量
最近失败任务
任务平均耗时
```

该功能可以用于 Dashboard 和任务中心。

------

### 6.2 基于现有表实现

Review 任务监控主要基于已有表：

```text
review_task
```

需要关注字段：

```text
id
pr_url
owner_name
repo_name
pr_number
pr_title
status
risk_score
risk_level
error_message
created_at
updated_at
```

如果后续要统计执行耗时，可以新增：

```sql
ALTER TABLE review_task
ADD COLUMN started_at DATETIME DEFAULT NULL COMMENT '任务开始时间',
ADD COLUMN finished_at DATETIME DEFAULT NULL COMMENT '任务结束时间',
ADD COLUMN duration_ms BIGINT DEFAULT NULL COMMENT '任务执行耗时';
```

V2 可以暂时用：

```text
updated_at - created_at
```

粗略估算耗时。

------

### 6.3 任务状态

建议统一任务状态：

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

------

### 6.4 后端接口设计

#### 6.4.1 最近任务列表

```http
GET /api/review-tasks?page=1&pageSize=10
```

支持可选筛选：

```text
status
riskLevel
repoName
keyword
```

------

#### 6.4.2 任务统计接口

```http
GET /api/review-tasks/statistics
```

返回示例：

```json
{
  "totalTasks": 120,
  "todayTasks": 12,
  "successTasks": 96,
  "failedTasks": 6,
  "runningTasks": 3,
  "highRiskTasks": 5,
  "mediumRiskTasks": 18,
  "lowRiskTasks": 73,
  "avgDurationMs": 18500
}
```

------

#### 6.4.3 最近失败任务

```http
GET /api/review-tasks/recent-failures?page=1&pageSize=5
```

返回示例：

```json
{
  "records": [
    {
      "taskId": 15,
      "prTitle": "feat: add auth service",
      "status": "FAILED",
      "errorMessage": "AI API request timeout",
      "createdAt": "2026-05-30 20:10:12"
    }
  ]
}
```

------

### 6.5 前端展示

Dashboard 中展示：

```text
今日任务数
成功报告数
失败任务数
高风险 PR 数
运行中任务数
```

任务中心展示：

```text
任务列表
状态筛选
风险等级筛选
失败原因
查看报告
重新分析
```

------

## 七、监控模块三：系统配置状态监控

### 7.1 功能目标

系统配置状态监控用于在前端展示当前系统关键配置是否可用。

适合答辩、联调和部署排错。

------

### 7.2 检查项

```text
GitHub Token 是否配置
GitHub API 是否可访问
AI API Key 是否配置
AI Base URL
AI Model Name
数据库是否连接
Redis 是否连接
Prompt Version
缓存是否启用
限流是否启用
```

------

### 7.3 后端接口设计

```http
GET /api/config/status
```

返回示例：

```json
{
  "githubTokenConfigured": true,
  "githubApiReachable": true,
  "aiKeyConfigured": true,
  "aiBaseUrl": "https://api.minimaxi.com/v1",
  "modelName": "MiniMax-M2.7",
  "databaseConnected": true,
  "redisConnected": true,
  "promptVersion": "v1",
  "cacheEnabled": true,
  "rateLimitEnabled": true
}
```

注意：

```text
不能返回真实 GitHub Token
不能返回真实 AI API Key
不能返回数据库密码
不能返回 Redis 密码
```

------

### 7.4 前端展示

在 Dashboard 中展示简洁状态：

```text
GitHub Token ✅
AI Key ✅
Database ✅
Redis ✅
Cache ✅
RateLimit ✅
```

在 Settings 页面展示完整状态：

```text
系统配置状态

GitHub：
- Token 已配置
- API 可访问

AI：
- Key 已配置
- Base URL: https://api.minimaxi.com/v1
- Model: MiniMax-M2.7

Database：
- 已连接

Redis：
- 已连接

Review：
- Prompt Version: v1
- Cache: 已启用
- Rate Limit: 已启用
```

------

## 八、监控模块四：缓存命中监控

### 8.1 功能目标

缓存命中监控用于展示数据库报告缓存是否有效。

该功能对应 V2 中的数据库缓存机制：

```text
同 PR + headSha + modelName + promptVersion
如果已有 SUCCESS 报告，直接复用历史结果
```

------

### 8.2 建议记录方式

在 `review_task` 中增加字段：

```sql
ALTER TABLE review_task
ADD COLUMN cached TINYINT(1) DEFAULT 0 COMMENT '是否命中缓存',
ADD COLUMN cached_from_task_id BIGINT DEFAULT NULL COMMENT '缓存来源任务ID';
```

也可以只通过返回值显示，不落库。但为了后续统计，建议落库或新增缓存事件表。

------

### 8.3 缓存统计指标

```text
缓存命中次数
缓存未命中次数
缓存命中率
节省模型调用次数
节省 Token 估算
```

------

### 8.4 后端接口设计

```http
GET /api/review-cache/statistics
```

返回示例：

```json
{
  "cacheHits": 12,
  "cacheMisses": 38,
  "cacheHitRate": 24.0,
  "savedModelCalls": 12,
  "savedTokensEstimate": 98000
}
```

------

### 8.5 前端展示

Dashboard 显示：

```text
缓存命中 12 次
缓存命中率 24%
```

任务详情页显示：

```text
缓存状态：已命中历史报告
缓存来源任务：#9
```

------

## 九、监控模块五：Redis 限流监控

### 9.1 功能目标

Redis 限流监控用于展示系统是否发生过限流，以及哪些接口被频繁访问。

------

### 9.2 V2 最小实现

V2 最小实现可以先不单独建表，只在日志中记录：

```text
限流类型
Redis Key
限制次数
时间窗口
请求来源 IP
```

示例日志：

```text
Request blocked by rate limit, key=rate_limit:ip:127.0.0.1:create-review, limit=5, windowSeconds=60
```

------

### 9.3 P1 增强：新增限流日志表

后续可以新增：

```sql
CREATE TABLE IF NOT EXISTS rate_limit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    limit_key VARCHAR(255) NOT NULL COMMENT 'Redis 限流 Key',
    limit_type VARCHAR(50) NOT NULL COMMENT '限流类型',
    ip VARCHAR(64) DEFAULT NULL COMMENT '客户端 IP',
    owner_name VARCHAR(100) DEFAULT NULL COMMENT '仓库 owner',
    repo_name VARCHAR(100) DEFAULT NULL COMMENT '仓库名',
    pr_number INT DEFAULT NULL COMMENT 'PR 编号',
    limit_count INT DEFAULT NULL COMMENT '限制次数',
    window_seconds INT DEFAULT NULL COMMENT '时间窗口',
    message VARCHAR(255) DEFAULT NULL COMMENT '提示信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_limit_type (limit_type),
    INDEX idx_created_at (created_at),
    INDEX idx_ip (ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限流触发日志表';
```

------

### 9.4 限流类型

```text
CREATE_REVIEW_IP
CREATE_REVIEW_PR
FORCE_REFRESH
GITHUB_PULLS
AI_CALL_GLOBAL
```

------

### 9.5 后端接口设计，P1 可选

```http
GET /api/rate-limit/logs?page=1&pageSize=20
GET /api/rate-limit/statistics
```

统计返回：

```json
{
  "totalBlocked": 36,
  "createReviewBlocked": 10,
  "forceRefreshBlocked": 5,
  "githubPullsBlocked": 18,
  "aiCallBlocked": 3
}
```

------

## 十、V2 监控前端页面汇总

### 10.1 DashboardView

展示系统总览：

```text
今日任务数
成功报告数
失败任务数
高风险 PR 数
缓存命中次数
模型 Token 消耗
系统配置状态
最近评审任务
最近失败任务
```

------

### 10.2 ModelUsageView

展示模型用量：

```text
调用次数
Token 总消耗
Prompt Tokens
Completion Tokens
平均耗时
成功率
调用明细
```

------

### 10.3 ReviewTaskListView

展示任务监控：

```text
任务列表
状态筛选
风险等级筛选
失败原因
查看报告
重新分析
```

------

### 10.4 SettingsView

展示配置状态：

```text
GitHub 配置
AI 配置
数据库连接
Redis 连接
缓存开关
限流开关
Prompt Version
```

------

### 10.5 ReviewReportView

展示单任务监控：

```text
任务状态
风险评分
模型用量
缓存状态
执行轨迹
```

------

## 十一、接口清单

### 11.1 模型用量接口

| 方法 | 路径                              | 说明             |
| ---- | --------------------------------- | ---------------- |
| GET  | `/api/model-usage/summary`        | 模型用量总览     |
| GET  | `/api/model-usage/logs`           | 模型调用明细     |
| GET  | `/api/model-usage/tasks/{taskId}` | 单任务模型用量   |
| GET  | `/api/model-usage/trend`          | Token 趋势，可选 |

------

### 11.2 任务监控接口

| 方法 | 路径                                | 说明         |
| ---- | ----------------------------------- | ------------ |
| GET  | `/api/review-tasks`                 | 任务列表     |
| GET  | `/api/review-tasks/statistics`      | 任务统计     |
| GET  | `/api/review-tasks/recent-failures` | 最近失败任务 |

------

### 11.3 配置状态接口

| 方法 | 路径                 | 说明         |
| ---- | -------------------- | ------------ |
| GET  | `/api/config/status` | 系统配置状态 |

------

### 11.4 缓存监控接口

| 方法 | 路径                           | 说明               |
| ---- | ------------------------------ | ------------------ |
| GET  | `/api/review-cache/statistics` | 缓存命中统计，可选 |

------

### 11.5 限流监控接口

| 方法 | 路径                         | 说明           |
| ---- | ---------------------------- | -------------- |
| GET  | `/api/rate-limit/logs`       | 限流日志，可选 |
| GET  | `/api/rate-limit/statistics` | 限流统计，可选 |

------

## 十二、前端路由设计

```js
const routes = [
  {
    path: '/',
    component: AppLayout,
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue')
      },
      {
        path: 'tasks',
        name: 'ReviewTasks',
        component: () => import('@/views/ReviewTaskListView.vue')
      },
      {
        path: 'tasks/:taskId',
        name: 'ReviewReport',
        component: () => import('@/views/ReviewReportView.vue')
      },
      {
        path: 'model-usage',
        name: 'ModelUsage',
        component: () => import('@/views/ModelUsageView.vue')
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/SettingsView.vue')
      }
    ]
  }
]
```

------

## 十三、前端组件设计

```text
components/
├── dashboard/
│   ├── StatCard.vue
│   ├── ConfigStatusCard.vue
│   ├── RecentTaskList.vue
│   └── RecentFailureList.vue
│
├── model-usage/
│   ├── ModelUsageFilterBar.vue
│   ├── ModelUsageSummaryCards.vue
│   ├── ModelUsageTrendPanel.vue
│   └── ModelUsageLogTable.vue
│
├── review/
│   ├── ReviewStatusTag.vue
│   ├── RiskLevelTag.vue
│   ├── RiskScoreCard.vue
│   └── TaskUsageCard.vue
│
└── settings/
    └── ConfigStatusPanel.vue
```

------

## 十四、API 封装设计

### 14.1 modelUsage.js

```js
import request from './request'

export function getModelUsageSummary(params) {
  return request.get('/api/model-usage/summary', { params })
}

export function listModelUsageLogs(params) {
  return request.get('/api/model-usage/logs', { params })
}

export function getTaskModelUsage(taskId) {
  return request.get(`/api/model-usage/tasks/${taskId}`)
}

export function getModelUsageTrend(params) {
  return request.get('/api/model-usage/trend', { params })
}
```

------

### 14.2 review.js

```js
import request from './request'

export function listReviewTasks(params) {
  return request.get('/api/review-tasks', { params })
}

export function getReviewTaskStatistics() {
  return request.get('/api/review-tasks/statistics')
}

export function listRecentFailures(params) {
  return request.get('/api/review-tasks/recent-failures', { params })
}
```

------

### 14.3 config.js

```js
import request from './request'

export function getConfigStatus() {
  return request.get('/api/config/status')
}
```

------

### 14.4 cache.js，可选

```js
import request from './request'

export function getReviewCacheStatistics() {
  return request.get('/api/review-cache/statistics')
}
```

------

### 14.5 rateLimit.js，可选

```js
import request from './request'

export function getRateLimitStatistics() {
  return request.get('/api/rate-limit/statistics')
}

export function listRateLimitLogs(params) {
  return request.get('/api/rate-limit/logs', { params })
}
```

------

## 十五、安全要求

监控功能必须注意安全边界。

### 15.1 不允许展示

```text
GitHub Token
AI API Key
数据库密码
Redis 密码
完整 Prompt
完整 AI Response
完整 PR Diff
```

### 15.2 可以展示

```text
是否配置 Token
是否配置 API Key
模型名称
Base URL
Token 数量
调用耗时
调用状态
错误摘要
```

### 15.3 错误信息处理

错误信息需要截断，例如最多保存或展示 500 字。

```java
private String truncateError(String errorMessage) {
    if (errorMessage == null) {
        return null;
    }
    return errorMessage.length() > 500
            ? errorMessage.substring(0, 500)
            : errorMessage;
}
```

------

## 十六、日志要求

V2 监控功能应配合日志优化。

默认环境不打印 MyBatis SQL 明细。

```yaml
logging:
  level:
    root: INFO
    com.example.aipr: INFO
    com.example.aipr.mapper: WARN
    org.mybatis: WARN
    com.baomidou.mybatisplus: WARN
    com.zaxxer.hikari: WARN
```

业务日志建议：

```text
Model usage recorded, taskId=9, model=MiniMax-M2.7, totalTokens=5980, latencyMs=3200
Review task completed, taskId=9, riskScore=28, riskLevel=LOW
Config status checked, databaseConnected=true, redisConnected=true
```

不建议记录：

```text
完整 prompt
完整 response
完整 patch
API key
token
password
```

------

## 十七、V2 监控落地优先级

### P0：必须实现

```text
1. model_usage_log 表
2. 模型调用用量记录
3. /api/model-usage/summary
4. /api/model-usage/logs
5. /api/model-usage/tasks/{taskId}
6. ModelUsageView 页面
7. /api/config/status
8. Dashboard 配置状态卡片
```

------

### P1：建议实现

```text
1. review_task statistics
2. 最近失败任务
3. 缓存命中统计
4. ReviewReportView 展示当前任务模型用量
5. SettingsView 配置状态页
```

------

### P2：后续扩展

```text
1. Token 趋势图
2. 成本估算
3. Redis 限流统计
4. Skill 用量统计
5. Agent 执行监控
6. Agent 评测中心
```

------

## 十八、测试方案

### 18.1 模型用量记录测试

步骤：

```text
1. 启动后端；
2. 创建一个 PR Review 任务；
3. 等待任务执行完成；
4. 查询 model_usage_log 表；
5. 确认有模型调用记录；
6. 检查 total_tokens、latency_ms、success 是否正常。
```

SQL：

```sql
SELECT * FROM model_usage_log ORDER BY created_at DESC LIMIT 10;
```

------

### 18.2 模型用量接口测试

```http
GET /api/model-usage/summary
GET /api/model-usage/logs?page=1&pageSize=20
GET /api/model-usage/tasks/9
```

预期：

```text
接口返回调用次数、Token 数、平均耗时和调用明细。
```

------

### 18.3 配置状态接口测试

```http
GET /api/config/status
```

预期：

```text
返回 GitHub、AI、数据库、Redis、缓存和限流状态。
不返回任何真实密钥。
```

------

### 18.4 前端页面测试

步骤：

```text
1. 启动前端；
2. 进入 /model-usage；
3. 查看统计卡片；
4. 查看调用明细表；
5. 进入 /settings；
6. 查看系统配置状态；
7. 进入某个 /tasks/{taskId}；
8. 查看当前任务模型用量。
```

------

### 18.5 构建测试

后端：

```bash
mvn clean package -DskipTests
```

前端：

```bash
npm run build
```

------

## 十九、与后续 Skill / Agent 的关系

V2 监控功能是后续 Skill 和 Agent 的基础。

### 19.1 对 Skill 的支持

后续 Skill 版可以在 `model_usage_log` 中记录：

```text
skill_code
call_type = SKILL_REVIEW
```

这样可以统计：

```text
哪个 Skill 最耗 Token
哪个 Skill 调用失败最多
哪个 Skill 平均耗时最高
```

------

### 19.2 对 Agent 的支持

后续 Agent 版可以扩展：

```text
agent_trace
agent_eval_result
```

模型用量监控可以继续复用：

```text
taskId
callType
totalTokens
latencyMs
success
```

Agent 评测也可以直接读取模型用量数据：

```text
totalTokens
avgLatencyMs
modelCallCount
estimatedCost
```

------

## 二十、总结

V2 监控功能的核心目标是让 AI PR Review 项目从“能跑通”升级为“可观测、可排查、可评估、可控成本”的工程系统。

最终实现效果：

```text
1. 可以看到每次模型调用消耗了多少 Token；
2. 可以看到每次模型调用是否成功；
3. 可以看到任务执行状态和失败原因；
4. 可以看到系统配置是否完整；
5. 可以看到缓存和限流是否生效；
6. 可以为后续 Skill / Agent / 评测闭环提供数据基础。
```

推荐落地顺序：

```text
第一步：model_usage_log + 模型调用记录
第二步：ModelUsageController + ModelUsageView
第三步：ConfigStatusController + SettingsView
第四步：任务统计 + Dashboard 监控卡片
第五步：缓存 / 限流统计扩展
```