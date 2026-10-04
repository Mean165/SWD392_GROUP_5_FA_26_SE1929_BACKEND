package com.swd392.aiviva.monitoring.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> getHealthStatus() {
        return ResponseEntity.ok(ApiResponse.success("Monitoring service available", "OK"));
    }
}

