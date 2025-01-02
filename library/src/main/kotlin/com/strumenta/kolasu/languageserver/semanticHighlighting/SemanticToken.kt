package com.strumenta.kolasu.languageserver.semanticHighlighting

import com.strumenta.kolasu.model.Position
import org.eclipse.lsp4j.SemanticTokens

data class SemanticToken(val position: Position, val type: SemanticTokenType, val modifiers: List<SemanticTokenModifier>)

fun encode(tokens: List<SemanticToken>): SemanticTokens {
    var lastLine = 1 // ? To offset that Kolasu lines start at 1
    var lastColumn = 0
    val data = mutableListOf<Int>()
    for (token in tokens) {
        data.addAll(listOf(
            token.position.start.line - lastLine,
            if (token.position.start.line == lastLine) token.position.start.column - lastColumn else token.position.start.column,
            token.position.end.column - token.position.start.column, // ! assumes tokens are in a single line
            token.type.ordinal,
            token.modifiers.sumOf { it.bit }
        ))
        lastLine = token.position.start.line
        lastColumn = token.position.start.column
    }
    return SemanticTokens(data)
}
