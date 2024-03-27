package testing

import com.google.gson.JsonObject
import com.strumenta.kolasu.languageserver.KolasuServer
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.system.measureNanoTime

open class TestKolasuServer(
    val serverProvider: () -> KolasuServer,
    val workspacePath: Path = Paths.get("src", "test", "resources")
) {

    protected lateinit var server: KolasuServer

    @BeforeEach
    fun beforeEach() {
        this.server = serverProvider()
    }

    protected open fun initializeServer() {
        expectDiagnostics(0)

        val workspace = workspacePath.toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add(this.server.language, JsonObject())
        server.getWorkspaceService().didChangeConfiguration(DidChangeConfigurationParams(configuration))
    }

    protected open fun expectDiagnostics(amount: Int) {
        server.connect(
            DiagnosticListenerClient {
                assertEquals(amount, it.diagnostics.size)
            }
        )
    }

    protected open fun open(
        uri: String,
        text: String
    ) {
        val textDocument = TextDocumentItem(uri, "", 0, text)
        val parameters = DidOpenTextDocumentParams(textDocument)

        server.textDocumentService.didOpen(parameters)
    }

    protected open fun change(
        uri: String,
        text: String
    ) {
        val document = VersionedTextDocumentIdentifier(uri, null)
        val changes = listOf(TextDocumentContentChangeEvent(text))
        val parameters = DidChangeTextDocumentParams(document, changes)

        return server.textDocumentService.didChange(parameters)
    }

    protected open fun outline(uri: String): DocumentSymbol? {
        val document = TextDocumentIdentifier(uri)
        val parameters = DocumentSymbolParams(document)

        return server.textDocumentService.documentSymbol(parameters).get()?.first()?.right
    }

    protected open fun definition(
        uri: String,
        position: Position
    ): Location? {
        val document = TextDocumentIdentifier(uri)
        val parameters = DefinitionParams(document, position)

        return server.textDocumentService.definition(parameters).get()?.left?.first()
    }

    protected open fun references(
        uri: String,
        position: Position,
        includeDeclaration: Boolean = true
    ): MutableList<out Location>? {
        val document = TextDocumentIdentifier(uri)
        val parameters = ReferenceParams(document, position, ReferenceContext(includeDeclaration))

        return server.textDocumentService.references(parameters).get()
    }

    protected fun requestAtEachPositionInResourceFiles(
        name: String,
        request: (String, Position) -> Unit
    ): List<Long> {
        val timings = mutableListOf<Long>()

        for (file in Files.list(workspacePath)) {
            if (this.server.fileExtensions.contains(file.extension)) {
                val uri = file.toUri().toString()
                val lines = Files.readAllLines(file)

                for (lineNumber in 0 until lines.size) {
                    for (characterNumber in lines[lineNumber].indices) {
                        timings.add(measureNanoTime { request(uri, Position(lineNumber, characterNumber)) })
                    }
                }
            }
        }

        Files.createDirectories(Paths.get("build", "performance"))
        Files.writeString(Paths.get("build", "performance", "$name.csv"), timings.joinToString("\n"))

        return timings
    }
}
