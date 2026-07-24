-- Only this column may be used to restore a registered session from a device token.
-- Historical first/latest user ids may have originated from an untrusted clientUid.
ALTER TABLE app_h5_visitor_device
    ADD COLUMN trusted_user_id BIGINT NULL AFTER latest_user_id,
    ADD CONSTRAINT fk_h5_visitor_trusted_user
        FOREIGN KEY (trusted_user_id) REFERENCES app_user(id);

CREATE INDEX idx_h5_visitor_trusted_user_id ON app_h5_visitor_device(trusted_user_id);
