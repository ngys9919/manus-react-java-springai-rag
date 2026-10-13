import type {
  ChatResponse,
  ConversationDetail,
  ConversationSummary,
  Health,
  KnowledgeDocument,
  ReindexResponse,
  SearchResponse,
} from './types'

const API_BASE = (import.meta.env.VITE_API_BASE ?? '/api').replace(/\/$/, '')

export class ApiRequestError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
    })
  } catch {
    throw new ApiRequestError(
      'The assistant service is unreachable. Confirm the Spring Boot backend is running.',
      0,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  const raw = await response.text()
  const payload = raw ? safeParse(raw) : null

  if (!response.ok) {
    const message =
      payload && typeof payload === 'object' && 'message' in payload
        ? String((payload as { message: unknown }).message)
        : `Request failed with status ${response.status}`
    throw new ApiRequestError(message, response.status)
  }

  return payload as T
}

function safeParse(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

export const api = {
  health: () => request<Health>('/system/health'),

  listConversations: () => request<ConversationSummary[]>('/conversations'),

  getConversation: (id: string) => request<ConversationDetail>(`/conversations/${id}`),

  createConversation: (title?: string) =>
    request<ConversationSummary>('/conversations', {
      method: 'POST',
      body: JSON.stringify({ title: title ?? null }),
    }),

  renameConversation: (id: string, title: string) =>
    request<ConversationSummary>(`/conversations/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ title }),
    }),

  deleteConversation: (id: string) =>
    request<void>(`/conversations/${id}`, { method: 'DELETE' }),

  ask: (message: string, conversationId: string | null) =>
    request<ChatResponse>('/chat', {
      method: 'POST',
      body: JSON.stringify({ message, conversationId }),
    }),

  listDocuments: () => request<KnowledgeDocument[]>('/knowledge/documents'),

  reindex: (force = false) =>
    request<ReindexResponse>(`/knowledge/reindex?force=${force}`, { method: 'POST' }),

  search: (query: string, topK = 5, threshold = 0) =>
    request<SearchResponse>(
      `/knowledge/search?q=${encodeURIComponent(query)}&topK=${topK}&threshold=${threshold}`,
    ),
}
