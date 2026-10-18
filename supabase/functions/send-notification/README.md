# Push delivery

iOS and Android use the lowercase Supabase user UUID as the OneSignal external
ID on sign-in and session restoration, and detach the device on sign-out.
Existing database triggers create new-story, direct-reply and mention events,
including events created from the web. Existing preferences and group mute apply.
Digests remain off: there is no scheduled digest generator yet.

## Deploy

1. Apply migrations with `supabase db push`.
2. Set `ONESIGNAL_REST_API_KEY` (the OneSignal **App API key**, never a client
   credential) and `PUSH_WEBHOOK_SECRET` (generate with `openssl rand -hex 32`)
   in Supabase Edge Function secrets. Never put either in a mobile build.
3. Deploy: `supabase functions deploy send-notification --no-verify-jwt`.
   The function checks its own secret; ordinary user JWTs cannot call it.
4. In Supabase Database Webhooks create an **INSERT** webhook on
   `public.notifications`, POST to
   `https://<project-ref>.supabase.co/functions/v1/send-notification`.
   Set `Content-Type: application/json` and `x-push-secret: <PUSH_WEBHOOK_SECRET>`.
   Use a 15000 ms timeout. The webhook's default JSON payload is sufficient.
5. Configure APNs and FCM v1 credentials in OneSignal, if not already configured.

Push text is generic (no names, story titles or message text on the lock screen).
Tapping opens the existing `/story/<uuid>` app/web link. Current membership,
preferences, read state and a 24-hour age limit are checked before dispatch.
A reply plus mention to the same recipient sends only the reply push.

## Verify and retry

Sign in on both mobile platforms; their OneSignal subscriptions should share
the same external ID. From another member, create a story, reply, and mention.
Check delivery and story opening, then test mute and sign-out/account switching.

Inspect Edge Function logs and Database Webhook responses for failures.
Replay a failed webhook with the same notification ID; OneSignal's idempotency
key prevents duplicate sends. Do not change the ID when retrying.

shortcut: Database Webhooks do not provide a durable automatic retry queue;
retry failed deliveries manually within 24 hours, and add an outbox worker
when the pilot needs guaranteed retries. Existing in-app notifications survive
push failures. Notifications created before the webhook is enabled are not sent.

Run checks: `deno test --allow-env --allow-net supabase/functions/send-notification/`.
