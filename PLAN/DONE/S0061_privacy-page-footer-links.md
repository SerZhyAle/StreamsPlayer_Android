# S0061 - Privacy page family footer links are broken and the Windows label predates SITE-FAMILY-MAP 1.10

**Ticket:** S0061
**Status:** Implemented
**Status note:** Implemented 2026-10-30 (queue pass pq1, static): build-site.ps1 $tools pairs now arrays (leading comma), Windows type label reads 1.10 wording in EN/RU/UK; direct call of the block count=8, exit 0. Not built, not tested until the release - RELEASE: run tools/site/build-site.ps1 to regenerate the three privacy pages and sitemap, then build-site.ps1 -Check exit 0 and read the footer tiles
**Complexity:** light - one generator array shape and three label strings, pages regenerated
**Date:** 2026-10-29

## Captured request and migration scope

Parked from S0057 (contract sync to the catalog of 2026-10-29).

Symptom 1: the family footer of the three hosted privacy pages (`site/privacy.html`, `privacy-ru.html`, `privacy-uk.html`) links nowhere useful. Each tile is rendered as `<a href="a" rel="noopener"><b>F</b><span>Android media sorter</span></a>`, then `href="t"`, `href="a"`, `href="t"` .. - the href is one character of a name or of an address. Cause, by reading `tools/site/build-site.ps1` (the `$tools` block under the SITE-FAMILY-MAP comment): the eight `@('name', 'url')` pairs are written on separate lines inside one `@( .. )`, which PowerShell flattens into sixteen strings, so `$tool[0]` and `$tool[1]` are the first two characters of a string rather than a name and an address. PAGE-STYLE section 6 and SITE-FAMILY-MAP rule 1 require the footer to link the sibling pages.

Symptom 2: SITE-FAMILY-MAP 1.10 (2026-10-29) changed the Windows row's type label to "Windows media and file manager"; the pages say "Windows media sorter" (the `Tools` lists of the three locales in the same script, and the generated tiles). The catalog notes that a footer with the old type still links the right page and misstates only the label; here the link is also wrong, so both go together.

Evidence (2026-10-29, static reading): `site/privacy.html` lines 325-335 and `tools/site/build-site.ps1` lines 33-67. `site/` is a render target of that script: fix the generator and regenerate with it, never hand-edit the pages. Nothing is published until the owner pushes `site/` (PRODUCT-WEB-PAGES pointer), so no user has seen the broken tiles.

Needed: build the pairs as an array of arrays (leading comma or `[pscustomobject]`), take the Windows type labels and the RU / UK equivalents from the 1.10 row in all three locales in one edit, regenerate the pages and their sitemap, and read the pointer `docs/contracts/PRODUCT-WEB-PAGES.md` at PAGE-STYLE 3.5 and SITE-FAMILY-MAP 1.10 once it is fixed. Run the generator at the release (not a measured fast check).

## 3. Open questions

All resolved on 2026-10-30 without the owner, by the records below.

