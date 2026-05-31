# AI PR Review 助手 Prompt 设计文档

## 1. 文档说明

本文档用于定义 **AI PR Review 助手** 的 Prompt 设计方案，适用于后端通过 OpenAI Compatible API 调用 DeepSeek、Qwen、OpenAI 或其他兼容模型生成结构化代码评审结果。

本项目的核心链路为：

```text
输入 GitHub PR URL
→ 获取 PR 基本信息、变更文件和 diff
→ 规则扫描识别显性风险
→ 大模型结合上下文进行代码评审
→ 输出结构化 Review 报告
→ 前端展示总结、风险项、测试建议和最终评审结论
```

Prompt 设计采用“**规则扫描 + 大模型分析 + 轻量 Skill 化架构**”的方式：

- 规则扫描负责快速识别硬编码密钥、危险 API、配置暴露、大范围删除等确定性线索。
- 大模型负责结合 PR 上下文、diff 内容和规则扫描结果生成可读、可执行的 Review 建议。
- 轻量 Skill 化架构将总结、风险识别、Review 建议、测试建议拆成独立 Prompt，便于 MVP 快速落地和后续扩展。

本文档中的 Prompt 模板可直接用于后端 `PromptBuilder` 或 `PromptRenderer`，模板变量使用 `{variable_name}` 格式。

## 2. Prompt 设计目标

Prompt 设计需要同时满足代码评审质量、工程稳定性和前端展示结构化的要求。

| 目标 | 说明 |
| --- | --- |
| 准确性 | 只基于给定 PR 信息和 diff 分析，不编造未提供的业务背景、代码文件或历史实现。 |
| 稳定性 | 使用低 temperature、明确角色和固定 JSON 输出格式，降低同一输入下的输出波动。 |
| 可解析性 | 所有核心 Prompt 优先输出合法 JSON，便于后端通过 Jackson 反序列化为 DTO / VO。 |
| 可执行性 | 每条建议必须包含问题描述、原因、证据、修改建议和置信度，避免泛泛而谈。 |
| 误报控制 | 要求模型给出 evidence 和 confidence，低置信度问题不作为阻塞项。 |
| 漏报控制 | 强制检查安全、权限、配置、SQL、异常、日志、测试、性能和大范围删除等风险。 |
| MVP 可落地 | 不依赖复杂 Agent 编排，使用少量 Skill Prompt 即可完成核心评审链路。 |

## 3. Prompt 设计原则

1. **只基于给定 PR 上下文分析**

   模型只能使用输入中的 PR 标题、描述、commit、changed files、diff patch 和规则扫描结果，不允许假设仓库中未提供的代码实现。

2. **不确定时降低置信度**

   如果 diff 不完整、调用链缺失、配置来源不清楚，应将 `confidence` 降低，并设置 `needHumanCheck=true`。

3. **风险点必须可定位、可解释、可处理**

   每个风险点必须包含 `filePath`、`description`、`reason`、`suggestion`、`confidence`、`evidence`、`actionLevel`，便于前端展示和人工复核。

4. **输出优先使用 JSON**

   后端需要稳定解析模型结果，因此 Prompt 必须明确要求“只输出合法 JSON，不要输出 Markdown，不要输出代码块标记”。

5. **明确区分风险等级**

   风险等级分为 `CRITICAL`、`HIGH`、`MEDIUM`、`LOW`、`INFO`。严重/高风险建议修改后再合并，中风险建议确认，低风险作为优化提醒。

6. **结合规则扫描结果，但不盲目相信规则扫描结果**

   规则扫描结果是线索，不是最终结论。模型需要结合 diff 和上下文验证规则扫描结果是否成立。

7. **上下文不足时明确说明需要进一步确认**

   对于无法仅凭 diff 判断的问题，应在 `comment` 或 `reason` 中说明“需要进一步确认”，并降低置信度。

8. **控制输出长度**

   每个 Prompt 都限制输出条数和字段长度，避免生成大量无效建议。

9. **面向真实 Code Review 场景**

   建议应具体到可执行动作，例如“将密钥改为从环境变量读取”“补充 token 过期测试”，而不是“注意安全性”。

10. **低 temperature 保证稳定性**

    代码评审任务建议 `temperature=0.1~0.3`，默认推荐 `0.2`。

## 4. 上下文输入设计

### 4.1 输入上下文字段

后端在构造 Prompt 前，应将 GitHub API、规则扫描和 diff 预处理结果组合为统一上下文。

| 字段 | 说明 | 示例变量 |
| --- | --- | --- |
| PR 标题 | Pull Request 标题 | `{pr_title}` |
| PR 描述 | Pull Request body | `{pr_description}` |
| PR 作者 | GitHub 用户名 | `{pr_author}` |
| 源分支 | head ref | `{source_branch}` |
| 目标分支 | base ref | `{target_branch}` |
| commit 列表 | commit message、作者、提交时间 | `{commits}` |
| 变更文件列表 | 文件路径、状态、增删行数 | `{changed_files}` |
| diff 摘要 | 预处理后的 diff 摘要 | `{diff_summary}` |
| diff patch | 原始或裁剪后的 patch | `{diff}` |
| 规则扫描结果 | 规则命中的风险线索 | `{rule_risks}` |
| 高风险文件标记 | 配置、认证、权限、数据库等文件 | `{high_risk_files}` |
| 测试文件变更 | 是否新增或修改测试文件 | `{has_test_changes}` |

MVP 阶段的 `StaticRuleScanner` 只做轻量扫描，不引入外部工具。当前基础规则包括：

- 硬编码密钥：识别 token、secret、password、API Key 等直接写入新增代码的线索。
- SQL 风险：识别 SQL 字符串拼接和 MyBatis `${}` 原始替换。
- 临时输出：识别 `System.out.println` 和 `console.log`。
- 异常吞没：识别新增空 `catch` 块。

这些结果只作为 Prompt 中的 `evidence` 线索，模型必须结合 diff 判断是否成立，不得把规则命中直接泛化为最终风险。

### 4.2 推荐上下文结构

```text
PR 信息:
{pr_info}

Commit 列表:
{commits}

变更文件:
{changed_files}

高风险文件:
{high_risk_files}

规则扫描结果:
{rule_risks}

Diff 摘要:
{diff_summary}

Diff 内容:
{diff}
```

### 4.3 上下文裁剪策略

当 diff 或 prompt 过长时，后端应先裁剪上下文，而不是让模型调用失败。

