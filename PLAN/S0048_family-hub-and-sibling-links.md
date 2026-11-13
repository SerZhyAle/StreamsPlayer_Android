# S0048 - Add StreamsPlayer for Android to sza.od.ua and to the other products' sites and documentation

**Ticket:** S0048
**Status:** BlockByOtherTask
**Status note:** Blocker: S0045 - G1 closed: the landing (/, index-ru.html, index-uk.html) and help/en/ under https://serzhyale.github.io/StreamsPlayer_Android/ answer 404 on 2026-11-08 (S0045 waits on S0014). S1 drafts done (plan R1 to R8): portal labels fixed, 12 self-contained requests and the target table in PLAN/S0048_family-hub-and-sibling-links/requests/. Next: R9, the owner routes T0 then the footer requests once G1 answers 200. Later gate: S0047 Play listing 200 (G2) for R10.
**Complexity:** hard - assessed at the spec pass and confirmed at the plan pass of 2026-11-08: shared publication contracts and
twelve independently owned targets in up to thirteen locales; the owner decisions are answered.
**Date:** 2026-10-26
**Phase:** after the product answers - each target waits for its gate (section 2.2); drafts may be prepared earlier, requests are
sent when the gate opens
**Verification level:** from outside, per target: the page that must carry the row, card or section is fetched and the expected
string is found in every locale page the target publishes; the contracts catalog lists the row; the hub's two page copies are
byte-identical. Static predicates in section 4, each with its command.
**Owner:** the StreamsPlayer Android session of this repository drafts the requests and checks the results; every target is
changed by its own session through its own process and gates (`CLAUDE.md` sections 1 and 6), routed by the owner.

## Remaining development

*As of 2026-11-08, checked against the tree. Built: step S1 (plan R1 to R8) - the portal sections checked against the shipped
strings and the request files with their index in `PLAN/S0048_family-hub-and-sibling-links/requests/`; evidence in section
7.3.*

1. **R9 - send at G1.** Waits for S0045 to publish the landing and `help/en/` (both 404 on 2026-11-08), then the owner routes
   T0 first and the footer requests after the catalog row lands.
2. **R10 - send at G2.** Waits for S0047 (the Play listing answers 200); T3 part B also needs one recorded run.
3. **R11 - verify from outside and close.** Waits for each target's publication (G3).

## Captured request and migration scope

Owner, 2026-10-26, "create specifications", item 3 of four, verbatim: "update sza.od.ua and add this application into all other
products related with links and articles in their documentation".

S0045 is this repository's half. This ticket is everything that lives in another repository: the contracts catalog row, the hub
card and README, the footer grid of eight sibling sites, the body-copy links and articles where a sibling genuinely covers what
this product does not, the shared media exchange portal's sections, and the canon repository's records. This repository holds
only the requests and the evidence (`PLAN/S0048_family-hub-and-sibling-links/`, created at the first request).

## 0. Source

- `SITE-FAMILY-MAP` 1.9: section 2 (the table: eight tools and the hub), rule 1 (every sibling row is in every other product's
  footer grid), rule 2 (the hub carries no tools grid, its cards are one link each), rule 3 (contact `sza@ukr.net`), rule 4
  (contextual cross-links are body copy only where a tool covers what another does not; a README "Related" section promoting an
  unrelated sibling is footer duplication), rule 5 (a URL enters the table only once it answers), rule 6 (help index column),
  section 2a (shared pages), section 4 (adding a tool is additive).
- `PAGE-CONTENT` "Hub contract": the hub is a curated index of main current products; neutral copy. `PORTAL-DOCS` and
  `DOC-EXTERNAL-QUALITY` section 9: the media exchange portal is one corpus with several writers and one publisher.
- The hub's `CLAUDE.md` (read 2026-10-26): a card edit is four edits (inline fallback plus the `en`, `ru` and `uk` dictionaries);
  `index.html` and `embed.html` are byte-identical by hand, `diff -q` must exit 0 before a deploy; the deploy is `deploy.bat`
  after the compliance gate; a site update is not a release.
- `feature-to-site` skill, step 9: a new product also means the kit's cross-link table, every sibling site's footer and the hub
  repository's own README product list; adding a product to the hub is an owner decision.
- `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` (S0030): the three portal sections already written in EN, RU, UK, plus the correction of
  the "devices that play broadcasts" row.
