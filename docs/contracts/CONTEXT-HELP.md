# CONTEXT-HELP - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `CONTEXT-HELP` 0.2 |
| **Reviewed** | 2026-11-08, S0051 (earlier: 2026-10-29, S0057; 2026-10-25, S0035) |
| **Home** | shared contracts catalog, `in-app-help/` |
| **Role here** | phone adopter (`partial`); the Wear app answers rule 4 only; the watch face is out of scope (section 1 of the contract) |

Rules 1-19 apply to every surface of the phone app. The owner's decisions of 2026-10-25 (S0035 section 3.3):
pages authored in EN, RU and UK with the other ten declared interface languages falling back to English (rule 13);
for the Wear app, one Help row that opens the phone's page through the existing "Open on phone" path with its routes
`inherit`-ing from it (item 2b; the page is `watch`, the request is the token `help:wear:WearRoutes.HOME`); the viewer is the app's own `WebView` on the bundled pages with scripting off (rule 7 and
rule 12); `APP-BEHAVIOUR` rules 4, 8, 9 and 11, as `CONTEXT-HELP` quotes them, bind this product for help.

The help map is `phone/src/main/assets/help/help-map.json`; the page sources are `docs/help/content/`; the gate is
`tools/help/assert-help-map.ps1`. Surfaces held back by an open feature ticket sit in
`tools/help/help-map-baseline.txt`, which only falls (`CHECK-BASELINE` rule 2). The resolver, map reader, generator
and gate are ported from FastMediaSorter Android (S0035 item 5), each with a row in `docs/port-ledger.jsonl`.

Conformance rung held: static (rung 1) for the phone surfaces mapped so far. Freshness (rung 2) and the walk
(rung 3) are carried by S0035 until the release that first ships the feature.

Section 7 of the contract (0.2, items A to J) was read on 2026-10-29 (S0057). It binds this product as follows: item A
(an `inherit` surface carries the button and answers F1) is how the dialogs are wired, for example `ExchangePasswordDialog`;
the gate run of 2026-10-29 passes (`assert-help-map: PASS`, 30 surfaces, 29 rows, 1 baseline entry) and the wiring of every
`inherit` row beyond that gate is not established here. Item B (`ask: "off"`) is used by no surface. Item E matches the
dialog title row with the help button at its trailing corner (`view_dialog_title_help`). Items F and G bind: the owner
decided on 2026-11-08 to publish the help as `help/{en,ru,uk}/` with the first release (S0045 D2), each index at the address
of item F. A published page carries `noindex`, stays out of the sitemap and is not blocked by robots;
`tools/site/build-site.ps1 -Check` holds that from the day the pages exist (S0045 T6 writes them). Item D (static rung 2 by input
hashes) is the form this product holds: `tools/help/assert-help-map.ps1` fails a picture set whose layouts, marker list,
strings or drawables moved after its capture; the strings and drawables bind from the next capture, which writes their
fingerprint (S0051, `docs/design/DOC_QUALITY_VARIANTS.md`).
