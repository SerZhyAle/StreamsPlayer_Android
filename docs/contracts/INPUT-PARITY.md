# INPUT-PARITY - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `INPUT-PARITY` 0.5 |
| **Reviewed** | 2026-10-29, S0057 (earlier: 2026-10-27, S0037; 2026-10-19, S0013/S0026) |
| **Home** | shared contracts catalog, `input-controls/` |
| **Role here** | phone/Wear adopter |

Sections 2-6 and the 2026-10-17 keyboard profile apply where the product offers the action. The applicability sweep was done on 2026-10-27 by code reading (S0037 audit section 5): chord, file and image actions, seek, select and Android TV are not applicable; thirteen gaps on the video player, the activity screens, the catalog and Wear are temporary debt until 2026-12-31, ticket S0052. Runtime evidence remains the 0.3 emulator walk. Physical TalkBack/gamepad/TV-remote and paired-watch checks stay in S0004/S0007. No device acceptance is inferred from JVM tests. 0.5 (amendment of 2026-10-29, items F to H, read in S0057) defines "applicable" (a row binds a product that offers the function), makes `VIEWER-KEYBOARD.md` govern where it differs from the section 4 column for a media viewer, and adds the `help` action on `F1`, which `CONTEXT-HELP` rule 1 already asks and the player answers. The sweep of 2026-10-27 was made against 0.4; S0052 reads the thirteen gaps against items F and G when it resumes, and no gap is added or removed here.
