import { redirect } from 'next/navigation'
import { requireUser, safeRedirect } from '@/lib/auth'
import { connection } from 'next/server'

export default async function OnboardingPage({ searchParams }: PageProps<'/onboarding'>) {
  await connection() // fully dynamic: session + database
  const params = await searchParams
  const destination = safeRedirect(params.redirect)
  const { supabase, user } = await requireUser(`/onboarding?${new URLSearchParams({ redirect: destination })}`)
  const { data: profile, error: profileError } = await supabase.from('profiles').select('display_name').eq('id', user.id).single()

  async function saveName(formData: FormData) {
    'use server'
    const { supabase, user } = await requireUser('/onboarding')
    const name = String(formData.get('name') ?? '').trim()
    const query = new URLSearchParams({ redirect: destination })
    if (!name || name.length > 80) {
      query.set('error', 'Choose a name between 1 and 80 characters.')
    } else {
      const { data, error } = await supabase.from('profiles').update({ display_name: name, monogram: Array.from(name)[0].toUpperCase() }).eq('id', user.id).select('id').single()
      if (error || !data) query.set('error', 'Your name could not be saved. Please try again.')
      else query.set('saved', '1')
    }
    redirect(`/onboarding?${query}`)
  }

  async function enterCode(formData: FormData) {
    'use server'
    const code = String(formData.get('code') ?? '').trim()
    redirect(`/invite?${new URLSearchParams({ code })}`)
  }

  return (
    <main style={{ maxWidth: 480, margin: '80px auto', padding: '0 20px' }}>
      <p className="masthead">The Grapevine</p>
      <hr style={{ border: 0, borderTop: '1px solid var(--rule)', margin: '24px 0 48px' }} />
      <h1 className="headline">Pull up a chair.</h1>
      <p className="summary" style={{ margin: '24px 0' }}>Your friends will see this name beside the links and messages you share.</p>
      {params.saved !== '1' ? <form action={saveName} style={{ display: 'grid', gap: 16 }}>
        <label htmlFor="name" className="meta">Your name</label>
        <input id="name" name="name" required maxLength={80} autoComplete="name" defaultValue={profile?.display_name ?? ''} style={{ padding: 12, font: 'inherit', color: 'var(--ink)', background: 'var(--raised)', border: '1px solid var(--rule)' }} />
        <button className="btn" style={{ justifySelf: 'start' }}>Save and continue</button>
      </form> : <>
        <p role="status">Your name is saved. Find your paper.</p>
        {destination.startsWith('/invite?') && <p><a href={destination} className="btn">Continue to your invitation</a></p>}
        <p><a href="/group/new" className="btn">Create a paper</a></p>
        <hr style={{ border: 0, borderTop: '1px solid var(--rule)', margin: '32px 0' }} />
        <form action={enterCode} style={{ display: 'grid', gap: 16 }}>
          <label htmlFor="code" className="meta">Or join with an invite code</label>
          <input id="code" name="code" required maxLength={100} autoCapitalize="none" style={{ padding: 12, font: 'inherit', color: 'var(--ink)', background: 'var(--raised)', border: '1px solid var(--rule)' }} />
          <button className="btn" style={{ justifySelf: 'start' }}>Find the paper</button>
        </form>
      </>}
      {(profileError || typeof params.error === 'string') && <p role="alert">{typeof params.error === 'string' ? params.error : 'Your profile could not be loaded. Please try again.'}</p>}
    </main>
  )
}
