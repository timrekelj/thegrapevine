import Link from 'next/link'

/** The app's mark: the bunch from the icon, ink grapes with the newest one ringed in vine green. */
export function BunchMark({ size = 28 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
    <path d="M32 19C32 14 34 10 39 7" stroke="var(--red)" strokeWidth="4" strokeLinecap="round" fill="none" />
    <path d="M32 17c-8-7-15-4-17 1 7 4 12 3 17-1z" fill="var(--red)" />
    <circle cx="21" cy="29" r="10" fill="var(--ink)" stroke="var(--paper)" strokeWidth="2.5" />
    <circle cx="43" cy="29" r="10" fill="var(--ink)" stroke="var(--paper)" strokeWidth="2.5" />
    <circle cx="23" cy="48" r="9" fill="var(--ink)" stroke="var(--paper)" strokeWidth="2.5" />
    <circle cx="43" cy="48" r="7" fill="var(--ink)" />
    <circle cx="43" cy="48" r="10.5" fill="none" stroke="var(--red)" strokeWidth="3" />
  </svg>
}

/** The header every signed-out and account page opens with: the mark and the name, linking home. */
export default function Brand({ href = '/', name = 'The Grapevine', className = '' }: { href?: string; name?: string; className?: string }) {
  return <Link href={href} className={`brand ${className}`}><BunchMark /><span className="masthead">{name}</span></Link>
}
