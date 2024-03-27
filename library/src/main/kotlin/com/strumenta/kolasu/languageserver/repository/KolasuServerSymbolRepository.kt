package com.strumenta.kolasu.languageserver.repository

import com.strumenta.kolasu.languageserver.utils.sortedByNodePosition
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.model.pos
import com.strumenta.kolasu.semantics.symbol.description.SymbolDescription
import com.strumenta.kolasu.semantics.symbol.repository.InMemorySymbolRepository
import com.strumenta.kolasu.semantics.symbol.repository.SymbolRepository
import kotlin.reflect.KClass

class KolasuServerSymbolRepository: SymbolRepository by InMemorySymbolRepository() {

    fun delete(symbol: SymbolDescription): Boolean {
        return this.delete(symbol.identifier)
    }

    fun getByPosition(position: Position): SymbolDescription {
        val symbolDescription = this.findByPosition(position)
        check (symbolDescription != null) {
            "Error while retrieving symbol by position - not found"
        }
        return symbolDescription
    }

    fun findByPosition(position: Position): SymbolDescription? {
        return this.searchByPosition(position).firstOrNull()
    }

    fun searchByPosition(position: Position): Sequence<SymbolDescription> {
        return this.loadAll { it.contains(position) }.sortedByNodePosition()
    }

}
