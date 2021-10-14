/*
 * ModuleRoot.kt
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

package avail.builder

import avail.descriptor.module.ModuleDescriptor
import avail.persistence.IndexedFileException
import avail.persistence.cache.Repositories
import avail.persistence.cache.Repository
import avail.resolver.ModuleRootResolver
import avail.utility.json.JSONWriter
import java.io.File
import java.lang.RuntimeException

/**
 * A `ModuleRoot` represents a vendor of Avail modules and/or the vended modules
 * themselves.
 *
 * @property name
 *   The [module&#32;root][ModuleRoot] name.
 * @property resolver
 *   If provided, then the [path][File] to the directory that contains source
 *   [modules][ModuleDescriptor] for this [root][ModuleRoot].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `ModuleRoot`.
 *
 * @param name
 *   The name of the module root.
 * @param resolver
 *   The [ModuleRootResolver] used to provide access to the location that
 *   contains source [modules][ModuleDescriptor] and resources for this
 *   [root][ModuleRoot].
 * @throws IndexedFileException
 *   If the indexed repository could not be opened.
 */
class ModuleRoot
@Throws(IndexedFileException::class) constructor(
	val name: String,
	val resolver: ModuleRootResolver)
{
	/**
	 * The [indexed&#32;repository][Repository] that contains compiled
	 * [modules][ModuleDescriptor] for this [root][ModuleRoot].
	 */
	val repository: Repository get() = Repositories[name]
		?: throw RuntimeException("Missing repository: $name")

	/**
	 * Clear the content of the repository for this root.
	 */
	fun clearRepository() = repository.clear()

	/**
	 * Write the [binary][Repository.fileName] and the
	 * [source&#32;module][resolver] (respectively) into a new JSON
	 * array.
	 *
	 * @param writer
	 *   A [JSONWriter].
	 */
	fun writePathsOn(writer: JSONWriter)
	{
		writer.writeArray {
			write(repository.fileName.absolutePath)
			write("${resolver.uri.scheme}://${resolver.uri.path}")
		}
	}

	override fun toString(): String = name
}
