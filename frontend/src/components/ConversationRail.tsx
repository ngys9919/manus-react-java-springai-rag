import { useMemo, useState } from 'react'
import type { ConversationSummary, Health } from '../api/types'
import { relativeTime } from '../utils/format'
import { BrandMark } from './BrandMark'

interface ConversationRailProps {
  conversations: ConversationSummary[]
  activeId: string | null
  health: Health | null
  onSelect: (id: string) => void
  onCreate: () => void
  onDelete: (id: string) => void
  onOpenCorpus: () => void
}

export function ConversationRail({
  conversations,
  activeId,
  health,
  onSelect,
  onCreate,
  onDelete,
  onOpenCorpus,
}: ConversationRailProps) {
  const [filter, setFilter] = useState('')

  const visible = useMemo(() => {
    const needle = filter.trim().toLowerCase()
    if (!needle) return conversations
    return conversations.filter((conversation) =>
      `${conversation.title} ${conversation.preview ?? ''}`.toLowerCase().includes(needle),
    )
  }, [conversations, filter])

  return (
    <aside className="rail">
      <div className="rail-header">
        <div className="brand">
          <BrandMark size={30} />
          <div className="brand-text">
            <span className="brand-name">HeiFinance</span>
            <span className="brand-sub">Knowledge Assistant</span>
          </div>
        </div>
      </div>

      <div className="rail-actions">
        <button type="button" className="btn-new" onClick={onCreate}>
          <svg width="13" height="13" viewBox="0 0 14 14" aria-hidden="true">
            <path
              d="M7 2v10M2 7h10"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
            />
          </svg>
          New question
        </button>
      </div>

      <div className="rail-filter">
        <input
          type="search"
          value={filter}
          placeholder="Filter conversations"
          aria-label="Filter conversations"
          onChange={(event) => setFilter(event.target.value)}
        />
      </div>

      <div className="rail-section-label">Conversations</div>

      <nav className="rail-list">
        {visible.length === 0 ? (
          <p className="rail-empty">
            {conversations.length === 0
              ? 'No conversations yet. Ask your first question to begin a thread.'
              : 'No conversations match that filter.'}
          </p>
        ) : (
          visible.map((conversation) => (
            <button
              key={conversation.id}
              type="button"
              className={`conversation-item${conversation.id === activeId ? ' active' : ''}`}
              onClick={() => onSelect(conversation.id)}
            >
              <span className="conversation-title">{conversation.title}</span>
              <span className="conversation-meta">
                <span>{relativeTime(conversation.updatedAt)}</span>
                {conversation.messageCount > 0 ? (
                  <>
                    <span>·</span>
                    <span>{Math.ceil(conversation.messageCount / 2)} Q</span>
                  </>
                ) : null}
                {conversation.preview ? (
                  <>
                    <span>·</span>
                    <span className="conversation-preview">{conversation.preview}</span>
                  </>
                ) : null}
              </span>
              <span
                role="button"
                tabIndex={-1}
                className="conversation-delete"
                title="Delete conversation"
                onClick={(event) => {
                  event.stopPropagation()
                  onDelete(conversation.id)
                }}
              >
                <svg width="12" height="12" viewBox="0 0 14 14" aria-hidden="true">
                  <path
                    d="M3 3l8 8M11 3l-8 8"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                    strokeLinecap="round"
                  />
                </svg>
              </span>
            </button>
          ))
        )}
      </nav>

      <div className="rail-footer">
        <button type="button" className="corpus-card" onClick={onOpenCorpus}>
          <span className="corpus-card-top">
            <span className="corpus-card-title">Indexed corpus</span>
            <span className="corpus-dot" />
          </span>
          <span className="corpus-card-value">
            {health ? health.indexedChunks.toLocaleString() : '—'} passages
          </span>
          <span className="corpus-card-sub">
            {health
              ? `${health.indexedDocuments} documents · ${health.embeddingDimensions}-dim embeddings`
              : 'Connecting to the assistant service…'}
          </span>
        </button>
      </div>
    </aside>
  )
}
