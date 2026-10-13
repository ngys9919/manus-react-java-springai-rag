package com.heifinance.rag.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.heifinance.rag.config.RagProperties;
import com.heifinance.rag.domain.KnowledgeDocument;
import com.heifinance.rag.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/**
 * Reads the HeiFinance PDF corpus, splits it into overlapping token windows and upserts the
 * resulting embeddings into PostgreSQL/pgvector.
 *
 * <p>Ingestion is idempotent: a document is skipped when its SHA-256 checksum is unchanged,
 * and stale chunks are purged before a document is re-indexed.</p>
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final VectorStore vectorStore;
    private final KnowledgeDocumentRepository documentRepository;
    private final RagProperties properties;
    private final TokenTextSplitter splitter;

    public KnowledgeIngestionService(VectorStore vectorStore,
                                     KnowledgeDocumentRepository documentRepository,
                                     RagProperties properties) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.properties = properties;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunk().size())
                .withMinChunkSizeChars(properties.chunk().minChunkSizeChars())
                .withMinChunkLengthToEmbed(properties.chunk().minChunkLengthToEmbed())
                .withMaxNumChunks(properties.chunk().maxNumChunks())
                .withKeepSeparator(true)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestOnStartup() {
        if (!properties.ingestOnStartup()) {
            log.info("Startup ingestion is disabled; serving the existing index");
            return;
        }
        try {
            IngestionReport report = ingest(false);
            log.info("Knowledge ingestion finished: scanned={} indexed={} skipped={} failed={} chunks={} in {} ms",
                    report.filesScanned(), report.indexed(), report.skipped(), report.failed(),
                    report.totalChunks(), report.durationMs());
        } catch (RuntimeException ex) {
            log.error("Knowledge ingestion could not complete; answering from the existing index", ex);
        }
    }

    public synchronized IngestionReport ingest(boolean force) {
        long startedAt = System.currentTimeMillis();
        Path root = knowledgeBaseRoot();
        List<Path> pdfs = listPdfs(root);

        int indexed = 0;
        int skipped = 0;
        int failed = 0;
        int totalChunks = 0;

        for (Path pdf : pdfs) {
            try {
                IngestedDocument outcome = ingestDocument(pdf, force);
                if (outcome.skipped()) {
                    skipped++;
                } else {
                    indexed++;
                    totalChunks += outcome.chunks();
                }
            } catch (Exception ex) {
                failed++;
                log.error("Failed to ingest {}", pdf.getFileName(), ex);
                markFailed(pdf);
            }
        }

        return new IngestionReport(pdfs.size(), indexed, skipped, failed, totalChunks,
                System.currentTimeMillis() - startedAt, documentRepository.findAll());
    }

    public Path knowledgeBaseRoot() {
        Path root = Path.of(properties.knowledgeBasePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Knowledge base directory does not exist: " + root);
        }
        return root;
    }

    public List<KnowledgeDocument> listDocuments() {
        return documentRepository.findAll();
    }

    private List<Path> listPdfs(Path root) {
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to list the knowledge base directory " + root, ex);
        }
    }

    private IngestedDocument ingestDocument(Path pdf, boolean force) throws IOException {
        String fileName = pdf.getFileName().toString();
        String documentId = documentId(fileName);
        String title = titleFrom(fileName);
        byte[] bytes = Files.readAllBytes(pdf);
        String checksum = sha256(bytes);

        Optional<KnowledgeDocument> existing = documentRepository.findById(documentId);
        if (!force && existing.isPresent()
                && checksum.equals(existing.get().checksum())
                && KnowledgeDocument.STATUS_INDEXED.equals(existing.get().status())
                && existing.get().chunkCount() > 0) {
            log.info("Unchanged since last index, skipping {}", fileName);
            return new IngestedDocument(true, existing.get().chunkCount(), existing.get().pageCount());
        }

        vectorStore.delete(new FilterExpressionBuilder().eq("documentId", documentId).build());

        List<Document> pages = new PagePdfDocumentReader(new FileSystemResource(pdf), readerConfig()).read();
        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Document page : pages) {
            int pageNumber = pageNumber(page);
            for (Document chunk : splitter.split(page)) {
                String text = chunk.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                chunks.add(Document.builder()
                        .text(text)
                        .metadata(Map.of(
                                "documentId", documentId,
                                "fileName", fileName,
                                "title", title,
                                "page", pageNumber,
                                "chunkIndex", chunkIndex++))
                        .build());
            }
        }

        if (!chunks.isEmpty()) {
            vectorStore.add(chunks);
        }

        documentRepository.upsert(new KnowledgeDocument(documentId, fileName, title, pages.size(), chunks.size(),
                bytes.length, checksum, KnowledgeDocument.STATUS_INDEXED, Instant.now()));

        log.info("Indexed {} — {} pages, {} chunks", fileName, pages.size(), chunks.size());
        return new IngestedDocument(false, chunks.size(), pages.size());
    }

    private PdfDocumentReaderConfig readerConfig() {
        return PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .withPageTopMargin(0)
                .withPageBottomMargin(0)
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withLeftAlignment(true)
                        .withNumberOfTopTextLinesToDelete(0)
                        .withNumberOfBottomTextLinesToDelete(0)
                        .build())
                .build();
    }

    private void markFailed(Path pdf) {
        String documentId = documentId(pdf.getFileName().toString());
        documentRepository.findById(documentId)
                .map(document -> document.withStatus(KnowledgeDocument.STATUS_FAILED))
                .ifPresent(documentRepository::upsert);
    }

    private static int pageNumber(Document page) {
        Object value = page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    static String documentId(String fileName) {
        return fileName.replaceAll("(?i)\\.pdf$", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    static String titleFrom(String fileName) {
        String base = fileName.replaceAll("(?i)\\.pdf$", "").replace('_', ' ').replaceAll("\\s+", " ").trim();
        return base.isEmpty() ? fileName : base;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record IngestedDocument(boolean skipped, int chunks, int pages) {
    }
}
