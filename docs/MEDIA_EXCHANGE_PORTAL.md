# Media exchange and broadcasting portal

The shared portal explains media exchange and live broadcasting across the FastMediaSorter and StreamsPlayer
family. It is published from the FastMediaSorter Android documentation in English, Russian and Ukrainian, and every
program that takes part writes its own sections of it. This repository holds only the links to its pages and never a
copy of their content. StreamsPlayer for Android takes part as a stream player and as a phone and watch broadcaster:
receiving a broadcast, going on air and the watch on the account are its sections.

## Pages

This is the list of the portal addresses the app holds. The resolver ExchangePortalLinks builds each address from
the page's base name and the app language, and ExchangePortalLinksTest fails when this list and the resolver
differ. The addresses carry no anchors.

| Topic | English | Russian | Ukrainian |
| --- | --- | --- | --- |
| Overview | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/index-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/index-uk.html) |
| Live broadcast | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/live-broadcast.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/live-broadcast-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/live-broadcast-uk.html) |
| Home network | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/home-network.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/home-network-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/home-network-uk.html) |
| <img src="assets/exchange-mark/mark-24.png" width="24" height="24" alt=""> Exchange server | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server-uk.html) |
| Cloud storage | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/clouds.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/clouds-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/clouds-uk.html) |
| Windows folder sharing | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/windows-servers.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/windows-servers-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/windows-servers-uk.html) |
| FastMediaSorter editions | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/editions.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/editions-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/editions-uk.html) |
| Accounts, invitations and passwords | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-accounts.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-accounts-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-accounts-uk.html) |
| How to delete an account | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/account-deletion.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/account-deletion-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/account-deletion-uk.html) |
| Receiving a broadcast | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-receive.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-receive-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-receive-uk.html) |
| Going on air | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-start.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-start-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-start-uk.html) |
| Files and folders via Google Drive | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-google-drive.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-google-drive-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-google-drive-uk.html) |
| The watch on your account | [EN](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange.html) | [RU](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange-ru.html) | [UK](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange-uk.html) |

## Where the app opens them

The link is built for the app language: Russian and Ukrainian open their own page, and every other language opens the
English page of the same function.

| Screen | Page |
| --- | --- |
| Catalog menu, Media exchange and broadcasts | the topic the person chooses (the first seven rows) |
| Exchange server screen, sign-in | Accounts, invitations and passwords |
| Exchange server screen, account | Accounts, invitations and passwords; How to delete an account |
| Exchange server screen, Via Google Drive | Files and folders via Google Drive |
| Exchange server screen, live broadcasts | Receiving a broadcast |
| Broadcast settings, notice before publishing through the exchange server | Going on air |
| Watch, broadcast screen, Help on the phone | The watch on your account, opened on the phone in the phone's language |

## Phone app

Open the catalog toolbar menu and choose **Media exchange and broadcasts**, then a topic. The page opens in the
browser. The exchange server screen, the broadcast settings and the watch's broadcast screen link the page that
explains exactly that screen. The existing broadcast viewer guide stays in the broadcast share panel for the
recipient's setup instructions.

## Ownership

The portal's pages and their three languages are written by the programs that have something to say and published
from FastMediaSorter Android; this product writes its own sections there through that repository's process, and
its handed-over text is kept in docs/PORTAL_SECTIONS_STREAMSPLAYER.md. Portal explanations do not replace the
shared behaviour contracts: playback and broadcast behaviour stay governed by STREAM-BANK and LIVE-BROADCAST, and
contract changes go through the shared catalog first.
