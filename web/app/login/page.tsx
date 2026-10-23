import type { Metadata } from 'next'
import Link from 'next/link'
import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'
import { safeRedirect } from '@/lib/auth'
import { connection } from 'next/server'
import { captureServer } from '@/lib/telemetry-server'
import { invitePreview } from '@/lib/metadata'
import { dayline } from '@/lib/format'
import { CodeInput, ResendButton, SignInButton } from './code-input'
import styles from './login.module.css'

function GoogleMark() {
  return <svg viewBox="0 0 48 48" aria-hidden="true">
    <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
    <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
    <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
    <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
  </svg>
}

function AppleMark() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701" /></svg>
}

function XMark() {
  return <svg viewBox="0 0 1200 1227" aria-hidden="true"><path fill="currentColor" d="M714.163 519.284L1160.89 0H1055.03L667.137 450.887L357.328 0H0L468.492 681.821L0 1226.37H105.866L515.491 750.218L842.672 1226.37H1200L714.137 519.284H714.163ZM569.165 687.828L521.697 619.934L144.011 79.6944H306.615L611.412 515.685L658.88 583.579L1055.08 1150.3H892.476L569.165 687.854V687.828Z" /></svg>
}

function ErrorMessage({ text }: { text: string }) {
  return <p id="login-error" role="alert" className={styles.error}>
    <svg width="15" height="15" viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="7" fill="none" stroke="currentColor" strokeWidth="1.5" /><path d="M8 4.5v4.2M8 11v.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></svg>
    {text}
  </p>
}

// Signed-out visitors, including link-preview bots, reach invites through this page.
export async function generateMetadata({ searchParams }: PageProps<'/login'>): Promise<Metadata> {
  const { redirect } = await searchParams
  return safeRedirect(redirect).startsWith('/invite?') ? invitePreview : {}
}

export default async function LoginPage({ searchParams }: PageProps<'/login'>) {
  await connection() // fully dynamic: session + auth flow
  const params = await searchParams
  const destination = safeRedirect(params.redirect)
  const sentTo = typeof params.email === 'string' ? params.email : ''
  const error = typeof params.error === 'string' ? params.error : ''
  const today = dayline(new Date())

  async function signInWithProvider(formData: FormData) {
    'use server'
    const provider = formData.get('provider')
    if (provider !== 'google' && provider !== 'apple' && provider !== 'x') return
    const callback = new URL('/auth/callback', process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000')
    callback.searchParams.set('redirect', destination)
    const supabase = await createClient()
    const { data, error } = await supabase.auth.signInWithOAuth({
      provider,
      options: { redirectTo: callback.toString() },
    })
    if (error || !data.url) {
      redirect(`/login?${new URLSearchParams({ redirect: destination, error: error?.message ?? 'Could not start sign-in. Please try again.' })}`)
    }
    redirect(data.url)
  }

  async function sendCode(formData: FormData) {
    'use server'
    const email = String(formData.get('email') ?? '').trim()
    const query = new URLSearchParams({ redirect: destination })
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
      query.set('error', 'Enter a valid email address.')
      redirect(`/login?${query}`)
    }
    const supabase = await createClient()
    const { error } = await supabase.auth.signInWithOtp({ email })
    if (error) query.set('error', error.message)
    else query.set('email', email)
    redirect(`/login?${query}`)
  }

  async function verifyCode(formData: FormData) {
    'use server'
    const email = String(formData.get('email') ?? '').trim()
    const token = String(formData.get('code') ?? '').replace(/\D/g, '')
    const supabase = await createClient()
    const { data, error } = await supabase.auth.verifyOtp({ email, token, type: 'email' })
    let user = data.user
    if (error || !user) {
      // A code works once. If the same form was submitted twice, the first submit already signed this
      // person in and the second is rejected; carry on instead of showing an error over a working session.
      const { data: { user: current } } = await supabase.auth.getUser()
      if (!current || current.email?.toLowerCase() !== email.toLowerCase()) {
        const query = new URLSearchParams({ redirect: destination, email, error: error?.message ?? 'That code didn’t work. Check it or send a new one.' })
        redirect(`/login?${query}`)
      }
      user = current
    }
    const { data: memberships, error: membershipError } = await supabase.from('group_members').select('group_id').eq('user_id', user.id).limit(1)
    await captureServer(user.id, 'signed_in')
    redirect(membershipError || memberships?.length ? destination : `/onboarding?${new URLSearchParams({ redirect: destination })}`)
  }

  return (
    <main className={styles.page}>
      <header className={styles.nameplate}>
        <Link href="/" className="nameplate">The Grapevine</Link>
        <div className="double-rule" />
        <p className={styles.dateline}><span>{today}</span><span>Private edition</span></p>
      </header>
      <div className={styles.body}>
        <div className={styles.intro}>
          <h1 className="display">{sentTo ? 'Check your inbox.' : <>Good stories.<br />Better company.</>}</h1>
          <p className="summary" role={sentTo ? 'status' : undefined}>
            {sentTo ? <>We sent a 6-digit code to <b>{sentTo}</b> · <a href={`/login?${new URLSearchParams({ redirect: destination })}`}>Change</a></> : 'A private paper, curated by your friends.'}
          </p>
        </div>
        {sentTo ? <>
          <form action={verifyCode} className={styles.form}>
            <input type="hidden" name="email" value={sentTo} />
            <CodeInput invalid={!!error} describedBy={error ? 'login-error' : undefined} />
            {error && <ErrorMessage text={error} />}
            <SignInButton className={styles.primary} />
          </form>
          <form action={sendCode}>
            <input type="hidden" name="email" value={sentTo} />
            <ResendButton seconds={30} />
          </form>
        </> : <>
          <form action={sendCode} className={styles.form}>
            <label htmlFor="email" className="label">Email address</label>
            <input id="email" name="email" type="email" autoComplete="email" required maxLength={254} placeholder="you@example.com"
              className={styles.field} aria-invalid={!!error} aria-describedby={error ? 'login-error' : undefined} />
            {error && <ErrorMessage text={error} />}
            <button className={styles.primary}>Email me a code</button>
          </form>
          <div className={styles.or}>or</div>
          <form action={signInWithProvider} className={styles.providers}>
            <button className={styles.provider} name="provider" value="google"><GoogleMark />Continue with Google</button>
            <button className={styles.provider} name="provider" value="apple"><AppleMark />Sign in with Apple</button>
            <button className={styles.provider} name="provider" value="x"><XMark />Continue with X</button>
          </form>
        </>}
      </div>
      <p className={`meta ${styles.footer}`}>
        By continuing, you agree to the <a href="/terms">Terms</a> and <a href="/privacy">Privacy Policy</a>.
      </p>
    </main>
  )
}
