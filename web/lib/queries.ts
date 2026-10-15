import type { SupabaseClient } from '@supabase/supabase-js';
import type { PaperMember, PaperStory, RankedStory } from './types';

export const MESSAGE_WEIGHT = 3;
export const SOURCE_WEIGHT = 2;
export const RECENCY_WEIGHT = 4;
export const RECENCY_HALF_LIFE_HOURS = 24;
export const ACTIVITY_WINDOW_HOURS = 48;

export function rankStories(stories: PaperStory[], now = Date.now()): PaperStory[] {
  const cutoff = now - ACTIVITY_WINDOW_HOURS * 3_600_000;
  const score = (story: PaperStory) =>
    MESSAGE_WEIGHT * story.messages.filter(m => Date.parse(m.created_at) >= cutoff).length +
    SOURCE_WEIGHT * story.sources.filter(s => Date.parse(s.created_at) >= cutoff).length +
    RECENCY_WEIGHT * 2 ** (-Math.max(0, now - Date.parse(story.last_activity_at)) / (RECENCY_HALF_LIFE_HOURS * 3_600_000));
  const byActivity = (a: PaperStory, b: PaperStory) =>
    Date.parse(b.last_activity_at) - Date.parse(a.last_activity_at) || a.id.localeCompare(b.id);
  const [lead, ...rest] = [...stories].sort(byActivity);
  return lead ? [lead, ...rest.sort((a, b) => score(b) - score(a) || byActivity(a, b))] : [];
}

export async function getPaperStories(client: SupabaseClient, groupId: string, lastSeen: string | null, members: PaperMember[] = []): Promise<RankedStory[]> {
  // shortcut: the five-person pilot loads all story activity; use aggregate SQL before expanding to large groups.
  const { data, error } = await client.from('stories').select(`
    id,title,summary,category,last_activity_at,created_at,
    sources(id,publisher,domain,image_url,submitted_by,created_at),
    messages(id,story_id,author_id,body,created_at)
  `).eq('group_id', groupId);
  if (error) throw error;
  const seen = lastSeen ? Date.parse(lastSeen) : -Infinity;
  return rankStories(data as PaperStory[]).map(story => {
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
