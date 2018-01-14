/**
 * LookupTree.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.dispatch;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code LookupTreeTraverser} is used to enumerate the nodes of a {@link
 * LookupTree}.  Visitor methods are invoked at various stages of the traversal,
 * and can communicate at the same level by means of a traversal memento.
 *
 * @param <Element>
 *        The kind of elements in the lookup tree, such as method definitions.
 * @param <Result>
 *        What we expect to produce from a lookup activity, such as the tuple of
 *        most-specific matching method definitions for some arguments.
 * @param <AdaptorMemento>
 *        The adaptor for interpreting the values in the tree, and deciding how
 *        to narrow the elements that are still applicable at each internal
 *        node of the tree.
 * @param <TraversalMemento>
 *        The type of memento to pass between the visit* methods while
 *        traversing the tree.
 */
public abstract class LookupTreeTraverser<
	Element extends A_BasicObject,
	Result extends A_BasicObject,
	AdaptorMemento,
	TraversalMemento>
{
	/** The {@link LookupTreeAdaptor} with which to interpret the tree. */
	final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor;

	/** A contextual parameter to assist the {@link #adaptor}. */
	final AdaptorMemento adaptorMemento;

	/**
	 * The stack of outstanding actions, which allows the tree to be traversed
	 * without recursion, only iteration.
	 */
	final List<Continuation0> actionStack = new ArrayList<>();

	/** Whether this traverser should expand every encountered internal node. */
	final boolean expandAll;

	/**
	 * Create a new lookup tree traverser.
	 *
	 * @param adaptor
	 *        The {@link LookupTreeAdaptor} with which to interpret the tree.
	 * @param adaptorMemento
	 *        A contextual parameter for the adaptor.
	 * @param expandAll
	 *        Whether to automatically expand every encountered internal node.
	 */
	public LookupTreeTraverser (
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento adaptorMemento,
		final boolean expandAll)
	{
		this.adaptor = adaptor;
		this.adaptorMemento = adaptorMemento;
		this.expandAll = expandAll;
	}

	/**
	 * An expanded internal node has been reached, having expanded it if it
	 * wasn't already if {@link #expandAll} is true.
	 *
	 * @param argumentIndex
	 *        The index of the argument position to test at this node.
	 * @param argumentType
	 *        The type to test the specified argument against at this node.
	 * @return A traversal memento to be provided to other visit methods at this
	 *         node.
	 */
	public abstract TraversalMemento visitPreInternalNode (
		final int argumentIndex,
		final A_Type argumentType);

	/**
	 * The {@link InternalLookupTree#ifCheckHolds()} path has already been fully
	 * visited below some node, and the {@link
	 * InternalLookupTree#ifCheckFails()} will be traversed next.  The argument
	 * is the TraversalMemento built by visitPreInternalNode for that node.
	 *
	 * @param memento
	 *        The memento constructed by a prior call of {@link
	 *        #visitPreInternalNode(int, A_Type)} it the same node.
	 */
	public void visitIntraInternalNode (
		final TraversalMemento memento)
	{
		// Do nothing by default.
	};

	/**
	 * An {@link InternalLookupTree} has now had both of its children fully
	 * visited.
	 */
	public void visitPostInternalNode (final TraversalMemento memento)
	{
		// Do nothing by default.
	};

	/**
	 * Traversal has reached a leaf node representing the given Result.
	 *
	 * @param lookupResult
	 *        The Result represented by the current leaf node.
	 */
	public abstract void visitLeafNode (Result lookupResult);

	/**
	 * An unexpanded {@link InternalLookupTree} has been reached.  This can only
	 * happen if {@link #expandAll} is false.
	 */
	public void visitUnexpanded ()
	{
		// Do nothing by default.
	};

	/**
	 * A {@link LookupTree} node has just been reached.  Perform and/or queue
	 * any visit actions that will effect a depth-first traversal.
	 *
	 * @param node The lookup tree node that was reached.
	 */
	private void visit (final LookupTree<Element, Result, AdaptorMemento> node)
	{
		final @Nullable Result solution = node.solutionOrNull();
		if (solution != null)
		{
			visitLeafNode(solution);
			return;
		}
		final InternalLookupTree<Element, Result, AdaptorMemento>
			internalNode =
				(InternalLookupTree<Element, Result, AdaptorMemento>) node;
		if (expandAll)
		{
			internalNode.expandIfNecessary(adaptor, adaptorMemento);
		}
		else if (!internalNode.isExpanded())
		{
			// This internal node isn't expanded.
			visitUnexpanded();
			return;
		}
		// Create a memento to share between the below actions operating at the
		// same position in the tree.  Push them in *reverse* order of their
		// execution.
		final MutableOrNull<TraversalMemento> memento = new MutableOrNull<>();
		actionStack.add(() -> visitPostInternalNode(memento.value));
		actionStack.add(() -> visit(internalNode.ifCheckFails()));
		actionStack.add(() -> visitIntraInternalNode(memento.value));
		actionStack.add(() -> visit(internalNode.ifCheckHolds()));
		actionStack.add(() -> memento.value = visitPreInternalNode(
			internalNode.argumentPositionToTest,
			internalNode.argumentTypeToTest()));
	}

	/**
	 * Visit every node in the tree, starting at the specified node.  The
	 * appropriate visit methods will be invoked for each node in depth-first
	 * order.
	 *
	 * @param node The root node of the traversal.
	 */
	public final void traverseEntireTree (
		final LookupTree<Element, Result, AdaptorMemento> node)
	{
		visit(node);
		while (!actionStack.isEmpty())
		{
			actionStack.remove(actionStack.size() - 1).value();
		}
	}
}
