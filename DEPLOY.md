# Deploying the HeiFinance Knowledge Assistant as a permanent website

The sandbox preview at `*.manus.computer` is temporary — it hibernates and its URL
changes. This guide puts the assistant on a machine that stays up, behind HTTPS, on
a stable address.

## Why not Manus WebDev

WebDev hosts Vite/React sites with an optional Node backend. This application needs:

| Requirement | WebDev |
| --- | --- |
| Java 21 / Spring Boot runtime | Node only |
| PostgreSQL 16 **with pgvector** | managed MySQL |
| A ~90 MB ONNX model on disk | no persistent model storage |
| Docker Compose | not available |

These are hard platform limits, not preferences, so the deployment target is a
**Cloud Computer** — a persistent Ubuntu VM with root, Docker and a fixed IP.

## Cost

| Plan | Price | Memory | Verdict |
| --- | --- | --- | --- |
| Basic | $10/month | 1 GB | Borderline — the JVM, PostgreSQL and Caddy must share 1 GB, so expect memory pressure and possible OOM restarts |
| **Standard** | **$30/month** | **4 GB** | **Recommended — the realistic minimum for this stack** |
| Advanced | $50/month | 8 GB | Only for heavy traffic |

Traffic beyond the plan's allowance is $0.15/GB, and the machine is **shut down
automatically** when the allowance is exhausted unless auto top-up is enabled.
Enable it at `https://manus.im/app#settings/usage/computer-<device_id>/manage-budget`.

There is no free option that satisfies the runtime requirements above. If the
assistant only ever needs to run on one machine on the internal network, a
self-hosted Linux server you already own also works — the steps below are the same.

---

## Step 0 — Prerequisites

