package com.swd392.aiviva.report.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.report.dto.response.ClassStatisticsResponse;
import com.swd392.aiviva.report.service.ReportService;
import com.swd392.aiviva.exam.attempt.service.ExamAttemptService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/lecturers")
public class LecturerReportController {

    private final ReportService reportService;
    private final ExamAttemptService examAttemptService;

    public LecturerReportController(ReportService reportService, ExamAttemptService examAttemptService) {
        this.reportService = reportService;
        this.examAttemptService = examAttemptService;
    }

    @GetMapping("/classes/{classId}")
    public ResponseEntity<ApiResponse<ClassStatisticsResponse>> getClassStatistics(@PathVariable Long classId) {
        return ResponseEntity.ok(ApiResponse.success("Class statistics retrieved", reportService.getClassStatistics(classId)));
    }

    @GetMapping("/exams/{examId}/official-results")
    public ResponseEntity<ApiResponse<Object>> getOfficialExamResults(@PathVariable UUID examId) {
        return ResponseEntity.ok(ApiResponse.success("Official exam results retrieved", examAttemptService.officialResults(examId)));
    }
}

