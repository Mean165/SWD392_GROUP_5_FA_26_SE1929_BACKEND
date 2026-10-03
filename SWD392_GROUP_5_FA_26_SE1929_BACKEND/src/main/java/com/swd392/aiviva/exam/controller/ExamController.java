package com.swd392.aiviva.exam.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.exam.dto.request.ExamRequest;
import com.swd392.aiviva.exam.dto.response.ExamResponse;
import com.swd392.aiviva.exam.service.ExamService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExamResponse>>> getExams() {
        return ResponseEntity.ok(ApiResponse.success("Exams retrieved", examService.getExams()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExamResponse>> createExam(@Valid @RequestBody ExamRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Exam created", examService.createExam(request)));
    }
}

