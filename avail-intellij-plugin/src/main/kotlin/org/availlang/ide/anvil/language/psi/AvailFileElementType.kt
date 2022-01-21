/*
 * AvailParseableElementType.kt
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

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import org.availlang.ide.anvil.language.AvailLanguage
import org.availlang.ide.anvil.language.AnvilPsiParser

/**
 *
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailFileElementType: IFileElementType("AVAIL FILE", AvailLanguage)
{
	/**
	 * Parses the contents of the specified chameleon node and returns the AST tree
	 * representing the parsed contents.
	 *
	 * @param chameleon the node to parse.
	 * @return the parsed contents of the node.
	 */
	override fun parseContents(chameleon: ASTNode): ASTNode?
	{
		val parentElement = chameleon.psi
			?: error("parent psi is null: $chameleon")
		return doParseContents(chameleon, parentElement)
	}

	override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode?
	{
		val project = psi.project
//		val builder = PsiBuilderFactory.getInstance().createBuilder(
//			project,
//			chameleon,
//			null,
//			AvailLanguage,
//			chameleon.chars)
		val parser = AnvilPsiParser()
		val node = parser.parse(chameleon.chars, psi as AnvilFile)
		return node.firstChildNode
	}
}

object AvailElementType: IElementType("AVAIL ELEMENT", AvailLanguage)
{

}
