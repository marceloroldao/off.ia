# OFF.IA Transformer Network Boundary

## Decision

OFF.IA must not connect directly to OpenAI, Gemini or another transformer/provider API.

The product boundary for external transformer access is:

`OFF.IA -> M2A2 -> Memoria.ia server -> transformer/provider`

This is a hard architecture decision for new OFF.IA product code.

## Ownership

- **OFF.IA** owns local UI/orchestration and local llama.cpp inference.
- **M2A2** owns the network transport/federation boundary used by OFF.IA for external intelligence.
- **Memoria.ia server** owns the authorized server-side transformer routing boundary and memory-aware context policy.
- **Transformer/provider integrations** remain behind the Memoria.ia server/M2A2 boundary.

OFF.IA must not store provider API keys and must not instantiate direct OpenAI/Gemini HTTP clients.

## Local-first behavior

The main chat path remains local:

`OFF.IA -> Memoria.ia -> BDR -> llama.cpp`

External improvement is optional and additive. Failure or absence of M2A2 must not prevent local chat, memory recall or local inference.

## Data minimization

When M2A2 improvement becomes available, OFF.IA should send only the minimum authorized payload needed for the request, such as:

- the current user question;
- the current local answer;
- the minimum context selected by Memoria.ia;
- identity/request metadata required by M2A2.

OFF.IA must not send raw BDR records or the complete memory database.

## Historical compatibility

Older alpha transcripts may contain response metadata identifying OpenAI or Gemini. Those enum values may remain readable so old histories do not break, but they are historical-only and must not be constructible as new direct network routes.

## Other online capabilities

This decision specifically governs **transformer/external-AI access**. Model downloads and Curiosity public-source acquisition remain separate explicit network capabilities until their own M2A2 migration contracts are defined.

## Future implementation target

A future `M2A2ImproveProvider` may implement the existing OFF.IA `ImproveProvider` boundary once the M2A2/Memory server contract is ready. The rest of the message UI and persisted improvement metadata should not need to know which server-side transformer was selected.
