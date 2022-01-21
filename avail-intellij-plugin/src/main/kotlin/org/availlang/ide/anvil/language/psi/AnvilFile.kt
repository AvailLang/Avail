/*
 * AvailFile.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package org.availlang.ide.anvil.language.psi

import avail.builder.ModuleRoot
import avail.compiler.ModuleManifestEntry
import avail.persistence.cache.RepositoryDescriber
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import org.availlang.ide.anvil.language.AvailFileElement
import org.availlang.ide.anvil.language.AnvilIcons
import org.availlang.ide.anvil.language.AvailLanguage
import org.availlang.ide.anvil.language.file.AvailFileType
import org.availlang.ide.anvil.models.AvailNode
import org.availlang.ide.anvil.models.project.AnvilProject
import org.availlang.ide.anvil.models.project.AnvilProjectService
import org.availlang.ide.anvil.models.ModuleNode
import org.availlang.ide.anvil.models.RootNode
import org.availlang.ide.anvil.models.project.anvilProjectService
import javax.swing.Icon
import avail.compiler.problems.Problem as AvailCompilerProblem

/**
 * `AnvilFile` is the [PsiFileBase] for an [AvailFileType].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AnvilFile constructor(
	viewProvider: FileViewProvider
): PsiFileBase(viewProvider, AvailLanguage)
{
	override fun getFileType(): FileType = AvailFileType

	override fun getBaseIcon(): Icon =
		if (isModified) { AnvilIcons.availFileDirty }
		else { super.getBaseIcon() }

	val problems = mutableListOf<AvailCompilerProblem>()

	/**
	 * This [AnvilFile]'s [ProblemsHolder] used to report problems with the
	 * represented Avail module.
	 */
	val problemsHolder = ProblemsHolder(
		projectService.inspectionManager,
		this,
		true) // This does a thing...on the fly? TODO

	/**
	 * The running [AnvilProjectService].
	 */
	val projectService: AnvilProjectService
		get() = project.anvilProjectService

	/**
	 * Provide the file contents.
	 */
	val text: CharSequence get() = viewProvider.contents

	/**
	 * The active [AnvilProject].
	 */
	val anvilProject: AnvilProject get() = projectService.anvilProject

	fun build (then: () -> Unit): Boolean =
		node?.let {
			anvilProject.build(this, it.reference.qualifiedName)
			{
				refreshAndGetManifest()
				then()
			}
		} ?: false

	/**
	 * Has this file been modified since it was loaded?
	 */
	val isModified get() =
		node?.let {
			this.modificationStamp > it.reference.lastModified
		} ?: false

	/**
	 * The [RootNode] of the [ModuleRoot] this [AnvilFile] belongs to or `null`
	 * if not an Avail module in any of the [AnvilProject]'s [ModuleRoot]s.
	 */
	val rootNode: RootNode? get() =
		anvilProject.rootForModuleUri(viewProvider.virtualFile.path)

	/**
	 * `true` indicates that this [AnvilFile] represents an Avail module that is
	 * a module in a [ModuleRoot] that is included the active [AnvilProject];
	 * `false` otherwise.
	 */
	val isIncludedProject: Boolean get() = rootNode != null

	/**
	 * The associated [AvailNode] in the active [AnvilProject]; or `null` if
	 * not found in the project.
	 */
	val node: ModuleNode? get()
	{
		val service = project.anvilProjectService
		val path = viewProvider.virtualFile.path
		return service.anvilProject.nodesURI[path] as? ModuleNode
	}

	/**
	 * Answer the [List] of [ModuleManifestEntry]s for this [AnvilFile]. An
	 * empty list indicates that either
	 *  * The module has not been built
	 *  * The module is [not included][isIncludedProject] in the active
	 *  [AnvilProject].
	 */
	val manifest: MutableList<ModuleManifestEntry> by lazy {
		calculateManifest()
	}

	/**
	 * Refresh the [manifest].
	 *
	 * @return
	 *   Answer a [MutableList] of the [ModuleManifestEntry]'s that have been
	 *   produced by a previous compilation of this module.
	 */
	fun refreshAndGetManifest (): MutableList<ModuleManifestEntry>
	{
		manifest.clear()
		manifest.addAll(calculateManifest())
		return manifest
	}

	/**
	 * @return
	 *   Answer a [MutableList] of the [ModuleManifestEntry]'s that have been
	 *   produced by a previous compilation of this module.
	 */
	private fun calculateManifest (): MutableList<ModuleManifestEntry>
	{
		val moduleName = node?.resolved
		val tempList = mutableListOf<ModuleManifestEntry>()
		moduleName?.repository?.use { repository ->
			repository.reopenIfNecessary()
			val archive =
				repository.getArchive(moduleName.rootRelativeName)
			val compilations = archive.allKnownVersions.flatMap {
				it.value.allCompilations
			}
			val compilationsArray = compilations.toTypedArray()
			val selectedCompilation =
				if (compilationsArray.isNotEmpty())
				{
					compilationsArray[0]
				}
				else
				{
					return mutableListOf()
				}
			val describer = RepositoryDescriber(repository)
			tempList.addAll(describer.manifestEntries(
				selectedCompilation.recordNumberOfManifestEntries))
		}
		return tempList
	}

	override fun getFirstChild(): PsiElement?
	{
		if (manifest.isEmpty())
		{
			return null
		}
		val manifestEntry = manifest[0]
		return AvailManifestEntryPsiElement(this, manifestEntry, 0, manager)
	}

	override fun getLastChild(): PsiElement?
	{
		if (manifest.isEmpty())
		{
			return null
		}
		val manifestEntry = manifest.last()
		return AvailManifestEntryPsiElement(
			this, manifestEntry, manifest.size - 1, manager)
	}

	val availChildPsiElements: Array<PsiElement> by lazy {
		manifest.mapIndexed { i, it ->
			AvailManifestEntryPsiElement(this, it, i, manager)
		}.toTypedArray()
	}

	override fun getChildren(): Array<PsiElement> = availChildPsiElements

	override fun createContentLeafElement(leafText: CharSequence?): TreeElement
	{
		return AvailFileElement(leafText!!, this)
	}

	override fun toString(): String =
		this.node?.resolved?.qualifiedName ?: viewProvider.virtualFile.path

	var posted = false
	private fun postProblem ()
	{
		if (!posted)
		{
			posted = true
//			availProject.service.reportAvailFileProblem(
//				virtualFile,
//				"Who is the problem now foo!",
//				-1,
//				-1)
		}
//		if (availChildPsiElements.isNotEmpty())
//		{
//			val eee= Factory.createErrorElement("Yo my error!")
//			problemsHolder.registerProblem(ProblemDescriptorBase(
//				availChildPsiElements[0],
//				availChildPsiElements[1],
//				"This is the error that Jack built!",
//				null,
//				ProblemHighlightType.ERROR,
//				false,
//				null,
//				true,
//				false))
//		}
	}
}
