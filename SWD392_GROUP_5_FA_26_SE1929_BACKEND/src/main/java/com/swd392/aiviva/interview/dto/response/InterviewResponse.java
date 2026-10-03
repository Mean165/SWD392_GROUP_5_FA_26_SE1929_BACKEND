package com.swd392.aiviva.interview.dto.response;

import com.swd392.aiviva.interview.enums.InterviewStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResponse {

    private Long id;
    private Long examId;
    private Long studentId;
    private LocalDateTime startedAt;
    private InterviewStatus status;
}

