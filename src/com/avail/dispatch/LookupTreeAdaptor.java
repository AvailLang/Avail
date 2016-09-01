/**
 * LookupTreeAdaptor.java
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
import com.avail.descriptor.*;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code LookupTreeAdaptor} is instantiated to construct and interpret a family
 * of type-dispatch trees.
 */
public abstract class LookupTreeAdaptor<
	Element extends A_BasicObject,
	Result extends A_BasicObject,
	Memento>
{
	abstract A_Type extractSignature (final Element element);

	abstract Result constructResult (
		final List<? extends Element> elements, final Memento memento);

	/**
	 * Create a {@link LookupTree}, using the provided list of {@link
	 * DefinitionDescriptor definitions} of a {@link MethodDescriptor
	 * method}.
	 *
	 * @param numArgs
	 *        The number of top-level arguments expected by the definitions.
	 * @param allElements
	 *        The list of elements to categorize.
	 * @param knownArgumentTypes
	 *        The initial knowledge about the argument types.
	 * @return A LookupTree, potentially lazy, suitable for dispatching.
	 */
	public LookupTree<Element, Result, Memento> createRoot (
		final int numArgs,
		final List<? extends Element> allElements,
		final List<A_Type> knownArgumentTypes,
		final Memento memento)
	{
		final List<Element> prequalified = new ArrayList<>(1);
		final List<Element> undecided = new ArrayList<>(allElements.size());
		for (final Element element : allElements)
		{
			final A_Type argsTupleType = extractSignature(element);
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
				if (knownType.typeIntersection(definitionArgType).isBottom())
				{
					impossible = true;
				}
			}
			if (allComply)
			{
				prequalified.add(element);
			}
			else if (!impossible)
			{
				undecided.add(element);
			}
		}
		return createTree(prequalified, undecided, knownArgumentTypes, memento);
	}


	/**
	 * Create a LookupTree suitable for deciding which definition to use
	 * when actual argument types are provided.
	 *
	 * @param positive
	 *        Elements which definitely apply at this node.
	 * @param undecided
	 *        Elements which are not known to apply or not apply at this node.
	 * @param knownArgumentTypes
	 *        The types that the arguments are known to comply with at this
	 *        point.
	 * @return A (potentially lazy) LookupTree used to look up Elements.
	 */
	public LookupTree<Element, Result, Memento> createTree (
		final List<? extends Element> positive,
		final List<? extends Element> undecided,
		final List<A_Type> knownArgumentTypes,
		final Memento memento)
	{
		if (undecided.size() == 0)
		{
			// Find the most specific applicable definitions.
			if (positive.size() <= 1)
			{
				return new LeafLookupTree<Element, Result, Memento>(
					constructResult(positive, memento));
			}
			final int size = positive.size();
			final List<Element> mostSpecific = new ArrayList<>(1);
			outer: for (int outer = 0; outer < size; outer++)
			{
				final A_Type outerType = extractSignature(positive.get(outer));
				for (int inner = 0; inner < size; inner++)
				{
					if (outer != inner)
					{
						final A_Type innerType =
							extractSignature(positive.get(inner));
						if (innerType.isSubtypeOf(outerType))
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
			return new LeafLookupTree<>(constructResult(mostSpecific, memento));
		}
		return new InternalLookupTree<Element, Result, Memento>(
			positive, undecided, knownArgumentTypes);
	}

	/**
	 * Use the list of types to traverse the tree.  Answer the solution, a
	 * {@link Result}.
	 *
	 * @param argumentTypesList
	 *        The input {@link List} of {@link A_Type}s.
	 * @param adaptor
	 *        The adaptor used to expand the tree and interpret the result.
	 * @return The unique {@link Result}.
	 * @throws MethodDefinitionException If the solution is not unique.
	 */
	public Result lookupByTypes (
		final LookupTree<Element, Result, Memento> root,
		final List<? extends A_Type> argumentTypesList,
		final Memento memento)
	{
		LookupTree<Element, Result, Memento> tree = root;
		Result solution = tree.solutionOrNull();
		while (solution == null)
		{
			tree = tree.lookupStepByTypes(argumentTypesList, this, memento);
			solution = tree.solutionOrNull();
		}
		return solution;
	}

	/**
	 * Use the list of types to traverse the tree.  Answer the solution, a
	 * {@link Result}.
	 *
	 * @param argumentTypesList
	 *        The input {@link List} of {@link A_Type}s.
	 * @param adaptor
	 *        The adaptor used to expand the tree and interpret the result.
	 * @return The unique {@link Result}.
	 * @throws MethodDefinitionException If the solution is not unique.
	 */
	public Result lookupByTypes (
		final LookupTree<Element, Result, Memento> root,
		final A_Tuple argumentTypesTuple,
		final Memento memento)
	{
		LookupTree<Element, Result, Memento> tree = root;
		Result solution = tree.solutionOrNull();
		while (solution == null)
		{
			tree = tree.lookupStepByTypes(argumentTypesTuple, this, memento);
			solution = tree.solutionOrNull();
		}
		return solution;
	}

	/**
	 * Use the list of values to traverse the tree.  Answer the solution, a
	 * {@link Result}.
	 *
	 * @param argValues
	 *        The input {@link List} of {@link A_BasicObject}s.
	 * @param adaptor
	 *        The adaptor for interpreting and expanding the tree.
	 * @return The unique {@link Result}.
	 */
	public Result lookupByValues (
		final LookupTree<Element, Result, Memento> root,
		final List<? extends A_BasicObject> argValues,
		final Memento memento)
	{
		LookupTree<Element, Result, Memento> tree = root;
		Result solution = tree.solutionOrNull();
		while (solution == null)
		{
			tree = tree.lookupStepByValues(argValues, this, memento);
			solution = tree.solutionOrNull();
		}
		return solution;
	}

	/**
	 * Use the {@link A_Tuple} of values to traverse the tree.  Answer the
	 * {@link Result}.
	 *
	 * @param argValues The {@link A_Tuple} of values.
	 * @return The {@link Result} of the lookup.
	 */
	public Result lookupByValues (
		final LookupTree<Element, Result, Memento> root,
		final A_Tuple argValues,
		final Memento memento)
	{
		LookupTree<Element, Result, Memento> tree = root;
		Result solution = tree.solutionOrNull();
		while (solution == null)
		{
			tree = tree.lookupStepByValues(argValues, this, memento);
			solution = tree.solutionOrNull();
		}
		return solution;
	}

	public final static LookupTreeAdaptor<A_Definition, A_Tuple, Void>
		runtimeDispatcher = new LookupTreeAdaptor<A_Definition, A_Tuple, Void>()
		{
			@Override
			A_Type extractSignature (final A_Definition element)
			{
				return element.bodySignature().argsTupleType();
			}

			@Override
			A_Tuple constructResult (
				final List<? extends A_Definition> elements,
				final Void ignored)
			{
				return TupleDescriptor.fromList(elements);
			}
		};

	public final static LookupTreeAdaptor<
			A_DefinitionParsingPlan, A_BundleTree, Integer>
		parserTypeChecker = new LookupTreeAdaptor<
			A_DefinitionParsingPlan, A_BundleTree, Integer>()
		{
			@Override
			A_Type extractSignature (final A_DefinitionParsingPlan element)
			{
				return element.definition().parsingSignature();
			}

			@Override
			A_BundleTree constructResult (
				final List<? extends A_DefinitionParsingPlan> elements,
				final Integer pc)
			{
				final A_BundleTree newBundleTree =
					MessageBundleTreeDescriptor.newPc(pc);
				for (A_DefinitionParsingPlan plan : elements)
				{
					newBundleTree.addBundle(plan.bundle());
					newBundleTree.addPlan(plan);
				}
				return newBundleTree;
			}
		};
}
