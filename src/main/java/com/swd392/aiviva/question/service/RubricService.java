package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.RubricRequest;
import com.swd392.aiviva.question.dto.response.RubricMatrixResponse;
import com.swd392.aiviva.question.dto.response.RubricResolutionResponse;
import com.swd392.aiviva.question.dto.response.RubricResponse;
import java.util.List;
import java.util.UUID;

public interface RubricService {
    RubricResponse createRubric(UUID subjectId, RubricRequest request);
    RubricResponse createRubricForTopic(UUID topicId, RubricRequest request);
    RubricResponse getActiveRubric(UUID subjectId);
    List<RubricResponse> listRubrics(UUID subjectId, String status);
    List<RubricResponse> listRubricsForTopic(UUID topicId, String status);
    RubricResponse getRubric(UUID rubricId);
    RubricMatrixResponse questionStats(UUID subjectId);
    RubricMatrixResponse questionStatsForTopic(UUID topicId);
    RubricResponse updateRubric(UUID rubricId, RubricRequest request);
    RubricResponse updateActiveRubric(UUID subjectId, RubricRequest request);
    void deleteRubric(UUID rubricId);
    void deleteActiveRubric(UUID subjectId);
    RubricResolutionResponse getEffectiveRubric(UUID questionId);
    RubricResolutionResponse setQuestionOverride(UUID questionId, RubricRequest request);
}
