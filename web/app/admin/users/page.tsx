import Link from 'next/link'
import { requireAdmin } from '@/lib/auth'
import { adminRpc, formatDate, formatUsd, num, type AdminUserRow } from '@/lib/admin'
import styles from '@/components/admin/admin.module.css'

export default async function AdminUsers() {
  const { supabase } = await requireAdmin()
  const users = await adminRpc<AdminUserRow[]>(supabase, 'admin_users')
  return <main className={styles.page}>
    <div className={styles.heading}><div><h1>Users</h1><p>{users.length} {users.length === 1 ? 'account' : 'accounts'}, newest first.</p></div></div>
    <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr>
        <th>Name</th><th>Email</th><th>Signed up</th><th>Last sign-in</th>
        <th className={styles.num}>Groups</th><th className={styles.num}>Sources</th><th className={styles.num}>Messages</th><th className={styles.num}>AI cost</th>
      </tr></thead>
      <tbody>{users.length ? users.map(u => <tr key={u.id}>
        <td><Link className={styles.who} href={`/admin/users/${u.id}`}><span className="av">{u.monogram}</span>{u.display_name}</Link>
          {u.is_admin && <> <span className={`${styles.badge} ${styles.badgeStrong}`}>Admin</span></>}</td>
        <td className={styles.wrap}>{u.email ?? <span className={styles.muted}>—</span>}</td>
        <td>{formatDate(u.created_at)}</td><td>{formatDate(u.last_sign_in_at, true)}</td>
        <td className={styles.num}>{num(u.group_count)}</td><td className={styles.num}>{num(u.source_count)}</td>
        <td className={styles.num}>{num(u.message_count)}</td><td className={styles.num}>{formatUsd(u.ai_cost)}</td>
      </tr>) : <tr><td colSpan={8} className={styles.empty}>No users yet.</td></tr>}</tbody>
    </table></div>
  </main>
}
