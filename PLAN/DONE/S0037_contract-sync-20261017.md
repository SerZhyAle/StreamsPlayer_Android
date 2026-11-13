# S0037 - Synchronize accepted contract proposals of 2026-10-17

**Ticket:** S0037
**Status:** Implemented
**Status note:** Audit, pointers, tickets S0049-S0053 and the registry proposal done 2026-10-27; registry rows await the canon session; unverified list in PLAN/S0037_contract-sync-20261017/audit.md section 14
**Date:** 2026-10-17

## Captured request and migration scope

Нужно закрепить все предложения внутрь контрактов, обновить версии контрактов и создать во все подчинённые им проекты новые спецификации о приведении себя в соотвествие к контракту. проект всегда должен иметь право потребовать иметь обоснованное исключение - проверь, что это есть в правилах работы по контрактам

Проект при приведении себя в соотвествие с контрактом должен иметь право объявлять обоснованные исключения, вносить рекломендованные поправки и дополнения

## 1. Problem and authority

The portfolio owner accepted outstanding proposals in canon S0007 on 2026-10-17. This repository must
read the applicable current contracts, audit its actual behavior, implement justified changes or
declare explicit exceptions, and propose any further corrections/additions. Existing implementation
claims stay at their previously verified versions until fresh evidence supports an update.
This is a new synchronization task, not closure of older implementation tickets or approval to publish.

## 2. Contract inventory

The versions below are the targets at task creation. Read each current contract's consolidation
amendment of 2026-10-17, its normative appendices and any later accepted changes. Cite by ID and section;
resolve the catalog only through this repository's agent-rules pointer, never duplicate its text here.
For newly applicable contracts establish a scoped row rather than infer conformance from a product name.

| Contract | Target | Existing Implements / Reads (verification date) |
| --- | --- | --- |
| `BOOLEAN-OPTION` | 2.0 | not verified here; determine applicable scope |
| `BUTTONS` | 2.0 | not verified here; determine applicable scope |
| `COLLAPSIBLE-GROUP` | 1.1 | not verified here; determine applicable scope |
| `CONTEXT-HELP` | 0.1 | not verified here; determine applicable scope |
| `DEVICE-EXCHANGE` | 1.3 | - / partial 0.17 (2026-10-11) |
| `DOC-EXTERNAL-QUALITY` | 0.12 | not verified here; determine applicable scope |
| `DOC-INTERNAL-QUALITY` | 0.10 | not verified here; determine applicable scope |
| `HARNESS-PROFILE` | 0.12 | 0.11 / - (2026-10-10) |
| `ICON-RENDER` | 0.24 | - / 0.18 (2026-10-10) |
| `ICON-SET` | 0.34 | - / 0.29 (2026-10-10) |
| `INPUT-PARITY` | 0.4 | - / 0.3 (pending) |
| `LIVE-BROADCAST` | 1.1 | - / 0.18 (2026-10-11) |
| `MEDIA-CLASSIFICATION` | 0.11 | - / - (pending) |
| `SETTINGS-NAVIGATION` | 2.0 | not verified here; determine applicable scope |
| `STREAM-BANK` | 2.4 | - / partial 2.3 (2026-10-10) |
| `UI-SIZING` | 1.0 | not verified here; determine applicable scope |

## 3. Project-specific work

Carry source-side fixes only after the source owner repairs and tests them; phone/watch/watch-face are distinct surfaces. Preserve the scoped classification declaration and partial artwork verdict until proven.

Read every existing pointer and own adoption/exception row, including missing, partial, unparsed and
not-applicable entries. Map the affected surfaces and existing tickets before implementation so this
task coordinates the new requirements without duplicating work already owned by another ticket.
No counterpart repository is edited from this product's implementation run without owner authorization.

## 4. Exceptions, corrections and additions

The project ALWAYS has the right to declare a justified exception in its own registry row without
prior contract-owner approval, and to recommend amendments, clarifications and additions. Record the
contract/clause, exact scope, concrete reason and evidence, user/data/security impact, compensating
behavior, responsible project and review condition. Temporary migration debt has an until date and
a named remediation ticket; permanent supported variants have a dated linked design/standards record.
Do not invent expiry dates for permanent variants or unconditional conformance for a local limitation.

Amend contracts this project owns through the catalog's dated/versioned change process. For another
owner, file a dated PROPOSAL with suggested wording, evidence and compatibility consequences; leave
the existing contract text intact. The owner records acceptance, partial acceptance or reasoned rejection.
Until acceptance, keep an exception for a justified local deviation. A proposal alone is not a waiver.
Independent legal, store, security and data-safety release blockers still apply. These rights follow
catalog RULES section 15 and canon CONTRACTS section 4 and cannot be removed from the audit verdict.

## 5. Implementation and evidence plan

