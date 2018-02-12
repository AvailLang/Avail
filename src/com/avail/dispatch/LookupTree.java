/*
 * LookupTree.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;

import javax.annotation.Nullable;
import java.util.List;

/**
 * {@code LookupTree} is used to look up method definitions by argument types,
 * although it's more general than that.
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
 */
public abstract class LookupTree<
	Element extends A_BasicObject,
	Result extends A_BasicObject,
	AdaptorMemento>
{
	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided list of argument types.  Answer another {@link
	 * LookupTree} with which to continue the search.  Requires {@link
	 * #solutionOrNull()} to be null, indicating the search has not concluded.
	 *
	 * @param argTypes The {@link List list} of argument types being looked up.
	 * @return The next {@code LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, AdaptorMemento>
		lookupStepByTypes(
			List<? extends A_Type> argTypes,
			LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
			AdaptorMemento memento);

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided tuple of argument types.  Answer another {@link
	 * LookupTree} with which to continue the search.  Requires {@link
	 * #solutionOrNull()} to be null, indicating the search has not concluded.
	 *
	 * @param argTypes
	 *        The {@link A_Tuple tuple} of argument types being looked up.
	 * @return The next {@code LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, AdaptorMemento>
		lookupStepByTypes(
			A_Tuple argTypes,
			LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
			AdaptorMemento memento);

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided list of arguments.  Answer another {@link
	 * LookupTree} with which to continue the search.  Requires {@link
	 * #solutionOrNull()} to be null, indicating the search has not concluded.
	 *
	 * @param argValues The {@link List} of arguments being looked up.
	 * @return The next {@code LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, AdaptorMemento>
		lookupStepByValues (
			List<? extends A_BasicObject> argValues,
			LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
			AdaptorMemento memento);

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided tuple of arguments.  Answer another {@link
	 * LookupTree} with which to continue the search.  Requires {@link
	 * #solutionOrNull()} to be null, indicating the search has not concluded.
	 *
	 * @param argValues The {@link A_Tuple tuple} of arguments being looked up.
	 * @return The next {@code LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, AdaptorMemento>
		lookupStepByValues (
			A_Tuple argValues,
			LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
			AdaptorMemento memento);

	/**
	 * Perform one step of looking up the most-specific {@link Result} that
	 * matches the provided value.  Answer another {@code LookupTree} with which
	 * to continue the search.  Requires {@link #solutionOrNull()} to be null,
	 * indicating the search has not concluded.
	 *
	 * @param probeValue The value being looked up.
	 * @return The next {@code LookupTree} to search.
	 */
	protected abstract LookupTree<Element, Result, AdaptorMemento>
		lookupStepByValue (
			final A_BasicObject probeValue,
			LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
			final AdaptorMemento memento);

	/**
	 * Answer the lookup solution ({@link List} of {@linkplain A_Definition
	 * definitions} at this leaf node, or {@code null} if this is not a leaf
	 * node.
	 *
	 * @return The solution or null.
	 */
	protected abstract @Nullable Result solutionOrNull ();

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
