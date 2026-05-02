CREATE TABLE sso_temp_tokens (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 token VARCHAR(100) NOT NULL UNIQUE,
                                 student_id VARCHAR(20) NOT NULL,
                                 name VARCHAR(50) NOT NULL,
                                 department VARCHAR(100),
                                 email VARCHAR(200),
                                 expires_at DATETIME NOT NULL
);