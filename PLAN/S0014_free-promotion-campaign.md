# S0014 - Free promotion campaign: a pre-launch plan, gated on the release decisions

**Status:** Partial
**Status note:** Partial 2026-11-13 (unattended pass, nothing outward): P0 baseline, P1 POSITIONING.md, P2 forbidden-terms list + assert-promotion gate + self-test wired into sweep and CI, P3 Play listing EN/RU/UK, P10 checkpoint script built; gate exit 0, 13 PASS 1 WARN (P1 research read store pages, not reviews). Open: owner review and the assumed market category, outward steps P4 P7 P8 P9 after S0046/S0047 and the owner go, P5 is S0045 P6, P6 demo needs a build. Not built, not tested until the release.
**Complexity:** hard - confirmed 2026-11-13: new tracked sources (positioning, listing in three languages), a new gate with a
self-test wired into the sweep and CI, a forbidden-terms list, and outward steps that wait on other tickets and the owner.
**Phase:** pre-launch campaign design - independent of the build phases of handoff section 8; every outward step
waits for the owner's go and, per step, for S0046 (repository), S0047 (listing) or S0045 (site) - S0010's decisions 4.3, 4.5
and 4.7 are recorded (section 2.2, decision 2, amended 2026-11-13)
**Verification level:** owner review now (the worksheet and the channel plan below); after launch, the static
predicates of section 4, each with its command, API field or file.

## Remaining development

*As of 2026-11-13, checked against the tree. Built in the 2026-11-13 pass (agent steps, nothing outward): P0 baseline
(`PLAN/S0014_free-promotion-campaign/metrics/2026-11-13-baseline.md` and `tools/promotion/write-checkpoint.ps1`), P1
(`POSITIONING.md`, internal research notes), P2 (`tools/promotion/forbidden-terms.json`, `assert-promotion.ps1`, its
self-test, wired into the sweep and CI), P3 (`docs/play/PLAY_LISTING.md`, `-ru.md`, `-uk.md`), the tactical folder with the
go ledger. Evidence in the Progress block of section 5 and in the Last Audit.*

1. **P1 refinement - A.** Read real reviews and forum threads of the alternatives in the owner's browser once the listing
   exists, and change a line of `POSITIONING.md` section 3 only where the persona's own words disagree (the research of
   2026-11-13 read store pages and listicles, not reviews).
2. **P4 repository metadata - A·GO**, after S0046 part A creates `SerZhyAle/StreamsPlayer_Android`: `gh repo edit` with the
   sentence of `POSITIONING.md` section 1 and its `Topics:` line; the social preview through the owner's browser (A→O).
3. **P5 - carried out by S0045 as its P6** (landing page heads, one `MobileApplication` JSON-LD, sitemap, social card). Its
   inputs P1 to P3 are in the tree now.
4. **P6 demo - A**, needs a debug build running on an emulator or device; a RELEASE-class run the owner names or the release
   performs. Also the screenshots and the feature graphic of the listing.
5. **P7 Play listing - A→O**, after S0047: the console fields from `docs/play/PLAY_LISTING*.md`.
6. **P8 catalogues - A·GO / A→O**: Obtainium after GitHub Releases (S0046 part B); AlternativeTo after the owner reads its
   rules (open question 5); F-Droid only if the owner's item 5 answers yes.
7. **P9 launch moments - O.** **P10 checkpoints - A**: `tools/promotion/write-checkpoint.ps1` at the first outward step, then at
   most fourteen days apart while outreach is active.

Owner items still open: 4 (Yandex, hub-owned, gates nothing before a host-root Yandex step) and 5 (the F-Droid constraint,
asked right before P8). Item 2 (market category) is an assumed decision, open to the owner's overrule (section 3.3).

## 0. Source

- Canon PROMOTION.md sections 1-8 and the `promote` skill, read 2026-10-09. The canon is cited, not restated.
- S0010 (this repository) section 3.3: owner decisions 4.3 (distribution), 4.5 (GitHub remote name and
  visibility) and 4.7 (site), all OPEN on 2026-10-09. `docs/BOOTSTRAP_HANDOFF.md` sections 2, 3 and 4.
- StreamsPlayer for Windows (`P:\WINDOWS\Streams_Player`) ticket SP-0039 "Free promotion" (In Progress, read
  2026-10-09): its decisions 7, 8, 10 and 11, its criterion 12 and its resolved question 5. Cited by repository and
  id; nothing in that repository is edited from here.
- Portfolio dependency: the hub's ticket "sza.od.ua docs/PLAN-2026-10-09-free-promotion-campaign.md" owns the
  host-root `robots.txt`, the host-root Search Console property, the Bing import and Yandex host verification
  (canon PROMOTION.md section 3 and section 8, last paragraph). This ticket names that dependency and plans none
  of that work.
- Owner decisions, 2026-10-09, quoted: (a) "остальное всё по рекомендациям" - every open question of this ticket
  that carries a recommended option takes it; (b) "Контактный адрес: sza@ukr.net или [private address withheld]. - мне
  не важно" (the second address redacted 2026-11-13 before the public push, S0046 A1) - resolved to the address `SITE-FAMILY-MAP` rule 3 names, `sza@ukr.net`; (c) "Убрать названия
  конкурентов" - canon PROMOTION.md section 1 rule 4. Recorded in section 2.2 (decisions 3 and 8), section 3.3
  and section 8.

## 1. Goal

