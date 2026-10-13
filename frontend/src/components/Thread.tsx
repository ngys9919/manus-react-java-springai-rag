import type { ChatMessage, ChatSource, KnowledgeDocument } from '../api/types'
import { BrandMark } from './BrandMark'
import { MessageTurn } from './MessageTurn'

interface ThreadProps {
  messages: ChatMessage[]
  documents: KnowledgeDocument[]
  busy: boolean
  stage: string
  activeSource: number | null
  onSelectSource: (source: ChatSource) => void
  onSuggestion: (question: string) => void
}

const SUGGESTIONS: { label: string; text: string }[] = [
  {
    label: 'Products',
    text: 'How much bonus interest can I earn on the SavingsPlus account, and what are the tiers?',
  },
  {
    label: 'People',
    text: 'Who is the Head of Consumer Banking and Wealth Management, and how do I reach them?',
  },
  {
    label: 'Policy',
    text: 'How much annual leave am I entitled to, and how does it accrue?',
  },
  {
    label: 'Performance',
    text: 'What were HeiFinance’s total assets and net profit in FY2025?',
  },
]

export function Thread({
  messages,
  documents,
  busy,
  stage,
  activeSource,
  onSelectSource,
  onSuggestion,
}: ThreadProps) {
  const isEmpty = messages.length === 0

  return (
    <div className="thread">
      {isEmpty ? (
        <section className="hero">
          <div className="hero-mark">
            <BrandMark size={46} />
          </div>
          <div className="hero-eyebrow">Internal support desk</div>
          <h1 className="hero-title">
            Answers traced <em>to the page.</em>
          </h1>
          <p className="hero-lede">
            The HeiFinance Knowledge Assistant answers staff questions strictly from five authoritative
            documents — the annual report, the product fact sheet, the employee handbook, the executive
            organisation chart and the full staff directory. Every answer arrives with the passages it
            relied on.
          </p>

          <div className="hero-rule" />

          <div className="suggestions">
            {SUGGESTIONS.map((suggestion) => (
              <button
                key={suggestion.text}
                type="button"
                className="suggestion"
                onClick={() => onSuggestion(suggestion.text)}
              >
                <div className="suggestion-label">{suggestion.label}</div>
                <div className="suggestion-text">{suggestion.text}</div>
              </button>
            ))}
          </div>

          {documents.length > 0 && (
            <div className="corpus-strip">
              <div className="corpus-strip-title">Source of truth</div>
              <div className="corpus-strip-list">
                {documents.map((document) => (
                  <span className="doc-pill" key={document.id}>
                    {document.title}
                    <span>{document.chunkCount} passages</span>
                  </span>
                ))}
              </div>
            </div>
          )}
        </section>
      ) : (
        messages.map((message) => (
          <MessageTurn
            key={message.id}
            message={message}
            activeSource={activeSource}
            onSelectSource={onSelectSource}
          />
        ))
      )}

      {busy && (
        <article className="turn turn-assistant">
          <header className="assistant-head">
            <BrandMark size={17} />
            <span className="assistant-name">HeiFinance Assistant</span>
            <span className="assistant-rule" />
          </header>
          <div className="progress">
            <span className="progress-bars" aria-hidden="true">
              <i />
              <i />
              <i />
              <i />
            </span>
            <span>{stage}</span>
          </div>
        </article>
      )}
    </div>
  )
}
