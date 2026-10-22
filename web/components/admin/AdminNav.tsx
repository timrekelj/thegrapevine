'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import styles from './admin.module.css'

const links = [
  { href: '/admin', label: 'Overview' },
  { href: '/admin/users', label: 'Users' },
  { href: '/admin/groups', label: 'Groups' },
  { href: '/admin/reports', label: 'Reports' },
  { href: '/admin/ai', label: 'AI usage' },
]

export default function AdminNav() {
  const path = usePathname()
  return <nav className={styles.nav} aria-label="Admin">
    {links.map(link => {
      const active = link.href === '/admin' ? path === '/admin' : path.startsWith(link.href)
      return <Link key={link.href} href={link.href} aria-current={active ? 'page' : undefined}>{link.label}</Link>
    })}
  </nav>
}
