/*
 * AvailContext.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

import com.avail.AvailRuntime
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoots
import com.avail.builder.RenamesFileParser
import com.avail.files.FileManager

// This file contains functionality to set up an Avail environment.

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
	val fileManager: FileManager = FileManager()
)
{
	/** The [AvailRuntime] for this project. */
	val runtime by lazy {
		createAvailRuntime(
			moduleRootsPath = moduleRootsPath,
			renamesFileBody = renamesFileBody,
			fileManager = fileManager)
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
): AvailRuntime
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
	fileManager: com.avail.files.FileManager
): AvailRuntime = AvailRuntime(moduleNameResolver, fileManager)
