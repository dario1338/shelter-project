CREATE TABLE refresh_tokens (
                                id         BIGSERIAL PRIMARY KEY,
                                user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                token_hash VARCHAR(64)  NOT NULL UNIQUE,
                                family_id  UUID         NOT NULL,
                                expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                used_at    TIMESTAMP WITH TIME ZONE,
                                revoked_at TIMESTAMP WITH TIME ZONE,
                                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- The key is the SHA-256 of the normalized email, so arbitrary input never ends up as a key.
CREATE TABLE login_attempts (
                                email_hash   VARCHAR(64) PRIMARY KEY,
                                failed_count INTEGER     NOT NULL,
                                window_start TIMESTAMP WITH TIME ZONE NOT NULL,
                                locked_until TIMESTAMP WITH TIME ZONE
);