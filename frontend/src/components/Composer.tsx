import { useEffect, useRef } from 'react'

interface ComposerProps {
  value: string
  busy: boolean
  onChange: (value: string) => void
  onSubmit: () => void
}

export function Composer({ value, busy, onChange, onSubmit }: ComposerProps) {
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)

  useEffect(() => {
    const element = textareaRef.current
    if (!element) return
    element.style.height = 'auto'
    element.style.height = `${Math.min(element.scrollHeight, 190)}px`
  }, [value])

  useEffect(() => {
    if (!busy) textareaRef.current?.focus()
  }, [busy])

  const canSend = value.trim().length > 0 && !busy

  return (
    <div className="composer-wrap">
      <div className="composer">
        <form
          className="composer-box"
          onSubmit={(event) => {
            event.preventDefault()
            if (canSend) onSubmit()
          }}
        >
          <label className="sr-only" htmlFor="composer-input">
            Ask the HeiFinance knowledge base
          </label>
          <textarea
            id="composer-input"
            ref={textareaRef}
            rows={1}
            value={value}
            placeholder="Ask about policy, products, people or performance…"
            onChange={(event) => onChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                if (canSend) onSubmit()
              }
            }}
          />
          <button className="btn-send" type="submit" disabled={!canSend} aria-label="Send question">
            {busy ? (
              <svg width="15" height="15" viewBox="0 0 16 16" aria-hidden="true">
                <rect x="3" y="3" width="10" height="10" rx="2" fill="currentColor" />
              </svg>
            ) : (
              <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
                <path
                  d="M8 13V3.4M8 3.4 3.8 7.6M8 3.4l4.2 4.2"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            )}
          </button>
        </form>
        <div className="composer-hint">
          <span>Grounded in the HeiFinance source-of-truth corpus. Nothing else is consulted.</span>
          <span>
            <kbd>Enter</kbd> to send · <kbd>Shift</kbd>+<kbd>Enter</kbd> for a new line
          </span>
        </div>
      </div>
    </div>
  )
}