| 优先级 | 保留内容 |
| --- | --- |
| P0 | 规则扫描命中的文件和对应 diff hunk |
| P0 | 认证、权限、配置、数据库、支付、接口层、序列化、日志相关代码 |
| P1 | 大范围删除、新增核心类、修改公共方法签名的文件 |
| P1 | 测试文件变更和测试缺失线索 |
| P2 | 普通业务代码 diff |
| P3 | 大量格式化、注释、静态资源、构建产物、锁文件 |

裁剪规则建议：

1. 单文件 patch 超过 12000 characters 时保留文件头、命中的 hunk、核心新增代码和结尾摘要。
2. 单次模型输入超过 20000 characters 时优先保留高风险文件和规则命中内容。
3. 单个 PR 最大 Review 文件数建议限制为 30。
4. 对低风险大文件只保留摘要，例如文件路径、状态、增删行数、主要变更片段。
5. 被裁剪的上下文需要在 Prompt 中标记：`当前 diff 已被截断，模型需要避免对缺失上下文作确定性结论。`

## 5. 统一输出 JSON 设计

### 5.1 统一报告 JSON

统一报告用于前端展示完整 PR Review 结果，也可作为 `UnifiedReviewReport Prompt` 的输出格式。

```json
{
  "summary": "本次 PR 主要修改了登录认证逻辑，新增 JWT 工具类并调整登录接口返回结构。",
  "riskScore": 78,
  "riskLevel": "HIGH",
  "mainChanges": [
    "新增 JwtUtil 工具类",
    "修改 LoginService 登录逻辑",
    "调整登录接口返回 token"
  ],
  "affectedModules": [
    "auth",
    "user-service"
  ],
  "riskItems": [
    {
      "filePath": "src/main/java/com/demo/auth/JwtUtil.java",
      "riskLevel": "HIGH",
      "riskType": "SECURITY_RISK",
      "riskCategory": "CONFIG",
      "description": "JWT 密钥存在硬编码风险",
      "reason": "密钥直接写在源码中，公开仓库或日志泄露时可能导致 token 被伪造。",
      "suggestion": "建议改为从环境变量或安全配置中读取，并避免在代码库中提交真实密钥。",
      "confidence": 0.93,
      "confidenceLevel": "HIGH",
      "needHumanCheck": false,
      "evidence": "private static final String SECRET = \"123456\";",
      "actionLevel": "MUST_FIX",
      "comment": "建议不要在源码中硬编码 JWT 密钥，可以改为从环境变量中读取。"
    }
  ],
  "testSuggestions": [
    "建议补充 token 过期场景测试",
    "建议补充登录失败场景测试",
    "建议补充非法 token 访问受保护接口的集成测试"
  ],
  "finalReviewDecision": "REQUEST_CHANGES",
  "finalReview": "建议先修复高风险安全问题，再合并该 PR。"
}
```

### 5.2 字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summary` | string | PR 总体变更摘要。 |
| `riskScore` | number | 风险分，范围 0-100，分数越高表示风险越高。 |
| `riskLevel` | string | 总体风险等级：`LOW`、`MEDIUM`、`HIGH`、`CRITICAL`。 |
| `mainChanges` | array | 主要变更点，建议 3-8 条。 |
| `affectedModules` | array | 受影响模块，例如 auth、config、frontend、database。 |
| `riskItems` | array | 具体风险项列表。 |
| `riskItems[].filePath` | string | 风险所在文件路径。 |
| `riskItems[].riskLevel` | string | 单项风险等级：`INFO`、`LOW`、`MEDIUM`、`HIGH`、`CRITICAL`。 |
| `riskItems[].riskType` | string | 后端统一风险枚举，建议使用 `BUG_RISK`、`SECURITY_RISK`、`PERFORMANCE_RISK`、`MAINTAINABILITY`、`STYLE`、`TEST_RISK`、`COMPATIBILITY`。 |
| `riskItems[].riskCategory` | string | 细分风险维度，例如 `SQL_INJECTION`、`AUTH`、`CONFIG`、`NULL_POINTER`。 |
| `riskItems[].description` | string | 风险问题描述。 |
| `riskItems[].reason` | string | 判断原因。 |
| `riskItems[].suggestion` | string | 可执行修改建议。 |
| `riskItems[].confidence` | number | 置信度，范围 0-1。 |
| `riskItems[].confidenceLevel` | string | 置信度等级：`LOW`、`MEDIUM`、`HIGH`。 |
| `riskItems[].needHumanCheck` | boolean | 是否需要人工进一步确认。 |
| `riskItems[].evidence` | string | diff 中可支撑判断的关键证据，避免过长。 |
| `riskItems[].actionLevel` | string | 处理级别：`MUST_FIX`、`SHOULD_FIX`、`OPTIONAL`。 |
| `riskItems[].comment` | string | 可复制到 GitHub PR 的 Review 评论。 |
| `testSuggestions` | array | 测试建议。 |
| `finalReviewDecision` | string | `APPROVE`、`COMMENT`、`REQUEST_CHANGES`。 |
| `finalReview` | string | 最终评审结论说明。 |

### 5.3 文件级 Review JSON

如果后端按文件逐个调用模型，建议文件级输出使用以下结构，便于保存到 `review_comment` 并聚合为报告。

```json
{
  "filePath": "src/main/java/com/example/aipr/service/AuthService.java",
  "summary": "该文件主要新增登录认证逻辑和 token 生成逻辑。",
  "comments": [
    {
      "line": null,
      "riskType": "SECURITY_RISK",
      "riskCategory": "AUTH",
      "riskLevel": "HIGH",
      "title": "登录失败次数未限制",
      "description": "当前登录逻辑没有看到失败次数限制或锁定策略，可能被暴力破解。",
      "reason": "登录接口缺少失败次数限制会增加暴力破解成功概率。",
      "evidence": "+ public LoginResult login(String username, String password) {",
      "actionLevel": "SHOULD_FIX",
      "suggestion": "建议增加登录失败次数限制、短时间锁定或验证码策略，并补充对应测试。",
      "confidence": 0.78,
      "needHumanCheck": true
    }
  ]
}
```

说明：

