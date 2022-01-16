/*
 * AvailStructureViewElement.kt
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
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import org.availlang.ide.anvil.language.psi.AvailFile
import org.availlang.ide.anvil.language.psi.AvailPsiElement
import javax.swing.Icon

/**
 * A `AvailStructureViewElement` is used to display an Avail module's top level
 * statements from the [ModuleManifestEntry]s.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property myElement
 */
open class AvailStructureViewElement constructor(
	val myElement: NavigatablePsiElement
): StructureViewTreeElement, SortableTreeElement
{

	override fun getPresentation(): ItemPresentation = myElement.presentation
		?: PresentationData("NO PRESENTATION", null, null, null)

	override fun getChildren(): Array<TreeElement>
	{
		if (myElement is AvailFile)
		{
			val list = myElement.refreshAndGetManifest().mapIndexed { i, it->
				AvailItemPresentationTreeElement(
					it,
					AvailPsiElement(
						myElement,
						it,
						i,
						myElement.manager))
			}
			return list.toTypedArray()
		}
		return arrayOf()
	}

	override fun navigate(requestFocus: Boolean)
	{
		myElement.navigate(requestFocus)
	}

	override fun canNavigate(): Boolean =
		myElement.canNavigate()

	override fun canNavigateToSource(): Boolean =
		myElement.canNavigateToSource()

	override fun getValue(): Any = myElement

	override fun getAlphaSortKey(): String =
		myElement.name ?: ""

	override fun toString(): String =
		myElement.name ?: super.toString()
}

/**
 * An [ItemPresentation] that presents an [ModuleManifestEntry].
 *
 * @property entry
 *   The [ModuleManifestEntry] to present.
 */
class AvailItemPresentation constructor (
	val entry: ModuleManifestEntry
): ItemPresentation
{
	override fun getPresentableText(): String =
		entry.run { "$summaryText ($kind)" }

	override fun getIcon(unused: Boolean): Icon =
		when (entry.kind)
		{
			ModuleManifestEntry.Kind.ATOM_DEFINITION_KIND ->
				AvailIcons.atom
			ModuleManifestEntry.Kind.METHOD_DEFINITION_KIND ->
				AvailIcons.method
			ModuleManifestEntry.Kind.ABSTRACT_METHOD_DEFINITION_KIND ->
				AvailIcons.method
			ModuleManifestEntry.Kind.FORWARD_METHOD_DEFINITION_KIND ->
				AvailIcons.forwardMethod
			ModuleManifestEntry.Kind.MACRO_DEFINITION_KIND ->
				AvailIcons.macro
			ModuleManifestEntry.Kind.SEMANTIC_RESTRICTION_KIND ->
				AvailIcons.semanticRestriction
			ModuleManifestEntry.Kind.LEXER_KIND ->
				AvailIcons.lexer
			ModuleManifestEntry.Kind.MODULE_CONSTANT_KIND ->
				AvailIcons.constant
			ModuleManifestEntry.Kind.MODULE_VARIABLE_KIND ->
				AvailIcons.variable
		}

	override fun getLocationString(): String
	{
		return entry.topLevelStartingLine.toString()
	}
}

/**
 * A [TreeElement] that is a leaf that is used to display a
 * [ModuleManifestEntry].
 */
class AvailItemPresentationTreeElement constructor(
	val entry: ModuleManifestEntry,
	myElement: NavigatablePsiElement
): AvailStructureViewElement(myElement)
{
	/**
	 * The [AvailItemPresentation] of the [ModuleManifestEntry].
	 */
	val itemPresentation = AvailItemPresentation(entry)

	override fun navigate(requestFocus: Boolean)
	{
		myElement.navigate(requestFocus)
	}

	override fun canNavigate(): Boolean = true

	override fun canNavigateToSource(): Boolean = true


	override fun getValue(): Any = entry

	override fun getPresentation(): ItemPresentation =
		itemPresentation

	override fun getChildren(): Array<TreeElement> = arrayOf()

	override fun toString(): String =
		"${entry.summaryText} (${entry.kind})"
}
