package com.strumenta.kolasu.languageserver.semanticHighlighting

import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
import org.eclipse.lsp4j.SemanticTokens

data class SemanticToken(val position: Position, val type: SemanticTokenType, val modifiers: List<SemanticTokenModifier>)

fun encode(tokens: List<SemanticToken>, code: String): SemanticTokens {
    // Create synthetic token at start to compute relative positions from
    var lastToken = SemanticToken(Position(Point(1, 0), Point(1, 0)), SemanticTokenType.TYPE, listOf())

    val data = mutableListOf<Int>()
    for (token in tokens) {
        val deltaLine = token.position.start.line - (lastToken.position.start.line)
        val deltaStart = if (token.position.start.line == lastToken.position.start.line) {
            token.position.start.column - lastToken.position.start.column
        } else {
            token.position.start.column
        }
        val length = token.position.length(code)
        val tokenType = token.type.ordinal
        val tokenModifiers = token.modifiers.sumOf { it.bit }

        data.addAll(listOf(deltaLine, deltaStart, length, tokenType, tokenModifiers))
        lastToken = token
    }
    return SemanticTokens(data)
}
