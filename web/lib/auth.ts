import { cache } from 'react'
import { notFound, redirect } from 'next/navigation'
import { connection } from 'next/server'
import type { SupabaseClient } from '@supabase/supabase-js'
import { createClient } from '@/lib/supabase/server'

export { safeRedirect } from './auth-redirect'

/**
 * The signed-in member's id, from the access token. The proxy has already refreshed the session, and
 * the token's signature is checked against the project's cached public keys, so a render needs no
 * round trip to the auth server. The database checks the same token again on every query.
 */
export async function currentUserId(supabase: SupabaseClient) {
  const { data } = await supabase.auth.getClaims()
  return data?.claims.sub ?? null
}

const signIn = (destination: string) => `/login?${new URLSearchParams({ redirect: destination })}`

export async function requireUser(destination: string) {
  const supabase = await createClient()
  const userId = await currentUserId(supabase)
  if (!userId) redirect(signIn(destination))
  return { supabase, userId }
}

/** The account as the auth server holds it (email, a pending email change), for the account page. */
export async function requireAccount(destination: string) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect(signIn(destination))
  return { supabase, user }
}

// Non-admins get a plain 404 so the admin area's existence isn't revealed.
// The database functions check admin status again on every call; this guard is for the UI.
// Cached per request so the layout and the page share one check.
export const requireAdmin = cache(async () => {
  await connection()
  const supabase = await createClient()
  if (!await currentUserId(supabase)) notFound()
  const { data: isAdmin, error } = await supabase.rpc('is_admin')
  if (error || isAdmin !== true) notFound()
  return { supabase }
})
