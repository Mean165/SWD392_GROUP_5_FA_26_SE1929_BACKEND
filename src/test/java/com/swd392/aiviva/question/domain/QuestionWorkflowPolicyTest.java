package com.swd392.aiviva.question.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.question.enums.BloomLevel;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionWorkflowPolicyTest {
    @Test
    void legacyEvaluateBloomValuesNormalizeToTheSupportedAnalyzeLevel() {
        assertEquals("ANALYZE", BloomLevel.normalizeLegacyValue("EVALUATE"));
        assertEquals("ANALYZE", BloomLevel.normalizeLegacyValue(" evaluate "));
        assertEquals("REMEMBER", BloomLevel.normalizeLegacyValue("remember"));
    }

    @Test
    void onlyDraftQuestionsCanBeApprovedOrRejected() {
        QuestionStateTransition.requireApprovable("DRAFT");
        QuestionStateTransition.requireRejectable("DRAFT");
        assertThrows(BusinessException.class, () -> QuestionStateTransition.requireApprovable("REJECTED"));
        assertThrows(BusinessException.class, () -> QuestionStateTransition.requireRejectable("APPROVED"));
        QuestionStateTransition.requireEditable("APPROVED");
    }

    @Test
    void nonApprovedQuestionsAndDocumentsAreOwnerScoped() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(QuestionOwnershipPolicy.canViewNonApprovedQuestion(false, owner, owner, "DRAFT"));
        assertFalse(QuestionOwnershipPolicy.canViewNonApprovedQuestion(false, other, owner, "DRAFT"));
        assertTrue(QuestionOwnershipPolicy.canViewNonApprovedQuestion(false, other, owner, "APPROVED"));
        assertFalse(QuestionOwnershipPolicy.canManageDocument(false, other, owner));
        assertTrue(QuestionOwnershipPolicy.canManageDocument(true, other, owner));
    }
}
