package com.heifinance.rag.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.heifinance.rag.domain.ChatMessage;
import com.heifinance.rag.domain.ChatSource;
import com.heifinance.rag.domain.Conversation;
import com.heifinance.rag.domain.ConversationSummary;
import com.heifinance.rag.repository.ChatMessageRepository;
import com.heifinance.rag.repository.ConversationRepository;
import org.springframework.stereotype.Service;

/**
 * Owns conversation lifecycle and the persisted transcript that doubles as the model's
 * short-term memory.
 */
@Service
public class ConversationService {

    private static final int MAX_TITLE_LENGTH = 72;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ChatMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public List<ConversationSummary> list() {
        return conversationRepository.findAllSummaries();
    }

    public Conversation create(String title) {
        String safeTitle = (title == null || title.isBlank()) ? "New conversation" : title.trim();
        if (safeTitle.length() > 200) {
            safeTitle = safeTitle.substring(0, 200);
        }
        return conversationRepository.create(safeTitle);
    }

    public Conversation require(UUID id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation " + id + " was not found"));
    }

    public ConversationSummary requireSummary(UUID id) {
        return conversationRepository.findSummary(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation " + id + " was not found"));
    }

    public Conversation rename(UUID id, String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A conversation title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(0, 200);
        }
        if (conversationRepository.rename(id, trimmed) == 0) {
            throw new ResourceNotFoundException("Conversation " + id + " was not found");
        }
        return require(id);
    }

    public void delete(UUID id) {
        if (conversationRepository.delete(id) == 0) {
            throw new ResourceNotFoundException("Conversation " + id + " was not found");
        }
    }

    public List<ChatMessage> transcript(UUID conversationId) {
        require(conversationId);
        return messageRepository.findByConversationId(conversationId);
    }

    public int messageCount(UUID conversationId) {
        return messageRepository.countByConversationId(conversationId);
    }

    /** The last {@code maxTurns} user/assistant pairs, in chronological order. */
    public List<ChatMessage> recentHistory(UUID conversationId, int maxTurns) {
        int limit = Math.max(0, maxTurns) * 2;
        if (limit == 0) {
            return List.of();
        }
        return messageRepository.findRecent(conversationId, limit);
    }

    public ChatMessage append(UUID conversationId, String role, String content, List<ChatSource> sources,
                              String model, Integer latencyMs) {
        ChatMessage message = new ChatMessage(UUID.randomUUID(), conversationId, role, content,
                sources == null ? List.of() : sources, model, latencyMs, Instant.now());
        return messageRepository.save(message);
    }

    public void touch(UUID conversationId) {
        conversationRepository.touch(conversationId);
    }

    public int conversationCount() {
        return conversationRepository.count();
    }

    /**
     * Derives a readable conversation title from the first question.
     */
    public static String deriveTitle(String question) {
        String normalised = question.replaceAll("\\s+", " ").trim();
        if (normalised.isEmpty()) {
            return "New conversation";
        }
        if (normalised.length() <= MAX_TITLE_LENGTH) {
            return capitalise(normalised);
        }
        int cut = normalised.lastIndexOf(' ', MAX_TITLE_LENGTH);
        if (cut < MAX_TITLE_LENGTH / 2) {
            cut = MAX_TITLE_LENGTH;
        }
        return capitalise(normalised.substring(0, cut).trim()) + "…";
    }

    private static String capitalise(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
