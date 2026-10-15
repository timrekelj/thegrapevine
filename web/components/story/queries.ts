import type { SupabaseClient } from '@supabase/supabase-js'
import type { PaperMember } from '@/lib/types'

export type Person = PaperMember
export type Story = { id: string; group_id: string; title: string; summary: string; category: string | null; flagged: boolean; last_activity_at: string; created_at: string }
export type Source = { id: string; created_at: string; url: string; publisher: string; domain: string; author: string | null; source_title: string; image_url: string | null; submitted_by: string; submitter_comment: string | null; extraction_status: string }
export type Media = { id: string; kind: 'image' | 'video' | 'gif'; url: string; alt_text: string | null; position: number; display_url?: string }
export type Reaction = { id: string; user_id: string; emoji: string }
export type Message = { id: string; author_id: string; body: string | null; reply_to: string | null; created_at: string; message_media: Media[]; reactions: Reaction[] }
export type History = { id: string; title: string; summary: string; created_at: string }

export async function loadStoryDetails(client: SupabaseClient, story: Story) {
  const results = await Promise.all([
    client.from('sources').select('*').eq('story_id', story.id).order('created_at', { ascending: false }),
    client.from('messages').select('*, message_media(*), reactions(*)').eq('story_id', story.id).order('created_at'),
    client.from('story_history').select('*').eq('story_id', story.id).order('created_at', { ascending: false }),
    client.from('group_members').select('profiles(id, display_name, monogram)').eq('group_id', story.group_id),
  ])
  for (const result of results) if (result.error) throw new Error(result.error.message)
  const messages = results[1].data as Message[]
  await Promise.all(messages.flatMap(message => message.message_media.map(async media => {
    if (/^https?:\/\//.test(media.url) || media.url.startsWith('/')) media.display_url = media.url
    else {
      const { data, error } = await client.storage.from('media').createSignedUrl(media.url, 3600)
      if (error) throw new Error('Could not load a private attachment. Please try again.')
      media.display_url = data.signedUrl
    }
  })))
  return {
    sources: results[0].data as Source[], messages, history: results[2].data as History[],
    people: results[3].data!.flatMap(row => row.profiles ? [row.profiles] : []) as unknown as Person[],
  }
}

export function mergeTimeline(sources: Source[], messages: Message[]) {
  return [...sources.map(source => ({ kind: 'source' as const, ...source })), ...messages.map(message => ({ kind: 'message' as const, ...message }))]
    .sort((a, b) => Date.parse(a.created_at) - Date.parse(b.created_at) || a.id.localeCompare(b.id))
}

export function validateAttachments(files: Pick<File, 'type' | 'size'>[]) {
  if (files.filter(file => file.type.startsWith('image/')).length > 10) return 'Attach no more than 10 images per message.'
  if (files.some(file => !file.type.startsWith('image/') && !file.type.startsWith('video/'))) return 'Choose image or video files only.'
  if (files.some(file => file.type.startsWith('video/') && file.size > 20 * 1024 * 1024)) return 'Videos must be 20 MB or smaller.'
  return null
}
