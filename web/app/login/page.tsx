import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'
import { safeRedirect } from '@/lib/auth'
import { connection } from 'next/server'

const inputStyle = { padding: 12, font: 'inherit', color: 'var(--ink)', background: 'var(--raised)', border: '1px solid var(--rule)' }

export default async function LoginPage({ searchParams }: PageProps<'/login'>) {
  await connection() // fully dynamic: session + auth flow
  const params = await searchParams
  const destination = safeRedirect(params.redirect)
  const sentTo = typeof params.email === 'string' ? params.email : ''

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
    if (error || !data.user) {
      const query = new URLSearchParams({ redirect: destination, email, error: error?.message ?? 'That code didn’t work. Check it or send a new one.' })
      redirect(`/login?${query}`)
    }
    const { data: memberships, error: membershipError } = await supabase.from('group_members').select('group_id').eq('user_id', data.user.id).limit(1)
    redirect(membershipError || memberships?.length ? destination : `/onboarding?${new URLSearchParams({ redirect: destination })}`)
  }

  return (
    <main style={{ maxWidth: 480, margin: '80px auto', padding: '0 20px' }}>
      <p className="masthead">The Grapevine</p>
      <hr style={{ border: 0, borderTop: '1px solid var(--rule)', margin: '24px 0 48px' }} />
      <h1 className="display" style={{ fontSize: 42 }}>A paper between friends.</h1>
      <p className="summary" style={{ margin: '24px 0' }}>Sign in with your email. No password, no public feed.</p>
      {sentTo ? <>
        <form action={verifyCode} style={{ display: 'grid', gap: 16 }}>
          <p role="status">We emailed a sign-in code to {sentTo}.</p>
          <input type="hidden" name="email" value={sentTo} />
          <label htmlFor="code" className="meta">Code</label>
          {/* Supabase codes are 6 digits by default and up to 10 if the project is configured so. */}
          <input id="code" name="code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6,10}" required maxLength={10} autoFocus style={inputStyle} />
          <button className="btn" style={{ justifySelf: 'start' }}>Sign in</button>
        </form>
        <form action={sendCode} style={{ display: 'flex', gap: 16, marginTop: 16, alignItems: 'baseline' }}>
          <input type="hidden" name="email" value={sentTo} />
          <button className="meta" style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', textDecoration: 'underline' }}>Send a new code</button>
          <a className="meta" href={`/login?${new URLSearchParams({ redirect: destination })}`}>Use a different email</a>
        </form>
      </> : (
        <form action={sendCode} style={{ display: 'grid', gap: 16 }}>
          <label htmlFor="email" className="meta">Your email</label>
          <input id="email" name="email" type="email" autoComplete="email" required maxLength={254} style={inputStyle} />
          <button className="btn" style={{ justifySelf: 'start' }}>Email me a code</button>
        </form>
      )}
      {typeof params.error === 'string' && <p role="alert">{params.error}</p>}
    </main>
  )
}
