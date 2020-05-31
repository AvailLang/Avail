/*
 * ModuleRootOrPackageNode.java
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

package com.avail.environment.nodes

import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleName
import com.avail.builder.ResolvedModuleName

/**
 * This is a tree node representing a module file or a package.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property originalModuleName
 *   The name of the module/package prior to resolution (renames).
 * @property resolvedModuleName
 *   The resolved name of the module or package.
 * @property isPackage
 *   Whether this module's name refers to a package (directory).
 * @constructor
 *   Construct a new [ModuleOrPackageNode].
 *
 * @param builder
 *   The builder for which this node is being built.
 * @param originalModuleName
 *   The name of the module/package prior to resolution (renames).
 * @param resolvedModuleName
 *   The resolved name of the module or package.
 * @param isPackage
 *   Whether it's a package.
 */
class ModuleOrPackageNode constructor(
	builder: AvailBuilder,
	private val originalModuleName: ModuleName,
	val resolvedModuleName: ResolvedModuleName,
	val isPackage: Boolean) : AbstractBuilderFrameTreeNode(builder)
{
	/**
	 * Is the [module&#32;or&#32;package][ModuleOrPackageNode] loaded?
	 *
	 * @return
	 *   `true` if the module or package is already loaded, `false` otherwise.
	 */
	val isLoaded: Boolean
		get() = synchronized(builder) {
			return builder.getLoadedModule(resolvedModuleName) != null
		}

	/**
	 * Answer whether this is a module that's the subject (source) of a rename
	 * rule.
	 *
	 * @return If this is a renamed module or package.
	 */
	private val isRenamedSource: Boolean
		get() = resolvedModuleName.isRename

	override fun iconResourceName(): String =
		if (isPackage) "PackageInTree" else "ModuleInTree"

	override fun text(selected: Boolean): String =
		if (isRenamedSource)
		{
			(originalModuleName.localName +
				" → " +
				resolvedModuleName.qualifiedName)
		}
		else
		{
			resolvedModuleName.localName
		}

	override fun htmlStyle(selected: Boolean): String =
		fontStyle(bold = isPackage, italic = !isLoaded) +
			colorStyle(selected, isLoaded, isRenamedSource)

	override val sortMajor: Int
		get() = if (isPackage) 10 else 20
}
