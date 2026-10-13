package com.heifinance.rag.api;

import java.time.Instant;

import com.heifinance.rag.api.dto.HealthResponse;
import com.heifinance.rag.config.RagProperties;
import com.heifinance.rag.repository.KnowledgeDocumentRepository;
import com.heifinance.rag.service.ConversationService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final EmbeddingModel embeddingModel;
    private final KnowledgeDocumentRepository documentRepository;
    private final ConversationService conversationService;
    private final RagProperties properties;
    private final String chatModel;
    private final String embeddingModelName;

    public SystemController(EmbeddingModel embeddingModel,
                            KnowledgeDocumentRepository documentRepository,
                            ConversationService conversationService,
                            RagProperties properties,
                            @Value("${spring.ai.openai.chat.options.model:unknown}") String chatModel,
                            @Value("${heifinance.rag.embedding-model-name:all-MiniLM-L6-v2 (local ONNX)}")
                            String embeddingModelName) {
        this.embeddingModel = embeddingModel;
        this.documentRepository = documentRepository;
        this.conversationService = conversationService;
        this.properties = properties;
        this.chatModel = chatModel;
        this.embeddingModelName = embeddingModelName;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse(
                "UP",
                Instant.now(),
                chatModel,
                embeddingModelName,
                embeddingModel.dimensions(),
                documentRepository.totalChunks(),
                documentRepository.countIndexed(),
                conversationService.conversationCount(),
                documentRepository.lastIndexedAt().orElse(null),
                properties.knowledgeBasePath()));
    }
}
