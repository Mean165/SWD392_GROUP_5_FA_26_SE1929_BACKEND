package com.swd392.aiviva.exam.dto.request;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class ScheduleExamRequest {
    private OffsetDateTime startTime;
    private Boolean shuffle = false;
    private Long seed;
    private Integer slotMinutes;
    private Boolean dryRun = false;
    private Integer attemptNo = 1;
}
