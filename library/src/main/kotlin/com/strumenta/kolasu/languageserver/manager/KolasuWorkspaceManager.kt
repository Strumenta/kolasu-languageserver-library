package com.strumenta.kolasu.languageserver.manager

import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.languageserver.repository.KolasuServerSymbolRepository
import com.strumenta.kolasu.parsing.KolasuANTLRToken
import com.strumenta.kolasu.parsing.KolasuParser
import org.eclipse.lsp4j.WorkspaceFolder
import java.io.File
import java.net.URI

class KolasuWorkspaceManager(
    private val kolasuParser: KolasuParser<*, *, *, out KolasuANTLRToken>,
    private val nodeIdProvider: NodeIdProvider,
    private val clientManager: KolasuClientManager,
) {

    val symbolRepository = KolasuServerSymbolRepository()
    private val fileManagers: MutableMap<String, KolasuFileManager> = mutableMapOf()

    fun load(folders: List<WorkspaceFolder>) {
        this.clear()
        folders.map { File(URI(it.uri)) }.forEach { folder ->
            folder.walk().filter { it.isFile }.forEach { file ->
                this.set(file.toURI().toString(), file.readText())
            }
        }
    }

    private fun clear() {
        this.symbolRepository.clear()
        this.fileManagers.clear()
    }

    fun get(uri: String): KolasuFileManager {
        return this.fileManagers.getOrPut(uri) {
            KolasuFileManager(uri, this.kolasuParser, this.symbolRepository, this.nodeIdProvider, this.clientManager)
        }
    }

    fun set(uri: String, text: String = File(URI(uri)).readText()) {
        val fileManager = this.get(uri)
        fileManager.synchronizeWith(text)
    }

    fun delete(uri: String) {
        val fileManager = this.get(uri)
        fileManager.deleteSymbols()
        this.fileManagers.remove(uri)
    }

    fun close(uri: String) {
        this.set(uri)
        this.fileManagers.remove(uri)
    }

    fun rename(oldUri: String, newUri: String) {
        // TODO handle file renaming
    }

}
