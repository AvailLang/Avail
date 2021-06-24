/*
 * ResolverRegistry.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.resolver

import com.avail.builder.ModuleRoot
import com.avail.files.FileManager
import java.lang.UnsupportedOperationException
import java.net.URI
import java.util.Locale

/**
 * `ModuleRootResolverRegistry` manages all the active
 * [ModuleRootResolverFactory]s known by this instance of Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ModuleRootResolverRegistry
{
	/**
	 * The [Map] of [URI.scheme] `String` to [ModuleRootResolverFactory]s that
	 * are available to Avail.
	 */
	private val resolvers = mutableMapOf<String, ModuleRootResolverFactory>()

	/**
	 * Register the provided [ModuleRootResolverFactory].
	 *
	 * Throws an [IllegalStateException] if the module root is already present.
	 *
	 * @param factory
	 *   The [ModuleRootResolverFactory] to register.
	 */
	fun register (factory: ModuleRootResolverFactory)
	{
		if (resolvers.keys.contains(factory.scheme))
		{
			throw IllegalStateException(
				"Attempted to add a ModuleRootResolverFactory, $factory, " +
					"but already present")
		}
		resolvers[factory.scheme.lowercase(Locale.getDefault())] = factory
	}

	/**
	 * Answer a [ModuleRootResolver].
	 *
	 * @param name
	 *   The name of the module root.
	 * @param uri
	 *   The [URI] that identifies the location of the [ModuleRoot].
	 * @param fileManager
	 *   The [FileManager] used to manage the files accessed via the
	 *   [ModuleRootResolver].
	 * @return
	 *   The constructed [ModuleRootResolver].
	 * @throws UnsupportedOperationException
	 *   If the [URI.scheme] does not have a registered
	 *   [ModuleRootResolverFactory].
	 */
	fun createResolver (
		name: String,
		uri: URI,
		fileManager: FileManager): ModuleRootResolver
	{
		val lookup =
			if (uri.scheme == null)
			{
				URI("file://$uri")
			}
			else
			{
				uri
			}
		val factory = resolvers[lookup.scheme]
		if (factory === null)
		{
			throw UnsupportedOperationException(
				"URI scheme, ${uri.scheme}, does not have a registered " +
					"ModuleRootResolverFactory.")
		}
		return factory.resolver(name, lookup, fileManager)
	}

	init
	{
		register(FileSystemModuleRootResolverFactory)
	}
}
