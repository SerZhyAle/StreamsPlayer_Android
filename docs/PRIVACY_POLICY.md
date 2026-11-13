# Privacy Policy for StreamsPlayer

**Last updated: November 10, 2026**

StreamsPlayer is a player for internet radio, live video (HLS, DASH) and RTSP streams that can also broadcast live
from the microphone and the camera. It is an Android app for phones, an app for Wear OS watches and a watch face,
published by Serhii Zhyhunenko (SerZhyAle). This policy covers all three packages and says which one it means
where they differ.

In short:

- **The developer receives none of your data.** The developer runs no server, keeps no accounts and the app
  contains no analytics, crash reporting, advertising or tracking code.
- **Everything optional is off until you turn it on:** the exchange server, Google Drive, and publishing a
  broadcast through a server.
- **What leaves your device goes to a place you chose:** the stream you play, the server you named, your own
  Google account, the listeners you gave an address to.

## 1. What the app does not do

- It has no telemetry: no analytics, no crash reporting, no advertising, no advertising identifier and no
  third-party tracking library. The libraries it is built from are listed in section 10.
- It creates no account. An account on an exchange server is made elsewhere (section 9); this app only signs in.
- It writes no log file in the version you install, and sends no diagnostics anywhere.
- It does not ask for your contacts, your location, your photos or files, or your phone number (section 4 lists
  every permission).
- It does not record broadcasts and keeps no copy of what it plays or sends.

## 2. What stays on your device

- Your favorites, pins, collections, your own channels, settings and playback state are stored in the app's private
  storage. The app is excluded from Android backup (`allowBackup="false"`), so they are not copied to your Google
  account or to another device by Android.
- Exchange sign-in tokens, and the passwords you save for protected broadcasts, are encrypted with a key that never
  leaves Android Keystore (AES-256-GCM). The password you type to sign in is wiped from memory right after it is
  used and is never written to disk or to a log.
- The channel catalog and the channel pictures you downloaded are stored on the device.
- Removing the app, or clearing its storage in Android settings, deletes all of this.

## 3. What leaves your device, and where it goes

Each line is one thing the app can send, to whom, and when.

- **The streams you play.** When you play a channel, the app connects to the server of that stream, the address you
  or the catalog gave it. That server sees your IP address and the usual request details. Some stream addresses use
  plain HTTP or RTSP, which are not encrypted; the app opens the address you ask for. The developer is not a party
  to these connections.
- **The channel catalog.** When you choose **Refresh catalog** in the menu or **Update** on the offer of a new
  channel list, and on the watch also when its channel list first loads, the app downloads the catalog file from
  GitHub (github.com). GitHub sees your IP address and the request, as for any download. Nothing about you is sent
  with it.
- **Checking for a new channel list.** On the phone, when you open the channel list, the app asks GitHub
  (github.com) whether a newer catalog file is published there: a short request that downloads no file. Only when one
  is, the list offers it. In **Settings > General**, **Updating the channel list** set to **Only when I ask** turns
  this check off; **Automatically on Wi-Fi** downloads a newer catalog without asking, on Wi-Fi and at most once a
  day. GitHub sees your IP address and the request. Nothing about you is sent with it.
- **Channel pictures.** On the phone, only when you accept the offer that follows a catalog update; on the watch, when
  its channel list first loads and after **Refresh catalog**. The app downloads the pictures, and a small file that says
  which ones are current, from GitHub (github.com). GitHub sees your IP address and the request, as for any download.
  Nothing about you is sent with it.
- **A live broadcast on your network.** While you broadcast, listeners connect straight to your phone or watch at the
  address it shows. This connection does not pass through any server and is not encrypted: anyone on that network
  who has the address can listen. It starts only when you start a broadcast and stops when you stop it.
- **The exchange server (optional).** Section 5.
- **Google Drive (optional).** Section 7.
- **Between your phone and your watch.** The two devices exchange your pins and channels, playback and broadcast
  commands, and the exchange pairing (section 8) over the Wear OS Data Layer, which is part of Google Play services
  on your own devices.
