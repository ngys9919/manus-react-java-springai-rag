export type Role = 'USER' | 'ASSISTANT'

export interface ChatSource {
  index: number
  documentId: string
  fileName: string
  title: string
  page: number | null
  score: number
  excerpt: string
  cited: boolean
}

export interface ChatMessage {
  id: string
  conversationId: string
  role: Role
  content: string
  sources: ChatSource[]
  model: string | null
  latencyMs: number | null
  createdAt: string
}

export interface ConversationSummary {
  id: string
  title: string
  messageCount: number
  preview: string | null
  createdAt: string
  updatedAt: string
}

export interface ConversationDetail {
  id: string
  title: string
  messageCount: number
  createdAt: string
  updatedAt: string
  messages: ChatMessage[]
}

export interface ChatResponse {
  conversationId: string
  conversationTitle: string
  messageId: string
  answer: string
  sources: ChatSource[]
  model: string
  latencyMs: number
  createdAt: string
}

export interface KnowledgeDocument {
  id: string
  fileName: string
  title: string
  pageCount: number
  chunkCount: number
  sizeBytes: number
  status: string
  indexedAt: string | null
}

export interface ReindexResponse {
  filesScanned: number
  indexed: number
  skipped: number
  failed: number
  totalChunks: number
  durationMs: number
  documents: KnowledgeDocument[]
}

export interface SearchHit {
  rank: number
  documentId: string
  fileName: string
  title: string
  page: number | null
  score: number
  excerpt: string
}

export interface SearchResponse {
  query: string
  topK: number
  similarityThreshold: number
  hits: SearchHit[]
}

export interface Health {
  status: string
  checkedAt: string
  chatModel: string
  embeddingModel: string
  embeddingDimensions: number
  indexedChunks: number
  indexedDocuments: number
  conversations: number
  lastIndexedAt: string | null
  knowledgeBasePath: string
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}
