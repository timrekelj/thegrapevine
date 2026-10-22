import type { Metadata } from 'next'
import Link from 'next/link'
import { requireAdmin } from '@/lib/auth'
import AdminNav from '@/components/admin/AdminNav'
import styles from '@/components/admin/admin.module.css'

export const metadata: Metadata = {
  title: 'Admin · The Grapevine',
  robots: { index: false, follow: false },
}

export default async function AdminLayout({ children }: LayoutProps<'/admin'>) {
  await requireAdmin()
  return <div className={styles.shell}>
    <header className={styles.top}>
      <Link href="/admin" className={styles.brand}>The Grapevine<small>Admin</small></Link>
      <AdminNav />
      <Link href="/" className={styles.back}>Back to the paper</Link>
    </header>
    {children}
  </div>
}
