# Dependency requests

## DR-001 — Stable Memoria.ia BDR v1.1 persistence contract

**Project:** `marceloroldao/memoria.ia`

**Missing capability:** A stable, documented product-level persistence interface in `main` that lets Memoria.ia use Resolutive-DB v1.1 as its durable backend. The implementation exists experimentally in `experiment/bdr-v110-atomic`, but OFF.IA cannot treat an experimental branch as the stable product contract.

**Why OFF.IA needs it:** The required invariant is “Memoria.ia owns memory/state; BDR owns persistence.” Without a stable bridge, OFF.IA would either bypass BDR or duplicate persistence logic, both architecturally invalid.

**Proposed interface:** Promote or formalize the existing storage-backend boundary so OFF.IA only selects/configures a backend, for example `open_resolutive_memory(..., backend="bdr", allow_fallback=False)`, with no BDR record logic in OFF.IA.

**Tests required:**
- create/store/retrieve through Memoria.ia using BDR v1.1;
- clean restart recovery;
- abrupt/torn-write recovery;
- checkpoint/reopen;
- deterministic reconstruction;
- backend-unavailable failure without silent SQLite fallback when BDR is explicitly required;
- Windows support status documented explicitly (native or unsupported for the milestone).

**Status:** BLOCKS claiming the stable OFF.IA `Memoria.ia -> BDR` vertical slice.

## DR-002 — Stable semantic context-selection entry point

**Project:** `marceloroldao/memoria.ia`

**Missing capability:** Product-level API that accepts a natural-language question and returns selected memory/context with confidence and `UNRESOLVED`, without requiring OFF.IA to supply explicit `memory_keys`.

**Why OFF.IA needs it:** The final MVP must support teaching a fact and asking for it differently. Implementing a semantic router in OFF.IA would duplicate Memoria.ia responsibility.

**Proposed interface:** A Memoria.ia method such as `resolve_context(scope, message) -> {items, memory_ids, confidence, status}` built from validated semantic-routing capabilities.

**Tests required:** paraphrase recall, open-set rejection, contradiction cases, confidence calibration, persistence/restart, and no-wrong-memory behavior below threshold.

**Status:** Does not block adapter/model initialization, but blocks the final “ask differently” MVP criterion.
