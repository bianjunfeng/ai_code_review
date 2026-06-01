-- Upgrade older deployments whose review_comment table was created before
-- reason/evidence/action_level were added to the AI review result contract.

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE review_comment ADD COLUMN reason TEXT DEFAULT NULL COMMENT ''风险原因说明'' AFTER description',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'review_comment'
      AND COLUMN_NAME = 'reason'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE review_comment ADD COLUMN evidence TEXT DEFAULT NULL COMMENT ''风险证据，来自diff或PR上下文'' AFTER reason',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'review_comment'
      AND COLUMN_NAME = 'evidence'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE review_comment ADD COLUMN action_level VARCHAR(20) DEFAULT ''OPTIONAL'' COMMENT ''处理级别：MUST_FIX/SHOULD_FIX/OPTIONAL'' AFTER evidence',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'review_comment'
      AND COLUMN_NAME = 'action_level'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
