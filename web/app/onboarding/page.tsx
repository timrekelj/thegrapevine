import { randomUUID } from 'node:crypto'
import { redirect } from 'next/navigation'
import { requireUser, safeRedirect } from '@/lib/auth'
import { createPaper, saveDisplayName } from '@/lib/papers'
import { connection } from 'next/server'
import form from '../login/login.module.css'
import styles from './onboarding.module.css'
import { OnboardingForm } from './onboarding-form'

const UUID = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i

/** Back to the form with what was typed and why it failed. */
function retry(destination: string, id: string, fields: Record<string, string>, error: string): never {
  redirect(`/onboarding?${new URLSearchParams({ redirect: destination, id, ...fields, error })}`)
}

export default async function OnboardingPage({ searchParams }: PageProps<'/onboarding'>) {
  await connection() // fully dynamic: session + database
  const params = await searchParams
  const destination = safeRedirect(params.redirect)
  const { supabase, user } = await requireUser(`/onboarding?${new URLSearchParams({ redirect: destination })}`)
  const { data: profile, error: profileError } = await supabase.from('profiles').select('display_name').eq('id', user.id).single()
  // Keep the paper's ID across retries so a failed membership insert never creates a second paper.
  const groupId = typeof params.id === 'string' && UUID.test(params.id) ? params.id : randomUUID()
  // Someone who arrived from an invite link joins first; the code rides along in the destination.
  const inviteCode = typeof params.code === 'string' ? params.code
    : destination.startsWith('/invite?') ? new URLSearchParams(destination.slice('/invite?'.length)).get('code') ?? '' : ''
  const value = (key: string) => typeof params[key] === 'string' ? params[key] : ''

  async function startPaper(formData: FormData) {
    'use server'
    const { supabase, user } = await requireUser('/onboarding')
    const name = String(formData.get('name') ?? '').trim()
    const paper = String(formData.get('paper') ?? '').trim()
    const error = await saveDisplayName(supabase, user.id, name) ?? await createPaper(supabase, user.id, groupId, paper)
    if (error) retry(destination, groupId, { name, paper }, error)
    redirect('/')
  }

  async function joinPaper(formData: FormData) {
    'use server'
    const { supabase, user } = await requireUser('/onboarding')
    const name = String(formData.get('name') ?? '').trim()
    const code = String(formData.get('code') ?? '').trim()
    const error = await saveDisplayName(supabase, user.id, name) ?? (code ? null : 'Enter the invite code from your friend.')
    if (error) retry(destination, groupId, { name, code }, error)
    redirect(`/invite?${new URLSearchParams({ code })}`)
  }

  const today = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })
  return (
    <main className={form.page}>
      <header className={styles.intro}>
        <p className="label" style={{ margin: 0 }}>Make yourself at home</p>
        <h1 className="display">Your people.<br />Your paper.</h1>
      </header>
      <div className={form.body}>
        <OnboardingForm id={groupId} today={today}
          initialName={value('name') || profile?.display_name || ''} initialPaper={value('paper')} initialCode={value('code') || inviteCode}
          inviteFirst={!!inviteCode} error={value('error') || (profileError ? 'Your profile could not be loaded. Please try again.' : '')}
          startPaper={startPaper} joinPaper={joinPaper} />
      </div>
    </main>
  )
}
