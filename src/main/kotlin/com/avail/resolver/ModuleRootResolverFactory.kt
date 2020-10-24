/*
 * ModuleRootResolverFactory.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation
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
import java.net.URI

/**
 * `ModuleRootResolverFactory` is used to create a [ModuleRootResolver].
 *
 * All implementations of [ModuleRootResolver] must have an accompanying
 * implementation of `ModuleRootResolverFactory` to enable the creation of the
 * resolver.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface ModuleRootResolverFactory
{
	/**
	 * Answer a [ModuleRootResolver] for the given [URI].
	 *
	 * @param name
	 *   The name of the module root.
	 * @param uri
	 *   The [URI] that identifies the location of the [ModuleRoot].
	 * @param fileManager
	 *   The [FileManager] used to manage the files accessed via the
	 *   [ModuleRootResolver].
	 * @return
	 *   The [ModuleRootResolver] that is linked to the [URI] of the
	 *   [ModuleRoot].
	 */
	fun resolver (
		name: String,
		uri: URI,
		fileManager: FileManager): ModuleRootResolver

	/**
	 * The [URI.scheme] type this [ModuleRootResolverFactory] creates
	 * [ModuleRootResolver]s to access.
	 */
	val scheme: String
}
