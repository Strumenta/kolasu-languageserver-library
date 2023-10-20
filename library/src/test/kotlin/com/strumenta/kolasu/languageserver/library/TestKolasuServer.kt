package com.strumenta.kolasu.languageserver.library

import com.google.gson.JsonObject
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.traversing.walk
import com.strumenta.rpgparser.RPGKolasuParser
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.nio.file.Files
import java.nio.file.Paths

@TestMethodOrder(OrderAnnotation::class)
class TestKolasuServer {
    @Test
    @Order(0)
    fun testInitializeWithoutWorkspaceFolders() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        val response = server.initialize(InitializeParams()).get()

        assertEquals(null, response)
    }

    @Test
    @Order(1)
    fun testInitializeWithWorkspaceFolders() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), RPGSymbolResolverAdapter())
        val workspace = Paths.get("src", "test", "resources").toUri().toString()
        val response = server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder(workspace)) }).get()

        assertEquals(true, response.capabilities.hoverProvider.left)
    }

    @Test
    @Order(2)
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
    @Order(3)
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
    @Order(4)
    fun testTreeWalk() {
        val tree = RPGKolasuParser().parse(Files.readString(Paths.get("src", "test", "resources", "fibonacci.rpgle"))).root!!
        val visited = mutableListOf<Node>()
        for (node in tree.walk()) {
            visited.add(node)
        }
        for (node in tree.walk()) {
            assertEquals(true, visited.contains(node))
        }
    }

    @Test
    @Order(5)
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
}
