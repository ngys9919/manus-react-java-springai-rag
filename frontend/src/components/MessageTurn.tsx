import { useMemo, useState } from 'react'
import Markdown from 'react-markdown'
import type { Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { ChatMessage, ChatSource } from '../api/types'
import { formatClock, formatLatency } from '../utils/format'
import { BrandMark } from './BrandMark'

interface MessageTurnProps {
  message: ChatMessage
  activeSource: number | null
  onSelectSource: (source: ChatSource) => void
}

/** Rewrites bare [n] markers into links so the renderer can turn them into citation chips. */
function linkifyCitations(content: string): string {
  return content.replace(/\[(\d{1,2})]/g, (_match, index: string) => `[${index}](#citation-${index})`)
}

export function MessageTurn({ message, activeSource, onSelectSource }: MessageTurnProps) {
  const [copied, setCopied] = useState(false)

  if (message.role === 'USER') {
    return (
      <article className="turn turn-user">
        <div className="turn-user-bubble">{message.content}</div>
      </article>
    )
  }

  const sources = message.sources ?? []

  const markdown = useMemo(() => {
    const components: Components = {
      a({ href, children }) {
        const match = /^#citation-(\d+)$/.exec(href ?? '')
        if (match) {
          const index = Number(match[1])
          const source = sources.find((candidate) => candidate.index === index)
          return (
            <button
              type="button"
              className={`citation-ref${activeSource === index ? ' active' : ''}`}
              title={source ? `${source.title} · page ${source.page ?? '—'}` : `Source ${index}`}
              onClick={() => source && onSelectSource(source)}
            >
              {index}
            </button>
          )
        }
        return (
          <a href={href} target="_blank" rel="noreferrer noopener">
            {children}
          </a>
        )
      },
    }
    return (
      <Markdown remarkPlugins={[remarkGfm]} components={components}>
        {linkifyCitations(message.content)}
      </Markdown>
    )
  }, [message.content, sources, activeSource, onSelectSource])

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(message.content)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1600)
    } catch {
      setCopied(false)
    }
  }

  return (
    <article className="turn turn-assistant">
      <header className="assistant-head">
        <BrandMark size={17} />
        <span className="assistant-name">HeiFinance Assistant</span>
        <span className="assistant-rule" />
      </header>

      <div className="assistant-body">{markdown}</div>

      {sources.length > 0 && (
        <div className="citations">
          {sources.map((source) => (
            <button
              key={source.index}
              type="button"
              className={`citation-chip${activeSource === source.index ? ' active' : ''}`}
              onClick={() => onSelectSource(source)}
            >
              <span className="citation-index">{source.index}</span>
              <span className="citation-text">{source.title}</span>
              {source.page ? <span className="citation-page">p.{source.page}</span> : null}
            </button>
          ))}
        </div>
      )}

      <footer className="turn-meta">
        <span>{formatClock(message.createdAt)}</span>
        {message.latencyMs ? (
          <>
            <span className="sep" />
            <span>{formatLatency(message.latencyMs)}</span>
          </>
        ) : null}
        {message.model ? (
          <>
            <span className="sep" />
            <span>{message.model}</span>
          </>
        ) : null}
        <span className="sep" />
        <button type="button" onClick={copy}>
          {copied ? 'Copied' : 'Copy answer'}
        </button>
      </footer>
    </article>
  )
}
