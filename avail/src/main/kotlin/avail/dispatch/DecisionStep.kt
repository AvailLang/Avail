/*
 * DecisionStep.kt
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
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_HASH
import avail.interpreter.levelTwo.operation.L2_MULTIWAY_JUMP
import avail.interpreter.levelTwo.operation.ShiftedHashSplitter
import avail.interpreter.primitive.general.P_Hash
import avail.interpreter.primitive.integers.P_BitShiftRight
import avail.interpreter.primitive.integers.P_BitwiseAnd
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import avail.utility.cast
import avail.utility.notNullAnd
import avail.utility.removeLast
import java.lang.Integer.toHexString

/**
 * This abstraction represents a mechanism for achieving a quantum of
 * progress toward looking up which method definition to invoke, or some
 * similar usage.
 *
 * @constructor
 * Create this [DecisionStep].
 *
 * @property argumentPositionToTest
 *   The argument position to test.  If the index is within bounds for the
 *   arguments, use the indicated argument, otherwise index the extraValues as
 *   though they were concatenated to the end of the arguments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
sealed class DecisionStep<Element : A_BasicObject, Result : A_BasicObject>
constructor(
	val argumentPositionToTest: Int)
{
	init
	{
		assert(argumentPositionToTest > 0)
	}

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
	open fun updateExtraValuesByTypes(
		types: List<A_Type>,
		extraValues: List<A_Type>
	): List<A_Type> = extraValues

	/**
	 * Given an optional list of values used to supplement the lookup, answer
	 * the updated list of values that takes this step into account. The given
	 * and resulting lists must not be modified by subsequent steps.
	 *
	 * By default, simply return the input.
	 */
	open fun updateExtraValuesByTypes(
		argTypes: A_Tuple,
		extraValues: List<A_Type>
	): List<A_Type> = extraValues

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
	 * Given a function that produces a list of types from an [Element] due to
	 * an ancestor tree node, produce a new function for producing a lists of
	 * types that takes this [DecisionStep] into account.  The function also
	 * optionally produces the type signature of the element, for reuse by
	 * subsequent wrapped functions.
	 *
	 * Most [DecisionStep]s return the original function, but steps that extract
	 * subobjects will append an entry to the given list.
	 *
	 * Since this function is only used when expanding the lazy [LookupTree],
	 * the extra effort of repeatedly extracting covariant subobjects from the
	 * elements' types won't be a significant ongoing cost.
	 *
	 * @param extrasTypeExtractor
	 *   The function to return or wrap.
	 * @param numArgs
	 *   The number of argument positions that an [Element] produces, not
	 *   counting the extras extracted from it.
	 */
	open fun <Memento> updateSignatureExtrasExtractor(
		adaptor: LookupTreeAdaptor<Element, Result, Memento>,
		extrasTypeExtractor: (Element)->Pair<A_Type?, List<A_Type>>,
		numArgs: Int
	): (Element)->Pair<A_Type?, List<A_Type>> = extrasTypeExtractor

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
		extraValues: List<A_BasicObject>,
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
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento
	): LookupTree<Element, Result>

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
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento
	): LookupTree<Element, Result>

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
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento
	): LookupTree<Element, Result>


	//////////////////////////////////
	//       Value extraction.      //
	//////////////////////////////////

	fun extractArgument(
		argValues: List<A_BasicObject>,
		extraValues: List<A_BasicObject>
	): AvailObject
	{
		val inExtras = argumentPositionToTest - argValues.size
		return when
		{
			inExtras > 0 -> extraValues[inExtras - 1]
			else -> argValues[argumentPositionToTest - 1]
		} as AvailObject
	}

	fun extractArgumentType(
		argTypes: List<A_Type>,
		extraValues: List<A_Type>
	): AvailObject
	{
		val inExtras = argumentPositionToTest - argTypes.size
		return when
		{
			inExtras > 0 -> extraValues[inExtras - 1]
			else -> argTypes[argumentPositionToTest - 1]
		} as AvailObject
	}

	fun extractArgumentType(
		argTypes: A_Tuple,
		extraValues: List<A_Type>
	): AvailObject
	{
		val inExtras = argumentPositionToTest - argTypes.tupleSize
		return when
		{
			inExtras > 0 -> extraValues[inExtras - 1]
			else -> argTypes.tupleAt(argumentPositionToTest)
		} as AvailObject
	}

	fun extractValue(
		probeValue: A_BasicObject,
		extraValues: List<A_BasicObject>
	): AvailObject = when (argumentPositionToTest)
	{
		1 -> probeValue
		else -> extraValues[argumentPositionToTest - 2]
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
		semanticValues: List<L2SemanticBoxedValue>,
		extraSemanticValues: List<L2SemanticBoxedValue>
	): L2SemanticBoxedValue
	{
		val inExtras = argumentPositionToTest - semanticValues.size
		return when
		{
			inExtras > 0 -> extraSemanticValues[inExtras - 1]
			else -> semanticValues[argumentPositionToTest - 1]
		}
	}

	/**
	 * Add the children [LookupTree]s to the given [list].  This can be used for
	 * scanning the tree for some condition, without recursion.
	 *
	 * @param list
	 *   The list in which to add the children, in an arbitrary order.
	 */
	abstract fun simplyAddChildrenTo(
		list: MutableList<LookupTree<Element, Result>>)

	/**
	 * Generate suitable branch instructions via the [CallSiteHelper], and
	 * answer a list of [Triple]s that coordinate each target [L2BasicBlock]
	 * with the [LookupTree] responsible for generating code in that block, plus
	 * the list of extra [L2SemanticValue]s that will be present at that block.
	 */
	abstract fun generateEdgesFor(
		semanticArguments: List<L2SemanticBoxedValue>,
		extraSemanticArguments: List<L2SemanticBoxedValue>,
		callSiteHelper: CallSiteHelper
	): List<
		Triple<
			L2BasicBlock,
			LookupTree<A_Definition, A_Tuple>,
			List<L2SemanticBoxedValue>>>

	/**
	 * Output a description of this step on the given [builder].  Do not expand
	 * any subtrees that are still lazy.
	 */
	abstract fun describe(
		node: InternalLookupTree<Element, Result>,
		indent: Int,
		builder: StringBuilder)

	/**
	 * Generate an [L2_MULTIWAY_JUMP] that uses hashing to handle all of the
	 * entries listed in [valueToSubtree], minimizing conflicts.  If the value
	 * at runtime does not match any of those listed values, control flow should
	 * end up in code generated for the [noMatchSubtree].
	 *
	 * @param semanticArguments
	 *   The original [L2SemanticValue] arguments available at this point in the
	 *   tree.
	 * @param extraSemanticArguments
	 *   Additional [L2SemanticValue]s for values that have been extracted from
	 *   the arguments at this point in the tree.
	 * @param callSiteHelper
	 *   The [CallSiteHelper] for which the dispatch is happening.
	 * @param valueToSubtree
	 *   A [Map] from each expected value to the [LookupTree] that should be
	 *   reached if that value occurs at runtime.
	 * @param noMatchSubtree
	 *   The [LookupTree] that should be reached if none of the entries in the
	 *   [valueToSubtree] was supplied at runtime.
	 */
	fun generateDispatchTriples(
		semanticArguments: List<L2SemanticBoxedValue>,
		extraSemanticArguments: List<L2SemanticBoxedValue>,
		callSiteHelper: CallSiteHelper,
		valueToSubtree: Map<A_BasicObject, LookupTree<Element, Result>>,
		noMatchSubtree: LookupTree<Element, Result>
	): List<
		Triple<
			L2BasicBlock,
			LookupTree<A_Definition, A_Tuple>,
			List<L2SemanticBoxedValue>>>
	{
		val semanticSource =
			sourceSemanticValue(semanticArguments, extraSemanticArguments)
		val generator = callSiteHelper.generator
		val manifest = generator.currentManifest
		val sourceRestriction = manifest.restrictionFor(semanticSource)
		var residue = sourceRestriction
		val reachableEntries = valueToSubtree.entries.filter { (key, _) ->
			residue = residue.minusValue(key)
			sourceRestriction.intersectsType(instanceTypeOrMetaOn(key))
		}
		// True if there are no other possible values that could occur.
		val exhaustive = residue.type.isBottom
		val reachableSize = reachableEntries.size
		when
		{
			reachableSize == 0 && exhaustive ->
			{
				// Nothing is possible here.
				generator.addUnreachableCode()
				return emptyList()
			}

			(reachableSize == 0 || (reachableSize == 1 && exhaustive)) ->
			{
				// Exactly one subtree is possible, so jump to it.
				val onlyTree = when
				{
					exhaustive -> reachableEntries.single().value
					else -> noMatchSubtree
				}
				val target = L2BasicBlock("Only outcome")
				generator.jumpTo(target)
				return listOf(
					Triple(target, onlyTree.cast(), extraSemanticArguments))
			}
		}
		// There are multiple targets.  Use the shifted version of the hash
		// value that spreads over the most targets.
		val hashes = reachableEntries.map { it.key.hash() }.toIntArray()
		var leadingZeros = hashes.size.countLeadingZeroBits()

		// This function takes the leading zeros in the number indicating how
		// many hash values are present, and returns a triple with a shift
		// amount, how many slots will be filled, and the mask.  The shift
		// amount is chosen to maximize the filled slots.
		fun attemptBestHashing(leadingZeros: Int): Triple<Int, Int, Int>
		{
			val tryMask = -1 ushr leadingZeros
			val ratingsByShift = (0 .. leadingZeros).map { shift ->
				shift to hashes.map { (it ushr shift) and tryMask }.toSet().size
			}
			val best = ratingsByShift.maxByOrNull { (_, rating) -> rating }!!
			return Triple(best.first, best.second, tryMask)
		}
		val smallestTableResult = attemptBestHashing(leadingZeros)
		var bestShift = smallestTableResult.first
		var mask = smallestTableResult.third
		if (smallestTableResult.second < hashes.size && leadingZeros > 2)
		{
			// There wasn't a perfect hash at the smallest possible size, so try
			// the next size up.
			val biggerTableResult = attemptBestHashing(leadingZeros - 1)
			if (biggerTableResult.second > smallestTableResult.second)
			{
				// The larger table had more bins with hits, so use it.
				bestShift = biggerTableResult.first
				mask = biggerTableResult.third
			}
		}

		// Whichever shift factor spread the hashes to the most bins should be
		// good enough.  We already had to test the actual values for equality,
		// so handle collisions by simply having multiple such tests.
		val targetsByShiftedHash = reachableEntries
			.groupBy { (it.key.hash() ushr bestShift) and mask }
			.mapValues { (shiftedKey, subtrees) ->
				L2BasicBlock("shifted hash = $shiftedKey") to subtrees
			}
		val noMatchBlock = L2BasicBlock("None matched by equality")
		// First, extract the hash value.
		val int32Restriction = intRestrictionForType(i32)
		val semanticHash = primitiveInvocation(P_Hash, listOf(semanticSource))
		val semanticHashInt = L2SemanticUnboxedInt(semanticHash)
		if (!generator.currentManifest.hasSemanticValue(semanticHashInt))
		{
			generator.addInstruction(
				L2_HASH,
				generator.readBoxed(semanticSource),
				generator.intWrite(setOf(semanticHashInt), int32Restriction))
		}
		// Now extract the relevant bits.  Pretend the hash was masked with the
		// value 0xFFFF_FFFFL, so that we can right shift it in a way that's
		// equivalent to unsigned.
		val indexRestriction = intRestrictionForType(inclusive(0, mask))
		val preMaskRestriction = intRestrictionForType(
			inclusive(0L, 0xFFFF_FFFFL ushr bestShift))
		val inputForMasking = if (bestShift == 0)
		{
			semanticHashInt
		}
		else
		{
			val semanticShiftedInt = L2SemanticUnboxedInt(
				primitiveInvocation(
					P_BitShiftRight,
					listOf(
						primitiveInvocation(
							P_BitwiseAnd,
							listOf(
								semanticHash,
								constant(fromLong(0xFFFF_FFFFL)))),
						constant(
							fromInt(bestShift)))))
			manifest.equivalentSemanticValue(semanticShiftedInt) ?: run {
				// Neither the semantic value representing the shifted hash nor
				// an equivalent semantic value exist.  Do the shift.
				generator.addInstruction(
					L2_BIT_LOGIC_OP.bitwiseUnsignedShiftRight,
					L2ReadIntOperand(
						semanticHashInt,
						int32Restriction,
						generator.currentManifest),
					generator.unboxedIntConstant(bestShift),
					generator.intWrite(
						setOf(semanticShiftedInt),
						preMaskRestriction))
				semanticShiftedInt
			}
		}
		val indexWrite = generator.intWriteTemp(indexRestriction)
		generator.addInstruction(
			L2_BIT_LOGIC_OP.bitwiseAnd,
			L2ReadIntOperand(
				inputForMasking,
				preMaskRestriction,
				generator.currentManifest),
			generator.unboxedIntConstant(mask),
			indexWrite)
		// indexWrite's register now contains the shifted, masked value with
		// which to dispatch.
		val triples = mutableListOf(
			Triple(
				noMatchBlock,
				noMatchSubtree.castForGenerator(),
				extraSemanticArguments))
		val splitter = ShiftedHashSplitter(
			bestShift,
			mask,
			(1 .. mask).toList(),
			(0 .. mask).toList())
		generator.addInstruction(
			L2_MULTIWAY_JUMP,
			L2ReadIntOperand(
				indexWrite.pickSemanticValue(),
				indexRestriction,
				generator.currentManifest),
			L2ConstantOperand(identityPojo(splitter)),
			L2PcVectorOperand(
				(0 .. mask).map { index ->
					val pair = targetsByShiftedHash[index]
					L2PcOperand(
						pair?.first ?: noMatchBlock,
						false,
						L2ValueManifest(generator.currentManifest).apply {
							if (pair === null) return@apply
							// Exclude the values that aren't on this
							// branch.  This technique was chosen so
							// so that it works for exhaustive or not.
							val excluded = mutableSetOf<A_BasicObject>()
							targetsByShiftedHash.forEach { (i, pair) ->
								if (i == index) return@forEach
								pair.second.forEach { excluded.add(it.key) }
							}
							subtractType(
								semanticSource,
								enumerationWith(
									setFromCollection(excluded)))
						},
						"masked = 0x${toHexString(index)}")
				}))
		// At each of the targets of the multi-way jump, we still have to
		// test for the exact object(s).  The successful paths from those tests
		// lead to blocks we create for each subtree.  The chains that are
		// unsuccessful lead to the noMatchSubtree's block.
		targetsByShiftedHash.forEach { (_, pair) ->
			val (block, valuesWithTrees) = pair
			generator.startBlock(block)
			var nextFailure: L2BasicBlock?
			valuesWithTrees.forEach { (valueToCheck, targetTree) ->
				val success = L2BasicBlock("Equality succeeded")
				nextFailure = L2BasicBlock("Equality failed")
				generator.jumpIfEqualsConstant(
					generator.readBoxed(semanticSource),
					valueToCheck,
					success,
					nextFailure!!)
				triples.add(
					Triple(success, targetTree.cast(), extraSemanticArguments))
				generator.startBlock(nextFailure!!)
			}
			// We're now inside the last failure block for this masked index.
			// Jump to the noMatch case.
			generator.jumpTo(noMatchBlock)
		}
		return triples
	}

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
			subtree: LookupTree<A_Definition, A_Tuple>
		): Boolean
		{
			val nodes = mutableListOf(subtree)
			while (nodes.isNotEmpty())
			{
				when (val node = nodes.removeLast())
				{
					is LeafLookupTree ->
					{
						if (node.solutionOrNull.notNullAnd { tupleSize == 1 })
							return true
					}
					is InternalLookupTree ->
					{
						node.decisionStepOrNull?.simplyAddChildrenTo(nodes)
					}
				}
			}
			// We exhausted the tree.
			return false
		}
	}
}
