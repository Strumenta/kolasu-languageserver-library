package com.strumenta.languageserver.test.rpg

import com.strumenta.kolasu.model.Node
import com.strumenta.languageserver.SymbolResolver
import com.strumenta.rpgparser.model.CompilationUnit
import com.strumenta.rpgparser.symbolresolution.RPGExternalProcessor
import com.strumenta.rpgparser.symbolresolution.RPGSymbolResolver
import java.nio.file.Paths

class RPGSymbolResolverAdapter : SymbolResolver {
    override fun resolveSymbols(tree: Node?, uri: String) {
        if (tree == null) return

        RPGExternalProcessor.resolve(tree as CompilationUnit, Paths.get("src", "test", "resources").toFile())
        RPGSymbolResolver.resolveSymbols(tree)
    }
}
