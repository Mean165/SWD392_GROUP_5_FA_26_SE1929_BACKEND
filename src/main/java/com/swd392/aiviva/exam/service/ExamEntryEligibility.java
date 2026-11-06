package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.exam.dto.response.ExamEntryEligibilityResponse;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import java.time.OffsetDateTime;

public final class ExamEntryEligibility {

    private ExamEntryEligibility() {
    }

    public static ExamEntryEligibilityResponse evaluate(StudentSessionAssignment assignment, OffsetDateTime now) {
        ExamSession session = assignment.getExamSession();
        OffsetDateTime scheduledTime = assignment.getScheduledTime();
        OffsetDateTime opensAt = scheduledTime.minusMinutes(10);
        OffsetDateTime closesAt = scheduledTime.plusMinutes(session.getTimeLimitMinutes());
        String reason = null;

        if ("CANCELLED".equalsIgnoreCase(session.getStatus())) {
            reason = "Exam session is cancelled.";
        } else if ("COMPLETED".equalsIgnoreCase(assignment.getStatus())
                || "ABSENT".equalsIgnoreCase(assignment.getStatus())) {
            reason = "This assignment is no longer available.";
        } else if (now.isBefore(opensAt)) {
            reason = "The interview entry window has not opened yet.";
        } else if (now.isAfter(closesAt)) {
            reason = "The interview entry window has closed.";
        }

        return ExamEntryEligibilityResponse.builder()
                .canEnter(reason == null)
                .reason(reason == null ? "Interview entry is available." : reason)
                .opensAt(opensAt)
                .closesAt(closesAt)
                .build();
    }
}