- `riskType` 使用项目后端稳定枚举，避免前后端展示不一致。
- `riskCategory` 用于展示更细的风险类型，不建议在 MVP 阶段直接扩展为后端主枚举。
- `riskLevel` 是评论级风险等级；后端仍兼容旧字段 `severity`，但最终报告的 `riskScore` 和 `riskLevel` 由后端固定规则重新计算。
- `actionLevel` 只能是 `MUST_FIX`、`SHOULD_FIX`、`OPTIONAL`；缺失时后端会按风险等级给默认值。
- `confidence` 使用 0-1 数值，便于后端排序和阈值判断。

## 6. System Prompt

### 6.1 设计说明

System Prompt 用于统一模型角色、边界和输出规范。该 Prompt 应在所有 Skill Prompt 前作为 system message 传入。

重点约束：

- 模型是资深代码审查专家，不是自由问答助手。
- 只能基于提供的 PR 上下文分析。
- 输出必须结构化、可解析、可落地。
- 不确定时要降低置信度并标记人工确认。

### 6.2 Prompt 模板

```text
你是一名资深代码审查专家和 AI PR Review 助手。

你熟悉以下技术栈：
- Java 17
- Spring Boot 3.x
- Spring Web
- Spring Validation
- Lombok
- Jackson
- WebClient / OkHttp
- MyBatis / MyBatis-Plus
- MySQL
- Vue 3
- Vite
- Element Plus
- Axios
- GitHub Pull Request Review 流程

你的任务是基于用户提供的 GitHub Pull Request 上下文、changed files、diff patch 和规则扫描结果，生成准确、结构化、可执行的代码评审结果。

你必须遵守以下规则：
1. 只能基于提供的 PR 信息和 diff 内容进行分析。
2. 不允许编造未提供的文件、方法、类、配置、业务背景或历史代码。
3. 如果上下文不足以确定问题，必须降低 confidence，并设置 needHumanCheck=true。
4. 规则扫描结果只是线索，不是最终结论；你需要结合 diff 判断是否成立。
5. 每个风险点必须包含文件路径、问题描述、原因、修改建议、证据、处理级别和置信度。
6. 优先识别安全、权限、配置、SQL 注入、异常处理、空指针、性能、敏感日志、依赖和测试缺失风险。
7. 不要输出泛泛而谈的建议；所有建议都应具体、可执行、可复制到 Code Review 评论中。
8. 输出必须是合法 JSON。
9. 不要输出 Markdown。
10. 不要使用 Markdown 代码块标记包裹结果。
11. 如果没有发现明确风险，返回空数组或低风险说明，不要为了凑数制造问题。
12. 所有中文描述应简洁、专业，适合直接展示在前端 Review 报告中。

风险等级定义：
- CRITICAL：严重风险，建议修复并完成人工复核后再合并。
- HIGH：高风险，建议修改后再合并。
- MEDIUM：中风险，建议修改或人工确认。
- LOW：低风险，不阻塞合并，可作为优化建议。
- INFO：提示信息，不一定需要修改。

最终评审结论定义：
- APPROVE：未发现阻塞问题，可以合并。
- COMMENT：存在建议项，但不阻塞合并。
- REQUEST_CHANGES：存在高风险或必须修复问题，建议修改后再合并。
```

## 7. PRSummarySkill Prompt

### 7.1 作用

`PRSummarySkill` 用于生成 PR 变更总结，帮助用户快速理解本次 PR 做了什么、影响了哪些模块、是否涉及高风险区域。

该 Prompt 不负责输出详细风险评论，只生成摘要和影响范围。

### 7.2 输入变量

| 变量 | 说明 |
| --- | --- |
| `{pr_info}` | PR 标题、描述、作者、源分支、目标分支。 |
| `{commits}` | commit 列表。 |
| `{changed_files}` | 变更文件列表及 additions / deletions / status。 |
| `{diff_summary}` | diff 摘要。 |

### 7.3 Prompt 模板

```text
请基于以下 GitHub Pull Request 上下文，生成 PR 变更总结。

要求：
1. 只总结输入中明确出现的变更，不要推测未提供的业务背景。
2. 重点说明本次 PR 修改了什么、影响哪些模块、属于什么类型变更。
3. 如果变更涉及认证、权限、配置、数据库、接口、日志、依赖或大范围删除，请标记为高风险模块。
4. 输出必须是合法 JSON。
5. 不要输出 Markdown，不要使用代码块包裹。
6. `mainChanges` 建议 3-8 条。
7. `highRiskModules` 如果没有明确高风险模块，返回空数组。

PR 信息:
{pr_info}

Commit 列表:
{commits}

变更文件列表:
{changed_files}

Diff 摘要:
{diff_summary}

请严格按照以下 JSON 结构输出：
{
  "summary": "用 1-3 句话概括本次 PR 的主要目的和影响。",
  "mainChanges": [
    "主要变更 1",
    "主要变更 2"
  ],
  "affectedModules": [
    "受影响模块 1",
    "受影响模块 2"
  ],
  "changeType": [
    "FEATURE",
    "BUGFIX",
    "REFACTOR",
    "CONFIG",
    "TEST",
    "DOCS",
    "DEPENDENCY"
  ],
  "highRiskModules": [
    {
      "module": "模块名称或文件路径",
      "reason": "为什么该模块需要重点关注"
    }
  ]
}
```

### 7.4 输出示例

```json
{
  "summary": "本次 PR 主要调整登录认证逻辑，新增 JWT 工具类，并修改登录接口返回 token 的方式。",
  "mainChanges": [
    "新增 JwtUtil 用于生成和解析 token",
    "修改 LoginService 的登录校验逻辑",
    "调整 AuthController 的登录响应结构"
  ],
  "affectedModules": [
    "auth",
    "controller",
    "service"
  ],
  "changeType": [
    "FEATURE",
    "CONFIG"
  ],
  "highRiskModules": [
    {
      "module": "auth",
      "reason": "认证逻辑变更会直接影响登录安全和接口访问控制。"
    }
  ]
}
```

## 8. RiskDetectSkill Prompt

### 8.1 作用

`RiskDetectSkill` 用于识别具体风险代码。它接收 PR 基本信息、文件路径、diff 内容和规则扫描结果，输出结构化风险项。

该 Prompt 是 AI Review 的核心，需要重点控制误报和漏报。

### 8.2 输入变量

| 变量 | 说明 |
| --- | --- |
| `{pr_info}` | PR 标题、描述、作者、分支信息。 |
| `{file_path}` | 当前分析文件路径。 |
| `{file_status}` | added / modified / removed / renamed。 |
| `{file_stats}` | additions / deletions / changes。 |
| `{diff}` | 当前文件 diff patch。 |
| `{rule_risks}` | 当前文件相关规则扫描结果。 |
| `{high_risk_files}` | 高风险文件标记。 |

