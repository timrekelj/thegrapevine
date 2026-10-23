import type { SupabaseClient } from '@supabase/supabase-js'
import type { PaperMember } from '@/lib/types'

type Person = PaperMember
export type Story = { id: string; group_id: string; title: string; summary: string; category: string | null; flagged: boolean; last_activity_at: string; created_at: string }
export type Source = { id: string; created_at: string; url: string; publisher: string; domain: string; author: string | null; source_title: string; image_url: string | null; submitted_by: string | null; submitter_comment: string | null; extraction_status: string }
export type Media = { id: string; kind: 'image' | 'video' | 'gif'; url: string; alt_text: string | null; position: number; display_url?: string; signed_until?: number }
type Reaction = { id: string; user_id: string; emoji: string }
export type Message = { id: string; author_id: string; body: string | null; reply_to: string | null; created_at: string; message_media: Media[]; reactions: Reaction[] }
type History = { id: string; title: string; summary: string; created_at: string }
export type StoryDetails = { sources: Source[]; messages: Message[]; history: History[]; people: Person[] }

// Named columns, never `*`: stories and sources also carry 1024-dimension embeddings and extracted
// text, which the reader never shows and which would ride along on every 30-second refresh.
const STORY = 'id,group_id,title,summary,category,flagged,last_activity_at,created_at'
const SOURCE = 'id,created_at,url,publisher,domain,author,source_title,image_url,submitted_by,submitter_comment,extraction_status'
const MESSAGE = 'id,author_id,body,reply_to,created_at,message_media(id,kind,url,alt_text,position),reactions(id,user_id,emoji)'
const SIGNED_FOR = 3600
const isPublic = (url: string) => /^https?:\/\//.test(url) || url.startsWith('/')

/**
 * A story and everything around it, in one round of parallel requests; null when it isn't readable.
 * Pass the previous messages to keep their still-valid signed URLs, so a refresh doesn't re-download every attachment.
 */
export async function loadStory(client: SupabaseClient, id: string, previous: Message[] = []): Promise<{ story: Story; details: StoryDetails } | null> {
  const [story, sources, messages, history] = await Promise.all([
    // The paper's members ride along with the story, so nothing waits for its group id.
    client.from('stories').select(`${STORY},groups(group_members(profiles(id,display_name,monogram)))`).eq('id', id).maybeSingle(),
    client.from('sources').select(SOURCE).eq('story_id', id).order('created_at', { ascending: false }),
    client.from('messages').select(MESSAGE).eq('story_id', id).order('created_at'),
    client.from('story_history').select('id,title,summary,created_at').eq('story_id', id).order('created_at', { ascending: false }),
  ])
  if (story.error) throw new Error('Could not load this story. Please try again.')
  if (!story.data) return null
  for (const result of [sources, messages, history]) if (result.error) throw new Error(result.error.message)
  const { groups, ...row } = story.data as unknown as Story & { groups: { group_members: { profiles: Person | null }[] } | null }
  const media = (messages.data as Message[]).flatMap(message => message.message_media.sort((a, b) => a.position - b.position))
  await signMedia(client, media, previous.flatMap(message => message.message_media))
  return {
    story: row,
    details: {
      sources: sources.data as Source[], messages: messages.data as Message[], history: history.data as History[],
      people: groups?.group_members.flatMap(member => member.profiles ? [member.profiles] : []) ?? [],
    },
  }
}

/** Private attachments get signed URLs in one request; ones signed earlier are reused until close to expiry. */
export async function signMedia(client: SupabaseClient, media: Media[], previous: Media[] = [], now = Date.now()) {
  const known = new Map(previous.filter(item => item.display_url && (item.signed_until ?? 0) > now).map(item => [item.url, item]))
  const unsigned: Media[] = []
  for (const item of media) {
    const reuse = known.get(item.url)
    if (isPublic(item.url)) item.display_url = item.url
    else if (reuse) Object.assign(item, { display_url: reuse.display_url, signed_until: reuse.signed_until })
    else unsigned.push(item)
  }
  if (!unsigned.length) return
  const { data, error } = await client.storage.from('media').createSignedUrls([...new Set(unsigned.map(item => item.url))], SIGNED_FOR)
  if (error) throw new Error('Could not load a private attachment. Please try again.')
  const signed = new Map(data.map(entry => [entry.path, entry.signedUrl]))
  for (const item of unsigned) {
    const url = signed.get(item.url)
    // A file that is gone (an abandoned or removed upload) prints as an unavailable attachment
    // instead of failing the whole story.
    if (!url) continue
    // Renew five minutes early so a page left open never shows an expired link.
    Object.assign(item, { display_url: url, signed_until: now + (SIGNED_FOR - 300) * 1000 })
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
