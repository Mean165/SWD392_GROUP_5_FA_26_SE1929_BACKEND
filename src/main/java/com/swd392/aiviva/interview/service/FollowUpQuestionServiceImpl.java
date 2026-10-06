package com.swd392.aiviva.interview.service;

import org.springframework.stereotype.Service;

@Service
public class FollowUpQuestionServiceImpl implements FollowUpQuestionService {

    @Override
    public String buildFollowUpQuestion(String transcript, String rubric) {
        // TODO: Generate adaptive follow-up question.
        return "Follow-up question";
    }
}

