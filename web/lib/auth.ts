import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'

export { safeRedirect } from './auth-redirect'

export async function requireUser(destination: string) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect(`/login?${new URLSearchParams({ redirect: destination })}`)
  return { supabase, user }
}
