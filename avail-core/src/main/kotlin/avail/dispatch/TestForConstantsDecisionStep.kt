/*
 * TestForConstantsDecisionStep.kt
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
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.Companion.createSmallInterval
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_HASH
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MULTIWAY_JUMP
import avail.interpreter.primitive.general.P_Hash
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import avail.utility.cast
import java.lang.String.format

/**
 * This is a [DecisionStep] which dispatches to subtrees by looking up the exact
 * value in a particular argument position.  Since Avail objects have permanent
 * hash values (over the lifetime of an OS process), we can use the hash value
 * for an initial lookup, followed by a full check that it's the right object
 * and not just a hash collision.
 *
 * Note that since metatypes have not just the instance type but all its
 * subtypes as effective instances, we exclude them from the map.  The bottom
 * type is an exception, since it has only the instance bottom (⊥).
 *
 * @constructor
 * Construct the new instance.
 *
 * @property argumentPositionToTest
 *   The 1-based index of the argument for which to test by equality.
 * @property valueToSubtree
 *   A [Map] from [A_BasicObject] to the child [LookupTree] that should be
 *   visited if the given value occurs during lookup.  If the provided value is
 *   not present, the [noMatchSubtree] will be visited instead.
 * @property noMatchSubtree
 *   The [LookupTree] to visit if the provided value is not present as a key in
 *   [valueToSubtree].
 * @property bypassForTypeLookup
 *   The [LookupTree] to visit if looking up by type.
 */
class TestForConstantsDecisionStep<
	Element : A_BasicObject,
	Result : A_BasicObject>
