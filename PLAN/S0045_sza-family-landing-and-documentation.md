# S0045 - StreamsPlayer for Android as a product of the sza.od.ua family: landing, documentation, in-repo links

**Ticket:** S0045
**Status:** BlockByOtherTask
**Status note:** Blocker: S0014 - T4 to T7 need S0014 P1 to P3 (POSITIONING.md, the forbidden-terms list, the listing source), none in the tree on 2026-11-08; S0014 is Draft with owner items 2 to 5 open. T1 to T3 done (S0014 P5 transfer text, docs/DOCS_SURFACES.md, README shipped-fact pass). Later gates: S0046 D4 owner sentence (T7), S0047 Play listing 200 and the owner go (T8).
**Complexity:** hard - confirmed 2026-10-29: new generated pages and artwork, several documentation surfaces and locales, shared publication contracts, and unresolved owner decisions.
**Date:** 2026-10-26
**Phase:** package 1 - the documentation half of the first public appearance; the half that lives in the other repositories is S0048
**Verification level:** the landing and its locale pages answer 200 on the published host and pass the site build check; every
surface of the ship-together manifest says the same one sentence in EN, RU and UK; the static predicates of section 4, each
with its command.
**Owner:** the StreamsPlayer Android session of this repository.

## Remaining development

*As of 2026-11-08, checked against the tree. Built: the S0014 P5 transfer text (T1), the ship-together manifest
`docs/DOCS_SURFACES.md` (T2), the README shipped-fact pass with its Related and Contact sections (T3); evidence in
section 7.3.*

1. **T4 to T6 - the one sentence, the landing generator, heads, social card, help on the site.** Wait for S0014 P1 to P3
   (`POSITIONING.md`, the forbidden-terms list, the listing source); T6 writes into `site/` only in the T8 commit.
2. **T7 - the README sentence and the AI-assistance line.** Wait for T4 and the owner's S0046 D4 sentence.
3. **T8 - publication, stamp, pointer.** Waits for S0047 (the Play listing answers 200) and the owner's go naming P9.

## Captured request and migration scope

Owner, 2026-10-26, "create specifications", item 0 of four, verbatim: "Document this project as a part of sza.od.ua and
links on all related products and portals".

