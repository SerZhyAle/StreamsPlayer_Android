# Documentation quality - permanent variants

**Date:** 2026-11-08, ticket S0051. Contracts read: `DOC-INTERNAL-QUALITY` 0.11, `DOC-EXTERNAL-QUALITY` 0.13, `CONTEXT-HELP` 0.2.

Where this repository holds a rule of those contracts in a form of its own, or does not hold it because another party does,
the reason is written here once and the registry rows point at the heading (`docs/DOCUMENT_REGISTRY.jsonl`, field
`design_record`). A variant is not a debt: debt has a ticket and a date, a variant has neither.

## V1 The portal is another product's corpus

The shared media exchange portal is published by FastMediaSorter Android (`DOC-EXTERNAL-QUALITY` section 9 item H). This
repository writes its own sections in three languages (`docs/PORTAL_SECTIONS_STREAMSPLAYER.md`), delivers them and links the
pages it holds (`docs/MEDIA_EXCHANGE_PORTAL.md`). Rules 2, 5 and 7 of the portal's pages, its sitemap, its index and its
page count bind the publisher. What binds here and is gated here: the blocks' language set, their freshness and their terms.

## V2 The help published on the site is not a page of the documentation corpus

The owner decided on 2026-11-08 to publish the help as `help/{en,ru,uk}/` with the first release (S0045 D2, option a). That
retires the variant this ticket opened with, "the offline help is not a site page". What replaces it comes from
`DOC-EXTERNAL-QUALITY` section 8 item O and `CONTEXT-HELP` section 7 item G: a published help page is out of `sitemap.xml`, owes
no SEO block, carries `<meta name="robots" content="noindex">`, and `robots.txt` does not block it. Its address is permanent. It
stays one source and one generator with the bundled copy. `tools/site/build-site.ps1 -Check` holds this from the day
`site/help/` exists.

## V3 Screenshots are held by the static form of rung 2

`CONTEXT-HELP` section 7 item D allows rung 2 without a device: a capture record stores a hash of what its picture can show,
and the gate fails a record whose inputs moved. `tools/help/assert-help-map.ps1` does that (`STALE-SHOT`). A regeneration from
a build is how a stale record is cleared, not how staleness is found: it needs an emulator, and a byte diff of two JPEG
captures says nothing about the screen. The freshness of the pictures in the sense of `DOC-EXTERNAL-QUALITY` rule 6 is this
hash.

## V4 Contract sync does not run on a CI runner

`tools/contracts/check-sync.ps1` reads the shared contracts catalog, which lives on the owner's machine and is no git
repository, so a runner cannot see it. `CHECK-VERDICT` rule 2 forbids reading that as a pass. It runs in the pre-release
sweep, on the machine that holds the catalog, and CI runs every documentation gate that needs only the tree.

## V5 Fewer than 25 public pages

By `DOC-EXTERNAL-QUALITY` section 8 items B and N the corpus is below the search-index threshold: one document in the primary
locale (the privacy policy) plus the landing, which item M places outside the contract; the help does not count (item O). The
conforming form is the subject index that links every help page, held complete because the generator renders it from the
page sources and `tools/help/generate-help-pages.ps1 -Check` fails any other content.

## V6 One registry, and what stays outside it

The corpus is tracked as a whole, so `DOC-INTERNAL-QUALITY` item K asks for no local registry. The ticket store under `PLAN/` is
outside the registry on purpose: its journal `PLAN/spec-catalog.jsonl` is its registry, written only through the harness. The
exact exclusions and their reasons are `tools/docs/registry-ignore.txt`. The canon's `document_registry/generate` is not used:
it writes a repository-root sitemap for a site served from the repository, and this site's `sitemap.xml` comes from
`tools/site/build-site.ps1`.
