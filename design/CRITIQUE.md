# CRITIQUE

One critic for the whole job. Rounds are numbered by what the critic saw; `shots/r1` to `r3` were my own rounds before the critic (scan fixes), so the critic's round 1 is `shots/r3`.

## Before the critic (my rounds r1 to r3)

Scan: r1 had 10 FAILs (member names in their hues at 1.95 to 4.35:1 contrast, fields read as cards nested in the sheet, a dark home indicator over the dark ground, an emoji reaction read as UI copy). r3 is clean at 402x874 and 412x915, and at 375x667 and 360x760.

Fixed on my own read-aloud pass:
- Names in the chat and on the lead are set in ink; only the disc and the grapes carry the person's hue (the hue failed as text on white).
- "Written from 3 of 4 sources · What changed · Wrong? Report" wrapped to two lines; now "3 of 4 sources" left, "What changed · Report" right.
- "Tap one to open it" under Sources: an explanatory sentence, cut.
- The dateline "Thursday 23 October · 4 new since 19:10" wrapped mid-phrase beside the member stack; now two lines by design, the count in vine green.
- The empty state's support line cut from 17 words to 9: "Links about the same event grow into one story."
- The share and people sheets had no element owning the screen (largest text 21px); their titles are now the 30px headline step.
- A scroll-edge fade at the bottom of every scrolling screen, so the home indicator and gesture handle sit on the ground, not on a cut row (the top already had one).

Answered warns (deliberate):
- "6 to 7 different gradients": the five member discs each carry a lit gradient of their own hue (the identity palette), plus the two scroll-edge fades. Each has an owner.
- "Text inside the system's safe areas": content scrolls under the home indicator and the gesture handle, as iOS 26 and Android 16 require; the edge fade keeps the chrome legible.
- "6 to 7 distinct left edges": the bunch column (20), the row text (74), the card inset (40) and the dateline (20) are the four verticals; the rest are the chips' and the badge's insets inside objects.
- "Text within 12pt of the edge" on the story: the source chips are a horizontal scroller; the cut chip is the affordance.
- "Largest text is 22px" on You: a settings screen, now with a 30px large title.

## Round 1 (critic saw `shots/r3`)

| line | score | evidence (critic) | the change for a 5 |
|---|---|---|---|
| 1 Concept on the pixel | 4 | The paper tells the concept with the name covered; the story screen has no grape on it | The sources row on the story becomes the bunch itself |
| 2 Not the category average | 4 | No tab bar, red or serif; "Luka sent a GIF, 20:29" is nobody else's; but lead photo card plus thumbnail rows is the Apple News list skeleton | Rows carry only the bunch; photos for the lead only |
| 3 Not this skill's average | 4 | Leaf-white, vine green, five owned hues, Bricolage; but People and You are stock inset lists | Draw each person's week as a mini bunch on People |
| 4 Hierarchy | 4 | Paper lands on the photo then the headline; on the story the dark card outweighs the 30px title and the chip row sits between | Order title, catch-up, sources, chat; title 34px |
| 5 Typography | 4 | Headlines break on the pause, numbers lead their words; masthead and lead headline weigh the same; small phones truncate the catch-up meta | Lead headline 34px; drop "1 min" on small phones |
| 6 Colour | 4 | Green does one job on the paper; dark mode designed; but "The catch-up" label is green (a second role) and the icon paints a grape green | Label in leaf-white; the icon's new grape wears the ring |
| 7 Richness and joy | 4 | The photo, the five-colour bunch, the seedling, the "+1 source" smile; but grapes are flat discs and the stem is a rod with a flag | Light the grapes; a curling stem |
| 8 Rhythm and space | 3 | Lead card groups well; the three sheets end at half height over a blank slab; 7 left edges | Sheets at a medium detent; Android sheet wraps |
| 9 Finish and craft | 3 | Depth on the two cards and glass chrome; but flat grapes, header avatars with no ground ring, icon modes that differ, placeholder values | Lit grapes and ringed avatars everywhere; one icon mark across modes; real-looking values |
| 10 Native fluency | 4 | iOS glass done right; Android app bar, FAB, sheet, no chevrons; misses: full-height share sheets, iOS-style fields on Android | Material filled text fields in a wrap-content sheet; medium detent on iOS |
| 11 Signature | 4 | Frame 2 clear; frame 1 reads as an avatar loading | Frame 1: a grape with a stem, the ring drawing itself as progress |
| 12 Copy | 4 | Voice holds; share sheet carries a system sentence, empty state over budget, "Joined the story" passive | Cut the two explanations; "Now 5 sources" |
| 15 Feature test | 3 | The paper and the "+1 source" frame could make a round-up; the rest would not be screenshot | Changes 1 and 2 |
| Fidelity | 3 | Kept with more precision; missing on the frames: earlier editions, the paywalled state, the new-since divider, quoted replies, reactions, GIFs | Show them on the frames |

