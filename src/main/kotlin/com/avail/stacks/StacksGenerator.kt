/*
 * StacksGenerator.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.stacks

import com.avail.AvailRuntime
import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.compiler.ModuleHeader
import com.avail.compiler.ModuleImport
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.tokens.CommentTokenDescriptor
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.stacks.module.CommentsModule
import com.avail.utility.IO
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import kotlin.collections.set

/**
 * An Avail documentation generator.  It takes tokenized method/class comments
 * in .avail files and creates navigable documentation from them.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property outputPath
 *   The [path][Path] to the output [directory][BasicFileAttributes.isDirectory]
 *   for documentation and data files.
 * @property resolver
 *   A [ModuleNameResolver] to resolve [ModuleImport]s.
 *
 * @constructor
 * Construct a new [StacksGenerator].
 *
 * @param outputPath
 *   The [path][Path] to the output [directory][BasicFileAttributes.isDirectory]
 *   for documentation and data files.
 * @param resolver
 *   A [ModuleNameResolver] to resolve [ModuleImport]s.
 * @throws IllegalArgumentException
 *   If the output path exists but does not specify a directory.
 */
class StacksGenerator @Throws(IllegalArgumentException::class) constructor(
	private val outputPath: Path,
	val resolver: ModuleNameResolver)
{
	/**
	 * The path for documentation storage as provided by the user.
	 */
	private var providedDocumentPath: Path

	/**
	 * The directory path for writing JSON Stacks data.
	 */
	private var jsonPath: Path

	/**
	 * An optional prefix to the stacks file link location in the website
	 */
	private val linkPrefix: String

	/**
	 * The location used for storing any log files such as error-logs.
	 */
	private val logPath: Path

	/**
	 * The error log file for the malformed comments.
	 */
	val errorLog: StacksErrorLog

	/**
	 * The [LinkingFileMap] is a map for all files in
	 * stacks
	 */
	private val linkingFileMap: LinkingFileMap

	/**
	 * A map of [module&#32;names][ModuleName] to a list of all the method
	 * names exported from said module
	 */
	private var moduleToComments: HashMap<String, CommentsModule>

	init
	{
		require(!(Files.exists(outputPath) && !Files.isDirectory(outputPath)))
		{ "$outputPath exists and is not a directory" }

		this.linkPrefix = "index.html#/method"
		this.linkingFileMap = LinkingFileMap()
		this.jsonPath = outputPath.resolve("json")
		this.logPath = outputPath.resolve("logs")
		this.errorLog = StacksErrorLog(logPath)

		this.providedDocumentPath = outputPath

		this.moduleToComments = HashMap(50)
	}

	/**
	 * Inform the [generator][StacksGenerator] about the documentation and
	 * linkage of a [module][ModuleDescriptor].
	 *
	 * @param header
	 *   The [header][ModuleHeader] of the module.
	 * @param commentTokens
	 *   The complete [collection][TupleDescriptor] of
	 *   [comments][CommentTokenDescriptor] produced for the given module.
	 */
	@Synchronized fun add(
		header: ModuleHeader,
		commentTokens: A_Tuple)
	{
		val commentsModule = CommentsModule(
			header, commentTokens, errorLog, resolver,
			moduleToComments, linkingFileMap,
			linkPrefix)
		updateModuleToComments(commentsModule)
	}

	/**
	 * Update moduleToComments with a new [CommentsModule].
	 *
	 * @param commentModule
	 *   A new [CommentsModule] to add to moduleToComments;
	 */
	private fun updateModuleToComments(
		commentModule: CommentsModule)
	{
		moduleToComments[commentModule.moduleName] = commentModule
	}

	/**
	 * Generate complete Stacks documentation.
	 *
	 * @param runtime
	 *   An [runtime][AvailRuntime].
	 * @param outermostModule
	 *   The outermost [module][ModuleDescriptor] for the generation request.
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	@Synchronized @Throws(IOException::class)
	fun generate(runtime: AvailRuntime, outermostModule: ModuleName)
	{
		val closeHTML = ByteBuffer.wrap(
			String.format(
				"</ol>\n<h4>Error Count: %d</h4>\n</body>\n</html>",
				errorLog.errorCount())
				.toByteArray(UTF_8))

		errorLog.addLogEntry(closeHTML, 0)

		val outerMost = moduleToComments[outermostModule.qualifiedName]!!

		try
		{
			Files.createDirectories(outputPath)
			Files.createDirectories(providedDocumentPath)
			Files.createDirectories(jsonPath)
		}
		catch (e: IOException)
		{
			// TODO Auto-generated catch block
			e.printStackTrace()
		}

		val fileToOutPutCount = outerMost.calculateFinalImplementationGroupsMap(
			linkingFileMap,
			outputPath, runtime, "/library-documentations")

		if (fileToOutPutCount > 0)
		{
			val synchronizer = StacksSynchronizer(fileToOutPutCount)

			// JSON files
			moduleToComments[outermostModule.qualifiedName]!!
				.writeMethodsToJSONFiles(
					providedDocumentPath,
					synchronizer,
					runtime,
					linkingFileMap,
					errorLog)
			synchronizer.waitForWorkUnitsToComplete()
		}

		linkingFileMap.writeInternalLinksToJSON(
			jsonPath.resolve("internalLink.json"))
		linkingFileMap.writeCategoryLinksToJSON(
			jsonPath.resolve("categories.json"))
		linkingFileMap.writeCategoryDescriptionToJSON(
			jsonPath.resolve("categoriesDescriptions.json"), errorLog)
		linkingFileMap.writeModuleCommentsToJSON(
			jsonPath.resolve("moduleDescriptions.json"), errorLog)
		IO.close(errorLog.file()!!)

		clear()
	}

	/**
	 * Clear all internal data structures and reinitialize this
	 * [StacksGenerator] for subsequent usage.
	 */
	@Synchronized fun clear()
	{
		moduleToComments.clear()
		linkingFileMap.clear()
	}

	companion object
	{
		/**
		 * The default path to the output directory for module-oriented
		 * documentation and data files.
		 */
		val defaultDocumentationPath: Path = Paths.get("resources/stacks")

		/**
		 * Obtain a template file and return a string of that template.
		 *
		 * @param templateFilePath
		 * The template file to obtain.
		 * @return The string contents of that file.
		 * @throws IOException
		 * If the template file could not be opened.
		 */
		@Throws(IOException::class)
		fun getOuterTemplate(templateFilePath: Path): String
		{
			val templateFile = FileInputStream(templateFilePath.toString())
			val channel = templateFile.channel

			val buf = ByteBuffer.allocate(channel.size().toInt())

			channel.read(buf)

			IO.close(channel)
			IO.close(templateFile)

			return String(buf.array(), UTF_8)
		}
	}
}
