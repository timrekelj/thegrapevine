import { body, errorResponse, HttpError, json, optionalText, uuid } from '../_shared/cors.ts';
import { caller, check, rateLimit, requireMember, supabase } from '../_shared/supabase.ts';
import { fetchSource, normalizeUrl } from '../_shared/fetch-source.ts';
import { cosineSimilarity, decide, sourceText, storyText, vector } from '../_shared/clustering.ts';
import { embed } from '../_shared/mistral.ts';
import { EDITORIAL_SOURCE, generate } from '../_shared/editorial.ts';
import { capture, featureEnabled, usageScope, withTelemetry } from '../_shared/telemetry.ts';

// Each submission costs a fetch and several model calls; this is generous for a friend group.
const SUBMISSIONS_PER_WINDOW = 30;
const WINDOW_MINUTES = 10;
const SUBMISSIONS_PER_DAY = 200;

type Candidate = { id: string; title: string; summary: string; embedding: string | number[] | null };

Deno.serve(withTelemetry('submit-source', async req => {
  if (req.method === 'OPTIONS') return json({});
  if (req.method !== 'POST') return json({ error: 'POST required' }, 405);
  let placeholderId: string | undefined;
  let savedSource: { id: string; story_id: string } | undefined;
  try {
    const user = await caller(req);
    const input = await body(req);
    if (!uuid(input.group_id)) throw new HttpError(400, 'Invalid group_id');
    const groupId: string = input.group_id;
    const url = normalizeUrl(input.url);
    const comment = optionalText(input.comment);
    const duplicate = () => check(supabase.from('sources').select('id,story_id')
      .eq('group_id', groupId).eq('url', url).maybeSingle());
    // Membership is checked before anything about the paper (including a duplicate) is returned.
    const [, existing] = await Promise.all([requireMember(groupId, user.id), duplicate()]);
    usageScope({ groupId });
    if (existing) {
      capture('source_submitted', { result: 'duplicate' });
      return json({ status: 'duplicate', story_id: existing.story_id, source_id: existing.id });
    }
    await rateLimit('submit-source', user.id, SUBMISSIONS_PER_WINDOW, WINDOW_MINUTES, SUBMISSIONS_PER_DAY);
    const fetched = await fetchSource(url);
    // sources.story_id is required: preserve the card before any AI call, then discard this story if joined.
    // The placeholder is flagged, so it is announced to the paper only once it is published below.
    const placeholder = (await check(supabase.from('stories').insert({
      group_id: groupId, created_by: user.id, title: fetched.title || fetched.domain,
      summary: 'Summary failed', flagged: true,
    }).select('id,title').single()))!;
    placeholderId = placeholder.id;
    usageScope({ storyId: placeholder.id });
    const { data: source, error: sourceError } = await supabase.from('sources').insert({
      story_id: placeholder.id, group_id: groupId, url, submitted_by: user.id,
      submitter_comment: comment, domain: fetched.domain, publisher: fetched.publisher,
      source_title: fetched.title, excerpt: fetched.excerpt, image_url: fetched.image_url,
      author: fetched.author, extraction_status: fetched.status,
    }).select('id,story_id,source_title,excerpt').single();
    if (sourceError) {
      await check(supabase.from('stories').delete().eq('id', placeholder.id));
      placeholderId = undefined;
      if (sourceError.code === '23505') {
        const other = await duplicate();
        if (other) return json({ status: 'duplicate', story_id: other.story_id, source_id: other.id });
      }
      throw sourceError;
    }
    savedSource = source;
    // A source with no readable text (failed fetch, deleted post) cannot be clustered:
    // it becomes a standalone card so the URL and the member's comment are never lost.
    const text = sourceText(source).trim();
    const [incoming, stories, clustering] = await Promise.all([
      text ? embed(text) : null,
      text ? check(supabase.from('stories').select('id,title,summary,embedding')
        .eq('group_id', groupId).neq('id', placeholder.id)
        .order('last_activity_at', { ascending: false }).limit(20)) as Promise<Candidate[]> : [],
      text ? featureEnabled('ai-clustering-enabled') : false,
    ]);
    const candidates: { story: Candidate; similarity: number }[] = [];
    if (incoming) {
      const [, scored] = await Promise.all([
        check(supabase.from('sources').update({ embedding: incoming }).eq('id', source.id)),
        // Older stories may predate embeddings: embed and store those concurrently.
        Promise.all((clustering ? stories : []).map(async story => {
          let embedding = story.embedding ? vector(story.embedding) : null;
          if (!embedding) {
            embedding = await embed(storyText(story));
            await check(supabase.from('stories').update({ embedding }).eq('id', story.id));
          }
          return { story, similarity: cosineSimilarity(incoming, embedding) };
        })),
      ]);
      candidates.push(...scored.filter(candidate => candidate.similarity >= 0.72));
    }
    let chosen: { id: string; title: string } = placeholder;
    let conflict = false;
    // Sequential on purpose: most submissions match the first candidate, and each decision is billed.
    for (const candidate of candidates.sort((a, b) => b.similarity - a.similarity).slice(0, 3)) {
      const decision = await decide(`${text} vs ${storyText(candidate.story)}`, candidate.similarity);
      if (decision.same_event) { chosen = candidate.story; conflict = decision.conflict; break; }
    }
    const joined = chosen.id !== placeholder.id;
    usageScope({ storyId: chosen.id });
    if (joined) {
      // Move the source before deleting the placeholder: the delete cascades to its sources.
      await check(supabase.from('sources').update({ story_id: chosen.id }).eq('id', source.id));
      savedSource = { id: source.id, story_id: chosen.id };
    }
    const [sources] = await Promise.all([
      check(supabase.from('sources').select(EDITORIAL_SOURCE).eq('story_id', chosen.id)),
      joined ? check(supabase.from('stories').delete().eq('id', placeholder.id)) : null,
    ]);
    let title: string;
    let summary: string;
    let category: string | null = null;
    let embedding: number[] | null = null;
    if (text) {
      [title, summary, category] = await Promise.all([
        joined ? chosen.title : generate('title', sources ?? []),
        generate('summary', sources ?? [], conflict),
        generate('category', sources ?? []),
      ]);
      embedding = await embed(storyText({ title, summary }));
    } else {
      title = fetched.domain;
      summary = 'This link could not be read yet. The card keeps the URL and your comment.';
    }
    await Promise.all([
      check(supabase.from('story_history').insert({ story_id: chosen.id, title, summary, category })),
      check(supabase.from('stories').update({ title, summary, category, embedding, flagged: false,
        last_activity_at: new Date().toISOString() }).eq('id', chosen.id)),
    ]);
    capture('source_submitted', { result: joined ? 'joined' : 'new_story' });
    return json({ status: joined ? 'joined' : 'new_story', story_id: chosen.id, source_id: source.id, story_title: title });
  } catch (error) {
    capture('source_submission_failed', { source_preserved: !!savedSource });
    if (savedSource) {
      console.error('AI organization failed after preserving source', error instanceof Error ? error.message : 'Database error');
      const { error: recoveryError } = await supabase.from('stories').update({ flagged: true, last_activity_at: new Date().toISOString() }).eq('id', savedSource.story_id);
      if (recoveryError) console.error('Could not mark preserved story for recovery');
      return json({ status: 'ai_failed', story_id: savedSource.story_id, source_id: savedSource.id,
        error: 'Source saved; AI organization failed. Report the story to retry generation.' }, 503);
    }
    if (placeholderId) console.error('Unfinished placeholder story', placeholderId);
    return errorResponse(error);
  }
}));
