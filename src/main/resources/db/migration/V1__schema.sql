-- V1: core schema for company tracker

CREATE TABLE app_locks (
    lock_name VARCHAR(64) PRIMARY KEY
);

INSERT INTO app_locks (lock_name) VALUES ('first_admin');

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    security_stamp  UUID         NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deletion_batch_id UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT users_role_chk CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));

CREATE TABLE companies (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES users (id),
    name            VARCHAR(200) NOT NULL,
    website         VARCHAR(500),
    industry        VARCHAR(200),
    location        VARCHAR(200),
    career_page_url VARCHAR(500),
    deleted_at      TIMESTAMPTZ,
    deletion_batch_id UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_companies_owner_active ON companies (owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_companies_owner_name_active ON companies (owner_id, LOWER(name)) WHERE deleted_at IS NULL;

CREATE TABLE resumes (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id          BIGINT       NOT NULL REFERENCES users (id),
    label             VARCHAR(200) NOT NULL,
    storage_key       VARCHAR(500),
    original_filename VARCHAR(255),
    content_type      VARCHAR(100),
    size_bytes        BIGINT,
    external_url      VARCHAR(1000),
    deleted_at        TIMESTAMPTZ,
    deletion_batch_id UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT resumes_file_or_url_chk CHECK (
        storage_key IS NOT NULL OR (external_url IS NOT NULL AND external_url <> '')
    )
);

CREATE INDEX idx_resumes_owner_active ON resumes (owner_id) WHERE deleted_at IS NULL;

CREATE TABLE job_applications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES companies (id),
    owner_id        BIGINT       NOT NULL REFERENCES users (id),
    title           VARCHAR(200) NOT NULL,
    jd_text         TEXT,
    jd_link         VARCHAR(1000),
    work_mode       VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    compensation    VARCHAR(200),
    applied_date    DATE,
    referral        BOOLEAN      NOT NULL DEFAULT FALSE,
    source          VARCHAR(200),
    status          VARCHAR(32)  NOT NULL DEFAULT 'WISHLIST',
    deleted_at      TIMESTAMPTZ,
    deletion_batch_id UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT job_applications_work_mode_chk CHECK (work_mode IN ('REMOTE', 'HYBRID', 'ONSITE', 'UNKNOWN')),
    CONSTRAINT job_applications_status_chk CHECK (status IN (
        'WISHLIST', 'APPLIED', 'OA', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN', 'GHOSTED', 'ON_HOLD'
    ))
);

CREATE INDEX idx_job_apps_owner_status_active ON job_applications (owner_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_job_apps_company_active ON job_applications (company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_job_apps_owner_applied_active ON job_applications (owner_id, applied_date) WHERE deleted_at IS NULL;

CREATE TABLE rounds (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id  BIGINT       NOT NULL REFERENCES job_applications (id),
    owner_id        BIGINT       NOT NULL REFERENCES users (id),
    name            VARCHAR(200) NOT NULL,
    scheduled_at    TIMESTAMPTZ,
    outcome         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    interviewers    VARCHAR(500),
    resume_id       BIGINT REFERENCES resumes (id) ON DELETE SET NULL,
    deleted_at      TIMESTAMPTZ,
    deletion_batch_id UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT rounds_outcome_chk CHECK (outcome IN ('PENDING', 'PASS', 'FAIL', 'SKIPPED'))
);

CREATE INDEX idx_rounds_owner_scheduled_active ON rounds (owner_id, scheduled_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_rounds_application_active ON rounds (application_id) WHERE deleted_at IS NULL;

CREATE TABLE notes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES users (id),
    title           VARCHAR(200) NOT NULL,
    body            TEXT         NOT NULL,
    company_id      BIGINT REFERENCES companies (id),
    application_id  BIGINT REFERENCES job_applications (id),
    round_id        BIGINT REFERENCES rounds (id),
    deleted_at      TIMESTAMPTZ,
    deletion_batch_id UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT notes_single_parent_chk CHECK (
        (CASE WHEN company_id IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN application_id IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN round_id IS NOT NULL THEN 1 ELSE 0 END) = 1
    )
);

CREATE INDEX idx_notes_company_active ON notes (company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_notes_application_active ON notes (application_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_notes_round_active ON notes (round_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_notes_owner_active ON notes (owner_id) WHERE deleted_at IS NULL;

CREATE TABLE resources (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id          BIGINT       NOT NULL REFERENCES users (id),
    title             VARCHAR(200) NOT NULL,
    url               VARCHAR(1000),
    storage_key       VARCHAR(500),
    original_filename VARCHAR(255),
    content_type      VARCHAR(100),
    size_bytes        BIGINT,
    company_id        BIGINT REFERENCES companies (id),
    application_id    BIGINT REFERENCES job_applications (id),
    round_id          BIGINT REFERENCES rounds (id),
    deleted_at        TIMESTAMPTZ,
    deletion_batch_id UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT resources_single_parent_chk CHECK (
        (CASE WHEN company_id IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN application_id IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN round_id IS NOT NULL THEN 1 ELSE 0 END) = 1
    ),
    CONSTRAINT resources_url_or_file_chk CHECK (
        (url IS NOT NULL AND url <> '') OR storage_key IS NOT NULL
    )
);

CREATE INDEX idx_resources_company_active ON resources (company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_resources_application_active ON resources (application_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_resources_round_active ON resources (round_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_resources_owner_active ON resources (owner_id) WHERE deleted_at IS NULL;
