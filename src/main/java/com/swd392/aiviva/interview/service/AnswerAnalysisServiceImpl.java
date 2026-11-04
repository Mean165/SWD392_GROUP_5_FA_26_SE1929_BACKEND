package com.swd392.aiviva.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.interview.ai.llm.LlmProviderAdapter;
import com.swd392.aiviva.interview.dto.AnswerAnalysisContext;
import com.swd392.aiviva.interview.dto.AnswerAnalysisResult;
import org.springframework.stereotype.Service;

@Service
public class AnswerAnalysisServiceImpl implements AnswerAnalysisService {

    private final LlmProviderAdapter llmProviderAdapter;
    private final ObjectMapper objectMapper;

    public AnswerAnalysisServiceImpl(LlmProviderAdapter llmProviderAdapter, ObjectMapper objectMapper) {
        this.llmProviderAdapter = llmProviderAdapter;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnswerAnalysisResult analyzeAnswer(AnswerAnalysisContext context) {
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception exception) {
            throw new ExternalServiceException("Unable to prepare interview follow-up context.", exception);
        }

        String prompt = """
                You are an oral-exam interviewer. Do not grade, score, evaluate correctness, assess rubric criteria,
                or return a summary/evaluation. Read the transcript only to decide whether a brief, relevant
                clarification question would help continue the conversation. Never reveal an expected answer.
                If the transcript is empty, unclear, unrelated, contradictory, or ambiguous, ask one neutral
                clarification question. If a useful clarification is not needed, or no follow-ups/time remain,
                set followUpQuestion to null. Use the supplied language. Return exactly this JSON shape:
                {"followUpQuestion": null}
                or {"followUpQuestion": "one short interviewer question"}.

                Interview context JSON:
                """ + contextJson;
        try {
            AnswerAnalysisResult result = objectMapper.readValue(
                    llmProviderAdapter.generateText(prompt), AnswerAnalysisResult.class);
            if (result == null || (result.followUpQuestion() != null
                    && (result.followUpQuestion().isBlank() || result.followUpQuestion().length() > 500))) {
                throw new ExternalServiceException("Interview follow-up response is invalid.");
            }
            return result;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExternalServiceException("Interview follow-up generation failed.", exception);
        }
    }
}
