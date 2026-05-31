# AI PR Review 助手

## 1. 项目简介

AI PR Review 助手是一个面向开发者的智能代码评审平台。用户输入 GitHub Pull Request 链接后，系统会自动获取 PR 基本信息和代码变更 Diff，并调用大模型进行智能分析，生成 PR 变更总结、风险代码识别和 Review 修改建议，帮助开发者提升 Pull Request Review 的效率与质量。

本项目聚焦代码评审中的真实需求，重点解决以下问题：

1. Reviewer 需要快速理解 PR 改了什么。
2. 人工 Review 容易遗漏潜在风险。
3. 新人开发者缺少代码审查经验。
4. PR Review 响应慢，影响团队协作效率。
5. 代码变更中常见的 Bug、安全、性能、可维护性问题需要提前发现。

本项目不是为了完全替代人工 Review，而是作为人工 Review 前的智能辅助工具，为开发者提供结构化、可解释、可追踪的 AI Review 报告。

------

## 2. 核心功能

### 2.1 PR 变更获取

用户输入 GitHub PR 链接后，系统自动解析：

```text
owner
repo
pullNumber
```

并调用 GitHub API 获取：

```text
PR 标题
PR 描述
PR 作者
源分支
目标分支
变更文件列表
新增 / 删除行数
Diff patch
```

------

### 2.2 AI PR 变更总结

系统根据 PR 标题、描述、文件路径和代码 Diff，自动生成本次 PR 的整体总结，包括：

```text
本次 PR 主要做了什么
涉及哪些模块
改动影响范围
是否存在需要重点关注的风险
```

------

### 2.3 风险代码识别

系统对代码变更进行智能分析，识别潜在风险，包括：

| 风险类型         | 说明               |
| ---------------- | ------------------ |
| BUG_RISK         | 可能导致运行时错误 |
| SECURITY_RISK    | 可能导致安全问题   |
| PERFORMANCE_RISK | 可能导致性能问题   |
| MAINTAINABILITY  | 可维护性问题       |
| STYLE            | 代码规范问题       |
| TEST_RISK        | 测试不足           |
| COMPATIBILITY    | 兼容性风险         |

风险等级包括：

| 等级   | 含义                     |
| ------ | ------------------------ |
| CRITICAL | 严重风险，建议修复并人工复核 |
| HIGH   | 高风险，建议必须修改     |
| MEDIUM | 中风险，建议优先修改     |
| LOW    | 低风险，可根据情况优化   |
| INFO   | 提示信息，不一定需要修改 |

------

### 2.4 Review 建议生成

系统会为每条风险生成 Review 建议，包括：

```text
文件路径
代码行号
风险类型
风险等级
问题描述
原因分析
风险证据
处理级别
修改建议
置信度
是否需要人工确认
```

------

### 2.5 Review 报告展示

前端页面展示完整 Review 报告，包括：

```text
PR 基本信息
任务状态
AI 总结
风险统计
文件级 Review 结果
Review 建议列表
复制建议
重新评审
历史记录
```

------

## 3. 技术栈

### 3.1 后端

| 技术               | 说明                         |
| ------------------ | ---------------------------- |
| Java 17            | 后端开发语言                 |
| Spring Boot 3      | 后端主框架                   |
| Spring Web         | REST API                     |
| MyBatis-Plus       | 数据库访问                   |
| MySQL 8            | 业务数据存储                 |
| Redis              | 缓存和任务状态，可选         |
| Spring Async       | 异步执行 Review 任务         |
| OkHttp / WebClient | 调用 GitHub API 和大模型 API |
| Jackson            | JSON 序列化与反序列化        |
| Knife4j / Swagger  | 接口文档                     |
| Docker             | 容器化部署                   |

------

### 3.2 前端

| 技术         | 说明           |
| ------------ | -------------- |
| Vue 3        | 前端框架       |
| Vite         | 构建工具       |
| Element Plus | UI 组件库      |
| Axios        | HTTP 请求      |
| Vue Router   | 前端路由       |
| Pinia        | 状态管理，可选 |

------

### 3.3 AI 能力

