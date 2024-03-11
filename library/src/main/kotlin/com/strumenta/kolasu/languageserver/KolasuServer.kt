package com.strumenta.kolasu.languageserver

import com.google.gson.JsonObject
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.PossiblyNamed
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.model.children
import com.strumenta.kolasu.model.kReferenceByNameProperties
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
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.apache.lucene.search.SortedNumericSortField
import org.apache.lucene.search.TermQuery
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
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.LogTraceParams
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
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.WorkspaceFoldersOptions
import org.eclipse.lsp4j.WorkspaceServerCapabilities
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

open class KolasuServer<T : Node>(
    protected open val parser: ASTParser<T>?,
    protected open val language: String = "",
    protected open val extensions: List<String> = listOf(),
    protected open val enableDefinitionCapability: Boolean = false,
    protected open val enableReferencesCapability: Boolean = false,
    protected open val generator: CodeGenerator<T>? = null,
) : LanguageServer, TextDocumentService, WorkspaceService, LanguageClientAware {
    protected open lateinit var client: LanguageClient
    protected open var configuration: JsonObject = JsonObject()
    protected open var traceLevel: String = "off"
    protected open val folders: MutableList<String> = mutableListOf()
    protected open val files: MutableMap<String, ParsingResult<T>> = mutableMapOf()
    protected open val indexPath: Path = Paths.get("indexes", UUID.randomUUID().toString())
    protected open lateinit var indexWriter: IndexWriter
    protected open lateinit var indexSearcher: IndexSearcher
    protected open val uuid = mutableMapOf<Node, String>()

    override fun getTextDocumentService() = this

    override fun getWorkspaceService() = this

    open fun startCommunication(
        inputStream: InputStream = System.`in`,
        outputStream: OutputStream = System.out,
    ) {
        val launcher = LSPLauncher.createServerLauncher(this, inputStream, outputStream)
        connect(launcher.remoteProxy)
        launcher.startListening()
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        val workspaceFolders = params?.workspaceFolders
        if (workspaceFolders != null) {
            for (folder in workspaceFolders) {
                folders.add(folder.uri)
            }
        }

        val capabilities = ServerCapabilities()

        capabilities.workspace =
            WorkspaceServerCapabilities(
                WorkspaceFoldersOptions().apply {
                    supported = true
                    changeNotifications = Either.forLeft("didChangeWorkspaceFoldersRegistration")
                },
            )

        capabilities.setTextDocumentSync(
            TextDocumentSyncOptions().apply {
                openClose = true
                change = TextDocumentSyncKind.Full
            },
        )
        capabilities.setDocumentSymbolProvider(true)
        capabilities.setDefinitionProvider(this.enableDefinitionCapability)
        capabilities.setReferencesProvider(this.enableReferencesCapability)

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun initialized(params: InitializedParams?) {
        val watchers = mutableListOf<FileSystemWatcher>()
        for (folder in folders) {
            watchers.add(FileSystemWatcher(Either.forLeft(URI(folder).path + """/**/*{${extensions.joinToString(","){".$it"}}}""")))
        }
        client.registerCapability(
            RegistrationParams(
                listOf(
                    Registration(
                        "workspace/didChangeWatchedFiles",
                        "workspace/didChangeWatchedFiles",
                        DidChangeWatchedFilesRegistrationOptions(watchers),
                    ),
                ),
            ),
        )

        client.registerCapability(
            RegistrationParams(
                listOf(
                    Registration(
                        "workspace/didChangeConfiguration",
                        "workspace/didChangeConfiguration",
                        object {
                            val section = language
                        },
                    ),
                ),
            ),
        )
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        val settings = params?.settings as? JsonObject ?: return
        configuration = settings[language].asJsonObject

        if (Files.exists(indexPath)) {
            indexPath.toFile().deleteRecursively()
        }
        val indexDirectory = FSDirectory.open(indexPath)
        val indexConfiguration = IndexWriterConfig(StandardAnalyzer()).apply { openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND }
        indexWriter = IndexWriter(indexDirectory, indexConfiguration)
        commitIndex()

        client.createProgress(WorkDoneProgressCreateParams(Either.forLeft("indexing")))
        client.notifyProgress(
            ProgressParams(Either.forLeft("indexing"), Either.forLeft(WorkDoneProgressBegin().apply { title = "indexing" })),
        )
        for (folder in folders) {
            val projectFiles = File(URI(folder)).walk().filter { extensions.contains(it.extension) }.toList()
            val totalBytes = projectFiles.sumOf { it.length() }
            var parsedBytes = 0L
            for (file in projectFiles) {
                parse(file.toURI().toString(), Files.readString(file.toPath()))
                parsedBytes += file.length()
                val percentage = (parsedBytes * 100 / totalBytes).toInt()
                client.notifyProgress(
                    ProgressParams(
                        Either.forLeft("indexing"),
                        Either.forLeft(
                            WorkDoneProgressReport().apply {
                                this.percentage = percentage
                            },
                        ),
                    ),
                )
            }
        }
        client.notifyProgress(ProgressParams(Either.forLeft("indexing"), Either.forLeft(WorkDoneProgressEnd())))
    }

    override fun setTrace(params: SetTraceParams?) {
        val level = params?.value ?: return
        traceLevel = level
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = params.textDocument.text

        parse(uri, text)
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = params.contentChanges.first()?.text ?: return

        parse(uri, text)
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val text = Files.readString(Paths.get(URI(uri)))

        parse(uri, text)
    }

    open fun parse(
        uri: String,
        text: String,
    ) {
        if (!::indexWriter.isInitialized) return

        val parsingResult = parser?.parse(text) ?: return
        files[uri] = parsingResult

        val tree = parsingResult.root ?: return

        indexWriter.deleteDocuments(TermQuery(Term("uri", uri)))
        commitIndex()
        var id = 0
        for (node in tree.walk()) {
            uuid[node] = "$uri${id++}"
        }
        for (node in tree.walk()) {
            val document = Document()
            if (uuid[node] == null) continue
            if (node == tree) {
                document.add(IntField("root", 1, Field.Store.YES))
            }
            document.add(StringField("uuid", uuid[node], Field.Store.YES))
            document.add(StringField("uri", uri, Field.Store.YES))
            node.position?.let { position ->
                document.add(IntField("startLine", position.start.line, Field.Store.YES))
                document.add(IntField("startColumn", position.start.column, Field.Store.YES))
                document.add(IntField("endLine", position.end.line, Field.Store.YES))
                document.add(IntField("endColumn", position.end.column, Field.Store.YES))
                document.add(IntField("size", sizeOf(position), Field.Store.YES))
            }

            if (node is PossiblyNamed && node.name != null) {
                document.add(StringField("name", node.name, Field.Store.YES))
            }

            // handle reference by name properties
            node.kReferenceByNameProperties()
                .mapNotNull { it.get(node) as? ReferenceByName<*> }
                .mapNotNull { it.referred as? Node }
                .mapNotNull { uuid[it] }
                .map { StringField("reference", it, Field.Store.YES) }
                .forEach(document::add)

            indexWriter.addDocument(document)
        }
        commitIndex()

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
                    diagnostics.add(
                        Diagnostic(
                            toLSPRange(node.position!!),
                            "Leaf type: ${node.simpleNodeType} but findByPositionType: ${tree.findByPosition(
                                node.position!!,
                            )?.simpleNodeType}",
                        ).apply {
                            severity = DiagnosticSeverity.Warning
                        },
                    )
                }

                if (showLeafPositions) {
                    diagnostics.add(
                        Diagnostic(
                            toLSPRange(node.position!!),
                            "Leaf position: ${node.position}, Source text: ${node.sourceText}",
                        ).apply {
                            severity = DiagnosticSeverity.Information
                        },
                    )
                }
            }
        }
        client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
    }

    // ? This is a bad workaround. In case nodes contain the sourceText field set properly, its length could be used instead
    protected open fun sizeOf(position: com.strumenta.kolasu.model.Position): Int {
        val lineDifference = position.end.line - position.start.line
        val lineValue = lineDifference * 8000
        val columnDifference = position.end.column - position.start.column

        return lineValue + columnDifference
    }

    override fun documentSymbol(params: DocumentSymbolParams?): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> {
        val uri = params?.textDocument?.uri ?: return CompletableFuture.completedFuture(null)
        val root = files[uri]?.root ?: return CompletableFuture.completedFuture(null)
        val range = toLSPRange(root.position!!)

        val namedTree = DocumentSymbol("Named tree", SymbolKind.Variable, range, range, "", mutableListOf())
        appendNamedChildren(root, namedTree)

        return CompletableFuture.completedFuture(mutableListOf(Either.forRight(namedTree)))
    }

    protected open fun appendNamedChildren(
        node: Node,
        parent: DocumentSymbol,
    ) {
        var nextParent = parent
        if (node is PossiblyNamed && node.name != null) {
            val range = toLSPRange(node.position!!)
            val symbol = DocumentSymbol(node.name, symbolKindOf(node), range, range, "", mutableListOf())
            parent.children.add(symbol)
            nextParent = symbol
        }
        node.children.forEach { child ->
            appendNamedChildren(child, nextParent)
        }
    }

    protected open fun symbolKindOf(node: Node): SymbolKind {
        return SymbolKind.Variable
    }

    protected open fun symbolKindOf(document: Document): SymbolKind {
        return SymbolKind.Variable
    }

    override fun definition(
        params: DefinitionParams?,
    ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        val document = getDocument(params) ?: return CompletableFuture.completedFuture(null)

        val symbolID = document.fields.find { it.name() == "reference" }?.stringValue() ?: return CompletableFuture.completedFuture(null)
        val result =
            indexSearcher.search(
                TermQuery(Term("uuid", symbolID)),
                1,
            ).scoreDocs.firstOrNull() ?: return CompletableFuture.completedFuture(null)
        val definition = indexSearcher.storedFields().document(result.doc)

        if (definition.fields.none { it.name() == "startLine" }) return CompletableFuture.completedFuture(null)
        val location = toLSPLocation(definition)

        return CompletableFuture.completedFuture(Either.forLeft(mutableListOf(location)))
    }

    override fun references(params: ReferenceParams?): CompletableFuture<MutableList<out Location>> {
        val document = getDocument(params) ?: return CompletableFuture.completedFuture(null)

        val symbolID = document.fields.find { it.name() == "reference" }?.stringValue() ?: return CompletableFuture.completedFuture(null)
        val results = indexSearcher.search(TermQuery(Term("reference", symbolID)), Int.MAX_VALUE).scoreDocs

        val list = mutableListOf<Location>()
        for (result in results) {
            val reference = indexSearcher.storedFields().document(result.doc)
            list.add(toLSPLocation(reference))
        }

        if (params?.context?.isIncludeDeclaration == true) {
            val result =
                indexSearcher.search(
                    TermQuery(Term("uuid", symbolID)),
                    1,
                ).scoreDocs.firstOrNull() ?: return CompletableFuture.completedFuture(null)
            val definition = indexSearcher.storedFields().document(result.doc)

            list.add(toLSPLocation(definition))
        }

        return CompletableFuture.completedFuture(list)
    }

    // ? This is not completely correct, as it assumes that the node at this position is contained in a single line
    protected open fun getDocument(params: TextDocumentPositionParams?): Document? {
        val uri = params?.textDocument?.uri ?: return null
        val position = params.position

        val query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("uri", uri)), BooleanClause.Occur.MUST)
                .add(IntPoint.newExactQuery("startLine", position.line + 1), BooleanClause.Occur.MUST)
                .add(IntPoint.newExactQuery("endLine", position.line + 1), BooleanClause.Occur.MUST)
                .add(IntPoint.newRangeQuery("startColumn", Int.MIN_VALUE, position.character), BooleanClause.Occur.MUST)
                .add(IntPoint.newRangeQuery("endColumn", position.character, Int.MAX_VALUE), BooleanClause.Occur.MUST)
                .build()

        val sortingField = SortedNumericSortField("size", SortField.Type.INT, true)
        val results = indexSearcher.search(query, 100, Sort(sortingField))
        if (results.scoreDocs.isEmpty()) return null

        val documentID = results.scoreDocs.last().doc
        return indexSearcher.storedFields().document(documentID)
    }

    protected open fun toLSPRange(kolasuRange: com.strumenta.kolasu.model.Position): Range {
        val start = Position(kolasuRange.start.line - 1, kolasuRange.start.column)
        val end = Position(kolasuRange.end.line - 1, kolasuRange.end.column)
        return Range(start, end)
    }

    protected open fun toLSPLocation(document: Document): Location {
        val uri = document.get("uri")
        val range =
            Range(
                Position(document.get("startLine").toInt() - 1, document.get("startColumn").toInt()),
                Position(document.get("endLine").toInt() - 1, document.get("endColumn").toInt()),
            )
        return Location(uri, range)
    }

    protected open fun toKolasuRange(position: Position): com.strumenta.kolasu.model.Position {
        val start = Point(position.line + 1, position.character)
        val end = Point(position.line + 1, position.character)
        return com.strumenta.kolasu.model.Position(start, end)
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        val uri = params?.textDocument?.uri ?: return
        val tree = files[uri]?.root ?: return

        generator?.generate(tree, uri)
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

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        val changes = params?.changes ?: return
        for (change in changes) {
            when (change.type) {
                FileChangeType.Created -> {
                    val uri = change.uri
                    val text = Files.readString(Paths.get(URI(uri)))

                    parse(uri, text)
                }
                FileChangeType.Changed -> {
                    val uri = change.uri
                    val text = Files.readString(Paths.get(URI(uri)))

                    parse(uri, text)
                }
                FileChangeType.Deleted -> {
                    files.remove(change.uri)
                }
                null -> {}
            }
        }
    }

    override fun shutdown(): CompletableFuture<Any> {
        indexWriter.close()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        exitProcess(0)
    }

    protected open fun log(
        text: String,
        verboseExplanation: String? = null,
    ) {
        client.logTrace(LogTraceParams(text, verboseExplanation))
    }

    protected open fun showNotification(
        text: String,
        messageType: MessageType = MessageType.Info,
    ) {
        client.showMessage(MessageParams(messageType, text))
    }

    protected open fun askClient(
        messageText: String,
        options: List<String> = listOf("Yes", "No"),
        messageType: MessageType = MessageType.Info,
    ): CompletableFuture<String> {
        val future = CompletableFuture<String>()

        val request =
            ShowMessageRequestParams(options.map { MessageActionItem(it) }).apply {
                type = messageType
                message = messageText
            }

        client.showMessageRequest(request).thenApply { item -> future.complete(item.title) }

        return future
    }

    protected open fun commitIndex() {
        indexWriter.commit()
        val reader = DirectoryReader.open(FSDirectory.open(indexPath))
        indexSearcher = IndexSearcher(reader)
    }
}

interface CodeGenerator<T : Node> {
    fun generate(
        tree: T,
        uri: String,
    )
}

interface SymbolResolver {
    fun resolveSymbols(
        tree: Node?,
        uri: String,
    )
}
