<div align="center">

# HeiFinance Knowledge Assistant

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker Hub](https://img.shields.io/badge/Docker%20Hub-ngys9919%2Fheifinance--rag-2496ED?logo=docker&logoColor=white)](https://hub.docker.com/r/ngys9919/heifinance-rag)

**A conversational RAG assistant that answers staff questions strictly from five source-of-truth PDFs — and cites the page it used.**

[Quick start](#quick-start) · [Architecture](#3-how-an-answer-is-produced) · [API](#7-rest-api) · [Documentation](#documentation)

</div>

---

An internal, conversational **Retrieval-Augmented Generation (RAG)** assistant for HeiFinance staff.
It answers internal support questions **strictly from five authoritative PDFs** and shows the exact
page each answer came from.

> **Answers traced to the page.** Ask the corpus. Nothing else is consulted.

## Quick start

Run the published image — no Java, Maven or Node required.

```bash
docker network create heifinance

docker run -d --name heifinance-db --network heifinance --restart unless-stopped \
  -e POSTGRES_DB=heifinance_rag -e POSTGRES_USER=heifinance \
  -e POSTGRES_PASSWORD=choose-a-password \
  -v heifinance-pgdata:/var/lib/postgresql/data \
  pgvector/pgvector:pg16

docker run -d --name heifinance-app --network heifinance --restart unless-stopped \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://heifinance-db:5432/heifinance_rag \
  -e DB_USERNAME=heifinance -e DB_PASSWORD=choose-a-password \
  -e OPENAI_API_KEY=sk-your-key \
  ngys9919/heifinance-rag:1.0.0
```

Open **http://localhost:8080**. The first boot ingests the five PDFs (~3-4 minutes); later starts are ready in seconds.

> The container needs an **OpenAI-compatible API key**. The embedding model is baked into the image, so there is no second dependency to install.

Prefer Compose, or want to build from source? See [RUN-WITH-DOCKER.md](RUN-WITH-DOCKER.md) and section 5 below.

## 1. Stack

| Layer | Technology |
| --- | --- |
| Frontend | **Vite + React 19 + TypeScript** |
| Backend | **Java 21 · Spring Boot 4.1.1 · Maven** |
| AI framework | **Spring AI 2.0.1** (`ChatClient`, `EmbeddingModel`, `VectorStore`, `PagePdfDocumentReader`, `TokenTextSplitter`) |
| Vector store | **PostgreSQL 16 + `pgvector`** (HNSW index, cosine distance) |
| Relational data | **PostgreSQL 16** (conversations, transcript, knowledge registry) |
| API contract | **RESTful JSON** under `/api` |

### Why a local embedding model?

The configured LLM gateway exposes **chat models only — there is no embeddings endpoint**.
The backend therefore runs `sentence-transformers/all-MiniLM-L6-v2` **locally as ONNX**
(384 dimensions) through Spring AI's Transformers integration. No second vendor, no
embedding cost, deterministic, and fully offline once the model files exist.

## 2. Source of truth

The assistant is grounded exclusively in these five documents, read from `knowledge-base/`:

1. `HeiFinance_Annual_Report_2025.pdf`
2. `HeiFinance Bank Product Fact Sheet.pdf`
3. `HeiFinance_Employee_Handbook.pdf`
4. `HeiFinance_Organization_Chart_Executive_Summary.pdf`
5. `HeiFinance_Full_Directory_Complete.pdf`

> **The PDFs are not published in this repository.** They *are* bundled into the
> published Docker image, so pulling the image runs with no extra files. Building
> from source, or running without Docker, needs the five files placed in
> `knowledge-base/` yourself — see
> [knowledge-base/README.md](knowledge-base/README.md).

Ingestion is idempotent: a document is skipped when its SHA-256 checksum is unchanged, and stale
chunks are purged before re-indexing. `POST /api/knowledge/reindex?force=true` rebuilds everything.

## 3. How an answer is produced

```
question ──▶ split into sub-questions (compound questions only)
                    │
        ┌───────────┴────────────────────────────────────────────┐
        │  legs, ordered by precision:                           │
        │   1. semantic      embed → pgvector cosine (top-k 12)  │
        │   2. year-scoped   content words + the question's years│
        │   3. exact phrase  longest wording found in the corpus │
        │   4. term overlap  full-text OR (lowest precision)     │
        └───────────┬────────────────────────────────────────────┘
                    │
        coverage-ordered fusion (RRF k=60, legs interleaved by rank)
                    │
        deduplicate · cap 6 per document · keep 12 passages
                    │
   numbered context blocks + last 8 turns of history ──▶ ChatClient (grounded prompt)
                    │
        answer + citation list persisted to PostgreSQL ──▶ REST response
```

**Why hybrid?** Vector search alone is weak on the precise lookups an internal desk deals in.
With page-sized passages, a block that merely shares vocabulary ("…Corporate Banking, Institutional
Banking, Transaction Banking…") can outrank the block that actually holds the answer. The lexical
leg guarantees that a passage containing the question's own wording is retrieved.

**Why coverage ordering?** A passage that shares vocabulary with every part of a compound question
accumulates rank points in several legs and would otherwise bury the single passage that answers one
part of it. Interleaving the legs by rank means every part of the question puts its best passage into
the window, while the summed score is still reported as the passage's relevance.

`ChatSource.score` is that **fused relevance**, normalised so the strongest passage of a question
scores 1.0. Note that the blocks are ordered by coverage, so relevance is not strictly descending.

**Why year-scoped probes?** Reporting-period questions are scoped by their years, and the years are
the most discriminative tokens in them — the same metric appears for several periods across the
corpus.

The system prompt forbids outside knowledge, requires inline `[n]` citations bound to the numbered
context blocks, and instructs the model to state plainly when the corpus does not contain an answer.

## 4. Project layout

```
heifinance-rag/
├── knowledge-base/                 source-of-truth PDFs (gitignored — supply your own)
├── backend/                        Spring Boot 4.1.1 · Java 21 · Maven
│   ├── models/                     ONNX embedding model + tokenizer (see §6)
│   ├── scripts/fetch-embedding-model.sh
│   └── src/main/java/com/heifinance/rag/
│       ├── api/                    REST controllers + DTOs + error handling
│       ├── config/                 AI beans, CORS, SPA routing, typed properties
│       ├── domain/                 conversation, message, citation, document models
│       ├── repository/             JdbcTemplate data access
│       └── service/                ingestion · retrieval · chat orchestration · conversations
├── frontend/                       Vite + React + TypeScript SPA
│   └── src/{api,components,hooks,styles,utils}
├── docker-compose.yml              PostgreSQL + pgvector + backend image
└── plan.md                         implementation and design plan
```

## 5. Running locally

### Prerequisites

- Java 21 (`JAVA_HOME` set)
- Maven 3.9+
- Node.js 20+
- PostgreSQL 16 with the `pgvector` extension

### 5.1 PostgreSQL

```bash
sudo -u postgres psql -c "CREATE USER heifinance WITH PASSWORD 'heifinance';"
sudo -u postgres psql -c "CREATE DATABASE heifinance_rag OWNER heifinance;"
sudo -u postgres psql -d heifinance_rag -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

Or simply: `docker compose up -d postgres` (the `pgvector/pgvector:pg16` image already ships the extension).

### 5.2 Embedding model

```bash
cd backend && ./scripts/fetch-embedding-model.sh     # ~90 MB, one time
```

### 5.3 Backend

```bash
cd backend
export OPENAI_API_KEY="<your key>"
export OPENAI_BASE_URL="https://api.openai.com/v1"   # include /v1
mvn spring-boot:run
```

The API is served on `http://localhost:8080/api`. On first start the backend indexes the corpus;
SQL schema is created automatically from `src/main/resources/db/schema.sql`.

> **First-run note.** Spring AI's transformers module pulls DJL, which downloads its CPU native
> runtime (~250 MB) into `~/.djl.ai` the first time the embedding model loads. It is cached
> afterwards and does not affect subsequent starts.

### 5.4 Frontend

```bash
cd frontend
npm install
npm run dev            # http://localhost:5173, proxies /api to :8080
```

For a single-origin deployment (no CORS, no proxy), build the SPA and let Spring Boot serve it:

```bash
cd frontend && npm run build      # emits frontend/dist
```

Spring Boot serves `frontend/dist` automatically (see `heifinance.web.static-locations`), so
`http://localhost:8080` then hosts the whole application. Client-side routes fall back to
`index.html`.

### 5.5 Docker Compose

```bash
export OPENAI_API_KEY="<your key>"
export OPENAI_BASE_URL="https://api.openai.com/v1"
docker compose up --build
```

This builds the frontend inside the image, compiles the backend, downloads the embedding model,
starts PostgreSQL with pgvector, and serves everything on `http://localhost:8080`.

## 6. Configuration

All knobs are environment-overridable (`backend/src/main/resources/application.yml`).

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPENAI_API_KEY` | — | **Required.** Chat model credentials |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible endpoint (include the version segment) |
| `OPENAI_CHAT_MODEL` | `gpt-5-mini` | Answer generation model |
| `OPENAI_MAX_COMPLETION_TOKENS` | `2048` | Output cap |
| `OPENAI_REASONING_EFFORT` | `low` | Reasoning effort (`minimal` / `low` / `medium` / `high`) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | localhost / heifinance / heifinance | PostgreSQL |
| `KNOWLEDGE_BASE_PATH` | `../knowledge-base` | PDF directory |
| `INGEST_ON_STARTUP` | `true` | Build/refresh the index at boot |
| `CHUNK_SIZE` | `200` | Token window per passage |
| `MIN_CHUNK_SIZE_CHARS` | `80` | Minimum passage size |
| `RETRIEVAL_TOP_K` | `12` | Semantic candidates retrieved |
| `RETRIEVAL_SIMILARITY_THRESHOLD` | `0.25` | Cosine similarity floor (semantic leg only) |
| `RETRIEVAL_MAX_CONTEXT_CHUNKS` | `12` | Passages sent to the model |
| `RETRIEVAL_MAX_PER_DOCUMENT` | `6` | Diversity cap per document |
| `HEIFINANCE_ALLOWED_ORIGIN_PATTERNS` | `*` | Origins allowed to call `/api/**` |
| `HISTORY_MAX_TURNS` | `8` | Conversation turns kept as context |
| `SERVER_PORT` | `8080` | Backend port |

## 7. REST API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/chat` | Ask a question → answer + citations |
| `GET` | `/api/conversations` | List conversations |
| `POST` | `/api/conversations` | Create an empty conversation |
| `GET` | `/api/conversations/{id}` | Conversation with full transcript |
| `PATCH` | `/api/conversations/{id}` | Rename a conversation |
| `DELETE` | `/api/conversations/{id}` | Delete a conversation and its transcript |
| `GET` | `/api/knowledge/documents` | Indexed corpus with page/passage counts |
| `POST` | `/api/knowledge/reindex` | Re-ingest (`?force=true` to rebuild) |
| `GET` | `/api/knowledge/search` | Retrieval-only inspection (`q`, `topK`, `threshold`) |
| `GET` | `/api/system/health` | Model, embedding and index status |

### `POST /api/chat`

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"How much bonus interest can I earn on SavingsPlus?"}'
```

```json
{
  "conversationId": "90647d7f-0bfe-42d3-afa8-8d813dc1d033",
  "conversationTitle": "How much bonus interest can I earn on SavingsPlus?",
  "messageId": "…",
  "answer": "You can earn up to 2.00% p.a. in bonus interest … [4]",
  "sources": [
    {
      "index": 4,
      "documentId": "heifinancebankproductfactsheet",
      "fileName": "HeiFinanceBankProductFactSheet.pdf",
      "title": "HeiFinanceBankProductFactSheet",
      "page": 1,
      "score": 0.368,
      "excerpt": "Base Interest: 0.05% p.a. …",
      "cited": true
    }
  ],
  "model": "gpt-5-mini",
  "latencyMs": 8903,
  "createdAt": "2026-10-13T05:11:42Z"
}
```

Errors return a consistent envelope: `{ timestamp, status, error, message, path }`.

## 8. Design

*Quiet-luxury editorial finance* — the visual language of a private bank's internal research note.

- **Palette** — parchment ground `#F7F4EE`, ink `#101B2D`, and a single signature **jade `#0E7C66`**
  used only for what the assistant itself produced (tying to HeiFinance's *"Digital Gateway to
  Asian Prosperity"* positioning).
- **Typography** — *Fraunces* (editorial serif) for the wordmark, headings and hero; *Inter* with
  tabular numerals for data; *JetBrains Mono* for file names, page numbers and citation indices.
- **Signature elements** — the **aperture mark** (concentric open arcs forming a gateway), **ledger
  hairlines** separating turns, and **citation chips** that expand into a full evidence panel with
  the verbatim passage, similarity score and page.
- **Layout** — asymmetric: a 292 px conversation rail, a 760 px reading column, and a sliding
  evidence drawer.
- **Motion** — 180–320 ms on `cubic-bezier(.22,.61,.36,1)`; turns fade-and-rise, citation chips
  pop, the drawer slides. Fully suppressed under `prefers-reduced-motion`.

## 9. Tests

```bash
cd backend && mvn test
```

### Verified end to end

Every question below was run against the live stack and checked against the source PDFs.

| Question | Answer produced | Source |
| --- | --- | --- |
| SavingsPlus bonus interest and tiers | up to 2.00% p.a. — 0.50% salary credit, 0.75% card spend, 0.75% bill payments | Product Fact Sheet p.1 |
| Who is the Head of Consumer Banking & Wealth Management, and their email? | Sarah Wong — `sarah.wong@heifinance.com` | Full Directory p.1 + Org Chart p.1 |
| Annual leave entitlement and accrual | pro-rated under 1 year, then 14 / 16 / 18 days; 7 days carry-forward | Employee Handbook p.6–8 |
| FY2025 total assets and net profit | SGD 155.2 billion total assets; SGD 2.85 billion net profit | Annual Report p.2, p.4 |
| How many country heads, and which markets? | six — Singapore, China, Hong Kong, Malaysia, Thailand, Indonesia | Org Chart p.1 + Directory p.1 |
| Phone number for Kenji Tan | +65 6789-0001 | Full Directory p.1 |
| Out-of-corpus question (cafeteria Wi-Fi password) | declined to answer, named the document that would cover it | — |

## Documentation

| Document | Contents |
| --- | --- |
| [RUN-WITH-DOCKER.md](RUN-WITH-DOCKER.md) | Container workflow: pull, run, verify, back up, replace a source PDF |
| [DEPLOY.md](DEPLOY.md) | Permanent hosting on a server, with automatic HTTPS |
| [TEST-REPORT.md](TEST-REPORT.md) | Retrieval testing, findings and the known limitation |
| [plan.md](plan.md) | Original implementation and design plan |

## License

No license has been specified for this repository. All rights are reserved by the author until one is added.
