/*
 * AnvilStructureViewModel.kt
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

import com.intellij.ide.structureView.StructureViewModel.ElementInfoProvider
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.NodeProvider
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import org.availlang.ide.anvil.language.psi.AvailFile

/**
 * `AvailStructureViewModel` is the [StructureViewModelBase] for
 * [AnvilStructureViewElement]s.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property availFile
 *   The associated [AvailFile].
 */
class AnvilStructureViewModel constructor(
	private val availFile: AvailFile
): StructureViewModelBase(availFile, AnvilStructureViewElement(availFile)),
	ElementInfoProvider
{
	override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean
	{
		return false
	}

	override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean =
		when (element)
		{
			null -> true
			is AnvilSingleModuleManifestItemPresentationTreeElement ->
			{
				true
			}
			is AnvilStructureViewElement ->
			{
				element.myElement !is AvailFile
			}
			else -> true
		}

	override fun isEnabled(provider: NodeProvider<*>): Boolean = true

	override fun getNodeProviders(): MutableCollection<NodeProvider<TreeElement>>
	{
		return super.getNodeProviders()
	}

	override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)
}
