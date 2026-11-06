package com.swd392.aiviva.exam.service;

import java.util.List;
import java.util.Map;

public record StudentImportResult(List<Map<String, Object>> added, List<Map<String, Object>> skipped) { }
