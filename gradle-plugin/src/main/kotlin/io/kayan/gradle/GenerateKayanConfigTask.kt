package io.kayan.gradle

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.squareup.kotlinpoet.TypeName
import io.kayan.ConfigDefinition
import io.kayan.ConfigFormat
import io.kayan.ConfigSchema
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.ResolvedFlavorConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URLClassLoader

internal abstract class GenerateKayanConfigTask : DefaultTask() {
    @get:Input
    public abstract val packageName: Property<String>

    @get:Input
    public abstract val flavor: Property<String>

    @get:Optional
    @get:Input
    public abstract val target: Property<String>

    @get:Input
    public abstract val className: Property<String>

    @get:Input
    public abstract val kotlinPluginApplied: Property<Boolean>

    @get:Input
    public abstract val declarationMode: Property<KayanDeclarationMode>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val baseConfigFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val customConfigFile: RegularFileProperty

    @get:Input
    public abstract val configFormat: Property<ConfigFormat>

    @get:OutputDirectory
    public abstract val outputDir: DirectoryProperty

    @get:Input
    public abstract val schemaEntries: ListProperty<String>

    @get:Classpath
    public abstract val buildscriptClasspath: ConfigurableFileCollection

    @TaskAction
    public fun generate() {
        requireKotlinPlugin()
        generateEither().getOrElse { throw it.toGradleException() }
    }

    private fun requireKotlinPlugin() {
        if (!kotlinPluginApplied.get()) {
            throw PluginConfigurationError.MissingKotlinPlugin.toGradleException()
        }
    }

    private fun generateEither(): Either<KayanGradleError, Unit> = either {
        val inputs = loadGenerationInputsEither().bind()
        val resolvedFlavor = resolveFlavorForEither(inputs).bind()
        writeGeneratedSourceEither(inputs, resolvedFlavor).bind()
    }

    private fun renderCustomPropertiesEither(
        schema: ConfigSchema,
        resolvedFlavor: ResolvedFlavorConfig?,
    ): Either<GenerationError, Map<ConfigDefinition, RenderedCustomProperty>> = either {
        val buildscriptClasspathLoader = createBuildscriptClasspathLoader(
            buildscriptClasspath = buildscriptClasspath,
            parentClassLoader = javaClass.classLoader,
        )
        buildscriptClasspathLoader?.use { classLoader ->
            renderCustomPropertiesWithLoaderEither(schema, resolvedFlavor, classLoader).bind()
        } ?: renderCustomPropertiesWithLoaderEither(schema, resolvedFlavor, null).bind()
    }

