/*
 * ModuleName.kt
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

import com.avail.descriptor.module.ModuleDescriptor

/**
 * A `ModuleName` represents the canonical name of an Avail
 * [module][ModuleDescriptor]. A canonical name is specified relative to an
 * Avail [module&#32;root][ModuleRoots] and has the form **R/X/Y/Z**, where
 * **R** is a module root on the Avail module path, **X** is a package within
 * **R**, **Y** is a package within **X**, and **Z** is a module or package
 * within **Y**.
 *
 * @property qualifiedName
 *   The fully-qualified module name.
 * @property isRename
 *   `true` iff this module name was transformed via a rename rule.
 * @author Todd L Smith &lt;todd@availlang.org &gt;
 *
 * @constructor
 *
 * Construct a new `ModuleName` from the specified fully-qualified module name.
 *
 * @param qualifiedName
 *   A fully-qualified module name.
 * @param isRename
 *   Whether module resolution followed a renaming rule.
 * @throws IllegalArgumentException
 *   If the argument was malformed.
 */
open class ModuleName
@Throws(IllegalArgumentException::class) @JvmOverloads constructor(
	val qualifiedName: String,
	val isRename: Boolean = false)
{
	/** The logical root name of the `ModuleName`. */
	val rootName: String

	/** The fully-qualified package name of the `ModuleName`. */
	val packageName: String

	/**
	 * The local name of the [module][ModuleDescriptor] referenced by this
	 * `ModuleName`.
	 */
	val localName: String

	/**
	 * The lazily-initialized root-relative `ModuleName`. This is the
	 * [fully-qualified&#32;name][qualifiedName] minus the #rootName() module
	 * root}.
	 */
	val rootRelativeName: String by lazy {
		val components = qualifiedName.split("/")
		val builder = StringBuilder(50)
		for (index in 2 until components.size)
		{
			if (index > 2)
			{
				builder.append('/')
			}
			builder.append(components[index])
		}
		builder.toString()
	}

	init
	{
		val components = qualifiedName.split("/")
		require(!(components.size < 3 || components[0].isNotEmpty())) {
			"invalid fully-qualified module name ($qualifiedName)"
		}

		// Handle the easy ones first.
		this.rootName = components[1]
		this.localName = components[components.size - 1]

		// Now determine the package.
		val builder = StringBuilder(50)
		for (index in 1 until components.size - 1)
		{
			builder.append("/")
			builder.append(components[index])
		}
		this.packageName = builder.toString()
	}

	/**
	 * Construct a new `ModuleName` from the specified canonical module group
	 * name and local name.
	 *
	 * @param packageName
	 *   A canonical package name.
	 * @param localName
	 *   A local module name.
	 * @param isRename
	 *   Whether module resolution followed a renaming rule.
	 * @throws IllegalArgumentException
	 *   If the argument was malformed.
	 */
	@Throws(IllegalArgumentException::class) @JvmOverloads constructor(
		packageName: String,
		localName: String,
		isRename: Boolean = false) : this("$packageName/$localName", isRename)

	override fun equals(other: Any?): Boolean
	{
		return this === other ||
			other is ModuleName && qualifiedName == other.qualifiedName
	}

	override fun hashCode() =
		// The magic number is a prime.
		345533 * qualifiedName.hashCode() xor 0x5881271A

	override fun toString(): String = qualifiedName
}
