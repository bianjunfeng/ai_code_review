# Redis 限流保护设计方案

## 一、背景说明

当前项目是一个 AI PR Review 助手，核心链路包括：

1. 用户提交 GitHub Pull Request 链接；
2. 后端解析 PR URL；
3. 调用 GitHub API 获取 PR 信息和 Diff；
4. 调用 AI 模型生成 Review 报告；
5. 将评审结果保存到数据库；
6. 前端轮询任务状态并展示报告。

在该链路中，以下操作都属于相对高成本操作：

- GitHub API 调用；
- AI 模型 API 调用；
- PR Diff 获取与解析；
- Review 任务创建；
- Review 结果持久化；
- forceRefresh 强制重新分析。

如果用户频繁点击“开始评审”或“重新分析”，可能导致：

- 重复创建大量 `review_task`；
- 重复调用 AI 模型，造成 Token 浪费；
- GitHub API 请求过多；
- 数据库写入压力增加；
- 前端任务状态混乱；
- 演示或部署时系统不稳定。

因此，需要引入 Redis 限流机制，对高成本接口进行保护。

------

## 二、设计目标

本方案采用 **Redis 固定窗口计数器限流**。

目标如下：

1. 限制同一 IP 创建 Review 任务的频率；
2. 限制同一个 PR 被重复分析的频率；
3. 限制 `forceRefresh=true` 强制重新分析的频率；
4. 限制 GitHub PR 列表接口的访问频率；
5. 限制全局 AI 模型调用频率；
6. Redis 异常时不影响主流程，默认放行并记录警告日志；
7. 尽量不改变现有业务流程，采用最小可行改动；
8. 为后续部署、多实例扩展和成本控制打基础。

------

## 三、为什么使用 Redis 做限流

如果使用 Java 本地内存做限流，例如：

```java
Map<String, Integer> counter = new ConcurrentHashMap<>();
```

会存在以下问题：

1. 服务重启后计数丢失；
2. 多个后端实例之间无法共享计数；
3. 需要自己处理过期逻辑；
4. 不适合后续真实部署；
5. 对分布式场景支持较差。

Redis 更适合限流计数，原因是：

1. `INCR` 操作是原子递增；
2. `EXPIRE` 可以自动过期；
3. 多个服务实例可以共享同一套限流状态；
4. 性能高；
5. 实现简单；
6. 后续可以扩展为滑动窗口、令牌桶、分布式锁等能力。

------

## 四、限流算法选择

### 4.1 当前方案：固定窗口计数器限流

固定窗口计数器限流的基本逻辑是：

```text
在一个固定时间窗口内，对某个 Redis Key 进行计数；
如果计数超过阈值，则拒绝请求；
窗口结束后 Key 自动过期。
```

示例：

```text
rate_limit:ip:127.0.0.1:create-review

60 秒内最多允许 5 次请求。
```

每次请求到来时：

1. Redis 对 Key 执行 `INCR`；
2. 如果是第一次请求，则设置过期时间；
3. 如果计数超过限制，则拒绝请求；
4. 如果没有超过限制，则放行。

### 4.2 优点

- 实现简单；
- 性能高；
- 对当前 MVP 项目足够；
- Redis 原生支持计数和过期；
- 便于排查和测试。

### 4.3 缺点

固定窗口存在窗口边界突刺问题。

例如：

```text
12:00:59 请求 5 次
12:01:00 又请求 5 次
```

短时间内可能实际请求 10 次。

当前项目对限流精度要求不高，所以可以先使用固定窗口计数器。后续如有需要，可以升级为：

- 滑动窗口限流；
- 令牌桶限流；
- 漏桶限流；
- Sentinel / Gateway 限流。

------

## 五、限流场景设计

### 5.1 创建 Review 任务接口限流

接口：

```http
POST /api/review-tasks
```

作用：

```text
创建 AI Review 任务，可能触发 GitHub API、Diff 获取、AI 模型调用和数据库写入。
```

