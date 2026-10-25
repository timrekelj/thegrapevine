import Link from '@/components/print/PressLink'
import Brand from '@/components/print/Brand'
import styles from './legal.module.css'

export default function LegalLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className={styles.page}>
      <header>
        <Brand />
        <nav aria-label="Legal pages">
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
