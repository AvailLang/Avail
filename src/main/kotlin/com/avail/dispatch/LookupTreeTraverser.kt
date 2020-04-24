/*
 * LookupTreeTraverser.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.dispatch

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.types.A_Type
import com.avail.utility.Casts.cast
import java.util.*

/**
 * `LookupTreeTraverser` is used to enumerate the nodes of a [LookupTree].
 * Visitor methods are invoked at various stages of the traversal, and can
 * communicate at the same level by means of a traversal memento.
 *
 * @param Element
 *   The kind of elements in the lookup tree, such as method definitions.
 * @param Result
 *   What we expect to produce from a lookup activity, such as the tuple of
 *   most-specific matching method definitions for some arguments.
 * @param AdaptorMemento
 *   The adaptor for interpreting the values in the tree, and deciding how to
 *   narrow the elements that are still applicable at each internal node of the
 *   tree.
 * @param TraversalMemento
 *   The type of memento to pass between the `visit*` methods while traversing
 *   the tree.
 * @property adaptor
 *   The [LookupTreeAdaptor] with which to interpret the tree.
 * @property adaptorMemento
 *   A contextual parameter to assist the [adaptor].
 * @property expandAll
 *   Whether this traverser should expand every encountered internal node.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a new lookup tree traverser.
 *
 * @param adaptor
 *   The [LookupTreeAdaptor] with which to interpret the tree.
 * @param adaptorMemento
 *   A contextual parameter for the adaptor.
 * @param expandAll
 *   Whether to force expansion of every path of the tree.
 */
internal abstract class LookupTreeTraverser<
	Element : A_BasicObject,
	Result : A_BasicObject,
	AdaptorMemento,
	TraversalMemento>
protected constructor(
	private val adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
	private val adaptorMemento: AdaptorMemento,
	private val expandAll: Boolean)
{
	/**
	 * The stack of outstanding actions, which allows the tree to be traversed
	 * without recursion, only iteration.
	 */
	private val actionStack: MutableList<() -> Unit> = ArrayList()

	/**
	 * An expanded internal node has been reached, having expanded it if it
	 * wasn't already if [expandAll] is true.
	 *
	 * @param argumentIndex
	 *   The index of the argument position to test at this node.
	 * @param argumentType
	 *   The type to test the specified argument against at this node.
	 * @return
	 *   A traversal memento to be provided to other visit methods at this node.
	 */
	abstract fun visitPreInternalNode(
		argumentIndex: Int,
		argumentType: A_Type): TraversalMemento

	/**
	 * The [InternalLookupTree.ifCheckHolds] path has already been fully visited
	 * below some node, and the [InternalLookupTree.ifCheckFails] will be
	 * traversed next.  The argument is the TraversalMemento built by
	 * visitPreInternalNode for that node.
	 *
	 * @param memento
	 *   The memento constructed by a prior call of [visitPreInternalNode] on
	 *   the same node.
	 */
	open fun visitIntraInternalNode(
		memento: TraversalMemento)
	{
		// Do nothing by default.
	}

	/**
	 * An [InternalLookupTree] has now had both of its children fully visited.
	 */
	open fun visitPostInternalNode(memento: TraversalMemento)
	{
		// Do nothing by default.
	}

	/**
	 * Traversal has reached a leaf node representing the given Result.
	 *
	 * @param lookupResult
	 *   The [Result] represented by the current leaf node.
	 */
	abstract fun visitLeafNode(lookupResult: Result)

	/**
	 * An unexpanded [InternalLookupTree] has been reached.  This can only
	 * happen if [expandAll] is false.
	 */
	open fun visitUnexpanded()
	{
		// Do nothing by default.
	}

	/**
	 * A [LookupTree] node has just been reached.  Perform and/or queue any
	 * visit actions that will effect a depth-first traversal.
	 *
	 * @param node
	 *   The lookup tree node that was reached.
	 */
	private fun visit(node: LookupTree<Element, Result>)
	{
		val solution = node.solutionOrNull
		if (solution !== null)
		{
			visitLeafNode(solution)
			return
		}
		val internalNode: InternalLookupTree<Element, Result> = cast(node)
		if (expandAll)
		{
			internalNode.expandIfNecessary(adaptor, adaptorMemento)
		}
		else if (!internalNode.isExpanded)
		{
			// This internal node isn't expanded.
			visitUnexpanded()
			return
		}
		// Create a memento to share between the below actions operating at the
		// same position in the tree.  Push them in *reverse* order of their
		// execution.
		var memento: TraversalMemento? = null
		actionStack.addAll(
			arrayOf(
				{ visitPostInternalNode(memento!!) },
				{ visit(internalNode.ifCheckFails!!) },
				{ visitIntraInternalNode(memento!!) },
				{ visit(internalNode.ifCheckHolds!!) },
				{
					memento = visitPreInternalNode(
						internalNode.argumentPositionToTest,
						internalNode.argumentTypeToTest!!)
				}))
	}

	/**
	 * Visit every node in the tree, starting at the specified node.  The
	 * appropriate visit methods will be invoked for each node in depth-first
	 * order.
	 *
	 * @param node
	 *   The root node of the traversal.
	 */
	fun traverseEntireTree(node: LookupTree<Element, Result>)
	{
		visit(node)
		while (actionStack.isNotEmpty())
		{
			actionStack.removeAt(actionStack.size - 1)()
		}
	}
}
