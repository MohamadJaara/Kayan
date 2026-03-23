---
title: JSON Shape
description: Expected structure of Kayan config files.
---

Kayan expects a root object with a required `flavors` object. Top-level keys outside
`flavors` act as defaults for every flavor.

```json
{
  "flavors": {
    "prod": {
      "bundle_id": "com.example.app",
      "feature_search_enabled": false
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

An optional override file uses the same shape and can selectively replace top-level or
flavor-specific values.
