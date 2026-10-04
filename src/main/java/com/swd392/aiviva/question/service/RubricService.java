package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.RubricRequest;
import com.swd392.aiviva.question.dto.response.RubricResponse;

public interface RubricService {

    RubricResponse createRubric(RubricRequest request);
}

