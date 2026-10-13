package com.heifinance.rag.api;

import java.util.ArrayList;
import java.util.List;

import com.heifinance.rag.api.dto.KnowledgeDocumentResponse;
import com.heifinance.rag.api.dto.ReindexResponse;
import com.heifinance.rag.api.dto.SearchResponse;
import com.heifinance.rag.domain.RetrievedChunk;
import com.heifinance.rag.service.KnowledgeIngestionService;
import com.heifinance.rag.service.RetrievalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final int EXCERPT_LENGTH = 320;

    private final KnowledgeIngestionService ingestionService;
    private final RetrievalService retrievalService;

    public KnowledgeController(KnowledgeIngestionService ingestionService, RetrievalService retrievalService) {
        this.ingestionService = ingestionService;
        this.retrievalService = retrievalService;
    }

    /** The indexed source-of-truth corpus. */
    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocumentResponse>> documents() {
        return ResponseEntity.ok(ingestionService.listDocuments().stream()
                .map(KnowledgeDocumentResponse::from)
                .toList());
    }

    /** Forces a full re-index of the knowledge base. */
    @PostMapping("/reindex")
    public ResponseEntity<ReindexResponse> reindex(@RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(ReindexResponse.from(ingestionService.ingest(force)));
    }

    /** Retrieval-only inspection so staff can see exactly what the assistant can reach. */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam("q") String query,
                                                 @RequestParam(defaultValue = "5") int topK,
                                                 @RequestParam(defaultValue = "0.0") double threshold) {
        List<RetrievedChunk> chunks = retrievalService.retrieve(query, topK, threshold);
        List<SearchResponse.SearchHit> hits = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            hits.add(new SearchResponse.SearchHit(index + 1, chunk.documentId(), chunk.fileName(), chunk.title(),
                    chunk.page(), chunk.score(), excerpt(chunk.text())));
        }
        return ResponseEntity.ok(new SearchResponse(query, topK, threshold, hits));
    }

    private static String excerpt(String text) {
        String normalised = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalised.length() <= EXCERPT_LENGTH) {
            return normalised;
        }
        int cut = normalised.lastIndexOf(' ', EXCERPT_LENGTH);
        if (cut < EXCERPT_LENGTH / 2) {
            cut = EXCERPT_LENGTH;
        }
        return normalised.substring(0, cut).trim() + "…";
    }
}
