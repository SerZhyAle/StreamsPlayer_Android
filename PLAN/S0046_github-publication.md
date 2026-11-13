# S0046 - Publish the repository, the site and the sideload channel on GitHub

**Ticket:** S0046
**Status:** Partial
**Status note:** Part A done 2026-11-13: public remote, Pages live (run 43438392545 green), metadata; open: fresh-clone proof (release tier), social preview upload (owner), homepage after the landing - S0072. Part B gated.
**Date:** 2026-10-26
**Phase:** package 1 for part A (the repository and Pages: S0033's open owner step and S0047's privacy URL need it); part B
(GitHub Releases, the sideload channel) opens only after the first Google Play release (S0010, В39)
**Verification level:** part A - the repository is public with the intended content only, a fresh clone builds and tests, the
three privacy pages answer 200 from Pages; part B - one release whose assets are signed with the production key and whose
update over a prior install is proven. The static predicates of section 4, each with its command.
**Owner:** the StreamsPlayer Android session of this repository. The remote, every push, every tag and every release are the
owner's (`CLAUDE.md` section 6; invariant 4).

## Captured request and migration scope

Owner, 2026-10-26, "create specifications", item 1 of four, verbatim: "about publish it in github,".

Read as: put the product on GitHub under the name decided in S0010 (public repository, source, the site on Pages) and, as its
own later step, publish release packages there. Part A is the first; part B is the second and is gated.

## 0. Source

- S0010 decision 4.5 (owner, 2026-10-10): the remote is `SerZhyAle/StreamsPlayer_Android`, Public. Stands.
- S0010 decision В39 (owner, 2026-10-14): the first release goes straight to Google Play; "GitHub Releases comes after it"; the
  `noLegal` sideload variant has no channel until a sideload channel opens; developer verification for sideload and the
  `INSTALL-TRUST` contract are decided and bound the day it opens.
