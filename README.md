# The Grapevine

A private newspaper curated by friends. The full product direction and decisions live in [PRODUCT_PLAN.md](PRODUCT_PLAN.md).

## Stack

- **Backend:** Supabase only — Postgres, Auth (email one-time codes), Realtime, Storage, Edge Functions. No separate app server.
- **Web:** Next.js (`web/`)
- **iOS:** SwiftUI with native Liquid Glass navigation and tabs (`ios/`, local pilot)
- **Android:** Kotlin + Jetpack Compose (`android/`, sideload APK via GitHub Releases)
- **AI:** Mistral API (titles, summaries, categories, embeddings); OpenAI Decisions API `gpt-6-luna` (same-event/conflict decisions). A self-hosted alternative (Strands Decider 2B, `model-server/`) exists but is not wired in.

## Local development

Prerequisites: macOS 14+ on Apple silicon (or supported Linux), Supabase CLI 2.121.0+ (`brew install supabase/tap/supabase`), Node 20+. Local Supabase uses the experimental native runtime; Docker is not required.

    cp .env.example .env    # add MISTRAL_API_KEY, OPENAI_API_KEY, and optional GIPHY_API_KEY
    supabase start --runtime native  # local Supabase: Postgres, Auth, Storage, Studio
    supabase db reset       # applies migrations and seed; deletes existing local data

- Supabase Studio: http://localhost:55323
- Auth emails: http://localhost:55324 (Mailpit). The local stack's email doesn't include the sign-in code; print one with `supabase/scripts/sign-in-code.sh tim@example.com`.
- API gateway: http://localhost:55321

(The Grapevine uses ports 5532x because another local Supabase project occupies the default 5432x range.)

The experimental stack keeps separate data per Git branch. Use `supabase status` for current URLs and keys and `supabase stop` to stop it. Native projects share host resources; fixed ports here support these two checkouts, not simultaneous worktrees of the same app.

`.env` is gitignored — never commit keys.

## Demo data

`supabase db reset` also loads `supabase/seed.sql`: the fictional paper "The Grapevine" from the design mockups — five members (tim@, luka@, nika@, jure@, maja@example.com), five stories with sources, messages and reactions. Sign in as any of them with a code from `supabase/scripts/sign-in-code.sh`. The group's invite code is `grapevinedemo`. Demo images live in `web/public/seed/`; run the web dev server to serve them.

## iOS simulator

Requires Xcode with the iOS 26+ SDK and XcodeGen (`brew install xcodegen`). Start Supabase, the Edge Functions, and the web server (`cd web` then `npm run dev`).

From `ios/`:

    xcodegen generate
    open Grapevine.xcodeproj

Choose the Grapevine scheme and an iPhone simulator, then Run. Use normal simulator signing: unsigned builds cannot reliably save Keychain sessions. Sign in with `tim@example.com` and the code from `supabase/scripts/sign-in-code.sh tim@example.com`.

Tests:

    xcodebuild -project Grapevine.xcodeproj -scheme Grapevine -destination 'platform=iOS Simulator,name=iPhone 17' test

The pilot includes the paper, sources/history/correction reports, chronological conversation, quoted replies, reactions, private image/video uploads, group creation/joining, invite sharing, and Keychain session refresh. Images are limited to 10 per selection; each attachment is capped at 20 MB. Visible stories refresh every 10 seconds and support pull-to-refresh.

The membership-permission regression can be run against seeded local data with `psql` (`brew install libpq`):

    "$(brew --prefix libpq)/bin/psql" postgresql://postgres:postgres@127.0.0.1:55322/postgres -v ON_ERROR_STOP=1 -f supabase/tests/membership_update.sql

Local-only configuration lives in `ios/Grapevine/Config.swift`. `localhost` works on the simulator, not a physical phone; invitation links and demo images also use localhost. Configure reachable URLs and a hosted Supabase project before distribution. No AI secret is bundled in the app.

Still required before release: GIPHY search credentials, push notifications, discussion-triggered AI summary updates, and cleanup/atomic finalization for abandoned media uploads. AI text currently regenerates on source submissions and correction reports, not new messages. Current GIF attachments render, but GIF search is unavailable without a GIPHY key. Test large videos, Dynamic Type, VoiceOver, and auth/network failure recovery on real hardware before release.

