# Play Console - Data safety, account deletion and the declarations that follow from them (S0033)

Source of truth for the **Data safety answers and the account-deletion block** of StreamsPlayer for Android. Like the
rest of the Play Console forms, the Data safety form lives only in the web console: the Play Developer API has no
endpoint for it. Decide **here**, then answer in the console. Do not decide inside the console.

The listing texts and screenshots (S0014) are kept beside this file in `docs/play/`. The Data safety form is one
form: before answering, open it as it stands in the console and reconcile it with this file; an answer given here
that the rest of the form contradicts is read by the reviewer as one form.

Reconciled on 2026-10-25 against the tree of that day: the store release of `phone`, `wear` and `watchface`. The
answers describe what that build does. When a later ticket changes what the build stores or sends, this file, the
privacy policy and the permissions table change in the same edit (canon invariant 14).

## The set that must say the same thing

Read together by the reviewer and by the user:

- `docs/PRIVACY_POLICY.md` and its Russian and Ukrainian editions, which are the source of the hosted page;
- the permissions table inside them, generated from the merged store release manifests by
  `tools/privacy/permissions.ps1` (a check fails when they differ);
- the in-app texts: the line of the exchange server card that says every broadcast is announced there from its
  start and what the server's operator sees (`exchange_broadcasts_on_server`), the
  notice before announcing via Google Drive (`broadcast_drive_notice_text`) and the Drive honest text
  (`drive_channel_honest_text`);
- the contract text `DEVICE-EXCHANGE` section 27: item DE (the honest text), item DO (Drive is not sealed), item DQ
  (what the server keeps and sees).

## What leaves the device, to whom, and which clause decides it

The reading of Google's definitions is the one FastMediaSorter Android reached in its S4241 and recorded on
2026-10-15 from `https://support.google.com/googleplay/android-developer/answer/10787469`: "collect" means
transmitting data from the app off the user's device; "sharing" means transferring user data collected from the app
to a third party; data that is only processed locally and not sent off the device does not need to be disclosed. The
exemptions used below are the page's own: data that is unreadable by the developer and by anyone but the sender and
the recipient as a result of end-to-end encryption; data the user chooses to upload directly to their own cloud
storage account; and a transfer to a third party based on a specific user-initiated action. The page has no rule for
a server the user configures, so the decision for it rests on the same clauses and says so. **Re-read the page on
the day the form is answered.**

| Flow | What leaves the device | To whom | Decision | Policy section |
| --- | --- | --- | --- | --- |
| Playing a stream | the request for the stream address the user or the catalog gave (IP address, request details) | the server of that stream | Not declared: a transfer to a third party on a specific user-initiated action; the developer receives nothing | 3 |
| Refresh catalog (menu) or Update on the offer of a new channel list; on the watch also the first load of its list | the request for the catalog file, nothing about the user | GitHub (a release file) | Not declared: user-initiated, no user data in the request | 3 |
| Checking for a new channel list (phone, on opening the list, unless **Only when I ask**); with **Automatically on Wi-Fi** also the download of a newer catalog, at most once a day | a `HEAD` request for the catalog file, and under the automatic position the request for the file itself; nothing about the user | GitHub (a release file) | Not declared, re-read on 2026-11-10 rather than carried over: the request is not user-initiated, so that clause does not cover it; it rests on the request carrying none of the form's data types (no identifier, no account, no content, nothing derived from the user) and on the developer receiving nothing; the user can turn it off in Settings > General | 3 |
| Channel pictures (the accepted offer on the phone; the first load of the list and Refresh catalog on the watch) | the requests for the picture files and the file naming the current ones, nothing about the user | GitHub (release files) | Not declared: user-initiated, no user data in the request | 3 |
| Broadcast on the local network | the microphone or camera content, to listeners who connect to the device's address | listeners on the user's network | Not declared: the user's own action, nothing reaches the developer, the app keeps no copy | 3 |
| Exchange server, content | the sound and the picture of a published broadcast, sealed on the device | the exchange server the user named | Not declared: end-to-end sealed (`DEVICE-EXCHANGE` items DE, DK to DQ); a server that does not offer `sealed` gets no published broadcast | 5, 6 |
| Exchange server, directory data | login, device name and random id, public key, presence, broadcast title and mode, addresses | the exchange server the user named | **Declared** as collected, not shared, optional (rows below) | 5, 6 |
| Google Drive | an announcement file and a device-name file, in the hidden `drive.appdata` folder | the user's own Google account | Not declared: the user's own cloud storage, one scope, never another account; fallback below | 7 |
| Phone and watch | pins, channels, playback and broadcast commands, the exchange pairing | the user's own paired watch, through Google Play services | Not declared: between the user's own devices; nothing reaches the developer | 3, 8 |
| QR scanner | nothing: camera frames are processed on the device | nobody | Nothing to declare | 3, 4 |
| Analytics, crash reports, advertising | nothing: the app has none and uses no advertising identifier | nobody | Nothing to declare; Android vitals are Google's own facility | 1, 10 |

## The answer

