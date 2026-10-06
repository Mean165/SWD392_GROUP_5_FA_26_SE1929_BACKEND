-- =========================================================
-- 1. ROLE
-- =========================================================
CREATE TABLE IF NOT EXISTS role (
    role_id UUID PRIMARY KEY,
    role_code VARCHAR UNIQUE NOT NULL,
    role_name VARCHAR NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ
);

-- =========================================================
-- 2. APP USER
-- =========================================================
CREATE TABLE IF NOT EXISTS app_user (
    user_id UUID PRIMARY KEY,
    role_id UUID NOT NULL,
    full_name VARCHAR NOT NULL,
    email VARCHAR UNIQUE NOT NULL,
    password_hash VARCHAR NOT NULL,
    student_or_staff_code VARCHAR UNIQUE,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ,

    CONSTRAINT fk_app_user_role
        FOREIGN KEY (role_id)
        REFERENCES role(role_id)
);

-- =========================================================
-- 3. SUBJECT TOPIC
-- =========================================================
CREATE TABLE IF NOT EXISTS subject_topic (
    topic_id UUID PRIMARY KEY,
    created_by UUID NOT NULL,
    topic_code VARCHAR UNIQUE NOT NULL,
    topic_name VARCHAR NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ,

    CONSTRAINT fk_subject_topic_creator
        FOREIGN KEY (created_by)
        REFERENCES app_user(user_id)
);

-- =========================================================
-- 4. TOPIC MATERIAL
-- =========================================================
CREATE TABLE IF NOT EXISTS topic_material (
    material_id UUID PRIMARY KEY,
    topic_id UUID NOT NULL,
    title VARCHAR NOT NULL,
    file_url VARCHAR NOT NULL,
    is_indexed BOOLEAN NOT NULL,
    uploaded_at TIMESTAMPTZ,

    CONSTRAINT fk_topic_material_topic
        FOREIGN KEY (topic_id)
        REFERENCES subject_topic(topic_id)
);

-- =========================================================
-- 5. QUESTION
-- =========================================================
CREATE TABLE IF NOT EXISTS question (
    question_id UUID PRIMARY KEY,
    topic_id UUID NOT NULL,
    created_by UUID NOT NULL,
    question_text TEXT NOT NULL,
    bloom_level VARCHAR NOT NULL,
    source_type VARCHAR NOT NULL,
    approval_status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ,

    CONSTRAINT fk_question_topic
        FOREIGN KEY (topic_id)
        REFERENCES subject_topic(topic_id),

    CONSTRAINT fk_question_creator
        FOREIGN KEY (created_by)
        REFERENCES app_user(user_id)
);

-- =========================================================
-- 6. RUBRIC CRITERIA
-- =========================================================
CREATE TABLE IF NOT EXISTS rubric_criteria (
    criteria_id UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    criteria_name VARCHAR NOT NULL,
    expected_knowledge_points TEXT NOT NULL,
    weight_ratio DECIMAL(3,2) NOT NULL,

    CONSTRAINT fk_rubric_question
        FOREIGN KEY (question_id)
        REFERENCES question(question_id)
);

-- =========================================================
-- 7. EXAM SESSION
-- =========================================================
CREATE TABLE IF NOT EXISTS exam_session (
    session_id UUID PRIMARY KEY,
    created_by UUID NOT NULL,
    session_name VARCHAR NOT NULL,
    max_main_questions INT NOT NULL,
    max_followup_per_question INT NOT NULL,
    time_limit_minutes INT NOT NULL,
    status VARCHAR NOT NULL,
    start_time TIMESTAMPTZ,
    created_at TIMESTAMPTZ,

    CONSTRAINT fk_exam_session_creator
        FOREIGN KEY (created_by)
        REFERENCES app_user(user_id)
);

-- =========================================================
-- 8. STUDENT SESSION ASSIGNMENT
-- =========================================================
CREATE TABLE IF NOT EXISTS student_session_assignment (
    assignment_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    student_id UUID NOT NULL,
    scheduled_time TIMESTAMPTZ NOT NULL,
    status VARCHAR NOT NULL,

    CONSTRAINT fk_assignment_session
        FOREIGN KEY (session_id)
        REFERENCES exam_session(session_id),

    CONSTRAINT fk_assignment_student
        FOREIGN KEY (student_id)
        REFERENCES app_user(user_id)
);

