import { body, errorResponse, HttpError, json, optionalText, uuid } from '../_shared/cors.ts';
import { caller, requireMember, supabase } from '../_shared/supabase.ts';
import { fetchSource, normalizeUrl } from '../_shared/fetch-source.ts';
import { cosineSimilarity, decide, sourceText, storyText, vector } from '../_shared/clustering.ts';
import { embed } from '../_shared/mistral.ts';
import { generate } from '../_shared/editorial.ts';

Deno.serve(async req => {
  if (req.method === 'OPTIONS') return json({});
  if (req.method !== 'POST') return json({ error: 'POST required' }, 405);
  let placeholderId: string | undefined;
  let savedSource: { id: string; story_id: string } | undefined;
  try {
    const user = await caller(req);
    const input = await body(req);
    if (!uuid(input.group_id)) throw new HttpError(400, 'Invalid group_id');
    const url = normalizeUrl(input.url);
    const comment = optionalText(input.comment);
    await requireMember(input.group_id, user.id);
    const duplicate = async () => {
      const { data, error } = await supabase.from('sources').select('id,story_id').eq('group_id', input.group_id).eq('url', url).maybeSingle();
      if (error) throw error;
      return data;
    };
    const existing = await duplicate();
    if (existing) return json({ status: 'duplicate', story_id: existing.story_id, source_id: existing.id });
    const fetched = await fetchSource(url);
    // sources.story_id is required: preserve the card before any AI call, then discard this story if joined.
    const { data: placeholder, error: storyError } = await supabase.from('stories').insert({
      group_id: input.group_id, created_by: user.id, title: fetched.title || fetched.domain,
      summary: 'Summary failed', flagged: true,
    }).select('*').single();
    if (storyError) throw storyError;
    placeholderId = placeholder.id;
    const { data: source, error: sourceError } = await supabase.from('sources').insert({
      story_id: placeholder.id, group_id: input.group_id, url, submitted_by: user.id,
      submitter_comment: comment, domain: fetched.domain, publisher: fetched.publisher,
      source_title: fetched.title, excerpt: fetched.excerpt, image_url: fetched.image_url,
      author: fetched.author, extraction_status: fetched.status,
    }).select('*').single();
    if (sourceError) {
      const { error } = await supabase.from('stories').delete().eq('id', placeholder.id);
      if (error) throw error;
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
    const incoming = text ? await embed(text) : null;
    if (incoming) {
      const { error: sourceEmbeddingError } = await supabase.from('sources').update({ embedding: incoming }).eq('id', source.id);
      if (sourceEmbeddingError) throw sourceEmbeddingError;
    }
    const { data: stories, error: loadError } = await supabase.from('stories').select('*')
      .eq('group_id', input.group_id).neq('id', placeholder.id).order('last_activity_at', { ascending: false }).limit(20);
    if (loadError) throw loadError;
    const candidates = [];
    if (incoming) {
      for (const story of stories || []) {
        const embedding = story.embedding ? vector(story.embedding) : await embed(storyText(story));
        if (!story.embedding) {
          const { error } = await supabase.from('stories').update({ embedding }).eq('id', story.id);
          if (error) throw error;
        }
        const similarity = cosineSimilarity(incoming, embedding);
        if (similarity >= 0.72) candidates.push({ story, similarity });
      }
    }
    let chosen = placeholder;
    let conflict = false;
    for (const candidate of candidates.sort((a, b) => b.similarity - a.similarity).slice(0, 3)) {
      const decision = await decide(`${text} vs ${storyText(candidate.story)}`, candidate.similarity);
      if (decision.same_event) { chosen = candidate.story; conflict = decision.conflict; break; }
    }
    const joined = chosen.id !== placeholder.id;
    if (joined) {
      const { error } = await supabase.from('sources').update({ story_id: chosen.id }).eq('id', source.id);
      if (error) throw error;
      savedSource = { id: source.id, story_id: chosen.id };
      const { error: deleteError } = await supabase.from('stories').delete().eq('id', placeholder.id);
      if (deleteError) throw deleteError;
      placeholderId = undefined;
    }
    const { data: sources, error: sourcesError } = await supabase.from('sources').select('*').eq('story_id', chosen.id);
    if (sourcesError) throw sourcesError;
    let title: string;
    let summary: string;
    let category: string | null = null;
    let embedding: number[] | null = null;
    if (text) {
      title = joined ? chosen.title : await generate('title', sources!);
      summary = await generate('summary', sources!, conflict);
      category = await generate('category', sources!);
      embedding = await embed(storyText({ title, summary }));
    } else {
      title = fetched.domain;
      summary = 'This link could not be read yet. The card keeps the URL and your comment.';
    }
    const { error: historyError } = await supabase.from('story_history').insert({ story_id: chosen.id, title, summary, category });
    if (historyError) throw historyError;
    const { error: updateError } = await supabase.from('stories').update({ title, summary, category, embedding, flagged: false,
      last_activity_at: new Date().toISOString() }).eq('id', chosen.id);
    if (updateError) throw updateError;
    return json({ status: joined ? 'joined' : 'new_story', story_id: chosen.id, source_id: source.id });
  } catch (error) {
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
});
