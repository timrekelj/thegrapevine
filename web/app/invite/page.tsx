import { redirect } from 'next/navigation'
import { requireUser } from '@/lib/auth'
import { connection } from 'next/server'
import { captureServer } from '@/lib/telemetry-server'
import { invitePreview } from '@/lib/metadata'
import SubmitButton from '@/components/print/SubmitButton'
import Brand from '@/components/print/Brand'
import styles from '@/components/print/card.module.css'

export const metadata = invitePreview

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
    const { supabase, userId } = await requireUser(path)
    const { error } = await supabase.rpc('join_group_with_invite', { code })
    // A fixed line rather than the database's text: invite links are shared, and a message carried in
    // the URL could be rewritten by whoever shares one.
    if (error) redirect(`${path}&failed=1`)
    await captureServer(userId, 'paper_joined')
    redirect('/')
  }

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <Brand className={styles.brand} />
        {error || !group ? <>
          <h1 className="headline">This invitation isn’t valid.</h1>
          <p className="summary" role="alert">Ask your friend for a new invite code.</p>
          <a href="/onboarding" className="btn">Try another code</a>
        </> : <>
          <p className="label">You’re invited to</p>
          <h1 className={styles.title}>{group.name}</h1>
          <p className="summary">A private newspaper, shared and discussed by the people in it.</p>
          <form action={join}><SubmitButton className="btn" pending="Joining the paper…">Join the paper</SubmitButton></form>
          {params.failed && <p role="alert" className={styles.error}>This paper could not be joined. Ask your friend for a new invite code.</p>}
        </>}
      </div>
    </main>
  )
}
