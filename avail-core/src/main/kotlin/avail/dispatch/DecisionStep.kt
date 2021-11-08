/*
 * DecisionStep.kt
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

import avail.descriptor.methods.A_Definition
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.values.L2SemanticValue
import avail.utility.notNullAnd
import avail.utility.removeLast

/**
 * This abstraction represents a mechanism for achieving a quantum of
 * progress toward looking up which method definition to invoke, or some
 * similar usage.
 *
 * @constructor
 * Create this [DecisionStep].
 *
 * @property argumentPositionToTest
 *   The argument position to test.  If the index is negative, `-1-x` is its
 *   index into the passed extraValues.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
sealed class DecisionStep<Element : A_BasicObject, Result : A_BasicObject>
constructor(
	val argumentPositionToTest: Int)
{
	/**
	 * Given an optional list of values used to supplement the lookup, answer
	 * the updated list of values that takes this step into account. The given
	 * and resulting lists must not be modified by subsequent steps.
	 *
	 * By default, simply return the input.
	 */
	open fun updateExtraValuesByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<Element>
	): List<Element> = extraValues

	/**
	 * Given an optional list of values used to supplement the lookup, answer
	 * the updated list of values that takes this step into account. The given
	 * and resulting lists must not be modified by subsequent steps.
	 *
	 * By default, simply return the input.
	 */
	open fun updateExtraValuesByValues(
		argValues: A_Tuple,
		extraValues: List<Element>
	): List<Element> = extraValues

	/**
	 * Given an optional list of values used to supplement the lookup, answer
	 * the updated list of values that takes this step into account. The given
	 * and resulting lists must not be modified by subsequent steps.
	 *
	 * By default, simply return the input.
	 */
	open fun updateExtraValuesByTypes(
		types: List<A_Type>,
		extraValues: List<Element>
	): List<Element> = extraValues

	/**
	 * Given an optional list of values used to supplement the lookup, answer
	 * the updated list of values that takes this step into account. The given
	 * and resulting lists must not be modified by subsequent steps.
	 *
	 * By default, simply return the input.
	 */
	open fun updateExtraValuesByTypes(
		argTypes: A_Tuple,
		extraValues: List<Element>
	): List<Element> = extraValues

	/**
	 * Given an optional list of values used to supplement the lookup, answer
	 * the updated list of values that takes this step into account. The given
	 * and resulting lists must not be modified by subsequent steps.
	 *
	 * By default, simply return the input.
	 */
	open fun updateExtraValuesByValue(
		probeValue: A_BasicObject,
		extraValues: List<Element>
	): List<Element> = extraValues

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided list of arguments.  Answer another [LookupTree] with which
	 * to continue the search.
	 *
	 * @param argValues
	 *   The [List] of arguments being looked up.
	 * @param extraValues
	 *   An optional immutable [List] of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided tuple of arguments.  Answer another [LookupTree] with which
	 * to continue the search.
	 *
	 * @param argValues
	 *   The [tuple][A_Tuple] of arguments being looked up.
	 * @param extraValues
	 *   An optional immutable [List] of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValues(
		argValues: A_Tuple,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided list of argument types.  Answer another [LookupTree] with
	 * which to continue the search.
	 *
	 * @param argTypes
	 *   The [list][List] of argument types being looked up.
	 * @param extraValues
	 *   An optional immutable [List] of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided tuple of argument types.  Answer another [LookupTree] with
	 * which to continue the search.
	 *
	 * @param argTypes
	 *   The [tuple][A_Tuple] of argument types being looked up.
	 * @param extraValues
	 *   An optional immutable [List] of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided value.  Answer another [LookupTree] with which to continue
	 * the search.
	 *
	 * @param probeValue
	 *   The value being looked up.
	 * @param extraValues
	 *   An optional immutable [List] of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>


	//////////////////////////////////
	//       Value extraction.      //
	//////////////////////////////////

	fun extractArgument(
		argValues: List<A_BasicObject>,
		extraValues: List<Element>
	) = when
	{
		argumentPositionToTest > 0 -> argValues[argumentPositionToTest - 1]
		else -> extraValues[-1 -argumentPositionToTest]
	} as AvailObject

	fun extractArgument(
		argValues: A_Tuple,
		extraValues: List<Element>
	) = when
	{
		argumentPositionToTest > 0 -> argValues.tupleAt(argumentPositionToTest)
		else -> extraValues[-1 -argumentPositionToTest]
	} as AvailObject

	fun extractArgumentType(
		argTypes: List<A_Type>,
		extraValues: List<Element>
	) = when
	{
		argumentPositionToTest > 0 -> argTypes[argumentPositionToTest - 1]
		else -> extraValues[-1 -argumentPositionToTest]
	} as AvailObject

	fun extractArgumentType(
		argTypes: A_Tuple,
		extraValues: List<Element>
	) = when
	{
		argumentPositionToTest > 0 -> argTypes.tupleAt(argumentPositionToTest)
		else -> extraValues[-1 -argumentPositionToTest]
	} as AvailObject

	fun extractValue(
		probeValue: A_BasicObject,
		extraValues: List<Element>
	) = when (argumentPositionToTest)
	{
		0 -> probeValue
		else -> extraValues[-1 -argumentPositionToTest]
	} as AvailObject

	/**
	 * Extract the [L2SemanticValue] that is providing the value operated on by
	 * this step.
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s provided as arguments to the call.
	 * @param extraSemanticValues
	 *   Any additional [L2SemanticValue]s available at this position in the
	 *   tree.
	 * @return
	 *   The [L2SemanticValue] that this step examines.
	 */
	fun sourceSemanticValue(
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>) = when
	{
		argumentPositionToTest >= 0 ->
			semanticValues[argumentPositionToTest - 1]
		else -> extraSemanticValues[-1 - argumentPositionToTest]
	}

	/**
	 * Add the children [LookupTree]s to the given [list].
	 *
	 * @param list
	 *   The list in which to add the children, in an arbitrary order.  Each
	 *   entry also contains the list of [L2SemanticValue]s that are available
	 *   upon reaching the corresponding position in the lookup tree.
	 * @param semanticValues
	 *   The original [L2SemanticValue]s that were available at the root of the
	 *   lookup tree.
	 * @param extraSemanticValues
	 *   A list of additional [L2SemanticValue]s that are available at this
	 *   position in the lookup tree, but were not at the top of the tree.
	 */
	abstract fun addChildrenTo(
		list: MutableList<
			Pair<LookupTree<Element, Result>, List<L2SemanticValue>>>,
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>)

	/**
	 * Generate suitable branch instructions via the [CallSiteHelper], and
	 * answer a list of [Triple]s that coordinate each target [L2BasicBlock]
	 * with the [LookupTree] responsible for generating code in that block, plus
	 * the list of extra [L2SemanticValue]s that will be present at that block.
	 */
	abstract fun generateEdgesFor(
		semanticArguments: List<L2SemanticValue>,
		extraSemanticArguments: List<L2SemanticValue>,
		callSiteHelper: CallSiteHelper
	): List<
		Triple<
			L2BasicBlock,
			LookupTree<A_Definition, A_Tuple>,
			List<L2SemanticValue>>>

	/**
	 * Output a description of this step on the given [builder].  Do not expand
	 * any subtrees that are still lazy.
	 */
	abstract fun describe(
		node : InternalLookupTree<Element, Result>,
		indent: Int,
		builder: StringBuilder)

	companion object
	{
		/**
		 * Test if the given method dispatch tree can reach any leaves
		 * containing exactly one solution.  There may be multiple such leaves,
		 * but we're satisfied if any exist.
		 *
		 * @param subtree
		 *   The tree to search.  Do not expand new nodes.
		 * @return
		 *   Whether any such leaf node was found.
		 */
		fun containsAnyValidLookup(
			subtree: LookupTree<A_Definition, A_Tuple>,
			semanticValues: List<L2SemanticValue>
		): Boolean
		{
			val nodes = mutableListOf(subtree to emptyList<L2SemanticValue>())
			while (nodes.isNotEmpty())
			{
				val (node, extraSemanticValues) = nodes.removeLast()
				if (node is LeafLookupTree)
				{
					if (node.solutionOrNull.notNullAnd { tupleSize == 1 })
						return true
				}
				else if (node is InternalLookupTree)
				{
					node.decisionStepOrNull?.addChildrenTo(
						nodes, semanticValues, extraSemanticValues)
				}
			}
			// We exhausted the tree.
			return false
		}
	}
}
