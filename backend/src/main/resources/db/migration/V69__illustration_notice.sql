CREATE TABLE IF NOT EXISTS app_illustration_notice (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(32) NOT NULL DEFAULT 'update',
  type_label VARCHAR(40) NOT NULL DEFAULT '更新',
  title VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  points_json TEXT NULL,
  important TINYINT(1) NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  KEY idx_illustration_notice_public (enabled, deleted, sort_order, created_at),
  KEY idx_illustration_notice_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
