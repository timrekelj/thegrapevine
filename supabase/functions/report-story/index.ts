import { body, errorResponse, HttpError, json, optionalText, uuid } from '../_shared/cors.ts';
import { caller, requireMember, supabase } from '../_shared/supabase.ts';
import { generate } from '../_shared/editorial.ts';
import { embed } from '../_shared/mistral.ts';
import { storyText } from '../_shared/clustering.ts';
import { capture, featureEnabled, withTelemetry } from '../_shared/telemetry.ts';

Deno.serve(withTelemetry('report-story', async req => {
  if (req.method === 'OPTIONS') return json({});
  if (req.method !== 'POST') return json({ error: 'POST required' }, 405);
  try {
    const user = await caller(req);
    const input = await body(req);
    if (!uuid(input.story_id) || !['summary', 'title', 'category'].includes(input.target)) throw new HttpError(400, 'Invalid story_id or target');
    const note = optionalText(input.note);
    const { data: story, error: storyError } = await supabase.from('stories').select('*').eq('id', input.story_id).maybeSingle();
    if (storyError) throw storyError;
    if (!story) throw new HttpError(404, 'Story unavailable');
    await requireMember(story.group_id, user.id);
    if (!await featureEnabled('story-corrections-enabled')) throw new HttpError(503, 'Corrections are temporarily unavailable.');
    const { data: report, error: reportError } = await supabase.from('reports').insert({
      story_id: story.id, reporter_id: user.id, target: input.target, note,
    }).select('id').single();
    if (reportError) throw reportError;
    const { error: flagError } = await supabase.from('stories').update({ flagged: true }).eq('id', story.id);
    if (flagError) throw flagError;
    const { data: sources, error: sourcesError } = await supabase.from('sources').select('*').eq('story_id', story.id);
    if (sourcesError) throw sourcesError;
    const value = await generate(input.target, sources!, false, note);
    const updated = { title: story.title, summary: story.summary, category: story.category, [input.target]: value };
    const embedding = input.target === 'category' ? story.embedding : await embed(storyText(updated));
    const { error: historyError } = await supabase.from('story_history').insert({ story_id: story.id, ...updated });
    if (historyError) throw historyError;
    const { error: resolvedError } = await supabase.from('reports').update({ status: 'resolved', resolved_at: new Date().toISOString() }).eq('id', report.id);
    if (resolvedError) throw resolvedError;
    const { data: open, error: openError } = await supabase.from('reports').select('id').eq('story_id', story.id).eq('status', 'open').limit(1);
    if (openError) throw openError;
    const { data, error: updateError } = await supabase.from('stories').update({ ...updated, embedding,
      flagged: !!open?.length, last_activity_at: new Date().toISOString() }).eq('id', story.id).select('*').single();
    if (updateError) throw updateError;
    capture('story_reported', { target: input.target });
    return json(data);
  } catch (error) { return errorResponse(error); }
}));
