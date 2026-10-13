package com.heifinance.rag.api.dto;

import java.time.Instant;

public record HealthResponse(
        String status,
        Instant checkedAt,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        long indexedChunks,
        int indexedDocuments,
        int conversations,
        Instant lastIndexedAt,
        String knowledgeBasePath) {
}
