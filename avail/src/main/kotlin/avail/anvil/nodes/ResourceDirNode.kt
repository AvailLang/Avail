/*
 * RootConfigDirNode.kt
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

import avail.anvil.AvailWorkbench
import avail.resolver.ResolverReference
import org.availlang.artifact.ResourceType
import org.availlang.artifact.environment.project.AvailProjectRoot
import java.io.File
import java.nio.file.Files

/**
 * This is a tree node representing an [AvailProjectRoot]
 * [resource directory][ResourceType.DIRECTORY].
 *
 * @author Richard Arriaga
 *
 * @property workbench
 *   The [AvailWorkbench] this
 * @property reference
 *   `true` indicates this node is visible; `false` otherwise.
 */
class ResourceDirNode constructor(
	workbench: AvailWorkbench,
	val reference: ResolverReference
) : AbstractWorkbenchTreeNode(workbench)
{
	override fun modulePathString(): String = reference.localName

	override fun iconResourceName(): String = "avail-icon-directory-resource"

	override fun text(selected: Boolean) = reference.localName

	override fun htmlStyle(selected: Boolean): String =
		fontStyle(bold = true) +
			colorStyle(selected, false, false)

	/**
	 * The number of [children] this [ResourceDirNode] has.
	 */
	private val privateChildCount: Int? get() =
		if (children == null)
		{
			null
		}
		else
		{
			children.size
		}

	override val sortMajor: Int = 10

	/**
	 * Conditionally populate this [ResourceDirNode] with its child contents.
	 */
	private fun conditionallyPopulate ()
	{
		val c = privateChildCount
		if (c !== null && c > 0) return
		if (children == null)
		{
			val f = File(reference.uri)
			f.listFiles()?.forEach {
				val childRef = when
				{
					it.isDirectory ->
						ResolverReference(
							reference.resolver,
							it.toURI(),
							"${reference.qualifiedName}/${it.name}",
							ResourceType.DIRECTORY,
							"",
							0,
							0)
						.apply {
							this@ResourceDirNode.add(
								ResourceDirNode(workbench, this))
						}
					it.isFile ->
						ResolverReference(
							reference.resolver,
							it.toURI(),
							"${reference.qualifiedName}/${it.name}",
							ResourceType.RESOURCE,
							"",
							it.lastModified(),
							Files.size(it.toPath()))
						.apply {
							this@ResourceDirNode.add(
								ResourceNode(workbench, this))
						}
					else -> null
				}
				childRef?.let { cr ->
					reference.resources.add(cr)
				}

			}
		}
	}

	override fun isLeaf(): Boolean
	{
		if (children == null) conditionallyPopulate()
		return super.isLeaf()
	}
}
