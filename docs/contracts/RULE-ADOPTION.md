# RULE-ADOPTION - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `REPO-STAMP` 0.13, `HARNESS-PROFILE` 0.14, `REPO-LAYOUT` 0.11, `RULE-DELIVERY` 0.12 |
| **Reviewed** | 2026-10-29, S0057 (earlier: 2026-10-27, S0041; S0034, S0013) |
| **Home** | shared contracts catalog, `rule-adoption/` |
| **Role here** | stamp/profile producer and layout/delivery adopter |

Sections 2-5 apply to the root stamp, root profile, repository layout and installed canon. REPO-STAMP 0.13 and HARNESS-PROFILE 0.13 (2026-10-23 amendment) add optional version carriers (`schemaVersion` in the stamp, `version` in the profile; absence, and the legacy profile `"1"`, mean wire 1.0) and generated reader vectors; no existing payload key becomes required, so this repository's stamp and profile stay valid unchanged. The carrier is written only by the adopt-canon run, never by hand, and the same run owns the digest re-sync (SZA-CANON03). The stamp and profile readers are the installed plugin's: this repository owns none and replays the catalog's reader vectors against it (S0041). This shape-1 repository keeps the unchanged dev-log default. Exceptions are recorded in the registry, not here. Section 7 real delivery is the canon's.

`HARNESS-PROFILE` 0.14 (2026-10-27, read in S0057) adds rules 8 to 10 of section 3: the agent fields of `runner.instances.<name>`, per-instance `promptTemplate` and `complexityModels`, and an optional top-level `portfolio` object. The wire stays 1.0 and every key is optional; `.sza-profile.json` declares none of them and stays valid unchanged. The shipped runner reads none of the new keys yet (canon plan S0015).
