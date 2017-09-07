/**
 * InternalLookupTree.java
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
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.TupleTypeDescriptor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.avail.utility.Nulls.stripNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

/**
 * A {@code LookupTree} representing an incomplete search.  To further the
 * search, the indicated {@linkplain #argumentTypeToTest type test} will be
 * made.  If successful, the {@link #ifCheckHolds} child will be visited,
 * otherwise the {@link #ifCheckFails} child will be visited.
 */
public class InternalLookupTree<
		Element extends A_BasicObject, Result extends A_BasicObject, Memento>
	extends LookupTree<Element, Result, Memento>
{
	/** The definitions that are applicable at this tree node. */
	private final List<? extends Element> positiveElements;

	/**
	 * The definitions whose applicability has not yet been decided at this
	 * tree node.
	 */
	private final List<? extends Element> undecidedElements;

	/**
	 * The types that the arguments must satisfy to have reached this
	 * position in the decision tree.
	 */
	private final List<A_Type> knownArgumentTypes;

	/** The type to test against an argument type at this node. */
	private volatile @Nullable
	A_Type argumentTypeToTest;

	/** The 1-based index of the argument to be tested at this node. */
	@InnerAccess
	int argumentPositionToTest = -1;

	/** The tree to visit if the supplied arguments conform. */
	private @Nullable LookupTree<Element, Result, Memento> ifCheckHolds;

	/** The tree to visit if the supplied arguments do not conform. */
	private @Nullable LookupTree<Element, Result, Memento> ifCheckFails;

	/**
	 * Construct a new {@link InternalLookupTree}.  It is constructed lazily
	 * at first.  An attempt to lookup that reaches this node will cause
	 * it to be expanded locally.
	 *
	 * @param positiveElements
	 *        The elements which definitely apply to the supplied arguments
	 *        at this point in the decision tree.
	 * @param undecidedElements
	 *        The elements for which a decision about whether they apply to the
	 *        supplied arguments has not yet been made at this point in the
	 *        decision tree.
	 * @param knownArgumentTypes
	 *        The list of argument types known to hold at this position in the
	 *        decision tree.  Each element corresponds with an argument position
	 *        for the method.
	 */
	public InternalLookupTree (
		final List<? extends Element> positiveElements,
		final List<? extends Element> undecidedElements,
		final List<A_Type> knownArgumentTypes)
	{
		this.positiveElements = positiveElements;
		this.undecidedElements = undecidedElements;
		this.knownArgumentTypes = knownArgumentTypes;
	}

	/**
	 * Return an argument type to test against the supplied argument.  The
	 * argument's 1-based index is provided by {@link #argumentPositionToTest}.
	 *
	 * @return A list of argument types to check, expanding this node if
	 *         necessary.
	 */
	@InnerAccess final A_Type argumentTypeToTest ()
	{
		return stripNull(argumentTypeToTest);
	}

	/**
	 * Answer the decision subtree to explore if the condition holds.
	 *
	 * @return The "yes" subtree previously set by chooseCriterion().
	 */
	final LookupTree<Element, Result, Memento> ifCheckHolds ()
	{
		return stripNull(ifCheckHolds);
	}

	/**
	 * Answer the decision subtree to explore if the condition does not
	 * hold.
	 *
	 * @return The "no" subtree previously set by chooseCriterion().
	 */
	final LookupTree<Element, Result, Memento> ifCheckFails ()
	{
		return stripNull(ifCheckFails);
	}

	/**
	 * If they have not already been computed, compute and cache information
	 * about this node's {@link #argumentTypeToTest}, {@link
	 * #argumentPositionToTest}, and {@link #ifCheckHolds}, and {@link
	 * #ifCheckFails}.
	 */
	final void expandIfNecessary (
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		if (argumentTypeToTest == null)
		{
			synchronized (this)
			{
				// We have to double-check if another thread has run
				// chooseCriterion() since our first check.  We're in a
				// synchronized mutual exclusion, so this is a stable check.
				// Also, argumentTypeToTest is volatile, ensuring Java's
				// infamous double-check problem won't bite us.
				if (argumentTypeToTest == null)
				{
					chooseCriterion(adaptor, memento);
				}
			}
		}
	}

	/**
	 * We're doing a method lookup, but {@link #argumentTypeToTest} was
	 * null, indicating a lazy subtree.  Expand it by choosing and recording
	 * a criterion to test at this node, then populating the two branches of
	 * the tree with nodes that may themselves need to be expanded in the
	 * future.
	 *
	 * <p>The criterion to choose should be one which serves to eliminate at
	 * least one of the {@linkplain #undecidedElements}, regardless of
	 * whether the test happens to be affirmative or negative.  Eliminating
	 * more than one is better, however.  Ideally, we should choose a test
	 * which serves to eliminate as much indecision as possible in the worst
	 * case (i.e., along the path that is the least effective of the two).
	 * We do this, but we also break ties by eliminating as much indecision
	 * as possible in the <em>best</em> case.</p>
	 *
	 * <p>We eliminate some of the redundancy of a naïve decision tree by
	 * testing a single argument at a time, keeping track of the types we
	 * have tested that argument against.</p>
	 *
	 * <p>Since the negative case is already efficient at eliminating
	 * uncertainty, we only need to track positive information about the
	 * argument types.  Thus, for each argument we maintain precise
	 * information about what type each argument must be at this point in
	 * the tree.  A single type for each argument suffices, since Avail's
	 * type lattice is precise with respect to type intersection, which is
	 * exactly what we use during decision tree construction.</p>
	 *
	 * @param adaptor
	 *        The {@link LookupTreeAdaptor} to use for expanding the tree.
	 */
	private final void chooseCriterion (
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		// Choose a signature to test that guarantees it eliminates the most
		// undecided definitions, regardless of whether the test passes or
		// fails.  If the larger of the two cases (success or failure of the
		// test) is a tie between two criteria, break it by choosing the
		// criterion that eliminates the most undecided definitions in the
		// *best* case.
		assert argumentTypeToTest == null;
		A_Type bestSignature = null;
		int smallestMax = Integer.MAX_VALUE;
		int smallestMin = Integer.MAX_VALUE;
		for (final Element criterion : undecidedElements)
		{
			final A_Type criterionSignature =
				adaptor.extractSignature(criterion);
			int undecidedCountIfTrue = 0;
			int undecidedCountIfFalse = 0;
			for (final Element each : undecidedElements)
			{
				switch (adaptor.compareTypes(
					criterionSignature, adaptor.extractSignature(each)))
				{
					case SAME_TYPE:
						break;
					case PROPER_ANCESTOR_TYPE:
						if (!adaptor.subtypesHideSupertypes())
						{
							undecidedCountIfFalse++;
						}
						break;
					case PROPER_DESCENDANT_TYPE:
						undecidedCountIfTrue++;
						break;
					case UNRELATED_TYPE:
						undecidedCountIfTrue++;
						undecidedCountIfFalse++;
						break;
					case DISJOINT_TYPE:
						undecidedCountIfFalse++;
						break;
				}
			}
			final int maxCount =
				max(undecidedCountIfTrue, undecidedCountIfFalse);
			final int minCount =
				min(undecidedCountIfTrue, undecidedCountIfFalse);
			assert maxCount < undecidedElements.size();
			if (maxCount < smallestMax ||
				(maxCount == smallestMax && minCount < smallestMin))
			{
				smallestMax = maxCount;
				smallestMin = minCount;
				bestSignature = criterionSignature;
			}
		}
		assert bestSignature != null;
		final List<A_Type> newPositiveKnownTypes;
		final A_Type criterionSignature;
		final A_Type knownSignature;
		A_Type selectedTypeToTest = null;
		if (adaptor.testsArgumentPositions())
		{
			// We have chosen one of the best signatures to test.  However, we
			// still need to decide which argument position to test.  Use the
			// leftmost one which is not already guaranteed by tests that have
			// already been performed.  In particular, ignore arguments whose
			// knownArgumentTypes information is a subtype of the chosen
			// signature's argument type at that position.
			final int numArgs =
				bestSignature.sizeRange().lowerBound().extractInt();
			for (int i = 1; i <= numArgs; i++)
			{
				final A_Type knownType = knownArgumentTypes.get(i - 1);
				final A_Type criterionArgumentType =
					bestSignature.typeAtIndex(i);
				if (!knownType.isSubtypeOf(criterionArgumentType))
				{
					argumentPositionToTest = i;
					selectedTypeToTest = criterionArgumentType;
					break;
				}
			}
			assert argumentPositionToTest >= 1;
			assert selectedTypeToTest != null;
			final A_Type oldArgType =
				knownArgumentTypes.get(argumentPositionToTest - 1);
			final A_Type replacementArgType =
				selectedTypeToTest.typeIntersection(oldArgType);
			// Sanity check:  Make sure we at least improve type knowledge in
			// the positive case.
			assert !replacementArgType.equals(oldArgType);
			newPositiveKnownTypes = new ArrayList<>(knownArgumentTypes);
			newPositiveKnownTypes.set(
				argumentPositionToTest - 1, replacementArgType);
			criterionSignature = TupleTypeDescriptor.tupleTypeForTypes(
				newPositiveKnownTypes.toArray(new A_Type[numArgs]));
			knownSignature = TupleTypeDescriptor.tupleTypeForTypes(
				knownArgumentTypes.toArray(new A_Type[numArgs]));
		}
		else
		{
			argumentPositionToTest = 0;
			newPositiveKnownTypes = Collections.singletonList(bestSignature);
			criterionSignature = bestSignature;
			selectedTypeToTest = criterionSignature;
			knownSignature = knownArgumentTypes.get(0);
		}
		// Compute the positive/undecided lists, both for the condition
		// being true and for the condition being false.
		final List<Element> positiveIfTrue = new ArrayList<>(positiveElements);
		final List<Element> undecidedIfTrue = new ArrayList<>();
		final List<Element> undecidedIfFalse = new ArrayList<>();
		for (final Element undecidedElement : undecidedElements)
		{
			// We need to synthesize a tuple type with the knowledge we
			// currently have about the element types.
			final TypeComparison comparison = adaptor.compareTypes(
				criterionSignature,
				adaptor.extractSignature(undecidedElement)
					.typeIntersection(knownSignature));
			comparison.applyEffect(
				undecidedElement,
				positiveIfTrue,
				undecidedIfTrue,
				undecidedIfFalse,
				adaptor.subtypesHideSupertypes());
		}
		ifCheckHolds = adaptor.createTree(
			positiveIfTrue, undecidedIfTrue, newPositiveKnownTypes, memento);
		ifCheckFails = adaptor.createTree(
			positiveElements, undecidedIfFalse, knownArgumentTypes, memento);
		assert undecidedIfFalse.size() < undecidedElements.size();
		// This is a volatile write, so all previous writes had to precede it.
		// If another process runs expandIfNecessary(), it will either see null
		// for this field, or see non-null and be guaranteed that all subsequent
		// reads will see all the previous writes.
		argumentTypeToTest = selectedTypeToTest;
	}

	@Override
	protected @Nullable Result solutionOrNull ()
	{
		return null;
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByValues (
		final List<? extends A_BasicObject> argValues,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		expandIfNecessary(adaptor, memento);
		final int index = argumentPositionToTest;
		assert index > 0;
		final A_BasicObject argument = argValues.get(index - 1);
		if (argument.isInstanceOf(argumentTypeToTest()))
		{
			return ifCheckHolds();
		}
		return ifCheckFails();
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByValues (
		final A_Tuple argValues,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		expandIfNecessary(adaptor, memento);
		final int index = argumentPositionToTest;
		assert index > 0;
		final A_BasicObject argument = argValues.tupleAt(index);
		if (argument.isInstanceOf(argumentTypeToTest()))
		{
			return ifCheckHolds();
		}
		return ifCheckFails();
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByTypes (
		final List<? extends A_Type> argTypes,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		expandIfNecessary(adaptor, memento);
		final int index = argumentPositionToTest;
		assert index > 0;
		final A_Type argumentType = argTypes.get(index - 1);
		if (argumentType.isSubtypeOf(argumentTypeToTest()))
		{
			return ifCheckHolds();
		}
		return ifCheckFails();
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByTypes (
		final A_Tuple argTypes,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		expandIfNecessary(adaptor, memento);
		final int index = argumentPositionToTest;
		assert index > 0;
		final A_Type argumentType = argTypes.tupleAt(index);
		if (argumentType.isSubtypeOf(argumentTypeToTest()))
		{
			return ifCheckHolds();
		}
		return ifCheckFails();
	}

	@Override
	protected LookupTree<Element, Result, Memento> lookupStepByValue (
		final A_BasicObject probeValue,
		final LookupTreeAdaptor<Element, Result, Memento> adaptor,
		final Memento memento)
	{
		expandIfNecessary(adaptor, memento);
		final int index = argumentPositionToTest;
		assert index == 0;
		if (probeValue.isInstanceOf(argumentTypeToTest()))
		{
			return ifCheckHolds();
		}
		return ifCheckFails();
	}

	@Override
	public final String toString (final int indent)
	{
		if (argumentTypeToTest == null)
		{
			return format(
				"Lazy internal node: known=%s",
				knownArgumentTypes);
		}
		final StringBuilder builder = new StringBuilder();
		builder.append(
			format(
				"#%d ∈ %s: known=%s%n",
				argumentPositionToTest,
				argumentTypeToTest,
				knownArgumentTypes));
		for (int i = 0; i <= indent; i++)
		{
			builder.append("\t");
		}
		builder.append(ifCheckHolds().toString(indent + 1));
		builder.append(format("%n"));
		for (int i = 0; i <= indent; i++)
		{
			builder.append("\t");
		}
		builder.append(ifCheckFails().toString(indent + 1));
		return builder.toString();
	}
}
