import Link from '@/components/print/PressLink'
import { notFound } from 'next/navigation'
import { requireAdmin } from '@/lib/auth'
import { adminRpc, fillDays, formatDate, formatTokens, formatUsd, num, type AdminGroup, type AISummary } from '@/lib/admin'
import { isUuid } from '@/lib/validate'
import SpendChart from '@/components/admin/SpendChart'
import styles from '@/components/admin/admin.module.css'
import Avatar from '@/components/print/Avatar'

export default async function AdminGroupPage({ params }: PageProps<'/admin/groups/[id]'>) {
  const { supabase } = await requireAdmin()
  const { id } = await params
  if (!isUuid(id)) notFound()
  const [group, summary] = await Promise.all([
    adminRpc<AdminGroup | null>(supabase, 'admin_group', { target: id }),
    adminRpc<AISummary>(supabase, 'admin_ai_summary', { days: 30, only_group: id }),
  ])
  if (!group) notFound()
  const usage = [
    { label: 'AI cost, all time', value: formatUsd(group.usage.cost) },
    { label: 'Input tokens', value: formatTokens(group.usage.input_tokens) },
    { label: 'Output tokens', value: formatTokens(group.usage.output_tokens) },
    { label: 'AI requests', value: num(group.usage.requests).toLocaleString('en-GB') },
    { label: 'Per request', value: formatUsd(num(group.usage.requests) ? num(group.usage.cost) / num(group.usage.requests) : 0) },
  ]
  return <main className={styles.page}>
    <div className={styles.heading}>
      <div><h1>{group.name}</h1><p>Created {formatDate(group.created_at)} · invite code <code>{group.invite_code}</code></p></div>
      <Link className={styles.chip} href={`/admin/ai?group=${group.id}`}>See this group&apos;s AI requests</Link>
    </div>
    <section className={styles.tiles} aria-label="AI usage">
      {usage.map(t => <div key={t.label} className={styles.tile}><span>{t.label}</span><b>{t.value}</b></div>)}
    </section>
    <section className={styles.section}>
      <h2>Members</h2>
      <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>Member</th><th>Email</th><th>Role</th><th>Joined</th><th>Last seen</th><th className={styles.num}>Sources</th><th className={styles.num}>Messages</th></tr></thead>
        <tbody>{group.members.map(m => <tr key={m.id}>
          <td><Link className={styles.who} href={`/admin/users/${m.id}`}><Avatar seed={m.id} />{m.display_name}</Link></td>
          <td className={styles.wrap}>{m.email ?? '—'}</td>
          <td>{m.role === 'owner' ? <span className={`${styles.badge} ${styles.badgeStrong}`}>Owner</span> : 'Member'}</td>
          <td>{formatDate(m.joined_at)}</td><td>{formatDate(m.last_seen_at, true)}</td>
          <td className={styles.num}>{num(m.source_count)}</td><td className={styles.num}>{num(m.message_count)}</td>
        </tr>)}</tbody>
      </table></div>
    </section>
    <section className={styles.section}>
      <h2>AI cost per day, last 30 days</h2>
      <SpendChart days={fillDays(summary.by_day, 30)} label={`AI cost per day for ${group.name} over the last 30 days`} />
    </section>
    <section className={styles.section}>
      <h2>Stories</h2>
      <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>Story</th><th>Category</th><th>Last activity</th><th className={styles.num}>Sources</th><th className={styles.num}>Messages</th><th className={styles.num}>Reports</th><th className={styles.num}>AI cost</th></tr></thead>
        <tbody>{group.stories.length ? group.stories.map(s => <tr key={s.id}>
          <td className={styles.wrap}><Link href={`/admin/stories/${s.id}`}>{s.title || 'Untitled'}</Link>{s.flagged && <> <span className={`${styles.badge} ${styles.badgeNew}`}>Flagged</span></>}</td>
          <td>{s.category ?? '—'}</td><td>{formatDate(s.last_activity_at, true)}</td>
          <td className={styles.num}>{num(s.source_count)}</td><td className={styles.num}>{num(s.message_count)}</td>
          <td className={styles.num}>{num(s.report_count)}</td><td className={styles.num}>{formatUsd(s.ai_cost)}</td>
        </tr>) : <tr><td colSpan={7} className={styles.empty}>No stories yet.</td></tr>}</tbody>
      </table></div>
    </section>
  </main>
}
