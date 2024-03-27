package com.strumenta.kolasu.languageserver.service

import com.strumenta.kolasu.languageserver.manager.KolasuWorkspaceManager
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType.Changed
import org.eclipse.lsp4j.FileChangeType.Created
import org.eclipse.lsp4j.FileChangeType.Deleted
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.services.WorkspaceService

class KolasuWorkspaceService(
    private val workspaceManager: KolasuWorkspaceManager
): WorkspaceService {

   override fun didChangeConfiguration(parameters: DidChangeConfigurationParams?) {
        // TODO handle configuration changesgit a
    }

    override fun didChangeWatchedFiles(parameters: DidChangeWatchedFilesParams?) {
        parameters?.changes?.filter { it.type == Created || it.type == Changed }
            ?.map(FileEvent::getUri)?.forEach(this.workspaceManager::set)
        parameters?.changes?.filter { it.type == Deleted }
            ?.map(FileEvent::getUri)?.forEach(this.workspaceManager::delete)
    }

    override fun didRenameFiles(parameters: RenameFilesParams?) {
        parameters?.files?.forEach { this.workspaceManager.rename(it.oldUri, it.newUri) }
    }

}
