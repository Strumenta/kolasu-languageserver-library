package com.strumenta.kolasu.languageserver.manager

import com.strumenta.kolasu.languageserver.utils.kolasuPositionToLsp4jRange
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.pos
import com.strumenta.kolasu.parsing.position
import com.strumenta.kolasu.semantics.scope.provider.ScopeProvider
import com.strumenta.kolasu.traversing.findByPosition
import com.vmware.antlr4c3.CodeCompletionCore
import com.vmware.antlr4c3.CodeCompletionCore.CandidatesCollection
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.Token
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind.Variable
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import com.strumenta.kolasu.model.Position as KolasuPosition
import org.eclipse.lsp4j.Position as Lsp4jPosition
import org.eclipse.lsp4j.Range as Lsp4jRange

class KolasuCompletionManager(
    private val workspaceManager: KolasuWorkspaceManager,
    private val scopeProvider: ScopeProvider,
    private val antlrParserFactory: (String) -> Parser,
    private val ignoredTokenIds: Set<Int>,
    private val referenceRuleIds: Set<Int>,
) {

    fun completionFor(uri: String, kolasuPosition: KolasuPosition): List<CompletionItem> {
        val completions: MutableList<CompletionItem> = mutableListOf()
        this.workspaceManager.get(uri).let { file ->
            this.createAntlrParser(file)?.let { antlrParser ->
                val engine = createCompletionEngine(antlrParser)
                this.findCurrentToken(file, kolasuPosition)
                    ?.let { currentToken -> this.getCompletionsFrom(currentToken, engine, file, antlrParser) }
                    ?.forEach(completions::add)
//                this.findPreviousToken(file, kolasuPosition)
//                    ?.let { previousToken -> this.getCompletionsFrom(previousToken, engine, file, antlrParser) }
//                    ?.forEach(completions::add)
            }
        }
        return completions
    }

    private fun findCurrentToken(file: KolasuFileManager, kolasuPosition: KolasuPosition): Token? {
        return file.tokens().find { it.position.contains(kolasuPosition) }
    }

    private fun findPreviousToken(file: KolasuFileManager, kolasuPosition: KolasuPosition): Token? {
        return file.tokens().findLast { it.position < kolasuPosition }
    }

    private fun createCompletionEngine(antlrParser: Parser): CodeCompletionCore {
        return CodeCompletionCore(antlrParser, this.referenceRuleIds, this.ignoredTokenIds)
    }

    private fun createAntlrParser(file: KolasuFileManager): Parser? {
        return file.root()?.sourceText?.let { this.antlrParserFactory(it) }
    }

    private fun getCompletionsFrom(
        token: Token,
        engine: CodeCompletionCore,
        file: KolasuFileManager,
        antlrParser: Parser,
    ): List<CompletionItem> {
        val completions: MutableList<CompletionItem> = mutableListOf()
        engine.collectCandidates(token.tokenIndex, null)?.let { candidates ->
            if (candidates.containsReferenceRules()) {
                file.findNode(token)
                    ?.let { this.scopeProvider.from(it) }?.names()
                    ?.mapNotNull { this.buildCompletionItem(token, it) }
                    ?.forEach(completions::add)
            }
//            candidates.tokenLiterals(antlrParser)
//                .mapNotNull { buildCompletionItem(token, it) }
//                .forEach(completions::add)
        }
        return completions
    }

    private fun CandidatesCollection.containsReferenceRules(): Boolean {
        return this.rules.keys.any { referenceRuleIds.contains(it) }
    }

    private fun KolasuFileManager.findNode(token: Token): Node? {
        return this.root()?.findByPosition(token.position)
    }

    private fun CandidatesCollection.tokenLiterals(antlrParser: Parser): List<String> {
        return this.tokens.keys.mapNotNull { antlrParser.vocabulary.getLiteralName(it) }
    }

    private fun buildCompletionItem(token: Token, text: String): CompletionItem? {
        return kolasuPositionToLsp4jRange(token.position).let { tokenRange ->
            CompletionItem(text).apply {
                kind = Variable
                textEdit = Either.forLeft(textEditFrom(tokenRange, text))
            }
        }
    }

    private fun textEditFrom(tokenRange: Lsp4jRange, text: String): TextEdit {
        return TextEdit(this.textEditRangeFrom(tokenRange, text), text)
    }

    private fun textEditRangeFrom(tokenRange: Lsp4jRange, text: String): Lsp4jRange {
        return Lsp4jRange(tokenRange.start, this.textEditRangeEndFrom(tokenRange, text))
    }

    private fun textEditRangeEndFrom(tokenRange: Lsp4jRange, text: String): Lsp4jPosition {
        return Lsp4jPosition(tokenRange.end.line, tokenRange.start.character + text.length)
    }

}
