package testing

import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

class DiagnosticListenerClient(private val onDiagnosticPublished: (PublishDiagnosticsParams) -> Unit) : LanguageClient {

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        diagnostics?.let(onDiagnosticPublished)
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
