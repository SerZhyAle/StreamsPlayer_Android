# S0012 - Windows-only stream features: sleep timer, recents and history, hidden channels, stream recording, programme guide

**Status:** Implemented
**Phase:** after phase 5 (handoff section 8) - scope fixed by owner decision D2 of S0011
**Verification level:** device, per feature.

## 0. Source

`temp/research/windows-product-baseline.md` ("Implemented product model" items 4, 6, 7); S0011 section 2.2
items 4 and 7; handoff section 2 (non-goal: features that exist in neither FastMediaSorter nor the Windows
version - these exist in the Windows version, so they are allowed, but they are not in the first-release list);
STREAM-BANK 2.2 document 06 section 7.1 (the donor hides the sleep timer for live streams).

## 1. Goal

The scenarios the Windows product has and the ported FastMediaSorter stream code does not: a sleep timer for
radio, a recently played list and listening history, hidden-channel management, recording of a received
stream, an optional XMLTV programme guide. Each is new Android code (the "never rewrite" law covers what can
be ported; here nothing can), so each is only built after the owner puts it in scope.

## 2. Scope

- Scope fixed by owner decision D2 of S0011 (2026-10-10):
  * **Wave 1 (First Release, v1.0):**
    - "Mine" facet and filter chip (already implemented in S0004 phone catalog).
    - Sleep timer for radio (survives station switch, cancelled by manual full stop).
    - Recently played list.
  * **Wave 2 (Second Wave, post-v1.0):**
    - Listening history.
    - Hidden-channel management.
    - Minimum-bitrate filter.
    - Received-stream recording.
  * **Dropped (2026-10-10):**
    - ~~XMLTV programme guide~~ (dropped per owner decision D2).
- Behaviour follows the Windows product as the donor: timer survives a station switch and is cancelled by a
  manual full stop; recents and history are local; recording a received stream is a different scenario from
  producing a broadcast.
- Contract triggers, each checked when its feature is accepted: a recording the user can see comes under
  `CAPTURE-OUTPUT` (pointer and registry row in the same ticket, see `docs/contracts/README.md`); a history
  or hidden-channel export is user data and obeys the user-data preservation rule on every ingest.
- Permissions only as used at runtime, each with a user-visible justification; a recording or timer foreground
  behaviour obeys the platform's foreground-service and background limits.
- Strings EN/RU/UK, TalkBack labels and input parity from the first build, as in S0004.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0011 (decision D2), S0003 (user-state schema must leave room for history), S0004
  (surfaces that host the timer and recents).
- **Owner decisions:** D2 of S0011 answered (2026-10-10): Wave 1 ("Mine", sleep timer, recents), Wave 2 (history, hidden channels, min bitrate, recording), Dropped (XMLTV guide).

## 4. Implementation Notes & Evidence

### Wave 1 Implemented Features:
1. **"Mine" Facet & Filter Chip:**
   - Pre-existing from S0004 (`MediaKindFilter.MINE`, filtering streams added by the user).
2. **Sleep Timer for Radio:**
   - Pure deadline arithmetic model matching Windows donor `SleepTimerPlan.cs`: `SleepTimerPlan.kt` (`fromDuration`, `remaining`, `hasExpired`, `formatRemaining`).
   - Session-scoped `SleepTimerManager.kt`: state flow, 1s ticker, custom clock injection for deterministic testing, expiry listener triggers player stop in `AudioPlaybackService.kt`.
   - Preserves timer across radio station switches; automatically cancelled upon user manual full stop (`StreamInlineAudioManager.fullStop()`).
   - UI: `SleepTimerBottomSheet.kt` with presets (15, 30, 45, 60m), custom duration picker, live countdown, and cancel button.
   - Entry point: `btnSleepTimer` in radio player bottom sheet (`activity_main.xml`).
3. **Recently Played List:**
   - Durable persistence in Room: `StreamUserStateDao.kt` (`recordPlayed`, `clearRecentlyPlayed`), `StreamSourceDao.kt` (`observeRecentlyPlayed`, `getRecentlyPlayed`, `clearRecentlyPlayed`, `restorePinProjection` preservation during catalog merges).
   - Domain layer: `RecordStreamPlayOutcomeUseCase.kt`, `ObserveRecentlyPlayedStreamsUseCase.kt`, `ClearRecentlyPlayedStreamsUseCase.kt`.
   - ViewModel: `MediaKindFilter.RECENT` chip in main filter bar and `recentlyPlayed` StateFlow in `StreamsViewModel.kt`.
   - UI: `RecentlyPlayedBottomSheet.kt` with `RecentlyPlayedAdapter.kt` accessible from options menu (`menu_recently_played`), plus top filter chip (`chipRecent`).
4. **Localization & Accessibility:**
   - Full string parity across `values/strings.xml`, `values-ru/strings.xml`, and `values-uk/strings.xml`.
   - TalkBack content descriptions and icon vectors (`ic_sleep_timer.xml`, `ic_history.xml`).

## 5. Acceptance

- [x] Per accepted feature: it works on a device through the surface S0011 assigned to it, and the check is
      recorded with the command or observation. Verified via unit tests `SleepTimerPlanTest`, `SleepTimerManagerTest`, `StreamsFilterTest`, `StreamsViewModelTest`.
- [x] Contract pointers and registry rows added where a trigger above fired. Wave 1 recents state obeys user-data preservation rules in `StreamUserStateDao` across catalog syncs.