限流规则：

```text
同一 IP 每 60 秒最多 5 次
```

Redis Key：

```text
rate_limit:ip:{ip}:create-review
```

示例：

```text
rate_limit:ip:127.0.0.1:create-review
```

------

### 5.2 同一个 PR 重复分析限流

场景：

```text
用户在短时间内反复提交同一个 PR 链接。
```

限流规则：

```text
同一个 PR 每 30 秒最多创建 1 次新任务
```

Redis Key：

```text
rate_limit:pr:{owner}:{repo}:{prNumber}
```

示例：

```text
rate_limit:pr:bianjunfeng:ai_code_review:11
```

说明：

该限流只用于“未命中缓存，需要创建新任务”的场景。如果同一个 PR 已经命中历史成功报告，应直接返回缓存结果，不应该被 PR 级限流误伤。

------

### 5.3 forceRefresh 强制重新分析限流

场景：

```text
用户传入 forceRefresh=true，强制绕过缓存重新生成报告。
```

限流规则：

```text
同一个 PR 每 300 秒最多强制刷新 1 次
```

Redis Key：

```text
rate_limit:force_refresh:{owner}:{repo}:{prNumber}
```

示例：

```text
rate_limit:force_refresh:bianjunfeng:ai_code_review:11
```

说明：

`forceRefresh=true` 会绕过数据库缓存，重新调用 AI 模型，成本更高，因此必须限流。

------

### 5.4 GitHub PR 列表接口限流

接口：

```http
GET /api/github/pulls?owner=xxx&repo=xxx&state=open
```

作用：

```text
调用 GitHub API 获取某个仓库的 Pull Request 列表。
```

限流规则：

```text
同一 IP + 同一仓库 每 60 秒最多 20 次
```

Redis Key：

```text
rate_limit:github_pulls:{ip}:{owner}:{repo}
```

示例：

```text
rate_limit:github_pulls:127.0.0.1:bianjunfeng:ai_code_review
```

------

### 5.5 全局 AI 模型调用限流

场景：

```text
所有模型调用统一保护，避免 AI API 调用失控。
```

限流规则：

```text
全局每 60 秒最多 30 次 AI 调用
```

Redis Key：

```text
rate_limit:global:ai-call
```

说明：

该限流应放在统一的 LLM 调用入口，例如 `LlmClient` 或 `AiReviewService`。

------

## 六、限流规则汇总

| 限流对象                 | Redis Key                                            | 时间窗口 | 最大次数 | 说明                       |
| ------------------------ | ---------------------------------------------------- | -------- | -------- | -------------------------- |
| 创建 Review 任务 IP 限流 | `rate_limit:ip:{ip}:create-review`                   | 60 秒    | 5        | 防止用户频繁创建任务       |
| 同一 PR 新任务限流       | `rate_limit:pr:{owner}:{repo}:{prNumber}`            | 30 秒    | 1        | 防止同一 PR 短时间重复分析 |
| 强制重新分析限流         | `rate_limit:force_refresh:{owner}:{repo}:{prNumber}` | 300 秒   | 1        | 防止频繁绕过缓存           |
| GitHub PR 列表限流       | `rate_limit:github_pulls:{ip}:{owner}:{repo}`        | 60 秒    | 20       | 防止频繁调用 GitHub API    |
| 全局 AI 调用限流         | `rate_limit:global:ai-call`                          | 60 秒    | 30       | 控制 AI 调用成本           |

------

## 七、Redis 配置设计

### 7.1 Maven 依赖

在 `backend/pom.xml` 中添加 Redis 依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

------

### 7.2 application.yml 配置

在 `backend/src/main/resources/application.yml` 中增加 Redis 配置。

