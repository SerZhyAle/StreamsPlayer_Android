# LIVE-BROADCAST - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `LIVE-BROADCAST` 1.6 |
| **Reviewed** | 2026-10-27, S0037 (earlier: 2026-10-25, S0034 / S0013) |
| **Home** | shared contracts catalog, `live-broadcast/` |
| **Role here** | phone/watch producer and broadcast receiver |

Sections 1-3, descriptor/handshake section 6 and the 2026-10-17/19/22 amendments apply. 1.0-1.2 preserve single-stream reception; independent multi-stream receiver state is not exercised. 1.3 item R (outward-link password choice: generated, set-by-the-person or explicit none, with the descriptor consequence) is not implemented by this producer - it stays a producer release blocker and is carried with auth/seal migration by S0028/S0032/S0037; live two-network handshakes by S0034 remain BlockExternal on the owner-attended stand. Existing implementation evidence remains partial 0.18. Own exception expires 2026-12-31. No full current-version, live latency or publication verdict is claimed.

1.4-1.6 were read in full on 2026-10-27 (S0037, `PLAN/S0037_contract-sync-20261017/audit.md` section 6). Items S (pending descriptor), T (a deliberate end, producer order and sign-out) and U (an ended broadcast at a tunnel; a removal ends nothing that still delivers) are not implemented; the product behaves as a program that recorded 1.3, except that the producer writes `broadcast-end` after closing its streams where item T requires it first. Carried by S0043 (receiver end state) and S0053 (producer order, sign-out, pending link, reader fixes), exception until 2026-12-31. The preparation before the start (DEVICE-EXCHANGE item DW) needs an owner decision. Item R stays with S0028/S0032.
