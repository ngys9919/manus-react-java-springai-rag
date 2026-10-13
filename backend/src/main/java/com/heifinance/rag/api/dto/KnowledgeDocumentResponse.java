package com.heifinance.rag.api.dto;

import java.time.Instant;

import com.heifinance.rag.domain.KnowledgeDocument;

public record KnowledgeDocumentResponse(
        String id,
        String fileName,
        String title,
        int pageCount,
        int chunkCount,
        long sizeBytes,
        String status,
        Instant indexedAt) {

    public static KnowledgeDocumentResponse from(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(document.id(), document.fileName(), document.title(),
                document.pageCount(), document.chunkCount(), document.sizeBytes(), document.status(),
                document.indexedAt());
    }
}
