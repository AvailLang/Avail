/*
 * ModuleOrPackageNode.kt
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

package avail.anvil.nodes

import avail.anvil.AvailEditor
import avail.anvil.AvailWorkbench
import avail.anvil.text.FileExtensionMetadata.AVAIL
import avail.builder.ModuleName
import avail.builder.ResolvedModuleName

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
 * @param workbench
 *   The [AvailWorkbench].
 * @param originalModuleName
 *   The name of the module/package prior to resolution (renames).
 * @param resolvedModuleName
 *   The resolved name of the module or package.
 * @param isPackage
 *   Whether it's a package.
 */
class ModuleOrPackageNode constructor(
	workbench: AvailWorkbench,
	private val originalModuleName: ModuleName,
	val resolvedModuleName: ResolvedModuleName,
	val isPackage: Boolean
): OpenableFileNode(workbench)
{
	override fun modulePathString(): String = when
	{
		resolvedModuleName.resolverReference.isPackageRepresentative ->
			resolvedModuleName.packageName
		else -> resolvedModuleName.qualifiedName
	}

	/**
	 * Is the [module&#32;or&#32;package][ModuleOrPackageNode] loaded?
	 *
	 * @return
	 *   `true` if the module or package is already loaded, `false` otherwise.
	 */
	val isLoaded: Boolean
		get() = synchronized(builder) {
			return builder.getLoadedModule(resolvedModuleName) !== null
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
		if (isPackage) "avail-icon-directory-module" else AVAIL.fileIcon

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
		} + suffix

	/**
	 * A bad hack to preserve enough space in the HTML [text] to allow the
	 * display of build progress in the event of a build. This is tied to the
	 * repaint in [AvailWorkbench.eventuallyUpdatePerModuleProgress]. A real
	 * solution is welcome.
	 */
	// TODO HACK - Please do better
	private val suffix: String get() =
		workbench.perModuleProgress[resolvedModuleName]?.let {
			"&nbsp;${it.percentCompleteString}"
		} ?: "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"

	override val isBuilding: Boolean get() =
		workbench.perModuleProgress[resolvedModuleName] != null

	override fun htmlStyle(selected: Boolean): String =
		fontStyle(bold = isPackage, italic = !isLoaded) +
			colorStyle(selected, isLoaded, isRenamedSource, isBuilding)

	override val sortMajor: Int
		get() = if (isPackage) 10 else 20

	override fun open()
	{
		var isNew = false
		val moduleName =
			workbench.selectedModule()!!.resolverReference.moduleName
		val editor = workbench.openEditors.computeIfAbsent(moduleName) {
			isNew = true
			AvailEditor(workbench, moduleName)
		}
		if (!isNew)
		{
			editor.openStructureView(true)
		}
	}

	override fun toString(): String =
		"${javaClass.simpleName}: ${text(false)}".removeSuffix(suffix)
}
