import Link from 'next/link'
import styles from './legal.module.css'

export default function LegalLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className={styles.page}>
      <header>
        <Link href="/" className="nameplate">The Grapevine</Link>
        <div className="double-rule" />
        <nav aria-label="Legal pages">
          <span>Public notices</span>
          <Link href="/terms">Terms of Service</Link>
          <Link href="/privacy">Privacy Policy</Link>
        </nav>
      </header>
      <article>{children}</article>
      <footer>
        <p>Questions? <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a></p>
        <Link href="/">Back to The Grapevine</Link>
      </footer>
    </main>
  )
}
