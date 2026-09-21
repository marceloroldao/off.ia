# Memoria.ia Server structural memory client V1

Status: transport gate only; not yet wired into the normal chat loop.

## Purpose

This gate lets OFF.IA call the authenticated structural-text endpoints exposed by
Memoria.ia Server without embedding the Memoria.ia administrative API key in the
APK.

Flow:

```text
OFF.IA
  -> DeviceTokenProvider
  -> Authorization: Device <short-lived token>
  -> Memoria.ia Server /device/memory/structural/*
  -> memory.sync authorization
  -> server-derived device hierarchy
  -> internal X-Memoria-Key
  -> Memoria.ia structural text recall
```

## Android boundary

`MemoriaServerStructuralClient` exposes:

- `observeUserText(text, sequence, sessionId)`
- `resolve(query, limit, maxScan)`

The client sends only user payload. It has no API for `hierarchy_id`,
`source_id` or `source_kind`; those remain Server-owned.

The client fails closed if the Server ever reports
`semantic_projection=true`, because this gate is specifically for the
non-semantic structural field.

## Token boundary

The client receives tokens through `DeviceTokenProvider`. It does not yet
implement:

- enrollment claim;
- device key generation/persistence;
- challenge signing;
- Device token refresh.

Those are the next device-identity gate. Keeping them separate prevents the
transport test from silently introducing an insecure private-key store.

## Current non-goal

This branch does not replace `MemoryGateway`, the RC6 local runtime, BDR, or
the existing Curiosity flow. It only establishes the authenticated Server
transport required before the structural V2 path can be connected to chat.
