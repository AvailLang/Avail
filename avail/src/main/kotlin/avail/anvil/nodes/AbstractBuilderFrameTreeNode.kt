/*
 * AbstractBuilderFrameTreeNode.kt
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

import avail.anvil.AdaptiveColor
import avail.anvil.AvailWorkbench
import avail.anvil.nodes.AbstractBuilderFrameTreeNode.Companion.LoadedState.Building
import avail.anvil.nodes.AbstractBuilderFrameTreeNode.Companion.LoadedState.Loaded
import avail.anvil.nodes.AbstractBuilderFrameTreeNode.Companion.LoadedState.Unloaded
import avail.anvil.nodes.AbstractBuilderFrameTreeNode.Companion.RenamedState.NotRenamed
import avail.anvil.nodes.AbstractBuilderFrameTreeNode.Companion.RenamedState.Renamed
import avail.anvil.nodes.AbstractBuilderFrameTreeNode.Companion.SelectedState.Selected
import avail.anvil.nodes.AbstractBuilderFrameTreeNode.Companion.SelectedState.Unselected
import avail.anvil.tasks.BuildTask
import avail.anvil.window.AvailWorkbenchLayoutConfiguration.Companion.resource
import avail.builder.AvailBuilder
import avail.utility.cast
import avail.utility.ifZero
import org.availlang.cache.LRUCache
import java.awt.Color
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
	internal val builder: AvailBuilder
) : DefaultMutableTreeNode(), Comparable<AbstractBuilderFrameTreeNode>
{
	/**
	 * Answer a [String] suitable for identifying this node even after
	 * refreshing the tree.
	 */
	abstract fun modulePathString(): String

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
	internal abstract fun htmlStyle(selected: Boolean): String

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
		val pair = iconResourceName to lineHeight.ifZero { 19 }
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
		"<div style=\"${htmlStyle(selected)}\">${text(selected)}</div>"

	override fun toString(): String = "${javaClass.simpleName}: ${text(false)}"

	/**
	 * Answer whether string is an appropriate semantic label for this node.
	 *
	 * @param string
	 *   The string.
	 * @return Whether this is the indicated node.
	 */
	fun isSpecifiedByString(string: String): Boolean = text(false) == string

	/**
	 * Order this node against another.
	 */
	override fun compareTo(other: AbstractBuilderFrameTreeNode): Int =
		sortMajor.compareTo(other.sortMajor).ifZero {
			text(false).compareTo(other.text(false))
		}

	/**
	 * Whether this [AbstractBuilderFrameTreeNode] represent an Avail module
	 * that is actively being [built][BuildTask].
	 */
	open val isBuilding: Boolean = false

	/**
	 * Sort the direct children of this node.  The default sort order is
	 * alphabetic by the nodes' [text] (passing false).
	 */
	fun sortChildren()
	{
		if (children !== null)
		{
			// HACK to make children (Vector!) sortable
			val temp: List<AbstractBuilderFrameTreeNode> =
				children.toList().cast()
			children.clear()
			children.addAll(temp.sorted())
		}
	}

	/**
	 * The primary order by which to sort this node relative to siblings.
	 *
	 * @return An [Int].  Lower values sort before higher ones.
	 */
	open val sortMajor: Int get() = 0

	companion object
	{
		enum class LoadedState {Loaded, Unloaded, Building}
		enum class RenamedState {Renamed, NotRenamed}
		enum class SelectedState {Selected, Unselected}

		internal data class NodePaletteType constructor(
			val loadedState: LoadedState,
			val renamedState: RenamedState,
			val selectedState: SelectedState)

		private val palette = mapOf(
			NodePaletteType(Unloaded, NotRenamed, Unselected) to AdaptiveColor(
				light = Color.gray,
				dark = Color.gray),
			NodePaletteType(Unloaded, NotRenamed, Selected) to AdaptiveColor(
				light = Color.lightGray,
				dark = Color.lightGray),
			NodePaletteType(Unloaded, Renamed, Unselected) to AdaptiveColor(
				light = Color(220, 170, 10),
				dark = Color(110, 98, 43)),
			NodePaletteType(Unloaded, Renamed, Selected) to AdaptiveColor(
				light = Color(180, 145, 0),
				dark = Color(180, 160, 72)),
			NodePaletteType(Loaded, NotRenamed, Unselected) to AdaptiveColor(
				light = Color.black,
				dark = Color(169, 183, 198)),
			NodePaletteType(Loaded, NotRenamed, Selected) to AdaptiveColor(
				light = Color.white,
				dark = Color(169, 183, 198)),
			NodePaletteType(Loaded, Renamed, Unselected) to AdaptiveColor(
				light = Color(200, 160, 0),
				dark = Color(180, 160, 72)),
			NodePaletteType(Loaded, Renamed, Selected) to AdaptiveColor(
				light = Color(235, 190, 30),
				dark = Color(180, 160, 72)),
			NodePaletteType(Building, NotRenamed, Unselected) to AdaptiveColor(
				light = Color.blue,
				dark = Color(0xC6, 0x6C, 0x31)),
			NodePaletteType(Building, NotRenamed, Selected) to AdaptiveColor(
				light = Color(0xC6, 0x6C, 0x31),
				dark = Color(0xC6, 0x6C, 0x31)),
			NodePaletteType(Building, Renamed, Unselected) to AdaptiveColor(
				light = Color(200, 160, 0),
				dark = Color(180, 160, 72)),
			NodePaletteType(Building, Renamed, Selected) to AdaptiveColor(
				light = Color(235, 190, 30),
				dark = Color(180, 160, 72)))

		/**
		 * Produce a style string that includes foreground and background
		 * colors based on the given parameters and current light/dark mode.
		 *
		 * @param selected
		 *   Whether the node in question is selected.
		 * @param loaded
		 *   Whether the node in question represents a module that is loaded.
		 * @param renamed
		 *   Whether the node in question represents an element that has been
		 *   renamed.
		 * @param isBuilding
		 *   Whether the node in question represents a module that is in the
		 *   process of being built.
		 */
		fun colorStyle(
			selected: Boolean,
			loaded: Boolean,
			renamed: Boolean,
			isBuilding: Boolean = false): String
		{
			val fore: AdaptiveColor? = palette[
				NodePaletteType(
					when
					{
						loaded -> Loaded
						isBuilding -> Building
						else -> Unloaded
					},
					if (renamed) Renamed else NotRenamed,
					if (selected) Selected else Unselected)]
			return "color:${fore!!.hex};"
		}

		/**
		 * Produce a style string that includes foreground and background
		 * colors based on the given parameters and current light/dark mode.
		 */
		fun fontStyle(
			bold: Boolean = false,
			italic: Boolean = false,
			strikethrough: Boolean = false,
		) = buildString {
			if (bold) append("font-weight:900;")
			if (italic) append("font-style:italic;")
			if (strikethrough) append("text-decoration:line-through;")
		}

		/**
		 * A static cache of scaled icons, organized by node class and line
		 * height.
		 */
		private val cachedScaledIcons = LRUCache<Pair<String, Int>, ImageIcon>(
			100, 20,
			{ (iconResourceName, height) ->
				val path = resource("$iconResourceName.png")
				val thisClass = AbstractBuilderFrameTreeNode::class.java
				val resource = thisClass.getResource(path)
				val originalIcon = ImageIcon(resource)
				val scaled = originalIcon.image.getScaledInstance(
					-1, height, Image.SCALE_SMOOTH)
				ImageIcon(scaled, iconResourceName)
			})
	}
}
