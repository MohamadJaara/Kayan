package io.kayan.gradle

import io.kayan.ConfigValueKind

internal enum class TestReleaseStage {
    BETA,
    PROD,
}

internal object ReleaseStageAdapter : BuildTimeConfigAdapter<TestReleaseStage> {
    override val rawKind: ConfigValueKind = ConfigValueKind.STRING
    override val kotlinType: String = "sample.ReleaseStage"

    override fun parse(rawValue: Any): TestReleaseStage = TestReleaseStage.valueOf((rawValue as String).uppercase())

    override fun renderKotlin(value: TestReleaseStage): String = "sample.ReleaseStage.${value.name}"
}
