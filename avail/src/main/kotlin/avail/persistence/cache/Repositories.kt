/*
 * Repositories.kt
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

package avail.persistence.cache

import avail.builder.ModuleRoot
import java.io.File
import java.util.Collections.synchronizedMap

/**
 * {@code Repositories} manages all the system repositories. `Repositories`
 * are stored in the user home directory (`System.getProperty("user.home")`)
 * in the directory `.avail/repositories`. If the repositories directory does
 * not exist it will be created. Additionally, if the .avail directory does not
 * exist, it will be created.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object Repositories
{
	/**
	 * The file extension for a repo.
	 */
	private const val repositoryExtension = "repo"

	/**
	 * The directory in which [repositories][Repository] are created/found.
	 */
	var directory: File

	init
	{
		// Initialize the repositories directory. If it doesn't exist it will
		// be created
		val repositoriesPath = System.getProperty("avail.repositories") ?: run {
			val home = System.getProperty("user.home")
			"$home/.avail/repositories/"
		}
		val repos = File(repositoriesPath)
		repos.mkdirs()
		assert(repos.isDirectory) { "$repositoriesPath must be a directory" }
		directory = repos
	}

	/**
	 * Switch to using a different directory for finding/creating repository
	 * files. This has no effect on existing open repositories, but is useful
	 * prior to running tests.
	 */
	fun setDirectoryLocation(repositoriesPath: File)
	{
		directory = repositoriesPath
	}

	/**
	 * The map from [Repository.rootName] to the corresponding [Repository].
	 */
	private val repositories =
		synchronizedMap(mutableMapOf<String, Repository>())

	operator fun get(name: String): Repository? = repositories[name]

	/**
	 * Add a [Repository] for the given [ModuleRoot].
	 *
	 * @param root
	 *   The [ModuleRoot] to add a repo for.
	 */
	fun addRepository (root: ModuleRoot)
	{
		repositories[root.name] = Repository(
			root.name,
			File("${directory.absolutePath}/${root.name}.$repositoryExtension"))
	}

	/**
	 * Delete the [Repository] for the given [Repository.rootName].
	 *
	 * @param rootName
	 *   The name of the repo that should be removed.
	 */
	fun deleteRepository (rootName: String)
	{
		repositories.remove(rootName)
	}

	/**
	 * [Clear][Repository.clear] all [Repository]s in [Repositories].
	 */
	fun clearAllRepositories ()
	{
		repositories.values.forEach(Repository::clear)
	}

	/**
	 * [Clear][Repository.clear] the [Repository] for the given
	 * [Repository.rootName].
	 *
	 * @param rootName
	 *   The name of the repo that should be cleared.
	 */
	fun clearRepositoryFor (rootName: String)
	{
		repositories[rootName]?.clear()
	}

	/**
	 * [Close][Repository.close] each [Repository] in [Repositories].
	 */
	fun closeAllRepositories ()
	{
		repositories.values.forEach(Repository::close)
	}

	/**
	 * [Close][Repository.close] each [Repository] in [Repositories], then
	 * completely remove them all.
	 */
	fun closeAndRemoveAllRepositories ()
	{
		repositories.values.forEach(Repository::close)
		repositories.clear()
	}
}
