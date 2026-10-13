# Running the HeiFinance Knowledge Assistant from a container

The whole application — Spring Boot backend, the built React SPA, the ONNX embedding
model and the five source PDFs — is packaged into a single image. Run it next to a
PostgreSQL/pgvector container and you have the assistant on `http://localhost:8080`,
with no Java, Maven or Node installed on the host.

The **PDFs are deliberately not published in the Git repository**, but they *are*
baked into the image, so a pulled image runs with no extra files. See
[knowledge-base/README.md](knowledge-base/README.md) for why.

For permanent server hosting instead, see [DEPLOY.md](DEPLOY.md).

## What's in the image

| Layer | Contents |
| --- | --- |
| Frontend | Vite build output, served by Spring Boot from `/app/spa` |
| Backend | Spring Boot fat jar (Java 21, Spring AI 2.0.1) |
| Model | `all-MiniLM-L6-v2` ONNX + tokenizer, baked in at build time |
| Corpus | The five HeiFinance PDFs, baked in from `knowledge-base/` |

The embedding model is inside the image, so the container needs **no** model
download at runtime. It does need outbound internet to reach the chat model API.

---

## Option 1 — Pull the published image

**Prerequisites:** Docker with Compose v2, and an OpenAI-compatible API key.

```bash
# 1. Get the project files that sit alongside the image
#    (docker-compose.local.yml, .env.example, docker/initdb/)
git clone https://github.com/ngys9919/manus-react-java-springai-rag.git heifinance-rag
cd heifinance-rag

# 2. Pull the image
docker pull ngys9919/heifinance-rag:1.0.0

# 3. Configure secrets
cp .env.example .env
nano .env            # set OPENAI_API_KEY and DB_PASSWORD

# 4. Start
IMAGE=ngys9919/heifinance-rag:1.0.0 \
  docker compose -f docker-compose.local.yml up -d

# 5. Watch it come up (first boot ingests the PDFs, ~1-2 min)
docker compose -f docker-compose.local.yml logs -f backend
```

Open **http://localhost:8080**.

## Option 2 — Build the image yourself

Building requires the PDFs to be present locally, because the repository does not
carry them:

```bash
cp /path/to/HeiFinance*.pdf knowledge-base/     # the five source PDFs
docker build --network=host -f backend/Dockerfile -t heifinance-rag:1.0.0 .
```

`--network=host` avoids a class of failures in sandboxes and CI runners that cannot
set up Docker's bridge network. It is harmless on a normal machine and can be
omitted there.

To build and push in one step:

```bash
docker login                                   # an access token is safer than a password
./scripts/build-and-push.sh ngys9919 1.0.0
```

---

## Verify it is healthy

```bash
curl -fsS http://localhost:8080/api/system/health | python3 -m json.tool
```

Expect `"status": "UP"`, `"indexedDocuments": 5` and a non-zero `indexedChunks`.
On Windows PowerShell use `curl.exe`, or just open the URL in a browser.

Then ask a question in the UI, or from the command line:

```bash
curl -s -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"How much annual leave am I entitled to, and how does it accrue?"}' \
  | python3 -m json.tool
```

## Day-to-day operation

```bash
# Stop / start
docker compose -f docker-compose.local.yml down
docker compose -f docker-compose.local.yml up -d

# Logs
docker compose -f docker-compose.local.yml logs --tail 200 backend

# Back up conversations and vectors
docker compose -f docker-compose.local.yml exec postgres \
  pg_dump -U heifinance heifinance_rag > heifinance-backup.sql

# Restore
cat heifinance-backup.sql | docker compose -f docker-compose.local.yml \
  exec -T postgres psql -U heifinance -d heifinance_rag
```

## Replace a source PDF

The corpus lives inside the image, so there are two ways to change it.

**Rebuild** (simplest, and what you need for a published image):

```bash
cp new-handbook.pdf knowledge-base/
docker build --network=host -f backend/Dockerfile -t heifinance-rag:1.0.0 .
docker compose -f docker-compose.local.yml up -d --force-recreate backend
```

**Or mount the folder** so a restart is enough — uncomment the `volumes:` block in
`docker-compose.local.yml` first, and make sure the folder holds all five PDFs,
since an empty folder would shadow the bundled corpus:

```bash
cp new-handbook.pdf knowledge-base/
docker compose -f docker-compose.local.yml restart backend
```

Either way, ingestion skips documents whose content hash is unchanged. To rebuild
everything — for example after changing chunking or the embedding model:

```bash
curl -X POST "http://localhost:8080/api/knowledge/reindex?force=true"
```

## Share it with colleagues on your network

The stack publishes port 8080 on the host. Colleagues reach it at
`http://<your-machine-ip>:8080`, which is plain HTTP and has **no login** — the
assistant is designed for a trusted internal network. Do not expose it directly to
the public internet without an authenticating proxy or VPN in front of it.

To bind only to localhost instead, change the port mapping in
`docker-compose.local.yml` to `"127.0.0.1:${SERVER_PORT:-8080}:8080"`.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `set OPENAI_API_KEY in .env` on startup | `.env` is missing or unset. Copy `.env.example` and fill it in. |
| Container restarts repeatedly, OOM | Give Docker Desktop more memory (Settings → Resources), or lower `JAVA_TOOL_OPTIONS` to `-XX:MaxRAMPercentage=50`. |
| `http://localhost:8080` refuses to connect | The backend is still starting or failed — check its logs. First boot waits for ingestion. |
| `Request failed with status 403` in the browser | The request's `Origin` was rejected. Set `HEIFINANCE_ALLOWED_ORIGIN_PATTERNS` to include your origin (or `*`) and restart. |
| `indexedDocuments: 0` and every question refused | The image was built from a clone without the PDFs, or a mounted `knowledge-base/` folder is empty and shadowing the bundled corpus. |
| Answers say the information is missing | Confirm ingestion finished and the corpus is right: `curl http://localhost:8080/api/knowledge/documents`. |
| Build fails fetching dependencies | Re-run with `--network=host`. |
| Build fails with `COPY knowledge-base/` | The folder is missing the PDFs locally. Copy the five PDFs in before building. |
| Port 8080 already in use | Set `SERVER_PORT=8090` in `.env`; the mapping follows it. |

## Notes and known limitations

- **Image size** is roughly 1.2 GB: a JRE, the Spring Boot jar with all its
  dependencies, the ~90 MB ONNX model, and the corpus.
- **The database is a Docker volume** (`heifinance-pgdata`). Removing the volume
  discards all conversations and vectors; they are rebuilt from the PDFs on the
  next start, but the chat history is not.
- **Conversations are unauthenticated and shared.** Everyone using one instance
  sees the same conversation list.
- **The published image contains the corpus.** Anyone who can pull it can extract
  the PDFs. The image is on Docker Hub and currently public — make the repository
  private there if that matters.
- **One enumeration question is imperfect.** Asking for net profit, total income,
  operating expenses and headcount in a single prompt can miss the headcount
  figure, because that table row is line-split in the PDF and the local embedding
  model is weak on numeric tables. Asking for headcount on its own works. See
  [TEST-REPORT.md](TEST-REPORT.md) for the diagnosis and the recommended fix
  (a stronger embedding model).