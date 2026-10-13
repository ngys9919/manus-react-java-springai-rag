package com.heifinance.rag.domain;

/**
 * A passage returned by retrieval, before it is presented as a citation.
 *
 * <p>{@code score} is the fused relevance produced by hybrid retrieval (semantic + lexical), already
 * normalised so the strongest passage of a question scores 1.0.
 */
public record RetrievedChunk(
        String documentId,
        String fileName,
        String title,
        Integer page,
        double score,
        String text) {

    public RetrievedChunk withScore(double newScore) {
        return new RetrievedChunk(documentId, fileName, title, page, newScore, text);
    }

    public ChatSource toSource(int index, String excerpt) {
        return new ChatSource(index, documentId, fileName, title, page, score, excerpt, false);
    }
}
