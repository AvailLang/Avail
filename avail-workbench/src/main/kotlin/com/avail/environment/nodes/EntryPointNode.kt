/*
 * EntryPointNode.java
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
import com.avail.builder.ResolvedModuleName

/**
 * This is a tree node representing an entry point of some module.  The parent
 * tree node should be an [EntryPointModuleNode].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property resolvedModuleName
 *   The name of the module containing the entry point.
 * @property entryPointString
 *   The entry point, which is a [String].
 * @constructor
 *   Construct a new [EntryPointNode], given the name of the module and the name
 *   of the entry point.
 *
 * @param builder
 *   The builder for which this node is being built.
 * @param resolvedModuleName
 *   The name of the module defining the entry point.
 * @param entryPointString
 *   The name of the entry point.
 */
class EntryPointNode constructor(
	builder: AvailBuilder,
	val resolvedModuleName: ResolvedModuleName,
	val entryPointString: String
): AbstractBuilderFrameTreeNode(builder) {
	override fun iconResourceName(): String? = null

	override fun text(selected: Boolean): String = entryPointString

	override fun htmlStyle(selected: Boolean): String =
		synchronized(builder) {
			val loaded = builder.getLoadedModule(resolvedModuleName) !== null
			fontStyle(italic = loaded) +
				colorStyle(selected, loaded, resolvedModuleName.isRename)
		}
}
