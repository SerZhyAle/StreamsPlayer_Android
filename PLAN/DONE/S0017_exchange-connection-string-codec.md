# S0017 - The `FMSEXCH1` connection string: a codec written to the rules, the vector test ready for the day it lands

**Status:** Implemented
**Status note:** Implemented 2026-10-23. Codec, facade, messages and both vendored corpora were in the tree; this pass closed the file-route family recognition, PROVENANCE rows, the check-sync byte comparison and the registry note. Replay green on store and noLegal (104 + 117 cases through codec and facade). Open against the DoD, not this ticket: check-sync.ps1 stops at REPO-STAMP 0.13 vs the RULE-ADOPTION pointer (its vendored-bytes block passes alone), and the full suite is red on 2 tests of the in-progress broadcast-password work. See section Result 2026-10-23.
**Phase:** device exchange, receiver side - independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM against the rules and, once it exists, against the catalog's generated vectors.

## 0. Source

- `PROPOSAL-2026-10-10-exchange-connection-string.md`, ask 1 (the string, the file, the QR payload, what a
  consumer does with `fp`); the Compatibility paragraph; "Conformance artifacts".
- `PROPOSAL-2026-10-10-connection-string-grammar-and-handling.md` (this repository): points 1-14, the grammar
  this codec is written to where the text is silent.
- `DEVICE-EXCHANGE` 5.1 (login form), 7.1 (the `SHA256:` fingerprint form), 7.2 (`publicEndpoint`); `VERSIONING.md`
  4.1-4.3 (match by name, accept a higher MINOR, refuse a different MAJOR cleanly).
- Facts in this tree, 2026-10-10: no codec, no scanner (only `zxing-core` for encoding), no file association,
  `BroadcastDescriptorParser` returns `Valid`, `UnsupportedVersion` or `Malformed` - the shape to follow.
- Catalog: `device-exchange/vectors/` holds only `records-v1.json`; the generator is FMS_W's
  `exchange-server/cmd/exchange-vectors` (its ticket 178).

## 1. Goal

One class that turns text into a connection string or into one clean refusal, and a connection string into its
canonical text, to the rules of the proposal and the recommendations written beside it - and a test that
replays the catalog's vector file the day it exists, so the two implementations agree by bytes and not by
reading.

## 2. Scope

1. **`ExchangeConnectionString`** (pure Kotlin, no Android types): `parse(text): Outcome` and
   `encode(value): String`.
   `Outcome` is `Valid(hostPort, fingerprint?, name?, login?)`, `UnsupportedMajor(prefix)`, `NotExchange`,
   `Refused(rule)`. No partial value, ever; no exception for input.
2. **Rules as named constants**, each with its own test and each marked in the source by the proposal point it
   implements, so a later fold changes one line: prefix `FMSEXCH1:` case-sensitive; trim white space and line
   breaks (a leading U+FEFF too); white space inside refused; at most 512 characters on the trimmed text;
   `&`-separated `key=value`; known key repeated -> refused; known key with a broken value -> refused; unknown
   `key=value` ignored; field order tolerated, written `x`, `fp`, `n`, `u`; percent decoding with `+` as a
   space, strict UTF-8; `x` host (DNS name, IPv4, bracketed IPv6) and port 1-65535; `fp` `SHA256:` plus 43
   standard base64 characters; `n` at most 64 scalar values, no control characters; `u` the form of 5.1.
   Where the proposals disagree with the text of a generated vector, the vector wins (section 3.1).
3. **Three refusals, three messages** (EN/RU/UK, resource strings, TalkBack-safe): a different digit after
   `FMSEXCH` names the contract and says to update the app; another family text (`FMSSFTP1:`, `FMSSFTP2:`, a
   broadcast descriptor, a bare URL) says it is not a server string and says what it looks like; anything else
   says it is not a connection string. One message each, never a stack of them.
4. **File reader.** `readConnectionStringFile(stream)`: read at most 4096 bytes plus one to detect more, refuse
   a longer file without reading on, strict UTF-8, one leading BOM stripped, one trailing line break accepted,
   a second non-empty line refused. A file's content and a pasted text then go through the same `parse`.
5. **The vector test** `ExchangeConnectionStringVectorsTest`: replays
   `vectors/connection-string-v1.json` (valid, tolerated with canonical re-encoding, refused), asserting
   outcomes and `encode(parse(x))` byte for byte. The file is vendored under
   `phone/src/test/resources/device-exchange/` with a `PROVENANCE.txt` of the same shape as `user-playlist/`
   (contract, version, date, SHA-256), and `tools/contracts/check-sync.ps1` compares its bytes with the
   catalog's. **Until the file exists the test is `@Ignore`d with the reason stated and the date, and the
   ticket stays open** - a hand-written fixture is not a vector (`RULES.md` 9).
6. **Hand-written cases** beside it, named as the readings they are (not conformance): the points of the
   grammar proposal that the generated file may leave open.

Not in scope: any UI (S0019), the QR scanner (S0023), trust and enrolment (S0016), the file association and
share target (S0019).

## 3. Constraints and dependencies

### 3.1 Constraints

- Never hand-edit a vector. A disagreement between this codec and the generated file is the generator's text
  or this codec's bug, decided by reading the contract, and written as a ticket or a proposal - not patched in
  the vendored copy.
- No password, token or pairing code is in a string, so none can reach a log from here; the codec still logs
  nothing of its input, only the rule that refused it.
- The same codec serves a pasted text, a file, a QR payload and a share intent; no second parser.
- Written new, not ported: FastMediaSorter has no `FMSEXCH1` codec yet. When its phone codec lands, the shared
  vectors are the comparison; no ledger row is made for a class that is not a copy.

