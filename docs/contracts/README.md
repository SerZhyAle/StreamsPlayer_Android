# Contract pointers

Every shared contract this product produces, consumes or adopts has one pointer here: id, version, home,
role, and what this repository owes to stay conformant. The contract text itself lives only in the shared
contracts catalog, located by `CLAUDE.md`; nothing here restates it. Cite a contract by id and section, never by
link or path. Versions were read from the catalog registry on 2026-10-02 - re-read the registry, not this table,
before work at a boundary.

| Pointer | Contract | Version | Role | State |
| --- | --- | --- | --- | --- |
| [STREAM-BANK.md](STREAM-BANK.md) | `STREAM-BANK` | 2.2 | consumer | declared; implementation in S0003 |
| [LIVE-BROADCAST.md](LIVE-BROADCAST.md) | `LIVE-BROADCAST` | 0.13 | producer (listener in the second wave) | declared; implementation in S0006, S0007 |
| [USER-PLAYLIST.md](USER-PLAYLIST.md) | `USER-PLAYLIST` | 0.12 | consumer (m3u import) | declared; S0003 or S0009 |
| [MEDIA-CLASSIFICATION.md](MEDIA-CLASSIFICATION.md) | `MEDIA-CLASSIFICATION` | 0.10 | consumer | declared; S0003 |
| [PACKAGE-VERSIONING.md](PACKAGE-VERSIONING.md) | `PACKAGE-VERSIONING` | 0.2 | consumer (Android profile) | families not registered yet; S0002 |
| [INPUT-PARITY.md](INPUT-PARITY.md) | `INPUT-PARITY` | 0.3 | adopter | declared; S0004 onward |
| [DIAGNOSTIC-REPORT.md](DIAGNOSTIC-REPORT.md) | `DIAGNOSTIC-REPORT` | 0.12 | producer, conditional | only if a diagnostic bundle is exported |
| [INSTALL-TRUST.md](INSTALL-TRUST.md) | `INSTALL-TRUST` | 1.1 | adopter, conditional | only if a sideload page ships (decision 4.3) |
| [RULE-ADOPTION.md](RULE-ADOPTION.md) | `REPO-STAMP`, `HARNESS-PROFILE`, `REPO-LAYOUT`, `RULE-DELIVERY` | 0.11 / 0.10 / 0.10 / 0.11 | adopter | adopted in S0001 |
| [PRODUCT-WEB-PAGES.md](PRODUCT-WEB-PAGES.md) | `PAGE-CONTENT`, `PAGE-STYLE`, `SITE-FAMILY-MAP` | 1.2 / 1.2 / 1.2 | adopter, conditional | only if the owner decides on a site (decision 4.7) |
