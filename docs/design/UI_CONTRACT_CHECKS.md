# UI contract checks - clause, surface, reproducible check

Every clause of the five unified UI contracts that binds a phone or Wear surface, the surface it binds, and the check
that proves it (S0049). Versions read: `UI-SIZING` 1.1, `BUTTONS` 2.0, `BOOLEAN-OPTION` 2.0, `COLLAPSIBLE-GROUP` 1.1,
`SETTINGS-NAVIGATION` 2.0. Decisions are in `docs/design/UI_SIZE_PROFILE.md`.

A component unit test alone proves no row. The check column names a procedure of the next section. **None of them has
run yet**: they need a release build on a device, so nothing here is measured conformance, and the registry rows do not
advance on this file. A result is recorded in this file, with its device, build, locale and scale, on the day it is run.

## Procedures

Run on a release build of `store`, installed on a named device. Record: device, Android version, build (`versionName`),
locale, font scale, density, contract version, date.

| Id | Procedure |
| --- | --- |
| `CAP` | Captures of the surface at: font scale 0.85 / 1.0 / 2.0 (`adb shell settings put system font_scale <n>`); longest strings (`adb shell cmd locale set-app-locales com.sza.streamsplayer --locales uk`, then `ru`); light / dark (the General page's theme row, and `adb shell cmd uimode night yes|no` for system); high contrast (`adb shell settings put secure high_text_contrast_enabled 1`); RTL (`--locales ar`); narrow / wide (`adb shell wm size 720x1280`, `wm size 1600x2560`). `adb exec-out screencap -p` from the real screen, never edited. |
| `GEO` | `adb shell uiautomator dump` of each capture's screen; from the bounds: every target at least 48 dp each way, no target overlaps another, no caption clipped, command widths differ by content (no stretch), rows of one height, gaps equal to the tokens. |
| `CON` | From the captures: text to its own face at least 4.5:1, a meaningful glyph, check mark and marker at least 3:1, on normal, pressed and selected faces in light and dark. Report the measured pairs. |
| `A11Y` | TalkBack walk of the surface, hardware-keyboard walk (Tab, arrows, Enter, Space, F1): every control named, state spoken, focus visible, focus returns to the invoker when a dialog or menu closes. |
| `NEG` | Negative cases: a scroll starting on a row, a header or a button activates nothing; the explanation and the help of an option do not toggle it; repeated activation acts once; large-text reflow keeps everything reachable; a stale result cannot replace the current page after rapid switching. |
| `TIME` | Page timing, section 5.4: read the trace sections `settings.page.<id>.first` and `.warm` with Perfetto (`atrace` category `app`, the release manifest is profileable from the shell). At least 20 warm visits and 20 first visits (a first visit is a fresh process each) per destination, on the reference device and on a minimum-supported configuration. Nearest-rank p95 from all samples, plus the maximum and the raw samples: warm below 150 ms, first below 500 ms. |
| `GROW` | Repeated switching between all pages for 2 minutes with Perfetto recording: no growth in layout work, no partial frame. `adb shell dumpsys gfxinfo com.sza.streamsplayer framestats` for frame evidence. |

## SETTINGS-NAVIGATION 2.0

| Clause | Surface | Check |
| --- | --- | --- |
| 2.1 rail at reading start, page at reading end, RTL mirrored | Settings, wide | `CAP` wide, RTL; `GEO` |
| 2.2 short names, stable order, whole captions in every locale | Settings rail | `CAP` longest strings, 2.0 font scale; `GEO` |
| 2.3 one glyph treatment (monochrome) | Settings rail | `CAP` light, dark, high contrast; `CON` |
| 2.4 child level | none: recorded in the profile | - |
| 2.5 48 dp rows, 20 dp glyphs, 8 dp gap, width from names | Settings rail | `GEO` |
| 2.6 narrow layout: dedicated navigation view, return restores the selection | Settings, narrow | `CAP` narrow; `A11Y` Up and Back |
| 2.7 independent scroll, non-color selection cue, selected state, keyboard | Settings rail | `CAP`; `A11Y` |
| 3.1 first page: theme, language, essentials; live, no restart, endonyms, follows system, high contrast | General | `CAP` theme and language changed live; `A11Y` |
| 3.2 one setting one editor, reachable | General, Broadcast, Broadcast Control | static: `rowEnableBroadcasting` only in `page_settings_general`; no panel include in Broadcast Control. The three settings with no editor are a named gap (profile section 5) |
| 3.3 complete About set, nothing placeholder | About | `CAP`; each link opened by hand on the release build |
| 3.4 related products, opening About contacts nothing | About | `CAP`; network log while opening About shows no request |
| 4.1 shared page shell | all three pages | `CAP`; `GEO` title and insets equal |
| 4.2 related controls follow the other contracts, no redundant wrapper | all three pages | `CAP` |
| 4.3 no resize, no commit, no operation on navigation; last page, viewport, fallback | all three pages | `NEG`; reopen the screen on a wide and on a narrow window, rotate, change language: page and viewport restored; the very first opening on a narrow window starts at the list |
| 4.4 ordinary page change does not jump focus | all three pages | `A11Y` |
| 5.1 one coherent frame, no partial page | all three pages | `GROW`; `TIME` frames |
| 5.2 no blocking on navigation | all three pages | `TIME`; StrictMode log clean in a debug build |
| 5.3 rapid navigation supersedes old work | Settings | `NEG` rapid switching |
| 5.4 budgets, samples, maximum | all three pages | `TIME` |
| 5.5 locales, large text, repeated switching | Settings | `CAP`; `GROW` |
| 6 captures of main, About, narrow, wide, light, dark, high contrast, long, RTL | Settings | `CAP` |
| Wear variant: page lists, no rail | Wear start screen | `CAP` on a round Wear device, `A11Y` rotary and touch |

## COLLAPSIBLE-GROUP 1.1

| Clause | Surface | Check |
| --- | --- | --- |
| 2.1-2.3 one topic per group, no redundant wrapper, independent groups, recorded first-use state | Exchange server; single-topic screens | `CAP`; profile section 6 |
| 3.1 header distinct from body in every theme | Exchange server | `CAP` light, dark, high contrast; `CON` |
| 3.2 filled triangle, state not action, RTL | Exchange server | `CAP` collapsed, expanded, RTL |
| 3.3 whole header toggles once, body does not | Exchange server | `NEG`: tap triangle, caption, empty space, body |
| 3.4 collapsed body has no space, focus or accessibility; captions wrap | Exchange server | `A11Y`; `CAP` 2.0 font scale |
| 3.5 full content width | Exchange server | `GEO` |
| 3.6 tokens | Exchange server | `GEO`; `CON` |
| 4.1 in-flow expansion, values kept | Exchange server | fill the sign-in form, fold and unfold: values intact |
| 4.2 header stays the anchor; delivered form reveals its group | Exchange server | `GEO` before and after; scan a QR with the group collapsed |
| 4.3-4.4 packing | not enabled: profile section 6 | - |
| 4.5 semantics, keyboard, focus before hiding | Exchange server | `A11Y` |
| 4.6 drag cancels activation, reduced motion | Exchange server | `NEG`; animator scale 0 |
| 4.7 state remembered, restore is no edit | Exchange server | reopen the screen, restart the process |
| 5 examples | Exchange server, Settings pages | `CAP` |
| 6 presentation only, stored values and defaults kept | Exchange server | static: the groups add `exchange__*` keys of their own and rename no existing preference |

## BUTTONS 2.0

| Clause | Surface | Check |
| --- | --- | --- |
| 2.1 command id, name, glyph policy | every command row | profile section 7; `CAP` |
| 2.2 behavior and rank, one primary per task surface | Broadcast Control, Exchange server, QR scanner, About | `CAP` |
| 2.3 markers: chevron, external, disclosure | Broadcast Control, Settings, About, Exchange server | `CAP` |
| 2.4 captions, icon-only only where recorded, tooltip and name | Player controls, list rows, corner help | `A11Y`; profile section 7 |
| 2.5 link semantics, toggle state | About, Exchange server, Broadcast Control | `A11Y`: role spoken as a link |
| 2.6 surface help | every mapped surface | `tools/help/capture-help-screenshots.ps1 -F1Proof` |
| 3 touch tokens, rectangular, content width, whole wrap, equal row height, roles, contrast | every command row | `GEO`; `CON`; `CAP` 2.0 font scale, narrow |
| 4.1 order, destructive last and apart | Exchange server account card, Broadcast Control | `CAP`; compare order across rotation |
| 4.2-4.3 flow, no full width, last line at start | every command row | `GEO`; static: no button at full width or by weight in a layout |
| 4.4-4.5 dialog answers, safe default | Sleep timer, exchange dialogs, system-built alerts | `A11Y`; `NEG` Escape and dismissal |
| 4.6 focus return | menus and dialogs of the catalog and exchange screens | `A11Y` |
| 4.7 shortcuts bind commands, leave editors and system combinations alone | every surface that takes keys | `A11Y` with a hardware keyboard, typing in a text field included; `tools/help/capture-help-screenshots.ps1 -F1Proof` for F1 |
| 4.8 busy state, duplicate invocation | Exchange sign-in, Start broadcast | `NEG` repeated activation |
| 5, 8 showcase and size range | Settings About page, Exchange server, Broadcast Control as the real showcase | `CAP` at all scales |
| 6 lists and options are not reclassified as buttons | Settings rail, dropdown and toggle rows | static: they keep their own components; `A11Y`: role spoken |
| Wear variant | Wear screens | `CAP` on a round Wear device |

## BOOLEAN-OPTION 2.0

| Clause | Surface | Check |
| --- | --- | --- |
| 2 anatomy, geometry | Broadcast page, General, Exchange server, Filter & Sort | `GEO`; `CAP` |
| 3.1 checkbox, caption, glyph toggle once; explanation and help do not | same | `NEG` |
| 3.2 scroll cancels activation | same | `NEG` |
| 3.3-3.4 one named checkbox, keyboard | same | `A11Y` |
| 3.5 disabled state, contrast, live theme | Broadcast page rows | `CAP`; `CON` |
| 4 local help where present | the rows that carry help | `A11Y`; focus return |
| 5 showcase states | same | `CAP` long text, narrow, large text |
| 6 stored values, defaults and commit model kept | same | static: the migrated rows read and write the same fields as before; `NEG`: a value set before the update is still set after it |
| Wear variant | Wear option rows | `CAP` on a round Wear device |

## UI-SIZING 1.1

| Clause | Surface | Check |
| --- | --- | --- |
| 2.1 stable touch profile | every phone surface | static: no profile switching in the product; `A11Y` with a hardware keyboard attached |
| 2.2 one baseline and range | covered components | static: tokens in `dimens_ui.xml`; `GEO` |
| 2.3 text enlargement independent, glyph bounds | covered components | `GEO` at font scale 2.0 against the recorded bounds |
| 2.4 48 dp floor, no compact mode | covered components | `GEO` |
| 2.5 vector drawing, hit area and focus agree, reflow | covered components | `CAP` 2.0 font scale, narrow |
| 3 approved variants recorded, migration dated | every phone and Wear surface | static: profile sections 2 and 4; the registry's unified-UI migration entry, until 2026-12-21 |
| 4 measured baseline, smallest, largest | covered components | `GEO`; `CAP` |
| 5 remeasure at final width after a scale change, no double scaling, no target moved under an active interaction | covered components | `GEO` before and after changing the font scale while a screen is open; `NEG` |
