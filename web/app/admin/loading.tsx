import InkDots from '@/components/print/InkDots'
import { Line } from '@/components/print/Proof'
import styles from '@/components/admin/admin.module.css'

/** Any admin ledger while it loads, as plain shapes: a heading, a row of totals and a table, under the real admin bar. */
export default function Loading() {
  return <main className={`galley ${styles.page}`} aria-busy="true">
    <div className={styles.heading}><div style={{ display: 'grid', gap: 10 }}><Line strong w="220px" h={30} /><Line w="260px" /></div><InkDots kicker="Totting up" size="sm" /></div>
    <section className={styles.tiles} aria-hidden="true">
      {Array.from({ length: 6 }, (_, i) => <div key={i} className={styles.tile}><Line w="60%" /><Line strong w="40%" h={24} /></div>)}
    </section>
    <section className={styles.section} aria-hidden="true">
      <Line w="180px" strong />
      <div className={styles.tableWrap}><table className={styles.table}><tbody>
        {Array.from({ length: 6 }, (_, i) => <tr key={i}><td><Line w={i % 2 ? '50%' : '70%'} /></td><td><Line w="60%" /></td><td><Line w="40%" /></td><td><Line w="56%" /></td></tr>)}
      </tbody></table></div>
    </section>
  </main>
}
