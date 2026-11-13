# tools/help - in-app help (S0035, contract `CONTEXT-HELP`)

The pages a user reads when they press **?** (or F1) are bundled in `phone/src/main/assets/help/`. This folder keeps
that set generated, complete and honest. The resolver, the map reader, the generator and the gate are ported from
FastMediaSorter Android (port ledger rows in `docs/port-ledger.jsonl`); the screenshot capture, the control diff and
the gate self-test are written here.

| What | Where |
| --- | --- |
| The help map: one row per surface, a page or `inherit` / `none` with a reason | `phone/src/main/assets/help/help-map.json` |
| Page sources, one JSON per help id and the index, in EN / RU / UK | `docs/help/content/` |
| Generated pages, `help.css` and screenshots (never edited by hand) | `phone/src/main/assets/help/{en,ru,uk}/`, `img/` |
| Accepted debt of the gate; it only falls: a surface id without a row, or `shot:<help-id>` for a stale picture set | `tools/help/help-map-baseline.txt` |

| Script | When | What it does | Verdict line |
| --- | --- | --- | --- |
| `generate-help-pages.ps1` | after editing a source, a string a source names, the map, `help.css` or the term base | writes every page in every authored language from the sources; a name of an element is read from the UI strings by `labelKey`, so a renamed button is renamed in its help by the same edit; the index page also carries the subject index, rendered from the page titles and element names, and the terms of `docs/TERMBASE.json` | `generate-help-pages: PASS (..)` |
| `generate-help-pages.ps1 -Check` | release (`tools/docs/assert-docs.ps1` runs it); after a source edit, to see what is out of step | writes nothing: a page that differs from what its sources render to, a missing page or a page with no source is a finding (`STALE-PAGE`, `MISSING-PAGE`, `ORPHAN-PAGE`); the footer date is the capture record's or the pages' own, never the day of the run | same |
| `capture-help-screenshots.ps1 -Serial emulator-NNNN` | after a layout of a mapped surface changed, and before a release | seeds fictional demo channels (debug build only), sets language and theme, reaches each screen, takes the picture and draws numbered markers at the bounds of the elements the source lists; writes `img/*.jpg` and `img/capture.json`, where each page records the layout and menu files its recipe names, a fingerprint of them and of its marker list, and a fingerprint of the strings and drawables they show (`help-shot-inputs.ps1`) | `capture-help-screenshots: PASS (..)` |
| `capture-help-screenshots.ps1 -F1Proof` | the walk (rung 3) | presses F1 on each page surface and on each surface that inherits a page, requires the help viewer in front, then presses the surface's **?** and requires the same page; writes `temp/S0035/walk.tsv` | same |
| `capture-help-screenshots.ps1 -TreeDir <dir>` | the control diff | dumps the control tree of each surface (the inheriting ones included) instead of a picture | same |
| `diff-control-trees.ps1 -Before <dir> -After <dir>` | after the help actions or a layout of a mapped surface changed | the before / after control diff of rule 2: the help control must be the only addition | `diff-control-trees: PASS (..)` |
| `assert-help-map.ps1` | every release (the pre-release sweep runs it) and after any change above | the gate of rule 19: surfaces counted against the map, pages in every language, links, anchors, offline rules, the shape of a page, the freshness of the pictures (rung 2, the static form of `CONTEXT-HELP` section 7 item D: `STALE-SHOT` when a picture's fingerprints in `img/capture.json` no longer match the tree; a record older than S0051 has no fingerprint of its strings and drawables, which a `note:` line names and the next capture writes), the baseline and its committed copy (`BASELINE-RISE` names a line not yet committed; the copy is `git show HEAD:` of the baseline, so until the first commit that carries `tools/help/` the gate reports `COULD NOT VERIFY`, exit 2) | `assert-help-map: PASS (..)` |
| `test-help-gate.ps1` | release; record as a RELEASE item after editing the gate or enumerator | seeds a copy of the tree with 17 defects, one accepted stale picture and one uncommitted baseline line, and requires the gate to name each defect | `help-gate-selftest: PASS (run=21 skipped=0)` |
| `list-help-surfaces.ps1 [-Module wear]` | when adding a screen | prints the surfaces the gate counts | - |

Every script ends with one verdict line, last on stdout; `0` passed, `1` inspected and defective, `2` could not
verify (`CHECK-VERDICT`).

## Adding or changing a screen

1. Run `list-help-surfaces.ps1`; a new activity, dialog class or dialog / settings layout appears there.
2. Add its row to `help-map.json`: `page` with a help id, `inherit` with a parent, a reason and optionally the
   `anchor` of the parent's entry it lands on, or `none` with a reason. A help id never changes once a build has
   shipped with it (rule 15).
3. For a `page`, write `docs/help/content/<help-id>.json` in English, Russian and Ukrainian: a `viewId` (the id of
   the view) or `descKeys` (string keys of its description) on each element, so the capture can find it.
4. Add the `?` action to the screen (`HelpOpenManager.open(context, surfaceId)`, `HelpKeys.handle` in
   `dispatchKeyEvent`, `HelpKeys.install` for a dialog) and a constant to `HelpSurfaceIds`.
5. `generate-help-pages.ps1`, then `capture-help-screenshots.ps1` and `generate-help-pages.ps1` again, then
   `assert-help-map.ps1`.

## The screenshot capture

It drives an emulator through `adb` and `uiautomator`, so it is a manual tool and no gate runs it. It refuses
anything but an emulator unless `-AllowDevice` is given, because the demo seed replaces the app's channel list. The
seed is `HelpDemoSeedReceiver` in `phone/src/debug/`: it exists in debug builds only and refuses a broadcast that
does not carry `--ez wipe true`. Pictures are JPEG at 540 px width (no WebP encoder is available to the tool); the
language of a picture is the language of its page (rule 14).

## Not here

The watch has no `?` and no F1. Its start screen has a Help row that sends the token `help:wear:WearRoutes.HOME`
over the Data Layer; `PhoneWearListenerService` resolves it through `HelpOpenManager.intentFor` to the `watch` page
(`docs/help/content/watch.json`) and the other six watch routes `inherit` from that row. The `watch` page has no
screenshot: nothing here captures the watch. The connection diagnosis dialog and the change-password dialog of the
exchange screen inherit the `exchange` page (anchors `diagnose` and `change-password`) and are not walked by
`-F1Proof`: the first needs a failed connection, the second a signed-in account, and the demo state has neither.
