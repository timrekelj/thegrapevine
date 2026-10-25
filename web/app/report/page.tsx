import type { Metadata } from 'next'
import Link from '@/components/print/PressLink'
import { connection } from 'next/server'
import { requireUser } from '@/lib/auth'
import { reportKind } from '@/lib/report'
import ReportForm from '@/components/report/ReportForm'
import Brand from '@/components/print/Brand'
import styles from '@/components/report/report.module.css'

export const metadata: Metadata = { title: 'Report a problem or idea · The Grapevine' }

const text = (value: unknown, limit: number) => typeof value === 'string' && value.trim() ? value.trim().slice(0, limit) : undefined

export default async function ReportPage({ searchParams }: PageProps<'/report'>) {
  await connection() // fully dynamic: session
  const params = await searchParams
  const kind = reportKind(params.kind)
  const error = text(params.error, 500)
  const from = text(params.from, 200)
  const back = new URLSearchParams({ kind, ...(error && { error }), ...(from && { from }) })
  const { userId } = await requireUser(`/report?${back}`)

  return <main className={`fresh-ink ${styles.page}`}>
    <header className={styles.top}>
      <Brand />
      <Link href="/" className="meta">Back to the paper</Link>
    </header>
    <div className={styles.body}>
      <div className={styles.intro}>
        <h1 className="headline">Report a problem or idea</h1>
        <p className="summary">Found something broken, or wish the paper did something better? Tell us, and add screenshots if they help.</p>
      </div>
      <ReportForm userId={userId} initialKind={kind} error={error} from={from?.startsWith('/') ? from : undefined} />
    </div>
  </main>
}
