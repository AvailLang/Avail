/*
 * InternalLookupTree.java
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.TupleDescriptor.toList;
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
		Element extends A_BasicObject,
		Result extends A_BasicObject,
		AdaptorMemento>
	extends LookupTree<Element, Result, AdaptorMemento>
{
	/** The definitions that are applicable at this tree node. */
	private final List<Element> positiveElements;

	/**
	 * The definitions whose applicability has not yet been decided at this
	 * tree node.
	 */
	private final List<Element> undecidedElements;

	/**
	 * The types that the arguments must satisfy to have reached this position
	 * in the decision tree.
	 */
	private final List<TypeRestriction> knownArgumentRestrictions;

	/** The type to test against an argument type at this node. */
	private volatile @Nullable A_Type argumentTypeToTest;

	/** The 1-based index of the argument to be tested at this node. */
	int argumentPositionToTest = -1;

	/** The tree to visit if the supplied arguments conform. */
	private @Nullable LookupTree<Element, Result, AdaptorMemento> ifCheckHolds;

	/** The tree to visit if the supplied arguments do not conform. */
	private @Nullable LookupTree<Element, Result, AdaptorMemento> ifCheckFails;

	/**
	 * Construct a new {@code InternalLookupTree}.  It is constructed lazily
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
	 * @param knownArgumentRestrictions
	 *        The list of argument {@link TypeRestriction}s known to hold at
	 *        this position in the decision tree.  Each element corresponds with
	 *        an argument position for the method.
	 */
	InternalLookupTree (
		final List<Element> positiveElements,
		final List<Element> undecidedElements,
		final List<TypeRestriction> knownArgumentRestrictions)
	{
		this.positiveElements = positiveElements;
		this.undecidedElements = undecidedElements;
		this.knownArgumentRestrictions = knownArgumentRestrictions;
	}

	/**
	 * Return an argument type to test against the supplied argument.  The
	 * argument's 1-based index is provided by {@link #argumentPositionToTest}.
	 *
	 * @return A list of argument types to check, expanding this node if
	 *         necessary.
	 */
	final A_Type argumentTypeToTest ()
	{
		return stripNull(argumentTypeToTest);
	}

	/**
	 * Answer the decision subtree to explore if the condition holds.
	 *
	 * @return The "yes" subtree previously set by chooseCriterion().
	 */
	final LookupTree<Element, Result, AdaptorMemento> ifCheckHolds ()
	{
		return stripNull(ifCheckHolds);
	}

	/**
	 * Answer the decision subtree to explore if the condition does not
	 * hold.
	 *
	 * @return The "no" subtree previously set by chooseCriterion().
	 */
	final LookupTree<Element, Result, AdaptorMemento> ifCheckFails ()
	{
		return stripNull(ifCheckFails);
	}

	/**
	 * Answer whether this internal node has already been expanded.
	 *
	 * @return {@code true} if this node has been expanded, otherwise {@code
	 *         false}.
	 */
	final boolean isExpanded ()
	{
		return argumentTypeToTest != null;
	}

	/**
	 * If they have not already been computed, compute and cache information
	 * about this node's {@link #argumentTypeToTest}, {@link
	 * #argumentPositionToTest}, and {@link #ifCheckHolds}, and {@link
	 * #ifCheckFails}.
	 */
	final void expandIfNecessary (
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento memento)
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
	private void chooseCriterion (
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento memento)
	{
		assert argumentTypeToTest == null;
		final int numArgs = knownArgumentRestrictions.size();

		final A_Type bound =
			adaptor.extractBoundingType(knownArgumentRestrictions);

		// To reduce duplication of the same tests, any argument that has the
		// same type in all definitions, but has not been proven yet, should be
		// selected first.
		if (adaptor.testsArgumentPositions()
			&& positiveElements.isEmpty()
			&& undecidedElements.size() > 1)
		{
			@Nullable List<A_Type> commonArgTypes = null;
			for (final Element element : undecidedElements)
			{
				final A_Type signature =
					adaptor.restrictedSignature(element, bound);
				final List<A_Type> argTypes =
					toList(signature.tupleOfTypesFromTo(1, numArgs));
				if (commonArgTypes == null)
				{
					commonArgTypes = argTypes;
				}
				else
				{
					for (int i = 0; i < numArgs; i++)
					{
						final A_Type commonArgType = commonArgTypes.get(i);
						if (commonArgType != null
							&& !commonArgType.equals(argTypes.get(i)))
						{
							commonArgTypes.set(i, null);
						}
					}
				}
			}
			// assert commonArgTypes != null;
			for (int argNumber = 1; argNumber <= numArgs; argNumber++)
			{
				final @Nullable A_Type commonType =
					commonArgTypes.get(argNumber - 1);
				if (commonType != null
					&& !knownArgumentRestrictions.get(argNumber - 1)
						.containedByType(commonType))
				{
					// Everybody needs this argument to satisfy this exact type,
					// but the argument isn't known to satisfy it yet.  This
					// test will be required by every traversal, so rather than
					// have duplicates near the leaves, test it as early as
					// possible.
					buildChildren(adaptor, memento, argNumber, commonType);
					return;
				}
			}
		}

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
		final int undecidedCount = undecidedElements.size();
		for (int criterionIndex = 0;
			criterionIndex < undecidedCount;
			criterionIndex++)
		{
			final Element criterion = undecidedElements.get(criterionIndex);
			final List<TypeRestriction> criterionRestrictions =
				new ArrayList<>(knownArgumentRestrictions);
			final A_Type boundedCriterionSignature =
				adaptor.restrictedSignature(criterion, bound);
			assert !boundedCriterionSignature.isBottom();
			if (adaptor.testsArgumentPositions())
			{
				for (int i = 1; i <= numArgs; i++)
				{
					criterionRestrictions.set(
						i - 1,
						criterionRestrictions.get(i - 1).intersectionWithType(
							boundedCriterionSignature.typeAtIndex(i)));
				}
			}
			else
			{
				criterionRestrictions.set(
					0,
					criterionRestrictions.get(0).intersectionWithType(
						boundedCriterionSignature));
			}
			int undecidedCountIfTrue = 0;
			int undecidedCountIfFalse = 0;
			for (int eachIndex = 0; eachIndex < undecidedCount; eachIndex++)
			{
				// Skip the element itself, since after a comparison it'll
				// be known to be included or be known not to be included.
				if (eachIndex != criterionIndex)
				{
					final Element each = undecidedElements.get(eachIndex);
					final A_Type eachSignature =
						adaptor.restrictedSignature(each, bound);
					final TypeComparison comparison = adaptor.compareTypes(
						criterionRestrictions, eachSignature);
					switch (comparison)
					{
						case SAME_TYPE:
							// This might occur if the projection of two
							// elements under call-site-specific type bounds
							// yields the same type.  Or something unforeseen.
							break;
						case PROPER_ANCESTOR_TYPE:
						case DISJOINT_TYPE:
							undecidedCountIfFalse++;
							break;
						case PROPER_DESCENDANT_TYPE:
							undecidedCountIfTrue++;
							break;
						case UNRELATED_TYPE:
							undecidedCountIfTrue++;
							undecidedCountIfFalse++;
							break;
					}
				}
			}
			final int maxCount =
				max(undecidedCountIfTrue, undecidedCountIfFalse);
			final int minCount =
				min(undecidedCountIfTrue, undecidedCountIfFalse);
			// The criterion should not have been used to evaluate itself.
			assert maxCount < undecidedElements.size();
			if (maxCount < smallestMax
				|| (maxCount == smallestMax && minCount < smallestMin))
			{
				smallestMax = maxCount;
				smallestMin = minCount;
				bestSignature = boundedCriterionSignature;
			}
		}
		assert bestSignature != null;

		// We have chosen one of the best signatures to test.  However, we still
		// need to decide which argument position to test.  Use the leftmost one
		// which is not already guaranteed by tests that have already been
		// performed.  In particular, ignore arguments whose knownArgumentTypes
		// information is a subtype of the chosen signature's argument type at
		// that position.
		A_Type selectedTypeToTest = null;
		int positionToTest;
		if (adaptor.testsArgumentPositions())
		{
			positionToTest = -999;  // Must be replaced in the loop below.;
			for (int i = 1; i <= numArgs; i++)
			{
				final TypeRestriction knownRestriction =
					knownArgumentRestrictions.get(i - 1);
				final A_Type criterionArgumentType =
					bestSignature.typeAtIndex(i);
				if (!knownRestriction.containedByType(criterionArgumentType))
				{
					positionToTest = i;
					selectedTypeToTest = criterionArgumentType;
					break;
				}
			}
			assert positionToTest >= 1;
		}
		else
		{
			positionToTest = 0;
			selectedTypeToTest = bestSignature;
		}

		buildChildren(adaptor, memento, positionToTest, selectedTypeToTest);
	}

	/**
	 * Build children of this node, and populate any other needed fields.
	 *
	 * @param adaptor
	 *        The {@link LookupTreeAdaptor} to use for expanding the tree.
	 * @param memento
	 *        The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *        The one-based index of the argument being tested.
	 * @param typeToTest
	 *        The {@link A_Type} that this node should test for.
	 */
	private void buildChildren (
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento memento,
		final int argumentIndex,
		final A_Type typeToTest)
	{
		argumentPositionToTest = argumentIndex;
		final int zeroBasedIndex;
		final TypeRestriction oldRestriction;
		if (adaptor.testsArgumentPositions())
		{
			zeroBasedIndex = argumentIndex - 1;
			oldRestriction = knownArgumentRestrictions.get(zeroBasedIndex);
		}
		else
		{
			zeroBasedIndex = 0;
			oldRestriction = knownArgumentRestrictions.get(0);
		}

		final List<TypeRestriction> positiveKnownRestrictions =
			new ArrayList<>(knownArgumentRestrictions);
		positiveKnownRestrictions.set(
			zeroBasedIndex, oldRestriction.intersectionWithType(typeToTest));
		final A_Type positiveBound =
			adaptor.extractBoundingType(positiveKnownRestrictions);

		final List<TypeRestriction> negativeKnownRestrictions =
			new ArrayList<>(knownArgumentRestrictions);
		negativeKnownRestrictions.set(
			zeroBasedIndex, oldRestriction.minusType(typeToTest));
		final A_Type negativeBound =
			adaptor.extractBoundingType(negativeKnownRestrictions);

		// Check each element against the positiveKnownRestrictions, and
		// classify it as a positive hit in the holds branch, an undecided in
		// the holds branch, an undecided in the fails branch, or some
		// combination (but not both collections in the holds branch).
		final List<Element> positiveIfTrue = new ArrayList<>(positiveElements);
		final List<Element> undecidedIfTrue = new ArrayList<>();
		final List<Element> positiveIfFalse = new ArrayList<>();
		final List<Element> undecidedIfFalse = new ArrayList<>();
		for (final Element undecidedElement : undecidedElements)
		{
			final TypeComparison positiveComparison = adaptor.compareTypes(
				positiveKnownRestrictions,
				adaptor.restrictedSignature(undecidedElement, positiveBound));
			final TypeComparison negativeComparison = adaptor.compareTypes(
				negativeKnownRestrictions,
				adaptor.restrictedSignature(undecidedElement, negativeBound));
			positiveComparison.applyEffect(
				undecidedElement,
				positiveIfTrue,
				undecidedIfTrue);
			negativeComparison.applyEffect(
				undecidedElement,
				positiveIfFalse,
				undecidedIfFalse);
		}
		ifCheckHolds = adaptor.createTree(
			positiveIfTrue,
			undecidedIfTrue,
			positiveKnownRestrictions,
			memento);
		// Since we're using TypeRestrictions, there are cases with instance
		// enumerations in which a failed test can actually certify a new
		// answer.  Merge the newly certified and already certified results.
		positiveIfFalse.addAll(positiveElements);
		ifCheckFails = adaptor.createTree(
			positiveIfFalse,
			undecidedIfFalse,
			negativeKnownRestrictions,
			memento);
		// This is a volatile write, so all previous writes had to precede it.
		// If another process runs expandIfNecessary(), it will either see null
		// for this field, or see non-null and be guaranteed that all subsequent
		// reads will see all the previous writes.
		argumentTypeToTest = typeToTest;
	}

	@Override
	protected @Nullable Result solutionOrNull ()
	{
		return null;
	}

	@Override
	protected LookupTree<Element, Result, AdaptorMemento> lookupStepByValues (
		final List<? extends A_BasicObject> argValues,
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento memento)
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
	protected LookupTree<Element, Result, AdaptorMemento> lookupStepByValues (
		final A_Tuple argValues,
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento memento)
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
	protected LookupTree<Element, Result, AdaptorMemento> lookupStepByTypes (
		final List<? extends A_Type> argTypes,
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento memento)
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
	protected LookupTree<Element, Result, AdaptorMemento> lookupStepByTypes (
		final A_Tuple argTypes,
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento memento)
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
	protected LookupTree<Element, Result, AdaptorMemento> lookupStepByValue (
		final A_BasicObject probeValue,
		final LookupTreeAdaptor<Element, Result, AdaptorMemento> adaptor,
		final AdaptorMemento memento)
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
				"Lazy internal node: (u=%d, p=%d) known=%s",
				undecidedElements.size(),
				positiveElements.size(),
				knownArgumentRestrictions);
		}
		final StringBuilder builder = new StringBuilder();
		builder.append(
			format(
				"#%d ∈ %s: (u=%d, p=%d) known=%s%n",
				argumentPositionToTest,
				argumentTypeToTest,
				undecidedElements.size(),
				positiveElements.size(),
				knownArgumentRestrictions));
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
