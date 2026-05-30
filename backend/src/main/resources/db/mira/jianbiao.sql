ALTER TABLE review_task
    ADD COLUMN head_sha VARCHAR(64) DEFAULT NULL COMMENT 'PR 头分支 SHA',
    ADD COLUMN base_sha VARCHAR(64) DEFAULT NULL COMMENT 'PR 基分支 SHA',
    ADD COLUMN model_name VARCHAR(128) DEFAULT NULL COMMENT 'AI 模型名称',
    ADD COLUMN prompt_version VARCHAR(32) DEFAULT NULL COMMENT 'Prompt 版本',
    ADD COLUMN cached_from_task_id BIGINT DEFAULT NULL COMMENT '缓存自哪个任务';

CREATE INDEX idx_cache_lookup ON review_task (owner_name, repo_name, pr_number, head_sha,
                                              model_name, prompt_version, status);