# Kayan Config Schema

Generated from `kayan { schema { ... } }`.
Generated Kotlin access point: `sample.config.KayanConfig`.

## File Shape

- The root object must contain `flavors`.
- Top-level keys act as defaults for every flavor.
- Every flavor object accepts the same keys as the top-level defaults section.
- Keys marked `required` must appear either at the top level or inside every flavor to resolve for all variants.

## Entries

| JSON key | Generated property | Raw JSON type | Required after resolution | Notes |
| --- | --- | --- | --- | --- |
| `bundle_id` | `BUNDLE_ID` | `string` | Yes | Required after resolution. |
| `brand_name` | `BRAND_NAME` | `string` | No | Built-in Kayan type. |
| `feature_search_enabled` | `FEATURE_SEARCH_ENABLED` | `boolean` | No | Built-in Kayan type. |
| `max_workspace_count` | `MAX_WORKSPACE_COUNT` | `integer` | No | Built-in Kayan type. |
| `max_cache_bytes` | `MAX_CACHE_BYTES` | `long` | No | Built-in Kayan type. |
| `rollout_ratio` | `ROLLOUT_RATIO` | `double` | No | Built-in Kayan type. |
| `support_links` | `SUPPORT_LINKS` | `array<string>` | No | Built-in Kayan type. |
| `support_labels` | `SUPPORT_LABELS` | `map<string, string>` | No | Built-in Kayan type. |
| `regional_support_links` | `REGIONAL_SUPPORT_LINKS` | `map<string, array<string>>` | No | Built-in Kayan type. |
| `release_stage` | `RELEASE_STAGE` | `string` | No | Generates Kotlin enum values of `sample.ReleaseStage` from normalized string input. |
| `support_email` | `SUPPORT_EMAIL` | `string` | No | Allows explicit null values. |
| `environment` | `ENVIRONMENT` | `string` | No | Parsed from `string` with custom adapter `sample.EnvironmentAdapter`. |
