import { costUsd, usageRows } from './usage.ts';
import { inBackground, observeAI, telemetryUser, usageScope, withTelemetry } from './telemetry.ts';

function assert(value: unknown, message = 'Assertion failed') { if (!value) throw new Error(message); }

Deno.test('cost is priced per million tokens and unknown models cost nothing', () => {
  assert(costUsd('mistral-large-latest', 1_000_000, 1_000_000) === 2);
  assert(costUsd('gpt-6-luna', 2_000_000, 500) === 0.2);
  assert(costUsd('mistral-small-latest', 610, 3) === 0.0000933);
  assert(costUsd('unknown-model', 1000, 1000) === 0);
});

Deno.test('usage rows drop ids that are not UUIDs', () => {
  const [row] = usageRows({ requestId: crypto.randomUUID(), functionName: 'test', userId: 'not-a-uuid',
    groupId: '00000000-0000-0000-0000-0000000000a1' }, [{ operation: 'embeddings', provider: 'mistral',
    model: 'mistral-embed', input_tokens: 100, output_tokens: 0, latency_ms: 3, is_error: false }]);
  assert(row.user_id === null && row.group_id === '00000000-0000-0000-0000-0000000000a1' && row.story_id === null);
});

Deno.test('each request writes its own calls once, attributed to its user, paper and story', async () => {
  const originalFetch = globalThis.fetch;
  const saved = { url: Deno.env.get('SUPABASE_URL'), key: Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'), posthog: Deno.env.get('POSTHOG_ENABLED') };
  Deno.env.set('SUPABASE_URL', 'https://db.test');
  Deno.env.set('SUPABASE_SERVICE_ROLE_KEY', 'service-key');
  Deno.env.delete('POSTHOG_ENABLED');
  const inserts: Record<string, unknown>[][] = [];
  try {
    globalThis.fetch = (async (input: unknown, init?: RequestInit) => {
      assert(String(input) === 'https://db.test/rest/v1/ai_usage');
      inserts.push(JSON.parse(String(init?.body)));
      return new Response(null, { status: 201 });
    }) as typeof fetch;
    const handler = withTelemetry('submit-source', async req => {
      const n = new URL(req.url).pathname.slice(1);
      telemetryUser(`00000000-0000-0000-0000-00000000000${n}`);
      usageScope({ groupId: `00000000-0000-0000-0000-0000000000a${n}` });
      await observeAI('mistral', 'mistral-large-latest', 'chat/completions', async () => ({ usage: { prompt_tokens: 1000, completion_tokens: 100 } }));
      usageScope({ storyId: `00000000-0000-0000-0000-0000000000b${n}` });
      try { await observeAI('openai', 'gpt-6-luna', 'clustering', async () => { throw new Error('down'); }); } catch { /* expected */ }
      return new Response('ok');
    });
    await Promise.all(['1', '2'].map(n => handler(new Request(`https://test/${n}`, { method: 'POST' }))));
    assert(inserts.length === 2, 'one insert per request');
    for (const rows of inserts) {
      assert(rows.length === 2);
      const n = String(rows[0].user_id).slice(-1);
      assert(rows.every(row => row.request_id === rows[0].request_id && row.function_name === 'submit-source'));
      assert(rows.every(row => row.group_id === `00000000-0000-0000-0000-0000000000a${n}` && row.story_id === `00000000-0000-0000-0000-0000000000b${n}`));
      assert(rows[0].cost_usd === 0.00065 && rows[0].input_tokens === 1000 && rows[0].is_error === false);
      assert(rows[1].is_error === true && rows[1].cost_usd === 0);
    }
    assert(inserts[0][0].request_id !== inserts[1][0].request_id);
    await withTelemetry('no-ai', async () => new Response('ok'))(new Request('https://test'));
    assert(inserts.length === 2, 'requests without AI calls write nothing');
  } finally {
    globalThis.fetch = originalFetch;
    for (const [name, value] of [['SUPABASE_URL', saved.url], ['SUPABASE_SERVICE_ROLE_KEY', saved.key], ['POSTHOG_ENABLED', saved.posthog]] as const) {
      if (value === undefined) Deno.env.delete(name); else Deno.env.set(name, value);
    }
  }
});

Deno.test('background work answers first and is still recorded with its request', async () => {
  const originalFetch = globalThis.fetch;
  const saved = { url: Deno.env.get('SUPABASE_URL'), key: Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') };
  Deno.env.set('SUPABASE_URL', 'https://db.test');
  Deno.env.set('SUPABASE_SERVICE_ROLE_KEY', 'service-key');
  const runtime = globalThis as { EdgeRuntime?: { waitUntil(promise: Promise<unknown>): void } };
  const kept: Promise<unknown>[] = [];
  runtime.EdgeRuntime = { waitUntil: promise => { kept.push(promise); } };
  const inserts: Record<string, unknown>[][] = [];
  let release!: () => void;
  try {
    globalThis.fetch = (async (_input: unknown, init?: RequestInit) => {
      inserts.push(JSON.parse(String(init?.body)));
      return new Response(null, { status: 201 });
    }) as typeof fetch;
    const response = await withTelemetry('submit-source', async () => {
      telemetryUser('00000000-0000-0000-0000-000000000001');
      inBackground(new Promise<void>(resolve => { release = resolve; }).then(() =>
        observeAI('mistral', 'mistral-large-latest', 'chat/completions', async () => ({ usage: { prompt_tokens: 10, completion_tokens: 1 } }))));
      return new Response(null, { status: 202 });
    })(new Request('https://test', { method: 'POST' }));
    assert(response.status === 202 && kept.length === 1 && inserts.length === 0, 'answers before the work finishes');
    release();
    await Promise.all(kept);
    assert(inserts.length === 1 && inserts[0][0].user_id === '00000000-0000-0000-0000-000000000001');
  } finally {
    delete runtime.EdgeRuntime;
    globalThis.fetch = originalFetch;
    for (const [name, value] of [['SUPABASE_URL', saved.url], ['SUPABASE_SERVICE_ROLE_KEY', saved.key]] as const) {
      if (value === undefined) Deno.env.delete(name); else Deno.env.set(name, value);
    }
  }
});
