# The Grapevine, redesign: DIRECTION

Working folder: `design/`. Renders: `shots/`, reels: `reels/`. Critique: `CRITIQUE.md`.

## The brief (self-authored)

The user said "redesign this application and try to keep an eye on a good UX" and was not reachable for the interview, so the brief is answered from the codebase, `PRODUCT_PLAN.md`, `README.md` and the seed data. No paid image credits are spent.

1. **What it is, who it is for.** The Grapevine is a private newspaper curated by friends. Five friends (Tim, Luka, Nika, Jure, Maja; Slovenia, English UI) share article and X links. The app groups links about the same event into one story with a short AI title, a one-minute summary and a category, and every story has a flat group chat (quoted replies, reactions, GIFs, photos, video). The pilot moves the group off Discord.
2. **The core action, first ten seconds.** Open the paper, see what moved since last time (which stories have new sources or new messages, who said what), open the one that moved, skim the catch-up if you missed the backstory, read the chat, reply. Sharing happens mostly from other apps through the share sheet; in-app sharing is secondary.
3. **Platforms.** iPhone (SwiftUI, iOS 26, the pilot) and Android (Jetpack Compose), shipping together; the web app is out of scope for this pass but takes the same tokens. Explored on the iPhone first.
4. **Three words.** Lively, trusted, ours. References outside apps: "heard it through the grapevine" (word of mouth among friends), a Karst vineyard in late September (chalk, vine green, grape blue-black), the regulars' table where five people argue about the news. No screenshots of loved apps were given: the bar is the skill's (Atoms, Apple News+, Flighty).
5. **Money.** Free. Private groups, no monetization (outside the agreed direction). No paywall is designed.
6. **Voice.** The person who runs the paper: short, dry, specific. From the seed: "There goes the November trip to Split.", "Three saves. Three. In the rain.", "Paywalled, but the headline says enough."
7. **What exists.** Name "The Grapevine" (store: "The Grapevine: Daily Paper", icon name "Grapevine"), domain thegrapevine.si, a shipped black-and-white broadsheet look on web, iOS and Android, fonts (Newsreader, IBM Plex Mono, UnifrakturMaguntia), seven demo photos in `web/public/seed/` (Unsplash stand-ins used by the seed), member avatars generated as colour halftone discs, the old riso icon on iOS and the broadsheet icon on web.
8. **Needed.** The app set on both platforms (core, detail, second core-loop screen, signature frames, dark core, empty state, both icons), the living core screen and a hero stage reel. No paywall. Store assets and code are not asked for: offered in the report. Depth: the critic's bar (done), not the portfolio version.
9. **Image generation.** Not used (self-authored brief, credits are the user's). Imagery is the seed photos the app already ships, and drawn SVG.

## Current app

Rebuilt from `ios/Grapevine/Views/FrontPageView.swift`, `StoryView.swift` and `android/.../FrontPageScreen.kt` into `before.html`; shot into `shots/before/`.

**The look as shipped** (web `globals.css`, iOS `Theme.swift`, Android `Theme.kt`): newsprint `#F4F1EA`, ink `#161513`, press red `#C8231B` for "new to you", a blackletter nameplate (UnifrakturMaguntia), Newsreader for everything you read, IBM Plex Mono for datelines, counts and labels, every photo printed through a colour halftone, square corners, a hard-shadow "press" style. It replaced an earlier riso-zine look (Gloock, one blue ink, pink accent) on 15 October 2026 (commit bb4a7f5).

**Slop faces it hits** (slop.md):
- §7 **The overcorrection**, exactly: pale paper, near-black ink, one red, mono figures, ruled rows. The riso look before it was the same rut in another ink.
- §1 Structure: the primary action ("Share a link") is a full-width ink button under the masthead, at the top of the page, not in the thumb zone. The masthead block (nameplate, double rule, volume and number, date, avatars, section strip, button) spends about 240pt before the first story on a phone, so the thing people open the app for (what moved) starts below the fold.
- §2 Typography: four families (blackletter, serif, mono, SF for controls); an ALL-CAPS mono eyebrow above every story and section; 10pt kickers (under the 11pt floor); the mono-for-all-metadata receipt costume.
- §3 Colour: "new to you" is a 7pt red dot in a mono line, the quietest thing on the page. Members are abstract halftone discs you cannot tell apart as people.
- §4 Imagery: colour only through a halftone screen, so five publishers' photos read as one grey texture.
- finish.md §7: the paper's name sits in the navigation bar of every story and again in a colophon at the foot of the front page ("Printed at 21:14 for 5 readers · Private edition").
- Android: the iPhone layout with a navigation bar underneath ("iPhone in a Pixel" in android.md §11): no top app bar, no FAB, the ink button at the top, Material list and account glyphs over a blackletter masthead.

**UX problems the redesign has to solve** (the reason for the brief):
1. The daily loop is "what moved, then the chat", and both are buried: "new" is a dot, the chat is below the summary and the clippings row on every story, so every visit scrolls past a 200-word summary to reach the conversation.
2. Three tabs for one screen used daily and two used once (Friends: members and the invite code; Profile: name, notifications, sign out).
3. The front page repeats the same metadata line under every story ("4 sources: Ferrite, The Meridian, Orbit Weekly, @launchwatch  5 messages") so nothing is scannable.
4. The AI text (title, summary) and the friends' messages share one voice and one style, so the "summary is not an opinion" rule of the product plan is not visible.

**Scan of `before.html`** (`shots/before/scan.json`): 13 FAILs. The real ones: more than two type families in content (three, on every screen); text below 11pt ("Vol. I · No. 12", "5 stories", "3 new", "Front page", "Space", "Clippings"); contrast 4.30:1 on the 11px mono captions ("Pictured via Orbit Weekly", "Updated 45 min ago", "Something off? Report the summary"); "5 stories" touching "3 new". The overlap FAILs with the composer and the gesture area are my rebuild's scroll clipping, not the app's. Warns: the lead summary runs 6 lines on the front page; a 12-size type scale.

## Inventory: keep / lose / unknown

**Keep (the contract: every one of these is in the redesign):**
- The paper's name as the masthead (the group names its paper); the date.
- Ranking: lead = most recent activity; the page never reshuffles while reading; the "later edition" line when the ranking changes (re-voiced).
- "N new" markers for new sources and new messages since you last looked; the "new since" divider in the conversation; the "+1 source" moment when a link joins a story.
- Per story: category, title, evolving summary, source count, message count, who filed what, the latest activity time.
- Sources as cards with publisher or X author, title, submitter, time, open the original; the states: paywalled (headline only), could not read, post deleted.
- The summary's provenance ("filed from 3 of 4 sources") and history ("What changed").
- Report a wrong summary, title or category (quiet, one tap away).
- The conversation: flat, chronological, no bubbles, day labels, consecutive messages grouped by author, quoted replies, emoji reactions, GIFs with alt text, photos (up to 10) and video, the composer at the bottom with attach.
- Share a link with an optional comment; progress ("Reading…"), the result (new story or joined one), duplicate rejection ("not added").
- Members: display names, who is in the paper, the invite code and share invitation, owner badge.
- Profile: name, email change, per-paper notification toggles (new stories, replies, mentions, mute), device notification settings, report a problem, help, privacy, terms, version, sign out, delete account.
- Empty paper state; error states with "Try again"; loading states that do not spin; pull to refresh.
- Earlier editions (the archive) reachable from the end of the paper.
- Sign in with email code, Google, Apple, X; onboarding: name, start a paper or join with a code.
- Good lines worth carrying: "Three saves. Three. In the rain." (seed), "There goes the November trip to Split.", "Paywalled, but the headline says enough.", the idea of "Printed for 5 readers".

**Lose:** the blackletter nameplate and the double rule; the volume and number line; the section strip of ALL-CAPS kickers; the colophon; the halftone screen on photographs; the drop cap; mono for metadata; the hard "press" shadow; the ink button at the top of the paper; the Friends and Profile tabs (see Decisions).

**Unknown (left as is, not designed here):** the admin dashboard (web only), the share extension's own sheet (it inherits the Share screen's look), the sign-in code email, the web front page and story page (same tokens, not mocked up), widgets.

