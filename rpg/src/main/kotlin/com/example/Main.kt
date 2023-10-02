package com.example

import com.strumenta.kolasu.languageserver.library.KolasuServer
import com.strumenta.kolasu.parsing.KolasuParser
import com.strumenta.rpgparser.RPGKolasuParser
import com.strumenta.rpgparser.RPGParser
import com.strumenta.rpgparser.model.CompilationUnit
import com.strumenta.rpgparser.symbolresolution.RPGExternalProcessor
import com.strumenta.rpgparser.symbolresolution.RPGSymbolResolver
import java.io.File

fun main() {
    val parser = RPGKolasuParser()
    val server = KolasuServer(parser)
    server.startCommunication()
}
