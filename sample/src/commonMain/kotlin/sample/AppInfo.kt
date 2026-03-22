package sample

import sample.generated.SampleConfig

object AppInfo {
    val brandName: String? = SampleConfig.BRAND_NAME
    val bundleId: String = SampleConfig.BUNDLE_ID
    val apiBaseUrl: String? = SampleConfig.API_BASE_URL
    val themeName: String? = SampleConfig.THEME_NAME
    val searchEnabled: Boolean? = SampleConfig.FEATURE_SEARCH_ENABLED
    val supportLinks: List<String> = SampleConfig.SUPPORT_LINKS
    val supportMatrix: SupportMatrix = SampleConfig.SUPPORT_MATRIX
    val resolvedEntries: List<Pair<String, String>> = Customization.resolvedEntries
}
