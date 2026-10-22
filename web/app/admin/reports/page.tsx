import Link from 'next/link'
import { requireAdmin } from '@/lib/auth'
import { adminRpc, formatDate, type AdminReport } from '@/lib/admin'
import styles from '@/components/admin/admin.module.css'

const FILTERS = [{ key: 'all', label: 'All' }, { key: 'open', label: 'Open' }, { key: 'resolved', label: 'Resolved' }] as const

export default async function AdminReports({ searchParams }: PageProps<'/admin/reports'>) {
  const { supabase } = await requireAdmin()
  const requested = (await searchParams).status
  const status = FILTERS.find(f => f.key === requested)?.key ?? 'all'
  const reports = await adminRpc<AdminReport[]>(supabase, 'admin_reports', { only_status: status === 'all' ? null : status })
  return <main className={styles.page}>
    <div className={styles.heading}>
      <div><h1>Reports</h1><p>Member corrections to generated titles, summaries, and categories. Each report triggers a regeneration.</p></div>
      <nav className={styles.filters} aria-label="Report status">
        {FILTERS.map(f => <Link key={f.key} className={styles.chip} aria-current={status === f.key ? 'true' : undefined}
          href={f.key === 'all' ? '/admin/reports' : `/admin/reports?status=${f.key}`}>{f.label}</Link>)}
      </nav>
    </div>
    <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr><th>Reported</th><th>Status</th><th>Target</th><th>Note</th><th>Story</th><th>Group</th><th>Reporter</th><th>Resolved</th></tr></thead>
      <tbody>{reports.length ? reports.map(r => <tr key={r.id}>
        <td>{formatDate(r.created_at, true)}</td>
        <td>{r.status === 'open' ? <span className={`${styles.badge} ${styles.badgeNew}`}>Open</span> : <span className={styles.badge}>Resolved</span>}</td>
        <td>{r.target}</td>
        <td className={styles.wrap}>{r.note || <span className={styles.muted}>No note</span>}</td>
        <td className={styles.wrap}><Link href={`/admin/stories/${r.story_id}`}>{r.story_title || 'Untitled'}</Link></td>
        <td><Link href={`/admin/groups/${r.group_id}`}>{r.group_name}</Link></td>
        <td>{r.reporter_id ? <Link href={`/admin/users/${r.reporter_id}`}>{r.reporter_name}</Link> : <span className={styles.muted}>Deleted</span>}</td>
        <td>{formatDate(r.resolved_at, true)}</td>
      </tr>) : <tr><td colSpan={8} className={styles.empty}>No {status === 'all' ? '' : `${status} `}reports.</td></tr>}</tbody>
    </table></div>
  </main>
}
