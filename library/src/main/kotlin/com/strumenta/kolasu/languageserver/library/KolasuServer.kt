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
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.IntField
import org.apache.lucene.document.IntPoint
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileSystemWatcher
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressReport
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
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.typeOf
import kotlin.system.exitProcess

open class KolasuServer<T : Node>(protected val parser: ASTParser<T>, protected val language: String = "", protected val extensions: List<String> = listOf(), protected val symbolResolver: SymbolResolver? = null, protected val generator: CodeGenerator<T>? = null) : LanguageServer, TextDocumentService, WorkspaceService, LanguageClientAware {

    protected lateinit var client: LanguageClient
    protected var configuration: JsonObject = JsonObject()
    protected var traceLevel: String = "off"
    protected val folders: MutableList<String> = mutableListOf()
    protected val files: MutableMap<String, ParsingResult<T>> = mutableMapOf()
    protected val indexPath: Path = Paths.get("indexes")

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
        data class DidChangeConfigurationRegistrationOptions(val section: String)
        val registrationParams = DidChangeConfigurationRegistrationOptions(language)
        client.registerCapability(RegistrationParams(listOf(Registration("workspace/didChangeConfiguration", "workspace/didChangeConfiguration", registrationParams))))

        val watchers = mutableListOf<FileSystemWatcher>()
        for (folder in folders) {
            watchers.add(FileSystemWatcher(Either.forLeft(URI(folder).path + """/**/*{${extensions.joinToString(","){".$it"}}}""")))
        }
        client.registerCapability(RegistrationParams(listOf(Registration("workspace/didChangeWatchedFiles", "workspace/didChangeWatchedFiles", DidChangeWatchedFilesRegistrationOptions(watchers)))))
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        val settings = params?.settings as? JsonObject ?: return
        configuration = settings[language].asJsonObject

