package com.swd392.aiviva.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.interview.ai.llm.LlmProviderAdapter;
import com.swd392.aiviva.interview.dto.AnswerAnalysisContext;
import com.swd392.aiviva.interview.dto.AnswerAnalysisResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnswerAnalysisServiceImplTest {

    @Mock
    private LlmProviderAdapter llmProviderAdapter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestsOnlyAContextualFollowUpAndNeverSuppliesRubricOrScoreFields() {
        AnswerAnalysisServiceImpl service = new AnswerAnalysisServiceImpl(llmProviderAdapter, objectMapper);
        AnswerAnalysisContext context = new AnswerAnalysisContext(
                "vi", "Explain encapsulation", "Explain encapsulation",
                "It hides internal state behind a public API.",
                List.of(new AnswerAnalysisContext.PreviousTurn(
                        "MAIN", "Explain encapsulation", "Encapsulation protects internal state.")),
                0, 2, 90);
        when(llmProviderAdapter.generateText(anyString())).thenReturn(
                "{\"followUpQuestion\":\"How can callers change the hidden state safely?\"}");

        AnswerAnalysisResult result = service.analyzeAnswer(context);

        assertEquals("How can callers change the hidden state safely?", result.followUpQuestion());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmProviderAdapter).generateText(prompt.capture());
        assertFalse(prompt.getValue().contains("\"rubric\":"));
        assertFalse(prompt.getValue().contains("\"scoreFraction\":"));
        assertFalse(prompt.getValue().contains("\"criteria\":"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.getValue().contains("Do not grade, score, evaluate correctness"));
    }

    @Test
    void acceptsNoFollowUpWhenTheInterviewerNeedsNoClarification() {
        AnswerAnalysisServiceImpl service = new AnswerAnalysisServiceImpl(llmProviderAdapter, objectMapper);
        when(llmProviderAdapter.generateText(anyString())).thenReturn("{\"followUpQuestion\":null}");

        assertEquals(null, service.analyzeAnswer(new AnswerAnalysisContext(
                "en", "Question", "Question", "Transcript", List.of(), 1, 1, 20)).followUpQuestion());
    }

    @Test
    void rejectsBlankFollowUpQuestions() {
        AnswerAnalysisServiceImpl service = new AnswerAnalysisServiceImpl(llmProviderAdapter, objectMapper);
        when(llmProviderAdapter.generateText(anyString())).thenReturn("{\"followUpQuestion\":\" \"}");

        assertThrows(ExternalServiceException.class,
                () -> service.analyzeAnswer(new AnswerAnalysisContext("vi", "Q", "Q", "A", List.of(), 0, 1, 10)));
    }
}
