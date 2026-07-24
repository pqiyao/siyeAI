-- Per-user read state for session inbox ads (announcement-style channel)

CREATE TABLE IF NOT EXISTS app_user_inbox_ad_read (
    user_id BIGINT NOT NULL,
    ad_id BIGINT NOT NULL,
    read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, ad_id)
);

CREATE INDEX idx_user_inbox_ad_read_ad ON app_user_inbox_ad_read (ad_id);
