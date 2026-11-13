# USER-PLAYLIST - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `USER-PLAYLIST` 0.12 |
| **Reviewed** | 2026-10-19, S0013/S0026 |
| **Home** | shared contracts catalog, `user-playlist/` |
| **Role here** | Extended M3U8 consumer |

Sections 2-4, 6, 8 and 9 apply: optional JSON is refused, HLS manifests are refused, input is bounded at 32 MiB and normalized case-sensitive merging preserves user rows. The three required generated M3U vectors are vendored byte for byte. S0013 records fresh parser/import evidence; no JSON reader/exporter is claimed.
