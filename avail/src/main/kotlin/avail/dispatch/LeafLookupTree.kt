/*
 * LeafLookupTree.kt
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

import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject.Companion.error
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import java.lang.String.format

/**
 * A `LookupTree` representing a solution.
 *
 * @param Element
 *   The kind of elements in the lookup tree, such as method definitions.
 * @param Result
 *   What we expect to produce from a lookup activity, such as the tuple of
 *   most-specific matching method definitions for some arguments.
 * @property finalResult
 *   The result of the lookup.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `LeafLookupTree`.
 *
 * @param finalResult
 *   The most specific definitions for the provided arguments.  Thus, if this is
 *   empty, there are no applicable definitions, and if there's more than one
 *   element the actual call is ambiguous.
 */
class LeafLookupTree<
	Element : A_BasicObject,
	Result : A_BasicObject>
internal constructor(private val finalResult: Result)
: LookupTree<Element, Result>()
{
	override val solutionOrNull: Result
		get() = finalResult

	override fun <AdaptorMemento> expandIfNecessary(
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		numNaturalArgs: Int,
		memento: AdaptorMemento): DecisionStep<Element, Result>
	{
		error("Attempting to expand leaf of decision tree")
	}

	override fun toString(indent: Int): String
	{
		// Special case tuples for easier debugging.  Assume that tupleSize = 1
		// means success.
		if (finalResult.isTuple)
		{
			val finalTuple = finalResult as A_Tuple
			val tupleSize = finalTuple.tupleSize
			return if (tupleSize == 1)
			{
				// Assume for now that it's a tuple of definitions.
				format(
					"Success: %s",
					finalTuple.tupleAt(1).bodySignature().argsTupleType)
			}
			else format(
				"Failure: (%d solutions)",
				tupleSize)
		}
		else
		{
			return format("Result: %s", finalResult)
		}
	}
}
