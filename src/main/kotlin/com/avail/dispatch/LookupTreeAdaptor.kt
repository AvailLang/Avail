/*
 * LookupTreeAdaptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import kotlin.streams.toList

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
	 * Convert from an [Element] to a suitable [A_Type] for
	 * organizing the tree.
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
	 * @param signatureBound
	 *   The tuple type with which to intersect the result.
	 * @return
	 *   The intersection of the element's signature and the bound.
	 */
	fun restrictedSignature(
		element: Element,
		signatureBound: A_Type): A_Type
	{
		return extractSignature(element).typeIntersection(signatureBound)
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
			val signatureType = restrictedSignature(element, bound)
			var allComply = true
			var impossible = false
			if (testsArgumentPositions())
			{
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
			}
			else
			{
				assert(knownArgumentRestrictions.size == 1)
				val knownRestriction = knownArgumentRestrictions[0]
				if (!knownRestriction.containedByType(signatureType))
				{
					allComply = false
				}
				if (!knownRestriction.intersectsType(signatureType))
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
			prequalified, undecided, knownArgumentRestrictions, memento)
	}

	/**
	 * Compute the bounding signature from the given [List] of
	 * [TypeRestriction]s.
	 *
	 * Consider three elements A, B, and C, with a single argument position, Say
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
	fun extractBoundingType(argumentRestrictions: List<TypeRestriction>): A_Type
	{
		return if (testsArgumentPositions())
		{
			tupleTypeForTypes(
				argumentRestrictions.stream()
					.map { r -> r.type }
					.toList())
		}
		else argumentRestrictions[0].type
	}

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
	 * @return
	 *   A (potentially lazy) LookupTree used to look up Elements.
	 */
	internal fun createTree(
		positive: List<Element>,
		undecided: List<Element>,
		knownArgumentRestrictions: List<TypeRestriction>,
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
			knownArgumentRestrictions)
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
	 * Use the list of types to traverse the tree.  Answer the solution, a
	 * [Result].  Uses iteration rather than recursion to limit stack depth.
	 *
	 * @param root
	 *   The [LookupTree] to search.
	 * @param argumentTypesList
	 *   The input [List] of [types][A_Type].
	 * @param memento
	 *   A value potentially used for constructing [Result]s in parts of the
	 *   tree that have not yet been constructed.
	 * @return The [Result].
	 */
	@Suppress("unused") fun lookupByTypes(
		root: LookupTree<Element, Result>,
		argumentTypesList: List<A_Type>,
		memento: Memento): Result
	{
		var tree = root
		var solution = tree.solutionOrNull
		while (solution === null)
		{
			tree = tree.lookupStepByTypes(argumentTypesList, this, memento)
			solution = tree.solutionOrNull
		}
		return solution
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
		memento: Memento): Result
	{
		var tree = root
		var solution = tree.solutionOrNull
		while (solution === null)
		{
			tree = tree.lookupStepByTypes(argumentTypesTuple, this, memento)
			solution = tree.solutionOrNull
		}
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
	 * @return
	 *   The [Result].
	 */
	fun lookupByValues(
		root: LookupTree<Element, Result>,
		argValues: List<A_BasicObject>,
		memento: Memento): Result
	{
		var tree = root
		var solution = tree.solutionOrNull
		while (solution === null)
		{
			tree = tree.lookupStepByValues(argValues, this, memento)
			solution = tree.solutionOrNull
		}
		return solution
	}

	/**
	 * Given a [tuple][A_Tuple] of [A_BasicObject]s, use their types to traverse
	 * the [LookupTree].  Answer the solution, a [Result]. Uses iteration rather
	 * than recursion to limit stack depth.
	 *
	 * @param root
	 *   The [LookupTree] to search.
	 * @param argValues
	 *   The input tuple of [A_BasicObject]s.
	 * @param memento
	 *   A value potentially used for constructing [Result]s in parts of the
	 *   tree that have not yet been constructed.
	 * @return
	 *   The [Result].
	 */
	fun lookupByValues(
		root: LookupTree<Element, Result>,
		argValues: A_Tuple,
		memento: Memento): Result
	{
		var tree = root
		var solution = tree.solutionOrNull
		while (solution === null)
		{
			tree = tree.lookupStepByValues(argValues, this, memento)
			solution = tree.solutionOrNull
		}
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
	 * @return The [Result].
	 */
	fun lookupByValue(
		root: LookupTree<Element, Result>,
		argValue: A_BasicObject,
		memento: Memento): Result
	{
		var tree = root
		var solution = tree.solutionOrNull
		while (solution === null)
		{
			tree = tree.lookupStepByValue(argValue, this, memento)
			solution = tree.solutionOrNull
		}
		return solution
	}
}
