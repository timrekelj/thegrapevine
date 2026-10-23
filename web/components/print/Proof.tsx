import type { CSSProperties } from 'react'

// Galley proofs: placeholders set in the shape of the page that is coming (see `.galley` in globals.css).

/** One line of type, as a pale bar. `h` sets the cap height for headlines and nameplates. */
export function Line({ w = '100%', h, strong = false, className = '', style }: { w?: string; h?: number | string; strong?: boolean; className?: string; style?: CSSProperties }) {
  return <span className={`proof ${strong ? 'strong' : ''} ${className}`} style={{ width: w, height: h, ...style }} />
}

/** A paragraph: `n` full lines, the last one short. */
export function Lines({ n, last = '62%', className = '' }: { n: number; last?: string; className?: string }) {
  return <span className={`proof-lines ${className}`}>{Array.from({ length: n }, (_, i) => <Line key={i} w={i === n - 1 ? last : '100%'} />)}</span>
}

/** A photograph not yet printed: the bare dot screen. */
export function ProofPlate({ className = '', style }: { className?: string; style?: CSSProperties }) {
  return <span className={`proof-plate ${className}`} style={style} />
}
