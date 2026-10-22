# The Grapevine

A private newspaper curated by friends. The full product direction and decisions live in [PRODUCT_PLAN.md](PRODUCT_PLAN.md).

## Stack

- **Backend:** Supabase only — Postgres, Auth (email one-time codes, Google, Apple, X), Realtime, Storage, Edge Functions. No separate app server.
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

## Admin dashboard

`/admin` on the web shows every user, every group with its members and stories, correction reports with each story's generated versions, and AI usage: tokens and cost per day, per model, per group, and per request, with each request expandable into its individual model calls.

Admins are listed in the `admins` table, which no API role can read. The demo seed makes tim@example.com an admin. To promote a hosted account, run this in the SQL editor:

    insert into public.admins (user_id) select id from auth.users where email = 'you@example.com';

Non-admins get a 404. All admin data comes from `admin_*` database functions that check admin status on every call; no table policy mentions admins, so an admin's own paper behaves like any member's.

AI usage is recorded by the edge functions: every model call through `observeAI` is buffered and written to `ai_usage` once per request, attributed to the member, paper, and story. Cost is priced at write time from `supabase/functions/_shared/usage.ts`; confirm those rates against the providers' pricing pages and update them when prices change. Usage before this was deployed exists only in PostHog.

The access regression runs like the membership one:

    "$(brew --prefix libpq)/bin/psql" postgresql://postgres:postgres@127.0.0.1:55322/postgres -v ON_ERROR_STOP=1 -f supabase/tests/admin_access.sql

Deleting a user keeps their sources and stories in the paper without attribution, hands each paper they owned to its longest-standing remaining member, and deletes papers left with no members. The regression:

    "$(brew --prefix libpq)/bin/psql" postgresql://postgres:postgres@127.0.0.1:55322/postgres -v ON_ERROR_STOP=1 -f supabase/tests/user_deletion.sql

## iOS simulator

Requires Xcode with the iOS 26+ SDK and XcodeGen (`brew install xcodegen`). Start Supabase, the Edge Functions, and the web server (`cd web` then `npm run dev`).

From `ios/`:

    xcodegen generate
    open Grapevine.xcodeproj

Choose the Grapevine scheme and an iPhone simulator, then Run. Use normal simulator signing: unsigned builds cannot reliably save Keychain sessions. Sign in with `tim@example.com` and the code from `supabase/scripts/sign-in-code.sh tim@example.com`.

Tests:

    xcodebuild -project Grapevine.xcodeproj -scheme Grapevine -destination 'platform=iOS Simulator,name=iPhone 17' test

The pilot includes the paper, sources/history/correction reports, chronological conversation, quoted replies, reactions, private image/video uploads, group creation/joining, invite sharing, and Keychain session refresh. Both mobile apps include a Profile tab for display-name editing, per-paper notification preferences (new stories, replies, mentions, and mute), device notification settings, support, and sign-out. Preferences sync through Supabase. Images are limited to 10 per selection; each attachment is capped at 20 MB. Visible stories refresh every 10 seconds and support pull-to-refresh.

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

Every app supports emailed one-time codes alongside Google, Apple, and X. In the hosted project (Authentication → Emails) both the **Magic Link** and **Confirm signup** templates must show the code: paste `supabase/templates/sign_in_code.html` into each.

App links: `https://thegrapevine.si/invite?code=…` and `/story/<id>` open the iOS and Android apps when installed, and the website otherwise. The sites' verification files are in `web/public/.well-known/`: `assetlinks.json` holds the Android release keystore's SHA-256 fingerprint, and `apple-app-site-association` holds Apple team `6JCT56CM9H`. Update them if either changes, or links fall back to the browser.

### Social sign-in setup

Google and X use Supabase OAuth with PKCE on all clients; Apple uses it on web and Android. iOS uses the native Apple sign-in sheet and exchanges the identity token plus the original nonce for a Supabase session; other iOS providers use the system authentication browser; Android uses the external browser and stores its pending verifier encrypted so the callback survives process recreation. Email codes remain available. No provider secret belongs in the website or mobile binaries.

