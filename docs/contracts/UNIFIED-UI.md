# UNIFIED-UI - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `UI-SIZING` 1.1, `BUTTONS` 2.0, `BOOLEAN-OPTION` 2.0, `COLLAPSIBLE-GROUP` 1.1, `SETTINGS-NAVIGATION` 2.0 |
| **Reviewed** | 2026-10-27, S0037 (built by S0036) |
| **Home** | shared contracts catalog, `ui-sizing/`, `buttons/`, `boolean-option/`, `collapsible-group/`, `settings-navigation/` |
| **Role here** | adopter on the phone and Wear; the watch face has no configuration surface |

The five contracts bind every user-facing surface of the phone and Wear apps without a per-product opt-in. The
profile record (touch profile, 48 dp floor, no compact mode, the Wear OS permanent variant) is
`docs/design/UI_SIZE_PROFILE.md`. The shared components are in `phone/src/main/java/com/sza/streamsplayer/ui/common/widget/`
and `ui/settings/`.

Wired into the running screens by S0049: the settings screen with the rail and the About view, the collapsible groups
of the exchange screen, the command flow on every command row and the boolean row on every boolean option; the profile
record lists each screen's decision. No capture, contrast, accessibility or page-timing evidence exists yet, so the
registry rows claim `partial` or `-`, never full conformance. The measured proof is S0049 (until 2026-12-21).