**Content: not declared** - sealed end to end on the exchange path, the user's own devices and listeners on the other
paths.

**Directory data: declared as collected, not shared, optional.** This is the conservative reading, chosen because the
definition of "collect" is literal about any transmission off the device and the page has no exception for a server
the user entered. It is also what the privacy policy says in its own words: the server knows the names of the account,
the devices and the broadcasts and which devices are online. It is the same answer FastMediaSorter Android gives for
the same flow, so the family answers one way.

| Data type (console category) | What it is here | Collected | Shared | Optional | Purpose |
| --- | --- | --- | --- | --- | --- |
| Personal info - User IDs | the login of the exchange account | yes | no | yes | App functionality |
| Device or other IDs | the exchange device id and name of each own device, with presence | yes | no | yes | App functionality |
| Files and docs - Files and docs (names) | the title of a published broadcast, as FastMediaSorter Android declares published folder names and broadcast titles | yes | no | yes | App functionality |

- **Encrypted in transit:** yes - TLS to the exchange server, whose certificate the app pins on first use and refuses
  after a change; HTTPS to Google Drive; broadcast content is additionally sealed on the device.
- **Users can request deletion:** yes - see "Account deletion" below.
- **Shared: none.** Nothing is transferred to a third party by the developer. The exchange server is entered by the
  person, so the transfer is the person's own action.
- **No ads, no analytics, no data to the developer, no advertising identifier** (no `AD_ID` permission is declared
  in any merged manifest).

Each form factor is filled separately in the console and the answer of one is not inherited by the other:

| Form factor | Directory data | Content | Account creation |
| --- | --- | --- | --- |
| Phone / tablet | declared as above | not declared | no - the app signs in to an account made elsewhere |
| Wear OS | declared as above - the watch enrolls its own device record and sends its device name and presence | not declared | no - the watch takes a pairing code from the paired phone and has no registration screen |
| Watch face (`com.sza.streamsplayer.watchface`) | **no data collected**: the package has no code, no permission and no network access | none | no |

**Fallbacks, if review reads an answer differently.** Written here so they are not re-derived:

