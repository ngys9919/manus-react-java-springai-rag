#!/usr/bin/env bash
# Starts the HeiFinance Knowledge Assistant locally:
#   - PostgreSQL must already be running with the heifinance_rag database
#   - backend on :8080 (Spring Boot, serving the built SPA)
#   - frontend dev server on :5173 (Vite, proxying /api to :8080)
#
# Usage:  ./scripts/run-local.sh [backend|frontend|both]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-both}"

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "OPENAI_API_KEY is not set. Export it before starting the backend." >&2
  exit 1
fi

export OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.openai.com/v1}"

start_backend() {
  echo "==> Fetching embedding model if needed"
  "${ROOT}/backend/scripts/fetch-embedding-model.sh"

  echo "==> Building the frontend so Spring Boot can serve it"
  (cd "${ROOT}/frontend" && npm install --no-audit --no-fund && npm run build)

  echo "==> Starting Spring Boot on :8080"
  (cd "${ROOT}/backend" && mvn spring-boot:run)
}

start_frontend() {
  echo "==> Starting Vite dev server on :5173"
  (cd "${ROOT}/frontend" && npm install --no-audit --no-fund && npm run dev)
}

case "${MODE}" in
  backend) start_backend ;;
  frontend) start_frontend ;;
  both)
    start_backend &
    BACKEND_PID=$!
    sleep 8
    start_frontend
    wait "${BACKEND_PID}"
    ;;
  *)
    echo "Usage: $0 [backend|frontend|both]" >&2
    exit 1
    ;;
esac