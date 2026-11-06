package com.swd392.aiviva.question.ai.port;

import java.util.List;

public interface EmbeddingPort {
    List<Double> embed(String text);

    default List<Double> embedDocument(String text) { return embed(text); }
    default List<Double> embedQuery(String text) { return embed(text); }
}
