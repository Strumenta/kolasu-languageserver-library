package com.strumenta.kolasu.languageserver.library

import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.PossiblyNamed
import com.strumenta.kolasu.model.children
import com.strumenta.kolasu.parsing.ASTParser
import com.strumenta.kolasu.parsing.ParsingResult
import com.strumenta.kolasu.traversing.findByPosition
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture

open class KolasuServer<R : Node>(private val parser: ASTParser<R>, private val includeErrorNodeIssues: Boolean = false) : LanguageServer, TextDocumentService, WorkspaceService, LanguageClientAware {

    protected lateinit var client: LanguageClient
    protected val uriToParsingResult: MutableMap<String, ParsingResult<R>> = mutableMapOf()

    fun startCommunication(inputStream: InputStream = System.`in`, outputStream: OutputStream = System.out) {
        val launcher = LSPLauncher.createServerLauncher(this, inputStream, outputStream)
        connect(launcher.remoteProxy)
        launcher.startListening()
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun getTextDocumentService(): TextDocumentService {
        return this
    }

    override fun getWorkspaceService(): WorkspaceService {
        return this
    }

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities()
        capabilities.setTextDocumentSync(TextDocumentSyncOptions().apply { openClose = true; change = TextDocumentSyncKind.Full })
        capabilities.setDocumentSymbolProvider(true)
        capabilities.setDefinitionProvider(true)
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        params?.apply {
            parseAndPublishDiagnostics(this.textDocument.text, this.textDocument.uri)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        params?.apply {
            assert(this.contentChanges.size == 1)
            parseAndPublishDiagnostics(this.contentChanges.first().text, params.textDocument.uri)
        }
    }

    private fun parseAndPublishDiagnostics(text: String, uri: String) {
        val parsingResult = parser.parse(text)
        uriToParsingResult[uri] = parsingResult

        val diagnostics = ArrayList<Diagnostic>()
        parsingResult.issues.filter { issue ->
            includeErrorNodeIssues || !issue.message.startsWith("Error node found")
        }.forEach { issue ->
            issue.position?.let {
                diagnostics.add(Diagnostic(toLSPRange(it), issue.message))
            }
        }
        client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
    }

    override fun documentSymbol(params: DocumentSymbolParams?): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> {
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        val parsingResult = uriToParsingResult[uri] ?: return CompletableFuture.completedFuture(null)
        val root = parsingResult.root ?: return CompletableFuture.completedFuture(null)
        val rootPosition = root.position ?: return CompletableFuture.completedFuture(null)

        val namedTree = DocumentSymbol("Named tree", SymbolKind.Variable, toLSPRange(rootPosition), toLSPRange(rootPosition), "", mutableListOf())
        appendNamedChildren(root, namedTree)

        return CompletableFuture.completedFuture(mutableListOf(Either.forRight(namedTree)))
    }

    private fun appendNamedChildren(node: Node, parent: DocumentSymbol) {
        var nextParent = parent
        if (node is PossiblyNamed && node.name != null) {
            node.position?.let {
                val range = toLSPRange(it)
                val symbol = DocumentSymbol(node.name, symbolKindOf(node), range, range, "", mutableListOf())
                parent.children.add(symbol)
                nextParent = symbol
            }
        }
        node.children.forEach { child ->
            appendNamedChildren(child, nextParent)
        }
    }

    open fun symbolKindOf(node: Node): SymbolKind {
        return SymbolKind.Variable
    }

    override fun definition(params: DefinitionParams?): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        if (params == null) return CompletableFuture.completedFuture(null)
        val uri = params.textDocument.uri ?: return CompletableFuture.completedFuture(null)
        val parsingResult = uriToParsingResult[uri] ?: return CompletableFuture.completedFuture(null)
        val root = parsingResult.root ?: return CompletableFuture.completedFuture(null)

        val position = toKolasuRange(params.position)
        val node = root.findByPosition(position) ?: return CompletableFuture.completedFuture(null)

        for (field in node.javaClass.declaredFields) {
            client.showMessage(MessageParams(MessageType.Info, field.toString()))
        }
        return CompletableFuture.completedFuture(null)
    }

    private fun toLSPRange(kolasuRange: com.strumenta.kolasu.model.Position): Range {
        val start = Position(kolasuRange.start.line - 1, kolasuRange.start.column)
        val end = Position(kolasuRange.end.line - 1, kolasuRange.end.column)
        return Range(start, end)
    }

    private fun toKolasuRange(position: Position): com.strumenta.kolasu.model.Position {
        val start = Point(position.line + 1, position.character)
        val end = Point(position.line + 1, position.character)
        return com.strumenta.kolasu.model.Position(start, end)
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
    }

    override fun setTrace(params: SetTraceParams?) {
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
    }
}
