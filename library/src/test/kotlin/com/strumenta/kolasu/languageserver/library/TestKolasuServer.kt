package com.strumenta.kolasu.languageserver.library

import com.strumenta.kolasu.model.Node
import com.strumenta.rpgparser.RPGKolasuParser
import com.strumenta.rpgparser.model.CompilationUnit
import com.strumenta.rpgparser.symbolresolution.RPGSymbolResolver
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class TestKolasuServer {
    @Test
    fun testInitializeWithoutWorkspaceFolders() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), MySymbolResolver())
        val response = server.initialize(InitializeParams()).get()

        assertEquals(null, response)
    }

    @Test
    fun testInitializeWithWorkspaceFolders() {
        val server = KolasuServer(RPGKolasuParser(), "rpg", listOf("rpgle", "dds"), MySymbolResolver())
        val response = server.initialize(InitializeParams().apply { workspaceFolders = mutableListOf(WorkspaceFolder("workspace")) }).get()

        assertEquals(true, response.capabilities.hoverProvider.left)
    }
}

class MySymbolResolver : SymbolResolver {
    override fun resolveSymbols(tree: Node?) {
        if (tree == null) return

        com.strumenta.rpgparser.symbolresolution.RPGExternalProcessor.resolve(tree as CompilationUnit, Paths.get("..", "..", "..", "..", "..", "..", "resources").toFile())
        RPGSymbolResolver.resolveSymbols(tree)
    }
}
