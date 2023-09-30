package org.availlang.lsp

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

/**
 * The Avail [LanguageServer] that conforms to the
 * [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
 *
 * @author Richard Arriaga
 */
class AvailLanguageServer: LanguageServer
{
	override fun initialize(
		params: InitializeParams?
	): CompletableFuture<InitializeResult>
	{
		TODO("Not yet implemented")
	}

	override fun shutdown(): CompletableFuture<Any>
	{
		TODO("Not yet implemented")
	}

	override fun exit()
	{
		TODO("Not yet implemented")
	}

	override fun getTextDocumentService(): TextDocumentService
	{
		TODO("Not yet implemented")
	}

	override fun getWorkspaceService(): WorkspaceService
	{
		TODO("Not yet implemented")
	}
}
