package io.kayan.gradle

import com.squareup.kotlinpoet.TypeName
import io.kayan.ConfigValueKind

/**
 * Adapts a raw resolved config value into a custom Kotlin type during source generation.
 *
 * Implementations decode the raw value exposed by the schema, then render a
 * Kotlin expression that can be embedded directly into generated source.
 */
public interface BuildTimeConfigAdapter<T : Any> {
    /** The raw schema kind the adapter expects before conversion. */
    public val rawKind: ConfigValueKind

    /** The Kotlin type emitted into generated source. */
    public val kotlinType: TypeName

    /** Converts the decoded raw value into the adapter's domain type. */
    public fun parse(rawValue: Any): T

    /** Renders [value] as a self-contained Kotlin expression for generated source. */
    public fun renderKotlin(value: T): String
}