| 模块                   | 说明                                          |
| ---------------------- | --------------------------------------------- |
| OpenAI Compatible API  | 统一接入 DeepSeek、Qwen、OpenAI 等模型        |
| Prompt Template        | 管理不同评审场景的 Prompt                     |
| Review Pipeline        | 编排 PR 获取、Diff 解析、AI Review 和结果汇总 |
| JSON Structured Output | 要求模型输出结构化 JSON                       |
| Skill Engine           | 二期扩展，用于专项代码审查                    |
| RAG                    | 后期扩展，用于仓库上下文检索                  |
| Agent                  | 后期扩展，用于多步分析和工具调用              |

------

## 4. 系统架构

### 4.1 MVP 架构

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

------

### 4.2 扩展架构

后续可引入 Skill、RAG、Agent，实现更强的上下文理解和专项代码审查能力。

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
  └── Test Suggestion Skill
  ↓
LLM Client
  ↓
大模型 API
```

------

## 5. 核心流程

### 5.1 用户使用流程

```text
1. 用户输入 GitHub PR 链接
2. 点击“开始评审”
3. 系统创建 Review 任务
4. 系统获取 PR 信息和 Diff
5. 系统调用大模型进行分析
6. 系统生成 Review 报告
7. 用户查看 PR 总结、风险点和修改建议
8. 用户复制建议或重新发起 Review
```

------

### 5.2 系统执行流程

```text
创建任务
→ 解析 PR URL
→ 获取 PR 基本信息
→ 获取 changed files
→ 过滤无效文件
→ Diff 切分
→ 文件级 AI Review
→ PR 级汇总
→ 保存 Review 结果
→ 前端展示报告
```

------

## 6. 项目目录结构

### 6.1 后端目录结构

```text
backend
├── src/main/java/com/example/aipr
│   ├── common
│   ├── config
│   ├── controller
│   ├── service
│   │   ├── github
│   │   ├── diff
│   │   ├── ai
│   │   ├── review
│   │   ├── prompt
│   │   └── skill
│   ├── domain
│   ├── mapper
│   ├── dto
│   └── enums
│
├── src/main/resources
│   ├── application.yml
│   └── mapper
│
└── pom.xml
```

------

### 6.2 前端目录结构

```text
frontend
├── src
│   ├── api
│   ├── views
│   ├── components
│   ├── router
│   ├── stores
│   └── main.ts
│
├── package.json
└── vite.config.ts
```

------

### 6.3 文档目录结构

```text
docs
├── 01-产品需求文档-PRD.md
├── 02-技术设计文档-TDD.md
├── 03-数据库设计.md
├── 04-接口设计.md
├── 05-Prompt设计.md
└── 06-开发计划.md
```

------

## 7. 数据库核心表

MVP 阶段主要包括以下表：

| 表名            | 说明                          |
| --------------- | ----------------------------- |
| review_task     | Review 任务表                 |
| review_file     | PR 变更文件表                 |
| review_comment  | AI Review 建议表              |
| model_usage_log | 模型用量日志表                |
| model_config    | 模型配置表                    |
| prompt_template | Prompt 模板表                 |
| review_skill    | Review Skill 配置表，二期扩展 |

------

## 8. 核心接口

当前 dev 状态说明：

```text
已实现：健康检查、PR 预览、Review 任务创建、任务列表、任务详情、文件列表、建议列表、报告详情、配置状态、模型用量监控和监控统计。
已补齐：GitHub PR 列表、单个 PR 本地评审状态。
待补齐：GitHub Review Markdown 后端导出。
```

### 8.1 健康检查

```http
GET /api/health
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "service": "ai-pr-review-backend"
  }
}
```

------

### 8.2 GitHub PR 列表

```http
GET /api/github/pulls
```

当前状态：后端已实现，用于 PR 工作台直接选择仓库 PR 后发起评审。

Query 参数：owner, repo, state, page, pageSize

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "owner": "example",
        "repo": "demo",
        "pullNumber": 12,
        "title": "fix login bug",
        "author": "demo-user",
        "state": "OPEN",
        "sourceBranch": "feature/login",
        "targetBranch": "main",
        "reviewed": false,
        "latestTaskId": null,
        "cachedAvailable": false
      }
    ],
    "page": 1,
    "pageSize": 10
  }
}
```

