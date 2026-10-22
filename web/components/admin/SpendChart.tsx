import { formatTokens, formatUsd, type DaySpend } from '@/lib/admin'
import styles from './admin.module.css'

const dayLabel = (day: string) => new Date(`${day}T00:00:00Z`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' })

// Single series, so no legend: the section title names it. Each bar is focusable and shows
// its value on hover and focus; the table underneath keeps every value reachable without hovering.
export default function SpendChart({ days, label }: { days: DaySpend[]; label: string }) {
  const peak = Math.max(...days.map(d => d.cost), 0)
  return <div className={styles.chart}>
    <div className={styles.bars} role="img" aria-label={`${label}. Peak ${formatUsd(peak)} per day.`}>
      {peak > 0 && <div className={styles.peak}>{formatUsd(peak)}</div>}
      {days.map(d => <div key={d.day} className={styles.bar} tabIndex={0} data-zero={d.cost === 0}
        aria-label={`${dayLabel(d.day)}: ${formatUsd(d.cost)}, ${d.requests} requests`}>
        <i style={{ height: peak > 0 && d.cost > 0 ? `max(2px, ${(d.cost / peak) * 100}%)` : '1px' }} />
        <span className={styles.tip} aria-hidden="true"><b>{formatUsd(d.cost)}</b>{dayLabel(d.day)} · {d.requests} req · {formatTokens(d.tokens)} tokens</span>
      </div>)}
    </div>
    <div className={styles.axis} aria-hidden="true"><span>{dayLabel(days[0].day)}</span><span>{dayLabel(days[days.length - 1].day)}</span></div>
    <details className={styles.details}>
      <summary>Show as table</summary>
      <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>Day</th><th className={styles.num}>Requests</th><th className={styles.num}>Tokens</th><th className={styles.num}>Cost</th></tr></thead>
        <tbody>{[...days].reverse().map(d => <tr key={d.day}>
          <td>{dayLabel(d.day)}</td><td className={styles.num}>{d.requests}</td>
          <td className={styles.num}>{formatTokens(d.tokens)}</td><td className={styles.num}>{formatUsd(d.cost)}</td>
        </tr>)}</tbody>
      </table></div>
    </details>
  </div>
}
