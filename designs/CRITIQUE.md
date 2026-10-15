# Critique log

One critic (fresh agent) for the whole job, briefed with the concept, colour
roles, refused defaults and the rubric in `app-designer/references/critique.md`.

## Round 1 (`shots/r1/`)

Scan: no FAILs. Warnings answered:

- *Text inside the home indicator area* (front pages, story): content scrolling
  under the tab bar or composer, faded by the scroll edge. Deliberate.
- *Distinct left edges* (story, conversation): avatar column, message column and
  the inner edges of source clippings. A chat has three verticals by nature.
- *Largest text 17px* (conversation): the story title has collapsed into the
  toolbar; the clippings and the pink GIF are the visual anchors. Deliberate.
- *Flat hierarchy* (signature board): a storyboard panel, not a screen.

| # | Line | R1 |
|---|---|---|
| 1 | Concept on the pixel | 4 |
| 2 | Not the category average | 4 |
| 3 | Not this skill's average | 3 |
| 4 | Hierarchy | 3 |
| 5 | Typography | 4 |
| 6 | Colour | 4 |
| 7 | Richness | 3 |
| 8 | Rhythm and space | 4 |
| 9 | Craft details | 3 |
| 10 | Native fluency | 4 |
| 11 | Signature | 3 |
| 12 | Feature test | 3 |

Critic's five changes:

1. **Filed**: one full-size frame at the peak moment instead of four 30% minis;
   the thumbnail visibly part-printed with a pink print-head line; the stamp off
   register; full toast text.
2. **Front page hierarchy**: the pink "Later edition" pill wins the squint test.
   Move it under the date rule as a pink divider. Make the hero stamp a real
   stamp (radius 2, slight rotation).
3. **Story**: add the hero halftone plate; turn source chips into mini clippings
   (thumbnail, publisher, Gloock headline) with a peek, not a clip; paywalled
   source gets the printed-plate fallback and a lock.
4. **Craft sweep**: Jure's quote time mismatch (14:31 vs 07:52); avatar stack
   rings; selected reaction filled in ink; nav title in Gloock; system line on
   the avatar column; web columns ending together; dark hero sky too dotty.
5. **Split**: thumbnails and Gloock headlines in the rows, a dashed "cut here"
   rule with scissors between kept and moved sources; a visible "split" entry
   on the story screen.

Declined in part:

- *A permanent off-register ghost on the resting hero stamp.* The signature's
  story is that the stamp lands off register and **settles into register**;
  leaving every resting stamp misregistered contradicts the motion and repeats
  the effect until it is noise. The resting stamp gets the rotation and radius,
  not the ghost.
- *Four step minis dropped entirely.* Kept as a second full frame (printing in)
  instead, so both halves of the moment are readable at full size.

## Round 2 (`shots/r2/`)

Scan: no FAILs. New warns: *largest text 21px* on `filed` (a scrolled front
page, the stamp is the hero of that frame); *tap target* on inline links
(marked `data-hit-expanded`, the row is the hit area).

| # | Line | R1 | R2 |
|---|---|---|---|
| 1 | Concept on the pixel | 4 | 4 |
| 2 | Not the category average | 4 | 4 |
| 3 | Not this skill's average | 3 | 4 |
| 4 | Hierarchy | 3 | 4 |
| 5 | Typography | 4 | 4 |
| 6 | Colour | 4 | 4 |
| 7 | Richness | 3 | 4 |
| 8 | Rhythm and space | 4 | 4 |
| 9 | Craft details | 3 | 3 |
| 10 | Native fluency | 4 | 4 |
| 11 | Signature | 3 | 4 |
| 12 | Feature test | 3 | 4 |

All five round-1 asks landed (critic's words: "the cut rule is the best new
idea in the set"; the declined resting ghost stamp was accepted). **Done by the
stop rule** (none below 3, 11 of 12 at 4). Craft at 3 for new defects:

1. `filed-printing`: page behind was empty below the summary; a descender
   peeked out under the clipping; grey scrim.
2. Wrapping links ("What changed", "correct the summary"); truncated nav title.
3. Story hero plate flush with the toolbar, cropped too tight.
4. Dead zone at the foot of `filed`: bring the web's "At the table" to the phone.
5. Icon: speech bubble is the category cliché and a pink ground makes the
   one-job accent the brand colour. Web: the pink nav pill.

Fixed after done (round 3): all of 1 to 5.
Declined: a second, shorter AI headline for the inline nav title ("Booster
lands at sea"). One generated title per story is less text to audit and
correct; tail truncation of the inline title is native behaviour.

## Round 3 (`shots/r3/`, small phone `shots/r3-se/`)

Scan: no FAILs at 402×874 or 375×667.

| # | Line | R1 | R2 | R3 |
|---|---|---|---|---|
| 1 | Concept on the pixel | 4 | 4 | 4 |
| 2 | Not the category average | 4 | 4 | 4 |
| 3 | Not this skill's average | 3 | 4 | 4 |
| 4 | Hierarchy | 3 | 4 | 4 |
| 5 | Typography | 4 | 4 | 4 |
| 6 | Colour | 4 | 4 | 4 |
| 7 | Richness | 3 | 4 | 4 |
| 8 | Rhythm and space | 4 | 4 | 4 |
| 9 | Craft details | 3 | 3 | 4 |
| 10 | Native fluency | 4 | 4 | 4 |
| 11 | Signature | 3 | 4 | 4 |
| 12 | Feature test | 3 | 4 | 4 |

All round-2 asks landed; the declined short nav title was accepted. **Done by
the stop rule.** No line reached 5.

Post-done defects the critic named, fixed and checked by eye on the renders
(**not re-scored by the critic**):

1. `filed`: the toast hid the second "At the table" quote. The Sport row is
   gone from this frame, so the toast sits over empty paper; "At the table" is
   hidden below 760pt tall, where it would not fit above the toast.
2. `filed-printing`: a sliver of covered text showed under the clipping. The
   covered headline is not drawn and the clipping sits 14pt higher, so it is
   clean at both widths.
3. `filed`, small phone: the toast wrapped. It is now 13pt and one line below
   390pt wide.
4. `story`, small phone: "What changed" no longer breaks across lines.
