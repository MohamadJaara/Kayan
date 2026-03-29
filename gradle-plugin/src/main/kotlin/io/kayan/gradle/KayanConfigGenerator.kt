@file:OptIn(ExperimentalKayanApi::class)

package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import io.kayan.ConfigDefinition
import io.kayan.ConfigSchema
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.ExperimentalKayanApi
import io.kayan.ResolvedFlavorConfig

internal data class RenderedCustomProperty(
    val typeName: TypeName,
    val expression: String?,
)

internal enum class KayanDeclarationMode {
    OBJECT,
    EXPECT,
    ACTUAL,
}

internal object KayanConfigGenerator {
    fun generate(
        packageName: String,
        className: String,
        schema: ConfigSchema,
        declarationMode: KayanDeclarationMode = KayanDeclarationMode.OBJECT,
        resolvedFlavorConfig: ResolvedFlavorConfig? = null,
        renderedCustomProperties: Map<ConfigDefinition, RenderedCustomProperty> = emptyMap(),
        declarationNullability: Map<ConfigDefinition, Boolean> = emptyMap(),
    ): String = generateEither(
        packageName = packageName,
        className = className,
        schema = schema,
        declarationMode = declarationMode,
        resolvedFlavorConfig = resolvedFlavorConfig,
        renderedCustomProperties = renderedCustomProperties,
        declarationNullability = declarationNullability,
    ).getOrElse { throw it.toGradleException() }

    internal fun generateEither(
        packageName: String,
        className: String,
        schema: ConfigSchema,
        declarationMode: KayanDeclarationMode = KayanDeclarationMode.OBJECT,
        resolvedFlavorConfig: ResolvedFlavorConfig? = null,
        renderedCustomProperties: Map<ConfigDefinition, RenderedCustomProperty> = emptyMap(),
        declarationNullability: Map<ConfigDefinition, Boolean> = emptyMap(),
    ): Either<GenerationError, String> = either {
        val resolvedFlavor = declarationMode.requiredResolvedFlavorOrNull(resolvedFlavorConfig)
        val objectSpec = TypeSpec.objectBuilder(className)
            .addModifiers(KModifier.PUBLIC)
            .apply {
                declarationMode.objectModifier()?.let { modifier ->
                    addModifiers(modifier)
                }
                addKdoc(kdocFor(declarationMode, resolvedFlavor))
            }

        schema.entries
            .map { definition ->
                buildProperty(
                    definition = definition,
                    declarationMode = declarationMode,
                    value = resolvedFlavor?.values?.get(definition),
                    renderedCustomProperty = renderedCustomProperties[definition],
                    declarationNullable = declarationNullability[definition],
                )
                    .bind()
            }
            .forEach(objectSpec::addProperty)

        FileSpec.builder(packageName, className)
            .addKotlinDefaultImports(true, true)
            .indent("    ")
            .addType(objectSpec.build())
            .build()
            .toString()
    }

    private fun buildProperty(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        value: ConfigValue?,
        renderedCustomProperty: RenderedCustomProperty?,
        declarationNullable: Boolean?,
    ): Either<GenerationError, PropertySpec> = when (declarationMode) {
        KayanDeclarationMode.EXPECT -> buildExpectProperty(definition, renderedCustomProperty, declarationNullable)
        KayanDeclarationMode.OBJECT,
        KayanDeclarationMode.ACTUAL ->
            if (renderedCustomProperty != null) {
                buildCustomProperty(definition, renderedCustomProperty, declarationMode, declarationNullable)
            } else {
                when (value) {
                    null,
                    is ConfigValue.NullValue -> either {
                        validateDeclarationNullability(declarationNullable, definition).bind()
                        buildNullableProperty(
                            definition = definition,
                            declarationMode = declarationMode,
                            typeName = typeNameFor(definition).copy(nullable = true),
                        )
                    }

                    else -> either {
                        buildBuiltInProperty(definition, value, declarationMode, declarationNullable)
                    }
                }
            }
    }

