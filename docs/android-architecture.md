# Android MVP Architecture

## Principle

`Memoria.ia owns memory/state; BDR owns durability; llama.cpp owns local neural inference; OFF.IA owns orchestration and UX.`

## Components

```text
Compose Chat UI
      |
      v
Android Chat Controller
      |
      v
OFF.IA Runtime Contract
   /       |        \
  v        v         v
Memoria   BDR     Language
Adapter  Adapter    Adapter
  |        |          |
  v        v          v
Memoria.ia BDR   llama.cpp/JNI
                        |
                        v
                    local GGUF
```

## Turn transaction

For Memoria mode:

1. Run semantic `resolve(userText)` and read-only structural resolve before inference.
2. Assemble only context selected by Memoria.ia; OFF.IA does not rescore structural evidence.
3. Generate response locally.
4. `learnTurn(userText, assistantText)` after successful generation for the existing conversation/state contract.
5. Explicitly observe only the persisted USER text in the structural trail, using the returned user memory ID as provenance.
6. Persist/flush through the shared Memoria/BDR contract.
7. Update the UI and metrics.

A failed inference must not create a fabricated assistant memory. Assistant/LLM output is never implicitly inserted into the structural trail, and the current query is resolved before it is observed so it cannot reinforce its own answer.

Baseline mode bypasses memory resolve/learn so experiments remain uncontaminated.

## Model lifecycle

`MISSING -> AVAILABLE -> LOADING -> READY`, with `INVALID` and `ERROR` failure states.

The app stores model metadata and a durable Android document URI/path reference where supported; model bytes remain external to the APK.

## Offline/security default

Core chat has no cloud dependency. No conversation telemetry. llama.cpp is embedded/native or strictly process-local where Android permits; it is never exposed on a public network interface.
