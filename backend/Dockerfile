# =============================================================================
# HeiFinance Knowledge Assistant
# Multi-stage build: frontend bundle -> backend jar -> slim runtime image
# Build context is the repository root (see docker-compose.yml).
# =============================================================================

# ---------------------------------------------------------------- frontend ---
FROM node:22-bookworm-slim AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ----------------------------------------------------------------- backend ---
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /src
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q -DskipTests package

# -------------------------------------------------------------- embedding ----
FROM eclipse-temurin:21-jdk AS embedding-model
WORKDIR /models
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL -o model.onnx \
      "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx" \
    && curl -fsSL -o tokenizer.json \
      "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json"

# ------------------------------------------------------------------ runtime --
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=backend /src/target/*.jar /app/app.jar
COPY --from=embedding-model /models/ /app/models/
# The corpus is baked into the image, so a pulled image is self-contained and
# needs no extra files. Building from a fresh clone does require the PDFs to be
# present in knowledge-base/ locally — they are not published in the repository.
COPY knowledge-base/ /app/knowledge-base/

# The built SPA is served by Spring Boot from /app/spa (heifinance.web.static-locations).
COPY --from=frontend /frontend/dist/ /app/spa/

ENV SERVER_PORT=8080 \
    KNOWLEDGE_BASE_PATH=/app/knowledge-base \
    EMBEDDING_MODEL_URI=file:/app/models/model.onnx \
    EMBEDDING_TOKENIZER_URI=file:/app/models/tokenizer.json \
    SPA_LOCATIONS=file:/app/spa/ \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
