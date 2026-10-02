# LIVE-BROADCAST - pointer

| Field | Value |
| --- | --- |
| **Id** | `LIVE-BROADCAST` |
| **Version** | 0.13, draft (registry, 2026-10-02) |
| **Home** | shared contracts catalog, `live-broadcast/` |
| **Owner** | FastMediaSorter Android |
| **Role here** | producer from the first release (phone microphone and camera, watch microphone); listener in the second wave |

What this repository owes:

- Emit the endpoint and descriptor shapes the contract defines, unchanged from the FastMediaSorter
  implementation it is ported from; no local reshape (handoff section 6a item 3).
- Acceptance is the verification handshake of LIVE-BROADCAST section 6, run against StreamsPlayer for Windows for
  each stream kind (S0006 phone, S0007 watch).
- Consent and permission flows ported with the feature; permissions declared only as used.
- The listener role (receiving broadcasts) is second-wave scope and gets its own ticket when scheduled.
