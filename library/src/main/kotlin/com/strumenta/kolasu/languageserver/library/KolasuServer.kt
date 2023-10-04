package com.strumenta.kolasu.languageserver.library

import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.PossiblyNamed
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.model.children
import com.strumenta.kolasu.parsing.ASTParser
import com.strumenta.kolasu.parsing.ParsingResult
import com.strumenta.kolasu.traversing.findByPosition
import com.strumenta.kolasu.traversing.walk
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
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
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.typeOf

open class KolasuServer<R : Node>(private val parser: ASTParser<R>, private val includeErrorNodeIssues: Boolean = false) : LanguageServer, TextDocumentService, WorkspaceService, LanguageClientAware {

    protected lateinit var client: LanguageClient
    protected val uriToParsingResult: MutableMap<String, ParsingResult<R>> = mutableMapOf()
    public val symbols: MutableList<Symbol> = mutableListOf()

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
        parsingResult.root?.let {
            resolveSymbols(it)
        }
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

    private fun resolveSymbols(tree: Node) {
        symbols.clear()
        tree.walk().forEach { node ->
            if (node is PossiblyNamed && node.name != null) {
                symbols.add(Symbol(node, mutableListOf(node)))
            }
        }
        tree.walk().forEach { node ->
            val referenceField = node::class.declaredMemberProperties.find { it.returnType.isSubtypeOf(typeOf<ReferenceByName<*>>()) }
            referenceField?.javaField?.let { field ->
                field.isAccessible = true
                val value = field.get(node) as ReferenceByName<*>
                val name = value.name
                val symbol = symbols.find { symbol -> symbol.definition.name == name }
                symbol?.references?.add(node)
                println(value.name)
            }
        }
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
        val referenceField = node::class.declaredMemberProperties.find { it.returnType.isSubtypeOf(typeOf<ReferenceByName<*>>()) }
        referenceField?.javaField?.let { field ->
            val value = field.get(node) as ReferenceByName<*>
            val symbol = symbols.find { it.definition.name == value.name }
            val definition = symbol?.definition as Node
            definition.position?.let {
                return CompletableFuture.completedFuture(Either.forLeft(mutableListOf(Location(uri, toLSPRange(it)))))
            }
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

data class Symbol(val definition: PossiblyNamed, val references: MutableList<Node>)
