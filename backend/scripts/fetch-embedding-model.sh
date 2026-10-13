#!/usr/bin/env bash
# Downloads the local ONNX embedding model used by the HeiFinance RAG backend.
# sentence-transformers/all-MiniLM-L6-v2 — 384 dimensions, ~90 MB.
#
# Usage:  ./scripts/fetch-embedding-model.sh [target-directory]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-${SCRIPT_DIR}/../models}"
BASE_URL="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main"

mkdir -p "${TARGET}"

fetch() {
  local url="$1"
  local destination="$2"
  local label="$3"

  if [[ -s "${destination}" ]]; then
    echo "  present  ${label}"
    return
  fi

  echo "  fetching ${label}"
  curl -fSL --retry 3 --retry-delay 2 -o "${destination}" "${url}"
}

echo "Embedding model -> ${TARGET}"
fetch "${BASE_URL}/onnx/model.onnx"  "${TARGET}/model.onnx"     "model.onnx (~90 MB)"
fetch "${BASE_URL}/tokenizer.json"   "${TARGET}/tokenizer.json" "tokenizer.json"

echo "Done."