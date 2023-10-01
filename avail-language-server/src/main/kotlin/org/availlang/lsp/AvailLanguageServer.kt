package org.availlang.lsp

import avail.AvailRuntime
import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoots
import avail.builder.RenamesFileParser
import avail.files.FileManager
import avail.persistence.cache.Repositories
import avail.utility.IO
import org.availlang.artifact.environment.project.AvailProject
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

/**
 * The Avail [LanguageServer] that conforms to the
 * [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
 *
 * @author Richard Arriaga
 */
class AvailLanguageServer: LanguageServer
{
	/**
	 * The [AvailProject] that describes the project being accessed in IntelliJ.
	 * This should be set in the [initialize] function call using
	 * [InitializeParams.workspaceFolders] to setup the [AvailProject].
	 */
	lateinit var availProject: AvailProject
		private set

	lateinit var runtime: AvailRuntime
		private set

	override fun initialize(
		params: InitializeParams
	): CompletableFuture<InitializeResult>
	{
		val workspaceFolder = params.workspaceFolders.firstOrNull()
			?: run {
				TODO("initialize failure not yet implemented")
			}
		val projectFileName = workspaceFolder.name
		val projectRoot = File(workspaceFolder.uri).absolutePath
		val project =
			AvailProject.from("$projectRoot/$projectFileName")
		availProject = project
		val repositoryDirectory =
			File(project.repositoryLocation.fullPathNoPrefix)
		val fileManager = FileManager()
		Repositories.setDirectoryLocation(repositoryDirectory)
		val runtimeReady = Semaphore(0)
		val rootsString = project.availProjectRoots
			.joinToString(";") { it.modulePath }
		val rootResolutionStart = System.currentTimeMillis()
		val failedResolutions = mutableListOf<String>()
		val semaphore = Semaphore(0)
		val roots = ModuleRoots(fileManager, rootsString) { fails ->
			failedResolutions.addAll(fails)
			semaphore.release()
		}
		semaphore.acquireUninterruptibly()
		val resolutionTime = System.currentTimeMillis() - rootResolutionStart
		// TODO log resolutionTime
		lateinit var resolver: ModuleNameResolver
		thread(name = "Parse renames") {
			var reader: Reader? = null
			try
			{
				val renames = System.getProperty("availRenames", null)
				reader = when (renames)
				{
					null -> StringReader("")
					// Load renames from file specified on the command line.
					else -> BufferedReader(
						InputStreamReader(
							FileInputStream(File(renames)),
							StandardCharsets.UTF_8))
				}
				val renameParser = RenamesFileParser(reader, roots)
				resolver = renameParser.parse()
			}
			finally
			{
				IO.closeIfNotNull(reader)
			}
			runtime = AvailRuntime(resolver, fileManager)
			runtimeReady.release()
		}
		runtimeReady.acquire()

		TODO("initialize CompletableFuture not yet implemented")
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
