# S0057 - Contract sync to the catalog of 2026-10-29: pointers and registry cells behind

**Ticket:** S0057
**Status:** Implemented
**Status note:** 2026-10-29: 12 pointers (16 contract ids) moved to the catalog of 2026-10-29, pointer block of check-sync PASS 31 of 31; one registry patch for the canon session replaces the three pending ones (registry is LF now), verified on a scratch catalog: lag behind 0 unparsed 0, check-sync leaves only the item CZ pair (S0053); gaps parked as S0058 S0059 S0060 S0061; nothing built or run
**Complexity:** medium - a contract-sync pass over 16 pointers and 11 registry cells, catalog side owned by the canon session
**Date:** 2026-10-29

## Captured request and migration scope

Parked from S0032 at the unattended queue pass of 2026-10-29. Symptom: `pwsh -NoProfile -File tools/contracts/check-sync.ps1 -CatalogRoot <catalog>` exits 1 with 29 findings (0.43 s). 16 pointer versions in docs/contracts/ name a version older than the catalog's domain header (among them DEVICE-EXCHANGE 1.11, STREAM-BANK 2.5, ICON-SET 0.37, DIAGNOSTIC-REPORT 0.15, PAGE-STYLE 3.5, SITE-FAMILY-MAP 1.10, HARNESS-PROFILE 0.14, CHECK-VERDICT 0.14, CHECK-BASELINE 0.12, CONTEXT-HELP 0.2, INPUT-PARITY 0.5, MEDIA-CLASSIFICATION 0.13, PACKAGE-VERSIONING 0.6, SITE-STRUCTURE 0.4, DOC-EXTERNAL-QUALITY 0.13, DOC-INTERNAL-QUALITY 0.11), and 11 of this product's adoption rows in the catalog registry carry a stale version cell. The item CZ table differs from the catalog text; its re-vendor is already S0053 item 3. Needed: a contract-sync pass that reads each newer version's document-log rows, records what this product conforms to, and moves the pointers and registry cells (the registry side by the canon session).

## What changes here

