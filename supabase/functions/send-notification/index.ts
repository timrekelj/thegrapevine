import { body, HttpError, json, sameSecret, uuid } from '../_shared/cors.ts';
import { check, supabase } from '../_shared/supabase.ts';
import { isPushKind, isReceipt, preferenceFor, pushPayload, type PushKind } from './push.ts';
import { capture, featureEnabled, telemetryUser, withTelemetry } from '../_shared/telemetry.ts';

// Database webhook only. Never accept a recipient or notification text from a client.
// shortcut: failed webhooks need manual replay; add a durable outbox when automatic retries are needed.
export async function handleNotification(req: Request) {
  if (req.method !== 'POST') return json({ error: 'POST required' }, 405);
  const secret = Deno.env.get('PUSH_WEBHOOK_SECRET');
  if (!secret || !await sameSecret(req.headers.get('x-push-secret') ?? '', secret)) {
    return json({ error: 'Unauthorized' }, 401);
  }
  try {
    const input = await body(req);
    if (input.type !== 'INSERT' || input.schema !== 'public' || input.table !== 'notifications' || !uuid(input.record?.id)) {
      throw new HttpError(400, 'Expected a notifications INSERT webhook');
    }
    const notification = await check(supabase.from('notifications')
      .select('*').eq('id', input.record.id).maybeSingle());
    if (!notification || notification.read || !isPushKind(notification.kind) ||
        (!notification.story_id && !isReceipt(notification.kind)) ||
        Date.parse(notification.created_at) < Date.now() - 86400000) {
      return json({ skipped: true });
    }
    const kind: PushKind = notification.kind;
    telemetryUser(notification.user_id);
    if (!await featureEnabled('push-delivery-enabled')) return json({ skipped: true });
    // Current membership and preferences (receipts skip preferences); a reply that also mentions
    // its recipient sends only the reply.
    const [member, enabled, reply] = await Promise.all([
      check(supabase.from('group_members').select('user_id')
        .eq('group_id', notification.group_id).eq('user_id', notification.user_id).maybeSingle()),
      isReceipt(kind) || check(supabase.rpc('notifications_enabled', {
        u: notification.user_id, g: notification.group_id, which: preferenceFor[kind],
      })),
      kind === 'mention' && notification.message_id
        ? check(supabase.from('notifications').select('id').eq('message_id', notification.message_id)
          .eq('user_id', notification.user_id).eq('kind', 'direct_reply').limit(1))
        : null,
    ]);
    if (!member || !enabled || reply?.length) return json({ skipped: true });
    const key = Deno.env.get('ONESIGNAL_REST_API_KEY');
    if (!key) throw new Error('Missing ONESIGNAL_REST_API_KEY');
    const response = await fetch('https://api.onesignal.com/notifications', {
      method: 'POST',
      headers: { Authorization: `Key ${key}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(pushPayload({ ...notification, kind })),
      signal: AbortSignal.timeout(10000),
    });
    const result = await response.json();
    if (!response.ok) throw new Error(`OneSignal HTTP ${response.status}`);
    if (!result.id) throw new Error('OneSignal did not accept a notification; check recipient subscriptions');
    capture('push_sent', { kind });
    return json({ id: result.id });
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error('Push delivery failed', error instanceof Error ? error.message : 'Database error');
    return json({ error: 'Push delivery failed; retry with the same notification ID' }, 502);
  }
}

if (import.meta.main) Deno.serve(withTelemetry('send-notification', handleNotification));
