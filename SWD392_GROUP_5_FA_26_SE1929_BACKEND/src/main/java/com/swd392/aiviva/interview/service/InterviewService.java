package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.dto.request.StartInterviewRequest;
import com.swd392.aiviva.interview.dto.response.InterviewResponse;

public interface InterviewService {

    InterviewResponse startInterview(StartInterviewRequest request);
}

