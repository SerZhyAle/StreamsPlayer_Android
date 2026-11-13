# S0072 - play-publication-remaining-steps

**Ticket:** S0072
**Status:** Draft
**Complexity:** medium - no source; ordered owner and release steps, several one-way
**Date:** 2026-11-13

## Captured request and migration scope

Owner's rule (deviceinfo-mob session, 2026-11-13): "\"Что осталось до публикации\" это должен быть тикет в репозитори проекта, а не мне сюда"
Owner's order for this product (2026-11-13, relayed by the deviceinfo-mob session): "Повтори всё выполненное для новго Streams_mob"

What remains before StreamsPlayer is published on Google Play, as of 2026-11-13. Already done the same day on the owner's order (S0046 part A, S0047 part B, quiz blocks of both):
- Public remote SerZhyAle/StreamsPlayer_Android (commit 7b6ce41 holds the product tree) and its Pages site: privacy.html, privacy-ru.html, privacy-uk.html, sitemap.xml, robots.txt answer 200; the site root answers 404 (no landing yet, S0045).
- Play Console app com.sza.streamsplayer ("StreamsPlayer: Internet Radio", personal account 8446656778368889827, app 4972961129584993008, App, Free): privacy URL, no ads, no advertising ID, not a government app, no financial or health features, fresh IARC rating (ESRB Teen, PEGI Parental guidance, USK 16, ClassInd 16, IARC 12+), Data safety saved as a draft (three directory data types, collected, not shared, optional), store settings (Music & Audio, sza@ukr.net), default listing in en-US, ru-RU and uk with icon and feature graphic, Wear OS form factor added.
- Play Console app com.sza.streamsplayer.watchface ("StreamsPlayer Watch Face", app 4976077107533670611, App, Free): every App content item complete (no restricted access, no ads, no data collected, IARC Everyone / PEGI 3, target audience 13 and over), store settings (Personalization, sza@ukr.net), Wear OS form factor added; listing empty.

Remaining, in order:
1. Done 2026-11-13: Sign in details "No" for the main app (owner's later decision, the answer Fast Media Sorter & Organizer gives in production).
2. Done 2026-11-13: Target audience 18 and over, Data safety submitted; the main app's App content overview reads "You're all caught up".
3. Phone screenshots (2 to 8, 16:9 or 9:16) and Wear OS screenshots for en-US, ru-RU and uk for the main app, from a real device check (S0047 A5 capture rig).
4. Watch face listing: write its source in docs/play (title, short and full description in en, ru, uk; icon and feature graphic as render targets), then fill the watch face's default listing and its Wear OS screenshots.
5. Landing (S0045): once https://serzhyale.github.io/StreamsPlayer_Android/ answers 200, set it as the website in Store settings of both apps and as the repository homepage (gh repo edit --homepage).
6. GitHub social preview: upload docs/github/social-preview-1280x640.png in the repository settings (S0046 A6, the owner's browser).
7. Release side (S0047 parts A and C, owner's order only): app signing key decision (S0047 D1), upload key and keystore.properties in P:\ANDROID\StreamsPlayer_credentials, store bundles of phone, wear and watchface, pre-flight verdict, first upload to internal testing with the app signing key import (one-way), the update proof, closed testing if the account requires it, countries and rollout (S0047 D4, D5), the foreground-service declarations the uploaded manifests trigger.
8. Wear OS opt-in and the Wear OS review policy for both apps (console Wear OS step 3; allowed only after the screenshots and a Wear bundle on a testing track; one-way, owner's yes).
9. Production and review (one-way, owner's order only).
10. After the landing and the Play listing answer 200: hub:S0010 (SITE-FAMILY-MAP row and hub card) and the footer-grid tickets filed in each consumer become workable; this repo's S0048 routes its remaining requests then.
11. Account-level, not this app: "Declare your trader status" (S0067, non-trader decided; due February 2027 for this account).

