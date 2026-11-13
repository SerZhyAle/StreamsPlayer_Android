# Contract synchronization spec - StreamsPlayer Android

Generated 2026-10-29 by tools/contract-lag.ps1 from the registry and the contracts' document logs.
For the project session that owns the product: it files this as its own ticket, edits its own registry
rows and its own code, and nobody does either on its behalf. Surfaces covered: StreamsPlayer Android.

The log entries below are matched by version and by the contract ids a row names; a row that names none is
shown for every contract in its domain. Treat each list as a map, not as the whole change.

## What is owed

| Contract | Surface | Synchronized with | Current | State | Verified |
| --- | --- | --- | --- | --- | --- |
| `CHECK-BASELINE` | StreamsPlayer Android | 0.11 | 0.12 | partial | 2026-10-26 |
| `CHECK-VERDICT` | StreamsPlayer Android | 0.13 | 0.14 | partial | 2026-10-26 |
| `CONTEXT-HELP` | StreamsPlayer Android | 0.1 | 0.2 | partial | 2026-10-26 |
| `HARNESS-PROFILE` | StreamsPlayer Android | 0.12 | 0.14 | behind | 2026-10-23 |
| `ICON-SET` | StreamsPlayer Android | 0.36 | 0.37 | behind | 2026-10-23 |
| `PACKAGE-VERSIONING` | StreamsPlayer Android | 0.5 | 0.6 | behind | 2026-10-23 |
| `REPO-STAMP` | StreamsPlayer Android | 0.12 | 0.13 | behind | 2026-10-23 |

## `CHECK-BASELINE` - 0.11 to 0.12  (StreamsPlayer Android)

Home: domain `automated-checks/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-29 | CHECK-BASELINE 0.12 | clarification | Item B, from item 2 of the doc-html-translate proposal and item 3 of the FileDO proposal: rule 4's comparison with the committed set binds every set baseline, the committed copy of the file may be the snapshot, and a hand edit committed with its reason is rule 6's update path. The requested exemption for sets no tool rewrites is not adopted. | Read - code changes only if it relied on the older wording |

The row says `partial`: close the gap, or list each missing rule as a dated exception.

## `CHECK-VERDICT` - 0.13 to 0.14  (StreamsPlayer Android)

Home: domain `automated-checks/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-29 | CHECK-VERDICT 0.14 | clarification | Item A, from `PROPOSAL-2026-10-20-doc-html-translate-contract-sync.md` item 1 and `PROPOSAL-2026-10-21-filedo-contract-sync.md` item 1: a self-test of its own assertions prints a test verdict line; further `key=value` tokens may follow `skipped=M` and a reader ignores them; `run` counts every executed test, a subtest included, and `skipped` the selected tests that did not execute; a failing test line may carry a bare deciding count; a suite that could not run every group ends .. | Read - code changes only if it relied on the older wording |

The row says `partial`: close the gap, or list each missing rule as a dated exception.

## `CONTEXT-HELP` - 0.1 to 0.2  (StreamsPlayer Android)

Home: domain `in-app-help/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-29 | 0.2 | additive | Section 7, items A to G, by the canon session on the portfolio owner's order of 2026-10-29: an `inherit` surface answers F1 and carries the button, opening its parent's help (`PROPOSAL-2026-10-21-filedo-contract-sync.md`); a surface whose own input contract forbids any added control wires neither, marked `ask: "off"`; an element a shipped locale does not show is recorded as absent; rung 2 may be held statically by input hashes; the place of an Android dialog's button (`PROPOS .. | Review - adopt it, or record a dated exception saying why not |

The row says `partial`: close the gap, or list each missing rule as a dated exception.

## `HARNESS-PROFILE` - 0.12 to 0.14  (StreamsPlayer Android)

Home: domain `rule-adoption/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-23 | REPO-STAMP 0.13, HARNESS-PROFILE 0.13 | additive | Define optional legacy-preserving carriers and dispatch, document-to-wire mapping and staged required-carrier migration. Add generated reader vectors; real delivery remains a separate evidence requirement. No existing payload key becomes required. | Review - adopt it, or record a dated exception saying why not |
| 2026-10-27 | HARNESS-PROFILE 0.14 | additive | Section 3 gains rules 8 to 10: the agent fields of `runner.instances.<name>`, with the meanings of the `TASK-FLOW` 0.7 agent record and never written by the portfolio; per-instance `promptTemplate` and `complexityModels`, falling back to the shared `runner` values; and an optional top-level `portfolio` object that overrides the registry's console and analysis values for one repository. Wire unchanged (document 0.14 maps to wire 1.0); every key is optional, so no profile must .. | Review - adopt it, or record a dated exception saying why not |

## `ICON-SET` - 0.36 to 0.37  (StreamsPlayer Android)

Home: domain `iconography/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-29 | 0.37 | additive | `nav.menu` added; `source.exchange-server` takes the exchange mark's one-tone glyph (the accepted 2026-10-19 proposal folded); further names for five records; the Ukrainian name of `action.autoplay-disable`; rule 3 and rung 3 clarified and the name collisions listed; Skip divided between `media.next` (the current item) and `action.skip` (a step or the rest of a flow). Proposals: `PROPOSAL-2026-10-20-doc-html-translate-contract-sync.md`, `PROPOSAL-2026-10-20-fms-skip-record-to .. | Review - adopt it, or record a dated exception saying why not |

## `PACKAGE-VERSIONING` - 0.5 to 0.6  (StreamsPlayer Android)

