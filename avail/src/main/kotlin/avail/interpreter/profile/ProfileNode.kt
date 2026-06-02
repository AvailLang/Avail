/*
 * ProfileNode.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.profile;

/**
 * A [ProfileNode] is a node of an aggregate call tree reconstructed by a
 * [ProfileReconstructor] operating on files produced by [ProfileCollector]s.
 *
 * @constructor
 *   Create a new [ProfileNode].
 * @property profileFunction
 *   The [ProfileFunction] that was invoked one or more times to produce this
 *   node.
 * @property parent
 *   If known, the parent of this node in the aggregate call tree.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ProfileNode(
	val profileFunction: ProfileFunction,
	var parent: ProfileNode? = null)
{
	/** The total amount of time spent in this node, including [children]. */
	var totalTime = 0L

	/** How many time this node's function was called. */
	var invocations = 0L

	/**
	 * The [ProfileNode]s representing functions called in the scope of
	 * executing this node.
	 */
	val children = mutableMapOf<ProfileFunction, ProfileNode>()

	/**
	 * Calculate the time spent in this node but not in any descendants. Because
	 * we capture total time in each node, this calculation is just a matter of
	 * starting with this node's [totalTime] and subtracting all the children's
	 * [totalTime]s.
	 */
	val localTime = totalTime - children.values.sumOf { it.totalTime }

	/**
	 * Add the information from the given [profileNode] to this node, altering
	 * it.  It must represent the same [ProfileFunction] as this node.  Note
	 * that some descendants of [profileNode] may end up under this node, and be
	 * subject to additional updates if other trees are merged in.
	 */
	fun aggregateNode(profileNode: ProfileNode)
	{
		assert(profileFunction == profileNode.profileFunction)
		totalTime += profileNode.totalTime
		invocations += profileNode.invocations
		profileNode.children.values.forEach { otherChild ->
			when (val existingChild = children[otherChild.profileFunction])
			{
				null ->
				{
					// Reuse otherChild, even though it might be altered later
					// by merging in other trees.  Also switch its parent to
					// this node.
					children[otherChild.profileFunction] = otherChild
					otherChild.parent = this
				}
				else -> existingChild.aggregateNode(otherChild)
			}
		}
	}
}
