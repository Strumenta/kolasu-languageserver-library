package testing

import com.google.gson.JsonObject
import com.strumenta.kolasu.languageserver.CodeGenerator
import com.strumenta.kolasu.languageserver.KolasuServer
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.parsing.ASTParser
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

open class TestKolasuServer<T : Node>(
    protected open var parser: ASTParser<T>? = null,
    protected open var enableDefinitionCapability: Boolean = false,
    protected open var enableReferencesCapability: Boolean = false,
    protected open var codeGenerator: CodeGenerator<T>? = null,
    protected open var language: String = "languageserver",
    protected open var fileExtensions: List<String> = listOf(),
    protected open var workspacePath: Path = Paths.get("src", "test", "resources")
) {
    protected open lateinit var server: KolasuServer<T>

    @BeforeEach
    fun beforeEach() {
        initializeServer()
    }

    protected open fun initializeServer() {
        server = KolasuServer(parser, language, fileExtensions, enableDefinitionCapability, enableReferencesCapability, codeGenerator)
        expectDiagnostics(0)

        val workspace = workspacePath.toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add(language, JsonObject())
        server.didChangeConfiguration(DidChangeConfigurationParams(configuration))
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

        server.didOpen(parameters)
    }

    protected open fun change(
        uri: String,
        text: String
    ) {
        val document = VersionedTextDocumentIdentifier(uri, null)
        val changes = listOf(TextDocumentContentChangeEvent(text))
        val parameters = DidChangeTextDocumentParams(document, changes)

        return server.didChange(parameters)
    }

    protected open fun outline(uri: String): DocumentSymbol? {
        val document = TextDocumentIdentifier(uri)
        val parameters = DocumentSymbolParams(document)

        return server.documentSymbol(parameters).get()?.first()?.right
    }

    protected open fun definition(
        uri: String,
        position: Position
    ): Location? {
        val document = TextDocumentIdentifier(uri)
        val parameters = DefinitionParams(document, position)

        return server.definition(parameters).get()?.left?.first()
    }

    protected open fun references(
        uri: String,
        position: Position,
        includeDeclaration: Boolean = true
    ): MutableList<out Location>? {
        val document = TextDocumentIdentifier(uri)
        val parameters = ReferenceParams(document, position, ReferenceContext(includeDeclaration))

        return server.references(parameters).get()
    }

    protected fun requestAtEachPositionInResourceFiles(
        name: String,
        request: (String, Position) -> Unit
    ): List<Long> {
        val timings = mutableListOf<Long>()

        for (file in Files.list(workspacePath)) {
            if (fileExtensions.contains(file.extension)) {
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