-- =========================================================
-- 9. VIVA ATTEMPT
-- =========================================================
CREATE TABLE IF NOT EXISTS viva_attempt (
    attempt_id UUID PRIMARY KEY,
    assignment_id UUID UNIQUE NOT NULL,
    full_audio_url VARCHAR,
    status VARCHAR NOT NULL,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,

    CONSTRAINT fk_viva_attempt_assignment
        FOREIGN KEY (assignment_id)
        REFERENCES student_session_assignment(assignment_id)
);

-- =========================================================
-- 10. INTERACTION TURN
-- =========================================================
CREATE TABLE IF NOT EXISTS interaction_turn (
    turn_id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    main_question_id UUID,
    turn_order INT NOT NULL,
    turn_type VARCHAR NOT NULL,
    ai_question_text TEXT NOT NULL,
    transcript_text TEXT,
    audio_segment_url VARCHAR,
    ai_reasoning TEXT,
    created_at TIMESTAMPTZ,

    CONSTRAINT fk_interaction_turn_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES viva_attempt(attempt_id),

    CONSTRAINT fk_interaction_turn_question
        FOREIGN KEY (main_question_id)
        REFERENCES question(question_id)
);

-- =========================================================
-- 11. ACOUSTIC FLUENCY METRIC
-- =========================================================
CREATE TABLE IF NOT EXISTS acoustic_fluency_metric (
    metric_id UUID PRIMARY KEY,
    turn_id UUID UNIQUE NOT NULL,
    words_per_minute DECIMAL(5,2),
    hesitation_pauses_count INT,
    stt_confidence_score DECIMAL(4,3),
    speech_clarity_score DECIMAL(4,2),

    CONSTRAINT fk_acoustic_metric_turn
        FOREIGN KEY (turn_id)
        REFERENCES interaction_turn(turn_id)
);

-- =========================================================
-- 12. AI RUBRIC ASSESSMENT
-- =========================================================
CREATE TABLE IF NOT EXISTS ai_rubric_assessment (
    assessment_id UUID PRIMARY KEY,
    turn_id UUID NOT NULL,
    criteria_id UUID NOT NULL,
    semantic_similarity DECIMAL(4,3),
    compliance_status VARCHAR NOT NULL,
    ai_suggested_points DECIMAL(4,2) NOT NULL,
    missing_concepts TEXT,
    ai_explanation_reasoning TEXT,

    CONSTRAINT fk_ai_assessment_turn
        FOREIGN KEY (turn_id)
        REFERENCES interaction_turn(turn_id),

    CONSTRAINT fk_ai_assessment_criteria
        FOREIGN KEY (criteria_id)
        REFERENCES rubric_criteria(criteria_id)
);

-- =========================================================
-- 13. AI CLAIM EVIDENCE
-- =========================================================
CREATE TABLE IF NOT EXISTS ai_claim_evidence (
    claim_id UUID PRIMARY KEY,
    turn_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    student_claim_text TEXT NOT NULL,
    exact_quote_evidence TEXT NOT NULL,
    start_timestamp_ms INT NOT NULL,
    end_timestamp_ms INT NOT NULL,
    is_factually_correct BOOLEAN NOT NULL,

    CONSTRAINT fk_claim_evidence_turn
        FOREIGN KEY (turn_id)
        REFERENCES interaction_turn(turn_id),

    CONSTRAINT fk_claim_evidence_assessment
        FOREIGN KEY (assessment_id)
        REFERENCES ai_rubric_assessment(assessment_id)
);

-- =========================================================
-- 14. FINAL GRADE REPORT
-- =========================================================
CREATE TABLE IF NOT EXISTS final_grade_report (
    report_id UUID PRIMARY KEY,
    attempt_id UUID UNIQUE NOT NULL,
    finalized_by_lecturer_id UUID,
    total_ai_score DECIMAL(5,2) NOT NULL,
    final_teacher_score DECIMAL(5,2),
    teacher_adjustment_reason TEXT,
    ai_overall_feedback TEXT,
    finalized_at TIMESTAMPTZ,

    CONSTRAINT fk_final_report_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES viva_attempt(attempt_id),

    CONSTRAINT fk_final_report_lecturer
        FOREIGN KEY (finalized_by_lecturer_id)
        REFERENCES app_user(user_id)
);
