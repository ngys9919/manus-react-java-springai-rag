# HeiFinance Knowledge Assistant — server notes

Context for any future Manus Task that connects to this machine.

## What runs here

A retrieval-augmented question-answering service for HeiFinance staff. It answers
only from five source-of-truth PDFs and cites the page it used.

- **Stack:** Java 21 / Spring Boot 4.1.1 / Spring AI 2.0.1, Vite + React SPA,
  PostgreSQL 16 with pgvector, Caddy for TLS.
- **Location:** `/opt/heifinance-rag`
- **Compose file:** `docker-compose.prod.yml` (the dev file `docker-compose.yml`
  is not used here)
- **Entry point:** `https://<SITE_ADDRESS>` → Caddy → `backend:8080` → `postgres:5432`

## Services

Managed by the systemd unit `heifinance-rag`, which runs `docker compose up -d`
and starts on boot. Three containers: `caddy`, `backend`, `postgres`.

```bash
sudo systemctl status heifinance-rag
sudo systemctl restart heifinance-rag          # apply a config change
cd /opt/heifinance-rag && docker compose -f docker-compose.prod.yml ps
cd /opt/heifinance-rag && docker compose -f docker-compose.prod.yml logs -f backend
```

## Configuration

Secrets live in `/opt/heifinance-rag/.env` (mode 600, never committed):

- `OPENAI_API_KEY` — required; the chat model credential
- `OPENAI_BASE_URL`, `OPENAI_CHAT_MODEL`
- `DB_PASSWORD` — PostgreSQL password
- `SITE_ADDRESS` — the public hostname Caddy serves and issues TLS for
- `HEIFINANCE_ALLOWED_ORIGIN_PATTERNS` — defaults to `*`

Restart the stack after changing `.env`.

## Knowledge base

PDFs live in `/opt/heifinance-rag/knowledge-base/`. Ingestion runs on startup
and skips documents whose content hash is unchanged, so adding or replacing a
PDF and restarting is enough. Force a full rebuild with:

```bash
curl -X POST "https://<SITE_ADDRESS>/api/knowledge/reindex?force=true"
```

The embedding model (all-MiniLM-L6-v2, 384 dimensions) is baked into the backend
image at build time — there is no model download at runtime.

## Data and backups

- Conversations and vectors live in the `heifinance-pgdata` Docker volume.
- Dump the database before any destructive change:
  `docker compose -f docker-compose.prod.yml exec postgres pg_dump -U heifinance heifinance_rag > backup.sql`
- The machine also has daily full-disk snapshots; see the Cloud Computer settings page.

## Networking

UFW allows 22 (SSH), 80 and 443 only. The application port 8080 and the database
port 5432 are deliberately not published to the host.

## Known limitation

A four-metric enumeration question (all of net profit, total income, operating
expenses and headcount in one prompt) can miss the headcount figure, because the
headcount table row is line-split in the PDF and the local embedding model is
weak on numeric tables. Asking for headcount on its own works correctly. See
`TEST-REPORT.md` for the diagnosis and the recommended fix (a stronger embedding
model).