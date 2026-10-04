-- Run in the auth PostgreSQL database BEFORE enabling FLASHSTOCK_TOKENS_ENABLED.
-- Keep this as a versioned production migration; do not rely on ddl-auto=update.
CREATE TABLE IF NOT EXISTS flashstock_refresh_families (
    family_id VARCHAR(36) PRIMARY KEY,
    expires_at BIGINT NOT NULL,
    revoked_at BIGINT
);
CREATE TABLE IF NOT EXISTS flashstock_refresh_sessions (
    jti VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL REFERENCES flashstock_refresh_families(family_id),
    subject_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    roles VARCHAR(128) NOT NULL,
    token_sha256 VARCHAR(64) NOT NULL UNIQUE,
    expires_at BIGINT NOT NULL,
    used_at BIGINT,
    revoked_at BIGINT
);
CREATE INDEX IF NOT EXISTS ix_flashstock_refresh_sessions_family ON flashstock_refresh_sessions(family_id);
