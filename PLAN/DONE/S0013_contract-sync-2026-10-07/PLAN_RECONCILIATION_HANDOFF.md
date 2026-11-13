# S0013 reconciliation handoff - 2026-10-10

This is prepared work for the canon and FastMediaSorter source sessions. Neither session has been
contacted by this run. Those repositories' sealed documents and source files were not edited here.

## Source carry

`source-carry-candidate.patch` compares the seven repaired core files against the current extraction
source, reversing only the Java package rename. **Do not apply it wholesale.** It includes product
adaptations and source-only facilities (for example the shared favicon cache and app-wide database)
that the source session must retain. It is a line-level review aid for the repairs in S0003 section 5.1:

1. Preserve collection storage on unknown versions/malformed roots; supported empty collections clear it.
2. Refuse unsupported/malformed playlist URLs, HLS manifests and optional JSON; parse quoted-title
   commas, use host fallback, and bound both local/network input to 32 MiB before mutation.
3. Deduplicate normalized URLs case-sensitively, preserve existing rows and report skipped/duplicate counts.
4. Bounds-check favicon indices before multiplying coordinates, including Int.MAX_VALUE and 1073741824.
5. Retry HTTP 404 inside the explicit, cancellable refresh at 3/6/12 seconds; other HTTP failures do not merge.

The destination regression tests and S0003 section 5.1 identify each behavior. Review the equivalent
source tests, retain source-specific caching/DI/schema behavior, then port only accepted changes back.
No ledger hash was refreshed to disguise destination differences.

STREAM-BANK document 03 section 2.4 requires an additional source-first correction: unknown/stale topic
values become General; blank stays blank and known rubric ids remain canonical. Both Android source
and destination currently preserve unknown topics. The producer already uses General. A dated registry
exception carries this difference until 2026-12-31; the owner proposal requests compliance, not a silent
local rewrite of the normalizer.

## Catalog/canon requests

- MEDIA-CLASSIFICATION 0.10 sections 1, 2 and 4: the filed scope proposal requests withdrawal of the
  unsupported Android category-consumer declaration. The Windows product remains a consumer. The
  Android row stays pending with no implementation/read version until the owner decides.
- STREAM-BANK 2.3: the filed reconciliation proposal requests source carry/topic correction and an
  owner update to the obsolete Android consumer record (it still says no reader). The favicon input
  ceiling is 30 MiB; the watch receives channels from the phone. Artwork-manifest/on-demand tile-pack
  support remains absent, with a dated registry exception.
- REPO-STAMP 0.12 rule 4 / RULE-DELIVERY 0.12 rule 5: the existing unknown-age reader delivery proposal
  applies. Fresh isolated probes reproduce plugin 2026.1002.3's incorrect WARN/exit 0 with missing or
  invalid dates and a differing digest. Expected: error/exit 1. Equal digest correctly gives no finding.
- ICON-SET: the domain header/version carrier is 0.29, while REGISTRY section 1 still says 0.28.
  Local pointers and this product's own row use the domain header. Only the catalog owner fixes the index.

## Remaining verification

S0013 stays Partial until the source-session carry or explicit parity decision is recorded and the
scope/delivery requests are resolved. Pending broadcast, input and icon surface checks remain their
existing feature tickets; no device handshake or visual sweep is claimed by this run.

The fresh drift check exits 1: 695 in step, three broadcast-control source files changed (manager and
portrait/landscape layouts). These are separate from the core repair handoff; review their carry in
the broadcast feature work before any release. The drift check does not establish destination equality.
