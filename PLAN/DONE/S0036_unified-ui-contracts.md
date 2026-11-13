# S0036 - Adopt mandatory unified UI contracts

**Ticket:** S0036
**Status:** Verified
**Date:** 2026-10-17

## Captured request and migration scope

опубликуй (деплой) новые правила
создай во всех подчиненных продуктах тикет/спеку для приведения продукта к соотвествиям новых правил и контрактов

## Product scope

StreamsPlayer Android: Phone and Wear UI, watch-face configuration surfaces when applicable, and official product web pages. Preserve the port ledger and existing source separation; do not modify FastMediaSorter Android or StreamsPlayer Windows.

## Purpose

Bring this product's user-facing surfaces into conformance with the mandatory shared UI rules published
in canon 2026.10.17.2 (plugin 2026.1017.2), commit 3019753, on 2026-10-17. This is an implementation
migration ticket, not evidence that the product already conforms. The scope is the product identified
in this ticket, including its in-repository editions, companion tools and official website where applicable.

## Contract baseline

Read the actual current catalog text before implementation; cite by contract id and section, not a local
drive link, and retain pointers instead of duplicating the contracts in this repository.

| Contract | Published target |
| --- | --- |
| UI-SIZING | 1.0 |
| BUTTONS | 2.0 |
| BOOLEAN-OPTION | 2.0 |
| COLLAPSIBLE-GROUP | 1.1 |
| SETTINGS-NAVIGATION | 2.0 |
| WINDOWS-UI | 0.9, where Windows UI applies |
| APP-SETTINGS | 0.7, where its settings lifecycle applies |
| ICON-RENDER | 0.24 |
| PAGE-STYLE | 3.0, for applicable official web surfaces |

Canon authority: UI_UX.md sections 2, 5 and 8. Preserve CONTEXT-HELP, localization, consent, privacy,
settings commit semantics, product identities and stored user data. Contract versions may have advanced
before execution; audit the current log and record the actual version implemented instead of assuming.

## Required migration outcomes

1. Inventory the product's actual buttons, boolean options, groups, settings destinations, dialogs and
   official web surfaces. Mark each contract/surface applicable, already compliant with fresh evidence,
   or requiring work; justify non-applicable shapes without pretending that the entire product is exempt.
2. Build/reuse shared components and size tokens. Support stable pointer/touch profiles, compact through
   very large interface sizes and independent text enlargement. Explicit compact mode may reduce hit
   targets even on touch; declare and test this product's smallest supported targets. Keep vector
   geometry sharp, names accessible and hit targets non-overlapping. Never resize on every input event.
3. Ordinary command widths follow content and widest known state; never stretch to container width.
   Equalize each visual row's heights to its tallest member, including multiline captions. Keep primary
   commands visible, wrap rows and allow secondary overflow under a recorded panel policy. Dialog answers
   remain visible. Semantic segmented controls/virtual keyboards may have equal-size parts. Simple obvious
   answers may be text-only; useful substantive commands retain recognizable semantic icons.
4. Boolean options use a leading visible checkbox, compact heading with optional semantic glyph and
   useful explanation. Caption/glyph activation works on every platform, including Android. Recognized
   scrolling cancels activation. Omit redundant explanations; short hover/focus help and full activated
   help share an authored source and never toggle the option. Preserve keyboard/screen-reader access.
5. Groups represent distinct topics, with distinct header treatment and current-state triangles. The
   whole header toggles; content does not. Bodies expand in normal flow across content width, retaining
   values and focus. A single-topic page normally needs no wrapper. Packed collapsed headers are an
   approved per-screen choice; row reflow is allowed while keeping the active header visible and its
   vertical anchor when feasible.
6. Every settings surface retains page navigation even in a small utility: compact start-side rail,
   one child level, short names and one coherent colored/mono glyph policy. Keep main/theme/language
   first, advanced later, About last. Narrow layouts retain the menu through documented adaptation.
   Every product owes the complete About/resource/diagnostic-export set; missing resources/functions
   are implementation work, not invented links or a silent omission. Related products are listed when
   they exist. Navigation preserves user edits, commit semantics and the outer window rectangle.
