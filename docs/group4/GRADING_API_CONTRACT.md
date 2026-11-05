# Grading API Contract

Base path: `/api/v1/grading`. Every endpoint requires a bearer JWT and the LE or AD role. Non-admin lecturers must own the exam or be assigned to its subject in `subject.assigned_lecturer_ids_json`. Responses use the repository envelope: `{ "success": true, "message": "...", "data": ..., "timestamp": "...", "errorCode": null }`.

## Implemented routes

| Method and path | Behavior |
| --- | --- |
| `GET /capabilities` | Backend availability and actual grading provider/model readiness. Local compatible server is probed at `/models` with a 1.5 s timeout. |
| `GET /queue?examId=&status=&attemptNo=&finishedFrom=&finishedTo=&page=0&size=20` | Assigned queue; page size is at most 100. `attemptNo` is optional and is added by the attempts feature migration. |
| `GET /queue/summary?examId=` | Counts by status for the caller's assigned exams. |
| `POST /reconcile` | Admin-only immediate scan of completed attempts. The same scan runs every 30 seconds. |
| `GET /submissions/{id}` | Transcript, STT review signals, AI runs/suggestions/evidence, lecturer scores/notes, rubric snapshot and version. Raw audio URLs are omitted. |
| `POST /submissions/{id}/claim` | Claims for 10 minutes, moves AI-ready/failed/pending rows into `IN_REVIEW`. |
| `PUT /submissions/{id}/lock`, `DELETE /submissions/{id}/lock` | Renew/release caller's claim. |
| `PUT /submissions/{id}/scores` | Saves lecturer score cells with optimistic `version`. `source` is `OVERRIDDEN` or `MANUAL`; AI acceptance uses its explicit route. |
| `PUT /submissions/{id}/questions/{questionId}/notes` | Saves a draft or approved question note. |
| `POST /submissions/{id}/accept-ai` | Lecturer explicitly copies latest completed AI criterion suggestions into lecturer score cells; body `{ "version": 4 }`. |
| `POST /submissions/{id}/ai-runs` | Queues a manual AI rerun, returns HTTP 202. Max 3 manual runs by default. |
| `GET /submissions/{id}/ai-runs` | Poll run states. |
| `POST /submissions/{id}/finalize` | Requires all criteria, confirmation and current version; creates immutable revision and writes official final fields. |
| `POST /submissions/{id}/release` | Releases a finalized grade; body `{ "version": 5 }`. |
| `POST /submissions/{id}/reopen` | Requires a reason and current version; preserves prior revision and reopens the current projection. |
| `GET /submissions/{id}/audit`, `GET /submissions/{id}/revisions` | Read-only audit/revision histories. |
| `GET /exams/{examId}/analytics/agreement` | Exact agreement, within-one agreement and mean absolute error for latest AI run vs lecturer criterion scores. |
| `GET/PUT /exams/{examId}/settings` | Read/update K, rounding, release, auto-AI and thresholds. Fairness signal scoring and bulk acceptance are rejected. |

## Score payload examples

```json
{
  "scores": [
    {
      "questionId": "7c2fd92b-e276-4c01-9e88-b6b76f26df83",
      "criterionId": "33d14564-cc4c-40a7-9c1b-73738711d0e0",
      "score": 1.5,
      "maxScore": 2.5,
      "source": "OVERRIDDEN",
      "overrideReason": "The answer gives a correct example not captured by the suggestion.",
      "comment": ""
    }
  ],
  "version": 4
}
```

```json
{ "confirm": true, "version": 5, "note": "Reviewed all criteria and evidence." }
```

```json
{ "reason": "Lecturer correction after appeal", "note": "Recheck question 2 evidence." }
```

## Errors

HTTP 401 `AUTH_REQUIRED`/`AUTH_INVALID`; 403 `ACCESS_DENIED`; 404 `RESOURCE_NOT_FOUND`; 409 `STALE_VERSION`, `ALREADY_FINALIZED`, `NOT_FINALIZED`, `RUBRIC_MISMATCH`, `AI_SUGGESTION_NOT_FOUND`, `NOT_IN_REVIEW`, `ATTEMPTS_EXHAUSTED`, `ATTEMPT_IN_PROGRESS`, `COOLDOWN_ACTIVE`, `OUTSIDE_WINDOW`, `EXAM_CLOSED`, `POLICY_NOT_MANUAL`, `CANNOT_VOID_RELEASED`; 423 `LOCKED_BY_OTHER`/`LOCK_EXPIRED`; 422 `INCOMPLETE_SCORES`/`REASON_REQUIRED`; 429 `RATE_LIMITED` with `Retry-After`; 503 `AI_NOT_READY` when no configured provider is ready. Other Bean Validation/domain input errors still use the shared 400 `VALIDATION_ERROR` envelope.

## Not implemented in this release

No signed audio URL/stream endpoint, exam bulk release, dedicated student-visible final-grade endpoint, run-by-ID endpoint, overall-comment endpoint, or group-6 export contract is exposed. Audio URL fields are intentionally excluded from detail until authorization, signing and view-audit storage are implemented.