- **QR codes.** The scanner reads the camera picture on the device only; the decoded text goes back to the form
  that asked for it. It never opens a link or connects by itself.

## 4. Permissions

The app asks only for the permissions below. This table is generated from the merged manifests of the store release
of the phone app, the Wear OS app and the watch face, and a check fails when the manifests and the table differ.
Android asks you at the moment of use for the microphone, the camera and notifications; you can refuse or withdraw
any of them in Android settings.

<!-- permissions:begin (generated by tools/privacy/permissions.ps1 from the merged store release manifests; do not edit) -->
| Permission | Where | What it is for |
|---|---|---|
| `android.permission.ACCESS_NETWORK_STATE` | Phone, Watch | Notices when the network connection changes, so playback can reconnect and return to the live point by itself. |
| `android.permission.CAMERA` | Phone | Camera broadcast and the QR code scanner (phone only). Used only after you start a camera broadcast or open the scanner; scanner pictures are processed on the device and never stored or sent. |
| `android.permission.CHANGE_NETWORK_STATE` | Watch | Lets the watch hold its own Wi-Fi or mobile connection while the audio player or a broadcast is on screen. |
| `android.permission.FOREGROUND_SERVICE` | Phone, Watch | Lets playback and the broadcasts you start keep running, with a notification you can see. |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Phone, Watch | Android requires it to mark the running playback service as media playback. |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Phone, Watch | Android requires it to mark the running broadcast service as one that uses the microphone; it runs only while you broadcast sound. |
| `android.permission.INTERNET` | Phone, Watch | Plays the streams you choose and connects to the services you set up yourself: your exchange server and, on the phone, Google Drive and the channel catalog on GitHub. |
| `android.permission.POST_NOTIFICATIONS` | Phone, Watch | Shows playback controls and the on-air status of a broadcast in the notification shade. |
| `android.permission.RECORD_AUDIO` | Phone, Watch | Microphone broadcast on the phone or the watch. Used only after you start a broadcast. |
| `android.permission.WAKE_LOCK` | Phone, Watch | Keeps playback or a broadcast running while the screen is off. |

The watch face asks for no permissions.
<!-- permissions:end -->

## 5. The exchange server (optional)

An exchange server connects your own devices when they cannot reach each other directly, so a broadcast from one
device can be found and played on another. It is a program that you run yourself, or that a person you know runs for
you. The developer of this app runs no exchange server and offers none to the public. Nothing here is on until you
sign in to a server.

- **What the app sends.** At sign-in: your login and password, this device's name, a random device identifier and
  the public part of the device's encryption key. While connected: that the device is online, and, for each broadcast
  the phone starts, its title, its mode (sound or camera) and the addresses a listener can try. For the relay through
  the server, its content as well - encrypted on your device first (section 6). Listening to a broadcast sends the
  server the request to join it.
- **How it is protected.** The connection to the server is encrypted (TLS). The first time a server shows a
  certificate the app asks you, and a certificate that later changes is refused. Your password is used only for the
  sign-in itself: it goes to the server you named over that encrypted connection to prove who you are, and the app
  does not keep it. Afterwards the app uses a token that stays in Android Keystore. **Change password** sends the old
  and the new password the same way.
- **Adding a server is the choice.** Without an exchange server a broadcast stays on your own network. Once you add
  one, every broadcast of the phone is announced on it from its start, and the broadcast screen names the server and
  the state of the broadcast there. Removing the server keeps broadcasts on your network again. A guest of a server
  announces nothing.
- **Broadcasts of other people.** Opening a shared broadcast connects to the addresses its link names, which can be
  another person's exchange server. That path sends no password and no token, and needs no account. The accepted
  certificate is kept locally for that broadcast, and a changed one is refused.
- **Casts to your own device.** A broadcast sent from another device of your account asks before it plays. Playing
  such casts without asking is an optional setting, off by default, and works only while the app is open.
