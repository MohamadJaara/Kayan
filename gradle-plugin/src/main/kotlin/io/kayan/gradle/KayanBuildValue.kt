@file:Suppress("TooManyFunctions")

package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.kayan.ConfigValueKind
import org.gradle.api.provider.Provider

/**
 * Provider-backed access to one resolved schema entry from Gradle build logic.
 *
 * Each accessor checks that the schema kind matches the requested type before
 * returning a value. Use `asString()`, `asBoolean()`, and similar methods when
 * you need a plain Kotlin value immediately during configuration. Use
 * `asStringProvider()`, `asBooleanProvider()`, and similar methods when wiring
 * lazy Gradle properties or task inputs.
 *
 * Non-null accessors fail for nullable results, `OrNull` accessors preserve
 * nullable results, and provider accessors defer the same checks until the
 * provider is queried. Enum accessors return the generated enum constant name
 * rather than an enum instance.
 */
@ExperimentalKayanGradleApi
public class KayanBuildValue internal constructor(
    private val valueProvider: Provider<ResolvedBuildValue>,
) {
    /** Returns the resolved value as a non-null string. */
    public fun asString(): String = readValue(
        requestedType = "String",
        nullAccessorHint = "asStringOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING, ConfigValueKind.ENUM),
    )

    /** Returns the resolved value as a non-null boolean. */
    public fun asBoolean(): Boolean = readValue(
        requestedType = "Boolean",
        nullAccessorHint = "asBooleanOrNull()",
        allowedKinds = setOf(ConfigValueKind.BOOLEAN),
    )

    /** Returns the resolved value as a non-null integer. */
    public fun asInt(): Int = readValue(
        requestedType = "Int",
        nullAccessorHint = "asIntOrNull()",
        allowedKinds = setOf(ConfigValueKind.INT),
    )

    /** Returns the resolved value as a non-null long. */
    public fun asLong(): Long = readValue(
        requestedType = "Long",
        nullAccessorHint = "asLongOrNull()",
        allowedKinds = setOf(ConfigValueKind.LONG),
    )

    /** Returns the resolved value as a non-null double. */
    public fun asDouble(): Double = readValue(
        requestedType = "Double",
        nullAccessorHint = "asDoubleOrNull()",
        allowedKinds = setOf(ConfigValueKind.DOUBLE),
    )

    /** Returns the resolved value as a non-null string list. */
    public fun asStringList(): List<String> = readValue(
        requestedType = "List<String>",
        nullAccessorHint = "asStringListOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST),
    )

    /** Returns the resolved value as a non-null string map. */
    public fun asStringMap(): Map<String, String> = readValue(
        requestedType = "Map<String, String>",
        nullAccessorHint = "asStringMapOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_MAP),
    )

    /** Returns the resolved value as a non-null string-list map. */
    public fun asStringListMap(): Map<String, List<String>> = readValue(
        requestedType = "Map<String, List<String>>",
        nullAccessorHint = "asStringListMapOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST_MAP),
    )

    /**
     * Returns the resolved normalized constant name for this enum value.
     *
     * @return The resolved enum constant name.
     */
    public fun asEnumName(): String = readValue(
        requestedType = "enum name",
        nullAccessorHint = "asEnumNameOrNull()",
        allowedKinds = setOf(ConfigValueKind.ENUM),
    )

    /** Returns the resolved value as a nullable string. */
    public fun asStringOrNull(): String? = readNullableValue(
        requestedType = "String",
        allowedKinds = setOf(ConfigValueKind.STRING, ConfigValueKind.ENUM),
    )

    /** Returns the resolved value as a nullable boolean. */
    public fun asBooleanOrNull(): Boolean? = readNullableValue(
        requestedType = "Boolean",
        allowedKinds = setOf(ConfigValueKind.BOOLEAN),
    )

    /** Returns the resolved value as a nullable integer. */
    public fun asIntOrNull(): Int? = readNullableValue(
        requestedType = "Int",
        allowedKinds = setOf(ConfigValueKind.INT),
    )

    /** Returns the resolved value as a nullable long. */
    public fun asLongOrNull(): Long? = readNullableValue(
        requestedType = "Long",
        allowedKinds = setOf(ConfigValueKind.LONG),
    )

    /** Returns the resolved value as a nullable double. */
    public fun asDoubleOrNull(): Double? = readNullableValue(
        requestedType = "Double",
        allowedKinds = setOf(ConfigValueKind.DOUBLE),
    )

    /** Returns the resolved value as a nullable string list. */
    public fun asStringListOrNull(): List<String>? = readNullableValue(
        requestedType = "List<String>",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST),
    )

    /** Returns the resolved value as a nullable string map. */
    public fun asStringMapOrNull(): Map<String, String>? = readNullableValue(
        requestedType = "Map<String, String>",
        allowedKinds = setOf(ConfigValueKind.STRING_MAP),
    )

    /** Returns the resolved value as a nullable string-list map. */
    public fun asStringListMapOrNull(): Map<String, List<String>>? = readNullableValue(
        requestedType = "Map<String, List<String>>",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST_MAP),
    )

    /**
     * Returns the resolved normalized constant name for this enum value, or
     * `null` when the schema entry resolves to null.
     *
     * @return The resolved enum constant name, or `null` when the resolved value is null.
     */
    public fun asEnumNameOrNull(): String? = readNullableValue(
        requestedType = "enum name",
        allowedKinds = setOf(ConfigValueKind.ENUM),
    )

    /** Returns a `Provider<String>` for lazy Gradle wiring instead of resolving the value immediately. */
    public fun asStringProvider(): Provider<String> = mapValue(
        requestedType = "String",
        nullAccessorHint = "asStringOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING, ConfigValueKind.ENUM),
    )

    /** Returns a `Provider<Boolean>` for lazy Gradle wiring instead of resolving the value immediately. */
    public fun asBooleanProvider(): Provider<Boolean> = mapValue(
        requestedType = "Boolean",
        nullAccessorHint = "asBooleanOrNull()",
        allowedKinds = setOf(ConfigValueKind.BOOLEAN),
    )

    /** Returns a `Provider<Int>` for lazy Gradle wiring instead of resolving the value immediately. */
    public fun asIntProvider(): Provider<Int> = mapValue(
        requestedType = "Int",
        nullAccessorHint = "asIntOrNull()",
        allowedKinds = setOf(ConfigValueKind.INT),
    )

    /** Returns a `Provider<Long>` for lazy Gradle wiring instead of resolving the value immediately. */
    public fun asLongProvider(): Provider<Long> = mapValue(
        requestedType = "Long",
        nullAccessorHint = "asLongOrNull()",
        allowedKinds = setOf(ConfigValueKind.LONG),
    )

    /** Returns a `Provider<Double>` for lazy Gradle wiring instead of resolving the value immediately. */
    public fun asDoubleProvider(): Provider<Double> = mapValue(
        requestedType = "Double",
        nullAccessorHint = "asDoubleOrNull()",
        allowedKinds = setOf(ConfigValueKind.DOUBLE),
    )

    /** Returns a `Provider<List<String>>` for lazy Gradle wiring instead of resolving the value immediately. */
    public fun asStringListProvider(): Provider<List<String>> = mapValue(
        requestedType = "List<String>",
        nullAccessorHint = "asStringListOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST),
    )

    /** Returns a `Provider<Map<String, String>>` for lazy Gradle wiring instead of resolving the value immediately. */
    public fun asStringMapProvider(): Provider<Map<String, String>> = mapValue(
        requestedType = "Map<String, String>",
        nullAccessorHint = "asStringMapOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_MAP),
    )

    /**
     * Returns a `Provider<Map<String, List<String>>>`
     * for lazy Gradle wiring instead of resolving the value immediately.
     */
    public fun asStringListMapProvider(): Provider<Map<String, List<String>>> = mapValue(
        requestedType = "Map<String, List<String>>",
        nullAccessorHint = "asStringListMapOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST_MAP),
    )

    /**
     * Returns a `Provider<String>` for lazy Gradle wiring of this enum value's normalized constant name.
     *
     * @return A provider that yields the resolved enum constant name.
     */
    public fun asEnumNameProvider(): Provider<String> = mapValue(
        requestedType = "enum name",
        nullAccessorHint = "asEnumNameOrNull()",
        allowedKinds = setOf(ConfigValueKind.ENUM),
    )

    private fun <T : Any> readValue(
        requestedType: String,
        nullAccessorHint: String,
        allowedKinds: Set<ConfigValueKind>,
    ): T = valueProvider.get()
        .requireValueEither<T>(
            requestedType = requestedType,
            nullAccessorHint = nullAccessorHint,
            allowedKinds = allowedKinds,
        ).getOrElse { throw it.toGradleException() }

    private fun <T> readNullableValue(
        requestedType: String,
        allowedKinds: Set<ConfigValueKind>,
    ): T? = valueProvider.get()
        .requireNullableValueEither<T>(
            requestedType = requestedType,
            allowedKinds = allowedKinds,
        ).getOrElse { throw it.toGradleException() }

    private fun <T : Any> mapValue(
        requestedType: String,
        nullAccessorHint: String,
        allowedKinds: Set<ConfigValueKind>,
    ): Provider<T> = valueProvider.map { value ->
        value.requireValueEither<T>(
            requestedType = requestedType,
            nullAccessorHint = nullAccessorHint,
            allowedKinds = allowedKinds,
        ).getOrElse { throw it.toGradleException() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> ResolvedBuildValue.requireValueEither(
        requestedType: String,
        nullAccessorHint: String,
        allowedKinds: Set<ConfigValueKind>,
    ): Either<BuildTimeAccessError, T> = either {
        requireKindEither(requestedType, allowedKinds).bind()
        val value = rawValue ?: raise(BuildTimeAccessError.NullValueAccess(jsonKey, nullAccessorHint))

        requireRuntimeTypeEither<T>(value, requestedType).bind()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> ResolvedBuildValue.requireNullableValueEither(
        requestedType: String,
        allowedKinds: Set<ConfigValueKind>,
    ): Either<BuildTimeAccessError, T?> = either {
        requireKindEither(requestedType, allowedKinds).bind()
        val value = rawValue ?: return@either null

        requireRuntimeTypeEither<T>(value, requestedType).bind()
    }

    private fun ResolvedBuildValue.requireKindEither(
        requestedType: String,
        allowedKinds: Set<ConfigValueKind>,
    ): Either<BuildTimeAccessError, Unit> =
        if (kind in allowedKinds) {
            Unit.right()
        } else {
            BuildTimeAccessError.ValueKindMismatch(jsonKey, kind, requestedType).left()
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> ResolvedBuildValue.requireRuntimeTypeEither(
        value: Any,
        requestedType: String,
    ): Either<BuildTimeAccessError, T> {
        val typedValue = when (kind) {
            ConfigValueKind.STRING,
            ConfigValueKind.ENUM -> value as? String

            ConfigValueKind.BOOLEAN -> value as? Boolean
            ConfigValueKind.INT -> value as? Int
            ConfigValueKind.LONG -> value as? Long
            ConfigValueKind.DOUBLE -> value as? Double
            ConfigValueKind.STRING_LIST -> (value as? List<*>)?.takeIf { entries ->
                entries.all { it is String }
            }

            ConfigValueKind.STRING_MAP -> (value as? Map<*, *>)?.takeIf { entries ->
                entries.keys.all { it is String } && entries.values.all { it is String }
            }

            ConfigValueKind.STRING_LIST_MAP -> (value as? Map<*, *>)?.takeIf { entries ->
                entries.keys.all { it is String } &&
                    entries.values.all { entryValue ->
                        (entryValue as? List<*>)?.all { it is String } == true
                    }
            }
        }

        return typedValue?.let { (it as T).right() } ?: BuildTimeAccessError.DecodedValueTypeMismatch(
            jsonKey = jsonKey,
            requestedType = requestedType,
            actualType = value.runtimeTypeName(),
        ).left()
    }

    private fun Any.runtimeTypeName(): String =
        this::class.qualifiedName ?: this::class.simpleName ?: javaClass.name
}
