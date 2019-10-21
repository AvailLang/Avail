/*
 * AbstractBuilderFrameTreeNode.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.environment.nodes

import com.avail.builder.AvailBuilder
import com.avail.environment.AvailWorkbench
import com.avail.utility.Casts.cast
import com.avail.utility.LRUCache
import com.avail.utility.Pair
import com.avail.utility.ifZero
import java.awt.Image
import javax.swing.ImageIcon
import javax.swing.tree.DefaultMutableTreeNode

/**
 * An `AbstractBuilderFrameTreeNode` is a tree node used within some
 * [AvailWorkbench].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property builder
 *   The [AvailBuilder] for which this node presents information.
 * @constructor
 * Construct a new `AbstractBuilderFrameTreeNode` on behalf of the
 * given [AvailBuilder].
 *
 * @param builder
 *   The builder for which this node is being built.
 */
abstract class AbstractBuilderFrameTreeNode internal constructor(
		internal val builder: AvailBuilder)
	: DefaultMutableTreeNode(), Comparable<AbstractBuilderFrameTreeNode>
{
	/**
	 * Extract text to display for this node.  Presentation styling will be
	 * applied separately.
	 *
	 * @param selected
	 *   Whether the node is selected.
	 * @return A [String].
	 */
	internal abstract fun text(selected: Boolean): String

	/**
	 * Produce a string for use in a <span style=…> tag for this node.
	 *
	 * @param selected
	 *   Whether the node is selected.
	 * @return The span style attribute text.
	 */
	internal open fun htmlStyle(selected: Boolean): String = "font-weight:normal"

	/**
	 * The local file name `String` of an image file, relative to the
	 * directory "/resources/workbench/".
	 *
	 * @return The local file name, or `null` to indicate not to display
	 *   an icon.
	 */
	internal abstract fun iconResourceName(): String?

	/**
	 * Return a suitable icon to display for this instance with the given line
	 * height.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun icon(lineHeight: Int): ImageIcon?
	{
		val iconResourceName = iconResourceName() ?: return null
		val pair = Pair(
			iconResourceName,
			if (lineHeight != 0) lineHeight else 19)
		return cachedScaledIcons[pair]
	}

	/**
	 * Construct HTML text to present for this node.
	 *
	 * @param selected
	 * Whether the node is selected.
	 * @return The HTML text as a [String].
	 */
	fun htmlText(selected: Boolean): String =
		("<div style=\"" + htmlStyle(selected) + "\">"
		        + text(selected)
		        + "</div>")

	override fun toString(): String =
		javaClass.simpleName + ": " + text(false)

	/**
	 * Answer whether string is an appropriate semantic label for this node.
	 *
	 * @param string
	 *   The string.
	 * @return Whether this is the indicated node.
	 */
	fun isSpecifiedByString(string: String): Boolean =
		text(false) == string

	/**
	 * Order this node against another.
	 */
	override fun compareTo(other: AbstractBuilderFrameTreeNode): Int =
		sortMajor().compareTo(other.sortMajor()).ifZero {
			text(false).compareTo(other.text(false))
		}

	/**
	 * Sort the direct children of this node.  The default sort order is
	 * alphabetic by the nodes' [text] (passing false).
	 */
	fun sortChildren()
	{
		if (children != null)
		{
			// HACK to make children (Vector!) sortable
			val temp: MutableList<AbstractBuilderFrameTreeNode> =
				cast(children.toMutableList())
			temp.sort()
			children.clear()
			children.addAll(temp)
		}
	}

	/**
	 * The primary order by which to sort this node relative to siblings.
	 *
	 * @return An `int`.  Lower values sort before higher ones.
	 */
	open fun sortMajor(): Int =  0

	companion object
	{

		/**
		 * A static cache of scaled icons, organized by node class and line
		 * height.
		 */
		private val cachedScaledIcons = LRUCache<Pair<String, Int>, ImageIcon>(
			100, 20,
			{ pair ->
				val iconResourceName = pair.first()
				val path = (AvailWorkbench.resourcePrefix
				            + iconResourceName + ".png")
				val thisClass =
					AbstractBuilderFrameTreeNode::class.java
				val resource = thisClass.getResource(path)
				val originalIcon = ImageIcon(resource)
				val scaled = originalIcon.image.getScaledInstance(
					-1, pair.second(), Image.SCALE_SMOOTH)
				ImageIcon(scaled, iconResourceName)
			})
	}
}