1. Inventory applicability per platform/edition/surface and correlate existing work; list exact clauses.
2. Audit the compatibility law: names, higher minor/major, unknown enum, absence, bounds and defaults.
3. Apply accepted behavior with safe migration and preserved user data/remaps, or record justified exceptions.
4. Use generated reference artifacts; regenerate in their owning implementation, never hand-edit vectors.
   New browser/worker/clipboard/sealing profiles require real negative cases as well as happy paths.
5. Run this repository's appropriate focused checks. UI changes require rendered keyboard/pointer,
   theme/high-contrast, scaling, long locale/RTL and supported device/engine acceptance. State anything
   untested instead of claiming that a build or source read proves it.
6. Update short contract pointers, own registry rows and exception/proposal references only after proof.
   Retain partial versions for unresolved gaps. A catalog version bump never upgrades implementation rows.

## 6. Acceptance criteria

- [x] Every applicable target has a clause-to-surface audit and an implemented, excepted or justified
      not-applicable outcome; no silent optional treatment of a mandatory requirement.
      (`PLAN/S0037_contract-sync-20261017/audit.md` sections 2-9)
- [x] Every declared exception contains the rationale/evidence/review fields above, and temporary debt
      has its expiry and remediation ticket. Recommended corrections/additions are recorded and handed off.
      (registry proposal `registry-sync.patch`, section 3 rows and the new section; `proposals.md` R1-R8)
- [x] Commands, exit codes, artifact versions/hashes and observed results are recorded. Missing generated
      artifacts, device/browser checks and joint replay remain explicitly unverified until performed.
      (audit sections 10 and 14; `attachments/`)
- [ ] Own implementation rows, pointers and docs reflect actual verified behavior. Other products' claims
      and credentials/anchors remain untouched. No release, tag, upload or deployment is implied.
      Pointers and docs: done. Registry rows: written as a proposal for the canon session (CLAUDE.md section 6),
      not yet applied - this box closes when the canon session applies it.
- [x] Open questions are resolved or explicitly carried to a named successor; the final audit distinguishes
      proven conformance, exceptions, recommendations and remaining unverified work.
      (successors S0049, S0050, S0051, S0052, S0053, S0041, S0043; owner questions Q1-Q4 in the audit)

## 7. Last Audit

**Date:** 2026-10-27 | **Mode:** contract synchronization | **Evidence:** `PLAN/S0037_contract-sync-20261017/`

The ticket's 2026-10-17 targets were read at the catalog's current versions (`DEVICE-EXCHANGE` 1.10,
`LIVE-BROADCAST` 1.6, `UI-SIZING` 1.1, `HARNESS-PROFILE` 0.13, `ICON-SET` 0.36, `MEDIA-CLASSIFICATION` 0.12).
No product code or contract-boundary behaviour was changed by this run.

**Proven by fresh commands (2026-10-27):** `gradlew.bat :build-logic:convention:test :exchange-core:test
testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --continue` under the `Build.Main` lock, exit 0 -
phone store 1158 and noLegal 1160, wear 274 tests, 0 failures (exchange-core 67 and build-logic 24 reported
UP-TO-DATE, not rerun); `tools/help/assert-help-map.ps1` exit 0; `tools/audit/run-selftests.ps1` exit 0; isolated
reader probes on plugin 2026.1026.3 show unknown age with a differing digest as an error and the equal-digest
control as no finding, so the `REPO-STAMP` / `RULE-DELIVERY` reader exceptions' closing condition is met.

**Findings:** the unified-UI components of S0036 are tested but the settings rail, the About view and the groups
reach no screen, the command flow layout is in 5 of 33 layouts, and no capture or timing exists (S0049);
`INPUT-PARITY` 0.4 has thirteen gaps on applicable surfaces (S0052); `LIVE-BROADCAST` items S, T, U and
`DEVICE-EXCHANGE` items DW, DX, DY are unimplemented and the producer stops in the reverse of item T's order
(S0053, S0043); the document registry and gates of `DOC-INTERNAL-QUALITY` / `DOC-EXTERNAL-QUALITY` do not exist
(S0051); the artwork exception of `STREAM-BANK` gets its carrier (S0050). `tools/contracts/check-sync.ps1` falls
from 8 findings to 4 with the pointers and the proposed registry; the 4 are S0041's (0.13) and the vendored item CZ
table (S0053).

**Handed off:** `registry-sync.patch` and `registry-sync-reason.txt` (apply after S0026's patch), `proposals.md`
(R1-R8), owner questions Q1-Q4 (audit section 13).

**Unverified:** every device, TalkBack, gamepad, TV-remote, headset, paired-watch and two-network check; rendered
captures and page timings; the RTSP server's end signals; source parity (129 ledger rows drifted). No release, tag,
upload or deployment is implied.

### Manual / on-target

- [ ] MANUAL: the owner decides Q1 (`PLAN/` tracked or ignored), Q3 (S0036 stays closed) and applies, through the canon session, the registry proposal.
