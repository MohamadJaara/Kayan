package io.kayan.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File

internal fun kayanGradleRunner(projectDir: File, vararg tasks: String): GradleRunner {
    val coverageArguments = if ("--configuration-cache" in tasks) {
        // Gradle does not yet support Java agents in configuration-cache TestKit builds.
        emptyList()
    } else {
        testKitCoverageArguments(projectDir)
    }
    val arguments = buildList {
        addAll(coverageArguments)
        addAll(tasks)
        add("--stacktrace")
    }

    return GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(arguments)
}

private fun testKitCoverageArguments(projectDir: File): List<String> {
    val agentArgument = System.getProperty("kayan.testkit.jacoco.agentArgument")
        ?.takeIf(String::isNotBlank)
        ?: return emptyList()
    val initScript = File(projectDir, ".kayan-testkit-jacoco.gradle").apply {
        writeText(
            """
                import java.lang.management.ManagementFactory
                import javax.management.ObjectName

                gradle.buildFinished {
                    def server = ManagementFactory.platformMBeanServer
                    def runtime = new ObjectName("org.jacoco:type=Runtime")
                    if (server.isRegistered(runtime)) {
                        server.invoke(runtime, "dump", [true] as Object[], ["boolean"] as String[])
                    }
                }
            """.trimIndent(),
        )
    }

    return listOf(
        "-Dorg.gradle.jvmargs=$agentArgument",
        "--init-script",
        initScript.absolutePath,
    )
}
