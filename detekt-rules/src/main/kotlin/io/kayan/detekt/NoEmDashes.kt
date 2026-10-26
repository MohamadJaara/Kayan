package io.kayan.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Location
import dev.detekt.api.Rule
import dev.detekt.api.SourceLocation
import dev.detekt.api.TextLocation
import org.jetbrains.kotlin.psi.KtFile

internal class NoEmDashes(config: Config) :
    Rule(
        config,
        "Em dashes (U+2014) are not allowed. Use a hyphen or rewrite the sentence.",
    ) {
    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)

        val path = Location.from(file).path
        var line = 1
        var column = 1
        file.text.forEachIndexed { offset, character ->
            if (character == '\u2014') {
                val location = Location(
                    SourceLocation(line, column),
                    SourceLocation(line, column + 1),
                    TextLocation(offset, offset + 1),
                    path,
                )
                report(Finding(Entity.from(file.findElementAt(offset) ?: file, location), description))
            }
            if (character == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
        }
    }
}
