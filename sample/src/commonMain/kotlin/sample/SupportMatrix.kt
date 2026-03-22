package sample

data class SupportRegion(
    val code: String,
    val links: List<String>,
) {
    val primaryLink: String
        get() = links.first()
}

data class SupportMatrix(
    val fallbackRegion: String,
    val regions: List<SupportRegion>,
) {
    init {
        require(regions.isNotEmpty()) { "SupportMatrix requires at least one region." }
    }

    val totalLinkCount: Int
        get() = regions.sumOf { it.links.size }

    val summary: String
        get() = "$fallbackRegion default, ${regions.size} regions, $totalLinkCount links"

    fun linksFor(regionCode: String): List<String> =
        regions.firstOrNull { it.code == regionCode }?.links
            ?: regions.first { it.code == fallbackRegion }.links

    override fun toString(): String = summary
}
