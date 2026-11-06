package com.swd392.aiviva.exam.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swd392.aiviva.common.exception.ConflictException;
import org.junit.jupiter.api.Test;

class ExamStateMachineTest {
    @Test
    void acceptsOnlyDocumentedSessionTransitions() {
        assertDoesNotThrow(() -> ExamStateMachine.requireSessionTransition("SCHEDULED", "IN_PROGRESS"));
        assertDoesNotThrow(() -> ExamStateMachine.requireSessionTransition("IN_PROGRESS", "COMPLETED"));
        assertThrows(ConflictException.class, () -> ExamStateMachine.requireSessionTransition("COMPLETED", "IN_PROGRESS"));
    }

    @Test
    void acceptsAssignmentSchedulingAndCancellationRules() {
        assertDoesNotThrow(() -> ExamStateMachine.requireAssignmentTransition("PENDING", "SCHEDULED"));
        assertDoesNotThrow(() -> ExamStateMachine.requireAssignmentTransition("SCHEDULED", "ABSENT"));
        assertThrows(ConflictException.class, () -> ExamStateMachine.requireAssignmentTransition("COMPLETED", "CANCELLED"));
    }
}
