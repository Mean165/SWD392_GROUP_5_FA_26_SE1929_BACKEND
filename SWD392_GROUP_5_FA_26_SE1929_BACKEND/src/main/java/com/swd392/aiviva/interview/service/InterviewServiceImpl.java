package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.dto.request.StartInterviewRequest;
import com.swd392.aiviva.interview.dto.response.InterviewResponse;
import org.springframework.stereotype.Service;

@Service
public class InterviewServiceImpl implements InterviewService {

    @Override
    public InterviewResponse startInterview(StartInterviewRequest request) {
        // TODO: Integrate with AI question loading, TTS, and interview orchestration.
        return null;
    }
}

