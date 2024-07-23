/*
 * TestForEnumerationOfNontypeDecisionStep.kt
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

import avail.descriptor.methods.A_Definition
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.values.L2SemanticBoxedValue
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import avail.utility.cast
import java.lang.String.format

/**
 * This is a [DecisionStep] which can efficiently handle instance types
 * (singular enumerations over non-types), as indicated in the signature of the
 * [Element]s.  It handles all other values by dispatching to a [fallThrough]
 * subtree.
 *
 * Since Avail objects have permanent hash values (over the lifetime of an OS
 * process), we can use the hash value for an initial lookup, followed by a full
 * check that it's the right object and not just a hash collision.
 *
 * @constructor
 * Construct the new instance.
 *
 * @property argumentPositionToTest
 *   The 1-based index of the argument for which to test by equality.
 * @property instanceTypeToSubtree
 *   A [Map] from [A_Type] to the child [LookupTree] that should be visited if
 *   that exact type is given as the argument *value* during lookup. If the
 *   provided value is not a type in the map, the [fallThrough] subtree will be
 *   visited instead.
 */
class TestForEnumerationOfNontypeDecisionStep<
	Element : A_BasicObject,
	Result : A_BasicObject>
constructor(
	argumentPositionToTest: Int,
	private val instanceTypeToSubtree:
		Map<A_Type, LookupTree<Element, Result>>,
	private val fallThrough: LookupTree<Element, Result>
) : DecisionStep<Element, Result>(argumentPositionToTest)
{
	init
	{
		instanceTypeToSubtree.keys.forEach(A_Type::makeShared)
	}

	//////////////////////////////
	//       Lookup steps.      //
	//////////////////////////////

	override fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argument = extractArgument(argValues, extraValues)
		return instanceTypeToSubtree[argument] ?: fallThrough
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argumentType = extractArgumentType(argTypes, extraValues)
		if (argumentType.isInstanceMeta)
		{
			return instanceTypeToSubtree[argumentType.instance] ?: fallThrough
		}
		return fallThrough
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argumentType = extractArgumentType(argTypes, extraValues)
		if (argumentType.isInstanceMeta)
		{
			return instanceTypeToSubtree[argumentType.instance] ?: fallThrough
		}
		return fallThrough
	}

	override fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argument = extractValue(probeValue, extraValues)
		return instanceTypeToSubtree[argument] ?: fallThrough
	}

	override fun describe(
		node: InternalLookupTree<Element, Result>,
		indent: Int,
		builder: StringBuilder
	): Unit = with(builder)
	{
		append(
			increaseIndentation(
				format(
					"(u=%d, p=%d) #%d instanceType lookup: known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					node.knownArgumentRestrictions),
				indent + 1))
		instanceTypeToSubtree.entries
			.map { (k, v) ->
				var keyString = k.toString()
				if (keyString.length > 30) {
					keyString = keyString.substring(0, 30)
				}
				keyString = keyString.substringBefore('\n')
				keyString to v
			}
			.sortedBy(Pair<String, *>::first)
			.forEach { (keyString, value) ->
				newlineTab(indent + 1)
				append("instanceType(CONST)=$keyString: ")
				append(value.toString(indent + 1))
			}
		newlineTab(indent + 1)
		append("fallThrough: ")
		append(fallThrough.toString(indent + 1))
	}

	override fun simplyAddChildrenTo(
		list: MutableList<LookupTree<Element, Result>>)
	{
		list.addAll(instanceTypeToSubtree.values)
		list.add(fallThrough)
	}

	override fun generateEdgesFor(
		semanticArguments: List<L2SemanticBoxedValue>,
		extraSemanticArguments: List<L2SemanticBoxedValue>,
		callSiteHelper: CallSiteHelper
	): List<
		Triple<
			L2BasicBlock,
			LookupTree<A_Definition, A_Tuple>,
			List<L2SemanticBoxedValue>>>
	{
		// For simplicity, let super-lookups always fall back.
		// They're *very* difficult to reason about.
		if (callSiteHelper.isSuper)
		{
			callSiteHelper.generator.jumpTo(
				callSiteHelper.onFallBackToSlowLookup)
			return emptyList()
		}

		return generateDispatchTriples(
			semanticArguments,
			extraSemanticArguments,
			callSiteHelper,
			instanceTypeToSubtree.cast(),
			fallThrough)
	}
}