    private fun buildExpectProperty(
        definition: ConfigDefinition,
        renderedCustomProperty: RenderedCustomProperty?,
        declarationNullable: Boolean?,
    ): Either<GenerationError, PropertySpec> = either {
        basePropertyBuilder(
            definition = definition,
            declarationMode = KayanDeclarationMode.EXPECT,
            typeName = declaredTypeName(definition, renderedCustomProperty, declarationNullable),
        ).build()
    }

    private fun buildCustomProperty(
        definition: ConfigDefinition,
        renderedCustomProperty: RenderedCustomProperty,
        declarationMode: KayanDeclarationMode,
        declarationNullable: Boolean?,
    ): Either<GenerationError, PropertySpec> = either {
        val typeName = renderedCustomProperty.typeName
        if (renderedCustomProperty.expression == null) {
            validateDeclarationNullability(declarationNullable, definition).bind()
            buildNullableProperty(
                definition = definition,
                declarationMode = declarationMode,
                typeName = when (declarationMode) {
                    KayanDeclarationMode.OBJECT -> typeName.copy(nullable = true)
                    KayanDeclarationMode.EXPECT,
                    KayanDeclarationMode.ACTUAL -> effectiveTypeName(
                        definition = definition,
                        declarationMode = declarationMode,
                        customTypeName = typeName,
                        declarationNullable = declarationNullable,
                    )
                },
            )
        } else {
            buildProperty(
                definition = definition,
                declarationMode = declarationMode,
                typeName = effectiveTypeName(
                    definition = definition,
                    declarationMode = declarationMode,
                    customTypeName = typeName,
                    declarationNullable = declarationNullable,
                ),
                initializer = CodeBlock.of("%L", renderedCustomProperty.expression),
            )
        }
    }

    private fun validateDeclarationNullability(
        declarationNullable: Boolean?,
        definition: ConfigDefinition,
    ): Either<GenerationError, Unit> = either {
        if (declarationNullable == false) {
            raise(GenerationError.NonNullableDeclarationResolvedToNull(definition))
        }
    }

    private fun buildBuiltInProperty(
        definition: ConfigDefinition,
        value: ConfigValue,
        declarationMode: KayanDeclarationMode,
        declarationNullable: Boolean?,
    ): PropertySpec =
        builtInPropertyInitializer(definition, value)?.let { initializer ->
            when (value) {
                is ConfigValue.StringValue,
                is ConfigValue.BooleanValue,
                is ConfigValue.IntValue,
                is ConfigValue.LongValue,
                is ConfigValue.DoubleValue ->
                    buildScalarBuiltInProperty(
                        definition = definition,
                        declarationMode = declarationMode,
                        declarationNullable = declarationNullable,
                        initializer = initializer,
                    )

                is ConfigValue.StringMapValue,
                is ConfigValue.StringListValue,
                is ConfigValue.StringListMapValue,
                is ConfigValue.EnumValue ->
                    buildRenderedBuiltInProperty(
                        definition = definition,
                        declarationMode = declarationMode,
                        declarationNullable = declarationNullable,
                        initializer = initializer,
                    )

                is ConfigValue.NullValue -> error("Null values should be handled before built-in property rendering.")
            }
        } ?: error("Null values should be handled before built-in property rendering.")

    private fun builtInPropertyInitializer(
        definition: ConfigDefinition,
        value: ConfigValue,
    ): CodeBlock? = when (value) {
        is ConfigValue.StringValue -> CodeBlock.of("%S", value.value)
        is ConfigValue.BooleanValue -> CodeBlock.of("%L", value.value)
        is ConfigValue.IntValue -> CodeBlock.of("%L", value.value)
        is ConfigValue.LongValue -> CodeBlock.of("%LL", value.value)
        is ConfigValue.DoubleValue -> CodeBlock.of("%L", value.value)
        is ConfigValue.StringMapValue -> renderStringMap(value.value)
        is ConfigValue.StringListValue -> renderStringList(value.value)
        is ConfigValue.StringListMapValue -> renderStringListMap(value.value)
        is ConfigValue.EnumValue -> CodeBlock.of(
            "%L.%L",
            requireNotNull(definition.enumTypeName),
            value.value,
        )
        is ConfigValue.NullValue -> null
    }

