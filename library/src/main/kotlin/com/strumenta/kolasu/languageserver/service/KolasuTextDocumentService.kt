package com.strumenta.kolasu.languageserver.service

import com.strumenta.kolasu.languageserver.manager.KolasuCompletionManager
import com.strumenta.kolasu.languageserver.manager.KolasuWorkspaceManager
import com.strumenta.kolasu.languageserver.utils.findReferenceFieldAtPosition
import com.strumenta.kolasu.languageserver.utils.findReferencesFieldsWithTarget
import com.strumenta.kolasu.languageserver.utils.getUri
import com.strumenta.kolasu.languageserver.utils.kolasuPositionToLsp4jRange
import com.strumenta.kolasu.languageserver.utils.lsp4jPositionToKolasuPosition
import com.strumenta.kolasu.semantics.scope.provider.ScopeProvider
import org.antlr.v4.runtime.Parser
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

class KolasuTextDocumentService(
    private val workspaceManager: KolasuWorkspaceManager,
    private val scopeProvider: ScopeProvider,
    private val antlrParserFactory: (String) -> Parser,
    private val ignoredTokenIds: Set<Int>,
    private val referenceRuleIds: Set<Int>,
) : TextDocumentService {

    private val completionManager: KolasuCompletionManager = KolasuCompletionManager(
        this.workspaceManager,
        this.scopeProvider,
        this.antlrParserFactory,
        this.ignoredTokenIds,
        this.referenceRuleIds
    )

    override fun didOpen(parameters: DidOpenTextDocumentParams?) {
        val uri = parameters?.textDocument?.uri
        val text = parameters?.textDocument?.text
        if (uri != null && text != null) {
            this.workspaceManager.set(uri, text)
        }
    }

    override fun didChange(parameters: DidChangeTextDocumentParams?) {
        val uri = parameters?.textDocument?.uri
        val text = parameters?.contentChanges?.firstOrNull { it.range == null }?.text
        if (uri != null && text != null) {
            this.workspaceManager.set(uri, text)
        }
    }

    override fun didSave(parameters: DidSaveTextDocumentParams?) {
        val uri = parameters?.textDocument?.uri
        val text = parameters?.text
        if (uri != null && text != null) {
            this.workspaceManager.set(uri, text)
        }
    }

    override fun didClose(parameters: DidCloseTextDocumentParams?) {
        parameters?.textDocument?.uri?.let(this.workspaceManager::close)
    }

    override fun definition(parameters: DefinitionParams?): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        val definitions: MutableList<Location> = mutableListOf()
        parameters?.position?.let { lsp4jPositionToKolasuPosition(it) }?.let { position ->
            this.workspaceManager.symbolRepository.findByPosition(position)
                ?.findReferenceFieldAtPosition(position)?.value
                ?.let { this.workspaceManager.symbolRepository.load(it) }
                ?.let { Location(it.getUri(), kolasuPositionToLsp4jRange(it.position!!))}
                ?.let(definitions::add)
        }
        return CompletableFuture.completedFuture(Either.forLeft(definitions))
    }

    override fun references(parameters: ReferenceParams?): CompletableFuture<MutableList<out Location>> {
        val references: MutableList<Location> = mutableListOf()
        parameters?.position?.let { lsp4jPositionToKolasuPosition(it) }?.let { position ->
            this.workspaceManager.symbolRepository.findByPosition(position)?.let { target ->
                this.workspaceManager.symbolRepository.loadAll().flatMap { source ->
                    source.findReferencesFieldsWithTarget(target.identifier)
                        .map { Location(source.getUri(), kolasuPositionToLsp4jRange(it.position!!)) }
                }.forEach(references::add)
            }
        }
        return CompletableFuture.completedFuture(references)
    }

    override fun completion(parameters: CompletionParams?): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        val completions: MutableList<CompletionItem> = mutableListOf()
        val uri = parameters?.textDocument?.uri
        val kolasuPosition = parameters?.position?.let { lsp4jPositionToKolasuPosition(it) }
        if (uri != null && kolasuPosition != null) {
            this.completionManager.completionFor(uri, kolasuPosition)
                .forEach(completions::add)
        }
        return CompletableFuture.completedFuture(Either.forLeft(completions))
    }
}
