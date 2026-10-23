import { normalizeUrl, metadata, fetchSource, previewImage, privateAddress } from './fetch-source.ts';
import { optionalText, sameSecret } from './cors.ts';
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
  const original = globalThis.fetch;
  let fetched = 0;
  globalThis.fetch = (() => { fetched++; return Promise.resolve(new Response('<title>x</title>')); }) as typeof fetch;
  try {
    for (const url of ['http://127.0.0.1/private', 'http://localhost/private', 'http://[::1]/private',
      'http://169.254.169.254/latest/meta-data', 'http://2130706433/', 'http://0x7f.1/', 'http://[::ffff:127.0.0.1]/',
      'http://[fd00::1]/', 'http://100.64.0.1/', 'http://metadata/', 'http://db.internal/', 'http://example.com:8080/']) {
      assert((await fetchSource(url)).status === 'failed');
    }
    assert(fetched === 0);
  } finally { globalThis.fetch = original; }
});

Deno.test('address classification covers IPv4, IPv6 and host names', () => {
  for (const ip of ['0.0.0.0', '10.1.2.3', '127.0.0.1', '169.254.169.254', '172.16.0.1', '172.31.255.255', '192.168.1.1',
    '100.64.0.1', '198.18.0.1', '224.0.0.1', '255.255.255.255', '::', '::1', '::ffff:7f00:1', '::ffff:10.0.0.1',
    '64:ff9b::a00:1', '2002:7f00:1::', 'fc00::1', 'fd12::1', 'fe80::1', 'fec0::1', 'ff02::1', '[::1]', 'fe80::1%eth0']) {
    assert(privateAddress(ip) === true);
  }
  for (const ip of ['8.8.8.8', '1.1.1.1', '172.32.0.1', '100.128.0.1', '2606:4700::1111', '2a00:1450:4001::200e']) {
    assert(privateAddress(ip) === false);
  }
  // public domains that merely start like private prefixes were once rejected
  for (const host of ['fcbarcelona.com', 'fdic.gov', 'fe80.example', '10news.com', 'localhost']) assert(privateAddress(host) === null);
  for (const ip of ['1.2.3', '1.2.3.256', '1::2::3', 'gggg::1']) assert(privateAddress(ip) === null);
});

Deno.test('redirects into private networks and non-HTML bodies are not read', async () => {
  const original = globalThis.fetch;
  const originalDns = Deno.resolveDns;
  try {
    // deno-lint-ignore no-explicit-any
    (Deno as any).resolveDns = () => Promise.resolve(['93.184.216.34']);
    const requested: string[] = [];
    globalThis.fetch = ((input: URL) => {
      requested.push(String(input));
      return Promise.resolve(new Response(null, { status: 302, headers: { location: 'http://169.254.169.254/' } }));
    }) as typeof fetch;
    assert((await fetchSource('https://example.com/a')).status === 'failed');
    assert(requested.length === 1);
    let cancelled = false;
    globalThis.fetch = (() => Promise.resolve(new Response(new ReadableStream({ cancel() { cancelled = true; } }),
      { headers: { 'content-type': 'video/mp4' } }))) as typeof fetch;
    assert((await fetchSource('https://example.com/video')).status === 'failed' && cancelled);
    globalThis.fetch = (() => Promise.resolve(new Response(
      `<html><head><title>Hi &amp; bye</title><meta property="og:image" content="http://10.0.0.1/x.png"></head></html>`,
      { headers: { 'content-type': 'text/html; charset=utf-8' } }))) as typeof fetch;
    const page = await fetchSource('https://example.com/page');
    assert(page.status === 'ok' && page.title === 'Hi & bye' && page.image_url === null);
    // DNS answers are checked too
    // deno-lint-ignore no-explicit-any
    (Deno as any).resolveDns = () => Promise.resolve(['10.0.0.5']);
    assert((await fetchSource('https://rebind.example.com/')).status === 'failed');
  } finally { globalThis.fetch = original; Deno.resolveDns = originalDns; }
});

Deno.test('preview images and title extraction', () => {
  const base = new URL('https://example.com/news/a');
  assert(previewImage('/img.png', base) === 'https://example.com/img.png');
  assert(previewImage(undefined, base) === null);
  for (const value of ['javascript:alert(1)', 'http://127.0.0.1/a.png', 'http://metadata/a.png', 'https://u:p@example.com/a.png']) {
    assert(previewImage(value, base) === null);
  }
  const started = performance.now();
  metadata('<title'.repeat(100_000));
  assert(performance.now() - started < 1000);
  assert(metadata('<title>Unclosed').title === '');
});

Deno.test('request helpers', async () => {
  assert(optionalText(null) === undefined && optionalText(undefined) === undefined && optionalText('x') === 'x');
  let rejected = false;
  try { optionalText(5); } catch { rejected = true; }
  assert(rejected);
  assert(await sameSecret('abc', 'abc'));
  assert(!await sameSecret('abc', 'abd') && !await sameSecret('', 'abc'));
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
