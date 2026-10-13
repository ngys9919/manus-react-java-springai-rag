package com.heifinance.rag.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A persisted turn in a conversation.
 *
 * @param role      {@code USER} or {@code ASSISTANT}
 * @param sources   citations attached to assistant turns
 * @param model     model identifier used for assistant turns
 * @param latencyMs end-to-end generation latency for assistant turns
 */
public record ChatMessage(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        List<ChatSource> sources,
        String model,
        Integer latencyMs,
        Instant createdAt) {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }
}
