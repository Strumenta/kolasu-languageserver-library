package com.strumenta.kolasu.languageserver.manager

import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.languageserver.repository.kolasuServerSymbolProvider
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.parsing.KolasuANTLRToken
import com.strumenta.kolasu.parsing.KolasuParser
import com.strumenta.kolasu.parsing.LexingResult
import com.strumenta.kolasu.parsing.ParsingResult
import com.strumenta.kolasu.semantics.symbol.provider.SymbolProvider
import com.strumenta.kolasu.semantics.symbol.repository.SymbolRepository
import com.strumenta.kolasu.traversing.walk
import com.strumenta.kolasu.validation.Issue
import org.antlr.v4.runtime.Token

class KolasuFileManager(
    private val uri: String,
    private val parser: KolasuParser<*, *, *, out KolasuANTLRToken>,
    private val symbols: SymbolRepository,
    private val identifiers: NodeIdProvider,
    private val client: KolasuClientManager
) {

    private lateinit var parsingResult: ParsingResult<*>

    private val symbolProvider: SymbolProvider = kolasuServerSymbolProvider(uri, this.identifiers)

    fun tokens(): Sequence<Token> {
        return this.parser.tokenFactory.extractTokens(this.parsingResult)
            ?.tokens?.map { it.token }?.asSequence() ?: emptySequence()
    }

    fun root(): Node? = this.parsingResult.root

    fun synchronizeWith(text: String) {
        this.deleteSymbols()
        this.updateParsingResult(text)
    }

    fun deleteSymbols() {
        this.symbols
            .loadAll { it.fields["uri"]?.value == this.uri }
            .toList()
            .forEach { this.symbols.delete(it.identifier) }
    }

    private fun updateParsingResult(text: String) {
        this.parsingResult = this.parser.parse(text)
        val issues: MutableList<Issue> = this.parsingResult.issues.toMutableList()
        this.parsingResult.root?.walk()
            ?.mapNotNull { this.symbolProvider.from(it, issues) }
            ?.forEach { this.symbols.store(it) }
        this.client.updateFileIssues(this.uri, issues)
        // if issues.isEmpty -> generateCode
    }

}
