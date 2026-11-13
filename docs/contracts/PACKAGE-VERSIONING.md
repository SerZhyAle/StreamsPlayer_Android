# PACKAGE-VERSIONING - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `PACKAGE-VERSIONING` 0.6 |
| **Reviewed** | 2026-10-29, S0057 (earlier: 2026-10-19, S0013/S0026) |
| **Home** | shared contracts catalog, `package-versioning/` |
| **Role here** | Android package-family consumer |

Section 2 Android rules 1-8 apply to phone, Wear and watchface; store/noLegal share each family band. Convention-plugin unit tests verify one instant, mechanically derived fields, pins, sentinel and family partitions. No package, signing, store upload or update-over-prior-install proof is supplied by this sync. 0.6 (2026-10-29, read in S0057) registers the three Tourist Android families in the band table and changes no text of rules 1-8; the bands of this product's three families are as before, and the version sources under `build-logic/` are unchanged since the tests of 2026-10-23.