### 8.3 重点识别风险

| 风险 | 识别重点 |
| --- | --- |
| 硬编码密钥 | token、secret、password、accessKey、privateKey 等直接写入源码。 |
| SQL 注入 | 字符串拼接 SQL、未参数化查询、动态拼接排序字段。 |
| 权限认证风险 | 缺少鉴权、绕过权限判断、token 校验不足。 |
| 配置风险 | 默认弱密钥、关闭安全配置、敏感配置提交。 |
| 异常处理缺失 | 吞异常、只打印日志、不返回明确业务错误。 |
| 空指针风险 | 新增链式调用但缺少 null 判断。 |
| 性能风险 | 循环中访问数据库、无分页查询、重复远程调用。 |
| 敏感日志 | 打印 token、密码、密钥、身份证、手机号等敏感信息。 |
| 测试缺失 | 核心逻辑变更但没有测试文件变更。 |
| 大范围删除 | 删除认证、校验、异常处理、测试或配置保护逻辑。 |

### 8.4 Prompt 模板

```text
请对以下 Pull Request 文件 diff 进行代码风险识别。

分析要求：
1. 只基于当前输入的 PR 信息、文件路径、diff 和规则扫描结果分析。
2. 不要假设仓库中存在未提供的代码。
3. 规则扫描结果只是风险线索，不要直接照搬为结论。
4. 每个风险点必须有 evidence，evidence 应来自 diff 中的关键代码或规则命中片段。
5. 如果证据不足，请降低 confidence，并设置 needHumanCheck=true。
6. 不要输出无证据的泛泛建议。
7. 如果没有发现明确风险，返回 "risks": []。
8. 最多输出 8 个风险项，优先输出 HIGH 和 MEDIUM。
9. 输出必须是合法 JSON。
10. 不要输出 Markdown，不要使用代码块包裹。

请重点检查：
- 硬编码密钥、token、密码、AK/SK、私钥
- SQL 注入和未参数化查询
- 认证、鉴权、权限绕过
- 配置风险和默认弱配置
- 异常处理缺失或吞异常
- 空指针风险
- 循环查询、无分页、重复远程调用等性能风险
- 敏感信息日志
- 核心逻辑变更但缺少测试
- 大范围删除安全、校验、测试或异常处理代码

PR 信息:
{pr_info}

当前文件:
{file_path}

文件状态:
{file_status}

文件变更统计:
{file_stats}

高风险文件标记:
{high_risk_files}

规则扫描结果:
{rule_risks}

Diff 内容:
{diff}

风险类型约束：
- riskType 必须使用以下值之一：BUG_RISK、SECURITY_RISK、PERFORMANCE_RISK、MAINTAINABILITY、STYLE、TEST_RISK、COMPATIBILITY。
- riskCategory 用于细分风险，可使用：SECURITY、SQL_INJECTION、AUTH、CONFIG、EXCEPTION、NULL_POINTER、PERFORMANCE、READABILITY、TEST_MISSING、LOGGING、DEPENDENCY、LARGE_DELETION。

风险等级约束：
- CRITICAL：严重风险，建议修复并完成人工复核后再合并。
- HIGH：高风险，建议修改后再合并。
- MEDIUM：中风险，建议修改或人工确认。
- LOW：低风险，不阻塞合并。
- INFO：提示信息，不一定需要修改。

置信度约束：
- confidence 使用 0 到 1 的数字。
- confidenceLevel 使用 HIGH、MEDIUM、LOW。
- 证据明确时 confidence >= 0.8。
- 有一定依据但需要结合其他文件确认时 confidence 介于 0.5 到 0.79。
- 上下文不足仅作为提醒时 confidence < 0.5。

请严格按照以下 JSON 结构输出：
{
  "filePath": "{file_path}",
  "risks": [
    {
      "riskLevel": "HIGH",
      "riskType": "SECURITY_RISK",
      "riskCategory": "CONFIG",
      "description": "问题描述",
      "reason": "为什么这是风险",
      "suggestion": "具体修改建议",
      "confidence": 0.9,
      "confidenceLevel": "HIGH",
      "needHumanCheck": false,
      "evidence": "来自 diff 的关键代码片段",
      "comment": "可复制到 GitHub PR 的简短 Review 评论"
    }
  ]
}
```

### 8.5 输出示例

```json
{
  "filePath": "src/main/java/com/demo/auth/JwtUtil.java",
  "risks": [
    {
      "riskLevel": "HIGH",
      "riskType": "SECURITY_RISK",
      "riskCategory": "CONFIG",
      "description": "JWT 密钥存在硬编码风险",
      "reason": "密钥直接写在源码中，如果仓库公开或代码被泄露，攻击者可能伪造 token。",
      "suggestion": "建议将密钥改为从环境变量或安全配置中读取，并避免提交真实密钥。",
      "confidence": 0.93,
      "confidenceLevel": "HIGH",
      "needHumanCheck": false,
      "evidence": "private static final String SECRET = \"123456\";",
      "comment": "建议不要在源码中硬编码 JWT 密钥，可以改为从环境变量或安全配置中读取。"
    }
  ]
}
```

## 9. ReviewSuggestionSkill Prompt

### 9.1 作用

`ReviewSuggestionSkill` 基于 PR 总结、风险列表和规则扫描结果生成最终 Review 建议，包括必须修改项、建议项、可复制评论和最终评审结论。

### 9.2 输入变量

| 变量 | 说明 |
| --- | --- |
| `{summary_result}` | PRSummarySkill 输出。 |
| `{risk_items}` | RiskDetectSkill 输出的风险列表。 |
| `{rule_risks}` | 全局规则扫描结果。 |
| `{main_changes}` | 主要变更列表。 |

### 9.3 Prompt 模板