When StreamsPlayer for Android first ships, a stranger looking for an internet radio or live stream player on a
phone or a watch can find it - through surfaces the product owns, the Play listing, free Android catalogues and the
author's own writing, at no cost and with no telemetry. Before then, the campaign is designed, not run: the
positioning worksheet and the channel plan are written now, and every outward step is blocked until S0010's
decisions land.

### Why - baseline measured 2026-10-09

Every number below was read on 2026-10-09; none is estimated.

| What | Read how | Value |
| --- | --- | --- |
| Git remote | `git remote -v` | none (empty) |
| GitHub repository | `gh api repos/SerZhyAle/<name>` for `StreamsPlayer_mob`, `StreamsPlayer-Android`, `StreamsPlayer_Android`, `StreamsPlayerMob`, `StreamsPlayer-mob` | 404 for all five |
| Commits / tags | `git log --oneline`, `git tag` | 2 commits, 0 tags |
| Declared channels | `.sza-canon.json` `channels` | `[]` |
| Site | `.sza-canon.json` `site.kind` | `none` (decision 4.7 open) |
| Privacy page | `.sza-canon.json` `privacy.page` | `null` |
| Ledger / What's new source | `.sza-canon.json` `ledgerShape` | `none` (chosen in S0010) |
| Positioning source | `POSITIONING.md` in this repository | not present |
| Forbidden-terms list | this repository | not present |
| Store listing source (texts, screenshots, feature graphic) | this repository | not present |
| Stars, forks, downloads, traffic, `usesCustomOpenGraphImage` | GitHub API | not applicable - no repository exists |
| Play installs | Play Console | not applicable - no listing exists |
| Host root `https://serzhyale.github.io/robots.txt` | `curl` status | 404 (the hub ticket's item) |
| Product state | `README.md` "Status", `PLAN/spec-catalog.jsonl` | skeleton: S0002 Implemented, S0003-S0013 Draft |

Reference only, not this product's baseline: the sibling `SerZhyAle/StreamsPlayer` (Windows) is public with
`description` null, `homepage` null, `topics` [], 0 stars, 0 forks, `usesCustomOpenGraphImage` false (REST and
GraphQL, 2026-10-09). The name `SerZhyAle/StreamsPlayer` is therefore taken; this product needs another (4.5).

Nothing exists yet that a stranger could find or install. The cost of designing the campaign now is low; the cost
of designing it after launch is a launch week spent writing fields by hand, and a one-shot moment (canon
PROMOTION.md section 6) fired on an unprepared product.

## 2. Scope

### 2.1 Non-goals

Canon PROMOTION.md section 1 binds whole; as this product's lines:

- No money: no ads, sponsored posts, paid reviews, paid directories or paid priority review. The Play developer
  account fee is distribution, decided in S0010 and CHANNEL_MATRIX, not promotion.
- No telemetry to measure the campaign: no analytics SDK, crash reporter with tracking, click redirector or `utm_`
  parameter in the app, a listing or a site. No Google Analytics or any other tracker on any page.
- No in-app nagging: no rate-us prompt, share button or notification that exists to feed a channel.
- No feature built to qualify for a catalogue.
- No meta keywords, no `robots.txt` `Disallow` of CSS, scripts or images, no `FAQPage` or `HowTo` markup for rich
  results, no `aggregateRating` or `review` unless real ratings by real users are visible on the page.
- No download-wrapper portal (a site that wraps the APK in its own installer or downloader), whatever traffic it
  offers; APKMirror and APKPure are mirrors of Play builds, not channels.
- No feature advertised before it ships: Wear playback, the watch face, widgets and m3u import (S0009 optional),
  receiving live broadcasts and Cast (handoff section 3, second wave) are not claimed until the release that
  carries them.
- Product-specific traps: no `iptv`, "free TV" or channel-brand word in any discoverability field, and no
  competitor's product or brand name in anything published (section 2.2, decision 3); no store claim the noLegal sideload variant does not honour, and the reverse; no listing copy that
  hides live broadcast from the microphone and camera.
- The host-root robots, console and Yandex work - the hub's ticket owns it.

### 2.2 Decisions

1. Canon PROMOTION.md section 1 rules 1-9 by reference, executor tags included: **A** agent alone, **A·GO** agent
   after the owner's go naming the step, **A→O** agent drives and the owner acts at one point, **O** owner only.
2. **Nothing outward before S0010.** Every step tagged A·GO, A→O or O below is blocked until S0010 records 4.3,
   4.5 and 4.7. The A steps (worksheet, forbidden-terms list, listing source drafts, research) may run now.
   *Amended 2026-11-13:* S0010 recorded all three - 4.5 (`SerZhyAle/StreamsPlayer_Android`, public) and 4.7 (a dedicated
   family page and a hosted privacy policy) as answered on 2026-10-10, and 4.3 as superseded by В39 (2026-10-14): the first
   release goes straight to Google Play, GitHub Releases after it. The decisions no longer block; the outward steps now wait
   on the owner's go and on the ticket that creates their target (S0046 the repository, S0047 the listing, S0045 the site).
3. **SP-0039's forbidden-terms decision applies here.** StreamsPlayer for Windows decided to remove the `iptv` tag
   and to keep a piracy-signal class out of every discoverability field (SP-0039 decision 8, resolved question 5;
   its `msix/listing/forbidden-terms.txt` read 2026-10-09). On 2026-10-09 that decision has not shipped there: `iptv`
   is still in its three `winget/templates/` locale manifests and SP-0039 criterion 12 is open. The decision carries
   over here; the Windows state does not. This product opens third-party stream addresses too, and Play forbids
   references to other apps in metadata (canon PROMOTION.md section 2), so two classes bind here:
   - the piracy-signal class - `iptv`, "free TV" and their transliterations - in every field and text;
   - competitor product and brand names (owner, 2026-10-09; canon PROMOTION.md section 1 rule 4) in anything
     published: keywords, GitHub topics, Play and catalogue fields and search terms, site page sources, the
     README in every locale, in-app strings, outreach texts, and "X alternative" phrasing - AlternativeTo
     included, whose entry names no competitor. A generic alternative is named instead ("a radio app tied to
     one directory"). Names live only in the internal worksheet research (section 2.3, step P1). A bundled
     component credited in the notices and platform names (Android, Wear OS, Google Play, GitHub) are not
     competitor names.

   `m3u playlist` is treated differently from the Windows list, whose reason ("a playlist-sideloading claim, not
   what this app is") does not hold here: m3u import is an owner wish whose code moves with the core (handoff
   section 3, S0009). It stays out of every keyword, tag and topic field (the same piracy-signal read by a
   reviewer), but once S0009 ships it, a plain feature sentence in a description may name it.
4. **Three audiences, never one post.** SP-0039 decision 7 (the radio and live-video listener is not the RTSP
   camera user) carries over, and this product adds a third: the person who broadcasts the phone's microphone or
   camera, or the watch's microphone. A post addresses one of them.
5. **The brand stays one; the surfaces disambiguate.** "StreamsPlayer" names both products. Repository name, Play
   title and site copy separate them by platform ("for Android", "for Wear OS") without a second brand. The exact
   spelling is an owner decision (section 3.3, item 3).
6. **AI assistance is never hidden** (SP-0039 decision 11, canon PROMOTION.md section 1 rule 5). This product is
   built with heavy AI assistance. Where a venue refuses AI-made code or text, the product is not submitted there
   or the text is the owner's own; where it asks, the answer is disclosed.
7. **The Play listing is the main owned surface** if 4.3 picks Play: it is the one surface the compass persona
   (ordinary non-technical people) actually searches. Catalogue and community channels follow it, never lead.
8. **Contact address is `sza@ukr.net`** (owner, 2026-10-09; the address `SITE-FAMILY-MAP` rule 3 names). Every
   surface this product publishes that states a contact - README, Play listing contact field, privacy page,
   site footer, catalogue entries - uses it. On 2026-10-09 no surface in this repository states a contact, so
   nothing changes now; P3 and P5 write it.

### 2.3 Positioning worksheet (draft, written 2026-10-09)

No positioning source exists in this repository. The worksheet below is the draft; writing it as the repository's
`POSITIONING.md` is step P1 (section 2.5), and if 4.7 gives the product a site, that file becomes the published half
bound by `SITE-REPRESENTATION` rules 1 and 2, as StreamsPlayer for Windows does - never a second source.

1. **Competitive alternatives** (to be taken from real Play reviews and forum threads in step P1, not
   brainstormed; candidates to check): a radio app tied to one aggregator's directory; a general video player
   that needs a URL pasted every time; a camera vendor's own app for RTSP; doing nothing on the watch. The field
   names alternatives generically; product names read in P1 stay in the research notes of the tactical folder
   and never enter `POSITIONING.md`, whose content may become the published half (decision 3).
2. **Unique attributes** - each needs a code pointer, and today none can have one (the apps are empty
   skeletons). Each stays a candidate until the ticket that builds it is Verified; an attribute without a pointer
   at listing time is cut (canon PROMOTION.md section 2):
   - curated catalog plus own channels, pins and collections - S0004;
   - radio plays straight from the list, video in a fullscreen player, correct media type - S0003, S0005;
   - RTSP streams - S0003, S0005;
   - live broadcast of the phone microphone and camera, and of the watch microphone - S0006, S0007;
   - a Wear OS app and a watch face - S0007, S0008;
   - EN/RU/UK from the first release, TalkBack and touch / gamepad / TV-remote parity (handoff section 3);
   - "no background surprises" (handoff section 2 goal 5) - needs a measurable pointer before it is claimed.
3. **Value** - written in the user's words once alternatives are read (jobs-to-be-done form).
4. **Target persona** - default from the compass: ordinary non-technical people who listen to internet radio or
   watch a live stream on a phone or watch. The RTSP camera user and the broadcaster are secondary segments
   (decision 4).
5. **Market category** - an existing one people search for. Candidates: "internet radio player", "live stream
   player". A real fork, so an owner decision (section 3.3, item 2).

**Compass conflict** (canon PROMOTION.md section 2): RTSP, HLS and DASH are jargon to the persona. The Play listing
and any site speak to the persona and lead with radio and live video; a technical catalogue or subreddit may lead
with RTSP or broadcast.

**Derived fields** - filled from `POSITIONING.md` in step P3, limits as canon PROMOTION.md section 2 reads them
2026-10-09: Play title (30 characters; name plus at most a category word), Play short description (80), full
description (4000), per-locale search wording researched per locale for EN/RU/UK, GitHub description and topics (at
most 20, none from the forbidden list) once a repository exists.

### 2.4 Channels (shape A - Android app; canon PROMOTION.md section 5, read 2026-10-09)

| Channel | Verdict | Reason or revisit condition |
| --- | --- | --- |
| Google Play listing (phone, Wear OS, watch face) | **planned**, blocked by S0010 4.3 | Listing source in the tree first (P3); metadata policy - no ranking, price or promo wording, no keyword repetition, no other apps named - re-read before P3 is used. Needs a hosted privacy page (S0010, gate SZA-SURF01) and a data-safety form that says the same as that page. |
| GitHub Releases (sideload APK) | **planned**, blocked by S0010 4.3 and 4.5 | Distribution, owned by S0010 and CHANNEL_MATRIX; listed here because Obtainium and SourceForge depend on it. An INSTALL-TRUST page comes with a sideload download page (S0010). |
| Obtainium | **planned**, blocked by GitHub Releases | No gate; publish an "Add to Obtainium" link on the releases page and the README. Repository page answered 200 on 2026-10-09. |
| AlternativeTo | **planned**, after the first public release | Verified account, moderation, English, no links in the description; our description names no competitor (decision 3) - the site's own users link alternatives. The FAQ page answered 403 to a non-browser read on 2026-10-09 - **[unverified]** at source; re-read in the owner's browser before acting. |
| F-Droid | **deferred** - feasibility study F1 | Read 2026-10-09 at f-droid.org Inclusion Policy: built from source with a FLOSS toolchain, and Google Play Services, Firebase, Crashlytics and proprietary tracking SDKs are forbidden. The skeleton's `gradle/libs.versions.toml` has no `com.google.android.gms` or Firebase entry today, but FastMediaSorter's (the port source, read 2026-10-09) carries `play-services-wearable`, `play-services-cast-framework`, `play-services-auth`, `play-services-auth-blockstore`, `credentials-play-services-auth` and `kotlinx-coroutines-play-services`. Revisit whenever the port could bring any of them in: S0007 (Wear Data Layer), second-wave Cast (handoff section 3) and any auth or backup code. Feasible only if the noLegal phone build stays free of all of them; a test or gate that keeps every GMS artefact out of the noLegal variant is the F1 predicate. Anti-features are labels, not bans. |
| IzzyOnDroid | **refused** | App Inclusion Policy, read 2026-10-09 at izzyondroid.org: "We are strongly opposed to apps which are fully or in part created by generative AI tools", "Vibe-coded apps will be rejected", code must be free of LLM output. This product's code is AI-assisted; canon PROMOTION.md section 1 rule 5 - not submitted. |
| SourceForge import | **deferred** | Mirrors repository and releases unmodified; revisit after GitHub Releases exist, with the integrity check (download through the directory's own button, compare SHA256). |
| APKMirror, APKPure | not a channel | Mirrors of Play builds. If found serving a file that differs from the published hash, a removal request. |
| Download-wrapper portals (Softonic, Uptodown and the like) | **refused** | Canon PROMOTION.md section 1 rule 6 - wrapping or bundling history; if found listed, ask for a direct link or removal. |
| awesome-* lists | **deferred** | Each list's contributing note read at attempt time; several refuse AI-generated work (decision 6). One entry per PR, A·GO. |
| Hub card and SITE-FAMILY-MAP | **deferred**, blocked by S0010 4.7 | A family-grid link goes through `SITE-FAMILY-MAP` in the catalog first (canon PROMOTION.md section 4 item 6: one edit changes every family page). |
| Cross-link from StreamsPlayer for Windows | **deferred** until something is installable | The Windows README and site would link here only where it serves the reader; this repository writes the request to that session and never edits that repository (canon PROMOTION.md section 4 item 6). |
| Show HN, Product Hunt, Reddit, Habr, dev.to, Lobsters | **deferred** behind the one-shot gate of canon PROMOTION.md section 6 | Each fires once per product, on a release with phase A complete and a demo. Show HN and Habr refuse generated or AI-edited text - the owner's own text or no post; the posting is always **O**. |

Windows-only rows of canon PROMOTION.md section 5 (winget, Microsoft Store, Scoop, Chocolatey, FossHub, Softpedia,
MajorGeeks) do not fit shape A and are not planned. Chrome / Edge / VS Code listings do not fit either.

### 2.5 Steps (outline; the tactical folder with the go ledger is written once the owner approves this ticket)

Phases in dependency order. Every outward step carries its tag. No step tagged A·GO, A→O or O runs before S0010's decisions; A steps run now
unless a step names another blocker.

- **P0 Baseline** - **A**: once the tactical folder exists, write the section 1 baseline as a dated file there
  (`metrics/2026-10-09-baseline.md`) by the checkpoint script of P10; re-read every row on the day the first
  outward step is planned.
- **P1 Worksheet** - **A**: read real Play reviews and forum threads for the alternatives; write `POSITIONING.md`
  (section 2.3) with a code pointer per attribute; owner decides category and brand spelling.
- **P2 Forbidden terms** - **A**: this product's own forbidden-terms list (the piracy-signal class of decision 3 in
  every script of EN/RU/UK, `m3u playlist` for keyword, tag and topic fields only, plus the competitor product and
  brand names P1 finds, binding in every published text, not only in fields), and a test that keeps every term
  out of every tag and keyword list the build owns.
- **P3 Listing source** - **A**: Play title, short and full description per locale, derived from `POSITIONING.md`,
  kept in the tree (format decided with S0010).
- **P4 Repository metadata** - **A·GO** (blocked by 4.5): `gh repo edit` description, homepage, topics; social
  preview through the owner's browser, **A→O** at sign-in.
- **P5 Pages and indexing** - carried out by S0045 as its P6 (owner, 2026-11-08): the step and criteria 2 and 3 and the
  1200x630 half of criterion 4 below are worked and checked there, not from this ticket; the text below stays as the
  record. **A·GO** (blocked by 4.7): if a site is decided, page heads of canon PROMOTION.md
  section 3 with one truthful `MobileApplication` JSON-LD, generated sitemap, the mandatory pages; the sitemap is
  submitted to the host-root Search Console property owned by the hub's ticket (**A→O** at console sign-in). If no
  site, the hosted privacy page alone (S0010).
- **P6 Demo** - **A**: one silent capture under a minute, once S0003-S0005 play something; encoded per surface.
- **P7 Play listing** - **A→O** (blocked by 4.3): console fields from P3; the owner signs in, accepts terms and
  submits; the data-safety form agrees with the privacy page.
- **P8 Catalogues** - Obtainium link **A·GO**; AlternativeTo **A→O** (account), its description naming no
  competitor (decision 3); F-Droid per F1 **A·GO**
  (merge request to `fdroiddata`), only if feasible.
- **P9 Launch moments** - **O** for every post and reply; the agent prepares facts, the rules record and the
  form, and checks the owner's draft.
- **P10 Metrics and closure** - **A**: a script in this repository that reads the counters and writes each
  checkpoint as a dated file (canon PROMOTION.md section 7), at most fourteen days apart while outreach is active.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0010 (blocks every outward step: 4.3, 4.5, 4.7), S0003-S0008 (the attributes the listing
  may claim), S0011 (the screen map the demo shows).
Owner's go 2026-10-09: "остальное всё по рекомендациям" - an item with a recommended option takes it; an item
this ticket gives no recommendation stays open (items 2-5).

- **1. Approve the campaign shape** (this ticket): the channel verdicts of section 2.4, the IzzyOnDroid refusal,
  the F-Droid feasibility study. **RESOLVED 2026-10-09 - approved as proposed** (the ticket's own proposal is the
  recommendation). Outward steps stay blocked by S0010 (decision 2).
- **2. Market category:** **ASSUMED 2026-11-13 - "internet radio player", with "live stream player" as the second phrase**
  (see "Assumed" below; open to the owner's overrule). The ticket named candidates and recommended none; the researched
  recommendation was taken because the choice is reversible inside the ticket.
- **3. Brand spelling on Android surfaces:** **RESOLVED 2026-11-08 - "StreamsPlayer"** (owner, quiz of S0045, D1, option (a):
  the name on the device and in the store; `app_name` per S0030; the platform is carried by the type line and the eyebrow). One
  answer served both tickets.
- **4. Russian-speaking reach:** OPEN - no recommendation. Whether Yandex matters for this product at all (canon PROMOTION.md section 3: a
  host-root act owned by the hub ticket, relevant only outside Ukraine).
- **5. F-Droid:** OPEN - no recommendation (item 1 approves the feasibility study F1, not the constraint). Whether the noLegal phone build should be kept free of every Google Play Services artefact the
  port could bring in (wearable for S0007, cast-framework for second-wave Cast, auth and auth-blockstore) so that
  F-Droid stays feasible - a constraint S0007, the Cast work and any auth port would then carry, enforced by a
  test or gate on the noLegal variant.
- **6. Contact address:** **RESOLVED 2026-10-09 - `sza@ukr.net`** (owner: "мне не важно"; the address
  `SITE-FAMILY-MAP` rule 3 names). Decision 8.
- **7. Competitor names:** **RESOLVED 2026-10-09 - none in anything published** (owner: "Убрать названия
  конкурентов"; canon PROMOTION.md section 1 rule 4). Decision 3.

### Assumed (unattended, 2026-11-13)

Each is reversible inside this ticket (text in unpublished sources); the owner may overrule any of them, and the rework is
one edit of the source named.

- **Market category (item 2) - "internet radio player", second phrase "live stream player".** Weighed against: "live stream
  player" first (the narrower, more technical phrase; the persona does not search for it), and the persona's own words from
  reviews (not read yet, P1 refinement). Why: the primary persona is an ordinary listener, radio is how a person reaches the
  product, and the radio apps compete in exactly this category (research notes of 2026-11-13). An overrule changes the Play
  title's category term, the first search phrase of every locale and section 3.5 of `POSITIONING.md`, nothing else.
- **Title form - the product name, a colon and the category phrase** ("StreamsPlayer: Internet Radio", 29 characters; the
  same in RU and UK). Weighed against: the bare name (no category word, loses the search phrase) and one category word
  ("StreamsPlayer: Radio", under-sells video and camera). The ticket's "at most a category word" is read as one category
  term. An overrule is one line in each `docs/play/PLAY_LISTING*.md`.
- **The list lives in `tools/promotion/forbidden-terms.json`, not in `docs/TERMBASE.json`.** Weighed against extending the
  term base: its gate scans only the help sources, the privacy policies and the portal sections, so extending it means
  editing a measured gate for sources it does not read, and its entries carry no scope for fields.
- **Four pillars, RTSP third** (radio, live video, RTSP, live broadcast), the first three as the Windows product's terms so
  the family reads alike; broadcast is the Android product's fourth. Weighed against three pillars with RTSP folded into
  video: jargon stays in one short section of the listing instead of the lead sentence.
- **Listing claims limited to what carries a code pointer** (`POSITIONING.md` section 3.2). Not claimed: input parity and
  TalkBack (the gaps of S0052 are open), "no background surprises" (no measured pointer), Cast and exchange receiving
  beyond what the README states. The playlist import is named in a plain feature sentence ("import a playlist file"), never
  with the term `m3u playlist` (decision 3).

## 4. Acceptance

Canon PROMOTION.md section 8 item 6, as static predicates for this repository. A criterion marked
**void until public** cannot be met while S0010's decisions are open; it is kept, not deleted, and binds from the
day its blocker lands.

From S0039 (the exchange server's mark, owner's answer of 2026-11-08 to its item 5), `EXCHANGE-MARK` rule 12: a site
page or a store listing section that features the exchange server shows the mark in that section (a file of the
catalog's artwork, copied byte for byte), never as the product's own icon; the launcher icon and the listing icon are
never replaced or badged by it. Store graphic sizes are out of the contract's scope.

- [ ] 1. Repository description, homepage and topics set from `POSITIONING.md`:
  `gh api repos/SerZhyAle/<name> --jq '{description,homepage,topics}'` matches the derived fields, at most 20
  topics, none in the forbidden-terms list. **Void until public** - no repository exists (4.5).
- [ ] 2. Every page carries the canon PROMOTION.md section 3 head and one truthful `MobileApplication` JSON-LD
  block (no `aggregateRating`, `review`, `FAQPage`, `HowTo`, no retyped `softwareVersion`), the site gate green.
  **Void until public** - no site (4.7); if 4.7 decides no site, void for good except the privacy page.
- [ ] 3. The sitemap is generated with `lastmod` from commit dates, lists every page, and the host-root Search
  Console property reports it "Success" with its discovered-URL count. **Void until public** - no site (4.7) and
  the host-root property belongs to the hub's ticket.
- [ ] 4. One social-card artwork rendered at 1200x630 (Open Graph, with width, height and alt tags) and 1280x640
  (repository preview, PNG under 1 MB); GraphQL `usesCustomOpenGraphImage` true. **Void until public** - no
  repository (4.5), no site (4.7).
- [ ] 5. One demo capture, referenced from the README and the site (and the Play listing where it is used).
  **Void until public** - nothing plays yet (S0003-S0005) and there is no site.
- [ ] 6. Every outreach text exists in `PLAN/S0014_free-promotion-campaign/outreach/` before use, one file per
  target, with a header naming the rules it was checked against and the date. Binds from the first text written.
- [x] 7. One outcome line per attempted channel in section 5, refusals included. Binding now: the IzzyOnDroid
  refusal is recorded. (Held 2026-11-13: one row, the IzzyOnDroid refusal; no other channel attempted.)
- [ ] 8. Metrics at baseline (section 1, 2026-10-09) and at each checkpoint, at most fourteen days apart while
  outreach is active, each a dated file written by a script in this repository. The baseline is recorded now in
  section 1; its dated file and the script bind from the tactical folder (P0, P10). Checkpoints are **void until
  public** - no counter exists to read.
- [ ] 9. No analytics, tracker, telemetry or redirector added: `gradle/libs.versions.toml` and every module's
  `build.gradle.kts` carry no Firebase, Crashlytics, analytics or advertising SDK, and no page carries a tracker.
  Binds now. The second half - the privacy page still literally true - is **void until public**: no privacy page
  exists (`privacy.page` null; S0010 writes it). *2026-11-13:* the first half holds - a case-insensitive grep for Firebase,
  Crashlytics, analytics, advertising and error-reporting SDK names over `gradle/libs.versions.toml` and the five
  `build.gradle.kts` files returns 0 in every file, and none of `site/privacy*.html` carries a tracker or a `utm_`
  parameter; the privacy page now exists (`site/privacy.html`, S0033) but is not yet public, so the second half stays void.
- [ ] 10. Every text names the author as the author, answers the venue's AI rule (decision 6), and contains no term
  of this product's forbidden-terms list (P2) and no competitor product or brand name (decision 3). Binds from
  the first text written.
- [x] 14. No competitor product or brand name in any published source of this repository (README, in-app
  strings, listing source, site sources, outreach texts): a case-insensitive grep of those paths for the P2
  name list returns nothing, and every contact stated is `sza@ukr.net` (decision 8). Binds now; read
  2026-10-09 with no hit and no contact stated (section 5). Re-read 2026-11-13 by `tools/promotion/assert-promotion.ps1`
  (61 published files, 12 rules): exit 0, `assert-promotion: PASS`; every contact stated is `sza@ukr.net`.
- [ ] 11. The mandatory pages of DOCUMENTATION_CONCEPT section 4 exist and are in the sitemap. **Void until
  public** - no site (4.7); the hosted privacy page is required for Play either way (S0010).
- [ ] 12. (a) A test keeps every term of the forbidden-terms list out of every tag and keyword list the build owns.
  Binds from P2. **(a) met 2026-11-13:** the `Topics:` and `Search terms` lines and the title and short description are
  scanned by `tools/promotion/assert-promotion.ps1`, whose self-test `test-promotion-gate.ps1` fails on a seeded term in a
  topic (14 cases, exit 0). (b) Discoverability fields agree across channels (repository topics, Play listing, catalogue
  entries) and a term dropped from one for policy is dropped from all. **Void until public** - fewer than two
  channels exist.
- [ ] 13. Every outward act has a go-ledger row in the tactical folder quoting the owner's go; every A→O and O act
  was the owner's. Binds from the first outward act; none has happened.

## 5. Channel outcomes

| Date | Channel | Outcome |
| --- | --- | --- |
| 2026-10-09 | IzzyOnDroid | Refused without submitting - App Inclusion Policy rejects apps made in part with generative AI (read 2026-10-09). |

### Progress 2026-11-13 - the agent steps P0 to P3 and P10 (unattended pass, nothing outward)

- **Tactical folder** `PLAN/S0014_free-promotion-campaign/`: `research/alternatives-2026-11-13.md` (internal; the only place
  product names are discussed), `go-ledger.md` (no outward act yet), `metrics/2026-11-13-baseline.md`.
- **P1** `POSITIONING.md`: the one sentence in EN, RU and UK, four pillars, the worksheet with a resolvable code pointer per
  attribute (all 14 paths exist), components and install intent, topics and search terms per locale. The research read two
  searches of store pages and guides, not reviews: recorded as a WARN in the Last Audit and as Remaining development item 1.
- **P2** `tools/promotion/forbidden-terms.json` (12 entries: the piracy-signal class in Latin and Cyrillic, the other products'
  names, the field-only playlist term), `assert-promotion.ps1` (0.63 s, `PASS`), `test-promotion-gate.ps1` (14 cases, 6.05 s,
  `PASS`), wired into `pre-release-sweep.ps1` (check `Promotion`, self-test `PromotionGateSelfTests`) and
  `tools/ci/build-packages.ps1`. Not exercised until the release: the sweep and the CI step themselves (syntax only,
  `check-syntax.ps1` `PASS`).
- **P3** `docs/play/PLAY_LISTING.md`, `-ru.md`, `-uk.md`: title 29 characters, short description inside 80, full description
  inside 4000, in all three languages, pillars in order; the gate holds it. Registered (`positioning`, `play-listing`), indexed
  in `docs/README.md`, listed in `docs/DOCS_SURFACES.md`.
- **P10** `tools/promotion/write-checkpoint.ps1`: read-only counters into a dated file; the baseline file is its first output.
- **P5** untouched here: S0045 P6 carries it, and its blocker condition (P1 to P3 absent) no longer holds in the tree.
- **Known gap:** the Play title must not carry ranking, price or promo words; the three titles carry the product name and the
  category phrase only. The policy text is re-read before P7 uses the fields (section 2.4, first row).

### Progress 2026-10-09 - owner's go (a), (b), (c)

- **Competitor-name sweep (c).** Every published-surface source in the tree was grepped case-insensitively for
  radio, video, IPTV, camera and broadcast player names in this niche and for "alternative", "instead of",
  "replacement for", "vs", "like X": `README.md`, `phone/`, `wear/` and `watchface/` `res/values*/strings.xml`
  (EN/RU/UK), `docs/`, `AGENTS.md`, `CLAUDE.md`. No hit; nothing removed from a published surface. No listing
  source, site source, outreach text or forbidden-terms list exists yet.
- **Removed from this ticket's own text:** the AlternativeTo exception. Section 2.2 decision 3, before "Naming
  alternatives stays allowed where a venue expects it (AlternativeTo), never as a keyword" -> after "AlternativeTo
  included, whose entry names no competitor"; section 2.4 AlternativeTo row, before "naming alternatives is
  expected there" -> after "our description names no competitor (decision 3)"; section 2.1 trap, before
  "competitor name in any discoverability field" -> after "no competitor's product or brand name in anything
  published". The internal-only names of section 2.4 (download portals and APK mirrors named as channels to
  refuse) are research, not published text, and stay.
- **GitHub topics and live-channel tags:** none to remove - no repository exists (`gh api
  repos/SerZhyAle/StreamsPlayer_mob`, `StreamsPlayer-Android`, `StreamsPlayer_Android`: 404 on 2026-10-09) and
  shape A has no winget or Store manifest. Topic setting and every live-channel tag ride the next **A·GO** step
  (P4 for topics, P7 and P8 for listings), written without competitor names from the start.
- **Contact address (b):** `sza@ukr.net` decided; no surface states a contact today, so the address change is a
  step of P3 and P5, not an edit now.

## 6. Metrics

| Date | Source | Reading |
| --- | --- | --- |
| 2026-10-09 | section 1 baseline | No repository, no release, no listing, no site: every counter not applicable. |
| 2026-11-13 | `metrics/2026-11-13-baseline.md`, by `tools/promotion/write-checkpoint.ps1 -Baseline` (1.84 s) | No remote, 2 commits, 0 tags, channels `["play"]`, site kind `product`; the repository, both Play listings, the hosted privacy page and the landing answer HTTP 404; the host-root `robots.txt` answers HTTP 200 (the hub ticket's item landed since 2026-10-09). |

## 7. Risks

- **Name collision** with StreamsPlayer for Windows: search results and a repository name that do not say the
  platform send people to the wrong product (decision 5).
- **Play review on third-party streams**: an app that opens public stream addresses draws review under Play's
  content policies; an `iptv` or channel-brand word in metadata raises it (decision 3).
- **Broadcast disclosure**: microphone and camera use, a foreground service and the data-safety form must say the
  same thing as the privacy page; a listing that hides broadcast is an over-claim by omission.
- **Two variants**: a claim true for the store variant and false for the noLegal sideload variant, or the reverse.
- **Wear OS and watch-face listing rules** are their own; the watch-face composition is still open (handoff 4.4),
  so no design is promised.
- **AI-assisted code against venue rules** (decision 6) - IzzyOnDroid is already refused for this reason.
- **Firing a one-shot moment early** on a release without phase A and a demo wastes it for good.

## 8. Open questions

1. Market category (section 3.3 item 2) - assumed on 2026-11-13, open to the owner's overrule. Brand spelling (item 3) -
   resolved 2026-11-08 ("StreamsPlayer", S0045 D1).
2. Whether F-Droid feasibility is worth a constraint on S0007, the Cast work and any auth port (item 5) - still
   open: no recommended option.
3. The listing-source format in the tree - resolved: Markdown under `docs/play/` in EN, RU and UK, one file per language
   with the console's field names as headings (S0047 part A, item A5, names `docs/play/` as the listing source; written
   2026-11-13).
4. Whether Yandex matters for this audience (item 4) - still open: no recommended option.
5. AlternativeTo's current rules - unread at source on 2026-10-09 (403); read in the owner's browser before P8.

## Last Audit

**Date:** 26-11-13 01:55 | **Mode:** static | **Outcome:** Partial
**Counts:** PASS 13 | WARN 1 | FAIL 0 | MANUAL 3 | UNCHECKABLE 0 | EXEMPT 3 | RELEASE 3

### Checks run (this pass, each fresh)

- PASS P0: `metrics/2026-11-13-baseline.md` exists, written by `write-checkpoint.ps1 -Baseline`, exit 0, 1.84 s.
- PASS P1 pointers: all 14 code paths named in `POSITIONING.md` section 3.2 resolve (`Test-Path` True x14).
- PASS P2 gate: `tools/promotion/assert-promotion.ps1` exit 0, `assert-promotion: PASS (rules=12 published-files=61 field-texts=40)`, 0.63 s.
- PASS P2 self-test: `tools/promotion/test-promotion-gate.ps1` exit 0, `promotion-gate-selftest: PASS (14 cases)`, 6.05 s (release class).
- PASS P3: the same gate holds title 29, short and full inside their limits and the pillar order, EN, RU and UK.
- PASS P10: `tools/promotion/write-checkpoint.ps1` exits 0 and writes the dated file (the P0 baseline is its first output).
- PASS documentation gates, each alone: `assert-doc-registry` (records=33, 1.59 s), `assert-doc-links` (0.94 s), `assert-doc-style`
  (0.75 s), `assert-termbase` (0.71 s), `assert-translation-freshness` (0.83 s), all exit 0 with `PASS`.
- PASS `check-syntax.ps1` on the edited `pre-release-sweep.ps1`: exit 0, `release-syntax: PASS`, 0.37 s.
- PASS tense grep (soon, planned, not yet, coming, beta, experimental, roadmap and their RU forms) over `POSITIONING.md`,
  `docs/play/PLAY_LISTING*.md`, `tools/promotion/README.md`: no match.
- PASS acceptance 9 first half: 0 hits of tracker SDK names in the six build files and in `site/privacy*.html`.
- PASS acceptance 14, 12(a), 7: see section 4 for each line.

### Action items

1. WARN - `PLAN/S0014_free-promotion-campaign/research/alternatives-2026-11-13.md` - P1 asks for real Play reviews and forum
   threads; the pass read store pages, listicles and a vendor guide. Read the reviews in the owner's browser once the listing
   exists and change `POSITIONING.md` section 3 only where the persona's words disagree (Remaining development item 1).
2. The market category and the title form are assumed decisions (section 3.3, "Assumed"); a different answer from the owner is
   one edit each, named there.
3. S0045 waits on this ticket by `Blocker: S0014` in its journal note; its condition (P1 to P3 absent) no longer holds in the
   tree, but the queue releases a blocker only when this ticket reaches a ready status (Implemented, Verified, BlockNeedUserTest).
   The owner or S0045's session moves it; it is not edited from this pass.

### Manual / on-target

- [ ] Owner review of `POSITIONING.md` and the three `docs/play/PLAY_LISTING*.md` texts (the verification level of this ticket
  until launch), including the assumed category and title form; counts as passed when the owner accepts or overrules them.
- [ ] Outward steps P4, P7, P8 and P9, each with its go in `go-ledger.md` before the act, each after the ticket that creates its
  target (S0046 repository, S0047 listing, S0046 part B releases); AlternativeTo's rules read in the owner's browser first.
- [ ] Owner items 4 (Yandex) and 5 (the F-Droid constraint on S0007, Cast and auth ports) answered right before the phase that
  needs them.

### Exempt and release items

- EXEMPT: criteria 1, 2, 3, 4, 5, 8 (checkpoints), 9 (second half), 11, 12(b) are void until public by their own text; 6, 10
  and 13 bind from the first outreach text or outward act and none exists; P5 is carried out by S0045 P6.
- RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` runs the new `Promotion` check, the
  `PromotionGateSelfTests` entry and, on CI, `tools/ci/build-packages.ps1` runs the gate (wiring written, not exercised).
- RELEASE: `pwsh -NoProfile -File tools/docs/assert-docs.ps1` and `tools/docs/test-docs-gates.ps1` (the registry gained two records
  and `docs/README.md` two lines; each gate alone passed, the aggregator and the gates' self-test were not run).
- RELEASE: the P6 demo and the listing screenshots need a build on an emulator or device (`./a.ps1 d` or the release's debug
  packages), run at release or when the owner names it.
