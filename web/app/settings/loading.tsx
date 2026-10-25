import InkDots from '@/components/print/InkDots'
import Brand from '@/components/print/Brand'
import { Line, Lines } from '@/components/print/Proof'
import styles from './settings.module.css'

/** The account page while it loads: the same cards as plain shapes. */
export default function Loading() {
  return <main className={styles.page} aria-busy="true">
    <header className={styles.top}><Brand /><InkDots label="Loading your account" size="sm" /></header>
    <div className={`galley ${styles.body}`}>
      <div className={styles.who}>
        <span className="proof-av" style={{ width: 56, height: 56 }} />
        <div style={{ display: 'grid', gap: 10, flex: 1 }}><Line strong w="52%" h={24} /><Line w="40%" /></div>
      </div>
      {[2, 2, 1].map((n, i) => <section key={i} className={styles.section}>
        <Line w="96px" strong h={18} />
        <Lines n={n} className={styles.proofLines} />
        <Line h={48} strong={i === 0} />
      </section>)}
    </div>
  </main>
}
