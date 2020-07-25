/*
 * InternalLookupTree.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.dispatch

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.types.A_Type
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import java.lang.String.format
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * A `LookupTree` representing an incomplete search.  To further the search, the
 * indicated [type&#32;test][argumentTypeToTest] will be made.  If successful,
 * the [ifCheckHolds] child will be visited, otherwise the [ifCheckFails] child
 * will be visited.
 *
 * @param Element
 *   The kind of elements in the lookup tree, such as method definitions.
 * @param Result
 *   What we expect to produce from a lookup activity, such as the tuple of
 *   most-specific matching method definitions for some arguments.
 * @property positiveElements
 *   The definitions that are applicable at this tree node.
 * @property undecidedElements
 *   The definitions whose applicability has not yet been decided at this tree
 *   node.
 * @property knownArgumentRestrictions
 *   The types that the arguments must satisfy to have reached this position in
 *   the decision tree.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `InternalLookupTree`.  It is constructed lazily
 * at first.  An attempt to lookup that reaches this node will cause
 * it to be expanded locally.
 *
 * @param positiveElements
 *   The elements which definitely apply to the supplied arguments at this point
 *   in the decision tree.
 * @param undecidedElements
 *   The elements for which a decision about whether they apply to the supplied
 *   arguments has not yet been made at this point in the decision tree.
 * @param knownArgumentRestrictions
 *   The list of argument [TypeRestriction]s known to hold at this position in
 *   the decision tree.  Each element corresponds with an argument position for
 *   the method.
 */
internal class InternalLookupTree<
	Element : A_BasicObject,
	Result : A_BasicObject>
internal constructor(
	private val positiveElements: List<Element>,
	private val undecidedElements: List<Element>,
	private val knownArgumentRestrictions: List<TypeRestriction>)
