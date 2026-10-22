import { randomUUID } from 'node:crypto'
import { redirect } from 'next/navigation'
import { requireUser } from '@/lib/auth'
import { connection } from 'next/server'
import { createPaper } from '@/lib/papers'

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
    <main style={{ maxWidth: 480, margin: '80px auto', padding: '0 20px' }}>
      <p className="masthead">The Grapevine</p>
      <hr style={{ border: 0, borderTop: '1px solid var(--rule)', margin: '24px 0 48px' }} />
      <h1 className="headline">Name your paper.</h1>
      <p className="summary" style={{ margin: '24px 0' }}>A private place for your friends’ links and the conversations around them.</p>
      <form action={createGroup} style={{ display: 'grid', gap: 16 }}>
        <label htmlFor="name" className="meta">Paper name</label>
        <input id="name" name="name" required maxLength={100} defaultValue={typeof params.name === 'string' ? params.name : ''} style={{ padding: 12, font: 'inherit', color: 'var(--ink)', background: 'var(--raised)', border: '1px solid var(--rule)' }} />
        <button className="btn" style={{ justifySelf: 'start' }}>Create the paper</button>
      </form>
      {typeof params.error === 'string' && <p role="alert">{params.error}</p>}
    </main>
  )
}
