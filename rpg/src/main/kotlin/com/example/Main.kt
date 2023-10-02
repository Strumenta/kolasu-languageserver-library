package com.example

import com.strumenta.rpgparser.RPGKolasuParser
import com.strumenta.rpgparser.model.CompilationUnit
import com.strumenta.rpgparser.symbolresolution.RPGExternalProcessor
import com.strumenta.rpgparser.symbolresolution.RPGSymbolResolver
import java.io.File

fun main() {
    val parser = RPGKolasuParser()
    val result = parser.parse(File("examples/sample.rpgle"))
    val root = result.root as? CompilationUnit
    if (root != null) {
        RPGExternalProcessor.resolve(root, File("examples"))
        RPGSymbolResolver.resolveSymbols(root)
    }
    println(result.correct)
}
