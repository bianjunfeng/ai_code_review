# AI PR Review 项目当前进度总结

> 更新时间：2026-05-30

---

## 1. 项目目标

构建一个 **AI PR Review 助手** 系统，实现：
- 输入 GitHub PR URL → 自动获取 PR 信息和代码变更
- 调用 AI 模型进行代码评审
- 生成结构化评审报告（风险项、测试建议、最终结论）
- 前端展示评审结果

**技术栈**：
- 后端：Java 17 + Spring Boot 3 + MyBatis-Plus 3.5.5 + OkHttp
- 前端：Vue 3 + Element Plus + Vite 6 + Axios
- AI：OpenAI Compatible API（支持 DeepSeek/Qwen）

---

## 2. 已完成任务

### 成员 B 职责范围内（AI Review 与报告展示链路）

| 阶段 | 完成内容 |
|------|----------|
| Phase 1 | 前端 API 对接（`/api/review-tasks` + `/api/review-tasks/{taskId}/report`） |
| Phase 2 | PromptRenderer + DTOs（AiReviewContext, FileReviewResult, FileReviewCommentResult） |
| Phase 3 | AiReviewOutputParser（Markdown 清理、JSON 解析、枚举校验、confidence 归一化） |
| Phase 4 | OpenAi Compatible LLM Client（OkHttp + RetryInterceptor 指数退避） |
| Phase 5 | AiReviewService（串联 Prompt、LLM、Parser，支持文件跳过规则） |
| Phase 6 | 报告聚合（PrInfoVO + ReviewReportVO） |
| Phase 7 | 前端展示完善（置信度百分比、无 comment 时拼接内容） |
| Phase 8 | 单元测试（PromptRendererTest、AiReviewOutputParserTest） |
| 代码审查 | 修复 13 个问题（DI、null检查、副作用、敏感信息泄露等） |

### 数据库持久化层（Entity/Mapper/MapperXML）

| 类型 | 数量 | 文件 |
|------|------|------|
| Entity | 5 | ReviewTask, ReviewFile, ReviewComment, ReviewSkillResult, ReviewSkill |
| Mapper 接口 | 5 | ReviewTaskMapper, ReviewFileMapper, ReviewCommentMapper, ReviewSkillResultMapper, ReviewSkillMapper |
| Mapper XML | 5 | 对应 5 个表的 SQL 配置 |
| **进行中** | 1 | ReviewTaskService 已修改为数据库持久化（待验证） |

---

## 3. 未完成任务

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 数据库初始化 | 高 | 需要执行 `db/schema.sql` 创建 5 张表 |
| DB 配置 | 高 | 配置 `DB_PASSWORD` 环境变量 |
| 持久化链路对接 | 高 | **进行中**：ReviewTaskService 已改为数据库，AiReviewService 待对接 |
| 任务状态轮询 | 中 | `GET /api/review-tasks/{taskId}` 接口 |
| GitHub API 对接 | 中 | 成员 A 负责，获取真实 PR 信息 |
| PR 级汇总 Review | 低 | 跨文件总结 |

---

## 4. 当前分支状态

| 项目 | 状态 |
|------|------|
| 本地分支 | `feature/ai-review-service` |
| 远程分支 | `origin/feature/ai-review-service` |
| 最新提交 | `2767db6` |
| 提交内容 | `feat: 新增 MyBatis-Plus 实体类和 Mapper，用于数据库持久化` |

**已推送的 Commit 历史（5个）**：
```
2767db6 feat: 新增 MyBatis-Plus 实体类和 Mapper，用于数据库持久化
6fb2dc5 feat: 新增 AI 评审服务，支持提示词渲染和输出解析
73f55c0 添加mybatis-plus与okhttp依赖
45c4794 docs: 修复文档一致性与 AGENTS.md 的偏差
648de1f chore: update application configuration
```

