/*
 * AvailLanguageServer.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
