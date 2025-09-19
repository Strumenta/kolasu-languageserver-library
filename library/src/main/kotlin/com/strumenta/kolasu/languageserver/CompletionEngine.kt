package com.strumenta.kolasu.languageserver

import org.eclipse.lsp4j.*

interface CompletionEngine {
    fun complete(uri: String, text: String, pos: Position): List<CompletionItem>
    fun resolve(item: CompletionItem): CompletionItem = item
}
