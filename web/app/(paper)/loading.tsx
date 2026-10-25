import InkDots from '@/components/print/InkDots'
import { Line, Lines, ProofPlate } from '@/components/print/Proof'
import styles from '@/components/paper/paper.module.css'

/** The front page while it loads: the lead card and the story list as plain shapes. */
export default function Loading() {
  return <main className={`galley ${styles.paper}`} aria-busy="true">
    <header className={styles.head}>
      <div className={styles.headerRow}><Line strong w="220px" h={28} /><InkDots label="Loading the paper" /></div>
      <div className={styles.dateline}><div className={styles.proofStack} style={{ gap: 8 }}><Line w="170px" strong /><Line w="120px" /></div><span style={{ display: 'flex', gap: 4 }}>{[0, 1, 2, 3].map(i => <span key={i} className="proof-av" />)}</span></div>
    </header>
    <section className={styles.fold}>
      <div className={styles.lead}>
        <ProofPlate className={styles.hero} />
        <div className={`${styles.leadBody} ${styles.proofStack}`}>
          <Line w="120px" />
          <span className={styles.proofLead}><Line strong h={34} w="94%" /><Line strong h={34} w="70%" /></span>
          <Lines n={2} last="40%" />
        </div>
      </div>
      <div className={styles.storyList}>
        {[0, 1, 2, 3].map(i => <div key={i} className={styles.storyRow}>
          <span className="proof-av" />
          <div className={`${styles.rowBody} ${styles.proofStack}`} style={{ gap: 8 }}><Line strong h={20} w={i % 2 ? '70%' : '90%'} /><Line w="140px" /></div>
          <ProofPlate className={styles.rowPhoto} />
        </div>)}
      </div>
    </section>
  </main>
}