- Which Windows type label, in which words? **The 1.10 row settles the English one only:** `Windows media and file manager`
  (`P:\Contracts\product-web-pages\SITE-FAMILY-MAP.md` section 2 and 1.10; `PROPOSAL-2026-10-26-windows-row-type-media-and-file-manager.md`).
  The RU and UK equivalents are the wording the product's own pages already use for the same type: `Медиа- и файловый менеджер`
  and `Медіа- та файловий менеджер` (`P:\WINDOWS\CyrFlip\docs`, the family's landing text). The tile reads `<label> для Windows`
  as the other RU and UK labels do: `Медиа- и файловый менеджер для Windows`, `Медіа- та файловий менеджер для Windows`.
- Is the tile name changed? **No.** The `<b>` name is the product's display name (SITE-FAMILY-MAP 1.9); only the type `<span>` changes.
- Are the URLs changed? **No.** 1.10 keeps the URL and the `Tool` cell unchanged.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none blocks this ticket. S0057 is the parent (the ticket it was parked from). S0060 and S0058 are neighbours
  that touch no file listed here.
- **Owner question on the Windows type label:** none - settled by SITE-FAMILY-MAP 1.10 (EN) and the product's own RU and UK wording (section 3 above).
- **Canon-session item:** none. SITE-FAMILY-MAP 1.10 is already in the catalog; this ticket only follows it.

## 4. Definition of done

- The `$tools` block in `tools/site/build-site.ps1` holds eight array pairs (`@(name, url)`), so `$tools[$_][0]` is a name and
  `$tools[$_][1]` an address.
- The Windows row's type label in the `Tools` list of all three locales is the 1.10 wording (EN, RU, UK), and no `Windows media sorter`
  or `Сортировщик медиа для Windows` or `Сортувальник медіа для Windows` text remains in the generator.
- Not regenerated here: `site/privacy*.html` and `site/sitemap.xml` are rendered by the generator at the release. Until then the
  published pages stay as they are (nothing is published until the owner pushes `site/`).
- Static checks in section 6 pass in this run, with their exit codes recorded in the audit.

## 5. Shared-contract boundary

Footer data only. SITE-FAMILY-MAP 1.10 is already in the catalog and this ticket reads it; no wire shape, no algorithm constant
and no catalog file is written from here. The pointer `docs/contracts/PRODUCT-WEB-PAGES.md` is read once the fix is in, as the
audit says.

## 6. Plan

Coverage: the array shape (symptom 1) maps to phase 1; the labels (symptom 2) map to phase 2; the direct call of the changed block
is phase 3; the regeneration of the pages is a RELEASE item (phase 4, not run here).

- [ ] Phase 1 - `tools/site/build-site.ps1` `$tools`: each `@('name', 'url')` line is prefixed with a leading comma, so the outer
  `@( .. )` keeps eight pairs instead of flattening them. Check: `grep -c "^    ,@('" tools/site/build-site.ps1` prints `8`.
- [ ] Phase 2 - the Windows row (index 1) of the `Tools` list in each of the three `$pages` entries: EN `Windows media and file manager`,
  RU `Медиа- и файловый менеджер для Windows`, UK `Медіа- та файловий менеджер для Windows`. Check:
  `grep -c "Windows media sorter\|Сортировщик медиа для Windows\|Сортувальник медіа для Windows" tools/site/build-site.ps1` prints `0`,
  and `grep -c "file manager\|файловый менеджер\|файловий менеджер" tools/site/build-site.ps1` prints `3`.
- [ ] Phase 3 - direct call of the changed block: a `pwsh -NoProfile` snippet that takes the `$tools` lines of the script and prints
  `$tools.Count` and `$tools[1][0]`, `$tools[1][1]`. Expected: `8`, `Fast Media Sorter &amp; Sharing for Windows`, the Lite URL. Measured in this run; recorded in the audit.
- [ ] Phase 4 - RELEASE: `pwsh -NoProfile -File tools/site/build-site.ps1` regenerates the three pages and the sitemap, then
  `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` exits 0. Not a fast check (unmeasured), so it is not run between releases.

## Last Audit

**Date:** 2026-10-30 | **Mode:** queue pass pq1 (between releases, static checks and a direct call only) | **Verdict:** Implemented - not built, not tested until the release

**Proven by fresh commands (2026-10-30):**

- `grep -c "^    ,@('" tools/site/build-site.ps1` - expected: 8 | actual: 8, exit 0
- `grep -c "Windows media sorter\|Сортировщик медиа для Windows\|Сортувальник медіа для Windows" tools/site/build-site.ps1` - expected: 0 | actual: 0, exit 1 (grep's no-match status)
- `grep -c "file manager\|файловый менеджер\|файловий менеджер" tools/site/build-site.ps1` - expected: 3 (EN, RU, UK) | actual: 3, exit 0
- Direct call of the changed `$tools` block: `pwsh -NoProfile -File PLAN/S0061_privacy-page-footer-links/attachments/check-tools.ps1` (scratch, evaluates lines 59-68 of the generator) - expected: `count=8`, `t1=Fast Media Sorter &amp; Sharing for Windows`, `u1=https://serzhyale.github.io/FastMediaSorter_Lite/` | actual: `count=8`, those two values, `elements=Object[] x8`, exit 0, wall 0.332 s

**Scope and hygiene:** one file changed (`tools/site/build-site.ps1`): the leading comma on the eight pairs, the Windows type label in the three `Tools` lists, the `$tools` comment now cites SITE-FAMILY-MAP 1.10. No new file in the tree; `PLAN/S0061_privacy-page-footer-links/attachments/check-tools.ps1` is scratch. No probe line (the ticket is not in the needs-manual-test status). Nothing dead left.

**RELEASE items (not built, not tested until the release):**

- RELEASE: `pwsh -NoProfile -File tools/site/build-site.ps1` - regenerates `site/privacy.html`, `privacy-ru.html`, `privacy-uk.html` and `sitemap.xml`; the footer tiles then carry real addresses and the Windows label. Not run here: the spec marks the generator as unmeasured.
- RELEASE: `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` - expected exit 0 after the regeneration.
- RELEASE: open the three regenerated pages and check the footer tiles link the eight sibling sites and the Windows tile reads the 1.10 label in each locale.
- Entry note: the headline behaviour (working footer tiles) is not yet shown in a rendered page, so this ticket is Implemented on the static evidence above only; it is not Verified until the release run above is read.

