# The Grapevine: design direction

## Brief

Source: the product brief of 12 October 2026 (in the session). Parts marked
*self-authored* were not in the brief and were filled in by the designer.

- **What and who.** A private newspaper curated by about five friends: members
  share article and X links, AI groups them into stories with a short title,
  an evolving summary and a category, and the friends talk underneath. It
  replaces a Discord channel where links disappear.
- **Core action (first ten seconds).** Open the paper, see what moved since
  last time (new sources, new discussion), open one story. Second loop: share
  a link with a comment and see where it lands.
- **Feel** *(self-authored)*: handmade, current, sociable. References from
  outside apps: a risograph-printed zine, a newsagent's front-page wall, the
  regulars' table at a coffeehouse.
- **What exists.** The name Buddypaper (since renamed The Grapevine). The brief's earlier exploration
  files (`designs/buddypaper-*.html`) were not in this folder, so this
  direction starts fresh. The brief's own constraints carried over: serif
  headlines, editorial hierarchy, thin rules, generous whitespace,
  newspaper grid on the front page; a chat-like story timeline without bubbles.
- **Needed.** Mobile home, mobile story, web home (in that order), plus the
  share, correction and split flows.
- **Imagery** *(self-authored)*: no image generator connected. Source images
  are Unsplash stand-ins, downloaded to `img/` and verified. In production
  they are the sources' own preview images (rights still open, see brief).

## Exploration

`explore.html`, shots in `shots/explore/`.

| | Concept | Family | Ground | Type | Accent | Richness |
|---|---|---|---|---|---|---|
| **A, winner** | A riso zine the five of you run off between lunches | printed | light | custom-display (Gloock) | pink | photo (one-ink halftone) |
| B | The regulars' table at a coffeehouse, papers on wooden holders | place | dark | serif (New York) | yellow (brass) | colour-material |
| C | A confluence: separate streams of links running into one river | nature | colour-field | condensed (SF Compressed) | teal | shape |

**Why A.** It is the most *this app*: a newspaper made by friends rather than
by an editor. The halftone fixes a real product problem too. Shared sources
bring a mess of publisher thumbnails (stock, logos, screenshots, nothing at
all). Printing every one of them through the same dot screen in the same ink
makes a page of five publishers look like one publication, and a source with
no image still gets a printed plate. It also keeps the brief's serif front
page without falling back on black ink, red and grey paper.

**Why B lost.** The wooden holder reads as a decorative bar on a card, and
dark green with brass feels like a private members' club, not five friends.
**Kept from B:** a friend's line quoted under the lead story, so the front page
carries the friends' voices as well as the news.

**Why C lost.** The river diagram explains clustering better than anything
else, but the page reads as a sports or data product, and compressed sans
drops the brief's serif newspaper. **Kept from C:** the idea that a new
source *joins* the story visibly, which becomes the signature.

History check: `~/.app-designer/history.md` was empty. No repeat possible.

## The category default we refuse

News readers (Apple News, Google News, Ground News) share: white ground, full
colour publisher photos in a card feed, SF or Roboto, a red or blue brand
accent, "For you" tabs. Group chat (Discord, WhatsApp) shares: dark grey
ground, bubbles or dense rows, blurple or green. AI summarisers share:
sparkles, violet gradients, a "summary card" with a glow.

We refuse all three: **no full-colour photo feed** (every image is printed in
one ink), **no red news accent and no black ink** (the paper is printed in
blue), **no chat bubbles**, **no sparkle or violet for anything the model
wrote**. AI output is labelled by what it is: "Summary from 4 sources".

## Tokens

### Colour

| Role | Light | Dark (night run) | Note |
|---|---|---|---|
| `paper` (ground) | `#EFEEE9` | `#11142A` | Cool uncoated stock, not cream. Night: blue-black, tinted from the ink |
| `raised` | `#F8F7F3` | `#1A1E38` | Sheets, composer field |
| `ink` | `#26368A` | `#E4E4F2` | Riso blue. All text, rules and images are printed in it |
| `ink-2` | `#4C5794` | `#A9AED3` | Summaries, metadata |
| `ink-3` | `#6C74A6` | `#7E84AE` | Timestamps, placeholders |
| `rule` | `rgba(38,54,138,.30)` | `rgba(228,228,242,.18)` | Hairlines. Section rules are 1.5pt solid ink |
| `plate` | = ink | `#8F9BE8` | The halftone ink. Dark mode screens images in a light blue on night paper |
| `pink` (accent) | `#FF6AA6` | `#FF7DB2` | The second drum. **One role: new to you** (new sources, new messages, later edition) |
| `on-pink` | `#1C2766` | `#1C2766` | Text on pink |

System tint is **ink**, not pink: pink is a state, not the action colour.
Primary buttons are ink capsules with paper text.

