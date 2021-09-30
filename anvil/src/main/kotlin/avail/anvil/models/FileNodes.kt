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

package avail.anvil.models

import androidx.compose.foundation.ExperimentalDesktopApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.mouseClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import avail.anvil.components.AsyncImageBitmap
import avail.anvil.components.AsyncSvg
import avail.anvil.models.Project
import avail.anvil.themes.AlternatingRowColor
import avail.anvil.themes.AlternatingRowColor.*
import avail.anvil.themes.AvailColors
import avail.anvil.themes.ImageResources
import avail.anvil.themes.LoadedStyle
import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleRoot
import com.avail.builder.ResolvedModuleName
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
	val project: Project,
	val reference: ResolverReference): Comparable<AvailNode>
{
	val builder: AvailBuilder get() = project.builder

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

	val rawIndent by lazy { indent * 12 + 4 }

	/**
	 * The indention [Dp] for this [AvailNode].
	 */
	val indentPadding: Dp by lazy { rawIndent.dp }

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
	protected var isExpanded= false

	/**
	 * `true` indicates there are [children]; `false` otherwise.
	 */
	protected var hasChildren  by mutableStateOf(children.isNotEmpty())

	protected fun build (then: () -> Unit): Boolean =
		project.build(reference.qualifiedName, then)

	/**
	 * Draw the expandable icon if there are [children].
	 */
	@Composable
	internal open fun ExpandableIcon (expanded: MutableState<Boolean>)
	{
		if (hasChildren)
		{
			val modifier =
				Modifier
					.clickable {
						expanded.value = !expanded.value
						isExpanded = expanded.value
					}
					.padding(start = indentPadding).widthIn(max = 18.dp)
			if (expanded.value)
			{
				AsyncSvg(
					resource = ImageResources.expandedDirectoryImage,
					modifier = modifier)

			}
			else
			{
				AsyncSvg(
					resource = ImageResources.collapsedDirectoryImage,
					modifier = modifier)
			}
		}
		else
		{
			Spacer(Modifier.padding(start = indentPadding).width(20.dp))
		}
	}

	/**
	 * Draw the icon that represents this file type
	 */
	@Composable
	abstract fun FileIcon ()

	/**
	 * The [ModuleRootResolver] used for the [draw] this node is part of.
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
	@ExperimentalComposeUiApi
	@OptIn(ExperimentalFoundationApi::class, ExperimentalDesktopApi::class)
	@Composable
	open fun draw ()
	{
		val expanded = remember { mutableStateOf(isExpanded) }
		// TODO add padding based on indentation
		val modifier = Modifier
		if (!isDirectory)
		{

			modifier.background(AvailColors.BG)
		}

		modifier.fillMaxWidth()
		Row (
			modifier = modifier,
			verticalAlignment = Alignment.CenterVertically)
		{
			ExpandableIcon(expanded)
			Spacer(Modifier.width(5.dp))
			FileIcon()
			Spacer(Modifier.width(4.dp))
			SelectionContainer {
				val active = remember { mutableStateOf(false) }
				val textColor =
					if (active.value) LoadedStyle.color.copy(alpha = 0.6f)
					else LoadedStyle.color
				Text(
					text = reference.localName,
					color = textColor,
					fontSize = LoadedStyle.size,
					modifier = Modifier
						.align(Alignment.CenterVertically)
						.clipToBounds()
						.pointerMoveFilter(
							onEnter = {
								active.value = true
								true
							},
							onExit = {
								active.value = false
								true
							}
						))
			}
		}
		if (expanded.value)
		{
			val rowColor = ROW1
			sortedChildren.forEach { it.draw() }
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
 */
class RootNode constructor(
	project: Project,
	reference: ResolverReference,
	val root: ModuleRoot
): AvailNode(project, reference)
{
	override val isDirectory: Boolean = true
	override val parentNode: AvailNode? = null
	override val resolver: ModuleRootResolver get() = root.resolver

	@Composable
	override fun FileIcon()
	{
		AsyncSvg(
			resource = ImageResources.rootFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}

/**
 * An [AvailNode] that represents a [ResourceType.PACKAGE] node.
 *
 * @author Richard Arriaga.
 */
class ModulePackageNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	project: Project
): AvailNode(project, reference)
{
	override val isDirectory: Boolean = true
	override val resolver: ModuleRootResolver get() = parentNode.resolver
	val entryPointNodes =
		mutableListOf<EntryPointNode>()

	/**
	 * The [ResolvedModuleName] this node represents.
	 */
	val resolved: ResolvedModuleName =
		project.moduleNameResolver.resolve(reference.moduleName)

	/**
	 * Is the module loaded?
	 *
	 * @return
	 *   `true` if the module or package is already loaded, `false` otherwise.
	 */
	val isLoaded: Boolean
		get() = synchronized(builder) {
			return builder.getLoadedModule(resolved) !== null
		}

	private val isbuilding = mutableStateOf(false)

	@OptIn(ExperimentalDesktopApi::class)
	@Composable
	override fun FileIcon()
	{
		AsyncImageBitmap(
			resource = ImageResources.packageFileImage,
			modifier = Modifier
				.widthIn(max = 20.dp))
//				.mouseClickable(
//					onClick = {
//						if (buttons.isPrimaryPressed
//							&& keyboardModifiers.isShiftPressed)
//						{
//							isbuilding.value = build { isbuilding.value = false }
//						}
//					}))
	}

	@OptIn(ExperimentalDesktopApi::class,
		androidx.compose.foundation.ExperimentalFoundationApi::class)
	@Composable
	@ExperimentalComposeUiApi
	override fun draw()
	{
		val expanded = remember { mutableStateOf(isExpanded) }
		// TODO add padding based on indentation
		val modifier = Modifier
		if (!isDirectory)
		{
			modifier.background(AvailColors.BG)
		}
		val building by remember { isbuilding }
		modifier.fillMaxWidth()
		Row (
			modifier = modifier,
			verticalAlignment = Alignment.CenterVertically)
		{
			ExpandableIcon(expanded)
			Spacer(Modifier.width(5.dp))
			FileIcon()
			Spacer(Modifier.width(4.dp))
			SelectionContainer {
				val textModifier = Modifier
					.mouseClickable(
					onClick = {
						if (buttons.isPrimaryPressed
							&& keyboardModifiers.isShiftPressed)
						{
							isbuilding.value = build { isbuilding.value = false }
						}
					})
				val active = remember { mutableStateOf(false) }
				val textColor =
					if (active.value) LoadedStyle.color.copy(alpha = 0.6f)
					else LoadedStyle.color
//				ContextMenuArea( { listOf(
//					ContextMenuItem("Fooey") { println("Did it")}
//				)}) {
				Text(
					text = reference.localName,
					color = textColor,
					fontSize = LoadedStyle.size,
					modifier = textModifier
						.align(Alignment.CenterVertically)
						.clipToBounds()
						.pointerMoveFilter(
							onEnter = {
								active.value = true
								true
							},
							onExit = {
								active.value = false
								true
							}
						))
				}
				if (entryPointNodes.isNotEmpty())
				{
					val entryIconModifier =
						Modifier
							.padding(start = 2.dp)
							.widthIn(max = 18.dp)
							.clickable {
								expanded.value = !expanded.value
								isExpanded = expanded.value
							}
					if (expanded.value)
					{
						AsyncSvg(
							resource = ImageResources.expandedModuleImage,
							modifier = entryIconModifier)
					}
					else
					{
						AsyncSvg(
							resource = ImageResources.collapsedModuleImage,
							modifier = entryIconModifier)
					}
				}
//			}
			if (building)
			{
				Spacer(Modifier.width(5.dp))
				Box (Modifier.fillMaxSize().align(Alignment.CenterVertically), Alignment.Center)
				{
					CircularProgressIndicator(
						modifier =
							Modifier.heightIn(max = 10.dp).widthIn(max = 10.dp),
						color = Color(0xFFAEB0B2),
						strokeWidth = 2.dp)
				}
			}
		}
		if (expanded.value)
		{
			entryPointNodes.forEach { it.draw(ROW1) }
			sortedChildren.forEach { it.draw() }
		}
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
	project: Project
): AvailNode(project, reference)
{
	override val isDirectory: Boolean = true
	override val resolver: ModuleRootResolver get() = parentNode.resolver

	@Composable
	override fun FileIcon()
	{
		AsyncSvg(
			resource = ImageResources.resourceDirectoryImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}

/**
 * An [AvailNode] that represents a [ResourceType.MODULE] node or
 * [ResourceType.REPRESENTATIVE] node.
 *
 * @author Richard Arriaga.
 */
class ModuleNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	project: Project
): AvailNode(project, reference)
{
	override val resolver: ModuleRootResolver get() = parentNode.resolver
	val entryPointNodes =
		mutableListOf<EntryPointNode>()

	/**
	 * The [ResolvedModuleName] this node represents.
	 */
	val resolved: ResolvedModuleName =
		project.moduleNameResolver.resolve(reference.moduleName)

	/**
	 * Is the module loaded?
	 *
	 * @return
	 *   `true` if the module or package is already loaded, `false` otherwise.
	 */
	val isLoaded: Boolean
		get() = synchronized(builder) {
			return builder.getLoadedModule(resolved) !== null
		}

	private val isbuilding = mutableStateOf(false)

	@OptIn(ExperimentalComposeUiApi::class, ExperimentalDesktopApi::class)
	@Composable
	override fun draw()
	{
		if (reference.type == ResourceType.REPRESENTATIVE) { return }
		val expanded = remember { mutableStateOf(isExpanded) }
		val modifier =
			Modifier
				.background(AvailColors.BG)
				.fillMaxWidth()
		val building by remember { isbuilding }
		Row (
			modifier = modifier.padding(vertical = 3.dp),
			verticalAlignment = Alignment.CenterVertically)
		{
//			ExpandableIcon(expanded)
			Spacer(Modifier.padding(start = indentPadding).width(23.dp))
//			Spacer(Modifier.width(5.dp))
			FileIcon()
			Spacer(Modifier.width(4.dp))
			SelectionContainer {
				val active = remember { mutableStateOf(false) }
				val textColor =
					if (active.value) LoadedStyle.color.copy(alpha = 0.6f)
					else LoadedStyle.color
				Text(
					text = reference.localName,
					color = textColor,
					fontSize = LoadedStyle.size,
					modifier = Modifier
						.mouseClickable(
							onClick = {
								if (buttons.isPrimaryPressed
									&& keyboardModifiers.isShiftPressed)
								{
									isbuilding.value =
										build { isbuilding.value = false }
								}
							})
						.align(Alignment.CenterVertically)
						.clipToBounds()
						.pointerMoveFilter(
							onEnter = {
								active.value = true
								true
							},
							onExit = {
								active.value = false
								true
							}
						))
			}
			if (entryPointNodes.isNotEmpty())
			{
				val entryIconModifier =
					Modifier
						.padding(start = 2.dp)
						.widthIn(max = 18.dp)
						.clickable {
							expanded.value = !expanded.value
							isExpanded = expanded.value
						}
				if (expanded.value)
				{
					AsyncSvg(
						resource = ImageResources.expandedModuleImage,
						modifier = entryIconModifier)
				}
				else
				{
					AsyncSvg(
						resource = ImageResources.collapsedModuleImage,
						modifier = entryIconModifier)
				}
			}
			if (building)
			{
				Spacer(Modifier.width(5.dp))
				Box (Modifier.fillMaxSize().align(Alignment.CenterVertically), Alignment.Center)
				{
					CircularProgressIndicator(
						modifier =
						Modifier.heightIn(max = 10.dp).widthIn(max = 10.dp),
						color = Color(0xFFAEB0B2),
						strokeWidth = 2.dp)
				}
			}
		}
		if (expanded.value)
		{
			val rowColor = ROW1
			entryPointNodes.forEach { it.draw(rowColor) }
		}
	}

	@Composable
	override fun ExpandableIcon(expanded: MutableState<Boolean>)
	{
		if (entryPointNodes.isNotEmpty())
		{
			val modifier =
				Modifier
					.clickable {
						expanded.value = !expanded.value
						isExpanded = expanded.value
					}
					.padding(start = indentPadding).widthIn(max = 18.dp)
			if (expanded.value)
			{
				AsyncSvg(
					resource = ImageResources.expandedModuleImage,
					modifier = modifier)
			}
			else
			{
				AsyncSvg(
					resource = ImageResources.collapsedModuleImage,
					modifier = modifier)
			}
		}
		else
		{
			Spacer(Modifier.padding(start = indentPadding).width(18.dp))
		}
	}

	@Composable
	override fun FileIcon()
	{
		AsyncImageBitmap(
			resource = ImageResources.moduleFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}

/**
 * An [AvailNode] that represents a [ResourceType.RESOURCE] node.
 *
 * @author Richard Arriaga.
 */
class ResourceNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	project: Project
): AvailNode(project, reference)
{
	override val resolver: ModuleRootResolver get() = parentNode.resolver

	@Composable
	override fun FileIcon()
	{
		AsyncSvg(
			resource = ImageResources.resourceFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
	}
}

/**
 * The node representation for entry points for a [ModuleNode].
 *
 * @author Richard Arriaga
 *
 * @property parent
 *   The parent [ModuleNode].
 * @property name
 *   The entry point method name.
 */
class EntryPointNode constructor(
	val parent: AvailNode,
	val name: String)
{
	val rawIndent by lazy { (parent.indent + 1) * 12 + 4 }

	/**
	 * Answer a [Composable] lambda that accepts a [AlternatingRowColor].
	 */
	@Composable
	fun draw (alternatingColor: AlternatingRowColor)
	{
		Row (
			modifier = Modifier
				.clickable { println("TODO: Run entry point $name") }
				.background(AvailColors.BG)
				.fillMaxWidth().
				padding(vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically)
		{
			Spacer(Modifier.width(20.dp))
			Spacer(Modifier.width(5.dp))
			AsyncSvg(
				resource = ImageResources.playArrowImage,
				modifier = Modifier
					.padding(start = rawIndent.dp)
					.widthIn(max = 14.dp))
			Spacer(Modifier.width(4.dp))
			Text(
				text = name,
				color = Color(0xFF3E86A0),
				fontSize = LoadedStyle.size)
		}
	}
}
