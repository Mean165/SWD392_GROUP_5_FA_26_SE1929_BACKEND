package com.swd392.aiviva.question.ai.port;

import java.util.List;

public interface EmbeddingPort {
    List<Double> embed(String text);
}
