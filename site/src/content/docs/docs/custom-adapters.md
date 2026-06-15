---
title: Custom Adapters
description: Convert raw config values into consumer-owned Kotlin types during source generation.
---

Use a custom adapter when a config value should become a domain type instead of
one of Kayan's built-in Kotlin shapes.

Built-in schema entries are data-only. Custom adapters are build code: Kayan loads
the adapter during source generation, calls it with the raw resolved value, and
embeds the returned Kotlin expression in generated source.

## Schema declaration

Declare the raw config shape with `custom(...)` and point to an adapter class or
object that is visible on the buildscript classpath:

```kotlin
kayan {
    schema {
        custom(
            jsonKey = "regional_support_links",
            propertyName = "SUPPORT_MATRIX",
            rawKind = io.kayan.ConfigValueKind.STRING_LIST_MAP,
            adapter = "sample.buildlogic.SupportMatrixAdapter",
            required = true,
        )
    }
}
```

`rawKind` is the shape Kayan validates before your adapter runs. The generated
property type comes from the adapter's `kotlinType`.

## Adapter contract

The recommended form is to implement `BuildTimeConfigAdapter<T>`:

```kotlin
package sample.buildlogic

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import io.kayan.ConfigValueKind
import io.kayan.gradle.BuildTimeConfigAdapter

object EnvironmentAdapter : BuildTimeConfigAdapter<EnvironmentSpec> {
    override val rawKind: ConfigValueKind = ConfigValueKind.STRING

    override val kotlinType: TypeName =
        ClassName("sample", "Environment")

    override fun parse(rawValue: Any): EnvironmentSpec {
        val name = rawValue as? String
            ?: error("environment must be a string.")

        require(name in setOf("dev", "staging", "prod")) {
            "Unsupported environment '$name'."
        }

        return EnvironmentSpec(name)
    }

    override fun renderKotlin(value: EnvironmentSpec): String =
        "sample.Environment(name = ${quote(value.name)})"

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

data class EnvironmentSpec(val name: String)
```

The rendered string must be a complete Kotlin expression that can compile in the
generated source file.

Kayan also supports reflective adapters with the same public members and methods,
but the interface is clearer and gives better compiler help.

## Buildscript classpath

Adapters usually live in included build logic or `buildSrc`. For example, the
sample app keeps its adapter in `sample/build-logic` and adds that build logic to
the sample buildscript classpath before applying Kayan.

The adapter type itself does not need to be part of the application runtime. It
only needs to be visible to Gradle while Kayan generates source.

## Nulls and build-time access

Custom adapters are not applied by `buildValue()`. Build-time access returns raw
Gradle-friendly values such as `String`, `Boolean`, `List<String>`, or
`Map<String, List<String>>`.

Custom adapters also do not receive explicit config `null` values. If a custom
entry is nullable and resolves to null, generated source uses `null` directly
instead of calling the adapter.

## Safety notes

Adapters are trusted build code. They can run arbitrary logic and render arbitrary
Kotlin expressions, so review them like Gradle plugins or build logic. Keep
`renderKotlin()` deterministic and escape any string data that becomes part of a
generated Kotlin literal.

For the broader trust model, see [Security](../security/).
