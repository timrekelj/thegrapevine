import InkDots from '@/components/print/InkDots'
import { Line, Lines, ProofPlate } from '@/components/print/Proof'
import styles from '@/components/paper/paper.module.css'

/** The front page as a galley proof: the broadsheet's columns set in pale ink while the edition is printed. */
export default function Loading() {
  return <main className={`galley ${styles.paper}`} aria-busy="true">
    <header>
      <div className={styles.proofNameplate}><Line strong w="min(560px, 70%)" h="clamp(36px, 6vw, 92px)" /></div>
      <div className="double-rule" />
      <div className={styles.dateline}>
        <Line w="110px" className={styles.edition} />
        <InkDots kicker="Going to press" />
        <span />
      </div>
      <div className={styles.sections}>
        <div className={styles.sectionList}><Line w="72px" strong /><Line w="56px" /><Line w="64px" /><Line w="48px" /></div>
        <div className={styles.tools}><Line w="132px" h={42} strong /></div>
      </div>
    </header>

    <section className={styles.fold}>
      <div className={`${styles.lead} ${styles.proofStack}`}>
        <Line w="64px" strong />
        <span className={styles.proofLead}><Line strong w="94%" /><Line strong w="72%" /></span>
        <ProofPlate className={styles.hero} />
        <Lines n={5} className={styles.proofSummary} />
      </div>
      <aside className={styles.side}>
        {[0, 1, 2].map(i => <article key={i} className={styles.proofStack}>
          {i === 0 && <ProofPlate className={styles.sidePlate} />}
          <Line w="56px" strong />
          <span className={styles.proofStack} style={{ gap: 8 }}><Line strong h={i === 0 ? 26 : 20} /><Line strong w="66%" h={i === 0 ? 26 : 20} /></span>
          {i === 0 && <Lines n={3} />}
        </article>)}
      </aside>
    </section>

    <section className={styles.inside}>
      <div className={styles.sectionHead}><Line w="220px" h={22} strong /></div>
      <div className={styles.ruled}><div className={styles.insideGrid}>{[0, 1, 2, 3].map(i => <article key={i}>
        <div className={styles.card}>
          <ProofPlate className={styles.cardPlate} />
          <div className={styles.cardText}><Line w="52px" strong /><Line strong h={20} /><Line strong w="58%" h={20} /><Lines n={2} /></div>
        </div>
      </article>)}</div></div>
    </section>
  </main>
}
