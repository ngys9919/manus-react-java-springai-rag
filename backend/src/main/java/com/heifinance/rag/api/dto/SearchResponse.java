package com.heifinance.rag.api.dto;

import java.util.List;

public record SearchResponse(
        String query,
        int topK,
        double similarityThreshold,
        List<SearchHit> hits) {

    public record SearchHit(
            int rank,
            String documentId,
            String fileName,
            String title,
            Integer page,
            double score,
            String excerpt) {
    }
}
