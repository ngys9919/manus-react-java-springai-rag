#!/usr/bin/env bash
# =============================================================================
# Build the HeiFinance Knowledge Assistant image and push it to Docker Hub.
#
#   ./scripts/build-and-push.sh <dockerhub-user> [tag]
#
# Example:
#   ./scripts/build-and-push.sh acme 1.0.0
#
# Run `docker login` first (an access token is safer than your password:
# Docker Hub -> Account settings -> Personal access tokens).
#
# NOTE ON NETWORKS: some sandboxes and CI runners cannot set up Docker's bridge
# network. If the build fails while fetching dependencies, add --network=host
# (already used below via BUILD_NETWORK).
# =============================================================================
set -euo pipefail

USER_NAME="${1:-}"
TAG="${2:-1.0.0}"

if [[ -z "${USER_NAME}" ]]; then
  echo "usage: $0 <dockerhub-user> [tag]" >&2
  exit 2
fi

REPO="${USER_NAME}/heifinance-rag"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILD_NETWORK="${BUILD_NETWORK:-host}"

cd "${ROOT}"

echo "==> Building ${REPO}:${TAG} (network: ${BUILD_NETWORK})"
docker build \
  --network="${BUILD_NETWORK}" \
  -f backend/Dockerfile \
  -t "${REPO}:${TAG}" \
  -t "${REPO}:latest" \
  .

echo "==> Image size"
docker image ls "${REPO}" --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}'

echo "==> Pushing ${REPO}:${TAG} and :latest"
docker push "${REPO}:${TAG}"
docker push "${REPO}:latest"

cat <<EOF

Done. On the target machine:

  docker pull ${REPO}:${TAG}

  cp .env.example .env      # fill in OPENAI_API_KEY and DB_PASSWORD
  IMAGE=${REPO}:${TAG} docker compose -f docker-compose.local.yml up -d
  # then open http://localhost:8080
EOF