/*
 * AvailPsiElement.kt
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
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiElementBase
import org.availlang.ide.anvil.language.AvailLanguage
import org.availlang.ide.anvil.language.AvailTreeElement

/**
 * A `AvailPsiElement` is a [PsiElementBase] that represents a
 * [ModuleManifestEntry] from an Avail module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property availFile
 *   The [AvailFile] the [manifestEntry] comes from.
 * @property manifestEntry
 *   The [ModuleManifestEntry] that this [AvailPsiElement] represents.
 * @property myManager
 *   The active [PsiManager] for the active project.
 */
class AvailPsiElement constructor(
	val availFile: AvailFile,
	val manifestEntry: ModuleManifestEntry,
	val manifestEntryIndex: Int,
	val myManager: PsiManager
): PsiElementBase()
{
	override fun getLanguage(): Language = AvailLanguage

	override fun getChildren(): Array<PsiElement> = arrayOf()

	override fun getParent(): PsiElement = availFile

	override fun getTextRange(): TextRange =
		TextRange(manifestEntry.definitionStartingLine, manifestEntry.definitionStartingLine)

	override fun getStartOffsetInParent(): Int =
		manifestEntry.definitionStartingLine

	override fun getTextLength(): Int = manifestEntry.summaryText.length

	override fun findElementAt(offset: Int): PsiElement? = null

	override fun getTextOffset(): Int
	{
		TODO("Not yet implemented")
	}

	override fun getText(): String = manifestEntry.summaryText

	override fun textToCharArray(): CharArray =
		manifestEntry.summaryText.toCharArray()

	override fun getNode(): ASTNode
	{
		return AvailTreeElement(this, manifestEntry)
	}

	override fun getManager(): PsiManager
	{
		return myManager
	}

	override fun getNextSibling(): PsiElement?
	{
		if (manifestEntryIndex < availFile.manifest.size - 1)
		{
			return availFile.availChildPsiElements[manifestEntryIndex + 1]
		}
		return null
	}

	override fun getPrevSibling(): PsiElement?
	{
		if (manifestEntryIndex > 0)
		{
			return availFile.availChildPsiElements[manifestEntryIndex - 1]
		}
		return null
	}

	override fun toString(): String = manifestEntry.summaryText
}
