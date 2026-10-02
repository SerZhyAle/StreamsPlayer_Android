# PACKAGE-VERSIONING - pointer

| Field | Value |
| --- | --- |
| **Id** | `PACKAGE-VERSIONING` |
| **Version** | 0.2, draft (registry, 2026-10-02) |
| **Home** | shared contracts catalog, `package-versioning/` |
| **Owner** | FastMediaSorter Android |
| **Role here** | consumer, Android profile - versionName and versionCode of all three packages |

What this repository owes:

- Derive versionName and versionCode as the contract says, mechanically, from the product's registered families.
- Before the first package ships: three package families added to the band table as a MINOR amendment written
  in the catalog first. Phone and watch share `com.sza.streamsplayer` (and the noLegal variant shares it too), so
  they need disjoint bands; the watch face (`com.sza.streamsplayer.watchface`) has its own family. The
  amendment is proposed through the canon session, never written from here (S0002).
- Not registered yet, by the owner's instruction of 2026-10-02.
