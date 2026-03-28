---
title: JSON Shape
description: Expected structure of Kayan config files.
---

Kayan expects a root object with a required `flavors` object. Top-level keys outside
`flavors` and `targets` act as defaults for every flavor. An optional root-level `targets`
object can refine those defaults for specific targets such as `android`, `ios`, or `jvm`.

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

Flavor objects accept the same keys as the top-level defaults section, plus an optional
`targets` object with the same per-target shape.

An optional override file uses the same shape and can selectively replace top-level, target,
flavor, or flavor-target values.
