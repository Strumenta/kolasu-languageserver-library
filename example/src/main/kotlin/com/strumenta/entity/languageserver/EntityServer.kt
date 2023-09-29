package com.strumenta.entity.languageserver

import com.strumenta.entity.parser.EntityKolasuParser
import com.strumenta.kolasu.languageserver.library.KolasuServer

fun main() {
    val parser = EntityKolasuParser()
    val server = KolasuServer(parser)
    server.startCommunication()
}
