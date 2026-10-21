import type { SupabaseClient } from '@supabase/supabase-js'
import { captureServer } from '@/lib/telemetry-server'

/**
 * Creates a paper with a caller-chosen ID and makes the user its owner. Idempotent per ID, so a retry
 * after a failed membership insert never creates a second paper. Returns an error message, or null.
 */
export async function createPaper(supabase: SupabaseClient, userId: string, groupId: string, name: string): Promise<string | null> {
  if (!name || name.length > 100) return 'Choose a paper name between 1 and 100 characters.'
  const { data: existing, error: lookupError } = await supabase.from('groups').select('id').eq('id', groupId).eq('created_by', userId).maybeSingle()
  if (lookupError) return 'The paper could not be checked. Please try again.'
  if (!existing) {
    const { error } = await supabase.from('groups').insert({ id: groupId, name, created_by: userId })
    if (error) return error.message
  }
  const { error } = await supabase.from('group_members').insert({ group_id: groupId, user_id: userId, role: 'owner' })
  if (error && error.code !== '23505') return 'Your paper was created, but membership could not be saved. Try again to finish joining it.'
  if (!error) await captureServer(userId, 'paper_created')
  return null
}

/** Saves the display name and its monogram. Returns an error message, or null. */
export async function saveDisplayName(supabase: SupabaseClient, userId: string, name: string): Promise<string | null> {
  if (!name || name.length > 80) return 'Choose a name between 1 and 80 characters.'
  const { data, error } = await supabase.from('profiles').update({ display_name: name, monogram: Array.from(name)[0].toUpperCase() }).eq('id', userId).select('id').single()
  return error || !data ? 'Your name could not be saved. Please try again.' : null
}
