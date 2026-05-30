CREATE TABLE IF NOT EXISTS review_task (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                           pr_url VARCHAR(500) NOT NULL COMMENT 'GitHub Pull Request链接',
                                           owner_name VARCHAR(100) DEFAULT NULL COMMENT 'GitHub仓库owner',
                                           repo_name VARCHAR(150) DEFAULT NULL COMMENT 'GitHub仓库名称',
                                           pr_number INT DEFAULT NULL COMMENT 'Pull Request编号',
                                           pr_title VARCHAR(500) DEFAULT NULL COMMENT 'PR标题',
                                           pr_author VARCHAR(100) DEFAULT NULL COMMENT 'PR作者',
                                           source_branch VARCHAR(200) DEFAULT NULL COMMENT '源分支',
                                           target_branch VARCHAR(200) DEFAULT NULL COMMENT '目标分支',
                                           status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/FETCHING_PR/PARSING_DIFF/REVIEWING/SUMMARIZING/SUCCESS/FAILED/CANCELLED',
                                           risk_score INT DEFAULT NULL COMMENT '风险评分，范围0到100',
                                           risk_level VARCHAR(20) DEFAULT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH',
                                           summary TEXT DEFAULT NULL COMMENT 'PR总结',
                                           final_review TEXT DEFAULT NULL COMMENT '最终Review结论',
                                           result_json LONGTEXT DEFAULT NULL COMMENT '完整AI Review结果JSON',
                                           error_message TEXT DEFAULT NULL COMMENT '任务失败原因',
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           INDEX idx_pr_url (pr_url),
                                           INDEX idx_repo_pr (owner_name, repo_name, pr_number),
                                           INDEX idx_created_at (created_at),
                                           INDEX idx_risk_level (risk_level),
                                           INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PR分析任务表';

CREATE TABLE IF NOT EXISTS review_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '关联review_task.id',
    file_path VARCHAR(600) DEFAULT NULL COMMENT '风险所在文件路径',
    line_number INT DEFAULT NULL COMMENT '代码行号',
    risk_type VARCHAR(50) DEFAULT NULL COMMENT '风险类型：BUG_RISK/SECURITY_RISK/PERFORMANCE_RISK/MAINTAINABILITY/STYLE/TEST_RISK/COMPATIBILITY',
    risk_level VARCHAR(20) DEFAULT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/INFO',
    title VARCHAR(300) DEFAULT NULL COMMENT '问题标题',
    description TEXT DEFAULT NULL COMMENT '问题描述',
    reason TEXT DEFAULT NULL COMMENT '风险原因说明',
    suggestion TEXT DEFAULT NULL COMMENT '修改建议',
    confidence DECIMAL(4,2) DEFAULT NULL COMMENT '置信度：0到1之间的小数',
    need_human_check TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要人工确认：0否，1是',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_risk_level (risk_level),
    INDEX idx_risk_type (risk_type),
    INDEX idx_task_level (task_id, risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PR Review评论表';

CREATE TABLE IF NOT EXISTS review_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '关联review_task.id',
    file_path VARCHAR(600) DEFAULT NULL COMMENT '文件路径',
    file_status VARCHAR(30) DEFAULT NULL COMMENT '文件状态：added/modified/removed/renamed',
    language VARCHAR(50) DEFAULT NULL COMMENT '语言类型',
    additions INT NOT NULL DEFAULT 0 COMMENT '新增行数',
    deletions INT NOT NULL DEFAULT 0 COMMENT '删除行数',
    changes INT NOT NULL DEFAULT 0 COMMENT '总变更行数',
    patch LONGTEXT DEFAULT NULL COMMENT 'Diff patch内容',
    ai_summary TEXT DEFAULT NULL COMMENT 'AI文件级总结',
    skipped TINYINT NOT NULL DEFAULT 0 COMMENT '是否跳过AI分析：0否，1是',
    skip_reason VARCHAR(500) DEFAULT NULL COMMENT '跳过原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_file_path (file_path),
    INDEX idx_skipped (skipped)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PR变更文件表';

CREATE TABLE IF NOT EXISTS review_skill_result (
                                                   id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                                   task_id BIGINT NOT NULL COMMENT '关联review_task.id',
                                                   skill_name VARCHAR(100) NOT NULL COMMENT 'Skill名称，例如PRSummarySkill',
                                                   success TINYINT NOT NULL DEFAULT 1 COMMENT '是否执行成功：0否，1是',
                                                   summary TEXT DEFAULT NULL COMMENT 'Skill输出摘要',
                                                   raw_output LONGTEXT DEFAULT NULL COMMENT '模型或规则扫描原始输出',
                                                   created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                   INDEX idx_task_id (task_id),
                                                   INDEX idx_skill_name (skill_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill执行结果表';

CREATE TABLE IF NOT EXISTS review_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    skill_code VARCHAR(100) NOT NULL COMMENT 'Skill编码',
    skill_name VARCHAR(100) NOT NULL COMMENT 'Skill名称',
    skill_type VARCHAR(50) NOT NULL COMMENT 'Skill类型',
    description VARCHAR(500) COMMENT 'Skill描述',
    supported_languages VARCHAR(200) COMMENT '支持语言，如 Java,SQL,Vue',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否，1是',
    priority INT NOT NULL DEFAULT 100 COMMENT '执行优先级，数字越小越优先',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_skill_code (skill_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Review Skill配置表';
