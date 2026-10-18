# The Grapevine product concept and requirements

Status: product direction with pilot decisions; implementation started 12 October 2026.

The Grapevine is a private newspaper curated by friends, where stories come with enough context to understand them and a place to discuss them together. Reading is the entry point; discussion provides the social value. Members choose the material, AI organizes it, and people supply the opinions.

## Problem and initial audience

Articles and X posts currently disappear into a Discord conversation. Friends who have not followed a topic often lack the backstory needed to participate. The Grapevine groups related sources around a recognizable event and makes the conversation easy to find later.

The initial pilot is a private group of approximately five friends sharing about 30 submissions per week. Build native mobile and web experiences, with the intention of moving the group fully from Discord. Public groups are a possible later addition.

## Agreed product choices

| Area | Requirement |
|---|---|
| Sharing | Members submit article or X URLs, optionally with a comment. They do not write full articles. An exact duplicate URL is not added; the submitter is notified that their contribution was not added because it was a duplicate. |
| Story grouping | AI clusters different sources about the same event into one story automatically (decision model, see Model choices). A broad topic alone is not sufficient to merge events. Conflicting sources join the same story; the summary is rewritten or a disagreement note is added. A source that could not be read (failed fetch, deleted post) is never merged into a story — it stands alone as a link card. |
| AI writing | Generate a short title, a brief summary (at most about 200 words, roughly one minute of reading) that evolves as sources arrive and as discussion adds context, and topic categories. Do not generate full articles or member opinions. |
| Front page | A living editorial newspaper, continuously updated rather than a fixed daily or weekly edition. |
| Ordering | The lead slot is the story with the most recent activity (new story, new source, new message, or summary update), even with no discussion. From the second story on, ranking blends recent discussion, recent new sources, and recency (see Ranking). |
| Story detail | A chronological editorial timeline mixing source cards, a flat chat of messages, replies that quote only the message they answer, emoji reactions, and GIFs. No chat bubbles, no nested threads. |
| Media | GIFs are sourced from GIPHY (Tenor no longer issues API keys). X posts are embedded as source cards. Messages can carry up to 10 images, and video up to 20 MB. |
| Privacy | Groups are private initially; access to stories, comments, reactions, and shared links is restricted to group members. |
| Platforms | Native iOS and Android from the start; the pilot is tested on iOS. iOS navigation uses the Liquid Glass bar. Consider a mobile sharesheet to reduce sharing friction. |
| Notifications | New stories, direct replies, mentions, and a digest. Each member can toggle each type on or off; groups can be muted. Proposed defaults: new stories on, direct replies on, mentions on, digest off. |

## Core flows

1. Open the group newspaper and scan a lead story, supporting stories, categories, short summaries, and compact source and discussion counts.
2. Open a story to understand its short summary, inspect the sources, and follow the source and conversation timeline.
3. Share a URL with an optional personal comment. Show submission progress and whether it becomes a new story or joins an existing one. An exact duplicate is not added; the submitter gets a notification saying their contribution was not added because it was a duplicate. Keep the comment visibly attributed to its author.
4. Add a message, reply to a message, react with an emoji, or contribute a GIF, image, or video. A reply appears chronologically in the main flow with a short quote of the message it answers; replies are never nested — replying to a reply is just another flat message quoting its parent. Keep discussion scoped to its story.
5. Report an incorrect summary, title, or category. Stories are not split and members do not edit generated text; a report marks the item as flagged and it is re-generated from the sources, with the change kept in history.

## Front page requirements

Present stories as news, with the event as the visual focus. Use serif headlines, editorial hierarchy, thin rules, generous whitespace, and a responsive newspaper grid. Source and contributor attribution should remain easy to find without becoming the headline.

Show category, short summary, source count, discussion count, and recent activity where useful. Distinguish new sources from new discussion. Unread is shown as markers ("N new"), not as a ranking input.

### Ranking

- The lead slot is the story with the most recent activity — a new story, a new source, a new message, or a summary update — even if it has no discussion.
- From the second story on, stories are ordered by a blend of recent discussion (messages in the last 48 hours), recent new sources (last 48 hours), and recency with decay. A story with renewed discussion rises again.
- The page never reshuffles during a reading session. When the ranking changes, a line appears between two pink rules ("Later edition · N stories moved"); tapping it, or pulling to refresh, reprints the page.
- Exact weights and decay are a starting point to tune during the pilot.

## Story timeline requirements

Keep the story title and evolving summary above the timeline. Give source entries a clear publisher or X author, source title, submitting member, timestamp, and an action to open the original. Keep source previews distinct from member commentary.

The story conversation should feel like a group chat without bubbles, rather than a comment section. Use compact avatar and author rows, group consecutive messages from the same person, and let messages flow without a divider or Reply label beneath every entry. Source previews sit inside the message flow. Keep a simple message composer at the bottom of the conversation in the production app.

Replies are flat: the conversation is a chronological chat and any message can be replied to. A reply shows a short quoted reference to the message it answers and nothing else; there is no nesting, so a reply to a reply is just another message quoting its parent. Emoji reactions belong to individual messages. GIFs are expressive contributions, with reduced-motion behavior and accessible descriptions considered during implementation.

