/*
 * ResolvedModuleName.kt
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

import com.avail.builder.ModuleNameResolver.Companion.availExtension
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.persistence.Repository
import java.io.File

/**
 * A `ResolvedModuleName` represents the canonical name of an Avail
 * [module][ModuleDescriptor] that has been resolved to an
 * [absolute][File.isAbsolute] [file&#32;reference][File].
 *
 * @property moduleRoots
 *   The [ModuleRoots] in which to look up the root name.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [ResolvedModuleName].
 *
 * @param qualifiedName
 *   The just-resolved [module&#32;name][ModuleName].
 * @param moduleRoots
 *   The [ModuleRoots] with which to look up the module.
 * @param isRename
 *   Whether module resolution followed a renaming rule.
 */
class ResolvedModuleName
internal constructor(
	qualifiedName: ModuleName,
	private val moduleRoots: ModuleRoots,
	isRename: Boolean) : ModuleName(qualifiedName.qualifiedName, isRename)
{
	/**
	 * `true` iff the [resolved&#32;module&#32;name][ResolvedModuleName]
	 * represents a package, `false` otherwise.
	 */
	val isPackage: Boolean

	private val moduleRoot get() = moduleRoots.moduleRootFor(rootName)!!

	/**
	 * The [resolved][ModuleNameResolver.resolve]
	 * [repository][Repository].
	 */
	val repository get() = moduleRoot.repository

	/**
	 * The [resolved][ModuleNameResolver.resolve] source
	 * [file&#32;reference][File].
	 */
	val sourceReference: File
		get()
		{
			val builder = StringBuilder(100)
			val sourceDirectory = moduleRoot.sourceDirectory!!
			builder.append(sourceDirectory)
			for (part in rootRelativeName.split("/"))
			{
				builder.append('/')
				builder.append(part)
				builder.append(availExtension)
			}
			return File(builder.toString())
		}

	/**
	 * The size, in bytes, of the [module][ModuleDescriptor]. If the source
	 * module is available, then the size of the source module is used;
	 * otherwise, the size of the compiled module is used.
	 */
	val moduleSize = sourceReference.length()

	init
	{
		val ref = sourceReference
		assert(ref.isFile)
		val fileName = ref.name
		val directoryName = ref.parentFile
		this.isPackage = directoryName != null && fileName == directoryName.name
	}

	/**
	 * Answer the local module name as a sibling of the
	 * [receiver][ResolvedModuleName].
	 *
	 * @param theLocalName
	 *   A local module name.
	 * @return
	 *   A [module&#32;name][ModuleName].
	 */
	fun asSibling(theLocalName: String) =
		ModuleName(
			if (isPackage) qualifiedName else packageName,
			theLocalName)
}
