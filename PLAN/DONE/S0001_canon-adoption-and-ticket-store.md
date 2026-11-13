# S0001 - Canon adoption, contract pointers, ticket store and release plan

**Status:** Verified
**Phase:** 1 of 10 (handoff section 8)
**Verification level:** gate - the canon compliance gate and the harness validators, exit codes cited.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 1, and the owner's session brief of 2026-10-02: adopt the canon in
new-repo mode (overlay B, reference model), sync the contracts, stand up the ticket store and the release
package plan from the canon harness, turn section 8 into phase tickets and section 4 into owner-decision items,
ask the phase 2 decisions, verify, commit locally. No product code, no remote, no push.

## 1. Goal

The repository has a real agent contract, a canon stamp, contract pointers, a working ticket store with its
release plan, and one ticket per remaining phase - so every later session starts from the store, not from the
handoff.

## 2. Scope

- `CLAUDE.md` rewritten from the bootstrap stub: canon pointer, overlay B, reference model, contrib record, the
  catalog named once, section 6a of the handoff as standing law, frozen anchors, the release package plan.
- `AGENTS.md` kept as the delegating stub.
- `.sza-canon.json` with a freshly computed digest; `.sza-profile.json` for the harness; `tools/harness.ps1`
  forwarder (no harness script is copied).
- `docs/contracts/` - one pointer per contract of handoff section 7, plus its index.
- This product's rows in the catalog registry and in the stream-catalog consumers list.
- Contrib record in the canon repository (`rules/contrib/streamsplayer_mob.md`).
- `PLAN/` ticket store: journal, phase tickets S0001-S0010, `RELEASE_QUEUE.md`, `RELEASE_READY.md`,
  `RELEASE_QUEUE_DONE.md`.
- `LICENSE` (MIT, owner decision 4.6), `.gitignore` leak globs.

## 3. Decisions taken in this phase

- **Ticket id scheme:** `S0001`.. (`^S\d{4}$`), the harness default. Another prefix was considered and
  rejected: the harness allocator (`New-CatalogId`) and `insert.ps1 -Id` hard-code `^S\d{4}$` although
  `grammar.ticketIdPattern` is a profile key, so any other scheme would validate ids allocation can never
  issue (reported to the canon session, kept as is by its order). Our ids overlap FastMediaSorter's S space,
  so a ticket of another repository is always cited with the repository name next to the id.
- **Release package plan:** the harness files under `PLAN/`, reconciled on the journal's single write path
  (`Write-Catalog` -> `Sync-ReleaseQueue`); package number from the `current-next-release:` marker, starting
  at 1; `PLAN/` is **tracked** in git. Full record in `CLAUDE.md`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none - first phase; S0002 depends on it.
- **4.1 Frozen anchors:** answered 2026-10-02, recorded in S0002.
- **4.2 Signing:** answered 2026-10-02, recorded in S0002.
- **4.6 License:** answered 2026-10-02 - MIT, `Copyright (c) 2026 Serhii Zhyhunenko (SerZhyAle)`; `LICENSE`
  written in this phase because the compliance gate fails without it.

## 4. Acceptance

- [x] Canon compliance gate from the plugin root: exit 0, 0 errors.
- [x] `spec_catalog/validate`: exit 0.
- [x] `spec_catalog/release-queue -Validate`: exit 0; one status change moves this ticket's line from
      `RELEASE_QUEUE.md` to `RELEASE_READY.md`.
- The foundation commit carries this ticket in its closed state; no remote, no push.

## Last Audit

**Date:** 2026-10-10 Europe/Malta | **Mode:** re-audit | **Outcome:** Verified
**Counts:** PASS 9 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 2

### Fixes

- Removed the `PLAN/` ignore rule, which contradicted this ticket and CLAUDE.md section 4.6.
- Corrected the journal paths for S0002-S0006 to their existing `PLAN/DONE/` specs through
  `tools/harness.ps1 spec_catalog/update -Id <id> -File <destination>`. Each call exited 0 and printed
  `Implemented -> Implemented`; their statuses, decisions and implementation evidence were preserved.
  The harness regenerated the release projections through its single write path.

### Evidence

1. **PASS - canon compliance.**
   `pwsh -NoProfile -File C:/Users/serzh/.claude/plugins/cache/sza-unified-rules/sza/2026.1002.3/tools/check-compliance.ps1 -RepoRoot P:/ANDROID/StreamsPlayer_mob`
   exited 0. Expected: no errors or warnings. Actual:
   `0 error(s), 0 warning(s) (overlay B, canon 2026.10.02.3)`.
2. **PASS - adoption digest.** The same gate with `-PrintDigest` exited 0. Expected: the stamp's digest.
   Actual: `sha256:410463a9ccdfd75af7a233fc91656c200fe05f047cf7617d7fc53d32e7b81afa`.
   The installed plugin's patch version is newer, but the digest is equal; RULE-DELIVERY rule 4 owes no
   stamp write. The stamp remains the original adoption record.
3. **PASS - ticket store.** `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate` exited 0.
   Expected: zero warnings or failures. Actual: `Summary: 9 OK, 0 WARN, 0 FAIL`, 14 records;
   `every non-archived record points at an existing file`.
4. **PASS - release plan.**
   `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/release-queue -Validate` exited 0.
   Expected: no drift. Actual: `release-queue: OK - 14 ticket(s), no drift`.
   S0001 remains Verified in package 1's ready set; current-next-release remains 1.
5. **PASS - ticket tracking policy.** `git check-ignore --no-index -- <all PLAN file paths>` exited 1
   with no matches. Expected: no PLAN files ignored. Actual: all 22 files eligible for tracking.
6. **PASS - secret protection.** `git check-ignore --no-index -- <signing/secret/scratch samples>`
   exited 0 with all 12 samples matched: jks, keystore, p12, pem, pfx, snk, keystore.properties,
   signing.properties, Play service account JSON, .env, .env.audit and temp/S0001/evidence.txt.
7. **PASS - adoption artifacts.** Re-read CLAUDE.md, AGENTS.md, the stamp/profile, harness forwarder,
   docs index, all contract-pointer entries and MIT LICENSE. The catalog registry retains this product's
   adoption rows; STREAM-BANK retains the Android consumer declaration; the canon contribution record
   exists and the compliance gate resolves it. PowerShell assertions for registry/consumer presence and
   all 14 journal paths exited 0 with `PASS` output.
8. **PASS - owner decisions.** S0002 retains answered 4.1, 4.2 and 4.6; S0008 owns open 4.4;
   S0010 owns open 4.3, 4.5 and 4.7. `rg -n` against those specs exited 0 with the named decision lines.
9. **PASS - probe invariant.** `rg -n 'S0001:' phone/src wear/src watchface/src` exited 1 with no
   matches. The enclosing assertion command exited 0 with `PASS: no S0001 runtime probes.`

### Scope and documentation

- **EXEMPT - later contract synchronization.** Current-version pointer/registry reconciliation already
  belongs to S0013. This audit verifies S0001's adoption foundation; no contract schema or product
  behavior changed. Contract pointers and registry rows were read but not rewritten.
- **EXEMPT - commit/release.** The original foundation commit was documented by the 2026-10-02 audit.
  This check/fix request does not authorize a new commit, push or release.
- Affected records: this audit, .gitignore and journal file paths. Release files are generated by the
  harness. The docs index, contract pointers, agent contract, stamp and profile need no edit for these fixes.

### Action items

None within S0001. No manual or device checks are required for this foundation repair.
