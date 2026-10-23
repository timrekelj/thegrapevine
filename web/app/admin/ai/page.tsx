import Link from 'next/link'
import { requireAdmin } from '@/lib/auth'
import { adminRpc, fillDays, formatDate, formatTokens, formatUsd, num, parseWindow, WINDOWS, type AIRequest, type AISummary } from '@/lib/admin'
import { isUuid } from '@/lib/validate'
import SpendChart from '@/components/admin/SpendChart'
import styles from '@/components/admin/admin.module.css'

const href = (days: number, group?: string) => `/admin/ai?${new URLSearchParams({ days: String(days), ...(group ? { group } : {}) })}`

export default async function AdminAI({ searchParams }: PageProps<'/admin/ai'>) {
  const { supabase } = await requireAdmin()
  const query = await searchParams
  const days = parseWindow(query.days)
  const group = isUuid(query.group) ? query.group : undefined
  const [summary, requests] = await Promise.all([
    adminRpc<AISummary>(supabase, 'admin_ai_summary', { days, only_group: group ?? null }),
    adminRpc<AIRequest[]>(supabase, 'admin_ai_requests', { days, only_group: group ?? null, max_rows: 200 }),
  ])
  const groupName = group ? summary.by_group.find(g => g.group_id === group)?.group_name ?? 'this group' : null
  const t = summary.totals
  const tiles = [
    { label: 'Cost', value: formatUsd(t.cost) },
    { label: 'Requests', value: num(t.requests).toLocaleString('en-GB') },
    { label: 'Per request', value: formatUsd(num(t.requests) ? num(t.cost) / num(t.requests) : 0) },
    { label: 'Input tokens', value: formatTokens(t.input_tokens) },
    { label: 'Output tokens', value: formatTokens(t.output_tokens) },
    { label: 'Failed calls', value: `${num(t.errors)} of ${num(t.calls)}` },
  ]
  return <main className={styles.page}>
    <div className={styles.heading}>
      <div>
        <h1>AI usage{groupName && <> · {groupName}</>}</h1>
        <p>Every model call made by the edge functions. A request is one submission or one correction. Costs are priced when the call is recorded.</p>
      </div>
      <nav className={styles.filters} aria-label="Time range">
        <span>Last</span>
        {WINDOWS.map(w => <Link key={w} className={styles.chip} aria-current={w === days ? 'true' : undefined} href={href(w, group)}>{w} days</Link>)}
        {group && <Link className={styles.chip} href={href(days)}>All groups ✕</Link>}
      </nav>
    </div>
    <section className={styles.tiles} aria-label="Totals">
      {tiles.map(x => <div key={x.label} className={styles.tile}><span>{x.label}</span><b>{x.value}</b></div>)}
    </section>
    <section className={styles.section}>
      <h2>Cost per day</h2>
      <SpendChart days={fillDays(summary.by_day, days)} label={`AI cost per day over the last ${days} days`} />
    </section>
    <div className={styles.split}>
      {!group && <section className={styles.section}>
        <h2>By group</h2>
        <div className={styles.tableWrap}><table className={styles.table}>
          <thead><tr><th>Group</th><th className={styles.num}>Requests</th><th className={styles.num}>Tokens</th><th className={styles.num}>Per request</th><th className={styles.num}>Cost</th></tr></thead>
          <tbody>{summary.by_group.length ? summary.by_group.map(g => <tr key={g.group_id ?? 'none'}>
            <td>{g.group_id ? <Link href={href(days, g.group_id)}>{g.group_name}</Link> : <span className={styles.muted}>No group</span>}</td>
            <td className={styles.num}>{num(g.requests)}</td><td className={styles.num}>{formatTokens(g.tokens)}</td>
            <td className={styles.num}>{formatUsd(g.cost_per_request)}</td><td className={styles.num}>{formatUsd(g.cost)}</td>
          </tr>) : <tr><td colSpan={5} className={styles.empty}>No usage in this window.</td></tr>}</tbody>
        </table></div>
      </section>}
      <section className={styles.section}>
        <h2>By model</h2>
        <div className={styles.tableWrap}><table className={styles.table}>
          <thead><tr><th>Model</th><th className={styles.num}>Calls</th><th className={styles.num}>Input</th><th className={styles.num}>Output</th><th className={styles.num}>Cost</th></tr></thead>
          <tbody>{summary.by_model.length ? summary.by_model.map(m => <tr key={`${m.provider}/${m.model}`}>
            <td>{m.model}<div className={styles.muted}>{m.provider}</div></td><td className={styles.num}>{num(m.calls)}</td>
            <td className={styles.num}>{formatTokens(m.input_tokens)}</td><td className={styles.num}>{formatTokens(m.output_tokens)}</td>
            <td className={styles.num}>{formatUsd(m.cost)}</td>
          </tr>) : <tr><td colSpan={5} className={styles.empty}>No usage in this window.</td></tr>}</tbody>
        </table></div>
      </section>
      <section className={styles.section}>
        <h2>By function</h2>
        <div className={styles.tableWrap}><table className={styles.table}>
          <thead><tr><th>Function</th><th className={styles.num}>Requests</th><th className={styles.num}>Per request</th><th className={styles.num}>Cost</th></tr></thead>
          <tbody>{summary.by_function.length ? summary.by_function.map(f => <tr key={f.function_name}>
            <td>{f.function_name}</td><td className={styles.num}>{num(f.requests)}</td>
            <td className={styles.num}>{formatUsd(f.cost_per_request)}</td><td className={styles.num}>{formatUsd(f.cost)}</td>
          </tr>) : <tr><td colSpan={4} className={styles.empty}>No usage in this window.</td></tr>}</tbody>
        </table></div>
      </section>
    </div>
    <section className={styles.section}>
      <h2>Requests, newest first{requests.length === 200 ? ' (latest 200)' : ''}</h2>
      <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>Request</th><th>Group</th><th>Member</th><th className={styles.num}>Calls</th><th className={styles.num}>Input</th><th className={styles.num}>Output</th><th className={styles.num}>Time</th><th className={styles.num}>Cost</th></tr></thead>
        <tbody>{requests.length ? requests.map(r => <tr key={r.request_id}>
          <td>
            <details className={styles.request}>
              <summary>{formatDate(r.started_at, true)} · {r.function_name}{num(r.errors) > 0 && <> <span className={`${styles.badge} ${styles.badgeNew}`}>{num(r.errors)} failed</span></>}</summary>
              <table className={styles.calls}><tbody>{r.calls.map((c, i) => <tr key={i}>
                <td>{c.operation}</td><td className={styles.muted}>{c.model}</td>
                <td className={styles.num}>{formatTokens(c.input_tokens)} in</td><td className={styles.num}>{formatTokens(c.output_tokens)} out</td>
                <td className={styles.num}>{num(c.latency_ms)} ms</td><td className={styles.num}>{c.is_error ? 'failed' : formatUsd(c.cost)}</td>
              </tr>)}</tbody></table>
              {r.story_id && <Link href={`/admin/stories/${r.story_id}`}>Open story</Link>}
            </details>
          </td>
          <td>{r.group_id ? <Link href={`/admin/groups/${r.group_id}`}>{r.group_name}</Link> : <span className={styles.muted}>—</span>}</td>
          <td>{r.user_id ? <Link href={`/admin/users/${r.user_id}`}>{r.user_name}</Link> : <span className={styles.muted}>—</span>}</td>
          <td className={styles.num}>{r.calls.length}</td>
          <td className={styles.num}>{formatTokens(r.input_tokens)}</td><td className={styles.num}>{formatTokens(r.output_tokens)}</td>
          <td className={styles.num}>{(num(r.latency_ms) / 1000).toFixed(1)} s</td><td className={styles.num}>{formatUsd(r.cost)}</td>
        </tr>) : <tr><td colSpan={8} className={styles.empty}>No requests in this window.</td></tr>}</tbody>
      </table></div>
    </section>
  </main>
}
