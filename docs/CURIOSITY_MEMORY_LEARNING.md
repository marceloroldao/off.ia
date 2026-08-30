# Curiosity -> Memoria.ia public knowledge learning

## Status

This integration targets the Memoria.ia **post-v1** development line and is intentionally separate from the published `v1.0.0-rc1` release.

Pinned Memoria.ia integration commit:

`e91eabeebdee11472a1ea09ef7ea51ca2aeb3fa7`

## Ownership boundary

The invariant is:

```text
Internet / Wikipedia
        ↓
OFF.IA Curiosity
  acquisition + UI + policy
        ↓
Memoria.ia external_public
  authority + dedup + conflict + provenance
        ↓
Resolutive-DB / BDR
  durable persistence
```

OFF.IA never writes or parses BDR records directly.

## Learning flow

For each Curiosity request:

1. OFF.IA acquires public source material through the explicit Curiosity network boundary.
2. The local GGUF model synthesizes the answer on-device.
3. Each usable public source excerpt is submitted to Memoria.ia through the additive mobile ABI as `external_public` with URL, domain, title, acquisition time and provider metadata, using `import_kind=imported`.
4. Memoria.ia returns durable memory IDs and owns source authority, semantic deduplication, conflict handling and persistence.
5. The locally synthesized Curiosity answer is submitted as `import_kind=derived` external knowledge whose `parent_memory_ids` are the already-learned public source memories. This matches the Memoria.ia contract that only derived external knowledge may carry parent IDs.
6. OFF.IA requests a flush through Memoria.ia. A flush failure is reported separately and does not discard the Curiosity answer.
7. Later normal Memoria.ia resolution can reuse the learned public knowledge while OFF.IA is offline.

## Authority and privacy

Public Web material is never persisted as if the user asserted it.

- imported Curiosity sources remain `external_public`;
- derived synthesis is allowed only after public parent memories exist;
- personal/user memory remains a different authority class;
- Memoria.ia, not OFF.IA, decides deduplication and conflict resolution;
- future MA2A eligibility is not enabled by this integration.

## Android boundary

`MemoryGateway.learnExternalKnowledge(...)` is the consumer-facing Kotlin abstraction. `NativeMemoryGateway` serializes the approved request and the JNI bridge invokes:

`memoria_mobile_learn_external_knowledge_json(...)`

The Android app contains no BDR-specific persistence code for Curiosity.

## Per-response public-learning audit

OFF.IA keeps a small transcript-level audit snapshot for each completed Curiosity response. This snapshot is **not** a second memory database and is not authoritative for semantic retrieval. Its only purpose is to make the consumer behavior auditable after app restart without reading raw BDR files.

The snapshot records:

- knowledge class (`external_public`);
- public source memory IDs returned by Memoria.ia;
- total public memory IDs learned for the Curiosity response;
- whether the local synthesis was accepted as derived public knowledge;
- number of rejected/failed source submissions;
- whether the final explicit flush reported a failure.

The handoff between `learnCuriosityResult(...)` and `ChatStore.save(...)` is process-local and transient. `ChatStore` immediately attaches the returned audit to the matching response ID and persists it in the chat workspace schema. Memoria.ia + BDR remain the only authoritative memory/persistence path.

The chat workspace schema is additive and reads historical schema versions 2 and 3 while writing schema version 4 with the optional `generation.public_knowledge` block.

## Validation

The consumer integration is gated by:

- Kotlin unit coverage for source-first learning, derived parent lineage, blank-synthesis handling, audit handoff and flush-failure isolation;
- Android arm64-v8a build/link against the pinned Memoria.ia post-v1 runtime;
- existing Memoria.ia native tests for persistence, restart, provenance, deduplication, conflict handling and offline resolution.

A real-device Curiosity acceptance remains the final consumer proof tracked in OFF.IA issue #11:

```text
Curiosity online
  -> external_public learning
  -> force-stop/reopen
  -> airplane mode
  -> normal Memoria.ia resolve
  -> local llama.cpp answer
```
