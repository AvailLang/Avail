/*
 * Avail.kt
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

package avail.anvil.utilities

import avail.anvil.Anvil
import avail.anvil.Anvil.defaults
import avail.anvil.Anvil.userHome
import avail.AvailRuntime
import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoots
import avail.builder.RenamesFileParser
import avail.files.FileManager
import avail.persistence.cache.Repository

////////////////////////////////////////////////////////////////////////////////
//                         Avail runtime management.                          //
////////////////////////////////////////////////////////////////////////////////

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
	moduleRootsPath: String = defaults.defaultModuleRootsPath,
	renamesFileBody: String = defaults.defaultRenamesFileBody,
	fileManager: FileManager = defaults.defaultFileManager
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
	moduleNameResolver: ModuleNameResolver = defaults.defaultModuleNameResolver,
	fileManager: FileManager = defaults.defaultFileManager
): AvailRuntime = AvailRuntime(moduleNameResolver, fileManager)

////////////////////////////////////////////////////////////////////////////////
//                       Avail module name resolution.                        //
////////////////////////////////////////////////////////////////////////////////
class Defaults
{
	/**
	 * The default [ModuleNameResolver], using the local filesystem to resolve
	 * references to Avail modules and resources.
	 */
	val defaultModuleNameResolver by lazy { defaultRenamesFileParser.parse() }

	/**
	 * The default [FileManager], using the local filesystem to locate Avail modules
	 * and resources.
	 */
	val defaultFileManager get() = FileManager()

	/**
	 * The default [renames&#32;file] body. The document is empty, specifying no
	 * renames.
	 */
	val defaultRenamesFileBody = ""

	/**
	 * The default module roots path, trying the following in order:
	 *
	 * 1. The system property `avail.roots`.
	 * 1. The environment variable `$AVAIL_ROOTS`.
	 * 1. The environment-based location `$AVAIL_HOME/src/avail`.
	 * 1. The Avail components bundled with Anvil, obtained via the system property
	 *    `java.class.path`.
	 *
	 * Where `$AVAIL_ROOTS` is the standard module roots path environment variable
	 * and `$AVAIL_HOME` is the standard Avail home environment variable.
	 */
	val defaultModuleRootsPath: String =
		buildString {
			val system = System.getProperty("avail.roots")
			if (system !== null)
			{
				append(system)
				return@buildString
			}
			val availRoots = System.getenv("AVAIL_ROOTS")
			if (availRoots !== null)
			{
				append(availRoots)
				return@buildString
			}
			append("avail=")
			val availHome = System.getenv("AVAIL_HOME")
			if (availHome !== null)
			{
				append(availHome)
				append("/src/avail")
				return@buildString
			}
			val classPath = System.getProperty("java.class.path")
			if (classPath !== null)
			{
				val bundled = classPath.splitToSequence(":").first {
					it.contains("avail-stdlib", ignoreCase = true)
				}
				append("jar:")
				append(bundled)
				return@buildString
			}
			error("cannot determine default module roots path")
		}

	/**
	 * The default [ModuleRoots], using the default module roots path.
	 */
	val defaultModuleRoots =
		ModuleRoots(
			defaultFileManager,
			defaultModuleRootsPath)
		{
			if (it.isNotEmpty())
			{
				System.err.println("module root resolution failure: $it")
			}
		}

	/**
	 * The default [RenamesFileParser], using an empty renames file.
	 */
	val defaultRenamesFileParser =
		RenamesFileParser(
			defaultRenamesFileBody.reader(),
			defaultModuleRoots)

	/**
	 * The default directory where Avail [Repository]s are written to.
	 */
	val defaultRepositoryPath: String =
		"$userHome/${Anvil.REPOS_DEFAULT}"
}