```text
请基于 PR 总结、风险列表和规则扫描结果，生成代码评审建议。

要求：
1. 只基于输入中的总结、风险项和规则扫描结果生成建议。
2. 不要新增没有证据支撑的风险项。
3. 高风险且高置信度的问题应进入 mustFixItems。
4. 中低风险或低置信度问题应进入 suggestionItems。
5. copyableComments 应适合直接复制到 GitHub PR 评论中。
6. finalReviewDecision 只能是 APPROVE、COMMENT、REQUEST_CHANGES。
7. 如果存在 HIGH 风险且 confidence >= 0.8，finalReviewDecision 应为 REQUEST_CHANGES。
8. 如果只有 MEDIUM / LOW 风险，finalReviewDecision 通常为 COMMENT。
9. 如果没有明确风险，finalReviewDecision 可以为 APPROVE。
10. 输出必须是合法 JSON。
11. 不要输出 Markdown，不要使用代码块包裹。

PR 总结:
{summary_result}

主要变更:
{main_changes}

风险列表:
{risk_items}

规则扫描结果:
{rule_risks}

请严格按照以下 JSON 结构输出：
{
  "reviewSummary": "对本次 PR 的总体评审总结。",
  "mustFixItems": [
    {
      "filePath": "文件路径",
      "title": "必须修复项标题",
      "reason": "为什么必须修复",
      "suggestion": "具体修复建议"
    }
  ],
  "suggestionItems": [
    {
      "filePath": "文件路径",
      "title": "建议项标题",
      "reason": "建议关注的原因",
      "suggestion": "具体优化建议"
    }
  ],
  "copyableComments": [
    {
      "filePath": "文件路径",
      "comment": "可复制到 PR 的 Review 评论"
    }
  ],
  "finalReviewDecision": "COMMENT",
  "finalReviewReason": "给出最终结论的原因"
}
```

### 9.4 输出示例

```json
{
  "reviewSummary": "本次 PR 涉及认证逻辑变更，整体目标清晰，但当前存在 JWT 密钥硬编码这一高风险问题。",
  "mustFixItems": [
    {
      "filePath": "src/main/java/com/demo/auth/JwtUtil.java",
      "title": "移除硬编码 JWT 密钥",
      "reason": "硬编码密钥可能导致 token 被伪造，属于高风险安全问题。",
      "suggestion": "将密钥改为从环境变量或安全配置读取，并为本地开发提供示例配置。"
    }
  ],
  "suggestionItems": [
    {
      "filePath": "src/test/java/com/demo/auth/LoginServiceTest.java",
      "title": "补充 token 异常场景测试",
      "reason": "认证逻辑变化较大，需要覆盖 token 过期和非法 token 场景。",
      "suggestion": "增加 token 过期、签名错误、空 token 的单元测试或集成测试。"
    }
  ],
  "copyableComments": [
    {
      "filePath": "src/main/java/com/demo/auth/JwtUtil.java",
      "comment": "这里不建议硬编码 JWT 密钥，建议从环境变量或安全配置中读取，避免仓库泄露后 token 可被伪造。"
    }
  ],
  "finalReviewDecision": "REQUEST_CHANGES",
  "finalReviewReason": "存在高置信度安全风险，建议修复后再合并。"
}
```

## 10. TestSuggestionSkill Prompt

### 10.1 作用

`TestSuggestionSkill` 根据 PR 主要变更、变更文件和风险项生成测试建议，重点覆盖单元测试、集成测试、边界条件和回归测试。

### 10.2 输入变量

| 变量 | 说明 |
| --- | --- |
| `{main_changes}` | 主要变更列表。 |
| `{changed_files}` | 变更文件列表。 |
| `{risk_items}` | 风险项列表。 |
| `{has_test_changes}` | 是否存在测试文件变更。 |
| `{diff_summary}` | diff 摘要。 |

### 10.3 Prompt 模板

```text
请基于 Pull Request 的主要变更、变更文件和风险项，生成测试建议。

要求：
1. 只基于输入内容提出测试建议，不要假设未提供的业务场景。
2. 测试建议要具体、可执行，避免只写“增加测试”。
3. 如果核心逻辑变更但没有测试文件变更，需要明确指出测试缺失风险。
4. 优先覆盖高风险模块、认证权限、配置、数据库、异常处理和接口输入输出。
5. 每类测试建议最多输出 5 条。
6. testPriority 只能是 HIGH、MEDIUM、LOW。
7. 输出必须是合法 JSON。
8. 不要输出 Markdown，不要使用代码块包裹。

主要变更:
{main_changes}

变更文件列表:
{changed_files}

风险项:
{risk_items}

是否存在测试文件变更:
{has_test_changes}

Diff 摘要:
{diff_summary}

请严格按照以下 JSON 结构输出：
{
  "unitTestSuggestions": [
    "单元测试建议"
  ],
  "integrationTestSuggestions": [
    "集成测试建议"
  ],
  "edgeCaseSuggestions": [
    "边界场景测试建议"
  ],
  "regressionTestSuggestions": [
    "回归测试建议"
  ],
  "testPriority": "HIGH",
  "testRiskReason": "为什么是该测试优先级"
}
```

### 10.4 输出示例

```json
{
  "unitTestSuggestions": [
    "为 JwtUtil 增加 token 生成、解析、过期时间校验的单元测试。",
    "为 LoginService 增加密码错误、用户不存在、账号禁用场景测试。"
  ],
  "integrationTestSuggestions": [
    "增加登录接口成功后返回 token 的接口测试。",
    "增加携带非法 token 访问受保护接口的集成测试。"
  ],
  "edgeCaseSuggestions": [
    "测试 Authorization header 为空、格式错误、token 过期的场景。",
    "测试 PR 中新增配置缺失时服务启动或调用是否有明确错误。"
  ],
  "regressionTestSuggestions": [
    "回归已有登录成功、登录失败、登出流程。",
    "回归需要认证的核心接口访问控制。"
  ],
  "testPriority": "HIGH",
  "testRiskReason": "本次 PR 修改认证逻辑，且存在安全风险，测试优先级应为 HIGH。"
}
```

## 11. UnifiedReviewReport Prompt

### 11.1 作用

如果 MVP 第一版不拆分 Skill，可以使用 `UnifiedReviewReport Prompt` 一次性生成完整 Review 报告。

适用场景：

- 后端实现较简单，希望一次模型调用完成报告生成。
- PR diff 较小，单次上下文可以容纳。
- 比赛演示希望链路稳定，减少多次模型调用失败概率。

不适用场景：

- PR 文件很多或 diff 很大。
- 需要逐文件保存 Review 建议。
- 需要并行分析多个文件。

### 11.2 输入变量

| 变量 | 说明 |
| --- | --- |
| `{pr_info}` | PR 基本信息。 |
| `{commits}` | commit 列表。 |
| `{changed_files}` | 变更文件列表。 |
| `{diff_summary}` | diff 摘要。 |
| `{diff}` | 裁剪后的核心 diff。 |
| `{rule_risks}` | 规则扫描结果。 |
| `{high_risk_files}` | 高风险文件列表。 |
| `{has_test_changes}` | 是否存在测试文件变更。 |

