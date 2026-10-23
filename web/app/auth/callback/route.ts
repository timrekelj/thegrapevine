import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'
import { safeRedirect } from '@/lib/auth'
import { captureServer } from '@/lib/telemetry-server'

export async function GET(request: Request) {
  const params = new URL(request.url).searchParams
  const destination = safeRedirect(params.get('redirect'))
  const code = params.get('code')
  if (code && !params.has('error')) {
    const supabase = await createClient()
    const { data, error } = await supabase.auth.exchangeCodeForSession(code)
    if (!error && data.user) {
      const { data: memberships, error: membershipError } = await supabase.from('group_members').select('group_id').eq('user_id', data.user.id).limit(1)
      await captureServer(data.user.id, 'signed_in')
      redirect(membershipError || memberships?.length ? destination : `/onboarding?${new URLSearchParams({ redirect: destination })}`)
    }
  }
  // An email-change link that fails should land back on the account page when this browser is still signed in.
  if (destination.startsWith('/settings')) {
    const supabase = await createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (user) redirect(`/settings?${new URLSearchParams({ error: 'That link could not be confirmed. Request a new one from your account page.' })}`)
  }
  redirect(`/login?${new URLSearchParams({ redirect: destination, error: 'Sign-in was cancelled or could not be completed. Please try again.' })}`)
}
