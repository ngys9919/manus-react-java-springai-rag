interface BrandMarkProps {
  size?: number
  tone?: 'jade' | 'mono'
}

/**
 * The HeiFinance aperture: concentric open arcs forming a gateway.
 * Two arcs in jade, two in ink — the assistant's own voice against the ledger.
 */
export function BrandMark({ size = 30, tone = 'jade' }: BrandMarkProps) {
  const accent = tone === 'jade' ? 'var(--jade)' : 'currentColor'
  const base = tone === 'jade' ? 'var(--ink)' : 'currentColor'

  return (
    <svg
      className="brand-mark"
      width={size}
      height={size}
      viewBox="0 0 32 32"
      role="img"
      aria-label="HeiFinance aperture mark"
    >
      <g fill="none" strokeWidth="2.2" strokeLinecap="round">
        <path d="M16 5.4a10.6 10.6 0 0 1 0 21.2" stroke={accent} />
        <path d="M16 5.4a10.6 10.6 0 0 0 0 21.2" stroke={base} opacity="0.85" />
        <path d="M16 11.2a4.8 4.8 0 0 1 0 9.6" stroke={accent} opacity="0.6" />
        <path d="M16 11.2a4.8 4.8 0 0 0 0 9.6" stroke={base} opacity="0.28" />
      </g>
    </svg>
  )
}
