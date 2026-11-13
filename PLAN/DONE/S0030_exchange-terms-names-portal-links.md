# S0030 - Exchange words, product names, icons and portal links on phone and watch

**Status:** BlockNeedUserTest
**Status note:** Development complete (dictionary, names, glyphs, deep links, relay wording; phone 1087+1089 and wear 266+266 tests green; EN/RU/UK deep links opened on the emulator). Only testing remains: the account-state UI on a signed-in exchange account and the watch on a Wear OS device; portal sections and registry rows are with other parties.
**Phase:** device exchange, release-46 milestone (FastMediaSorter Android S4203) - independent of the build phases of handoff section 8
**Verification level:** a string sweep against the contract dictionary (a test that reads the vendored table); an icon sweep in both themes; each deep link opened on the emulator in the three locales.

## 0. Source

- Owner, 2026-10-14: answers to the questionnaire of FastMediaSorter Android (FMS_A) milestone S4203, final:
  - Names: Google Play hides "for Android"; the word "mobile" is avoided in product names - the products also
    run on VR headsets and Android TV.
  - One common documentation portal, published from FMS_A; every program that has something to say writes its
    own sections and edits the common part; EN is the original, RU and UK are rebuilt before deploy and all
    three are written at once.
  - "through Google Drive" / "through GD" for the Drive channel; never "relay" in the UI.
- FMS_A S4219 (`DEVICE-EXCHANGE`: terms dictionary EN/RU/UK, product names, the common error table), FMS_A
  S4221 (`ICON-SET`: exchange glyphs), FMS_A S4222 (documentation contracts: portal links, languages,
  authorship), FMS_A S4220 (canon: the store-listing platform suffix and "mobile"). The same work elsewhere:
  FMS_W 233, StreamsPlayer (Windows) StreamsPlayer SP-0224.
- Facts in this tree, 2026-10-14: `app_name` is "StreamsPlayer" on phone and watch; README's title is
  "StreamsPlayer for Android"; the phone opens portal topic pages from the catalog menu "Media exchange and
  broadcasts" (`docs/MEDIA_EXCHANGE_PORTAL.md`) - topic level only, no link from a particular screen; the watch
  opens a web portal from About ("Open on phone"); the phone's publish notice says the server "relays" the
  broadcast; "Mobile" in the watch's network monitor names the cellular network, not the product.

## 1. Goal

Every exchange screen of the phone and the watch uses the family's words for the same things, in the three
languages, with the family's glyphs; the product carries its agreed name on every surface; and where a screen
needs explaining, one tap opens the portal page that explains exactly that screen, in the app's language.

## 2. Scope

1. **Dictionary.** The EN/RU/UK terms table of S4219 vendored with provenance (contract, version, date,
   SHA-256) and compared byte for byte by `tools/contracts/check-sync.ps1`; every exchange string of phone and
   watch (S0016-S0022, S0027-S0029, S0031, S0032) rewritten to its terms; a JVM test fails on a dictionary term
   spelled otherwise in the exchange string set.
2. **Product names.** The names table of S4219 applied to `app_name`, the watch, the watch face, the About
   screens, README and the store listing source: the Play title without "for Android", no "mobile" in any
   product name; the cellular-network wording of the watch's network monitor stays.
3. **Glyphs.** The exchange glyphs S4221 adds to `ICON-SET`, used on every exchange control of phone and watch
   that S4221 names, mapped in `docs/PHONE_SHELL_ICON_MAP.md`, with the `ICON-RENDER` contrast and state rules
   held.
4. **Deep links.** The portal pages S4222 names, opened from the screen they explain: the account screen
   (accounts and sign-in, deleting an account), the broadcast list, the publish notice, the Drive screen, each
   diagnosis step (S0031); on the watch through "Open on phone". The link picks EN, RU or UK by the app
   language with EN as the fallback, as today.
5. **This product's portal sections.** The sections the portal gives this product (receiving, broadcasting,
   the watch) written in EN, RU and UK at once in the FMS_A portal source, under S4222's authorship rule - the
   common part edited only where this product's facts change it.