Home: domain `package-versioning/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-29 | 0.6 | additive | `PROPOSAL-2026-10-23-tourist-android-bands.md` accepted by the catalog owner on the portfolio owner's order of 2026-10-29 to decide every pending proposal: band table (rule 3) - three Tourist Android families registered before the first package ships (phone `com.sza.tourist` band 0..5, watch app `com.sza.tourist` shared id band 6..9, watch face `com.sza.tourist.watchface` own namespace band 0..5), and the `noLegal` edition paragraph names Tourist beside StreamsPlayer. Section .. | Review - adopt it, or record a dated exception saying why not |

## `REPO-STAMP` - 0.12 to 0.13  (StreamsPlayer Android)

Home: domain `rule-adoption/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-23 | REPO-STAMP 0.13, HARNESS-PROFILE 0.13 | additive | Define optional legacy-preserving carriers and dispatch, document-to-wire mapping and staged required-carrier migration. Add generated reader vectors; real delivery remains a separate evidence requirement. No existing payload key becomes required. | Review - adopt it, or record a dated exception saying why not |

## Never verified (`pending`)

Nobody has read these contracts against the product since the catalog took its shape. Read each at its
current version and fill the row; there is no log range to show because no starting version is recorded.

- `INPUT-PARITY` (StreamsPlayer Android), current 0.5
- `MEDIA-CLASSIFICATION` (StreamsPlayer Android), current 0.13

## Rows the program cannot read

`Implements` / `Reads` must be one version, `partial <version>` or `-` (REGISTRY section 2). Move the
detail into `Notes` and split multi-contract rows:

- `DEVICE-EXCHANGE` (StreamsPlayer Android), registry line 629: `partial 1.7`
- `LIVE-BROADCAST` (StreamsPlayer Android), registry line 628: `partial 1.3`
- `RULE-DELIVERY` (StreamsPlayer Android), registry line 640: `partial 0.12`
- `STREAM-BANK` (StreamsPlayer Android), registry line 627: `partial 2.4`

## Complete adoption inventory

Read every pointer and own adoption/exception row, including current, pending, partial, unreadable, ahead and not-applicable entries.
Determine newly applicable contracts from actual surfaces; a missing row is not proof of conformity or non-applicability.
Correlate existing tickets before implementation. Do not edit another product's tree or registry claims.

| Contract | Surface | State | Recorded version | Current | Verified |
| --- | --- | --- | --- | --- | --- |
| `CHECK-BASELINE` | StreamsPlayer Android | partial | partial 0.11 | 0.12 | 2026-10-26 |
| `CHECK-VERDICT` | StreamsPlayer Android | partial | partial 0.13 | 0.14 | 2026-10-26 |
| `CONTEXT-HELP` | StreamsPlayer Android | partial | partial 0.1 | 0.2 | 2026-10-26 |
| `DEVICE-EXCHANGE` | StreamsPlayer Android | unparsed | partial 1.7 | 1.11 | 2026-10-23 |
| `HARNESS-PROFILE` | StreamsPlayer Android | behind | 0.12 | 0.14 | 2026-10-23 |
| `ICON-EXTERNAL` | StreamsPlayer Android | current | 0.12 | 0.12 | 2026-10-23 |
| `ICON-RENDER` | StreamsPlayer Android | current | 0.24 | 0.24 | 2026-10-23 |
| `ICON-SET` | StreamsPlayer Android | behind | 0.36 | 0.37 | 2026-10-23 |
| `INPUT-PARITY` | StreamsPlayer Android | pending | 0.4 | 0.5 | pending |
| `LIVE-BROADCAST` | StreamsPlayer Android | unparsed | partial 1.3 | 1.6 | 2026-10-23 |
| `MEDIA-CLASSIFICATION` | StreamsPlayer Android | pending | - | 0.13 | pending |
| `PACKAGE-VERSIONING` | StreamsPlayer Android | behind | 0.5 | 0.6 | 2026-10-23 |
| `REPO-LAYOUT` | StreamsPlayer Android | current | 0.11 | 0.11 | 2026-10-23 |
| `REPO-STAMP` | StreamsPlayer Android | behind | 0.12 | 0.13 | 2026-10-23 |
| `RULE-DELIVERY` | StreamsPlayer Android | unparsed | partial 0.12 | 0.12 | 2026-10-23 |
| `STREAM-BANK` | StreamsPlayer Android | unparsed | partial 2.4 | 2.5 | 2026-10-23 |
| `TEST-LAB` | StreamsPlayer Android | current | 0.1 | 0.1 | 2026-10-23 |
| `USER-PLAYLIST` | StreamsPlayer Android | current | 0.12 | 0.12 | 2026-10-23 |

## Definition of done

1. Each contract above is read at its current version and cited by id and section, never by catalog path.
2. The implementation matches it, or the project declares a justified exception in its own REGISTRY section 3 row
   without prior owner approval: clause, scope, evidence, impact, compensating behavior and review condition.
   Name the responsible project. These rights apply to mandatory, active and draft contracts; no unconditional compliance claim is required.
   Temporary debt has an `until` date and remediation ticket; a permanent supported variant has a linked design record.
3. The product's own suite runs the catalog's conformance vectors at the current version; the command and
   its exit code are cited.
4. The product's own rows in REGISTRY section 2 carry `Implements`, `Reads`, `Verified` (today) and a one-line
   note, in the cell format of that section, one contract id per row.
5. Each `docs/contracts/<ID>.md` pointer names the current version.
6. The project may recommend corrections and additions at any point. It amends contracts it owns; for another owner,
   it files `PROPOSAL-<date>-<topic>.md` with evidence and compatibility consequences, retaining an exception where needed.
   Only its own implementation rows advance after fresh proof; another product's rows remain untouched.
7. The final audit distinguishes proven conformance, exceptions, recommendations and unverified work.
   Record missing vectors or runtime evidence explicitly; retain partial claims for unresolved gaps.
   An exception does not waive independent legal, store, security or data-safety release blockers.
