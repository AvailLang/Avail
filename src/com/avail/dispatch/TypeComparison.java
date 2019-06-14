/*
 * TypeComparison.java
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
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.BottomTypeDescriptor;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;

import java.util.List;

/**
 * Answer the relationship between two signatures, the argument tuple
 * types of function types representing (1) a criterion to test, and (2)
 * a definition's signature to be classified.
 */
public enum TypeComparison
{
	/**
	 * The definition's signature equals the criterion.
	 */
	SAME_TYPE,

	/**
	 * The definition is a proper ancestor of the criterion.
	 */
	PROPER_ANCESTOR_TYPE,

	/**
	 * The definition is a proper descendant of the criterion.
	 */
	PROPER_DESCENDANT_TYPE,

	/**
	 * The definition's signature and the criterion are not directly
	 * related, but may share subtypes other than {@linkplain
	 * BottomTypeDescriptor bottom} (⊥).
	 */
	UNRELATED_TYPE,

	/**
	 * The definition's signature and the criterion have ⊥ as their
	 * nearest common descendant.  Thus, there are no tuples of actual
	 * arguments that satisfy both signatures simultaneously.  This is
	 * a useful distinction from {@link #UNRELATED_TYPE}, since a
	 * successful test against the criterion <em>eliminates</em> the
	 * other definition from being considered possible.
	 */
	DISJOINT_TYPE;

	/**
	 * Conditionally augment the supplied lists with the provided undecided
	 * {@link Element}.  The decision of which lists to augment depends on this
	 * instance, which is the result of a comparison with the proposed node's
	 * type restrictions.
	 *
	 * @param undecidedDefinition
	 *        An {link Entry} whose applicability has not yet been decided.
	 * @param ifPositive
	 *        A list of definitions that will be applicable to some arguments if
	 *        this comparison indicates the definition is proven at some node.
	 * @param ifUndecided
	 *        A list of definitions that will be applicable to some arguments if
	 *        this comparison indicates the definition is possible at some node.
	 */
	public <Element extends A_BasicObject> void applyEffect (
		final Element undecidedDefinition,
		final List<Element> ifPositive,
		final List<Element> ifUndecided)
	{
		switch (this)
		{
			case SAME_TYPE:
			case PROPER_ANCESTOR_TYPE:
			{
				ifPositive.add(undecidedDefinition);
				break;
			}
			case PROPER_DESCENDANT_TYPE:
			case UNRELATED_TYPE:
			{
				ifUndecided.add(undecidedDefinition);
				break;
			}
			case DISJOINT_TYPE:
			{
				break;
			}
		}
	}

	/**
	 * Compare two types extracted from {@link
	 * A_Definition#bodySignature()}s.  The first is the criterion, which
	 * will eventually be tested against arguments.  The second signature is the
	 * one being compared by specificity with the criterion.
	 *
	 * @param argumentRestrictions
	 *        The {@link TypeRestriction}s that are known to hold at some point.
	 * @param elementSignature
	 *        An element signature type (a tuple type, here) to test against the
	 *        given restrictions..
	 * @return A TypeComparison representing the relationship between
	 *         the restrictions and the signature type.
	 */
	public static TypeComparison compareForDispatch (
		final List<TypeRestriction> argumentRestrictions,
		final A_Type elementSignature)
	{
		boolean allBelow = true;
		boolean allAbove = true;
		final int numArgs = argumentRestrictions.size();
		for (int i = 1; i <= numArgs; i++)
		{
			final TypeRestriction restriction = argumentRestrictions.get(i - 1);
			final A_Type argType = elementSignature.typeAtIndex(i);
			if (!restriction.intersectsType(argType))
			{
				return DISJOINT_TYPE;
			}
			allBelow &= restriction.containsEntireType(argType);
			allAbove &= restriction.containedByType(argType);
		}
		return
			allBelow
				? (allAbove ? SAME_TYPE : PROPER_DESCENDANT_TYPE)
				: (allAbove ? PROPER_ANCESTOR_TYPE : UNRELATED_TYPE);
	}

	/**
	 * Compare two phrase types extracted from {@link
	 * A_Definition#parsingSignature()}s.  The first is the criterion, which
	 * will eventually be tested against arguments.  The second signature is the
	 * one being compared by specificity with the criterion.
	 *
	 * @param argumentRestrictions
	 *        The list of argument restrictions that will hold at some point
	 *        during type testing.
	 * @param someType
	 *        A signature type to test against the given restrictions.
	 * @return A TypeComparison representing the relationship between
	 *         the restrictions and the signature.
	 */
	public static TypeComparison compareForParsing (
		final List<TypeRestriction> argumentRestrictions,
		final A_Type someType)
	{
		assert argumentRestrictions.size() == 1;
		final TypeRestriction restriction = argumentRestrictions.get(0);
		final TypeRestriction intersection =
			restriction.intersectionWithType(someType);
		if (intersection.type.expressionType().isBottom())
		{
			// For the purpose of parsing, if the intersection of these phrase
			// types produces a yield type that's ⊥, treat the types as
			// disjoint.  That's because a parsed expression phrase isn't
			// allowed to yield ⊥.
			return DISJOINT_TYPE;
		}
		final boolean below = restriction.containsEntireType(someType);
		final boolean above = restriction.containedByType(someType);
		return
			below
				? (above ? SAME_TYPE : PROPER_DESCENDANT_TYPE)
				: (above ? PROPER_ANCESTOR_TYPE : UNRELATED_TYPE);
	}
}
