import { randomUUID } from 'node:crypto'
import Link from 'next/link'
import { redirect } from 'next/navigation'
import { requireUser } from '@/lib/auth'
import { connection } from 'next/server'
import { createPaper } from '@/lib/papers'
import styles from '@/components/print/card.module.css'

export default async function NewGroupPage({ searchParams }: PageProps<'/group/new'>) {
  await connection() // fully dynamic: session + database
  const params = await searchParams
  await requireUser('/group/new')
  const groupId = typeof params.id === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(params.id) ? params.id : randomUUID()

  async function createGroup(formData: FormData) {
    'use server'
    const { supabase, user } = await requireUser('/group/new')
    const name = String(formData.get('name') ?? '').trim()
    const error = await createPaper(supabase, user.id, groupId, name)
    if (error) redirect(`/group/new?${new URLSearchParams({ id: groupId, name, error })}`)
    redirect('/')
  }

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <Link href="/" className={`masthead ${styles.brand}`}>The Grapevine</Link>
        <div className="double-rule" />
        <p className="label">A new paper</p>
        <h1 className="headline">Name your paper.</h1>
        <p className="summary">A private place for your friends’ links and the conversations around them.</p>
        <form action={createGroup} className={styles.form}>
          <label htmlFor="name">Paper name</label>
          <input id="name" name="name" required maxLength={100} placeholder="The Sunday Club" defaultValue={typeof params.name === 'string' ? params.name : ''} />
          <button className="btn">Create the paper</button>
        </form>
        {typeof params.error === 'string' && <p role="alert" className={styles.error}>{params.error}</p>}
      </div>
    </main>
  )
}
