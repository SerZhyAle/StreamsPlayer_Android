# USER-PLAYLIST - pointer

| Field | Value |
| --- | --- |
| **Id** | `USER-PLAYLIST` |
| **Version** | 0.12, draft (registry, 2026-10-02) |
| **Home** | shared contracts catalog, `user-playlist/` |
| **Owner** | StreamsPlayer (Windows) |
| **Role here** | consumer - m3u playlist import |

What this repository owes:

- Import playlists as the contract defines them and run this product's tests against the catalog's vectors at
  the registry version.
- Imported rows are user-authored data: a later catalog refresh never touches them.
- Lands with the core (S0003) if the import code moves with it, otherwise in S0009.
