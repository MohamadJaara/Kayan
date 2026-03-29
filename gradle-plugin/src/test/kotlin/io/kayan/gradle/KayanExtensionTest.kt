package io.kayan.gradle

import arrow.core.Either
import io.kayan.ConfigFormat
import io.kayan.ConfigValueKind
import io.kayan.KayanValidationMode
import io.kayan.assertMessageContains
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalKayanGenerationApi::class)
class KayanExtensionTest {
    @Test
    fun deserializeInheritedRootEntriesReturnsPluginConfigurationErrorForInvalidSerializedEntry() {
        when (val result = deserializeInheritedRootEntriesEither(listOf("[]"))) {
            is Either.Left -> {
                val error = result.value

                assertTrue(error is PluginConfigurationError.InvalidInheritedSchemaEntry)
                assertMessageContains(
                    error.toGradleException(),
                    "Failed to deserialize inherited Kayan root schema entry",
                    "entry #0",
                )
            }

            is Either.Right -> error("Expected inherited root entry deserialization to fail.")
        }
    }

    @Test
    fun inheritFromRootUsesRootConventionsAndIncludedSchemaEntries() {
        val projects = createProjectHierarchy()
        val rootExtension = projects.root.extensions.getByType(KayanRootExtension::class.java)
        val childExtension = projects.child.extensions.getByType(KayanExtension::class.java)

        rootExtension.flavor.set("prod")
        rootExtension.baseConfigFile.set(projects.root.layout.projectDirectory.file("shared.json"))
        rootExtension.customConfigFile.set(projects.root.layout.projectDirectory.file("custom.json"))
        rootExtension.configFormat.set(ConfigFormat.YAML)
        rootExtension.validationMode.set(KayanValidationMode.STRICT)
        rootExtension.schema {
            boolean("use_unified_core_crypto", "USE_UNIFIED_CORE_CRYPTO", required = true)
            string("provider_cache_scope", "PROVIDER_CACHE_SCOPE", required = true)
        }

        childExtension.inheritFromRoot()
        childExtension.schema {
            include("use_unified_core_crypto")
        }

        val specs = childExtension.serializedSchemaEntries().map(KayanSchemaEntrySpec::deserialize)

        assertEquals("prod", childExtension.flavor.get())
        assertEquals("shared.json", childExtension.baseConfigFile.get().asFile.name)
        assertEquals("custom.json", childExtension.customConfigFile.get().asFile.name)
        assertEquals(ConfigFormat.YAML, childExtension.configFormat.get())
        assertEquals(KayanValidationMode.STRICT, childExtension.validationMode.get())
        assertEquals(listOf("use_unified_core_crypto"), specs.map(KayanSchemaEntrySpec::jsonKey))
    }

    @Test
    fun inheritFromRootRejectsLocalSchemaEntries() {
        val projects = createProjectHierarchy()
        val rootExtension = projects.root.extensions.getByType(KayanRootExtension::class.java)
        val childExtension = projects.child.extensions.getByType(KayanExtension::class.java)

        rootExtension.schema {
            string("bundle_id", "BUNDLE_ID", required = true)
        }
        childExtension.schema {
            string("module_only_key", "MODULE_ONLY_KEY")
        }

        val error = assertFailsWith<GradleException> {
            childExtension.inheritFromRoot()
        }

        assertMessageContains(
            error,
            "does not support module-local schema entries",
            "Define new keys in `kayanRoot { schema { ... } }`",
        )
    }

    @Test
    fun inheritFromRootRejectsUnknownIncludedSchemaKeyWithSuggestions() {
        val projects = createProjectHierarchy()
        val rootExtension = projects.root.extensions.getByType(KayanRootExtension::class.java)
        val childExtension = projects.child.extensions.getByType(KayanExtension::class.java)

        rootExtension.schema {
            boolean("use_unified_core_crypto", "USE_UNIFIED_CORE_CRYPTO", required = true)
        }
        childExtension.inheritFromRoot()
        childExtension.schema {
            include("use_unified_core_crypt")
        }

        val error = assertFailsWith<GradleException> {
            childExtension.serializedSchemaEntries()
        }

        assertMessageContains(
            error,
            "could not find shared schema key 'use_unified_core_crypt'",
            "Did you mean 'use_unified_core_crypto'?",
        )
    }

