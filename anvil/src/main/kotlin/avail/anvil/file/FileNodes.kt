/*
 * FileNodes.kt
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

package avail.anvil.file

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import avail.anvil.components.AsyncImageBitmap
import avail.anvil.components.AsyncSvg
import avail.anvil.models.Project
import avail.anvil.themes.AlternatingRowColor
import avail.anvil.themes.AlternatingRowColor.*
import avail.anvil.themes.ImageResources
import avail.anvil.themes.LoadedStyle
import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleRoot
import com.avail.resolver.ModuleRootResolver
import com.avail.resolver.ResolverReference
import com.avail.resolver.ResourceType

/**
 * A node in the file tree.
 *
 * @author Richard Arriaga.
 *
 * @property builder
 *   The [AvailBuilder] for the represented [Project].
 * @property reference
 *   The [ResolverReference] that this [AvailNode] points to.
 */
sealed class AvailNode constructor(
	val builder: AvailBuilder,
	val reference: ResolverReference): Comparable<AvailNode>
{
	/**
	 * This [AvailNode]'s immediate parent node.
	 */
	abstract val parentNode: AvailNode?

	/**
	 * The indentation level of this [AvailNode].
	 */
	val indent: Int  by lazy {
		parentNode?.let { it.indent + 1 } ?: 0
	}

	/**
	 * The indention [Dp] for this [AvailNode].
	 */
	val indentPadding: Dp by lazy {
		(indent * 12 + 4).dp
	}

	/**
	 * `true` indicates this is a directory; `false` otherwise.
	 */
	open val isDirectory: Boolean = false

	/**
	 * The [Map] from [ResolverReference.qualifiedName] to the corresponding
	 * [AvailNode].
	 */
	val children = mutableMapOf<String, AvailNode>()

	/**
	 * `true` indicates it is expanded showing its children; `false` otherwise.
	 */
	protected var isExpanded by mutableStateOf(false)

	/**
	 * `true` indicates there are [children]; `false` otherwise.
	 */
	internal var hasChildren  by mutableStateOf(children.isNotEmpty())

	/**
	 * Draw the expandable icon if there are [children].
	 */
	@Composable
	internal open fun ExpandableIcon ()
	{
		if(hasChildren)
		{
			AsyncSvg(
				file =
					if (isExpanded) ImageResources.expandedDirectoryImage
					else ImageResources.collapsedDirectoryImage,
				modifier =
					Modifier.padding(start = indentPadding).widthIn(max = 18.dp))
		}
		else
		{
			Spacer(Modifier.padding(start = indentPadding).width(20.dp))
		}
	}

	/**
	 * The
	 */
	@Composable
	abstract fun FileIcon ()

	/**
	 * The [ModuleRootResolver] used for the [ModuleRootView] this node is part of.
	 */
	abstract val resolver: ModuleRootResolver

	/**
	 * Add the [AvailNode] to this node's [children].
	 */
	fun addChild (node: AvailNode)
	{
		children[node.reference.qualifiedName] = node
		hasChildren = true
	}

	/**
	 * The list of [children] nodes in a sorted list.
	 */
	val sortedChildren : List<AvailNode> get() =
		children.values.toList().sorted()

	/**
	 * Answer a [Composable] lambda that accepts a [AlternatingRowColor].
	 */
	@Composable
	open fun draw (alternatingColor: AlternatingRowColor = ROW1)
	{
		// TODO add padding based on indentation
		val modifier =
			Modifier.clickable { isExpanded = !isExpanded }
		if (!isDirectory)
		{
			modifier.background(alternatingColor.next.color)
		}
		modifier.fillMaxWidth()

		Row (
			modifier = modifier,
			verticalAlignment = Alignment.CenterVertically)
		{
			ExpandableIcon()
			Spacer(Modifier.width(5.dp))
			FileIcon()
			Spacer(Modifier.width(4.dp))
			Text(
				text = reference.localName,
				color = LoadedStyle.color,
				fontSize = LoadedStyle.size)
		}
		if (isExpanded)
		{
			val rowColor = ROW1
			sortedChildren.forEach { it.draw(rowColor) }
		}
	}

	override fun compareTo(other: AvailNode): Int
	{
		return when (this)
		{
			is DirectoryNode ->
			{
				when (other)
				{
					is DirectoryNode, is ModulePackageNode ->
						reference.localName.compareTo(other.reference.localName)
					is ModuleNode,
					is ResourceNode -> 1
					is RootNode -> -1
				}
			}
			is ModuleNode ->
			{
				when (other)
				{
					is ModuleNode, is ResourceNode ->
						reference.localName.compareTo(other.reference.localName)
					is DirectoryNode,
					is ModulePackageNode,
					is RootNode -> -1
				}
			}
			is ModulePackageNode ->
			{
				when (other)
				{
					is DirectoryNode, is ModulePackageNode ->
						reference.localName.compareTo(other.reference.localName)
					is ModuleNode,
					is ResourceNode -> 1
					is RootNode -> -1
				}
			}
			is ResourceNode ->
			{
				when (other)
				{
					is ModuleNode, is ResourceNode ->
						reference.localName.compareTo(other.reference.localName)
					is DirectoryNode,
					is ModulePackageNode,
					is RootNode -> -1
				}
			}
			is RootNode -> {
				when (other)
				{
					is DirectoryNode,
					is ModuleNode,
					is ModulePackageNode,
					is ResourceNode -> 1
					is RootNode ->
						reference.localName.compareTo(other.reference.localName)
				}
			}
		}
	}

	override fun toString(): String
	{
		return reference.localName
	}
}

