import { capture, featureEnabled, observeAI, telemetryUser, withTelemetry } from './telemetry.ts';
import { gunzipSync } from 'node:zlib';

function assert(value: unknown) { if (!value) throw new Error('Assertion failed'); }

Deno.test('telemetry isolates concurrent users, omits AI content, preserves failures and evaluates flags', async () => {
  const originalFetch = globalThis.fetch;
  const originalEnabled = Deno.env.get('POSTHOG_ENABLED');
  Deno.env.set('POSTHOG_ENABLED', 'true');
  const batches: Record<string, unknown>[] = [];
  try {
    globalThis.fetch = (async (_input: unknown, init?: RequestInit) => {
      const bytes = new Uint8Array(await new Response(init?.body).arrayBuffer());
      const text = new TextDecoder().decode(bytes[0] === 31 && bytes[1] === 139 ? gunzipSync(bytes) : bytes);
      const payload = JSON.parse(text || '{}');
      if (payload.batch) batches.push(...payload.batch);
      return new Response(JSON.stringify({ status: 1, featureFlags: { 'story-corrections-enabled': false } }), { headers: { 'Content-Type': 'application/json' } });
    }) as typeof fetch;
    const handler = withTelemetry('test', async req => {
      const id = new URL(req.url).pathname.slice(1);
      telemetryUser(id);
      assert(await featureEnabled('story-corrections-enabled') === false);
      assert(await featureEnabled('missing-flag') === true);
      const result = await observeAI('mistral', 'mistral-small-latest', 'summary', async () => ({
        usage: { prompt_tokens: 10, completion_tokens: 5 }, output: 'PRIVATE_RESPONSE', input: 'PRIVATE_PROMPT',
      }));
      assert(result.output === 'PRIVATE_RESPONSE');
      let failed = false;
      try { await observeAI('mistral', 'mistral-small-latest', 'summary', async () => { throw new Error('PRIVATE_ERROR'); }); }
      catch { failed = true; }
      assert(failed);
      capture('test_completed', { owner: id });
      return new Response('ok');
    });
    await Promise.all(['user-a', 'user-b'].map(id => handler(new Request(`https://test/${id}`, { method: 'POST' }))));
    assert(batches.length > 0);
    const text = JSON.stringify(batches);
    assert(!text.includes('PRIVATE_'));
    const completed = batches.filter(event => event.event === 'test_completed');
    assert(completed.length === 2);
    for (const event of completed) {
      const properties = event.properties as Record<string, unknown>;
      assert(event.distinct_id === properties.owner);
    }
    const generations = batches.filter(event => event.event === '$ai_generation');
    assert(generations.length === 4);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalEnabled === undefined) Deno.env.delete('POSTHOG_ENABLED'); else Deno.env.set('POSTHOG_ENABLED', originalEnabled);
  }
});

Deno.test('disabled telemetry leaves local work untouched and flags use defaults', async () => {
  Deno.env.delete('POSTHOG_ENABLED');
  const response = await withTelemetry('local-test', async () => {
    assert(await featureEnabled('missing'));
    capture('should_not_send');
    return new Response('preserved');
  })(new Request('https://test'));
  assert(await response.text() === 'preserved');
});
