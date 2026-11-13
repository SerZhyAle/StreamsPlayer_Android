# S0033 - Before the first store release: privacy policy on the site, permissions list, Play Data safety

**Status:** BlockNeedUserTest
**Status note:** Development complete (policy EN/RU/UK, generated permissions table, hosted site/ pages, Data safety source, play channel; the About privacy button resolves the hosted page by app language). Only testing remains: the owner pushes site/ and enables Pages, the three URLs answer 200 and equal their sources, the Data safety form is answered; the About link is checked on a phone once S0049 makes the About page reachable.
**Phase:** first store release (owner decision В39 of 2026-10-14, recorded in S0010) - package 1
**Verification level:** the policy page published and reachable; the Data safety answers read against the policy and the merged manifests; the canon compliance gate green with the `play` channel declared.
**Complexity:** hard - assessed at the spec pass: store privacy spans app, site and Play, and the first-package scope requires an owner decision.
**Depends on:** S0049

## 0. Source

- Owner, 2026-10-14 (FastMediaSorter Android (FMS_A) milestone S4203, decision В39): this product's first
  release goes straight to Google Play (S0010 section "S4203 decisions 2026-10-14").
- Owner, 2026-10-14, same milestone: the exchange server, Google Drive and end-to-end encryption ship in release
  46; the encryption hides content from the server host while metadata (account, device, broadcast names,
  presence) stays visible to the server - say so honestly. The exchange server is not open to strangers: no terms
  of use and no complaint button. This product never registers accounts.
- S0010 decision 4.7 (2026-10-10): join `SITE-FAMILY-MAP` with a dedicated page and a hosted privacy policy.
- FMS_A S4241 (privacy policy, Data safety and declarations for FastMediaSorter Android) - the same flows answered
  the same way across the family. StreamsPlayer (Windows): StreamsPlayer SP-0227.
- `.sza-canon.json` (read 2026-10-14): `channels` `["github"]`, `site.kind` `none`, `privacy.page`
  `docs/PRIVACY_POLICY.md`, `sensitiveAccess` true; its comment says a declared store channel makes the
  compliance gate SZA-SURF01 require a hosted privacy page.
- Facts in this tree, 2026-10-14: `docs/PRIVACY_POLICY.md` (updated 2026-10-11) lists seven permissions and says
  the exchange server sees a published broadcast's content; it says nothing of Google Drive, of encryption or of
  the watch's own account; no Data safety answers exist; no site exists.

## 1. Goal

On the day the first package reaches Google Play, the policy a store reader opens, the permissions the app asks
for and the Data safety form all say the same thing, and each says exactly what the published build does - the
exchange server, Google Drive and encryption included where the build carries them.

## 2. Scope

1. **Policy page on the site.** `docs/PRIVACY_POLICY.md` rewritten as the source of the hosted page on the
   product's `SITE-FAMILY-MAP` page (S0010 decision 4.7), EN with RU and UK, `PRODUCT-WEB-PAGES` bound for that
   page; the contact address of S0014 decision 8.
2. **Permissions list.** Generated from the merged manifests of the `store` release of `phone`, `wear` and
   `watchface` (not typed), each with its purpose in the user's words; a check fails when the manifest and the
   list differ.
3. **Exchange server section.** What the app sends to the server the user names (login, the device's name and
   id, broadcast records, the published stream), what the server keeps, that the server is the user's own or
   their host's, that the password never leaves the sign-in and the token stays in the Keystore.
4. **Google Drive section.** The app-folder scope only, what an announcement file holds, own account only
   (S0029).
5. **Encryption section.** What the server cannot see (content) and what it does see (account, device,
   broadcast names, presence) (S0032).
