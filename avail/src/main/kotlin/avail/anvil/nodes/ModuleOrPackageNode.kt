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
import avail.compiler.ModuleCorpus
import avail.persistence.cache.record.ModuleVersionKey
import avail.utility.ifZero
import org.availlang.artifact.ResourceType
import org.availlang.cache.LRUCache
import java.awt.Image
import java.io.File
import javax.swing.ImageIcon

/**
 * This is a tree node representing a module file or a package.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property originalModuleName
 *   The name of the module/package prior to resolution (renames).
 * @property resolvedModuleName
 *   The resolved name of the module or package.
 * @property resourceType
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
 */
class ModuleOrPackageNode(
	workbench: AvailWorkbench,
	private val originalModuleName: ModuleName,
	val resolvedModuleName: ResolvedModuleName
): OpenableFileNode(workbench)
{
	private val resourceType: ResourceType get() =
		resolvedModuleName.resolverReference.type
	/**
	 * The list of [module corpora][ModuleCorpus] on which to operate.  This is
	 * fetched from the repository as needed.
	 */
	private val corpora: List<ModuleCorpus> by lazy {
		if (resourceType !is ResourceType.Package)
		{
			// Only package representatives can define corpora, to avoid having
			// multiple modules with overlapping claims.
			emptyList()
		}
		else
		{
			// Fetch the hopefully already-parsed module header from the
			// repository.
			val resolverReference = resolvedModuleName.resolverReference
			val archive = resolvedModuleName.repository.getArchive(
				resolvedModuleName.rootRelativeName)
			archive.provideDigest(resolverReference)?.let { digest ->
				archive
					.getVersion(ModuleVersionKey(resolvedModuleName, digest))
					?.moduleHeader(resolvedModuleName, workbench.runtime)
					?.corpora
			} ?: emptyList()
		}
	}

	override fun icon(lineHeight: Int): ImageIcon? =
		when (val rt = resourceType)
		{
			is ResourceType.HeaderlessModule ->
				rt.fileIcon
			is ResourceType.HeaderModule ->
				rt.fileIcon
			else -> null
		}?.let {
			workbench.availProject.roots[resolvedModuleName.moduleRoot.name]
				?.let { root ->
					val path = File(root.resourcesDirectory, it).absolutePath
					cachedCustomScaledIcons[path to lineHeight.ifZero { 19 }]
				}
		} ?: super.icon(lineHeight)

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
		if (resourceType is ResourceType.Package)
			"anvilicon-dir-module-outline"
		else AVAIL.fileIcon

	override fun equalityText(): String = resolvedModuleName.localName

	override fun text(selected: Boolean): String = buildString {
		when
		{
			isRenamedSource ->
			{
				append(originalModuleName.localName)
				append(" → ")
				append(resolvedModuleName.qualifiedName)
			}
			else -> append(resolvedModuleName.localName)
		}

		if (corpora.isNotEmpty())
		{
			corpora.joinTo(this, ", ", "\n\t\tcorpora=", "")
		}
		append(suffix)
	}

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
		fontStyle(
			bold = resourceType is ResourceType.Package,
			italic = !isLoaded) +
				colorStyle(selected, isLoaded, isRenamedSource, isBuilding)

	override val sortMajor: Int
		get() = if (resourceType is ResourceType.Package) 10 else 20

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

	companion object
	{
		/**
		 * A static cache of scaled icons, organized by node class and line
		 * height.
		 */
		private val cachedCustomScaledIcons =
			LRUCache<Pair<String, Int>, ImageIcon>(
			100, 20,
				{ (iconPath, height) ->
					val originalIcon = ImageIcon(iconPath)
					val scaled = originalIcon.image.getScaledInstance(
						-1, height, Image.SCALE_SMOOTH)
					ImageIcon(scaled, iconPath)
				})
	}
}