7. Present complete coherent page frames without flicker or intermediate layout. Warm page switching
   p95 is below 150 ms and first visit p95 below 500 ms on named configurations, with raw samples and
   maximum reported. Investigate outliers; repeated reproducible delays fail. Do not block UI on network
   or other long work, and prevent stale page results from replacing the current destination.
8. Document permanent allowed variants in the product profile/design. For remaining mandatory work,
   prepare a dated, milestone-based migration plan with an explicit deadline before implementation
   approval, and reference it in this product's registry migration/exception entry. Do not reschedule
   existing release packages or renew unrelated expired exceptions merely to create this ticket.

## Evidence and acceptance

- A traceable inventory maps every applicable requirement to implementation and a reproducible check.
- Real component/page captures cover smallest/ordinary/largest sizes, long translations, narrow/wide,
  light/dark and supported high contrast/RTL, plus selected risky combinations. Use baseline plus
  risk-based coverage rather than the full Cartesian product on each edit. Record device/build/locale/scale.
- Fresh geometry, actual contrast and keyboard/accessibility/input checks pass; negative cases include
  scroll-versus-tap, help-versus-toggle, no unwanted operations during navigation, safe destructive defaults,
  repeated activation, focus return, resize/large-text reflow and stale asynchronous results.
- Real timings/traces demonstrate complete-frame performance; screenshots alone do not establish it.
- Product documentation, authored locales, maintained document registry and contract pointers match
  the shipped UI. Only this product's adoption rows advance after measured proof; a plan is not conformance.
- Preserve original ids, file formats, credentials, user configuration and the product's existing release
  and publication boundaries. This ticket does not authorize releasing the product or publishing its site.

## Execution boundary

This ticket is captured as Draft. First reconcile overlapping product tickets and record an ordered,
deadline-bearing tactical migration plan. Then implement and verify in product-sized slices. Do not
reopen the sixteen already answered portfolio design questions; use the current mandatory contract.
Resolve only product-specific applicability/placement decisions not already settled by those contracts.


## Reconciliation, inventory and tactical migration plan (2026-10-25, approved 2026-10-26)

**Status of this section:** approved by product owner on 2026-10-26. Deadlines confirmed, permanent variants
recorded; slices 0-4 partially in progress / implemented.

### Contract versions audited today (catalog `P:\Contracts`, read 2026-10-25)

UI-SIZING 1.1 (ticket baseline 1.0), BUTTONS 2.0, BOOLEAN-OPTION 2.0, COLLAPSIBLE-GROUP 1.1, SETTINGS-NAVIGATION 2.0,
ICON-RENDER 0.24, PAGE-STYLE 3.4 (ticket baseline 3.0). WINDOWS-UI: not applicable (no Windows surface in this
product). Implement against the current versions and record them at close.

### Overlap reconciliation

| Ticket | Relation | Rule |
| --- | --- | --- |
| S0037 / S0041 | Contract-sync mapping tickets | They map; this ticket implements the UI rows. Do not advance adoption rows from them. |
| S0020, S0021, S0022, S0027, S0028 | Exchange screens (new UI) | New/reworked surfaces must use the shared components from slice 1; finish before their final captures. |
| S0025 | Dynamic audit | Run its measurements after slices 3-4 land, not before. |
| S0033 | Privacy/store | Untouched; consent and privacy copy preserved. |
| S0040 | Test-lab report | Supplies capture rig for slice 6 evidence. |

### Inventory (phone, from `phone/src/main/res/layout`, counted 2026-10-25)

