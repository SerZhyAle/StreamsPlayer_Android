# DIAGNOSTIC-REPORT - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `DIAGNOSTIC-REPORT` 0.15 |
| **Reviewed** | 2026-10-29, S0057 (earlier: 2026-10-19, S0013/S0026) |
| **Home** | shared contracts catalog, `diagnostic-report/` |
| **Role here** | conditional diagnostic-export producer |

Sections 2/8/10 and the 2026-10-17 amendment bind a diagnostic export, including redaction before truncation and whole omission of oversized untrusted input. No exporter or adoption row is claimed here. S0036/S0037 must establish that boundary when implementing the mandatory About/diagnostic surface. 0.15 (2026-10-29, read in S0057) opens the omission marker's `reason` set to readers that ignore an unknown value (`reason=archive_cap` names a log left out at the archive budget), lets a placeholder without the profile name satisfy rule 3, and writes an uncountable family `unavailable`; no byte of a conforming bundle changes, and this product has no exporter to move.
