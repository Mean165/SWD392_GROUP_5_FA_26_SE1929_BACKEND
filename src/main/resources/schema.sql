-- =============================================================================
-- AI VIVA EXAMINATION SYSTEM - DATABASE SCHEMA (14 TABLES)
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
    student_or_staff_code VARCHAR(255) UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_user_role FOREIGN KEY (role_id) REFERENCES role(role_id) ON DELETE RESTRICT
);

-- 3. Table subject_topic
CREATE TABLE IF NOT EXISTS subject_topic (
    topic_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_by UUID NOT NULL,
    topic_code VARCHAR(255) UNIQUE NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
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
    CONSTRAINT fk_question_topic FOREIGN KEY (topic_id) REFERENCES subject_topic(topic_id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id) ON DELETE RESTRICT
);

-- 6. Table rubric_criteria
CREATE TABLE IF NOT EXISTS rubric_criteria (
    criteria_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    question_id UUID NOT NULL,
    criteria_name VARCHAR(255) NOT NULL,
    expected_knowledge_points TEXT NOT NULL,
    weight_ratio DECIMAL(3,2) NOT NULL,
    CONSTRAINT fk_rubric_criteria_question FOREIGN KEY (question_id) REFERENCES question(question_id) ON DELETE CASCADE
);

-- 7. Table exam_session
CREATE TABLE IF NOT EXISTS exam_session (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_by UUID NOT NULL,
    session_name VARCHAR(255) NOT NULL,
    max_main_questions INT NOT NULL,
    max_followup_per_question INT NOT NULL,
    time_limit_minutes INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exam_session_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id) ON DELETE RESTRICT
);

-- 8. Table student_session_assignment
CREATE TABLE IF NOT EXISTS student_session_assignment (
    assignment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL,
    student_id UUID NOT NULL,
    scheduled_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_assignment_session FOREIGN KEY (session_id) REFERENCES exam_session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_student FOREIGN KEY (student_id) REFERENCES app_user(user_id) ON DELETE RESTRICT
);

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