------

### 8.3 查询单个 PR 本地评审状态

```http
GET /api/github/pulls/{owner}/{repo}/{pullNumber}/review-state
```

用于查询某个 PR 是否已有历史评审任务，以及当前 head commit 是否可复用历史成功报告。

------

### 8.4 创建 Review 任务

```http
POST /api/review-tasks
```

请求示例：

```json
{
  "prUrl": "https://github.com/example/demo/pull/12",
  "forceRefresh": false
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 10001,
    "status": "PENDING",
    "cached": false,
    "cachedFromTaskId": null
  }
}
```

------

### 8.5 查询 Review 任务列表

```http
GET /api/review-tasks
```

Query 参数：page, pageSize, status, riskLevel, keyword, createdFrom, createdTo

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "taskId": 12,
        "prUrl": "https://github.com/example/demo/pull/12",
        "prTitle": "fix login bug",
        "status": "SUCCESS",
        "riskScore": 78,
        "riskLevel": "HIGH",
        "cached": false,
        "createdAt": "2026-05-30 20:01:00"
      }
    ],
    "page": 1,
    "pageSize": 10,
    "total": 1,
    "pages": 1
  }
}
```

------

### 8.6 查询 Review 报告

```http
GET /api/review-tasks/{taskId}/report
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 12,
    "prInfo": {
      "title": "fix login bug",
      "author": "demo-user",
      "url": "https://github.com/example/demo/pull/12"
    },
    "summary": "本次 PR 主要修改了登录认证逻辑。",
    "riskScore": 78,
    "riskLevel": "HIGH",
    "riskItems": [
      {
        "filePath": "src/main/java/com/demo/auth/JwtUtil.java",
        "line": 35,
        "riskLevel": "HIGH",
        "riskType": "SECURITY_RISK",
        "title": "JWT 密钥存在硬编码风险",
        "description": "密钥直接写在源码中，公开仓库可能导致泄露。",
        "reason": "密钥属于敏感配置，硬编码会扩大泄露影响面。",
        "evidence": "private static final String SECRET = \"123456\";",
        "actionLevel": "MUST_FIX",
        "suggestion": "建议改为从环境变量或安全配置读取。",
        "confidence": 0.92,
        "needHumanCheck": true
      }
    ],
    "finalReview": "建议修复高风险问题后再合并。"
  }
}
```

------

### 8.7 模型用量监控

#### 查询用量概览

```http
GET /api/model-usage/summary
```

Query 参数：from, to, modelName

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalCalls": 18,
    "successCalls": 17,
    "failedCalls": 1,
    "successRate": 94.44,
    "totalPromptTokens": 12345,
    "totalCompletionTokens": 4321,
    "totalTokens": 16666,
    "avgLatencyMs": 1830
  }
}
```

#### 查询任务用量

```http
GET /api/model-usage/tasks/{taskId}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 12,
    "totalCalls": 5,
    "successCalls": 5,
    "failedCalls": 0,
    "totalPromptTokens": 8500,
    "totalCompletionTokens": 2100,
    "totalTokens": 10600,
    "avgLatencyMs": 1850
  }
}
```

#### 查询调用明细

```http
GET /api/model-usage/logs
```

