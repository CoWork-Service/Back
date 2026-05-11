CREATE TABLE stored_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    storage_type VARCHAR(20) NOT NULL,
    bucket VARCHAR(100) NULL,
    object_key VARCHAR(700) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    module VARCHAR(50) NOT NULL,
    cohort_id BIGINT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uq_stored_files_storage_path (storage_path),
    KEY idx_stored_files_module_cohort (module, cohort_id),
    CONSTRAINT fk_stored_files_cohort
        FOREIGN KEY (cohort_id) REFERENCES cohorts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
