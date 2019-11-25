/*
 * ModuleNameResolver.kt
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

package com.avail.builder

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.ModuleDescriptor
import com.avail.persistence.IndexedRepositoryManager
import com.avail.utility.LRUCache
import java.io.File
import java.util.*

/**
 * A `ModuleNameResolver` resolves fully-qualified references to Avail
 * [modules][ModuleDescriptor] to [absolute][File.isAbsolute] [file
 * references][File].
 *
 * Assuming that the Avail module path comprises four module roots listed in the
 * order _S_, _P_,_Q_, _R_, then the following algorithm is used for resolution
 * of a fully-qualified reference _R/X/Y/Z/M_:
 *
 *  1. Obtain the canonical name _/R'/A/B/C/M'_ by applying an existing renaming
 *     rule for _/R/X/Y/Z/M_.
 *  2. If package _/R'/A/B/C_ contains a module _M'_, then capture its file
 *     reference _F_.
 *  3. If package _/R'/A/B_ contains a module _M'_, then capture its file
 *     reference _F_.
 *  4. If package _/R'/A_ contains a module _M'_, then capture its file
 *     reference _F_.
 *  5. If module root _/R_ contains a module _M'_, then capture its file
 *     reference _F_.
 *  6. If module root _/S_ contains a module _M'_, then capture its file
 *     reference _F_.
 *  7. If module root _/P_ contains a module _M'_, then capture its file
 *     reference _F_.
 *  8. If module root _/Q_ contains a module _M'_, then capture its file
 *     reference _F_.
 *  9. If the resolution succeeded and _F_ specifies a directory, then replace
 *     the resolution with _F/M'.avail_. Verify that the resolution specifies
 *     an existing regular file.
 *  10. Otherwise resolution failed.
 *
 * An instance is obtained via [RenamesFileParser.parse].
 *
 * @property moduleRoots
 *   The [Avail module roots][ModuleRoots].
 * @author Todd L Smith &lt;todd@availlang.org &gt;
 * @author Leslie Schultz &lt;leslie@availlang.org &gt;
 *
 * @constructor
 *
 * Construct a new `ModuleNameResolver`.
 *
 * @param moduleRoots
 *   The Avail [module roots][ModuleRoots].
 */
@ThreadSafe
class ModuleNameResolver constructor(val moduleRoots: ModuleRoots)
{
	/**
	 * A [map][Map] from fully-qualified module names to their canonical names.
	 */
	private val renames = LinkedHashMap<String, String>()

	/**
	 * A [cache][LRUCache] of [resolved][ResolvedModuleName], keyed by
	 * fully-qualified [module][ModuleName].
	 */
	private val resolutionCache =
		LRUCache<ModuleName, ModuleNameResolutionResult>(
			10000, 100, { privateResolve(it) })

	/** An immutable [Map] of all the module path renames. */
	val renameRules: Map<String, String>
		get () = Collections.unmodifiableMap(renames)

	/** An immutable [Map] from module rename targets to sources. */
	val renameRulesInverted: Map<String, List<String>> by lazy {
		renames.entries.groupBy({ it.value }) { it.key }
	}

	/**
	 * Does the resolver have a transformation rule for the specified
	 * fully-qualified module name?
	 *
	 * @param modulePath
	 *   A fully-qualified module name.
	 * @return
	 *   `true` if there is a rule to transform the fully-qualified module name
	 *   into another one, `false` otherwise.
	 */
	internal fun hasRenameRuleFor(modulePath: String) =
		renames.containsKey(modulePath)

	/**
	 * Remove all rename rules.
	 */
	fun clearRenameRules() = renames.clear()

	/**
	 * Add a rule to translate the specified fully-qualified module name.
	 *
	 * @param modulePath
	 *   A fully-qualified module name.
	 * @param substitutePath
	 *   The canonical name.
	 */
	fun addRenameRule(modulePath: String, substitutePath: String)
	{
		assert(!renames.containsKey(modulePath))
		renames[modulePath] = substitutePath
	}

