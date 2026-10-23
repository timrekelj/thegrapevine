import type { Metadata } from 'next'
import Link from 'next/link'
import { redirect } from 'next/navigation'
import { connection } from 'next/server'
import { requireUser } from '@/lib/auth'
import { emailChangeNotice, sameEmail } from '@/lib/account'
import { dayline } from '@/lib/format'
import { isEmail } from '@/lib/validate'
import { captureServer } from '@/lib/telemetry-server'
import Avatar from '@/components/print/Avatar'
import { PendingButton } from './pending-button'
import styles from './settings.module.css'

export const metadata: Metadata = { title: 'Your account · The Grapevine' }

const text = (value: unknown) => typeof value === 'string' ? value.slice(0, 240) : ''

function back(fields: Record<string, string>) {
  return `/settings?${new URLSearchParams(fields)}`
}

export default async function SettingsPage({ searchParams }: PageProps<'/settings'>) {
  await connection() // fully dynamic: session + database
  const params = await searchParams
  const confirming = params.confirm === 'delete'
  const destination = confirming ? '/settings?confirm=delete' : '/settings'
  const { supabase, user } = await requireUser(destination)
  const { data: profile } = await supabase.from('profiles').select('display_name').eq('id', user.id).maybeSingle()
  const displayName = profile?.display_name || 'Member'
  const error = text(params.error)
  const notice = emailChangeNotice(text(params.notice), user.email, user.new_email)
  const describedBy = error ? 'account-error' : undefined
  const now = new Date()
  const today = dayline(now)

  async function changeEmail(formData: FormData) {
    'use server'
    const { supabase, user } = await requireUser('/settings')
    const email = String(formData.get('email') ?? '').trim()
    if (!isEmail(email)) redirect(back({ error: 'Enter a valid email address.' }))
    if (sameEmail(user.email, email)) redirect(back({ error: 'That is already the email on this account.' }))
    const origin = (process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000').replace(/\/$/, '')
    const emailRedirectTo = `${origin}/auth/callback?${new URLSearchParams({ redirect: '/settings?notice=email' })}`
    const { error } = await supabase.auth.updateUser({ email }, { emailRedirectTo })
    if (error) redirect(back({ error: error.message.slice(0, 240) || 'The confirmation could not be sent. Please try again.' }))
    redirect(back({ notice: 'sent' }))
  }

  async function signOut() {
    'use server'
    const { supabase, user } = await requireUser('/settings')
    await captureServer(user.id, 'signed_out')
    await supabase.auth.signOut({ scope: 'local' })
    redirect('/login')
  }

  async function deleteAccount(formData: FormData) {
    'use server'
    const { supabase, user } = await requireUser('/settings?confirm=delete')
    const typed = String(formData.get('confirm') ?? '')
    if (!sameEmail(user.email, typed)) {
      redirect(back({ confirm: 'delete', error: 'Type the email address on this account to confirm.' }))
    }
    const { data, error } = await supabase.functions.invoke<{ deleted?: boolean; error?: string }>('delete-account')
    if (error || data?.deleted !== true) {
      let message = 'Your account could not be deleted. Please try again.'
      const context = error && typeof error === 'object' && 'context' in error ? error.context : null
      if (context instanceof Response) {
        const body = await context.json().catch(() => null) as { error?: unknown } | null
        if (body && typeof body.error === 'string' && body.error.length > 0 && body.error.length <= 240) message = body.error
      } else if (error && typeof error === 'object' && 'name' in error && error.name === 'FunctionsFetchError') {
        message = 'The deletion service could not be reached. Your account is unchanged.'
      }
      redirect(back({ confirm: 'delete', error: message }))
    }
    await captureServer(user.id, 'account_deleted')
    await supabase.auth.signOut({ scope: 'local' })
    redirect(`/login?${new URLSearchParams({ notice: 'deleted' })}`)
  }

  return (
    <main className={styles.page}>
      <header className={styles.nameplate}>
        <Link href="/" className="nameplate">The Grapevine</Link>
        <div className="double-rule" />
        <p className={styles.dateline}><time dateTime={now.toISOString()}>{today}</time><span>Your account</span></p>
      </header>
      <div className={styles.body}>
        <div className={styles.who}>
          <Avatar seed={user.id} name={displayName} />
          <div>
            <h1 className="headline">{displayName}</h1>
            <p className="meta">{user.email ?? 'No email on this account'}</p>
          </div>
        </div>

        {error && <p id="account-error" role="alert" className={styles.error}>{error}</p>}
        {notice && <p role="status" className={styles.status}>{notice}</p>}

        <section className={styles.section} aria-labelledby="email-heading">
          <h2 id="email-heading" className="label">Email</h2>
          <p className="summary">Sign-in codes are sent to this address. A change is confirmed from your inbox before it takes effect. Open the links in this browser.</p>
          <form action={changeEmail} className={styles.form}>
            <label htmlFor="email">New email address</label>
            <input id="email" name="email" type="email" autoComplete="email" required maxLength={254} placeholder="you@example.com"
              className={styles.field} aria-invalid={!!error && !confirming} aria-describedby={!confirming ? describedBy : undefined} />
            <PendingButton className={styles.primary} idle="Send confirmation" pending="Sending…" />
          </form>
        </section>

        <section className={styles.section} aria-labelledby="session-heading">
          <h2 id="session-heading" className="label">This browser</h2>
          <p className="summary">Signing out here leaves your account as it is, including on a phone where you are also signed in.</p>
          <form action={signOut}>
            <PendingButton className={styles.quiet} idle="Sign out" pending="Signing out…" />
          </form>
        </section>

        <section className={styles.section} aria-labelledby="delete-heading">
          <h2 id="delete-heading" className="label">Close the account</h2>
          {confirming ? <>
            <p className="summary">This is permanent and cannot be undone.</p>
            <ul>
              <li>Your profile, messages, and reactions are removed.</li>
              <li>Photos and videos you uploaded are removed.</li>
              <li>Stories and links you shared stay in the paper, without your name.</li>
              <li>A paper you alone belong to is deleted. One you own, and others still read, passes to its longest-standing member.</li>
            </ul>
            {user.email ? <form action={deleteAccount} className={styles.form}>
              <label htmlFor="confirm">Type your email to confirm</label>
              <p className="meta">The address on this account is <b>{user.email}</b>.</p>
              <input id="confirm" name="confirm" type="text" autoComplete="off" autoCapitalize="none" spellCheck={false} required maxLength={254}
                className={styles.field} aria-invalid={!!error} aria-describedby={describedBy} />
              <PendingButton className={styles.danger} idle="Delete account permanently" pending="Deleting…" />
            </form> : <p className="summary">This account has no email address, so it can’t be deleted from here. Write to <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a> and we will close it.</p>}
            <Link className={styles.keep} href="/settings">Keep my account</Link>
          </> : <>
            <p className="summary">Deleting your account is permanent. Your messages go with you. Stories you shared stay in the paper, without your name.</p>
            <Link className={styles.dangerLink} href="/settings?confirm=delete">Delete account</Link>
          </>}
        </section>
      </div>
    </main>
  )
}
