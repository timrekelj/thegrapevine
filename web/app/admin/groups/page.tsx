import Link from '@/components/print/PressLink'
import { requireAdmin } from '@/lib/auth'
import { adminRpc, formatDate, formatTokens, formatUsd, num, type AdminGroupRow } from '@/lib/admin'
import styles from '@/components/admin/admin.module.css'

export default async function AdminGroups() {
  const { supabase } = await requireAdmin()
  const groups = await adminRpc<AdminGroupRow[]>(supabase, 'admin_groups')
  return <main className={styles.page}>
    <div className={styles.heading}><div><h1>Groups</h1><p>{groups.length} {groups.length === 1 ? 'paper' : 'papers'}, newest first. AI totals are all time.</p></div></div>
    <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr>
        <th>Group</th><th>Created</th><th>Last activity</th>
        <th className={styles.num}>Members</th><th className={styles.num}>Stories</th><th className={styles.num}>Sources</th>
        <th className={styles.num}>Messages</th><th className={styles.num}>Open reports</th><th className={styles.num}>Tokens</th><th className={styles.num}>AI cost</th>
      </tr></thead>
      <tbody>{groups.length ? groups.map(g => <tr key={g.id}>
        <td><Link href={`/admin/groups/${g.id}`}>{g.name}</Link><div className={styles.muted}>by {g.created_by_name ?? 'unknown'}</div></td>
        <td>{formatDate(g.created_at)}</td><td>{formatDate(g.last_activity_at, true)}</td>
        <td className={styles.num}>{num(g.member_count)}</td><td className={styles.num}>{num(g.story_count)}</td>
        <td className={styles.num}>{num(g.source_count)}</td><td className={styles.num}>{num(g.message_count)}</td>
        <td className={styles.num}>{num(g.open_reports) > 0 ? <span className={`${styles.badge} ${styles.badgeNew}`}>{num(g.open_reports)}</span> : 0}</td>
        <td className={styles.num}>{formatTokens(g.ai_tokens)}</td><td className={styles.num}>{formatUsd(g.ai_cost)}</td>
      </tr>) : <tr><td colSpan={10} className={styles.empty}>No groups yet.</td></tr>}</tbody>
    </table></div>
  </main>
}
