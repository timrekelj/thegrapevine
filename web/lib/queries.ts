import type { SupabaseClient } from '@supabase/supabase-js';
import type { PaperMember, PaperStory, RankedStory } from './types';

const MESSAGE_WEIGHT = 3;
const SOURCE_WEIGHT = 2;
const RECENCY_WEIGHT = 4;
const RECENCY_HALF_LIFE_HOURS = 24;
const ACTIVITY_WINDOW_HOURS = 48;

// The front page is this week's edition: stories active in the last EDITION_DAYS, never fewer than
// EDITION_MIN (a quiet paper still has a front page) nor more than EDITION_MAX. Older stories move
// to the archive, ARCHIVE_PAGE at a time.
const EDITION_DAYS = 7;
export const EDITION_MIN = 10;
export const EDITION_MAX = 40;
const ARCHIVE_PAGE = 20;

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

/**
 * Cuts an edition from stories newest-activity first (fetched with EDITION_MAX + 1 rows).
 * `earlier` is the archive cursor: the last kept story's activity, when anything older exists.
 */
export function cutEdition<T extends { last_activity_at: string }>(newestFirst: T[], now = Date.now()): { stories: T[]; earlier: string | null } {
  const cutoff = now - EDITION_DAYS * 86_400_000;
  const stories: T[] = [];
  for (const story of newestFirst) {
    if (stories.length >= EDITION_MAX || (stories.length >= EDITION_MIN && Date.parse(story.last_activity_at) < cutoff)) break;
    stories.push(story);
  }
  return { stories, earlier: newestFirst.length > stories.length ? stories.at(-1)?.last_activity_at ?? null : null };
}

export async function loadPaperStories(client: SupabaseClient, groupId: string) {
  const { data, error } = await client.from('stories').select(`
    id,title,summary,category,last_activity_at,created_at,
    sources(id,publisher,domain,image_url,submitted_by,created_at),
    messages(id,story_id,author_id,body,created_at)
  `).eq('group_id', groupId).order('last_activity_at', { ascending: false }).limit(EDITION_MAX + 1);
  if (error) throw error;
  return cutEdition(data as PaperStory[]);
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
    .select('id,last_activity_at,sources(created_at),messages(created_at)').eq('group_id', groupId)
    .order('last_activity_at', { ascending: false }).limit(EDITION_MAX + 1);
  if (error) throw error;
  return rankStories(cutEdition(data as Rankable[]).stories).map(story => story.id);
}

type ArchivedStory = { id: string; title: string; category: string | null; last_activity_at: string; sources: { count: number }[]; messages: { count: number }[] };

/** A page of the archive: stories whose last activity is before `before`, newest first. */
export async function loadArchive(client: SupabaseClient, groupId: string, before: string) {
  const { data, error } = await client.from('stories')
    .select('id,title,category,last_activity_at,sources(count),messages(count)')
    .eq('group_id', groupId).lt('last_activity_at', before)
    .order('last_activity_at', { ascending: false }).limit(ARCHIVE_PAGE + 1);
  if (error) throw error;
  const rows = data as ArchivedStory[];
  const stories = rows.slice(0, ARCHIVE_PAGE);
  return { stories, earlier: rows.length > ARCHIVE_PAGE ? stories.at(-1)!.last_activity_at : null };
}
