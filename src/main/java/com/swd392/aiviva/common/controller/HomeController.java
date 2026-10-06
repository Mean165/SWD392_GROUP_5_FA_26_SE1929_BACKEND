package com.swd392.aiviva.common.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<ApiResponse<Map<String, Object>>> home() {
        Map<String, Object> info = Map.of(
                "application", "AIVIVA Examination System API",
                "status", "UP",
                "endpoints", Map.of(
                        "questions", "/api/questions",
                        "users", "/api/users",
                        "exams", "/api/exams",
                        "auth", "/api/auth",
                        "interviews", "/api/interviews",
                        "evaluations", "/api/evaluations"
                )
        );
        return ResponseEntity.ok(ApiResponse.success("AiViva Backend is running successfully!", info));
    }
}
