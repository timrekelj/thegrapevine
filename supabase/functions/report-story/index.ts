import { body, errorResponse, HttpError, json, optionalText, uuid } from '../_shared/cors.ts';
import { caller, check, rateLimit, requireMember, supabase } from '../_shared/supabase.ts';
import { EDITORIAL_SOURCE, generate } from '../_shared/editorial.ts';
import { embed } from '../_shared/mistral.ts';
import { storyText } from '../_shared/clustering.ts';
import { capture, featureEnabled, usageScope, withTelemetry } from '../_shared/telemetry.ts';

// Every report regenerates part of the story with a model call.
const REPORTS_PER_WINDOW = 10;
const WINDOW_MINUTES = 10;
const REPORTS_PER_DAY = 50;
// Returned to the apps: the story without its embedding vector.
const STORY_COLUMNS = 'id,group_id,title,summary,category,flagged,last_activity_at,created_at';

Deno.serve(withTelemetry('report-story', async req => {
  if (req.method === 'OPTIONS') return json({});
  if (req.method !== 'POST') return json({ error: 'POST required' }, 405);
  try {
    const user = await caller(req);
    const input = await body(req);
    if (!uuid(input.story_id) || !['summary', 'title', 'category'].includes(input.target)) throw new HttpError(400, 'Invalid story_id or target');
    const target: 'summary' | 'title' | 'category' = input.target;
    const note = optionalText(input.note);
    const story = await check(supabase.from('stories')
      .select('id,group_id,title,summary,category').eq('id', input.story_id).maybeSingle());
    if (!story) throw new HttpError(404, 'Story unavailable');
    await requireMember(story.group_id, user.id);
    usageScope({ groupId: story.group_id, storyId: story.id });
    const [enabled] = await Promise.all([
      featureEnabled('story-corrections-enabled'),
      rateLimit('report-story', user.id, REPORTS_PER_WINDOW, WINDOW_MINUTES, REPORTS_PER_DAY),
    ]);
    if (!enabled) throw new HttpError(503, 'Corrections are temporarily unavailable.');
    const [report, , sources] = await Promise.all([
      check(supabase.from('reports').insert({ story_id: story.id, reporter_id: user.id, target, note }).select('id').single()),
      check(supabase.from('stories').update({ flagged: true }).eq('id', story.id)),
      check(supabase.from('sources').select(EDITORIAL_SOURCE).eq('story_id', story.id)),
    ]);
    const value = await generate(target, sources ?? [], false, note);
    const updated = { title: story.title, summary: story.summary, category: story.category, [target]: value };
    // A category does not change the story text, so its embedding stays as it is.
    const embedding = target === 'category' ? undefined : await embed(storyText(updated));
    const [, , open] = await Promise.all([
      check(supabase.from('story_history').insert({ story_id: story.id, ...updated })),
      check(supabase.from('reports').update({ status: 'resolved', resolved_at: new Date().toISOString() }).eq('id', report!.id)),
      check(supabase.from('reports').select('id').eq('story_id', story.id).eq('status', 'open').neq('id', report!.id).limit(1)),
    ]);
    const data = await check(supabase.from('stories').update({ ...updated, ...(embedding && { embedding }),
      flagged: !!open?.length, last_activity_at: new Date().toISOString() }).eq('id', story.id).select(STORY_COLUMNS).single());
    capture('story_reported', { target });
    return json(data);
  } catch (error) { return errorResponse(error); }
}));
