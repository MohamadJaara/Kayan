---
title: Resolution Order
description: How Kayan resolves config values across layers.
---

For a selected flavor, values resolve in this priority order:

1. **Custom config flavor value** — highest priority
2. **Custom config top-level default value**
3. **Base config flavor value**
4. **Base config top-level default value** — lowest priority

If a schema entry is marked `required`, it must resolve to a non-null value for the
selected flavor or the build fails.

This deterministic precedence means you always know which layer "wins" for a given key,
making debugging straightforward and behavior predictable across builds.