constructor(
	argumentPositionToTest: Int,
	private val valueToSubtree: Map<A_BasicObject, LookupTree<Element, Result>>,
	private val noMatchSubtree: LookupTree<Element, Result>,
	private val bypassForTypeLookup: LookupTree<Element, Result>
) : DecisionStep<Element, Result>(argumentPositionToTest)
{
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
		return valueToSubtree[argument] ?: noMatchSubtree
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return bypassForTypeLookup
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return bypassForTypeLookup
	}

	override fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argument = extractValue(probeValue, extraValues)
		return valueToSubtree[argument] ?: noMatchSubtree
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
					"(u=%d, p=%d) #%d constants : known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					node.knownArgumentRestrictions),
				indent + 1))
		valueToSubtree.entries
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
				append("CONST=$keyString: ")
				append(value.toString(indent + 1))
			}
		newlineTab(indent + 1)
		append("noMatch: ")
		append(noMatchSubtree.toString(indent + 1))
		newlineTab(indent + 1)
		append("bypassForTypeLookup: ")
		append(bypassForTypeLookup.toString(indent + 1))
	}

	override fun simplyAddChildrenTo(
		list: MutableList<LookupTree<Element, Result>>)
	{
		// Add them all, even though bypassForTypeLookup isn't really mutually
		// exclusive of the rest.
		list.addAll(valueToSubtree.values)
		list.add(noMatchSubtree)
		list.add(bypassForTypeLookup)
	}

	override fun addChildrenTo(
		list: MutableList<
			Pair<LookupTree<Element, Result>, List<L2SemanticValue>>>,
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>)
	{
		valueToSubtree.values.forEach { subtree ->
			list.add(subtree to extraSemanticValues)
		}
		list.add(noMatchSubtree to extraSemanticValues)
		list.add(bypassForTypeLookup to extraSemanticValues)
	}

	override fun generateEdgesFor(
		semanticArguments: List<L2SemanticValue>,
		extraSemanticArguments: List<L2SemanticValue>,
		callSiteHelper: CallSiteHelper
	): List<
		Triple<
			L2BasicBlock,
			LookupTree<A_Definition, A_Tuple>,
			List<L2SemanticValue>>>
	{
		// For simplicity, let super-lookups always fall back.
		// They're *very* difficult to reason about.
		if (callSiteHelper.isSuper)
		{
			callSiteHelper.generator().jumpTo(
				callSiteHelper.onFallBackToSlowLookup)
			return emptyList()
		}

		val semanticSource =
			sourceSemanticValue(semanticArguments, extraSemanticArguments)
		val generator = callSiteHelper.generator()
		val manifest = generator.currentManifest
		val sourceRestriction = manifest.restrictionFor(semanticSource)
		var residue = sourceRestriction
		val reachableEntries = valueToSubtree.entries.filter { (key, _) ->
			residue = residue.minusValue(key)
			sourceRestriction.intersectsType(instanceTypeOrMetaOn(key))
		}
		// True if there are no other possible values that could occur.
		val exhaustive = residue.type.isBottom
		when
		{
			reachableEntries.isEmpty() && exhaustive ->
			{
				// Nothing is possible here.
				generator.addUnreachableCode()
				return emptyList()
			}
			(reachableEntries.size == 1 && exhaustive)
				|| (reachableEntries.isEmpty() && !exhaustive) ->
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
		val hashes = reachableEntries.map { (k, _) -> k.hash() }.toIntArray()
		val leadingZeros = hashes.size.countLeadingZeroBits()
		val mask = -1 ushr leadingZeros
		val ratingsByShift = (0..leadingZeros).map { shift ->
			shift to hashes.map { (it ushr shift) and mask }.distinct().size
		}
		val (bestShift, _) = ratingsByShift.maxByOrNull(Pair<*, Int>::second)!!
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
		val int32Restriction = restrictionForType(int32, UNBOXED_INT_FLAG)
		val semanticHash = primitiveInvocation(P_Hash, listOf(semanticSource))
		val semanticHashInt = L2SemanticUnboxedInt(semanticHash)
		if (!generator.currentManifest.hasSemanticValue(semanticHashInt))
		{
			generator.addInstruction(
				L2_HASH,
				generator.readBoxed(semanticSource),
				generator.intWrite(setOf(semanticHashInt), int32Restriction))
		}
		// Now extract the relevant bits.  Use temps.
		val indexRestriction =
			restrictionForType(inclusive(0L, mask.toLong()), UNBOXED_INT_FLAG)
		val indexWrite = generator.intWriteTemp(indexRestriction)
		val tempShifted = generator.intWriteTemp(int32Restriction)
		if (bestShift != 0)
		{
			generator.addInstruction(
				L2_BIT_LOGIC_OP.bitwiseUnsignedShiftRight,
				L2ReadIntOperand(
					semanticHashInt,
					int32Restriction,
					generator.currentManifest),
				generator.unboxedIntConstant(bestShift),
				tempShifted)
		}
		else
		{
			generator.moveRegister(
				L2_MOVE.unboxedInt,
				semanticHashInt,
				tempShifted.pickSemanticValue())
		}
		generator.addInstruction(
			L2_BIT_LOGIC_OP.bitwiseAnd,
			L2ReadIntOperand(
				semanticHashInt,
				int32Restriction,
				generator.currentManifest),
			generator.unboxedIntConstant(mask),
			indexWrite)
		// indexWrite's register now contains the shifted, masked value with
		// which to dispatch.
		val triples = mutableListOf(
			Triple(
				noMatchBlock,
				noMatchSubtree
					.cast<LookupTree<*,*>, LookupTree<A_Definition, A_Tuple>>(),
				extraSemanticArguments))
		generator.addInstruction(
			L2_MULTIWAY_JUMP,
			L2ReadIntOperand(
				indexWrite.pickSemanticValue(),
				indexRestriction,
				generator.currentManifest),
			L2ConstantOperand(createSmallInterval(1, mask, 1)),
			L2PcVectorOperand(
				(0..mask).map { index ->
					L2PcOperand(
						targetsByShiftedHash[index]?.first ?: noMatchBlock,
						false,
						null,
						"masked = $$${Integer.toHexString(index)}")
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
}
