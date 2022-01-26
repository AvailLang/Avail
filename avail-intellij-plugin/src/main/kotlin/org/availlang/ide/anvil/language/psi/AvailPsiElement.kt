/*
 * AvailPsiElement.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

import avail.compiler.problems.Problem
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiElementBase
import org.availlang.ide.anvil.language.AnvilProblemTreeElement
import org.availlang.ide.anvil.language.AvailLanguage

/**
 * A `AvailPsiElement` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
sealed class AvailPsiElement constructor(
	val availFile: AvailFile,
	val myManager: PsiManager
): PsiElementBase()
{
	override fun getLanguage(): Language = AvailLanguage

	override fun getParent(): PsiElement = availFile
}

class AvailErrorPsiElement constructor(
	availFile: AvailFile,
	myManager: PsiManager,
	val problem: Problem,
	val rawText: String
): AvailPsiElement(availFile, myManager)
{
	override fun getText(): String = rawText
	override fun getChildren(): Array<PsiElement> = arrayOf()
	private var range: TextRange =
		TextRange(problem.characterInFile.toInt(),
			problem.characterInFile.toInt())

	override fun getTextRange(): TextRange = range

	override fun getStartOffsetInParent(): Int
	{
		TODO("Not yet implemented")
	}

	override fun getTextLength(): Int = rawText.length

	override fun findElementAt(offset: Int): PsiElement?
	{
		TODO("Not yet implemented")
	}

	override fun getTextOffset(): Int = problem.characterInFile.toInt()

	override fun textToCharArray(): CharArray = rawText.toCharArray()

	override fun getNode(): ASTNode = AnvilProblemTreeElement(ETInvalid, this)
}