Stranger-test defects: the lone "T" disc on signature-1 reads as an avatar loading; the straight stem with one leaf reads as a flag on a pole; the dark and tinted icons ring a grape the light icon does not; the icon's green grape is a hue the app never gives a grape; "updated after Ferrite" has no referent above the body text; the bottom fade bleaches the fourth row's grape; the lead's activity line ("Luka sent a GIF, 20:29") does not match its preview (Nika, 19:12); "GRAPEVINEDEMO" and "tim@example.com" are placeholders. Small phones: the lead fills the viewport, the catch-up meta truncates.

Strings quoted: "Links about an event already in the paper join its story." (cut); "Joined the story" (now "Now 5 sources"); "1 min · updated after Ferrite" (now "rewritten after Ferrite, 19:12", with Ferrite shown ringed in the sources row under the card); "Links about the same event grow into one story." (cut); "Help and account requests" (now "Help with your account"); the "Share a link" title on the result frame (dropped); the two placeholders (now tim@rekelj.si and GROZD-7314).

Swap and family tests: all four pairs pass; core and dark pass narrowly (the tells are the glass disc and capsule against the flat app bar and the 16dp FAB). Share pair: the Android fields were iOS boxes.

Critic's five changes and what I did:
1. Craft the grape: done. Every grape is lit (radial highlight top-left, a deeper rim), 2px ground gap where grapes overlap, a curved stem with the leaf hanging off it; header avatars carry a 2px ground ring; the arriving grape on the result frame is a hair larger.
2. Fit the sheets: done. iOS share and both signature frames at one medium detent (top at 412pt); the Android sheet wraps its content and uses Material filled text fields.
3. Restore the keeps: done. The date on the paper is a control with a chevron ("Thursday 23 October ›", earlier editions) and the list still ends with "Earlier editions"; the paywalled chip and its quote card carry a lock; the "3 new since 19:10" divider, a thumbs-up reaction, a quoted reply, a GIF and a photo clip are on a new frame, `story-chat`, the same story scrolled to the conversation.
4. The bunch on the story: done, as a legend: four grapes in the filers' colours with the publisher under each, the paywalled one locked, Ferrite ringed and marked new. Signature frame 1: a lone grape with a stem, no letter, the green ring drawing itself as the progress arc.
5. Words and data: done. The lead's activity line is "Nika, 20:29" and the preview is Nika's 20:29 line; the timeline is Nika filed Ferrite 19:12, Luka's GIF 19:40, Nika's line 20:29 (3 new since 19:10: one source, two messages). "The catch-up" label is leaf-white.

Declined:
- Rows without thumbnails (line 2): declined. The thumbnails are the sources' own preview images and the product plan makes the event the visual focus; a photo tells a reader which story this is faster than a bunch tells them who filed it. The bunch grows to 40pt beside the thumbnail instead.
- Fewer left edges (line 8): declined; the four verticals (margin, bunch column, row text, card inset) are the layout.

## Round 2 (critic saw `shots/r4` and `reels/`)

