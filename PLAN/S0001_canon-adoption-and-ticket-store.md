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

2026-10-02, run in this session against plugin `2026.1002.2` (canon `2026.10.02.2`, core digest
`sha256:410463a9..`, printed identically by the plugin and the canon repository):

- `check-compliance.ps1 -RepoRoot P:\ANDROID\StreamsPlayer_mob` from the plugin root: exit 0,
  `0 error(s), 1 warning(s) (overlay B, canon 2026.10.02.2)`. The warning is SZA-CANON06: the contrib record
  exists in the canon repository and reaches the plugin cache only with the canon session's deploy. The same
  gate from the canon repository root: exit 0, `0 error(s), 0 warning(s)`. Baseline before this phase: exit 1,
  3 errors, 1 warning.
- `tools/harness.ps1 spec_catalog/validate`: exit 0, `9 OK, 0 WARN, 0 FAIL` over 10 records.
- `tools/harness.ps1 spec_catalog/release-queue -Validate`: exit 0, `10 ticket(s), no drift`.
- Catalog: seven adoption rows in the registry section 2 and a consumer section in the stream-catalog consumers
  list, written as this product's own entries; byte-checked against a backup (7 and 9 inserted lines, line
  endings preserved).
- Found and reported to the canon session, not patched: the harness id allocator hard-codes `^S\d{4}$`; the
  harness validator exits 1 on an empty journal.