- **Target:** the version current when this ticket is worked (owner's rule of 2026-10-14, S0026): the catalog of 2026-10-29.
- **Shared-contract boundary:** this ticket moves pointers and proposes registry cells only. No format, wire shape, algorithm
  constant or behaviour of this product changes in it; a gap found at a boundary is a ticket (S0058-S0061), not an edit, because the
  contract moves first and the code follows (CONTRACTS section 4).
- **Pointers:** the 12 files of `docs/contracts/` that name an older version move to the current one (16 contract ids), each with a
  short paragraph of what the new text obliges this product, and the table of `docs/contracts/README.md` follows.
- **Registry:** not edited from here. `PLAN/S0057_contract-sync-2026-10-29/registry-sync.patch` is one patch for the canon session
  that replaces the pending patches of S0026, S0037 and S0041: the registry changed from CRLF to LF, so none of the three applies
  to it any more.
- **Out of scope:** the item CZ table re-vendor (S0053 item 3); every change of code; `LIVE-BROADCAST` (1.6 is already current).

## Research items

None open. Every contract was read at its current version from the document-log rows and the amendment text of 2026-10-29.

## Result (run of 2026-10-29)

| Contract | From -> to | Kind | What this product does with it |
| --- | --- | --- | --- |
| `STREAM-BANK` | 2.4 -> 2.5 | clarification | Item P: country half held; language half not (three-letter codes), S0059. Vocabulary and vectors owed by the contract owner, nothing to replay. |
| `DEVICE-EXCHANGE` | 1.10 -> 1.11 | additive | Items EB, EG, EH, EI held by reading; ED not held (S0058); EE, EF read, not inspected; DZ, EA, EC, EJ-EM bind roles not declared or restate records kept. |
| `ICON-SET` | 0.36 -> 0.37 | additive | Nothing moves in `docs/PHONE_SHELL_ICON_MAP.md`; `source.exchange-server` was already mapped to the one-tone glyph. |
| `DIAGNOSTIC-REPORT` | 0.14 -> 0.15 | additive | No exporter exists; no claim made. |
| `PAGE-STYLE` | 3.4 -> 3.5 | additive | No kit byte changes; the pages read no locale query. |
| `SITE-FAMILY-MAP` | 1.9 -> 1.10 | corrective | The footer of the privacy pages carries the old Windows label and one-character addresses (S0061). |
| `HARNESS-PROFILE` | 0.12/0.13 -> 0.14 | additive | New keys are optional; `.sza-profile.json` declares none. |
| `CHECK-VERDICT` / `CHECK-BASELINE` | 0.13 -> 0.14 / 0.11 -> 0.12 | clarification | Two gaps in the help-gate scripts, S0060. |
| `CONTEXT-HELP` | 0.1 -> 0.2 | additive | Section 7 read; the help gate ends `PASS`; F and G wait for the owner decision S0045 D2. |
| `INPUT-PARITY` | 0.4 -> 0.5 | clarification + additive | "Applicable", viewer text precedence and the `help`/`F1` row; the thirteen gaps stand (S0052). |
| `MEDIA-CLASSIFICATION` | 0.12 -> 0.13 | additive | Not applicable, as recorded. |
| `PACKAGE-VERSIONING` | 0.5 -> 0.6 | additive | Registers Tourist bands only; the `build-logic` sources are unchanged since 2026-10-02. |
| `SITE-STRUCTURE`, `DOC-EXTERNAL-QUALITY`, `DOC-INTERNAL-QUALITY` | 0.3/0.12/0.10 -> 0.4/0.13/0.11 | additive, clarification | No gap moves (S0051). |

Parked while reading: S0058 (change-password dialog shows no address), S0059 (three-letter language codes), S0060 (help-gate
scripts vs `CHECK-VERDICT` 0.14 and `CHECK-BASELINE` 0.12), S0061 (privacy page footer links and label).

The seven compatibility-law questions (CONTRACTS section 5): no boundary code changed in this run, so the answers of S0037 audit
section 9 stand. The one new item that touches them is `STREAM-BANK` item P (an unrecognized enum value degrades to the documented
default): the closed value set holds, the code is only wrong about which value, S0059.

## Acceptance criteria

- [x] Every pointer in `docs/contracts/` names the current version: `check-sync.ps1` on the live catalog, "31 pointer versions match
      domain headers".
- [x] Each contract read at its current version and cited by id and section, with no catalog path in a tracked file (grep of
      `docs/contracts/` for `P:` finds none).
- [x] The registry proposal parses and is current: on a scratch catalog `contract-lag.ps1` reports 25 rows, behind 0, pending 0,
      unparsed 0; `check-sync.ps1` leaves only the item CZ pair.
- [x] The patch applies to the live registry and reproduces the proposal byte for byte (`patch -p1 --binary`, exit 0, `cmp`).
- [x] Gaps are tickets or dated exceptions: S0058, S0059, S0060 (exception rows until 2026-12-31 in the patch), S0061, and S0052,
      S0053 for what they already carried.
- [ ] MANUAL: the canon session applies the patch; `check-sync.ps1` then reports only the item CZ findings.

## Last Audit

**Date:** 26-10-29 23:59 | **Mode:** static | **Outcome:** Partial
**Counts:** PASS 8 | WARN 1 | FAIL 0 | MANUAL 1 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 1

### Action items
1. WARN - `tools/contracts/check-sync.ps1` still exits 1 on the live catalog and on the proposed one - item CZ table and its
   PROVENANCE row (S0053 item 3) - the re-vendor is that ticket's.
2. EXEMPT - `LIVE-BROADCAST`: 1.6 is current in the registry shape of S0037 (`partial 1.6` x2) and in the pointer; no cell moves.
3. RELEASE - run the pre-release sweep (`pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`): the phone and Wear suites
   with the golden facet fold and the vendored vectors, `:build-logic:convention:test` for the 0.6 note, and the help-gate
   self-test. Nothing was built or run in this pass; the registry notes say "by reading" wherever that is the case.

**Proven by fresh commands (2026-10-29):** `check-sync.ps1` live (exit 1; 16 pointer findings, 11 cell findings and the CZ pair
before, pointer block PASS after the pointer edits) and on the scratch catalog with the proposal (exit 1, the CZ pair only);
`contract-lag.ps1` live (18 rows, 4 behind, 4 unparsed, 2 pending) and proposed (25 rows, 0 behind, 0 unparsed, 0 pending);
`patch -p1 --binary` of the proposal on a copy of the live file (exit 0, identical); `assert-help-map.ps1` (exit 0, `PASS`).
Evidence: `PLAN/S0057_contract-sync-2026-10-29/attachments/`.

**Unverified:** every `held by reading` item of `DEVICE-EXCHANGE` 1.11 (EB, EG, EH, EI) and the not inspected ones (EE, EF) were
read in code and not run; the pairing-code surfaces against item ED; whether the help gate refuses an unwired `inherit` row;
the catalog vectors of `STREAM-BANK` items O and P (unpublished); anything on a device or in a store. No release, tag or upload
is implied.

### Manual / on-target
- [ ] MANUAL: the canon session applies `PLAN/S0057_contract-sync-2026-10-29/registry-sync.patch` to `_meta/REGISTRY.md` (and
      discards the three older patches), then reruns `tools/contracts/check-sync.ps1`; only the item CZ findings should remain.