**当前未提交修改**：
```
modified: .claude/settings.local.json
modified: backend/src/main/java/com/example/aipr/service/review/ReviewTaskService.java
```

---

## 5. 最近修改的文件

### 后端新增文件

```
backend/src/main/java/com/example/aipr/
├── config/AiProperties.java
├── dto/
│   ├── AiReviewContext.java
│   ├── FileReviewCommentResult.java
│   └── FileReviewResult.java
├── service/
│   ├── ai/
│   │   ├── AiReviewService.java
│   │   ├── LlmClient.java
│   │   ├── LlmMessage.java
│   │   ├── LlmRequest.java
│   │   ├── LlmResponse.java
│   │   ├── OpenAiCompatibleClient.java
│   │   └── RetryInterceptor.java
│   └── prompt/
│       ├── AiReviewOutputParser.java
│       └── PromptRenderer.java
├── entity/
│   ├── ReviewTask.java
│   ├── ReviewFile.java
│   ├── ReviewComment.java
│   ├── ReviewSkillResult.java
│   └── ReviewSkill.java
├── mapper/
│   ├── ReviewTaskMapper.java
│   ├── ReviewFileMapper.java
│   ├── ReviewCommentMapper.java
│   ├── ReviewSkillResultMapper.java
│   └── ReviewSkillMapper.java
└── vo/PrInfoVO.java

backend/src/main/resources/
├── mapper/
│   ├── ReviewTaskMapper.xml
│   ├── ReviewFileMapper.xml
│   ├── ReviewCommentMapper.xml
│   ├── ReviewSkillResultMapper.xml
│   └── ReviewSkillMapper.xml
└── application.yml（已更新 MyBatis-Plus 配置）
```

### 后端修改文件

```
backend/src/main/java/com/example/aipr/
├── AiPrReviewBackendApplication.java（添加 @EnableConfigurationProperties）
├── enums/ErrorCode.java（新增 AI_SERVICE_ERROR、AI_RESPONSE_PARSE_ERROR）
├── service/review/ReviewTaskService.java（已改为数据库持久化，待验证）
├── service/report/ReviewReportService.java（添加 prInfo）
└── vo/ReviewReportVO.java（添加 prInfo 字段）
```

### 前端修改文件

```
frontend/src/
├── api/review.js（修复 API 路径，添加 unwrapResult 递归检查）
├── components/RiskItemCard.vue（置信度格式化、无 comment 时拼接）
└── views/HomeView.vue（增强 PR URL 验证、错误处理）
```

### 测试文件

```
backend/src/test/java/com/example/aipr/service/
├── prompt/PromptRendererTest.java
└── prompt/AiReviewOutputParserTest.java
```

---

## 6. 后续任务清单

### 紧急（数据库可用前必须完成）

```bash
# 1. 数据库初始化
mysql -u root -p -e "CREATE DATABASE ai_code_review DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p ai_code_review < backend/src/main/resources/db/schema.sql

# 2. 配置环境变量
export DB_PASSWORD=your_password
export AI_API_KEY=your_api_key

# 3. 验证 ReviewTaskService 数据库持久化
cd backend && mvn compile

# 4. 修改 AiReviewService 使用数据库
```

### 中期（完善功能链路）

1. ReviewTaskService 持久化改造 - **进行中**
2. 添加任务状态轮询接口 `GET /api/review-tasks/{taskId}`
3. 对接成员 A 的 GitHub API 获取真实 PR 信息
4. 实现文件级 Review 结果保存到 review_file
5. 实现评论保存到 review_comment

### 长期（优化体验）

1. PR 级汇总 Review（跨文件总结）
2. 批量插入优化
3. 任务取消功能
4. 历史任务查询

---

## 7. 重要约束和注意事项

### 技术约束

| 约束 | 说明 |
|------|------|
| 不暴露 API Key | 日志中不打印 key 值 |
| 不返回 Entity | 数据库 Entity 不直接返回前端，使用 VO |
| 不硬编码配置 | 使用环境变量或 application.yml |
| 敏感信息处理 | 错误消息不包含技术细节 |

