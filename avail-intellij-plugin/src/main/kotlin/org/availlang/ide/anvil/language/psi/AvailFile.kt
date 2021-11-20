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

import avail.compiler.ModuleManifestEntry
import avail.persistence.cache.Repository
import avail.persistence.cache.RepositoryDescriber
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.availlang.ide.anvil.language.AvailLanguage
import org.availlang.ide.anvil.language.file.AvailFileType
import org.availlang.ide.anvil.models.AvailNode
import org.availlang.ide.anvil.models.AvailProjectService
import org.availlang.ide.anvil.models.ModuleNode

/**
 * `AvailFile` is the [PsiFileBase] for an Avail file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailFile constructor(
	viewProvider: FileViewProvider
): PsiFileBase(viewProvider, AvailLanguage)
{
	override fun getFileType(): FileType = AvailFileType

	init
	{
		println("==================================================================Oh yeah!")
		println("sure, like we got here....")
	}

	/**
	 * The associated [AvailNode].
	 */
	val node: ModuleNode? get()
	{
		val service =
			project.getService(AvailProjectService::class.java)
		val path = viewProvider.virtualFile.path
		val uri = viewProvider.virtualFile.url
		val p = service.availProject
		val n = p.nodesURI
		val g = service.availProject.nodesURI[path] as? ModuleNode
		return service.availProject.nodesURI[path] as? ModuleNode
	}

	/**
	 * Answer the [List] of [ModuleManifestEntry]s for this [AvailFile].
	 */
	val manifest: List<ModuleManifestEntry> get()
	{
		val moduleName = node?.resolved
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
					null
				}
			when (selectedCompilation)
			{
				is Repository.ModuleCompilation ->
				{
					val describer = RepositoryDescriber(repository)
					return describer.manifestEntries(
						selectedCompilation.recordNumberOfManifestEntries)
				}
				is Any -> assert(false) { "Unknown type selected" }
			}
		}
		return listOf()
	}

	override fun toString(): String =
		this.node?.resolved?.qualifiedName ?: "Unknown Avail File"
}
