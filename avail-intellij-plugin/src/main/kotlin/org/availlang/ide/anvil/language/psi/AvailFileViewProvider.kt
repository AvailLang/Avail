/*
 * AvailFileViewProvider.kt
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

import com.intellij.lang.FileASTNode
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import org.availlang.ide.anvil.language.AvailLanguage
import org.availlang.ide.anvil.language.file.AvailFileType

/**
 * `AvailFileViewProvider` is the [AbstractFileViewProvider] for [AnvilFile]s.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property eventSystemEnabled
 *   A copy of [AbstractFileViewProvider]'s `myEventSystemEnabled` as it is
 *   private state and is needed to create a copy of this
 *   [AvailFileViewProvider].
 */
class AvailFileViewProvider constructor(
	manager: PsiManager,
	virtualFile: VirtualFile,
	private val eventSystemEnabled: Boolean
): AbstractFileViewProvider(manager, virtualFile, eventSystemEnabled)
{
	// TODO can these get dirty? need to be recreated?
	/**
	 * The [AnvilFile] this provider will provide.
	 */
	private var myPsiFile: AnvilFile = AnvilFile(this)

	override fun getBaseLanguage(): Language = AvailLanguage

	override fun getLanguages(): Set<Language> = setOf(AvailLanguage)

	override fun getAllFiles(): List<PsiFile> =
		ContainerUtil.createMaybeSingletonList(getPsi(baseLanguage))

	override fun findElementAt(offset: Int): PsiElement? =
		AbstractFileViewProvider.findElementAt(getPsi(baseLanguage), offset)

	override fun findElementAt(
		offset: Int,
		lang: Class<out Language>): PsiElement?
	{
		if (lang == AvailLanguage.javaClass)
		{
			findElementAt(getPsi(baseLanguage), offset)
		}
		return null
	}

	override fun findReferenceAt(offset: Int): PsiReference?
	{
		val psiFile = getPsi(baseLanguage)
		return findReferenceAt(psiFile, offset)
	}

	override fun createCopy(copy: VirtualFile): FileViewProvider =
		AvailFileViewProvider(manager, copy, eventSystemEnabled)

	override fun getPsiInner(target: Language?): PsiFile?
	{
		if (target != AvailLanguage)
		{
			return null
		}
		return myPsiFile
	}

	override fun getCachedPsi(target: Language): PsiFile?
	{
		return if (target !== baseLanguage) null
		else ObjectUtils.nullizeIfDefaultValue(
			myPsiFile,
			PsiUtilCore.NULL_PSI_FILE)!!
	}

	override fun getCachedPsiFiles(): MutableList<PsiFile>
	{
		return ContainerUtil.createMaybeSingletonList(
			getCachedPsi(
				baseLanguage))
	}

	override fun getKnownTreeRoots(): List<FileASTNode>
	{
		val psiFile = getCachedPsi(baseLanguage) as? PsiFileImpl
			?: return listOf()
		val element = psiFile.nodeIfLoaded
		return ContainerUtil.createMaybeSingletonList(element)
	}

	override fun createFile(
		project: Project,
		file: VirtualFile,
		fileType: FileType): PsiFile?
	{
		if (fileType == AvailFileType)
		{
			return myPsiFile
		}
		return null
	}

	override fun createFile(lang: Language): PsiFile?
	{
		if (lang == AvailLanguage)
		{
			return myPsiFile
		}
		return null
	}
}
