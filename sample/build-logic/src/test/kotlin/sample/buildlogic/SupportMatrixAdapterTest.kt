package sample.buildlogic

import com.squareup.kotlinpoet.CodeBlock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SupportMatrixAdapterTest {
    @Test
    fun renderKotlinSafelyQuotesConfigDerivedStrings() {
        val injectedExpression = "\${error(\"marker\")}"
        val links = listOf(
            "\$plainTemplate",
            "quote\" slash\\ newline\n carriage\r tab\t",
            "Grüße",
        )

        val rendered = SupportMatrixAdapter.renderKotlin(
            SupportMatrixAdapter.parse(linkedMapOf(injectedExpression to links)),
        )

        (listOf(injectedExpression) + links).forEach { value ->
            assertContains(rendered, CodeBlock.of("%S", value).toString())
        }
        assertContains(rendered, "{'${'$'}'}")
        assertFalse(rendered.contains("\"${'$'}{error("))
    }
}
