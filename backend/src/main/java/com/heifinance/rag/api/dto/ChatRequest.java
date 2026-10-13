package com.heifinance.rag.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId existing conversation to continue, or {@code null} to start a new one
 */
public record ChatRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 2000, message = "message must be at most 2000 characters")
        String message,

        UUID conversationId) {
}
