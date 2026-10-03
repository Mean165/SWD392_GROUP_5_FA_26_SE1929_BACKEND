package com.swd392.aiviva.exam.selection;

import org.springframework.stereotype.Component;

@Component
public class RandomQuestionSelectionStrategy implements QuestionSelectionStrategy {

    @Override
    public String selectQuestions() {
        // TODO: Implement randomized selection strategy.
        return "random";
    }
}

