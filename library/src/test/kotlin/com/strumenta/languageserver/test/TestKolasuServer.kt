package com.strumenta.languageserver.test

import com.google.gson.JsonObject
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.parsing.ASTParser
import com.strumenta.languageserver.CodeGenerator
import com.strumenta.languageserver.KolasuServer
import com.strumenta.languageserver.SymbolResolver
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
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
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import java.nio.file.Paths

open class TestKolasuServer<T : Node>() {

    protected open var parser: ASTParser<T>? = null
    protected open var symbolResolver: SymbolResolver? = null
    protected open var generator: CodeGenerator<T>? = null

    protected open var language: String = "languageserver"
    protected open var fileExtensions: List<String> = listOf()

    protected open var workspacePath: Path = Paths.get("src", "test", "resources")

    protected open lateinit var server: KolasuServer<T>

    @BeforeEach
    fun beforeEach() {
        server = initializeServer()
    }

    protected open fun initializeServer(): KolasuServer<T> {
        val server = KolasuServer(parser, language, fileExtensions, symbolResolver, generator)
        server.connect(DiagnosticSizeCheckerClient(0))
        val workspace = workspacePath.toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add(language, JsonObject())
        server.didChangeConfiguration(DidChangeConfigurationParams(configuration))

        return server
    }

    protected open fun change(uri: String, text: String) {
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
    protected open fun expectDiagnostics(amount: Int) {
        server.connect(DiagnosticSizeCheckerClient(amount))
    }

    protected open fun definition(uri: String, position: Position): Location? {
        val document = TextDocumentIdentifier(uri)
        val parameters = DefinitionParams(document, position)

        return server.definition(parameters).get()?.left?.first()
    }

    protected open fun references(uri: String, position: Position, includeDeclaration: Boolean = true): MutableList<out Location>? {
        val document = TextDocumentIdentifier(uri)
        val parameters = ReferenceParams(document, position, ReferenceContext(includeDeclaration))

        return server.references(parameters).get()
    }
}