    private fun buildScalarBuiltInProperty(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        declarationNullable: Boolean?,
        initializer: CodeBlock,
    ): PropertySpec = buildScalarProperty(
        definition = definition,
        declarationMode = declarationMode,
        typeName = effectiveTypeName(definition, declarationMode, declarationNullable = declarationNullable),
        initializer = initializer,
    )

    private fun buildRenderedBuiltInProperty(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        declarationNullable: Boolean?,
        initializer: CodeBlock,
    ): PropertySpec = buildProperty(
        definition = definition,
        declarationMode = declarationMode,
        typeName = effectiveTypeName(definition, declarationMode, declarationNullable = declarationNullable),
        initializer = initializer,
    )

    private fun buildScalarProperty(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        typeName: TypeName,
        initializer: CodeBlock,
    ): PropertySpec {
        val propertyBuilder = basePropertyBuilder(definition, declarationMode, typeName)
        if (definition.required && declarationMode == KayanDeclarationMode.OBJECT) {
            propertyBuilder.addModifiers(KModifier.CONST)
        }
        return propertyBuilder.initializer(initializer).build()
    }

    private fun buildNullableProperty(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        typeName: TypeName,
    ): PropertySpec = buildProperty(
        definition = definition,
        declarationMode = declarationMode,
        typeName = typeName,
        initializer = CodeBlock.of("null"),
    )

    private fun buildProperty(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        typeName: TypeName,
        initializer: CodeBlock,
    ): PropertySpec = basePropertyBuilder(definition, declarationMode, typeName)
        .initializer(initializer)
        .build()

    private fun basePropertyBuilder(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        typeName: TypeName,
    ): PropertySpec.Builder = PropertySpec.builder(definition.propertyName, typeName).apply {
        addModifiers(KModifier.PUBLIC)
        if (declarationMode == KayanDeclarationMode.ACTUAL) {
            addModifiers(KModifier.ACTUAL)
        }
    }
}

private const val EXPECT_KDOC: String =
    "Generated by the Kayan Gradle plugin as common expect declarations for target-specific config.\n"

private fun KayanDeclarationMode.requiredResolvedFlavorOrNull(
    resolvedFlavorConfig: ResolvedFlavorConfig?,
): ResolvedFlavorConfig? =
    when (this) {
        KayanDeclarationMode.EXPECT -> null
        KayanDeclarationMode.OBJECT,
        KayanDeclarationMode.ACTUAL -> requireNotNull(resolvedFlavorConfig) {
            "resolvedFlavorConfig is required when declarationMode is $this."
        }
    }

private fun KayanDeclarationMode.objectModifier(): KModifier? = when (this) {
    KayanDeclarationMode.OBJECT -> null
    KayanDeclarationMode.EXPECT -> KModifier.EXPECT
    KayanDeclarationMode.ACTUAL -> KModifier.ACTUAL
}

private fun declaredTypeName(
    definition: ConfigDefinition,
    renderedCustomProperty: RenderedCustomProperty?,
    declarationNullable: Boolean? = null,
): TypeName {
    val baseTypeName = renderedCustomProperty?.typeName ?: typeNameFor(definition)
    val isNullable = declarationNullable ?: (definition.nullable || !definition.required)
    return if (isNullable) {
        baseTypeName.copy(nullable = true)
    } else {
        baseTypeName
    }
}

