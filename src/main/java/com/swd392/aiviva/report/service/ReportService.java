package com.swd392.aiviva.report.service;

import com.swd392.aiviva.report.dto.response.ClassStatisticsResponse;
import com.swd392.aiviva.report.dto.response.StudentExamReportResponse;

public interface ReportService {

    StudentExamReportResponse getStudentReport(Long studentId);

    ClassStatisticsResponse getClassStatistics(Long classId);
}