        client.createProgress(WorkDoneProgressCreateParams(Either.forLeft("parsingFolders")))
        client.notifyProgress(ProgressParams(Either.forLeft("parsingFolders"), Either.forLeft(WorkDoneProgressBegin().apply { title = "indexing"; message = "0 out of 5"; })))
        client.notifyProgress(ProgressParams(Either.forLeft("parsingFolders"), Either.forLeft(WorkDoneProgressReport().apply { percentage = 0 })))
        for (folder in folders) {
            if (Files.exists(indexPath)) {
                indexPath.toFile().deleteRecursively()
            }
            val indexDirectory = FSDirectory.open(indexPath)
            val indexConfiguration = IndexWriterConfig(StandardAnalyzer())
            indexConfiguration.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
            val indexWriter = IndexWriter(indexDirectory, indexConfiguration)

            val projectFiles = mutableListOf<Path>()
            projectFilesIn(Paths.get(URI(folder)), projectFiles)

            val totalBytes = projectFiles.sumOf { it.fileSize() }
            var parsedBytes = 0L
            for (file in projectFiles) {
                val uri = file.toUri().toString()
                val text = Files.readString(file)

                parseAndPublishDiagnostics(text, uri, indexWriter)
                parsedBytes += file.fileSize()
                val percentage = (parsedBytes * 100 / totalBytes).toInt()
                client.notifyProgress(ProgressParams(Either.forLeft("parsingFolders"), Either.forLeft(WorkDoneProgressReport().apply { this.percentage = percentage })))
            }
            indexWriter.close()
        }
        client.notifyProgress(ProgressParams(Either.forLeft("parsingFolders"), Either.forLeft(WorkDoneProgressEnd())))
    }

    protected open fun projectFilesIn(directory: Path, result: MutableList<Path>) {
        for (file in Files.list(directory)) {
            if (file.isDirectory()) {
                projectFilesIn(file, result)
            } else if (extensions.contains(file.extension)) {
                result.add(file)
            }
        }
    }

    override fun setTrace(params: SetTraceParams?) {
        val level = params?.value ?: return
        traceLevel = level
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = params.textDocument.text

        // parseAndPublishDiagnostics(text, uri)
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = params.contentChanges.first()?.text ?: return

        // parseAndPublishDiagnostics(text, uri)
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = Files.readString(Paths.get(URI(uri)))

        // parseAndPublishDiagnostics(text, uri)
    }

    protected open fun parseAndPublishDiagnostics(text: String, uri: String, indexWriter: IndexWriter) {
        val parsingResult = parser.parse(text)
        symbolResolver?.resolveSymbols(parsingResult.root)
        files[uri] = parsingResult

        val tree = parsingResult.root ?: return

        for (node in tree.walk()) {
            val document = Document()
            document.add(StringField("uri", uri, Field.Store.YES))
            node.position?.let { position ->
                document.add(IntField("startLine", position.start.line, Field.Store.YES))
                document.add(IntField("startColumn", position.start.column, Field.Store.YES))
                document.add(IntField("endLine", position.end.line, Field.Store.YES))
                document.add(IntField("endColumn", position.end.column, Field.Store.YES))
            }

            if (node is PossiblyNamed && node.name != null) {
                document.add(StringField("name", node.name, Field.Store.YES))
            }

            val referenceField = node::class.declaredMemberProperties.find { it.returnType.isSubtypeOf(typeOf<ReferenceByName<*>>()) }
            referenceField?.javaField?.let { field ->
                field.isAccessible = true
                val value = field.get(node) as ReferenceByName<*>

                document.add(TextField("referenceTo", value.name, Field.Store.YES))
            }

            indexWriter.addDocument(document)
        }

        val showASTWarnings = configuration["showASTWarnings"]?.asBoolean ?: false
        val showLeafPositions = configuration["showLeafPositions"]?.asBoolean ?: false
        val showParsingErrors = configuration["showParsingErrors"]?.asBoolean ?: true

        val diagnostics = ArrayList<Diagnostic>()

        if (showParsingErrors) {
            for (issue in parsingResult.issues) {
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

    override fun documentSymbol(params: DocumentSymbolParams?): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> {
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        val root = files[uri]?.root ?: return CompletableFuture.completedFuture(null)
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
        field.isAccessible = true
        val value = field.get(node) as ReferenceByName<*>
        val definition = value.referred as Node
        val definitionPosition = definition.position ?: return CompletableFuture.completedFuture(null)

        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null) // TODO support multiple files

        return CompletableFuture.completedFuture(Either.forLeft(mutableListOf(toLSPLocation(definitionPosition, uri))))
    }

    override fun references(params: ReferenceParams?): CompletableFuture<MutableList<out Location>> {
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        /*val projectFile = files[uri] ?: return CompletableFuture.completedFuture(null)


        val symbol = projectFile.symbols[node.sourceText] ?: return CompletableFuture.completedFuture(null)
        val locations = symbol.references.map { toLSPLocation(it.position!!, uri) }.toMutableList()

        return CompletableFuture.completedFuture(locations)*/
        return CompletableFuture.completedFuture(mutableListOf())
    }

    override fun rename(params: RenameParams?): CompletableFuture<WorkspaceEdit> {
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        val projectFile = files[uri] ?: return CompletableFuture.completedFuture(null)

        val future = CompletableFuture<WorkspaceEdit>()
        askClient("Are you sure").thenApply { answer ->
            if (answer == "No") {
                future.complete(null)
            }
            future.complete(null)
        }
        /*val symbol = projectFile.symbols[node.sourceText]
        if (symbol == null) {
            future.complete(null)
        }
        val edits = symbol!!.references.map { rename(it, params.newName) }.toMutableList()
        edits.reverse()
        val textEdits = TextDocumentEdit(VersionedTextDocumentIdentifier(uri, 0), edits)
        future.complete(WorkspaceEdit(listOf(Either.forLeft(textEdits))))
        }*/

        return future
    }

    protected open fun rename(node: Node, newName: String): TextEdit {
        return TextEdit(toLSPRange(node.position!!), newName)
    }

    override fun hover(params: HoverParams?): CompletableFuture<Hover> {
        val node = getNode(params) ?: return CompletableFuture.completedFuture(null)
        val information = informationFor(node)

        val indexDirectory = FSDirectory.open(indexPath)
        val reader: IndexReader = DirectoryReader.open(indexDirectory)
        val searcher = IndexSearcher(reader)

        val query = BooleanQuery.Builder()
            .add(IntPoint.newRangeQuery("startLine", Int.MIN_VALUE, node.position!!.start.line), BooleanClause.Occur.MUST)
            .add(IntPoint.newRangeQuery("endLine", node.position!!.end.line, Int.MAX_VALUE), BooleanClause.Occur.MUST)
            .add(IntPoint.newRangeQuery("startColumn", Int.MIN_VALUE, node.position!!.start.column), BooleanClause.Occur.MUST)
            .add(IntPoint.newRangeQuery("endColumn", node.position!!.end.column, Int.MAX_VALUE), BooleanClause.Occur.MUST)
            .build()

        val results = searcher.search(query, 1)
        val result = results.scoreDocs.first() ?: return CompletableFuture.completedFuture(null)
        val document = searcher.storedFields().document(result.doc)

        return CompletableFuture.completedFuture(Hover(MarkupContent("markdown", document.get("name"))))
    }

    protected open fun informationFor(node: Node): String {
        return node.simpleNodeType
    }

    private fun getNode(params: TextDocumentPositionParams?): Node? {
        if (params == null) return null
        val parsingResult = files[params.textDocument.uri] ?: return null
        val root = parsingResult.root ?: return null
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
        val uri = params?.textDocument?.uri ?: return
        val tree = files[uri]?.root ?: return

        generator?.generate(tree)
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
        /*for (file in files) {
            for (symbol in file.value.symbols) {
                val definition = symbol.value.definition as Node
                symbols.add(WorkspaceSymbol(symbol.key, symbolKindOf(definition), Either.forLeft(Location(file.key, toLSPRange(definition.position!!)))))
            }
        }*/
        return CompletableFuture.completedFuture(Either.forRight(symbols))
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        val changes = params?.changes ?: return
        for (change in changes) {
            when (change.type) {
                FileChangeType.Created -> {
                    val uri = change.uri
                    val text = Files.readString(Paths.get(URI(uri)))

                    // parseAndPublishDiagnostics(text, uri)
                }
                FileChangeType.Changed -> {
                    val uri = change.uri
                    val text = Files.readString(Paths.get(URI(uri)))

                    // parseAndPublishDiagnostics(text, uri)
                }
                FileChangeType.Deleted -> {
                    files.remove(change.uri)
                }
                null -> {}
            }
        }
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        exitProcess(0)
    }

    protected open fun log(text: String, verboseExplanation: String? = null) {
        client.logTrace(LogTraceParams(text, verboseExplanation))
    }

    protected open fun showNotification(text: String, messageType: MessageType = MessageType.Info) {
        client.showMessage(MessageParams(messageType, text))
    }

    protected open fun askClient(messageText: String, options: List<String> = listOf("Yes", "No"), messageType: MessageType = MessageType.Info): CompletableFuture<String> {
        val future = CompletableFuture<String>()

        val request = ShowMessageRequestParams(options.map { MessageActionItem(it) }).apply {
            type = messageType
            message = messageText
        }

        client.showMessageRequest(request).thenApply { item -> future.complete(item.title) }

        return future
    }

    protected open fun showDocument() {
        client.showDocument(ShowDocumentParams())
    }
}

interface CodeGenerator<T : Node> {
    fun generate(tree: T)
}

interface SymbolResolver {
    fun resolveSymbols(tree: Node?)
}
