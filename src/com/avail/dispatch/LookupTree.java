/**
 * LookupTree.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import com.avail.annotations.InnerAccess;
import com.avail.compiler.splitter.MessageSplitter;
import org.jetbrains.annotations.Nullable;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Transformer2;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code LookupTree} is used to look up method definitions during calls,
 * based on the passed arguments or argument types.
 */
public abstract class LookupTree<
	Element extends A_BasicObject,
	Result extends A_BasicObject,
	Memento>
{
	/**
	 * Answer the index of the given permutation (tuple of integers), adding it
	 * to the global {@link MessageSplitter#constantsList} if necessary.
	 *
	 * @param permutation
	 *        The permutation whose globally unique one-based index should be
	 *        determined.
	 * @return The permutation's one-based index.
	 */
	@InnerAccess
	public static int indexForPermutation (final A_Tuple permutation)
	{
		int checkedLimit = 0;
		while (true)
		{
			final A_Tuple before = MessageSplitter.permutations.get();
			final int newLimit = before.tupleSize();
			for (int i = checkedLimit + 1; i <= newLimit; i++)
			{
				if (before.tupleAt(i).equals(permutation))
				{
					// Already exists.
					return i;
				}
			}
			final A_Tuple after =
				before.appendCanDestroy(permutation, false).makeShared();
			if (MessageSplitter.permutations.compareAndSet(before, after))
			{
				// Added it successfully.
				return after.tupleSize();
			}
			checkedLimit = newLimit;
		}
	}

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided list of argument types.  Answer another {@link
	 * LookupTree} with which to continue the search.  Requires {@link
	 * #solutionOrNull()} to be null, indicating the search has not concluded.
	 *
	 * @param argTypes The {@link List list} of argument types being looked up.
	 * @return The next {@link LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, Memento> lookupStepByTypes(
		List<? extends A_Type> argTypes,
		LookupTreeAdaptor<Element, Result, Memento> adaptor,
		Memento memento);

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided tuple of argument types.  Answer another {@link
	 * LookupTree} with which to continue the search.  Requires {@link
	 * #solutionOrNull()} to be null, indicating the search has not concluded.
	 *
	 * @param argTypes
	 *        The {@link A_Tuple tuple} of argument types being looked up.
	 * @return The next {@link LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, Memento> lookupStepByTypes(
		A_Tuple argTypes,
		LookupTreeAdaptor<Element, Result, Memento> adaptor,
		Memento memento);

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided list of arguments.  Answer another {@link
	 * LookupTree} with which to continue the search.  Requires {@link
	 * #solutionOrNull()} to be null, indicating the search has not concluded.
	 *
	 * @param argValues The {@link List} of arguments being looked up.
	 * @return The next {@link LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, Memento> lookupStepByValues (
		List<? extends A_BasicObject> argValues,
		LookupTreeAdaptor<Element, Result, Memento> adaptor,
		Memento memento);

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided tuple of arguments.  Answer another {@link
	 * LookupTree} with which to continue the search.  Requires {@link
	 * #solutionOrNull()} to be null, indicating the search has not concluded.
	 *
	 * @param argValues The {@link A_Tuple tuple} of arguments being looked up.
	 * @return The next {@link LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, Memento> lookupStepByValues (
		A_Tuple argValues,
		LookupTreeAdaptor<Element, Result, Memento> adaptor,
		Memento memento);

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided value.  Answer another {@link LookupTree} with which
	 * to continue the search.  Requires {@link #solutionOrNull()} to be null,
	 * indicating the search has not concluded.
	 *
	 * @param probeValue The value being looked up.
	 * @return The next {@link LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, Memento> lookupStepByValue (
		final A_BasicObject probeValue,
		LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento);

	/**
	 * Answer the lookup solution ({@link List} of {@linkplain A_Definition
	 * definitions} at this leaf node, or {@code null} if this is not a leaf
	 * node.
	 *
	 * @return The solution or null.
	 */
	protected abstract @Nullable Result solutionOrNull ();

	/**
	 * Traverse the entire {@link LookupTree}, expanding nodes as necessary.
	 * For each leaf node, invoke the forEachLeafNode {@link Continuation1
	 * continuation}.  For each non-leaf node, first invoke the
	 * preInternalNode, save the memento, recurse into the ifCheckHolds
	 * subtree, invoke the intraInternalNode with the saved memento, recurse
	 * into the ifCheckFails subtree, then invoke the postInternalNode with
	 * the same memento as before.
	 *
	 * @param preInternalNode
	 *            What to do with the the argument number and criterion type
	 *            found at each non-leaf node.  It must produce a memento to
	 *            be passed (eventually) to the next two operations.
	 * @param intraInternalNode
	 *            What to do between the two branches of a non-leaf, given
	 *            the Memento produced before the first branch.
	 * @param postInternalNode
	 *            What to do after the second branch of a non-leaf, given
	 *            the Memento produced before the first branch.
	 * @param forEachLeafNode
	 *            What to do with the {@link List} of {@linkplain
	 *            A_Definition definitions} found in a leaf node.
	 */
	public final <M> void traverseEntireTree (
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento adaptorMemento,
		final Transformer2<Integer, A_Type, M> preInternalNode,
		final Continuation1<M> intraInternalNode,
		final Continuation1<M> postInternalNode,
		final Continuation1<Result> forEachLeafNode)
	{
		final List<Continuation0> actionStack = new ArrayList<>();
		final MutableOrNull<
				Continuation1<LookupTree<Element, Result, Memento>>>
			visit = new MutableOrNull<>();
		visit.value = new Continuation1<LookupTree<Element, Result, Memento>>()
		{
			@Override
			public void value (
				final @Nullable LookupTree<Element, Result, Memento> node)
			{
				assert node != null;
				final @Nullable Result solution = node.solutionOrNull();
				if (solution != null)
				{
					forEachLeafNode.value(solution);
				}
				else
				{
					final InternalLookupTree<Element, Result, Memento>
						internalNode =
							(InternalLookupTree<Element, Result, Memento>) node;
					internalNode.expandIfNecessary(adaptor, adaptorMemento);
					final MutableOrNull<M> memento =
						new MutableOrNull<>();
					// Push some actions in *reverse* order of their
					// execution.
					actionStack.add(new Continuation0()
					{
						@Override
						public void value ()
						{
							postInternalNode.value(memento.value());
						}
					});
					actionStack.add(new Continuation0()
					{
						@Override
						public void value ()
						{
							visit.value().value(
								internalNode.ifCheckFails());
						}
					});
					actionStack.add(new Continuation0()
					{
						@Override
						public void value ()
						{
							intraInternalNode.value(memento.value());
						}
					});
					actionStack.add(new Continuation0()
					{
						@Override
						public void value ()
						{
							visit.value().value(
								internalNode.ifCheckHolds());
						}
					});
					actionStack.add(new Continuation0()
					{
						@Override
						public void value ()
						{
							memento.value = preInternalNode.value(
								internalNode.argumentPositionToTest,
								internalNode.argumentTypeToTest());
						}
					});
				}
			}
		};
		visit.value().value(this);
		while (!actionStack.isEmpty())
		{
			actionStack.remove(actionStack.size() - 1).value();
		}
	}

	/**
	 * Describe this {@code LookupTree} at a given indent level.
	 *
	 * @param indent How much to indent.
	 * @return The description of this {@code LookupTree}
	 */
	public abstract String toString (final int indent);

	@Override
	public final String toString ()
	{
		return toString(0);
	}
}
