package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.ai.followup.FollowUpQuestionProviderAdapter;
import org.springframework.stereotype.Service;

@Service
public class InterviewAIServiceImpl implements InterviewAIService {

    private final FollowUpQuestionProviderAdapter followUpQuestionProviderAdapter;

    public InterviewAIServiceImpl(FollowUpQuestionProviderAdapter followUpQuestionProviderAdapter) {
        this.followUpQuestionProviderAdapter = followUpQuestionProviderAdapter;
    }

    @Override
    public String generateFollowUp(String context) {
        return followUpQuestionProviderAdapter.generateFollowUp(context);
    }
}
