# OFF.IA Android MVP

The first Android target is deliberately small: a single local chat screen that proves online learning plus restart persistence.

## Runtime cycle

```text
user message
  -> Memoria.ia semantic resolve + structural read-only resolve
  -> selected context
  -> llama.cpp local GGUF inference
  -> assistant response
  -> Memoria.ia persists the conversation turn
  -> OFF.IA explicitly observes only the USER text in the structural trail
  -> BDR durable persistence
  -> next message can use the new memory without restarting
```

Restart is only a persistence test; it is not required for learning.

## First APK UI

One chat screen:
- OFF.IA title and offline/local status;
- scrollable user/assistant messages;
- text input + Send;
- compact diagnostic line: Memory HIT/MISS, local model, latency;
- model picker when no GGUF is configured.

The GGUF model is never committed to this repository or embedded in the APK. The user explicitly selects/imports a local model file.

## Android boundaries

- Kotlin/Compose owns Android lifecycle and UI only.
- Native/JNI boundary hosts llama.cpp integration.
- Memoria adapter delegates memory semantics to Memoria.ia; OFF.IA must not recreate its algorithms.
- BDR adapter delegates durability to Resolutive-DB; OFF.IA must not create a substitute database.
- No network permission is required for the core offline chat path.

## MVP acceptance tests

1. Select a valid local GGUF.
2. Teach a fact in chat.
3. Ask a paraphrased question in the same running session: Memoria.ia must retrieve the newly learned turn.
4. Fully terminate and reopen the app.
5. Ask another paraphrase: the fact must still be recoverable from durable storage.
6. Show Memory HIT/MISS and local inference status.
7. Repeat with airplane mode enabled.

## Dependency gate

The Android shell can be built independently, but the project must not claim the complete `Memoria.ia -> BDR` APK vertical slice until the native/mobile-compatible persistence contract is demonstrated. Any missing capability belongs in the dependency project rather than being reimplemented here.
