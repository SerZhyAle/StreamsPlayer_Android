# S0010 - Release pipeline and a first release dry-run

**Status:** Draft
**Phase:** 10 of 10 (handoff section 8)
**Verification level:** dry-run - full build, pre-release sweep with a written verdict, drift check, no
upload and no tag.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 10, sections 4.3, 4.5, 4.7, 6a item 2, 7, 9; canon
NEW_PROJECT_CHECKLIST step 9.

## 1. Goal

A release pipeline proven end to end without the one-way step: build every package, run the pre-release
sweep, run the port-ledger drift check, produce the PASS/FAIL verdict the ship step would take - and stop.

## 2. Scope

- Ledger shape chosen here (the stamp says `none` while nothing ships).
- Channels added to `.sza-canon.json` only with their committed manifests.
- Privacy page and Play data-safety form saying the same thing (canon invariants 13-15); a store channel needs
  a hosted privacy page (gate SZA-SURF01).
- The drift check reports zero uncarried FastMediaSorter changes without a written decision (handoff section 9
  item 7).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0002-S0008 (S0009 optional).
- **4.3 Distribution of the first release:** OPEN - ask right before this phase: Play (phone + wear) plus a
  sideload variant at once, or GitHub/sideload first. Decides the signing flow, the version family use and
  whether an INSTALL-TRUST page ships.
- **4.5 GitHub remote:** OPEN - repository name and visibility (`SerZhyAle/StreamsPlayer` is taken by the
  Windows product). Nothing is pushed until the owner names it; ask earlier only if the owner wants a remote
  before this phase.
- **4.7 Site:** OPEN - whether the product gets a page and joins SITE-FAMILY-MAP. Ask before the first store
  upload is planned: the Play listing needs a hosted privacy policy either way.

## 4. Acceptance

- [ ] Build of every package and variant from a clean checkout, exit codes cited.
- [ ] Pre-release sweep verdict file naming the version it judged.
- [ ] Drift check green, or every uncarried change carries a written decision.
- [ ] Nothing tagged, uploaded or published.
