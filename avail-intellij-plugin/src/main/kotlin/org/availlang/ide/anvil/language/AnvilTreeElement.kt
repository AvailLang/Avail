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

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.TreeElementVisitor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.availlang.ide.anvil.language.psi.AvailElementType
import org.availlang.ide.anvil.language.psi.AvailErrorPsiElement

/**
 * A `AvailTreeElement` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
abstract class AnvilTreeElement(val elementType: AvailElementType) :
	TreeElement(elementType)
{
	override fun getFirstChildNode(): TreeElement? = null

	override fun getLastChildNode(): TreeElement? = null

	override fun getChildren(filter: TokenSet?): Array<ASTNode> = arrayOf()

	override fun addChild(child: ASTNode)
	{
		// cannot be done
	}

	override fun addChild(child: ASTNode, anchorBefore: ASTNode?)
	{
		// cannot be done
	}

	override fun addLeaf(
		leafType: IElementType,
		leafText: CharSequence,
		anchorBefore: ASTNode?
	)
	{
		// cannot be done
	}

	override fun removeChild(child: ASTNode)
	{
		// cannot be done
	}

	override fun removeRange(
		firstNodeToRemove: ASTNode,
		firstNodeToKeep: ASTNode?
	)
	{
		// cannot be done
	}

	override fun replaceChild(oldChild: ASTNode, newChild: ASTNode)
	{
		// cannot be done
	}

	override fun replaceAllChildrenToChildrenOf(anotherParent: ASTNode)
	{
		// cannot be done
	}

	override fun addChildren(
		firstChild: ASTNode,
		firstChildToNotAdd: ASTNode?,
		anchorBefore: ASTNode?
	)
	{
		// cannot be done
	}

	override fun findLeafElementAt(offset: Int): LeafElement? = null

	override fun findChildByType(type: IElementType): ASTNode? = null

	override fun findChildByType(
		type: IElementType, anchor: ASTNode?
	): ASTNode? = null

	override fun findChildByType(typesSet: TokenSet): ASTNode? = null

	override fun findChildByType(
		typesSet: TokenSet, anchor: ASTNode?
	): ASTNode? = null

	override fun <T : PsiElement?> getPsi(clazz: Class<T>): T
	{
		TODO("Not yet implemented")
	}

	override fun textMatches(buffer: CharSequence, start: Int): Int
	{
		TODO("Not yet implemented")
	}

	override fun hc(): Int
	{
		TODO("Not yet implemented")
	}

	override fun acceptTree(visitor: TreeElementVisitor)
	{
		TODO("Not yet implemented")
	}
}

/**
 * A `AvailTreeElement` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AnvilProblemTreeElement constructor(
	elementType: AvailElementType,
	val psiElement: AvailErrorPsiElement
) : AnvilTreeElement(elementType)
{
	override fun getText(): String = psiElement.rawText

	override fun getChars(): CharSequence = psiElement.rawText

	override fun textContains(c: Char): Boolean =
		psiElement.rawText.contains(c)

	override fun getTextLength(): Int = psiElement.rawText.length

	override fun getPsi(): PsiElement = psiElement

	override fun textToCharArray(): CharArray =
		psiElement.rawText.toCharArray()

	override fun getCachedLength(): Int =
		psiElement.rawText.length
}
