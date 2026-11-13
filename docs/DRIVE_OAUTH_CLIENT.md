# Google Drive announcements - registering the Android OAuth client

Ticket S0029. The Cloud project and the OAuth clients are the owner's accounts: nobody else registers them, and
nothing below puts a secret, a client id or a key file in this repository. Google Play services identifies the
app by its package name and signing certificate, so the code carries no client id at all.

## Why this is needed

The Drive channel of `DEVICE-EXCHANGE` (section 8.2, item B) works across the family only when every product's
OAuth client lives in **one Google Cloud project**: one Google account then sees one hidden app folder from
FastMediaSorter Android, FastMediaSorter for Windows and this app. The premise was proven on 2026-10-23 for an
Android client and a Desktop-app client of the family project. A client in a project of its own would see only
this app's own announcements, so that setup is not used.

## Steps (family project, owner at the keyboard)

1. **API.** APIs and services - Library - Google Drive API - Enable (once per project).
2. **Consent screen.** User type External, publishing status **In production** (never Testing: in Testing a
   refresh token expires after 7 days). The only scope is `https://www.googleapis.com/auth/drive.appdata`, which
   Google classes as non-sensitive, so no verification review is needed.
3. **Android clients.** Credentials - Create credentials - OAuth client ID - Application type **Android**. One
   client for each signing certificate that can sign an installed copy, all with the package name
   **`com.sza.streamsplayer`** (frozen anchor, `CLAUDE.md` section 3):
   - the **debug** certificate of each development machine (sign-in on a debug build);
   - the **upload key** (`P:\ANDROID\StreamsPlayer_credentials`, outside the repository);
   - the **Play app-signing key** (Play Console - App integrity), which is also the certificate of sideload APKs.

   SHA-1 of a certificate: `keytool -list -v -keystore <keystore> -alias <alias>` (it asks for the keystore
   password, which stays with the owner; the fingerprint itself is not a secret). The debug certificate of the
   default `%USERPROFILE%\.android\debug.keystore` answers to the public password `android`.
4. **Nothing to paste.** No client id, secret or `google-services.json` is added to the project.

## Check on a phone

With the owner's Google account on the phone: install a build signed by a registered certificate, open
Exchange, tap **Sign in with Google**. Google shows its consent screen for the hidden application data folder
only. An immediate failure ("Google sign-in did not complete") with a developer error in the log means the
package name and SHA-1 pair is not registered yet.

## Record (no secrets)

| Date | Certificate | SHA-1 (first 8 hex) | Registered by |
|---|---|---|---|
| | debug | E8:F8:B9:82 | |
| | upload | | |
| | Play app signing | | |
