package com.avail.dispatch;
import com.avail.annotations.Nullable;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.DefinitionDescriptor;
import com.avail.descriptor.MethodDescriptor;
import com.avail.exceptions.MethodDefinitionException;
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
public abstract class LookupTree
{
	/**
	 * Lookup the most-specific definitions that match the provided list of
	 * arguments.  An empty list indicates there were no applicable
	 * definitions.  More than one entry indicates an ambiguous lookup, due
	 * to multiple incomparable, locally most-specific definitions.
	 *
	 * @param argValues The arguments of the call.
	 * @return The list of most applicable definitions.
	 */
	protected abstract LookupTree lookupStepByValues (
		List<? extends A_BasicObject> argValues);

	/**
	 * Lookup the most-specific definitions that match the provided tuple of
	 * arguments.  An empty list indicates there were no applicable
	 * definitions.  More than one entry indicates an ambiguous lookup, due
	 * to multiple incomparable, locally most-specific definitions.
	 *
	 * @param argValues The arguments of the call.
	 * @return The list of most applicable definitions.
	 */
	protected abstract LookupTree lookupStepByValues (
		A_Tuple argValues);

	/**
	 * Lookup the most-specific definitions that satisfy the provided
	 * argument types.  An empty list indicates there were no applicable
	 * definitions.  More than one entry indicates an ambiguous lookup, due
	 * to multiple incomparable, locally most-specific definitions.
	 *
	 * @param argTypes The types of arguments of the call.
	 * @return The list of most applicable definitions.
	 */
	protected abstract LookupTree lookupStepByTypes (
		List<? extends A_Type> argTypes);

	/**
	 * Answer the lookup solution ({@link List} of {@linkplain A_Definition
	 * definitions} at this leaf node, or {@code null} if this is not a leaf
	 * node.
	 *
	 * @return The solution or null.
	 */
	protected abstract @Nullable List<A_Definition> solutionOrNull ();

	/**
	 * Create a {@link LookupTree}, using the provided list of {@link
	 * DefinitionDescriptor definitions} of a {@link MethodDescriptor
	 * method}.
	 *
	 * @param numArgs
	 *        The number of top-level arguments expected by the definitions.
	 * @param allDefinitions
	 *        The list of definitions.
	 * @param knownArgumentTypes
	 *        The initial knowledge about the argument types.
	 * @return A LookupTree, potentially lazy, suitable for dispatching.
	 */
	public static LookupTree createRoot (
		final int numArgs,
		final List<A_Definition> allDefinitions,
		final List<A_Type> knownArgumentTypes)
	{
		final List<A_Definition> prequalified = new ArrayList<>(1);
		final List<A_Definition> undecided =
			new ArrayList<>(allDefinitions.size());
		for (final A_Definition definition : allDefinitions)
		{
			final A_Type argsTupleType =
				definition.bodySignature().argsTupleType();
			boolean allComply = true;
			boolean impossible = false;
			for (int i = 1; i <= numArgs; i++)
			{
				final A_Type knownType = knownArgumentTypes.get(i - 1);
				final A_Type definitionArgType =
					argsTupleType.typeAtIndex(i);
				if (!knownType.isSubtypeOf(definitionArgType))
				{
					allComply = false;
				}
				if (knownType.typeIntersection(definitionArgType)
					.isBottom())
				{
					impossible = true;
				}
			}
			if (allComply)
			{
				prequalified.add(definition);
			}
			else if (!impossible)
			{
				undecided.add(definition);
			}
		}
		final LookupTree tree = LookupTree.createTree(
			prequalified,
			undecided,
			knownArgumentTypes);
		return tree;
	}

	/**
	 * Create a LookupTree suitable for deciding which definition to use
	 * when actual argument types are provided.
	 *
	 * @param positive
	 *            Definitions which definitely apply at this node.
	 * @param undecided
	 *            Definitions which are not known to apply or not apply at
	 *            this node.
	 * @param knownArgumentTypes
	 *            The types that the arguments are known to comply with at
	 *            this point.
	 * @return A (potentially lazy) LookupTree used to look up method
	 *         definitions during calls.
	 */
	public static LookupTree createTree (
		final List<A_Definition> positive,
		final List<A_Definition> undecided,
		final List<A_Type> knownArgumentTypes)
	{
		if (undecided.size() == 0)
		{
			// Find the most specific applicable definitions.
			if (positive.size() <= 1)
			{
				return new LeafLookupTree(positive);
			}
			final int size = positive.size();
			final List<A_Definition> mostSpecific =
				new ArrayList<>(1);
			outer: for (int outer = 0; outer < size; outer++)
			{
				final A_Type outerType =
					positive.get(outer).bodySignature();
				for (int inner = 0; inner < size; inner++)
				{
					if (outer != inner)
					{
						final A_Type innerType =
							positive.get(inner).bodySignature();
						if (outerType.acceptsArgTypesFromFunctionType(
							innerType))
						{
							// A more specific definition was found
							// (i.e., inner was more specific than outer).
							// This disqualifies outer from being considered
							// most specific.
							continue outer;
						}
					}
				}
				mostSpecific.add(positive.get(outer));
			}
			return new LeafLookupTree(mostSpecific);
		}
		return new InternalLookupTree(
			positive, undecided, knownArgumentTypes);
	}

