package io.kayan.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicationDependencyMetadataTest {
    @Test
    fun publishesKotlinPoetAsApiWithoutKotlinGradlePluginDependencies() {
        val publicationDirectory = File(
            assertNotNull(System.getProperty("kayan.plugin.publicationDirectory")),
        )
        val pomDependencies = pomDependencies(File(publicationDirectory, "pom-default.xml"))
        val variants = moduleVariants(File(publicationDirectory, "module.json"))

        assertEquals("compile", pomDependencies[KOTLIN_POET_COORDINATE])
        assertTrue(KOTLIN_POET_COORDINATE in variants.getValue("apiElements"))
        assertTrue(KOTLIN_POET_COORDINATE in variants.getValue("runtimeElements"))

        KOTLIN_GRADLE_PLUGIN_COORDINATES.forEach { coordinate ->
            assertFalse(coordinate in pomDependencies)
            assertFalse(coordinate in variants.getValue("apiElements"))
            assertFalse(coordinate in variants.getValue("runtimeElements"))
        }
    }

    private fun pomDependencies(pomFile: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
        val dependencies = document.getElementsByTagName("dependency")

        return buildMap {
            repeat(dependencies.length) { index ->
                val dependency = dependencies.item(index) as Element
                val group = dependency.getElementsByTagName("groupId").item(0).textContent
                val module = dependency.getElementsByTagName("artifactId").item(0).textContent
                val scope = dependency.getElementsByTagName("scope").item(0).textContent
                put("$group:$module", scope)
            }
        }
    }

    private fun moduleVariants(moduleFile: File): Map<String, Set<String>> {
        val root = Json.parseToJsonElement(moduleFile.readText()).jsonObject

        return root.getValue("variants").jsonArray.associate { variantElement ->
            val variant = variantElement.jsonObject
            val dependencies = variant["dependencies"]?.jsonArray.orEmpty().mapTo(linkedSetOf()) { dependency ->
                val dependencyObject = dependency.jsonObject
                val group = dependencyObject.getValue("group").jsonPrimitive.content
                val module = dependencyObject.getValue("module").jsonPrimitive.content
                "$group:$module"
            }
            variant.getValue("name").jsonPrimitive.content to dependencies
        }
    }

    private companion object {
        private const val KOTLIN_POET_COORDINATE = "com.squareup:kotlinpoet-jvm"
        private val KOTLIN_GRADLE_PLUGIN_COORDINATES = setOf(
            "org.jetbrains.kotlin:kotlin-gradle-plugin",
            "org.jetbrains.kotlin:kotlin-gradle-plugin-api",
        )
    }
}
