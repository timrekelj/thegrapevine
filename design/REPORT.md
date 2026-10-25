# The Grapevine redesign

Design deliverables for iPhone and Android. This is a visual specification and motion study; production SwiftUI, Compose and web code have not been changed. The brief was self-authored from the repository and seed content.

## Direction and UX

Three directions were explored: a vine, a friends' broadcast station, and a sticker-covered laptop. The vine won because each grape represents a source and its filer. The broadcast concept felt like breaking news; the sticker concept made serious stories feel frivolous and long titles harder to read.

The design replaces the blackletter masthead, halftone photos, press-red markers and dense metadata with clean photography, Bricolage Grotesque, identifiable member discs and green new-content indicators. The front page shows who last spoke and what they said. Sharing sits in the thumb zone. A separate catch-up card distinguishes generated summaries from friends' conversation.

Voice: name who did what and when; use counts instead of adjectives; address people by name or as “you.” The signature turns a submitted link into a grape joining the story's bunch, with a ring marking the new source.

Product choices to review before implementation: replace Friends/Profile tabs with header entry points; collapse a previously read summary and reopen it when updated; assign consistent member colours; show photographs without halftone. The name stays. Colour never replaces textual counts or names.

## Deliverables

- [Complete mockups](mockup.html), [design specification](DIRECTION.md), [critique history](CRITIQUE.md).
- [Three directions](shots/explore/sheet.png), [before/after board](shots/before-after/stages/before-after.png).
- Final render paths and final review outcome are recorded below.
- [iPhone reel](reels/paper-live.mp4), [Android reel](reels/paper-live-android.mp4), [hero reel](reels/hero.mp4).

## Preservation audit

“Shown” means visible in the mockups, not implemented or interaction-tested.

| Existing capability | Design coverage |
|---|---|
| Paper name, date, stable ranking, new-content counts | Shown; the moving-stories prompt requires explicit refresh |
| Story title, category, summary, source/message counts, filer and time | Shown |
| Source attribution and original links | Source strip and conversation clip cards shown; full original-link interaction specified |
| Paywalled source | Lock shown; unreadable/deleted-source variants retained in specification, not separately rendered |
| Summary provenance, history and reporting | Entry points shown; downstream screens not rendered |
| Chronological chat, grouping, day labels, quotes, reactions, new-since divider, GIF | Shown across story and story-chat |
| Photo/video attachment and up-to-10-photo support | Attachment entry shown; picker, limits and video playback not rendered |
| Link submission, optional comment, progress, joined-story result | Shown; duplicate and new-story outcomes specified, not separate final screens |
| Members, owner status, invitation | Shown on People |
| Account, notification preferences, device settings, support, legal, sign-out, deletion | Entry points shown on You; downstream flows not rendered |
| Empty paper | Shown |
| Loading/error/retry, pull to refresh | Retained requirements; interaction verification remains |
| Earlier editions | Shown in paper-end |
| Authentication and joining/creating a paper | Retained requirements; not redesigned in this set |
| Existing friends' writing | Seed lines retained in story content |

## Validation and limits

Round 1: 10/14 rubric lines at 4 or above (71%), not done. Round 2: 14/15 (93%), met the skill's threshold; motion remained 3. Round-3 fixes were rendered before the original critic hit a provider limit. A replacement critic reviewed those artifacts on continuation and found remaining Android composer alignment, small-phone metadata wrapping, and hero-strip sampling defects; these are addressed in the final verification below.

Static HTML replicas cannot establish native gesture feel, screen-reader behaviour, keyboard avoidance, dynamic type, actual haptics, or motion performance on hardware. Those need implementation and device testing. Summary collapse needs per-member version-read state; “rewritten after” needs the source trigger stored in history. The local mockup uses the App Designer Pro device kit and external fonts, so it is not a standalone distributable app.

Store screenshots, store-policy validation, onboarding/paywall design, web redesign and production implementation are outside this design pass. No store panels were generated. The product is free.

## Final verification

Final [screen sheet](shots/r6/sheet.png), [small-phone sheet](shots/r6-small/sheet.png), and platform pairs: [core](shots/r6/pairs/core.png), [story](shots/r6/pairs/story.png), [sharing](shots/r6/pairs/share.png), [dark](shots/r6/pairs/dark.png). Icons: [iOS modes](shots/r6/icon-ios-modes.png), [Android masks](shots/r6/icons/icon-android-masks.png).

Both final scans report zero failures. The critic verified the composer and deliberate two-line small-phone metadata; Rhythm improved from 3 to 4. Final scores: five lines at 5, ten at 4; all 15 meet the shipping threshold. Detailed evidence and accepted warnings are in CRITIQUE.md. Native accessibility, dynamic type, keyboard behaviour and small-sheet safe-area reachability remain untested.

All three 8-second, 30fps reels regenerated successfully. Loop seam SSIM: iPhone 1.0000, Android 0.9997, hero 0.9999; longest still intervals 1.8s, 1.8s and 2.1s. Hero contact sheet now includes the 7.9s return-to-start sample. These are renderer checks, not hardware playback measurements.