    private fun renderCustomPropertiesWithLoaderEither(
        schema: ConfigSchema,
        resolvedFlavor: ResolvedFlavorConfig?,
        buildscriptClasspathLoader: URLClassLoader?,
    ): Either<GenerationError, Map<ConfigDefinition, RenderedCustomProperty>> = either {
        val adapterCache = mutableMapOf<String, LoadedCustomAdapter>()
        buildMap {
            for (definition in schema.entries) {
                val adapterClassName = definition.adapterClassName ?: continue
                val adapter = adapterCache[adapterClassName]
                    ?: loadAdapterEither(adapterClassName, buildscriptClasspathLoader).bind().also {
                        adapterCache[adapterClassName] = it
                    }

                validateAdapterEither(definition, adapter).bind()

                val expression = resolvedFlavor?.values?.get(definition)
                    ?.takeUnless { it is ConfigValue.NullValue }
                    ?.let { value ->
                        renderCustomExpressionEither(
                            definition = definition,
                            adapter = adapter,
                            rawValue = value,
                        ).bind()
                    }

                put(
                    definition,
                    RenderedCustomProperty(
                        typeName = adapter.kotlinType,
                        expression = expression,
                    ),
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun renderCustomExpressionEither(
        definition: ConfigDefinition,
        adapter: LoadedCustomAdapter,
        rawValue: ConfigValue,
    ): Either<GenerationError, String> = either {
        val rawAdapterValue = rawValue.toRawValueEither().bind()
        val parsedValue = adapter.parse(definition, rawAdapterValue).bind()
        val rendered = adapter.renderKotlin(definition, parsedValue).bind()

        requireRenderedExpressionEither(definition, rendered).bind()
    }

    private fun loadAdapterEither(
        className: String,
        buildscriptClasspathLoader: URLClassLoader?,
    ): Either<GenerationError, LoadedCustomAdapter> = either {
        val loaderCandidates = listOfNotNull(
            buildscriptClasspathLoader,
            Thread.currentThread().contextClassLoader,
            javaClass.classLoader,
        ).distinct()

        val adapterClass = loaderCandidates.asSequence()
            .mapNotNull { classLoader ->
                Either.catch { Class.forName(className, true, classLoader) }.getOrNull()
            }
            .firstOrNull()
            ?: raise(GenerationError.AdapterClassNotFound(className))

        val objectInstance = Either.catch {
            adapterClass.getField("INSTANCE").get(null)
        }
        val instance = when (objectInstance) {
            is Either.Left -> {
                val constructed = Either.catch {
                    adapterClass.getDeclaredConstructor().newInstance()
                }
                when (constructed) {
                    is Either.Left -> raise(GenerationError.AdapterInstantiationFailure(className, constructed.value))
                    is Either.Right -> constructed.value
                }
            }
            is Either.Right -> objectInstance.value
        }

        loadedAdapterForEither(className, instance).bind()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadedAdapterForEither(
        className: String,
        instance: Any,
    ): Either<GenerationError, LoadedCustomAdapter> = either {
        if (instance is BuildTimeConfigAdapter<*>) {
            return@either loadedTypedAdapter(instance as BuildTimeConfigAdapter<Any>)
        }

        val adapterClass = instance.javaClass
        val kotlinType = reflectiveTypeNamePropertyEither(adapterClass, instance, "kotlinType", className).bind()
        val rawKind = reflectiveRawKindProperty(adapterClass, instance)
        val parseMethod = reflectiveSingleArgumentMethodEither(adapterClass, "parse", className).bind()
        val renderMethod = reflectiveSingleArgumentMethodEither(
            adapterClass,
            "renderKotlin",
            className,
        ).bind()

        loadedReflectiveAdapter(
            className = className,
            instance = instance,
            rawKind = rawKind,
            kotlinType = kotlinType,
            parseMethod = parseMethod,
            renderMethod = renderMethod,
        )
    }

    @OptIn(ExperimentalKayanGradleApi::class)
    private fun loadGenerationInputsEither(): Either<KayanGradleError, GenerationInputs> = either {
        val mode = declarationMode.orNull ?: KayanDeclarationMode.OBJECT
        GenerationInputs(
            packageName = requireConfiguredEither(packageName.orNull, "packageName").bind(),
            flavor = requireConfiguredEither(flavor.orNull, "flavor").bind(),
            targetName = loadTargetNameEither(target.orNull, mode).bind(),
            className = requireConfiguredEither(className.orNull, "className").bind(),
            schema = requireSchemaEither(schemaEntries.orNull.orEmpty()).bind(),
            baseFile = requireExistingFileEither(baseConfigFile.asFile.get(), "base").bind(),
            customFile = customConfigFile.asFile.orNull?.let { file ->
                requireExistingFileEither(file, "custom").bind()
            },
            configFormat = configFormat.orNull ?: ConfigFormat.AUTO,
            declarationMode = mode,
        )
    }

    private fun writeGeneratedSourceEither(
        inputs: GenerationInputs,
        resolvedFlavor: ResolvedFlavorConfig?,
    ): Either<GenerationError, Unit> = either {
        val renderedCustomProperties = renderCustomPropertiesEither(
            schema = inputs.schema,
            resolvedFlavor = resolvedFlavor,
        ).bind()
        val source = Either.catch {
            KayanConfigGenerator.generate(
                packageName = inputs.packageName,
                className = inputs.className,
                schema = inputs.schema,
                declarationMode = inputs.declarationMode,
                resolvedFlavorConfig = resolvedFlavor,
                renderedCustomProperties = renderedCustomProperties,
            )
        }.getOrElse { raise(GenerationError.SourceGenerationFailure("Kayan config source", it)) }

        val outputRoot = outputDir.get().asFile
        val packagePath = inputs.packageName.replace('.', File.separatorChar)
        val outputFile = File(outputRoot, "$packagePath/${inputs.className}.kt")
        val parent = outputFile.parentFile
        if (!parent.exists() && !parent.mkdirs()) {
            raise(GenerationError.DirectoryCreationFailure(parent.path))
        }

        val writeResult = Either.catch {
            outputFile.writeText(source)
        }
        when (writeResult) {
            is Either.Left -> raise(GenerationError.FileWriteFailure(outputFile.path, writeResult.value))
            is Either.Right -> writeResult.value
        }
    }
}

private fun GenerateKayanConfigTask.resolveFlavorForEither(
    inputs: GenerationInputs,
): Either<KayanGradleError, ResolvedFlavorConfig?> =
    if (!inputs.declarationMode.requiresResolvedFlavor()) {
        null.right()
    } else {
        resolveConfigEither(
            schema = inputs.schema,
            baseFile = inputs.baseFile,
            customFile = inputs.customFile,
            configFormat = inputs.configFormat,
            targetName = inputs.targetName,
        ).flatMap { resolved ->
            requireResolvedFlavorEither(resolved, inputs.flavor)
        }
    }

private fun createBuildscriptClasspathLoader(
    buildscriptClasspath: ConfigurableFileCollection,
    parentClassLoader: ClassLoader,
): URLClassLoader? =
    buildscriptClasspath.files
        .takeIf { it.isNotEmpty() }
        ?.let { files ->
            URLClassLoader(
                files.map { it.toURI().toURL() }.toTypedArray(),
                parentClassLoader,
            )
        }

private fun validateAdapterEither(
    definition: ConfigDefinition,
    adapter: LoadedCustomAdapter,
): Either<GenerationError, Unit> = when {
    adapter.rawKind != null && adapter.rawKind != definition.kind -> {
        GenerationError.AdapterRawKindMismatch(definition, adapter.rawKind).left()
    }

    else -> Unit.right()
}

private fun reflectiveTypeNamePropertyEither(
    adapterClass: Class<*>,
    instance: Any,
    propertyName: String,
    className: String,
): Either<GenerationError, TypeName> {
    val getterValue = Either.catch {
        adapterClass.getMethod(getterName(propertyName)).invoke(instance)
    }
    val value = when (getterValue) {
        is Either.Left -> {
            val fieldValue = Either.catch {
                adapterClass.getDeclaredField(propertyName).apply { isAccessible = true }.get(instance)
            }
            when (fieldValue) {
                is Either.Left -> {
                    return GenerationError.MissingAdapterProperty(
                        className = className,
                        propertyName = propertyName,
                        cause = fieldValue.value,
                    ).left()
                }

                is Either.Right -> fieldValue.value
            }
        }

        is Either.Right -> getterValue.value
    }

    return (value as? TypeName)?.right()
        ?: GenerationError.AdapterPropertyWrongType(className, propertyName, "TypeName").left()
}

private fun reflectiveRawKindProperty(
    adapterClass: Class<*>,
    instance: Any,
): ConfigValueKind? {
    val rawValue = Either.catch {
        adapterClass.getMethod(getterName("rawKind")).invoke(instance)
    }.getOrElse {
        Either.catch {
            adapterClass.getDeclaredField("rawKind").apply { isAccessible = true }.get(instance)
        }.getOrNull()
    } ?: return null

    return when (rawValue) {
        is ConfigValueKind -> rawValue
        is String -> Either.catch { ConfigValueKind.valueOf(rawValue) }.getOrNull()
        else -> null
    }
}

/**
 * Loads the configured target name for source generation.
 *
 * A target is only required for [KayanDeclarationMode.ACTUAL], because target
 * overlays are resolved only when generating target-specific `actual` objects.
 * For [KayanDeclarationMode.OBJECT] and [KayanDeclarationMode.EXPECT], the
 * target is ignored and this returns `null`.
 */
private fun loadTargetNameEither(
    targetName: String?,
    declarationMode: KayanDeclarationMode,
): Either<PluginConfigurationError, String?> {
    if (!declarationMode.requiresTargetName()) {
        return null.right()
    }

    return requireConfiguredEither(targetName, "target").map(String::trim)
}

private fun KayanDeclarationMode.requiresResolvedFlavor(): Boolean = this != KayanDeclarationMode.EXPECT

private fun KayanDeclarationMode.requiresTargetName(): Boolean = this == KayanDeclarationMode.ACTUAL
