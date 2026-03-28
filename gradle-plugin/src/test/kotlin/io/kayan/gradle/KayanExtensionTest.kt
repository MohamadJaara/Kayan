package io.kayan.gradle

import io.kayan.ConfigValueKind
import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalKayanGenerationApi::class)
class KayanExtensionTest {
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

    private fun createExtension(): KayanExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create("kayan", KayanExtension::class.java)
    }
}
