package io.kayan.gradle

import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalKayanGenerationApi::class)
class KayanTargetSourceSetContainerTest {

    private fun createContainer(): KayanTargetSourceSetContainer {
        val project = ProjectBuilder.builder().build()
        return project.objects.newInstance(KayanTargetSourceSetContainer::class.java)
    }

    // -------- sourceSet(String, String) --------

    @Test
    fun sourceSetStringStringAddsEntryWithCorrectNames() {
        val container = createContainer()

        container.sourceSet("jvmMain", "jvm")

        val entries = container.entries
        assertEquals(1, entries.size)
        assertEquals("jvmMain", entries[0].sourceSetName.get())
        assertEquals("jvm", entries[0].targetName.get())
    }

    @Test
    fun sourceSetStringStringAddsMultipleEntries() {
        val container = createContainer()

        container.sourceSet("jvmMain", "jvm")
        container.sourceSet("iosMain", "ios")

        assertEquals(2, container.entries.size)
    }

    // -------- sourceSet(Action) --------

    @Test
    fun sourceSetActionAddsEntryConfiguredByAction() {
        val container = createContainer()

        container.sourceSet(
            Action { spec ->
                spec.sourceSetName.set("androidMain")
                spec.targetName.set("android")
            },
        )

        val entries = container.entries
        assertEquals(1, entries.size)
        assertEquals("androidMain", entries[0].sourceSetName.get())
        assertEquals("android", entries[0].targetName.get())
    }

    // -------- target(String) --------

    @Test
    fun targetAndroidMapsToAndroidMain() {
        val container = createContainer()

        container.target("android")

        assertEquals(1, container.entries.size)
        assertEquals("androidMain", container.entries[0].sourceSetName.get())
        assertEquals("android", container.entries[0].targetName.get())
    }

    @Test
    fun targetIosMapsToIosMain() {
        val container = createContainer()

        container.target("ios")

        assertEquals("iosMain", container.entries[0].sourceSetName.get())
        assertEquals("ios", container.entries[0].targetName.get())
    }

    @Test
    fun targetJvmMapsToJvmMain() {
        val container = createContainer()

        container.target("jvm")

        assertEquals("jvmMain", container.entries[0].sourceSetName.get())
        assertEquals("jvm", container.entries[0].targetName.get())
    }

    @Test
    fun targetJsMapsToJsMain() {
        val container = createContainer()

        container.target("js")

        assertEquals("jsMain", container.entries[0].sourceSetName.get())
        assertEquals("js", container.entries[0].targetName.get())
    }

    @Test
    fun targetWasmJsMapsToWasmJsMain() {
        val container = createContainer()

        container.target("wasmJs")

        assertEquals("wasmJsMain", container.entries[0].sourceSetName.get())
        assertEquals("wasmJs", container.entries[0].targetName.get())
    }

    @Test
    fun targetTrimsWhitespaceBeforeLookup() {
        val container = createContainer()

        container.target("  jvm  ")

        assertEquals("jvmMain", container.entries[0].sourceSetName.get())
        assertEquals("  jvm  ", container.entries[0].targetName.get())
    }

    @Test
    fun targetThrowsForUnsupportedTargetName() {
        val container = createContainer()

        val exception = assertFailsWith<IllegalArgumentException> {
            container.target("unknown")
        }

        assertTrue(exception.message?.contains("unknown") == true)
    }

    @Test
    fun targetThrowsForUnsupportedTargetNameWithHelpfulMessage() {
        val container = createContainer()

        val exception = assertFailsWith<IllegalArgumentException> {
            container.target("desktop")
        }

        assertTrue(exception.message?.contains("sourceSet") == true)
    }

    // -------- targets(vararg) --------

    @Test
    fun targetsVarargAddsAllConventionalMappings() {
        val container = createContainer()

        container.targets("android", "ios", "jvm")

        assertEquals(3, container.entries.size)
        assertEquals("androidMain", container.entries[0].sourceSetName.get())
        assertEquals("iosMain", container.entries[1].sourceSetName.get())
        assertEquals("jvmMain", container.entries[2].sourceSetName.get())
    }

    @Test
    fun targetsVarargWithEmptyArrayAddsNoEntries() {
        val container = createContainer()

        container.targets()

        assertEquals(0, container.entries.size)
    }

    // -------- android() --------

    @Test
    fun androidAddsAndroidMainWithDefaultTargetName() {
        val container = createContainer()

        container.android()

        assertEquals("androidMain", container.entries[0].sourceSetName.get())
        assertEquals("android", container.entries[0].targetName.get())
    }

    @Test
    fun androidAddsAndroidMainWithCustomTargetName() {
        val container = createContainer()

        container.android("my-android")

        assertEquals("androidMain", container.entries[0].sourceSetName.get())
        assertEquals("my-android", container.entries[0].targetName.get())
    }

    // -------- ios() --------

    @Test
    fun iosAddsIosMainWithDefaultTargetName() {
        val container = createContainer()

        container.ios()

        assertEquals("iosMain", container.entries[0].sourceSetName.get())
        assertEquals("ios", container.entries[0].targetName.get())
    }

    @Test
    fun iosAddsIosMainWithCustomTargetName() {
        val container = createContainer()

        container.ios("ios-custom")

        assertEquals("iosMain", container.entries[0].sourceSetName.get())
        assertEquals("ios-custom", container.entries[0].targetName.get())
    }

    // -------- jvm() --------

    @Test
    fun jvmAddsJvmMainWithDefaultTargetName() {
        val container = createContainer()

        container.jvm()

        assertEquals("jvmMain", container.entries[0].sourceSetName.get())
        assertEquals("jvm", container.entries[0].targetName.get())
    }

    @Test
    fun jvmAddsJvmMainWithCustomTargetName() {
        val container = createContainer()

        container.jvm("desktop")

        assertEquals("jvmMain", container.entries[0].sourceSetName.get())
        assertEquals("desktop", container.entries[0].targetName.get())
    }

    // -------- js() --------

    @Test
    fun jsAddsJsMainWithDefaultTargetName() {
        val container = createContainer()

        container.js()

        assertEquals("jsMain", container.entries[0].sourceSetName.get())
        assertEquals("js", container.entries[0].targetName.get())
    }

    @Test
    fun jsAddsJsMainWithCustomTargetName() {
        val container = createContainer()

        container.js("browser")

        assertEquals("jsMain", container.entries[0].sourceSetName.get())
        assertEquals("browser", container.entries[0].targetName.get())
    }

    // -------- wasmJs() --------

    @Test
    fun wasmJsAddsWasmJsMainWithDefaultTargetName() {
        val container = createContainer()

        container.wasmJs()

        assertEquals("wasmJsMain", container.entries[0].sourceSetName.get())
        assertEquals("wasmJs", container.entries[0].targetName.get())
    }

    @Test
    fun wasmJsAddsWasmJsMainWithCustomTargetName() {
        val container = createContainer()

        container.wasmJs("wasm")

        assertEquals("wasmJsMain", container.entries[0].sourceSetName.get())
        assertEquals("wasm", container.entries[0].targetName.get())
    }

    // -------- ordering --------

    @Test
    fun entriesPreserveInsertionOrder() {
        val container = createContainer()

        container.ios()
        container.jvm("desktop")
        container.sourceSet("appleMain", "ios-shared")

        val names = container.entries.map { it.sourceSetName.get() }
        assertEquals(listOf("iosMain", "jvmMain", "appleMain"), names)
    }
}