注意：如果项目中已经存在 `spring:` 节点，需要合并，不要重复写多个 `spring:`。

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      timeout: ${REDIS_TIMEOUT:3000ms}
```

结合当前项目已有数据库配置，可以是：

```yaml
spring:
  application:
    name: ai-pr-review-backend

  datasource:
    url: ${DB_URL:jdbc:mysql://127.0.0.1:3306/ai_code_review?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:ai_review_user}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver

  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      timeout: ${REDIS_TIMEOUT:3000ms}
```

------

### 7.3 限流配置

建议不要把限流阈值写死在代码里，而是放入配置文件。

```yaml
rate-limit:
  enabled: ${RATE_LIMIT_ENABLED:true}

  create-review:
    ip-limit: ${RATE_LIMIT_CREATE_REVIEW_IP_LIMIT:5}
    ip-window-seconds: ${RATE_LIMIT_CREATE_REVIEW_IP_WINDOW_SECONDS:60}
    pr-limit: ${RATE_LIMIT_CREATE_REVIEW_PR_LIMIT:1}
    pr-window-seconds: ${RATE_LIMIT_CREATE_REVIEW_PR_WINDOW_SECONDS:30}

  force-refresh:
    limit: ${RATE_LIMIT_FORCE_REFRESH_LIMIT:1}
    window-seconds: ${RATE_LIMIT_FORCE_REFRESH_WINDOW_SECONDS:300}

  github-pulls:
    limit: ${RATE_LIMIT_GITHUB_PULLS_LIMIT:20}
    window-seconds: ${RATE_LIMIT_GITHUB_PULLS_WINDOW_SECONDS:60}

  ai-call:
    limit: ${RATE_LIMIT_AI_CALL_LIMIT:30}
    window-seconds: ${RATE_LIMIT_AI_CALL_WINDOW_SECONDS:60}
```

------

## 八、本地 Redis 启动方式

### 8.1 使用 Docker 启动 Redis

```powershell
docker run -d --name ai-review-redis -p 6379:6379 redis:7-alpine
```

### 8.2 查看 Redis 容器

```powershell
docker ps
```

### 8.3 测试 Redis 是否正常

```powershell
docker exec -it ai-review-redis redis-cli ping
```

如果返回：

```text
PONG
```

说明 Redis 正常运行。

------

## 九、环境变量配置

如果本地 Redis 没有密码，可以在 PowerShell 中设置：

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:REDIS_PORT="6379"
$env:REDIS_DATABASE="0"
$env:REDIS_PASSWORD=""
```

然后启动后端：

```powershell
cd D:\code1\ai_code_review\backend
mvn spring-boot:run
```

------

## 十、Redis Key 命名规范

统一使用前缀：

```text
rate_limit:
```

格式：

```text
rate_limit:{dimension}:{identifier}:{action}
```

示例：

```text
rate_limit:ip:127.0.0.1:create-review
rate_limit:pr:bianjunfeng:ai_code_review:11
rate_limit:force_refresh:bianjunfeng:ai_code_review:11
rate_limit:github_pulls:127.0.0.1:bianjunfeng:ai_code_review
rate_limit:global:ai-call
```

规范要求：

1. 全部小写；
2. 用冒号分隔层级；
3. 不在 Key 中放敏感信息；
4. 可以放 owner、repo、prNumber；
5. 不能放 token、apiKey、password；
6. 后续如果有用户系统，可以增加 `user:{userId}` 维度。

------

## 十一、核心实现设计

### 11.1 RateLimitService

新增服务类：

```text
backend/src/main/java/com/example/aipr/service/ratelimit/RateLimitService.java
```

职责：

```text
封装 Redis 固定窗口计数器限流逻辑。
```

核心方法：

```java
boolean tryAcquire(String key, int limit, long windowSeconds);
```

参数说明：

| 参数          | 含义                   |
| ------------- | ---------------------- |
| key           | Redis 限流 Key         |
| limit         | 时间窗口内最大请求次数 |
| windowSeconds | 时间窗口大小，单位秒   |

------

### 11.2 为什么使用 Lua 脚本

简单写法如下：

```java
Long count = redis.opsForValue().increment(key);
if (count == 1) {
    redis.expire(key, Duration.ofSeconds(60));
}
```

该写法存在一个问题：

```text
如果 INCR 成功后 EXPIRE 失败，Key 可能永不过期。
```

因此推荐使用 Lua 脚本，将 `INCR + EXPIRE + 判断是否超限` 放在 Redis 中原子执行。

------

### 11.3 Lua 脚本

```lua
local current = redis.call('INCR', KEYS[1])

if tonumber(current) == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end

if tonumber(current) > tonumber(ARGV[1]) then
    return 0
end

return 1
```

参数说明：

```text
KEYS[1] = Redis 限流 Key
ARGV[1] = 最大次数 limit
ARGV[2] = 时间窗口 windowSeconds
```

------

### 11.4 Java 示例代码

```java
package com.example.aipr.service.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String RATE_LIMIT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if tonumber(current) == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if tonumber(current) > tonumber(ARGV[1]) then
                return 0
            end
            return 1
            """;

    public boolean tryAcquire(String key, int limit, long windowSeconds) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(RATE_LIMIT_SCRIPT);
            script.setResultType(Long.class);

            Long result = stringRedisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(windowSeconds)
            );

            return result != null && result == 1L;
        } catch (Exception e) {
            log.warn("Redis rate limit check failed, key={}, message={}", key, e.getMessage());
            return true;
        }
    }
}
```

说明：

```java
return true;
```

表示 Redis 异常时默认放行，避免 Redis 临时不可用导致主业务不可用。

------

## 十二、限流配置类设计

建议新增：

```text
RateLimitProperties
```

示例：

```java
package com.example.aipr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private CreateReview createReview = new CreateReview();
    private ForceRefresh forceRefresh = new ForceRefresh();
    private GithubPulls githubPulls = new GithubPulls();
    private AiCall aiCall = new AiCall();

    @Data
    public static class CreateReview {
        private int ipLimit = 5;
        private long ipWindowSeconds = 60;
        private int prLimit = 1;
        private long prWindowSeconds = 30;
    }

    @Data
    public static class ForceRefresh {
        private int limit = 1;
        private long windowSeconds = 300;
    }

    @Data
    public static class GithubPulls {
        private int limit = 20;
        private long windowSeconds = 60;
    }

    @Data
    public static class AiCall {
        private int limit = 30;
        private long windowSeconds = 60;
    }
}
```

------

## 十三、客户端 IP 获取设计

新增工具类：

```text
IpUtils
```

获取顺序：

1. `X-Forwarded-For`
2. `X-Real-IP`
3. `request.getRemoteAddr()`

示例：

```java
package com.example.aipr.util;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtils {

    private IpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (isValidIpHeader(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (isValidIpHeader(realIp)) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private static boolean isValidIpHeader(String value) {
        return value != null
                && !value.isBlank()
                && !"unknown".equalsIgnoreCase(value);
    }
}
```

如果后续通过 Nginx 部署，需要在 Nginx 中正确转发：

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

------

## 十四、创建 Review 任务限流流程设计

推荐执行顺序：

```text
POST /api/review-tasks

1. 按 IP 限流；
2. 解析 PR URL；
3. 获取 GitHub PR 信息和 headSha；
4. 如果 forceRefresh=false，先查数据库缓存；
5. 如果命中缓存，直接返回 cached=true；
6. 如果 forceRefresh=true，执行 forceRefresh 限流；
7. 如果未命中缓存，执行同一 PR 创建任务限流；
8. 创建 review_task；
9. 异步执行 AI Review。
```

这样做的好处：

```text
缓存优先，限流保护高成本路径。
```

注意：

```text
普通重复请求如果命中缓存，不应该被 PR 级限流误伤。
forceRefresh=true 必须执行限流。
缓存未命中时才需要执行 PR 级创建任务限流。
```

------

## 十五、Controller 层 IP 限流示例

```java
@PostMapping("/api/review-tasks")
public Result<ReviewTaskCreatedVO> createTask(
        @RequestBody CreateReviewTaskRequest request,
        HttpServletRequest httpRequest
) {
    String ip = IpUtils.getClientIp(httpRequest);

    String key = "rate_limit:ip:" + ip + ":create-review";

    boolean allowed = rateLimitService.tryAcquire(
            key,
            rateLimitProperties.getCreateReview().getIpLimit(),
            rateLimitProperties.getCreateReview().getIpWindowSeconds()
    );

    if (!allowed) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
    }

    return Result.success(reviewTaskService.createTask(request));
}
```

------

## 十六、Service 层 PR 限流示例

PR 限流需要先解析 PR URL，因此适合放在 `ReviewTaskService#createTask` 中。

```java
ParsedPrUrl parsed = prUrlParser.parse(request.getPrUrl());

String prKey = String.format(
        "rate_limit:pr:%s:%s:%s",
        parsed.owner(),
        parsed.repo(),
        parsed.pullNumber()
);

boolean prAllowed = rateLimitService.tryAcquire(
        prKey,
        rateLimitProperties.getCreateReview().getPrLimit(),
        rateLimitProperties.getCreateReview().getPrWindowSeconds()
);

if (!prAllowed) {
    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "同一个 PR 正在处理中，请稍后再试");
}
```

------

## 十七、forceRefresh 限流示例

```java
if (Boolean.TRUE.equals(request.getForceRefresh())) {
    String forceKey = String.format(
            "rate_limit:force_refresh:%s:%s:%s",
            parsed.owner(),
            parsed.repo(),
            parsed.pullNumber()
    );

    boolean forceAllowed = rateLimitService.tryAcquire(
            forceKey,
            rateLimitProperties.getForceRefresh().getLimit(),
            rateLimitProperties.getForceRefresh().getWindowSeconds()
    );

    if (!forceAllowed) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "该 PR 刚刚重新分析过，请稍后再试");
    }
}
```

------

## 十八、GitHub PR 列表接口限流示例

```java
@GetMapping("/api/github/pulls")
public Result<List<GitHubPullVO>> listPulls(
        @RequestParam String owner,
        @RequestParam String repo,
        @RequestParam(defaultValue = "open") String state,
        HttpServletRequest request
) {
    String ip = IpUtils.getClientIp(request);

    String key = String.format(
            "rate_limit:github_pulls:%s:%s:%s",
            ip,
            owner,
            repo
    );

    boolean allowed = rateLimitService.tryAcquire(
            key,
            rateLimitProperties.getGithubPulls().getLimit(),
            rateLimitProperties.getGithubPulls().getWindowSeconds()
    );

    if (!allowed) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "PR 列表刷新过于频繁，请稍后再试");
    }

    return Result.success(gitHubService.listPulls(owner, repo, state));
}
```

------

## 十九、全局 AI 调用限流示例

在统一 LLM 调用入口中接入：

```java
String key = "rate_limit:global:ai-call";

boolean allowed = rateLimitService.tryAcquire(
        key,
        rateLimitProperties.getAiCall().getLimit(),
        rateLimitProperties.getAiCall().getWindowSeconds()
);

if (!allowed) {
    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "AI 模型调用繁忙，请稍后再试");
}
```

适合放置位置：

```text
LlmClient
AiReviewService
OpenAiCompatibleClient
MiniMaxClient
```

推荐放在最统一的模型调用入口，避免遗漏。

------

## 二十、错误码设计

建议在 `ErrorCode` 中新增：

```java
TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试")
```

如果项目中业务码不直接等于 HTTP 状态码，可以使用：

```java
TOO_MANY_REQUESTS(429000, "请求过于频繁，请稍后再试")
```

全局异常处理建议映射为 HTTP 429：

```text
429 Too Many Requests
```

返回示例：

```json
{
  "code": 429,
  "message": "请求过于频繁，请稍后再试",
  "data": null
}
```

------

## 二十一、前端处理设计

前端收到以下情况时：

```text
HTTP 429
```

或业务错误码：

```text
TOO_MANY_REQUESTS
```

统一展示：

```js
ElMessage.warning(error.response?.data?.message || '请求过于频繁，请稍后再试')
```

不同场景可以展示不同文案：

| 场景                     | 提示                             |
| ------------------------ | -------------------------------- |
| 创建任务太频繁           | 请求过于频繁，请稍后再试         |
| 同一 PR 太频繁           | 同一个 PR 正在处理中，请稍后再试 |
| 强制刷新太频繁           | 该 PR 刚刚重新分析过，请稍后再试 |
| GitHub PR 列表刷新太频繁 | PR 列表刷新过于频繁，请稍后再试  |
| AI 全局限流              | AI 模型调用繁忙，请稍后再试      |

------

## 二十二、限流与数据库缓存的关系

限流和缓存不是同一个功能。

### 22.1 数据库缓存解决的问题

```text
同一个 PR 已经有成功报告时，不重复调用 AI。
```

判断条件通常是：

```text
ownerName + repoName + prNumber + headSha + modelName + promptVersion
```

### 22.2 Redis 限流解决的问题

```text
用户短时间内频繁提交请求，防止系统被刷爆。
```

### 22.3 推荐执行顺序

```text
1. IP 限流；
2. 解析 PR URL；
3. 获取 PR 信息和 headSha；
4. 如果 forceRefresh=false，先查询数据库缓存；
5. 命中缓存则直接返回；
6. 如果 forceRefresh=true，执行 forceRefresh 限流；
7. 未命中缓存时，执行 PR 级限流；
8. 创建新任务；
9. 执行 AI Review。
```

这样可以避免：

```text
用户只是重复查看已有历史报告，却被 PR 限流拦截。
```

------

## 二十三、日志设计

限流日志不宜过多，否则会刷屏。

建议：

```java
log.warn("Request blocked by rate limit, key={}, limit={}, windowSeconds={}", key, limit, windowSeconds);
```

一般不建议记录每一次放行日志。只记录：

1. 被限流的请求；
2. Redis 异常；
3. 关键规则命中情况。

注意：

```text
不要打印 token；
不要打印 apiKey；
不要打印数据库密码；
不要打印完整 prompt；
不要打印完整 diff。
```

------

## 二十四、测试方案

### 24.1 启动 Redis

```powershell
docker run -d --name ai-review-redis -p 6379:6379 redis:7-alpine
```

### 24.2 检查 Redis

```powershell
docker exec -it ai-review-redis redis-cli ping
```

预期：

```text
PONG
```

### 24.3 启动后端

```powershell
cd D:\code1\ai_code_review\backend
mvn spring-boot:run
```

------

### 24.4 测试创建任务 IP 限流

连续快速请求 `POST /api/review-tasks` 超过 5 次。

PowerShell 示例：

```powershell
curl -X POST http://localhost:8080/api/review-tasks `
  -H "Content-Type: application/json" `
  -d "{\"prUrl\":\"https://github.com/bianjunfeng/ai_code_review/pull/11\"}"
```

预期：

```text
前 5 次正常；
第 6 次返回请求过于频繁。
```

------

### 24.5 测试同一 PR 限流

在 30 秒内重复提交同一个 PR。

预期：

```text
未命中缓存时，同一个 PR 第二次创建新任务被拒绝。
```

------

### 24.6 测试 forceRefresh 限流

连续请求：

```json
{
  "prUrl": "https://github.com/bianjunfeng/ai_code_review/pull/11",
  "forceRefresh": true
}
```

预期：

```text
5 分钟内第二次 forceRefresh 被拒绝。
```

------

### 24.7 测试 GitHub PR 列表限流

连续刷新：

```http
GET /api/github/pulls?owner=bianjunfeng&repo=ai_code_review&state=open
```

预期：

```text
60 秒内超过 20 次后返回限流提示。
```

------

### 24.8 查看 Redis Key

进入 Redis：

```powershell
docker exec -it ai-review-redis redis-cli
```

查看 Key：

```redis
KEYS rate_limit*
```

查看某个 Key 的值和剩余时间：

```redis
GET rate_limit:ip:127.0.0.1:create-review
TTL rate_limit:ip:127.0.0.1:create-review
```

------

### 24.9 测试 Redis 异常情况

停止 Redis：

```powershell
docker stop ai-review-redis
```

再次请求后端。

预期：

```text
系统不崩溃；
日志记录 warn；
默认放行请求。
```

------

## 二十五、后续优化方向

当前方案是固定窗口计数器限流，适合作为 MVP。

后续可以扩展：

1. 滑动窗口限流；
2. 令牌桶限流；
3. 按用户 ID 限流；
4. 按 GitHub 仓库限流；
5. 按模型供应商限流；
6. AI Token 消耗预算限流；
7. 管理后台配置限流规则；
8. 限流命中统计；
9. 与模型用量监控页面联动；
10. Redis + 数据库记录限流审计日志。

------

## 二十六、Claude Code 编码提示词

下面提示词可以直接发给 Claude Code：

```text
你现在是我的 Java Spring Boot 项目开发助手。当前项目是 AI PR Review 助手，后端会调用 GitHub API 和 AI 模型 API。现在需要新增 Redis 固定窗口计数器限流能力，用来保护高成本接口，避免频繁创建任务、重复分析 PR 和重复调用模型。

请按最小可行方案实现，不要大规模重构。

一、目标

实现 Redis 计数器限流，保护以下场景：

1. 创建 Review 任务接口：
   POST /api/review-tasks
   同一 IP 每 60 秒最多 5 次

2. 同一 PR 重复分析：
   同一个 owner/repo/prNumber 每 30 秒最多 1 次

3. forceRefresh 强制重新分析：
   同一个 owner/repo/prNumber 每 300 秒最多 1 次

4. GitHub PR 列表接口：
   GET /api/github/pulls
   同一 IP + owner/repo 每 60 秒最多 20 次

5. 全局 AI 模型调用：
   所有模型调用每 60 秒最多 30 次

二、依赖

检查 pom.xml 是否已有：

spring-boot-starter-data-redis

如果没有，请添加。

三、Redis 配置

在 application.yml 中新增或合并：

spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      timeout: ${REDIS_TIMEOUT:3000ms}

四、限流配置

新增 application.yml 配置：

rate-limit:
  enabled: ${RATE_LIMIT_ENABLED:true}
  create-review:
    ip-limit: ${RATE_LIMIT_CREATE_REVIEW_IP_LIMIT:5}
    ip-window-seconds: ${RATE_LIMIT_CREATE_REVIEW_IP_WINDOW_SECONDS:60}
    pr-limit: ${RATE_LIMIT_CREATE_REVIEW_PR_LIMIT:1}
    pr-window-seconds: ${RATE_LIMIT_CREATE_REVIEW_PR_WINDOW_SECONDS:30}
  force-refresh:
    limit: ${RATE_LIMIT_FORCE_REFRESH_LIMIT:1}
    window-seconds: ${RATE_LIMIT_FORCE_REFRESH_WINDOW_SECONDS:300}
  github-pulls:
    limit: ${RATE_LIMIT_GITHUB_PULLS_LIMIT:20}
    window-seconds: ${RATE_LIMIT_GITHUB_PULLS_WINDOW_SECONDS:60}
  ai-call:
    limit: ${RATE_LIMIT_AI_CALL_LIMIT:30}
    window-seconds: ${RATE_LIMIT_AI_CALL_WINDOW_SECONDS:60}

五、RateLimitProperties

新增 RateLimitProperties，并使用 @ConfigurationProperties(prefix = "rate-limit") 绑定配置。

六、RateLimitService

新增 RateLimitService，使用 StringRedisTemplate 和 Lua 脚本实现原子限流。

方法：

boolean tryAcquire(String key, int limit, long windowSeconds)

Lua 逻辑：
1. INCR key
2. current == 1 时设置 EXPIRE
3. current > limit 返回 0
4. 否则返回 1

要求：
- Redis 异常时记录 warn 日志；
- Redis 异常时默认放行，不影响主流程；
- 不打印敏感信息。

七、Redis Key 设计

请使用以下 key：

1. IP 创建任务限流：
rate_limit:ip:{ip}:create-review

2. PR 级限流：
rate_limit:pr:{owner}:{repo}:{prNumber}

3. forceRefresh 限流：
rate_limit:force_refresh:{owner}:{repo}:{prNumber}

4. GitHub PR 列表限流：
rate_limit:github_pulls:{ip}:{owner}:{repo}

5. 全局 AI 调用限流：
rate_limit:global:ai-call

八、IP 获取工具

新增 IpUtils。

获取顺序：
1. X-Forwarded-For
2. X-Real-IP
3. request.getRemoteAddr()

九、错误码

在 ErrorCode 中新增 TOO_MANY_REQUESTS。

建议返回：
请求过于频繁，请稍后再试

如果项目有全局异常处理，请映射为 HTTP 429 或业务码 429。

十、创建 Review 任务限流

在 POST /api/review-tasks 或 ReviewTaskService#createTask 中接入限流。

建议流程：

1. 按 IP 限流；
2. 解析 PR URL；
3. 获取 GitHub PR 信息和 headSha；
4. 如果 forceRefresh=false，先查数据库缓存；
5. 如果命中缓存，直接返回，不做 PR 级限流；
6. 如果 forceRefresh=true，执行 forceRefresh 限流；
7. 未命中缓存时，执行 PR 级限流；
8. 创建新任务。

注意：
- 普通重复请求如果命中缓存，不应该被 PR 级限流误伤；
- forceRefresh 必须限流；
- 缓存未命中才需要走 PR 级创建任务限流。

十一、GitHub PR 列表限流

在 GET /api/github/pulls 中接入：

key:
rate_limit:github_pulls:{ip}:{owner}:{repo}

规则：
每 60 秒最多 20 次

十二、AI 调用限流

在统一 LLM 调用入口接入：

key:
rate_limit:global:ai-call

规则：
每 60 秒最多 30 次

如果超过限制，抛出：
AI 模型调用繁忙，请稍后再试

十三、前端适配

如果前端收到 TOO_MANY_REQUESTS 或 HTTP 429，提示：

请求过于频繁，请稍后再试

十四、测试

请给出测试步骤：

1. 启动 Redis；
2. 启动后端；
3. 连续请求创建 Review 任务超过 5 次，验证 IP 限流；
4. 同一个 PR 30 秒内重复提交，验证 PR 限流；
5. forceRefresh=true 5 分钟内重复提交，验证强制刷新限流；
6. 高频刷新 GitHub PR 列表，验证 GitHub 接口限流；
7. 临时关闭 Redis，确认后端不崩溃，warn 后默认放行。

十五、输出要求

完成后输出：
1. 修改文件清单；
2. Redis 配置说明；
3. 限流规则说明；
4. Redis Key 设计；
5. 测试步骤；
6. 是否影响现有接口。

请确保：
mvn clean package -DskipTests
可以通过。
```

------

## 二十七、总结

本方案通过 Redis 固定窗口计数器限流，对 AI PR Review 项目中的高成本接口进行保护。

最终效果：

1. 防止频繁创建 Review 任务；
2. 防止同一 PR 被重复分析；
3. 防止用户频繁绕过缓存重新调用 AI；
4. 防止 GitHub API 被频繁刷新；
5. 防止 AI 模型调用成本失控；
6. 为后续部署、多实例、成本监控和模型用量监控打基础。

当前阶段推荐优先实现：

```text
P0：Redis 配置 + RateLimitService
P0：POST /api/review-tasks 限流
P0：forceRefresh 限流
P1：GitHub PR 列表限流
P1：全局 AI 调用限流
```