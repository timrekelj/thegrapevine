# The Grapevine — web

The private newspaper on the web: Next.js 16 (App Router) on Supabase. The repository README
covers running the whole stack; this app needs a local Supabase (`supabase start`) or a hosted project.

```bash
cp .env.local.example .env.local   # then fill in the keys
npm install
npm run dev     # http://localhost:3000
npm test        # unit tests for the pure logic (ranking, redirects, formatting, telemetry)
npm run lint
```

Environment:

- `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` — the Supabase project.
- `NEXT_PUBLIC_SITE_URL` — the public origin (OAuth callbacks, redirects, link previews). Defaults to `http://localhost:3000`.
- `GIPHY_API_KEY` — optional; enables GIF search in conversations. Server-only.
- `POSTHOG_CLI_API_KEY` — optional, build-time only; uploads source maps to PostHog.

Telemetry is sent only from production builds (`lib/telemetry-config.ts`).