- Canon `PROMOTION.md` section 4 item 6: one edit to the map changes every family page.
- S0010 decision 4.7: this product joins `SITE-FAMILY-MAP` with a dedicated page.

## 1. Goal

A visitor of sza.od.ua, of any sibling product's site or of the media exchange portal can reach StreamsPlayer for Android in one
click where it is relevant, with the same name, the same contact and a link that answers; and a visitor of this product's
landing reaches the family. No sibling is promoted where it covers nothing the reader needs.

### Baseline - read 2026-10-26

| What | Read how | Value |
| --- | --- | --- |
| Family map row for this product | `SITE-FAMILY-MAP` 1.9 section 2 | none; rows: Fast Media Sorter & Organizer, Fast Media Sorter & Sharing for Windows, CyrFlip, doc-html-translate, FileDO, STREAMS Player, OneClickRunner, Universal Agent Kit, SZA hub |
| Hub card | hub `index.html` | `STREAMS Player` (Windows Desktop) with `assets/streamsplayer.png`; no Android card |
| Hub README product list | hub `README.md` line 51 | lists STREAMS Player (Windows) only |
| Portal sections | portal HTML in the FastMediaSorter Android repository, grep for "StreamsPlayer" | none in the portal pages; the hand-over exists as `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` and is not applied |
| Sibling footers | grep for the OneClickRunner URL (a row every footer carries) | present in the FastMediaSorter Android site (12 or more locale pages), FastMediaSorter for Windows (`lang/*/`), CyrFlip, doc-html-translate, FileDO, OneClickRunner, STREAMS Player (`docs/<lang>/`), Universal Agent Kit |
| The row's URL | `curl -L https://serzhyale.github.io/StreamsPlayer_Android/` | 404 (S0046, S0045) |
| `https://sza.od.ua` and `https://serzhyale.github.io/StreamsPlayer/` | `curl -L` | 200, 200 |

## 2. Scope

### 2.1 Targets - what each one owes

Every change is made by the target's session. The request file for each target states the exact change, the text in the target's
languages where text is needed, the evidence that the URL answers, the checks the target runs and the check this repository runs
afterwards. Texts are written as shipped facts (invariant 17).