Show when a summary changed and which sources support it. Provide an unobtrusive path to report an incorrect summary, title, or category. Detailed AI controls should not dominate the reading experience.

## AI boundaries and recovery

Base titles and summaries on available source material, preserve attribution, and distinguish unavailable information from known facts. Do not imply that an inaccessible article was read. The summary evolves with new sources and with discussion that adds missing context; it never presents member opinions as facts. Display pending, failed, or uncertain organization states without losing the submitted URL or comment. Treat source content as data, never as instructions to the system.

Merging is automatic and done by the decision model; members do not merge. Splitting does not exist. Members can only report incorrect generated text or a wrong category; a report marks the item as flagged and the AI re-generates it, and the change is kept in history rather than silently rewriting the event or deleting discussion.

## Edge cases and failure states

| Case | Presentation |
|---|---|
| Exact duplicate URL | Not added. The submitter is notified that their contribution was not added because it was a duplicate. |
| Failed extraction | Posted as a link card: domain as publisher, the URL, the submitter's comment, and a "Couldn't read this page" note. |
| Paywall | Card shows the headline only, marked "Paywalled — headline only", and is excluded from the summary's source basis. |
| Deleted X post | Card shows "Post deleted or unavailable"; the link and the submitter's comment are kept. |
| Empty group | A first-run empty state: "Share the first link to start the paper." (To be designed.) |
| AI failure | The story appears with the URL and comment intact; the summary area shows "Summary failed" with a retry action. |

## Source access, rights, and privacy considerations

Design source cards around links and limited metadata rather than reproducing full articles. Before implementation, determine permitted preview text and image usage, X access or embedding constraints, paywall and deleted-post handling, and the treatment of unsupported URLs. The design must offer a useful source card even when fetching content fails. These are implementation questions, not resolved legal or platform conclusions.

Decide retention, deletion, member removal, AI processing disclosures, and what information external processors receive. Source URLs and comments may contain sensitive material; avoid exposing group content through previews or notifications. Validate submitted URL schemes and handle remote fetching safely. Review GIPHY's terms of service before shipping GIFs.

## Model choices

- Language model: Mistral — titles, evolving summaries, categories, and conflict notes.
- Decision model: OpenAI Decisions API (`gpt-6-luna`, `POST /v1/decisions`) — typed predicate questions with probabilities, no generated text, $0.10/M input tokens, output free. Confirmed 12 October 2026, replacing the earlier self-hosted pick (Strands Decider 2B); note this means source text now goes to OpenAI for clustering decisions, in addition to Mistral for summaries. The self-hosted service remains in `model-server/` as a privacy-preserving alternative but is not wired in.
- Clustering pipeline: embed each incoming source (Mistral Embed), compare against the embeddings of recent stories, and for candidates above a similarity threshold (0.72) ask the decision model two predicate questions in one call: "Are these two items about the same specific event?" and "Do the sources disagree on a material fact?" Conflicting sources join the story and trigger a summary rewrite or a disagreement note via Mistral. If the decision model is unreachable, fall back to a conservative cosine threshold (0.88).
- Label loop: every member report ("wrong merge", "wrong summary") is stored as a labeled example. After roughly 100 labels, fine-tune a small encoder (DistilBERT/ModernBERT class, 67M–150M parameters) on them; published measurements (5 October 2026) show such a model beats every zero-shot decision model on a custom task like this — on accuracy, latency, and cost.

Research note (12 October 2026): Jev (TypeSafe AI, launched 15 September 2026) created the decision-model category — typed decisions with probabilities, no generated text, 70–500 ms, output tokens free. The category now includes OpenAI's Decisions API (public beta 6 October, `gpt-6-luna`), Microsoft-Decision-1, Quyet-1.0-Large, GLiDE, and open alternatives (Strands Decider 2B, THX-01 322M, typical-small 1.7B, Clef 27B, Tev1-4B, and others). Independent benchmarks add two cautions: decision models match LLMs only on bounded choices (weaker on multi-step reasoning), and fine-tuned small encoders beat all zero-shot decision models on custom tasks given ~100 labels — which is why the label loop is part of the plan. OpenAI's Decisions API supports Zero Data Retention for eligible customers.

## Architecture

- Backend is Supabase only: Postgres, Auth (email one-time codes), Realtime, Storage, Edge Functions. All orchestration (submission, duplicate rejection, clustering, summaries, notification fan-out) runs in Edge Functions and database triggers; there is no separate app server.
- All content tables have row level security; access is limited to group members.
- The decision model is OpenAI's hosted Decisions API (`gpt-6-luna`), called from Edge Functions during clustering. A self-hosted alternative (Strands Decider 2B, `model-server/`) exists in the repo but is not wired in.
- Web: Next.js. iOS: SwiftUI with the Liquid Glass navigation bar. Android: Jetpack Compose.
- Local development: `supabase start`, `supabase db reset` (applies `supabase/migrations/`).

## Initial implementation scope

