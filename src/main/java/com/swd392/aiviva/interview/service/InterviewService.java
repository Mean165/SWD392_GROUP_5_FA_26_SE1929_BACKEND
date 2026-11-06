package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.dto.request.StartInterviewRequest;
import com.swd392.aiviva.interview.dto.request.SubmitAnswerRequest;
import com.swd392.aiviva.interview.dto.response.InteractionTurnResponse;
import com.swd392.aiviva.interview.dto.response.InterviewResponse;
import com.swd392.aiviva.interview.dto.response.SubmitAnswerResponse;
import java.util.List;
import java.util.UUID;

public interface InterviewService {

    InterviewResponse startInterview(StartInterviewRequest request);

    InterviewResponse getInterviewStatus(UUID attemptId);

    List<InteractionTurnResponse> getInterviewTurns(UUID attemptId);

    SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request);

    InterviewResponse completeInterview(UUID attemptId);

    InterviewResponse completeInterview(UUID attemptId, String fullAudioDataUrl);

    byte[] getQuestionSpeechAudio(UUID turnId);

    byte[] getQuestionSpeechAudio(UUID turnId, String language);

    AudioFile getInterviewAudio(UUID attemptId, String filename);

    record AudioFile(byte[] bytes, String contentType) {
    }
}
