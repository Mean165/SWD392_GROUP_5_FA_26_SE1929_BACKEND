-- =============================================================================
-- AI VIVA EXAMINATION SYSTEM - DATABASE SCHEMA (14 original + 2 approved tables)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Table role
CREATE TABLE IF NOT EXISTS role (
    role_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_code VARCHAR(255) UNIQUE NOT NULL,
    role_name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. Table app_user
CREATE TABLE IF NOT EXISTS app_user (
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id UUID NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    student_or_staff_code VARCHAR(255) UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_user_role FOREIGN KEY (role_id) REFERENCES role(role_id) ON DELETE RESTRICT
);

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Table subject_topic
CREATE TABLE IF NOT EXISTS subject (
    subject_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_code VARCHAR(100) UNIQUE NOT NULL,
    subject_name VARCHAR(255) NOT NULL,
    description TEXT,
    assigned_lecturer_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb
);

ALTER TABLE subject ADD COLUMN IF NOT EXISTS description TEXT;

CREATE TABLE IF NOT EXISTS subject_topic (
    topic_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_by UUID NOT NULL,
    topic_code VARCHAR(255) UNIQUE NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    subject_id UUID REFERENCES subject(subject_id) ON DELETE SET NULL,
    CONSTRAINT fk_subject_topic_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id) ON DELETE RESTRICT
);

-- 4. Table topic_material
CREATE TABLE IF NOT EXISTS topic_material (
    material_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    is_indexed BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_topic_material_topic FOREIGN KEY (topic_id) REFERENCES subject_topic(topic_id) ON DELETE CASCADE
);

-- 5. Table question
CREATE TABLE IF NOT EXISTS question (
    question_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL,
    created_by UUID NOT NULL,
    question_text TEXT NOT NULL,
    bloom_level VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    approval_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    generation_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT fk_question_topic FOREIGN KEY (topic_id) REFERENCES subject_topic(topic_id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id) ON DELETE RESTRICT
);

-- Constraints are recreated with DROP IF EXISTS/ADD because Spring's SQL initializer
-- splits PostgreSQL dollar-quoted DO blocks at semicolons.
ALTER TABLE question DROP CONSTRAINT IF EXISTS ck_question_bloom_level;
ALTER TABLE question ADD CONSTRAINT ck_question_bloom_level
    CHECK (bloom_level IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE','EVALUATE','CREATE'));

-- Rubrics are scoped to subjects; legacy topic/Bloom columns remain nullable for audit data.
CREATE TABLE IF NOT EXISTS rubric (
    rubric_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_id UUID,
    topic_id UUID,
    bloom_level VARCHAR(50),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rubric_subject FOREIGN KEY (subject_id) REFERENCES subject(subject_id) ON DELETE CASCADE,
    CONSTRAINT fk_rubric_topic FOREIGN KEY (topic_id) REFERENCES subject_topic(topic_id) ON DELETE CASCADE,
    CONSTRAINT fk_rubric_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id) ON DELETE SET NULL,
    CONSTRAINT ck_rubric_bloom_level CHECK (bloom_level IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE','EVALUATE','CREATE')),
    CONSTRAINT ck_rubric_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);

ALTER TABLE rubric ADD COLUMN IF NOT EXISTS topic_id UUID;
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS bloom_level VARCHAR(50);
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS subject_id UUID;
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE rubric ALTER COLUMN topic_id DROP NOT NULL;
ALTER TABLE rubric ALTER COLUMN bloom_level DROP NOT NULL;
ALTER TABLE rubric DROP CONSTRAINT IF EXISTS fk_rubric_subject;
ALTER TABLE rubric ADD CONSTRAINT fk_rubric_subject FOREIGN KEY (subject_id) REFERENCES subject(subject_id) ON DELETE CASCADE;
ALTER TABLE rubric DROP CONSTRAINT IF EXISTS fk_rubric_topic;
ALTER TABLE rubric ADD CONSTRAINT fk_rubric_topic FOREIGN KEY (topic_id) REFERENCES subject_topic(topic_id) ON DELETE CASCADE;
ALTER TABLE rubric DROP CONSTRAINT IF EXISTS fk_rubric_creator;
ALTER TABLE rubric ADD CONSTRAINT fk_rubric_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id) ON DELETE SET NULL;
ALTER TABLE rubric DROP CONSTRAINT IF EXISTS ck_rubric_status;
ALTER TABLE rubric ADD CONSTRAINT ck_rubric_status CHECK (status IN ('ACTIVE','ARCHIVED'));