private fun effectiveTypeName(
    definition: ConfigDefinition,
    declarationMode: KayanDeclarationMode,
    customTypeName: TypeName? = null,
    declarationNullable: Boolean? = null,
): TypeName = when (declarationMode) {
    KayanDeclarationMode.OBJECT -> customTypeName ?: typeNameFor(definition)
    KayanDeclarationMode.EXPECT,
    KayanDeclarationMode.ACTUAL -> declaredTypeName(
        definition = definition,
        renderedCustomProperty = customTypeName?.let { RenderedCustomProperty(it, null) },
        declarationNullable = declarationNullable,
    )
}

private fun kdocFor(
    declarationMode: KayanDeclarationMode,
    resolvedFlavorConfig: ResolvedFlavorConfig?,
): String = when (declarationMode) {
    KayanDeclarationMode.OBJECT ->
        "Generated by the Kayan Gradle plugin for flavor `${requireNotNull(resolvedFlavorConfig).flavorName}`.\n"

    KayanDeclarationMode.EXPECT -> EXPECT_KDOC

    KayanDeclarationMode.ACTUAL -> buildString {
        val requiredResolvedFlavor = requireNotNull(resolvedFlavorConfig)
        append("Generated by the Kayan Gradle plugin for flavor `")
        append(requiredResolvedFlavor.flavorName)
        append('`')
        requiredResolvedFlavor.targetName?.let { targetName ->
            append(" and target `")
            append(targetName)
            append('`')
        }
        append(".\n")
    }
}

private fun typeNameFor(definition: ConfigDefinition): TypeName = when (definition.kind) {
    ConfigValueKind.STRING -> String::class.asTypeName()
    ConfigValueKind.BOOLEAN -> Boolean::class.asTypeName()
    ConfigValueKind.INT -> Int::class.asTypeName()
    ConfigValueKind.LONG -> Long::class.asTypeName()
    ConfigValueKind.DOUBLE -> Double::class.asTypeName()
    ConfigValueKind.STRING_MAP -> KayanTypeNames.parameterized(
        Map::class.asClassName(),
        String::class.asTypeName(),
        String::class.asTypeName(),
    )

    ConfigValueKind.STRING_LIST -> KayanTypeNames.parameterized(
        List::class.asClassName(),
        String::class.asTypeName(),
    )

    ConfigValueKind.STRING_LIST_MAP -> KayanTypeNames.parameterized(
        Map::class.asClassName(),
        String::class.asTypeName(),
        KayanTypeNames.parameterized(
            List::class.asClassName(),
            String::class.asTypeName(),
        ),
    )

    ConfigValueKind.ENUM -> KayanTypeNames.bestGuess(requireNotNull(definition.enumTypeName))
}

private fun renderStringMap(values: Map<String, String>): CodeBlock =
    values.entries
        .sortedBy { it.key }
        .toCodeBlock(prefix = "mapOf(") { builder, (key, entryValue), isFirst ->
            if (!isFirst) {
                builder.add(", ")
            }
            builder.add("%S to %S", key, entryValue)
        }

private fun renderStringList(values: List<String>): CodeBlock =
    values.toCodeBlock(prefix = "listOf(") { builder, value, isFirst ->
        if (!isFirst) {
            builder.add(", ")
        }
        builder.add("%S", value)
    }

private fun renderStringListMap(values: Map<String, List<String>>): CodeBlock =
    values.entries
        .sortedBy { it.key }
        .toCodeBlock(prefix = "mapOf(") { builder, (key, entries), isFirst ->
            if (!isFirst) {
                builder.add(", ")
            }
            builder.add("%S to %L", key, renderStringList(entries))
        }

private fun <T> Iterable<T>.toCodeBlock(
    prefix: String,
    renderEntry: (builder: CodeBlock.Builder, value: T, isFirst: Boolean) -> Unit,
): CodeBlock {
    val builder = CodeBlock.builder().add(prefix)
    forEachIndexed { index, value ->
        renderEntry(builder, value, index == 0)
    }
    return builder.add(")").build()
}
