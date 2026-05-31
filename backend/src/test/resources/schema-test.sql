DROP TABLE IF EXISTS model_usage_log;
DROP TABLE IF EXISTS review_skill_result;
DROP TABLE IF EXISTS review_skill;
DROP TABLE IF EXISTS review_comment;
DROP TABLE IF EXISTS review_file;
DROP TABLE IF EXISTS review_task;

CREATE TABLE review_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    pr_url VARCHAR(500) NOT NULL,
    owner_name VARCHAR(100),
    repo_name VARCHAR(150),
    pr_number INT,
    pr_title VARCHAR(500),
    pr_description CLOB,
    pr_author VARCHAR(100),
    source_branch VARCHAR(200),
    target_branch VARCHAR(200),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    risk_score INT,
    risk_level VARCHAR(20),
    summary CLOB,
    final_review CLOB,
    result_json CLOB,
    error_message CLOB,
    head_sha VARCHAR(64),
    base_sha VARCHAR(64),
    model_name VARCHAR(100),
    prompt_version VARCHAR(50) DEFAULT 'v1',
    cached_from_task_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_review_task_pr_url ON review_task (pr_url);
CREATE INDEX idx_review_task_repo_pr ON review_task (owner_name, repo_name, pr_number);
CREATE INDEX idx_review_task_created_at ON review_task (created_at);
CREATE INDEX idx_review_task_risk_level ON review_task (risk_level);
CREATE INDEX idx_review_task_status ON review_task (status);
CREATE INDEX idx_review_task_cache ON review_task (owner_name, repo_name, pr_number, head_sha, model_name, prompt_version, status);

CREATE TABLE review_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    file_path VARCHAR(600),
    file_status VARCHAR(30),
    language VARCHAR(50),
    additions INT DEFAULT 0,
    deletions INT DEFAULT 0,
    changes INT DEFAULT 0,
    patch CLOB,
    ai_summary CLOB,
    skipped TINYINT NOT NULL DEFAULT 0,
    skip_reason VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_review_file_task_id ON review_file (task_id);
CREATE INDEX idx_review_file_path ON review_file (file_path);

CREATE TABLE review_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    file_path VARCHAR(600),
    line_number INT,
    risk_type VARCHAR(50),
    risk_level VARCHAR(20),
    title VARCHAR(300),
    description CLOB,
    reason CLOB,
    evidence CLOB,
    action_level VARCHAR(20) DEFAULT 'OPTIONAL',
    suggestion CLOB,
    confidence DECIMAL(4, 2),
    need_human_check TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_review_comment_task_id ON review_comment (task_id);
CREATE INDEX idx_review_comment_risk_level ON review_comment (risk_level);
CREATE INDEX idx_review_comment_risk_type ON review_comment (risk_type);

CREATE TABLE review_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_code VARCHAR(100) NOT NULL,
    skill_name VARCHAR(100) NOT NULL,
    skill_type VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    supported_languages VARCHAR(200),
    enabled TINYINT NOT NULL DEFAULT 1,
    priority INT NOT NULL DEFAULT 100,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_review_skill_code ON review_skill (skill_code);

CREATE TABLE review_skill_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    skill_name VARCHAR(100) NOT NULL,
    success TINYINT NOT NULL DEFAULT 1,
    summary CLOB,
    raw_output CLOB,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_review_skill_result_task_id ON review_skill_result (task_id);
CREATE INDEX idx_review_skill_result_skill_name ON review_skill_result (skill_name);

CREATE TABLE model_usage_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT,
    file_id BIGINT,
    skill_code VARCHAR(100),
    provider VARCHAR(200),
    model_name VARCHAR(100),
    call_type VARCHAR(50),
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT,
    success TINYINT NOT NULL DEFAULT 1,
    error_message TEXT,
    estimated_cost DECIMAL(10,4),
    request_id VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_model_usage_log_task_id ON model_usage_log (task_id);
CREATE INDEX idx_model_usage_log_created_at ON model_usage_log (created_at);
CREATE INDEX idx_model_usage_log_success ON model_usage_log (success);
