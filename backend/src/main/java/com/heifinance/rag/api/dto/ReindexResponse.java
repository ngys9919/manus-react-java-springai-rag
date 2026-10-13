package com.heifinance.rag.api.dto;

import java.util.List;

import com.heifinance.rag.service.IngestionReport;

public record ReindexResponse(
        int filesScanned,
        int indexed,
        int skipped,
        int failed,
        int totalChunks,
        long durationMs,
        List<KnowledgeDocumentResponse> documents) {

    public static ReindexResponse from(IngestionReport report) {
        return new ReindexResponse(report.filesScanned(), report.indexed(), report.skipped(), report.failed(),
                report.totalChunks(), report.durationMs(),
                report.documents().stream().map(KnowledgeDocumentResponse::from).toList());
    }
}
