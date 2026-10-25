import InkDots from '@/components/print/InkDots'
import { Line } from '@/components/print/Proof'
import Brand from '@/components/print/Brand'
import styles from '@/components/paper/paper.module.css'

/** Earlier editions while they load: day labels and story lines as plain shapes. */
export default function Loading() {
  return <main className={styles.paper} aria-busy="true">
    <header className={styles.archiveHead}><Brand /><InkDots label="Loading earlier editions" /></header>
    <section className={`galley ${styles.archive}`}>
      <h1 className={styles.sectionHead}>Earlier editions</h1>
      {[4, 3].map((count, day) => <section key={day} className={styles.archiveDay}>
        <Line w="180px" strong />
        {Array.from({ length: count }, (_, i) => <div key={i} className={styles.archiveStory}>
          <Line w="56px" />
          <Line strong h={20} w={i % 2 ? '64%' : '86%'} />
          <Line w="140px" />
        </div>)}
      </section>)}
    </section>
  </main>
}
