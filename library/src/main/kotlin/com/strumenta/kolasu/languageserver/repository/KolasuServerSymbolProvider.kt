package com.strumenta.kolasu.languageserver.repository

import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.languageserver.utils.kolasuPositionToLsp4jRange
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.PossiblyNamed
import com.strumenta.kolasu.model.kReferenceByNameProperties
import com.strumenta.kolasu.semantics.symbol.provider.SymbolProvider
import com.strumenta.kolasu.semantics.symbol.provider.symbolProvider

fun kolasuServerSymbolProvider(uri: String, nodeIdProvider: NodeIdProvider) = symbolProvider(nodeIdProvider) {
    rule(Node::class) { (node) ->
        include("name", (node as? PossiblyNamed)?.name ?: nodeIdProvider.id(node))
        include("uri", uri)
        node.kReferenceByNameProperties().forEach { referenceByNameProperty ->
            include(referenceByNameProperty.name, referenceByNameProperty.get(node))
        }
    }
}
