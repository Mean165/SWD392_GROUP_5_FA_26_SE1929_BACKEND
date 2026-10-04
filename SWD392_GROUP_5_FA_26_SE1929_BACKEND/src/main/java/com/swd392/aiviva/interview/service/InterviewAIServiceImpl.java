package com.swd392.aiviva.interview.service;

import org.springframework.stereotype.Service;

@Service
public class InterviewAIServiceImpl implements InterviewAIService {

    @Override
    public String generateFollowUp(String context) {
        // TODO: Integrate LLM provider adapter.
        return "Generated follow-up";
    }
}

