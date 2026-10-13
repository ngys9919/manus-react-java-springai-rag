package com.heifinance.rag.domain;

/**
 * A single retrieved passage that supports an answer, exposed to the client as a citation.
 *
 * @param index     1-based position used in the inline citation markers of the answer
 * @param documentId stable identifier of the source document
 * @param fileName  original PDF file name
 * @param title     human readable document title
 * @param page      page number within the PDF, when known
 * @param score     cosine similarity reported by the vector store (0..1, higher is closer)
 * @param excerpt   verbatim passage returned by the retriever
 * @param cited     whether the generated answer referenced this passage
 */
public record ChatSource(
        int index,
        String documentId,
        String fileName,
        String title,
        Integer page,
        double score,
        String excerpt,
        boolean cited) {

    public ChatSource asCited(boolean isCited) {
        return new ChatSource(index, documentId, fileName, title, page, score, excerpt, isCited);
    }
}
