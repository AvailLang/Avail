/*
 * DocumentationTracer.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.builder

import com.avail.AvailTask
import com.avail.compiler.ModuleHeader
import com.avail.compiler.problems.Problem
import com.avail.compiler.problems.ProblemHandler
import com.avail.compiler.problems.ProblemType.INTERNAL
import com.avail.compiler.problems.ProblemType.TRACE
import com.avail.descriptor.fiber.FiberDescriptor.Companion.loaderPriority
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.tokens.CommentTokenDescriptor
import com.avail.descriptor.tuples.A_Tuple
import com.avail.error.ErrorCode
import com.avail.persistence.IndexedFile
import com.avail.persistence.IndexedFile.Companion.validatedBytesFrom
import com.avail.persistence.cache.Repository.ModuleVersion
import com.avail.persistence.cache.Repository.ModuleVersionKey
import com.avail.serialization.Deserializer
import com.avail.serialization.MalformedSerialStreamException
import com.avail.stacks.StacksGenerator
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Used for parallel documentation generation.
 *
 * @property availBuilder
 *   The [AvailBuilder] for which to generate documentation.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `DocumentationTracer`.
 *
 * @param availBuilder
 *   The [AvailBuilder] for which to generate documentation.
 * @param documentationPath
 *   The [path][Path] to the output [directory][BasicFileAttributes.isDirectory]
 *   for documentation and data files.
 */
internal class DocumentationTracer constructor(
	val availBuilder: AvailBuilder,
	documentationPath: Path)
{
	/**
	 * The [Stacks&#32;documentation&#32;generator][StacksGenerator].
	 */
	private val generator: StacksGenerator = StacksGenerator(
		documentationPath, availBuilder.runtime.moduleNameResolver)

	/**
	 * Get the [module&#32;version][ModuleVersion] for the
	 * [named][ResolvedModuleName] [module][ModuleDescriptor].
	 *
	 * @param moduleName
	 *   A resolved module name.
	 * @param withVersion
	 *   A function that accepts A module version, or `null` if no version was
	 *   available.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and a `nullable` [Throwable].
	 */
	private fun getVersion(
		moduleName: ResolvedModuleName,
		withVersion: (ModuleVersion?) -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		val repository = moduleName.repository
		val archive = repository.getArchive(moduleName.rootRelativeName)
		archive.digestForFile(
			moduleName,
			false,
			{ digest ->
				val versionKey = ModuleVersionKey(moduleName, digest)
				withVersion(archive.getVersion(versionKey))
			},
			failureHandler)
	}

	/**
	 * Load [comments][CommentTokenDescriptor] for the
	 * [named][ResolvedModuleName] [module][ModuleDescriptor] into the
	 * [Stacks&#32;documentation&#32;generator][StacksGenerator].
	 *
	 * @param moduleName
	 *   A module name.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 * @param completionAction
	 *   What to do when comments have been loaded for the named module (or an
	 *   error occurs).
	 */
	private fun loadComments(
		moduleName: ResolvedModuleName,
		problemHandler: ProblemHandler,
		completionAction: ()->Unit)
	{
		getVersion(moduleName,
			{ version ->
				if (version?.comments === null)
				{
					val problem = object : Problem(
						moduleName,
						1,
						0,
						TRACE,
						"Module \"{0}\" should have been compiled already",
						moduleName)
					{
						override fun abortCompilation()
						{
							availBuilder.stopBuildReason = "Comment loading failed"
							completionAction()
						}
					}
					problemHandler.handle(problem)
					return@getVersion
				}
				val tuple: A_Tuple?
				try
				{
					val bytes = version.comments!!
					val input = validatedBytesFrom(bytes)
					val deserializer = Deserializer(input, availBuilder.runtime)
					tuple = deserializer.deserialize()
					assert(tuple !== null)
					assert(tuple!!.isTuple)
					val residue = deserializer.deserialize()
					assert(residue === null)
				}
				catch (e: MalformedSerialStreamException)
				{
					val problem = object : Problem(
						moduleName,
						1,
						0,
						INTERNAL,
						"Couldn''t deserialize comment tuple for module \"{0}\"",
						moduleName)
					{
						override fun abortCompilation()
						{
							availBuilder.stopBuildReason =
								"Comment deserialization failed"
							completionAction()
						}
					}
					problemHandler.handle(problem)
					return@getVersion
				}

				val header: ModuleHeader
				try
				{
					val input = validatedBytesFrom(version.moduleHeader)
					val deserializer = Deserializer(input, availBuilder.runtime)
					header = ModuleHeader(moduleName)
					header.deserializeHeaderFrom(deserializer)
				}
				catch (e: MalformedSerialStreamException)
				{
					val problem = object : Problem(
						moduleName,
						1,
						0,
						INTERNAL,
						"Couldn''t deserialize header for module \"{0}\"",
						moduleName)
					{
						override fun abortCompilation()
						{
							availBuilder.stopBuildReason =
								"Module header deserialization failed when " +
									"loading comments"
							completionAction()
						}
					}
					problemHandler.handle(problem)
					return@getVersion
				}

				generator.add(header, tuple)
				completionAction()
			}) { code, ex ->
			val problem = object : Problem(
				moduleName,
				1,
				0,
				TRACE,
				"Problem getting Module \"{0}\" to load comments: {1} - {2}",
				moduleName,
				code,
				ex ?: "")
			{
				override fun abortCompilation()
				{
					availBuilder.stopBuildReason = "Comment loading failed"
					completionAction()
				}
			}
			problemHandler.handle(problem)
		}
	}

	/**
	 * Schedule a load of the [comments][CommentTokenDescriptor]
	 * for the [named][ResolvedModuleName] [modules][ModuleDescriptor].
	 *
	 * @param moduleName
	 *   A module name.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 * @param completionAction
	 *   What to do when comments have been loaded for the named module.
	 */
	private fun scheduleLoadComments(
		moduleName: ResolvedModuleName,
		problemHandler: ProblemHandler,
		completionAction: ()->Unit)
	{
		// Avoid scheduling new tasks if an exception has happened.
		if (availBuilder.shouldStopBuild)
		{
			completionAction()
			return
		}
		availBuilder.runtime.execute(loaderPriority) {
			if (availBuilder.shouldStopBuild)
			{
				// An exception has been encountered since the earlier check.
				// Exit quickly.
				completionAction()
			}
			else
			{
				loadComments(moduleName, problemHandler, completionAction)
			}
		}
	}

	/**
	 * Load the [comments][CommentTokenDescriptor] for all
	 * [modules][ModuleDescriptor] in the [AvailBuilder.moduleGraph].
	 *
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 */
	fun load(problemHandler: ProblemHandler) =
		availBuilder.moduleGraph.parallelVisit { moduleName, completionAction ->
			availBuilder.runtime.execute(
				AvailTask(loaderPriority) {
					scheduleLoadComments(
						moduleName,
						problemHandler,
						completionAction)
				})
		}

	/**
	 * Generate Stacks documentation.
	 *
	 * @param target
	 *   The outermost [module][ModuleDescriptor] for the generation request.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 */
	fun generate(target: ModuleName, problemHandler: ProblemHandler)
	{
		try
		{
			generator.generate(availBuilder.runtime, target)
		}
		catch (e: Exception)
		{
			val problem = object : Problem(
				target,
				1,
				0,
				TRACE,
				"Could not generate Stacks documentation: {0}",
				e.localizedMessage)
			{
				override fun abortCompilation()
				{
					availBuilder.stopBuildReason =
						"Unable to generate Stacks documentation"
				}
			}
			problemHandler.handle(problem)
		}
	}
}
