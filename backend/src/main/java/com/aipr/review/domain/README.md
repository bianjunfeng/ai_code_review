# Domain Layer

`domain` 放业务内部数据结构，表示 Review 任务、变更文件和 Review 建议。

当前 MVP 先用内存存储承载这些对象；后续接入 MySQL / MyBatis-Plus 时，优先让这些对象与 `review_task`、`review_file`、`review_comment` 三张表对齐。

约束：

- Controller 不直接返回 domain 对象。
- 前端响应统一从 domain 转成 `vo`。
- GitHub API 原始响应不放在这里，外部接口响应留在对应 service 包内。
