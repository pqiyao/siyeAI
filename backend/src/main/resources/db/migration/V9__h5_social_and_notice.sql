-- H5 compat: notices + character social (favorite/like/dislike)

CREATE TABLE app_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    level VARCHAR(16) NOT NULL DEFAULT 'info',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notice_created_at ON app_notice(created_at);

CREATE TABLE app_character_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fav_user_character UNIQUE (user_id, character_id)
);

CREATE INDEX idx_fav_user_id ON app_character_favorite(user_id);
CREATE INDEX idx_fav_character_id ON app_character_favorite(character_id);

CREATE TABLE app_character_vote (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    vote VARCHAR(16) NOT NULL, -- like | dislike
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_vote_user_character UNIQUE (user_id, character_id)
);

CREATE INDEX idx_vote_user_id ON app_character_vote(user_id);
CREATE INDEX idx_vote_character_id ON app_character_vote(character_id);

