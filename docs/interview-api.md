# Student oral interview API

All routes below require `Authorization: Bearer <JWT>` for an active Student account (`ST`). The authenticated account is authoritative; `studentId`, if supplied to start, must match it. JSON operations use the existing `ApiResponse` envelope (`success`, `message`, `data`, `timestamp`).

## Routes

| Method and path | Request | Success response | State effect |
|---|---|---|---|
| `POST /api/interviews/start` | JSON `{"sessionId":"<uuid>","studentId":"<uuid-or-null>"}` | `200`, `ApiResponse<InterviewResponse>`; contains `attemptId`, status, current turn and turn history | Locks the student's assignment; creates or resumes its single attempt. First turn is the first approved question ordered by creation time. |
| `GET /api/interviews/{attemptId}` | No body | `200`, `ApiResponse<InterviewResponse>` | Returns the authorized attempt and current turn; marks an expired in-progress attempt complete. |
| `GET /api/interviews/{attemptId}/turns` | No body | `200`, `ApiResponse<List<InteractionTurnResponse>>` | Returns authorized turn history. |
| `GET /api/interviews/turns/{turnId}/speech` | No body | `200`, `Content-Type: audio/mpeg`, inline MP3 bytes | Synthesizes the turn's question using OpenAI TTS. The audio is generated on demand, not stored. The browser client can use authenticated `fetch()` and play the returned Blob. |
| `POST /api/interviews/submit-answer` | JSON shown below | `200`, `ApiResponse<SubmitAnswerResponse>` | Synchronously transcribes inline audio if needed, analyzes the answer, persists transcript/assessment, and creates one follow-up/next main turn or completes the attempt. |
| `POST /api/interviews/{attemptId}/complete` | No body | `200`, `ApiResponse<InterviewResponse>` | Completes an in-progress owned attempt. Repeating completion is idempotent. |

The answer body must include the current `attemptId` and `turnId`, plus exactly one of `transcriptText` or `audioSegmentUrl`:

```json
{
  "attemptId": "00000000-0000-0000-0000-000000000001",
  "turnId": "00000000-0000-0000-0000-000000000002",
  "transcriptText": "Student answer text"
}
```

For audio, `audioSegmentUrl` is a base64 data URL, not a network URL:

```json
{
  "attemptId": "00000000-0000-0000-0000-000000000001",
  "turnId": "00000000-0000-0000-0000-000000000002",
  "audioSegmentUrl": "data:audio/webm;base64,<base64-audio>"
}
```

Audio is uploaded as a complete file and transcribed synchronously; this API is **not** streaming and does not return partial transcripts. Supported media types are `audio/webm`, `audio/wav`, `audio/x-wav`, `audio/mpeg`, `audio/mp4`, `audio/ogg`, `audio/aac`, and `audio/flac`. The default decoded-file limit is 10 MiB. Audio is not persistently stored, and `audio_segment_url` remains null; the backend does not fabricate a playback URL.

`SubmitAnswerResponse` includes `turnId`, the confirmed persisted `transcriptText`, `answerClassification`, `analysisSummary`, criterion assessments, `followUpGenerated`, `nextTurn` (or null), and `completed`. Retrying a finalized `turnId` returns its stored result and does not invoke analysis or create another turn. If provider work fails, the transaction is rolled back so the same current turn can be retried.

## State and errors

- The attempt is authoritative for ownership, current turn, follow-up count, and timing. Turn writes are serialized on the attempt row.
- Interviews can start only for assignments whose scheduled time has arrived and sessions in `SCHEDULED` or `IN_PROGRESS`; completion submits the assignment.
- The per-answer timer starts at turn creation and defaults to 180 seconds. The exam-wide deadline is the attempt start plus `exam_session.time_limit_minutes`; the earlier deadline applies. An expired attempt is persisted as completed and late submission returns `410 Gone`.
- Follow-up count is the number of generated `FOLLOW_UP` turns for the same main question. The configured session maximum takes precedence; the model cannot override the limit.
- An answer is analyzed with its main/current question, earlier turns for that main question, rubric, follow-up count, and remaining time. Structured model output is validated before persistence. A follow-up is created only when the model supplies an appropriate question and a configured limit remains; otherwise the service advances to another approved question or completes the attempt.
- Main questions are currently selected from the global approved question bank. The existing schema has no exam-session-to-question/topic relation, so per-session question pools cannot be enforced without a schema/domain change.
- `400` invalid input or state transition; `401` missing/invalid authentication; `403` non-Student or non-owner; `404` unknown attempt/turn/assignment; `410` expired interview; `502` upstream provider failure or malformed provider response; `503` missing provider configuration or provider rate limit. Provider secrets and raw upstream error bodies are not returned.

## Configuration

Set `OPENAI_API_KEY` on the backend only. With Docker Compose, export it in the host environment (or provide it through a local, uncommitted `.env` file); Compose forwards it only to the backend container. Optional provider settings are `OPENAI_API_URL`, `OPENAI_MODEL`, `OPENAI_TIMEOUT` (milliseconds, default `30000`), `OPENAI_LANGUAGE` (default `vi`), and `OPENAI_TTS_MAX_TEXT_LENGTH` (default `4000`). Interview settings are `INTERVIEW_LANGUAGE` (default `vi`), `INTERVIEW_ANSWER_TIME_LIMIT_SECONDS` (default `180`), `INTERVIEW_MAX_AUDIO_BYTES` (default `10485760`), and `INTERVIEW_MAX_TRANSCRIPT_CHARACTERS` (default `20000`). No database migration is required.