| Id | Target (owner) | What it owes | Mechanism and gate |
| --- | --- | --- | --- |
| T0 | Contracts catalog - `SITE-FAMILY-MAP` (owned by the hub), registry rows (canon) | A new section 2 row: Tool per owner input D1 of S0045, Type "Android and Wear OS stream player" (or the owner's wording), URL `https://serzhyale.github.io/StreamsPlayer_Android/`, Help index `-` or `help/en/` (S0045 D2); the consumer lists of `SITE-FAMILY-MAP`, `PAGE-CONTENT`, `PAGE-STYLE` gain this product; additive version bump, document-log row; this product's registry rows | An exact diff with the reason, sent to the canon session `sza-unified-rules-dc` (`CLAUDE.md` section 6), which routes it to the hub as owner of the map. Gate G1 (rule 5) |
| T1 | Hub `sza.od.ua` (hub session) | A product card "StreamsPlayer" for Android and Wear OS: four edits (inline fallback, `en`, `ru`, `uk` dictionaries), the application icon in `assets/`, one link to the landing; the same edit in `embed.html`; `diff -q index.html embed.html` exit 0; the hub README product list line; the hub compliance gate and UI check | Owner input D1. The hub deploys with `deploy.bat` only on the owner's confirmation. Gate G1 |
| T2 | Fast Media Sorter & Organizer for Android (FastMediaSorter Android session) | (a) the footer row in every locale page that carries the grid; (b) the three portal sections and the device-row correction of `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` applied to `broadcast-receive`, `broadcast-start`, `watch-exchange` in EN, RU, UK; (c) a short body-copy mention where its docs say which devices play a broadcast (rule 4: this app broadcasts, StreamsPlayer plays radio and live video and receives the account's broadcasts); (d) the app's own README/docs "Related" line if it has one | The portal is that repository's publication process. Gates G1 for (a), G2 for (b) to (d) |
| T3 | Fast Media Sorter & Sharing for Windows (FastMediaSorter Windows session) | (a) the footer row on every locale page that carries the grid (landing and the secondary pages that repeat it); (b) body copy where its pages describe broadcasting from the Windows host and where the broadcast is played; (c) the portal link stays the portal's | Gates G1 for (a), G2 for (b) |
| T4 | STREAMS Player for Windows (StreamsPlayer Windows session) | (a) the footer row on every locale page (`docs/<lang>/index`, `privacy`, `support`, `trust`, `whats-new`, and the rest that carry the grid); (b) a landing section or a "Related" link to the Android and Wear OS product: the same stream bank and playlist formats, the same brand on another platform (rule 4: it covers a platform this one does not); (c) its README line "Android sibling" gets the live URL | Gates G1 for (a), G2 for (b) and (c) |
| T5 | CyrFlip, doc-html-translate, FileDO, OneClickRunner, Universal Agent Kit (each its own session) | The footer row only, localized type label as each site localizes the others; **no body-copy link** (rule 4: none covers what this product does not and none is covered by it) | Gate G1 |
| T6 | Canon repository `sza-unified-rules` (canon session) | The contrib record `rules/contrib/streamsplayer_mob.md` (channels `play`, later `github`; the site; the landing); the kit's cross-link table named by `PROMOTION.md` section 4 item 6; the registry consumer rows | An exact diff to the canon session. Gates G1, G2 |
| T7 | Hub sitemap and its search surfaces | If the hub's `sitemap.xml` lists product pages, the landing is added by the hub session; host-root Search Console, robots and Bing stay the hub's ticket | Gate G1 |

### 2.2 Gates

- **G1** - the landing answers 200 in EN, RU, UK (S0045 P9). Nothing is sent to T0, T1, T5, T6, T7 or the footer parts of T2 to T4
  before it, because `SITE-FAMILY-MAP` rule 5 admits a URL only once it answers and a footer link to a 404 is wrong on eight sites.
- **G2** - the Play listing answers 200 (S0047 D-3). Text that says "on Google Play", and the portal sections that describe a screen of
  an app a reader can install, wait for it.
- **G3** - each target's own publication (its deploy or release), observed from outside; this repository never waits inside
  another repository.

### 2.3 Steps

- **S1 Drafts - A.** Before the gates: the exact diff for T0, the card text and README line for T1 (EN, RU, UK), the portal
  sections already in `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` brought up to date with the shipped build, the type label of the row
  in each language the sibling sites localize (their own existing labels are the model), and one request file per target in
  `PLAN/S0048_family-hub-and-sibling-links/requests/`. Every text is a shipped fact; none says "soon".
- **S2 Send at the gate - A→O.** At G1 and G2 the owner routes each request to its session (this repository cannot message
  those sessions); the request states the gate, the evidence (the `curl` output) and the check the target must run.
- **S3 Verify from outside - A.** After a target reports its publication: fetch every page the target publishes and look for the
  expected string (section 4 item 3 table); record the result in `temp/S0048/`. A miss is returned to the target's session as a
  finding, not fixed here.
- **S4 Close - A.** The table of targets, locales and results is the evidence; the ticket closes when every row is found or has a
  recorded reason (an owner decision to skip a target is a dated line, not silence).

### 2.4 Non-goals

- No edit of another repository or of the catalog from this repository's tree (`CLAUDE.md` sections 1 and 6).
- No footer or link on a page whose product the row does not serve; no body-copy promotion where rule 4 is not met (T5).
- No new portal page and no new product for StreamsPlayer on the portal unless input D3 asks for it.
- No hub deploy without the owner's confirmation; no hub card without the owner's decision (D1).
- No analytics, `utm_` parameter or tracker on any link.
- No promotion channel work (S0014 P8, P9) - catalogues, AlternativeTo and launch moments are that ticket.

## 3. Constraints and dependencies

### 3.1 Constraints

- The contact on every touched page stays `sza@ukr.net` and GitHub `SerZhyAle`; the label is the `Tool` cell of the map.
- Every target's text appears in each locale that target authors, in one edit (invariant 17); a locale missing in a target is that
  target's session to decide, never padded from here.
- A request that names a page address uses only addresses that answer today.
- The hub page and its embedded twin stay byte-identical; a card is four edits; the hub card order is the hub's.

### 3.2 Related tickets

S0045 (the landing is G1), S0046 (the host), S0047 (G2), S0030 (names, portal sections, deep links), S0014 (P8, P9 channels), S0036
and S0037 (shared contracts the pages follow), and in other repositories: the hub's own tickets for the map and cards, FastMediaSorter
Android's portal process, StreamsPlayer for Windows' site process (cited by repository and id when they are known; no ticket id of
another repository is invented here).

Blocker: S0045
Blocker: S0047

### 3.2.1 Spec pass - 2026-10-29

- **Rung:** complex. The work spans independent sites and languages, creates request files, changes a shared map and has
  unresolved owner decisions.
- **Shared-contract boundary:** yes. The family-map row and consumer registry entries require a catalog amendment, including
  the version and document log, by the responsible catalog session before the row is published; this ticket supplies the request.
- **Current map:** `SITE-FAMILY-MAP` 1.10 section 1 and rule 1 require the grid on landings; child pages and portal pages may
  carry it but are not required to. Existing grids within the requested target scope remain subject to the requested update.
- **Cross-link table research:** `PROMOTION.md` section 4 item 6 identifies `SITE-FAMILY-MAP` as the table; it does not identify
  a separate kit table. The T6 request must reference the map and ask the canon session to identify any additional kit artifact.
- **Dependencies:** S0045 has not settled its D1 name or D2 help-index decision. The landing returned HTTP 404 on this pass;
  S0047's production listing is a separate G2 dependency. The owner must route requests to the target sessions.
- **Owner decisions remain open:** D1 to D5 below, including the shared name and type wording; no recommendation is an answer.
  The target sessions determine their own footer source and report it with their results.
- **Documentation context:** `PRODUCT-WEB-PAGES` and `PORTAL-DOCS` pointers apply. No project document registry was found;
  S0051 tracks the missing registry. This pass changes the ticket only, with no product documentation surface changed.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0045, S0046, S0047, S0030, S0014, S0036 and S0037.
- **D1 decision:** (a) a new card "StreamsPlayer" for Android and Wear OS next to the Windows card - owner, 2026-11-08 (quiz).
- **D2 decision:** the map row reads Tool `StreamsPlayer`, Type `Android and Wear OS stream player`, Help index `help/en/` (the
  help is published with the first release) - owner, 2026-11-08 (quiz). The same answers cover S0045 D1 and D2 and S0014 section 3.3
  item 3; those tickets record them when they are worked.
- **D3 decision:** (a) the three existing portal pages carry a StreamsPlayer section, no new portal page and no section 2a record
  - owner, 2026-11-08 (quiz).
- **D4 decision:** resolved without asking, see the quiz block below: every request file is self-contained and the owner routes it
  either way.
- **D5 decision:** (a) the G2-gated texts are applied only after the Play listing answers 200 - owner, 2026-11-08 (quiz).
- **D1 Hub card.** Options: (a) **a new card "StreamsPlayer" for Android and Wear OS** next to the Windows card (recommended: the
  hub's cards are one link each (rule 2), the platform and audience differ, and the product has its own page), (b) no hub card, the
  map row and the sibling footers only, (c) one card with two platform links (breaks "one link each"). The hub is a curated index:
  adding a product is the owner's decision, not a consequence of the repository existing.
- **D2 Row wording.** Follows S0045 D1 (the name) and the type line; the owner approves the type line once, for the map and all
  eight footers.
- **D3 Portal.** Options: (a) **the three existing portal pages carry a StreamsPlayer section, no new page** (recommended: the
  portal is one corpus, `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` is ready), (b) additionally a dedicated portal page about
  StreamsPlayer (`SITE-FAMILY-MAP` section 2a record, the portal owner's process).
- **D4 Routing.** How the requests reach the other sessions (the owner pastes each request into the target's session, or the owner
  starts that session with the request file as input). This repository cannot message them.
- **D5 Timing of G2-gated text.** Options: (a) **apply the G2 texts only after the Play listing answers** (recommended),
  (b) apply them with the footers at G1 with the get-it wording limited to the landing link.
- Settled and not asked again: the contact, no competitor names, the dedicated family page (S0010 4.7).

### Quiz decisions (2026-11-08)

- D1 Hub card -> a new card "StreamsPlayer" for Android and Wear OS next to the Windows card (the hub's cards are one link
  each, the platform and audience differ, and the product has its own page)
- D2 Tool label (same question as S0045 D1 and S0014 section 3.3 item 3) -> `StreamsPlayer`, the platform told apart by the Type
  column (matches `app_name` and the Play title; `SITE-FAMILY-MAP` 1.9 makes the label the product's own display name; the
  Windows row keeps `STREAMS Player`)
- D2 Type line -> `Android and Wear OS stream player` (the exact pair of `Windows stream player`; the watch app and the watch
  face are part of the product); localized labels for the sibling sites are drafted in S1 on the model of their existing labels
- D2 Help index (same question as S0045 D2) -> publish the help with the first release: the cell reads `help/en/` under the
  landing address (the pattern of the other rows), and the address is permanent (`CONTEXT-HELP` rule 15, accepted)
- D3 Portal -> the three sections of `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` and the device-row correction go onto
  `broadcast-receive`, `broadcast-start` and `watch-exchange`; no new portal page (the portal stays one corpus; the owner's rule
  of 2026-10-14 is that each program writes its sections into the common part)
- D5 Timing of G2-gated text -> only after the Play listing answers 200 (no "on Google Play" wording and no section about the
  screens of an installable app in another repository before that; the footers go at G1)

**Resolved without asking**
- D4 Routing -> the owner passes each request to its target session, either by pasting it or by starting that session with
  the file as input; every request file under `PLAN/S0048_family-hub-and-sibling-links/requests/` is written self-contained (the
  gate, the `curl` evidence, the exact change in the target's languages, the check the target runs, the check run here
  afterwards), so both routes work and the choice is made per target at G1 and G2. Settled by section 2.3 step S2 and by the
  precedent of S0041 and S0057, which keep the exact patch beside the ticket for the session that applies it.
- Records the D2 label answer follows: `DEVICE-EXCHANGE` item CX ("StreamsPlayer, on Windows and on Android, ... keep their
  names") and the owner's names answer of 2026-10-14 (S0030: `app_name` is `StreamsPlayer`, Play hides "for Android"). They
  narrowed the question to the choices offered; they did not answer it.

**Consequences for the work** (written for the implementation pass; the quiz writes no request and sends nothing)
- T0 exact diff: one section 2 row `StreamsPlayer | Android and Wear OS stream player |
  https://serzhyale.github.io/StreamsPlayer_Android/ | https://serzhyale.github.io/StreamsPlayer_Android/help/en/`, additive
  version bump, document-log row, consumer lists. Sent only at G1 (the landing and the help index must answer 200 first).
- T1: the hub card is in scope (four edits, `embed.html`, the hub README line); the hub deploy still waits for the owner's
  confirmation.
- T2 (b) to (d), T3 (b), T4 (b) and (c) and the portal sections wait for G2 (D5 (a)); the footer rows (T2 (a), T3 (a), T4 (a), T5)
  go at G1.
- S0045 P5 (help on the site) is in force as the owner's answer; S0045 itself is not edited by this quiz.

## 4. Acceptance

- [ ] 1. Catalog: the `SITE-FAMILY-MAP` section 2 table lists the row with the URL of the landing and the map's own conformance
  command returns 200 for it; the map's document log has the additive row; the consumer lists name this product.
- [ ] 2. Hub: the `StreamsPlayer` card is present in the inline markup and in the `en`, `ru` and `uk` dictionaries of `index.html`
  and `embed.html`; its link equals the map URL; `diff -q index.html embed.html` exits 0 and the two SHA-256 hashes are stated; the
  hub compliance gate exits 0; the deployed `https://sza.od.ua` contains the card (`curl -s https://sza.od.ua | grep -c
  StreamsPlayer_Android` is the expected count). Only if D1 is (a) or (c).
- [ ] 3. Footer rows: a table in `temp/S0048/` lists, for each of the eight siblings and each locale page that carries the grid, the
  result of `curl -s <page> | grep -c "StreamsPlayer_Android"`; every cell is at least 1; the label equals the map's `Tool`
  cell; the contact on those pages is unchanged.
- [ ] 4. Body-copy links exist only in T2, T3 and T4 and the portal; a grep of the T5 sites for `StreamsPlayer_Android` finds only
  the footer occurrence (rule 4).
- [ ] 5. Portal: `broadcast-receive`, `broadcast-start` and `watch-exchange` answer 200 in EN, RU, UK and each contains the
  StreamsPlayer section; the "devices that play broadcasts" row reads "StreamsPlayer (an Android phone, a Wear OS watch)" and the
  Windows row stays "STREAMS Player"; the app's `docs/MEDIA_EXCHANGE_PORTAL.md` page list still matches the resolver test.
- [ ] 6. Every link added anywhere answers 200 on the day (the map's `for u in ...; do curl ... done` over the new URLs).
- [ ] 7. Tense grep (the pattern of S0045 item 5) over every request text and, from outside, over the added strings: no hit.
- [ ] 8. No competitor product name, no piracy-signal term and no `utm_` parameter in any added text or link.
- [ ] 9. Canon: the contrib record and the kit's cross-link table name this product (read in the canon repository by its session;
  the canon session reports the commit).
- [ ] 10. This repository's tree contains only requests and evidence for this ticket, no copy of another repository's source.
- [ ] 11. A table of targets T0 to T7, with locales, the request date, the publication date and the check result, is complete; a
  skipped target has the owner's dated line.

## 5. Risks

- **A footer link to a 404 on eight sites**: G1 sends nothing before the landing answers.
- **Eight sites, many locale pages**: the FastMediaSorter sites carry a dozen or more locales; a partial update leaves a grid two
  rows short on some pages (additive, nothing wrong on them, `SITE-FAMILY-MAP` section 4) - the table of item 3 shows which.
- **Name collision** with STREAMS Player for Windows in the grid: the type label and the name decision (S0045 D1) separate them.
- **Promotion by footer duplication** in a README or body copy of a sibling that does not need it (rule 4): T5 is footer-only.
- **The hub copies drifting apart** (no generator, no gate for the byte-identical pair beyond the compliance gate): `diff -q` and the
  hashes are stated in the request and checked.
- **The portal is a shared corpus with one publisher**: sections reach readers only through that repository's process; the
  request carries the three languages at once or the portal's gates refuse it.
- **Sessions cannot be messaged from here**: D4; a request not routed is not delivered.

## 6. Open questions

1. D1 to D5 above - answered 2026-11-08, see the quiz block in section 3.3.
2. Resolved in the spec pass: `PROMOTION.md` section 4 item 6 points to `SITE-FAMILY-MAP`; the canon session confirms whether a
   separate kit artifact also needs an update when it receives the T6 request.
3. Whether the sibling sites generate their footer from one source per repository (then one edit per site) or per page; each
   target's session answers in its reply.

## 7. Plan - tactical pass 2026-11-08

Complexity re-read against this plan: still **hard** (eight independently owned sites, the hub, the catalog and the canon,
three locales and more on some sites, a shared map change); the plan agrees with the 2026-10-29 line.

Gate state read on this pass (`curl -sL -o /dev/null -w '%{http_code}'`, 2026-11-08): the landing `/`, `index-ru.html`, `index-uk.html`
(the routes of S0045 acceptance 2) and `help/en/` under `https://serzhyale.github.io/StreamsPlayer_Android/` answer 404 (G1 closed); the Play listing
`https://play.google.com/store/apps/details?id=com.sza.streamsplayer` answers 404 (G2 closed); `https://sza.od.ua` and
`https://serzhyale.github.io/StreamsPlayer/` answer 200.

### 7.1 Coverage inventory

| Line | Step |
| --- | --- |
| S1 "portal sections brought up to date with the shipped build", D3 (a), acceptance 5 (text half) | R1 |
| T0, D2 (row wording), quiz consequence "T0 exact diff", acceptance 1 (request half) | R2 |
| T1, D1 (a), constraint "hub copies byte-identical, a card is four edits", risk "hub copies drifting apart", acceptance 2 (request half) | R3 |
| T7 (conditional on the hub sitemap listing product pages) | R3 (the condition is read and recorded) |
| T2 (a) to (d), T3 (a) (b), T4 (a) to (c), D5 (a), acceptance 3 to 5 (request half) | R4 |
| T5 (five sites, footer only), rule 4, acceptance 4 (request half) | R5 |
| T6, open question 2, acceptance 9 (request half) | R6 |
| S1 "one request file per target", D4 (self-contained files), acceptance 11 (table skeleton), section 6 item 3 | R7 |
| Acceptance 7, 8, 10 (the request texts) | R8 |
| S2 (send at G1 and G2), S3 (verify from outside), S4 (close), acceptance 1 to 6, 9, 11 (outside halves) | R9 to R11 - gated, not this pass |
| Section 2.4 non-goals, section 3.1 constraints, section 5 risks other than the two mapped above | out-of-scope: constraints every step obeys, not steps |

### 7.2 Steps, topology and checks

Pre-implementation blockers: R1 to R8 none (S1 is the "before the gates" step of section 2.3). R9 needs G1 (S0045 P9, the
landing and `help/en/` answer 200) and the owner's routing; R10 needs G2 (S0047, the Play listing answers 200); R11 needs each
target's own publication (G3).

- [x] **R1 Portal sections against the shipped build.** Produces: `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` whose every bold label
  equals a string of the shipped phone or watch resources in the same locale. Consumes: `phone/src/main/res/values*/`,
  `wear/src/main/res/values*/`. Check: the label sweep (every `**label**` of each locale block grepped as `>label<` in that
  locale's resources) reports 0 misses.
- [x] **R2 T0 request - catalog.** Produces: `requests/T0_contracts-catalog.md` with the exact `SITE-FAMILY-MAP` 1.11 diff (the
  row, the version, the document-log row), the consumer lines of `PAGE-CONTENT` and `PAGE-STYLE`, the registry version cells and
  this product's adoption rows. Consumes: the catalog's `SITE-FAMILY-MAP` 1.10, `PAGE-CONTENT` 1.5, `PAGE-STYLE` 3.5,
  `_meta/REGISTRY.md`. Check: the file contains the quiz's T0 row string verbatim.
- [x] **R3 T1 and T7 requests - hub.** Produces: `requests/T1_hub-card.md` (inline card, `en`, `ru`, `uk` dictionary keys,
  `embed.html`, README line, icon, checks) and `requests/T7_hub-sitemap.md` (the condition read and its result). Consumes: the
  hub's `index.html`, `embed.html`, `README.md`, `sitemap.xml`, `CLAUDE.md`. Check: both files exist; T1 carries the four edits
  and `diff -q index.html embed.html`.
- [x] **R4 T2, T3, T4 requests - the siblings with body copy.** Produces: `requests/T2_fastmediasorter-android.md`,
  `requests/T3_fastmediasorter-windows.md`, `requests/T4_streamsplayer-windows.md`, each with the G1 footer part in every
  locale the site publishes and the G2 part. Consumes: R1, each site's footer tile and locale list (read 2026-11-08). Check: the
  three files exist; each names every locale the site publishes; the T2 file names the three portal pages in EN, RU, UK.
- [x] **R5 T5 requests - footer only.** Produces: `requests/T5a_cyrflip.md`, `T5b_doc-html-translate.md`, `T5c_filedo.md`,
  `T5d_oneclickrunner.md`, `T5e_universal-agent-kit.md`, each with the tile in every locale the site publishes and no body copy.
  Consumes: each site's footer tile and locale list. Check: five files exist; none contains a body-copy section.
- [x] **R6 T6 request - canon.** Produces: `requests/T6_canon.md` (the contrib record lines, the `PROMOTION.md` section 4 item 6
  question, the registry consumer rows pointing at T0). Consumes: the canon's `rules/contrib/streamsplayer_mob.md`,
  `rules/PROMOTION.md`. Check: the file exists and names `rules/contrib/streamsplayer_mob.md`.
- [x] **R7 Request index and the target table.** Produces: `requests/README.md` - routing (D4), gates, the per-target table
  (target, session, file, locales, gate, request date, publication date, check result). Consumes: R2 to R6. Check: every file
  the table names exists (`Test-Path`), and the table has rows T0 to T7.
- [x] **R8 Static sweep of the request texts.** Consumes: R2 to R7. Check: the acceptance 7 tense pattern over the quoted
  shipped texts, `utm_` over every file, no file under `requests/` copies another repository's source beyond the quoted lines
  it changes.
- [ ] **R9 Send at G1** (gated): the owner routes T0, T1, T5, T6, T7 and the footer parts of T2 to T4 with the `curl` evidence of
  that day.
- [ ] **R10 Send at G2** (gated): the body-copy and portal parts of T2 to T4.
- [ ] **R11 Verify from outside and close** (gated on G3): the section 4 checks, results in `temp/S0048/`.

No step consumes what a later step produces: R4 consumes R1; R7 and R8 consume R2 to R6. Self-review: every inventory line maps
to a step; R1 to R6 consume only files that exist on 2026-11-08 (the resources, the catalog files, the hub files, the sibling
sites, the canon records named above).

### 7.3 Progress and phase-boundary audit - 2026-11-08 (unattended queue pass)

Static rung only: the change is one documentation file and request texts under `PLAN/`, no code, no build, no test. Between
releases nothing above 5 s runs, and none was needed for these files.

| Check | Command | Expected | Actual | Outcome |
| --- | --- | --- | --- | --- |
| R1 portal labels against the shipped strings | the label sweep: every `**label**` of each locale block of `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` grepped as `>label<` in `phone/src/main/res/values{,-ru,-uk}` and `wear/src/main/res/values{,-ru,-uk}` | 0 misses | 51 labels, 0 misses (before the edit: RU `Справка на телефоне` and UK `Довідка на телефоні` missed; the watch ships `Помощь на телефоне` and `Допомога на телефоні`, `wear_help_on_phone`) | PASS |
| R2 the T0 row verbatim | `grep -cF '<the quiz T0 row>' requests/T0_contracts-catalog.md` | 1 | 1 | PASS |
| R3 T1 four edits and the pair check | `grep -cF` for the four edit headings and `diff -q index.html embed.html` | 1 each | 1 each | PASS |
| R3 T7 condition | read of the hub `sitemap.xml` | the condition recorded | one URL, `https://sza.od.ua/`; nothing owed | PASS |
| R4 locales per site | `grep` for every locale row of T2 (13), T4 (13 `copy/*.txt`) and the T3 ru/uk keys; the three portal page names in T2 | none missing | none missing | PASS |
| R5 T5 footer only | `grep -ciE '^##.*body'` over `T5*.md`; `grep -c 'No body-copy link'` | 0 and 1 per file | 0 and 1 per file | PASS |
| R6 contrib path | `grep -c rules/contrib/streamsplayer_mob.md requests/T6_canon.md` | at least 1 | 2 | PASS |
| R7 every file in the table exists, rows T0 to T7 | file test over the table's file names; `grep -c '^\| T[0-9]'` | 0 missing, 12 rows (T5 is five) | 0 missing, 12 rows | PASS |
| R8 acceptance 7, request texts | the S0045 acceptance 5 tense pattern over `requests/*.md` | exit 1, no hit | exit 1, no hit | PASS |
| R8 acceptance 8, request texts | `grep 'utm_'`; a competitor and piracy-term grep (VLC, TuneIn, IPTV, Kodi, Plex, Spotify, YouTube, Netflix, pirat, crack, warez, torrent) | exit 1 each | exit 1 each | PASS |
| R8 house style | `grep -P '\.\.\.\|\x{2014}\|\x{2013}\|\x{2026}'` over `requests/*.md` | prose hits 0 | 1 hit, `go test ./tests/...` in T5b - a Go package pattern in a command, not prose | PASS |
| G1 | `curl -sL -o /dev/null -w '%{http_code}'` over `/`, `index-ru.html`, `index-uk.html`, `help/en/` of the landing | 200 | 404 x4 | gate closed |
| G2 | the same over the Play listing URL | 200 | 404 | gate closed |

Findings: none at P0 to P3 in this repository. Findings in other repositories are carried inside their requests and listed in
`requests/README.md` (T3 translation keys, T5c test guard, T5d missing tile and old labels, T5e name-only grid); none is fixed
here. Acceptance 8 is checked against an ad hoc term list: the S0014 P2 forbidden-terms list does not exist yet, and the
request texts are re-swept against it at R9. Probe invariant: no code changed, so no `S0048` probe exists (the ticket is not
in `BlockNeedUserTest`). Documentation context: `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` changed (two labels), registered in
`docs/DOCS_SURFACES.md`, whose row is unchanged; the `PRODUCT-WEB-PAGES` and `PORTAL-DOCS` pointers are unchanged; no document
registry exists (S0051).

Stopped at R9: G1 is closed - the landing and its help index answer 404 because S0045 has not published them (S0045 is
`BlockByOtherTask` on S0014). R9 then needs the owner to route the G1 requests; R10 needs G2 (S0047, Draft).