Private group access; article and X sharing with duplicate rejection; source metadata with fallback; automatic event clustering; evolving short titles and summaries; categories; a living front page with the ranking above; source and discussion timelines; flat quoted replies; emoji reactions; GIFs from GIPHY; up to 10 images and 20 MB video per message; X embeds; notifications (new stories, direct replies, mentions, digest) with per-type toggles and group mute; and a report workflow for incorrect generated text. iOS uses the Liquid Glass navigation bar. Mobile sharesheet requires an early feasibility and product decision.

Later possibilities: public groups, richer discovery, and publication-style editions. Monetization, broad social profiles, follower mechanics, full AI articles, and political-spectrum classification are outside the initial agreed direction.

## Confirmed at implementation start (12 October 2026)

- Sign-in and invitation: emailed one-time codes (Supabase Auth OTP; switched from magic links on 17 October 2026) + one-time invite codes per group.
- Backend: Supabase only — no separate app backend.
- Stack: Next.js (web), SwiftUI with Liquid Glass navigation (iOS), Jetpack Compose (Android).
- Development: local, via `supabase start` / `supabase db reset`.
- Embeddings: Mistral Embed API (consistent with the Mistral language model).
- Notification defaults confirmed: new stories, direct replies, mentions on; digest off. Any group member can see the flagged-report queue.
- GIFs: GIPHY (free API key at developers.giphy.com).
- Decision model: OpenAI Decisions API (`gpt-6-luna`) for same-event/conflict clustering decisions.

## Still open

- Ranking weights and decay: the scheme above is the starting point; tune during the pilot.
- GIPHY's terms of service should be reviewed before shipping GIFs.
- Mobile sharesheet feasibility.
- Empty-group and other first-run states are not yet designed.

## Exploratory visual direction

Three directions were explored on 12 October: a riso zine the five of you run off between lunches (chosen), the regulars' table at a coffeehouse, and a confluence of link streams merging into one river. The chosen direction was mocked up and passed three critique rounds.

The look: a printed zine, not a feed. A warm paper ground, one blue riso ink for all text, rules and images, and a single pink accent reserved for "new to you" (new sources, new messages). Headlines are set in Gloock, a display serif; everything you read or touch is SF Pro. Every source image is printed through a halftone screen in the one ink, so five different publishers read as one publication; a source with no image gets a flat printed plate with the publisher's name; GIFs are the one exception, printed on the pink drum because they are reactions, not reporting. Members are monograms, never photos. Printed matter keeps square corners; rules are thin; whitespace is generous. A dark "night run" variant inverts the paper to blue-black with light ink.

Mocked-up screens: mobile front page (masthead "The Grapevine", lead story, editorial rows, tabs), story top (hero plate, evolving summary with provenance, source clippings, correction line), story conversation (day rules, compact avatar and author rows, inline source clippings, chronological quoted replies, emoji reactions, GIF with alt text, composer), share sheet, the "Filed" signature moment (a shared link prints dot by dot while the status reads "Reading …", then slides into its story as a pink "+1 source" stamp lands), dark front page, web front page (12-column grid, lead across seven columns, "At the table" conversation band), and an app icon. The mockup's split-correction sheet is superseded: splitting was removed from the direction.

The front page does not reshuffle while you read; when ranking changes, a line appears between two pink rules ("Later edition · N stories moved") and tapping it reprints the page.

On iOS the navigation uses native Liquid Glass navigation, tabs, and sheets. Android will use its native navigation. Design priority — mobile home, mobile story, web home — is complete as mockups; web, the native iOS local pilot, and the native Android app are now implemented.

### Local implementation status — 13 October 2026

The iOS pilot builds and runs on iPhone 17. Simulator checks covered magic-link callback, Keychain persistence across relaunches, paper/story loading, messages, quoted replies, reaction toggling, duplicate rejection, and private photo uploads with signed-URL display. Five native regression tests cover ranking, auth callback parsing, session serialization, Supabase timestamps, and attachment limits. A rolled-back SQL regression checks that members can mark their own membership seen but cannot change roles or group IDs.

Visible iOS conversations currently poll every 10 seconds rather than subscribing to Realtime. GIPHY search needs a key; push notifications, discussion-triggered AI summary updates, hosted configuration, media cleanup, and real-device/accessibility testing remain before launch. AI text currently regenerates on source submissions and reports, not new messages. Native group onboarding, correction reports, and video support are implemented but need additional end-to-end coverage.

All mockup stories, publishers, people, timestamps, and counts are fictional sample content (paper name "The Grapevine"; members You, Luka, Nika, Jure, Maja). Source images were Unsplash stand-ins; in production they are the sources' own preview images. Final branding, complete navigation, native platform conventions, and production behavior remain to be developed.

## Pilot success criteria to validate

Friends can understand a story without requesting basic backstory; sharing takes little effort; related sources consolidate without hiding attribution; quiet readers return to the newspaper; and discussions remain findable. Track successful shares, clustering corrections, story opens, distinct discussion participants, and whether the five-person group actually moves its routine from Discord. Set numerical targets after observing the pilot.
