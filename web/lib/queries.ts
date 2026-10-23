import type { SupabaseClient } from '@supabase/supabase-js';
import type { PaperMember, PaperStory, RankedStory } from './types';

export const MESSAGE_WEIGHT = 3;
export const SOURCE_WEIGHT = 2;
export const RECENCY_WEIGHT = 4;
export const RECENCY_HALF_LIFE_HOURS = 24;
export const ACTIVITY_WINDOW_HOURS = 48;

type Rankable = { id: string; last_activity_at: string; sources: { created_at: string }[]; messages: { created_at: string }[] };

export function rankStories<T extends Rankable>(stories: T[], now = Date.now()): T[] {
  const cutoff = now - ACTIVITY_WINDOW_HOURS * 3_600_000;
  const score = (story: T) =>
    MESSAGE_WEIGHT * story.messages.filter(m => Date.parse(m.created_at) >= cutoff).length +
    SOURCE_WEIGHT * story.sources.filter(s => Date.parse(s.created_at) >= cutoff).length +
    RECENCY_WEIGHT * 2 ** (-Math.max(0, now - Date.parse(story.last_activity_at)) / (RECENCY_HALF_LIFE_HOURS * 3_600_000));
  const byActivity = (a: T, b: T) =>
    Date.parse(b.last_activity_at) - Date.parse(a.last_activity_at) || a.id.localeCompare(b.id);
  const [lead, ...rest] = [...stories].sort(byActivity);
  return lead ? [lead, ...rest.sort((a, b) => score(b) - score(a) || byActivity(a, b))] : [];
}

// shortcut: the five-person pilot loads all story activity; use aggregate SQL before expanding to large groups.
export async function loadPaperStories(client: SupabaseClient, groupId: string): Promise<PaperStory[]> {
  const { data, error } = await client.from('stories').select(`
    id,title,summary,category,last_activity_at,created_at,
    sources(id,publisher,domain,image_url,submitted_by,created_at),
    messages(id,story_id,author_id,body,created_at)
  `).eq('group_id', groupId);
  if (error) throw error;
  return data as PaperStory[];
}

/** Ranks an edition and marks what is new since `lastSeen`. */
export function printEdition(stories: PaperStory[], lastSeen: string | null, members: PaperMember[], now = Date.now()): RankedStory[] {
  const seen = lastSeen ? Date.parse(lastSeen) : -Infinity;
  return rankStories(stories, now).map(story => {
    const sources = [...story.sources].sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || a.id.localeCompare(b.id));
    return {
      ...story, sources,
      sourceCount: sources.length, messageCount: story.messages.length,
      publishers: [...new Set(sources.map(s => s.publisher || s.domain))],
      newSources: sources.filter(s => Date.parse(s.created_at) > seen).length,
      newMessages: story.messages.filter(m => Date.parse(m.created_at) > seen).length,
      submitters: members.filter(m => sources.some(s => s.submitted_by === m.id)),
      imageUrl: sources.find(s => s.image_url)?.image_url ?? null,
    };
  });
}

/** The edition's order alone, for the later-edition check: only the timestamps the ranking reads. */
export async function loadEditionOrder(client: SupabaseClient, groupId: string): Promise<string[]> {
  const { data, error } = await client.from('stories')
    .select('id,last_activity_at,sources(created_at),messages(created_at)').eq('group_id', groupId);
  if (error) throw error;
  return rankStories(data as Rankable[]).map(story => story.id);
}
