package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.ai.followup.FollowUpQuestionProviderAdapter;
import org.springframework.stereotype.Service;

@Service
public class FollowUpQuestionServiceImpl implements FollowUpQuestionService {

    private final FollowUpQuestionProviderAdapter followUpQuestionProviderAdapter;

    public FollowUpQuestionServiceImpl(FollowUpQuestionProviderAdapter followUpQuestionProviderAdapter) {
        this.followUpQuestionProviderAdapter = followUpQuestionProviderAdapter;
    }

    @Override
    public String buildFollowUpQuestion(String transcript, String rubric) {
        String context = "Student Answer: \"" + transcript + "\". Rubric / Key points: \"" + rubric + "\"";
        return followUpQuestionProviderAdapter.generateFollowUp(context);
    }
}
