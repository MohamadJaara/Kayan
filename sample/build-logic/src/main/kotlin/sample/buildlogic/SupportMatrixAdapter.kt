package sample.buildlogic

private data class SupportRegionSpec(
    val code: String,
    val links: List<String>,
)

private data class SupportMatrixSpec(
    val fallbackRegion: String,
    val regions: List<SupportRegionSpec>,
)

object SupportMatrixAdapter {
    val rawKind: String = "STRING_LIST_MAP"
    val kotlinType: String = "sample.SupportMatrix"

    fun parse(rawValue: Any): Any {
        val rawMap = rawValue as? Map<*, *> ?: error("Expected a map of region codes to support links.")
        val regions = rawMap.entries
            .map { entry ->
                val code = entry.key as? String ?: error("Support matrix keys must be strings.")
                val links = (entry.value as? List<*>)?.map { link ->
                    link as? String ?: error("Support links for '$code' must all be strings.")
                } ?: error("Support links for '$code' must be a list of strings.")
                require(links.isNotEmpty()) { "Support region '$code' must contain at least one link." }
                SupportRegionSpec(code = code, links = links)
            }
            .sortedBy(SupportRegionSpec::code)

        require(regions.isNotEmpty()) { "Support matrix requires at least one region." }

        val fallbackRegion = regions.firstOrNull { it.code == "global" }?.code ?: regions.first().code
        return SupportMatrixSpec(
            fallbackRegion = fallbackRegion,
            regions = regions,
        )
    }

    fun renderKotlin(value: Any): String {
        val matrix = value as SupportMatrixSpec
        val renderedRegions = matrix.regions.joinToString(
            prefix = "listOf(",
            postfix = ")",
            separator = ", ",
        ) { region ->
            "sample.SupportRegion(code = ${quote(region.code)}, links = ${renderStringList(region.links)})"
        }

        return "sample.SupportMatrix(" +
            "fallbackRegion = ${quote(matrix.fallbackRegion)}, " +
            "regions = $renderedRegions" +
            ")"
    }

    private fun renderStringList(values: List<String>): String =
        values.joinToString(prefix = "listOf(", postfix = ")", separator = ", ") { quote(it) }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}
