package io.kayan.gradle

import io.kayan.ConfigValueKind

internal data class ResolvedBuildValue(
    val jsonKey: String,
    val kind: ConfigValueKind,
    val rawValue: Any?,
)
