# Contract pointers

Current catalog headers reviewed 2026-10-29 in S0057 (earlier rounds: 2026-10-27 S0037 and S0041; S0034/S0026/S0013). A pointer records the text reviewed,
not an implementation upgrade. Adoption versions and exceptions remain in the own registry rows.
The catalog is located only through CLAUDE.md. Cite contracts by id and section.

| Pointer | Current text reviewed | Role |
| --- | --- | --- |
| [RULE-ADOPTION.md](RULE-ADOPTION.md) | `REPO-STAMP` 0.13, `HARNESS-PROFILE` 0.14, `REPO-LAYOUT` 0.11, `RULE-DELIVERY` 0.12 | stamp/profile producer and layout/delivery adopter |
| [STREAM-BANK.md](STREAM-BANK.md) | `STREAM-BANK` 2.5 | consumer on phone and Wear; item P language half open (S0059) |
| [USER-PLAYLIST.md](USER-PLAYLIST.md) | `USER-PLAYLIST` 0.12 | Extended M3U8 consumer |
| [MEDIA-CLASSIFICATION.md](MEDIA-CLASSIFICATION.md) | `MEDIA-CLASSIFICATION` 0.13 | not applicable: no primary-category boundary |
| [PACKAGE-VERSIONING.md](PACKAGE-VERSIONING.md) | `PACKAGE-VERSIONING` 0.6 | Android package-family consumer |
| [ICONOGRAPHY.md](ICONOGRAPHY.md) | `ICON-SET` 0.37, `ICON-RENDER` 0.24, `ICON-EXTERNAL` 0.12 | icon vocabulary/render/external-image adopter |
| [INPUT-PARITY.md](INPUT-PARITY.md) | `INPUT-PARITY` 0.5 | phone/Wear adopter; sweep done, thirteen gaps (S0052) |
| [LIVE-BROADCAST.md](LIVE-BROADCAST.md) | `LIVE-BROADCAST` 1.6 | phone/watch producer and broadcast receiver; items S, T, U open (S0043, S0053) |
| [DEVICE-EXCHANGE.md](DEVICE-EXCHANGE.md) | `DEVICE-EXCHANGE` 1.11 | receiver and broadcaster; never either resource role; items DW, DX, DY open (S0043, S0053), item ED open (S0058) |
| [INSTALL-TRUST.md](INSTALL-TRUST.md) | `INSTALL-TRUST` 1.2 | conditional sideload-page adopter |
| [DIAGNOSTIC-REPORT.md](DIAGNOSTIC-REPORT.md) | `DIAGNOSTIC-REPORT` 0.15 | conditional diagnostic-export producer |
| [PRODUCT-WEB-PAGES.md](PRODUCT-WEB-PAGES.md) | `PAGE-CONTENT` 1.5, `PAGE-STYLE` 3.5, `SITE-FAMILY-MAP` 1.10 | planned official product-page adopter; footer defect S0061 |
| [TEST-LAB.md](TEST-LAB.md) | `TEST-LAB` 0.1 | reporter only (S0040) |
| [PORTAL-DOCS.md](PORTAL-DOCS.md) | `DOC-EXTERNAL-QUALITY` 0.13, `DOC-INTERNAL-QUALITY` 0.11, `SITE-STRUCTURE` 0.4 | portal contributor and linking program (S0030); the corpus gates are S0051, first sweep at the release |
| [UNIFIED-UI.md](UNIFIED-UI.md) | `UI-SIZING` 1.1, `BUTTONS` 2.0, `BOOLEAN-OPTION` 2.0, `COLLAPSIBLE-GROUP` 1.1, `SETTINGS-NAVIGATION` 2.0 | phone/Wear adopter, partial (S0036 built, S0049 finishes) |
| [CONTEXT-HELP.md](CONTEXT-HELP.md) | `CONTEXT-HELP` 0.2 | phone adopter, partial (S0035) |
| [AUTOMATED-CHECKS.md](AUTOMATED-CHECKS.md) | `CHECK-VERDICT` 0.14, `CHECK-BASELINE` 0.12 | consumer, for the help gate (S0035) and the documentation gates (S0051); gaps S0060 |
| [EXCHANGE-MARK.md](EXCHANGE-MARK.md) | `EXCHANGE-MARK` 0.4 (read 2026-11-08, S0039) | phone/watch consumer, partial (help rows S0035) |

No new adoption is inferred for conditional surfaces. S0037 inventoried the newly applicable contracts (`PLAN/S0037_contract-sync-20261017/audit.md`);
S0049 owns the remaining mandatory UI implementation and S0035 owns context help. Older reviewed/not-bound decisions
remain subject to those newer tickets, rather than overriding their mandatory scope.
