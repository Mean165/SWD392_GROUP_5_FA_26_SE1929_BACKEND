package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.common.exception.InvalidRequestParameterException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public final class StudentExamDateFilter {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private StudentExamDateFilter() { }

    public static OffsetDateTime parse(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        try {
            LocalDate date = LocalDate.parse(normalized);
            return (endOfDay ? date.plusDays(1).atStartOfDay().minusNanos(1) : date.atStartOfDay())
                    .atZone(DEFAULT_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(normalized);
            } catch (DateTimeParseException ex) {
                String name = endOfDay ? "to" : "from";
                throw new InvalidRequestParameterException(name, "INVALID_EXAM_DATE",
                        "Parameter '" + name + "' must be an ISO date or offset date-time.");
            }
        }
    }
}
