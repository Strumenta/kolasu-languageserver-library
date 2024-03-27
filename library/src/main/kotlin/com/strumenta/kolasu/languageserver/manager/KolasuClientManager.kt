package com.strumenta.kolasu.languageserver.manager

import com.strumenta.kolasu.languageserver.utils.kolasuPositionToLsp4jRange
import com.strumenta.kolasu.validation.Issue
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CompletionRegistrationOptions
import org.eclipse.lsp4j.DefinitionRegistrationOptions
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.DocumentFilter
import org.eclipse.lsp4j.FileSystemWatcher
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceRegistrationOptions
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import java.util.*

class KolasuClientManager(
    private val language: String,
    private val fileExtensions: List<String>
) {

    private val documentSelector: List<DocumentFilter> by lazy {
        this.fileExtensions.map { DocumentFilter(this.language, "file", "*.${it}") }
    }

    var languageClient: LanguageClient? = null
    var capabilities: ClientCapabilities? = null

    fun updateFileIssues(fileUri: String, issues: List<Issue>) {
        this.languageClient?.publishDiagnostics(PublishDiagnosticsParams(fileUri, issues.mapNotNull { issue ->
            issue.position?.let { kolasuPositionToLsp4jRange(it) }?.let { Diagnostic(it, issue.message) }
        }))
    }

    fun supportsDynamicDefinitionCapabilityRegistration(): Boolean {
        return this.capabilities?.textDocument?.definition?.dynamicRegistration ?: false
    }

    fun supportsDynamicReferencesCapabilityRegistration(): Boolean {
        return this.capabilities?.textDocument?.references?.dynamicRegistration ?: false
    }

    fun supportsDynamicCompletionCapabilityRegistration(): Boolean {
        return this.capabilities?.textDocument?.completion?.dynamicRegistration ?: false
    }

    fun watchFileChanges() {
        this.registerSupport("workspace/didChangeWatchedFiles", DidChangeWatchedFilesRegistrationOptions(
            this.fileExtensions.map { FileSystemWatcher(Either.forLeft("/**/*.${it}")) }
        ))
    }

    fun registerDefinitionCapability() {
        if (this.supportsDynamicDefinitionCapabilityRegistration()) {
            this.registerSupport("textDocument/definition", DefinitionRegistrationOptions()
                .also { it.documentSelector = this.documentSelector })
        }
    }

    fun registerReferencesCapability() {
        if (this.supportsDynamicReferencesCapabilityRegistration()) {
            this.registerSupport("textDocument/references", ReferenceRegistrationOptions()
                .also { it.documentSelector = this.documentSelector })
        }
    }

    fun registerCompletionCapability() {
        if (this.supportsDynamicCompletionCapabilityRegistration()) {
            this.registerSupport("textDocument/completion", CompletionRegistrationOptions()
                .also { it.documentSelector = this.documentSelector})
        }
    }

    private fun registerSupport(method: String, options: Any) {
        val registration = Registration(UUID.randomUUID().toString(), method, options)
        val parameters = RegistrationParams(listOf(registration))
        this.languageClient?.registerCapability(parameters)
    }

}
