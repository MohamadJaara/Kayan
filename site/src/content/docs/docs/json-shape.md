---
title: Config File Shape
description: Expected JSON and YAML structure for Kayan config files.
---

Kayan expects a root object with a required `flavors` object. Top-level keys
outside `flavors` and `targets` act as defaults for every flavor. An optional
root-level `targets` object can refine those defaults for specific targets such
as `android`, `ios`, or `jvm`.

This page shows the JSON form of the shape. YAML uses the same structure and resolution rules.

```json
{
  "targets": {
    "ios": {
      "brand_name": "Example iOS"
    }
  },
  "flavors": {
    "prod": {
      "bundle_id": "com.example.app",
      "feature_search_enabled": false,
      "targets": {
        "ios": {
          "bundle_id": "com.example.app.ios"
        }
      }
    },
    "dev": {
      "bundle_id": "com.example.app.dev",
      "feature_search_enabled": true
    }
  },
  "brand_name": "Example App",
  "api_base_url": "https://api.example.com"
}
```

Flavor objects accept the same keys as the top-level defaults section, plus an
optional `targets` object with the same per-target shape.

An optional override file uses the same shape and can selectively replace top-level, target,
flavor, or flavor-target values.

## Reserved keys

`flavors` and `targets` are reserved by Kayan and cannot be declared as schema
entries. All other config keys should be declared in `kayan { schema { ... } }`
when the module consumes them.

With the Gradle plugin's default `KayanValidationMode.SUBSET`, undeclared keys
are ignored by that module. With `KayanValidationMode.STRICT`, undeclared keys
fail validation.

## Target overlays

Target overlays only affect generated values when Kayan is asked to resolve a
target. For KMP `expect` / `actual` generation and target-aware `buildValue()`,
see [Target-Specific Generation](../target-specific-generation/).
