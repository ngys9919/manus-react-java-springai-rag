package com.heifinance.rag.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.heifinance.rag.domain.Conversation;
import com.heifinance.rag.domain.ConversationSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private static final String SUMMARY_SQL = """
            SELECT c.id,
                   c.title,
                   c.created_at,
                   c.updated_at,
                   COALESCE(m.message_count, 0) AS message_count,
                   m.preview
            FROM conversations c
            LEFT JOIN (
                SELECT conversation_id,
                       COUNT(*) AS message_count,
                       (ARRAY_AGG(content ORDER BY seq DESC))[1] AS preview
                FROM chat_messages
                GROUP BY conversation_id
            ) m ON m.conversation_id = c.id
            """;

    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ConversationSummary> findAllSummaries() {
        return jdbc.query(SUMMARY_SQL + " ORDER BY c.updated_at DESC", (rs, rowNum) -> new ConversationSummary(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getInt("message_count"),
                rs.getString("preview"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
    }

    public Optional<ConversationSummary> findSummary(UUID id) {
        return jdbc.query(SUMMARY_SQL + " WHERE c.id = ?", (rs, rowNum) -> new ConversationSummary(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getInt("message_count"),
                rs.getString("preview"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), id).stream().findFirst();
    }

    public Optional<Conversation> findById(UUID id) {
        return jdbc.query("SELECT id, title, created_at, updated_at FROM conversations WHERE id = ?",
                (rs, rowNum) -> new Conversation(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                id).stream().findFirst();
    }

    public Conversation create(String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO conversations (id, title) VALUES (?, ?)", id, title);
        return findById(id).orElseThrow(() -> new IllegalStateException("Conversation insert failed"));
    }

    public int rename(UUID id, String title) {
        return jdbc.update("UPDATE conversations SET title = ?, updated_at = now() WHERE id = ?", title, id);
    }

    public int touch(UUID id) {
        return jdbc.update("UPDATE conversations SET updated_at = now() WHERE id = ?", id);
    }

    public int delete(UUID id) {
        return jdbc.update("DELETE FROM conversations WHERE id = ?", id);
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Integer.class);
        return count == null ? 0 : count;
    }

    public Instant lastActivity() {
        return jdbc.queryForObject("SELECT MAX(updated_at) FROM conversations", Instant.class);
    }
}
