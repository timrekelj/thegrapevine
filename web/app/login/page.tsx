import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'
import { safeRedirect } from '@/lib/auth'
import { connection } from 'next/server'

export default async function LoginPage({ searchParams }: PageProps<'/login'>) {
  await connection() // fully dynamic: session + auth flow
  const params = await searchParams
  const destination = safeRedirect(params.redirect)

  async function sendLink(formData: FormData) {
    'use server'
    const email = String(formData.get('email') ?? '').trim()
    const query = new URLSearchParams({ redirect: destination })
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
      query.set('error', 'Enter a valid email address.')
      redirect(`/login?${query}`)
    }
    const callback = new URL('http://localhost:3000/auth/callback')
    callback.searchParams.set('redirect', destination)
    const supabase = await createClient()
    const { error } = await supabase.auth.signInWithOtp({ email, options: { emailRedirectTo: callback.toString() } })
    query.set(error ? 'error' : 'sent', error ? error.message : '1')
    redirect(`/login?${query}`)
  }

  return (
    <main style={{ maxWidth: 480, margin: '80px auto', padding: '0 20px' }}>
      <p className="masthead">The Grapevine</p>
      <hr style={{ border: 0, borderTop: '1px solid var(--rule)', margin: '24px 0 48px' }} />
      <h1 className="display" style={{ fontSize: 42 }}>A paper between friends.</h1>
      <p className="summary" style={{ margin: '24px 0' }}>Sign in with your email. No password, no public feed.</p>
      {params.sent === '1' ? <p role="status">Check your email for a sign-in link. You can close this tab and open the link in the same browser.</p> : (
        <form action={sendLink} style={{ display: 'grid', gap: 16 }}>
          <label htmlFor="email" className="meta">Your email</label>
          <input id="email" name="email" type="email" autoComplete="email" required maxLength={254} style={{ padding: 12, font: 'inherit', color: 'var(--ink)', background: 'var(--raised)', border: '1px solid var(--rule)' }} />
          <button className="btn" style={{ justifySelf: 'start' }}>Send a sign-in link</button>
        </form>
      )}
      {typeof params.error === 'string' && <p role="alert">{params.error}</p>}
    </main>
  )
}