- *Reviewer says directory data need not be declared* (the server is the person's own storage): remove the three
  rows. The privacy policy stays as written - it already states what the server sees.
- *Reviewer says content must be declared too* (the end-to-end exemption refused because whoever runs the server can add
  a device of their own to the account, as `DEVICE-EXCHANGE` item DE states honestly): declare Audio - Voice or sound
  recordings and Photos and videos as collected, not shared, optional, App functionality, encrypted in transit. Change
  this file first, then the console.
- *Reviewer says the Drive announcements must be declared* (the app reads and writes them): declare App activity - Other
  user-generated content (the title) and Device or other IDs (the random device id and the device name) as collected,
  not shared, optional, App functionality, encrypted in transit. Change this file first, then the console.

## Account deletion

Source of the requirement, as FastMediaSorter Android read it on 2026-10-15 at
`https://support.google.com/googleplay/android-developer/answer/13327111` (re-read it when answering): the
requirement applies "if your app allows users to create an account from within your app".

**This app creates no account.** Since S0027 no surface of the app registers one: an invitation is validated and
discarded, the enrollment sends no invitation, and an account on an exchange server is made in
Fast Media Sorter & Organizer, in Fast Media Sorter & Sharing for Windows or by the person who runs the server. The
form's question "Does your app allow users to create an account?" is answered **No**, and the account-deletion block
is **not applicable**.

What the app and the policy offer instead, so the data question is still answered:

- **In the app:** **Sign out on this device** on the Exchange screen revokes the device's identity on the server,
  forgets the token and removes the entry, and signs out the watch connected through it. Channels the user kept stay on
  the device and go with uninstalling.
- **The account itself:** deleted in the program where it was made, or by the person who runs the server. The portal
  page "How to delete an account" describes both ways in English, Russian and Ukrainian, and the app opens it from the
  Exchange screen (`ExchangePortalLinks`, the page `account-deletion`). The privacy policy, section 9, points to it.
  Give that page's English address as the data-deletion request link in the console. Check that the address answers
  before submitting.
- **The honest gap.** The developer runs no exchange server and cannot delete an account on one that is not theirs.
  For a server run by someone else the page can only name the person to ask, and the policy says so.

## Declarations that follow from the same facts

Filed with the store listing (S0014 P7), not decided here; recorded so that they are not contradicted:

- **Ads:** none. **Advertising ID:** not used.
- **Foreground service permissions:** `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (phone and watch: audio and video playback
  with a media session) and `FOREGROUND_SERVICE_MICROPHONE` (phone and watch: a microphone broadcast the user
  started). The policy table gives the same two purposes.
- **Target audience:** not directed at children (policy section 11). The age rating needs a fresh IARC questionnaire:
  the app opens uncurated third-party streams and can broadcast the microphone and the camera.
- **Privacy policy link:** the address of the hosted English page; the Russian and Ukrainian pages are its siblings.

## Operator steps

1. Confirm that the hosted policy answers 200 in English, Russian and Ukrainian and that its text equals the
   repository sources (`tools/site/build-site.ps1 -Check`). Do not submit the form while it does not.
2. Open `App content -> Data safety`. Observed on 2026-11-13: the console asks the Data safety form once per app, with
   no form-factor choice, so phone and Wear OS share the answers of `com.sza.streamsplayer` (the table above still says
   what each side sends); the watch face package has its own form.
3. Phone and Wear OS: declare the three directory rows of the table, collected and not shared, optional, App
   functionality, encrypted in transit; leave Content unchecked. Answer the deletion question with the portal address.
4. Watch face: no data collected.
5. Under a policy enforcement Play refuses automatic review - send the held edit from
   `Publishing overview -> Send changes for review`.
6. Write the state and the date into the release record.

**Timing.** Do not touch the console while a submission is under review; an edit restarts the review from the beginning.

## Console record (2026-11-13)

Filled in the owner's personal developer account on the owner's order (S0047 quiz decisions of 2026-11-13). Nothing is
uploaded, nothing is sent for review.

### `com.sza.streamsplayer` (app 4972961129584993008, "StreamsPlayer: Internet Radio")

| Item | State | Answer |
| --- | --- | --- |
| Privacy policy | saved | `https://serzhyale.github.io/StreamsPlayer_Android/privacy.html` |
| Ads | saved | No |
| Advertising ID | saved | No (no `AD_ID`, ads, Firebase or analytics in the manifests or `gradle/libs.versions.toml`) |
| Government apps | saved | No |
| Financial features | saved | none |
| Health apps | saved | none (no `BODY_SENSORS` or health permission in any manifest; the wear `heart_rate_*` strings are unused) |
| Content rating | saved, fresh IARC questionnaire | see below |
| Sign in details | **owner** - form filled, not saved | "Yes, part of the app is restricted"; name "Exchange server review account"; instructions below; login, password and the server address are the owner's to type |
| Target audience | **owner**, after Sign in details | 18 and over (owner decision of 2026-11-13, taken with the IARC answers) |
| Data safety | saved as a draft; submits after Target audience | the answer of this file: User IDs, Files and docs, Device or other IDs - collected, not shared, optional (users can choose), not ephemeral, App functionality; encrypted in transit; account creation "My app does not allow users to create an account", login with accounts created outside the app "Yes - Other: On the user's own exchange server: in Fast Media Sorter apps or by the server's operator"; deletion request "Yes", `https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/account-deletion.html` (answered 200 on 2026-11-13) |

Reviewer instructions typed in Sign in details ("Any other information", 422 of 500 characters; the owner appends the
address):

> Everything except the Exchange server screen works without sign-in: channel catalog, radio, video and RTSP playback,
> own channels, local network broadcast. The Exchange server screen (menu > Exchange server) signs in to a server the
> user runs; the app creates no accounts. To review it: menu > Exchange server, enter the server address and port below
> and the login and password above, tap Sign in. Server address and port:

IARC questionnaire, category "All Other App Types", contact `sza@ukr.net`, answered by the facts (owner decision): the
catalog holds third-party live channels that the developer does not curate, and its `Adult` rubric (37 rows, all audio
radio, read from STREAMS Player for Windows SP-0063) carries explicit material; there is no pornographic video channel.

| Section | Answers |
| --- | --- |
| Downloaded app | ratings-relevant content in the package: No |
| User content sharing | users exchange content (the live broadcast): Yes; UGC the primary content: No; public nudity: No; public graphic violence: No; block users: No; report users: No; chat moderation: No; interactions limited to invited listeners: Yes |
| Online content | content outside the package: Yes |
| Violence | can contain: Yes; primary purpose: No; visual: Yes; gory: Yes; referred to in speech: Yes |
| Sexuality | can contain: Yes; primary purpose: No; visual: Yes; nudity: Yes; pornographic: No; referred to in speech: Yes |
| Language | offensive language: Yes; the focus: No; minor profanity, moderate swearing, discriminatory language, sexual expletives: Yes |
| Controlled substance | references: Yes; the focus: No; visual: Yes; referred to in speech: Yes |
| Age-restricted products | focus on promoting them: No |
| Miscellaneous | location sharing, digital purchases, cash rewards or NFTs, web browser or search engine, news or educational: No |

Resulting ratings: ESRB Teen (Diverse Content: Discretion Advised, Users Interact), PEGI Parental guidance, USK 16+,
ClassInd 16+ (violence, sexual content, illegal drugs), IARC generic 12+, Google Play Russia and South Korea 12+.

### `com.sza.streamsplayer.watchface` (app 4976077107533670611, "StreamsPlayer Watch Face")

Every App content item saved ("You're all caught up"): privacy policy as above; Sign in details "No"; ads No;
advertising ID No; government No; financial none; health none; target audience 13-15, 16-17, 18 and over (owner
decision); Data safety "no data collected, no data shared" (the package has no code, no permission, no network access);
IARC "All Other App Types", every answer No: ESRB Everyone, PEGI 3, USK all ages, ClassInd all ages, IARC 3+.
