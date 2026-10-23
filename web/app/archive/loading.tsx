import Link from '@/components/print/PressLink'
import InkDots from '@/components/print/InkDots'
import { Line } from '@/components/print/Proof'
import styles from '@/components/paper/paper.module.css'

/** Earlier editions as a galley proof: day rules and story lines waiting for their type. */
export default function Loading() {
  return <main className={styles.paper} aria-busy="true">
    <header className={styles.archiveHead}>
      <Link href="/" className="meta">← Front page</Link>
      <span style={{ justifySelf: 'center' }}><InkDots kicker="Pulling the back issues" /></span>
      <span />
    </header>
    <div className="double-rule" />
    <section className={`galley ${styles.archive}`}>
      <h1 className={styles.sectionHead}>Earlier editions</h1>
      {[4, 3].map((count, day) => <section key={day} className={styles.archiveDay}>
        <h2 className="label"><Line w="180px" strong /></h2>
        {Array.from({ length: count }, (_, i) => <div key={i} className={styles.archiveStory}>
          <Line w="56px" strong />
          <Line strong h={20} w={i % 2 ? '64%' : '86%'} />
          <Line w="140px" />
        </div>)}
      </section>)}
    </section>
  </main>
}
