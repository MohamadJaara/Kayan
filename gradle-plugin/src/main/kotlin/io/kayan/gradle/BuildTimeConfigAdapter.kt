package io.kayan.gradle

import io.kayan.ConfigValueKind

public interface BuildTimeConfigAdapter<T : Any> {
    public val rawKind: ConfigValueKind
    public val kotlinType: String

    public fun parse(rawValue: Any): T

    public fun renderKotlin(value: T): String
}
