package com.swd392.aiviva.report.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.report.dto.response.StudentExamReportResponse;
import com.swd392.aiviva.report.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/students")
public class StudentReportController {

    private final ReportService reportService;

    public StudentReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<ApiResponse<StudentExamReportResponse>> getStudentReport(@PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.success("Student report retrieved", reportService.getStudentReport(studentId)));
    }
}

