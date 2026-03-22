package sample

import sample.generated.SampleConfig

object Customization {
    val resolvedEntries: List<Pair<String, String>> = listOf(
        entry("BRAND_NAME", SampleConfig.BRAND_NAME),
        entry("BUNDLE_ID", SampleConfig.BUNDLE_ID),
        entry("ONBOARDING_ENABLED", SampleConfig.ONBOARDING_ENABLED),
        entry("API_BASE_URL", SampleConfig.API_BASE_URL),
        entry("THEME_NAME", SampleConfig.THEME_NAME),
        entry("FEATURE_SEARCH_ENABLED", SampleConfig.FEATURE_SEARCH_ENABLED),
        entry("SUPPORT_LINKS", SampleConfig.SUPPORT_LINKS),
        entry("SUPPORT_MATRIX", SampleConfig.SUPPORT_MATRIX),
    )

    private fun entry(name: String, value: Any?): Pair<String, String> = name to stringify(value)

    private fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is List<*> -> value.joinToString(prefix = "[", postfix = "]")
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, entryValue) ->
            "$key=${stringify(entryValue)}"
        }
        else -> value.toString()
    }
}