- `CLAUDE.md`: section 3 (licence MIT, one signature for store and sideload builds, signing key outside the repository),
  section 4 (the ticket store), section 6 (remotes, tags, uploads and keys are the owner's).
- S0033 section 5: "Open for the owner: push site/ and enable Pages (no remote yet)". Part A is that step.
- S0014: P4 (repository metadata), P8 (Obtainium needs GitHub Releases), decision 6 (AI assistance is never hidden), decision 3
  (forbidden terms), non-goal "no telemetry".
- `.github/workflows/pages.yml` (publishes `site/` with `upload-pages-artifact`, `path: site`) and `android-build.yml` exist.

## 1. Goal

**Part A.** Anyone can open `https://github.com/SerZhyAle/StreamsPlayer_Android`, read what the product is, clone it and build
it with the documented commands, and the family page answers at `https://serzhyale.github.io/StreamsPlayer_Android/` with the
three privacy pages the store needs. Nothing private is in the repository or its history.

**Part B.** A person who does not use Google Play downloads a signed `noLegal` package from GitHub Releases, can verify it, and
Obtainium can follow the repository.

### Baseline - read 2026-10-26

| What | Read how | Value |
| --- | --- | --- |
| Git remote | `git remote -v` | none |
| Repository `SerZhyAle/StreamsPlayer_Android` | `curl -L https://github.com/SerZhyAle/StreamsPlayer_Android` | 404 |
| Windows sibling `SerZhyAle/StreamsPlayer` | same | 200 (the name is taken; that is why this repository carries `_Android`) |
| Pages `https://serzhyale.github.io/StreamsPlayer_Android/privacy.html` | `curl -L` | 404 |
| History | `git log --oneline` | 2 commits, 0 tags; the product tree (`phone/`, `wear/`, `watchface/`, `exchange-core/`, `build-logic/`, `tools/`, `site/` and the rest) is in neither commit, so the first push decides what the public history contains |
| Licence file | `LICENSE` | present (confirm it is MIT at execution) |
| Leak globs | `.gitignore` | keystores, `keystore.properties`, tokens, secrets, `google-services.json`, `play-service-account*.json`, `local.properties` |
| `PLAN/` | working-tree `.gitignore` vs `CLAUDE.md` section 4 item 6 | the working-tree `.gitignore` now ends with `PLAN/`; `CLAUDE.md` still says `PLAN/` is tracked because there is no remote. The two disagree - input D1 |
| Workflows | `.github/workflows/` | `pages.yml` (site), `android-build.yml` (build); neither has been run |
| `gh` CLI | `command -v gh` in Git Bash on this machine | not found on that `PATH`; check PowerShell, else the owner's browser does the console steps |
| Release channel declared | `.sza-canon.json` | `["play"]`; `github` is added in part B |
| Signing | `CLAUDE.md` section 3, S0010 | key in `P:\ANDROID\StreamsPlayer_credentials` (outside the repository); unsigned release builds when no key is configured |

## 2. Scope

Tags: **A** agent alone, **A·GO** agent after the owner's go naming the step, **A→O** agent drives and the owner acts at one
point, **O** owner only.

### Part A - the repository and the site

- **A1 Publication hygiene - A.** Before any push, on the exact set that would be pushed: (i) every path matched by a leak
  glob or by a signing-material extension is absent from `git ls-files` and from the full history; (ii) a secret scan over the
  tracked files and over `git log -p` (a secret scanner if installed, else the leak check of `tools/release/pre-release-sweep.ps1`
  plus a pattern grep for private keys, `client_secret`, bearer tokens, passwords, addresses and device ids of the owner),
  including `docs/DRIVE_OAUTH_CLIENT.md` and every test fixture; (iii) no personal data, local absolute path of the owner's
  machine or private ticket reference of another repository outside what input D1 allows; (iv) `LICENSE` is MIT and the
  third-party notices are generated (`tools/third-party-notices/`). Evidence in `temp/S0046/`. Publishing is one-way: GitHub
  content may be cached or indexed even if deleted.
- **A2 First-commit shape - A, input D2.** Build the commits the owner chose from the tree as it is on disk.
- **A3 Public front page - A.** `README.md` as S0045 P4 writes it (shipped-fact prose, no ticket id, the AI-assistance line of
  input D4, the contact `sza@ukr.net`), and the documented build and test commands that a fresh clone needs. No link to a file
  that is not in the public tree.
- **A4 Create the repository and push - A·GO or O.** Create `SerZhyAle/StreamsPlayer_Android` public, add `origin`, push
  `main`. The owner's go names this step.
- **A5 Enable Pages - A→O.** `pages.yml` uses `upload-pages-artifact`, so Pages must be set to the **GitHub Actions** source (a
  legacy branch source cannot serve `site/`). Set it through `gh api` if available, else in the owner's browser; run the
  workflow once (`workflow_dispatch`); confirm the privacy URLs answer. This closes S0033's open step.
- **A6 Repository metadata - A·GO (S0014 P4).** Description and homepage from `POSITIONING.md` (homepage: the landing once it
  answers, else the Pages root), topics at most 20 and none in the forbidden-terms list, social preview 1280x640 PNG under 1 MB
  uploaded through the owner's browser (A→O at sign-in), Issues and the support route per input D3, `SECURITY.md` and the
  contact in the support surfaces per the canon's `SUPPORT_AND_FEEDBACK`.
- **A7 Fresh-clone proof - A.** In an empty directory: `git clone`, create the gitignored `local.properties` with the SDK
  path, run the documented unit-test and debug-build commands. Anything missing from the clone is a defect of the tree, fixed
  before the proof is accepted.
- **A8 CI first run - A.** `android-build.yml` runs on the first push with no secret; read it before the push to confirm it
  needs none and does not sign. A red first run is a finding, fixed or recorded with its reason.
- **A9 Hand-over - A.** Record the live URLs in S0033 (closing its open step), S0045 and S0047 (privacy URL for the console).

### Part B - GitHub Releases (the sideload channel)

Opens only on the owner's exact request after the first Google Play production release is live (В39). Every step is one-way and
goes through the `release` skill flow; this ticket never triggers it.

- **B1 Declare the channel - A.** `.sza-canon.json` `channels` gains `github`; `INSTALL-TRUST` binds: a sideload download page
  with the signer's certificate fingerprint, the checksums and how to verify; its text in EN, RU, UK on the site (generated).
  Sideload developer verification is decided that day (S0010).
- **B2 Packages - A.** The `noLegal` variant of `phone` and `wear` and the watch face (set per input D5), built with the
  stamped version pair per family (`-Psp.versionName`, `-Psp.versionCode`, one module per invocation), signed with the one
  production signature (`CLAUDE.md` section 3), `SHA256SUMS` beside them, `THIRD-PARTY-NOTICES.txt` inside every package that
  bundles third-party binaries (invariant 12). Stable asset names so Obtainium's pattern matches.
- **B3 Release - O (A·GO for the mechanical steps).** The tag follows `.sza-canon.json` `versionShape`
  (`^v\d+\.\d+\.\d+\.\d+$`) and is derived, never hand-bumped (invariant 8); "What's new" is in every listing surface before the
  tag; the pre-flight verdict PASS naming the version exists first (invariant 5); the owner asked for this exact release.
- **B4 Update proof - A.** The release APK installed over a real prior install (the Play build or the previous APK) keeps its
  data (`adb install -r`, then read the app state), recorded (invariant 6).
- **B5 Obtainium - A·GO (S0014 P8).** The "Add to Obtainium" link on the releases page and in the README.
- **B6 Durable links - A.** The landing and README link `/releases/latest`, never a version (S0045 P3).

### 2.1 Non-goals

- No release, tag, store upload or marketplace publish from this ticket's part A; part B only on the owner's exact request.
- No change of a frozen anchor: application ids, signing key, the repository name decided in 4.5.
- No telemetry, analytics or tracker added to the app, the repository or the site (S0014 non-goal).
- No GitHub Discussions, Sponsors button, wiki or paid feature unless input D3 asks for it.
- No second signing key: store and sideload builds carry one signature (`CLAUDE.md` section 3).

## 3. Constraints and dependencies

### 3.1 Constraints

- Work on the files as they are on disk; the tree state is not a gate except at the commit step the owner asks for.
- Never `--force`, `--no-verify` or a bypassed signing step; agent commits carry the co-author trailer; never commit or push on
  the owner's behalf without the owner's go (invariant 18).
- A listing, site or README sentence is a present-tense fact of the shipped build (invariant 17).

### 3.2 Related tickets

S0010 (decisions 4.5 and В39, the dry-run pipeline), S0014 (P4, P8, decisions 3 and 6), S0033 (the Pages step it waits on),
S0045 (README and landing), S0047 (Play; part B waits for it), S0048 (other repositories link the repository and Pages URLs
only after they answer), S0025 (audit evidence that may be public).

### 3.3 Owner inputs (Approval gate)

- **D1 What is public besides the product.** `PLAN/` (the tickets quote the owner's decisions and name internal tickets of the
  other repositories), `docs/BOOTSTRAP_HANDOFF.md` (names another repository's tickets and the catalog path) and `docs/`
  records such as `S0007_WEAR_VERIFICATION.md`. The working-tree `.gitignore` ignores `PLAN/`; `CLAUDE.md` section 4 item 6
  still says tracked. Options: (a) **`PLAN/` stays local and ignored, the handoff stays out of the README and is not linked;
  `CLAUDE.md` section 4 item 6 is amended to match** (recommended: nothing in the product needs the tickets, and they carry
  private quotes - the owner then keeps a backup of `PLAN/` somewhere else, since the remote will not hold it), (b) everything
  public, (c) `PLAN/` in a separate private repository. `.gitignore` and `CLAUDE.md` are then made consistent in the same edit.
  Status of the two on 2026-11-08 (S0051): the `PLAN/` line was removed from `.gitignore`, because `CLAUDE.md` section 4 item 6 is
  the decision on record and no remote exists. So `git add -A` now takes `PLAN/`. This input is still the owner's and still
  binds before the first push: option (a) restores the line and amends `CLAUDE.md`; acceptance 3 reads `git ls-files` either way.
- **D2 History shape.** Options: (a) **keep the two existing commits and add one commit holding the product tree**
  (recommended: honest and simple), (b) one squashed "initial public commit", (c) commits grouped by module and tooling.
- **D3 Support route.** Issues on or off, the issue templates, and the support surface the README points to. Recommended: Issues
  on, the contact `sza@ukr.net` repeated in the README (`SITE-FAMILY-MAP` rule 3).
- **D4 AI-assistance line (S0014 decision 6).** The agent proposes one sentence for the README for approval, for example "This
  project is developed with heavy AI assistance; the author reviews every change." The owner approves or writes their own.
- **D5 Part B scope.** Which packages go on the release page (phone `noLegal`; the Wear OS app; the watch face) and how soon
  after the Play release. Recommended: all three, after the Play production release is live and the owner's go.
- Settled and not asked again: the repository name and visibility (4.5), Play first (В39), the licence (MIT).

### Quiz decisions (2026-11-13)

Asked by the side session that repeats DeviceInfo's public presence for this product, on the owner's order "Повтори всё
выполненное для новго Streams_mob" (relayed from the deviceinfo-mob session):

- D1 -> (b) everything public, as DeviceInfo: `PLAN/` and `docs/BOOTSTRAP_HANDOFF.md` are tracked and pushed; `CLAUDE.md`
  section 4 item 6 and `.gitignore` already say so. Before the push the one private address in the tree (an owner quote in
  S0014) was redacted.
- D2 -> (a) keep the two existing commits and add one commit holding the product tree.
- D3 -> Issues on, the contact `sza@ukr.net` in the README; wiki and projects off (canon Google Play reference, section 1).
- D4 -> the README carries "This project is developed with heavy AI assistance; the author reviews every change."
- D5 -> not asked (part B).
- The go for A4 and A5: the owner's order above, relayed with "the initial import push and the Pages workflow push are
  covered by this order". A6 metadata is the same order (canon reference, section 1 step 6); the social preview upload stays
  with the owner.

## 4. Acceptance

Part A binds with the first push; part B lines are marked and bind with the first release.

- [ ] 1. `git remote -v` shows `origin` `https://github.com/SerZhyAle/StreamsPlayer_Android.git`; `curl -s -o /dev/null -w "%{http_code}" -L`
  on the repository URL returns 200; `gh api repos/SerZhyAle/StreamsPlayer_Android --jq '{visibility,default_branch,has_issues}'`
  (or the same read in the browser) shows public, `main`, and the input D3 value.
- [ ] 2. Leak check: no path matching a leak glob in `git ls-files` or in `git log --all --name-only`; the secret scan over the
  tree and the history reports nothing; evidence file in `temp/S0046/` names the scanner, its version and the exit code.
- [ ] 3. What is public matches input D1: if (a), `git ls-files PLAN docs/BOOTSTRAP_HANDOFF.md` is empty and no tracked file
  links to either; `CLAUDE.md` and `.gitignore` agree.
- [ ] 4. Fresh clone: in an empty directory the documented commands exit 0 for the unit tests and the debug build of `phone`,
  `wear`, `watchface`; no file outside the clone and the SDK is needed.
- [ ] 5. `https://serzhyale.github.io/StreamsPlayer_Android/privacy.html`, `privacy-ru.html`, `privacy-uk.html`, `sitemap.xml`,
  `robots.txt` answer 200 and equal `site/` (`tools/site/build-site.ps1 -Check` exit 0 before the push).
- [ ] 6. Both workflows ran on the first push; `gh run list --limit 5` (or the Actions page) shows the result of each; a red run
  has a recorded finding.
- [ ] 7. Metadata: description, homepage, at most 20 topics none in the forbidden-terms list; the 1280x640 preview under 1 MB
  and `usesCustomOpenGraphImage` true (GraphQL read).
- [ ] 8. README predicates of S0045 section 4 item 5 hold on the pushed README.
- [ ] 9. The owner's go for each of A4, A5 and A6 is quoted in a go-ledger row of this ticket.
- [ ] B1. `.sza-canon.json` declares `github`, the sideload page answers 200 in EN, RU, UK with the signer fingerprint, and the
  canon compliance check exits 0.
- [ ] B2. The release tag matches `^v\d+\.\d+\.\d+\.\d+$`; every asset is verified with `apksigner verify --print-certs` and its
  certificate SHA-256 equals the fingerprint the sideload page prints; `SHA256SUMS` matches the downloaded files;
  `THIRD-PARTY-NOTICES.txt` is inside each package that bundles third-party binaries.
- [ ] B3. The release was requested by the owner for that exact version and has a PASS pre-flight verdict naming it; an update
  over a real prior install is recorded.
- [ ] B4. `/releases/latest` is the only release link on the landing and in the README.

## 5. Risks

- **One-way publication of private material** (history included): A1 runs on the exact push set, history included, first.
- **Pages cannot serve `site/` from a branch**: A5 sets the Actions source, otherwise the privacy URLs stay 404 and the store
  review stalls.
- **Name collision** with the Windows repository in search results (S0014 decision 5): the description and topics name the
  platform.
- **A public repository invites AI-rule venues to apply their rules** (S0014 decision 6): the disclosure is explicit.
- **Sideload developer verification** may apply to part B packages (S0010): decided the day part B opens, not assumed here.
- **A tracked `PLAN/` on a public remote** exposes owner quotes; an ignored `PLAN/` has no remote copy: input D1 states the
  trade-off.

## 6. Open questions

1. D1 to D5 above.
2. Whether `gh` is installed for PowerShell on this machine (not on the Git Bash `PATH` on 2026-10-26); if not, A4 to A6 are
   browser steps for the owner with exact values prepared by the agent.
