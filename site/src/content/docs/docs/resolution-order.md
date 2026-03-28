---
title: Resolution Order
description: How Kayan resolves config values across layers.
---

For a selected flavor without a target, values resolve in this priority order:

1. **Custom config flavor value** — highest priority
2. **Custom config top-level default value**
3. **Base config flavor value**
4. **Base config top-level default value** — lowest priority

When a target is selected through target-specific source generation, values resolve in this
priority order:

1. **Custom config flavor target value**
2. **Custom config flavor value**
3. **Custom config top-level target value**
4. **Custom config top-level default value**
5. **Base config flavor target value**
6. **Base config flavor value**
7. **Base config top-level target value**
8. **Base config top-level default value**

If a schema entry is marked `required`, it must resolve to a non-null value for the
selected flavor and optional target or the build fails.

This deterministic precedence means you always know which layer "wins" for a given key,
making debugging straightforward and behavior predictable across builds.
