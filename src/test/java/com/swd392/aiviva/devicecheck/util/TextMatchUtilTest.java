package com.swd392.aiviva.devicecheck.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextMatchUtilTest {

    private static final String SAMPLE = "Tôi sẵn sàng bắt đầu bài thi vấn đáp";

    @Test
    void identicalSentencesScoreOne() {
        assertEquals(1.0, TextMatchUtil.score(SAMPLE, SAMPLE), 0.0001);
    }

    @Test
    void unaccentedVietnameseStillMatches() {
        assertTrue(TextMatchUtil.score(SAMPLE, "Toi san sang bat dau bai thi van dap") >= 0.8);
    }

    @Test
    void unrelatedSentenceScoresBelowPointThree() {
        assertTrue(TextMatchUtil.score(SAMPLE, "The weather is sunny today") < 0.3);
    }

    @Test
    void emptyStringsScoreZero() {
        assertEquals(0.0, TextMatchUtil.score(SAMPLE, ""));
        assertEquals(0.0, TextMatchUtil.score("", SAMPLE));
    }

    @Test
    void caseAndPunctuationDoNotAffectScore() {
        assertEquals(1.0, TextMatchUtil.score(SAMPLE, "TÔI, SẴN SÀNG! BẮT ĐẦU BÀI THI VẤN ĐÁP."), 0.0001);
    }
}