: LookupTree<Element, Result>()
{
	/** The type to test against an argument type at this node.  */
	@Volatile internal var argumentTypeToTest: A_Type? = null

	/** The 1-based index of the argument to be tested at this node.  */
	internal var argumentPositionToTest = -1

	/** The tree to visit if the supplied arguments conform.  */
	internal var ifCheckHolds: LookupTree<Element, Result>? = null

	/** The tree to visit if the supplied arguments do not conform.  */
	internal var ifCheckFails: LookupTree<Element, Result>? = null

	/** `true` if this node has been expanded, otherwise `false`. */
	internal val isExpanded: Boolean
		get() = argumentTypeToTest !== null

	/**
	 * If they have not already been computed, compute and cache information
	 * about this node's [argumentTypeToTest], [argumentPositionToTest], and
	 * [ifCheckHolds], and [ifCheckFails].
	 */
	internal fun <AdaptorMemento> expandIfNecessary(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento)
	{
		if (argumentTypeToTest === null)
		{
			synchronized(this) {
				// We have to double-check if another thread has run
				// chooseCriterion() since our first check.  We're in a
				// synchronized mutual exclusion, so this is a stable check.
				// Also, argumentTypeToTest is volatile, ensuring Java's
				// infamous double-check problem won't bite us.
				if (argumentTypeToTest === null)
				{
					chooseCriterion(adaptor, memento)
				}
			}
		}
	}

	/**
	 * We're doing a method lookup, but [argumentTypeToTest] was `null`,
	 * indicating a lazy subtree.  Expand it by choosing and recording a
	 * criterion to test at this node, then populating the two branches of the
	 * tree with nodes that may themselves need to be expanded in the future.
	 *
	 * The criterion to choose should be one which serves to eliminate at least
	 * one of the [undecidedElements], regardless of whether the test happens
	 * to be affirmative or negative.  Eliminating more than one is better,
	 * however.  Ideally, we should choose a test which serves to eliminate as
	 * much indecision as possible in the worst case (i.e., along the path that
	 * is the least effective of the two). We do this, but we also break ties by
	 * eliminating as much indecision as possible in the *best* case.
	 *
	 * We eliminate some of the redundancy of a naïve decision tree by testing a
	 * single argument at a time, keeping track of the types we have tested that
	 * argument against.
	 *
	 * Since the negative case is already efficient at eliminating uncertainty,
	 * we only need to track positive information about the argument types.
	 * Thus, for each argument we maintain precise information about what type
	 * each argument must be at this point in the tree.  A single type for each
	 * argument suffices, since Avail's type lattice is precise with respect to
	 * type intersection, which is exactly what we use during decision tree
	 * construction.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 */
	private fun <AdaptorMemento> chooseCriterion(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento)
	{
		assert(argumentTypeToTest === null)
		val numArgs = knownArgumentRestrictions.size

		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)

		// To reduce duplication of the same tests, any argument that has the
		// same type in all definitions, but has not been proven yet, should be
		// selected first.
		if (adaptor.testsArgumentPositions()
			&& positiveElements.isEmpty()
			&& undecidedElements.size > 1)
		{
			val iterator = undecidedElements.iterator()
			assert(iterator.hasNext())
			val firstElement = iterator.next()
			val commonArgTypes =
				adaptor.restrictedSignature(firstElement, bound)
					.tupleOfTypesFromTo(1, numArgs)
					.toMutableList<A_Type?>()
			iterator.forEachRemaining { element ->
				val argTypes = adaptor.restrictedSignature(element, bound)
					.tupleOfTypesFromTo(1, numArgs)
					.toList<A_Type>()
				for (i in 0 until numArgs)
				{
					val commonArgType = commonArgTypes[i]
					if (commonArgType !== null
						&& !commonArgType.equals(argTypes[i]))
					{
						commonArgTypes[i] = null
					}
				}
			}
			for (argNumber in 1..numArgs)
			{
				val commonType = commonArgTypes[argNumber - 1]
				if (commonType !== null
						&& !knownArgumentRestrictions[argNumber - 1]
							.containedByType(commonType))
				{
					// Everybody needs this argument to satisfy this exact type,
					// but the argument isn't known to satisfy it yet.  This
					// test will be required by every traversal, so rather than
					// have duplicates near the leaves, test it as early as
					// possible.
					buildChildren(adaptor, memento, argNumber, commonType)
					return
				}
			}
		}

		// Choose a signature to test that guarantees it eliminates the most
		// undecided definitions, regardless of whether the test passes or
		// fails.  If the larger of the two cases (success or failure of the
		// test) is a tie between two criteria, break it by choosing the
		// criterion that eliminates the most undecided definitions in the
		// *best* case.
		assert(argumentTypeToTest === null)
		var bestSignature: A_Type? = null
		var smallestMax = Integer.MAX_VALUE
		var smallestMin = Integer.MAX_VALUE
		val undecidedCount = undecidedElements.size
		for (criterionIndex in 0 until undecidedCount)
		{
			val criterion = undecidedElements[criterionIndex]
			val criterionRestrictions = ArrayList(knownArgumentRestrictions)
			val boundedCriterionSignature =
				adaptor.restrictedSignature(criterion, bound)
			assert(!boundedCriterionSignature.isBottom)
			if (adaptor.testsArgumentPositions())
			{
				for (i in 1..numArgs)
				{
					criterionRestrictions[i - 1] =
						criterionRestrictions[i - 1].intersectionWithType(
							boundedCriterionSignature.typeAtIndex(i))
				}
			}
			else
			{
				criterionRestrictions[0] =
					criterionRestrictions[0].intersectionWithType(
						boundedCriterionSignature)
			}
			var undecidedCountIfTrue = 0
			var undecidedCountIfFalse = 0
			for (eachIndex in 0 until undecidedCount)
			{
				// Skip the element itself, since after a comparison it'll be
				// known to be included or be known not to be included.
				if (eachIndex != criterionIndex)
				{
					val each = undecidedElements[eachIndex]
					val eachSignature = adaptor.restrictedSignature(each, bound)
					val comparison = adaptor.compareTypes(
						criterionRestrictions, eachSignature)
					when (comparison)
					{
						TypeComparison.SAME_TYPE ->
						{
							// This might occur if the projection of two
							// elements under call-site-specific type bounds
							// yields the same type.  Or something unforeseen.
						}
						TypeComparison.PROPER_ANCESTOR_TYPE,
						TypeComparison.DISJOINT_TYPE ->
							undecidedCountIfFalse++
						TypeComparison.PROPER_DESCENDANT_TYPE ->
							undecidedCountIfTrue++
						TypeComparison.UNRELATED_TYPE ->
						{
							undecidedCountIfTrue++
							undecidedCountIfFalse++
						}
					}
				}
			}
			val maxCount =
				max(undecidedCountIfTrue, undecidedCountIfFalse)
			val minCount =
				min(undecidedCountIfTrue, undecidedCountIfFalse)
			// The criterion should not have been used to evaluate itself.
			assert(maxCount < undecidedElements.size)
			if (maxCount < smallestMax
				|| maxCount == smallestMax && minCount < smallestMin)
			{
				smallestMax = maxCount
				smallestMin = minCount
				bestSignature = boundedCriterionSignature
			}
		}
		assert(bestSignature !== null)

		// We have chosen one of the best signatures to test.  However, we still
		// need to decide which argument position to test.  Use the leftmost one
		// which is not already guaranteed by tests that have already been
		// performed.  In particular, ignore arguments whose knownArgumentTypes
		// information is a subtype of the chosen signature's argument type at
		// that position.
		var selectedTypeToTest: A_Type? = null
		var positionToTest: Int
		if (adaptor.testsArgumentPositions())
		{
			positionToTest = -999  // Must be replaced in the loop below.;
			for (i in 1..numArgs)
			{
				val knownRestriction = knownArgumentRestrictions[i - 1]
				val criterionArgumentType = bestSignature!!.typeAtIndex(i)
				if (!knownRestriction.containedByType(criterionArgumentType))
				{
					positionToTest = i
					selectedTypeToTest = criterionArgumentType
					break
				}
			}
			assert(positionToTest >= 1)
		}
		else
		{
			positionToTest = 0
			selectedTypeToTest = bestSignature
		}

		buildChildren(adaptor, memento, positionToTest, selectedTypeToTest!!)
	}

	/**
	 * Build children of this node, and populate any other needed fields.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param memento
	 *   The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *   The one-based index of the argument being tested.
	 * @param typeToTest
	 *   The [A_Type] that this node should test for.
	 */
	private fun <AdaptorMemento> buildChildren(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento,
		argumentIndex: Int,
		typeToTest: A_Type)
	{
		argumentPositionToTest = argumentIndex
		val zeroBasedIndex: Int
		val oldRestriction: TypeRestriction
		if (adaptor.testsArgumentPositions())
		{
			zeroBasedIndex = argumentIndex - 1
			oldRestriction = knownArgumentRestrictions[zeroBasedIndex]
		}
		else
		{
			zeroBasedIndex = 0
			oldRestriction = knownArgumentRestrictions[0]
		}

		val positiveKnownRestrictions = ArrayList(knownArgumentRestrictions)
		positiveKnownRestrictions[zeroBasedIndex] =
			oldRestriction.intersectionWithType(typeToTest)
		val positiveBound =
			adaptor.extractBoundingType(positiveKnownRestrictions)

		val negativeKnownRestrictions = ArrayList(knownArgumentRestrictions)
		negativeKnownRestrictions[zeroBasedIndex] =
			oldRestriction.minusType(typeToTest)
		val negativeBound =
			adaptor.extractBoundingType(negativeKnownRestrictions)

		// Check each element against the positiveKnownRestrictions, and
		// classify it as a positive hit in the holds branch, an undecided in
		// the holds branch, an undecided in the fails branch, or some
		// combination (but not both collections in the holds branch).
		val positiveIfTrue = ArrayList(positiveElements)
		val undecidedIfTrue = ArrayList<Element>()
		val positiveIfFalse = ArrayList<Element>()
		val undecidedIfFalse = ArrayList<Element>()
		for (undecidedElement in undecidedElements)
		{
			val positiveComparison = adaptor.compareTypes(
				positiveKnownRestrictions,
				adaptor.restrictedSignature(undecidedElement, positiveBound))
			val negativeComparison = adaptor.compareTypes(
				negativeKnownRestrictions,
				adaptor.restrictedSignature(undecidedElement, negativeBound))
			positiveComparison.applyEffect(
				undecidedElement,
				positiveIfTrue,
				undecidedIfTrue)
			negativeComparison.applyEffect(
				undecidedElement,
				positiveIfFalse,
				undecidedIfFalse)
		}
		ifCheckHolds = adaptor.createTree(
			positiveIfTrue,
			undecidedIfTrue,
			positiveKnownRestrictions,
			memento)
		// Since we're using TypeRestrictions, there are cases with instance
		// enumerations in which a failed test can actually certify a new
		// answer.  Merge the newly certified and already certified results.
		positiveIfFalse.addAll(positiveElements)
		ifCheckFails = adaptor.createTree(
			positiveIfFalse,
			undecidedIfFalse,
			negativeKnownRestrictions,
			memento)
		// This is a volatile write, so all previous writes had to precede it.
		// If another process runs expandIfNecessary(), it will either see null
		// for this field, or see non-null and be guaranteed that all subsequent
		// reads will see all the previous writes.
		argumentTypeToTest = typeToTest
	}

	override val solutionOrNull: Result?
		get() = null

	override fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		expandIfNecessary(adaptor, memento)
		val index = argumentPositionToTest
		assert(index > 0)
		val argument = argValues[index - 1]
		return if (argument.isInstanceOf(argumentTypeToTest!!))
		{
			ifCheckHolds!!
		}
		else ifCheckFails!!
	}

	override fun <AdaptorMemento> lookupStepByValues(
		argValues: A_Tuple,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		expandIfNecessary(adaptor, memento)
		val index = argumentPositionToTest
		assert(index > 0)
		val argument = argValues.tupleAt(index)
		return if (argument.isInstanceOf(argumentTypeToTest!!))
		{
			ifCheckHolds!!
		}
		else ifCheckFails!!
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		expandIfNecessary(adaptor, memento)
		val index = argumentPositionToTest
		assert(index > 0)
		val argumentType = argTypes[index - 1]
		return if (argumentType.isSubtypeOf(argumentTypeToTest!!))
		{
			ifCheckHolds!!
		}
		else ifCheckFails!!
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		expandIfNecessary(adaptor, memento)
		val index = argumentPositionToTest
		assert(index > 0)
		val argumentType = argTypes.tupleAt(index)
		return if (argumentType.isSubtypeOf(argumentTypeToTest!!))
		{
			ifCheckHolds!!
		}
		else ifCheckFails!!
	}

	override fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		expandIfNecessary(adaptor, memento)
		val index = argumentPositionToTest
		assert(index == 0)
		return if (probeValue.isInstanceOf(argumentTypeToTest!!))
		{
			ifCheckHolds!!
		}
		else ifCheckFails!!
	}

	override fun toString(indent: Int): String
	{
		if (argumentTypeToTest === null)
		{
			return format(
				"Lazy internal node: (u=%d, p=%d) known=%s",
				undecidedElements.size,
				positiveElements.size,
				knownArgumentRestrictions)
		}
		val builder = StringBuilder()
		builder.append(
			format(
				"#%d ∈ %s: (u=%d, p=%d) known=%s%n",
				argumentPositionToTest,
				argumentTypeToTest,
				undecidedElements.size,
				positiveElements.size,
				knownArgumentRestrictions))
		for (i in 0..indent)
		{
			builder.append("\t")
		}
		builder.append(ifCheckHolds!!.toString(indent + 1))
		builder.append(format("%n"))
		for (i in 0..indent)
		{
			builder.append("\t")
		}
		builder.append(ifCheckFails!!.toString(indent + 1))
		return builder.toString()
	}
}
