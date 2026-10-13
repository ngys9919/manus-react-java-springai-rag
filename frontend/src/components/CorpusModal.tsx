import type { KnowledgeDocument } from '../api/types'
import { formatBytes, relativeTime } from '../utils/format'

interface CorpusModalProps {
  documents: KnowledgeDocument[]
  indexedChunks: number
  embeddingModel: string
  embeddingDimensions: number
  reindexing: boolean
  onReindex: () => void
  onClose: () => void
}

export function CorpusModal({
  documents,
  indexedChunks,
  embeddingModel,
  embeddingDimensions,
  reindexing,
  onReindex,
  onClose,
}: CorpusModalProps) {
  return (
    <div className="modal-scrim" role="dialog" aria-modal="true" aria-label="Source of truth corpus">
      <div className="modal">
        <header className="modal-head">
          <div>
            <h2 className="modal-title">Source of truth</h2>
            <p className="modal-sub">
              {documents.length} documents · {indexedChunks.toLocaleString()} passages embedded with{' '}
              {embeddingModel} ({embeddingDimensions} dimensions)
            </p>
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close">
            <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
              <path
                d="M2.5 2.5l9 9M11.5 2.5l-9 9"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </header>

        <div className="modal-body">
          {documents.length === 0 ? (
            <p style={{ fontSize: 13, color: 'var(--ink-muted)', padding: '14px 0' }}>
              No documents are indexed yet. Run a re-index once the PDFs are present in the knowledge base
              directory.
            </p>
          ) : (
            documents.map((document) => (
              <div className="doc-row" key={document.id}>
                <div className="doc-row-main">
                  <div className="doc-row-title">{document.title}</div>
                  <div className="doc-row-file">{document.fileName}</div>
                  <div className="doc-row-file" style={{ marginTop: 4 }}>
                    {formatBytes(document.sizeBytes)} · indexed {relativeTime(document.indexedAt)} ·{' '}
                    {document.status.toLowerCase()}
                  </div>
                </div>
                <div className="doc-row-stats">
                  <b>{document.chunkCount}</b>
                  passages
                  <br />
                  {document.pageCount} pages
                </div>
              </div>
            ))
          )}
        </div>

        <footer className="modal-foot">
          <span style={{ fontSize: 11.5, color: 'var(--ink-muted)' }}>
            Re-indexing re-reads every PDF and rebuilds the vector index.
          </span>
          <button type="button" className="btn-ghost" onClick={onReindex} disabled={reindexing}>
            {reindexing ? 'Re-indexing…' : 'Re-index corpus'}
          </button>
        </footer>
      </div>
    </div>
  )
}
