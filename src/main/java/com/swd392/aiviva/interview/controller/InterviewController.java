package com.swd392.aiviva.interview.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.interview.dto.request.StartInterviewRequest;
import com.swd392.aiviva.interview.dto.request.SubmitAnswerRequest;
import com.swd392.aiviva.interview.dto.request.CompleteInterviewRequest;
import com.swd392.aiviva.interview.dto.response.InteractionTurnResponse;
import com.swd392.aiviva.interview.dto.response.InterviewResponse;
import com.swd392.aiviva.interview.dto.response.SubmitAnswerResponse;
import com.swd392.aiviva.interview.service.InterviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    /**
     * Start or resume an AI Viva interview for a session and student.
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<InterviewResponse>> startInterview(@Valid @RequestBody StartInterviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Interview started", interviewService.startInterview(request)));
    }

    /**
     * Get current status and details of an interview attempt.
     */
    @GetMapping("/{attemptId}")
    public ResponseEntity<ApiResponse<InterviewResponse>> getInterviewStatus(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(ApiResponse.success("Interview status retrieved", interviewService.getInterviewStatus(attemptId)));
    }

    /**
     * Get list of interaction turns (questions, transcripts, follow-ups) for an interview attempt.
     */
    @GetMapping("/{attemptId}/turns")
    public ResponseEntity<ApiResponse<List<InteractionTurnResponse>>> getInterviewTurns(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(ApiResponse.success("Interview turns retrieved", interviewService.getInterviewTurns(attemptId)));
    }

    /**
     * Submit voice/text answer for the current turn, perform STT/analysis, and receive follow-up or next question.
     */
    @PostMapping("/submit-answer")
    public ResponseEntity<ApiResponse<SubmitAnswerResponse>> submitAnswer(@Valid @RequestBody SubmitAnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Answer processed successfully", interviewService.submitAnswer(request)));
    }

    /**
     * Finish/complete an interview attempt.
     */
    @PostMapping("/{attemptId}/complete")
    public ResponseEntity<ApiResponse<InterviewResponse>> completeInterview(
            @PathVariable UUID attemptId,
            @RequestBody(required = false) @Valid CompleteInterviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Interview marked as completed",
                interviewService.completeInterview(attemptId,
                        request == null ? null : request.getFullAudioDataUrl())));
    }

    /** Generates an authenticated MP3 response for the question in this turn. */
    @GetMapping(value = "/turns/{turnId}/speech", produces = "audio/mpeg")
    public ResponseEntity<byte[]> getTurnSpeechAudio(
            @PathVariable UUID turnId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String language) {
        byte[] audioBytes = interviewService.getQuestionSpeechAudio(turnId, language);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"question_" + turnId + ".mp3\"")
                .body(audioBytes);
    }

    @GetMapping(value = "/{attemptId}/audio/{fileName}")
    public ResponseEntity<byte[]> getInterviewAudio(
            @PathVariable UUID attemptId, @PathVariable String fileName) {
        InterviewService.AudioFile audio = interviewService.getInterviewAudio(attemptId, fileName);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(audio.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(audio.bytes());
    }
}
