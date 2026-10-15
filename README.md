# The Grapevine

A private newspaper curated by friends. The full product direction and decisions live in [PRODUCT_PLAN.md](PRODUCT_PLAN.md).

## Stack

- **Backend:** Supabase only — Postgres, Auth (magic links), Realtime, Storage, Edge Functions. No separate app server.
- **Web:** Next.js (`web/`)
- **iOS:** SwiftUI with native Liquid Glass navigation and tabs (`ios/`, local pilot)
- **Android:** Jetpack Compose (planned, `android/`)
- **AI:** Mistral API (titles, summaries, categories, embeddings); OpenAI Decisions API `gpt-6-luna` (same-event/conflict decisions). A self-hosted alternative (Strands Decider 2B, `model-server/`) exists but is not wired in.

## Local development

Prerequisites: Docker, Supabase CLI (`brew install supabase/tap/supabase`), Node 20+.

    cp .env.example .env    # add MISTRAL_API_KEY, OPENAI_API_KEY, and optional GIPHY_API_KEY
    supabase start          # local Supabase: Postgres, Auth, Storage, Studio
    supabase db reset       # applies migrations and seed; deletes existing local data

- Supabase Studio: http://localhost:55323
- Magic-link emails: http://localhost:55324 (Mailpit)
- API gateway: http://localhost:55321

(The Grapevine uses ports 5532x because another local Supabase project occupies the default 5432x range.)

`.env` is gitignored — never commit keys.

## Demo data

`supabase db reset` also loads `supabase/seed.sql`: the fictional paper "The Grapevine" from the design mockups — five members (tim@, luka@, nika@, jure@, maja@example.com), five stories with sources, messages and reactions. Log in as any of them via magic link; the email lands in Mailpit (http://localhost:55324). The group's invite code is `grapevinedemo`. Demo images live in `web/public/seed/`; run the web dev server to serve them.

## iOS simulator

Requires Xcode with the iOS 26+ SDK and XcodeGen (`brew install xcodegen`). Start Supabase, the Edge Functions, and the web server (`cd web` then `npm run dev`).

From `ios/`:

    xcodegen generate
    open Grapevine.xcodeproj

Choose the Grapevine scheme and an iPhone simulator, then Run. Use normal simulator signing: unsigned builds cannot reliably save Keychain sessions. Sign in with `tim@example.com`, open its Mailpit magic link in simulator Safari, and approve opening The Grapevine.

Tests:

    xcodebuild -project Grapevine.xcodeproj -scheme Grapevine -destination 'platform=iOS Simulator,name=iPhone 17' test

The pilot includes the paper, sources/history/correction reports, chronological conversation, quoted replies, reactions, private image/video uploads, group creation/joining, invite sharing, and Keychain session refresh. Images are limited to 10 per selection; each attachment is capped at 20 MB. Visible stories refresh every 10 seconds and support pull-to-refresh.

The membership-permission regression can be run against seeded local data:

    docker exec -i supabase_db_grapevine psql -v ON_ERROR_STOP=1 -U postgres -d postgres < supabase/tests/membership_update.sql

Local-only configuration lives in `ios/Grapevine/Config.swift`. `localhost` works on the simulator, not a physical phone; invitation links and demo images also use localhost. Configure reachable URLs and a hosted Supabase project before distribution. No AI secret is bundled in the app.

Still required before release: Android, GIPHY search credentials, push notifications, discussion-triggered AI summary updates, and cleanup/atomic finalization for abandoned media uploads. AI text currently regenerates on source submissions and correction reports, not new messages. Current GIF attachments render, but GIF search is unavailable without a GIPHY key. Test large videos, Dynamic Type, VoiceOver, and auth/network failure recovery on real hardware before release.

## Edge Functions

`supabase functions serve --env-file supabase/.env.local` runs the AI pipeline locally (submit-source, report-story). Create `supabase/.env.local` (gitignored) with MISTRAL_API_KEY and OPENAI_API_KEY (copy both from the repo-root `.env`). See `supabase/.env.example`.

## Data model

`supabase/migrations/20261012000000_initial.sql` holds the whole schema: groups, members, stories (+ history), sources, messages, media, reactions, reports, notification preferences + queue. Row level security restricts every table to group members. Notifications (new story, direct reply, mention) are created by database triggers and respect per-member preferences (defaults on/on/on/off, group mute).

Rules encoded in the schema:

- Exact duplicate URLs are rejected per group (`sources` unique on `group_id, url`).
- Replies are flat: `messages.reply_to` quotes the parent; there is no nesting.
- No splits and no member editing of AI text; reports flag a story for AI re-generation, and every generated version is kept in `story_history`.
- `stories.last_activity_at` is bumped by triggers on new sources and messages; it drives the lead slot on the front page.
