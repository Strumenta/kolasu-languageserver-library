package com.strumenta.kolasu.languageserver.utils

import com.strumenta.kolasu.semantics.symbol.description.ReferenceValueDescription
import com.strumenta.kolasu.semantics.symbol.description.SymbolDescription
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import com.strumenta.kolasu.model.Point as KolasuPoint
import org.eclipse.lsp4j.Position as Lsp4jPosition
import com.strumenta.kolasu.model.Position as KolasuPosition
import org.eclipse.lsp4j.Range as Lsp4jRange

fun kolasuPositionToLsp4jRange(kolasuPosition: KolasuPosition): Lsp4jRange {
    return Lsp4jRange(
        Lsp4jPosition(kolasuPosition.start.line - 1, kolasuPosition.start.column),
        Lsp4jPosition(kolasuPosition.end.line - 1, kolasuPosition.end.column)
    )
}

fun lsp4jPositionToKolasuPosition(lsp4jPosition: Lsp4jPosition): KolasuPosition {
    return KolasuPosition(
        KolasuPoint(lsp4jPosition.line + 1, lsp4jPosition.character),
        KolasuPoint(lsp4jPosition.line + 1, lsp4jPosition.character)
    )
}

fun kolasuPositionToNodeId(uri: String, kolasuPosition: KolasuPosition): String {
    return lsp4jRangeToIdentifier(uri, kolasuPositionToLsp4jRange(kolasuPosition))
}

fun kolasuPositionToReferenceNodeId(uri: String, kolasuPosition: KolasuPosition): String {
    return "${kolasuPositionToNodeId(uri, kolasuPosition)}:ref"
}

fun lsp4jRangeToIdentifier(uri: String, lsp4jRange: Lsp4jRange) =
    "${uri}:${lsp4jRange.start.line}:${lsp4jRange.start.character}:${lsp4jRange.end.line}:${lsp4jRange.end.character}"

fun SymbolDescription.toLsp4jRange(): Lsp4jRange {
    val startLine = this.fields["startLine"]?.value as? String
    val startCharacter = this.fields["startCharacter"]?.value as? String
    val startPosition = Position(startLine?.toInt() ?: 0, startCharacter?.toInt() ?: 1)
    val endLine = this.fields["endLine"]?.value as? String
    val endCharacter = this.fields["endCharacter"]?.value as? String
    val endPosition = Position(endLine?.toInt() ?: 0, endCharacter?.toInt() ?: 1)
    return Range(startPosition, endPosition)
}

fun lsp4jRangeFromSymbolDescription(symbolDescription: SymbolDescription): Lsp4jRange {
    val startLine = symbolDescription.fields["startLine"]?.value as? String
    val startCharacter = symbolDescription.fields["startCharacter"]?.value as? String
    val startPosition = Position(startLine?.toInt() ?: 0, startCharacter?.toInt() ?: 1)
    val endLine = symbolDescription.fields["endLine"]?.value as? String
    val endCharacter = symbolDescription.fields["endCharacter"]?.value as? String
    val endPosition = Position(endLine?.toInt() ?: 0, endCharacter?.toInt() ?: 1)
    return Range(startPosition, endPosition)
}

fun SymbolDescription.isReference(): Boolean {
    return this.fields["reference"]?.value as? Boolean == true
}

fun SymbolDescription.getUri(): String? {
    return this.fields["uri"]?.value as? String
}

fun SymbolDescription.getReferenceTarget(): String? {
    return this.fields["target"]?.value as? String
}

fun SymbolDescription.findReferenceFieldAtPosition(kolasuPosition: KolasuPosition): ReferenceValueDescription? {
    return this.fields.values.asSequence()
        .filterIsInstance<ReferenceValueDescription>()
        .filter { it.contains(kolasuPosition) }
        .sortedByReferenceByNamePosition().firstOrNull()
}

fun SymbolDescription.findReferencesFieldsWithTarget(targetNodeId: String): Sequence<ReferenceValueDescription> {
    return this.fields.values.asSequence()
        .filterIsInstance<ReferenceValueDescription>()
        .filter { it.value == targetNodeId }
}

fun Sequence<SymbolDescription>.sortedByNodePosition(): Sequence<SymbolDescription> {
    return this.sortedWith { leftSymbolDescription, rightSymbolDescription -> when {
        leftSymbolDescription.contains(rightSymbolDescription.position) -> 1
        rightSymbolDescription.contains(leftSymbolDescription.position) -> -1
        else -> 0
    }}
}

fun Sequence<ReferenceValueDescription>.sortedByReferenceByNamePosition(): Sequence<ReferenceValueDescription> {
    return this.sortedWith { leftReferenceValueDescription, rightReferenceValueDescription -> when {
        leftReferenceValueDescription.contains(rightReferenceValueDescription.position) -> 1
        rightReferenceValueDescription.contains(leftReferenceValueDescription.position) -> -1
        else -> 0
    }}
}