- **What the server keeps.** That is decided by whoever runs the server. The server program of this app family keeps
  the account, its devices and the records of published broadcasts until the account is deleted, passes broadcast
  content on without storing it, and writes a log whose retention its operator sets and which holds no password,
  token or content. Broadcast identifiers work as access keys and are never written to this app's logs.
- **The developer** receives none of this.

## 6. Encryption: what the exchange server can and cannot see

The text below is the one every program of this family gives, in these words:

> The exchange server connects your devices, so it knows the names of your account, devices, folders and
> broadcasts, sees which devices are online, and how much is sent and when. It cannot open what goes through it:
> folders and their files, broadcasts and the clipboard are encrypted on your device and opened only on your other
> devices or by the people you gave access to. To look inside, whoever runs the server would have to add a device
> of their own to your account, and it would appear in your list of devices.

For this app that means:

- **Encrypted on your device, unreadable to the server:** the sound and the picture of a broadcast you publish
  through the server, including a broadcast sent by your watch. Only your other devices of the account, and the
  people you gave the link, can open it.
- **Visible to the server, because without it the server connects nothing:** your login, each device's name and
  presence, a broadcast's title and mode, who connects to whom and when, the addresses clients connect from, and the
  size and timing of what is sent.
- **No clear fallback.** If a server does not support encryption, the app does not publish a broadcast through it.
- **Not covered:** the connection on your own network (section 3), which does not use the server, and the streams
  you play from other people's servers.

## 7. Google Drive (optional)

Announcing a broadcast via Google Drive is optional and **off by default**. It starts only when you sign in with your
own Google account on the Exchange screen and turn on **Announce via Google Drive** for a broadcast; the choice is
remembered separately for sound and for the camera.

- **One narrow permission.** The app asks Google for a single permission, `https://www.googleapis.com/auth/drive.appdata`,
  a non-sensitive one. It reaches only the hidden app folder that Google keeps for this app family in your Drive.
  The app cannot see, list, read or change any other file in your Drive.
- **Your own account only.** The folder is read and written by your own devices signed in to the same Google
  account. Other people are reached through an exchange server or a link, never through your Drive.
