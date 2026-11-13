# S0055 - Help pages lose their layout on WebView before Chrome 87 (API 26-28 without WebView updates)

**Status:** BlockNeedUserTest
**Status note:** Needs the before/after look of two help pages on the API 27 (WebView 61) and API 35 emulators on a release build; see the MANUAL lines of Last Audit.
**Complexity:** primitive - confirmed 2026-10-29 at the spec pass: two existing files (the help stylesheet and its
generated asset copy), no new file, no new type, no DI, no screen, about 80 lines of CSS, no deferred decision once
the three questions of section 3 were settled by research
**Phase:** in-app help; parked from S0035 on 2026-10-29 (found by its release-build walk, not fixed there)

## 1. Symptom

On the Small_Phone emulator (API 27, system WebView 61.0.3163.98, never updated) the help viewer of the store release
build opens every page and F1 and `?` reach the same page, but the page has no side margins: the text and the picture
touch the screen edges. The phone's `minSdk` is 26, so API 26-28 devices are supported; a device whose WebView has
not been updated from the store renders the pages this way.

## 2. Evidence

- `temp/S0035/release-api27-viewer.png` (not committed): the catalog page on API 27, text flush to the left edge.
- `temp/S0035/walk-release-api27.tsv`: 9 surfaces, F1 and `?` open the same page on the release build.
- `phone/src/main/assets/help/help.css` (ported from FastMediaSorter Android, derived from the `CONTEXT-HELP`
  reference `help.css`) uses `clamp()` (Chrome 79), logical properties `padding-inline`, `margin-block`,
  `inset-inline-start`, `border-block-*` (Chrome 87) and flex `gap` (Chrome 84). Before those versions the declarations
  are dropped: the `--gutter` custom property built from `clamp()` is invalid and `.container` loses its padding.
- During the first page load on that emulator the sandboxed WebView renderer process wrote a breakpad microdump
  (`P renderer`, 00:17:11); the app process did not die and the page opened on the next request. Not reproduced since.

## 3. Open questions - resolved by research at the spec pass (2026-10-29, unattended queue pass)

No question needed the owner: each was settled by the contract text, the generator or the port ledger.

1. **Local fallbacks or the catalog first?** Local, in a separate layer. `CONTEXT-HELP` 0.1/0.2 rule 10 asks for
   "the help layer `help.css`" and logical properties so a right-to-left page mirrors; it names no browser floor and
   no byte-identity with `reference/help.css`, and section 7 item I already lets a product add "its own layer after
   `help.css`". The fallbacks are an additive layer whose every block is guarded by `@supports not (..)`, so a current
   WebView renders the reference layer alone: nothing another product reads or reproduces changes, and this is not a
   shared-contract boundary change (`CONTRACTS` section 4 does not apply). The same layer is offered upstream as a
   proposal for the canon session (section 6); it does not block this ticket.
2. **Right-to-left.** A physical declaration that sits beside a logical one breaks the mirror only where both apply.
   Here every physical declaration lives inside `@supports not (padding-inline: 0)`, so on a current WebView none of
   them applies; on an old one every asymmetric declaration has a `[dir="rtl"]` twin that swaps left and right. The
   generator writes `dir="rtl"` on the `<html>` of a right-to-left locale (`generate-help-pages.ps1`, `$script:RtlLocales`),
   so the twins have their hook. No right-to-left page is generated today (en, ru, uk only).
3. **The port ledger row.** The ledger row of `help.css` (`scripts/docs/help/help.css` -> `tools/help/help.css`)
   tracks the FastMediaSorter source hash only; the drift check never hashes the destination, and the destination
   already differed from the source after the port's clean-up. The local layer is therefore recorded where the
   destination says what it is: the layer's own header comment in `tools/help/help.css` states that it is not part of the
   reference layer (no ticket id: permanent code carries none), and this spec links it to S0055. The source hash is unchanged since the port (`38f1c056..`, re-hashed
   2026-10-29), so no drift row is affected.

## 4. Problem

A help page on a WebView older than Chrome 87 drops every logical property, `clamp()`, `min()`, flex `gap`,
`overflow-wrap: anywhere` and `color-mix()`, so the page has no gutters, the numbers of the "In this order" list sit
over the text, the element entries lose their gold start border and their marker spacing, and the related links and
pills lose their spacing and tint.

## 5. Approach

Append to `tools/help/help.css` (the source of truth; never the generated copy) a "local layer" after the reference
layer, then regenerate the asset copy with `tools/help/generate-help-pages.ps1`:

