import type { ReactNode } from 'react'

/**
 * The paper's loading mark, in place of a spinner: three dots of ink printed one after another.
 * With a `kicker` it is set as a mono line ("Going to press"); otherwise `label` is read out only.
 * `status={false}` inside a control, where `aria-busy` already says it is working.
 */
export default function InkDots({ label = 'Loading', kicker, size = 'md', status = true, className = '' }: {
  label?: string; kicker?: string; size?: 'sm' | 'md' | 'lg'; status?: boolean; className?: string
}) {
  return <span className={`ink-press ${className}`} role={status ? 'status' : undefined}>
    <span className={`ink-dots ${size}`} aria-hidden="true"><i /><i /><i /></span>
    {kicker ? <span className="label">{kicker}</span> : <span className="sr-only">{label}</span>}
  </span>
}

/**
 * A control's label while its work runs: the label keeps its place (so the control never changes
 * width) and the dots are printed over it. Give the control `aria-busy={busy}` and `disabled`.
 */
export function BusyLabel({ busy, label, children }: { busy: boolean; label: string; children: ReactNode }) {
  return <>
    <span className="press-label">{children}</span>
    {busy && <InkDots label={label} status={false} className="press-over" />}
  </>
}
