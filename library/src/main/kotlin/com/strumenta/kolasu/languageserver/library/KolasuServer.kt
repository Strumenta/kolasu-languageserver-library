package com.strumenta.kolasu.languageserver.library

import com.strumenta.kolasu.model.FileSource
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
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
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
    protected val symbols: MutableMap<String, Symbol> = mutableMapOf()

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
        capabilities.setReferencesProvider(true)
        capabilities.setRenameProvider(true)
        capabilities.setHoverProvider(true)
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
            if (node is PossiblyNamed) {
                node.name?.let {
                    symbols.put(it, Symbol(node, mutableListOf(node)))
                }
            }
        }
        tree.walk().forEach { node ->
            val referenceField = node::class.declaredMemberProperties.find { it.returnType.isSubtypeOf(typeOf<ReferenceByName<*>>()) }
            referenceField?.javaField?.let { field ->
                field.isAccessible = true
                val value = field.get(node) as ReferenceByName<*>
                val name = value.name
                val symbol = symbols[name]
                symbol?.references?.add(node)
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
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)

        val field = node::class.declaredMemberProperties.find { it.returnType.isSubtypeOf(typeOf<ReferenceByName<*>>()) }?.javaField ?: return CompletableFuture.completedFuture(null)
        val value = field.get(node) as ReferenceByName<*>
        val symbol = symbols[value.name] ?: return CompletableFuture.completedFuture(null)
        val definition = symbol.definition as Node
        val definitionPosition = definition.position ?: return CompletableFuture.completedFuture(null)

        return CompletableFuture.completedFuture(Either.forLeft(mutableListOf(toLSPLocation(definitionPosition, params!!.textDocument.uri))))
    }

    override fun references(params: ReferenceParams?): CompletableFuture<MutableList<out Location>> {
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)

        val symbol = symbols[node.sourceText] ?: return CompletableFuture.completedFuture(null)
        val locations = symbol.references.map { toLSPLocation(it.position!!, params!!.textDocument.uri) }.toMutableList()

        return CompletableFuture.completedFuture(locations)
    }

    override fun rename(params: RenameParams?): CompletableFuture<WorkspaceEdit> {
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)

        val symbol = symbols[node.sourceText] ?: return CompletableFuture.completedFuture(null)
        val edits = symbol.references.map { rename(it, params!!.newName) }.toMutableList()
        edits.reverse()

        val textEdits = TextDocumentEdit(VersionedTextDocumentIdentifier(params!!.textDocument.uri, 0), edits)
        return CompletableFuture.completedFuture(WorkspaceEdit(listOf(Either.forLeft(textEdits))))
    }

    protected open fun rename(node: Node, newName: String): TextEdit {
        return TextEdit(toLSPRange(node.position!!), newName)
    }

    override fun hover(params: HoverParams?): CompletableFuture<Hover> {
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)
        val information = informationFor(node)

        return CompletableFuture.completedFuture(Hover(MarkupContent("markdown", information)))
    }

    protected open fun informationFor(node: Node): String {
        return node.simpleNodeType
    }

    private fun getNode(params: TextDocumentPositionParams?): Node? {
        if (params == null) return null
        val ast = uriToParsingResult[params.textDocument.uri] ?: return null
        val root = ast.root ?: return null
        return root.findByPosition(toKolasuRange(params.position))
    }

    protected fun toLSPRange(kolasuRange: com.strumenta.kolasu.model.Position): Range {
        val start = Position(kolasuRange.start.line - 1, kolasuRange.start.column)
        val end = Position(kolasuRange.end.line - 1, kolasuRange.end.column)
        return Range(start, end)
    }

    protected fun toLSPLocation(position: com.strumenta.kolasu.model.Position, uri: String): Location {
        val range = toLSPRange(position)
        val source = position.source
        if (source is FileSource) {
            return Location(source.file.toURI().toString(), range)
        } else {
            return Location(uri, range)
        }
    }

    protected fun toKolasuRange(position: Position): com.strumenta.kolasu.model.Position {
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
