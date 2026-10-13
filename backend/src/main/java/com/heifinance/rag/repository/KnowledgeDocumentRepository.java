package com.heifinance.rag.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.heifinance.rag.domain.KnowledgeDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeDocumentRepository {

    private static final String COLUMNS =
            "id, file_name, title, page_count, chunk_count, size_bytes, checksum, status, indexed_at";

    private final JdbcTemplate jdbc;
    private final RowMapper<KnowledgeDocument> rowMapper = this::mapRow;

    public KnowledgeDocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeDocument> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_documents ORDER BY file_name ASC", rowMapper);
    }

    public Optional<KnowledgeDocument> findById(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_documents WHERE id = ?", rowMapper, id)
                .stream().findFirst();
    }

    public void upsert(KnowledgeDocument document) {
        jdbc.update("""
                INSERT INTO knowledge_documents
                    (id, file_name, title, page_count, chunk_count, size_bytes, checksum, status, indexed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    file_name = EXCLUDED.file_name,
                    title = EXCLUDED.title,
                    page_count = EXCLUDED.page_count,
                    chunk_count = EXCLUDED.chunk_count,
                    size_bytes = EXCLUDED.size_bytes,
                    checksum = EXCLUDED.checksum,
                    status = EXCLUDED.status,
                    indexed_at = EXCLUDED.indexed_at
                """,
                document.id(),
                document.fileName(),
                document.title(),
                document.pageCount(),
                document.chunkCount(),
                document.sizeBytes(),
                document.checksum(),
                document.status(),
                document.indexedAt() == null ? null : java.sql.Timestamp.from(document.indexedAt()));
    }

    public void delete(String id) {
        jdbc.update("DELETE FROM knowledge_documents WHERE id = ?", id);
    }

    public long totalChunks() {
        Long total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(chunk_count), 0) FROM knowledge_documents WHERE status = ?",
                Long.class, KnowledgeDocument.STATUS_INDEXED);
        return total == null ? 0L : total;
    }

    public int countIndexed() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_documents WHERE status = ?",
                Integer.class, KnowledgeDocument.STATUS_INDEXED);
        return count == null ? 0 : count;
    }

    public Optional<Instant> lastIndexedAt() {
        return Optional.ofNullable(jdbc.queryForObject(
                "SELECT MAX(indexed_at) FROM knowledge_documents WHERE status = ?",
                Instant.class, KnowledgeDocument.STATUS_INDEXED));
    }

    private KnowledgeDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp indexedAt = rs.getTimestamp("indexed_at");
        return new KnowledgeDocument(
                rs.getString("id"),
                rs.getString("file_name"),
                rs.getString("title"),
                rs.getInt("page_count"),
                rs.getInt("chunk_count"),
                rs.getLong("size_bytes"),
                rs.getString("checksum"),
                rs.getString("status"),
                indexedAt == null ? null : indexedAt.toInstant());
    }
}
