CREATE TABLE users (
    id                     UUID PRIMARY KEY,
    email                  VARCHAR(255) NOT NULL UNIQUE,
    password_hash          VARCHAR(255) NOT NULL,
    display_name           VARCHAR(255) NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_login_attempts  INT NOT NULL DEFAULT 0,
    locked_until            TIMESTAMP WITH TIME ZONE
);

CREATE TABLE revoked_tokens (
    jti         VARCHAR(255) PRIMARY KEY,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
