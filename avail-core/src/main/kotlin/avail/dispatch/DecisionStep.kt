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

import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.methods.A_Definition
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectTypeDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.computeSuperkind
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.InstanceMetaDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.nonnegativeInt32
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.Companion.tagFromOrdinal
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_EXTRACT_OBJECT_VARIANT_ID
import avail.interpreter.levelTwo.operation.L2_EXTRACT_TAG_ORDINAL
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_COMPARE_INT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_CONSTANT
import avail.interpreter.levelTwo.operation.L2_MULTIWAY_JUMP
import avail.interpreter.levelTwo.operation.L2_STRENGTHEN_TYPE
import avail.interpreter.levelTwo.operation.L2_TYPE_UNION
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticObjectVariantId
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.newlineTab
import avail.utility.cast
import avail.utility.notNullAnd
import avail.utility.partitionRunsBy
import avail.utility.removeLast
import java.lang.String.format
import java.util.concurrent.ConcurrentHashMap

/**
 * This abstraction represents a mechanism for achieving a quantum of
 * progress toward looking up which method definition to invoke, or some
 * similar usage.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
sealed class DecisionStep<Element : A_BasicObject, Result : A_BasicObject>
{
	/**
	 * Given an optional array of values used to supplement the lookup, answer
	 * the updated optional array of values that takes this step into account.
	 * The given and resulting arrays may be modified or replaced, at the
	 * discretion of the step.
	 *
	 * By default, simply return the input.
	 */
	fun updateExtraValues(
		extraValues: Array<Element?>?
	): Array<Element?>? = extraValues

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided list of arguments.  Answer another [LookupTree] with which
	 * to continue the search.
	 *
	 * @param argValues
	 *   The [List] of arguments being looked up.
	 * @param extraValues
	 *   An optional mutable array of additional values, only created when
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
		extraValues: Array<Element?>?,
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
	 *   An optional mutable array of additional values, only created when
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
		extraValues: Array<Element?>?,
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
	 *   An optional mutable array of additional values, only created when
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
		extraValues: Array<Element?>?,
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
	 *   An optional mutable array of additional values, only created when
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
		extraValues: Array<Element?>?,
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
	 *   An optional mutable array of additional values, only created when
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
		extraValues: Array<Element?>?,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Add the children [LookupTree]s to the given [list].
	 *
	 * @param list
	 *   The list in which to add the children, in an arbitrary order
	 */
	abstract fun addChildrenTo(list: MutableList<LookupTree<Element, Result>>)

	/**
	 * Generate suitable branch instructions via the [CallSiteHelper], and
	 * answer a list of [Pair]s that coordinate each target [L2BasicBlock] with
	 * the [LookupTree] responsible for generating code in that block.
	 */
	abstract fun generateEdgesFor(
		semanticArguments: List<L2SemanticValue>,
		callSiteHelper: CallSiteHelper
	): List<Pair<L2BasicBlock, LookupTree<A_Definition, A_Tuple>>>

	/**
	 * Output a description of this step on the given [builder].  Do not expand
	 * any subtrees that are still lazy.
	 */
	abstract fun describe(
		node : InternalLookupTree<Element, Result>,
		indent: Int,
		builder: StringBuilder)

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
		val argumentPositionToTest: Int,
		private val ifCheckHolds: LookupTree<Element, Result>,
		private val ifCheckFails: LookupTree<Element, Result>
	) : DecisionStep<Element, Result>()
	{
		override fun <AdaptorMemento> lookupStepByValues(
			argValues: List<A_BasicObject>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues[index - 1]
			return if (argument.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByValues(
			argValues: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues.tupleAt(index)
			return if (argument.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: List<A_Type>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes[index - 1]
			return if (argumentType.isSubtypeOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes.tupleAt(index)
			return if (argumentType.isSubtypeOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByValue(
			probeValue: A_BasicObject,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index == 0)
			return if (probeValue.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun describe(
			node : InternalLookupTree<Element, Result>,
			indent: Int,
			builder: StringBuilder
		): Unit = builder.run {
			append(
				format(
					"(u=%d, p=%d) #%d ∈ %s: known=%s%n",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					argumentTypeToTest,
					node.knownArgumentRestrictions))
			for (i in 0..indent)
			{
				append("\t")
			}
			append(ifCheckHolds.toString(indent + 1))
			append(format("%n"))
			for (i in 0..indent)
			{
				append("\t")
			}
			append(ifCheckFails.toString(indent + 1))
		}

		override fun addChildrenTo(
			list: MutableList<LookupTree<Element, Result>>)
		{
			list.add(ifCheckHolds)
			list.add(ifCheckFails)
		}

		override fun generateEdgesFor(
			semanticArguments: List<L2SemanticValue>,
			callSiteHelper: CallSiteHelper
		): List<Pair<L2BasicBlock, LookupTree<A_Definition, A_Tuple>>>
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
			val result =
				listOf<Pair<L2BasicBlock, LookupTree<A_Definition, A_Tuple>>>(
					passBlock to ifCheckHolds.cast(),
					failBlock to ifCheckFails.cast())
			val semanticArgument = semanticArguments[argumentPositionToTest - 1]
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
						translator.jumpIfEqualsConstant(
							argRead,
							instance,
							passBlock,
							nextCheckOrFail)
						generator.startBlock(nextCheckOrFail)
						instance = iterator.next()
					}
					translator.jumpIfEqualsConstant(
						argRead,
						instance,
						passBlock,
						failBlock)
					return result
				}
				// A runtime test is needed, and it's not a small enumeration.
				translator.jumpIfKindOfConstant(
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
				generator.jumpTo(
					when
					{
						superUnionElementType.isSubtypeOf(argumentTypeToTest) ->
							passBlock
						else -> failBlock
					})
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
			val argMeta =
				InstanceMetaDescriptor.instanceMeta(argRestriction.type)
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

	///**
	// * This is a [DecisionStep] which extracts a value or type that's a part of
	// * another value or type, and can be used to filter more quickly than the
	// * original.  For example, a phrase type's yield type depends covariantly on
	// * the phrase type, but sometimes the yield type can be efficiently
	// * dispatched by a subsequent [TypeTagDecisionStep], which would be
	// * inaccessible if we didn't extract the yield type first.
	// *
	// * Note that if the value to be tested at the current tree node isn't all of
	// * one [TypeTag], this type of step will not yet be available, and a
	// * [TypeTagDecisionStep] can be used first.
	// *
	// * @constructor
	// * Construct the new instance.
	// *
	// * @property argumentTypeToTest
	// *   The type to test against an argument type at this node.
	// * @property argumentPositionToTest
	// *   The 1-based index of the argument to be tested at this node.
	// * @property ifCheckHolds
	// *   The tree to visit if the supplied arguments conform.
	// * @property ifCheckFails
	// *   The tree to visit if the supplied arguments do not conform.
	// */
	//class ExtractVariantDecisionStep<
	//	Element : A_BasicObject,
	//	Result : A_BasicObject>
	//constructor(
	//	val argumentTypeToTest: A_Type,
	//	val argumentPositionToTest: Int,
	//	val ifCheckHolds: LookupTree<Element, Result>,
	//	val ifCheckFails: LookupTree<Element, Result>
	//) : DecisionStep<Element, Result>()
	//{
	//	override fun <AdaptorMemento> lookupStepByValues(
	//		argValues: List<A_BasicObject>,
	//		extraValues: Array<Element?>?,
	//		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
	//		memento: AdaptorMemento): LookupTree<Element, Result>
	//	{
	//		val index = argumentPositionToTest
	//		assert(index > 0)
	//		val argument = argValues[index - 1]
	//		return if (argument.isInstanceOf(argumentTypeToTest))
	//		{
	//			ifCheckHolds
	//		}
	//		else ifCheckFails
	//	}
	//
	//	override fun <AdaptorMemento> lookupStepByValues(
	//		argValues: A_Tuple,
	//		extraValues: Array<Element?>?,
	//		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
	//		memento: AdaptorMemento): LookupTree<Element, Result>
	//	{
	//		val index = argumentPositionToTest
	//		assert(index > 0)
	//		val argument = argValues.tupleAt(index)
	//		return if (argument.isInstanceOf(argumentTypeToTest))
	//		{
	//			ifCheckHolds
	//		}
	//		else ifCheckFails
	//	}
	//
	//	override fun <AdaptorMemento> lookupStepByTypes(
	//		argTypes: List<A_Type>,
	//		extraValues: Array<Element?>?,
	//		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
	//		memento: AdaptorMemento): LookupTree<Element, Result>
	//	{
	//		val index = argumentPositionToTest
	//		assert(index > 0)
	//		val argumentType = argTypes[index - 1]
	//		return if (argumentType.isSubtypeOf(argumentTypeToTest))
	//		{
	//			ifCheckHolds
	//		}
	//		else ifCheckFails
	//	}
	//
	//	override fun <AdaptorMemento> lookupStepByTypes(
	//		argTypes: A_Tuple,
	//		extraValues: Array<Element?>?,
	//		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
	//		memento: AdaptorMemento): LookupTree<Element, Result>
	//	{
	//		val index = argumentPositionToTest
	//		assert(index > 0)
	//		val argumentType = argTypes.tupleAt(index)
	//		return if (argumentType.isSubtypeOf(argumentTypeToTest))
	//		{
	//			ifCheckHolds
	//		}
	//		else ifCheckFails
	//	}
	//
	//	override fun <AdaptorMemento> lookupStepByValue(
	//		probeValue: A_BasicObject,
	//		extraValues: Array<Element?>?,
	//		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
	//		memento: AdaptorMemento): LookupTree<Element, Result>
	//	{
	//		val index = argumentPositionToTest
	//		assert(index == 0)
	//		return if (probeValue.isInstanceOf(argumentTypeToTest))
	//		{
	//			ifCheckHolds
	//		}
	//		else ifCheckFails
	//	}
	//
	//	override fun addChildrenTo(list: MutableList<LookupTree<Element, Result>>)
	//	{
	//		TODO("Not yet implemented")
	//	}
	//
	//	override fun generateEdgesFor(
	//		semanticArguments: List<L2SemanticValue>,
	//		callSiteHelper: CallSiteHelper): List<Pair<L2BasicBlock, LookupTree<A_Definition, A_Tuple>>>
	//	{
	//		TODO("Not yet implemented")
	//	}
	//
	//	override fun describe(
	//		node : InternalLookupTree<Element, Result>,
	//		indent: Int,
	//		builder: StringBuilder
	//	): Unit = builder.run {
	//		append(
	//			format(
	//				"(u=%d, p=%d) #%d ∈ %s: known=%s%n",
	//				node.undecidedElements.size,
	//				node.positiveElements.size,
	//				argumentPositionToTest,
	//				argumentTypeToTest,
	//				node.knownArgumentRestrictions))
	//		for (i in 0..indent)
	//		{
	//			append("\t")
	//		}
	//		append(ifCheckHolds.toString(indent + 1))
	//		append(format("%n"))
	//		for (i in 0..indent)
	//		{
	//			append("\t")
	//		}
	//		append(ifCheckFails.toString(indent + 1))
	//	}
	//}

	/**
	 * This is a [DecisionStep] which dispatches to subtrees by looking up the
	 * argument's [ObjectLayoutVariant].  It can only be used when the argument
	 * has been constrained to an [object][mostGeneralObjectType].
	 *
	 * The idea is to filter out most variants based on the fields that they
	 * define, *and* the fields they specifically don't define.  If variant #1
	 * has fields {x, y}, and variant #2 has fields {y, z}, then an object with
	 * variant #1 can't ever be an instance of an object type using variant #2.
	 *
	 * Note that only the variants which have occurred have entries in this
	 * step; new variants arriving will be dynamically added to this step.
	 *
	 * Also note that when an argument position is specified with a type that is
	 * an enumeration of multiple objects, the corresponding [Result] is
	 * duplicated for each of the objects' variants, ensuring a lookup of any of
	 * those actual objects can reach the correct solution.
	 *
	 * @constructor
	 * Construct the new instance.
	 *
	 * @property thisInternalLookupTree
	 *   A reference to the [InternalLookupTree] in which this has been
	 *   installed as the [DecisionStep].
	 * @property argumentPositionToTest
	 *   The 1-based index of the argument for which to test by
	 *   [ObjectLayoutVariant].
	 * @property variantToElements
	 *   A [Map] grouping this step's [Element]s by their [ObjectLayoutVariant]
	 *   at the indicated [argumentPositionToTest].
	 */
	class ObjectLayoutVariantDecisionStep<
		Element : A_BasicObject,
		Result : A_BasicObject>
	constructor(
		private val thisInternalLookupTree: InternalLookupTree<Element, Result>,
		val argumentPositionToTest: Int,
		private val variantToElements: Map<ObjectLayoutVariant, List<Element>>,
		private val alreadyVariantTestedArgumentsForChildren: A_Number
	) : DecisionStep<Element, Result>()
	{
		/**
		 * A [Map] from [ObjectLayoutVariant.variantId] to the child
		 * [LookupTree] that should be visited if the given
		 * [ObjectLayoutVariant] occurs during lookup.  If the provided variant
		 * is not present, it will be added dynamically.
		 */
		private val variantToSubtree: ConcurrentHashMap
				<ObjectLayoutVariant, LookupTree<Element, Result>> =
			ConcurrentHashMap()

		/**
		 * Given the actual [variant] that has been supplied for an actual
		 * lookup, collect the relevant [Element]s into a suitable [LookupTree].
		 */
		private fun elementsForVariant(
			variant: ObjectLayoutVariant
		): List<Element>
		{
			val entries = variantToElements.entries.filter {
				variant.isSubvariantOf(it.key)
			}
			return when (entries.size)
			{
				0 -> emptyList()
				1 -> entries.first().value
				else -> entries.flatMap { it.value }
			}
		}

		override fun <AdaptorMemento> lookupStepByValues(
			argValues: List<A_BasicObject>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues[index - 1].traversed()
			val descriptor = argument.descriptor() as ObjectDescriptor
			val variant = descriptor.variant
			return variantToSubtree.getOrPut(variant) {
				thisInternalLookupTree.run {
					adaptor.createTree(
						positiveElements,
						elementsForVariant(variant),
						knownArgumentRestrictions,
						alreadyTypeTestedArguments,
						alreadyVariantTestedArgumentsForChildren,
						memento)
				}
			}
		}

		override fun <AdaptorMemento> lookupStepByValues(
			argValues: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues.tupleAt(index)
			val descriptor = argument.descriptor() as ObjectDescriptor
			val variant = descriptor.variant
			return variantToSubtree.getOrPut(variant) {
				thisInternalLookupTree.run {
					adaptor.createTree(
						positiveElements,
						elementsForVariant(variant),
						knownArgumentRestrictions,
						alreadyTypeTestedArguments,
						alreadyVariantTestedArgumentsForChildren,
						memento)
				}
			}
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: List<A_Type>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes[index - 1].traversed()
			val argumentKind = when (argumentType.isEnumeration)
			{
				true -> argumentType.computeSuperkind()
				else -> argumentType
			}
			val descriptor = argumentKind.descriptor() as ObjectTypeDescriptor
			val variant = descriptor.variant
			return variantToSubtree.getOrPut(variant) {
				thisInternalLookupTree.run {
					adaptor.createTree(
						positiveElements,
						elementsForVariant(variant),
						knownArgumentRestrictions,
						alreadyTypeTestedArguments,
						alreadyVariantTestedArgumentsForChildren,
						memento)
				}
			}
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes.tupleAt(index).traversed()
			val argumentKind = when (argumentType.isEnumeration)
			{
				true -> argumentType.computeSuperkind()
				else -> argumentType
			}
			val descriptor = argumentKind.descriptor() as ObjectTypeDescriptor
			val variant = descriptor.variant
			return variantToSubtree.getOrPut(variant) {
				thisInternalLookupTree.run {
					adaptor.createTree(
						positiveElements,
						elementsForVariant(variant),
						knownArgumentRestrictions,
						alreadyTypeTestedArguments,
						alreadyVariantTestedArgumentsForChildren,
						memento)
				}
			}
		}

		override fun <AdaptorMemento> lookupStepByValue(
			probeValue: A_BasicObject,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			assert(argumentPositionToTest == 0)
			val argument = probeValue.traversed()
			val descriptor = argument.descriptor() as ObjectDescriptor
			val variant = descriptor.variant
			return variantToSubtree.getOrPut(variant) {
				thisInternalLookupTree.run {
					adaptor.createTree(
						positiveElements,
						elementsForVariant(variant),
						knownArgumentRestrictions,
						alreadyTypeTestedArguments,
						alreadyVariantTestedArgumentsForChildren,
						memento)
				}
			}
		}

		override fun describe(
			node: InternalLookupTree<Element, Result>,
			indent: Int,
			builder: StringBuilder
		): Unit = with(builder)
		{
			val entries = variantToSubtree.entries.toList()
			append(
				format(
					"(u=%d, p=%d) #%d variants : known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					node.knownArgumentRestrictions))
			entries.sortedBy { it.key.variantId }.forEach { (variant, child) ->
				newlineTab(indent + 1)
				append("VAR#${variant.variantId}")
				variant.allFields
					.map { it.atomName.asNativeString() }
					.sorted()
					.joinTo(this, ", ", " (", "): ")
				append(child.toString(indent + 1))
			}
		}

		override fun addChildrenTo(
			list: MutableList<LookupTree<Element, Result>>)
		{
			list.addAll(variantToSubtree.values)
		}

		override fun generateEdgesFor(
			semanticArguments: List<L2SemanticValue>,
			callSiteHelper: CallSiteHelper
		): List<Pair<L2BasicBlock, LookupTree<A_Definition, A_Tuple>>>
		{
			// Create a multi-way branch using an object's variant.  Any variant
			// that wasn't present during generation will jump to the slower,
			// general lookup.  The slow lookup will populate the map with a new
			// subtree, which (TODO) should increase pressure to reoptimize the
			// calling method, specifically to include the new variant.
			val semanticSource = semanticArguments[argumentPositionToTest - 1]
			val generator = callSiteHelper.generator()
			val currentRestriction =
				generator.currentManifest.restrictionFor(semanticSource)
			val restrictionType = currentRestriction.type.traversed()
			val restrictionDescriptor =
				restrictionType.descriptor() as? ObjectTypeDescriptor ?:
					restrictionType.computeSuperkind().descriptor()
						as ObjectTypeDescriptor
			val restrictionVariant = restrictionDescriptor.variant
			// Only keep relevant variants, and only if they lead to at least
			// one success.
			val applicableEntries = variantToSubtree.entries
				.filter { (key, subtree) ->
					key.isSubvariantOf(restrictionVariant)
						&& containsAnyValidLookup(subtree.cast())
				}
				.sortedBy { (key, _) -> key.variantId }
			if (applicableEntries.isEmpty())
			{
				// Just jump to the slow lookup, and don't continue down any
				// more lookup subtrees.
				generator.jumpTo(callSiteHelper.onFallBackToSlowLookup)
				return emptyList()
			}
			val semanticVariantId = L2SemanticUnboxedInt(
				L2SemanticObjectVariantId(semanticSource))
			generator.addInstruction(
				L2_EXTRACT_OBJECT_VARIANT_ID,
				generator.readBoxed(semanticSource),
				generator.intWrite(
					setOf(semanticVariantId),
					restrictionForType(nonnegativeInt32, UNBOXED_INT_FLAG)))
			if (applicableEntries.size == 1)
			{
				// Check for the only variant that leads to a solution.
				val (variant, subtree) = applicableEntries[0]
				val variantId = variant.variantId
				val matchBlock = L2BasicBlock("matches variant #$variantId")
				val trulyUnreachable = L2BasicBlock("truly unreachable")
				L2_JUMP_IF_COMPARE_INT.equal.compareAndBranch(
					callSiteHelper.generator(),
					generator.readInt(semanticVariantId, trulyUnreachable),
					generator.unboxedIntConstant(variantId),
					edgeTo(matchBlock),
					edgeTo(callSiteHelper.onFallBackToSlowLookup))
				assert(trulyUnreachable.predecessorEdges().isEmpty())
				return listOf(matchBlock to subtree.cast())
			}
			// There are at least two variants that can lead to valid solutions,
			// so extract create a multi-way branch.
			val splits = mutableListOf<Int>()
			val targets = mutableListOf(callSiteHelper.onFallBackToSlowLookup)
			val result = mutableListOf<
				Pair<L2BasicBlock, LookupTree<A_Definition, A_Tuple>>>()
			var lastSplit = 0
			// The multi-way branch has positive cases for each individual
			// possible variant (based on the known restrictions at this site),
			// and fall-through cases for the spans between them.
			applicableEntries.forEach { (variant, subtree) ->
				val variantId = variant.variantId
				if (variantId == lastSplit)
				{
					// Two adjacent variantIds occurred, so we save a split.
					assert(targets.last()
						== callSiteHelper.onFallBackToSlowLookup)
					targets.removeLast()
					splits.removeLast()
				}
				val target = L2BasicBlock("Variant = #$variantId")
				splits.add(variantId)
				targets.add(target)
				result.add(target to subtree.cast())
				splits.add(variantId + 1)
				targets.add(callSiteHelper.onFallBackToSlowLookup)
				lastSplit = variantId + 1
			}
			assert(targets.size == splits.size + 1)
			assert(result.size == applicableEntries.size)
			// Generate the multi-way branch.
			generator.addInstruction(
				L2_MULTIWAY_JUMP,
				generator.currentManifest.readInt(semanticVariantId),
				L2ConstantOperand(tupleFromIntegerList(splits)),
				L2PcVectorOperand(targets.map { L2PcOperand(it, false) }))
			return result
		}
	}

	/**
	 * This is a [DecisionStep] which dispatches to subtrees by looking up the
	 * [TypeTag] for a particular argument position.  Narrowing the effective
	 * [TypeRestriction] on the argument in this way can quickly reduce the
	 * number of applicable elements in the subtrees, which may also promote
	 * other techniques, such as dispatching on an object or object type's
	 * [ObjectLayoutVariant], or extracting a covariant or contravariant
	 * parameter into a separate argument.
	 *
	 * @constructor
	 * Construct the new instance.
	 *
	 * @property argumentPositionToTest
	 *   The 1-based index of the argument for which to test by [TypeTag].
	 * @property tagToSubtree
	 *   A [Map] from [TypeTag] to the child [LookupTree] that should be visited
	 *   if the given tag occurs during lookup.  If the provided tag is not
	 *   present, its ancestors will be looked up until successful.
	 */
	class TypeTagDecisionStep<
		Element : A_BasicObject,
		Result : A_BasicObject>
	constructor(
		val argumentPositionToTest: Int,
		private val tagToSubtree: Map<TypeTag, LookupTree<Element, Result>>
	) : DecisionStep<Element, Result>()
	{
		override fun <AdaptorMemento> lookupStepByValues(
			argValues: List<A_BasicObject>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues[index - 1]
			var tag = (argument as AvailObject).typeTag
			while (true)
			{
				tagToSubtree[tag]?.let { return it }
				tag = tag.parent ?: return adaptor.emptyLeaf
			}
		}

		override fun <AdaptorMemento> lookupStepByValues(
			argValues: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues.tupleAt(index)
			var tag = argument.typeTag
			while (true)
			{
				tagToSubtree[tag]?.let { return it }
				tag = tag.parent ?: return adaptor.emptyLeaf
			}
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: List<A_Type>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes[index - 1]
			var tag = argumentType.instanceTag
			while (true)
			{
				tagToSubtree[tag]?.let { return it }
				tag = tag.parent ?: return adaptor.emptyLeaf
			}
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes.tupleAt(index)
			var tag = argumentType.instanceTag
			while (true)
			{
				tagToSubtree[tag]?.let { return it }
				tag = tag.parent ?: return adaptor.emptyLeaf
			}
		}

		override fun <AdaptorMemento> lookupStepByValue(
			probeValue: A_BasicObject,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index == 0)
			var tag = (probeValue as AvailObject).typeTag
			while (true)
			{
				tagToSubtree[tag]?.let { return it }
				tag = tag.parent ?: return adaptor.emptyLeaf
			}
		}

		override fun describe(
			node: InternalLookupTree<Element, Result>,
			indent: Int,
			builder: StringBuilder
		): Unit = with(builder)
		{
			append(
				format(
					"(u=%d, p=%d) #%d typeTag : known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					node.knownArgumentRestrictions))
			for ((k, v) in tagToSubtree.entries.sortedBy { it.key.ordinal })
			{
				newlineTab(indent + 1)
				append("$k(${k.ordinal}): ")
				append(v.toString(indent + 1))
			}
		}

		override fun addChildrenTo(
			list: MutableList<LookupTree<Element, Result>>)
		{
			list.addAll(tagToSubtree.values)
		}

		/**
		 * A private class for keeping track of a run of tags and its associated
		 * information.
		 *
		 * @property low
		 *   The lowest [TypeTag] ordinal included in this [Span].
		 * @property high
		 *   The highest [TypeTag] ordinal included in this [Span].
		 * @property subtree
		 *   The [LookupTree] reachable through this [Span].
		 * @property tag
		 *   The [TypeTag] that this [Span] guarantees the value will be in. If
		 *   this is null, the [Span] should be treated as a don't-care.
		 * @property restriction
		 *   The [TypeRestriction] for the value when this [Span] is in effect.
		 */
		private data class Span(
			val low: Int,
			val high: Int,
			val subtree: LookupTree<A_Definition, A_Tuple>?,
			val tag: TypeTag?,
			var restriction: TypeRestriction? = tag?.run {
				restrictionForType(supremum, BOXED_FLAG)
			})

		/**
		 * Given a [List] of [Span]s that are contiguous and *don't disagree*
		 * about their subtrees (although some may be `null`), compute a
		 * replacement [Span] that includes the entire range and has the common
		 * ancestor of any tags that were present.
		 */
		private fun mergeSpans(spans: List<Span>): Span
		{
			val low = spans[0].low
			var high = low - 1
			var tag: TypeTag? = null
			spans.forEach { span ->
				assert(span.low == high + 1)
				high = span.high
				span.tag?.let { tag = tag?.commonAncestorWith(it) ?: it }
			}
			val subtrees = spans.mapNotNull { it.subtree }.toSet()
			assert(subtrees.size <= 1)
			return Span(low, high, subtrees.firstOrNull(), tag)
		}

		override fun generateEdgesFor(
			semanticArguments: List<L2SemanticValue>,
			callSiteHelper: CallSiteHelper
		): List<Pair<L2BasicBlock, LookupTree<A_Definition, A_Tuple>>>
		{
			// Convert the tags' ordinal ranges into a flat list of runs.
			// Use a stack to keep track of which ordinal ranges are still
			// outstanding, to know when to resume or finish them.
			val semanticSource = semanticArguments[argumentPositionToTest - 1]
			val generator = callSiteHelper.generator()
			val currentRestriction =
				generator.currentManifest.restrictionFor(semanticSource)
			val couldBeBottom = currentRestriction.intersectsType(bottomMeta)
			val restrictionTag = currentRestriction.type.instanceTag
			val strongTagToSubtree:
					Map<TypeTag, LookupTree<A_Definition, A_Tuple>> =
				tagToSubtree.cast()
			// Keep the entries that are both valid solutions (1 method def) and
			// reachable (tags could actually occur).
			val reducedMap = strongTagToSubtree
				.filterKeys {
					restrictionTag.isSubtagOf(it)
						|| it.isSubtagOf(restrictionTag)
				}
				.mapValuesTo(mutableMapOf()) { (tag, subtree) ->
					when
					{
						!containsAnyValidLookup(subtree) -> null
						restrictionTag.isSubtagOf(tag) -> subtree
						tag.isSubtagOf(restrictionTag) &&
								currentRestriction.intersectsType(tag.supremum)
							-> subtree
						else -> null
					}
				}
			if (!couldBeBottom)
			{
				// This condition shouldn't be possible at runtime, so force an
				// actual bottom type coming in to be looked up the slow way.
				reducedMap[TypeTag.BOTTOM_TYPE_TAG] = null
			}
			val runs = mutableListOf(Span(0, TypeTag.count - 1, null, null))
			reducedMap.entries.sortedBy { it.key }.forEach { (tag, subtree) ->
				val index = runs.binarySearch { (low, high, _, _) ->
					when
					{
						tag.highOrdinal < low -> 1
						tag.ordinal > high -> -1
						else -> 0
					}
				}
				assert(0 <= index && index < runs.size)
				// Subtract the new tag's supremum from all existing spans, then
				// insert the new tag's spans at the appropriate place.
				runs.forEach { span ->
					span.restriction?.let {
						span.restriction = it.minusType(tag.supremum)
					}
				}
				// Replace the existing element with a left part, the new value,
				// and a right part, omitting any empty ranges.
				val (low, high, existing, oldTag, oldRestriction) = runs[index]
				runs.removeAt(index)
				runs.addAll(
					index,
					listOf(
						Span(
							low,
							tag.ordinal - 1,
							existing,
							oldTag,
							oldRestriction),
						Span(
							tag.ordinal,
							tag.ordinal,
							(if (tag.isAbstract) null else subtree),
							(if (tag.isAbstract) null else tag)),
						Span(
							tag.ordinal + 1,
							tag.highOrdinal,
							subtree,
							tag),
						Span(
							tag.highOrdinal + 1,
							high,
							existing,
							oldTag,
							oldRestriction)
					).filter { (low, high) -> low <= high })
			}
			val ordinalRestriction = restrictionForType(
				inclusive(
					fromInt(
						restrictionTag.ordinal +
							(if (restrictionTag.isAbstract) 1 else 0)),
					fromInt(
						if (couldBeBottom) TypeTag.BOTTOM_TYPE_TAG.ordinal
						else restrictionTag.highOrdinal)),
				UNBOXED_INT_FLAG)
			// We have to smear it both directions, in case there were multiple
			// entries that homogenized with entries that later homogenized with
			// something different, breaking the equivalence.  Forward then
			// backward over the indices should be sufficient to handle all such
			// cases.
			val ordinalLow = ordinalRestriction.type.lowerBound.extractInt
			val ordinalHigh = ordinalRestriction.type.upperBound.extractInt
			val reachableSpans = runs.filter { (low, high) ->
				high >= ordinalLow && low <= ordinalHigh
			}
			if (reachableSpans.all { it.subtree == null })
			{
				// Just jump to the slow lookup, and don't continue down any
				// more lookup subtrees.
				generator.jumpTo(callSiteHelper.onFallBackToSlowLookup)
				return emptyList()
			}
			// Expand the ranges through the don't-cares that were removed, so
			// that the entire tag range is covered.  Initially pad to the left,
			// then do a separate step at the end to pad the last one rightward.
			var nextOrdinal = 0
			val padded = reachableSpans.map { (_, high, subtree, tag) ->
				Span(nextOrdinal, high, subtree, tag).also {
					nextOrdinal = high + 1
				}
			}.toMutableList()
			// Extend the last one.
			padded.add(padded.removeLast().copy(high = TypeTag.count - 1))
			// Merge consecutive spans that have the same outcome.
			val reducedSpans = padded
				.partitionRunsBy(Span::subtree)
				.map(::mergeSpans)

			// We now have contiguous runs that cover the tag space, with no
			// spurious checks.
			if (reducedSpans.size == 1)
			{
				// Only one path is reachable.
				reducedSpans[0].run {
					// Check if the value is already as strong as the
					// restriction in the span.
					if (currentRestriction.isStrongerThan(restriction!!))
					{
						// No need to strengthen the type.
						val target = L2BasicBlock("Sole target")
						generator.jumpTo(target)
						return listOf(target to subtree!!)
					}
					// We need to strengthen the type to correspond with the
					// fact that it now has this tag.
					val strengthenerBlock = L2BasicBlock(
						"Strengthen for " +
							"[$low(${tagFromOrdinal(low)}).." +
							"$high(${tagFromOrdinal(high)})]")
					val soleTarget = L2BasicBlock(
						"Guaranteed lookup for " +
							"[$low(${tagFromOrdinal(low)}).." +
							"$high(${tagFromOrdinal(high)})]")
					generator.jumpTo(strengthenerBlock)
					generator.startBlock(strengthenerBlock)
					generator.addInstruction(
						L2_STRENGTHEN_TYPE,
						generator.readBoxed(semanticSource),
						generator.boxedWrite(
							semanticSource,
							currentRestriction.intersection(restriction!!)))
					generator.jumpTo(soleTarget)
					return listOf(soleTarget to subtree!!)
				}
			}
			// Generate a multi-way branch.
			val splitsTuple =
				tupleFromIntegerList(reducedSpans.drop(1).map(Span::low))
			val edges = reducedSpans.map { (low, high, subtree) ->
				when (subtree)
				{
					null -> callSiteHelper.onFallBackToSlowLookup
					else -> L2BasicBlock("Tag in [$low..$high]")
				}
			}
			val semanticTag = L2SemanticUnboxedInt(
				L2SemanticExtractedTag(semanticSource))
			return generator.run {
				// Assume the base type is sufficient to limit the possible tag
				// ordinals.
				addInstruction(
					L2_EXTRACT_TAG_ORDINAL,
					readBoxed(semanticSource),
					intWrite(setOf(semanticTag), ordinalRestriction))
				addInstruction(
					L2_MULTIWAY_JUMP,
					currentManifest.readInt(semanticTag),
					L2ConstantOperand(splitsTuple),
					L2PcVectorOperand(edges.map { L2PcOperand(it, false) }))
				// Generate type strengthening clauses along every non-fallback
				// path.
				reducedSpans.mapIndexedNotNull {
						index, (low, high, subtree, tag, restriction) ->
					val supremum = tag?.supremum
					when
					{
						subtree == null -> null
						supremum.notNullAnd {
							currentRestriction.type.isSubtypeOf(this@notNullAnd)
						} ->
						{
							// No need to further restrict the type.
							edges[index] to subtree
						}
						else ->
						{
							// Restrict the type to the supremum that the actual
							// encountered type tag guarantees.
							startBlock(edges[index])
							addInstruction(
								L2_STRENGTHEN_TYPE,
								readBoxed(semanticSource),
								boxedWrite(
									semanticSource,
									currentRestriction.intersection(
										restriction!!)))
							val newBlock =
								L2BasicBlock("Strengthened [$low..$high]")
							jumpTo(newBlock)
							newBlock to subtree
						}
					}
				}
			}
		}
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
				val node = nodes.removeLast()
				if (node is LeafLookupTree)
				{
					if (node.solutionOrNull.notNullAnd { tupleSize == 1 })
						return true
				}
				else if (node is InternalLookupTree)
				{
					val step = node.decisionStepOrNull
					step?.run { addChildrenTo(nodes) }
				}
			}
			// We exhausted the tree.
			return false
		}
	}
}
