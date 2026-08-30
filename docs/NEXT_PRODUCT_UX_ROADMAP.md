# OFF.IA — Next Product / UX Roadmap

Tracking issue: **#5 — Chat UX, model onboarding, Curiosity and external response improvement**

This document records the planned product/UX layer that follows the current Android integration acceptance gate. It must not be used to bypass or dilute the architecture:

- OFF.IA owns UI/orchestration;
- Memoria.ia owns memory semantics and selected context;
- Resolutive-DB owns durability;
- llama.cpp owns local inference.

The current PR #4 / Issue #3 physical-device gate remains separate and should be completed before this roadmap is treated as a release-blocking refactor.

## Product direction

OFF.IA should look and behave like a familiar modern chat application while remaining local-first and auditable.

Target per-response actions:

`Copy | Memory | Curiosity | Improve | Regenerate | More`

`Regenerate` is always local through llama.cpp. `Improve` is optional external intelligence. `Curiosity` is optional public-Web knowledge acquisition. `Memory` explains what Memoria.ia selected for that exact response.

## Chat UX

Planned conversation features:

- modern chat layout and conversation drawer/list;
- new conversation;
- rename;
- delete with confirmation;
- duplicate/copy whole conversation;
- share/export conversation;
- search conversations;
- optional pin/favorite later;
- stop generation while llama.cpp is producing tokens;
- local regenerate;
- per-message actions instead of global-only actions.

Each assistant message should persist its own memory/generation metadata so historical responses remain auditable after app restart.

## Per-response Memory action

The `Memory` action belongs beside `Copy` for each assistant response.

Normal view:

- HIT / MISS / UNRESOLVED;
- selected source/context;
- confidence when available;
- memory IDs;
- trajectory/window usage.

Laboratory/advanced details may show provenance, source authority, `ultimate_source_memory_id`, relations, episode metadata, context size, memory latency, LLM latency and token counts.

Do not reconstruct memory semantics inside OFF.IA. The UI only presents metadata returned by Memoria.ia.

## Markdown and code blocks

Render assistant Markdown with support for headings, emphasis, lists, blockquotes, inline code, fenced code blocks, JSON/terminal/config content, tables where practical and links.

Every code block should have its own copy action and language label when detectable. A whole-response copy action remains available.

## First-run model onboarding

If no local model exists after first install/open:

1. detect network availability;
2. if online, download or offer the OFF.IA recommended default GGUF;
3. display model name, size, progress, pause/cancel and free-space requirements;
4. validate checksum/integrity before loading;
5. retain a visible `Choose/import another model` path.

Add a `Download large models on Wi-Fi only` preference.

Every recommended model must have an explicit source, version, checksum and license/redistribution review. Models remain external to the APK/repository unless licensing explicitly permits bundling.

Manual GGUF import through Android storage APIs remains supported.

## Model manager

Settings -> Models should provide:

- active model;
- installed models;
- download recommended model;
- import GGUF;
- switch/delete;
- storage use;
- GGUF version/quantization when available;
- compatibility state;
- checksum/integrity state;
- source/license metadata;
- approximate RAM guidance;
- optional local smoke test;
- advanced generation/performance controls hidden from normal users by default.

## Settings

Create a first-class settings screen with sections for:

### General

Appearance/theme, language, font size, send behavior, animations/haptics.

### Models

Model manager described above.

### Memoria.ia

Status, local metrics where available, diagnostics, export when Memoria.ia #55 is available, and destructive clear/reset only with explicit confirmation.

### Storage

Models, conversations, Memoria.ia/BDR, cache and cleanup.

### Privacy / Network

Clearly state what runs locally and what optional actions can use the network. Provide a possible `Block network after model download` mode. Never silently upload Memoria.ia/BDR state.

### Laboratory mode

Keep detailed memory/inference observability available for development without cluttering normal chat use.

## Curiosity

Add a per-response `Curiosity` action for optional Internet research.

Conceptual flow:

`current question/response -> Curiosity -> Web/search adapter -> selected public sources -> local llama.cpp synthesis -> supplemental answer`

Requirements:

- explicit network action;
- show consulted sources;
- prefer authoritative/official sources where appropriate;
- allow opening sources;
- no-network failure must not break normal chat;
- configurable source/provider behavior later.

### Curiosity provenance

Public-Web knowledge must never be stored as if it came directly from the user.

If the user chooses to persist it, Memoria.ia should receive external provenance such as `external_import` plus available source metadata: URL/domain/title/time/reference/excerpt metadata.

Curiosity results should not automatically become persistent memory. The user chooses whether to keep them only in the conversation, save them to Memoria.ia, or eventually make eligible public knowledge available through MA2A.

OFF.IA must never parse/write raw BDR records for this.

## Improve response

`Improve` is distinct from `Regenerate`.

- `Regenerate` -> current local GGUF through llama.cpp.
- `Improve` -> optional external provider/network.

Provider abstraction should eventually support:

- None/local only;
- OpenAI API;
- Gemini API;
- MA2A network.

Preferred future policy:

`local first -> MA2A when available -> cloud provider only when configured/authorized`

Requirements:

- user supplies provider credentials when required;
- credentials are never Memoria.ia memories and never BDR records;
- store secrets through Android secure/Keystore-backed mechanisms;
- never log or export credentials;
- transmit only the minimum context required for the requested improvement;
- prefer current question/response plus Memoria.ia-selected minimal context rather than whole memory/history;
- ask before cloud transmission by default;
- identify which provider produced the improved answer;
- allow keep original / replace / keep both.

MA2A is a future provider/route and must not block the first local product release.

## Network boundary

OFF.IA started intentionally without Android `INTERNET` permission. Automatic model download, Curiosity and cloud-assisted Improve introduce network access and must therefore be isolated behind explicit boundaries:

- Model Download Manager;
- Curiosity/Web Adapter;
- External Improvement Provider Adapter.

Normal chat, Memoria.ia, BDR and llama.cpp inference must continue working with the network unavailable.

## Existing upstream dependencies

### Memoria.ia #55

Mobile memory export / diagnostic snapshot. OFF.IA must wait for the official Memoria.ia read-only export contract and must not parse raw BDR as a workaround.

### Memoria.ia #56

Active conversation / trajectory-aware resolve. The runtime and OFF.IA are already integrating the active conversation window, but final upstream/device validation remains tracked there. Semantic trajectory selection remains owned by Memoria.ia.

## Suggested implementation order

1. per-message metadata/action model and visual chat refactor;
2. Markdown/code rendering plus Copy/Stop/Regenerate;
3. conversation management actions;
4. settings shell, storage and model manager;
5. first-run model download manager and explicit network boundary;
6. Memoria.ia export after #55;
7. Curiosity/Web adapter;
8. external Improve provider abstraction for OpenAI/Gemini;
9. MA2A provider/routing when its client contract is ready;
10. accessibility, polish, performance and regression gates.

## Acceptance principle

The user experience should be a normal modern chat app, while OFF.IA preserves its distinguishing properties:

- local-first inference;
- persistent Memoria.ia + BDR;
- per-response memory auditability;
- optional, explicit Internet curiosity;
- optional, explicit external response improvement;
- no hidden cloud dependency.
