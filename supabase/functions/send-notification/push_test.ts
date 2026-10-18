import { pushPayload } from './push.ts';

Deno.env.set('SUPABASE_URL', 'https://test.supabase.co');
Deno.env.set('SUPABASE_SERVICE_ROLE_KEY', 'test-service-key');
Deno.env.set('PUSH_WEBHOOK_SECRET', 'test-webhook-secret');
Deno.env.set('ONESIGNAL_REST_API_KEY', 'test-app-key');
const { handleNotification } = await import('./index.ts');

function assert(value: unknown) { if (!value) throw new Error('Assertion failed'); }
const notification = {
  id: 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa',
  user_id: 'BBBBBBBB-BBBB-4BBB-BBBB-BBBBBBBBBBBB',
  story_id: 'cccccccc-cccc-4ccc-cccc-cccccccccccc',
  group_id: 'dddddddd-dddd-4ddd-dddd-dddddddddddd',
  message_id: 'eeeeeeee-eeee-4eee-eeee-eeeeeeeeeeee',
  kind: 'direct_reply' as const,
  read: false,
  created_at: new Date().toISOString(),
};

function request(secret = 'test-webhook-secret') {
  return new Request('https://example.com', {
    method: 'POST', headers: { 'x-push-secret': secret },
    body: JSON.stringify({ type: 'INSERT', schema: 'public', table: 'notifications', record: { id: notification.id } }),
  });
}

Deno.test('push targets the canonical user, preserves retry key and opens the story', () => {
  for (const kind of ['new_story', 'direct_reply', 'mention'] as const) {
    const payload = pushPayload({ ...notification, kind });
    assert(payload.include_aliases.external_id[0] === notification.user_id.toLowerCase());
    assert(payload.idempotency_key === notification.id);
    assert(payload.url.endsWith(`/story/${notification.story_id}`));
    assert(payload.contents.en.length > 0 && payload.target_channel === 'push');
  }
});

Deno.test('webhook rejects missing/wrong secret before querying the database', async () => {
  assert((await handleNotification(request(''))).status === 401);
  assert((await handleNotification(request('wrong'))).status === 401);
});

Deno.test('delivery checks membership and preferences; skips read, old, muted and duplicate events', async () => {
  const original = globalThis.fetch;
  try {
    const cases = [
      { row: notification, member: true, enabled: true, send: true },
      { row: notification, member: false, enabled: true, send: false },
      { row: notification, member: true, enabled: false, send: false },
      { row: { ...notification, read: true }, member: true, enabled: true, send: false },
      { row: { ...notification, created_at: '2020-01-01T00:00:00Z' }, member: true, enabled: true, send: false },
      { row: { ...notification, kind: 'digest' }, member: true, enabled: true, send: false },
      { row: { ...notification, kind: 'mention' }, member: true, enabled: true, send: false },
      { row: notification, member: true, enabled: true, send: true, fail: true },
    ];
    for (const example of cases) {
      let sent = 0;
      globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
        const url = String(input instanceof Request ? input.url : input);
        let value: unknown;
        if (url.startsWith('https://api.onesignal.com/')) {
          sent++;
          if ('fail' in example) return new Response('{}', { status: 503 });
          assert(new Headers(init?.headers).get('Authorization') === 'Key test-app-key');
          assert(JSON.parse(String(init?.body)).idempotency_key === notification.id);
          value = { id: 'accepted' };
        } else if (url.includes('/rpc/notifications_enabled')) value = example.enabled;
        else if (url.includes('/group_members')) value = example.member ? { user_id: notification.user_id } : null;
        else if (url.includes('kind=eq.direct_reply')) value = [{ id: 'existing-reply' }];
        else value = example.row;
        return new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } });
      }) as typeof fetch;
      const response = await handleNotification(request());
      assert(response.status === ('fail' in example ? 502 : 200));
      assert(sent === (example.send ? 1 : 0));
      await response.body?.cancel();
    }
  } finally { globalThis.fetch = original; }
});
