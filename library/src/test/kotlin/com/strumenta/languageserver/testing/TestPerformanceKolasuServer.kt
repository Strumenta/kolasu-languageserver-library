package com.strumenta.languageserver.testing

import com.strumenta.kolasu.model.Node
import org.eclipse.lsp4j.Position
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.system.measureNanoTime

class TestPerformanceKolasuServer<T : Node> : TestKolasuServer<T>(fileExtensions = listOf("rpgle", "dds")) {

    private val maxTimePerRequest = 50_000

    @Test
    fun testDefinitionPerformance() {
        val startTime = System.nanoTime()
        val times = mutableListOf<Long>()
        var definitionsResolved = 0f
        for (file in Files.list(workspacePath)) {
            if (fileExtensions.contains(file.extension)) {
                val uri = file.toUri().toString()
                val lines = Files.readAllLines(file)
                for (lineNumber in 0 until lines.size) {
                    val line = lines[lineNumber]
                    for (characterNumber in line.indices) {
                        times.add(measureNanoTime { definition(uri, Position(lineNumber, characterNumber)) })
                        definitionsResolved++
                    }
                }
            }
        }
        Files.writeString(Paths.get("output.csv"), times.joinToString("\n"))

        val time = System.nanoTime() - startTime
        val timePerDefinition = time / definitionsResolved
        println(timePerDefinition)
        assert(timePerDefinition < maxTimePerRequest)
    }

    @Test
    fun testReferencesPerformance() {
        val startTime = System.nanoTime()
        var referencesResolved = 0f
        for (file in Files.list(workspacePath)) {
            if (fileExtensions.contains(file.extension)) {
                val uri = file.toUri().toString()

                val lines = Files.readAllLines(file)
                for (lineNumber in 0 until lines.size) {
                    val line = lines[lineNumber]
                    for (characterNumber in line.indices) {
                        references(uri, Position(lineNumber, characterNumber))
                        referencesResolved++
                    }
                }
            }
        }
        val time = System.nanoTime() - startTime
        val timePerReference = time / referencesResolved
        assert(timePerReference < maxTimePerRequest)
    }
}
