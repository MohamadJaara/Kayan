@file:Suppress("TooManyFunctions")

package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.kayan.ConfigValueKind
import org.gradle.api.provider.Provider

@ExperimentalKayanGradleApi
public class KayanBuildValue internal constructor(
    private val valueProvider: Provider<ResolvedBuildValue>,
) {
    public fun asString(): String = readValue(
        requestedType = "String",
        nullAccessorHint = "asStringOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING, ConfigValueKind.ENUM),
    )

    public fun asBoolean(): Boolean = readValue(
        requestedType = "Boolean",
        nullAccessorHint = "asBooleanOrNull()",
        allowedKinds = setOf(ConfigValueKind.BOOLEAN),
    )

    public fun asInt(): Int = readValue(
        requestedType = "Int",
        nullAccessorHint = "asIntOrNull()",
        allowedKinds = setOf(ConfigValueKind.INT),
    )

    public fun asLong(): Long = readValue(
        requestedType = "Long",
        nullAccessorHint = "asLongOrNull()",
        allowedKinds = setOf(ConfigValueKind.LONG),
    )

    public fun asDouble(): Double = readValue(
        requestedType = "Double",
        nullAccessorHint = "asDoubleOrNull()",
        allowedKinds = setOf(ConfigValueKind.DOUBLE),
    )

    public fun asStringList(): List<String> = readValue(
        requestedType = "List<String>",
        nullAccessorHint = "asStringListOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST),
    )

    public fun asStringMap(): Map<String, String> = readValue(
        requestedType = "Map<String, String>",
        nullAccessorHint = "asStringMapOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_MAP),
    )

    public fun asStringListMap(): Map<String, List<String>> = readValue(
        requestedType = "Map<String, List<String>>",
        nullAccessorHint = "asStringListMapOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST_MAP),
    )

    public fun asEnumName(): String = readValue(
        requestedType = "enum name",
        nullAccessorHint = "asEnumNameOrNull()",
        allowedKinds = setOf(ConfigValueKind.ENUM),
    )

    public fun asStringOrNull(): String? = readNullableValue(
        requestedType = "String",
        allowedKinds = setOf(ConfigValueKind.STRING, ConfigValueKind.ENUM),
    )

    public fun asBooleanOrNull(): Boolean? = readNullableValue(
        requestedType = "Boolean",
        allowedKinds = setOf(ConfigValueKind.BOOLEAN),
    )

    public fun asIntOrNull(): Int? = readNullableValue(
        requestedType = "Int",
        allowedKinds = setOf(ConfigValueKind.INT),
    )

    public fun asLongOrNull(): Long? = readNullableValue(
        requestedType = "Long",
        allowedKinds = setOf(ConfigValueKind.LONG),
    )

    public fun asDoubleOrNull(): Double? = readNullableValue(
        requestedType = "Double",
        allowedKinds = setOf(ConfigValueKind.DOUBLE),
    )

    public fun asStringListOrNull(): List<String>? = readNullableValue(
        requestedType = "List<String>",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST),
    )

    public fun asStringMapOrNull(): Map<String, String>? = readNullableValue(
        requestedType = "Map<String, String>",
        allowedKinds = setOf(ConfigValueKind.STRING_MAP),
    )

    public fun asStringListMapOrNull(): Map<String, List<String>>? = readNullableValue(
        requestedType = "Map<String, List<String>>",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST_MAP),
    )

    public fun asEnumNameOrNull(): String? = readNullableValue(
        requestedType = "enum name",
        allowedKinds = setOf(ConfigValueKind.ENUM),
    )

    public fun asStringProvider(): Provider<String> = mapValue(
        requestedType = "String",
        nullAccessorHint = "asStringOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING, ConfigValueKind.ENUM),
    )

    public fun asBooleanProvider(): Provider<Boolean> = mapValue(
        requestedType = "Boolean",
        nullAccessorHint = "asBooleanOrNull()",
        allowedKinds = setOf(ConfigValueKind.BOOLEAN),
    )

    public fun asIntProvider(): Provider<Int> = mapValue(
        requestedType = "Int",
        nullAccessorHint = "asIntOrNull()",
        allowedKinds = setOf(ConfigValueKind.INT),
    )

    public fun asLongProvider(): Provider<Long> = mapValue(
        requestedType = "Long",
        nullAccessorHint = "asLongOrNull()",
        allowedKinds = setOf(ConfigValueKind.LONG),
    )

    public fun asDoubleProvider(): Provider<Double> = mapValue(
        requestedType = "Double",
        nullAccessorHint = "asDoubleOrNull()",
        allowedKinds = setOf(ConfigValueKind.DOUBLE),
    )

    public fun asStringListProvider(): Provider<List<String>> = mapValue(
        requestedType = "List<String>",
        nullAccessorHint = "asStringListOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST),
    )

    public fun asStringMapProvider(): Provider<Map<String, String>> = mapValue(
        requestedType = "Map<String, String>",
        nullAccessorHint = "asStringMapOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_MAP),
    )

    public fun asStringListMapProvider(): Provider<Map<String, List<String>>> = mapValue(
        requestedType = "Map<String, List<String>>",
        nullAccessorHint = "asStringListMapOrNull()",
        allowedKinds = setOf(ConfigValueKind.STRING_LIST_MAP),
    )

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