### 11.3 Prompt 模板

```text
请基于以下 GitHub Pull Request 上下文，生成一份完整的 AI Code Review 报告。

你需要完成：
1. 总结 PR 主要变更。
2. 识别风险代码。
3. 给出测试建议。
4. 给出最终评审结论。

分析要求：
1. 只能基于输入中的 PR 信息、changed files、diff 和规则扫描结果分析。
2. 不要编造未提供的代码、业务背景或历史实现。
3. 规则扫描结果只是线索，需要结合 diff 判断是否成立。
4. 每个风险项必须包含文件路径、风险等级、风险类型、描述、原因、建议、证据和置信度。
5. 如果上下文不足，必须降低 confidence，并设置 needHumanCheck=true。
6. 如果没有明确风险，riskItems 返回空数组，不要为了凑数生成问题。
7. 测试建议必须结合本次变更内容。
8. 最多输出 10 个风险项，优先输出 HIGH 和 MEDIUM。
9. 输出必须是合法 JSON。
10. 不要输出 Markdown，不要使用代码块包裹。

PR 信息:
{pr_info}

Commit 列表:
{commits}

变更文件列表:
{changed_files}

高风险文件:
{high_risk_files}

是否存在测试文件变更:
{has_test_changes}

规则扫描结果:
{rule_risks}

Diff 摘要:
{diff_summary}

Diff 内容:
{diff}

风险类型约束：
- riskType 必须使用以下值之一：BUG_RISK、SECURITY_RISK、PERFORMANCE_RISK、MAINTAINABILITY、STYLE、TEST_RISK、COMPATIBILITY。
- riskCategory 可使用：SECURITY、SQL_INJECTION、AUTH、CONFIG、EXCEPTION、NULL_POINTER、PERFORMANCE、READABILITY、TEST_MISSING、LOGGING、DEPENDENCY、LARGE_DELETION。

风险等级约束：
- CRITICAL：严重风险，建议修复并完成人工复核后再合并。
- HIGH：高风险，建议修改后再合并。
- MEDIUM：中风险，建议修改或人工确认。
- LOW：低风险，不阻塞合并。
- INFO：提示信息，不一定需要修改。

最终评审结论约束：
- APPROVE：可以合并。
- COMMENT：有建议但不阻塞。
- REQUEST_CHANGES：建议修改后再合并。

请严格按照以下 JSON 结构输出：
{
  "summary": "本次 PR 的总体总结。",
  "riskScore": 0,
  "riskLevel": "LOW",
  "mainChanges": [
    "主要变更 1"
  ],
  "affectedModules": [
    "受影响模块"
  ],
  "riskItems": [
    {
      "filePath": "文件路径",
      "riskLevel": "HIGH",
      "riskType": "SECURITY_RISK",
      "riskCategory": "AUTH",
      "description": "风险描述",
      "reason": "判断原因",
      "suggestion": "修改建议",
      "confidence": 0.85,
      "confidenceLevel": "HIGH",
      "needHumanCheck": false,
      "evidence": "来自 diff 的证据",
      "comment": "可复制到 PR 的 Review 评论"
    }
  ],
  "testSuggestions": [
    "测试建议"
  ],
  "finalReviewDecision": "COMMENT",
  "finalReview": "最终评审结论说明。"
}

最终 riskScore / riskLevel 计算规则：
- 后端基于所有已保存的 Review Comment 统一计算最终 riskScore / riskLevel，不直接采用 AI 输出的总评分。
- 风险等级基础分：INFO=0，LOW=8，MEDIUM=20，HIGH=40，CRITICAL=70。
- 风险类型加分只取最高一项：SECURITY/SECURITY_RISK=20，BUG_RISK=15，PERFORMANCE/PERFORMANCE_RISK=10，MAINTAINABILITY=5，STYLE/INFO=0。
- needHumanCheck=true 每条 +5，最多 +15。
- 映射：0-30=LOW，31-60=MEDIUM，61-85=HIGH，86-100=CRITICAL。
```

### 11.4 输出示例

```json
{
  "summary": "本次 PR 主要新增登录认证能力，包含 JWT 工具类、登录服务逻辑和认证接口返回结构调整。",
  "riskScore": 82,
  "riskLevel": "HIGH",
  "mainChanges": [
    "新增 JwtUtil 负责 token 生成和解析",
    "修改 LoginService 登录校验逻辑",
    "调整 AuthController 登录接口返回 token"
  ],
  "affectedModules": [
    "auth",
    "service",
    "controller"
  ],
  "riskItems": [
    {
      "filePath": "src/main/java/com/demo/auth/JwtUtil.java",
      "riskLevel": "HIGH",
      "riskType": "SECURITY_RISK",
      "riskCategory": "CONFIG",
      "description": "JWT 密钥存在硬编码风险",
      "reason": "密钥写在源码中，仓库泄露或公开后可能导致 token 被伪造。",
      "suggestion": "建议从环境变量或安全配置读取密钥，并避免提交真实密钥。",
      "confidence": 0.93,
      "confidenceLevel": "HIGH",
      "needHumanCheck": false,
      "evidence": "private static final String SECRET = \"123456\";",
      "comment": "建议不要在源码中硬编码 JWT 密钥，可以改为从环境变量或安全配置中读取。"
    }
  ],
  "testSuggestions": [
    "建议补充 token 过期、签名错误和空 token 的测试。",
    "建议补充登录失败和账号不存在场景测试。",
    "建议增加受保护接口的鉴权集成测试。"
  ],
  "finalReviewDecision": "REQUEST_CHANGES",
  "finalReview": "本次 PR 存在高置信度安全风险，建议修复 JWT 密钥硬编码问题后再合并。"
}
```

## 12. 风险等级、风险类型与置信度设计

### 12.1 风险等级

| 风险等级 | 含义 | 合并建议 |
| --- | --- | --- |
| `LOW` | 低风险，通常是可读性、轻微维护性或非阻塞测试建议。 | 不阻塞合并，可作为优化建议。 |
| `MEDIUM` | 中风险，可能影响稳定性、安全性或可维护性，但需要结合上下文确认。 | 建议修改或人工确认。 |
| `HIGH` | 高风险，可能导致安全漏洞、数据错误、线上故障或核心流程不可用。 | 建议修改后再合并。 |

