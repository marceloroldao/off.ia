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

1. `resolve(userText)` before inference.
2. Assemble only Memoria-selected context.
3. Generate response locally.
4. `learnTurn(userText, assistantText)` immediately after successful generation.
5. Persist/flush through the Memoria/BDR contract.
6. Update the UI and metrics.

A failed inference must not create a fabricated assistant memory. The user message may later be represented as an event by Memoria.ia if its own contract supports that behavior.

Baseline mode bypasses memory resolve/learn so experiments remain uncontaminated.

## Model lifecycle

`MISSING -> AVAILABLE -> LOADING -> READY`, with `INVALID` and `ERROR` failure states.

The app stores model metadata and a durable Android document URI/path reference where supported; model bytes remain external to the APK.

## Offline/security default

Core chat has no cloud dependency. No conversation telemetry. llama.cpp is embedded/native or strictly process-local where Android permits; it is never exposed on a public network interface.
