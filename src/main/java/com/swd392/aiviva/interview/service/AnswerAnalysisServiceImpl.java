package com.swd392.aiviva.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.interview.dto.AnswerAnalysisContext;
import com.swd392.aiviva.interview.dto.AnswerAnalysisResult;
import com.swd392.aiviva.interview.ai.llm.LlmProviderAdapter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        } catch (JsonProcessingException e) {
            throw new ExternalServiceException("Unable to prepare interview context for AI analysis.", e);
        }

        String prompt = """
                You are evaluating an oral examination answer. Evaluate meaning rather than exact wording.
                Use the main question, current question, transcript, earlier turns for that same main question,
                and rubric. Do not infer lack of knowledge from uncertain transcription. Mark an unclear,
                contradictory, or unreliable answer as AMBIGUOUS, CONTRADICTORY, or UNASSESSABLE and ask
                a short clarification/retry question when appropriate. Ask a follow-up only when it targets
                a specific relevant gap, ambiguity, or contradiction. Do not reveal the expected answer,
                repeat the original question, or introduce another topic. If the answer is sufficient or no
                follow-ups/time remain, set followUpQuestion to null.

                Return only one JSON object with exactly these fields:
                {"classification":"SUFFICIENT|PARTIAL|AMBIGUOUS|CONTRADICTORY|UNRELATED|UNASSESSABLE",
                 "summary":"concise evidence-based assessment",
                 "missingConcepts":["specific concept"],
                 "followUpQuestion":null,
                 "criteria":[{"rubricIndex":0,"criteriaName":"exact rubric criterion name",
                              "scoreFraction":0.0,
                              "complianceStatus":"COMPLIANT|PARTIAL|NOT_COMPLIANT",
                              "missingConcepts":["specific concept"],
                              "reasoning":"brief evidence-based rationale"}]}
                scoreFraction must be between 0 and 1. Include exactly one criteria entry per supplied rubric item,
                preserving each criteriaName exactly. Use an empty criteria array when no rubric was supplied.
                Write the summary and any follow-up in the supplied interview language. Keep follow-upQuestion
                null when no follow-up is appropriate.

                Interview context JSON:
                """ + contextJson;

        try {
            AnswerAnalysisResult result = objectMapper.readValue(
                    llmProviderAdapter.generateText(prompt), AnswerAnalysisResult.class);
            validate(result, context.rubric());
            return result;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("AI answer analysis returned an invalid response.", e);
        }
    }

    private void validate(AnswerAnalysisResult result, List<AnswerAnalysisContext.RubricItem> rubric) {
        if (result == null || result.classification() == null || isBlank(result.summary())) {
            throw new ExternalServiceException("AI answer analysis returned an incomplete response.");
        }
        if (result.summary().length() > 4000
                || (result.followUpQuestion() != null && result.followUpQuestion().length() > 500)) {
            throw new ExternalServiceException("AI answer analysis exceeded supported response limits.");
        }

        List<AnswerAnalysisResult.CriterionResult> criteria =
                result.criteria() == null ? List.of() : result.criteria();
        if (criteria.size() != rubric.size()) {
            throw new ExternalServiceException("AI answer analysis did not return every rubric criterion.");
        }

        Set<Integer> returnedIndices = new HashSet<>();
        for (AnswerAnalysisResult.CriterionResult criterion : criteria) {
            if (criterion == null || criterion.rubricIndex() == null || isBlank(criterion.criteriaName())
                    || isBlank(criterion.complianceStatus())
                    || isBlank(criterion.reasoning()) || criterion.scoreFraction() == null
                    || !Double.isFinite(criterion.scoreFraction())
                    || criterion.scoreFraction() < 0 || criterion.scoreFraction() > 1
                    || !returnedIndices.add(criterion.rubricIndex())) {
                throw new ExternalServiceException("AI answer analysis returned an invalid rubric assessment.");
            }
            boolean known = criterion.rubricIndex() >= 0 && criterion.rubricIndex() < rubric.size()
                    && rubric.get(criterion.rubricIndex()).name().equals(criterion.criteriaName());
            if (!known || !Set.of("COMPLIANT", "PARTIAL", "NOT_COMPLIANT")
                    .contains(criterion.complianceStatus())) {
                throw new ExternalServiceException("AI answer analysis returned an unknown rubric assessment.");
            }
        }
        if (result.followUpQuestion() != null && result.followUpQuestion().isBlank()) {
            throw new ExternalServiceException("AI answer analysis returned a blank follow-up question.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
