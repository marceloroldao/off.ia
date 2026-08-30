# Dependency requests

OFF.IA treats Memoria.ia as the owner of memory semantics and Resolutive-DB as the owner of durability. OFF.IA must not duplicate either dependency's core behavior.

## DR-001 — Memoria.ia -> Resolutive-DB v1.1 product persistence

**Project:** `marceloroldao/memoria.ia`

**Status:** RESOLVED IN V1 CANDIDATE.

The Memoria.ia v1 candidate persists ProductEvidenceService state through the supported persistence boundary and later gained the mobile atomic BDR path used by OFF.IA Android.

OFF.IA must continue consuming Memoria.ia's persistence boundary rather than addressing BDR records directly.

## DR-002 — Semantic/relational conversational recall

**Project:** `marceloroldao/memoria.ia`

**Status:** RESOLVED IN PRODUCT API CANDIDATE — Memoria.ia Issue #53 closed; native/mobile parity subsequently integrated.

Available behavior includes:

- conversational ingest/resolve;
- `HIT | MISS | UNRESOLVED`;
- confidence;
- memory IDs;
- exact selected source context;
- relation metadata;
- source provenance / authority / ultimate-source lineage;
- correction/supersession support;
- native Android/mobile semantic relation handling.

The implementation remains domain-agnostic.

## DR-003 — Generic episodic/temporal recall

**Project:** `marceloroldao/memoria.ia`

**Status:** RESOLVED IN PRODUCT API CANDIDATE AND NATIVE MOBILE RUNTIME.

Available behavior includes ordered/time-addressable generic episodes, selected context, episode IDs, confidence, order/timestamp/event metadata, provenance and ambiguity -> `UNRESOLVED`.

## DR-004 — Android/mobile parity with Memoria.ia v1 candidate

**Project:** `marceloroldao/memoria.ia`

**Tracking:** Memoria.ia Issue #50 and OFF.IA Issue #3 / PR #4.

**Status:** MEMORIA.IA SIDE SUBSTANTIALLY COMPLETE; OFF.IA DEVICE ACCEPTANCE REMAINS.

Current OFF.IA Android consumes the frozen Memoria.ia mobile ABI v1 and durable atomic BDR path rather than the superseded draft PR #51 interface.

Current integration includes:

- semantic/relational resolve;
- provenance/anti-self-confirmation;
- episodic recall;
- exact selected context + IDs + confidence;
- BDR-backed restart reconstruction;
- arm64-v8a native build/link;
- active-session trajectory-capable resolve on the current pinned Memoria.ia runtime.

Remaining acceptance is consumer/device evidence tracked in OFF.IA Issue #3: same-session paraphrase, kill/restart durable recall and airplane-mode end-to-end behavior.

OFF.IA must not port Memoria.ia semantics into Kotlin as a substitute.

## DR-005 — Mobile memory export / diagnostic snapshot

**Project:** `marceloroldao/memoria.ia`

**Tracking:** Issue #55.

**Status:** OPEN.

OFF.IA needs an official read-only Memoria.ia export/snapshot contract so the user can save/share a versioned JSON/JSONL diagnostic artifact. OFF.IA must not parse raw BDR persistence files.

The Android UI may show the action as pending until Memoria.ia exposes the contract.

## DR-006 — Active conversation window / trajectory-aware resolve

**Project:** `marceloroldao/memoria.ia`

**Tracking:** Issue #56.

**Status:** PARTIALLY INTEGRATED; FINAL UPSTREAM/DEVICE VALIDATION OPEN.

The current OFF.IA Android branch submits a bounded active-session window together with the new message. The pinned Memoria.ia runtime includes JSON trajectory wiring and keeps semantic trajectory selection inside Memoria.ia.

Desired invariant remains:

`recent conversation window + new message -> Memoria.ia -> trajectory resolution -> persistent retrieval -> minimal selected context -> LLM`

The full window must not automatically be forwarded to the LLM. Selection remains Memoria.ia's responsibility.

Issue #56 remains the upstream tracker for final window/multi-source/reference behavior and real-device regression closure.

## DR-007 — Next product/UX layer

**Project:** `marceloroldao/off.ia`

**Tracking:** OFF.IA Issue #5 and `docs/NEXT_PRODUCT_UX_ROADMAP.md`.

**Status:** PLANNED FOR NEXT UPDATES; DOES NOT BLOCK CURRENT PR #4 / ISSUE #3 ACCEPTANCE.

This roadmap includes:

- modern chat-style visual shell;
- delete/rename/copy/share/search conversation management;
- per-response `Copy | Memory | Curiosity | Improve | Regenerate | More` actions;
- per-message persisted memory audit metadata;
- Markdown/code rendering with per-block copy;
- stop-generation and local regenerate;
- first-run automatic recommended GGUF download when online, while retaining manual model import;
- model manager and compatibility/integrity/license metadata;
- settings for General, Models, Memoria.ia, Storage, Privacy/Network and Laboratory mode;
- `Curiosity` as explicit optional Web knowledge acquisition with source visibility and external provenance;
- `Improve` as optional external intelligence through provider adapters such as OpenAI API, Gemini API and future MA2A;
- credentials kept outside Memoria.ia/BDR in secure Android credential storage;
- local-first routing and explicit authorization before cloud transmission;
- isolated network boundaries for model download, Curiosity and external Improve;
- normal local chat/Memoria.ia/BDR/llama.cpp operation remaining functional without network.

Memoria.ia #55 remains a prerequisite for true memory export. MA2A integration remains a future provider/route and must not block the initial local product release.

## Release-gate separation

The current Android PR #4 / Issue #3 device acceptance remains the immediate integration gate. The product/UX roadmap in DR-007 should be implemented incrementally afterward without weakening the already validated offline architecture.
