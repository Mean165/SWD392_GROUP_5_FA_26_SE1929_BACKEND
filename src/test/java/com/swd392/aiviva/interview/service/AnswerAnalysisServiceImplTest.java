package com.swd392.aiviva.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.interview.ai.llm.LlmProviderAdapter;
import com.swd392.aiviva.interview.dto.AnswerAnalysisContext;
import com.swd392.aiviva.interview.dto.AnswerAnalysisResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnswerAnalysisServiceImplTest {

    @Mock
    private LlmProviderAdapter llmProviderAdapter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void analyzesQuestionContextAndValidatesRubricResults() {
        AnswerAnalysisServiceImpl service = new AnswerAnalysisServiceImpl(llmProviderAdapter, objectMapper);
        AnswerAnalysisContext context = new AnswerAnalysisContext(
                "vi",
                "Explain encapsulation",
                "Explain encapsulation",
                "It hides internal state behind a public API.",
                List.of(new AnswerAnalysisContext.PreviousTurn(
                        "MAIN", "Explain encapsulation", "Encapsulation protects internal state.")),
                List.of(new AnswerAnalysisContext.RubricItem(0, "Data hiding", "Restrict direct access", 0.5)),
                0,
                2,
                90);
        when(llmProviderAdapter.generateText(anyString())).thenReturn("""
                {
                  "classification":"PARTIAL",
                  "summary":"The answer explains data hiding but omits controlled access.",
                  "missingConcepts":["controlled access"],
                  "followUpQuestion":"How can callers access or change the hidden state safely?",
                  "criteria":[{"rubricIndex":0,"criteriaName":"Data hiding","scoreFraction":0.7,
                    "complianceStatus":"PARTIAL","missingConcepts":["controlled access"],
                    "reasoning":"The transcript describes hiding, but not controlled access."}]
                }
                """);

        AnswerAnalysisResult result = service.analyzeAnswer(context);

        assertEquals(AnswerAnalysisResult.Classification.PARTIAL, result.classification());
        assertEquals("controlled access", result.missingConcepts().get(0));
        assertEquals(0.7, result.criteria().get(0).scoreFraction());
        assertEquals("Data hiding", result.criteria().get(0).criteriaName());
    }

    @Test
    void rejectsMissingRubricResultsInsteadOfInventingAnAssessment() {
        AnswerAnalysisServiceImpl service = new AnswerAnalysisServiceImpl(llmProviderAdapter, objectMapper);
        AnswerAnalysisContext context = new AnswerAnalysisContext(
                "vi", "Question", "Question", "Answer", List.of(),
                List.of(new AnswerAnalysisContext.RubricItem(0, "Concept", "Expected idea", 1)), 0, 1, 60);
        when(llmProviderAdapter.generateText(anyString())).thenReturn("""
                {"classification":"SUFFICIENT","summary":"Looks good.","missingConcepts":[],
                 "followUpQuestion":null,"criteria":[]}
                """);

        assertThrows(ExternalServiceException.class, () -> service.analyzeAnswer(context));
    }
}