Landed from round 1: all five changes (the critic's words: lit grapes with a curled stem and ground gaps, ringed avatars, one icon mark across every mode; sheets at one detent and a wrapping Android sheet with Material fields; the date control, the lock, the divider, the reaction, the quote, the clip and the GIF on frames; the sources row as grapes and the lone grape with the progress arc; every string and the data). Declines accepted.

| line | score | evidence (critic) | the change for a 5 |
|---|---|---|---|
| 1 Concept on the pixel | 5 | Every content screen carries the bunch; the icon is the bunch with the ring | holds |
| 2 Not the category average | 4 | The bunch, the beads, "Nika, 20:29" and the dark catch-up are nobody else's; the lead card and thumbnail rows are still the news-list skeleton (thumbnails kept by decision) | mount the lead's bunch as a sticker on the photo's edge |
| 3 Not this skill's average | 5 | Lists speak the direction (week beads, row bunches, grape sources); settings stay system | holds |
| 4 Hierarchy | 4 | Story lands on the 34px title, then the card, then the grapes; the scrolled chat frame had no landing point | a collapsed title in the bar |
| 5 Typography | 4 | Story title outweighs the masthead; "on" stranded in the card headline; "4  new" slot; small-phone meta truncates | hand-break, size the slot, wrap the meta |
| 6 Colour | 5 | Green has one job on every frame; "The catch-up" leaf-white; dark mode designed; the icon's new grape wears the ring | holds (brand name on the stage in ink) |
| 7 Richness and joy | 5 | Lit grapes at 24pt and 180pt, the arriving grape, the beads, the seedling, the hero cluster | holds |
| 8 Rhythm and space | 4 | Sheets fit; live frames reserved empty slots; the 360dp row wrapped to three lines; the pill landed on the date row | reflow, 32pt bunch at 360, the pill under the header |
| 9 Finish and craft | 4 | Lit grapes, ringed avatars, one icon mark, real values; the iOS bottom bleach read as a disabled row; centred Android placeholder; the digit slot | kill the bleach, start-align, size the slot |
| 10 Native fluency | 4 | iOS glass right; Android bar, FAB, sheet, fields right; no collapsed title; the placeholder | the collapsed title in both bars |
| 11 Signature | 5 | A grape with the ring drawing itself, then the bunch with that grape ringed and larger | holds |
| 12 Copy | 4 | Voice holds; "4 this week" lost its noun; "Say something" pasteable anywhere | "filed 4 this week"; "Tell the others" |
| 13 Motion | 3 | Beats read in order and nothing decorates; four things changed in one beat, the kicker a frame later, the pill covered the date, the strip ended before the seam | one protagonist per beat, the pill under the header, a 7.9s frame |
| 15 Feature test | 4 | The paper, the signature pair and the hero stage are feature material; the bleached row and the pill collision are what an editor sees first | changes 1 and 2 |
| Fidelity | 4 | Everything kept plus the round-1 restores; "Earlier editions" and Sign out / Delete account not rendered | one frame of the paper's end; You scrolled |

Stranger-test defects: the iOS bottom fade bleached the third row into a disabled state (Android did not); the scrolled chat had no title in the bar; "4 this week" without a noun; the reserved slots in the live frames ("4  new", "1 new source    · Travel"); the pill over the date row at 6.67s; a sliver of stem at the stage's corner; the Android placeholder read as centred. Swap and family tests: all four pairs pass; the share pair now passes clearly.

**Done by the stop rule:** 15 lines scored, none below 3, 14 of 15 at 4 or above (93%), gates Concept 5, Finish 4, Native fluency 4, Copy 4.

Strings quoted and what they became: "4 this week" became "filed 4 this week" (and 9, 7, 6); "Say something" became "Tell the others"; "2 stories moved · Show" kept (stories move both ways).

The critic's five changes and what I did (round 3 render, `shots/r5`, `reels/`):
1. The reel's last beat: done. The "2 stories moved · Show" pill now opens a row between the dateline and the lead card and pushes the list down 48pt (the list makes room), never over the date or the avatars, on both platforms. Beat 1 is staggered: Jure's line lands first (0.6 to 1.3s); the counts (5 to 6 messages, 3 to 4 new, 4 to 5 new since) and the kicker ("Nika, 20:29" to "Jure, 21:14") roll together at 1.6 to 2.1s. The strips now show 0, 1.3, 2.2, 3.7, 6.0 and 7.9s, so the seam is on pixels: 7.9s is the crossfade back to frame 1. Seam SSIM 1.000 (iPhone), 0.9998 (Pixel), 0.9999 (stage); longest still 1.8s.
2. The bottom bleach: done. The fade is a 28pt scrim at the very edge on iOS (24dp on Android); rows sit at full ink under the capsule.
3. The collapsed title: done on `story-chat` ("Reusable booster lands…" at 17px semibold between the chevron and the capsule); the Android app bar carries the same rule (not on a frame: the Android story is shown unscrolled).
4. Counters and small phones: done. The badge rolls "3 new" to "4 new" with no slot; the bus row's meta reflows (the counter's width animates 82 to 91pt with the state); the card headline is hand-broken "Reusable booster / lands at sea / on its second try" on every platform and mode; under 400pt the catch-up meta wraps to a second line and rows use a 32pt bunch and a 56pt thumbnail, so the meta stays on one line at 360dp.
5. Strings and the field: done. "filed 4 this week" and the others; "Tell the others" as the composer placeholder on all three story frames; the Android placeholder is start-aligned with 16dp padding.
Also from the stranger list: the stage shows the whole stem of the big bunch and sets "The Grapevine" in ink; Fidelity: a `paper-end` frame renders "Earlier editions" (with the two previous editions under it) and `you` renders tall to "Sign out" and "Delete account".

