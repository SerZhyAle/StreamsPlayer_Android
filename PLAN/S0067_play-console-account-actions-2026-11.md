# S0067 - play-console-account-actions-2026-11

**Ticket:** S0067
**Status:** BlockByOtherTask
**Status note:** Blocker: S0047 - items 3 and 4 are read in Play Console after the first upload of S0047 (Draft); items 1, 2 and 5 were decided 2026-11-10, the console declarations stay with the owner
**Complexity:** light - confirmed at the spec pass 2026-11-08: owner-only console declarations and two readings, no code, no build, no test
**Date:** 2026-11-08

## Captured request and migration scope

Parked from S0062 (Google Play policy announcement of 2026-10-29), by the unattended queue pass of 2026-11-08.

The announcement carries account and console items that no code change answers and that only the owner can do in Play Console. Each was read on 2026-11-08 from the linked Google page; the console itself is not readable from this repository (S0047 input D2), so the account state is unknown.

1. Trader status. Every active developer account declares trader or non-trader in Play Console (Manage account > Account details > About you). The page read 2026-11-08 gives 2027-02-01 for existing accounts and "immediately" for a new account; failing to declare can block app updates or remove apps. A trader is someone acting for trade, business or profession; the page says Google makes no legal determination and suggests legal advice when unsure. For an individual account that is a trader, a phone number and an address are shown on the listing. The product is free, MIT-licensed and carries no ads or purchases, but the status is the owner's own declaration. Page: support.google.com/googleplay/android-developer/answer/17594752.
2. Japan payments authorization, deadline 2026-12-01. It applies to a developer that sells into or out of Japan; otherwise payments are held globally. The product has no paid app, subscription or in-app purchase, so it is expected not to apply; the owner confirms there is no payments profile with Japan activity. Form: support.google.com/paymentscenter/answer/17595997.
3. App registration (Play Console Requirements, deadline 2026-09-30 in the Policy Deadlines table). Read the requirement when the app is created in the console (S0047) and follow it there.
4. After the first upload (S0047), read two console figures and record them. DEX optimization in the app bundle explorer: from February 2027 an upload must reach 25 percent obfuscation, optimization and shrinking, enforced only where the DEX is above 10 MB; the last release APKs measured 5.7 MB (phone) and 5.6 MB (wear). Memory in Android vitals: from February 2027 the 90th percentile over 28 days must stay under the page's per-RAM-tier thresholds, for mobile and tablet on Android 13 and later and for devices with 4 GB of RAM or more; there is no figure until the app has users.
5. Optional, no deadline: Play Age Signals, launched for all developers (developer.android.com/google/play/age-signals/use-age-signals-api).

Out of scope here: the listing, the privacy answers and the console forms of the first release stay in S0047 and S0033; the code change for the local network permission is its own ticket.

## Contract boundary

None. The items are declarations and confirmations in Play Console and readings of Google pages. This ticket writes no format, wire shape, algorithm constant or behaviour that another product reads or reproduces, so no catalog amendment is needed.

### Quiz decisions (2026-11-10)
- Trader status -> Non-trader (Free open-source MIT app with no ads or purchases; developer address and phone number not shown on listing).
- Japan payments -> Confirms absence (No payments profile with Japan activity; no paid features or in-app purchases).
- Age Signals (item 5) -> Not at all (Optional API without deadline; app has no age restrictions and does not target children).

## Owner inputs (Approval gate)

- **Trader status:** non-trader (declared in Play Console under Manage account > Account details > About you; product is free, MIT-licensed and carries no ads or purchases)
- **Japan payments:** confirmed absent (no payments profile with Japan activity; product has no paid features, subscriptions or in-app purchases, so 2026-12-01 deadline does not apply)
- **Age Signals (item 5):** not at all (optional API with no deadline; app does not target children and has no age restrictions)
- **Related tickets:** S0062 (the policy announcement that parked this ticket); S0047 (the first Google Play release - items 3 and 4 are read after its first upload, so this ticket closes after it; `Blocker: S0047` is in the journal statusNote from 2026-11-10, because items 1, 2 and 5 are owner console actions the queue cannot take and without the token the ticket would be handed out again with nothing to do)

## Last Audit

**Date:** 26-11-10 20:13 | **Mode:** static | **Outcome:** Partial
**Counts:** PASS 1 | WARN 0 | FAIL 0 | MANUAL 1 | UNCHECKABLE 2 | EXEMPT 1 | RELEASE 0

Static pass, no build and no test: the ticket changes no source, resource or config, so there is no plan with real steps and it stays at `Approved` until its Block. Each item of the Captured request is mapped to a check or to its owner:

- Item 1, trader status - decision recorded (Non-trader, quiz 2026-11-10). The declaration is in Play Console, which this repository cannot read (S0047 input D2): MANUAL.
- Item 2, Japan payments - decision recorded (absence confirmed by the owner, quiz 2026-11-10): PASS.
- Item 3, app registration - UNCHECKABLE until S0047 creates the app in the console (S0047 is Draft).
- Item 4, DEX and memory figures - UNCHECKABLE until S0047's first upload; no figure exists before then.
- Item 5, Age Signals - decision recorded (not at all, quiz 2026-11-10): EXEMPT, optional with no deadline.

Not Verified: a MANUAL item and two UNCHECKABLE items are open. Status moves to `BlockByOtherTask` on `Blocker: S0047`.

### Action items
1. Items 3 and 4 - PLAN/S0047_google-play-first-release.md - read the registration requirement and the two console figures after S0047's first upload, then record them here and re-audit.
2. Item 1 - Play Console, Manage account > Account details > About you - the owner confirms the declared value is non-trader; the page gives "immediately" for a new account and 2027-02-01 for existing ones.

### Manual / on-target
- [ ] Play Console shows "non-trader" under Manage account > Account details > About you for this developer account (item 1). Passing: the declared value equals the decision recorded 2026-11-10.; S0066 (its local network declaration is the owner's on the day the target moves, and it names this ticket); S0033 (the permissions table and the privacy answers of the first release).