UPDATE rubric r SET subject_id = t.subject_id
FROM subject_topic t WHERE r.topic_id = t.topic_id AND r.subject_id IS NULL AND t.subject_id IS NOT NULL;
DROP INDEX IF EXISTS ux_rubric_active_topic_bloom;
UPDATE rubric SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE' AND (topic_id IS NOT NULL OR bloom_level IS NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS ux_rubric_active_subject
    ON rubric(subject_id) WHERE status = 'ACTIVE' AND subject_id IS NOT NULL;

-- 6. Table rubric_criteria
CREATE TABLE IF NOT EXISTS rubric_criteria (
    criteria_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rubric_id UUID,
    question_id UUID,
    criteria_name VARCHAR(255) NOT NULL,
    expected_knowledge_points TEXT NOT NULL,
    weight_ratio DECIMAL(3,2) NOT NULL,
    CONSTRAINT fk_rubric_criteria_rubric FOREIGN KEY (rubric_id) REFERENCES rubric(rubric_id) ON DELETE CASCADE,
    CONSTRAINT fk_rubric_criteria_question FOREIGN KEY (question_id) REFERENCES question(question_id) ON DELETE CASCADE,
    CONSTRAINT ck_rubric_criteria_owner CHECK ((rubric_id IS NOT NULL) <> (question_id IS NOT NULL)),
    CONSTRAINT ck_rubric_criteria_weight CHECK (weight_ratio > 0 AND weight_ratio <= 1)
);

ALTER TABLE rubric_criteria ADD COLUMN IF NOT EXISTS rubric_id UUID;
ALTER TABLE rubric_criteria ALTER COLUMN question_id DROP NOT NULL;
ALTER TABLE rubric_criteria DROP CONSTRAINT IF EXISTS fk_rubric_criteria_rubric;
ALTER TABLE rubric_criteria ADD CONSTRAINT fk_rubric_criteria_rubric
    FOREIGN KEY (rubric_id) REFERENCES rubric(rubric_id) ON DELETE CASCADE;
ALTER TABLE rubric_criteria DROP CONSTRAINT IF EXISTS ck_rubric_criteria_owner;
ALTER TABLE rubric_criteria ADD CONSTRAINT ck_rubric_criteria_owner
    CHECK ((rubric_id IS NOT NULL) <> (question_id IS NOT NULL));
ALTER TABLE rubric_criteria DROP CONSTRAINT IF EXISTS ck_rubric_criteria_weight;
ALTER TABLE rubric_criteria ADD CONSTRAINT ck_rubric_criteria_weight
    CHECK (weight_ratio > 0 AND weight_ratio <= 1);

