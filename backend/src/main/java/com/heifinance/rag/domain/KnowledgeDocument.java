package com.heifinance.rag.domain;

import java.time.Instant;

public record KnowledgeDocument(
        String id,
        String fileName,
        String title,
        int pageCount,
        int chunkCount,
        long sizeBytes,
        String checksum,
        String status,
        Instant indexedAt) {

    public static final String STATUS_INDEXED = "INDEXED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FAILED = "FAILED";

    public KnowledgeDocument withStatus(String newStatus) {
        return new KnowledgeDocument(id, fileName, title, pageCount, chunkCount, sizeBytes, checksum,
                newStatus, indexedAt);
    }
}
