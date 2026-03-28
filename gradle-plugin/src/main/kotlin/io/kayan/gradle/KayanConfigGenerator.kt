package io.kayan.gradle

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
    ): String {
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
                )
            }
            .forEach(objectSpec::addProperty)

        return FileSpec.builder(packageName, className)
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
    ): PropertySpec = when (declarationMode) {
        KayanDeclarationMode.EXPECT -> buildExpectProperty(definition, renderedCustomProperty)
        KayanDeclarationMode.OBJECT,
        KayanDeclarationMode.ACTUAL,
        -> if (renderedCustomProperty != null) {
            buildCustomProperty(definition, renderedCustomProperty, declarationMode)
        } else {
            when (value) {
                null,
                is ConfigValue.NullValue -> buildNullableProperty(
                    definition = definition,
                    declarationMode = declarationMode,
                    typeName = typeNameFor(definition).copy(nullable = true),
                )

                else -> buildBuiltInProperty(definition, value, declarationMode)
            }
        }
    }

    private fun buildExpectProperty(
        definition: ConfigDefinition,
        renderedCustomProperty: RenderedCustomProperty?,
    ): PropertySpec = basePropertyBuilder(
        definition = definition,
        declarationMode = KayanDeclarationMode.EXPECT,
        typeName = declaredTypeName(definition, renderedCustomProperty),
    ).build()

    private fun buildCustomProperty(
        definition: ConfigDefinition,
        renderedCustomProperty: RenderedCustomProperty,
        declarationMode: KayanDeclarationMode,
    ): PropertySpec {
        val typeName = renderedCustomProperty.typeName
        return if (renderedCustomProperty.expression == null) {
            buildNullableProperty(
                definition = definition,
                declarationMode = declarationMode,
                typeName = when (declarationMode) {
                    KayanDeclarationMode.OBJECT -> typeName.copy(nullable = true)
                    KayanDeclarationMode.EXPECT,
                    KayanDeclarationMode.ACTUAL,
                    -> effectiveTypeName(
                        definition = definition,
                        declarationMode = declarationMode,
                        customTypeName = typeName,
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
                ),
                initializer = CodeBlock.of("%L", renderedCustomProperty.expression),
            )
        }
    }

    private fun buildBuiltInProperty(
        definition: ConfigDefinition,
        value: ConfigValue,
        declarationMode: KayanDeclarationMode,
    ): PropertySpec = when (value) {
        is ConfigValue.StringValue -> buildScalarBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = CodeBlock.of("%S", value.value),
        )

        is ConfigValue.BooleanValue -> buildScalarBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = CodeBlock.of("%L", value.value),
        )

        is ConfigValue.IntValue -> buildScalarBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = CodeBlock.of("%L", value.value),
        )

        is ConfigValue.LongValue -> buildScalarBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = CodeBlock.of("%LL", value.value),
        )

        is ConfigValue.DoubleValue -> buildScalarBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = CodeBlock.of("%L", value.value),
        )
        is ConfigValue.StringMapValue -> buildRenderedBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = renderStringMap(value.value),
        )

        is ConfigValue.StringListValue -> buildRenderedBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = renderStringList(value.value),
        )

        is ConfigValue.StringListMapValue -> buildRenderedBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = renderStringListMap(value.value),
        )

        is ConfigValue.EnumValue -> buildRenderedBuiltInProperty(
            definition = definition,
            declarationMode = declarationMode,
            initializer = CodeBlock.of(
                "%L.%L",
                requireNotNull(definition.enumTypeName),
                value.value,
            ),
        )

        is ConfigValue.NullValue -> error("Null values should be handled before built-in property rendering.")
    }

    private fun buildScalarBuiltInProperty(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        initializer: CodeBlock,
    ): PropertySpec = buildScalarProperty(
        definition = definition,
        declarationMode = declarationMode,
        typeName = effectiveTypeName(definition, declarationMode),
        initializer = initializer,
    )

    private fun buildRenderedBuiltInProperty(
        definition: ConfigDefinition,
        declarationMode: KayanDeclarationMode,
        initializer: CodeBlock,
    ): PropertySpec = buildProperty(
        definition = definition,
        declarationMode = declarationMode,
        typeName = effectiveTypeName(definition, declarationMode),
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
        KayanDeclarationMode.ACTUAL,
        -> requireNotNull(resolvedFlavorConfig) {
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
): TypeName {
    val baseTypeName = renderedCustomProperty?.typeName ?: typeNameFor(definition)
    return if (definition.nullable || !definition.required) {
        baseTypeName.copy(nullable = true)
    } else {
        baseTypeName
    }
}

private fun effectiveTypeName(
    definition: ConfigDefinition,
    declarationMode: KayanDeclarationMode,
    customTypeName: TypeName? = null,
): TypeName = when (declarationMode) {
    KayanDeclarationMode.OBJECT -> customTypeName ?: typeNameFor(definition)
    KayanDeclarationMode.EXPECT,
    KayanDeclarationMode.ACTUAL,
    -> declaredTypeName(
        definition = definition,
        renderedCustomProperty = customTypeName?.let { RenderedCustomProperty(it, null) },
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
