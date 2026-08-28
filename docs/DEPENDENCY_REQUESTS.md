# Dependency requests

OFF.IA treats Memoria.ia as the owner of memory semantics and Resolutive-DB as the owner of durability. OFF.IA must not duplicate either dependency's core behavior.

## DR-001 — Memoria.ia -> Resolutive-DB v1.1 product persistence

**Project:** `marceloroldao/memoria.ia`

**Status:** RESOLVED IN V1 CANDIDATE.

The Memoria.ia v1 candidate now persists the ProductEvidenceService contract through SQLite and native Resolutive-DB v1.1, with restart-safe validation. The final episodic Product API candidate is merged at:

`b4d6363a99fc692283f3f10b2ae851648426794e`

OFF.IA must continue consuming Memoria.ia's persistence boundary rather than addressing BDR records directly.

## DR-002 — Semantic/relational conversational recall

**Project:** `marceloroldao/memoria.ia`

**Status:** RESOLVED IN PRODUCT API CANDIDATE — Memoria.ia Issue #53 closed.

Available product contracts:

- `POST /api/v1/conversation/ingest`
- `POST /api/v1/conversation/resolve`
- `HIT | MISS | UNRESOLVED`
- confidence
- memory IDs
- exact selected source context
- relation metadata
- source provenance / authority / ultimate-source lineage
- correction/supersession support

The implementation remains domain-agnostic and is validated through restart-safe persistence.

## DR-003 — Generic episodic/temporal recall

**Project:** `marceloroldao/memoria.ia`

**Status:** RESOLVED IN PRODUCT API CANDIDATE.

Available contracts:

- `POST /api/v1/episodes`
- `POST /api/v1/episodes/recall`
- ordered/time-addressable generic episodes
- exact selected context
- episode IDs, confidence, order/timestamp/event metadata
- provenance fields
- ambiguity -> `UNRESOLVED`

Final merge commit: `b4d6363a99fc692283f3f10b2ae851648426794e`.

## DR-004 — Android/mobile parity with Memoria.ia v1 candidate

**Project:** `marceloroldao/memoria.ia`

**Tracking:** Issue #50 / draft PR #51.

**Status:** OPEN — BLOCKS claiming that the Android APK is exercising the new v1 candidate semantics.

The current OFF.IA APK consumes the draft native mobile runtime from Memoria.ia PR #51. That ABI predates the stronger Product API semantics now present at `b4d6363...`.

Required mobile parity includes:

- conversational ingest with session/order/source lineage;
- semantic/relational resolve with `HIT | MISS | UNRESOLVED`;
- exact selected context + IDs + confidence;
- provenance/authority + ultimate-source tracing;
- generic episode record/recall;
- BDR-backed restart persistence;
- Android arm64-v8a CI coverage.

OFF.IA must not port the Python semantic implementation into the app as a substitute.

## DR-005 — Mobile memory export / diagnostic snapshot

**Project:** `marceloroldao/memoria.ia`

**Tracking:** Issue #55.

**Status:** OPEN.

OFF.IA needs an official read-only Memoria.ia export/snapshot contract so the user can save/share a versioned JSON/JSONL diagnostic artifact. OFF.IA must not parse raw BDR persistence files.

The Android UI may show the action as pending until Memoria.ia exposes the contract.

## DR-006 — Active conversation window / trajectory-aware resolve

**Project:** `marceloroldao/memoria.ia`

**Tracking:** Issue #56.

**Status:** OPEN.

Real chat tests show that an isolated latest utterance is insufficient for references such as `e o azul?`, `e dos dois?` and `qual modelo?`.

The current v1 Product API candidate `ConversationResolveRequest` accepts `query + session_id`, but not an ordered recent conversation window. The desired boundary is:

`recent conversation window + new message -> Memoria.ia -> trajectory resolution -> persistent retrieval -> minimal selected context -> LLM`

The full window must not automatically be forwarded to the LLM; selection remains Memoria.ia's responsibility.

## Release-gate separation

Memoria.ia Issue #52 controls v1.0 RC readiness for the Product API candidate. Android/mobile parity requests above are integration dependencies for OFF.IA and should not silently expand the RC scope unless the Memoria.ia release explicitly declares them release-blocking.