Query 参数：page, pageSize, taskId, success, modelName

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "taskId": 12,
        "fileId": 31,
        "provider": "openai-compatible",
        "modelName": "deepseek-chat",
        "callType": "FILE_REVIEW",
        "promptTokens": 1200,
        "completionTokens": 360,
        "totalTokens": 1560,
        "latencyMs": 1800,
        "success": true,
        "createdAt": "2026-05-30 20:03:00"
      }
    ],
    "page": 1,
    "pageSize": 10,
    "total": 1,
    "pages": 1
  }
}
```

------

### 8.8 监控统计

#### 查询任务统计

```http
GET /api/review-tasks/statistics
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalTasks": 27,
    "todayTasks": 15,
    "successTasks": 21,
    "failedTasks": 6,
    "runningTasks": 0,
    "highRiskTasks": 15,
    "mediumRiskTasks": 2,
    "lowRiskTasks": 4,
    "cacheHits": 2,
    "avgDurationMs": 0.0
  }
}
```

`cacheHits` 表示命中历史报告缓存的任务数，工作台“缓存命中”卡片使用该字段。

#### 查询缓存统计

```http
GET /api/review-cache/statistics
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "cacheHits": 2,
    "cacheMisses": 25,
    "cacheHitRate": 7.4,
    "savedModelCalls": 2,
    "savedTokensEstimate": 10000
  }
}
```

------

## 9. 环境要求

### 9.1 基础环境

| 环境    | 版本建议   |
| ------- | ---------- |
| JDK     | 17+        |
| Maven   | 3.8+       |
| Node.js | 18+        |
| MySQL   | 8.0+       |
| Redis   | 6.0+，可选 |
| Git     | 2.0+       |
| Docker  | 可选       |

------

### 9.2 外部配置

需要准备：

```text
GitHub Token
大模型 API Key
大模型 Base URL
大模型名称
```

示例：

```text
GITHUB_TOKEN=ghp_xxx
AI_BASE_URL=https://api.deepseek.com/v1
AI_API_KEY=sk-xxx
AI_MODEL_NAME=deepseek-chat
```

------

## 10. 本地启动方式

### 10.1 克隆项目

```bash
git clone git@github.com:yourname/ai-pr-review.git
cd ai-pr-review
```

------

### 10.2 启动后端

进入后端目录：

```bash
cd backend
```

修改配置文件：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_pr_review?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password

github:
  token: ${GITHUB_TOKEN}

ai:
  base-url: ${AI_BASE_URL}
  api-key: ${AI_API_KEY}
  model-name: ${AI_MODEL_NAME}
```

启动后端：

```bash
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

------

### 10.3 启动前端

进入前端目录：

```bash
cd frontend
```

安装依赖：

```bash
npm install
```

启动项目：

```bash
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

------

## 11. Docker 部署规划

后续可使用 Docker Compose 部署：

```text
docker-compose.yml
├── frontend
├── backend
├── mysql
├── redis
└── nginx
```

部署架构：

```text
用户浏览器
  ↓
Nginx
  ├── 前端静态资源
  └── /api 反向代理到 Spring Boot
        ↓
      MySQL / Redis
```

------

## 12. AI Review 设计说明

### 12.1 模型选择

项目采用 OpenAI Compatible API 作为模型接入方式，便于统一适配：

```text
DeepSeek
Qwen
OpenAI
本地私有化模型
其他兼容 OpenAI API 的模型服务
```

模型选择重点考虑：

```text
代码理解能力
长上下文能力
JSON 输出稳定性
响应速度
调用成本
中文解释能力
```

------

### 12.2 上下文获取方式

MVP 阶段使用轻量上下文：

```text
PR 标题
PR 描述
文件路径
文件状态
Diff patch
新增 / 删除行数
```

后续可扩展为仓库级上下文：

```text
完整文件内容
相关类和方法
README
接口文档
数据库表结构
历史 Review 记录
向量数据库检索结果
```

------

### 12.3 误报控制

系统通过以下方式降低误报：

```text
要求模型只基于 Diff 内容分析
要求输出置信度 confidence
不确定问题标记 needHumanCheck=true
过滤空泛建议
低置信度结果降低展示优先级
结构化 JSON 输出后做字段校验
```

------

### 12.4 漏报控制

系统通过以下方式降低漏报：

```text
按文件逐个 Review
按风险类型清单逐项检查
对 Java、SQL、安全等场景使用专项 Prompt
对高风险文件二次分析
PR 级汇总时再次检查主要风险
后续引入 RAG 补充上下文
```

------

## 13. Skill 扩展设计

Skill 是本项目的二期扩展能力，用于将不同专项 Review 能力封装为可插拔模块。

示例：

| Skill                  | 说明              |
| ---------------------- | ----------------- |
| GeneralReviewSkill     | 通用代码评审      |
| JavaReviewSkill        | Java 后端代码审查 |
| SqlReviewSkill         | SQL 安全审查      |
| SecurityReviewSkill    | 安全漏洞审查      |
| PerformanceReviewSkill | 性能问题审查      |
| TestSuggestionSkill    | 测试建议生成      |

