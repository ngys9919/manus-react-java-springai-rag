package com.heifinance.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Spring AI chat client used for grounded answer generation.
 *
 * <p>The embedding model (local ONNX {@code all-MiniLM-L6-v2}, 384 dimensions) and the
 * {@code PgVectorStore} are created by Spring AI's auto-configuration; see
 * {@code application.yml} for the {@code spring.ai.*} settings.</p>
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