1. In Supabase **Authentication → URL Configuration**, set Site URL to `https://thegrapevine.si` and allow `https://thegrapevine.si/auth/callback` (including its redirect query, e.g. `https://thegrapevine.si/auth/callback?**`) and `grapevine://auth/callback`. The web callback preserves the requested invitation/story destination and routes new users through onboarding.
2. Each provider's authorized callback/return URL is **`https://ivjaenrmhsqnsvzemoez.supabase.co/auth/v1/callback`**, not the website callback or mobile scheme.
3. **Google:** create a Web application OAuth client in Google Cloud, configure its consent screen/test users and authorized callback, then enter its client ID and secret under Supabase **Sign In / Providers → Google** and enable it.
4. **Apple:** enable Sign in with Apple for the Apple Developer App ID; create a Services ID linked to it, register `ivjaenrmhsqnsvzemoez.supabase.co` as its domain and the Supabase callback as its return URL. Create a Sign in with Apple signing key, securely retain the `.p8`, and generate a client-secret JWT. Enable Apple in Supabase with the **Services ID** as client ID and that JWT as secret. Keep the Services ID for web/Android OAuth and also add `si.timrekelj.thegrapevine` to the Apple provider’s allowed client IDs for native iOS sign-in. The iOS app declares the Sign in with Apple entitlement; enable that capability on its App ID and regenerate any manually managed provisioning profiles. **Rotate the Apple secret before it expires (at most six months).** Collect the display name in the existing onboarding form; the existing sign-in flows do not store Apple’s name response.
5. **X:** configure an OAuth 2.0 Web App with the Supabase callback, website, privacy policy and terms URLs; enable **Request email from users**. Enter the OAuth 2.0 client ID/secret under Supabase **X / Twitter (OAuth 2.0)** and enable it. The app uses provider `x`, not legacy `twitter`.

For local Google/X testing, add `http://localhost:55321/auth/v1/callback` to the provider, populate the root `.env` variables from `.env.example`, enable the corresponding `[auth.external.*]` blocks in `supabase/config.toml`, and restart Supabase. Local callback URLs, including destination queries, are already allow-listed. Apple requires HTTPS and a registered domain; test Apple against a configured hosted environment rather than localhost. Do not disable nonce checks. Local providers stay disabled by default so email-only development needs no OAuth credentials.

Before release, test each provider on web, iOS and Android: new and returning users, cancellation/retry, app return, session restoration, and signing in from an invitation/story link. Supabase can link verified matching-email identities automatically; Apple's Hide My Email can create a separate account, and this change does not add manual account merging.

Provider references: [Google](https://supabase.com/docs/guides/auth/social-login/auth-google), [Apple](https://supabase.com/docs/guides/auth/social-login/auth-apple), [X](https://supabase.com/docs/guides/auth/social-login/auth-twitter).

## Edge Functions

`supabase functions serve --env-file supabase/.env.local` runs the AI pipeline locally (submit-source, report-story). Create `supabase/.env.local` (gitignored) with MISTRAL_API_KEY and OPENAI_API_KEY (copy both from the repo-root `.env`). See `supabase/.env.example`.

## Data model

`supabase/migrations/20261012000000_initial.sql` holds the whole schema: groups, members, stories (+ history), sources, messages, media, reactions, reports, notification preferences + queue. Row level security restricts every table to group members. Notifications (new story, direct reply, mention) are created by database triggers and respect per-member preferences (defaults on/on/on/off, group mute).

Rules encoded in the schema:

- Exact duplicate URLs are rejected per group (`sources` unique on `group_id, url`).
- Replies are flat: `messages.reply_to` quotes the parent; there is no nesting.
- No splits and no member editing of AI text; reports flag a story for AI re-generation, and every generated version is kept in `story_history`.
- `stories.last_activity_at` is bumped by triggers on new sources and messages; it drives the lead slot on the front page.
