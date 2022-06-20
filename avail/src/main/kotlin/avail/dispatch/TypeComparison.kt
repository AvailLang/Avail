/*
 * TypeComparison.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

import avail.descriptor.methods.A_Sendable
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.parsingSignature
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.BottomTypeDescriptor
import avail.interpreter.levelTwo.operand.TypeRestriction

/**
 * Answer the relationship between two signatures, the argument tuple types of
 * function types representing (1) a criterion to test, and (2) a definition's
 * signature to be classified.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
enum class TypeComparison
{
	/** The definition's signature equals the criterion. */
	SAME_TYPE,

	/** The definition is a proper ancestor of the criterion. */
	PROPER_ANCESTOR_TYPE,

	/** The definition is a proper descendant of the criterion. */
	PROPER_DESCENDANT_TYPE,

	/**
	 * The definition's signature and the criterion are not directly related,
	 * but may share subtypes other than [bottom][BottomTypeDescriptor] (⊥).
	 */
	UNRELATED_TYPE,

	/**
	 * The definition's signature and the criterion have ⊥ as their nearest
	 * common descendant.  Thus, there are no tuples of actual arguments that
	 * satisfy both signatures simultaneously.  This is a useful distinction
	 * from [UNRELATED_TYPE], since a successful test against the criterion
	 * *eliminates* the other definition from being considered possible.
	 */
	DISJOINT_TYPE;

	/**
	 * Conditionally augment the supplied lists with the provided undecided
	 * [Element].  The decision of which lists to augment depends on this
	 * instance, which is the result of a comparison with the proposed node's
	 * type restrictions.
	 *
	 * @param undecidedDefinition
	 *   An [Element] whose applicability has not yet been decided.
	 * @param ifPositive
	 *   A list of definitions that will be applicable to some arguments if this
	 *   comparison indicates the definition is proven at some node.
	 * @param ifUndecided
	 *   A list of definitions that will be applicable to some arguments if this
	 *   comparison indicates the definition is possible at some node.
	 */
	fun <Element : A_BasicObject> applyEffect(
		undecidedDefinition: Element,
		ifPositive: MutableList<Element>,
		ifUndecided: MutableList<Element>)
	{
		when (this)
		{
			SAME_TYPE, PROPER_ANCESTOR_TYPE ->
				ifPositive.add(undecidedDefinition)
			PROPER_DESCENDANT_TYPE, UNRELATED_TYPE ->
				ifUndecided.add(undecidedDefinition)
			DISJOINT_TYPE -> {}
		}
	}

	companion object
	{
		/**
		 * Compare two types extracted from [A_Sendable.bodySignature]s.  The
		 * first is the criterion, which will eventually be tested against
		 * arguments.  The second signature is the one being compared by
		 * specificity with the criterion.
		 *
		 * @param argumentRestrictions
		 *   The [TypeRestriction]s that are known to hold at some point.
		 * @param elementSignature
		 *   An element signature type (a tuple type, here) to test against the
		 *   given restrictions.
		 * @return
		 *   A [TypeComparison] representing the relationship between the
		 *   restrictions and the signature type.
		 */
		fun compareForDispatch(
			argumentRestrictions: List<TypeRestriction>,
			elementSignature: A_Type): TypeComparison
		{
			var allBelow = true
			var allAbove = true
			val numArgs = argumentRestrictions.size
			for (i in 1..numArgs)
			{
				val restriction = argumentRestrictions[i - 1]
				val argType = elementSignature.typeAtIndex(i)
				if (!restriction.intersectsType(argType))
				{
					return DISJOINT_TYPE
				}
				allBelow = allBelow and restriction.containsEntireType(argType)
				allAbove = allAbove and restriction.containedByType(argType)
				//val knownRestriction = knownArgumentRestrictions[i - 1]
				//val criterionArgumentType = bestSignature!!.typeAtIndex(i)
				//if (knownRestriction.containedByType(criterionArgumentType))
				//{
				//	// Don't use this position, because it will always be true.
				//	continue
				//}
				//if (!knownRestriction.intersectsType(criterionArgumentType))
				//{
				//	// Don't use this position, because it will always be true.
				//	continue
				//}
				//positionToTest = i
				//selectedTypeToTest = criterionArgumentType
				//break
			}
			return if (allBelow)
				if (allAbove) SAME_TYPE else PROPER_DESCENDANT_TYPE
			else
				if (allAbove) PROPER_ANCESTOR_TYPE else UNRELATED_TYPE
		}

		/**
		 * Compare two phrase types extracted from
		 * [A_Sendable.parsingSignature]s.  The first is the criterion, which
		 * will eventually be tested against arguments.  The second signature is
		 * the one being compared by specificity with the criterion.
		 *
		 * @param argumentRestrictions
		 *   The list of argument restrictions that will hold at some point
		 *   during type testing.
		 * @param someType
		 *   A signature type to test against the given restrictions.
		 * @return
		 *   A TypeComparison representing the relationship between the
		 *   restrictions and the signature.
		 */
		fun compareForParsing(
			argumentRestrictions: List<TypeRestriction>,
			someType: A_Type): TypeComparison
		{
			val restriction = argumentRestrictions[0]
			val elementType = someType.typeAtIndex(1)
			val intersection = restriction.intersectionWithType(elementType)
			if (intersection.type.phraseTypeExpressionType.isBottom)
			{
				// For the purpose of parsing, if the intersection of these
				// phrase types produces a yield type that's ⊥, treat the types
				// as disjoint.  That's because a parsed expression phrase isn't
				// allowed to yield ⊥.
				return DISJOINT_TYPE
			}
			val below = restriction.containsEntireType(elementType)
			val above = restriction.containedByType(elementType)
			return if (below)
				if (above) SAME_TYPE else PROPER_DESCENDANT_TYPE
			else
				if (above) PROPER_ANCESTOR_TYPE else UNRELATED_TYPE
		}
	}
}
