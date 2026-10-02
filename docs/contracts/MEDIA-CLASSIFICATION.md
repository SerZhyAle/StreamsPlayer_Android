# MEDIA-CLASSIFICATION - pointer

| Field | Value |
| --- | --- |
| **Id** | `MEDIA-CLASSIFICATION` |
| **Version** | 0.10, draft (registry, 2026-10-02) |
| **Home** | shared contracts catalog, `media-classification/` |
| **Owner** | FastMediaSorter Android |
| **Role here** | consumer of the normalized media category enum |

What this repository owes:

- Use the contract's category names; an unknown value degrades to the documented default, never to an error.
- Implemented with the stream core (S0003).
