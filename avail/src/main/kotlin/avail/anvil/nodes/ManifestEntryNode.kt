/*
 * ManifestEntryNode.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package avail.anvil.nodes

import avail.compiler.ModuleManifestEntry
import avail.anvil.AvailEditor
import avail.anvil.icons.structure.SideEffectIcons
import avail.utility.ifZero
import javax.swing.ImageIcon
import javax.swing.tree.DefaultMutableTreeNode

/**
 * This is a tree node representing an entry point of some module. The parent
 * tree node should be an [ManifestEntryNameNode].
 *
 * @author Richard Arriaga
 *
 * @property editor
 *   The associated [AvailEditor].
 * @property entry
 *   The [ModuleManifestEntry].
 */
class ManifestEntryNode constructor(
	val editor: AvailEditor,
	val entry: ModuleManifestEntry
): DefaultMutableTreeNode(), Comparable<ManifestEntryNode>
{
	override fun compareTo(other: ManifestEntryNode): Int =
		entry.topLevelStartingLine - other.entry.topLevelStartingLine

	/**
	 * Return a suitable icon to display for this instance with the given line
	 * height.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun icon(lineHeight: Int): ImageIcon =
		SideEffectIcons.icon(lineHeight.ifZero { 16 }, entry.kind)
}
