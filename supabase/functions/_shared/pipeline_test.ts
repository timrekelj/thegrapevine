import { normalizeUrl, metadata, fetchSource } from './fetch-source.ts';
import { cosineSimilarity, decide, sourceText, storyText } from './clustering.ts';
import { generate } from './editorial.ts';

function assert(condition: boolean) { if (!condition) throw new Error('Assertion failed'); }

Deno.test('URL normalization and scheme validation', () => {
  assert(normalizeUrl(' HTTPS://Example.COM/news/ ') === 'https://example.com/news');
  for (const url of ['file:///etc/passwd', 'javascript:alert(1)', 'https://user:pass@example.com']) {
    let rejected = false;
    try { normalizeUrl(url); } catch { rejected = true; }
    assert(rejected);
  }
});

Deno.test('metadata handles reordered attributes and entities', () => {
  const result = metadata(`<meta content='A &amp; B' property='og:title'><meta name="author" content="Jane"><meta content="Preview" name="description">`);
  assert(result.title === 'A & B' && result.author === 'Jane' && result.excerpt === 'Preview');
});

Deno.test('cosine similarity and text bases', () => {
  assert(cosineSimilarity([1, 0], [1, 0]) === 1);
  assert(cosineSimilarity([1, 0], [0, 1]) === 0);
  assert(cosineSimilarity([0, 0], [0, 0]) === 0);
  assert(sourceText({ source_title: 'Headline', excerpt: null }) === 'Headline ');
  assert(storyText({ title: 'Headline', summary: 'Summary' }) === 'Headline Summary');
});

Deno.test('private source addresses are never fetched', async () => {
  for (const url of ['http://127.0.0.1/private', 'http://localhost/private', 'http://[::1]/private']) {
    assert((await fetchSource(url)).status === 'failed');
  }
});

Deno.test('unreadable sources are excluded without calling Mistral', async () => {
  const sources = [{ publisher: 'Example', source_title: 'Unverified claim', excerpt: 'Opinion', extraction_status: 'paywalled' }];
  assert(await generate('summary', sources) === 'No readable source material is available yet.');
  assert(await generate('title', sources) === 'Shared link awaiting readable sources');
  assert(await generate('category', sources) === 'Media');
});

Deno.test('decision response contract and conservative unavailable fallback', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = (() => Promise.resolve(new Response(JSON.stringify({
      answers: [
        { type: 'predicate', name: 'same_event', probability: 0.95 },
        { type: 'predicate', name: 'conflict', probability: 0.8 },
      ],
    })))) as typeof fetch;
    const decision = await decide('Source versus story', 0.75);
    assert(decision.same_event && decision.conflict);
    globalThis.fetch = (() => Promise.reject(new Error('Unavailable'))) as typeof fetch;
    assert(!(await decide('Source versus story', 0.879)).same_event);
    assert((await decide('Source versus story', 0.88)).same_event);
    assert(!(await decide('Source versus story', 0.95)).conflict);
  } finally { globalThis.fetch = original; }
});
