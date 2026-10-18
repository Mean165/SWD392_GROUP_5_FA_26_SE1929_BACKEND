package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.dto.AnswerAnalysisContext;
import com.swd392.aiviva.interview.dto.AnswerAnalysisResult;

public interface AnswerAnalysisService {

    AnswerAnalysisResult analyzeAnswer(AnswerAnalysisContext context);
}
