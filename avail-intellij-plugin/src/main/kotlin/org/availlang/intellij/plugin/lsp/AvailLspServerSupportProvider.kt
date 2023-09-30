package org.availlang.intellij.plugin.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import org.availlang.intellij.plugin.language.AvailFileType
import org.availlang.lsp.AvailLanguageServer


/**
 * The [LspServerSupportProvider] [LspServerSupportProvider] used to add
 * Avail LSP support, ensuring the [AvailLanguageServer] is started when an
 * Avail file is opened.
 *
 * @author Richard Arriaga
 */
class AvailLspServerSupportProvider: LspServerSupportProvider
{
	override fun fileOpened(
		project: Project,
		file: VirtualFile,
		serverStarter: LspServerSupportProvider.LspServerStarter)
	{
		if(file.fileType != AvailFileType) return
		serverStarter.ensureServerStarted(AvailLspServerDescriptor(project))
	}
}
