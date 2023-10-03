package com.strumenta.rpgle

import com.strumenta.kolasu.languageserver.library.KolasuServer
import com.strumenta.kolasu.model.Node
import com.strumenta.rpgparser.RPGKolasuParser
import com.strumenta.rpgparser.model.*
import org.eclipse.lsp4j.SymbolKind

fun main() {
    val server = RPGServer()
    server.startCommunication()
}

class RPGServer : KolasuServer<CompilationUnit>(RPGKolasuParser()) {
    override fun symbolKindOf(node: Node): SymbolKind {
        if (node is StandaloneField) {
            if (node.type is CharacterType) {
                return SymbolKind.String
            } else if (node.type is PackedDecimalType) {
                return SymbolKind.Number
            } else if (node.type is ZonedDecimalType) {
                return SymbolKind.Number
            }
        } else if (node is Subfield) {
            if (node.type is CharacterType) {
                return SymbolKind.String
            } else if (node.type is PackedDecimalType) {
                return SymbolKind.Number
            } else if (node.type is ZonedDecimalType) {
                return SymbolKind.Number
            }
        } else if (node is StandardDataStructure) {
            return SymbolKind.Struct
        } else if (node is FileDescriptionSpecification) {
            return SymbolKind.Object
        } else if (node is Subroutine) {
            return SymbolKind.Function
        }
        return SymbolKind.Boolean
    }
}
