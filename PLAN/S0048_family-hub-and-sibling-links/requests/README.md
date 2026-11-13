# S0048 requests - index, routing and the target table

One self-contained request per target of S0048 section 2.1. This repository never edits another repository or the catalog;
each request is carried to its session by the owner (decision D4): pasted into that session, or that session started with the
file as its input. Every file states its gate, the `curl` evidence to attach on the day of sending, the exact change in the
target's languages, the check the target runs and the check run here afterwards.

## Gates

- **G1** - the landing `https://serzhyale.github.io/StreamsPlayer_Android/` answers 200 in EN, RU, UK (`/`, `index-ru.html`,
  `index-uk.html`) and its help index `help/en/` answers 200 (S0045). T0 goes first; the footer requests follow once the
  `SITE-FAMILY-MAP` 1.11 row is in the catalog.
- **G2** - the Play listing `https://play.google.com/store/apps/details?id=com.sza.streamsplayer` answers 200 (S0047); the
  body-copy and portal parts wait for it (decision D5 (a)).
- **G3** - each target's own publication, observed from outside.

State on 2026-11-08: G1 closed (all four landing routes 404), G2 closed (404).

## Target table (S0048 acceptance 11)

| Target | Session (repository) | File | Locales of the change | Gate | Drafted | Sent | Published | Check result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T0 | canon `sza-unified-rules-dc` -> hub (`P:\Contracts`) | `T0_contracts-catalog.md` | - (catalog text) | G1 | 2026-11-08 | - | - | - |
| T1 | hub (`P:\WEB\sites.google.comsiteszaodua`) | `T1_hub-card.md` | en, ru, uk | G1 + owner's deploy confirmation | 2026-11-08 | - | - | - |
| T2 | FastMediaSorter Android (`P:\ANDROID\FastMediaSorter_mob_v2`) | `T2_fastmediasorter-android.md` | A: 13 footer locales; B: en, ru, uk | A: G1; B: G2 | 2026-11-08 | - | - | - |
| T3 | FastMediaSorter Windows (`P:\WINDOWS\FastMediaSorter_Lite`) | `T3_fastmediasorter-windows.md` | A: en, ru, uk (others English); B: en, ru, uk | A: G1; B: G2 and one recorded run | 2026-11-08 | - | - | - |
| T4 | StreamsPlayer Windows (`P:\WINDOWS\Streams_Player`) | `T4_streamsplayer-windows.md` | A: 13 locales; B: en, ru, uk and the landing locales | A: G1; B: G2 | 2026-11-08 | - | - | - |
| T5a | CyrFlip (`P:\WINDOWS\CyrFlip`) | `T5a_cyrflip.md` | English grid on 13 pages | G1 | 2026-11-08 | - | - | - |
| T5b | doc-html-translate (`P:\WINDOWS\EPUB_2_HTML`) | `T5b_doc-html-translate.md` | English grid on 21 pages | G1 | 2026-11-08 | - | - | - |
| T5c | FileDO (`P:\WINDOWS\FileDo`) | `T5c_filedo.md` | English grid on 6 pages and the guides | G1 | 2026-11-08 | - | - | - |
| T5d | OneClickRunner (`P:\WINDOWS\OneClickRunner`) | `T5d_oneclickrunner.md` | English grid on 3 pages | G1 | 2026-11-08 | - | - | - |
| T5e | Universal Agent Kit (`P:\WEB\universal-agent-kit`) | `T5e_universal-agent-kit.md` | name-only grid on 55 pages | G1 | 2026-11-08 | - | - | - |
| T6 | A: this repository; B: canon `sza-unified-rules-dc` | `T6_canon.md` | - | A: G1, G2; B: G1 | 2026-11-08 | - | - | - |
| T7 | hub | `T7_hub-sitemap.md` | - | - | 2026-11-08 | nothing owed | - | nothing owed - condition false (the hub sitemap lists only `https://sza.od.ua/`, read 2026-11-08) |

## Findings in other repositories, carried inside their requests

- T3: the Windows row's translations are keyed `"StreamsPlayer"` while the member is `"STREAMS Player"`, so the ru and uk
  pages show the English type today, and a new member `StreamsPlayer` would inherit the Windows translation.
- T5c: a test fails any page containing `<b>StreamsPlayer</b>`; the new tile needs that guard narrowed.
- T5d: the grid lacks the `STREAMS Player` tile and two labels predate the map's 1.3 and 1.9 names (observed, not asked).
- T5e: a name-only grid shows `STREAMS Player` and `StreamsPlayer` with nothing telling them apart.

## Sources of the texts

- The portal sections: `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` (S0030), labels checked against the shipped phone and watch
  strings on 2026-11-08.
- The type label of each locale: each site's own label for `Windows stream player`, or for its Android row where it has no
  stream-player label (T4).
- The hub card: the hub's own card 8 and the product's README.
