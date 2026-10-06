package com.swd392.aiviva.exam.selection;

import org.springframework.stereotype.Component;

@Component
public class AdaptiveQuestionSelectionStrategy implements QuestionSelectionStrategy {

    @Override
    public String selectQuestions() {
        // TODO: Implement adaptive question selection strategy.
        return "adaptive";
    }
}