1. **A Cloud Computer.** Create one at
   [Settings → My Computer → Create](https://manus.im/app#settings/my-computer/create).
   If you already own one, attach it with the computer icon below the chat input
   instead of buying another. Choose **Southeast Asia** for the closest latency.
2. **An OpenAI-compatible API key.** The server needs its own credential; the
   sandbox key is not transferable. Any endpoint that speaks the OpenAI chat
   completions API works.
3. **A domain** (optional but recommended). HTTPS needs one. Without it, the site
   runs on plain HTTP at the server's IP. Buy through
   [Settings → Purchased Domains](https://manus.im/app#settings/data-controls/purchased-domains).

---

## Step 1 — Prepare the machine

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2 git
sudo systemctl enable --now docker
sudo usermod -aG docker ubuntu      # log out and back in for this to apply

# Only SSH, HTTP and HTTPS are reachable. Port 8080 and 5432 stay closed.
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status
```

## Step 2 — Get the code onto the server

```bash
sudo mkdir -p /opt/heifinance-rag && sudo chown ubuntu:ubuntu /opt/heifinance-rag
git clone https://github.com/ngys9919/manus-react-java-springai-rag.git /opt/heifinance-rag
```

The five source PDFs are **deliberately not published in the repository**, and the
build needs them — so copy them in before Step 5:

```bash
cp /path/to/HeiFinance*.pdf /opt/heifinance-rag/knowledge-base/
```

If you would rather not build at all, pull the prebuilt image instead
(`ngys9919/heifinance-rag:1.0.0`); it already contains the corpus, so no PDFs are
needed on the server.

## Step 3 — Configure secrets

```bash
cd /opt/heifinance-rag
cp .env.example .env
chmod 600 .env
nano .env
```

Fill in at minimum:

```ini
OPENAI_API_KEY=sk-...                          # required
OPENAI_BASE_URL=https://api.openai.com/v1      # or your gateway
OPENAI_CHAT_MODEL=gpt-5-mini
DB_PASSWORD=<a long random password>           # not the default
SITE_ADDRESS=support.heifinance.example        # your domain, or :80 for HTTP only
```

## Step 4 — Point DNS at the machine

In your DNS provider, add an **A record** for `SITE_ADDRESS` pointing to the
Cloud Computer's public IP (shown on its settings page). Wait for it to resolve:

```bash
dig +short support.heifinance.example
```

Caddy will not be able to issue a certificate until this resolves correctly.

## Step 5 — Launch

```bash
cd /opt/heifinance-rag
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

The first build takes several minutes: it compiles the frontend, packages the
Spring Boot jar, and downloads the embedding model into the image. The first boot
then ingests the five PDFs, which takes another minute or two.

Confirm the assistant is healthy:

```bash
curl -fsS https://support.heifinance.example/api/system/health | python3 -m json.tool
```

Expect `"status": "UP"`, `"indexedDocuments": 5` and a non-zero `indexedChunks`.

## Step 6 — Survive reboots

Tier upgrades, backup restores and manual restarts all reboot the machine. Without
this unit the stack stays down and the site looks broken.

```bash
sudo cp deploy/heifinance-rag.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now heifinance-rag
systemctl status heifinance-rag
```

Test it properly:

```bash
sudo reboot
# reconnect, then:
curl -fsS https://support.heifinance.example/api/system/health
```

---

## Operating the site

**Update to a new version**

```bash
cd /opt/heifinance-rag
git pull
sudo systemctl restart heifinance-rag
```

**Replace a source PDF.** The corpus is baked into the image, so a change needs a
rebuild:

```bash
cp /path/to/new.pdf /opt/heifinance-rag/knowledge-base/
docker compose -f docker-compose.prod.yml up -d --build
```

Ingestion skips documents whose content hash is unchanged. If you would rather
update PDFs without rebuilding, uncomment the `volumes:` block in
`docker-compose.prod.yml` and restart — but the folder must then hold all five
PDFs, because an empty folder would shadow the bundled corpus.

**Force a full re-index** (after changing chunking or the embedding model):

```bash
curl -X POST "https://support.heifinance.example/api/knowledge/reindex?force=true"
```

**Back up the database** — conversations and vectors live in a Docker volume:

```bash
cd /opt/heifinance-rag
docker compose -f docker-compose.prod.yml exec postgres \
  pg_dump -U heifinance heifinance_rag > ~/heifinance-$(date +%F).sql
```

The Cloud Computer also takes daily full-disk snapshots (10 retained) at
`https://manus.im/app#settings/my-computer/cloud-<device_id>/backups`.

**Logs**

```bash
cd /opt/heifinance-rag
docker compose -f docker-compose.prod.yml logs --tail 200 backend
docker compose -f docker-compose.prod.yml logs --tail 200 caddy
```

---

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Site unreachable | UFW is blocking 80/443 — `sudo ufw status`. Or DNS has not propagated. |
| Certificate not issued | `SITE_ADDRESS` does not resolve to this machine, or port 80 is closed. Check `docker compose logs caddy`. |
| 502 from Caddy | The backend is still starting or crashed — check its logs. First boot waits for ingestion. |
| `Request failed with status 403` in the browser | The request's `Origin` was rejected. Set `HEIFINANCE_ALLOWED_ORIGIN_PATTERNS` to include your site origin (or `*`) and restart. |
| Container killed, "out of memory" | The machine is too small for the stack. Upgrade the tier, or lower `JAVA_TOOL_OPTIONS` in `docker-compose.prod.yml`. |
| Site down after a while | The outbound traffic allowance was exhausted and the machine was shut down. Enable auto top-up. |
| Answers say the information is missing | Confirm ingestion completed and the document list is right: `curl https://<site>/api/knowledge/documents`. |

## What is deliberately not exposed

- PostgreSQL has **no host port**; it is reachable only on the internal Compose network.
- The backend's `8080` is **not published**; only Caddy talks to it.
- There is no authentication in front of the assistant. It is intended for the
  internal network. If it must be reachable from the public internet, put an
  identity-aware proxy or VPN in front of it — the app itself has no login.
