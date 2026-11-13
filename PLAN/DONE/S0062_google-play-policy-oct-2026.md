# S0062 - google-play-policy-oct-2026

**Ticket:** S0062
**Status:** Implemented
**Status note:** Implemented 2026-11-08 (queue pass, static): announcement reviewed, no product change required today; S0066 (target 37 local network permission, policy date 2027-08-25) and S0067 (owner console items) parked. Not built, not tested until the release - RELEASE: tools/privacy/permissions.ps1 -Build, read its list once
**Complexity:** medium - confirmed 2026-11-08: one announcement read against three modules and two variants, no product code changes, evidence file and two parked tickets
**Date:** 2026-10-30

## Captured request and migration scope

[https://support.google.com/googleplay/android-developer/answer/18100766](https://support.google.com/googleplay/android-developer/answer/18100766)

раздай задания андроид-проектам

## Assignment context

Review Google's October 29, 2026 Play policy announcement linked above for this Android product and its Google Play distribution plans. Verify the current policy text and exact deadlines against Google's linked policy and deadline pages. Inventory applicable app modules, variants, permissions, SDKs and Play Console declarations; distinguish actual requirements from clarifications and from changes that do not apply.

Assess local network access, location, all-files access, contacts, accessibility, call logs, AppFunctions, subscriptions and payments where relevant, and the announced app quality and developer account requirements. Implement and validate necessary project changes within the ticket's normal workflow. Record evidence, non-applicability and any account or Play Console actions and deadlines that need the owner. Do not assume a permission or policy violation without evidence.

## 1. Spec

Triage (2026-11-08): the complex rung, forced by "no new files" - the review adds an evidence file and parks two tickets. It changes no
product code, no schema, no screen and no DI wiring.

Problem: Google's announcement of 2026-10-29 lists new and changed Play policies, clarifications and console requirements, each with
a date. Which of them bind this product, with what date, and what the owner has to do, was not recorded.

Goal: every item of the announcement is classed (requirement, clarification, reminder) and either shown to bind or shown not to bind
this product by a command run on the tree, the dates are the ones of Google's Policy Deadlines table, and what only the owner can do
is written down with its date.

Constraint: no permission or policy violation is assumed; a finding needs evidence. The pages were read through a summarizing fetch
tool, so the account-level dates are confirmed by the owner in the console.

Shared-contract boundary: none touched (section 5).

## 2. Findings

Full table, commands and measurements: `PLAN/S0062_google-play-policy-oct-2026/evidence.md`.

- **No change to the product is required by this announcement today.** None of the permissions or APIs it names (local network,
  contacts, location, all files, accessibility, call log, SMS, AppFunctions, billing, ads SDKs) is declared, imported or
  called in `phone`, `wear`, `watchface` or `exchange-core`; the watch face has no code.
- **One requirement binds later:** the local network permission, from the day the target moves from 36 to 37 (policy date
  2027-08-25). The product serves broadcasts on the LAN and plays user-typed addresses, so it will need `ACCESS_LOCAL_NETWORK` and the
  console declaration. Parked as S0066.
- **Owner-only items:** trader status (2027-02-01), Japan payments confirmation (2026-12-01, expected not to apply), the console's
  app registration requirement, two app quality readings after the first upload, Play Age Signals (optional). Parked as S0067.
- **App quality (February 2027):** `release` minifies and shrinks in `phone` and `wear`; their DEX is 5.7 MB and 5.6 MB, below the
  10 MB floor where the 25 percent requirement is enforced. Memory thresholds exist only from real users, after the first upload.

## 3. Open questions

None open. Every item was settled by a page or a command of 2026-11-08, listed in the evidence file.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0066 and S0067 are parked from this ticket and block nothing here. S0047 (first Play release) and S0033
  (privacy and Data safety) are neighbours that touch no file listed here; S0067 hands S0047 the two post-upload readings.
- **Owner questions:** none. The trader declaration is the owner's own act and is in S0067, not a question to this ticket.
- **Canon-session item:** none.

## 4. Definition of done

- Every item of the announcement is classed and has an applies verdict with its reason in the evidence file.
- The permission and API search over the module sources returns no match, with the search proven to find a known permission.
- The follow-ups exist as tickets: S0066 (code) and S0067 (owner), each in the journal and in the queue.
- The dates come from the Policy Deadlines table, and the two places where pages disagree are named.

## 5. Shared-contract boundary

None. No format, wire shape, algorithm constant or behaviour that another product reads is touched; the ticket writes a record and
two Drafts. S0066 will touch the permission list that `docs/privacy/permission-purposes.json` and the three privacy policy tables
render; that is its own boundary check.

## 6. Plan

Coverage: the reading of the announcement and its linked pages is phase 1; the inventory of manifests, code and dependencies is
phase 2; the classification and the evidence file are phase 3; the parked tickets are phase 4.

- [x] Phase 1 - read the announcement, the Policy Deadlines table, the policy preview, the Android local network page, the app quality page
  and the trader page. Check: each is named with its use in section 1 of the evidence file.
- [x] Phase 2 - inventory: the six source manifests, the search over module sources, foreground service types, platform levels, DEX
  sizes, minify settings. Check: the commands of section 3 of the evidence file, run 2026-11-08.
- [x] Phase 3 - `PLAN/S0062_google-play-policy-oct-2026/evidence.md` with the class and applies table. Check: the file exists and
  every announcement item has a row.
- [x] Phase 4 - park S0066 and S0067 with `capture-draft`. Check: both ids print and appear in `spec_catalog/select`.
- RELEASE: `pwsh -NoProfile -File tools/privacy/permissions.ps1 -Build` re-merges the manifests of the tree as it is at the release
  and fails on any permission without a sentence; read its list once for a library-contributed permission of the announced kinds.
  It is not a measured fast check, so it is not run between releases.

## Last Audit

**Date:** 26-11-08 16:30 | **Mode:** static, queue pass (between releases, greps and gate scripts only) | **Verdict:** Implemented - the review is complete; not built, not tested until the release

**Counts:** PASS 8 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 2 | RELEASE 1

**Proven by fresh commands (2026-11-08):**

- PASS - policy permission and API search over `phone/src wear/src watchface/src exchange-core/src build-logic gradle` and the four module
  build scripts (list in evidence section 3.1) - expected: no match | actual: no match, exit 1 (grep's no-match status)
- PASS - the same file set finds a known permission (`grep -rEn 'android.permission.RECORD_AUDIO' --include=AndroidManifest.xml
  --exclude-dir=build phone/src wear/src | wc -l`) - expected: at least 1 | actual: 2
- PASS - source manifests declare only `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, the
  four `FOREGROUND_SERVICE*` ones, `RECORD_AUDIO`, `CAMERA` - expected: none of the announced kinds | actual: none
- PASS - `grep -nE '^(compile|target|min)-sdk' gradle/libs.versions.toml` - expected: `target-sdk = "36"` (below 37, so the local network
  rule is not yet binding) | actual: `target-sdk = "36"`
- PASS - DEX of the last store release APKs - expected: below 10 MB | actual: phone 5,719,864 bytes, wear 5,561,928 bytes
- PASS - `isMinifyEnabled = true` and `isShrinkResources = true` in `release` of `phone` and `wear` - expected: both present | actual: both present
- PASS - `spec_catalog/select -Id S0066` and `-Id S0067` - expected: a Draft record with its file | actual: both found, exit 0
- PASS - `check-headings-unique`, `check-owner-inputs`, `check-evidence-durable` for S0062 - expected: exit 0 | actual: exit 0 each

**Known non-gate, not this ticket's:** `spec_catalog/validate` exits 2 on one row, `Journal->FS`: S0031 is journalled at
`PLAN/S0031_exchange-connection-diagnosis.md` while its file sits in `PLAN/DONE/` (another session's move in flight). Nothing in this
ticket touched S0031; the other nine rows are OK or the 30-day staleness WARN of S0014.

### Action items

1. RELEASE - re-merge the manifests of the tree at the release: `pwsh -NoProfile -File tools/privacy/permissions.ps1 -Build` - exit 0, and
   read its list once for a library-contributed permission of the announced kinds. The merged manifests on disk are from 2026-10-28
   and 2026-10-29, older than the current sources.
2. EXEMPT - out-of-scope: the local network permission at target 37 and its console declaration are S0066.
3. EXEMPT - out-of-scope: the owner's console items (trader status 2027-02-01, Japan payments 2026-12-01, app registration, the two app
   quality readings after the first upload, Play Age Signals) are S0067.

### Manual / on-target

None open in this ticket. The account-level dates in the evidence file were read through a summarizing fetch tool; the owner confirms
them in the console as part of S0067.