### 12.2 后端主风险类型

为保持前后端和数据库枚举稳定，MVP 阶段建议 `riskType` 使用以下主类型。

| riskType | 含义 |
| --- | --- |
| `BUG_RISK` | 可能导致功能错误、空指针、异常流程或数据错误。 |
| `SECURITY_RISK` | 安全相关风险，包括密钥、认证、权限、注入、敏感信息。 |
| `PERFORMANCE_RISK` | 性能风险，例如循环查询、无分页、大对象处理。 |
| `MAINTAINABILITY` | 可维护性风险，例如复杂逻辑、重复代码、边界不清晰。 |
| `STYLE` | 代码风格、命名、格式等非阻塞问题。 |
| `TEST_RISK` | 测试缺失、测试覆盖不足、关键场景未验证。 |
| `COMPATIBILITY` | 兼容性风险，例如接口契约、依赖版本、配置变更影响。 |

### 12.3 细分风险类型

为了满足报告展示和规则扫描分析，建议增加 `riskCategory` 表示细分风险维度。

| riskCategory | 含义 | 推荐映射 riskType |
| --- | --- | --- |
| `SECURITY` | 通用安全风险 | `SECURITY_RISK` |
| `SQL_INJECTION` | SQL 注入风险 | `SECURITY_RISK` |
| `AUTH` | 认证权限风险 | `SECURITY_RISK` |
| `CONFIG` | 配置风险、硬编码密钥、弱配置 | `SECURITY_RISK` / `COMPATIBILITY` |
| `EXCEPTION` | 异常处理风险 | `BUG_RISK` |
| `NULL_POINTER` | 空指针风险 | `BUG_RISK` |
| `PERFORMANCE` | 性能风险 | `PERFORMANCE_RISK` |
| `READABILITY` | 可读性问题 | `MAINTAINABILITY` / `STYLE` |
| `TEST_MISSING` | 测试缺失 | `TEST_RISK` |
| `LOGGING` | 敏感日志或日志不足 | `SECURITY_RISK` / `MAINTAINABILITY` |
| `DEPENDENCY` | 依赖升级、漏洞、冲突 | `COMPATIBILITY` / `SECURITY_RISK` |
| `LARGE_DELETION` | 大范围删除代码 | `BUG_RISK` / `TEST_RISK` |

### 12.4 置信度

| 置信度 | 数值范围 | 含义 | 处理方式 |
| --- | --- | --- | --- |
| `HIGH` | `0.8 - 1.0` | 证据明确，diff 中有直接支撑。 | 建议优先处理，高风险可作为阻塞项。 |
| `MEDIUM` | `0.5 - 0.79` | 有一定依据，但需要结合其他文件或运行结果确认。 | 建议人工确认。 |
| `LOW` | `0 - 0.49` | 上下文不足，仅作为提醒。 | 不作为阻塞项。 |

使用置信度降低误报：

1. 低置信度问题不进入 `mustFixItems`。
2. 中置信度问题需要在 `reason` 中说明需要人工确认。
3. 高置信度问题必须有明确 `evidence`。
4. 如果模型无法从 diff 找到证据，不应输出高置信度风险。

## 13. 误报与漏报控制策略

### 13.1 降低误报

| 策略 | Prompt 约束 |
| --- | --- |
| 不盲信规则扫描 | “规则扫描结果只是线索，不是最终结论。” |
| 要求证据 | 每个风险项必须输出 `evidence`。 |
| 要求原因 | 每个风险项必须输出 `reason`，说明为什么是风险。 |
| 置信度分层 | 低置信度问题不作为阻塞项。 |
| 上下文不足说明 | 设置 `needHumanCheck=true` 并说明需要进一步确认。 |
| 限制输出数量 | 最多输出高价值风险项，避免为了凑数生成问题。 |

### 13.2 降低漏报

| 策略 | Prompt 约束 |
| --- | --- |
| 强制检查高风险维度 | 安全、权限、配置、SQL、异常、日志、测试、性能必须检查。 |
| 结合规则扫描结果 | 将规则命中作为重点关注对象。 |
| 高风险文件优先 | 对认证、权限、配置、数据库、接口层代码提高关注度。 |
| 检查大范围删除 | 删除校验、异常、测试、安全逻辑时必须关注。 |
| 检查测试缺失 | 核心逻辑变更但无测试文件变更时输出测试建议。 |

### 13.3 Prompt 中的关键控制语句

建议在风险类 Prompt 中固定加入以下句子：

```text
如果没有证据，请不要输出该风险项。
如果证据不足但值得关注，请降低 confidence，并设置 needHumanCheck=true。
规则扫描结果只是线索，不要直接照搬为结论。
如果没有发现明确风险，返回空数组，不要为了凑数制造问题。
```

## 14. PromptBuilder 代码映射建议

后端可以设计 `PromptBuilder` 类统一管理 Prompt 模板，避免 Controller 或 Service 中散落字符串拼接逻辑。

```java
public class PromptBuilder {

    public String buildSystemPrompt();

    public String buildSummaryPrompt(ReviewContext context);

    public String buildRiskDetectPrompt(ReviewContext context);

    public String buildReviewSuggestionPrompt(ReviewContext context, List<RiskItem> riskItems);

    public String buildTestSuggestionPrompt(ReviewContext context);

    public String buildUnifiedReviewPrompt(ReviewContext context);
}
```

### 14.1 方法映射

| 方法 | 对应 Prompt | 说明 |
| --- | --- | --- |
| `buildSystemPrompt()` | System Prompt | 构造 system message，所有模型调用复用。 |
| `buildSummaryPrompt(ReviewContext context)` | PRSummarySkill Prompt | 根据 PR 信息、commit、changed files、diff 摘要构造总结 Prompt。 |
| `buildRiskDetectPrompt(ReviewContext context)` | RiskDetectSkill Prompt | 可按文件构造风险识别 Prompt。 |
| `buildReviewSuggestionPrompt(ReviewContext context, List<RiskItem> riskItems)` | ReviewSuggestionSkill Prompt | 根据总结和风险列表生成最终 Review 建议。 |
| `buildTestSuggestionPrompt(ReviewContext context)` | TestSuggestionSkill Prompt | 生成测试建议。 |
| `buildUnifiedReviewPrompt(ReviewContext context)` | UnifiedReviewReport Prompt | MVP 一次性生成完整报告。 |

### 14.2 ReviewContext 建议字段

