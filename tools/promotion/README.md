# tools/promotion - the free-promotion sources and their checks (S0014)

The promotion of the product is written from one source, [POSITIONING.md](../../POSITIONING.md), and checked here: no other
product's name and no piracy word in any published text, the Play listing inside its limits and in the pillar order, honest
topics, one contact. Nothing in this folder sends anything anywhere; the checkpoint script only reads what a stranger could read.

| What | Where |
| --- | --- |
| The forbidden terms, two scopes, each with a reason | `tools/promotion/forbidden-terms.json` |
| The positioning source: sentence, pillars, worksheet, topics, search terms | `POSITIONING.md` |
| The Play listing source: title, short and full description in en, ru, uk | `docs/play/PLAY_LISTING.md`, `-ru.md`, `-uk.md` |
| The internal research notes and the go ledger (product names allowed here only) | `PLAN/S0014_free-promotion-campaign/` |

| Script | When | What it holds | Verdict line |
| --- | --- | --- | --- |
| `assert-promotion.ps1` | any edit of a README, a string resource, the listing, the positioning source or the list; the pre-release sweep runs it | forbidden terms in published sources and in the discoverability fields, the listing limits (30, 80, 4000), at most twenty valid topics, the one contact `sza@ukr.net`, the pillar terms of POSITIONING.md section 2 in their order | `assert-promotion: PASS (..)` |
| `test-promotion-gate.ps1` | release; after editing the gate or the list | a seeded copy with one defect per case (a name, a piracy word, a free-TV claim in two languages, the field-only term in a topic and in prose, the limits, the topics, a foreign contact, a missing pillar, a broken pattern, a missing file); each must be named exactly | `promotion-gate-selftest: PASS (14 cases)` |
| `write-checkpoint.ps1 [-Baseline]` | the first outward step, then at most fourteen days apart while outreach is active | the counters of the S0014 baseline table read now into `PLAN/S0014_free-promotion-campaign/metrics/<date>-checkpoint.md` (`-Baseline`: `-baseline.md`); a counter that cannot exist is `not applicable`, a failed read `unreadable` | `write-checkpoint: wrote ..` |

Every script exits `0` passed, `1` inspected and defective, `2` could not verify (`CHECK-VERDICT`); the verdict line is last on stdout.

## The two scopes of the list

- `published` entries bind every published source: the README, the ledger, the positioning source, the listing source, the
  privacy policies, the portal sections, the help sources, every string resource of the three modules and the pages of `site/`.
- `fields` entries bind only the discoverability fields: a topic, a search term, the Play title and the short description. The
  playlist term lives here: a plain feature sentence in a description may name it, a keyword may not.

## Retiring a word or a name

Add an entry to `forbidden-terms.json` with its scope, a pattern and a reason of at least four words, and fix every hit in the
same edit. A name read in a later research pass goes into the internal notes first, then into the list. The list is the one place
product names are written in the tracked tree; it is never a published source.

## Adding a source or a language

A published source the gate does not list is a hole: add its path to `Get-Sources` in `assert-promotion.ps1` in the same edit
that creates it, and its pattern to `tools/promotion/test-promotion-gate.ps1` if it is a new kind of source.
