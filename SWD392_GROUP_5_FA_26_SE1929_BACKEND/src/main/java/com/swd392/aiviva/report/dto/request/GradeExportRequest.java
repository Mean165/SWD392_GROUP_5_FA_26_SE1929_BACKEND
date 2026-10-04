package com.swd392.aiviva.report.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeExportRequest {

    private Long classId;
    private String format;
}