```java
public class ReviewContext {

    private String prTitle;

    private String prDescription;

    private String prAuthor;

    private String sourceBranch;

    private String targetBranch;

    private List<String> commits;

    private List<ChangedFileContext> changedFiles;

    private String diffSummary;

    private String diff;

    private List<RuleRisk> ruleRisks;

    private List<String> highRiskFiles;

    private Boolean hasTestChanges;
}
```

### 14.3 工程实现建议

1. Prompt 模板可以先放在 Java 常量中，MVP 阶段不需要引入复杂模板引擎。
2. 后续如果模板变多，可以迁移到 `resources/prompts/*.txt`。
3. 渲染模板时应对空字段做默认值处理，例如 `无`、`[]`、`未提供`。
4. diff 内容进入 Prompt 前应先裁剪和脱敏。
5. Controller 不应直接拼接 Prompt，应通过 `AiReviewService` 调用 `PromptBuilder`。

## 15. LLM 调用参数建议

| 参数 | 建议值 | 说明 |
| --- | --- | --- |
| `temperature` | `0.1 - 0.3`，默认 `0.2` | 低温度提高输出稳定性。 |
| `max_tokens` | `2000 - 4000` | 根据模型上下文和报告长度调整。 |
| `response_format` | 支持时使用 JSON 模式 | 如果模型支持，优先设置为 JSON 输出。 |
| `timeout` | `30 - 60 秒` | 避免模型调用长时间阻塞任务。 |
| `top_p` | 默认值或 `0.8 - 1.0` | 一般无需特别调整。 |
| `frequency_penalty` | `0` | 代码评审场景不建议增加惩罚。 |
| `presence_penalty` | `0` | 避免引入不必要发散。 |

### 15.1 推荐调用策略

MVP 阶段推荐两种调用方式：

| 方式 | 说明 | 适用场景 |
| --- | --- | --- |
| 单次统一调用 | 使用 `UnifiedReviewReport Prompt` 一次生成完整报告。 | PR 较小、演示链路、快速落地。 |
| 多 Skill 调用 | Summary → RiskDetect → ReviewSuggestion → TestSuggestion。 | 需要更清晰流程和更稳定解析。 |

### 15.2 JSON 解析建议

1. 如果模型支持 `response_format={"type":"json_object"}`，优先开启。
2. 如果模型返回 Markdown 代码块，后端需要先清理代码块起止标记。
3. 使用 Jackson 解析为 DTO。
4. 解析失败时保存 `rawOutput`，任务状态标记为失败或部分失败。
5. 不要直接把原始 LLM 输出返回给前端作为最终结构化报告。

## 16. 异常与兜底策略

### 16.1 AI 返回不是合法 JSON

处理方式：

1. 清理 Markdown 代码块标记后重试解析。
2. 如果仍解析失败，记录脱敏后的 `rawOutput` 摘要。
3. 将任务或文件 Review 标记为失败。
4. 返回友好错误：`模型返回格式异常，请重新评审。`
5. 不要把完整原始输出直接展示给普通用户。

### 16.2 AI 服务超时

处理方式：

1. 设置 30-60 秒超时时间。
2. 超时后可重试 1 次。
3. 多文件 Review 时，只将当前文件标记为失败，不影响已完成文件结果。
4. 任务最终报告中说明部分文件未完成 AI 分析。

### 16.3 diff 为空

处理方式：

1. 如果 changed files 为空，返回空风险列表。
2. 报告中说明：`当前 PR 未获取到可分析 diff，无法进行代码级 Review。`
3. 如果只有二进制文件或被跳过文件，说明跳过原因。

### 16.4 上下文过长

处理方式：

1. 先按高风险文件、规则命中文件、核心模块裁剪。
2. 单文件过长时截断 diff，并保留规则命中的 hunk。
3. 在 Prompt 中明确标记 diff 已截断。
4. 模型输出需要对缺失上下文降低置信度。

### 16.5 模型输出字段缺失

后端应补默认值，避免前端渲染失败。

| 字段 | 默认值 |
| --- | --- |
| `summary` | `暂无总结` |
| `riskScore` | `0` |
| `riskLevel` | `LOW` |
| `mainChanges` | `[]` |
| `affectedModules` | `[]` |
| `riskItems` | `[]` |
| `testSuggestions` | `[]` |
| `finalReviewDecision` | `COMMENT` |
| `finalReview` | `AI 未返回完整评审结论，请人工确认。` |
| `confidence` | `0.5` |
| `confidenceLevel` | `MEDIUM` |
| `needHumanCheck` | `true` |

### 16.6 rawOutput 保留策略

建议在后端内部保留模型原始输出，便于排查问题。

注意事项：

1. `rawOutput` 不应默认返回给前端用户。
2. 日志中只记录摘要，避免过长日志。
3. 如果 rawOutput 可能包含敏感信息，需要脱敏后再记录。
4. 可在数据库中为 Review 任务保留 `ai_raw_output` 或失败日志字段，MVP 也可以只记录到日志。

## 17. 后续优化方向

后续可以在不破坏 MVP 链路的前提下逐步优化 Prompt 和评审质量。

| 方向 | 说明 |
| --- | --- |
| Prompt 模板配置化 | 将 Prompt 从 Java 常量迁移到 `resources/prompts` 或数据库模板表。 |
| 规则扫描增强 | 增加硬编码密钥、危险依赖、SQL 拼接、敏感日志等规则。 |
| 分文件并行 Review | 对 changed files 并行调用 RiskDetectSkill，提高大 PR 处理速度。 |
| 风险评分优化 | 根据风险等级、置信度、文件类型和规则命中计算更稳定的 riskScore。 |
| Few-shot 示例 | 为常见风险提供少量示例，提高模型输出一致性。 |
| 多语言支持 | 后续扩展 Python、Go、Node.js 等语言的风险识别规则。 |
| GitHub 评论集成 | 在用户授权后，将 copyableComments 自动提交到 GitHub PR。 |
| 轻量 Skill 编排 | 将 Summary、RiskDetect、TestSuggestion、Report 聚合为可配置 Skill 流程。 |
| 人工反馈闭环 | 记录用户采纳或忽略的建议，用于优化规则和 Prompt。 |

MVP 阶段应优先保证以下能力稳定：

```text
输入 PR URL
→ 获取 diff
→ 构造 Prompt
→ 调用模型
→ 解析 JSON
→ 展示结构化 Review 报告
```