6. **"relay" out of the UI.** The publish notice and any other user-visible string drop "relay" for the
   dictionary's word.

Not in scope: new features; the privacy page (S0033); strings of screens unrelated to the exchange.

## 3. Constraints and dependencies

### 3.1 Constraints

- Written to the texts FMS_A S4219, S4221 and S4222 fold; not started before S4219 folds. A term this product
  needs and the dictionary lacks is a proposal beside the contract, never a local word.
- The portal source lives in the FMS_A repository and is published from there: this product writes its own
  sections there through that repository's process, never by an unrecorded edit, and `docs/MEDIA_EXCHANGE_PORTAL.md`
  here keeps only the links.
- House text style in every string; TalkBack names change together with the visible words.

### 3.2 Related tickets

S0019, S0020, S0021, S0022, S0027, S0028, S0029, S0031, S0032 (their strings), S0004 (the icon map), S0014
(listing source), S0033 (privacy page), S0034 (contract rows).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0004, S0014, S0019, S0020, S0021, S0022, S0027, S0028, S0029, S0031, S0032, S0033, S0034.
- **Names and portal rules:** answered 2026-10-14 (FMS_A S4203 questionnaire) - section 0.

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :wear:testDebugUnitTest
  --console=plain` exits 0 including the dictionary test.
- `tools/contracts/check-sync.ps1` exits 0 including the vendored terms table.
- Icon sweep of the exchange screens in light and dark themes on phone and watch, recorded in
  `docs/PHONE_SHELL_ICON_MAP.md`.
- Every deep link opened on the emulator in EN, RU and UK, the page named and reached, recorded in
  `temp/S0030/`.
- The `ICON-SET` registry row and the documentation-contract rows S4222 names carry this product's state.

## 5. Implementation record (2026-10-25)

What was done, by scope item, and what stays open. Evidence: `temp/S0030/` (not committed).

1. **Dictionary.** Items CW (terms), CZ (one table of messages) and CY (ladder) of `DEVICE-EXCHANGE` 1.7 are vendored as text
   under `phone/src/test/resources/device-exchange/` with provenance rows in `PROVENANCE.txt`;
   `tools/contracts/check-sync.ps1` cuts the same lines from the catalog and compares them byte for byte (it also
   binds `DOC-EXTERNAL-QUALITY` and `SITE-STRUCTURE` to the new pointer `docs/contracts/PORTAL-DOCS.md`).
   `ExchangeTermsDictionaryTest` (13 tests) holds the exchange string set of the phone and the watch, EN/RU/UK, to the
   terms, the retired words, item CZ word for word for every refusal and cast failure (the Kotlin mirror
   `DeviceExchangeReasons` too), item CK (no "update the program"), the house text style and the Russian ё. Every
   exchange string was rewritten to it; "relay" is out of the UI except as the term "relay through the server".
2. **Names.** `app_name` stays StreamsPlayer on phone, watch and watch face (no "mobile" in any product name; the watch's
   cellular-network wording stays). The invitation notice names the Android sibling by its name (Fast Media Sorter &
   Organizer) and the Windows one (Fast Media Sorter & Sharing for Windows); README, CHANGELOG follow the dictionary.
   The store listing source does not exist in this repository yet (S0014).
3. **Glyphs.** `ic_connect_device`, `ic_server_relay`, `ic_guest` are the records' own drawings, used on Connect watch,
   the publish row / switch / watch relay status and the guest notices; mapped in `docs/PHONE_SHELL_ICON_MAP.md`.
   Device sweep of the broadcast settings row in EN/RU/UK, light and dark: 8.88:1 / 10.91:1.
4. **Deep links.** `ExchangePortalLinks` (one resolver, EN/RU/UK, English fallback) opens: accounts and sign-in, deleting
   an account, the broadcast list, the Drive card, the publish notice ("Learn more"), and, from the watch, "Help on the
   phone" (a request by page name over the existing open-on-phone message; the phone builds the address in its own
   language). Opened on the emulator in EN, RU, UK (and German, which reaches the English page): `temp/S0030/deeplinks.txt`.
   `docs/MEDIA_EXCHANGE_PORTAL.md` is the readable list, held to the resolver by `ExchangePortalLinksTest`.
5. **Portal sections.** Written in EN, RU and UK in `docs/PORTAL_SECTIONS_STREAMSPLAYER.md`; the portal source is
   FastMediaSorter Android's and read-only here, so they are handed over, not applied.
6. **"relay".** The publish notice and the watch strings use the dictionary's wording.

Also fixed on the way: the audio playback service crashed at creation (Media3 1.11: `BroadcastAudioLoadControl` relied
on Kotlin delegation, which does not forward LoadControl's throwing defaults; regression test added); the directory
showed the wire mode (`AUDIO_ONLY`) as a word; five hard-coded English toasts and the "PIN:" label are resources.

Open: (a) the account-state UI of the Exchange server screen (Connect watch glyph and link, account help and deletion
links, the broadcast-list link, the guest glyph) and the watch (relay glyph, "Help on the phone", the phone's answer) were
not exercised on a device - the first needs a signed-in exchange account, the second a Wear OS device; (b) the deep
links of each diagnosis step wait for S0031; (c) the portal sections await FastMediaSorter Android; (d) the registry
rows are the canon session's - the exact text is in `PLAN/DONE/S0030_exchange-terms-names-portal-links/registry-patch.md`;
(e) `check-sync.ps1` still exits 1 on two findings that are not this ticket's (`REPO-STAMP` and `HARNESS-PROFILE` 0.12 against
the catalog's 0.13, S0041); all of its S0030 blocks pass; (f) the owner's wording "through Google Drive" against
item CW "via Google Drive": the product follows item CW.

## Last Audit

**Date:** 2026-10-27 | **Mode:** pre-handoff (static re-read of this spec and its recorded evidence; no new build or test run) | **Outcome:** BlockNeedUserTest
**Counts:** PASS 6 | WARN 3 | FAIL 0 | MANUAL 2 | UNCHECKABLE 0 | EXEMPT 0

1. PASS: scope 1 - the dictionary is vendored with provenance and compared byte for byte by `check-sync.ps1`; `ExchangeTermsDictionaryTest` (13 tests) holds the exchange strings of the phone and the watch to the terms in EN/RU/UK.
2. PASS: scope 2 - no "mobile" in any product name, `app_name` unchanged on phone, watch and watch face.
3. PASS: scope 3 - the glyphs are mapped in `docs/PHONE_SHELL_ICON_MAP.md`; the broadcast settings row was swept in EN/RU/UK, light and dark (8.88:1 and 10.91:1).
4. PASS: scope 4 - the deep links go through one resolver and were opened on the emulator in EN/RU/UK (`temp/S0030/deeplinks.txt`, `ExchangePortalLinksTest`).
5. PASS: scopes 5 and 6 - the portal sections are written in EN/RU/UK in `docs/PORTAL_SECTIONS_STREAMSPLAYER.md`; "relay" is out of the UI.
6. PASS: phone 1087 and 1089 tests, wear 266 and 266, green (status note of 2026-10-25).
7. WARN: the portal sections await FastMediaSorter Android, the owner of the portal source.
8. WARN: the registry rows are the canon session's (`PLAN/DONE/S0030_exchange-terms-names-portal-links/registry-patch.md`).
9. WARN: `check-sync.ps1` exits 1 on the `REPO-STAMP` and `HARNESS-PROFILE` findings of S0041; every S0030 block passes.

- [ ] MANUAL: the account-state UI of the Exchange server screen (Connect watch glyph and link, account help and deletion links, the broadcast-list link, the guest glyph) on a signed-in exchange account.
- [ ] MANUAL: the watch (relay glyph, "Help on the phone" and the phone's answer) on a Wear OS device. The deep links of each diagnosis step come with S0031.
