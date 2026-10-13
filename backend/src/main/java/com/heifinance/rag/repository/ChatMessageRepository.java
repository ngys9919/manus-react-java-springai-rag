package com.heifinance.rag.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.heifinance.rag.domain.ChatMessage;
import com.heifinance.rag.domain.ChatSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ChatMessageRepository {

    private static final String COLUMNS =
            "id, conversation_id, role, content, sources, model, latency_ms, created_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<ChatMessage> rowMapper = this::mapRow;

    public ChatMessageRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public ChatMessage save(ChatMessage message) {
        jdbc.update("""
                INSERT INTO chat_messages (id, conversation_id, role, content, sources, model, latency_ms, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """,
                message.id(),
                message.conversationId(),
                message.role(),
                message.content(),
                writeSources(message.sources()),
                message.model(),
                message.latencyMs(),
                java.sql.Timestamp.from(message.createdAt() == null ? Instant.now() : message.createdAt()));
        return message;
    }

    public List<ChatMessage> findByConversationId(UUID conversationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM chat_messages WHERE conversation_id = ? ORDER BY seq ASC",
                rowMapper, conversationId);
    }

    /** Returns the most recent {@code limit} turns in chronological order. */
    public List<ChatMessage> findRecent(UUID conversationId, int limit) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT seq, %s FROM chat_messages
                    WHERE conversation_id = ?
                    ORDER BY seq DESC
                    LIMIT ?
                ) recent
                ORDER BY recent.seq ASC
                """.formatted(COLUMNS), rowMapper, conversationId, limit);
    }

    public int countByConversationId(UUID conversationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?", Integer.class, conversationId);
        return count == null ? 0 : count;
    }

    private ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getString("role"),
                rs.getString("content"),
                readSources(rs.getString("sources")),
                rs.getString("model"),
                (Integer) rs.getObject("latency_ms"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String writeSources(List<ChatSource> sources) {
        try {
            return objectMapper.writeValueAsString(sources == null ? List.of() : sources);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialise citations", ex);
        }
    }

    private List<ChatSource> readSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChatSource>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
