-- HeiFinance RAG — application schema
-- The vector table itself is created and maintained by Spring AI's PgVectorStore.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS conversations (
    id          UUID PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_messages (
    seq             BIGSERIAL   PRIMARY KEY,
    id              UUID        NOT NULL,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    sources         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    model           VARCHAR(80),
    latency_ms      INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation
    ON chat_messages (conversation_id, seq);

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id          VARCHAR(120) PRIMARY KEY,
    file_name   VARCHAR(255) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    page_count  INTEGER      NOT NULL DEFAULT 0,
    chunk_count INTEGER      NOT NULL DEFAULT 0,
    size_bytes  BIGINT       NOT NULL DEFAULT 0,
    checksum    VARCHAR(64),
    status      VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    indexed_at  TIMESTAMPTZ
);
