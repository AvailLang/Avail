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

package org.availlang.ide.anvil.models

import avail.builder.AvailBuilder
import avail.builder.ModuleRoot
import avail.builder.ResolvedModuleName
import avail.resolver.ModuleRootResolver
import avail.resolver.ResolverReference
import avail.resolver.ResourceType

/**
 * A node in the file tree.
 *
 * @author Richard Arriaga.
 *
 * @property builder
 *   The [AvailBuilder] for the represented [AvailProject].
 * @property reference
 *   The [ResolverReference] that this [AvailNode] points to.
 */
sealed class AvailNode constructor(
	val availProject: AvailProject,
	val reference: ResolverReference): Comparable<AvailNode>
{
	val builder: AvailBuilder get() = availProject.builder

	/**
	 * This [AvailNode]'s immediate parent node.
	 */
	abstract val parentNode: AvailNode?

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
	 * `true` indicates there are [children]; `false` otherwise.
	 */
	protected val hasChildren get() = children.isNotEmpty()

	protected fun build (then: () -> Unit): Boolean =
		availProject.build(reference.qualifiedName, then)

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
	}

	/**
	 * The list of [children] nodes in a sorted list.
	 */
	val sortedChildren : List<AvailNode> get() =
		children.values.toList().sorted()

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

	override fun toString(): String = reference.localName
}

/**
 * An [AvailNode] that represents a [ResourceType.ROOT] node.
 *
 * @author Richard Arriaga.
 */
class RootNode constructor(
	availProject: AvailProject,
	reference: ResolverReference,
	val root: ModuleRoot
): AvailNode(availProject, reference)
{
	override val isDirectory: Boolean = true
	override val parentNode: AvailNode? = null
	override val resolver: ModuleRootResolver get() = root.resolver
}

/**
 * An [AvailNode] that represents a [ResourceType.DIRECTORY] node.
 *
 * @author Richard Arriaga.
 */
class DirectoryNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	availProject: AvailProject
): AvailNode(availProject, reference)
{
	override val isDirectory: Boolean = true
	override val resolver: ModuleRootResolver get() = parentNode.resolver
}

/**
 * An [AvailNode] that represents a [ResourceType.PACKAGE] node.
 *
 * @author Richard Arriaga.
 */
class ModulePackageNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	availProject: AvailProject
): AvailNode(availProject, reference)
{
	override val isDirectory: Boolean = true
	override val resolver: ModuleRootResolver get() = parentNode.resolver
	val entryPointNodes =
		mutableListOf<EntryPointNode>()

	/**
	 * The [ResolvedModuleName] this node represents.
	 */
	val resolved: ResolvedModuleName =
		availProject.moduleNameResolver.resolve(reference.moduleName)

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
	availProject: AvailProject
): AvailNode(availProject, reference)
{
	override val resolver: ModuleRootResolver get() = parentNode.resolver
	val entryPointNodes =
		mutableListOf<EntryPointNode>()

	/**
	 * The [ResolvedModuleName] this node represents.
	 */
	val resolved: ResolvedModuleName =
		availProject.moduleNameResolver.resolve(reference.moduleName)

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
}

/**
 * An [AvailNode] that represents a [ResourceType.RESOURCE] node.
 *
 * @author Richard Arriaga.
 */
class ResourceNode constructor(
	override val parentNode: AvailNode,
	reference: ResolverReference,
	availProject: AvailProject
): AvailNode(availProject, reference)
{
	override val resolver: ModuleRootResolver get() = parentNode.resolver
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
