import { cache } from 'react'
import { notFound, redirect } from 'next/navigation'
import { connection } from 'next/server'
import { createClient } from '@/lib/supabase/server'

export { safeRedirect } from './auth-redirect'

export async function requireUser(destination: string) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect(`/login?${new URLSearchParams({ redirect: destination })}`)
  return { supabase, user }
}

// Non-admins get a plain 404 so the admin area's existence isn't revealed.
// The database functions check admin status again on every call; this guard is for the UI.
// Cached per request so the layout and the page share one check.
export const requireAdmin = cache(async () => {
  await connection()
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) notFound()
  const { data: isAdmin, error } = await supabase.rpc('is_admin')
  if (error || isAdmin !== true) notFound()
  return { supabase, user }
})
