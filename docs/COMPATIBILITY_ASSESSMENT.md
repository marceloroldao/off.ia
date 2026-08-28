# Compatibility assessment — 2026-08-28

## Memoria.ia

Current inspected main: `c36a3dd71f901b40e93bc094e774c6742bfd0e95`.
Current product candidate: `v0.99.0-alpha.1`.

Usable now:
- provider-neutral `LLMAdapter` contract;
- `ProductChatService` baseline vs Memoria mode and token/context metrics;
- organization-scoped product facade and restart-safe product snapshot;
- existing experimental semantic-routing work, kept outside the stable OFF.IA path.

Important gap:
- current main product persistence is its own snapshot path, not the BDR v1.1 backend;
- `experiment/bdr-v110-atomic` contains `BDRResolutiveMemory`, native pybind and tests, but this is not a stable main contract yet.

## Resolutive-DB / BDR

Current inspected main: `4b1cc837af65efcb5d24ea5f8c18056fadf9efee`.
Current stable release: `v1.1.0`.

Usable now:
- `AtomicDatabase` in C++ with atomic batch WAL path;
- checkpoints/recovery/integrity facilities;
- backward compatibility with v1.0 interfaces;
- Python `PersistentBDR` remains available, while the v1.1 atomic path is C++-first.

## llama.cpp

Integration choice for MVP: run official llama.cpp as a local process/service and call its OpenAI-compatible chat-completions endpoint on loopback. Use a user-provisioned local GGUF path. Do not vendor llama.cpp or model weights into this repository.

Why:
- clean process boundary;
- Windows/Linux/Docker portability;
- replaceable local model/backend;
- no need to make llama.cpp responsible for memory;
- easy baseline-vs-Memoria experiments against the same inference engine.

## License compatibility

- llama.cpp software: MIT.
- Memoria.ia: RRNCL v1.0 source-visible/noncommercial research terms; commercial use requires authorization.
- Resolutive-DB: source-available academic/noncommercial terms; commercial/production use requires separate authorization.
- Model weights have their own licenses and must be checked per model.

OFF.IA therefore must not be presented as an unrestricted commercially redistributable combined stack until the project owner chooses and documents compatible commercial terms for Memoria.ia/BDR and any bundled/recommended model.

## Smallest honest offline MVP architecture

```text
UI / local API
    -> OFF.IA orchestrator
       -> Memoria.ia context boundary
          -> BDR persistence boundary
       -> llama.cpp loopback server
          -> local GGUF
       <- response
    -> Memoria.ia memory update
```

The stable vertical slice is gated on a stable Memoria.ia-to-BDR persistence contract. OFF.IA must not replace that missing contract with its own database implementation.
