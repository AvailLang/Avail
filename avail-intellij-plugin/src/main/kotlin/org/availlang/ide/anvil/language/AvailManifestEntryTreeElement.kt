/*
 * AvailTreeElement.kt
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

package org.availlang.ide.anvil.language

import avail.compiler.ModuleManifestEntry
import com.intellij.psi.PsiElement
import org.availlang.ide.anvil.language.psi.AvailManifestEntryPsiElement

/**
 * A `AvailTreeElement` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailManifestEntryTreeElement constructor(
	val psiElement: AvailManifestEntryPsiElement,
	val manifestEntry: ModuleManifestEntry
): AvailTreeElement()
{
	override fun getText(): String = manifestEntry.summaryText

	override fun getChars(): CharSequence = manifestEntry.summaryText

	override fun textContains(c: Char): Boolean =
		manifestEntry.summaryText.contains(c)

	override fun getTextLength(): Int = manifestEntry.summaryText.length

	override fun getPsi(): PsiElement = psiElement

	override fun textToCharArray(): CharArray =
		manifestEntry.summaryText.toCharArray()

	override fun getCachedLength(): Int =
		manifestEntry.summaryText.length
}
