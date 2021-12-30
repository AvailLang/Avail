/*
 * LookupTreeAdaptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.dispatch

import avail.AvailRuntimeSupport.captureNanos
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import avail.descriptor.types.TypeTag
import avail.interpreter.levelTwo.operand.TypeRestriction

/**
 * `LookupTreeAdaptor` is instantiated to construct and interpret a family
 * of type-dispatch trees.
 *
 * @param Element
 *   The kind of elements in the lookup tree, such as method definitions.
 * @param Result
 *   What we expect to produce from a lookup activity, such as the tuple of
 *   most-specific matching method definitions for some arguments.
 * @param Memento
 *   A memento to supply arbitrary additional information.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class LookupTreeAdaptor<
	Element : A_BasicObject,
	Result : A_BasicObject,
	Memento>
{
	/**
	 * A lightweight leaf node that can be used to indicate a negative
	 * search (i.e., no elements were applicable).
	 */
	abstract val emptyLeaf: LeafLookupTree<Element, Result>

	/**
	 * Convert from an [Element] to a suitable [A_Type] for organizing the tree.
	 *
	 * @param element
	 *   The [Element].
	 * @return
	 *   The corresponding [A_Type].
	 */
	abstract fun extractSignature(element: Element): A_Type

	/**
	 * Construct a [Result] from a [List] of [Element]s.
	 *
	 * @param elements
	 *   The list of elements from which to construct a result.
	 * @param memento
	 *   A memento to supply arbitrary additional information.
	 * @return
	 *   The answer, some combination of the elements.
	 */
	abstract fun constructResult(
		elements: List<Element>,
		memento: Memento): Result

	/**
	 * Compare two types to produce a [TypeComparison].
	 *
	 * @param argumentRestrictions
	 *   The [TypeRestriction]s that are known to hold at some point.
	 * @param signatureType
	 *   The signature type of some element, which can be tested for containment
	 *   and intersection against the given restriction.
	 * @return
	 *   A [TypeComparison] indicating the result of the comparison.
	 */
	abstract fun compareTypes(
		argumentRestrictions: List<TypeRestriction>,
		signatureType: A_Type): TypeComparison

	/**
	 * `true` if the tree uses whole type testing, or `false` if the tree tests
	 * individual elements of a tuple type.
	 */
	abstract fun testsArgumentPositions(): Boolean

	/**
	 * `true` if [Element]s with more specific signatures exclude those with
	 * strictly more general signatures, `false` otherwise.
	 */
	abstract fun subtypesHideSupertypes(): Boolean

	/**
	 * Extract the signature of the element, then intersect it with the given
	 * tuple type.
	 *
	 * @param element
	 *   The element providing a signature.
	 * @param signatureExtrasExtractor
	 *   A function that extracts a list of [A_Type]s from the [Element], which
	 *   should correspond with the extra values extracted from the value being
	 *   looked up.
	 * @param signatureBound
	 *   The tuple type with which to intersect the result.
	 * @return
	 *   The intersection of the element's signature and the bound.
	 */
	fun restrictedSignature(
		element: Element,
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
		signatureBound: A_Type
	): A_Type
	{
		var signature = extractSignature(element)
		val (_, extraTypes) = signatureExtrasExtractor(element)
		if (extraTypes.isNotEmpty())
		{
			signature = tupleTypeForTypesList(
				signature.tupleOfTypesFromTo(
					1, signature.sizeRange.lowerBound.extractInt
				) + extraTypes)
		}
		return signature.typeIntersection(signatureBound).makeImmutable()
	}

	/**
	 * Create a [LookupTree], using the provided collection of [Element]s, and
	 * the list of initial argument [types][A_Type].
	 *
	 * @param allElements
	 *   The collection of [Element]s to categorize.
	 * @param knownArgumentRestrictions
	 *   The initial knowledge about the argument types.
	 * @param memento
	 *   A value used by this adaptor to construct a [Result].
	 * @return
	 *   A [LookupTree], potentially lazy, suitable for dispatching.
	 */
	fun createRoot(
		allElements: Collection<Element>,
		knownArgumentRestrictions: List<TypeRestriction>,
		memento: Memento): LookupTree<Element, Result>
	{
		// Do all type testing intersected with the known type bounds.
		val bound = extractBoundingType(knownArgumentRestrictions)
		val prequalified = mutableListOf<Element>()
		val undecided = mutableListOf<Element>()
		for (element in allElements)
		{
			val signatureType = restrictedSignature(
				element, { defaultSignatureExtrasValue }, bound)
			var allComply = true
			var impossible = false
			val numArgs = knownArgumentRestrictions.size
			for (i in 1..numArgs)
			{
				val knownRestriction = knownArgumentRestrictions[i - 1]
				val definitionArgType = signatureType.typeAtIndex(i)
				if (!knownRestriction.containedByType(definitionArgType))
				{
					allComply = false
				}
				if (!knownRestriction.intersectsType(definitionArgType))
				{
					impossible = true
				}
			}
			if (allComply)
			{
				prequalified.add(element)
			}
			else if (!impossible)
			{
				undecided.add(element)
			}
		}
		return createTree(
			prequalified,
			undecided,
			knownArgumentRestrictions,
			zero,
			zero,
			zero,
			memento)
	}

	/**
	 * Compute the bounding signature from the given [List] of
	 * [TypeRestriction]s.
	 *
	 * Consider three elements A, B, and C, with a single argument position. Say
	 * C = A ∩ B.  If we eliminate the possibility C at some point, but later
	 * prove the argument is an A, then we know we can eliminate the possibility
	 * that it's also a B, because it also would have had to be a C, which was
	 * excluded.  By doing all type testing intersected with the known upper
	 * type bounds at this point, we can identify this case.
	 *
	 * @param argumentRestrictions
	 *   The [List] of [TypeRestriction]s active for each argument position.
	 * @return
	 *   The type that acts as an upper bound for comparisons within these
	 *   restrictions.
	 */
	fun extractBoundingType(argumentRestrictions: List<TypeRestriction>) =
		tupleTypeForTypesList(
			argumentRestrictions.map(TypeRestriction::type)).makeImmutable()

	/**
	 * Create a [LookupTree] suitable for deciding which [Result] applies when
	 * supplied with actual argument [types][A_Type].
	 *
	 * @param positive
	 *   [Element]s which definitely apply at this node.
	 * @param undecided
	 *   Elements which are not known to apply or not apply at this node.
	 * @param knownArgumentRestrictions
	 *   The [TypeRestriction]s that the arguments are known to comply with at
	 *   this point.
	 * @param alreadyTypeTestedArguments
	 *   An Avail [integer][A_Number] coding whether the arguments (and extras
	 *   that may have been generated during traversal of ancestors) have had
	 *   their [TypeTag] extracted and dispatched on by an ancestor.  Argument
	 *   #n is indicated by a set bit in the n-1st bit position, the one whose
	 *   value in the integer is 2^(n-1).
	 * @param alreadyVariantTestedArguments
	 *   An Avail [integer][A_Number] coding whether the arguments (and extras
	 *   that may have been generated during traversal of ancestors) have been
	 *   proven to be an [object][ObjectDescriptor], and was already dispatched
	 *   via an [ObjectLayoutVariantDecisionStep] in an ancestor.  Argument #n
	 *   is indicated by a set bit in the n-1st bit position, the one whose
	 *   value in the integer is 2^(n-1).
	 * @param alreadyPhraseTypeExtractArguments
	 *   An Avail [integer][A_Number] coding which arguments (and extras that may
	 *   have been generated during traversal of ancestors) were known to be phrase
	 *   types, and have had their yield type extracted already into another field
	 *   via an [ExtractPhraseTypeDecisionStep] in an ancestor.  Argument #n is
	 *   indicated by a set bit in the n-1st bit position, the one whose value in
	 *   the integer is 2^(n-1).
	 * @param memento
	 *   A memento for this adaptor to use.
	 * @return
	 *   A (potentially lazy) LookupTree used to look up Elements.
	 */
	internal fun createTree(
		positive: List<Element>,
		undecided: List<Element>,
		knownArgumentRestrictions: List<TypeRestriction>,
		alreadyTypeTestedArguments: A_Number,
		alreadyVariantTestedArguments: A_Number,
		alreadyPhraseTypeExtractArguments: A_Number,
		memento: Memento): LookupTree<Element, Result>
	{
		if (undecided.isEmpty())
		{
			// Find the most specific applicable definitions.
			if (!subtypesHideSupertypes() || positive.size <= 1)
			{
				return LeafLookupTree(constructResult(positive, memento))
			}
			val size = positive.size
			val mostSpecific = mutableListOf<Element>()
			outer@ for (outer in 0 until size)
			{
				// Use the actual signatures for dominance checking, since using
				// the restrictedSignature would break method lookup rules.
				val outerType = extractSignature(positive[outer])
				for (inner in 0 until size)
				{
					if (outer != inner)
					{
						val innerType = extractSignature(positive[inner])
						if (innerType.isSubtypeOf(outerType))
						{
							// A more specific definition was found (i.e., inner
							// was more specific than outer). This disqualifies
							// outer from being considered most specific.
							continue@outer
						}
					}
				}
				mostSpecific.add(positive[outer])
			}
			return LeafLookupTree(constructResult(mostSpecific, memento))
		}
		return InternalLookupTree(
			simplifyList(positive),
			simplifyList(undecided),
			knownArgumentRestrictions,
			alreadyTypeTestedArguments,
			alreadyVariantTestedArguments,
			alreadyPhraseTypeExtractArguments)
	}

	/**
	 * Answer a list (possibly immutable) with the same elements as the given
	 * list.  Use a smaller representation if possible.
	 *
	 * @param X
	 *   The type of elements in the input list.
	 * @param list
	 *   The input list.
	 * @return
	 *   A list with the same elements.
	 */
	private fun <X> simplifyList(list: List<X>): List<X> =
		when (list.size)
		{
			0 -> emptyList()
			1 -> listOf(list[0])
			else -> list
		}

	/**
	 * Use the tuple of types to traverse the tree.  Answer the solution, a
	 * [Result].  Uses iteration rather than recursion to limit stack depth.
	 *
	 * @param root
	 *   The [LookupTree] to search.
	 * @param argumentTypesTuple
	 *   The input [tuple][A_Tuple] of [types][A_Type].
	 * @param memento
	 *   A value potentially used for constructing [Result]s in parts of the
	 *   tree that have not yet been constructed.
	 * @return
	 *   The [Result].
	 */
	fun lookupByTypes(
		root: LookupTree<Element, Result>,
		argumentTypesTuple: A_Tuple,
		memento: Memento,
		lookupStats: LookupStatistics): Result
	{
		val before = captureNanos()
		val numArgs = argumentTypesTuple.tupleSize
		var depth = 0
		var tree = root
		var solution = tree.solutionOrNull
		var extraValues = emptyList<Element>()
		var signatureExtrasExtractor = { _ : Element ->
			defaultSignatureExtrasValue }
		while (solution === null)
		{
			val step = tree.expandIfNecessary(
				signatureExtrasExtractor, this, memento)
			extraValues = step.updateExtraValuesByTypes(
				argumentTypesTuple, extraValues)
			signatureExtrasExtractor = step.updateSignatureExtrasExtractor(
				this, signatureExtrasExtractor, numArgs)
			tree = step.lookupStepByTypes(
				argumentTypesTuple, extraValues, this, memento)
			solution = tree.solutionOrNull
			depth++
		}
		lookupStats.recordDynamicLookup(
			(captureNanos() - before).toDouble(), depth)
		return solution
	}

	/**
	 * Given a [List] of [A_BasicObject]s, use their types to traverse the
	 * [LookupTree].  Answer the solution, a [Result]. Uses iteration rather
	 * than recursion to limit stack depth.
	 *
	 * @param root
	 *   The [LookupTree] to search.
	 * @param argValues
	 *   The input [List] of [A_BasicObject]s.
	 * @param memento
	 *   A value potentially used for constructing [Result]s in parts of the
	 *   tree that have not yet been constructed.
	 * @param lookupStats
	 *   The [LookupStatistics] in which to record the lookup.
	 * @return
	 *   The [Result].
	 */
	fun lookupByValues(
		root: LookupTree<Element, Result>,
		argValues: List<A_BasicObject>,
		memento: Memento,
		lookupStats: LookupStatistics): Result
	{
		val before = captureNanos()
		val numArgs = argValues.size
		var depth = 0
		var tree = root
		var solution = tree.solutionOrNull
		var extraValues = emptyList<Element>()
		var signatureExtrasExtractor = { _ : Element ->
			defaultSignatureExtrasValue }
		while (solution === null)
		{
			val step = tree.expandIfNecessary(
				signatureExtrasExtractor, this, memento)
			extraValues = step.updateExtraValuesByValues(argValues, extraValues)
			signatureExtrasExtractor = step.updateSignatureExtrasExtractor(
				this, signatureExtrasExtractor, numArgs)
			tree = step.lookupStepByValues(
				argValues, extraValues, this, memento)
			solution = tree.solutionOrNull
			depth++
		}
		lookupStats.recordDynamicLookup(
			(captureNanos() - before).toDouble(), depth)
		return solution
	}

	/**
	 * Use the given singular value to traverse the tree.  Answer the solution,
	 * a [Result].  Uses iteration rather than recursion to limit stack depth.
	 *
	 * @param root
	 *   The [LookupTree] to search.
	 * @param argValue
	 *   The input [value][A_BasicObject].
	 * @param memento
	 *   A value potentially used for constructing [Result]s in parts of the
	 *   tree that have not yet been constructed.
	 * @param lookupStats
	 *   The [LookupStatistics] in which to record the lookup.
	 * @return The [Result].
	 */
	fun lookupByValue(
		root: LookupTree<Element, Result>,
		argValue: A_BasicObject,
		memento: Memento,
		lookupStats: LookupStatistics): Result
	{
		val before = captureNanos()
		val numArgs = 1
		var depth = 0
		var tree = root
		var solution = tree.solutionOrNull
		var extraValues = emptyList<Element>()
		var signatureExtrasExtractor = { _ : Element ->
			defaultSignatureExtrasValue }
		while (solution === null)
		{
			val step = tree.expandIfNecessary(
				signatureExtrasExtractor, this, memento)
			extraValues = step.updateExtraValuesByValue(argValue, extraValues)
			signatureExtrasExtractor = step.updateSignatureExtrasExtractor(
				this, signatureExtrasExtractor, numArgs)
			tree = step.lookupStepByValue(argValue, extraValues, this, memento)
			solution = tree.solutionOrNull
			depth++
		}
		lookupStats.recordDynamicLookup(
			(captureNanos() - before).toDouble(), depth)
		return solution
	}

	companion object
	{
		val defaultSignatureExtrasValue: Pair<A_Type?, List<A_Type>> =
			null to emptyList()
	}
}
