import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'

// Only same-origin paths may survive the email round trip.
export function safeRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(value)) return '/'
  const url = new URL(value, 'http://localhost:3000')
  return url.origin === 'http://localhost:3000' ? url.pathname + url.search + url.hash : '/'
}

export async function requireUser(destination: string) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect(`/login?${new URLSearchParams({ redirect: destination })}`)
  return { supabase, user }
}
