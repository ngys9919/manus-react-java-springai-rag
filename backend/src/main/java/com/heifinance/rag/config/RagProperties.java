package com.heifinance.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the retrieval-augmented generation pipeline.
 */
@ConfigurationProperties(prefix = "heifinance.rag")
public record RagProperties(
        String knowledgeBasePath,
        boolean ingestOnStartup,
        Chunk chunk,
        Retrieval retrieval,
        History history,
        Assistant assistant) {

    public record Chunk(int size, int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks) {
    }

    public record Retrieval(int topK, double similarityThreshold, int maxContextChunks, int maxPerDocument) {
    }

    public record History(int maxTurns) {
    }

    public record Assistant(String name) {
    }
}
