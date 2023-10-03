package com.strumenta.rpgle

import com.strumenta.kolasu.languageserver.library.KolasuServer
import com.strumenta.rpgparser.RPGKolasuParser

fun main() {
    val parser = RPGKolasuParser()
    val server = KolasuServer(parser)
    server.startCommunication()
}
