/*
 * AvailContext.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package org.availlang.sample

import avail.AvailRuntime
import avail.builder.AvailBuilder
import avail.builder.ModuleName
import avail.descriptor.module.ModuleDescriptor
import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoots
import avail.builder.RenamesFileParser
import avail.compiler.CompilerProgressReporter
import avail.compiler.GlobalProgressReporter
import avail.compiler.problems.Problem
import avail.compiler.problems.SimpleProblemHandler
import avail.files.FileManager

// This file contains functionality to set up and manage an Avail environment.

/**
 * A project aggregates and encapsulates all state pertinent to a user
 * application or library.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property name
 *   The name of the project.
 * @property moduleRootsPath
 *   The [module&#32;roots][ModuleRoots] path for the project.
 * @property renamesFileBody
 *   The content of a [renames&#32;file][RenamesFileParser] for the project.
 * @property fileManager
 *   The [FileManager] for locating all Avail modules and resources associated
 *   with the project.
 */
data class Project constructor(
	val name: String,
	val moduleRootsPath: String,
	val renamesFileBody: String = "",
	val fileManager: FileManager = FileManager())
{
	/** The [AvailRuntime] for this project. */
	val runtime by lazy {
		createAvailRuntime(
			moduleRootsPath = moduleRootsPath,
			renamesFileBody = renamesFileBody,
			fileManager = fileManager)
	}
	/** The [AvailBuilder] used to build Avail [Modules][ModuleDescriptor]. */
	val builder: AvailBuilder by lazy {
		AvailBuilder(runtime)
	}

	/**
	 * Build the indicated Avail [module][ModuleDescriptor].
	 *
	 * @param qualifiedModuleName
	 *   The [fully qualified module name][ModuleName.qualifiedName].
	 * @param done
	 *   The lambda to run after build is complete.
	 */
	fun build (qualifiedModuleName: String, done: () -> Unit)
	{
		val resolvedModuleName =
			builder.runtime.moduleNameResolver.resolve(
				ModuleName(qualifiedModuleName), null)
		println("Target module for compilation: $qualifiedModuleName")

		val progressReporter: CompilerProgressReporter =
			{   moduleName,
				moduleSizeInBytes,
				currentByteProcessing,
				lineNumber,
				phrase ->

				// Add behavior to present compiler progress on module currently
				// being compiled. This can be used to present the compilation
				// progress on the currently compiling file.
			}
		val globalProgressReporter: GlobalProgressReporter =
			{ bytesCompiled, totalBytesToCompile ->
				// Add behavior to present total compiler progress on all
				// modules being compiled in the course of compiling target
				// module. This can be used to show a counter counting up:
				// "$bytesCompiled / $totalBytesToCompile"
			}
		println("Compiling...")
		builder.buildTargetThen(
			resolvedModuleName,
			progressReporter,
			globalProgressReporter,
			object : SimpleProblemHandler
			{
				override fun handleGeneric(
					problem: Problem,
					decider: (Boolean) -> Unit)
				{
					builder.stopBuildReason = "Build failed"
					val problemText = with(problem) {
						val adjustedLine = lineNumber - 5
						"$moduleName, line $adjustedLine:\n$this"
					}
					System.err.println(problemText)
					decider(false)
				}
			}
		) { done() }
	}
}

/**
 * Create an [AvailRuntime].
 *
 * @param moduleRootsPath
 *   The [module&#32;roots][ModuleRoots] path.
 * @param renamesFileBody
 *   The content of a [renames&#32;file][RenamesFileParser].
 * @param fileManager
 *   The [FileManager] for locating all Avail modules and resources.
 */
fun createAvailRuntime (
	moduleRootsPath: String,
	renamesFileBody: String = "",
	fileManager: FileManager = FileManager()
) : AvailRuntime
{
	val moduleRootResolutionErrors = mutableListOf<String>()
	val moduleRoots = ModuleRoots(fileManager, moduleRootsPath) {
		moduleRootResolutionErrors.addAll(it)
	}
	val renamesFileParser = RenamesFileParser(
		renamesFileBody.reader(), moduleRoots)
	val moduleNameResolver = renamesFileParser.parse()
	return createAvailRuntime(moduleNameResolver, fileManager)
}

/**
 * Create an [AvailRuntime].
 *
 * @param moduleNameResolver
 *   The [ModuleNameResolver] for resolving all module names.
 * @param fileManager
 *   The [FileManager] for locating all Avail modules and resources.
 * @return
 *   The new [AvailRuntime].
 */
fun createAvailRuntime (
	moduleNameResolver: ModuleNameResolver,
	fileManager: FileManager
) : AvailRuntime = AvailRuntime(moduleNameResolver, fileManager)
