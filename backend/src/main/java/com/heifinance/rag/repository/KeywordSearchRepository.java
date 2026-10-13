package com.heifinance.rag.repository;

import java.util.List;

import com.heifinance.rag.domain.RetrievedChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lexical search over the same passage table the vector store writes to.
 *
 * <p>Vector search alone is weak at precise lookups — "what is Kenji Tan's number?", "who is the
 * Head of Consumer Banking?" — because one 384-dimension embedding has to summarise an entire
 * passage, and a passage that merely shares vocabulary can outrank the passage that holds the
 * answer. The lexical leg guarantees that a passage containing the question's own wording is
 * retrieved; {@code RetrievalService} then fuses both legs.
 */
@Repository
public class KeywordSearchRepository {

    private static final String COLUMNS = """
            SELECT content,
                   metadata ->> 'documentId' AS document_id,
                   metadata ->> 'fileName'   AS file_name,
                   metadata ->> 'title'      AS title,
                   COALESCE((metadata ->> 'page')::int, 0) AS page
            FROM heifinance_vectors
            """;

    /** Passages that literally contain a consecutive phrase taken from the question. */
    private static final String PHRASE_SQL = COLUMNS + """
            WHERE regexp_replace(lower(content), '[^a-z0-9]+', ' ', 'g') LIKE '%' || ? || '%'
            ORDER BY length(content) ASC
            LIMIT ?
            """;

    /** Passages matching any of the question's content terms, best cover-density first. */
    private static final String TERM_SQL = """
            SELECT content,
                   metadata ->> 'documentId' AS document_id,
                   metadata ->> 'fileName'   AS file_name,
                   metadata ->> 'title'      AS title,
                   COALESCE((metadata ->> 'page')::int, 0) AS page,
                   ts_rank_cd(to_tsvector('english', content),
                              websearch_to_tsquery('english', ?)) AS rank
            FROM heifinance_vectors
            WHERE to_tsvector('english', content) @@ websearch_to_tsquery('english', ?)
            ORDER BY rank DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public KeywordSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RetrievedChunk> findByPhrase(String phrase, int limit) {
        return jdbcTemplate.query(PHRASE_SQL,
                (resultSet, rowNumber) -> new RetrievedChunk(
                        resultSet.getString("document_id"),
                        resultSet.getString("file_name"),
                        resultSet.getString("title"),
                        resultSet.getInt("page"),
                        0d,
                        resultSet.getString("content")),
                phrase, limit);
    }

    public List<RetrievedChunk> findByAnyTerm(String webQuery, int limit) {
        return jdbcTemplate.query(TERM_SQL,
                (resultSet, rowNumber) -> new RetrievedChunk(
                        resultSet.getString("document_id"),
                        resultSet.getString("file_name"),
                        resultSet.getString("title"),
                        resultSet.getInt("page"),
                        resultSet.getDouble("rank"),
                        resultSet.getString("content")),
                webQuery, webQuery, limit);
    }
}
