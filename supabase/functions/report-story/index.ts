import { body, errorResponse, HttpError, json, optionalText, uuid } from '../_shared/cors.ts';
import { caller, rateLimit, requireMember, supabase } from '../_shared/supabase.ts';
import { generate } from '../_shared/editorial.ts';
import { embed } from '../_shared/mistral.ts';
import { storyText } from '../_shared/clustering.ts';
import { capture, featureEnabled, usageScope, withTelemetry } from '../_shared/telemetry.ts';

// Every report regenerates part of the story with a model call.
const REPORTS_PER_WINDOW = 10;
const WINDOW_MINUTES = 10;
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
    const { data: story, error: storyError } = await supabase.from('stories')
      .select('id,group_id,title,summary,category,embedding').eq('id', input.story_id).maybeSingle();
    if (storyError) throw storyError;
    if (!story) throw new HttpError(404, 'Story unavailable');
    await requireMember(story.group_id, user.id);
    usageScope({ groupId: story.group_id, storyId: story.id });
    const [enabled] = await Promise.all([
      featureEnabled('story-corrections-enabled'),
      rateLimit('reports', 'reporter_id', user.id, REPORTS_PER_WINDOW, WINDOW_MINUTES),
    ]);
    if (!enabled) throw new HttpError(503, 'Corrections are temporarily unavailable.');
    const { data: report, error: reportError } = await supabase.from('reports').insert({
      story_id: story.id, reporter_id: user.id, target, note,
    }).select('id').single();
    if (reportError) throw reportError;
    const [{ error: flagError }, { data: sources, error: sourcesError }] = await Promise.all([
      supabase.from('stories').update({ flagged: true }).eq('id', story.id),
      supabase.from('sources').select('source_title,excerpt,publisher,extraction_status').eq('story_id', story.id),
    ]);
    if (flagError) throw flagError;
    if (sourcesError) throw sourcesError;
    const value = await generate(target, sources ?? [], false, note);
    const updated = { title: story.title, summary: story.summary, category: story.category, [target]: value };
    const embedding = target === 'category' ? story.embedding : await embed(storyText(updated));
    const [{ error: historyError }, { error: resolvedError }] = await Promise.all([
      supabase.from('story_history').insert({ story_id: story.id, ...updated }),
      supabase.from('reports').update({ status: 'resolved', resolved_at: new Date().toISOString() }).eq('id', report.id),
    ]);
    if (historyError) throw historyError;
    if (resolvedError) throw resolvedError;
    const { data: open, error: openError } = await supabase.from('reports').select('id').eq('story_id', story.id).eq('status', 'open').limit(1);
    if (openError) throw openError;
    const { data, error: updateError } = await supabase.from('stories').update({ ...updated, embedding,
      flagged: !!open?.length, last_activity_at: new Date().toISOString() }).eq('id', story.id).select(STORY_COLUMNS).single();
    if (updateError) throw updateError;
    capture('story_reported', { target });
    return json(data);
  } catch (error) { return errorResponse(error); }
}));