### 3.2 Related tickets

S0016 (the trust rule that consumes `fp`), S0018 (the one entry's identity from `(host:port, login)`), S0019
(the surfaces), S0020 (a descriptor, unlike a connection string, names a server with no sign-in), S0013
(check-sync).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0018, S0019, S0013.
- **Grammar:** answered 2026-10-10 - the portfolio owner accepted **all** recommendations of
  `PROPOSAL-2026-10-10-connection-string-grammar-and-handling.md` (points 1-14, the MIME type name and the
  destination-first sign-in rules). They stand as rules, each still a named constant; the generated vector
  wins where it differs, and the difference is a ticket.
- **Scanner:** answered 2026-10-10 - CameraX with `zxing-core` decoding; the camera reader is S0023 and
  hands this codec the scanned text like any other route.

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain` exits 0; the
  new test classes and their counts cited.
- The vector replay ran green against the generated file **or** the ticket is `BlockExternal` with the
  vector's absence named; never `Implemented` with the replay ignored.
- `tools/contracts/check-sync.ps1 -CatalogRoot <catalog>` exits 0 including the new vendored-bytes comparison.
- The registry row's note says which readings are conformance and which are this product's own.

## Result 2026-10-23

Where the scope landed. Most of it was already in the tree, written under other tickets of the exchange block while
this one waited; this pass checked it against the scope line by line and closed the rest.

- **Codec.** `domain/model/ExchangeConnectionStringCodec` (reader, writer, file reader, the contract's refusal
  vocabulary) and `domain/model/ExchangeConnectionString` (the value). `data/exchange/ExchangeConnectionString` is
  the facade of scope item 1: `parse`, `readConnectionStringFile`, `encode`, `Outcome` (`Valid`, `UnsupportedMajor`,
  `NotExchange`, `Refused`) and the named `Rules`, each with a hand-written test marked by its grammar point. The
  codec logs nothing of its input.
- **Three messages.** `exchange_connection_unsupported_major`, `exchange_connection_not_exchange`,
  `exchange_connection_invalid` in `values`, `values-ru`, `values-uk`; `formatUserMessage` picks one.
- **Vendored vectors.** `phone/src/test/resources/device-exchange/` holds `connection-string-v1.json` and
  `fmsw-224/connection-string-v1.json`; `PROVENANCE.txt` now records both (and the other two files of the
  directory) at DEVICE-EXCHANGE 1.7, and `ExchangeConnectionStringVectorsTest` (domain/model) asserts the two rows.
  `tools/contracts/check-sync.ps1` compares all four vendored vector files with the catalog's bytes. No `@Ignore`
  exists anywhere in the tree.
- **Replay.** Both corpora (104 + 117 cases) are replayed through the codec (outcome, reason, `looks`, fields,
  canonical text) and through the facade (`Valid` / `UnsupportedMajor` / `NotExchange` / `Refused` by the artifact's
  verdict).
- **Closed in this pass.** The file route recognised another family's text differently from a paste (an
  `FMSSFTP2:` file showed the artifact's code `share-code` in the message, untranslated, and a later `FMSSFTP3:`
  did too); both routes now share one recognition, and the family is named by its own prefix.
- **Registry.** The `DEVICE-EXCHANGE` row of `StreamsPlayer Android` says what is conformance and what is this
  product's own reading.

Open against section 4, neither of them this ticket's work: `check-sync.ps1` exits 1 at its first stage because the
catalog moved `REPO-STAMP` to 0.13 on 2026-10-23 and the `RULE-ADOPTION` pointer still names 0.12 (the vendored-bytes
block passes when run alone); and the full unit suite is red on `ExchangeSignInOnlyTest` and
`KeystoreBroadcastPasswordStoreTest`, which fail the same with this pass's main-source edits reverted and belong to
the broadcast-password work in progress in the tree.

## S4203 decisions 2026-10-14

S4203 is the release-46 device-exchange milestone of FastMediaSorter Android (FMS_A). The owner's answers of
2026-10-14 to its questionnaire are final; what reaches this ticket:

- **In release 46.** First of the exchange block in `PLAN/RELEASE_QUEUE.md`.
- **Unblock condition.** This ticket is unblocked once FMS_A S4212 folds the pending `DEVICE-EXCHANGE`
  proposals, the FMS_W vectors proposal (`PROPOSAL-2026-10-11-fmsw-vectors.md`) first; S4212 names this ticket as
  what that fold unblocks. The status note was rewritten through `spec_catalog/update` to say so.
- **Fact read 2026-10-14 (catalog, read-only).** `vectors/connection-string-v1.json` has been among the
  contract's binding vectors since 2026-10-11, byte-identical to the proposal's copy (SHA-256
  `4e75c0da11515f4b4e77a46e24751b55f874d699a0362de26cd591df79a8d26d`) and named in the contract header's
  `artifacts`; the contract is at 0.20. The file this ticket's old status note waited for therefore exists;
  what the fold adds is the server corpora (`frames-v2.json`, the server `records-v1.json`) under their own
  names. When the ticket is taken up: vendor the file under `phone/src/test/resources/device-exchange/` with its
  `PROVENANCE.txt`, remove the `@Ignore`, replay, and add the vendored copy to `tools/contracts/check-sync.ps1`.
  The server corpora, where a receiver or a broadcaster reads them, are replayed by S0034 item E.
- **Release coupling.** Package 1 carries the exchange client (there is no feature gate), and
  `docs/contracts/DEVICE-EXCHANGE.md` says a release that carries it waits for this replay green. This ticket is
  therefore a precondition of package 1's release although it stands in `--`; when package 1 ships is the
  owner's call.
