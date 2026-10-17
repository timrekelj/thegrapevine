import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'

// Only same-origin paths may survive the email round trip.
export function safeRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(value)) return '/'
  const base = process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000'
  const url = new URL(value, base)
  return url.origin === new URL(base).origin ? url.pathname + url.search + url.hash : '/'
}

export async function requireUser(destination: string) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect(`/login?${new URLSearchParams({ redirect: destination })}`)
  return { supabase, user }
}
