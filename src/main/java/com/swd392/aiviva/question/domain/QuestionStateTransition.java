package com.swd392.aiviva.question.domain;

import com.swd392.aiviva.common.exception.BusinessException;

public final class QuestionStateTransition {
    private QuestionStateTransition() {}
    public static void requireEditable(String status) {
        if (!"DRAFT".equals(status) && !"APPROVED".equals(status)) throw new BusinessException("Only DRAFT or APPROVED questions can be edited");
    }
    public static void requireApprovable(String status) {
        if (!"DRAFT".equals(status)) throw new BusinessException("Only DRAFT questions can be approved");
    }
    public static void requireRejectable(String status) {
        if (!"DRAFT".equals(status)) throw new BusinessException("Only DRAFT questions can be rejected");
    }
}
