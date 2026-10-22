import Link from 'next/link'
import { notFound } from 'next/navigation'
import { requireAdmin } from '@/lib/auth'
import { adminRpc, formatDate, formatUsd, isUuid, type AdminUser } from '@/lib/admin'
import styles from '@/components/admin/admin.module.css'

export default async function AdminUserPage({ params }: PageProps<'/admin/users/[id]'>) {
  const { supabase } = await requireAdmin()
  const { id } = await params
  if (!isUuid(id)) notFound()
  const user = await adminRpc<AdminUser | null>(supabase, 'admin_user', { target: id })
  if (!user) notFound()
  return <main className={styles.page}>
    <div className={styles.heading}>
      <div>
        <h1><span className="av">{user.monogram}</span> {user.display_name} {user.is_admin && <span className={`${styles.badge} ${styles.badgeStrong}`}>Admin</span>}</h1>
        <p>{user.email ?? 'No email'} · signed up {formatDate(user.created_at)} · last sign-in {formatDate(user.last_sign_in_at, true)} · AI cost {formatUsd(user.ai_cost)}</p>
      </div>
    </div>
    <section className={styles.section}>
      <h2>Groups</h2>
      <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>Group</th><th>Role</th><th>Joined</th><th>Last seen</th></tr></thead>
        <tbody>{user.groups.length ? user.groups.map(g => <tr key={g.id}>
          <td><Link href={`/admin/groups/${g.id}`}>{g.name}</Link></td><td>{g.role}</td>
          <td>{formatDate(g.joined_at)}</td><td>{formatDate(g.last_seen_at, true)}</td>
        </tr>) : <tr><td colSpan={4} className={styles.empty}>Not in any group.</td></tr>}</tbody>
      </table></div>
    </section>
    <div className={styles.split}>
      <section className={styles.section}>
        <h2>Latest sources shared</h2>
        <div className={styles.tableWrap}><table className={styles.table}>
          <thead><tr><th>Source</th><th>Story</th><th>When</th></tr></thead>
          <tbody>{user.sources.length ? user.sources.map(s => <tr key={s.id}>
            <td className={styles.wrap}><a href={s.url} target="_blank" rel="noreferrer noopener">{s.source_title || s.domain}</a><div className={styles.muted}>{s.publisher || s.domain}</div></td>
            <td className={styles.wrap}><Link href={`/admin/stories/${s.story_id}`}>{s.story_title}</Link><div className={styles.muted}>{s.group_name}</div></td>
            <td>{formatDate(s.created_at, true)}</td>
          </tr>) : <tr><td colSpan={3} className={styles.empty}>No sources shared.</td></tr>}</tbody>
        </table></div>
      </section>
      <section className={styles.section}>
        <h2>Latest messages</h2>
        <div className={styles.tableWrap}><table className={styles.table}>
          <thead><tr><th>Message</th><th>Story</th><th>When</th></tr></thead>
          <tbody>{user.messages.length ? user.messages.map(m => <tr key={m.id}>
            <td className={styles.wrap}>{m.body || <span className={styles.muted}>Media only</span>}</td>
            <td className={styles.wrap}><Link href={`/admin/stories/${m.story_id}`}>{m.story_title}</Link><div className={styles.muted}>{m.group_name}</div></td>
            <td>{formatDate(m.created_at, true)}</td>
          </tr>) : <tr><td colSpan={3} className={styles.empty}>No messages.</td></tr>}</tbody>
        </table></div>
      </section>
    </div>
  </main>
}
