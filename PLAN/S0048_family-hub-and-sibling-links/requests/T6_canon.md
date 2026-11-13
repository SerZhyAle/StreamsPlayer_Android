# S0048 T6 - canon repository: the contrib record and the cross-link table

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** part A - this repository's own session (the canon allows a project session to write its own contrib record); part B -
the canon session `sza-unified-rules-dc`.
**Drafted:** 2026-11-08. **Sent / applied:** part A - (at G1 for the site lines, at G2 for the `play` channel); part B - (at G1,
with request T0).
**Gates:** G1 (the landing answers 200) and G2 (the Play listing answers 200). State when drafted (2026-11-08): both answer
404.

## Part A - `rules/contrib/streamsplayer_mob.md` (written by this repository's session, with the re-sync it belongs to)

Read 2026-11-08: "Channels + listing files. None yet - `channels: []`" and "Channel-matrix rows (this project) - None yet".
The lines that replace them, each applied only when its gate opens:

- At G1, under "Overlay facts", a new bullet:
  `- **Site.** Landing https://serzhyale.github.io/StreamsPlayer_Android/ (EN, RU, UK: `/`, `index-ru.html`, `index-uk.html`),
  help index `help/en/` under it, both answering 200 on <date>; the row `StreamsPlayer` of `SITE-FAMILY-MAP` 1.11.`
- At G2, "Channels + listing files": `play` - package `com.sza.streamsplayer` (phone and Wear OS) and
  `com.sza.streamsplayer.watchface`, the listing source files as S0047 lands them; the listing answered 200 on <date>.
- At the first GitHub release (S0046), the `github` channel line, written by that ticket.

Each line is stated from the evidence of its day; nothing is written before its gate.

## Part B - to the canon session

1. **The cross-link table of `PROMOTION.md` section 4 item 6.** Read 2026-11-08, the item says a link added to the family grid
   "goes through `SITE-FAMILY-MAP` in the contracts catalog first"; it names no separate kit table. The question: is there a
   kit artifact besides `SITE-FAMILY-MAP` that lists the family's products (a template, a site kit list, a generator input), and
   does it need the `StreamsPlayer` row? If none exists, the answer "none" closes the item.
2. **The registry consumer rows** are the `_meta/REGISTRY.md` part of request T0 (three `StreamsPlayer Android` adoption rows
   and the `SITE-FAMILY-MAP` version cell); this file does not repeat them.

## Check run by the sender afterwards

- Part A: `rules/contrib/streamsplayer_mob.md` holds the site bullet (and, after G2, the `play` channel) and the canon
  compliance check of this repository exits 0.
- Part B: the canon session's reply names the artifact or says none, and reports its commit if it changed one.
- Results recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
