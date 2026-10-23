import Link from '@/components/print/PressLink'
import { notFound } from 'next/navigation'
import { requireAdmin } from '@/lib/auth'
import { adminRpc, formatDate, formatUsd, type AdminStory } from '@/lib/admin'
import { isUuid, safeUrl } from '@/lib/validate'
import styles from '@/components/admin/admin.module.css'

export default async function AdminStoryPage({ params }: PageProps<'/admin/stories/[id]'>) {
  const { supabase } = await requireAdmin()
  const { id } = await params
  if (!isUuid(id)) notFound()
  const story = await adminRpc<AdminStory | null>(supabase, 'admin_story', { target: id })
  if (!story) notFound()
  return <main className={styles.page}>
    <div className={styles.heading}>
      <div>
        <h1>{story.title || 'Untitled'} {story.flagged && <span className={`${styles.badge} ${styles.badgeNew}`}>Flagged</span>}</h1>
        <p><Link href={`/admin/groups/${story.group_id}`}>{story.group_name}</Link> · {story.category ?? 'No category'} · created {formatDate(story.created_at, true)} · AI cost {formatUsd(story.ai_cost)}</p>
      </div>
    </div>
    <section className={styles.section}>
      <h2>Reports</h2>
      <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>Reported</th><th>Status</th><th>Target</th><th>Note</th><th>Reporter</th><th>Resolved</th></tr></thead>
        <tbody>{story.reports.length ? story.reports.map(r => <tr key={r.id}>
          <td>{formatDate(r.created_at, true)}</td>
          <td>{r.status === 'open' ? <span className={`${styles.badge} ${styles.badgeNew}`}>Open</span> : <span className={styles.badge}>Resolved</span>}</td>
          <td>{r.target}</td><td className={styles.wrap}>{r.note || <span className={styles.muted}>No note</span>}</td>
          <td>{r.reporter_id ? <Link href={`/admin/users/${r.reporter_id}`}>{r.reporter_name}</Link> : '—'}</td>
          <td>{formatDate(r.resolved_at, true)}</td>
        </tr>) : <tr><td colSpan={6} className={styles.empty}>No reports on this story.</td></tr>}</tbody>
      </table></div>
    </section>
    <section className={styles.section}>
      <h2>Generated versions, newest first</h2>
      <div className={styles.versions}>
        <article className={styles.version}>
          <div className={styles.muted}>Current · {story.category ?? 'No category'}</div>
          <h3>{story.title}</h3><p>{story.summary}</p>
        </article>
        {story.history.map(v => <article key={v.id} className={styles.version}>
          <div className={styles.muted}>{formatDate(v.created_at, true)} · {v.category ?? 'No category'}</div>
          <h3>{v.title}</h3><p>{v.summary}</p>
        </article>)}
      </div>
    </section>
    <section className={styles.section}>
      <h2>Sources</h2>
      <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>Source</th><th>Status</th><th>Shared by</th><th>When</th></tr></thead>
        <tbody>{story.sources.map(s => <tr key={s.id}>
          <td className={styles.wrap}><a href={safeUrl(s.url)} target="_blank" rel="noreferrer noopener">{s.source_title || s.domain}</a><div className={styles.muted}>{s.publisher || s.domain}</div></td>
          <td>{s.extraction_status}</td><td>{s.submitted_by_name ?? '—'}</td><td>{formatDate(s.created_at, true)}</td>
        </tr>)}</tbody>
      </table></div>
    </section>
  </main>
}