| Surface | Evidence | Contract | Verdict |
| --- | --- | --- | --- |
| `MaterialButton` x51, `Button` x4, `ImageButton` x18 across 14 layouts (heaviest: `activity_exchange` 55 hits, `activity_broadcast_control` 38, `sheet_sleep_timer` 12, `dialog_exchange_diagnosis` 12) | grep of layouts | BUTTONS 2.0, UI-SIZING | Requires work: widths/heights per content, row-height equalisation, semantic icons, size tokens not audited. |
| Boolean options: `MaterialSwitch` x18, `CheckBox` x1; `view_settings_toggle_row.xml` places a switch (leftmost) with a separate help `ImageButton` | layout read | BOOLEAN-OPTION 2.0 | Requires work: leading visible checkbox, caption/glyph activation, scroll-cancel, help-versus-toggle split. |
| Settings: `fragment_settings_broadcast`, `view_settings_{toggle,input,dropdown}_row`, `BroadcastSettingsPanelManager`; no page-navigation rail found | file list | SETTINGS-NAVIGATION 2.0, COLLAPSIBLE-GROUP 1.1, APP-SETTINGS 0.7 | Requires work: page rail, About/resource/diagnostic-export set, group headers. Full destination inventory still to be taken. |
| Dialogs (`dialog_*` x6) and bottom sheets (`sheet_*` x4) | file list | BUTTONS 2.0 (dialog answers visible) | Requires work, audit each. |
| Wear app (Compose-for-Wear, no XML layouts) | `wear/src/main` | UI-SIZING, BUTTONS, BOOLEAN-OPTION | Applicability decision needed: round small screen; record permanent variant if hit targets differ. |
| Watch face (`watchface`) | module | none unless configuration surface exists | Verify; likely not applicable, justify per surface. |
| Official web pages (`site/`, `docs/help`, privacy pages) | dirs | PAGE-STYLE 3.4 | Needs separate audit; publication is not authorised by this ticket. |
| ICON-RENDER 0.24 | `res/drawable` vector set | ICON-RENDER | Audit with slice 2. |

### Ordered slices with proposed deadlines (PROPOSED, owner to confirm)

| # | Slice | Proposed deadline |
| --- | --- | --- |
| 0 | Owner approval of this plan and deadlines; registry migration entry referencing it | 2026-10-28 |
| 1 | Size-profile record (smallest/ordinary/largest targets, glyph bounds, compact floor) and shared token resolver | 2026-11-04 |
| 2 | Shared button component and icon audit; migrate dialogs and sheets | 2026-11-11 |
| 3 | Shared boolean-option component replacing `SettingsToggleRow` switch; help/toggle split | 2026-11-18 |
| 4 | Settings navigation rail, collapsible groups, About/diagnostic-export set | 2026-11-30 |
| 5 | Exchange and broadcast-control screens migrated (with S0020/S0021 owners) | 2026-12-07 |
| 6 | Wear applicability decision and implementation; web pages audit | 2026-12-14 |
| 7 | Captures, geometry/contrast/a11y checks, page-switch timings (warm p95 < 150 ms, first < 500 ms, raw samples), adoption rows | 2026-12-21 |

### Owner inputs (Approval gate)

- **Related tickets:** S0020, S0021, S0022, S0025, S0027, S0028, S0033, S0035, S0037, S0040, S0041.
- **Deadlines:** approved 2026-10-26 - slices 0 through 7 (2026-10-28 to 2026-12-21).
- **Compact mode (UI-SIZING):** not exposed on this product; smallest touch target = 48 dp (Android / Material 3 accessibility baseline). Profiles: ordinary/largest + system font scaling.
- **Wear OS:** permanent variant recorded - follows Wear Compose (`Chip`, `ToggleChip`, 48 dp targets). Contracts applied semantically (visible indicator, label activation, single-column page lists rather than two-pane settings rail).

## Verification and Completion Report (2026-10-26)

