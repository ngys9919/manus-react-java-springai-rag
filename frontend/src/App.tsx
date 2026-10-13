import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiRequestError, api } from './api/client'
import type {
  ChatMessage,
  ChatSource,
  ConversationSummary,
  Health,
  KnowledgeDocument,
} from './api/types'
import { Composer } from './components/Composer'
import { ConversationRail } from './components/ConversationRail'
import { CorpusModal } from './components/CorpusModal'
import { EvidenceDrawer } from './components/EvidenceDrawer'
import { Thread } from './components/Thread'

const STAGE_ONE = 'Searching the HeiFinance corpus…'
const STAGE_TWO = 'Reading the most relevant passages…'
const STAGE_THREE = 'Composing a grounded answer…'

export default function App() {
  const navigate = useNavigate()
  const { conversationId } = useParams<{ conversationId?: string }>()

  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [health, setHealth] = useState<Health | null>(null)
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [stage, setStage] = useState(STAGE_ONE)
  const [error, setError] = useState<string | null>(null)
  const [drawerSources, setDrawerSources] = useState<ChatSource[] | null>(null)
  const [activeSource, setActiveSource] = useState<number | null>(null)
  const [corpusOpen, setCorpusOpen] = useState(false)
  const [railOpen, setRailOpen] = useState(false)
  const [reindexing, setReindexing] = useState(false)

  const loadedConversation = useRef<string | null>(null)
  const skipNextLoad = useRef<string | null>(null)
  const bodyRef = useRef<HTMLDivElement | null>(null)

  /* ------------------------------------------------------------ initial load */
  const refreshHealth = useCallback(async () => {
    try {
      setHealth(await api.health())
    } catch {
      setHealth(null)
    }
  }, [])

  const refreshConversations = useCallback(async () => {
    try {
      setConversations(await api.listConversations())
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load conversations')
    }
  }, [])

  const refreshDocuments = useCallback(async () => {
    try {
      setDocuments(await api.listDocuments())
    } catch {
      setDocuments([])
    }
  }, [])

  useEffect(() => {
    void refreshHealth()
    void refreshConversations()
    void refreshDocuments()
  }, [refreshHealth, refreshConversations, refreshDocuments])

  /* --------------------------------------------------------- conversation load */
  useEffect(() => {
    if (!conversationId) {
      loadedConversation.current = null
      setMessages([])
      setDrawerSources(null)
      setActiveSource(null)
      return
    }
    if (skipNextLoad.current === conversationId) {
      skipNextLoad.current = null
      loadedConversation.current = conversationId
      return
    }
    if (loadedConversation.current === conversationId) {
      return
    }

    let cancelled = false
    ;(async () => {
      try {
        const detail = await api.getConversation(conversationId)
        if (cancelled) return
        setMessages(detail.messages)
        loadedConversation.current = conversationId
        const lastAssistant = [...detail.messages].reverse().find((message) => message.role === 'ASSISTANT')
        setDrawerSources(lastAssistant?.sources?.length ? lastAssistant.sources : null)
        setActiveSource(lastAssistant?.sources?.[0]?.index ?? null)
      } catch (cause) {
        if (cancelled) return
        setError(cause instanceof Error ? cause.message : 'Unable to open that conversation')
      }
    })()

    return () => {
      cancelled = true
    }
  }, [conversationId])

  /* --------------------------------------------------------------- autoscroll */
  useEffect(() => {
    const element = bodyRef.current
    if (!element) return
    const frame = window.requestAnimationFrame(() => {
      element.scrollTop = element.scrollHeight
    })
    return () => window.cancelAnimationFrame(frame)
  }, [messages, busy])

  /* --------------------------------------------------------------- behaviours */
  const openSource = useCallback((source: ChatSource) => {
    setDrawerSources((current) => {
      const existing = current ?? []
      return existing.some((candidate) => candidate.index === source.index) ? existing : [...existing, source]
    })
    setActiveSource(source.index)
  }, [])

  const send = useCallback(
    async (rawQuestion: string) => {
      const question = rawQuestion.trim()
      if (!question || busy) return

      const previousDraft = draft
      setDraft('')
      setError(null)
      setBusy(true)
      setStage(STAGE_ONE)

      const stageTwo = window.setTimeout(() => setStage(STAGE_TWO), 1300)
      const stageThree = window.setTimeout(() => setStage(STAGE_THREE), 3200)

      const optimistic: ChatMessage = {
        id: `pending-${Date.now()}`,
        conversationId: conversationId ?? '',
        role: 'USER',
        content: question,
        sources: [],
        model: null,
        latencyMs: null,
        createdAt: new Date().toISOString(),
      }
      setMessages((current) => [...current, optimistic])

      try {
        const response = await api.ask(question, conversationId ?? null)

        const assistant: ChatMessage = {
          id: response.messageId,
          conversationId: response.conversationId,
          role: 'ASSISTANT',
          content: response.answer,
          sources: response.sources,
          model: response.model,
          latencyMs: response.latencyMs,
          createdAt: response.createdAt,
        }

        setMessages((current) => [...current, assistant])
        setDrawerSources(response.sources.length > 0 ? response.sources : null)
        setActiveSource(response.sources[0]?.index ?? null)

        if (response.conversationId !== conversationId) {
          skipNextLoad.current = response.conversationId
          navigate(`/c/${response.conversationId}`, { replace: true })
        }

        void refreshConversations()
        void refreshHealth()
      } catch (cause) {
        setMessages((current) => current.filter((message) => message.id !== optimistic.id))
        setDraft(previousDraft || question)
        setError(
          cause instanceof ApiRequestError
            ? cause.message
            : cause instanceof Error
              ? cause.message
              : 'The question could not be answered',
        )
      } finally {
        window.clearTimeout(stageTwo)
        window.clearTimeout(stageThree)
        setBusy(false)
        setStage(STAGE_ONE)
      }
    },
    [busy, conversationId, draft, navigate, refreshConversations, refreshHealth],
  )

  const createConversation = useCallback(() => {
    setRailOpen(false)
    setMessages([])
    setDrawerSources(null)
    setActiveSource(null)
    setError(null)
    loadedConversation.current = null
    navigate('/')
  }, [navigate])

  const deleteConversation = useCallback(
    async (id: string) => {
      try {
        await api.deleteConversation(id)
        setConversations((current) => current.filter((conversation) => conversation.id !== id))
        if (id === conversationId) {
          createConversation()
        }
        void refreshHealth()
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'Unable to delete that conversation')
      }
    },
    [conversationId, createConversation, refreshHealth],
  )

  const reindex = useCallback(async () => {
    setReindexing(true)
    setError(null)
    try {
      const report = await api.reindex(true)
      setDocuments(report.documents)
      await refreshHealth()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Re-indexing failed')
    } finally {
      setReindexing(false)
    }
  }, [refreshHealth])

  const activeTitle =
    conversations.find((conversation) => conversation.id === conversationId)?.title ??
    (messages.length > 0 ? 'Current conversation' : 'New question')

  return (
    <div
      className={`app-shell${drawerSources ? ' drawer-open' : ''}${railOpen ? ' rail-open' : ''}`}
    >
      <ConversationRail
        conversations={conversations}
        activeId={conversationId ?? null}
        health={health}
        onSelect={(id) => {
          setRailOpen(false)
          setError(null)
          navigate(`/c/${id}`)
        }}
        onCreate={createConversation}
        onDelete={(id) => void deleteConversation(id)}
        onOpenCorpus={() => setCorpusOpen(true)}
      />

      <main className="stage">
        <header className="stage-header">
          <button
            type="button"
            className="icon-btn rail-toggle"
            aria-label="Toggle conversations"
            onClick={() => setRailOpen((open) => !open)}
          >
            <svg width="15" height="15" viewBox="0 0 16 16" aria-hidden="true">
              <path
                d="M2 4h12M2 8h12M2 12h12"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
              />
            </svg>
          </button>

          <div className="stage-heading">
            <div className="stage-title">{activeTitle}</div>
            <div className="stage-sub">
              {health
                ? `${health.indexedDocuments} documents · ${health.indexedChunks.toLocaleString()} passages indexed`
                : 'Connecting to the assistant service…'}
            </div>
          </div>

          {health && (
            <span className="chip mono" title="Answer generation model">
              <span className="dot" />
              {health.chatModel}
            </span>
          )}

          <button
            type="button"
            className={`icon-btn${drawerSources ? ' on' : ''}`}
            aria-label="Toggle evidence panel"
            title="Retrieved evidence"
            disabled={!drawerSources}
            onClick={() => {
              if (!drawerSources) return
              setDrawerSources(null)
            }}
          >
            <svg width="15" height="15" viewBox="0 0 16 16" aria-hidden="true">
              <path
                d="M2.5 3.5h11M2.5 8h7M2.5 12.5h11"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </header>

        <div className="stage-body" ref={bodyRef}>
          {error && (
            <div className="thread" style={{ paddingBottom: 0 }}>
              <div className="notice">
                <span>{error}</span>
                <button type="button" onClick={() => setError(null)}>
                  Dismiss
                </button>
              </div>
            </div>
          )}
          <Thread
            messages={messages}
            documents={documents}
            busy={busy}
            stage={stage}
            activeSource={activeSource}
            onSelectSource={openSource}
            onSuggestion={(question) => void send(question)}
          />
        </div>

        <Composer value={draft} busy={busy} onChange={setDraft} onSubmit={() => void send(draft)} />
      </main>

      {drawerSources && (
        <EvidenceDrawer
          sources={drawerSources}
          activeIndex={activeSource}
          onSelect={(source) => setActiveSource(source.index)}
          onClose={() => setDrawerSources(null)}
        />
      )}

      {corpusOpen && (
        <CorpusModal
          documents={documents}
          indexedChunks={health?.indexedChunks ?? 0}
          embeddingModel={health?.embeddingModel ?? 'local ONNX'}
          embeddingDimensions={health?.embeddingDimensions ?? 384}
          reindexing={reindexing}
          onReindex={() => void reindex()}
          onClose={() => setCorpusOpen(false)}
        />
      )}
    </div>
  )
}
