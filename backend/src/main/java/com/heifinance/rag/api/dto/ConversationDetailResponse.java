package com.heifinance.rag.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.heifinance.rag.domain.ChatMessage;
import com.heifinance.rag.domain.ConversationSummary;

public record ConversationDetailResponse(
        UUID id,
        String title,
        int messageCount,
        Instant createdAt,
        Instant updatedAt,
        List<ChatMessage> messages) {

    public static ConversationDetailResponse of(ConversationSummary summary, List<ChatMessage> messages) {
        return new ConversationDetailResponse(summary.id(), summary.title(), messages.size(),
                summary.createdAt(), summary.updatedAt(), messages);
    }
}
