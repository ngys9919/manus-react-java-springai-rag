# HeiFinance Knowledge Assistant — Implementation & Design Plan

## 1. Product

An internal, conversational **Retrieval-Augmented Generation (RAG)** assistant for HeiFinance
staff. It answers internal support questions **strictly from five authoritative PDFs** and shows
the exact passages it used as citations.

Source-of-truth corpus (immutable for the assistant's purpose):

1. `HeiFinance_Annual_Report_2025.pdf`
2. `HeiFinance Bank Product Fact Sheet.pdf`
3. `HeiFinance_Employee_Handbook.pdf`
4. `HeiFinance_Organization_Chart_Executive_Summary.pdf`
5. `HeiFinance_Full_Directory_Complete.pdf`

## 2. Stack (as requested)

| Layer | Technology |
| --- | --- |
| Frontend | Vite + React (TypeScript) |
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| AI | Spring AI 2.0.1 (ChatClient, EmbeddingModel, VectorStore, DocumentReader) |
| Vector store | PostgreSQL 16 + `pgvector` (HNSW, cosine) |
| Relational data | PostgreSQL 16 (conversations, messages, knowledge registry) |
| API contract | RESTful JSON under `/api` |

### Embedding decision (important)

The configured LLM endpoint exposes **chat models only — there is no embeddings endpoint**.
The backend therefore runs a **local ONNX embedding model** (`sentence-transformers/all-MiniLM-L6-v2`,
384 dimensions) through Spring AI's Transformers integration. Benefits: no second vendor, no
per-token embedding cost, deterministic, and it works fully offline once the model files exist.
Model files live in `backend/models/` and are fetched by `backend/scripts/fetch-embedding-model.sh`.

## 3. Architecture

```
Vite + React SPA  ──REST/JSON──▶  Spring Boot 4.1.1 (Java 21)
                                        │
                        ┌───────────────┼────────────────┐
                        ▼               ▼                ▼
                 Spring AI ChatClient  EmbeddingModel   PgVectorStore
                 (OpenAI-compatible)   (local ONNX)     (pgvector/HNSW)
                        │                                │
                        └──────────▶ PostgreSQL 16 ◀─────┘
                              conversations · chat_messages · knowledge_documents
```

### Ingestion pipeline (startup + on demand)

`PagePdfDocumentReader` → page-level `Document`s (file name + page metadata) → `TokenTextSplitter`
(≈800-token chunks, 150 overlap) → metadata enrichment (`documentId`, `title`, `fileName`, `page`)
→ checksum comparison against `knowledge_documents` → delete stale chunks → embed → upsert into
pgvector. Idempotent: unchanged files are skipped, `POST /api/knowledge/reindex` forces a rebuild.

### Query pipeline

1. Embed the question with the same ONNX model.
2. `vectorStore.similaritySearch(topK=8, similarityThreshold=0.25)`.
3. Re-rank/deduplicate, keep the best `maxContextChunks`.
4. Build a grounded prompt: numbered context blocks `[n] <file> (page p)` + last 8 turns of history.
5. `ChatClient` call with a strict system prompt (answer only from context, cite `[n]`, refuse
   gracefully when the corpus does not contain the answer).
6. Persist the user turn and the assistant turn (with the exact source list) in PostgreSQL.

### Backend module layout

```
com.heifinance.rag
├── HeiFinanceRagApplication      entry point
├── config/                       AI beans, CORS, SPA forwarding, typed properties
├── api/                          REST controllers + request/response DTO records
├── service/                      ingestion, retrieval, chat orchestration, conversations
├── repository/                   JdbcTemplate data access
└── domain/                       conversation, message, knowledge-document models
```

## 4. REST contract

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/chat` | Ask a question; returns answer + citations |
| `GET` | `/api/conversations` | List conversations |
| `POST` | `/api/conversations` | Create an empty conversation |
| `GET` | `/api/conversations/{id}` | Conversation with full transcript |
| `PATCH` | `/api/conversations/{id}` | Rename |
| `DELETE` | `/api/conversations/{id}` | Delete conversation and transcript |
| `GET` | `/api/knowledge/documents` | Indexed corpus with page/chunk counts |
| `POST` | `/api/knowledge/reindex` | Force re-ingestion |
| `GET` | `/api/knowledge/search` | Retrieval-only inspection (transparency) |
| `GET` | `/api/system/health` | Model, embedding and vector-store status |

## 5. Design

- **Design movement** — *Quiet-luxury editorial finance*: the visual language of a private bank's
  internal research note, not a generic chat app. Editorial serif headlines, hairline ledger rules,
  parchment ground, restrained motion.
- **Core principles** — (1) Evidence first: every answer is visibly anchored to a citation.
  (2) Calm density: generous whitespace, one idea per band, no chrome. (3) Asymmetry: a left
  rail and an offset reading column instead of a centred widget. (4) Craft in small details:
  hairlines, tabular numerals, precise type scale.
- **Colour philosophy** — Parchment ivory ground (`#F7F4EE`) reads as archival paper and lowers
  glare for long reading. Deep ink (`#101B2D`) carries authority. A single **signature jade**
  (`#0E7C66`) marks everything the assistant itself produced — the "gateway" colour, tying to
  HeiFinance's *"Digital Gateway to Asian Prosperity"* positioning. Amber is reserved for
  retrieval-in-progress signals.
- **Layout paradigm** — Two-zone asymmetric split: a fixed 288 px conversation rail (brand
  wordmark, corpus status, session history) and a wide reading column capped at 760 px for
  comfortable measure, with a right-hand evidence drawer that slides in when a citation is opened.
- **Signature elements** — (1) The **aperture mark**: concentric arcs forming a gateway. (2)
  **Ledger hairlines** separating turns and sections. (3) **Citation chips** that expand into
  full source cards showing file, page, similarity and the verbatim excerpt.
- **Interaction philosophy** — The interface behaves like a well-briefed analyst: it states what
  it is doing ("searching the corpus"), never pretends to stream, and always shows its sources.
  Empty state offers real questions staff actually ask.
- **Animation** — 180–320 ms, `cubic-bezier(.22,.61,.36,1)`. Turn entries fade-and-rise 8 px with
  40 ms stagger; citation chips scale from 0.94 with a soft ring; the evidence drawer slides on
  transform only. Retrieval shows a three-stage progress label rather than a spinner.
- **Typography** — Display: *Fraunces* (variable serif, editorial authority) for the wordmark,
  page titles and answer headings. Text: *Inter* with `font-variant-numeric: tabular-nums` for
  data, citations and timestamps. Mono: *JetBrains Mono* for file names and identifiers.
- **Brand essence** — *The internal knowledge desk of HeiFinance* — for staff who need an
  authoritative answer in seconds; different because every claim it makes is traceable to a page.
  Personality: *assured, precise, discreet*.
- **Brand voice** — Composed, declarative, never chatty. Examples: "Answers traced to the page."
  and "Ask the corpus. Nothing else is consulted."
- **Wordmark & logo** — Lowercase-tight "HeiFinance" in Fraunces with a jade aperture glyph: three
  concentric open arcs rotated to imply a gateway/diaphragm.
- **Signature brand colour** — **HeiFinance Jade `#0E7C66`**.

## 6. Local environment

- PostgreSQL 16 on `localhost:5432`, database `heifinance_rag`, extensions `vector`, `uuid-ossp`.
- Backend on `8080`; Vite dev server on `5173` proxying `/api` to the backend.
- For a single-origin demo, Spring Boot also serves the built SPA from `frontend/dist`.
- `docker-compose.yml` reproduces the whole stack (Postgres + pgvector + backend image).
