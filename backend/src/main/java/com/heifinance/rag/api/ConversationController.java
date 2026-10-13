package com.heifinance.rag.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.heifinance.rag.api.dto.ConversationDetailResponse;
import com.heifinance.rag.api.dto.CreateConversationRequest;
import com.heifinance.rag.api.dto.RenameConversationRequest;
import com.heifinance.rag.domain.Conversation;
import com.heifinance.rag.domain.ConversationSummary;
import com.heifinance.rag.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ResponseEntity<List<ConversationSummary>> list() {
        return ResponseEntity.ok(conversationService.list());
    }

    @PostMapping
    public ResponseEntity<ConversationSummary> create(@Valid @RequestBody(required = false)
                                                      CreateConversationRequest request) {
        String title = request == null ? null : request.title();
        Conversation created = conversationService.create(title);
        return ResponseEntity.created(URI.create("/api/conversations/" + created.id()))
                .body(conversationService.requireSummary(created.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDetailResponse> detail(@PathVariable UUID id) {
        ConversationSummary summary = conversationService.requireSummary(id);
        return ResponseEntity.ok(ConversationDetailResponse.of(summary, conversationService.transcript(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ConversationSummary> rename(@PathVariable UUID id,
                                                      @Valid @RequestBody RenameConversationRequest request) {
        conversationService.rename(id, request.title());
        return ResponseEntity.ok(conversationService.requireSummary(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        conversationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
