package com.swd392.aiviva.interview.service;

import org.springframework.stereotype.Service;

@Service
public class AnswerAnalysisServiceImpl implements AnswerAnalysisService {

    @Override
    public String analyzeAnswer(String transcript) {
        // TODO: Analyze transcript against rubric or answer model.
        return "analysis-result";
    }
}

