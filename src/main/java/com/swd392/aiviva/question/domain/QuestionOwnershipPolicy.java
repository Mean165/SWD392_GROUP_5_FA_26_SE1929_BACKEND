package com.swd392.aiviva.question.domain;

import java.util.UUID;

public final class QuestionOwnershipPolicy {
    private QuestionOwnershipPolicy() {}
    public static boolean canViewNonApprovedQuestion(boolean admin, UUID viewer, UUID owner, String status) {
        return admin || "APPROVED".equals(status) || viewer.equals(owner);
    }
    public static boolean canManageDocument(boolean admin, UUID viewer, UUID owner) {
        return admin || viewer.equals(owner);
    }
}