### Type

Two families. **Gloock** (OFL, one weight) for anything that is a
headline; **SF Pro** for everything you read or touch.

| Step | Face | Size / leading | Tracking | Use |
|---|---|---|---|---|
| Display | Gloock 400 | 34 / 1.04 | -1.5% | Lead headline, story title |
| Masthead | Gloock 400 | 28 / 1 | -2% | The group's paper name |
| Headline | Gloock 400 | 21 / 1.1 | -1% | Story rows, source titles in sheets |
| Body | SF Pro 400 | 17 / 22 | -0.4 | Messages |
| Summary | SF Pro 400 | 15 / 21 | -0.2 | Summaries, comments in cards |
| Meta | SF Pro 500 / 600 | 13 / 18 | 0 | Counts, names, times |
| Label | SF Pro 600 caps | 11 / 13 | +7% | Category and day labels only |

Counts use tabular figures. Web uses the same faces one step up
(display 56, headline 26, body 17).

### Space and shape

- Side margin **20**. Spacing 4, 8, 12, 16, 20, 24, 32, 48.
- Images and source clippings: **square corners** (printed matter).
  Chips 4. Glass chrome and sheets follow iOS (capsules, 26 to 34).
- Rules: 1.5pt solid ink opens a section, 0.5pt `rule` separates rows.

## Richness: the halftone plate

Every image in the app is printed, not shown. Source preview images are
greyscaled, screened at 4.5pt dots on a 30° angle and printed in `plate` ink
onto `paper`. No colour photograph appears anywhere. GIFs are the one
exception to the ink: they print on the **pink drum**, because they are
reactions, not reporting. A source with no image gets a flat 30% halftone
tint with the publisher's name in Gloock. Members are monograms in Gloock,
never photos.

Art direction for sources: whatever the publisher sends, cropped to the
upper-middle third, contrast pushed so the subject survives the screen.

## Signature: "Filed"

The moment a shared link becomes part of the paper.

1. **Post** (0 ms). The share sheet folds into a clipping (the link card)
   that drops to the top of the front page. Haptic: `.impact(.light)`.
2. **Printing in** (0 to ~1.2 s, as long as fetching takes). The clipping's
   thumbnail prints dot by dot: the halftone screen grows from blank paper
   to full tone as the page is read. Status underneath, in words: "Reading
   coastlinedaily.com", then "Looking for the same event".
3. **Filed** (spring, 0.45 s, bounce 0.15). The clipping slides into its
   story's row; a pink `+1 source` stamp lands on the story a few points off
   register and settles into place (the second drum catching up). The source
   count rolls 1 → 2. Haptic: `.success`. If it is a new story, the row is
   inserted instead and the stamp reads `New story`.
4. Under the stamp: "Filed under Night buses to the coast stop for winter ·
   Undo", for 6 seconds.

Reduced motion: no slide or stamp offset; the clipping cross-fades into the
row and the count changes without rolling. The status words stay, so nothing
is lost. If the page cannot be read, the clipping stays on the page as
"Couldn't read this page. Posted as a link with your comment." Nothing the
member typed is ever lost.

## Living page

The front page does not reshuffle while you read. When ranking changes, a
line appears under the date rule, between two pink rules: "↑ Later edition ·
2 stories moved". Tapping it reprints the page (rows slide to their new
places). Pull to refresh does the same. (Round 1 had this as a pink toolbar
pill; it out-shouted the lead story.)

## Content

All people, publishers and events are fictional. Clock: Monday 12 October
2026, 14:52. Members: **You** (T), **Luka**, **Nika**, **Jure**, **Maja**.
Paper name: **The Grapevine**.

### 1. Front page (14:52)

- Toolbar: members (left), share link + (right).
- Masthead: The Grapevine. Edition rule: "Mon 12 October" left, "Live · 14:48" right.
  Under it: "↑ Later edition · 2 stories moved" between pink rules.
- **Lead.** Image: rocket on its pad, halftone. Label: SPACE. Stamp: "+1 source".
  Headline: "Reusable booster lands at sea on its second try".
  Summary (first sentence): "Vela Orbital’s Nimbus booster landed on a barge in the
  North Sea on Sunday night, two months after its first attempt tipped over."
  Quote: “Second try is still a landing.” Jure, 07:52.
  Meta: 4 sources · 23 messages · 3 new · shared by Luka, Nika +2.
- Rows:
  - GAMES. "Studio Kettle pushes Wayfarer to spring 2027". 3 sources · 8 new of 41. Image: retro consoles.
  - CITY. "Old town goes car-free on Sundays from March". 2 sources · 6 messages. Image: city street.
  - TRAVEL. "Night buses to the coast stop for winter". 1 source · New story, Maja, 14:40. Image: coach at night.
  - SPORT. "Third-tier side knock out the champions on penalties". 1 source (X post, no image: printed plate "@tribuna_north") · 14 messages.