6. **Data safety.** The form's answers written into the repository as the listing source, flow by flow, with
   the same reasoning FMS_A S4241 uses for the same flows (data sent to a server the user chooses, Google Drive in
   the user's own account, encryption in transit and end to end, no account creation in this app); kept beside
   the S0014 listing source.
7. **Account deletion.** This app creates no account, so Play's account-deletion requirement for apps that
   create accounts is answered as not applicable; the policy points to the portal page "how to delete an
   account: in the program or ask the server host" (S0030 deep links).
8. **Channel declaration.** `.sza-canon.json` `channels` becomes `["play"]` and `site` names the page, in the
   same change as the hosted policy, and the canon compliance gate runs green.
9. **Later parts.** A part the first store build does not carry (the owner decides which package ships what) is
   left out of the page and the form, and the ticket that ships it adds it in the same edit (canon invariant 14) -
   S0029 and S0032 carry that in their definitions of done.

Not in scope: the store listing texts and screenshots (S0014), terms of use (the owner: none), any tracker or
analytics (none exist).

## 3. Constraints and dependencies

### 3.1 Constraints

- Every sentence is true of the published build; nothing announces a feature the build lacks (the owner: no
  "not yet available" labels anywhere).
- The policy, the permission list and the Data safety answers are written together and change together.
- The site page follows `SITE-FAMILY-MAP` and the family hub's process; this repository never edits another
  product's site.

### 3.2 Related tickets

S0010 (the decision to ship to Play first), S0014 (listing source, contact address, P7 Play listing), S0021,
S0022, S0029, S0032 (the flows described), S0030 (names and deep links), S0034 (contract rows).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0010, S0014, S0021, S0022, S0029, S0030, S0032, S0034.
- **First release channel:** answered 2026-10-14 - Google Play first (В39).
- **Site:** answered 2026-10-10 (S0010 decision 4.7) - a dedicated page in `SITE-FAMILY-MAP` with the hosted
  policy.
- **Package:** this ticket stands in package 1 because package 1 is the next release and the first store
  release; the exchange tickets stand in `--`. If package 1 ships before Drive (S0029) and encryption (S0032),
  item 9 applies (recommended), or this ticket moves to the package that carries them - the owner's call.
  **Answered 2026-11-08, option (a)** - resolved by research, not asked; see "Quiz decisions (2026-11-08)" below.
  Original question: choose (a) apply item 9 and omit features absent from the first build from the policy and form
  (recommended), or (b) move this ticket to the package carrying those features. The release operator still
  reconciles the policy and form against the actual uploaded build in either case.

### Quiz decisions (2026-11-08)

- If package 1 precedes Drive (S0029) and encryption (S0032): apply item 9 or move S0033? -> (a) apply item 9; S0033
  stays in package 1. Resolved without asking: the owner decision В39 (the first release goes straight to Google
  Play, S0010) makes the hosted policy and the Data safety source a precondition of that release - S0047 acceptance 4
  and S0046 part A both read this ticket's pages - so option (b) would leave the first Play upload without a policy
  URL. Item 9 is also the rule S0029 and S0032 already carry in their definitions of done and S0047 states in its
  non-goals (listing and form carry Drive and encryption only if the uploaded build carries them).
- Which parts the first store build carries is not decided here: it is the owner's package-1 call (S0047 D7). The
  policy as written describes the `store` build as the tree builds it today (section 5, decision 1); the release
  operator reconciles policy, permissions table and form against the uploaded build in one edit (item 9, S0047
  acceptance 4).
- Facts for that reconcile, read at this sitting, not decisions: the certificate table of
  `docs/DRIVE_OAUTH_CLIENT.md` holds no registered upload or Play app-signing certificate, and S0032 is
  `BlockByOtherTask` with its open items 2 and 3 (an unsealed relay or tunnel endpoint is still played; opened keys
  are kept in clear preferences) while the policy carries the encryption text of `DEVICE-EXCHANGE` item DE.

## 4. Definition of done

- The hosted policy page answers 200 at its URL in EN, RU and UK, and its text equals the repository source.
- The permission list check exits 0 against the merged `store` release manifests of the three modules.
- The Data safety answers are in the repository and read against the policy, flow by flow, with the result
  recorded in `temp/S0033/`.
- `.sza-canon.json` declares `play` and the canon compliance check exits 0.

## 5. Implementation record (2026-10-25)

### What exists

- **Policy** - `docs/PRIVACY_POLICY.md` rewritten, with `-ru` and `-uk` editions of the same 15 sections: what the app does
  not do, what stays on the device, every flow that leaves it, permissions, the exchange server (what is sent, TLS and
  pinning, off by default, guests, other people's broadcasts, what the server keeps), encryption (the family's honest
  text of `DEVICE-EXCHANGE` item DE verbatim in all three languages, then what it means here), Google Drive (the old
  section 4, now 7, with `via` instead of `through` to match the interface and a Limited Use paragraph), the watch,
  accounts and deletion, components from others, children, the website, open source, changes, contact `sza@ukr.net`.
- **Permissions table** - generated, not typed: `tools/privacy/permissions.ps1` reads the merged manifests of
  `phone` and `wear` (store release) and `watchface` (release), joins the sentences of
  `docs/privacy/permission-purposes.json` (EN/RU/UK) and writes the table between the markers of the three policies.
  `-Mode Check` (default) exits 1 when a manifest permission has no sentence, a sentence has no manifest permission or a
  table differs; `-Build` merges the manifests first under the Build.Main lock. The first generation found three
  permissions the old table never listed (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MICROPHONE`,
  `CHANGE_NETWORK_STATE`) and the signature permission of the app itself, which is not listed.
- **Hosted pages** - `site/privacy.html`, `privacy-ru.html`, `privacy-uk.html`, `sitemap.xml`, `robots.txt`, generated by
  `tools/site/build-site.ps1` from the three policies (never hand-edited; `-Check` proves they equal their sources, that
  the vendored kit is unchanged and that no page loads anything from another host). The kit is the byte-identical
  `PAGE-STYLE` reference kit (`site/assets/sza-kit.css`, hash in `site/assets/PROVENANCE.txt`, `-text` in
  `.gitattributes`) with a page layer `site/assets/policy.css`; header with RU EN UA and the theme button, footer with the
  eight sibling tools of `SITE-FAMILY-MAP` 1.9 and the one contact. `.github/workflows/pages.yml` publishes `site/`.
- **Data safety source** - `docs/play/PLAY_DATA_SAFETY.md`: the flows table, the three declared directory rows (same as
  FastMediaSorter Android S4241), content not declared, the three fallbacks, form factors including the watch-face package
  (no data), account creation answered No and deletion not applicable, the declarations that follow, operator steps.
- **Channel declaration** - `.sza-canon.json`: `channels` `["play"]`, `site` `product` / `site` / the three pages,
  `privacy.page` `site/privacy.html`; `.sza-profile.json` lock rule gives `site/` to `Code.Scripts`.
- Other surfaces in the same edit: `README.md` Privacy section, `CHANGELOG.md`, `docs/README.md`, the
  `PRODUCT-WEB-PAGES` pointer.

### Decisions taken here (flag any the owner wants changed)

1. **The policy describes the `store` build as the tree builds it today**: exchange server, Drive and the sealed layer
   included. Ticket item 9 applies only if the owner narrows the build. See section 3.3 and the findings below.
2. **`site/` is a separate generated root**, not `docs/`, so that Pages never publishes the handoff, the contract
   pointers or the plan; `privacy.page` names the hosted HTML, the markdown is the source.
3. **Pages are per language** (`PAGE-STYLE` 6), not one trilingual page: the ticket asks for 200 in each language and the
   store takes one address per language.
4. **No web font, no `og:image`**: the pages must make no outside request, and the social card belongs to S0014. The
   compliance gate warns about `og:image` (SZA-SURF02, warning) until S0014 adds it.
5. **Directory data row 3** keeps FastMediaSorter Android's category (Files and docs, names) for the broadcast title so the
   family answers one way; the form source names the fallbacks.
6. **Account deletion answered "not applicable"** (this app creates no account, S0027) with the portal page named for the
   data-deletion link.

### Evidence (2026-10-25, `temp/S0033/checks-2026-10-25.txt`, `temp/S0033/data-safety-vs-policy.md`)

- `tools/privacy/permissions.ps1 -Build`: exit 0, 10 permissions in 3 merged manifests, 3 tables equal; a deliberate edit of
  one table row made it exit 1 and the regenerated table restored exit 0.
- `tools/site/build-site.ps1 -Check`: exit 0. Browser check at 1697 px and in a 360 px frame: one `h1`, no horizontal page
  scroll (the table scrolls inside its box), light and dark theme, zero requests outside the origin.
- `check-compliance.ps1 -IncludeUntracked`: 0 errors, 5 warnings (stale canon digest, one catalog path in
  `PLAN/DONE/S0003`, three `og:image`). Without `-IncludeUntracked` it fails SZA-VER03 only because `build.gradle.kts`
  is not committed yet: the `play` channel is judged on tracked files and the release flow commits them.
- The Data safety answers read against the policy flow by flow: 13 rows, all agree; one NOTE (encryption proven by code,
  not yet by the joint run).

### Still open

- **Owner:** push `site/` to the repository `SerZhyAle/StreamsPlayer_Android` and enable Pages (source: GitHub Actions);
  then `curl -L` the three URLs for 200 and diff their text against the sources - the first line of the definition of done.
  The repository has no remote today.
- **Owner:** answer the Data safety form in the console from `docs/play/PLAY_DATA_SAFETY.md`, after the pages answer.
- **In-app link to the policy** (Play expects one): done 2026-11-08, see "In-app policy link" below.
- **Registry and map:** a row for this product in `SITE-FAMILY-MAP` section 2 and the registry rows are the catalog
  session's; the exact request is in `temp/S0033/catalog-request.md` for the canon session.
- **Reconcile at release:** when S0032 closes, S0029's OAuth client is registered, or the owner narrows the build,
  re-run the three checks and amend the policy, the table and the form source in one edit.

### In-app policy link (2026-11-08)

- The About page (`SettingsAboutDestinationView`, built by S0036) carried a privacy button that opened
  `https://serzhyale.github.io/StreamsPlayer_Android/privacy-policy-en.html`, a page the site never had.
- The About page is not reachable yet: no activity, fragment or layout instantiates it or the settings rail (S0049 item
  1, package 1, which wires the About destination with its license and privacy pages). The in-app link reaches users
  when S0049 lands; until then the app has no path to the policy, which Play expects before the first upload. The
  dependency is the `**Depends on:**` line in the header.
- `SettingsAboutDestinationView.privacyUrlFor(language)` now resolves the hosted page of `site/` by the app language
  (`LocaleHelper.getLanguage`): `privacy-ru.html` for Russian, `privacy-uk.html` for Ukrainian, `privacy.html` for every
  other language - the rule `ExchangePortalLinks` applies to the portal. `PRIVACY_URL` is the English page.
- `SettingsAboutDestinationViewTest` gains three tests: the resolver by language (case ignored, regional tags fall back
  to English); the three addresses equal `$base` and the `File` names of `tools/site/build-site.ps1`, so a renamed page
  or a moved site fails the suite; a click on the privacy button starts `ACTION_VIEW` on `PRIVACY_URL`.
- No other file named the old address (a tree-wide search outside `temp/` and build outputs found none). The help map
  is unchanged: the About surface keeps its controls.
- Parked: S0063 - the release sweep runs `tools/privacy/permissions.ps1` but never `tools/site/build-site.ps1 -Check`,
  so a policy edit left unrendered would reach the store with the sweep green.
- Not built, not tested until the release.

## Last Audit

**Date:** 26-11-08 05:33 | **Mode:** pre-handoff (static: file and value checks, a tree-wide search, the help-map gate, the new test's regexes run on the site generator; no Gradle build or test between releases) | **Outcome:** BlockNeedUserTest
**Counts:** PASS 7 | WARN 1 | FAIL 0 | MANUAL 3 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 3

1. PASS: scope 1 - `docs/PRIVACY_POLICY.md`, `-ru.md`, `-uk.md` exist; `site/privacy.html`, `privacy-ru.html`, `privacy-uk.html` exist (Test-Path, all True).
2. PASS: scope 6 - `docs/play/PLAY_DATA_SAFETY.md` exists; the flow-by-flow reading against the policy (13 rows agree, one NOTE on encryption proven by code only) stands from 2026-10-25 and no policy or form source changed in this pass.
3. PASS: scope 8 - `.sza-canon.json`: expected `channels` play, `site.kind` product, three pages, `privacy.page` `site/privacy.html` | actual the same.
4. PASS: in-app link - the test's two regexes run on `tools/site/build-site.ps1` give `https://serzhyale.github.io/StreamsPlayer_Android` and `privacy.html`, `privacy-ru.html`, `privacy-uk.html`; expected the three addresses `privacyUrlFor` builds for en, ru, uk | actual the same three.
5. PASS: no file outside `temp/` and build outputs carries `privacy-policy-en` (tree-wide search, zero hits).
6. PASS: `tools/help/assert-help-map.ps1 -CommittedBaseline tools/help/help-map-baseline.txt`: exit 0, `assert-help-map: PASS (30 surfaces, 29 rows, 1 baseline, 33 pages, 8 picture sets checked)`, 0.74 s. The default call exits 2, `COULD NOT VERIFY`, because HEAD carries no committed baseline - a tree state outside this ticket; this pass did not touch the baseline.
7. PASS: probe invariant - exactly one `Timber.d("S0033: privacy link opens url=$url")`, alone on its line, in the privacy click handler of `SettingsAboutDestinationView`.
8. RELEASE: compile and the phone unit suites with the three new tests of `SettingsAboutDestinationViewTest` - `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`.
9. RELEASE: definition of done 2 - `tools/privacy/permissions.ps1` against the fresh merged `store` release manifests, run by the same sweep (last exit 0 on 2026-10-25 with `-Build`).
10. RELEASE: definition of done 4 - `check-compliance.ps1` with `play` declared, run by the same sweep (2026-10-25: 0 errors with `-IncludeUntracked`; tracked-only fails SZA-VER03 until the release flow commits `build.gradle.kts`); and `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` by the release operator before the push, until S0063 puts it in the sweep.
11. WARN: the in-app link is right but unreachable - a search of `phone/src/main` for `SettingsAboutDestinationView`, `SettingsNavigationRail` and `settings_nav_about` finds only their declarations; S0049 item 1 (package 1, Approved) wires the About destination with its privacy page. Recorded as `**Depends on:** S0049`.
12. EXEMPT: the `SITE-FAMILY-MAP` row and the registry rows are the catalog session's; the request is prepared for the canon session.

- [ ] MANUAL: the owner pushes `site/` to `SerZhyAle/StreamsPlayer_Android` and enables Pages (source: GitHub Actions); then `curl -L` of `privacy.html`, `privacy-ru.html` and `privacy-uk.html` under `https://serzhyale.github.io/StreamsPlayer_Android/` answers 200 and each page's text equals its source - definition of done 1.
- [ ] MANUAL (after S0049 makes the About page reachable): on a phone with the release build, Settings > About > privacy policy opens `privacy.html` with the app in English or German, `privacy-ru.html` in Russian and `privacy-uk.html` in Ukrainian; logcat shows `S0033: privacy link opens url=` with each address.
- [ ] MANUAL: the owner answers the Play Data safety form in the console from `docs/play/PLAY_DATA_SAFETY.md` after the pages answer, reconciled against the uploaded build (item 9).
