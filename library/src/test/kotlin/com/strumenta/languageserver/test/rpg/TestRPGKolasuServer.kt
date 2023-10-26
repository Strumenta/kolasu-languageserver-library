package com.strumenta.languageserver.test.rpg

import com.strumenta.kolasu.parsing.ASTParser
import com.strumenta.languageserver.SymbolResolver
import com.strumenta.languageserver.test.TestKolasuServer
import com.strumenta.rpgparser.RPGKolasuParser
import com.strumenta.rpgparser.model.CompilationUnit
import org.eclipse.lsp4j.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.toPath

class TestRPGKolasuServer : TestKolasuServer<CompilationUnit>() {

    override var parser: ASTParser<CompilationUnit>? = RPGKolasuParser()
    override var symbolResolver: SymbolResolver? = RPGSymbolResolverAdapter()

    private val fibonacciFile = Paths.get("src", "test", "resources", "fibonacci.rpgle").toUri().toString()
    private val inexistentFile = Paths.get("inexistent").toUri().toString()
    private val symbolPosition = Position(19, 38)
    private val noSymbolPosition = Position(14, 1)
    private val externalSymbolPosition = Position(40, 52)

    @Test
    fun testDidChangePublishesDiagnostics() {
        val fibonacciPath = URI(fibonacciFile).toPath()

        var code = Files.readString(fibonacciPath) + "forced error"
        expectDiagnostics(1)
        change(fibonacciFile, code)

        code = Files.readString(fibonacciPath)
        expectDiagnostics(0)
        change(fibonacciFile, code)
    }

    @Test
    fun testChangeInexistentFile() {
        expectDiagnostics(0)
        change(inexistentFile, "code")
    }

    @Test
    fun testOutline() {
        val outline = outline(fibonacciFile)!!

        assertEquals("Named tree", outline.name)
        assertEquals(11, outline.children.size)

        for (i in 0..10) {
            val child = outline.children[i]
            if (i == 6) {
                assertEquals(4, child.children.size)
            } else {
                assertEquals(0, child.children.size)
            }
        }
    }

    @Test
    fun testOutlineOfInexistentFile() {
        val outline = outline(inexistentFile)

        assertEquals(null, outline)
    }

    @Test
    fun testDefinitionOfNoSymbol() {
        val definition = definition(fibonacciFile, noSymbolPosition)

        assertEquals(null, definition)
    }

    @Test
    fun testDefinitionOfSymbol() {
        val definition = definition(fibonacciFile, symbolPosition)!!

        assertEquals(fibonacciFile, definition.uri)
        assertEquals(1, definition.range.start.line)
        assertEquals(1, definition.range.end.line)
        assertEquals(0, definition.range.start.character)
        assertEquals(42, definition.range.end.character)
    }

    @Test
    fun testExternalSymbolDefinition() {
        val definition = definition(fibonacciFile, externalSymbolPosition)

        assertEquals(null, definition)
    }

    @Test
    fun testReferencesOfNoSymbol() {
        val references = references(fibonacciFile, noSymbolPosition)

        assertEquals(null, references)
    }

    @Test
    fun testReferencesOfSymbolWithoutDeclaration() {
        val references = references(fibonacciFile, symbolPosition, includeDeclaration = false)!!

        assertEquals(4, references.size)
    }

    @Test
    fun testReferencesOfSymbolWithDeclaration() {
        val references = references(fibonacciFile, symbolPosition, includeDeclaration = true)!!

        assertEquals(5, references.size)
    }
}
