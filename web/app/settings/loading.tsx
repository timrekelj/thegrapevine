import Link from '@/components/print/PressLink'
import InkDots from '@/components/print/InkDots'
import { Line, Lines } from '@/components/print/Proof'
import styles from './settings.module.css'

/** The account page as a galley proof, under its real nameplate. */
export default function Loading() {
  return <main className={styles.page} aria-busy="true">
    <header className={styles.nameplate}>
      <Link href="/" className="nameplate">The Grapevine</Link>
      <div className="double-rule" />
      <p className={styles.dateline}><InkDots kicker="Going to press" size="sm" /><span>Your account</span></p>
    </header>
    <div className={`galley ${styles.body}`}>
      <div className={styles.who}>
        <span className="proof-av" style={{ width: 56, height: 56 }} />
        <div style={{ display: 'grid', gap: 10, flex: 1 }}><Line strong w="52%" h={24} /><Line w="40%" /></div>
      </div>
      {[2, 2, 1].map((n, i) => <section key={i} className={styles.section}>
        <Line w="64px" strong />
        <Lines n={n} className={styles.proofLines} />
        <Line h={48} strong={i === 0} />
      </section>)}
    </div>
  </main>
}