### 代码规范

| 项目 | 规范 |
|------|------|
| Commit Message | 使用 Conventional Commits（feat/fix/docs/style/refactor/test/chore） |
| 分支命名 | `feature/` 新功能，`fix/` 修复 |
| PR 描述 | 必须包含测试情况和风险点 |

### 依赖关系

| 依赖 | 成员 A 提供 | 成员 B 使用 |
|------|------------|-------------|
| 真实 GitHub PR 信息 | 成员 A | Prompt 中填充 PR 标题、描述等 |
| review_task 持久化 | 成员 A | 报告按 taskId 查询 |
| review_file 持久化 | 成员 A | 文件级 Review 保存 |
| 任务状态轮询接口 | 成员 A | 前端轮询 PENDING→SUCCESS |

---

## 8. 代码审查修复清单（13个问题）

| # | 问题 | 修复文件 | 修复方式 |
|---|------|----------|----------|
| 1 | ObjectMapper DI | OpenAiCompatibleClient.java | 构造器注入 |
| 2 | 无效对象引用 | AiReviewService.java | 添加 Collections import |
| 3 | truncateForLog 不清晰 | AiReviewOutputParser.java | 添加 MAX_LOG_LENGTH 常量 |
| 4 | URL 空指针 | OpenAiCompatibleClient.java | 防御性检查 + 末尾/规范化 |
| 5 | 副作用修改输入 | PromptRenderer.java | 移除 context.setTruncated() |
| 6 | 错误信息泄露 | HomeView.vue | 增强错误处理逻辑 |
| 7 | NaN 显示 | RiskItemCard.vue | formatConfidence() 函数 |
| 8 | null 检查缺失 | AiReviewOutputParser.java | parseComment 添加 null 检查 |
| 9 | 嵌套错误码未处理 | review.js | unwrapResult 递归检查 |
| 10 | 分支为空无提示 | PromptRenderer.java | 显示"未指定" |
| 11 | API Key 安全 | OpenAiCompatibleClient.java | 移除 @RequiredArgsConstructor |
| 12 | PR URL 验证不足 | HomeView.vue | 检查 pull number 范围 |
| 13 | 缺少重试机制 | RetryInterceptor.java | 指数退避策略 |

---

## 9. 数据库 Schema（5张表）

| 表名 | 主键 | 用途 |
|------|------|------|
| review_task | id | PR 分析任务 |
| review_file | id | PR 变更文件 |
| review_comment | id | Review 评论 |
| review_skill_result | id | Skill 执行结果 |
| review_skill | id | Skill 配置 |

**SQL 文件位置**：`backend/src/main/resources/db/schema.sql`

---

## 10. 环境变量配置

```bash
# 数据库
DB_URL=jdbc:mysql://127.0.0.1:3306/ai_code_review
DB_USERNAME=ai_review_user
DB_PASSWORD=<password>

# AI
AI_BASE_URL=https://api.deepseek.com
AI_API_KEY=<api_key>
AI_MODEL_NAME=deepseek-chat
AI_TEMPERATURE=0.2

# GitHub
GITHUB_TOKEN=<token>
```

---

## 11. 当前进度总结

| 维度 | 完成度 | 说明 |
|------|--------|------|
| AI Review 服务 | 90% | Prompt、Parser、LLM Client、Retry 均已完成 |
| 前端展示 | 85% | 报告展示、错误处理、空状态均已处理 |
| 数据库持久化 | 55% | Entity/Mapper 已创建，ReviewTaskService 已改造为数据库持久化 |
| 单元测试 | 40% | 只覆盖 Prompt 和 Parser |
| 文档同步 | 30% | 需同步接口文档 |

**下一步最优先工作**：
1. 验证 ReviewTaskService 编译通过
2. 数据库初始化并测试
3. AiReviewService 对接数据库持久化