	/**
	 * Answer the canonical name that should be used in place of the
	 * fully-qualified [module name][ModuleName].
	 *
	 * @param qualifiedName
	 *   A fully-qualified [module name][ModuleName].
	 * @return
	 *   The canonical name that should be used in place of the fully-qualified
	 *   [module name][ModuleName].
	 */
	private fun canonicalNameFor(qualifiedName: ModuleName): ModuleName
	{
		val substitute = renames[qualifiedName.qualifiedName]
		return if (substitute != null)
		{
			ModuleName(substitute, true)
		}
		else qualifiedName
	}

	/**
	 * Clear all cached module resolutions.  Also release all file locks on
	 * repositories and close them.
	 */
	fun clearCache() = resolutionCache.clear()

	/**
	 * Release all external locks and handles associated with this resolver.  It
	 * must not be used again.
	 */
	fun destroy()
	{
		moduleRoots.forEach { root -> root.repository.close() }
	}

	/**
	 * Actually resolve the qualified module name.  This is not `public` to
	 * ensure clients always go through the cache.
	 *
	 * @param qualifiedName
	 *   The qualified name of the module.
	 * @return
	 *   A [ModuleNameResolutionResult] indicating the result of the attempted
	 *   resolution.
	 */
	private fun privateResolve(
		qualifiedName: ModuleName): ModuleNameResolutionResult
	{
		// Attempt to look up the fully-qualified name in the map of renaming
		// rules. Apply the rule if it exists.
		var canonicalName = canonicalNameFor(qualifiedName)

		// If the root cannot be resolved, then neither can the module.
		val enclosingRoot = canonicalName.rootName
		var root: ModuleRoot? = moduleRoots.moduleRootFor(enclosingRoot)
			?: return ModuleNameResolutionResult(
				UnresolvedRootException(
					null, qualifiedName.localName, enclosingRoot))

		val components = canonicalName.packageName.split("/")
		assert(components.size > 1)
		assert(components[0].isEmpty())

		val nameStack = LinkedList<String>()
		nameStack.addLast("/$enclosingRoot")
		var pathStack: Deque<File>? = null

		// If the source directory is available, then build a search stack of
		// trials at ascending tiers of enclosing packages.
		var sourceDirectory = root!!.sourceDirectory
		if (sourceDirectory != null)
		{
			pathStack = LinkedList()
			pathStack.addLast(sourceDirectory)
		}
		for (index in 2 until components.size)
		{
			assert(components[index].isNotEmpty())
			nameStack.addLast(String.format(
				"%s/%s",
				nameStack.peekLast(),
				components[index]))
			if (sourceDirectory != null)
			{
				pathStack!!.addLast(File(
					pathStack.peekLast(),
					components[index] + availExtension))
			}
		}

		// If the source directory is available, then search the file system.
		val checkedPaths = ArrayList<ModuleName>()
		var repository: IndexedRepositoryManager? = null
		var sourceFile: File? = null
		if (sourceDirectory != null)
		{
			assert(!pathStack!!.isEmpty())
			// Explore the search stack from most enclosing package to least
			// enclosing.
			while (!pathStack.isEmpty())
			{
				canonicalName = ModuleName(
					nameStack.removeLast(),
					canonicalName.localName,
					canonicalName.isRename)
				checkedPaths.add(canonicalName)
				val trial = File(filenameFor(
					pathStack.removeLast().path,
					canonicalName.localName))
				if (trial.exists())
				{
					repository = root.repository
					sourceFile = trial
					break
				}
			}
		}

		// If resolution failed, then one final option is available: search the
		// other roots.
		if (repository == null)
		{
			for (rootName in moduleRoots.rootNames)
			{
				if (rootName != enclosingRoot)
				{
					canonicalName = ModuleName(
						String.format(
							"/%s/%s", rootName, canonicalName.localName),
						canonicalName.isRename)
					checkedPaths.add(canonicalName)
					root = moduleRoots.moduleRootFor(rootName)
					assert(root != null)
					sourceDirectory = root!!.sourceDirectory
					if (sourceDirectory != null)
					{
						val trial = File(
							sourceDirectory,
							canonicalName.localName + availExtension)
						if (trial.exists())
						{
							repository = root.repository
							sourceFile = trial
							break
						}
					}
				}
			}
		}

		// We found a candidate.
		if (repository != null)
		{
			// If the candidate is a package, then substitute
			// the package representative.
			if (sourceFile!!.isDirectory)
			{
				sourceFile = File(
					sourceFile,
					canonicalName.localName + availExtension)
				canonicalName = ModuleName(
					canonicalName.qualifiedName,
					canonicalName.localName,
					canonicalName.isRename)
				if (!sourceFile.isFile)
				{
					// Alas, the package representative did not exist.
					return ModuleNameResolutionResult(
						UnresolvedModuleException(
							null, qualifiedName.localName, checkedPaths))
				}
			}
			return ModuleNameResolutionResult(
				ResolvedModuleName(
					canonicalName, moduleRoots, canonicalName.isRename))
		}

		// Resolution failed.
		return ModuleNameResolutionResult(
			UnresolvedModuleException(
				null, qualifiedName.localName, checkedPaths))
	}

