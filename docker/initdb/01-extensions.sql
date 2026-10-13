-- Extensions required by the HeiFinance RAG service.
-- The pgvector/pgvector image already provides the extension binaries.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";