	/**
	 * Use the list of types to traverse the tree.  Answer the solution, a
	 * {@link A_Definition}, or throw a suitable {@link
	 * MethodDefinitionException} if the definition con not be uniquely
	 * determined.
	 *
	 * @param argumentTypesList
	 *        The input {@link List} of {@link A_Type}s.
	 * @return The unique {@link A_Definition}s
	 * @throws MethodDefinitionException If the solution is not unique.
	 */
	public A_Definition lookupByTypes (
		final List<A_Type> argumentTypesList)
	throws MethodDefinitionException
	{
		LookupTree tree = this;
		List<A_Definition> solutions;
		while ((solutions = tree.solutionOrNull()) == null)
		{
			tree = tree.lookupStepByTypes(argumentTypesList);
		}
		if (solutions.size() != 1)
		{
			throw solutions.size() == 0
				? MethodDefinitionException.noMethodDefinition()
				: MethodDefinitionException.ambiguousMethodDefinition();
		}
		return solutions.get(0);
	}

	/**
	 * Use the list of values to traverse the tree.  Answer the solution, a
	 * {@link A_Definition}, or throw a suitable {@link
	 * MethodDefinitionException} if the definition con not be uniquely
	 * determined.
	 *
	 * @param argValues
	 *        The input {@link List} of {@link A_BasicObject}s.
	 * @return The unique {@link A_Definition}s
	 * @throws MethodDefinitionException If the solution is not unique.
	 */
	public A_Definition lookupByValues (
		final List<? extends A_BasicObject> argValues)
	throws MethodDefinitionException
	{
		LookupTree tree = this;
		List<A_Definition> solutions;
		while ((solutions = tree.solutionOrNull()) == null)
		{
			tree = tree.lookupStepByValues(argValues);
		}
		if (solutions.size() != 1)
		{
			throw solutions.size() == 0
				? MethodDefinitionException.noMethodDefinition()
				: MethodDefinitionException.ambiguousMethodDefinition();
		}
		return solutions.get(0);
	}

	/**
	 * Use the {@link A_Tuple} of values to traverse the tree.  Answer the
	 * {@link A_Definition}, or throw a suitable {@link
	 * MethodDefinitionException} if the definition con not be uniquely
	 * determined.
	 *
	 * @param argValues
	 *        The {@link A_Tuple} of values.
	 * @return The unique {@link A_Definition}s
	 * @throws MethodDefinitionException If the solution is not unique.
	 */
	public A_Definition lookupByValues (
		final A_Tuple argValues)
	throws MethodDefinitionException
	{
		LookupTree tree = this;
		List<A_Definition> solutions;
		while ((solutions = tree.solutionOrNull()) == null)
		{
			tree = tree.lookupStepByValues(argValues);
		}
		if (solutions.size() != 1)
		{
			throw solutions.size() == 0
				? MethodDefinitionException.noMethodDefinition()
				: MethodDefinitionException.ambiguousMethodDefinition();
		}
		return solutions.get(0);
	}

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
	public final <Memento> void traverseEntireTree (
		final Transformer2<Integer, A_Type, Memento> preInternalNode,
		final Continuation1<Memento> intraInternalNode,
		final Continuation1<Memento> postInternalNode,
		final Continuation1<List<A_Definition>> forEachLeafNode)
	{
		final List<Continuation0> actionStack = new ArrayList<>();
		final MutableOrNull<Continuation1<LookupTree>> visit =
			new MutableOrNull<>();
		visit.value = new Continuation1<LookupTree>()
		{
			@Override
			public void value (final @Nullable LookupTree node)
			{
				assert node != null;
				final List<A_Definition> solution =
					node.solutionOrNull();
				if (solution != null)
				{
					forEachLeafNode.value(solution);
				}
				else
				{
					final InternalLookupTree internalNode =
						(InternalLookupTree) node;
					internalNode.expandIfNecessary();
					final MutableOrNull<Memento> memento =
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