- `@supports not (padding-inline: 0)` (Chrome < 87): every logical declaration of the reference layer restated with
  physical properties (start = left, end = right, block-start = top, block-end = bottom), the container gutter as a
  fixed 16 px, flex `gap` replaced by child margins with `gap: 0` (so Chrome 84-86 does not double it), grid `gap`
  restated as `grid-gap` (Chrome 57); `[dir="rtl"]` twins for every asymmetric declaration.
- `@supports (width: clamp(..)) and (not (padding-inline: 0))` (Chrome 79-86): the container keeps the reference
  `--gutter`.
- `@supports not (width: clamp(..))`: a fixed hero heading size, one column of related links.
- `@supports not (overflow-wrap: anywhere)`: `break-word`.
- `@supports not (color: color-mix(..))`: a plain tint and border for `.pill`.

`scroll-margin-block-start` has no replacement on WebView 61 and is left alone (it only shifts a `:target` scroll).

## 6. Upstream proposal for the canon session (not a blocker)

For the catalog's `in-app-help/reference/help.css` and FastMediaSorter Android's `scripts/docs/help/help.css` (same
text, same defect; that repository is read-only from here): append the block between the
`local layer: physical fallbacks for a WebView before Chrome 87` header and the end of
`tools/help/help.css` in this repository, verbatim. Reason: every
Android adopter with `minSdk` below 29 ships help pages to devices whose WebView may predate Chrome 87; the layer is
inert on current engines, so the reference template renders unchanged.

## 7. Done criteria

- A help page on the API 27 emulator (WebView 61) has its gutters, its numbered order list and its element markers in
  place, and the same page on API 35 is unchanged (before / after screenshots).
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1` exits 0.
- Every logical property, `clamp()`, `min()`, flex `gap`, `overflow-wrap: anywhere` and `color-mix()` use of the
  reference layer has a guarded fallback in the local layer (static grep).
- `phone/src/main/assets/help/help.css` is the generator's copy of `tools/help/help.css` (hash equal).

### 7.1 Owner inputs (Approval gate)

- **Related tickets:** S0035 (in-app help) parked this ticket from its release-build walk; nothing blocks S0055 and
  S0055 blocks nothing.
- **Owner decisions:** none needed - section 3 was settled by the contract text, the generator and the port ledger.

## Last Audit

**Date:** 2026-10-29 | **Mode:** pre-handoff, static | **Outcome:** BlockNeedUserTest
**Counts:** PASS 5 | WARN 0 | FAIL 0 | MANUAL 2 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 1

1. PASS - `pwsh -NoProfile -File tools/help/generate-help-pages.ps1`, exit 0, 2.27 s (first run); expected PASS and
   only the stylesheet rewritten | actual `generate-help-pages: PASS (files=34 written=1 pages=10 locales=en,ru,uk
   date=2026-10-29)`, the one written file `phone/src/main/assets/help/help.css`.
2. PASS - `sha256sum tools/help/help.css phone/src/main/assets/help/help.css`; expected equal | actual both
   `6a09a241..a698`.
3. PASS - `pwsh -NoProfile -File tools/help/assert-help-map.ps1`, exit 0, 0.73 s; expected PASS | actual
   `assert-help-map: PASS (30 surfaces, 29 rows, 1 baseline, 33 pages, 8 picture sets checked ..)`.
4. PASS - static coverage: a grep of the reference layer (lines before the local-layer header) for
   `(margin|padding|border|inset|scroll-margin)-(inline|block)`, `clamp(`, `min(`, `anywhere`, `color-mix` and
   flex/grid `gap:` listed 33 lines; each has a physical or guarded twin in the local layer except
   `scroll-margin-block-start` (no replacement exists on WebView 61; section 5). Braces of the local layer balance
   (53 open, 53 close). Every asymmetric declaration has a `[dir="rtl"]` twin.
5. PASS - `rg -n S0055 phone/src wear/src watchface/src exchange-core/src tools/help`, exit 1; expected no ticket id
   in code | actual none; the ticket is registered in `PLAN/probe-baseline.txt` (assets only, no executable path).
6. EXEMPT - shared contract boundary: `CONTEXT-HELP` is not amended; the local layer is additive and inert on a
   current WebView (section 3 item 1). The upstream proposal (section 6) is for the canon session and blocks nothing.
7. RELEASE - `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`: the release build that packages the new
   asset was not built between releases (no measured fast Gradle form, `CLAUDE.md` section 5a).

- [ ] MANUAL - API 27 emulator (Small_Phone, WebView 61, not updated): install the release build, open help on the
  catalog page and on the player page; expected side gutters, the "In this order" numbers in their own column left of
  the text, the gold start border and the spacing of the element markers, related links in one column.
- [ ] MANUAL - API 35 emulator: the same two pages, light and dark, look as before this change (compare with the
  S0035 walk screenshots); expected no visible difference.
