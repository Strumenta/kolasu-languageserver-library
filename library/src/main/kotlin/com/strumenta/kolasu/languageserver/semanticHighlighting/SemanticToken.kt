package com.strumenta.kolasu.languageserver.semanticHighlighting

import com.strumenta.kolasu.model.Position

data class SemanticToken(
        val position: Position,
        val type: SemanticTokenType,
        val modifiers: List<SemanticTokenModifier>
)
