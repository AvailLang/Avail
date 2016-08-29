package com.avail.dispatch;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.MethodDescriptor;

import java.util.List;

import static com.avail.descriptor.AvailObject.error;

/**
 * A {@code LookupTree} representing a solution.
 */
public final class LeafLookupTree extends LookupTree
{
	/** The result of the lookup, zero or more most-specific definitions. */
	private final List<A_Definition> finalResult;

	/**
	 * Construct a new {@link LeafLookupTree}.
	 *
	 * @param finalResult
	 *            The most specific definitions for the provided arguments.
	 *            Thus, if this is empty, there are no applicable
	 *            definitions, and if there's more than one element the
	 *            actual call is ambiguous.
	 */
	public LeafLookupTree (final List<A_Definition> finalResult)
	{
		this.finalResult = finalResult;
	}

	@Override
	protected List<A_Definition> solutionOrNull()
	{
		return finalResult;
	}

	@Override
	protected LookupTree lookupStepByTypes (
		final List<? extends A_Type> argTypes)
	{
		error("Attempting to lookup past leaf of decision tree");
		return this;
	}

	@Override
	protected LookupTree lookupStepByValues (
		final List<? extends A_BasicObject> argValues)
	{
		error("Attempting to lookup past leaf of decision tree");
		return this;
	}

	@Override
	protected LookupTree lookupStepByValues (
		final A_Tuple argValues)
	{
		error("Attempting to lookup past leaf of decision tree");
		return this;
	}

	@Override
	public final String toString (final int indent)
	{
		if (finalResult.size() == 1)
		{
			return String.format(
				"Success: %s",
				finalResult.get(0).bodySignature().argsTupleType());
		}
		return String.format(
			"Failure: (%d solutions)",
			finalResult.size());
	}
}
