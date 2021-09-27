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

import androidx.compose.runtime.Composable
import avail.anvil.models.Project
import avail.anvil.themes.AlternatingRowColor
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
	 * The [ModuleRootResolver] used for the [ModuleRootView] this node is part of.
	 */
	abstract val resolver: ModuleRootResolver

	/**
	 * The [Map] from [ResolverReference.qualifiedName] to the corresponding
	 * [AvailNode].
	 */
	val children = mutableMapOf<String, AvailNode>()

	/**
	 * Add the [AvailNode] to this node's [children].
	 */
	fun addChild (node: AvailNode)
	{
		children[node.reference.qualifiedName] = node
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
	abstract fun draw (alternatingColor: AlternatingRowColor)

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
	override val parentNode: AvailNode? = null

	override val resolver: ModuleRootResolver get() = root.resolver

	@Composable
	override fun draw (alternatingColor: AlternatingRowColor)
	{
		ModuleRootView(this@RootNode)
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
	override val resolver: ModuleRootResolver get() = parentNode.resolver

	@Composable
	override fun draw (alternatingColor: AlternatingRowColor)
	{
		ModulePackageView(this@ModulePackageNode, alternatingColor)
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
	override val resolver: ModuleRootResolver get() = parentNode.resolver

	@Composable
	override fun draw (alternatingColor: AlternatingRowColor)
	{
		DirectoryView(this@DirectoryNode, alternatingColor)
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
	@Composable
	override fun draw (alternatingColor: AlternatingRowColor)
	{
		// TODO get entrie points
		ModuleView(this@ModuleNode, alternatingColor)
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
	override fun draw (alternatingColor: AlternatingRowColor)
	{
		ResourceView(this@ResourceNode, alternatingColor)
	}
}