    @Test
    fun schemaInclusionRequiresInheritFromRoot() {
        val extension = createExtension()

        extension.schema {
            include("bundle_id")
        }

        val error = assertFailsWith<GradleException> {
            extension.serializedSchemaEntries()
        }

        assertMessageContains(
            error,
            "schema inclusion requires `inheritFromRoot()`",
            "`include(...)`",
            "`includeAll()`",
        )
    }

    @Test
    fun schemaActionPropagatesPreventOverrideAcrossBuilderEntryKinds() {
        val extension = createExtension()

        extension.schema(
            Action { schema ->
                schema.string("string_key", "STRING_KEY", preventOverride = true)
                schema.boolean("boolean_key", "BOOLEAN_KEY", preventOverride = true)
                schema.int("int_key", "INT_KEY", preventOverride = true)
                schema.long("long_key", "LONG_KEY", preventOverride = true)
                schema.double("double_key", "DOUBLE_KEY", preventOverride = true)
                schema.stringMap("string_map_key", "STRING_MAP_KEY", preventOverride = true)
                schema.stringList("string_list_key", "STRING_LIST_KEY", preventOverride = true)
                schema.stringListMap("string_list_map_key", "STRING_LIST_MAP_KEY", preventOverride = true)
                schema.enumValue(
                    jsonKey = "enum_value_key",
                    propertyName = "ENUM_VALUE_KEY",
                    enumTypeName = "sample.Stage",
                    preventOverride = true,
                )
                schema.enum(
                    jsonKey = "enum_key",
                    propertyName = "ENUM_KEY",
                    enumTypeName = "sample.Mode",
                    preventOverride = true,
                )
                schema.custom(
                    jsonKey = "custom_key",
                    propertyName = "CUSTOM_KEY",
                    rawKind = ConfigValueKind.STRING,
                    adapter = "sample.Adapter",
                    preventOverride = true,
                )
            },
        )

        val specs = extension.serializedSchemaEntries().map(KayanSchemaEntrySpec::deserialize)

        assertEquals(11, specs.size)
        assertTrue(specs.all(KayanSchemaEntrySpec::preventOverride))
        assertEquals("sample.Stage", specs.single { it.jsonKey == "enum_value_key" }.enumTypeName)
        assertEquals("sample.Mode", specs.single { it.jsonKey == "enum_key" }.enumTypeName)
        assertEquals("sample.Adapter", specs.single { it.jsonKey == "custom_key" }.adapterClassName)
    }

    @Test
    fun androidFlavorSourceSetsActionStoresConfiguredFlavors() {
        val extension = createExtension()

        extension.androidFlavorSourceSets(
            Action { spec ->
                spec.flavors.set(listOf("prod", "staging"))
            },
        )

        assertEquals(listOf("prod", "staging"), extension.androidFlavorSourceSetFlavors())
    }

    @Test
    fun schemaLambdaUsesDefaultPreventOverrideAcrossBuilderEntryKinds() {
        val extension = createExtension()

        extension.schema {
            string("string_key", "STRING_KEY")
            boolean("boolean_key", "BOOLEAN_KEY")
            int("int_key", "INT_KEY")
            long("long_key", "LONG_KEY")
            double("double_key", "DOUBLE_KEY")
            stringMap("string_map_key", "STRING_MAP_KEY")
            stringList("string_list_key", "STRING_LIST_KEY")
            stringListMap("string_list_map_key", "STRING_LIST_MAP_KEY")
            enumValue("enum_value_key", "ENUM_VALUE_KEY", "sample.Stage")
            enum("enum_key", "ENUM_KEY", "sample.Mode")
            custom("custom_key", "CUSTOM_KEY", ConfigValueKind.STRING, "sample.Adapter")
        }

        val specs = extension.serializedSchemaEntries().map(KayanSchemaEntrySpec::deserialize)

        assertEquals(11, specs.size)
        assertTrue(specs.none(KayanSchemaEntrySpec::preventOverride))
        assertFalse(specs.single { it.jsonKey == "enum_value_key" }.enumTypeName.isNullOrBlank())
        assertFalse(specs.single { it.jsonKey == "custom_key" }.adapterClassName.isNullOrBlank())
    }

