package com.swd392.aiviva.exam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swd392.aiviva.common.exception.InvalidRequestParameterException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class StudentExamDateFilterTest {
    @Test
    void acceptsUiDateRangeAsInclusiveLocalDatesInVietnamTime() {
        assertEquals(OffsetDateTime.parse("2026-11-04T00:00:00+07:00"),
                StudentExamDateFilter.parse("2026-11-04", false));
        assertEquals(OffsetDateTime.parse("2026-12-04T23:59:59.999999999+07:00"),
                StudentExamDateFilter.parse("2026-12-04", true));
    }

    @Test
    void preservesOffsetDateTimeInputsAndTreatsEmptyValuesAsNoFilter() {
        assertEquals(OffsetDateTime.parse("2026-11-04T00:00:00+07:00"),
                StudentExamDateFilter.parse("2026-11-04T00:00:00+07:00", false));
        assertNull(StudentExamDateFilter.parse(" ", false));
        assertNull(StudentExamDateFilter.parse(null, true));
    }

    @Test
    void rejectsMalformedDateWithFieldSpecificCode() {
        InvalidRequestParameterException error = assertThrows(InvalidRequestParameterException.class,
                () -> StudentExamDateFilter.parse("not-a-date", false));
        assertEquals("from", error.getParameter());
        assertEquals("INVALID_EXAM_DATE", error.getErrorCode());
    }
}
