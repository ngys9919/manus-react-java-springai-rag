package com.heifinance.rag.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.heifinance.rag.domain.ChatAnswer;
import com.heifinance.rag.domain.ChatSource;

public record ChatResponse(
        UUID conversationId,
        String conversationTitle,
        UUID messageId,
        String answer,
        List<ChatSource> sources,
        String model,
        long latencyMs,
        Instant createdAt) {

    public static ChatResponse from(ChatAnswer answer) {
        return new ChatResponse(answer.conversationId(), answer.conversationTitle(), answer.messageId(),
                answer.answer(), answer.sources(), answer.model(), answer.latencyMs(), answer.createdAt());
    }
}