CREATE TABLE IF NOT EXISTS question_document (
    document_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_id UUID NOT NULL REFERENCES subject(subject_id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES subject_topic(topic_id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    original_filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_reason TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS question_generation_job (
    job_id UUID PRIMARY KEY,
    subject_id UUID NOT NULL REFERENCES subject(subject_id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES subject_topic(topic_id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    document_ids_json JSONB NOT NULL,
    request_json JSONB NOT NULL,
    question_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
    progress INT NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    generated_count INT NOT NULL DEFAULT 0,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS ix_question_generation_job_owner_created
    ON question_generation_job(requested_by, created_at);
CREATE INDEX IF NOT EXISTS ix_question_generation_job_active
    ON question_generation_job(requested_by, subject_id, topic_id, status);

ALTER TABLE question ADD COLUMN IF NOT EXISTS generation_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE subject ADD COLUMN IF NOT EXISTS assigned_lecturer_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE subject_topic ADD COLUMN IF NOT EXISTS subject_id UUID REFERENCES subject(subject_id) ON DELETE SET NULL;

-- 7. Table exam_session
CREATE TABLE IF NOT EXISTS exam_session (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_by UUID NOT NULL,
    topic_id UUID,
    session_name VARCHAR(255) NOT NULL,
    max_main_questions INT NOT NULL,
    max_followup_per_question INT NOT NULL,
    time_limit_minutes INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    buffer_minutes INT NOT NULL DEFAULT 5,
    avoid_repeat_window INT NOT NULL DEFAULT 2,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exam_session_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_session_topic FOREIGN KEY (topic_id) REFERENCES subject_topic(topic_id) ON DELETE RESTRICT
);

ALTER TABLE exam_session
    ADD COLUMN IF NOT EXISTS topic_id UUID REFERENCES subject_topic(topic_id) ON DELETE RESTRICT;
ALTER TABLE exam_session ADD COLUMN IF NOT EXISTS buffer_minutes INT NOT NULL DEFAULT 5;
ALTER TABLE exam_session ADD COLUMN IF NOT EXISTS avoid_repeat_window INT NOT NULL DEFAULT 2;
ALTER TABLE exam_session ADD COLUMN IF NOT EXISTS max_overlap_percent INT NOT NULL DEFAULT 40;
ALTER TABLE exam_session ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE exam_session ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE exam_session SET status='SCHEDULED' WHERE upper(status) IN ('DRAFT','PLANNED');
ALTER TABLE exam_session DROP CONSTRAINT IF EXISTS exam_session_topic_id_fkey;
ALTER TABLE exam_session DROP CONSTRAINT IF EXISTS fk_exam_session_topic;
ALTER TABLE exam_session ADD CONSTRAINT fk_exam_session_topic
    FOREIGN KEY (topic_id) REFERENCES subject_topic(topic_id) ON DELETE RESTRICT;
ALTER TABLE exam_session DROP CONSTRAINT IF EXISTS ck_exam_session_status;
ALTER TABLE exam_session ADD CONSTRAINT ck_exam_session_status
    CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'));
ALTER TABLE exam_session DROP CONSTRAINT IF EXISTS ck_exam_session_buffer;
ALTER TABLE exam_session ADD CONSTRAINT ck_exam_session_buffer CHECK (buffer_minutes >= 0 AND avoid_repeat_window >= 0);
ALTER TABLE exam_session DROP CONSTRAINT IF EXISTS ck_exam_session_max_overlap_percent;
ALTER TABLE exam_session ADD CONSTRAINT ck_exam_session_max_overlap_percent CHECK (max_overlap_percent BETWEEN 0 AND 100);

CREATE TABLE IF NOT EXISTS exam_session_bloom_distribution (
    session_id UUID NOT NULL,
    bloom_level VARCHAR(50) NOT NULL,
    question_count INT NOT NULL,
    CONSTRAINT pk_exam_session_bloom_distribution PRIMARY KEY (session_id, bloom_level),
    CONSTRAINT fk_exam_session_bloom_session FOREIGN KEY (session_id) REFERENCES exam_session(session_id) ON DELETE CASCADE,
    CONSTRAINT ck_exam_session_bloom_level CHECK (bloom_level IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE','EVALUATE','CREATE')),
    CONSTRAINT ck_exam_session_bloom_count CHECK (question_count > 0)
);
ALTER TABLE exam_session_bloom_distribution ADD COLUMN IF NOT EXISTS question_count INT NOT NULL DEFAULT 1;
ALTER TABLE exam_session_bloom_distribution DROP CONSTRAINT IF EXISTS ck_exam_session_bloom_level;
ALTER TABLE exam_session_bloom_distribution ADD CONSTRAINT ck_exam_session_bloom_level
    CHECK (bloom_level IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE','EVALUATE','CREATE'));
ALTER TABLE exam_session_bloom_distribution DROP CONSTRAINT IF EXISTS ck_exam_session_bloom_count;
ALTER TABLE exam_session_bloom_distribution ADD CONSTRAINT ck_exam_session_bloom_count CHECK (question_count > 0);

-- 8. Table student_session_assignment
CREATE TABLE IF NOT EXISTS student_session_assignment (
    assignment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL,
    student_id UUID NOT NULL,
    scheduled_time TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL,
    slot_order INT,
    cancelled_reason TEXT,
    CONSTRAINT fk_assignment_session FOREIGN KEY (session_id) REFERENCES exam_session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_student FOREIGN KEY (student_id) REFERENCES app_user(user_id) ON DELETE RESTRICT
);

ALTER TABLE student_session_assignment ALTER COLUMN scheduled_time DROP NOT NULL;
ALTER TABLE student_session_assignment ADD COLUMN IF NOT EXISTS slot_order INT;
ALTER TABLE student_session_assignment ADD COLUMN IF NOT EXISTS cancelled_reason TEXT;
UPDATE student_session_assignment SET status='COMPLETED' WHERE upper(status)='SUBMITTED';
UPDATE student_session_assignment SET status='SCHEDULED' WHERE upper(status) IN ('INVITED','READY');
ALTER TABLE student_session_assignment DROP CONSTRAINT IF EXISTS ck_assignment_status;
ALTER TABLE student_session_assignment ADD CONSTRAINT ck_assignment_status
    CHECK (status IN ('PENDING','SCHEDULED','IN_PROGRESS','COMPLETED','ABSENT','CANCELLED'));

-- 9. Table viva_attempt
CREATE TABLE IF NOT EXISTS viva_attempt (
    attempt_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    assignment_id UUID UNIQUE NOT NULL,
    full_audio_url VARCHAR(1000),
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    CONSTRAINT fk_viva_attempt_assignment FOREIGN KEY (assignment_id) REFERENCES student_session_assignment(assignment_id) ON DELETE CASCADE
);

ALTER TABLE viva_attempt DROP CONSTRAINT IF EXISTS ck_viva_attempt_status;
UPDATE viva_attempt SET status='COMPLETED' WHERE upper(status)='SUBMITTED';
UPDATE viva_attempt SET status='NOT_STARTED' WHERE upper(status) IN ('INVITED','READY');
ALTER TABLE viva_attempt ADD CONSTRAINT ck_viva_attempt_status
    CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED','ABANDONED','CANCELLED'));

DELETE FROM student_session_assignment duplicate
WHERE duplicate.assignment_id IN (
    SELECT newer.assignment_id
    FROM student_session_assignment newer
    JOIN student_session_assignment older
      ON older.session_id = newer.session_id AND older.student_id = newer.student_id
     AND older.assignment_id < newer.assignment_id
    WHERE NOT EXISTS (SELECT 1 FROM viva_attempt attempt WHERE attempt.assignment_id = newer.assignment_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_assignment_session_student
    ON student_session_assignment(session_id, student_id);
CREATE INDEX IF NOT EXISTS ix_assignment_session_scheduled
    ON student_session_assignment(session_id, scheduled_time);
CREATE INDEX IF NOT EXISTS ix_assignment_student_scheduled
    ON student_session_assignment(student_id, scheduled_time);

CREATE TABLE IF NOT EXISTS attempt_question_plan (
    plan_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attempt_id UUID NOT NULL,
    question_id UUID NOT NULL,
    position INT NOT NULL,
    bloom_level VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempt_plan_attempt FOREIGN KEY (attempt_id) REFERENCES viva_attempt(attempt_id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_plan_question FOREIGN KEY (question_id) REFERENCES question(question_id) ON DELETE RESTRICT,
    CONSTRAINT uq_attempt_plan_position UNIQUE (attempt_id, position),
    CONSTRAINT uq_attempt_plan_question UNIQUE (attempt_id, question_id),
    CONSTRAINT ck_attempt_plan_position CHECK (position > 0),
    CONSTRAINT ck_attempt_plan_bloom CHECK (bloom_level IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE','EVALUATE','CREATE'))
);
ALTER TABLE attempt_question_plan DROP CONSTRAINT IF EXISTS ck_attempt_plan_bloom;
ALTER TABLE attempt_question_plan ADD CONSTRAINT ck_attempt_plan_bloom
    CHECK (bloom_level IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE','EVALUATE','CREATE'));

-- 10. Table interaction_turn
CREATE TABLE IF NOT EXISTS interaction_turn (
    turn_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attempt_id UUID NOT NULL,
    main_question_id UUID,
    turn_order INT NOT NULL,
    turn_type VARCHAR(50) NOT NULL,
    ai_question_text TEXT NOT NULL,
    transcript_text TEXT,
    audio_segment_url VARCHAR(1000),
    ai_reasoning TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_turn_attempt FOREIGN KEY (attempt_id) REFERENCES viva_attempt(attempt_id) ON DELETE CASCADE,
    CONSTRAINT fk_turn_main_question FOREIGN KEY (main_question_id) REFERENCES question(question_id) ON DELETE SET NULL
);

-- 11. Table acoustic_fluency_metric
CREATE TABLE IF NOT EXISTS acoustic_fluency_metric (
    metric_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_id UUID UNIQUE NOT NULL,
    words_per_minute DECIMAL(5,2),
    hesitation_pauses_count INT,
    stt_confidence_score DECIMAL(4,3),
    speech_clarity_score DECIMAL(4,2),
    CONSTRAINT fk_fluency_turn FOREIGN KEY (turn_id) REFERENCES interaction_turn(turn_id) ON DELETE CASCADE
);

-- 12. Table ai_rubric_assessment
CREATE TABLE IF NOT EXISTS ai_rubric_assessment (
    assessment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_id UUID NOT NULL,
    criteria_id UUID NOT NULL,
    semantic_similarity DECIMAL(4,3),
    compliance_status VARCHAR(50) NOT NULL,
    ai_suggested_points DECIMAL(4,2) NOT NULL,
    missing_concepts TEXT,
    ai_explanation_reasoning TEXT,
    CONSTRAINT fk_rubric_assessment_turn FOREIGN KEY (turn_id) REFERENCES interaction_turn(turn_id) ON DELETE CASCADE,
    CONSTRAINT fk_rubric_assessment_criteria FOREIGN KEY (criteria_id) REFERENCES rubric_criteria(criteria_id) ON DELETE CASCADE
);

-- 13. Table ai_claim_evidence
CREATE TABLE IF NOT EXISTS ai_claim_evidence (
    claim_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    student_claim_text TEXT NOT NULL,
    exact_quote_evidence TEXT NOT NULL,
    start_timestamp_ms INT NOT NULL,
    end_timestamp_ms INT NOT NULL,
    is_factually_correct BOOLEAN NOT NULL,
    CONSTRAINT fk_claim_evidence_turn FOREIGN KEY (turn_id) REFERENCES interaction_turn(turn_id) ON DELETE CASCADE,
    CONSTRAINT fk_claim_evidence_assessment FOREIGN KEY (assessment_id) REFERENCES ai_rubric_assessment(assessment_id) ON DELETE CASCADE
);

-- 14. Table final_grade_report
CREATE TABLE IF NOT EXISTS final_grade_report (
    report_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attempt_id UUID UNIQUE NOT NULL,
    finalized_by_lecturer_id UUID,
    total_ai_score DECIMAL(5,2) NOT NULL,
    final_teacher_score DECIMAL(5,2),
    teacher_adjustment_reason TEXT,
    ai_overall_feedback TEXT,
    finalized_at TIMESTAMPTZ,
    CONSTRAINT fk_final_report_attempt FOREIGN KEY (attempt_id) REFERENCES viva_attempt(attempt_id) ON DELETE CASCADE,
    CONSTRAINT fk_final_report_lecturer FOREIGN KEY (finalized_by_lecturer_id) REFERENCES app_user(user_id) ON DELETE SET NULL
);

-- 15. Speech recognition result and audit/event signals.
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS response_latency_ms BIGINT;
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS speech_duration_ms BIGINT;
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS longest_pause_ms BIGINT;
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS filler_count INT;
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS confidence_source VARCHAR(20);

CREATE TABLE IF NOT EXISTS stt_result (
    stt_result_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_id UUID NOT NULL UNIQUE,
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(80) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    streaming_result_json JSONB,
    final_result_json JSONB,
    official_text_raw TEXT,
    official_source VARCHAR(20),
    text_normalized TEXT,
    corrections_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    needs_human_review BOOLEAN NOT NULL DEFAULT FALSE,
    review_reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    reconciliation_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stt_result_turn FOREIGN KEY (turn_id) REFERENCES interaction_turn(turn_id) ON DELETE CASCADE,
    CONSTRAINT ck_stt_result_mode CHECK (mode IN ('streaming','final','dual')),
    CONSTRAINT ck_stt_result_source CHECK (official_source IS NULL OR official_source IN ('FINAL','STREAMING'))
);

CREATE TABLE IF NOT EXISTS session_event (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attempt_id UUID NOT NULL,
    seq BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_event_attempt FOREIGN KEY (attempt_id) REFERENCES viva_attempt(attempt_id) ON DELETE CASCADE,
    CONSTRAINT uq_session_event_attempt_seq UNIQUE (attempt_id, seq),
    CONSTRAINT ck_session_event_seq CHECK (seq > 0)
);

CREATE INDEX IF NOT EXISTS ix_session_event_attempt_created
    ON session_event(attempt_id, created_at);
