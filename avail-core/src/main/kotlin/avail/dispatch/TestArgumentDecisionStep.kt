/*
 * TestArgumentDecisionStep.kt
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
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_CONSTANT
import avail.interpreter.levelTwo.operation.L2_TYPE_UNION
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import java.lang.String.format

/**
 * This is a [DecisionStep] which tests a particular argument position
 * against some constant type.
 *
 * @constructor
 * Construct the new instance.
 *
 * @property argumentTypeToTest
 *   The type to test against an argument type at this node.
 * @property argumentPositionToTest
 *   The 1-based index of the argument to be tested at this node.
 * @property ifCheckHolds
 *   The tree to visit if the supplied arguments conform.
 * @property ifCheckFails
 *   The tree to visit if the supplied arguments do not conform.
 */
class TestArgumentDecisionStep<
	Element : A_BasicObject,
	Result : A_BasicObject>
constructor(
	val argumentTypeToTest: A_Type,
	argumentPositionToTest: Int,
	private val ifCheckHolds: LookupTree<Element, Result>,
	private val ifCheckFails: LookupTree<Element, Result>
) : DecisionStep<Element, Result>(argumentPositionToTest)
{
	override fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argument = extractArgument(argValues, extraValues)
		return if (argument.isInstanceOf(argumentTypeToTest))
		{
			ifCheckHolds
		}
		else ifCheckFails
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argumentType = extractArgumentType(argTypes, extraValues)
		return if (argumentType.isSubtypeOf(argumentTypeToTest))
		{
			ifCheckHolds
		}
		else ifCheckFails
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argumentType = extractArgumentType(argTypes, extraValues)
		return if (argumentType.isSubtypeOf(argumentTypeToTest))
		{
			ifCheckHolds
		}
		else ifCheckFails
	}

	override fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val value = extractValue(probeValue, extraValues)
		return if (value.isInstanceOf(argumentTypeToTest))
		{
			ifCheckHolds
		}
		else ifCheckFails
	}

	override fun describe(
		node: InternalLookupTree<Element, Result>,
		indent: Int,
		builder: StringBuilder
	): Unit = builder.run {
		append(
			increaseIndentation(
				format(
					"(u=%d, p=%d) #%d ∈ %s: known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					argumentTypeToTest,
					node.knownArgumentRestrictions),
				indent + 1))
		newlineTab(indent + 1)
		append(ifCheckHolds.toString(indent + 1))
		newlineTab(indent + 1)
		append(ifCheckFails.toString(indent + 1))
	}

	override fun simplyAddChildrenTo(
		list: MutableList<LookupTree<Element, Result>>)
	{
		list.add(ifCheckHolds)
		list.add(ifCheckFails)
	}

	override fun addChildrenTo(
		list: MutableList<
			Pair<LookupTree<Element, Result>, List<L2SemanticValue>>>,
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>)
	{
		list.add(ifCheckHolds to extraSemanticValues)
		list.add(ifCheckFails to extraSemanticValues)
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
		val translator = callSiteHelper.translator()
		val generator = translator.generator
		if (!generator.currentlyReachable())
		{
			// If no paths lead here, don't generate code.  This can happen
			// when we short-circuit type-tests into unconditional jumps,
			// due to the complexity of super calls.  This eliminates an
			// entire subtree.
			return emptyList()
		}
		val counter = callSiteHelper.branchLabelCounter++
		val passBlock = generator.createBasicBlock(
			"Pass #$counter for ${callSiteHelper.quotedBundleName}")
		val failBlock = generator.createBasicBlock(
			"Fail #$counter for ${callSiteHelper.quotedBundleName}")
		val result = listOf(
			Triple(
				passBlock,
				ifCheckHolds.castForGenerator(),
				extraSemanticArguments),
			Triple(
				failBlock,
				ifCheckFails.castForGenerator(),
				extraSemanticArguments))
		val semanticArgument =
			sourceSemanticValue(semanticArguments, extraSemanticArguments)
		val argRead = generator.readBoxed(semanticArgument)
		val argRestriction = argRead.restriction()

		// Tricky here.  We have the type we want to test for, and we have
		// the argument for which we want to test the type, but we also have
		// an element of the superUnionType to consider.  And that element
		// might be a combination of restrictions and bottoms.  Deal with
		// the easy, common cases first.
		val superUnionElementType =
			callSiteHelper.superUnionType.typeAtIndex(
				argumentPositionToTest)
		if (superUnionElementType.isBottom)
		{
			// It's not a super call, or at least this test isn't related to
			// any parts that are supercast upward.
			val intersection =
				argRestriction.intersectionWithType(argumentTypeToTest)
			if (intersection === TypeRestriction.bottomRestriction)
			{
				// It will always fail the test.
				generator.jumpTo(failBlock)
				return result
			}
			if (argRestriction.type.isSubtypeOf(argumentTypeToTest))
			{
				// It will always pass the test.
				generator.jumpTo(passBlock)
				return result
			}

			// A runtime test is needed.  Try to special-case small enumeration.
			val possibleValues =
				intersection.enumerationValuesOrNull(
					L2Generator.maxExpandedEqualityChecks)
			if (possibleValues !== null)
			{
				// The restriction has a small number of values.  Use equality
				// checks rather than the more general type checks.
				val iterator = possibleValues.iterator()
				var instance: A_BasicObject = iterator.next()
				while (iterator.hasNext())
				{
					val nextCheckOrFail = generator.createBasicBlock(
						"test next case of enumeration")
					generator.jumpIfEqualsConstant(
						argRead,
						instance,
						passBlock,
						nextCheckOrFail)
					generator.startBlock(nextCheckOrFail)
					instance = iterator.next()
				}
				generator.jumpIfEqualsConstant(
					argRead,
					instance,
					passBlock,
					failBlock)
				return result
			}
			// A runtime test is needed, and it's not a small enumeration.
			generator.jumpIfKindOfConstant(
				argRead,
				argumentTypeToTest,
				passBlock,
				failBlock)
			return result
		}

		// The argument is subject to a super-cast.
		if (argRestriction.type.isSubtypeOf(superUnionElementType))
		{
			// The argument's actual type will always be a subtype of the
			// superUnion type, so the dispatch will always be decided by only
			// the superUnion type, which does not vary at runtime.  Decide
			// the branch direction right now.
			when
			{
				superUnionElementType.isSubtypeOf(argumentTypeToTest) ->
					generator.jumpTo(passBlock)
				else -> generator.jumpTo(failBlock)
			}
			return result
		}

		// This is the most complex case, where the argument dispatch type is a
		// mixture of supercasts and non-supercasts.  Do it the slow way with a
		// type union.  Technically, the superUnionElementType's recursive tuple
		// structure mimics the call site, so it must have a fixed, finite
		// structure corresponding with occurrences of supercasts syntactically.
		// Thus, in theory we could analyze the superUnionElementType and
		// generate a more complex collection of branches – but this is already
		// a pretty rare case.
		val argMeta = instanceMeta(argRestriction.type)
		val argTypeWrite =
			generator.boxedWriteTemp(argRestriction.metaRestriction())
		generator.addInstruction(L2_GET_TYPE, argRead, argTypeWrite)
		val superUnionReg = generator.boxedConstant(superUnionElementType)
		val unionReg = generator.boxedWriteTemp(
			restrictionForType(
				argMeta.typeUnion(superUnionReg.type()), BOXED_FLAG))
		generator.addInstruction(
			L2_TYPE_UNION,
			generator.readBoxed(argTypeWrite),
			superUnionReg,
			unionReg)
		generator.addInstruction(
			L2_JUMP_IF_SUBTYPE_OF_CONSTANT,
			generator.readBoxed(unionReg),
			L2ConstantOperand(argumentTypeToTest),
			edgeTo(passBlock),
			edgeTo(failBlock))
		return result
	}
}