	/**
	 * This class was created so that, upon an [UnresolvedDependencyException],
	 * the [ModuleNameResolver] could bundle information about the different
	 * paths checked for the missing file into the exception itself.
	 */
	internal class ModuleNameResolutionResult
	{
		/** The module that was successfully resolved, or null if not found.  */
		internal val resolvedModule: ResolvedModuleName?

		/** An exception if the module was not found, or null if it was.  */
		internal val exception: UnresolvedDependencyException?

		/**
		 * Whether the resolution produced a [ResolvedModuleName], rather than
		 * an exception.
		 */
		val isResolved get() = resolvedModule != null

		/**
		 * Construct a new `ModuleNameResolutionResult`, upon successful
		 * resolution, with the [resolved module][ResolvedModuleName].
		 *
		 * @param resolvedModule
		 *   The module that was successfully resolved.
		 */
		constructor(resolvedModule: ResolvedModuleName)
		{
			this.resolvedModule = resolvedModule
			this.exception = null
		}

		/**
		 * Construct a new `ModuleNameResolutionResult`, upon an unsuccessful
		 * resolution, with an [UnresolvedDependencyException] containing the
		 * paths that did not have the missing module.
		 *
		 * @param e
		 *   The [UnresolvedDependencyException] that was thrown while resolving
		 *   a module.
		 */
		constructor(e: UnresolvedDependencyException)
		{
			this.resolvedModule = null
			this.exception = e
		}
	}

	/**
	 * Resolve a fully-qualified module name (as a reference to the [local
	 * name][ModuleName.localName] made from within the
	 * [package][ModuleName.packageName]).
	 *
	 * @param qualifiedName
	 *   A fully-qualified [module name][ModuleName].
	 * @param dependent
	 *   The name of the module that requires this resolution, if any.
	 * @return
	 *   A [resolved module name][ResolvedModuleName] if the resolution was
	 *   successful.
	 * @throws UnresolvedDependencyException
	 *   If resolution fails.
	 */
	@Throws(UnresolvedDependencyException::class)
	fun resolve(
		qualifiedName: ModuleName,
		dependent: ResolvedModuleName? = null): ResolvedModuleName
	{
		val result = resolutionCache[qualifiedName]
		if (!result.isResolved)
		{
			// The resolution failed.
			if (dependent != null)
			{
				result.exception!!.referringModuleName = dependent
			}
			throw result.exception!!
		}
		return result.resolvedModule!!
	}

	/**
	 * Commit all dirty repositories.
	 */
	fun commitRepositories()
	{
		for (root in moduleRoots)
		{
			root.repository.commit()
		}
	}

	companion object
	{
		/**
		 * The standard extension for Avail [module][ModuleDescriptor] source
		 * files.
		 */
		@JvmStatic val availExtension = ".avail"

		/**
		 * Trivially translate the specified package name and local module name
		 * into a filename.
		 *
		 * @param packageName
		 *   A package name.
		 * @param localName
		 *   A local module name.
		 * @return
		 *   A filename that specifies the module within the package.
		 */
		internal fun filenameFor(packageName: String, localName: String) =
			"$packageName/$localName$availExtension"
	}
}