    @Test
    fun androidFlavorSourceSetsLambdaStoresConfiguredFlavors() {
        val extension = createExtension()

        extension.androidFlavorSourceSets {
            flavors.set(listOf("prod", "beta"))
        }

        assertEquals(listOf("prod", "beta"), extension.androidFlavorSourceSetFlavors())
    }

    @Test
    fun targetSourceSetsStoreConfiguredMappings() {
        val extension = createExtension()

        extension.targetSourceSets {
            sourceSet("iosMain", "ios")
            sourceSet("jvmMain", "jvm")
        }

        assertEquals(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
            ),
            extension.targetSourceSetMappings(),
        )
    }

    @Test
    fun targetSourceSetsDslRejectsMissingSourceSetNameImmediately() {
        val extension = createExtension()

        val error = assertFailsWith<IllegalArgumentException> {
            extension.targetSourceSets {
                sourceSet(
                    Action { spec ->
                        spec.targetName.set("ios")
                    },
                )
            }
        }

        assertMessageContains(
            error,
            "Invalid Kayan target source set mapping:",
            "sourceSetName=<unset>",
            "targetName='ios'",
        )
    }

    @Test
    fun targetSourceSetsDslRejectsBlankTargetNameImmediately() {
        val extension = createExtension()

        val error = assertFailsWith<IllegalArgumentException> {
            extension.targetSourceSets {
                sourceSet(
                    Action { spec ->
                        spec.sourceSetName.set("iosMain")
                        spec.targetName.set("   ")
                    },
                )
            }
        }

        assertMessageContains(
            error,
            "Invalid Kayan target source set mapping:",
            "sourceSetName='iosMain'",
            "targetName='   '",
            "Both sourceSetName and targetName must be configured with non-blank values.",
        )
    }

    @Test
    fun targetsVarargStoresConventionalMappings() {
        val extension = createExtension()

        extension.targets("android", "ios", "jvm")

        assertEquals(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "androidMain", targetName = "android"),
                KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
            ),
            extension.targetSourceSetMappings(),
        )
    }

    @Test
    fun targetsVarargNormalizesWhitespaceInConventionalTargetNames() {
        val extension = createExtension()

        extension.targets(" jvm ")

        assertEquals(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
            ),
            extension.targetSourceSetMappings(),
        )
    }

    @Test
    fun targetsVarargRejectsUnsupportedConventionalTargetAsGradleException() {
        val extension = createExtension()

        val error = assertFailsWith<GradleException> {
            extension.targets("desktop")
        }

        assertMessageContains(
            error,
            "Unsupported Kayan target 'desktop'.",
            "'android'",
            "'ios'",
            "'jvm'",
            "'js'",
            "'wasmJs'",
            "sourceSet(\"<sourceSet>\", \"desktop\")",
        )
    }

    @Test
    fun targetsDslStoresConvenienceAndExplicitMappings() {
        val extension = createExtension()

        extension.targets {
            ios()
            jvm("desktop")
            sourceSet("appleMain", "ios-shared")
        }

        assertEquals(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "desktop"),
                KayanTargetSourceSetMapping(sourceSetName = "appleMain", targetName = "ios-shared"),
            ),
            extension.targetSourceSetMappings(),
        )
    }

    @Test
    fun pluginDefaultsValidationModeToSubset() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(KayanConfigPlugin::class.java)

        val extension = project.extensions.getByType(KayanExtension::class.java)

        assertEquals(KayanValidationMode.SUBSET, extension.validationMode.get())
    }

    private fun createExtension(): KayanExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create("kayan", KayanExtension::class.java).apply {
            owningProject = project
        }
    }

    private fun createProjectHierarchy(): RootChildProjects {
        val root = ProjectBuilder.builder().withName("root").build()
        val child = ProjectBuilder.builder().withName("child").withParent(root).build()

        root.plugins.apply(KayanConfigPlugin::class.java)
        child.plugins.apply(KayanConfigPlugin::class.java)

        return RootChildProjects(root = root, child = child)
    }

    private data class RootChildProjects(
        val root: Project,
        val child: Project,
    )
}