## Assets harvested

- Photos: `web/public/seed/{rocket,bus,city,games,servers,storm,crowd}.jpg` (the seed's own demo images; Unsplash stand-ins, replaced by the sources' preview images in production).
- Content: `supabase/seed.sql` (five stories, ten sources, eight messages, one GIF, two reports, five members). Every story, publisher and person in the mockups comes from it.
- Name and store name (memory): "The Grapevine: Daily Paper", icon name "Grapevine".
- Brand marks: the web icon (black paper, halftone, red stamp) and the iOS icon (riso blue, pink stamp): two different icons ship today. Neither is kept; the new mark is drawn in Step 4 for both platforms.

## Decisions for the user (not made silently)

1. **Tabs.** The design drops the three-tab bar. The paper is the app: the members sit in the paper's header (tap: people and the invite), you sit top right (tap: your settings), and "Share a link" is a floating capsule in the thumb zone (iOS) or an extended FAB (Android). Friends and Profile keep every feature, as sheets and pushes instead of tabs. Say so if you want the tabs back; everything else still holds.
2. **The story screen opens on the conversation.** The catch-up (title, sources, summary) stays above the chat as the product plan requires, but it collapses to two lines once you have read that version, and re-opens when the summary changes ("Updated after Ferrite"). Regulars reach the chat in one scroll; newcomers still get the backstory.
3. **People own colours.** Each member gets one hue, used on their avatar and the sources they filed; names stay in ink for contrast. Five members fit; a sixth and seventh get the two spare hues; beyond that hues repeat (the product is five friends).
4. **The name stays.** "The Grapevine", "The Grapevine: Daily Paper" and the icon name are kept. The direction finally uses the name: the vine and the bunch.
5. **Photos are shown clean**, no halftone. The product plan allows the sources' own preview images; the screen gives them colour.