/**
 * An [AvailNode] that represents a [ResourceType.ROOT] node.
 *
 * @author Richard Arriaga.
 *
 * @property root
 *   The corresponding [ModuleRootView].
 */
class RootNode constructor(
	builder: AvailBuilder,
	reference: ResolverReference,
	val root: ModuleRoot
): AvailNode(builder, reference)
{
	override val isDirectory: Boolean = true
	override val parentNode: AvailNode? = null
	override val resolver: ModuleRootResolver get() = root.resolver

	@Composable
	override fun FileIcon()
	{
		AsyncSvg(
			file = ImageResources.rootFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}

/**
 * An [AvailNode] that represents a [ResourceType.PACKAGE] node.
 *
 * @author Richard Arriaga.
 *
 * @property root
 *   The corresponding [ModuleRootView].
 */
class ModulePackageNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	builder: AvailBuilder
): AvailNode(builder, reference)
{
	override val isDirectory: Boolean = true
	override val resolver: ModuleRootResolver get() = parentNode.resolver

	@Composable
	override fun FileIcon()
	{
		AsyncImageBitmap(
			file = ImageResources.packageFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}
/**
 * An [AvailNode] that represents a [ResourceType.DIRECTORY] node.
 *
 * @author Richard Arriaga.
 */
class DirectoryNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	builder: AvailBuilder
): AvailNode(builder, reference)
{
	override val isDirectory: Boolean = true
	override val resolver: ModuleRootResolver get() = parentNode.resolver

	@Composable
	override fun FileIcon()
	{
		AsyncSvg(
			file = ImageResources.resourceDirectoryImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}

/**
 * An [AvailNode] that represents a [ResourceType.MODULE] node or
 * [ResourceType.REPRESENTATIVE] node.
 *
 * @author Richard Arriaga.
 *
 * @property root
 *   The corresponding [ModuleRootView].
 */
class ModuleNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	builder: AvailBuilder
): AvailNode(builder, reference)
{
	override val resolver: ModuleRootResolver get() = parentNode.resolver
	val entryPointNodes =
		mutableListOf<EntryPointNode>()

	@Composable
	override fun draw (alternatingColor: AlternatingRowColor)
	{
		// TODO add padding based on indentation
		val modifier =
			Modifier.clickable {
				if (entryPointNodes.isNotEmpty()) { isExpanded = !isExpanded } }
				.background(alternatingColor.next.color)
				.fillMaxWidth()

		Row (
			modifier = modifier,
			verticalAlignment = Alignment.CenterVertically)
		{
			ExpandableIcon()
			Spacer(Modifier.width(5.dp))
			FileIcon()
			Spacer(Modifier.width(4.dp))
			Text(
				text = reference.localName,
				color = LoadedStyle.color,
				fontSize = LoadedStyle.size)
		}
		if (isExpanded)
		{
			val rowColor = ROW1
			entryPointNodes.forEach { it.draw(rowColor) }
		}
	}

	@Composable
	override fun ExpandableIcon()
	{
		if(hasChildren)
		{
			AsyncSvg(
				file =
				if (isExpanded) ImageResources.expandedModuleImage
				else ImageResources.expandedDirectoryImage,
				modifier =
					Modifier.padding(start = indentPadding).widthIn(max = 18.dp))
		}
		else
		{
			Spacer(Modifier.padding(start = indentPadding).width(20.dp))
		}
	}

	@Composable
	override fun FileIcon()
	{
		AsyncImageBitmap(
			file = ImageResources.moduleFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}

/**
 * An [AvailNode] that represents a [ResourceType.RESOURCE] node.
 *
 * @author Richard Arriaga.
 *
 * @property root
 *   The corresponding [ModuleRootView].
 */
class ResourceNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	builder: AvailBuilder
): AvailNode(builder, reference)
{
	override val resolver: ModuleRootResolver get() = parentNode.resolver

	@Composable
	override fun FileIcon()
	{
		AsyncSvg(
			file = ImageResources.resourceFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}

class EntryPointNode constructor(
	val parent: ModuleNode,
	val name: String)
{
	/**
	 * Answer a [Composable] lambda that accepts a [AlternatingRowColor].
	 */
	@Composable
	fun draw (alternatingColor: AlternatingRowColor)
	{
		// TODO add padding based on indentation
		Row (
			modifier = Modifier
				.clickable { println("TODO: Run entry point $name") }
				.background(alternatingColor.next.color)
				.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically)
		{
			Spacer(Modifier.width(20.dp))
			Spacer(Modifier.width(5.dp))
			AsyncSvg(
				file = ImageResources.playArrowImage,
				modifier = Modifier.widthIn(max = 20.dp))
			Spacer(Modifier.width(4.dp))
			Text(
				text = name,
				color = LoadedStyle.color,
				fontSize = LoadedStyle.size)
		}
	}
}
