# OFF.IA

Offline-first AI chat integration layer for **Memoria.ia + Resolutive-DB (BDR) + llama.cpp**.

> Memoria.ia owns memory and state. BDR owns persistence. llama.cpp provides local language-model inference.

## Status

**Pre-MVP integration scaffold.** The repository now establishes the application boundaries, local llama.cpp adapter, GGUF model registry, observability skeleton and cross-platform unit CI. It deliberately does **not** duplicate Memoria.ia or BDR internals.

A stable end-to-end `Memoria.ia -> BDR v1.1` product path is currently gated by a dependency request because the validated BDR v1.1 Memoria.ia adapter is still on `experiment/bdr-v110-atomic`, not Memoria.ia `main`. See `docs/DEPENDENCY_REQUESTS.md`.

## Architecture

```text
User / UI
   |
 OFF.IA
   |
   +--> Memoria.ia ----> BDR
   |       |
   |       +--> selected minimal context
   |
   +--> llama.cpp on loopback ----> local GGUF
   |
 response
   |
 Memoria.ia memory update
```

llama.cpp is an inference engine, not a memory layer. OFF.IA does not feed full history automatically in Memoria mode.

## Current components

- `offia.adapters.llama.LlamaCppAdapter`: local OpenAI-compatible `/v1/chat/completions` client; loopback-only by default.
- `offia.models.ModelRegistry`: model metadata and states `MODEL_AVAILABLE`, `MODEL_MISSING`, `MODEL_INVALID`, `MODEL_LOADING`, `MODEL_READY`, `MODEL_ERROR`.
- `offia.adapters.memoria.MemoriaBoundary`: boundary only; semantic implementation belongs to Memoria.ia.
- `offia.adapters.bdr.detect_bdr_v11_memoria_backend`: capability detection only; persistence implementation belongs to BDR/Memoria.ia.
- `offia.runtime.OfflineRuntime`: minimal context -> local language adapter orchestration and memory observability fields.

## llama.cpp integration

Provision llama.cpp separately and run its server bound to loopback with a local GGUF model. OFF.IA defaults to `http://127.0.0.1:8080` and does not download a model automatically.

The repository does not include GGUF weights. Add models to `models/registry.json`; `models/*.gguf` is ignored by Git.

## Tests

```bash
python -m pip install -e .[test]
pytest -q
```

Unit tests use fakes/mocks only and are never evidence of real LLM inference. Real-model and benchmark suites will be added separately and must be explicitly labelled.

## MVP gate

The first meaningful milestone remains:

```text
teach fact -> persist in BDR -> restart -> paraphrased question
-> Memoria.ia retrieves -> llama.cpp answers -> no Internet
```

This repository will not claim that milestone until it is run with a real GGUF model and the stable Memoria.ia/BDR path.

## Scope exclusions for this phase

No Android, ESP32, MA2A federation, cloud fallback, automatic multi-GB model download, or direct Internet exposure of llama.cpp.

## Licensing

OFF.IA has not yet been assigned an independent redistribution license. Dependencies retain their own terms. Current Memoria.ia and Resolutive-DB licensing restricts commercial use without authorization; llama.cpp itself is MIT. GGUF model licenses vary by model. See `docs/COMPATIBILITY_ASSESSMENT.md`.
