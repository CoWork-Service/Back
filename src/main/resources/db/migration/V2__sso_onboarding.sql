ALTER TABLE organizations
    ADD COLUMN department VARCHAR(100) NULL AFTER name;

CREATE INDEX idx_organizations_department ON organizations (department);

ALTER TABLE users
    ADD COLUMN student_id VARCHAR(20) NULL AFTER organization_id;

CREATE UNIQUE INDEX uq_users_student_id ON users (student_id);

CREATE TABLE sso_temp_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(100) NOT NULL UNIQUE,
    student_id VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    department VARCHAR(100) NULL,
    email VARCHAR(200) NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE organization_departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_organization_department_name (organization_id, name),
    CONSTRAINT fk_organization_departments_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
