/**
 * LeafLookupTree.java
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
import org.jetbrains.annotations.Nullable;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;

import java.util.List;

import static com.avail.descriptor.AvailObject.error;

/**
 * A {@code LookupTree} representing a solution.
 */
public final class LeafLookupTree<
	Element extends A_BasicObject, Result extends A_BasicObject, Memento>
extends LookupTree<Element, Result, Memento>
{
	/** The result of the lookup. */
	private final Result finalResult;

	/**
	 * Construct a new {@link LeafLookupTree}.
	 *
	 * @param finalResult
	 *        The most specific definitions for the provided arguments.  Thus,
	 *        if this is empty, there are no applicable definitions, and if
	 *        there's more than one element the actual call is ambiguous.
	 */
	public LeafLookupTree (final Result finalResult)
	{
		this.finalResult = finalResult;
	}

	@Override
	protected @Nullable Result solutionOrNull ()
	{
		return finalResult;
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByTypes (
		final List<? extends A_Type> argTypes,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		error("Attempting to lookup past leaf of decision tree");
		return this;
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByTypes (
		final A_Tuple argTypes,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		error("Attempting to lookup past leaf of decision tree");
		return this;
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByValues (
		final List<? extends A_BasicObject> argValues,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		error("Attempting to lookup past leaf of decision tree");
		return this;
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByValues (
		final A_Tuple argValues,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		error("Attempting to lookup past leaf of decision tree");
		return this;
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByValue (
		final A_BasicObject probeValue,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		error("Attempting to lookup past leaf of decision tree");
		return this;
	}

	@Override
	public final String toString (final int indent)
	{
		// Special case tuples for easier debugging.  Assume that tupleSize = 1
		// means success.
		if (finalResult.isTuple())
		{
			final A_Tuple finalTuple = (A_Tuple)finalResult;
			final int tupleSize = finalTuple.tupleSize();
			if (tupleSize == 1)
			{
				// Assume for now that it's a tuple of definitions.
				return String.format(
					"Success: %s",
					finalTuple.tupleAt(1).bodySignature().argsTupleType());
			}
			return String.format(
				"Failure: (%d solutions)",
				tupleSize);
		}
		else
		{
			return String.format("Result: %s", finalResult);
		}
	}
}
