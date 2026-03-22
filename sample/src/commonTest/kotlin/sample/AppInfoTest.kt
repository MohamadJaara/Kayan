package sample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppInfoTest {
    @Test
    fun usesGeneratedConfigValues() {
        assertEquals("Example App Custom", AppInfo.brandName)
        assertEquals("com.example.app", AppInfo.bundleId)
        assertEquals("https://api.example.com", AppInfo.apiBaseUrl)
        assertEquals("sunrise", AppInfo.themeName)
        assertEquals(true, AppInfo.searchEnabled)
        assertEquals(listOf("https://custom.example.com/help"), AppInfo.supportLinks)
        assertEquals("global", AppInfo.supportMatrix.fallbackRegion)
        assertEquals(
            listOf("https://custom.example.com/help"),
            AppInfo.supportMatrix.linksFor("global")
        )
        assertEquals(
            listOf("https://custom.example.com/help"),
            AppInfo.supportMatrix.linksFor("missing-region")
        )
        assertEquals(
            listOf("https://custom.example.com/eu/help", "https://custom.example.com/eu/vip"),
            AppInfo.supportMatrix.linksFor("emea")
        )
        assertEquals(4, AppInfo.supportMatrix.regions.size)
        assertEquals(5, AppInfo.supportMatrix.totalLinkCount)
        assertEquals("global default, 4 regions, 5 links", AppInfo.supportMatrix.summary)
        assertEquals(8, AppInfo.resolvedEntries.size)
        assertTrue(AppInfo.resolvedEntries.contains("BRAND_NAME" to "Example App Custom"))
        assertTrue(AppInfo.resolvedEntries.contains("BUNDLE_ID" to "com.example.app"))
        assertTrue(AppInfo.resolvedEntries.contains("ONBOARDING_ENABLED" to "true"))
        assertTrue(AppInfo.resolvedEntries.contains("FEATURE_SEARCH_ENABLED" to "true"))
        assertTrue(AppInfo.resolvedEntries.any { it.first == "API_BASE_URL" && it.second == "https://api.example.com" })
        assertTrue(AppInfo.resolvedEntries.contains("THEME_NAME" to "sunrise"))
        assertTrue(
            AppInfo.resolvedEntries.contains(
                "SUPPORT_LINKS" to "[https://custom.example.com/help]"
            )
        )
        assertTrue(
            AppInfo.resolvedEntries.contains(
                "SUPPORT_MATRIX" to "global default, 4 regions, 5 links"
            )
        )
    }
}