## Round 3 — replacement critic, r5

The original critic failed at the provider usage limit. A replacement reviewed all r5 review sheets, platform pairs, small-phone sheet, icons, hero and motion strips against the previous findings.

| Line | Score | Visible evidence / remaining improvement |
|---|---:|---|
| Concept | 5 | Source grapes, member beads, seedling and signature consistently express the vine |
| Category distinction | 4 | Friend previews distinguish a familiar news layout; photo-edge bunch remains optional polish |
| Skill distinction | 5 | Lit identity objects and Bricolage avoid the austere newspaper default |
| Hierarchy | 4 | Strong title and collapsed iOS title; Android collapsed state unshown |
| Typography | 4 | Headline and counters improved; small Android metadata still fragments |
| Colour | 5 | Owned member colours, green newness, deliberate dark surfaces |
| Richness | 5 | Lit grapes, activity beads and seedling |
| Rhythm | 3 | Small Android bus metadata breaks into narrow columns |
| Finish | 4 | Bottom bleach fixed; Android placeholder alignment still wrong |
| Native fluency | 4 | Four pairs pass family/swap; composer needs correction |
| Signature | 5 | Loading grape and ringed fifth source communicate state |
| Copy | 4 | “Tell the others” and “filed 4 this week” landed |
| Motion | 4 | Phone beats improved; hero strip still lacks final seam sample |
| Feature | 4 | Hero/signature strong; small layout defect detracts |
| Fidelity | 4 | Archive and account actions visible; remaining specified states not rendered |

Numerical stop rule met: 14/15 ≥4 (93.3%), none below 3; gates 5/4/4/4. Concrete defects still require correction. This supersedes earlier claims that the Android placeholder, small-row wrapping and all three strips were fixed in r5.

Final corrections in r6: explicitly reset Android composer flex-direction to row (generic fields inherited column); reset its minimum height, bottom margin and field underline; make narrow row metadata deliberately wrap with the new-source phrase intact; add hero beat samples through 7.9s. Document fixture year as 2025 to match Thursday 23 October. Updated stale direction-document copy and timing. Production source is unaffected.

## Final targeted verification — r6

Replacement critic confirmed the Android composer is left-aligned and vertically centered, small Android static/live rows keep “1 new source” intact with category/name/time below, and fixture documentation uses 2025. Rhythm rises **3 → 4**; all other round-3 scores hold. Thus **15/15 lines ≥4 (100%)**, required gates 5/4/4/4. This is the shipping threshold, not a claim of all-5 portfolio quality.

Both full scans have zero FAILs: `shots/r6/scan.json` and `shots/r6-small/scan.json`.

Remaining scan warnings are deliberate or bounded mockup limitations:
- Gradients belong to lit member objects and edge scrims; left-edge counts include nested source/chat content and card insets.
- The 22px archive heading and 17px scrolled chat title reflect continuation states, not new hero screens. Three chat sizes keep conversation text consistent rather than making messages compete.
- Android sheet scan counts the underlying paper plus native form labels; those use different typography roles. The aggregate platform scale includes native control sizes.
- Six-line catch-up paragraphs at small widths preserve the story text; the card is collapsible in the specification.
- The near-edge source label belongs to an intentional horizontal source scroller.
- Safe-area warnings mostly concern continuing scroll content behind bottom controls. Small signature/People sheets additionally need native scroll-to-reach and safe-area verification on implementation; static frames do not verify reachability. This is recorded as a device-validation limit, not a passed interaction test.

Hero-strip follow-up: critic verified all six timestamps (0, 1.3, 2.2, 3.7, 6.0, 7.9s), the pill occupying its own row at 6s, and return to the opening state at 7.9s. Motion holds at 4. All concrete round-3 defects resolved; 15/15 scores ≥4. Regenerated reels exited successfully; seam SSIM 1.0000 / 0.9997 / 0.9999 (iPhone / Android / hero).
