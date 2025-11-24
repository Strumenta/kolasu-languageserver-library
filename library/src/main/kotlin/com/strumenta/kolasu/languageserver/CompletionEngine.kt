package com.strumenta.kolasu.languageserver

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

interface CompletionEngine {
    fun complete(uri: String, text: String, pos: Position): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>>
    fun resolve(item: CompletionItem): CompletionItem = item
    val triggerCharacters: List<String> get() = listOf(".", ":", "@")
}