## Android app

A native Kotlin + Jetpack Compose port of the iOS app (`android/`): the same screens, the same Supabase REST calls, and the same one-ink plates. Sessions are stored encrypted (Android Keystore), and `https://thegrapevine.si/invite?code=…` links open the app.

Requires JDK 21 and the Android SDK with platform 37 (Android Studio installs both). Start Supabase as above, then forward the local ports to the emulator or a USB device — API 37 blocks apps from LAN addresses like `10.0.2.2`, so debug builds use `localhost`:

    adb reverse tcp:55321 tcp:55321    # Supabase
    adb reverse tcp:3000 tcp:3000      # demo images (`cd web && npm run dev`)
    cd android
    ./gradlew installDebug             # or open android/ in Android Studio and Run
    ./gradlew testDebugUnitTest        # ranking, dates, JWTs, attachments, plates

Sign in with `tim@example.com` and the code from `supabase/scripts/sign-in-code.sh tim@example.com`. Release builds point at the hosted project.

Every merge to `master` runs `.github/workflows/android-apk.yml`, which runs the unit tests, builds a signed release APK, and publishes it as a [GitHub Release](https://github.com/timrekelj/thegrapevine/releases) asset (`grapevine.apk`). Install: download `grapevine.apk`, allow *Install unknown apps* for your browser/file manager, then open it. Because the repo is private, you must be signed in to GitHub with access to download it.

First-time setup (one-off, maintainer):

    # 1. Generate a release keystore — back it up outside the repo; losing it
    #    means existing installs can never be updated.
    keytool -genkeypair -v -keystore release.keystore -alias grapevine \
      -keyalg RSA -keysize 2048 -validity 10000
    # 2. Add GitHub secrets (Settings → Secrets and variables → Actions):
    #    ANDROID_KEYSTORE_B64   = base64 -i release.keystore
    #    ANDROID_STORE_PASSWORD = keystore password
    #    ANDROID_KEY_ALIAS      = grapevine
    #    ANDROID_KEY_PASSWORD   = key password

When a Google Play account is available, switch the workflow from `assembleRelease` to `bundleRelease` and upload the AAB with `r0adkll/upload-google-play`.

## Hosted setup

The hosted web app (Vercel) needs `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` and `NEXT_PUBLIC_SITE_URL=https://thegrapevine.si`.

Every app signs in with an emailed one-time code, so in the hosted project (Authentication → Emails) both the **Magic Link** and **Confirm signup** templates must show the code: paste `supabase/templates/sign_in_code.html` into each.

App links: `https://thegrapevine.si/invite?code=…` and `/story/<id>` open the iOS and Android apps when installed, and the website otherwise. The sites' verification files are in `web/public/.well-known/`: `assetlinks.json` holds the Android release keystore's SHA-256 fingerprint, and `apple-app-site-association` holds Apple team `6JCT56CM9H`. Update them if either changes, or links fall back to the browser.

## Edge Functions

`supabase functions serve --env-file supabase/.env.local` runs the AI pipeline locally (submit-source, report-story). Create `supabase/.env.local` (gitignored) with MISTRAL_API_KEY and OPENAI_API_KEY (copy both from the repo-root `.env`). See `supabase/.env.example`.

## Data model

`supabase/migrations/20261012000000_initial.sql` holds the whole schema: groups, members, stories (+ history), sources, messages, media, reactions, reports, notification preferences + queue. Row level security restricts every table to group members. Notifications (new story, direct reply, mention) are created by database triggers and respect per-member preferences (defaults on/on/on/off, group mute).

Rules encoded in the schema:

- Exact duplicate URLs are rejected per group (`sources` unique on `group_id, url`).
- Replies are flat: `messages.reply_to` quotes the parent; there is no nesting.
- No splits and no member editing of AI text; reports flag a story for AI re-generation, and every generated version is kept in `story_history`.
- `stories.last_activity_at` is bumped by triggers on new sources and messages; it drives the lead slot on the front page.