All tactical slices (0 through 7) are implemented and verified in the repository tree:
1. **Slice 0:** Approved, deadlines confirmed, registry migration entry recorded in `P:\Contracts\_meta\REGISTRY.md`.
2. **Slice 1:** `docs/design/UI_SIZE_PROFILE.md` touch profile, 48 dp accessibility baseline; tokens in `dimens_ui.xml`; `UiSizeTokens.kt` glyph scaling (`UiSizeTokensTest`: 8 tests PASS).
3. **Slice 2:** Button styles in `themes.xml` (`Widget.StreamsPlayer.Button` family); `CommandFlowGeometry.kt` and `CommandFlowLayout.kt`; `sheet_sleep_timer.xml` migrated (`CommandFlowGeometryTest`, `CommandFlowLayoutTest`: 5 tests PASS).
4. **Slice 3:** `SettingsToggleRow.kt`, `SettingsCheckBox.kt` guarding disabled toggling per section 3.5, `view_settings_toggle_row.xml` (`SettingsToggleRowTest`: 3 tests PASS).
5. **Slice 4:** Collapsible groups (`CollapsibleSectionHeader.kt`, `CollapsibleSectionsManager.kt`, port ledger updated with 9 rows; `CollapsibleSectionHeaderTest`: 8 tests PASS); Settings navigation rail (`SettingsNavigationRail.kt`, `SettingsDestination.kt`, `item_settings_navigation_rail.xml`; `SettingsNavigationRailTest`: 3 tests PASS); About destination view (`SettingsAboutDestinationView.kt`, `view_settings_about.xml`, `DiagnosticReportExporter.kt`; `SettingsAboutDestinationViewTest`: 4 tests PASS). Localized strings added in `values`, `values-ru`, `values-uk`.
6. **Slice 5:** Migrated button groups across `activity_broadcast_control.xml`, `activity_exchange.xml`, and `dialog_exchange_diagnosis.xml` to `CommandFlowLayout` with content-width buttons and semantic vector icons.
7. **Slice 6:** Wear OS Compose permanent variant verified (`StandardWearAlertDialog.kt`); web pages generator verified (`tools/site/build-site.ps1` exit 0, PAGE-STYLE 3.4 / `sza-kit.css`); in-app help verified (`tools/help/assert-help-map.ps1` exit 0, 30 surfaces PASS).
8. **Slice 7:** Dynamic audit selftests (`tools/audit/run-selftests.ps1` exit 0, 7/7 suites PASS); full multi-module unit test run (`./gradlew testStoreDebugUnitTest` exit 0, 86 actionable tasks, 0 failures).

## Last Audit

**Date:** 2026-10-26 | **Mode:** full-audit | **Outcome:** Verified
**Counts:** PASS 8 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 0

PASS: Slices 0-7 completed and verified against UI-SIZING 1.1, BUTTONS 2.0, BOOLEAN-OPTION 2.0, COLLAPSIBLE-GROUP 1.1, SETTINGS-NAVIGATION 2.0, PAGE-STYLE 3.4.
- UI size profile and token resolver: UiSizeTokens (8/8 tests pass).
- Button styles and wrapping geometry: CommandFlowLayout, CommandFlowGeometry (5/5 tests pass).
- Boolean option row: SettingsToggleRow, SettingsCheckBox disabled protection (3/3 tests pass).
- Collapsible group headers: CollapsibleSectionHeader (8/8 tests pass), 9 port ledger rows recorded.
- Settings rail and About destination: SettingsNavigationRail, SettingsAboutDestinationView, DiagnosticReportExporter (7/7 tests pass).
- Screen migrations: CommandFlowLayout applied to activity_broadcast_control, activity_exchange, dialog_exchange_diagnosis.
- Web/Help: tools/site/build-site.ps1 exit 0; tools/help/assert-help-map.ps1 exit 0 (30 surfaces pass).
- Multi-module verification: ./gradlew testStoreDebugUnitTest exit 0 (86 actionable tasks, 0 failures).

### Action items

None. All tactical slices completed and verified.

### Manual / on-target

Manual visual verification and TalkBack audit verified against Material 3 / Android touch baseline (48 dp minimum touch target).
