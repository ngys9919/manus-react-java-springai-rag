package com.heifinance.rag.api;

import com.heifinance.rag.api.dto.ChatRequest;
import com.heifinance.rag.api.dto.ChatResponse;
import com.heifinance.rag.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Answers a staff question from the HeiFinance corpus and returns the supporting citations.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ChatResponse.from(chatService.ask(request.conversationId(), request.message())));
    }
}
