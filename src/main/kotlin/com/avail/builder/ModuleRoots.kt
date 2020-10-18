/*
 * ModuleRoots.kt
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

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.files.FileManager
import com.avail.persistence.cache.Repositories
import com.avail.resolver.ModuleRootResolver
import com.avail.resolver.ModuleRootResolverRegistry
import com.avail.utility.json.JSONWriter
import java.net.URI
import java.util.Collections.unmodifiableSet
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * `ModuleRoots` encapsulates the Avail [module][ModuleDescriptor] path. The
 * Avail module path specifies bindings between *logical root names* and
 * [locations][ModuleRoot] of Avail modules. A logical root name should
 * typically belong to a vendor of Avail modules, ergo a domain name or
 * registered trademark suffices nicely.
 *
 * The format of an Avail module path is described by the following simple
 * grammar:
 *
 * ```
 * modulePath ::= binding ++ ";" ;
 * binding ::= logicalRoot "=" objectRepository ("," sourceDirectory) ;
 * logicalRoot ::= [^=;]+ ;
 * objectRepository ::= [^;]+ ;
 * sourceDirectory ::= [^;]+ ;
 * ```
 *
 * `logicalRoot` represents a logical root name. `objectRepository` represents
 * the absolute path of a binary module repository. `sourceDirectory` represents
 * the absolute path of a package, i.e., a directory containing source modules,
 * and may be sometimes be omitted (e.g., when compilation is not required).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property fileManager
 *   The associated [FileManager].
 *
 * @constructor
 *
 * Construct a new `ModuleRoots` from the specified Avail roots path.
 *
 * @param fileManager
 *   The associated [FileManager].
 * @param modulePath
 *   An Avail [module][ModuleDescriptor] path.
 * @param withFailures
 *   A lambda that accepts [List] of the string [ModuleRoot] [URI]s that failed
 *   to [resolve][ModuleRootResolver.resolve].
 * @throws IllegalArgumentException
 *   If the Avail [module][ModuleDescriptor] path is malformed.
 */
@ThreadSafe
class ModuleRoots constructor(
	val fileManager: FileManager,
	modulePath: String,
	withFailures: (List<String>) -> Unit) : Iterable<ModuleRoot>
{
	/**
	 * A [map][Map] from logical root names to [module&#32;root][ModuleRoot]s.
	 */
	private val rootMap = LinkedHashMap<String, ModuleRoot>()

	/**
	 * The Avail [module][ModuleDescriptor] path.
	 */
	val modulePath: String by lazy {
		val builder = StringBuilder(200)
		var first = true
		for ((_, root) in rootMap)
		{
			if (!first)
			{
				builder.append(";")
			}
			builder.append(root.name)
			builder.append("=")
			builder.append(root.repository.fileName.path)
			val resolver = root.resolver
			builder.append(",")
			builder.append(resolver.uri.toString())
			first = false
		}
		builder.toString()
	}

	/**
	 * Parse the Avail [module][ModuleDescriptor] path into a [map][Map] of
	 * logical root names to [module&#32;root][ModuleRoot]s.
	 *
	 * @param modulePath
	 *   The module roots path string.
	 * @param withFailures
	 *   A lambda that accepts [List] of the string [ModuleRoot] [URI]s that failed
	 *   to [resolve][ModuleRootResolver.resolve].
	 * @throws IllegalArgumentException
	 *   If any component of the Avail [module][ModuleDescriptor] path is
	 *   invalid.
	 */
	private fun parseAvailModulePathThen(
		modulePath: String, withFailures: (List<String>) -> Unit)
	{
		clearRoots()
		// Root definitions are separated by semicolons.
		val components =
			if (modulePath.isEmpty())
			{
				listOf()
			}
			else
			{
				modulePath.split(";")
			}
		val workCount = AtomicInteger(components.size)
		val failures = mutableListOf<String>()
		val lock = ReentrantLock()
		for (component in components)
		{
			// An equals separates the root name from its paths.
			val binding = component.split("=")
			require(binding.size == 2) {
				"Bad module root location setting: $component"
			}

			// A comma separates the repository path from the source directory
			// path.
			val rootName = binding[0]
			val location = binding[1]

			val rootUri = URI(location)
			val resolver =
				ModuleRootResolverRegistry.createResolver(
					rootName,
					rootUri,
					fileManager)

			resolver.resolve(
				{
					synchronized(lock)
					{
						addRoot(resolver.moduleRoot)
						if (workCount.decrementAndGet() == 0)
						{
							withFailures(failures)
						}
					}
				}
			) { code, ex ->
				val message =
					"$code: Could not resolve module root, $rootName ($rootUri)"
				synchronized(lock)
				{
					failures.add(message)
				}
				System.err.println(message)
				ex?.printStackTrace()
				if (workCount.decrementAndGet() == 0)
				{
					withFailures(failures)
				}
			}
		}
	}

	/**
	 * Clear the [root&#32;map][rootMap].
	 */
	fun clearRoots() = rootMap.clear()

	/**
	 * Add a [root][ModuleRoot] to the [root&#32;map][rootMap].
	 *
	 * @param root
	 *   The root.
	 */
	fun addRoot(root: ModuleRoot)
	{
		rootMap[root.name] = root
		Repositories.addRepository(root)
	}

	/**
	 * Fully remove the provided [ModuleRoot.name].
	 *
	 * @param name
	 *   The name of the root to remove.
	 */
	fun removeRoot (name: String)
	{
		rootMap.remove(name)?.let {
			Repositories.deleteRepository(name)
		}
	}

	/**
	 * The logical root names in the order that they are specified in the Avail
	 * [module][ModuleDescriptor] path.
	 */
	val rootNames: Set<String> get() = unmodifiableSet(rootMap.keys)

	/**
	 * The [module&#32;roots][ModuleRoot] in the order that they are specified
	 * in the Avail [module][ModuleDescriptor] path.
	 */
	val roots get () = rootMap.values.toSet()

	override fun iterator () = roots.toSet().iterator()

	/**
	 * Answer the [module&#32;root][ModuleRoot] bound to the specified logical
	 * root name.
	 *
	 * @param rootName
	 *   A logical root name, typically something owned by a vendor of Avail
	 *   [modules][ModuleDescriptor].
	 * @return
	 *   The module root, or `null` if no such binding exists.
	 */
	fun moduleRootFor(rootName: String): ModuleRoot? = rootMap[rootName]

	init
	{
		parseAvailModulePathThen(modulePath, withFailures)
	}

	/**
	 * Write a JSON encoding of the module roots to the specified [JSONWriter].
	 *
	 * @param writer
	 *   A `JSONWriter`.
	 */
	fun writeOn(writer: JSONWriter)
	{
		writer.writeArray {
			roots.forEach { root -> write(root.name) }
		}
	}

	/**
	 * Write a JSON object whose fields are the module roots and whose values
	 * are [JSON&#32;arrays][ModuleRoot.writePathsOn] containing path
	 * information.
	 *
	 * @param writer
	 *   A [JSONWriter].
	 */
	fun writePathsOn(writer: JSONWriter)
	{
		writer.writeArray {
			roots.forEach { root ->
				at(root.name) { root.writePathsOn(writer) }
			}
		}
	}
}
