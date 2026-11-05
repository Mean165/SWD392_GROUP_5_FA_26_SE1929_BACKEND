# Grading Evaluation Guide

No gold set is committed because student transcripts and grades are sensitive. For an authorized offline evaluation, prepare JSONL with anonymized question/rubric/transcript data and lecturer criterion scores outside Git. Compare median AI suggestions to lecturer scores using MAE, exact agreement, within-one-point agreement, evidence exact-match rate, valid-output rate and run latency. Report cohorts only where sample size permits; do not use accent, fluency, STT confidence or demographic metadata as score inputs.

Current live endpoint: `GET /api/v1/grading/exams/{examId}/analytics/agreement` computes comparisons only where a latest AI run and lecturer criterion score both exist. It returns comparison count, MAE, exact agreement and within-one agreement. It does not compute QWK or demographic parity yet.
