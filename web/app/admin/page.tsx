import Link from '@/components/print/PressLink'
import { requireAdmin } from '@/lib/auth'
import { adminRpc, fillDays, formatTokens, formatUsd, num, type AISummary, type Overview } from '@/lib/admin'
import SpendChart from '@/components/admin/SpendChart'
import styles from '@/components/admin/admin.module.css'

export default async function AdminOverview() {
  const { supabase } = await requireAdmin()
  const [overview, summary] = await Promise.all([
    adminRpc<Overview>(supabase, 'admin_overview'),
    adminRpc<AISummary>(supabase, 'admin_ai_summary', { days: 30 }),
  ])
  const tiles = [
    { label: 'Users', value: num(overview.users), href: '/admin/users' },
    { label: 'Groups', value: num(overview.groups), href: '/admin/groups' },
    { label: 'Stories', value: num(overview.stories) },
    { label: 'Sources', value: num(overview.sources) },
    { label: 'Messages', value: num(overview.messages) },
    { label: 'Open reports', value: num(overview.open_reports), href: '/admin/reports?status=open' },
  ]
  const spend = [
    { label: 'AI cost, 7 days', value: formatUsd(overview.cost_7d) },
    { label: 'AI cost, 30 days', value: formatUsd(overview.cost_30d) },
    { label: 'AI cost, all time', value: formatUsd(overview.cost_all) },
    { label: 'Tokens, 30 days', value: formatTokens(overview.tokens_30d) },
    { label: 'Requests, 30 days', value: num(overview.requests_30d).toLocaleString('en-GB') },
    { label: 'Per request, 30 days', value: formatUsd(num(overview.requests_30d) ? num(overview.cost_30d) / num(overview.requests_30d) : 0) },
  ]
  return <main className={styles.page}>
    <div className={styles.heading}><div><h1>Overview</h1><p>Everything across every paper.</p></div></div>
    <section className={styles.tiles} aria-label="Totals">
      {tiles.map(t => t.href
        ? <Link key={t.label} href={t.href} className={styles.tile}><span>{t.label}</span><b>{t.value.toLocaleString('en-GB')}</b></Link>
        : <div key={t.label} className={styles.tile}><span>{t.label}</span><b>{t.value.toLocaleString('en-GB')}</b></div>)}
    </section>
    <section className={styles.tiles} aria-label="AI spend">
      {spend.map(t => <Link key={t.label} href="/admin/ai" className={styles.tile}><span>{t.label}</span><b>{t.value}</b></Link>)}
    </section>
    <section className={styles.section}>
      <h2>AI cost per day, last 30 days</h2>
      <SpendChart days={fillDays(summary.by_day, 30)} label="AI cost per day over the last 30 days" />
    </section>
    <section className={styles.section}>
      <h2>Most expensive groups, last 30 days</h2>
      <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>Group</th><th className={styles.num}>Requests</th><th className={styles.num}>Tokens</th><th className={styles.num}>Per request</th><th className={styles.num}>Cost</th></tr></thead>
        <tbody>{summary.by_group.length ? summary.by_group.slice(0, 5).map(g => <tr key={g.group_id ?? 'none'}>
          <td>{g.group_id ? <Link href={`/admin/groups/${g.group_id}`}>{g.group_name}</Link> : <span className={styles.muted}>No group</span>}</td>
          <td className={styles.num}>{num(g.requests)}</td><td className={styles.num}>{formatTokens(g.tokens)}</td>
          <td className={styles.num}>{formatUsd(g.cost_per_request)}</td><td className={styles.num}>{formatUsd(g.cost)}</td>
        </tr>) : <tr><td colSpan={5} className={styles.empty}>No AI usage recorded yet.</td></tr>}</tbody>
      </table></div>
    </section>
  </main>
}
