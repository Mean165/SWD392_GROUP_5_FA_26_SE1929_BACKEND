package com.swd392.aiviva.interview.ai.followup;

import com.swd392.aiviva.interview.ai.llm.LlmProviderAdapter;
import org.springframework.stereotype.Component;

@Component
public class OpenAiFollowUpQuestionProviderAdapter implements FollowUpQuestionProviderAdapter {

    private final LlmProviderAdapter llmProviderAdapter;

    public OpenAiFollowUpQuestionProviderAdapter(LlmProviderAdapter llmProviderAdapter) {
        this.llmProviderAdapter = llmProviderAdapter;
    }

    @Override
    public String generateFollowUp(String transcriptContext) {
        String prompt = "Based on the student's answer: \"" + transcriptContext + "\", generate a concise, focused follow-up examination question to test their understanding deeper. Return only the question text.";
        return llmProviderAdapter.generateText(prompt);
    }
}
