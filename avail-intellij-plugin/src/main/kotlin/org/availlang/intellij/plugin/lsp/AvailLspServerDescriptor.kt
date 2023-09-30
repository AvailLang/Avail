package org.availlang.intellij.plugin.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.availlang.intellij.plugin.language.AvailFileType
import org.eclipse.lsp4j.launch.LSPLauncher

/**
 * The Avail [ProjectWideLspServerDescriptor] used to start and communicate with
 * the
 * [Avail Language Server](https://microsoft.github.io/language-server-protocol/).
 *
 * @author Richard Arriaga
 */
class AvailLspServerDescriptor(
	project: Project
): ProjectWideLspServerDescriptor(project, "Avail")
{
	override fun createCommandLine(): GeneralCommandLine
	{
//		LSPLauncher.createServerLauncher(AvailLanguageServer(),
//			System.`in`,
//			System.out);
		TODO("Not yet implemented")
	}

	override fun isSupportedFile(file: VirtualFile): Boolean =
		file.fileType == AvailFileType
}
