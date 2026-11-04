package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.common.exception.ConflictException;
import java.util.Locale;

public final class ExamStateMachine {
    private ExamStateMachine() { }

    public static void requireSessionTransition(String current, String target) {
        String from = normalize(current), to = normalize(target);
        boolean valid = from.equals(to)
                || (from.equals("SCHEDULED") && (to.equals("IN_PROGRESS") || to.equals("CANCELLED")))
                || (from.equals("IN_PROGRESS") && (to.equals("COMPLETED") || to.equals("CANCELLED")));
        if (!valid) throw new ConflictException("Invalid exam session transition: " + from + " -> " + to, "STATE_TRANSITION_INVALID");
    }

    public static void requireAssignmentTransition(String current, String target) {
        String from = normalize(current), to = normalize(target);
        boolean valid = from.equals(to)
                || (from.equals("PENDING") && to.equals("SCHEDULED"))
                || (from.equals("PENDING") && to.equals("CANCELLED"))
                || (from.equals("SCHEDULED") && (to.equals("IN_PROGRESS") || to.equals("ABSENT") || to.equals("CANCELLED")))
                || (from.equals("IN_PROGRESS") && to.equals("COMPLETED"));
        if (!valid) throw new ConflictException("Invalid assignment transition: " + from + " -> " + to, "STATE_TRANSITION_INVALID");
    }

    private static String normalize(String status) { return status == null ? "" : status.trim().toUpperCase(Locale.ROOT); }
}
