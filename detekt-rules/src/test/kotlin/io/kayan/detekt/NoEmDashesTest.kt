package io.kayan.detekt

import dev.detekt.api.Config
import dev.detekt.api.SourceLocation
import dev.detekt.api.TextLocation
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoEmDashesTest {
    private val emDash = '\u2014'

    @Test
    fun reportsEmDashesInLineCommentsBlockCommentsAndKdoc() {
        val code = "// $emDash\n/* $emDash */\n/** $emDash */\nfun example() = Unit"

        val findings = NoEmDashes(Config.empty).lint(code)

        assertEquals(
            listOf(SourceLocation(1, 4), SourceLocation(2, 4), SourceLocation(3, 5)),
            findings.map { it.entity.location.source },
        )
    }

    @Test
    fun reportsEveryEmDashInStringsCharactersAndRawStrings() {
        val code = "val text = \"$emDash$emDash\"\nval character = '$emDash'\nval raw = \"\"\"$emDash\"\"\""

        val findings = NoEmDashes(Config.empty).lint(code)

        assertEquals(4, findings.size)
        findings.forEach { finding ->
            val location = finding.entity.location
            assertEquals(TextLocation(location.text.start, location.text.start + 1), location.text)
            assertEquals(code.indexOf(emDash, location.text.start), location.text.start)
            assertEquals(location.source.column + 1, location.endSource.column)
        }
    }

    @Test
    fun acceptsHyphensEnDashesAndEscapedUnicode() {
        val code = "// hyphen - and en dash \u2013\nval escaped = \"\\u2014\""

        assertTrue(NoEmDashes(Config.empty).lint(code).isEmpty())
    }
}
