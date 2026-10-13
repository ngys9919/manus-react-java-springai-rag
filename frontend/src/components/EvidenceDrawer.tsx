import type { ChatSource } from '../api/types'
import { formatScore } from '../utils/format'

interface EvidenceDrawerProps {
  sources: ChatSource[]
  activeIndex: number | null
  onSelect: (source: ChatSource) => void
  onClose: () => void
}

export function EvidenceDrawer({ sources, activeIndex, onSelect, onClose }: EvidenceDrawerProps) {
  const active = sources.find((source) => source.index === activeIndex) ?? sources[0]

  if (!active) return null

  return (
    <aside className="drawer" aria-label="Retrieved evidence">
      <header className="drawer-head">
        <div className="drawer-eyebrow">Retrieved evidence</div>
        <h2 className="drawer-title">{active.title}</h2>
        <div className="drawer-file">{active.fileName}</div>

        <div className="drawer-stats">
          <div>
            <div className="drawer-stat-label">Page</div>
            <div className="drawer-stat-value">{active.page ?? '—'}</div>
          </div>
          <div>
            <div className="drawer-stat-label">Relevance</div>
            <div className="drawer-stat-value">{formatScore(active.score)}</div>
            <div className="score-bar">
              <i style={{ width: `${Math.max(4, Math.min(100, active.score * 100))}%` }} />
            </div>
          </div>
          <div>
            <div className="drawer-stat-label">Cited</div>
            <div className="drawer-stat-value">{active.cited ? 'Yes' : 'No'}</div>
          </div>
        </div>

        <div style={{ marginTop: 14, display: 'flex', justifyContent: 'flex-end' }}>
          <button type="button" className="btn-ghost" onClick={onClose}>
            Close evidence
          </button>
        </div>
      </header>

      <div className="drawer-body">
        <p className="excerpt">{active.excerpt}</p>
      </div>

      <div className="drawer-list">
        {sources.map((source) => (
          <button
            key={source.index}
            type="button"
            className={`drawer-list-item${source.index === active.index ? ' active' : ''}`}
            onClick={() => onSelect(source)}
          >
            <span>{source.index}</span>
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {source.title} · p.{source.page ?? '—'}
            </span>
          </button>
        ))}
      </div>
    </aside>
  )
}
