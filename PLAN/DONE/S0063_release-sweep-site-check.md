# S0063 - Release sweep checks the hosted privacy pages against their sources

**Ticket:** S0063
**Status:** Implemented
**Status note:** Implemented 2026-11-08 (unattended queue pass, static): Site check added after the Permissions block in tools/release/pre-release-sweep.ps1; check-syntax PASS (exit 0); regex call matches the OK line and rejects a FAIL line. Not built, not tested until the release - RELEASE: build-site.ps1 regenerates site/ (S0061 phase 4), then build-site.ps1 -Check exit 0, then a pre-release-sweep verdict with Site PASS
**Complexity:** light - one more check in the release sweep script
**Date:** 2026-11-08

## Captured request and migration scope

Parked from S0033 (2026-11-08, unattended queue pass).

Symptom: the release sweep (`tools/release/pre-release-sweep.ps1`) checks the privacy permissions tables against the release manifests (`tools/privacy/permissions.ps1`), but it never runs `tools/site/build-site.ps1 -Check`. The hosted privacy pages under `site/` are generated from `docs/PRIVACY_POLICY*.md`; when the policy or its generated permissions table changes and nobody re-renders `site/`, the pages Play links to drift from the source while the sweep stays green.

Evidence: `Select-String tools/release/pre-release-sweep.ps1 -Pattern build-site` finds nothing; the header of `tools/site/build-site.ps1` says "-Check .. Run it before the privacy URL is given to the store", and `.github/workflows/pages.yml` says to check the site before the push - both are operator steps no gate enforces. S0033 definition of done 1 requires the hosted text to equal the repository source.

Wanted: the sweep records a `Site` check from `build-site.ps1 -Check` (exit code and verdict line), after the permissions check, so a stale page fails the release verdict.

## 3. Open questions

None open. The check is mechanical, and the one shared-contract question is answered in section 6.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none blocks this ticket. S0033 is the parent (the ticket it was parked from) and touches no file listed here. S0061 changed the generator this check compares against; its regeneration is a RELEASE item of this ticket (section 5, item 4).
- **Owner question on the check:** none - the check takes the same pass shape as the Permissions check beside it, and S0033 definition of done 1 already states the requirement.
- **Canon-session item:** none.

## 4. Approach

Rung: **primitive spec**. One existing file changes (`tools/release/pre-release-sweep.ps1`), no new file, no new public type, no schema or migration, no DI, no screen, the check is mechanical, and the delta is about ten lines. Every criterion of Stage 0 holds.

- After the `Permissions` block, run `tools/site/build-site.ps1 -Check` the way that block runs its script: capture the output, read the exit code at once, and record a `Site` check through `Record-Check`.
- The check passes only when the exit code is 0 and the output has a line that starts with `OK site: `. Any other result fails the verdict, and so does an exception.
- It runs with and without `-SkipBuild`: the pages are sources, not build outputs.
- `-Check` writes nothing, so the sweep stays read-only toward `site/`.
- Consequence to record, not a defect: `site/` still holds the pages rendered before S0061, so the first sweep after this change fails `Site` until the generator has run once (S0061 phase 4). The failure is the gate doing its job.

## 5. Definition of done

1. `tools/release/pre-release-sweep.ps1` calls `build-site.ps1` with `-Check` on one line, placed after the line that calls `permissions.ps1`. Check: `grep -n "permissions.ps1\|build-site.ps1" tools/release/pre-release-sweep.ps1` lists the permissions line first, then a build-site line that ends in `-Check`.
2. The success pattern matches the line the script prints. Check: `grep -c "OK site: " tools/release/pre-release-sweep.ps1` prints `1`.
3. The release syntax gate passes. Check: `pwsh -NoProfile -File tools/release/check-syntax.ps1` exits 0 and prints `release-syntax: PASS` (recorded fast command, 0.288 s).
4. RELEASE: `pwsh -NoProfile -File tools/site/build-site.ps1` regenerates `site/` (S0061 phase 4); then `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` exits 0 and prints `OK site:`; then `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` writes a verdict in which `Site` is PASS. Not run between releases: the generator and the gate have no recorded wall time.

## 6. Shared-contract boundary

None. The sweep reads the rendered pages and records a verdict; it does not change page text, a wire shape, an algorithm constant or any catalog file. PRODUCT-WEB-PAGES governs the page content and is only read.

## Last Audit

**Date:** 26-11-08 07:37 | **Mode:** static (between releases: static checks, the syntax gate and a direct regex call only) | **Outcome:** Implemented - not built, not tested until the release
**Counts:** PASS 6 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 0 | RELEASE 2

**Proven by fresh commands (2026-11-08):**

- `grep -n "permissions.ps1\|build-site.ps1" tools/release/pre-release-sweep.ps1` - expected: the permissions line, then a build-site line ending in `-Check` | actual: line 277 (permissions) and line 286 (`build-site.ps1') -Check`), exit 0
- `grep -c "OK site: " tools/release/pre-release-sweep.ps1` - expected: 1 | actual: 1, exit 0
- `pwsh -NoProfile -File tools/release/check-syntax.ps1` - expected: exit 0 and `release-syntax: PASS` | actual: exit 0, `release-syntax: PASS (...\tools\release\pre-release-sweep.ps1)`. Recorded fast command, 0.288 s on 2026-10-29; not re-timed in this run
- Regex direct call (the sweep's pattern `(?m)^OK site: ` against the success line `build-site.ps1 -Check` prints, and against a `FAIL site:` line) - expected: `success=True failure=False` | actual: `success=True failure=False`, exit 0. No gate was run: `build-site.ps1 -Check` has no recorded wall time, so it is a RELEASE item
- Probe invariant: `grep -rln 'S0063' phone/src wear/src watchface/src exchange-core/src` - expected: no log line carries the id | actual: 0 files
- Doc sync: `docs/DOCS_SURFACES.md` line 39 already names "the pre-release sweep" as a place that runs `build-site.ps1 -Check`; that statement is true after this change | actual: line 39, exit 0

**Scope and hygiene:** two files changed by hand: `tools/release/pre-release-sweep.ps1` (one `Site` block of about nine lines after the Permissions block) and this spec. `PLAN/spec-catalog.jsonl` and the status line were written by `spec_catalog/update` only. No new file, no probe line, no dead code, no edit to `site/`.

**Shared-contract boundary:** none (section 6). No catalog file, page text or wire shape is touched.

**Entry note:** the headline behaviour (the release verdict gates on the Site check) runs only at release, so it is proven by the static rungs above and not yet by a run. The ticket stays `Implemented`; it moves to `Verified` only when a release verdict reads `Site` PASS.

**RELEASE items (not built, not tested until the release):**

- RELEASE: `pwsh -NoProfile -File tools/site/build-site.ps1` - regenerates `site/privacy.html`, `privacy-ru.html`, `privacy-uk.html` and `sitemap.xml` with the S0061 generator changes. Until it runs, the new check fails by design.
- RELEASE: `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` - expected: exit 0 and an `OK site:` line.
- RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` - expected: the verdict the sweep writes lists the `Site` check with status PASS (the sweep prints the verdict path on its last line), after the regeneration above.

