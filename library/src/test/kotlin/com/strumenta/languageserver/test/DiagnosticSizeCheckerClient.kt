package com.strumenta.languageserver.test

import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.concurrent.CompletableFuture

class DiagnosticSizeCheckerClient(private val expectedDiagnostics: Int) : LanguageClient {

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        assertEquals(expectedDiagnostics, diagnostics?.diagnostics?.size)
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
        return CompletableFuture.completedFuture(null)
    }
    override fun registerCapability(params: RegistrationParams?): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }
    override fun createProgress(params: WorkDoneProgressCreateParams?): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun notifyProgress(params: ProgressParams?) {}
    override fun telemetryEvent(message: Any?) {}
    override fun logMessage(message: MessageParams?) {}
    override fun showMessage(message: MessageParams?) {}
}
