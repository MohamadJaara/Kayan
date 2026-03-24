package io.kayan

internal fun closeKeyMatches(
    unknownKey: String,
    candidates: List<String>,
): List<String> {
    val normalizedUnknownKey = unknownKey.lowercase()
    return candidates
        .map { candidate ->
            candidate to levenshteinDistance(normalizedUnknownKey, candidate.lowercase())
        }
        .filter { (candidate, distance) ->
            distance <= suggestionThreshold(normalizedUnknownKey.length, candidate.length)
        }
        .sortedWith(compareBy<Pair<String, Int>>({ it.second }, { it.first }))
        .take(MAX_SUGGESTIONS)
        .map(Pair<String, Int>::first)
}

private fun suggestionThreshold(firstLength: Int, secondLength: Int): Int =
    maxOf(
        MIN_SUGGESTION_THRESHOLD,
        minOf(MAX_SUGGESTION_THRESHOLD, maxOf(firstLength, secondLength) / LENGTH_DIVISOR),
    )

@Suppress("ReturnCount")
private fun levenshteinDistance(first: String, second: String): Int {
    if (first == second) return 0
    if (first.isEmpty()) return second.length
    if (second.isEmpty()) return first.length

    var previous = IntArray(second.length + 1) { it }
    var current = IntArray(second.length + 1)

    for (firstIndex in first.indices) {
        current[0] = firstIndex + 1
        for (secondIndex in second.indices) {
            val substitutionCost = if (first[firstIndex] == second[secondIndex]) 0 else 1
            current[secondIndex + 1] = minOf(
                current[secondIndex] + 1,
                previous[secondIndex + 1] + 1,
                previous[secondIndex] + substitutionCost,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }

    return previous[second.length]
}

private const val MAX_SUGGESTIONS: Int = 3
private const val MIN_SUGGESTION_THRESHOLD: Int = 1
private const val MAX_SUGGESTION_THRESHOLD: Int = 3
private const val LENGTH_DIVISOR: Int = 3
