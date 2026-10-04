package com.swd392.aiviva.report.service;

import com.swd392.aiviva.report.dto.response.ClassStatisticsResponse;
import com.swd392.aiviva.report.dto.response.StudentExamReportResponse;
import org.springframework.stereotype.Service;

@Service
public class ReportServiceImpl implements ReportService {

    @Override
    public StudentExamReportResponse getStudentReport(Long studentId) {
        // TODO: Build report from evaluation and monitoring evidence.
        return null;
    }

    @Override
    public ClassStatisticsResponse getClassStatistics(Long classId) {
        // TODO: Aggregate class-level statistics.
        return null;
    }
}

