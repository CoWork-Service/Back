CREATE TABLE user_policy_consents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    terms_version VARCHAR(30) NOT NULL,
    privacy_version VARCHAR(30) NOT NULL,
    terms_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    privacy_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    agreed_at DATETIME NOT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    INDEX idx_user_policy_consents_user_agreed_at (user_id, agreed_at),
    CONSTRAINT fk_user_policy_consents_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