## History row for the current look

| date | app | source family | ground | type | accent hue | richness source |
|---|---|---|---|---|---|---|
| 2026-10-15 | The Grapevine (shipped broadsheet) | printed | light | serif (Newsreader) + blackletter | red | photo (halftone) |
| 2026-10-12 | The Grapevine (riso zine, superseded) | printed | light | serif (Gloock) | pink | colour-material |

The new direction must differ from each on at least 3 of 5 columns.

## The exploration (`explore.html`, `shots/explore/sheet.png`)

Three concepts, one per source family, rendered on the real front page:

- **A. The Grapevine is a vine** (nature). Leaf-white ground, green-black ink, vine green for "new to you", five member hues on avatars and on grapes, Bricolage Grotesque. Every story carries a bunch: one grape per source in the filer's colour.
- **B. The Grapevine is the late show on the friends' own station** (media). Blue-black, an amber "on air" lamp, full-bleed dusk photography, Big Shoulders Display in capitals, a running order with time codes.
- **C. The Grapevine is the sticker-covered laptop lid the five of you pass around** (object). An aqua field, white stickers at a tilt, Archivo expanded in capitals, a "2 NEW" burst, category stickers in five colours.

**Chosen: A, the vine.** It is the only one where the app's name is on the pixel: the bunch is the story's own data (how many sources, who filed them, which are new), so the concept carries information instead of decorating. Reading a one-minute summary stays comfortable on a light ground, and the five friends become five colours you can scan in a flat chat.
- B lost: "On air" is a prop a stranger has to have explained; amber on black in condensed capitals is the breaking-news skeleton of ESPN and Bleacher Report, so it fails the category test; and on a dark ground the people vanish into grey discs.
- C lost: it is the loudest, and it trivialises the car-free Sundays and the cup upset into stickers; expanded capitals at 15px are tiring for titles that run four lines; three contrast fails on the field. Two things are taken from it: the "new" marker as a confident object, and people shown as big, simple discs.

**History check.** A is nature / light / grotesque (Bricolage Grotesque) / green / shape: it differs from the shipped broadsheet (printed / light / serif / red / photo) on four columns and from the riso zine (printed / light / serif / pink / colour-material) on four. Claimed in `~/.app-designer/history.md` as "The Grapevine (exploring)".

