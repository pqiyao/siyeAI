-- Phase 6: 角色目录（业务侧 characterId 与 ST avatar_url 映射）

CREATE TABLE app_character (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    st_avatar_url VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_character_st_avatar_url ON app_character(st_avatar_url);
CREATE INDEX idx_character_name ON app_character(name);

