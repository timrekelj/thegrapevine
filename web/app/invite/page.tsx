import { redirect } from 'next/navigation'
import { requireUser } from '@/lib/auth'
import { connection } from 'next/server'
import { captureServer } from '@/lib/telemetry-server'

export default async function InvitePage({ searchParams }: PageProps<'/invite'>) {
  await connection() // fully dynamic: session + database
  const params = await searchParams
  const code = typeof params.code === 'string' ? params.code.trim() : ''
  const path = `/invite?${new URLSearchParams({ code })}`
  const { supabase } = await requireUser(path)
  const { data, error } = await supabase.rpc('group_preview_for_invite', { code })
  const group = data?.[0] as { id: string; name: string } | undefined

  async function join() {
    'use server'
    const { supabase, user } = await requireUser(path)
    const { error } = await supabase.rpc('join_group_with_invite', { code })
    if (error) redirect(`${path}&${new URLSearchParams({ error: error.message })}`)
    await captureServer(user.id, 'paper_joined')
    redirect('/')
  }

  return (
    <main style={{ maxWidth: 480, margin: '80px auto', padding: '0 20px' }}>
      <p className="masthead">The Grapevine</p>
      <hr style={{ border: 0, borderTop: '1px solid var(--rule)', margin: '24px 0 48px' }} />
      {error || !group ? <><h1 className="headline">This invitation isn’t valid.</h1><p role="alert">Ask your friend for a new invite code.</p><a href="/onboarding" className="btn">Try another code</a></> : <>
        <p className="label">You’re invited to</p>
        <h1 className="display" style={{ fontSize: 42 }}>{group.name}</h1>
        <p className="summary" style={{ margin: '24px 0' }}>A private newspaper, shared and discussed by the people in it.</p>
        <form action={join}><button className="btn">Join the paper</button></form>
        {typeof params.error === 'string' && <p role="alert">{params.error}</p>}
      </>}
    </main>
  )
}
