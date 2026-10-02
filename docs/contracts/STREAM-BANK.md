# STREAM-BANK - pointer

| Field | Value |
| --- | --- |
| **Id** | `STREAM-BANK` |
| **Version** | 2.2 (registry, 2026-10-02) |
| **Home** | shared contracts catalog, `stream-catalog/` |
| **Owner** | FastMediaSorter Android |
| **Role here** | consumer - the published catalog ZIP is this product's channel source |

What this repository owes:

- Read the published catalog as the contract says, under the compatibility law: match by name, accept a higher
  MINOR, refuse a higher MAJOR cleanly, degrade unknown enum values to the documented default, bounds-check
  every index.
- Never destroy user-authored data on refresh: merge by stable key, touch only producer-origin rows; the fetch
  stays explicit, never a silent background download.
- Behaviour reference for the catalog screen and the player: STREAM-BANK documents 03, 05, 06 and 07 (handoff
  section 6a item 3). A behaviour this product wants to change is amended in the catalog first.
- Listed as a consumer in the catalog's stream-catalog consumers list; the seven compatibility-law answers are
  owed by S0003, then this product's registry row moves from `pending` to verified.
