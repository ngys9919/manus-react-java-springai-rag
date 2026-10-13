package com.heifinance.rag.domain;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(
        UUID id,
        String title,
        int messageCount,
        String preview,
        Instant createdAt,
        Instant updatedAt) {
}
