// Loading placeholders: plain shapes in the layout of the page that is coming (see `.galley` in globals.css).

/** One line of text, as a pale bar. `h` sets the height for headlines. */
export function Line({ w = '100%', h, strong = false, className = '' }: { w?: string; h?: number | string; strong?: boolean; className?: string }) {
  return <span className={`proof ${strong ? 'strong' : ''} ${className}`} style={{ width: w, height: h }} />
}

/** A paragraph: `n` full lines, the last one short. */
export function Lines({ n, last = '62%', className = '' }: { n: number; last?: string; className?: string }) {
  return <span className={`proof-lines ${className}`}>{Array.from({ length: n }, (_, i) => <Line key={i} w={i === n - 1 ? last : '100%'} />)}</span>
}

/** A picture that has not arrived yet: a plain rounded tile. */
export function ProofPlate({ className = '' }: { className?: string }) {
  return <span className={`proof-plate ${className}`} />
}
