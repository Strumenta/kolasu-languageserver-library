package com.strumenta.kolasu.languageserver.library

import com.google.gson.JsonObject
import com.strumenta.rpgparser.RPGKolasuParser
import com.strumenta.rpgparser.model.CompilationUnit
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class TestKolasuServer {

    private val testFilePath = Paths.get("src", "test", "resources", "fibonacci.rpgle")
    private val testFile = TextDocumentIdentifier(testFilePath.toUri().toString())
    private val symbolPosition = Position(20, 38)
    private val noSymbolPosition = Position(15, 1)

    @Test
    fun testInitializeWithoutWorkspaceFolders() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())

        val response = server.initialize(InitializeParams()).get()

        assertEquals(null, response)
    }

    @Test
    fun testInitializeWithWorkspaceFolders() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())

        val response = server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(Paths.get("src", "test", "resources").toUri().toString())) }).get()

        assertEquals(true, response.capabilities.hoverProvider.left)
    }

    @Test
    fun testDidChange() {
        val server = initializeServer()

        server.didChange(DidChangeTextDocumentParams(VersionedTextDocumentIdentifier(testFilePath.toUri().toString(), null), listOf(TextDocumentContentChangeEvent(Files.readString(testFilePath)))))
    }

    @Test
    fun testDefinitionOfNoSymbol() {
        val server = initializeServer()

        val definition = server.definition(DefinitionParams(testFile, noSymbolPosition)).get()

        assertEquals(null, definition)
    }

    @Test
    fun testDefinitionOfSymbol() {
        val server = initializeServer()

        val definition = server.definition(DefinitionParams(testFile, symbolPosition)).get().left.first()

        assertEquals(testFilePath.toUri().toString(), definition.uri)
        assertEquals(2, definition.range.start.line)
        assertEquals(2, definition.range.end.line)
        assertEquals(0, definition.range.start.character)
        assertEquals(42, definition.range.end.character)
    }

    @Test
    fun testReferencesOfNoSymbol() {
        val server = initializeServer()

        val references = server.references(ReferenceParams(testFile, noSymbolPosition, ReferenceContext(false))).get()

        assertEquals(null, references)
    }

    @Test
    fun testReferencesWithoutDefinition() {
        val server = initializeServer()

        val references = server.references(ReferenceParams(testFile, symbolPosition, ReferenceContext(false))).get()

        assertEquals(4, references.size)
    }

    @Test
    fun testReferencesWithDefinition() {
        val server = initializeServer()

        val references = server.references(ReferenceParams(testFile, symbolPosition, ReferenceContext(true))).get()

        assertEquals(5, references.size)
    }

    private fun initializeServer(): KolasuServer<CompilationUnit> {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        server.connect(DiagnosticSizeCheckerClient(0))
        val workspace = Paths.get("src", "test", "resources").toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add("rpg", JsonObject())
        server.didChangeConfiguration(DidChangeConfigurationParams(configuration))

        return server
    }
}