- **What an announcement holds.** A small text file with the broadcast's title and mode, this phone's name and a
  random identifier, the start time, the addresses a listener can try (your local network address and, if you also
  publish through your exchange server, that server's addresses) and a lifetime stamp. A second small file names
  this phone to your other devices. **Never the sound or the picture** - the stream does not go through Google
  Drive.
- **The words of the family:** "Via Google Drive, files and the clipboard are kept in your own Google account, like
  your other files on Google Drive; no exchange server takes part." This app uses Google Drive for announcements
  only. The announcement is not encrypted by this app.
- **When it is removed.** The files are deleted when the broadcast stops. If the phone cannot delete them (for
  example, it lost the network), other devices read the announcement as ended after about 90 seconds, and the phone
  deletes the leftovers the next time it announces.
- **What is read.** While the broadcast list is open, the app reads the announcement and device-name files that
  your other devices wrote to the same folder and shows them in the list under "via Google Drive". Files of a
  broadcast that has ended are not shown.
- **What stays on the phone.** The access token is held by Google Play services; the app does not store it, log it or
  send it anywhere except to Google's Drive service. The e-mail address of the account is not read. The app keeps only
  that you signed in and a random identifier of this phone for the announcement files.
- **Signing out.** **Sign out of Google Drive** stops the app from using Drive and does not touch your exchange
  account. You can also withdraw the access in your Google account settings.
- **Your storage.** The files take a few kilobytes of your Google storage while a broadcast runs. The developer
  receives none of this data.
- **Limited use.** The use of information received from Google APIs adheres to the
  [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
  including the Limited Use requirements. It is used only for the announcements above, is not transferred to
  anyone, is not used for advertising and is not used to train any model.

## 8. Your watch

- The watch app works without the phone. A watch connects to an exchange account only when you choose **Connect
  watch** on the phone's Exchange screen.
- Connecting sends the watch the server address, certificate trust information, the login and a short-lived one-time
  pairing code over the Wear OS Data Layer. The watch creates its own random device identity and stores its own
  encrypted token. Neither the phone's password nor either device's token is transferred. The phone remembers the
  watch it connected so that it can remove it later.
- The watch broadcasts sound only. When the watch is connected to an account, every broadcast is announced on that
  exchange server from its start, over the watch's own Wi-Fi or mobile connection; to keep broadcasts on your own
  network only, sign out on the phone.
- The watch's connection to the server stays open while a broadcast runs, and otherwise only while an audio player or
  broadcast screen is visible and the display is on.
- Signing out on the phone asks the watch to erase its local account and revoke its device identity. If the watch
  is unavailable, the phone revokes it on the server and shows that the cleanup on the watch is still pending.
- The watch face shows the time. It asks for no permission and stores or sends nothing.

## 9. Accounts and deleting them

- **This app creates no account.** It never registers one. An account on an exchange server is made in another
  program of the family or by the person who runs the server; this app signs in to it.
- **Sign out on this device** revokes this device on the server, forgets the token and removes the entry from the app
  (and signs out the watch you connected). The channels you kept stay on the device. The account itself and your
  other devices stay.
- **To delete the account itself,** use the program where it was made, or ask the person who runs the server: only the
  operator of a server can delete an account on it. The portal page "How to delete an account" describes both ways, in
  English, Russian and Ukrainian; the app opens it from the Exchange screen.
- **What may remain after that:** the server's log for the retention period its operator set, copies of broadcasts
  already delivered to people who were listening, and the announcement files in your own Google Drive, which you can
  delete there.
- **Everything the app keeps on your device** is removed by uninstalling it or clearing its storage.

## 10. Components from others

- **Google Play services** is part of Android on your device. The app uses its Wear OS Data Layer to talk to your
  watch and, only if you sign in for Drive announcements, its Authorization API. Google's own terms apply to those
  parts. If you have allowed Android to share usage and diagnostics data, Google Play can show the developer
  anonymous crash and performance statistics; that is a Google facility, not code in this app, and it carries no
  content.
- **Open source libraries** do the playback (AndroidX Media3 and ExoPlayer), networking (OkHttp), the on-device
  broadcast server (NanoHTTPD and RootEncoder), camera access (CameraX), QR decoding (ZXing), cryptography (Bouncy
  Castle), the local database (Room), dependency injection (Hilt), and background downloads (WorkManager). None of them
  sends data anywhere on its own.
  The full list with licenses is in `THIRD-PARTY-NOTICES.txt` inside the app package.
- The app carries no advertising, analytics or tracking library, and the version catalog it is built from has none.

## 11. Children

StreamsPlayer opens streams that other people publish and that the app does not curate, so it is not directed at
children. It knowingly collects no information from anyone, children included.

## 12. This website

This policy is also published as static pages on GitHub Pages. The pages load no script, font, image or tracker from
any other site, run no analytics and keep no data about visitors; GitHub, as the host, receives every page request
under its own [privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).
The pages keep two values in your browser: the theme and the language you picked. They never leave the browser.

## 13. Open source

StreamsPlayer is open source software released under the MIT License. The full source code and build instructions
are publicly available at [GitHub](https://github.com/SerZhyAle/StreamsPlayer_Android).

## 14. Changes to this policy

When the app changes what it stores or sends, this page changes in the same release, and the "Last updated" date at
the top moves. The text on this page is the text of the version of the app that is currently published.

## 15. Contact

For questions, feedback, or support requests:

- **E-mail:** sza@ukr.net
- **Issue tracker:** https://github.com/SerZhyAle/StreamsPlayer_Android/issues
- **Developer / publisher:** Serhii Zhyhunenko (SerZhyAle)
