package com.heifinance.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.heifinance.rag.config.RagProperties;
import com.heifinance.rag.domain.RetrievedChunk;
import com.heifinance.rag.repository.KeywordSearchRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Hybrid retrieval over the indexed HeiFinance corpus.
 *
 * <p>Two legs run for every question and are fused with Reciprocal Rank Fusion:
 *
 * <ol>
 *   <li><b>Semantic</b> — pgvector cosine similarity, which handles paraphrase and intent.</li>
 *   <li><b>Lexical</b> — PostgreSQL full-text and exact-phrase matching, which handles the precise
 *       lookups an internal desk deals in: names, phone numbers, policy phrases and product terms.</li>
 * </ol>
 *
 * <p>The lexical leg carries twice the weight because a passage that literally contains the
 * question's own wording is a stronger signal than a merely nearby embedding. Fusion means a
 * passage found by both legs rises to the top, while either leg alone can still surface a passage
 * the other missed.
 */
@Service
public class RetrievalService {

    private static final int RRF_K = 60;
    private static final double SEMANTIC_WEIGHT = 1.0;
    private static final double PHRASE_WEIGHT = 2.0;
    private static final double TERM_WEIGHT = 0.8;
    private static final double SUB_SEMANTIC_WEIGHT = 0.9;
    private static final double SUB_PHRASE_WEIGHT = 1.2;
    private static final double SUB_TERM_WEIGHT = 0.5;
    private static final double YEAR_SEMANTIC_WEIGHT = 1.0;
    private static final int MAX_SUB_QUESTIONS = 3;
    private static final int MAX_YEARS = 3;
    private static final int MAX_YEAR_PROBES = 4;
    /**
     * Two-word phrases are tried last, and only when nothing longer matched. They matter because
     * table row labels survive PDF extraction split across lines — "Total" and "Employees" end up in
     * different lines of the same block — so a metric name is often only recoverable as a pair.
     */
    private static final int MIN_PHRASE_WORDS = 2;
    private static final int MAX_PHRASE_WORDS = 5;
    private static final int MAX_PHRASES = 16;
    private static final int MAX_TERMS = 24;

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "was", "were", "with", "what", "who", "whom", "how", "when",
            "where", "which", "does", "did", "can", "could", "should", "would", "you", "your",
            "our", "their", "them", "they", "this", "that", "these", "those", "from", "have",
            "has", "had", "will", "shall", "may", "might", "any", "all", "its", "into", "out",
            "about", "there", "here", "please", "tell", "give", "much", "many", "between",
            "within", "under", "over", "per", "not", "but", "his", "her", "him", "she", "he",
            "it", "is", "of", "in", "to", "on", "at", "by", "or", "as", "be", "do", "if", "no",
            "my", "me", "am", "than", "then", "also", "each", "some", "such", "more", "most",
            "other", "only", "own", "same", "too", "very", "just", "get", "got", "both", "every",
            "either", "neither");

    private final VectorStore vectorStore;
    private final KeywordSearchRepository keywordSearchRepository;
    private final RagProperties properties;

    public RetrievalService(VectorStore vectorStore,
                            KeywordSearchRepository keywordSearchRepository,
                            RagProperties properties) {
        this.vectorStore = vectorStore;
        this.keywordSearchRepository = keywordSearchRepository;
        this.properties = properties;
    }

    public List<RetrievedChunk> retrieve(String query, Integer topK, Double similarityThreshold) {
        int effectiveTopK = (topK == null || topK <= 0) ? properties.retrieval().topK() : topK;
        double effectiveThreshold = similarityThreshold == null
                ? properties.retrieval().similarityThreshold()
                : similarityThreshold;

        // A question that asks two or three different things needs a passage for each of them. A
        // single ranked list tends to fill up with the strongest topic and starve the others, so
        // every sub-question contributes its own legs to the fusion.
        List<String> probes = new ArrayList<>();
        probes.add(query);
        probes.addAll(subQuestions(query));

        int limit = Math.max(effectiveTopK, properties.retrieval().topK());
        List<RankedLeg> legs = new ArrayList<>();

        // Legs are added in descending order of precision, because the window is filled by walking
        // the legs in order. Semantic results for every part of the question come first, then exact
        // phrase matches, and the loose "shares some words" leg comes last where it cannot crowd out
        // anything better.
        for (int index = 0; index < probes.size(); index++) {
            legs.add(new RankedLeg(
                    semanticSearch(probes.get(index), effectiveTopK, effectiveThreshold),
                    index == 0 ? SEMANTIC_WEIGHT : SUB_SEMANTIC_WEIGHT));
        }

        // A reporting-period question is scoped by its years, and the years are the most
        // discriminative tokens in it: the same metric appears for several periods across the
        // corpus. Re-probing each part with the years appended reliably separates the reporting
        // page from pages that merely discuss the same subject.
        String years = yearsIn(query);
        if (years != null) {
            for (String probe : probes) {
                if (probes.indexOf(probe) >= MAX_YEAR_PROBES) {
                    break;
                }
                legs.add(new RankedLeg(
                        semanticSearch(compactProbe(probe) + ' ' + years, effectiveTopK, effectiveThreshold),
                        YEAR_SEMANTIC_WEIGHT));
            }
        }

        for (int index = 0; index < probes.size(); index++) {
            List<RetrievedChunk> hits = phraseSearch(probes.get(index), limit);
            if (!hits.isEmpty()) {
                legs.add(new RankedLeg(hits, index == 0 ? PHRASE_WEIGHT : SUB_PHRASE_WEIGHT));
            }
        }
        for (int index = 0; index < probes.size(); index++) {
            String terms = contentTerms(probes.get(index));
            if (terms != null) {
                legs.add(new RankedLeg(
                        keywordSearchRepository.findByAnyTerm(terms, limit * 2),
                        index == 0 ? TERM_WEIGHT : SUB_TERM_WEIGHT));
            }
        }

        return fuse(legs);
    }

    public int maxContextChunks() {
        return properties.retrieval().maxContextChunks();
    }

    /* ------------------------------------------------------------------ legs */

    private List<RetrievedChunk> semanticSearch(String query, int topK, double threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream().map(this::toChunk).toList();
    }

    /** The longest run of the question's own wording that appears anywhere in the corpus. */
    private List<RetrievedChunk> phraseSearch(String query, int limit) {
        for (String phrase : contentPhrases(query)) {
            List<RetrievedChunk> hits = keywordSearchRepository.findByPhrase(phrase, limit);
            if (!hits.isEmpty()) {
                return hits;
            }
        }
        return List.of();
    }

    /* ----------------------------------------------------------------- fusion */

    private List<RetrievedChunk> fuse(List<RankedLeg> legs) {
        Map<String, Double> fused = new LinkedHashMap<>();
        Map<String, RetrievedChunk> byKey = new LinkedHashMap<>();
        for (RankedLeg leg : legs) {
            accumulate(fused, byKey, leg.chunks(), leg.weight());
        }

        if (fused.isEmpty()) {
            return List.of();
        }

        double highest = fused.values().stream().mapToDouble(Double::doubleValue).max().orElse(1d);

        // Ordering is by coverage, not by summed score. A passage that shares vocabulary with every
        // part of a compound question accumulates rank points in several legs and would otherwise
        // bury the single passage that actually answers one part of it. Interleaving the legs by
        // rank guarantees that each leg — and therefore each sub-question — puts its best passage
        // into the window, while the summed score is still reported as the passage's relevance.
        Map<String, RetrievedChunk> ordered = new LinkedHashMap<>();
        int deepest = legs.stream().mapToInt(leg -> leg.chunks().size()).max().orElse(0);
        for (int rank = 0; rank < deepest; rank++) {
            for (RankedLeg leg : legs) {
                if (rank < leg.chunks().size()) {
                    RetrievedChunk chunk = leg.chunks().get(rank);
                    ordered.putIfAbsent(key(chunk), chunk);
                }
            }
        }

        return ordered.entrySet().stream()
                .map(entry -> entry.getValue()
                        .withScore(fused.getOrDefault(entry.getKey(), 0d) / highest))
                .toList();
    }

    private void accumulate(Map<String, Double> fused,
                            Map<String, RetrievedChunk> byKey,
                            List<RetrievedChunk> chunks,
                            double weight) {
        for (int rank = 0; rank < chunks.size(); rank++) {
            RetrievedChunk chunk = chunks.get(rank);
            fused.merge(key(chunk), weight / (RRF_K + rank + 1), Double::sum);
            byKey.putIfAbsent(key(chunk), chunk);
        }
    }

    private static String key(RetrievedChunk chunk) {
        String normalized = chunk.text().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        String head = normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
        return chunk.documentId() + '|' + chunk.page() + '|' + head;
    }

    private record RankedLeg(List<RetrievedChunk> chunks, double weight) {
    }

    /* -------------------------------------------------------------- question */

    private static List<String> contentWords(String question) {
        List<String> words = new ArrayList<>();
        for (String token : question.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
            if (token.length() >= 3 && !STOPWORDS.contains(token)) {
                words.add(token);
            }
        }
        return words;
    }

    private static List<String> contentPhrases(String question) {
        List<String> words = contentWords(question);
        List<String> phrases = new ArrayList<>();
        int longest = Math.min(MAX_PHRASE_WORDS, words.size());
        for (int size = longest; size >= MIN_PHRASE_WORDS; size--) {
            for (int start = 0; start + size <= words.size(); start++) {
                phrases.add(String.join(" ", words.subList(start, start + size)));
                if (phrases.size() >= MAX_PHRASES) {
                    return phrases;
                }
            }
        }
        return phrases;
    }

    /**
     * Splits a compound staff question into the separate things it asks about. Short, single-topic
     * questions are returned untouched.
     */
    static List<String> subQuestions(String question) {
        if (question.length() < 60) {
            return List.of();
        }
        List<String> subQuestions = new ArrayList<>();
        for (String fragment : question.split("(?i)\\s*(?:[;?]|\\band\\b|\\balso\\b|\\bplus\\b)\\s*")) {
            String cleaned = fragment.trim().replaceAll("^[,:.]+|[,:.]+$", "").trim();
            if (cleaned.length() >= 20 && contentWords(cleaned).size() >= 3) {
                subQuestions.add(cleaned);
            }
            if (subQuestions.size() >= MAX_SUB_QUESTIONS) {
                break;
            }
        }
        return subQuestions.size() >= 2 ? subQuestions : List.of();
    }

    /** The distinct reporting years named in a question, in the order they appear. */
    private static String yearsIn(String question) {
        Set<String> years = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\b(?:19|20)\\d{2}\\b").matcher(question);
        while (matcher.find() && years.size() < MAX_YEARS) {
            years.add(matcher.group());
        }
        return years.isEmpty() ? null : String.join(" ", years);
    }

    /**
     * Reduces a probe to its content words. Filler such as "for both years" measurably degrades the
     * embedding of a short probe, so the year-scoped version is built from keywords alone.
     */
    private static String compactProbe(String probe) {
        List<String> words = contentWords(probe);
        return words.isEmpty() ? probe : String.join(" ", words);
    }

    private static String contentTerms(String question) {
        Set<String> terms = new LinkedHashSet<>();
        for (String word : contentWords(question)) {
            terms.add(word);
            if (terms.size() >= MAX_TERMS) {
                break;
            }
        }
        return terms.isEmpty() ? null : String.join(" OR ", terms);
    }

    /* ------------------------------------------------------------------- map */

    private RetrievedChunk toChunk(Document document) {
        var metadata = document.getMetadata();
        String documentId = stringValue(metadata.get("documentId"));
        String fileName = stringValue(metadata.get("fileName"));
        String title = stringValue(metadata.get("title"));
        Integer page = intValue(metadata.get("page"));
        double score = document.getScore() == null ? 0d : document.getScore();
        return new RetrievedChunk(documentId, fileName, title == null ? fileName : title, page, score,
                document.getText());
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
