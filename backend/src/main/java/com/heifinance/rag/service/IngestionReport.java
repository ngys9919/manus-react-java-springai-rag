package com.heifinance.rag.service;

import java.util.List;

import com.heifinance.rag.domain.KnowledgeDocument;

public record IngestionReport(
        int filesScanned,
        int indexed,
        int skipped,
        int failed,
        int totalChunks,
        long durationMs,
        List<KnowledgeDocument> documents) {
}
