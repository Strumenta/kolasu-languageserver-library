package com.strumenta.kolasu.languageserver.library

import com.google.gson.JsonObject
import com.strumenta.rpgparser.RPGKolasuParser
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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class TestKolasuServer {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setupBeforeAll() {
            val indexes = Paths.get("indexes").toFile()
            if (indexes.exists()) {
                indexes.deleteRecursively()
            }
        }
    }

    @Test
    fun testInitializeWithoutWorkspaceFolders() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        val response = server.initialize(InitializeParams()).get()

        assertEquals(null, response)
    }

    @Test
    fun testInitializeWithWorkspaceFolders() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        val workspace = Paths.get("src", "test", "resources").toUri().toString()
        val response = server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) }).get()

        assertEquals(true, response.capabilities.hoverProvider.left)
    }

    @Test
    fun testDidChangeConfiguration() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        server.connect(DiagnosticSizeCheckerClient(0))
        val workspace = Paths.get("src", "test", "resources").toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add("rpg", JsonObject())
        server.didChangeConfiguration(DidChangeConfigurationParams(configuration))
    }

    @Test
    fun testDidChange() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        server.connect(DiagnosticSizeCheckerClient(0))
        val workspace = Paths.get("src", "test", "resources").toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add("rpg", JsonObject())
        server.didChangeConfiguration(DidChangeConfigurationParams(configuration))

        val fibonacci = Paths.get("src", "test", "resources", "fibonacci.rpgle")
        server.didChange(DidChangeTextDocumentParams(VersionedTextDocumentIdentifier(fibonacci.toUri().toString(), null), listOf(TextDocumentContentChangeEvent(Files.readString(fibonacci)))))
    }

    @Test
    fun testDefinition() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        server.connect(DiagnosticSizeCheckerClient(0))
        val workspace = Paths.get("src", "test", "resources").toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add("rpg", JsonObject())
        server.didChangeConfiguration(DidChangeConfigurationParams(configuration))

        val fibonacci = Paths.get("src", "test", "resources", "fibonacci.rpgle")
        val definition = server.definition(DefinitionParams(TextDocumentIdentifier(fibonacci.toUri().toString()), Position(20, 38))).get().left.first()

        assertEquals(fibonacci.toUri().toString(), definition.uri)
        assertEquals(2, definition.range.start.line)
        assertEquals(2, definition.range.end.line)
        assertEquals(0, definition.range.start.character)
        assertEquals(42, definition.range.end.character)
    }

    @Test
    fun testReferencesWithoutDefinition() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        server.connect(DiagnosticSizeCheckerClient(0))
        val workspace = Paths.get("src", "test", "resources").toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add("rpg", JsonObject())
        server.didChangeConfiguration(DidChangeConfigurationParams(configuration))

        val fibonacci = Paths.get("src", "test", "resources", "fibonacci.rpgle")
        val references = server.references(ReferenceParams(TextDocumentIdentifier(fibonacci.toUri().toString()), Position(20, 38), ReferenceContext(false))).get()

        assertEquals(4, references.size)
    }

    @Test
    fun testReferencesWithDefinition() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        server.connect(DiagnosticSizeCheckerClient(0))
        val workspace = Paths.get("src", "test", "resources").toUri().toString()
        server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) })
        server.initialized(InitializedParams())

        val configuration = JsonObject()
        configuration.add("rpg", JsonObject())
        server.didChangeConfiguration(DidChangeConfigurationParams(configuration))

        val fibonacci = Paths.get("src", "test", "resources", "fibonacci.rpgle")
        val references = server.references(ReferenceParams(TextDocumentIdentifier(fibonacci.toUri().toString()), Position(20, 38), ReferenceContext(true))).get()

        assertEquals(5, references.size)
    }
}