Skill 机制的作用：

```text
根据文件类型选择不同 Review 策略
根据代码语言选择不同 Prompt
让系统从“单一 Prompt”升级为“可插拔专项评审平台”
提高专业性和可扩展性
```

MVP 阶段不强制实现完整 Skill 平台，但代码结构中可以预留接口。

------

## 14. 项目开发计划

### 第一阶段：项目初始化

目标：

```text
创建 Spring Boot 后端项目
创建 Vue3 前端项目
配置 MySQL
跑通前后端接口
```

验收标准：

```text
后端可以启动
前端可以启动
前端可以调用后端 hello 接口
```

------

### 第二阶段：GitHub PR 获取

目标：

```text
实现 PR URL 解析
调用 GitHub API 获取 PR 信息
获取 changed files 和 patch
```

验收标准：

```text
输入 PR 链接后可以获取 PR 标题、作者、文件列表和 diff
```

------

### 第三阶段：Review 任务管理

目标：

```text
创建 review_task、review_file 表
实现任务创建接口
实现任务状态流转
保存 PR 文件信息
```

验收标准：

```text
可以创建 Review 任务
可以查询任务状态
数据库中有任务和文件记录
```

------

### 第四阶段：AI Review 接入

目标：

```text
实现大模型 API 调用
编写文件级 Review Prompt
解析模型 JSON 输出
保存 Review 建议
```

验收标准：

```text
可以生成文件级 Review 结果
可以识别风险代码
可以保存 Review 建议
```

------

### 第五阶段：Review 报告展示

目标：

```text
前端展示 PR 基本信息
展示 AI 总结
展示风险统计
展示文件级建议
支持复制建议
```

验收标准：

```text
用户可以看到完整 Review 报告
用户可以复制 Review 建议
```

------

### 第六阶段：优化与包装

目标：

```text
增加异常处理
增加文件过滤
增加历史记录
增加模型配置页面
补充 README 和答辩材料
```

验收标准：

```text
系统可以稳定演示
文档完整
具备答辩展示效果
```

------

## 15. 项目亮点

本项目亮点包括：

1. 基于 GitHub PR 的自动化代码变更获取。
2. 基于 Diff 的大模型代码评审。
3. 文件级 Review 和 PR 级汇总的两阶段分析流程。
4. 支持 PR 变更总结、风险识别和 Review 建议生成。
5. 使用结构化 JSON 输出，便于解析、存储和展示。
6. 支持风险类型、风险等级和置信度标注。
7. 通过 Prompt 约束降低误报和模型幻觉。
8. 采用异步任务执行，提升用户体验。
9. 模型调用统一抽象，支持 DeepSeek、Qwen、OpenAI 等模型切换。
10. 预留 Skill 机制，支持专项代码审查能力扩展。
11. 后续可扩展 RAG 和 Agent，提升上下文理解能力。
12. 可接入 GitHub Webhook 和 CI/CD，实现自动化 Review 流程。

------

## 16. 未来扩展方向

后续可以扩展：

```text
GitHub OAuth 登录
GitHub Webhook 自动触发 Review
自动评论到 GitHub PR
GitLab / Gitee 支持
仓库级 RAG 上下文检索
Agent 自动选择相关文件
多模型评审和结果投票
团队自定义 Review 规则
CI/CD 质量门禁
Review 结果反馈闭环
单元测试自动生成建议
安全专项扫描
```

------

## 17. 项目状态

当前项目处于设计与 MVP 开发阶段。

当前优先目标：

```text
输入 GitHub PR 链接
→ 获取 PR Diff
→ 调用 AI 模型
→ 生成 Review 报告
→ 前端展示
```

------

## 18. 许可证

```
MIT License

Copyright (c) 2026 码就对了

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

------

## 19. 联系方式

```text
作者：边峻峰、陈浩楠
邮箱：bianjunfeng2027@163.com
GitHub：https://github.com/bianjunfeng
```
