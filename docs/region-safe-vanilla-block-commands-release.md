---
version: 0.1.5
title: Region-Safe Vanilla Block Commands
description: Region routing for block queries from the server console and RCON
date: 2026-08-28
minecraftVersion: "26.2"
status: stable
breaking: false
tags:
  - Tessera
  - Commands
  - Block Queries
  - Region Threading
  - RCON
releaseUrl: https://github.com/Hackii1432/Tessera
downloadUrl: https://home.mosaikdev.com
---

## Changes

- Vanilla commands using `execute in <dimension> if block ...` or `unless block ...` from the server console and RCON are executed on the responsible region.
- Block predicates using individual block types or block tags now work safely in the Overworld, Nether, and End.
- `data get block` has been restored as a read-only vanilla command with region ownership checks.
- Block operations using `data merge`, `data modify`, or `data remove` remain disabled until they can be executed in a fully region-safe manner.
- Block queries targeting unloaded regions are rejected with a clear message instead of causing an internal thread check or a `captureTreeGeneration` exception.
- Region-bound block commands use the responsible region task queue without blocking the global region thread or directly reading data owned by another region.
- Console and RCON regression tests cover `if block`, `unless block`, block tags, and `data get block` in all three Vanilla dimensions.
- The tests cover loaded and unloaded target regions as well as the original `forceload` reproduction.
- The complete Tessera build, including Checkstyle and tests, and the Console/RCON smoke suite completed successfully.