## The category default refused

The top news readers (Apple News, Artifact's heirs, Flipboard) share: white or near-white, black, one red or orange, a serif or a heavy sans headline, a hero photo card then rows with thumbnails, a tab bar. The group-chat apps (Discord, WhatsApp, iMessage) share: a dark or white ground, blue or green bubbles, names in a random colour per person. The category's colour cliché is press red for news and bubble blue for chat. The shipped app is the news cliché done well (black, white, serif, red).

Refused here: press red, the serif, the blackletter, the halftone, the bubbles, the tab bar, the "Recent" dashboard skeleton. Kept from the category on purpose: a lead with a picture (the event is the visual focus, per the product plan), rows for the rest, a composer at the foot of the chat.

## Tokens

### Colour

| Role | Light | Dark | Job |
|---|---|---|---|
| ground | `#F0F4EB` | `#0E1711` | leaf-white; night on the vine |
| raised | `#FFFFFF` | `#172219` | cards (a story is an object), sheets |
| raised-2 | `#F6F8F3` | `#1F2C22` | fields, the composer, quoted replies |
| ink | `#15221B` | `#EEF3EA` | text, glyphs, the filled primary button, the catch-up card's ground (light) |
| ink-2 | `#3F4C45` | `#B5C1B8` | secondary text (6.9:1 light) |
| ink-3 | `#5E6B63` | `#8C9A90` | metadata, placeholders (4.9:1 light) |
| rule | `rgba(21,34,27,.08)` | `rgba(238,243,234,.10)` | hairlines |
| accent: vine | `#1E7A39` | `#4CC46F` | **one role: new to you** (badges, the new-since divider, the ring on a new grape, the stem). Not the action colour. |
| vine-soft | `#DCEFE0` | `#1D3A26` | the soft fill behind a "new" line |
| on-accent | `#FFFFFF` | `#0E1711` | |
| catch-up | `#15221B` | `#1D3A26` | the paper's own writing (the AI summary) sits on a dark card; its text `#EEF3EA`, its label vine |
| negative | `#B42318` | `#FF7A6E` | destructive only (delete account, a failed upload) |
| glass | `rgba(255,255,255,.66)` | `rgba(23,34,25,.55)` | iOS navigation layer |

**System tint: ink.** The accent's role is a state ("new"), so toggles, links and selection use ink, and the green means one thing. Primary buttons are ink-filled capsules.

**Identity palette (owners: people).** Each member owns one hue, on their avatar disc, the grapes they filed, and their activity beads in People; names stay in ink. The hues are tuned to one energy (lightness around 55 to 60, chroma high) so they sit together like a set of paints. Initials on the discs are set in ink on all five so the set stays consistent.

| Owner | Hue | Hex | Lit gradient (top-left to bottom-right) |
|---|---|---|---|
| Luka | marigold | `#E3A512` | `#F2BE3E` to `#E3A512` |
| Nika | terra | `#E2552B` | `#F07A55` to `#E2552B` |
| Jure | sky | `#2F7BEA` | `#5C9BF5` to `#2F7BEA` |
| Maja | grape | `#8E4FD8` | `#AB7AE6` to `#8E4FD8` |
| Tim (you) | raspberry | `#D23D7E` | `#E66A9C` to `#D23D7E` |
| spare 6 | teal | `#12A29A` | `#3FC1B9` to `#12A29A` |
| spare 7 | cocoa | `#9C6B3C` | `#B98858` to `#9C6B3C` |

Beyond seven members the hues repeat in order; the product is five friends. Screens that show the palette carry `data-palette="identity"`. The chrome (ground, text, navigation, buttons) stays neutral so the colour lands on people and grapes only.

### Type

One display face on both platforms: **Bricolage Grotesque** (OFL, variable: opsz, wdth, wght), bundled in both apps. A bricolage is a thing assembled from what is at hand, which is how this paper is made. UI text is the platform's face: SF Pro on iPhone, Google Sans Flex on Android.

| Step | Face | Size / leading | Weight | Tracking | Where |
|---|---|---|---|---|---|
| display | Bricolage, opsz 96 | 34 / 1.04 | 800 | -2% | the empty state, "+1 source", the hero stage |
| headline | Bricolage, opsz 96 | 30 / 1.03 | 800 | -2% | the lead's title, the story title |
| row title | Bricolage, opsz 24 | 19 / 1.15 | 700 | -1.5% | story rows, sheet titles, section heads |
| body | SF / GSF | 17 / 22 | 400 (600 for a name) | -0.4 | messages, the catch-up, fields |
| secondary | SF / GSF | 15 / 20 | 400, 600 | -0.2 | counts, the dateline, buttons |
| meta | SF / GSF | 13 / 16 | 400, 600 | -0.1 | times, categories, "new" marks |
| caption | SF / GSF | 11 / 13 | 500 | +2% | the "GIF" tag, the owner tag |

Seven steps. Names in the chat are 600 in the owner's hue. Numbers that change are tabular (SF and GSF have `tnum`; Bricolage display sizes never show live numbers).

### Shape and space

- Side margin 20 on both platforms. Spacing scale 4, 8, 12, 16, 20, 24, 32, 40, 48.
- Radius family: 28 for the lead card and the catch-up card, 14 for thumbnails and clip cards, 10 for fields and chips, capsule for buttons, badges and the member discs. Concentric: a thumbnail inside a 16-padded card gets 12.
- Depth, three levels: the ground is flat; objects (the lead card, clip cards) carry one pillow shadow `0 1px 1px rgba(21,34,27,.04), 0 12px 30px -12px rgba(21,34,27,.16), inset 0 0 0 1px rgba(21,34,27,.04)`; what floats (the Share capsule, the glass toolbar, sheets) carries `0 10px 24px -8px rgba(21,34,27,.45)` or Liquid Glass. Dark mode raises surfaces lighter instead.
- Android keeps the same values: lead card corner 28 (extra large), chips 10 (small), the extended FAB full-radius; its navigation layer is tonal, not glass.

## The richness source, as art direction

1. **The bunch.** Every story owns a bunch of grapes: one grape per source, filled in the filer's hue, a vine-green stem and leaf on top, a green ring on each grape new to you. Grapes overlap with a ring of the ground colour. The bunch sits at the lead's title, at the left of every row, at the top of the story (where its grapes become the source chips), on the share result, and on the icon. It is the one drawing in the app, drawn one way.
2. **The people.** Five discs: an initial in ink on a lit gradient of the owner's hue (light at the top-left), overlapping with a 2px ring of the ground. On the front page, in every message row, in People.
3. **The photographs.** The sources' own preview images, shown clean (no screen, no filter), always in the same frame: the top of the lead card at 16:10, a 64pt square with a 14 radius on rows, a 16:10 clip inside the chat. The frame unifies what five publishers shoot.
4. **The counterpoint card.** The paper's own writing (the catch-up) sits on a vine-black card with light text, so a stranger can tell the summary from a friend's message at a glance. One per story.

## The signature: filing a link

The core action is sharing a link, from the share sheet or in the app. The moment is the result.

| Beat | Time | What happens | Haptic (iOS / Android) |
|---|---|---|---|
| 1 Reading | 0 to about 2s | The sheet shows the link as a lone grape in your hue, the domain under it, "Reading the page". A green progress arc draws around the grape. | none |
| 2 Rolls in | 0 to 450ms after the result | The story it belongs to appears as a card with its bunch. Your grape rolls from the top into the bunch along a short arc (translate 0 to 48pt, rotate one turn), lands, and the count rolls 4 to 5. | `.impact(.light)` / `CONFIRM` on landing |
| 3 Ring | 450 to 900ms | A vine-green ring blooms around the new grape (scale .6 to 1, opacity 0 to 1, `.snappy`), and "+1 source" lands at 34pt with the story's title under it. "Open the story" and "Done". | `.success` / `CONFIRM` |
| New story | instead of 2 and 3 | A stem draws down (300ms) and the grape hangs on it alone: "A new story. The write-up takes a minute." | `.success` |
| Duplicate | instead of 2 and 3 | The grape bounces off the bunch (translate back 24pt, `.bouncy`): "Already in the paper. Luka filed it on Tuesday." | `.warning` / `REJECT` |

Reduced motion: the three states cross-fade; the count still changes; haptics stay. Shown in the set as `signature-1` (reading) and `signature-2` (rolled in), both full frames.

## Voice

Who is speaking: the friend who runs the paper. Dry, precise, short. Three rules you can check:

1. **Who and when, never "activity".** "Nika filed Ferrite, 19:12", "Luka sent a GIF, 20:29", never "New activity" or "Updated recently".
2. **Numbers and names instead of adjectives.** "4 sources · 5 messages", "3 new since 19:10", never "a lively discussion" or "lots of new". No praise, no cheering, no exclamation marks.
3. **Says "you" and the names, never "users" or "we".** One dry line per screen at most, and only in the empty state and the share result.

Platform words: "Share a link" on both (the share sheet and the Sharesheet); "Sign in with Apple" only on iPhone; "Device notification settings" on both.

## Content

Fixture date: Thursday 23 October 2025, 21:14 (historical sample content, not the current date). You are Tim. You last opened the paper at 19:10. Since then: Nika filed Ferrite on the booster story (19:12), Luka sent a GIF there (19:40), Nika wrote a line (20:29), Maja filed Coastline Daily as a new story (19:14). Four things are new: one source and two messages on the lead, one source on the bus story.

### paper (core, iOS and Android)
- Toolbar: "The Grapevine" (the paper's name, left); your disc T (right).
- Dateline: "Thursday 23 October ›" (a control: earlier editions) · "4 new since 19:10" (vine) · the member stack L N J M (tap: People).
- Lead card: photo (rocket); badge on the photo "3 new"; "Space · Nika, 20:29" (who moved it last, when); title "Reusable booster lands at sea on its second try" at 34pt; bunch of 4 (Luka marigold, Nika terra, Tim raspberry, Nika terra ringed green); "4 sources, one new from Nika" / "5 messages"; last line: N "Nika  This is the actual news." 20:29.
- Rows: "Night buses to the coast stop for winter" · 1 new source · Travel · Maja, 19:14 · bunch of 1 (Maja, ringed) · thumb (bus). "Studio Kettle pushes Wayfarer to spring 2027" · Games · 2 sources · 1 message · 18:14 · bunch (Maja, Jure) · thumb (games). "Third-tier side knock out the champions on penalties" · Sport · 1 source · 1 message · 17:14 · bunch (Nika). "Old town goes car-free on Sundays from March" · City · 2 sources · 1 message · 15:14 · bunch (Jure, Luka) · thumb (city).
- Below the fold: "Earlier editions" row. Floating: "Share a link" capsule (iOS), extended FAB "Share a link" (Android).

### story (detail, iOS and Android): the booster story
- Nav: back; share and more (iOS: two glass circles; Android: top app bar with arrow_back, share, more_vert).
- "Space · Nika, 20:29". Title "Reusable booster lands at sea on its second try" at 34pt.
- The catch-up card (dark, right under the title): label "The catch-up · rewritten after Ferrite, 19:12"; text "Vela Orbital's Nimbus booster landed on a barge in the North Sea on Sunday night, two months after its first attempt tipped over. Ferrite reports the next flight will carry a paying customer."; foot "3 of 4 sources" left, "What changed · Report" right; a fold chevron top right.
- Sources · 4, as the bunch picked apart: a grape in the filer's hue with the publisher under it: "@launchwatch" (Luka), "Orbit Weekly" (Nika), "The Meridian" (Tim, a lock: headline only), "Ferrite · new" (Nika, ringed green). Each opens its source.
- Conversation: "Tuesday 21 October". L Luka · 20:11 filed @launchwatch: "They actually stuck it this time." with the clip (@launchwatch on X: "Nimbus is down on the barge. Upright, legs out. Second attempt and they stuck it."). L Luka · 20:14 "Replay from 1:42:10 if you missed it." "Today". J Jure · 14:02 "Second try is still a landing. Ask anyone how many tries the first ones took." reactions 👍 2. M Maja · 14:05, quoting Jure: "The first one tipped over in front of a camera crew. It counts as a try." Divider "3 new since 19:10". N Nika · 19:12 filed Ferrite: "Ferrite has the customer's name." with the clip (Ferrite: "Vela's next Nimbus flight will carry a paying customer", photo). L Luka · 19:40 a GIF (crowd), alt "Crowd cheering in stage smoke", tag "GIF". N Nika · 20:29 "This is the actual news."
- `story-chat`: the same story scrolled to the conversation, so the reaction, the quoted reply, the divider, the clip and the GIF are on a frame.
- Composer: attach, "Tell the others", send.

### share (create, iOS and Android), signature-1, signature-2
- Sheet "Share a link" at a medium detent (Android: a bottom sheet that wraps its content, Material filled fields). Link: "x.com/vela_orbital/status/9120" (pasted). "Add a line (optional)": "Vela's own clip of the landing." Note: "Everyone in The Grapevine sees this with your name." Button "File it" (Android adds "Cancel").
- signature-1: your grape (raspberry) alone on its stem, no letter, the green ring drawing itself around it; "x.com" under it, "Reading the page", "Finding the story it belongs to."
- signature-2: no title; the bunch with your grape rolled in, ringed and a hair larger; "+1 source", "Now 5 sources", the story title, "Open the story", "Done".

### paper-dark (iOS and Android): the same front page at night.

### empty (first run)
- A lone stem and leaf, no grapes. "Share the first link to start the paper." Button "Share a link". Nothing else.

### people (sheet, iOS)
- "People", "5 in The Grapevine". Rows: T Tim · "You · started the paper" · filed 4 this week; N Nika · filed 9 this week; L Luka · filed 7 this week; M Maja · filed 6 this week; J Jure · filed 4 this week, each week drawn as that many tiny grapes in the person's hue. Invite: "Invite someone", "Invite code GROZD-7314", "Anyone with the code can join and read everything in the paper.", button "Share invitation".

### you (settings, iOS)
- "You". T Tim Rekelj, tim@rekelj.si (row opens name and email). Notifications for The Grapevine: New stories (on), Replies to you (on), Mentions (on), Mute the paper (off); "Device notification settings · Push also needs permission on this phone." Support: Report a problem or idea, Help with your account, Privacy, Terms. Sign out; Delete account. "The Grapevine 1.4 (112)".

## Motion (living screen `paper-live`, 8s loop)

| Beat | Time | Protagonist |
|---|---|---|
| 1 | 0.6 to 1.3s | Jure's line arrives on the lead ("Jure  Legs out. Job done.") replacing Nika's. At 1.6 to 2.1s the badge goes 3 to 4 new, the dateline 4 to 5, the messages 5 to 6, and the activity line changes: "Nika, 20:29" becomes "Jure, 21:14". |
| 2 | 3.0 to 3.6s | On the "Night buses" row a second grape (Luka) rolls into the bunch and the meta reads "2 new sources". |
| 3 | 5.4 to 6.0s | "2 stories moved · Show" pill opens its own 48pt row between the dateline and lead card, pushing the list down (the later edition). Held. |
| reset | 7.3 to 8.0s | Everything fades back to frame 0 under a short cross-fade. |

Freeze for the still: 2.2s (preview, counts and activity line settled). iPhone uses the iOS smooth and snappy springs; the Pixel the Material default and fast springs.

## Promises the app must keep

- "Updated after Ferrite" on the catch-up means the summary is regenerated when a source is added (true today) and the app records which source caused it (needs the story history to store the trigger).
- "Reading the page" then "+1 source · Now 5 sources" means the share sheet waits for the clustering result and names the story (true today: the submit function returns new or joined).
- "2 stories moved · Show" means the page never reshuffles while open (true today).
- The catch-up collapses only after the member has read that version (needs a per-member "summary seen at" timestamp).
