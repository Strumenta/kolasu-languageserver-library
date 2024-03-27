package com.strumenta.kolasu.languageserver

import com.google.gson.JsonObject
import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.languageserver.manager.KolasuClientManager
import com.strumenta.kolasu.languageserver.manager.KolasuWorkspaceManager
import com.strumenta.kolasu.languageserver.service.KolasuTextDocumentService
import com.strumenta.kolasu.languageserver.service.KolasuWorkspaceService
import com.strumenta.kolasu.parsing.KolasuANTLRToken
import com.strumenta.kolasu.parsing.KolasuParser
import com.strumenta.kolasu.semantics.scope.provider.ScopeProvider
import org.antlr.v4.runtime.Parser
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentSyncKind.Full
import org.eclipse.lsp4j.TraceValue
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

class KolasuServer(
    val language: String,
    val fileExtensions: List<String>,
    val kolasuParser: KolasuParser<*, *, *, out KolasuANTLRToken>,
    val nodeIdProvider: NodeIdProvider,
    val scopeProvider: ScopeProvider,
    val antlrParserFactory: (String) -> Parser,
    val ignoredTokenIds: Set<Int>,
    val referenceRuleIds: Set<Int>
) : LanguageServer, LanguageClientAware {

    private var configuration: JsonObject = JsonObject()
    private var traceLevel: String = TraceValue.Off

    private val clientManager = KolasuClientManager(
        this.language,
        this.fileExtensions
    )

    private val workspaceManager = KolasuWorkspaceManager(
        this.kolasuParser,
        this.nodeIdProvider,
        this.clientManager
    )

    private val kolasuTextDocumentService = KolasuTextDocumentService(
        this.workspaceManager,
        this.scopeProvider,
        this.antlrParserFactory,
        this.ignoredTokenIds,
        this.referenceRuleIds
    )

    private val kolasuWorkspaceService = KolasuWorkspaceService(
        this.workspaceManager
    )

    override fun initialize(parameters: InitializeParams?): CompletableFuture<InitializeResult> {
        val response = InitializeResult(ServerCapabilities())
        // load workspace manager
        parameters?.workspaceFolders?.let(this.workspaceManager::load)
        // store client capabilities
        this.clientManager.capabilities = parameters?.capabilities
        // receive full text on synchronization
        response.capabilities.setTextDocumentSync(Full)
        // handle smart features (definitions, references, etc.)
        // configure definition support
        clientManager.takeUnless { it.supportsDynamicDefinitionCapabilityRegistration() }
            .let { response.capabilities.setDefinitionProvider(true) }
        // configure references support
        clientManager.takeUnless { it.supportsDynamicReferencesCapabilityRegistration() }
            .let { response.capabilities.setReferencesProvider(true) }
        // configure completion support
        clientManager.takeUnless { it.supportsDynamicCompletionCapabilityRegistration() }
            .let { response.capabilities.completionProvider = CompletionOptions() }
        return CompletableFuture.supplyAsync { response }
    }

    override fun connect(languageClient: LanguageClient?) {
        this.clientManager.languageClient = languageClient
    }

    override fun initialized(parameters: InitializedParams?) {
        // configure definition support
        this.clientManager.registerDefinitionCapability()
        // configure references support
        this.clientManager.registerReferencesCapability()
        // configure completion support
        this.clientManager.registerCompletionCapability()
        // watch file changes
        this.clientManager.watchFileChanges()
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        exitProcess(0)
    }

    // TODO logging as utility extensions

    fun startCommunication(inputStream: InputStream = System.`in`, outputStream: OutputStream = System.out) {
        val launcher = LSPLauncher.createServerLauncher(this, inputStream, outputStream)
        connect(launcher.remoteProxy)
        launcher.startListening()
    }

    override fun getTextDocumentService() = this.kolasuTextDocumentService

    override fun getWorkspaceService() = this.kolasuWorkspaceService

    override fun setTrace(parameters: SetTraceParams?) {
        this.traceLevel = when (val traceLevel = parameters?.value) {
            TraceValue.Verbose, TraceValue.Messages -> traceLevel
            else -> TraceValue.Off
        }
    }

    protected open fun log(
        message: String,
        verbose: String? = null
    ) = this.clientManager.languageClient?.logTrace(LogTraceParams(message, verbose))

    protected open fun showNotification(
        type: MessageType = MessageType.Info,
        message: String,
    ) = this.clientManager.languageClient?.showMessage(MessageParams(type, message))

    protected open fun askClient(
        message: String,
        type: MessageType = MessageType.Info,
        actions: List<String> = listOf("Yes", "No"),
    ) = CompletableFuture<String>().apply {
        clientManager.languageClient?.showMessageRequest(
            ShowMessageRequestParams(actions.map { MessageActionItem(it) })
                .apply { this.type = type; this.message = message }
        )?.thenApply { this.complete(it.title) }
    }

}
