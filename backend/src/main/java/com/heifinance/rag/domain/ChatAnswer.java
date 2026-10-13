package com.heifinance.rag.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatAnswer(
        UUID conversationId,
        String conversationTitle,
        UUID messageId,
        String answer,
        List<ChatSource> sources,
        String model,
        long latencyMs,
        Instant createdAt) {
}