The four items became four tickets: S0045 (this one: the product's own documentation as a member of the family), S0046
(item 1, GitHub), S0047 (item 2, Google Play) and S0048 (item 3, "update sza.od.ua and add this application into all other
products related with links and articles in their documentation").

The split between S0045 and S0048 is by repository, not by topic. S0045 writes in this repository. S0048 asks for writes in
the others and checks them from outside; another repository's source is never edited from here (`CLAUDE.md` sections 1 and 6).

## 0. Source

- `SITE-FAMILY-MAP` 1.9 (section 2 table, rules 1 to 5), `PAGE-CONTENT` 1.5 ("Mandatory page order for apps", "The landing
  always carries"), `PAGE-STYLE` 3.4 (per-language secondary pages, the byte-identical reference kit), `PRODUCT-WEB-PAGES`
  pointer of this repository (2026-10-25, S0033), `PORTAL-DOCS` pointer (2026-10-25, S0030), `CONTEXT-HELP` rules 11 and 15.
  Cited by id and section; the catalog is not restated.
- Owner decision 4.7 of S0010 (2026-10-10): "Join SITE-FAMILY-MAP with a dedicated page and hosted privacy policy" - stands.
- Owner decision В39 (2026-10-14, S0010): the first release goes straight to Google Play - the landing's get-it block is built
  around that fact.
- S0033: the hosted privacy pages and their generator `tools/site/build-site.ps1` exist; the landing extends that generator.
- S0014: positioning, the forbidden-terms list and the listing source (P1 to P3) are the content this landing renders; its
  P5 ("Pages and indexing") is carried out here (section 2, item P6).

## 1. Goal

A stranger who arrives from sza.od.ua, from the footer of another product, from the media exchange portal, from GitHub or from
the store finds one landing, in English, Russian and Ukrainian, in the family's page shape: what StreamsPlayer is, who it is
for, where to get it, where the help is. Every surface of this repository - the app strings, the help, the README, the site,
the listing source - says the same one sentence about the product, and the repository links the family where it genuinely
covers what this product does not.

### Baseline - read 2026-10-26

| What | Read how | Value |
| --- | --- | --- |
| Product landing | `ls site` | none: `privacy.html`, `privacy-ru.html`, `privacy-uk.html`, `sitemap.xml`, `robots.txt`, `assets/` |
| Pages host | `curl -L` on `https://serzhyale.github.io/StreamsPlayer_Android/` | 404 (no repository, S0046) |
| Pages workflow | `.github/workflows/pages.yml` | publishes `site/` on a push to `main` that touches `site/**` |
| Site generator | `tools/site/build-site.ps1` | renders only the three privacy pages, `sitemap.xml`, `robots.txt`; its `-Check` fails on drift and on any request outside the host |
| Canon stamp | `.sza-canon.json` | `site.kind` product, `site.pages` the three privacy pages; the stamp's own comment says the landing joins `site.pages` when it exists |
| Family map | `SITE-FAMILY-MAP` 1.9 section 2 | no row for this product; the Windows product is the row `STREAMS Player` |
| Hub cards | hub `index.html`, the `STREAMS Player` card | the Windows product only; no Android card |
| README | `README.md` | English only; names the exchange work as pending and cites ticket ids (for example "remain pending the joint run recorded by S0020 and S0034", "remain tracked in S0028, S0032 and S0022") and links `docs/BOOTSTRAP_HANDOFF.md` - prose that is wrong on a public front page |
| In-app help | `phone/src/main/assets/help/{en,ru,uk}/`, `help-map.json` | bundled, generated from `docs/help/content/`; not published on a site |
| Application icon | `phone/src/main/res/mipmap-anydpi/` | present; the watch and watch face have their own |
| Contact stated on any surface | grep of `README.md`, `site/` | `sza@ukr.net` on the privacy pages (S0033); none in the README |

## 2. Scope

Executor tags as in S0014: **A** agent alone, **A·GO** agent after the owner's go naming the step, **A→O** agent drives and the
owner acts at one point, **O** owner only.

- **P1 Source facts and the one sentence - A.** Extract the facts `PAGE-CONTENT` "Required source facts" lists: name, platform
  and packages (phone, Wear OS app, watch face), the variants, the real channels, one job to be done, the audience, three to
  five scenarios, the quick-start steps, the limits, permissions and data risks, the screenshots, the documentation URLs and
  one genuinely relevant sibling. Write the one task-first sentence of the `feature-to-site` skill, step 2, in EN, RU and UK,
  as a shipped fact. S0014 P1 (`POSITIONING.md`) and P2 (forbidden terms) come first; this ticket does not write them.
- **P2 Landing - A.** `site/index.html`, `site/index-ru.html`, `site/index-uk.html`, **generated** by extending
  `tools/site/build-site.ps1` (never hand-edited), in the page order of `PAGE-CONTENT` for apps and with its seven "landing
  always carries" items: identity block with the product's own application icon and the full name of the `Tool` cell, the
  RU EN UA switch, the get-it block `#get`, the help and documentation links, the footer grid and the contact. Pages make no
  request outside their own host (the S0033 rule, enforced by the generator's `-Check`).
- **P3 Get-it block - A.** One link per real channel, only channels that answer: the Play listing by its package id
  (`https://play.google.com/store/apps/details?id=com.sza.streamsplayer`, a durable address, no `utm_` parameter) once S0047
  lands; GitHub Releases through `/releases/latest` once S0046 part B opens; the source repository as **Source code**. No
  "soon" button, no badge for a channel the product is not on, no hard-coded version.
- **P4 README - A.** Rewritten as shipped facts: no ticket id, no "pending", "planned", "remain" or "not yet", no link to
  `docs/BOOTSTRAP_HANDOFF.md` (it stays in the tree for the owner and the sessions; S0046 decides what is public). Adds the
  sentence of P1, the packages, the build and test commands, the licence, the contact `sza@ukr.net`, the AI-assistance line
  (S0014 decision 6; text per owner input D4 of S0046) and a short **Related** section limited to `SITE-FAMILY-MAP` rule 4:
  StreamsPlayer for Windows (the same stream bank and playlist formats on the desktop), Fast Media Sorter & Organizer and Fast
  Media Sorter & Sharing for Windows (accounts, the exchange server and the broadcasts this app plays), the media exchange
  portal and the hub link. English only: the landing carries the translations, which the canon permits
  (`feature-to-site` step 5).
- **P5 Help on the site - A, input D2.** If owner input D2 says publish: the help pages generated from `docs/help/content/`
  also render to `site/help/{en,ru,uk}/`, from the same generator that writes the bundled copy (never a second hand copy), and
  the index address `help/en/` goes to the family map's *Help index* column through S0048. Gate: the help-map gate
  `tools/help/assert-help-map.ps1` is green. Reconciled by S0051 (2026-11-08) with `DOC-EXTERNAL-QUALITY` section 8 item O and
  `CONTEXT-HELP` section 7 item G: each published help page carries `<meta name="robots" content="noindex">`, is not listed in
  `sitemap.xml`, owes no SEO block and is not blocked by `robots.txt`; `tools/site/build-site.ps1 -Check` fails anything else from the
  day `site/help/` exists. T6 adds the tag, a record of `docs/DOCUMENT_REGISTRY.jsonl` for those pages and keeps them out of the
  generated sitemap.
- **P6 Heads, structured data and indexing - A.** (This is S0014 P5.) Every page: `title`, `description`, `canonical`, Open
  Graph with width, height and alt, `hreflang` for en, ru, uk (the switch key `ua` is the page-style key, the language code
  is `uk`), one `h1`, one truthful `MobileApplication` JSON-LD block (no `aggregateRating`, `review`, `FAQPage`, `HowTo`, no
  retyped `softwareVersion`). `sitemap.xml` generated with `lastmod` from commit dates, listing every page except the published help pages (P5). One social-card
  artwork at 1200x630 for Open Graph (the 1280x640 repository preview of S0046 is cut from the same artwork). The host-root
  Search Console property, `robots.txt` and the Bing and Yandex steps belong to the hub's ticket (S0014 section 0) and are not
  planned here.
- **P7 Pointers and stamp - A.** `docs/contracts/PRODUCT-WEB-PAGES.md` pointer updated from "landing is S0014" to the adopted
  landing and its reviewed versions; `.sza-canon.json` `site.pages` gains the landing pages in the commit that publishes them;
  a registry row for this product is a request to the canon session (S0048 item T0), never an edit of the catalog.
- **P8 Ship-together manifest - A.** Print the surface table of `feature-to-site` step 10 and persist it as
  `docs/DOCS_SURFACES.md` (or extend the repository's existing surface list instead, if one is found at execution): in-app
  strings (`values`, `values-ru`, `values-uk` of phone, wear, watchface), help pages and `help-map.json`, README, `site/`
  (landing, privacy, help), `docs/PRIVACY_POLICY*.md`, `docs/play/` listing and Data safety sources,
  `docs/PORTAL_SECTIONS_STREAMSPLAYER.md`, each with its locales and its one source of truth; no unexplained N/A.
- **P9 Publication moment - A·GO.** Landing files enter `site/` only in the commit that follows the first channel answering
  200 (input D3), because `pages.yml` deploys on any push that touches `site/**` and `SITE-FAMILY-MAP` rule 5 admits a URL only
  once it answers. Until then Pages carries the privacy pages alone.

### 2.1 Non-goals

- No edit of any other repository, of the hub, or of the contracts catalog (S0048).
- No analytics, tracker, cookie banner, web font or script from another host; no `utm_` parameter; no rating markup.
- No claim of a feature the shipped build lacks, no claim true for one variant and false for the other, no competitor name, no
  `iptv` or "free TV" word (S0014 decision 3), and no teaser or roadmap line for what is not shipped.
- No landing for the `noLegal` sideload variant before the `github` channel opens (S0046 part B).

## 3. Constraints and dependencies

### 3.1 Constraints

- Every published sentence is a present-tense fact of the shipped build, in EN, RU and UK in the same edit
  (invariant 17); the owner is told the plain truth in chat, including what is not built or not proven.
- The contact everywhere is `sza@ukr.net` and GitHub `SerZhyAle` (`SITE-FAMILY-MAP` rule 3).
- The footer grid carries every sibling row of the map except this product's own (rule 1). A row for this product is added to
  the map by the catalog's owner (S0048), not here.
- `site/` is a render target: regenerate, never hand-edit.

### 3.2 Related tickets

S0014 (content, and P5 carried here), S0033 (privacy pages and generator), S0035 (help), S0030 (names, portal sections),
S0036 (UI contracts the screenshots depend on), S0046 (host and repository), S0047 (the first channel), S0048 (the other
repositories), S0010 (decision 4.7).

Blocker: S0014

### 3.2.1 Spec pass - 2026-10-29

- **Rung:** complex. The scope creates pages and artwork, changes more than three files and has unresolved owner decisions.
- **Shared-contract boundary:** yes. The landing and any public help addresses implement `PAGE-CONTENT`, `PAGE-STYLE`,
  `SITE-FAMILY-MAP` and `CONTEXT-HELP`; permanent help URLs are externally consumed. A new family-map row and consumer
  registry amendments are owned by the catalog session and carried by S0048 T0; this ticket does not edit the catalog.
  No cross-product wire format or playback algorithm change is ordered here. Confirm the applicable catalog versions and
  amendment before implementing a changed shared contract or publishing a new map row.
- **Source dependencies:** S0014 P1 to P3 provide positioning, forbidden terms and listing content. `POSITIONING.md` and a
  forbidden-terms file were not found in the repository on this pass; S0014 is Draft. The public README also needs the
  owner's AI-assistance wording, which S0046 section 3.3 calls D4 (this ticket's P4 calls it D5; confirm the reference).
- **Owner decisions still open:** D1 to D4 and confirmation that S0014 P5 moves here. S0014 explicitly leaves its shared
  brand-spelling question open; its 2026-10-09 approval of recommendations does not settle that question. The dedicated
  page and hosted policy, Play first, contact and exclusion of competitor names are already recorded decisions.
- **Documentation context:** `PRODUCT-WEB-PAGES` and `PORTAL-DOCS` pointers are relevant. The document registry is absent
  under S0051, so no registry records can be consulted yet. This spec pass changes no product documentation surface.

### 3.3 Owner inputs (Approval gate)

Each asked once, in question-with-options form, right before the phase that needs it.

- **Related tickets:** S0010, S0014, S0030, S0033, S0035, S0036, S0046, S0047, S0048, S0051 (section 3.2; S0051 takes
  the D2 answer).

- **D1 Name spelling on the family surfaces.** ANSWERED 2026-11-08, option (a) "StreamsPlayer". The `Tool` cell of the map and the landing's identity block need the name a
  user meets in the product. `app_name` is "StreamsPlayer" on phone and watch (S0030: no "mobile", Play hides "for Android");
  the Windows row is "STREAMS Player" (owner's English identity there). Options: (a) **"StreamsPlayer"** with the type
  "Android and Wear OS stream player" (recommended: it is the name on the device and in the store; the type and the eyebrow
  carry the platform), (b) "STREAMS Player" for both, (c) another form. Same question as S0014 3.3 item 3; one answer serves both.
- **D2 Publish the help on the site.** ANSWERED 2026-11-08, option (a). Options: (a) **publish `help/{en,ru,uk}/` with the first release** (recommended: the
  pages exist in three languages, the ids become permanent when the first build ships anyway, and other products can then link
  them), (b) keep the help in the package only, the map's *Help index* cell stays `-`.
- **D3 When the landing goes public.** ANSWERED 2026-11-08, option (a). Options: (a) **in the commit after the Play listing answers 200** (recommended: nothing
  to put in the get-it block before, and no placeholder is allowed), (b) with the first GitHub push, the get-it block showing
  the source repository alone.
- **D4 Artwork.** ANSWERED 2026-11-08, option (a). The identity block uses the colour application icon unaltered; the 1200x630 social card needs one artwork.
  Options: (a) **render it from the application icon on the family's plain background** (recommended), (b) the owner supplies it.
- Settled and not asked again: the dedicated family page and hosted policy (S0010 4.7), Play first (В39), the contact address
  (S0014 decision 8), no competitor names (S0014 decision 3).

### Quiz decisions (2026-11-08)

- D1 name spelling on the family surfaces -> "StreamsPlayer" with the type "Android and Wear OS stream player"
  (option (a), the recommendation; the name on the device and in the store, `app_name`, S0030; the type and the eyebrow
  carry the platform). One answer also serves S0014 section 3.3 item 3 and feeds S0048 D2; the wording of the type line
  stays S0048 D2, which the owner approves once there.
- D2 publish the help on the site -> publish `help/{en,ru,uk}/` with the first release (option (a); the pages exist in
  three languages, the ids become permanent with the first build anyway, and other products can then link them). P5
  stays in scope; the addresses are permanent (`CONTEXT-HELP` rule 15). The open question of S0051 and the *Help index*
  cell of S0048 take this answer.
- D3 when the landing goes public -> in the commit after the Play listing answers 200 (option (a); nothing to put in
  the get-it block before, and no placeholder is allowed). Until then Pages carries the privacy pages alone; P9 stays
  A·GO.
- D4 artwork -> the social card is rendered from the application icon on the family's plain background (option (a));
  the 1280x640 repository preview of S0046 is cut from the same artwork. Nothing is expected from the owner.
- S0014 P5 transfer (section 6 item 2) -> confirmed: S0014 P5 is carried out here as P6, and S0014's text is amended
  at the first execution step of this ticket, not before.
- The reference of the AI-assistance wording -> S0046 section 3.3 D4, not D5. Resolved without asking: S0046 section
  3.3 holds D1 to D5 and the README sentence is its D4 (its D5 is the scope of part B). P4 is corrected.
- Still outstanding, not owner inputs of this ticket: the content from S0014 P1 to P3 (positioning, forbidden terms,
  listing source - reported absent at the spec pass of 2026-10-29; S0014 is Draft and is this ticket's blocker), and
  the owner's approval of the one README sentence proposed under S0046 D4.

## 4. Acceptance

Binds when the landing is published (P9) unless a line says otherwise.

- [ ] 1. `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` exits 0: the landing, privacy pages, sitemap and robots equal
  their sources, and no page loads a script, font, image or stylesheet from another host.
- [ ] 2. `curl -s -o /dev/null -w "%{http_code}" -L` returns 200 for `/`, `/index-ru.html`, `/index-uk.html`, the three privacy
  pages, `sitemap.xml`, `robots.txt` and, if D2 is (a), `help/en/`, `help/ru/`, `help/uk/`.
- [ ] 3. Each page has exactly one `h1`, one `canonical`, the three `hreflang` alternates, Open Graph with width, height and alt,
  and one `MobileApplication` JSON-LD block that contains none of `aggregateRating`, `review`, `FAQPage`, `HowTo`.
- [ ] 4. The footer grid on every landing and privacy page lists every row of the `SITE-FAMILY-MAP` section 2 table except this
  product's own, with the labels of the table; the map's own conformance command returns 200 for each URL.
- [ ] 5. Tense and promise grep over `README.md`, `site/` sources and generator copy, `docs/play/`, `docs/help/content/`: the
  `feature-to-site` pattern (`soon|planned|upcoming|not yet|in development|under construction|will be added|experimental|beta|
  not fully tested|скоро|планируется|в разработке|ещё не|экспериментальн|бета|незабаром|планується|у розробці|експериментальн`)
  returns no hit that is not an app state string; `README.md` additionally holds no `S\d{4}` ticket id and no "pending".
  Binds now for the README.
- [ ] 6. No competitor product or brand name and no piracy-signal term (S0014 P2 list) in any published source; every contact
  stated is `sza@ukr.net`. Binds from the first text written.
- [ ] 7. The get-it block links only channels that answer 200 today; the Play link is the package-id URL; no `/download/vX.Y`
  version in any link.
- [ ] 8. `docs/DOCS_SURFACES.md` exists with every surface, its locales and its source, and no unexplained N/A.
- [ ] 9. The feature noun and the one sentence of P1 are the same in the app string, the README, the landing and the listing
  source: grep the noun across the touched files and read the hits.
- [ ] 10. `.sza-canon.json` lists the landing pages in `site.pages` and the canon compliance check exits 0.
- [ ] 11. The Related section of the README and the body-copy links of the landing name only the siblings of `SITE-FAMILY-MAP`
  rule 4; no sibling is promoted that does not cover what this product lacks.

## 5. Risks

- **A half-finished landing goes public the minute it is pushed**, because the Pages workflow deploys on any `site/**` change.
  Mitigation: P9 and input D3; the landing is committed complete or not at all.
- **Name collision with StreamsPlayer for Windows** sends readers to the wrong product (S0014 decision 5): the eyebrow, the
  title and the schema block name the platform.
- **Two variants**: a sentence true of the store build and false of `noLegal`, or the reverse.
- **README rewrite drops what the owner uses**: the ticket references leave the README, never `PLAN/`.
- **Permanent addresses**: a published help address cannot change (`CONTEXT-HELP` rule 15); input D2 (a) accepts that on purpose.

## 6. Open questions

1. D1 to D4 above - answered 2026-11-08.
2. S0014 P5 moves here: confirmed by the owner on 2026-11-08, and S0014's text is amended at the first execution step of this ticket (not edited
   by the filing of this ticket).

## 7. Plan - tactical pass 2026-11-08

Complexity re-read against this plan: still **hard** (generated pages and artwork, several surfaces and locales, shared
publication contracts); the plan agrees with the 2026-10-29 line.

### 7.1 Coverage inventory

| Line | Step |
| --- | --- |
| Section 6 item 2, quiz decision "S0014 P5 transfer" | T1 |
| P8, acceptance 8 | T2 |
| P4 shipped-fact cleanup, Related section, contact; acceptance 5 (binds now for the README), 6, 11 (README half) | T3 |
| P1, acceptance 9 (the sentence) | T4 |
| P2, P3, P6, acceptance 1, 3, 4, 7, 11 (landing half) | T5 |
| P5 (D2 a) | T6 |
| P4 remainder (the one sentence, the AI-assistance line of S0046 D4), acceptance 9 | T7 |
| P7, P9 (D3 a), acceptance 2, 10 | T8 |
| Section 2.1 non-goals, section 5 risks | out-of-scope: constraints every step obeys, not steps |
| Risk "README rewrite drops what the owner uses" | T3 removes ticket references from the README only; `PLAN/` and `docs/BOOTSTRAP_HANDOFF.md` stay |

### 7.2 Steps, topology and checks

Pre-implementation blockers: T1 to T3 none. T4 to T7: S0014 P1 to P3 (`POSITIONING.md`, the forbidden-terms list, the listing
source) - absent on 2026-11-08, S0014 is Draft. T7 also: the owner's approval of the S0046 D4 sentence. T8: S0047 (the Play
listing answers 200) and the owner's go naming P9.

- [x] **T1 S0014 text amended.** Produces: the P5 line of `PLAN/S0014_free-promotion-campaign.md` naming this ticket. Consumes:
  nothing. Check: `Select-String -Path PLAN/S0014_free-promotion-campaign.md -Pattern 'carried out by S0045'` returns 1 line.
- [x] **T2 Ship-together manifest.** Produces: `docs/DOCS_SURFACES.md` and its `docs/README.md` index row. Consumes: the
  surfaces on disk (no surface list existed). Check: the file exists; every row has locales, a source and a state; every
  N/A carries its reason; `docs/README.md` names it.
- [x] **T3 README shipped-fact pass.** Produces: `README.md` without ticket ids, "pending", "remain" or the handoff link, with
  a Related section limited to `SITE-FAMILY-MAP` rule 4 and the contact of its rule 3. Consumes: `SITE-FAMILY-MAP` 1.10
  sections 2, 2a and 3 (catalog). Check: `Select-String README.md -Pattern 'S\d{4}|pending|remain|BOOTSTRAP_HANDOFF'` returns
  nothing; the acceptance 5 tense pattern returns nothing; `sza@ukr.net` is present; every Related URL answers 200.
- [ ] **T4 Source facts and the one sentence (P1).** Consumes: S0014 P1 to P3. Produces: the landing copy deck (facts, the
  sentence in EN, RU and UK) as the generator's source. Check: the copy deck exists and the forbidden-terms grep over it
  returns nothing.
- [ ] **T5 Landing generator, heads, social card (P2, P3, P6).** Consumes: T4. Produces: `tools/site/build-site.ps1` rendering
  the landing pages, the get-it block, the heads, the JSON-LD and the sitemap; the 1200x630 card from the application icon
  (D4 a). Check: `build-site.ps1 -Check` exits 0 on a scratch output; the acceptance 3 and 4 greps pass.
- [ ] **T6 Help on the site (P5).** Consumes: `docs/help/content/`, the help generator. Produces: `site/help/{en,ru,uk}/` from the
  same generator, written in the T8 commit. Check: `tools/help/assert-help-map.ps1` prints PASS.
- [ ] **T7 README sentence and AI line (P4 remainder).** Consumes: T4, S0046 D4. Check: acceptance 9 grep.
- [ ] **T8 Publication (P7, P9).** Consumes: T5, T6, S0047. Produces: the landing in `site/`, `.sza-canon.json` `site.pages`, the
  `PRODUCT-WEB-PAGES` pointer. Check: acceptance 1, 2, 10.

No step consumes what a later step produces. Self-review: every inventory line maps to a step above; T1 to T3 consume only
files that exist on 2026-11-08 (`PLAN/S0014_free-promotion-campaign.md`, `README.md`, `docs/README.md`, the catalog's
`SITE-FAMILY-MAP`).

### 7.3 Progress and phase-boundary audit - 2026-11-08 (unattended queue pass)

Static rung only: the change is documentation and ticket text, no code, no build, no test. Between releases nothing above
5 s runs, and none was needed for these files.

| Check | Command | Expected | Actual | Outcome |
| --- | --- | --- | --- | --- |
| T1 S0014 names this ticket on its P5 | `(Select-String -Path PLAN/S0014_free-promotion-campaign.md -Pattern 'carried out by S0045').Count` | 1 | 1 | PASS |
| T2 every path the manifest cites exists | `Test-Path` over the 31 cited paths | 0 missing | 0 missing | PASS |
| T2 index row | `Select-String docs/README.md -Pattern 'DOCS_SURFACES.md'` | 1 line | 1 line | PASS |
| T2 no unexplained N/A | read of the table: every row has a state; no N/A cell (the two `n/a` grep hits are inside `main/assets`) | none | none | PASS |
| T3 acceptance 5, README half | `grep -nE 'S[0-9]{4}\|pending\|remain\|BOOTSTRAP_HANDOFF' README.md` | exit 1, no hit | exit 1, no hit | PASS |
| T3 tense pattern of acceptance 5 | `grep -niE '<the acceptance 5 pattern>' README.md` | exit 1, no hit | exit 1, no hit | PASS |
| T3 acceptance 6, contact | every address in `README.md` | only `sza@ukr.net` | only `sza@ukr.net` (line 160) | PASS |
| T3 acceptance 11, README half | Related lists only the rule 4 siblings, the portal and the hub; `curl -s -o /dev/null -w '%{http_code}' -L` on each URL | 200 x5 | 200 x5 | PASS |

Findings: none at P0 to P3. Probe invariant: this pass added no code, so no `S0045` probe exists (the ticket is not in
`BlockNeedUserTest`). Documentation context: `docs/README.md` gained the `DOCS_SURFACES.md` row; the `PRODUCT-WEB-PAGES` and
`PORTAL-DOCS` pointers are unchanged (the landing does not exist yet); no document registry exists (S0051).

Stopped at T4: S0014 P1 to P3 are absent (`POSITIONING.md`, a forbidden-terms list and the listing source - none in the tree
on 2026-11-08; S0014 is Draft with its owner items 2 to 5 open, item 2 the market category the positioning needs). The
ticket waits on S0014 (`BlockByOtherTask`); T7 also waits on the owner's S0046 D4 sentence and T8 on S0047.
