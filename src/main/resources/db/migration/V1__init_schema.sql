CREATE TABLE organizations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    invite_code VARCHAR(10) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(500) NULL,
    join_status ENUM('PENDING','ACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_organization FOREIGN KEY (organization_id) REFERENCES organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cohorts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    label VARCHAR(50) NOT NULL,
    year INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cohorts_organization FOREIGN KEY (organization_id) REFERENCES organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cohort_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('ADMIN','EDITOR','VIEWER') NOT NULL DEFAULT 'EDITOR',
    department ENUM('회장단','총무부','복지국','기획국','홍보국','대외협력','기타') NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_cohort_user (cohort_id, user_id),
    CONSTRAINT fk_cohort_members_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id),
    CONSTRAINT fk_cohort_members_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE memos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    department ENUM('회장단','총무부','복지국','기획국','홍보국','대외협력','기타') NULL,
    priority ENUM('NORMAL','IMPORTANT') NOT NULL DEFAULT 'NORMAL',
    status ENUM('OPEN','DONE') NOT NULL DEFAULT 'OPEN',
    due_date DATE NULL,
    author VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_memos_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cowork_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NULL,
    status ENUM('PLANNING','ONGOING','DONE','CANCELLED') NOT NULL DEFAULT 'PLANNING',
    description TEXT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    location VARCHAR(200) NULL,
    lead_department ENUM('회장단','총무부','복지국','기획국','홍보국','대외협력','기타') NULL,
    organizers JSON NULL,
    budget BIGINT NULL,
    cover_color VARCHAR(20) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_events_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id),
    CONSTRAINT fk_events_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE event_photos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    caption VARCHAR(500) NULL,
    tag VARCHAR(20) NOT NULL DEFAULT '기타',
    uploaded_by BIGINT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_photos_event FOREIGN KEY (event_id) REFERENCES cowork_events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_photos_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE file_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type ENUM('FILE','FOLDER') NOT NULL,
    mime_type VARCHAR(100) NULL,
    size BIGINT NULL,
    parent_id BIGINT NULL,
    storage_path VARCHAR(500) NULL,
    department ENUM('회장단','총무부','복지국','기획국','홍보국','대외협력','기타') NULL,
    uploaded_by VARCHAR(50) NULL,
    event_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_file_items_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id),
    CONSTRAINT fk_file_items_parent FOREIGN KEY (parent_id) REFERENCES file_items(id),
    CONSTRAINT fk_file_items_event FOREIGN KEY (event_id) REFERENCES cowork_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE file_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_item_id BIGINT NOT NULL,
    action ENUM('UPLOAD','UPDATE','RENAME','MOVE','DELETE') NOT NULL,
    actor_id BIGINT NULL,
    actor_name VARCHAR(50) NULL,
    detail JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_logs_file_item FOREIGN KEY (file_item_id) REFERENCES file_items(id),
    CONSTRAINT fk_file_logs_actor FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    date DATE NOT NULL,
    department ENUM('회장단','총무부','복지국','기획국','홍보국','대외협력','기타') NOT NULL,
    category VARCHAR(100) NOT NULL,
    vendor VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    amount BIGINT NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    receipt_storage_path VARCHAR(500) NULL,
    note TEXT NULL,
    event_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_expenses_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id),
    CONSTRAINT fk_expenses_event FOREIGN KEY (event_id) REFERENCES cowork_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NULL,
    tags JSON NULL,
    photo_storage_path VARCHAR(500) NULL,
    quantity INT NOT NULL DEFAULT 1,
    available_quantity INT NOT NULL DEFAULT 1,
    purchase_price BIGINT NULL,
    location VARCHAR(200) NULL,
    status ENUM('AVAILABLE','RENTED','UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE',
    description TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_assets_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rental_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    borrower_name VARCHAR(50) NOT NULL,
    student_id VARCHAR(20) NULL,
    contact VARCHAR(50) NULL,
    rented_at DATETIME NOT NULL,
    due_at DATETIME NOT NULL,
    returned_at DATETIME NULL,
    quantity INT NOT NULL DEFAULT 1,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rental_records_asset FOREIGN KEY (asset_id) REFERENCES assets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    student_number VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    department VARCHAR(100) NULL,
    grade INT NULL,
    payment_status ENUM('PAID','UNPAID') NOT NULL DEFAULT 'UNPAID',
    paid_at DATETIME NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uq_cohort_student (cohort_id, student_number),
    CONSTRAINT fk_students_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE surveys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status ENUM('DRAFT','OPEN','CLOSED') NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    event_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_surveys_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id),
    CONSTRAINT fk_surveys_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_surveys_event FOREIGN KEY (event_id) REFERENCES cowork_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE survey_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    survey_id BIGINT NOT NULL,
    order_index INT NOT NULL DEFAULT 0,
    title VARCHAR(500) NOT NULL,
    type ENUM('SHORT_TEXT','LONG_TEXT','MULTIPLE_CHOICE','CHECKBOX','DROPDOWN') NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_survey_questions_survey FOREIGN KEY (survey_id) REFERENCES surveys(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE question_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    order_index INT NOT NULL DEFAULT 0,
    label VARCHAR(500) NOT NULL,
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES survey_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE survey_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    survey_id BIGINT NOT NULL,
    respondent_name VARCHAR(50) NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_survey_responses_survey FOREIGN KEY (survey_id) REFERENCES surveys(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE response_answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    response_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_text TEXT NULL,
    selected_option_ids JSON NULL,
    CONSTRAINT fk_response_answers_response FOREIGN KEY (response_id) REFERENCES survey_responses(id) ON DELETE CASCADE,
    CONSTRAINT fk_response_answers_question FOREIGN KEY (question_id) REFERENCES survey_questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE workspaces (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    department ENUM('회장단','총무부','복지국','기획국','홍보국','대외협력','기타') NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_workspaces_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meetings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    date DATE NOT NULL,
    attendees JSON NULL,
    agenda TEXT NULL,
    content TEXT NULL,
    created_by BIGINT NULL,
    event_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_meetings_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id),
    CONSTRAINT fk_meetings_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_meetings_event FOREIGN KEY (event_id) REFERENCES cowork_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meeting_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    file_item_id BIGINT NULL,
    storage_path VARCHAR(500) NULL,
    name VARCHAR(255) NOT NULL,
    size BIGINT NULL,
    CONSTRAINT fk_meeting_attachments_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE,
    CONSTRAINT fk_meeting_attachments_file_item FOREIGN KEY (file_item_id) REFERENCES file_items(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE timetables (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cohort_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    date_range_start DATE NOT NULL,
    date_range_end DATE NOT NULL,
    time_range_start TIME NOT NULL,
    time_range_end TIME NOT NULL,
    slot_minutes INT NOT NULL DEFAULT 30,
    status ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
    created_by BIGINT NULL,
    event_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_timetables_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id),
    CONSTRAINT fk_timetables_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_timetables_event FOREIGN KEY (event_id) REFERENCES cowork_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE timetable_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timetable_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    responded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_timetable_participants_timetable FOREIGN KEY (timetable_id) REFERENCES timetables(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE timetable_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timetable_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    selected_slots JSON NOT NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_timetable_responses_timetable FOREIGN KEY (timetable_id) REFERENCES timetables(id),
    CONSTRAINT fk_timetable_responses_participant FOREIGN KEY (participant_id) REFERENCES timetable_participants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mobile_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_token VARCHAR(64) NOT NULL UNIQUE,
    cohort_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    photo_path VARCHAR(500) NULL,
    extra_data JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mobile_sessions_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id),
    CONSTRAINT fk_mobile_sessions_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
