# Service Layer Map

MVP 主链路：

```text
ReviewTaskController
-> ReviewTaskService
-> GitHubPullRequestService
-> GitHubClient
-> ReviewTaskStore
-> ReviewReportService
-> VO
```

职责边界：

- `service.github`：只处理 PR URL、GitHub API、GitHub 数据到内部快照的映射。
- `service.review`：创建任务、维护状态、保存任务文件、提供任务查询。
- `service.report`：从任务、文件、评论聚合前端报告。
- `service.ai` / `service.prompt`：后续接入模型和 Prompt，不直接碰 Controller。

当前不引入 Skill、RAG、Agent。它们只作为文档中的未来扩展方向保留。
