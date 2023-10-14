package com.strumenta.kolasu.languageserver.library

import com.google.gson.JsonObject
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
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFoldersOptions
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.typeOf
import kotlin.system.exitProcess

open class KolasuServer<R : Node>(private val parser: ASTParser<R>, private val language: String = "kolasuServer") : LanguageServer, TextDocumentService, WorkspaceService, LanguageClientAware {

    protected lateinit var client: LanguageClient
    protected var configuration: JsonObject = JsonObject()
    protected var traceLevel: String = "off"
    protected val folders: MutableList<String> = mutableListOf()
    protected val files: MutableMap<String, ProjectFile<R>> = mutableMapOf()

    override fun getTextDocumentService() = this
    override fun getWorkspaceService() = this

    fun startCommunication(inputStream: InputStream = System.`in`, outputStream: OutputStream = System.out) {
        val launcher = LSPLauncher.createServerLauncher(this, inputStream, outputStream)
        connect(launcher.remoteProxy)
        launcher.startListening()
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        val workspaceFolders = params?.workspaceFolders ?: return CompletableFuture.completedFuture(null)
        for (folder in workspaceFolders) {
            folders.add(folder.uri)
        }
        val capabilities = ServerCapabilities()
        capabilities.workspace = WorkspaceServerCapabilities(WorkspaceFoldersOptions().apply { supported = true; changeNotifications = Either.forLeft("didChangeWorkspaceFoldersRegistration"); })
        capabilities.setTextDocumentSync(TextDocumentSyncOptions().apply { openClose = true; change = TextDocumentSyncKind.Full })
        capabilities.setDocumentSymbolProvider(true)
        capabilities.setDefinitionProvider(true)
        capabilities.setReferencesProvider(true)
        capabilities.setRenameProvider(true)
        capabilities.setHoverProvider(true)
        capabilities.setWorkspaceSymbolProvider(true)
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun initialized(params: InitializedParams?) {
        val registrationParams = DidChangeConfigurationRegistrationOptions(language)
        client.registerCapability(RegistrationParams(listOf(Registration("myID", "workspace/didChangeConfiguration", registrationParams))))
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        val settings = params?.settings as? JsonObject ?: return
        configuration = settings[language].asJsonObject
    }

    override fun setTrace(params: SetTraceParams?) {
        val level = params?.value ?: return
        traceLevel = level
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = params.textDocument.text

        parseAndPublishDiagnostics(text, uri)
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = params.contentChanges.first()?.text ?: return

        parseAndPublishDiagnostics(text, uri)
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = File(URI(uri)).readText()

        parseAndPublishDiagnostics(text, uri)
    }

    private fun parseAndPublishDiagnostics(text: String, uri: String) {
        val parsingResult = parser.parse(text)
        val symbols = resolveSymbols(parsingResult.root)
        files[uri] = ProjectFile(parsingResult, symbols)

        val tree = parsingResult.root ?: return

        val showASTWarnings = configuration["showASTWarnings"]?.asBoolean ?: false
        val showLeafPositions = configuration["showLeafPositions"]?.asBoolean ?: false
        val showParsingErrors = configuration["showParsingErrors"]?.asBoolean ?: true
        val includeErrorNodeFoundIssues = configuration["includeErrorNodeFoundIssues"]?.asBoolean ?: false

        val diagnostics = ArrayList<Diagnostic>()

        if (showParsingErrors) {
            for (issue in parsingResult.issues) {
                if (!includeErrorNodeFoundIssues && issue.message.startsWith("Error node found") || issue.position == null) continue

                diagnostics.add(Diagnostic(toLSPRange(issue.position!!), issue.message))
            }
        }
        if (showASTWarnings || showLeafPositions) {
            for (node in tree.walk()) {
                if (node.children.isNotEmpty() || node.position == null) continue

                if (showASTWarnings && tree.findByPosition(node.position!!) != node) {
                    diagnostics.add(Diagnostic(toLSPRange(node.position!!), "Leaf type: ${node.simpleNodeType} but findByPositionType: ${tree.findByPosition(node.position!!)?.simpleNodeType}").apply { severity = DiagnosticSeverity.Warning })
                }

                if (showLeafPositions) {
                    diagnostics.add(Diagnostic(toLSPRange(node.position!!), "Leaf position: ${node.position}, Source text: ${node.sourceText}").apply { severity = DiagnosticSeverity.Information })
                }
            }
        }
        client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
    }

    private fun resolveSymbols(root: Node?): MutableMap<String, Symbol> {
        val tree = root ?: return mutableMapOf()
        val symbols = mutableMapOf<String, Symbol>()
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
        return symbols
    }

    override fun documentSymbol(params: DocumentSymbolParams?): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> {
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        val projectFile = files[uri] ?: return CompletableFuture.completedFuture(null)
        val root = projectFile.parsingResult.root ?: return CompletableFuture.completedFuture(null)
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
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        val projectFile = files[uri] ?: return CompletableFuture.completedFuture(null)

        val field = node::class.declaredMemberProperties.find { it.returnType.isSubtypeOf(typeOf<ReferenceByName<*>>()) }?.javaField ?: return CompletableFuture.completedFuture(null)
        val value = field.get(node) as ReferenceByName<*>
        val symbol = projectFile.symbols[value.name] ?: return CompletableFuture.completedFuture(null)
        val definition = symbol.definition as Node
        val definitionPosition = definition.position ?: return CompletableFuture.completedFuture(null)

        return CompletableFuture.completedFuture(Either.forLeft(mutableListOf(toLSPLocation(definitionPosition, uri))))
    }

    override fun references(params: ReferenceParams?): CompletableFuture<MutableList<out Location>> {
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        val projectFile = files[uri] ?: return CompletableFuture.completedFuture(null)

        val symbol = projectFile.symbols[node.sourceText] ?: return CompletableFuture.completedFuture(null)
        val locations = symbol.references.map { toLSPLocation(it.position!!, uri) }.toMutableList()

        return CompletableFuture.completedFuture(locations)
    }

    override fun rename(params: RenameParams?): CompletableFuture<WorkspaceEdit> {
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        val projectFile = files[uri] ?: return CompletableFuture.completedFuture(null)

        val future = CompletableFuture<WorkspaceEdit>()
        val confirmation = ShowMessageRequestParams(listOf(MessageActionItem("Yes"), MessageActionItem("No"))).apply {
            this.type = MessageType.Info
            this.message = "Are you sure?"
        }
        client.showMessageRequest(confirmation).thenApply { item ->
            if (item.title == "No") {
                future.complete(null)
            }
            val symbol = projectFile.symbols[node.sourceText]
            if (symbol == null) {
                future.complete(null)
            }
            val edits = symbol!!.references.map { rename(it, params.newName) }.toMutableList()
            edits.reverse()
            val textEdits = TextDocumentEdit(VersionedTextDocumentIdentifier(uri, 0), edits)
            future.complete(WorkspaceEdit(listOf(Either.forLeft(textEdits))))
        }

        return future
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
        val ast = files[params.textDocument.uri] ?: return null
        val root = ast.parsingResult.root ?: return null
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

    override fun didSave(params: DidSaveTextDocumentParams?) {
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams?) {
        val event = params?.event ?: return
        for (folder in event.added) {
            folders.add(folder.uri)
        }
        for (folder in event.removed) {
            folders.removeIf { it == folder.uri }
        }
    }

    override fun symbol(params: WorkspaceSymbolParams?): CompletableFuture<Either<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>> {
        val symbols = mutableListOf<WorkspaceSymbol>()
        for (file in files) {
            for (symbol in file.value.symbols) {
                val definition = symbol.value.definition as Node
                symbols.add(WorkspaceSymbol(symbol.key, symbolKindOf(definition), Either.forLeft(Location(file.key, toLSPRange(definition.position!!)))))
            }
        }
        return CompletableFuture.completedFuture(Either.forRight(symbols))
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        client.showMessage(MessageParams(MessageType.Info, "Change watched files"))
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        exitProcess(0)
    }

    data class ProjectFile<R : Node>(val parsingResult: ParsingResult<R>, val symbols: MutableMap<String, Symbol>)
    data class Symbol(val definition: PossiblyNamed, val references: MutableList<Node>)
    data class DidChangeConfigurationRegistrationOptions(val section: String)
}
