package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.ai.llm.LlmProviderAdapter;
import org.springframework.stereotype.Service;

@Service
public class AnswerAnalysisServiceImpl implements AnswerAnalysisService {

    private final LlmProviderAdapter llmProviderAdapter;

    public AnswerAnalysisServiceImpl(LlmProviderAdapter llmProviderAdapter) {
        this.llmProviderAdapter = llmProviderAdapter;
    }

    @Override
    public String analyzeAnswer(String transcript) {
        String prompt = "Analyze the following student interview answer for completeness, factual correctness, and clarity:\n\n"
                + "\"" + transcript + "\"\n\n"
                + "Provide a brief summary evaluation of strengths, weaknesses, and missing concepts.";
        return llmProviderAdapter.generateText(prompt);
    }
}
