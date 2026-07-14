package io.kayan.gradle

import com.squareup.kotlinpoet.STRING
import io.kayan.ConfigValueKind
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenerateKayanConfigTaskTest {
    @Test
    fun generateWritesResolvedKotlinSourceAndCleansStaleOutput() {
        val projectDir = createTempDirectory(prefix = "kayan-generate-task-test").toFile()
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val baseFile = File(projectDir, "default.json").apply {
            writeText(
                """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.base"
                        }
                      }
                    }
                """.trimIndent(),
            )
        }
        val customFile = File(projectDir, "custom-overrides.json").apply {
            writeText(
                """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.custom"
                        }
                      }
                    }
                """.trimIndent(),
            )
        }
        val outputDir = File(projectDir, "build/generated/kayan/kotlin").apply {
            mkdirs()
        }
        val staleFile = File(outputDir, "stale.kt").apply {
            writeText("stale")
        }
        val task = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) {
            it.kotlinPluginApplied.set(true)
            it.packageName.set("sample.config")
            it.flavor.set("prod")
            it.generatedTargetNames.set(emptyList())
            it.className.set("KayanConfig")
            it.declarationMode.set(KayanDeclarationMode.OBJECT)
            it.baseConfigFile.set(baseFile)
            it.customConfigFile.set(customFile)
            it.outputDir.set(outputDir)
            it.schemaEntries.set(listOf(bundleIdEntry().serialize()))
        }.get()

        task.generate()

        val generatedFile = File(outputDir, "sample/config/KayanConfig.kt")
        assertTrue(!staleFile.exists())
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.readText().contains("public const val BUNDLE_ID: String = \"com.example.custom\""))
    }

    @Test
    fun generateExplainsSupportedKotlinPluginPathsWhenNoCompatiblePluginWasDetected() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) {
            it.kotlinPluginApplied.set(false)
        }.get()

        val error = assertFailsWith<GradleException> {
            task.generate()
        }

        assertMessageContains(
            error,
            "org.jetbrains.kotlin.multiplatform",
            "org.jetbrains.kotlin.jvm",
            "com.android.application",
            "com.android.library",
        )
    }

    @Test
    fun generateRendersValuesThroughTypedCustomAdapter() {
        val projectDir = createTempDirectory(prefix = "kayan-generate-task-test").toFile()
        val task = configuredGenerateTask(
            projectDir = projectDir,
            baseConfig = """
                {
                  "environment": "prod",
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            schemaEntries = listOf(
                bundleIdEntry(),
                environmentEntry(adapterClassName = TaskStringAdapter::class.java.name),
            ),
        )

        task.generate()

        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        assertTrue(generatedFile.readText().contains("public val ENVIRONMENT: String = \"PROD\""))
    }

    @Test
    fun generateRendersValuesThroughReflectiveAdapterWithEnumRawKind() {
        val generatedSource = generateWithReflectiveAdapter(ReflectiveEnumRawKindAdapter::class.java.name)

        assertTrue(generatedSource.contains("public val ENVIRONMENT: String = \"PROD\""))
    }

    @Test
    fun generateRendersValuesThroughReflectiveAdapterWithStringRawKind() {
        val generatedSource = generateWithReflectiveAdapter(ReflectiveStringRawKindAdapter::class.java.name)

        assertTrue(generatedSource.contains("public val ENVIRONMENT: String = \"PROD\""))
    }

    @Test
    fun generateRendersValuesThroughReflectiveAdapterWithFieldOnlyRawKind() {
        val generatedSource = generateWithReflectiveAdapter(ReflectiveFieldOnlyRawKindAdapter::class.java.name)

        assertTrue(generatedSource.contains("public val ENVIRONMENT: String = \"PROD\""))
    }

    @Test
    fun generateRejectsReflectiveAdapterWithoutRawKind() {
        val error = generateWithReflectiveAdapterFails(ReflectiveMissingRawKindAdapter::class.java.name)

        assertMessageContains(error, "must expose a 'rawKind' property or getter")
    }

    @Test
    fun generateRejectsReflectiveAdapterWithNullRawKind() {
        val error = generateWithReflectiveAdapterFails(ReflectiveNullRawKindAdapter::class.java.name)

        assertMessageContains(error, "must expose 'rawKind' as a ConfigValueKind or a valid ConfigValueKind name")
    }

    @Test
    fun generateRejectsReflectiveAdapterWithWrongRawKindType() {
        val error = generateWithReflectiveAdapterFails(ReflectiveWrongTypeRawKindAdapter::class.java.name)

        assertMessageContains(error, "must expose 'rawKind' as a ConfigValueKind or a valid ConfigValueKind name")
    }

    @Test
    fun generateRejectsReflectiveAdapterWithInvalidRawKindName() {
        val error = generateWithReflectiveAdapterFails(ReflectiveInvalidRawKindAdapter::class.java.name)

        assertMessageContains(error, "exposes rawKind 'TEXT', which is not a valid ConfigValueKind")
        assertTrue(error.cause is IllegalArgumentException)
    }

    @Test
    fun generateDoesNotBypassThrowingReflectiveRawKindGetter() {
        val error = generateWithReflectiveAdapterFails(ReflectiveThrowingRawKindGetterAdapter::class.java.name)

        assertMessageContains(error, "Failed to read 'rawKind'", "rawKind getter failed")
        assertTrue(error.cause is IllegalStateException)
    }

    @Test
    fun generateRejectsReflectiveAdapterRawKindMismatch() {
        val error = generateWithReflectiveAdapterFails(ReflectiveMismatchedRawKindAdapter::class.java.name)

        assertMessageContains(
            error,
            "declares raw kind 'BOOLEAN'",
            "schema expects 'STRING'",
        )
    }

    @Test
    fun generateRejectsMissingCustomAdapterClass() {
        val error = generateWithReflectiveAdapterFails("sample.missing.Adapter")

        assertMessageContains(
            error,
            "Failed to load custom adapter class 'sample.missing.Adapter'",
            "Ensure it is on the Gradle build classpath",
        )
    }

    @Test
    fun generateRejectsAdapterWithoutPublicZeroArgumentConstructor() {
        val className = ReflectiveNonInstantiableAdapter::class.java.name
        val error = generateWithReflectiveAdapterFails(className)

        assertMessageContains(
            error,
            "Custom adapter class '$className' must be a Kotlin object or expose a public zero-argument constructor",
        )
    }

    @Test
    fun generateRejectsReflectiveAdapterWithoutKotlinType() {
        val className = ReflectiveMissingKotlinTypeAdapter::class.java.name
        val error = generateWithReflectiveAdapterFails(className)

        assertMessageContains(error, "Custom adapter '$className' must expose a 'kotlinType' property or getter")
    }

    @Test
    fun generateRejectsReflectiveAdapterWithWrongKotlinType() {
        val className = ReflectiveWrongKotlinTypeAdapter::class.java.name
        val error = generateWithReflectiveAdapterFails(className)

        assertMessageContains(error, "Custom adapter '$className' must expose 'kotlinType' as a TypeName")
    }

    @Test
    fun generateRejectsReflectiveAdapterWithoutParseMethod() {
        val className = ReflectiveMissingParseMethodAdapter::class.java.name
        val error = generateWithReflectiveAdapterFails(className)

        assertMessageContains(
            error,
            "Custom adapter '$className' must define a 'parse' method that accepts exactly one argument",
        )
    }

    @Test
    fun generateRejectsCustomAdapterRawKindMismatch() {
        val projectDir = createTempDirectory(prefix = "kayan-generate-task-test").toFile()
        val task = configuredGenerateTask(
            projectDir = projectDir,
            baseConfig = """
                {
                  "environment": "prod",
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            schemaEntries = listOf(
                bundleIdEntry(),
                environmentEntry(adapterClassName = BooleanRawKindAdapter::class.java.name),
            ),
        )

        val error = assertFailsWith<GradleException> {
            task.generate()
        }

        assertMessageContains(
            error,
            "declares raw kind 'BOOLEAN'",
            "schema expects 'STRING'",
        )
    }

    private fun configuredGenerateTask(
        projectDir: File,
        baseConfig: String,
        schemaEntries: List<KayanSchemaEntrySpec>,
    ): GenerateKayanConfigTask {
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val baseFile = File(projectDir, "default.json").apply {
            writeText(baseConfig)
        }
        val outputDir = File(projectDir, "build/generated/kayan/kotlin")

        return project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) {
            it.kotlinPluginApplied.set(true)
            it.packageName.set("sample.config")
            it.flavor.set("prod")
            it.generatedTargetNames.set(emptyList())
            it.className.set("KayanConfig")
            it.declarationMode.set(KayanDeclarationMode.OBJECT)
            it.baseConfigFile.set(baseFile)
            it.outputDir.set(outputDir)
            it.schemaEntries.set(schemaEntries.map(KayanSchemaEntrySpec::serialize))
        }.get()
    }

    private fun generateWithReflectiveAdapter(adapterClassName: String): String {
        val projectDir = createTempDirectory(prefix = "kayan-reflective-adapter-test").toFile()
        configuredReflectiveAdapterTask(projectDir, adapterClassName).generate()

        return File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt").readText()
    }

    private fun generateWithReflectiveAdapterFails(adapterClassName: String): GradleException {
        val projectDir = createTempDirectory(prefix = "kayan-reflective-adapter-test").toFile()
        val task = configuredReflectiveAdapterTask(projectDir, adapterClassName)

        return assertFailsWith {
            task.generate()
        }
    }

    private fun configuredReflectiveAdapterTask(projectDir: File, adapterClassName: String): GenerateKayanConfigTask =
        configuredGenerateTask(
            projectDir = projectDir,
            baseConfig = """
                {
                  "environment": "prod",
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            schemaEntries = listOf(
                bundleIdEntry(),
                environmentEntry(adapterClassName = adapterClassName),
            ),
        )

    private fun bundleIdEntry(): KayanSchemaEntrySpec = KayanSchemaEntrySpec(
        jsonKey = "bundle_id",
        propertyName = "BUNDLE_ID",
        kind = ConfigValueKind.STRING,
        required = true,
        nullable = false,
    )

    private fun environmentEntry(adapterClassName: String): KayanSchemaEntrySpec = KayanSchemaEntrySpec(
        jsonKey = "environment",
        propertyName = "ENVIRONMENT",
        kind = ConfigValueKind.STRING,
        required = true,
        nullable = false,
        adapterClassName = adapterClassName,
    )
}

internal object TaskStringAdapter : BuildTimeConfigAdapter<String> {
    override val rawKind: ConfigValueKind = ConfigValueKind.STRING
    override val kotlinType: com.squareup.kotlinpoet.TypeName = STRING

    override fun parse(rawValue: Any): String = rawValue.toString().uppercase()

    override fun renderKotlin(value: String): String = "\"$value\""
}

internal object BooleanRawKindAdapter : BuildTimeConfigAdapter<String> {
    override val rawKind: ConfigValueKind = ConfigValueKind.BOOLEAN
    override val kotlinType: com.squareup.kotlinpoet.TypeName = STRING

    override fun parse(rawValue: Any): String = rawValue.toString()

    override fun renderKotlin(value: String): String = "\"$value\""
}

internal abstract class ReflectiveTaskAdapter {
    public val kotlinType: com.squareup.kotlinpoet.TypeName = STRING

    public fun parse(rawValue: Any): String = rawValue.toString().uppercase()

    public fun renderKotlin(value: String): String = "\"$value\""
}

internal object ReflectiveEnumRawKindAdapter : ReflectiveTaskAdapter() {
    public val rawKind: ConfigValueKind = ConfigValueKind.STRING
}

internal object ReflectiveStringRawKindAdapter : ReflectiveTaskAdapter() {
    @Suppress("MayBeConstant")
    public val rawKind: String = "STRING"
}

internal object ReflectiveFieldOnlyRawKindAdapter : ReflectiveTaskAdapter() {
    @JvmField
    public val rawKind: ConfigValueKind = ConfigValueKind.STRING
}

internal object ReflectiveMissingRawKindAdapter : ReflectiveTaskAdapter()

internal object ReflectiveNullRawKindAdapter : ReflectiveTaskAdapter() {
    public val rawKind: ConfigValueKind? = null
}

internal object ReflectiveWrongTypeRawKindAdapter : ReflectiveTaskAdapter() {
    @Suppress("MayBeConstant")
    public val rawKind: Int = 1
}

internal object ReflectiveInvalidRawKindAdapter : ReflectiveTaskAdapter() {
    @Suppress("MayBeConstant")
    public val rawKind: String = "TEXT"
}

internal object ReflectiveThrowingRawKindGetterAdapter : ReflectiveTaskAdapter() {
    public val rawKind: ConfigValueKind = ConfigValueKind.STRING
        get() = if (field == ConfigValueKind.STRING) {
            error("rawKind getter failed")
        } else {
            field
        }
}

internal object ReflectiveMismatchedRawKindAdapter : ReflectiveTaskAdapter() {
    @Suppress("MayBeConstant")
    public val rawKind: String = "BOOLEAN"
}

internal class ReflectiveNonInstantiableAdapter private constructor(@Suppress("unused") private val value: String)

internal object ReflectiveMissingKotlinTypeAdapter {
    public val rawKind: ConfigValueKind = ConfigValueKind.STRING

    public fun parse(rawValue: Any): String = rawValue.toString()

    public fun renderKotlin(value: String): String = "\"$value\""
}

internal object ReflectiveWrongKotlinTypeAdapter {
    @Suppress("MayBeConstant")
    public val kotlinType: String = "String"
    public val rawKind: ConfigValueKind = ConfigValueKind.STRING

    public fun parse(rawValue: Any): String = rawValue.toString()

    public fun renderKotlin(value: String): String = "\"$value\""
}

internal object ReflectiveMissingParseMethodAdapter {
    public val kotlinType: com.squareup.kotlinpoet.TypeName = STRING
    public val rawKind: ConfigValueKind = ConfigValueKind.STRING

    public fun renderKotlin(value: String): String = "\"$value\""
}
