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
