/*
 * LookupTreeAdaptor.java
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
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * {@code LookupTreeAdaptor} is instantiated to construct and interpret a family
 * of type-dispatch trees.
 */
public abstract class LookupTreeAdaptor<
	Element extends A_BasicObject,
	Result extends A_BasicObject,
	Memento>
{
	/**
	 * Convert from an {@link Element} to a suitable {@link A_Type} for
	 * organizing the tree.
	 *
	 * @param element The {@link Element}.
	 * @return The corresponding {@link A_Type}.
	 */
	public abstract A_Type extractSignature (final Element element);

	/**
	 * Construct a {@link Result} from a {@link List} of {@link Element}s.
	 *
	 * @param elements
	 *        The list of elements from which to construct a result.
	 * @param memento
	 *        A memento to supply arbitrary additional information
	 * @return The answer, some combination of the elements.
	 */
	public abstract Result constructResult (
		final List<? extends Element> elements,
		final Memento memento);

	/**
	 * Compare two types to produce a {@link TypeComparison}.
	 *
	 * @param argumentRestrictions
	 *        The {@link TypeRestriction}s that are known to hold at some point.
	 * @param signatureType
	 *        The signature type of some element, which can be tested for
	 *        containment and intersection against the given restriction.
	 * @return A {@link TypeComparison} indicating the result of the comparison.
	 */
	public abstract TypeComparison compareTypes (
		final List<TypeRestriction> argumentRestrictions,
		final A_Type signatureType);

	/**
	 * Answer whether the tree tests individual argument positions, versus the
	 * entire type.
	 *
	 * @return {@code true} if the tree uses whole type testing, or {@code
	 *         false} if the tree tests individual elements of a tuple type.
	 */
	public abstract boolean testsArgumentPositions ();

	/**
	 * Answer whether {@link Element}s with more specific signatures exclude
	 * those with strictly more general signatures.
	 *
	 * @return A boolean.
	 */
	public abstract boolean subtypesHideSupertypes ();

	/**
	 * Extract the signature of the element, then intersect it with the given
	 * tuple type.
	 *
	 * @param element
	 *        The element providing a signature.
	 * @param signatureBound
	 *        The tuple type with which to intersect the result.
	 * @return The intersection of the element's signature and the bound.
	 */
	public A_Type restrictedSignature (
		final Element element,
		final A_Type signatureBound)
	{
		return extractSignature(element).typeIntersection(signatureBound);
	}

	/**
	 * Create a {@link LookupTree}, using the provided collection of {@link
	 * Element}s, and the list of initial argument {@link A_Type types}.
	 *
	 * @param allElements
	 *        The collection of {@link Element}s to categorize.
	 * @param knownArgumentRestrictions
	 *        The initial knowledge about the argument types.
	 * @param memento
	 *        A value used by this adaptor to construct a {@link Result}.
	 * @return A LookupTree, potentially lazy, suitable for dispatching.
	 */
	public LookupTree<Element, Result, Memento> createRoot (
		final Collection<? extends Element> allElements,
		final List<TypeRestriction> knownArgumentRestrictions,
		final Memento memento)
	{
		// Do all type testing intersected with the known type bounds.
		final A_Type bound = extractBoundingType(knownArgumentRestrictions);
		final List<Element> prequalified = new ArrayList<>(1);
		final List<Element> undecided = new ArrayList<>(allElements.size());
		for (final Element element : allElements)
		{
			final A_Type signatureType = restrictedSignature(element, bound);
			boolean allComply = true;
			boolean impossible = false;
			if (testsArgumentPositions())
			{
				final int numArgs = knownArgumentRestrictions.size();
				for (int i = 1; i <= numArgs; i++)
				{
					final TypeRestriction knownRestriction =
						knownArgumentRestrictions.get(i - 1);
					final A_Type definitionArgType =
						signatureType.typeAtIndex(i);
					if (!knownRestriction.containedByType(definitionArgType))
					{
						allComply = false;
					}
					if (!knownRestriction.intersectsType(definitionArgType))
					{
						impossible = true;
					}
				}
			}
			else
			{
				assert knownArgumentRestrictions.size() == 1;
				final TypeRestriction knownRestriction =
					knownArgumentRestrictions.get(0);
				if (!knownRestriction.containedByType(signatureType))
				{
					allComply = false;
				}
				if (!knownRestriction.intersectsType(signatureType))
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
		return createTree(
			prequalified, undecided, knownArgumentRestrictions, memento);
	}

	/**
	 * Compute the bounding signature from the given {@link List} of {@link
	 * TypeRestriction}s.
	 *
	 * <p>Consider three elements A, B, and C, with a single argument position,
	 * Say C = A ∩ B.  If we eliminate the possibility C at some point, but
	 * later prove the argument is an A, then we know we can eliminate the
	 * possibility that it's also a B, because it also would have had to be a C,
	 * which was excluded.  By doing all type testing intersected with the known
	 * upper type bounds at this point, we can identify this case.</p>
	 *
	 * @param argumentRestrictions
	 *        The {@link List} of {@link TypeRestriction}s active for each
	 *        argument position.
	 * @return The type that acts as an upper bound for comparisons within these
	 *         restrictions.
	 */
	public A_Type extractBoundingType (
		final List<TypeRestriction> argumentRestrictions)
	{
		if (testsArgumentPositions())
		{
			return tupleTypeForTypes(
				argumentRestrictions.stream()
					.map(r -> r.type)
					.collect(toList()));
		}
		return argumentRestrictions.get(0).type;
	}

	/**
	 * Create a {@link LookupTree} suitable for deciding which {@link Result}
	 * applies when supplied with actual argument {@link A_Type types}.
	 *
	 * @param positive
	 *        {@link Element}s which definitely apply at this node.
	 * @param undecided
	 *        Elements which are not known to apply or not apply at this node.
	 * @param knownArgumentRestrictions
	 *        The {@link TypeRestriction}s that the arguments are known to
	 *        comply with at this point.
	 * @return A (potentially lazy) LookupTree used to look up Elements.
	 */
	LookupTree<Element, Result, Memento> createTree (
		final List<Element> positive,
		final List<Element> undecided,
		final List<TypeRestriction> knownArgumentRestrictions,
		final Memento memento)
	{
		if (undecided.size() == 0)
		{
			// Find the most specific applicable definitions.
			if (!subtypesHideSupertypes() || positive.size() <= 1)
			{
				return new LeafLookupTree<>(constructResult(positive, memento));
			}
			final int size = positive.size();
			final List<Element> mostSpecific = new ArrayList<>(1);
			outer:
			for (int outer = 0; outer < size; outer++)
			{
				// Use the actual signatures for dominance checking, since using
				// the restrictedSignature would break method lookup rules.
				final A_Type outerType = extractSignature(positive.get(outer));
				for (int inner = 0; inner < size; inner++)
				{
					if (outer != inner)
					{
						final A_Type innerType =
							extractSignature(positive.get(inner));
						if (innerType.isSubtypeOf(outerType))
						{
							// A more specific definition was found (i.e., inner
							// was more specific than outer). This disqualifies
							// outer from being considered most specific.
							continue outer;
						}
					}
				}
				mostSpecific.add(positive.get(outer));
			}
			return new LeafLookupTree<>(constructResult(mostSpecific, memento));
		}
		return new InternalLookupTree<>(
			simplifyList(positive),
			simplifyList(undecided),
			knownArgumentRestrictions);
	}

	/**
	 * Answer a list (possibly immutable) with the same elemnts as the given
	 * list.  Use a smaller representation if possible.
	 *
	 * @param list
	 *        The input list.
	 * @param <X>
	 *        The type of elements in the input list.
	 * @return A list with the same elements.
	 */
	private static <X> List<X> simplifyList (final List<X> list)
	{
		switch (list.size())
		{
			case 0: return emptyList();
			case 1: return singletonList(list.get(0));
			default: return list;
		}
	}

	/**
	 * Use the list of types to traverse the tree.  Answer the solution, a
	 * {@link Result}.  Uses iteration rather than recursion to limit stack
	 * depth.
	 *
	 * @param root
	 *        The {@link LookupTree} to search.
	 * @param argumentTypesList
	 *        The input {@link List} of {@link A_Type types}.
	 * @param memento
	 *        A value potentially used for constructing {@link Result}s in parts
	 *        of the tree that have not yet been constructed.
	 * @return The {@link Result}.
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
	 * Use the tuple of types to traverse the tree.  Answer the solution, a
	 * {@link Result}.  Uses iteration rather than recursion to limit stack
	 * depth.
	 *
	 * @param root
	 *        The {@link LookupTree} to search.
	 * @param argumentTypesTuple
	 *        The input {@link A_Tuple tuple} of {@link A_Type types}.
	 * @param memento
	 *        A value potentially used for constructing {@link Result}s in parts
	 *        of the tree that have not yet been constructed.
	 * @return The {@link Result}.
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
	 * Given a {@link List} of {@link A_BasicObject}s, use their types to
	 * traverse the {@link LookupTree}.  Answer the solution, a {@link Result}.
	 * Uses iteration rather than recursion to limit stack depth.
	 *
	 * @param root
	 *        The {@link LookupTree} to search.
	 * @param argValues
	 *        The input {@link List} of {@link A_BasicObject}s.
	 * @param memento
	 *        A value potentially used for constructing {@link Result}s in parts
	 *        of the tree that have not yet been constructed.
	 * @return The {@link Result}.
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
	 * Given a {@link A_Tuple tuple} of {@link A_BasicObject}s, use their types
	 * to traverse the {@link LookupTree}.  Answer the solution, a {@link
	 * Result}. Uses iteration rather than recursion to limit stack depth.
	 *
	 * @param root
	 *        The {@link LookupTree} to search.
	 * @param argValues
	 *        The input tuple of {@link A_BasicObject}s.
	 * @param memento
	 *        A value potentially used for constructing {@link Result}s in parts
	 *        of the tree that have not yet been constructed.
	 * @return The {@link Result}.
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

	/**
	 * Use the given singular value to traverse the tree.  Answer the solution,
	 * a {@link Result}.  Uses iteration rather than recursion to limit stack
	 * depth.
	 *
	 * @param root
	 *        The {@link LookupTree} to search.
	 * @param argValue
	 *        The input {@link A_BasicObject value}.
	 * @param memento
	 *        A value potentially used for constructing {@link Result}s in parts
	 *        of the tree that have not yet been constructed.
	 * @return The {@link Result}.
	 */
	public Result lookupByValue (
		final LookupTree<Element, Result, Memento> root,
		final A_BasicObject argValue,
		final Memento memento)
	{
		LookupTree<Element, Result, Memento> tree = root;
		Result solution = tree.solutionOrNull();
		while (solution == null)
		{
			tree = tree.lookupStepByValue(argValue, this, memento);
			solution = tree.solutionOrNull();
		}
		return solution;
	}
}
