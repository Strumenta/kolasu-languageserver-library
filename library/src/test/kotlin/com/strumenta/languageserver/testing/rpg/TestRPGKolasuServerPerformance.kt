package com.strumenta.languageserver.testing.rpg

import com.strumenta.kolasu.model.Node
import com.strumenta.languageserver.testing.TestKolasuServer
import org.junit.jupiter.api.Test

class TestRPGKolasuServerPerformance<T : Node> : TestKolasuServer<T>(fileExtensions = listOf("rpgle", "dds")) {

    private val maximumMillisecondsOnAverage = 500

    @Test
    fun testDefinitionPerformance() {
        val timings = requestAtEachPositionInResourceFiles("definition") { uri, position -> definition(uri, position) }

        val averageMillisecondsPerRequest = timings.sum() / (timings.size * 1e6f)

        assert(averageMillisecondsPerRequest < maximumMillisecondsOnAverage)
    }

    @Test
    fun testReferencesIncludingDefinitionPerformance() {
        val timings = requestAtEachPositionInResourceFiles("referencesIncludingDeclaration") { uri, position -> references(uri, position) }

        val averageMillisecondsPerRequest = timings.sum() / (timings.size * 1e6f)

        assert(averageMillisecondsPerRequest < maximumMillisecondsOnAverage)
    }

    @Test
    fun testReferencesNotIncludingDefinitionPerformance() {
        val timings = requestAtEachPositionInResourceFiles("referencesNotIncludingDeclaration") { uri, position -> references(uri, position, false) }

        val averageMillisecondsPerRequest = timings.sum() / (timings.size * 1e6f)

        assert(averageMillisecondsPerRequest < maximumMillisecondsOnAverage)
    }
}
