package com.swd392.aiviva.question.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.question.enums.BloomLevel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bloom-levels")
public class BloomLevelController {

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLevels() {
        List<Map<String, Object>> levels = Arrays.stream(BloomLevel.values())
                .map(level -> Map.<String, Object>of(
                        "code", level.name(),
                        "labelVi", level.getLabelVi(),
                        "labelEn", level.getLabelEn(),
                        "order", level.getOrder(),
                        "description", level.getDescription()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Bloom levels retrieved", levels));
    }
}