- Tabs: Paper, Sections, Replies; Search.

### 2. Story, top (14:53)

- Toolbar: back; share, more.
- Lead image (same halftone plate as the front page).
- SPACE · Updated 13:07. Title as above.
- Summary: "Vela Orbital’s Nimbus booster landed on a barge in the North Sea on Sunday
  night, two months after its first attempt tipped over. Ferrite reports the next
  flight will carry a paying customer."
- Provenance: "Summary from 3 of 4 sources, newest first. What changed".
- Source clippings, newest first, scrolling sideways: Ferrite (new), The Meridian (printed
  plate, lock, "Headline only"), Orbit Weekly, @launchwatch.
- "Two different events in one story? Split it or correct the summary".
- SUNDAY 11 OCTOBER
- **Luka** 22:14 "They actually stuck it this time." Source (X): @launchwatch, "Nimbus is down
  on the barge. Upright, legs out. Second attempt and they stuck it." Sun 22:09. Open on X.
  Then "Replay from 1:42:10 if you missed it." Reactions 🚀 3, 👏 2.
- **Nika** 22:40 "Proper write-up with the numbers." Source: Orbit Weekly, "Vela lands Nimbus
  booster at sea, a first for a European company", orbitweekly.eu.
- Composer: "Message", GIF, +.

### 3. Story, conversation (14:53, scrolled)

- Inline title: "Reusable booster lands at sea…"
- MONDAY 12 OCTOBER
- **You** 07:30 "Paywalled, but the headline says enough." Source (no image, paywalled): The
  Meridian, "Europe’s launch market gets its first reusable stage". Note: "Paywalled. Only the
  headline was readable, so the summary doesn’t use it."
- **Jure** 07:52 "Second try is still a landing. Ask anyone how many tries the first ones took."
  Reactions 😂 2.
- **Maja** 08:03, replying to Jure ("Second try is still a landing…"): "The first one tipped
  over in front of a camera crew. It counts as a try."
- Pink divider: "3 new since 08:10".
- **Nika** 13:05 "This is the actual news." Source: Ferrite, "Vela’s next Nimbus flight will
  carry a paying customer", ferrite.news.
- System: "Summary updated with Ferrite · 13:07 · What changed".
- **Luka** 14:20 GIF (pink drum), alt "Crowd cheering in stage smoke". Reaction ❤️ 1.
- Composer.

### 4. Share (14:52)

Front page behind. Sheet: "Share to The Grapevine". Clipping: Coastline Daily ·
coastlinedaily.com, "Night bus routes to the coast suspended until April", image: coach at
night. Comment (typed): "There goes the November trip to Split." Note: "Everyone in The Long
Lunch sees this with your name." Button: Post.

### 5. Signature, two full frames

- **Printing in** (14:52): the clipping over the front page, its image printed down to a
  pink print-head line, "Reading coastlinedaily.com", "Then looking for a story about the
  same event."
- **Filed** (14:53): front page scrolled to the rows (Games, City, Travel); Travel now
  "2 sources · Maja, You"; the pink "+1 source" plate caught mid-landing, off register; then
  "At the table" with two lines (Maja on Wayfarer delayed, 14:22; Luka on Car-free old town,
  13:58); toast "Filed under Night buses to the coast · Undo".

### Icon

Paper ground, an ink clipping with a knocked-out halftone photo and a headline bar, and
the pink plate overprinted off register on its corner, where it turns plum.

### 6. Split (correction path)

Story: WEATHER, "Up to 120 mm of rain forecast for the weekend" (3 sources, 5 messages).
Sheet: "Split this story". "Tick the sources about a different event. They move below the
cut, with the replies to them."
- Meteo Desk, "Up to 120 mm of rain forecast for Saturday and Sunday" (stays)
- Radio Centre, "Rivers expected to peak on Saturday evening" (stays, printed plate)
- ✂ dashed cut rule, "New story"
- City Herald, "One year after the October floods, what was rebuilt" (**moves**, "2 replies
  move with it")
Note: "Both stories get a note saying who split them. Anyone can undo it for 24 hours."
Button: Split into 2 stories. Secondary: "Something else is wrong".

### 7. Front page, night run (dark)

Same content as 1, 21:40, lead +1 source; "Later edition" absent.

### 8. Web front page

Same stories, 1440 wide: date, masthead and share/members across the top; sections nav with
the later-edition line and search; lead across 7 columns; four stories in the right column;
"At the table" band below with the latest four lines of conversation.

### Not designed this round

Sign-in and invitation, empty paper (first run), sections tab, replies tab, search, summary
history, notification settings, member removal, failed-AI and deleted-post states beyond the
paywalled card and the "Couldn't read this page" line.
