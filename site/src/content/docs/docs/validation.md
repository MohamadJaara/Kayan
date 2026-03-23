---
title: Validation
description: How Kayan validates your config at build time.
---

Kayan fails the build when any of the following conditions are detected:

- **`flavors` is missing or not an object** — every config file must contain a `flavors` key
  with an object value
- **Unknown keys** — a config contains a key not declared in the schema
- **Type mismatches** — a value does not match the declared schema type
- **Unknown custom flavors** — the custom config introduces a flavor missing from the base config
- **Missing required values** — a required key does not resolve for a flavor

All validation errors include the source file, key path, and flavor name so you can find
and fix the issue quickly.
