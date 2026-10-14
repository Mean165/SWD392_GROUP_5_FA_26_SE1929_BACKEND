-- AI VIVA EXAMINATION SYSTEM
-- PostgreSQL 15+ recommended
-- Schema proposal: normalized core tables for all 7 functional groups.
-- Run in a dedicated database. Review retention/privacy policy before production.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Roles and users
CREATE TABLE role (
    role_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_code VARCHAR(30) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id UUID NOT NULL REFERENCES role(role_id) ON DELETE RESTRICT,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    student_or_staff_code VARCHAR(50) UNIQUE,
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'vi'
        CHECK (preferred_language IN ('vi','en')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Subjects, topics, teaching assignment and student enrollment
CREATE TABLE subject (
    subject_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_code VARCHAR(30) NOT NULL UNIQUE,
    subject_name VARCHAR(200) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lecturer_subject (
    lecturer_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    subject_id UUID NOT NULL REFERENCES subject(subject_id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lecturer_id, subject_id)
);

CREATE TABLE student_enrollment (
    student_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    subject_id UUID NOT NULL REFERENCES subject(subject_id) ON DELETE CASCADE,
    semester VARCHAR(30) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, subject_id, semester)
);

CREATE TABLE subject_topic (
    topic_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_id UUID NOT NULL REFERENCES subject(subject_id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    topic_code VARCHAR(50) NOT NULL,
    topic_name VARCHAR(200) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subject_id, topic_code)
);

-- 3. Source material and RAG processing
CREATE TABLE topic_material (
    material_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL REFERENCES subject_topic(topic_id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    file_type VARCHAR(30) NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no > 0),
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (processing_status IN ('PENDING','PROCESSING','INDEXED','FAILED')),
    processing_error TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic_id, title, version_no)
);

CREATE TABLE material_chunk (
    chunk_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    material_id UUID NOT NULL REFERENCES topic_material(material_id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content_text TEXT NOT NULL,
    page_number INTEGER CHECK (page_number IS NULL OR page_number > 0),
    embedding_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (material_id, chunk_index)
);
-- embedding_ref may reference an external vector store. For in-PostgreSQL vectors,
-- install pgvector and add a vector column with the chosen embedding dimension.

-- 4. Question bank and rubric
CREATE TABLE question (
    question_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL REFERENCES subject_topic(topic_id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    question_text TEXT NOT NULL,
    bloom_level VARCHAR(20) NOT NULL
        CHECK (bloom_level IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE')),
    source_type VARCHAR(20) NOT NULL
        CHECK (source_type IN ('MANUAL','IMPORT','AI_RAG')),
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (approval_status IN ('PENDING','APPROVED','REJECTED','ARCHIVED')),
    difficulty_level SMALLINT CHECK (difficulty_level BETWEEN 1 AND 5),
    source_material_id UUID REFERENCES topic_material(material_id) ON DELETE SET NULL,
    version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rubric_criteria (
    criteria_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    question_id UUID NOT NULL REFERENCES question(question_id) ON DELETE CASCADE,
    criteria_name VARCHAR(200) NOT NULL,
    expected_knowledge_points TEXT NOT NULL,
    max_points NUMERIC(6,2) NOT NULL CHECK (max_points > 0),
    weight_ratio NUMERIC(5,4) NOT NULL CHECK (weight_ratio >= 0 AND weight_ratio <= 1),
    display_order INTEGER NOT NULL DEFAULT 1 CHECK (display_order > 0),
    UNIQUE (question_id, display_order)
);

CREATE TABLE rubric_score_level (
    score_level_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    criteria_id UUID NOT NULL REFERENCES rubric_criteria(criteria_id) ON DELETE CASCADE,
    score_value NUMERIC(6,2) NOT NULL CHECK (score_value >= 0),
    level_label VARCHAR(100) NOT NULL,
    performance_description TEXT NOT NULL,
    UNIQUE (criteria_id, score_value)
);

-- 5. Exam setup and question pool
CREATE TABLE exam_session (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_id UUID NOT NULL REFERENCES subject(subject_id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    session_name VARCHAR(200) NOT NULL,
    semester VARCHAR(30),
    language_code VARCHAR(10) NOT NULL DEFAULT 'vi'
        CHECK (language_code IN ('vi','en')),
    max_main_questions INTEGER NOT NULL CHECK (max_main_questions > 0),
    max_followup_per_question INTEGER NOT NULL DEFAULT 2 CHECK (max_followup_per_question >= 0),
    answer_time_limit_seconds INTEGER NOT NULL DEFAULT 120 CHECK (answer_time_limit_seconds > 0),
    question_repeat_window INTEGER NOT NULL DEFAULT 3 CHECK (question_repeat_window >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_time IS NULL OR start_time IS NULL OR end_time > start_time)
);

CREATE TABLE exam_session_topic (
    session_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES subject_topic(topic_id) ON DELETE RESTRICT,
    PRIMARY KEY (session_id, topic_id)
);

CREATE TABLE exam_session_question (
    session_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES question(question_id) ON DELETE RESTRICT,
    selection_weight NUMERIC(6,2) NOT NULL DEFAULT 1 CHECK (selection_weight > 0),
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (session_id, question_id)
);

CREATE TABLE student_session_assignment (
    assignment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    scheduled_time TIMESTAMPTZ NOT NULL,
    scheduled_duration_minutes INTEGER NOT NULL DEFAULT 15 CHECK (scheduled_duration_minutes > 0),
    queue_order INTEGER NOT NULL CHECK (queue_order > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED','CHECKED_IN','IN_PROGRESS','COMPLETED','ABSENT','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, student_id),
    UNIQUE (session_id, queue_order)
);
CREATE INDEX idx_assignment_student_time ON student_session_assignment(student_id, scheduled_time);

-- 6. Attempt, turns, audio and acoustic metrics
CREATE TABLE viva_attempt (
    attempt_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    assignment_id UUID NOT NULL UNIQUE REFERENCES student_session_assignment(assignment_id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED'
        CHECK (status IN ('NOT_STARTED','IN_PROGRESS','SUBMITTED','UNDER_REVIEW','GRADED','CANCELLED','ERROR')),
    full_audio_url TEXT,
    full_video_url TEXT,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at)
);

CREATE TABLE interaction_turn (
    turn_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attempt_id UUID NOT NULL REFERENCES viva_attempt(attempt_id) ON DELETE CASCADE,
    parent_turn_id UUID REFERENCES interaction_turn(turn_id) ON DELETE SET NULL,
    main_question_id UUID REFERENCES question(question_id) ON DELETE SET NULL,
    turn_order INTEGER NOT NULL CHECK (turn_order > 0),
    turn_type VARCHAR(20) NOT NULL
        CHECK (turn_type IN ('MAIN_QUESTION','FOLLOW_UP','STUDENT_ANSWER','SYSTEM_NOTICE')),
    question_text TEXT,
    transcript_text TEXT,
    audio_segment_url TEXT,
    asked_at TIMESTAMPTZ,
    answered_at TIMESTAMPTZ,
    stt_language VARCHAR(10),
    stt_confidence NUMERIC(5,4) CHECK (stt_confidence IS NULL OR stt_confidence BETWEEN 0 AND 1),
    ai_model VARCHAR(100),
    ai_reasoning_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (attempt_id, turn_order),
    CHECK (answered_at IS NULL OR asked_at IS NULL OR answered_at >= asked_at)
);

CREATE TABLE acoustic_fluency_metric (
    metric_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_id UUID NOT NULL UNIQUE REFERENCES interaction_turn(turn_id) ON DELETE CASCADE,
    words_per_minute NUMERIC(7,2) CHECK (words_per_minute IS NULL OR words_per_minute >= 0),
    hesitation_pauses_count INTEGER CHECK (hesitation_pauses_count IS NULL OR hesitation_pauses_count >= 0),
    speech_clarity_score NUMERIC(5,2) CHECK (speech_clarity_score IS NULL OR speech_clarity_score BETWEEN 0 AND 100),
    measured_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. AI assessment and evidence
CREATE TABLE ai_rubric_assessment (
    assessment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_id UUID NOT NULL REFERENCES interaction_turn(turn_id) ON DELETE CASCADE,
    criteria_id UUID NOT NULL REFERENCES rubric_criteria(criteria_id) ON DELETE RESTRICT,
    semantic_similarity NUMERIC(5,4) CHECK (semantic_similarity IS NULL OR semantic_similarity BETWEEN 0 AND 1),
    compliance_status VARCHAR(30) NOT NULL
        CHECK (compliance_status IN ('MET','PARTIAL','NOT_MET','UNCERTAIN')),
    ai_suggested_points NUMERIC(6,2) NOT NULL CHECK (ai_suggested_points >= 0),
    missing_concepts TEXT,
    explanation TEXT,
    model_name VARCHAR(100),
    prompt_version VARCHAR(50),
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (turn_id, criteria_id)
);

CREATE TABLE ai_claim_evidence (
    claim_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_id UUID NOT NULL REFERENCES interaction_turn(turn_id) ON DELETE CASCADE,
    assessment_id UUID NOT NULL REFERENCES ai_rubric_assessment(assessment_id) ON DELETE CASCADE,
    student_claim_text TEXT NOT NULL,
    exact_quote_evidence TEXT NOT NULL,
    start_timestamp_ms INTEGER CHECK (start_timestamp_ms IS NULL OR start_timestamp_ms >= 0),
    end_timestamp_ms INTEGER CHECK (end_timestamp_ms IS NULL OR end_timestamp_ms >= start_timestamp_ms),
    is_factually_correct BOOLEAN,
    evidence_note TEXT
);

-- 8. Final grade, revisions, recording consent, audit and settings
CREATE TABLE final_grade_report (
    report_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attempt_id UUID NOT NULL UNIQUE REFERENCES viva_attempt(attempt_id) ON DELETE CASCADE,
    total_ai_score NUMERIC(7,2) NOT NULL DEFAULT 0 CHECK (total_ai_score >= 0),
    final_teacher_score NUMERIC(7,2) CHECK (final_teacher_score IS NULL OR final_teacher_score >= 0),
    ai_overall_feedback TEXT,
    lecturer_feedback TEXT,
    grading_status VARCHAR(20) NOT NULL DEFAULT 'AI_PENDING'
        CHECK (grading_status IN ('AI_PENDING','AI_SUGGESTED','LECTURER_REVIEW','FINALIZED')),
    finalized_by_lecturer_id UUID REFERENCES app_user(user_id) ON DELETE RESTRICT,
    finalized_at TIMESTAMPTZ,
    teacher_adjustment_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((grading_status <> 'FINALIZED') OR
           (final_teacher_score IS NOT NULL AND finalized_by_lecturer_id IS NOT NULL AND finalized_at IS NOT NULL))
);

CREATE TABLE grade_revision (
    revision_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    report_id UUID NOT NULL REFERENCES final_grade_report(report_id) ON DELETE CASCADE,
    changed_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    old_score NUMERIC(7,2),
    new_score NUMERIC(7,2) NOT NULL CHECK (new_score >= 0),
    reason TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recording_consent (
    consent_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    assignment_id UUID NOT NULL REFERENCES student_session_assignment(assignment_id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    consent_type VARCHAR(20) NOT NULL CHECK (consent_type IN ('AUDIO','VIDEO','DATA_PROCESSING')),
    consent_given BOOLEAN NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    UNIQUE (assignment_id, consent_type, recorded_at)
);

CREATE TABLE audit_log (
    audit_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_user_id UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

CREATE TABLE system_setting (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value JSONB NOT NULL,
    description TEXT,
    updated_by UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
