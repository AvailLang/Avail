/*
 * L2Generator.kt
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
package com.avail.optimizer

import com.avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import com.avail.descriptor.character.A_Character.Companion.codePoint
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.methods.A_ChunkDependable
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tuples.ByteTupleDescriptor.Companion.generateByteTupleFrom
import com.avail.descriptor.tuples.IntTupleDescriptor.Companion.generateIntTupleFrom
import com.avail.descriptor.tuples.LongTupleDescriptor.Companion.generateLongTupleFrom
import com.avail.descriptor.tuples.NybbleTupleDescriptor.Companion.generateNybbleTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.StringDescriptor.Companion.generateStringFromCodePoints
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int64
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.nybbles
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.interpreter.Primitive
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Chunk.Companion.allocate
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandDispatcher
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2CommentOperand
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2Operand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import com.avail.interpreter.levelTwo.operation.L2_BOX_FLOAT
import com.avail.interpreter.levelTwo.operation.L2_BOX_INT
import com.avail.interpreter.levelTwo.operation.L2_CREATE_TUPLE
import com.avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE
import com.avail.interpreter.levelTwo.operation.L2_JUMP
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_FLOAT
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_INT
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import com.avail.interpreter.levelTwo.operation.L2_MOVE
import com.avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_AT_UPDATE
import com.avail.interpreter.levelTwo.operation.L2_UNBOX_FLOAT
import com.avail.interpreter.levelTwo.operation.L2_UNBOX_INT
import com.avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.interpreter.levelTwo.register.L2FloatRegister
import com.avail.interpreter.levelTwo.register.L2IntRegister
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation
import com.avail.optimizer.L2Generator.OptimizationLevel
import com.avail.optimizer.values.Frame
import com.avail.optimizer.values.L2SemanticValue
import com.avail.optimizer.values.L2SemanticValue.Companion.constant
import com.avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport
import com.avail.utility.cast
import com.avail.utility.notNullAnd

/**
 * The `L2Generator` converts a Level One [function][FunctionDescriptor] into a
 * [Level&#32;Two&#32;chunk][L2Chunk].  It optimizes as it does so, folding and
 * inlining method invocations whenever possible.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property optimizationLevel
 *   The amount of [effort][OptimizationLevel] to apply to the current
 *   optimization attempt.
 * @property topFrame
 *   The topmost [Frame] for translation.
 * @property codeName
 *   The descriptive name of the code being translated.
 *
 * @constructor
 * Construct a new `L2Generator`.
 *
 * @param optimizationLevel
 *   The [OptimizationLevel] for controlling code generation.
 * @param topFrame
 *   The topmost [Frame] for code generation.
 * @param codeName
 *   The descriptive name of the code being translated.
 */
class L2Generator internal constructor(
	val optimizationLevel: OptimizationLevel,
	val topFrame: Frame,
	val codeName: String)
{
	/**
	 * An indication of the possible degrees of optimization effort.  These are
	 * arranged approximately monotonically increasing in terms of both cost to
	 * generate and expected performance improvement.
	 */
	enum class OptimizationLevel
	{
		/**
		 * Unoptimized code, interpreted via Level One machinery.  Technically
		 * the current implementation only executes Level Two code, but the
		 * default Level Two chunk relies on a Level Two instruction that simply
		 * fetches each nybblecode and interprets it.
		 */
		UNOPTIMIZED,

		/**
		 * The initial translation into Level Two instructions customized to a
		 * particular raw function.  This at least should avoid the cost of
		 * fetching nybblecodes.  It also avoids looking up monomorphic methods
		 * at execution time, and can inline or even fold calls to suitable
		 * primitives.  The inlined calls to infallible primitives are simpler
		 * than the calls to fallible ones or non-primitives or polymorphic
		 * methods.  Inlined primitive attempts avoid having to reify the
		 * calling continuation in the case that they're successful, but have to
		 * reify if the primitive fails.
		 */
		FIRST_TRANSLATION,

		/**
		 * Unimplemented.  The idea is that at this level some inlining of
		 * non-primitives will take place, emphasizing inlining of function
		 * application.  Invocations of methods that take a literal function
		 * should tend very strongly to get inlined, as the potential to turn
		 * things like continuation-based conditionals and loops into mere jumps
		 * is expected to be highly profitable.
		 */
		@Suppress("unused")
		CHASED_BLOCKS;

		companion object
		{
			/** An array of all [OptimizationLevel] enumeration values. */
			private val all = values()

			/**
			 * Answer the `OptimizationLevel` for the given ordinal value.
			 *
			 * @param targetOptimizationLevel
			 *   The ordinal value, an `int`.
			 * @return
			 *   The corresponding `OptimizationLevel`, failing if the ordinal
			 *   was out of range.
			 */
			fun optimizationLevel(
				targetOptimizationLevel: Int): OptimizationLevel =
					all[targetOptimizationLevel]
		}
	}

	/**
	 * All [contingent&#32;values][A_ChunkDependable] for which changes should
	 * cause the current [Level&#32;Two&#32;chunk][L2Chunk] to be invalidated.
	 */
	var contingentValues = emptySet

	/**
	 * The head of the loop formed when a [P_RestartContinuation] is invoked on
	 * a label created for the current frame.
	 */
	var restartLoopHeadBlock: L2BasicBlock? = null

	/**
	 * An `int` used to quickly generate unique integers which serve to
	 * visually distinguish new registers.
	 */
	private var uniqueCounter = 0

	/**
	 * Answer the next value from the unique counter.  This is only used to
	 * distinguish registers for visual debugging.
	 *
	 * @return
	 *   An [Int].
	 */
	fun nextUnique(): Int = uniqueCounter++

	/**
	 * The [Level&#32;Two&#32;chunk][L2Chunk] generated by [createChunk].  It can be
	 * retrieved via [chunk].
	 */
	private var chunk: L2Chunk? = null

	/**
	 * The [L2BasicBlock] which is the entry point for a function that has just
	 * been invoked.
	 */
	val initialBlock: L2BasicBlock = createBasicBlock("START for $codeName")

	/** The block at which to resume execution after a failed primitive. */
	val afterOptionalInitialPrimitiveBlock =
		createLoopHeadBlock("After optional primitive")

	/** The [L2BasicBlock] that code is currently being generated into. */
	private var currentBlock: L2BasicBlock? = initialBlock

	/**
	 * Use this [L2ValueManifest] to track which [L2Register] holds which
	 * [L2SemanticValue] at the current code generation point.
	 */
	val currentManifest = L2ValueManifest()

	/**
	 * Answer the current [L2ValueManifest], which tracks which [L2Register]
	 * holds which [L2SemanticValue] at the current code generation point.
	 *
	 * @return
	 *   The current [L2ValueManifest].
	 */
	fun currentManifest(): L2ValueManifest = currentManifest

	/** The control flow graph being generated. */
	val controlFlowGraph = L2ControlFlowGraph()

	/**
	 * An [L2BasicBlock] that shouldn't actually be dynamically reachable.
	 */
	var unreachableBlock: L2BasicBlock? = null

	/**
	 * Add an instruction that's not supposed to be reachable.
	 */
	fun addUnreachableCode()
	{
		addInstruction(L2_UNREACHABLE_CODE)
	}

	/**
	 * Answer an L2PcOperand that targets an [L2BasicBlock] which should never
	 * actually be dynamically reached.
	 *
	 * @return
	 * An [L2PcOperand] that should never be traversed.
	 */
	fun unreachablePcOperand(): L2PcOperand
	{
		if (unreachableBlock == null)
		{
			// Create it as a normal node, so L1 translation can produce simple
			// edges to it, then switch it to be a loop head so that placeholder
			// instructions can still connect to it with back-edges when they
			// get they generate their replacement code.
			unreachableBlock = createBasicBlock("UNREACHABLE")
		}
		return unreachableBlock!!.let {
			if (it.isLoopHead) backEdgeTo(it)
			else edgeTo(it)
		}
	}

	/**
	 * Allocate a new [L2BoxedRegister].  Answer an [L2WriteBoxedOperand] that
	 * writes to it as a new temporary [L2SemanticValue], restricting it with
	 * the given [TypeRestriction].
	 *
	 * @param restriction
	 *   The initial [TypeRestriction] for the new operand.
	 * @return
	 *   The new boxed write operand.
	 */
	fun boxedWriteTemp(restriction: TypeRestriction): L2WriteBoxedOperand =
		boxedWrite(topFrame.temp(nextUnique()), restriction)

	/**
	 * Allocate a new [L2BoxedRegister].  Answer an [L2WriteBoxedOperand] that
	 * writes to it as the given [L2SemanticValue], restricting it with the
	 * given [TypeRestriction].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new boxed write operand.
	 */
	fun boxedWrite(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction): L2WriteBoxedOperand
	{
		assert(restriction.isBoxed)
		return L2WriteBoxedOperand(
			setOf(semanticValue),
			restriction,
			L2BoxedRegister(nextUnique()))
	}

	/**
	 * Allocate a new [L2IntRegister].  Answer an [L2WriteIntOperand] that
	 * writes to it as a new temporary [L2SemanticValue], restricting it with
	 * the given [TypeRestriction].
	 *
	 * @param restriction
	 *   The initial [TypeRestriction] for the new operand.
	 * @return
	 *   The new unboxed int write operand.
	 */
	fun intWriteTemp(restriction: TypeRestriction): L2WriteIntOperand =
		intWrite(topFrame.temp(nextUnique()), restriction)

	/**
	 * Allocate a new [L2IntRegister].  Answer an [L2WriteIntOperand] that
	 * writes to it as the given [L2SemanticValue], restricting it with the
	 * given [TypeRestriction].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new unboxed int write operand.
	 */
	fun intWrite(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction): L2WriteIntOperand
	{
		assert(restriction.isUnboxedInt)
		return L2WriteIntOperand(
			setOf(semanticValue),
			restriction,
			L2IntRegister(nextUnique()))
	}

	/**
	 * Allocate a new [L2FloatRegister]. Answer an [L2WriteFloatOperand] that
	 * writes to it as a new temporary [L2SemanticValue], restricting it with
	 * the given [TypeRestriction].
	 *
	 * @param restriction
	 *   The initial [TypeRestriction] for the new operand.
	 * @return
	 *   The new unboxed float write operand.
	 */
	@Suppress("unused")
	fun floatWriteTemp(restriction: TypeRestriction): L2WriteFloatOperand =
		floatWrite(topFrame.temp(nextUnique()), restriction)

	/**
	 * Allocate a new [L2FloatRegister].  Answer an [L2WriteFloatOperand] that
	 * writes to it as the given [L2SemanticValue], restricting it with the
	 * given [TypeRestriction].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new unboxed float write operand.
	 */
	fun floatWrite(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction): L2WriteFloatOperand
	{
		assert(restriction.isUnboxedFloat)
		return L2WriteFloatOperand(
			setOf(semanticValue),
			restriction,
			L2FloatRegister(nextUnique()))
	}

	/**
	 * Generate code to move the given constant into a boxed register, if it's
	 * not already known to be in a boxed register.  Answer an
	 * [L2ReadBoxedOperand] to retrieve this value.
	 *
	 * @param value
	 *   The constant value to write to a register.
	 * @return
	 *   The [L2ReadBoxedOperand] that retrieves the value.
	 */
	fun boxedConstant(value: A_BasicObject): L2ReadBoxedOperand
	{
		val semanticConstant = constant(value)
		if (currentManifest.hasSemanticValue(semanticConstant))
		{
			val restriction =
				currentManifest.restrictionFor(semanticConstant)
			if (restriction.isBoxed && restriction.isImmutable)
			{
				return readBoxed(semanticConstant)
			}
			// Even though the exact value is known up to equality, the Java
			// structure that implements it might not be immutable.  If not,
			// fall through and let the L2_MOVE_CONSTANT ensure it.
		}
		val restriction =
			restrictionForConstant(value, RestrictionFlagEncoding.BOXED)
		addInstruction(
			L2_MOVE_CONSTANT.boxed,
			L2ConstantOperand(value),
			boxedWrite(semanticConstant, restriction))
		return readBoxed(semanticConstant)
	}

	/**
	 * Generate code to move the given `int` constant into an unboxed int
	 * register, if it's not already known to be in such a register.  Answer an
	 * [L2ReadIntOperand] to retrieve this value.
	 *
	 * @param value
	 *   The constant `int` to write to an int register.
	 * @return
	 *   The [L2ReadIntOperand] that retrieves the value.
	 */
	fun unboxedIntConstant(value: Int): L2ReadIntOperand
	{
		val boxedValue: A_Number = fromInt(value)
		val semanticConstant = constant(boxedValue)
		var restriction: TypeRestriction
		if (currentManifest.hasSemanticValue(semanticConstant))
		{
			restriction = currentManifest.restrictionFor(semanticConstant)
			if (restriction.isUnboxedInt)
			{
				return currentManifest.readInt(semanticConstant)
			}
			restriction =
				restriction.withFlag(RestrictionFlagEncoding.UNBOXED_INT)
			currentManifest.setRestriction(semanticConstant, restriction)
		}
		else
		{
			val synonym = L2Synonym(setOf(semanticConstant))
			restriction = restrictionForConstant(
				boxedValue, RestrictionFlagEncoding.UNBOXED_INT)
			currentManifest.introduceSynonym(synonym, restriction)
		}
		addInstruction(
			L2_MOVE_CONSTANT.unboxedInt,
			L2IntImmediateOperand(value),
			intWrite(semanticConstant, restriction))
		return L2ReadIntOperand(
			semanticConstant, restriction, currentManifest)
	}

	/**
	 * Generate code to move the given `double` constant into an unboxed float
	 * register, if it's not already known to be in such a register. Answer an
	 * [L2ReadFloatOperand] to retrieve this value.
	 *
	 * @param value
	 *   The constant `double` to write to a float register.
	 * @return
	 *   The [L2ReadFloatOperand] that retrieves the value.
	 */
	private fun unboxedFloatConstant(value: Double): L2ReadFloatOperand
	{
		val boxedValue = fromDouble(value)
		val semanticConstant = constant(boxedValue)
		var restriction: TypeRestriction?
		if (currentManifest.hasSemanticValue(semanticConstant))
		{
			restriction = currentManifest.restrictionFor(semanticConstant)
			if (restriction.isUnboxedFloat)
			{
				return currentManifest.readFloat(semanticConstant)
			}
			restriction =
				restriction.withFlag(RestrictionFlagEncoding.UNBOXED_FLOAT)
			currentManifest.setRestriction(semanticConstant, restriction)
		}
		else
		{
			val synonym = L2Synonym(setOf(semanticConstant))
			restriction =
				restrictionForConstant(
					boxedValue, RestrictionFlagEncoding.UNBOXED_FLOAT)
			currentManifest.introduceSynonym(synonym, restriction)
		}
		addInstruction(
			L2_MOVE_CONSTANT.unboxedFloat,
			L2FloatImmediateOperand(value),
			floatWrite(semanticConstant, restriction))
		return L2ReadFloatOperand(semanticConstant, restriction, currentManifest)
	}

	/**
	 * Given an [L2WriteBoxedOperand], produce an [L2ReadBoxedOperand] of the
	 * same value, but with the current manifest's [TypeRestriction] applied.
	 *
	 * @param write
	 *   The [L2WriteBoxedOperand] for which to generate a read.
	 * @return
	 *   The [L2ReadBoxedOperand] that reads the value.
	 */
	fun readBoxed(write: L2WriteBoxedOperand): L2ReadBoxedOperand =
		currentManifest.readBoxed(write.pickSemanticValue())

	/**
	 * Answer an [L2ReadBoxedOperand] for the given [L2SemanticValue],
	 * generating code to transform it as necessary.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read.
	 * @return
	 *   A suitable [L2ReadBoxedOperand] that captures the current
	 *   [TypeRestriction] for the semantic value.
	 */
	fun readBoxed(semanticValue: L2SemanticValue): L2ReadBoxedOperand
	{
		val restriction = currentManifest.restrictionFor(semanticValue)
		if (restriction.isBoxed)
		{
			return currentManifest.readBoxed(semanticValue)
		}
		val boxedRestriction =
			restriction.withFlag(RestrictionFlagEncoding.BOXED)
		currentManifest.setRestriction(semanticValue, boxedRestriction)
		val writer = L2WriteBoxedOperand(
			currentManifest.semanticValueToSynonym(semanticValue)
				.semanticValues(),
			boxedRestriction,
			L2BoxedRegister(nextUnique()))
		if (restriction.isUnboxedInt)
		{
			addInstruction(
				L2_BOX_INT,
				currentManifest.readInt(semanticValue),
				writer)
		}
		else
		{
			assert(restriction.isUnboxedFloat)
			addInstruction(
				L2_BOX_FLOAT,
				currentManifest.readFloat(semanticValue),
				writer)
		}
		return currentManifest.readBoxed(semanticValue)
	}
//	{
//		val restriction = currentManifest.restrictionFor(semanticValue)
//		if (restriction.isBoxed)
//		{
//			return currentManifest.readBoxed(semanticValue)
//		}
//		when
//		{
//			restriction.isUnboxedInt ->
//			{
//				val reader = currentManifest.readInt(semanticValue)
//				val boxedRestriction =
//					restriction.withFlag(RestrictionFlagEncoding.BOXED)
//				currentManifest.setRestriction(semanticValue, boxedRestriction)
//				val writer = L2WriteBoxedOperand(
//					currentManifest.semanticValueToSynonym(semanticValue)
//						.semanticValues(),
//					boxedRestriction,
//					L2BoxedRegister(nextUnique()))
//				addInstruction(L2_BOX_INT, reader, writer)
//			}
//			restriction.isUnboxedFloat ->
//			{
//				val reader = currentManifest.readFloat(semanticValue)
//				val boxedRestriction =
//					restriction.withFlag(RestrictionFlagEncoding.BOXED)
//				currentManifest.setRestriction(semanticValue, boxedRestriction)
//				val writer = L2WriteBoxedOperand(
//					currentManifest.semanticValueToSynonym(semanticValue)
//						.semanticValues(),
//					boxedRestriction,
//					L2BoxedRegister(nextUnique()))
//				addInstruction(L2_BOX_FLOAT, reader, writer)
//			}
//			else -> error("Unknown unboxed type")
//		}
//		return currentManifest.readBoxed(semanticValue)
//	}

	/**
	 * Return an [L2ReadIntOperand] for the given [L2SemanticValue]. The
	 * [TypeRestriction] must have been proven by the VM.  If the semantic value
	 * only has a boxed form, generate code to unbox it.
	 *
	 * In the case that unboxing may fail, a branch to the supplied onFailure
	 * [L2BasicBlock] will be generated. If the unboxing cannot fail (or if a
	 * corresponding [L2IntRegister] already exists), no branch will lead to
	 * onFailure, which can be determined by the client by testing
	 * [L2BasicBlock.currentlyReachable].
	 *
	 * In any case, the generation position after this call is along the
	 * success path.  This may itself be unreachable in the event that the
	 * unboxing will *always* fail.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read as an unboxed int.
	 * @param onFailure
	 *   Where to jump in the event that an [L2_JUMP_IF_UNBOX_INT] fails. The
	 *   manifest at this location will not contain bindings for the unboxed
	 *   `int` (since unboxing was not possible).
	 * @return
	 *   The unboxed [L2ReadIntOperand].
	 */
	fun readInt(
		semanticValue: L2SemanticValue,
		onFailure: L2BasicBlock): L2ReadIntOperand
	{
		val restriction = currentManifest.restrictionFor(semanticValue)
		if (restriction.isUnboxedInt)
		{
			// It already exists in an unboxed int register.
			assert(restriction.type.isSubtypeOf(int32))
			return currentManifest.readInt(semanticValue)
		}
		// It's not available as an unboxed int, so generate code to unbox it.
		if (!restriction.isBoxed ||
			!restriction.intersectsType(int32))
		{
			// It's not an unboxed int, and it's either not boxed or it has a
			// type that can never be an int32, so it must always fail.
			jumpTo(onFailure)
			// Return a dummy, which should get suppressed or optimized away.
			return unboxedIntConstant(-999)
		}
		// Check for constant.  It can be infallibly converted.
		if (restriction.containedByType(int32)
			&& restriction.constantOrNull !== null)
		{
			// Make it available as a constant in an int register.
			return unboxedIntConstant(restriction.constantOrNull.extractInt())
		}
		// Write it to a new int register.
		val intWrite = L2WriteIntOperand(
			currentManifest.semanticValueToSynonym(semanticValue)
				.semanticValues(),
			restriction
				.intersectionWithType(int32)
				.withFlag(RestrictionFlagEncoding.UNBOXED_INT),
			L2IntRegister(nextUnique()))
		val boxedRead =
			currentManifest.readBoxed(semanticValue)
		if (restriction.containedByType(int32))
		{
			addInstruction(L2_UNBOX_INT, boxedRead, intWrite)
		}
		else
		{
			// Conversion may succeed or fail at runtime.
			val onSuccess =
				createBasicBlock("successfully unboxed")
			addInstruction(
				L2_JUMP_IF_UNBOX_INT,
				boxedRead,
				intWrite,
				edgeTo(onFailure),
				edgeTo(onSuccess))
			startBlock(onSuccess)
		}
		// This is the success path.  The operations have already ensured the
		// intWrite is in the same synonym as the boxedRead.

		// This checks that the synonyms were merged nicely.
		return currentManifest.readInt(semanticValue)
	}

	/**
	 * Return an [L2ReadFloatOperand] for the given [L2SemanticValue]. The
	 * [TypeRestriction] must have been proven by the VM.  If the semantic value
	 * only has a boxed form, generate code to unbox it.
	 *
	 * In the case that unboxing may fail, a branch to the supplied onFailure
	 * [L2BasicBlock] will be generated. If the unboxing cannot fail (or if a
	 * corresponding [L2FloatRegister] already exists), no branch will lead to
	 * onFailure, which can be determined by the client by testing
	 * [L2BasicBlock.currentlyReachable].
	 *
	 * In any case, the generation position after this call is along the
	 * success path.  This may itself be unreachable in the event that the
	 * unboxing will *always* fail.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read as an unboxed float.
	 * @param onFailure
	 *   Where to jump in the event that an [L2_JUMP_IF_UNBOX_FLOAT] fails. The
	 *   manifest at this location will not contain bindings for the unboxed
	 *   `float` (since unboxing was not possible).
	 * @return
	 *   The unboxed [L2ReadFloatOperand].
	 */
	@Suppress("unused")
	fun readFloat(
		semanticValue: L2SemanticValue,
		onFailure: L2BasicBlock): L2ReadFloatOperand
	{
		val restriction =
			currentManifest.restrictionFor(semanticValue)
		if (restriction.isUnboxedFloat)
		{
			// It already exists in an unboxed float register.
			assert(restriction.type.isSubtypeOf(Types.DOUBLE.o))
			return currentManifest.readFloat(semanticValue)
		}
		// It's not available as an unboxed float, so generate code to unbox it.
		if (!restriction.isBoxed ||
			!restriction.intersectsType(Types.DOUBLE.o))
		{
			// It's not an unboxed float, and it's either not boxed or it has a
			// type that can never be a float, so it must always fail.
			jumpTo(onFailure)
			// Return a dummy, which should get suppressed or optimized away.
			return unboxedFloatConstant(-99.9)
		}
		// Check for constant.  It can be infallibly converted.
		if (restriction.containedByType(Types.DOUBLE.o)
			&& restriction.constantOrNull !== null)
		{
			// Make it available as a constant in a float register.
			return unboxedFloatConstant(
				restriction.constantOrNull.extractDouble())
		}
		// Write it to a new float register.
		val floatWrite = L2WriteFloatOperand(
			currentManifest.semanticValueToSynonym(semanticValue)
				.semanticValues(),
			restriction
				.intersectionWithType(Types.DOUBLE.o)
				.withFlag(RestrictionFlagEncoding.UNBOXED_FLOAT),
			L2FloatRegister(nextUnique()))
		val boxedRead =
			currentManifest.readBoxed(semanticValue)
		if (restriction.containedByType(Types.DOUBLE.o))
		{
			addInstruction(L2_UNBOX_FLOAT, boxedRead, floatWrite)
		}
		else
		{
			// Conversion may succeed or fail at runtime.
			val onSuccess =
				createBasicBlock("successfully unboxed")
			addInstruction(
				L2_JUMP_IF_UNBOX_FLOAT,
				boxedRead,
				floatWrite,
				edgeTo(onSuccess),
				edgeTo(onFailure))
			startBlock(onSuccess)
		}
		// This is the success path.  The operations have already ensured the
		// floatWrite is in the same synonym as the boxedRead.

		// This checks that the synonyms were merged nicely.
		return currentManifest.readFloat(semanticValue)
	}

	/**
	 * Generate instructions to arrange for the value in the given
	 * [L2ReadOperand] to end up in an [L2Register] associated in the
	 * [L2ValueManifest] with the new [L2SemanticValue].  After the move, the
	 * synonyms for the source and destination are effectively merged, which is
	 * justified by virtue of SSA (static-single-assignment) being in effect.
	 *
	 * @param <R>
	 *   The kind of [L2Register] to move.
	 * @param <RR>
	 *   The kind of [L2ReadOperand] for reading.
	 * @param <WR>
	 *   The kind of [L2WriteOperand] for writing.
	 * @param moveOperation
	 *   The [L2_MOVE] operation to generate.
	 * @param sourceSemanticValue
	 *   Which [L2SemanticValue] to read.
	 * @param targetSemanticValue
	 *   Which [L2SemanticValue] will have the same value as the source semantic
	 *   value.
	 */
	fun <R : L2Register, RR : L2ReadOperand<R>, WR : L2WriteOperand<R>>
	moveRegister(
		moveOperation: L2_MOVE<R, RR, WR>,
		sourceSemanticValue: L2SemanticValue,
		targetSemanticValue: L2SemanticValue)
	{
		assert(!currentManifest.hasSemanticValue(targetSemanticValue))
		val block = currentBlock()
		val sourceRegisters =
			currentManifest.getDefinitions<L2Register>(
				sourceSemanticValue, moveOperation.kind)
		val sourceWritesInBlock = sourceRegisters
			.flatMap { it.definitions() }
			.filter { it.instruction().basicBlock() == block }
			.map { it.cast() }
		if (sourceWritesInBlock.isNotEmpty())
		{
			// Find the latest equivalent write in this block.
			val latestWrite = sourceWritesInBlock.maxBy {
				it.instruction().basicBlock().instructions()
					.indexOf(it.instruction())
			}!!
			// Walk backward through instructions until the latest equivalent
			// write, watching for disqualifying pitfalls.
			for (i in block.instructions().indices.reversed())
			{
				val eachInstruction = block.instructions()[i]
				if (eachInstruction == latestWrite.instruction())
				{
					// We reached the writing instruction without trouble.
					// Augment the write's semantic values retroactively to
					// include the targetSemanticValue.
					val pickedSemanticValue = latestWrite.pickSemanticValue()
					// This line must be after we pick a representative semantic
					// value, otherwise it might choose the new one.
					latestWrite.retroactivelyIncludeSemanticValue(
						targetSemanticValue)
					currentManifest.extendSynonym(
						currentManifest.semanticValueToSynonym(
							pickedSemanticValue),
						targetSemanticValue)
					return
				}
				// Here's where we would check eachInstruction to see if it's a
				// pitfall that prevents us from retroactively updating an
				// earlier write.  Break if this happens.
			}
			// Fall through, due to a break from a pitfall.
		}
		// Note that even though we couldn't avoid the move in this case, this
		// move can still be updated by subsequent moves from the same synonym.
		val restriction = currentManifest.restrictionFor(sourceSemanticValue)
		val register: R = currentManifest.getDefinition(
			sourceSemanticValue, moveOperation.kind)
		val operand = moveOperation.kind.readOperand(
			sourceSemanticValue, restriction, register)
		addInstruction(
			moveOperation,
			operand,
			moveOperation.createWrite(this, targetSemanticValue, restriction))
	}

	/**
	 * Generate code to ensure an immutable version of the given register is
	 * written to the returned register.  Update the [currentManifest]
	 * to indicate that after this point, the returned register should be used
	 * for reading the boxed form of the given register's semantic values.
	 *
	 * @param read
	 *   The [L2ReadBoxedOperand] that was given.
	 * @return
	 *   The resulting [L2ReadBoxedOperand], holding an immutable version of the
	 *   given register.
	 */
	fun makeImmutable(read: L2ReadBoxedOperand): L2ReadBoxedOperand
	{
		val restriction = read.restriction()
		assert(restriction.isBoxed)
		if (restriction.isImmutable)
		{
			// The source read is definitely already immutable.
			return read
		}
		// Pick a semantic value from the read's synonym.  Pass the original
		// boxed value through an L2_MAKE_IMMUTABLE into that semantic value,
		// then augment the write to include all other semantic values from the
		// same synonym.  Int and float unboxed registers are unaffected.
		val temp = topFrame.temp(nextUnique())
		val immutableRestriction =
			restriction.withFlag(RestrictionFlagEncoding.IMMUTABLE)
		assert(immutableRestriction.isBoxed)
		addInstruction(
			L2_MAKE_IMMUTABLE,
			read,
			boxedWrite(temp, immutableRestriction))
		return currentManifest.readBoxed(temp)
	}

	/**
	 * Cause a tuple to be constructed from the given [L2ReadBoxedOperand]s.
	 *
	 * @param elements
	 *   The [L2ReadBoxedOperand] that supply the elements of the tuple.
	 * @return
	 *   An [L2ReadBoxedOperand] that will contain the tuple.
	 */
	fun createTuple(elements: List<L2ReadBoxedOperand>): L2ReadBoxedOperand
	{
		val size = elements.size
		if (size == 0)
		{
			return boxedConstant(emptyTuple())
		}

		// Special cases for characters and integers
		val unionType = elements.fold(bottom) { t, read ->
			t.typeUnion(read.type())
		}
		val template = when
		{
			unionType.isSubtypeOf(Types.CHARACTER.o) ->
			{
				// The string contains only characters.
				// Create a (shared) Avail string statically, and use that as
				// the basis for the string that will be built, only editing the
				// necessary parts.
				generateStringFromCodePoints(size) { oneBasedIndex ->
					elements[oneBasedIndex - 1].constantOrNull().let {
						if (it === null) '?'.toInt() else it.codePoint()
					}
				}
			}
			unionType.isSubtypeOf(int64) ->
			{
				// It'll be a numeric tuple that we're able to optimize. Build a
				// template of suitable representation to copy, with constants
				// included.
				val constantsWithZeros = elements.map {
					it.constantOrNull() ?: zero()
				}
				when
				{
					unionType.isSubtypeOf(nybbles) ->
						generateNybbleTupleFrom(size) { oneIndex ->
							constantsWithZeros[oneIndex - 1].extractInt()
						}
					unionType.isSubtypeOf(bytes) ->
						generateByteTupleFrom(size) { oneIndex ->
							constantsWithZeros[oneIndex - 1].extractInt()
						}
					unionType.isSubtypeOf(int32) ->
						generateIntTupleFrom(size) { oneIndex ->
							constantsWithZeros[oneIndex - 1].extractInt()
						}
					else ->
						generateLongTupleFrom(size) { oneIndex ->
							constantsWithZeros[oneIndex - 1].extractLong()
						}
				}
			}
			elements.all { it.constantOrNull() === null } ->
			{
				// We expect the tuple to use [ObjectTupleDescriptor], but there
				// are no constant values in it.  Build it all at once at
				// runtime.
				val write = boxedWriteTemp(
					restrictionForType(
						tupleTypeForTypesList(elements.map { it.type() }),
						RestrictionFlagEncoding.BOXED))
				addInstruction(
					L2_CREATE_TUPLE,
					L2ReadBoxedVectorOperand(elements),
					write)
				return readBoxed(write)
			}
			else ->
			{
				// We expect the tuple to use [ObjectTupleDescriptor], and there
				// is at least one constant value.  Build a template tuple with
				// 'false' in the unknown fields as an eye-catcher.
				generateObjectTupleFrom(size) { oneIndex ->
					elements[oneIndex - 1].constantOrNull() ?: falseObject
				}
			}
		}.makeShared()

		var latestRead = boxedConstant(template)
		val typesList = template.map(::instanceTypeOrMetaOn).toMutableList()
		// Generate the updates for the non-constant parts (if any).
		elements.forEachIndexed { zeroIndex, read ->
			if (read.constantOrNull() === null)
			{
				typesList[zeroIndex] = read.type()
				val newWrite = boxedWriteTemp(
					restrictionForType(
						tupleTypeForTypesList(typesList),
						RestrictionFlagEncoding.BOXED))
				addInstruction(
					L2_TUPLE_AT_UPDATE,
					latestRead,
					L2IntImmediateOperand(zeroIndex + 1),
					read,
					newWrite)
				latestRead = readBoxed(newWrite)
			}
		}
		return latestRead
	}

	/**
	 * Given a register that will hold a tuple and a fixed index that is known
	 * to be in range, generate code and answer a [L2ReadBoxedOperand] that
	 * accesses that element.
	 *
	 * Depending on the source of the tuple, this may cause the creation of
	 * the tuple to be entirely elided.
	 *
	 * This must only be used while the [controlFlowGraph] is still in SSA form.
	 *
	 * @param tupleReg
	 *   The [L2BoxedRegister] containing the tuple.
	 * @param index
	 *   The one-based subscript into the tuple.
	 * @return
	 *   A [L2ReadBoxedOperand]s that provides that element of the tuple,
	 *   whether by tracing the source of the instruction that created the tuple
	 *   or by extracting the value from the tuple.
	 */
	fun extractTupleElement(
		tupleReg: L2ReadBoxedOperand,
		index: Int): L2ReadBoxedOperand
	{
		return tupleReg.definition().instruction().operation()
			.extractTupleElement(tupleReg, index, this)
	}

	/**
	 * Given a register that will hold a tuple, check that the tuple has the
	 * number of elements and statically satisfies the corresponding provided
	 * type constraints.  If so, generate code and answer a list of register
	 * reads corresponding to the elements of the tuple; otherwise, generate no
	 * code and answer null.
	 *
	 * Depending on the source of the tuple, this may cause the creation of
	 * the tuple to be entirely elided.
	 *
	 * @param tupleReg
	 *   The [L2BoxedRegister] containing the tuple.
	 * @param requiredTypes
	 *   The required [types][A_Type] against which to check the tuple's own
	 *   type.
	 * @return
	 *   A [List] of [L2ReadBoxedOperand]s corresponding to the tuple's
	 *   elements, or `null` if the tuple could not be proven to have the
	 *   required shape and type.
	 */
	fun explodeTupleIfPossible(
		tupleReg: L2ReadBoxedOperand,
		requiredTypes: List<A_Type>): List<L2ReadBoxedOperand>?
	{
		// First see if there's enough type information available about the
		// tuple.
		val tupleType = tupleReg.type()
		val tupleTypeSizes = tupleType.sizeRange()
		if (!tupleTypeSizes.upperBound().isInt
			|| !tupleTypeSizes.lowerBound().equals(tupleTypeSizes.upperBound()))
		{
			// The exact tuple size is not known.  Give up.
			return null
		}
		val tupleSize = tupleTypeSizes.upperBound().extractInt()
		if (tupleSize != requiredTypes.size)
		{
			// The tuple is the wrong size.
			return null
		}

		// Check the tuple element types against the required types.
		for (i in 1 .. tupleSize)
		{
			if (!tupleType.typeAtIndex(i).isSubtypeOf(requiredTypes[i - 1]))
			{
				// This tuple element's type isn't strong enough.
				return null
			}
		}

		// At this point we know the tuple has the right type.  Extract each
		// element, using registers originally provided to the tuple's creation
		// if possible.
		return (1 .. tupleSize).map { extractTupleElement(tupleReg, it) }
	}

	/**
	 * If we can determine where the function in this register came from, and
	 * unambiguously determine the function's exact
	 * [signature][FunctionTypeDescriptor], answer it.
	 *
	 * @param functionReg
	 *   The register that contains the function to investigate.
	 * @return
	 *   Either the exact signature that this function will always have (a
	 *   function type), or `null`.
	 */
	private fun exactFunctionSignatureFor(
		functionReg: L2ReadBoxedOperand
	): A_Type? = functionReg.exactFunctionType()

	/**
	 * Given a register containing a function and a parameter index, emit code
	 * to extract the parameter type at runtime from the actual function.
	 *
	 * @param functionRead
	 *   The register that will hold the function at runtime.
	 * @param parameterIndex
	 *   Which function parameter should have its type extracted.
	 * @return
	 *   The register containing the parameter type.
	 */
	fun extractParameterTypeFromFunction(
		functionRead: L2ReadBoxedOperand,
		parameterIndex: Int
	): L2ReadBoxedOperand
	{
		// First, see if the function type is exactly known.
		val exactFunctionType = exactFunctionSignatureFor(functionRead)
		if (exactFunctionType !== null)
		{
			return boxedConstant(
				exactFunctionType
					.argsTupleType()
					.typeAtIndex(parameterIndex))
		}
		// Extract it at runtime instead.
		val parameterTypeWrite = boxedWriteTemp(
			restrictionForType(
				anyMeta(),
				RestrictionFlagEncoding.BOXED))
		addInstruction(
			L2_FUNCTION_PARAMETER_TYPE,
			functionRead,
			L2IntImmediateOperand(parameterIndex),
			parameterTypeWrite)
		return readBoxed(parameterTypeWrite)
	}

	/**
	 * Answer a semantic value representing the result of invoking a foldable
	 * primitive.
	 *
	 * @param primitive
	 *   The [Primitive] that was executed.
	 * @param argumentReads
	 *   [L2SemanticValue]s that supplied the arguments to the primitive.
	 * @return
	 *   The semantic value representing the primitive result.
	 */
	fun primitiveInvocation(
		primitive: Primitive,
		argumentReads: List<L2ReadBoxedOperand>
	): L2SemanticValue =
		primitiveInvocation(
			primitive,
			argumentReads.map { it.semanticValue() })

	/**
	 * Create a new [L2BasicBlock].  It's initially not connected to anything,
	 * and is ignored if it is never actually added with [startBlock].
	 *
	 * @param name
	 *   The descriptive name of the new basic block.
	 * @return
	 *   The new [L2BasicBlock].
	 */
	fun createBasicBlock(name: String): L2BasicBlock = L2BasicBlock(name)

	/**
	 * Create an [L2BasicBlock], and mark it as a loop head.
	 *
	 * @param name
	 *   The name of the new loop head block.
	 * @return
	 *   The loop head block.
	 */
	fun createLoopHeadBlock(name: String): L2BasicBlock =
		L2BasicBlock(name, true, null)

	/**
	 * Create an [L2BasicBlock], and mark it as used for reification.
	 *
	 * @param name
	 *   The name of the new block.
	 * @param zone
	 *   The [L2ControlFlowGraph.Zone] (or `null`) into which to group this
	 *   block in the [L2ControlFlowGraphVisualizer].
	 * @return
	 *   The new block.
	 */
	fun createBasicBlock(name: String, zone: L2ControlFlowGraph.Zone?)
		: L2BasicBlock = L2BasicBlock(name, false, zone)

	/**
	 * Start code generation for the given [L2BasicBlock].  Unless this is a
	 * loop head, ensure all predecessor blocks have already finished
	 * generation.
	 *
	 * Also, reconcile the live [L2SemanticValue]s and how they're grouped into
	 * [L2Synonym]s in each predecessor edge, creating
	 * [L2_PHI_PSEUDO_OPERATION]s as needed.
	 *
	 * @param block
	 *   The [L2BasicBlock] beginning code generation.
	 */
	fun startBlock(block: L2BasicBlock)
	{
		if (!block.isIrremovable)
		{
			val predecessorCount = block.predecessorEdgesCount()
			if (predecessorCount == 0)
			{
				currentBlock = null
				return
			}
			if (!block.isLoopHead && predecessorCount == 1)
			{
				val predecessorEdge = block.predecessorEdges()[0]
				val predecessorBlock = predecessorEdge.sourceBlock()
				val jump = predecessorBlock.finalInstruction()
				if (jump.operation() === L2_JUMP)
				{
					// The new block has only one predecessor, which
					// unconditionally jumps to it.  Remove the jump and
					// continue generation in the predecessor block.  Restore
					// the manifest from the jump edge.
					currentManifest.clear()
					currentManifest.populateFromIntersection(
						listOf(predecessorEdge.manifest()),
						this,
						false,
						false)
					predecessorBlock.instructions().removeAt(
						predecessorBlock.instructions().size - 1)
					jump.justRemoved()
					currentBlock = predecessorBlock
					return
				}
			}
		}
		currentBlock = block
		controlFlowGraph.startBlock(block)
		block.startIn(this)
	}

	/**
	 * Answer the current [L2BasicBlock] being generated.
	 *
	 * @return
	 *   The current [L2BasicBlock].
	 */
	fun currentBlock(): L2BasicBlock = currentBlock!!

	/**
	 * Determine whether the current block is probably reachable.  If it has no
	 * predecessors and is removable, it's unreachable, but otherwise we assume
	 * it's reachable, at least until dead code elimination.
	 *
	 * @return
	 *   Whether the current block is probably reachable.
	 */
	fun currentlyReachable(): Boolean =
		currentBlock.notNullAnd { currentlyReachable() }

	/**
	 * Create and add an [L2Instruction] with the given [L2Operation] and
	 * variable number of [L2Operand]s.
	 *
	 * @param operation
	 *   The operation to invoke.
	 * @param operands
	 *   The operands of the instruction.
	 */
	fun addInstruction(operation: L2Operation, vararg operands: L2Operand) =
		currentBlock?.run {
			addInstruction(
				L2Instruction(currentBlock, operation, *operands),
				currentManifest)
		}

	/**
	 * Add an [L2Instruction].
	 *
	 * @param instruction
	 *   The instruction to add.
	 */
	fun addInstruction(instruction: L2Instruction) =
		currentBlock?.run {
			addInstruction(instruction, currentManifest)
		}

	/**
	 * Create and add an [L2Instruction] with the given [L2Operation] and
	 * variable number of [L2Operand]s.  However, this may happen after dead
	 * code has been eliminated, including moves into semantic values that
	 * propagated into synonyms and were subsequently looked up by reads.  If
	 * necessary, fall back on using the register itself to identify a suitable
	 * semantic value.
	 *
	 * @param operation
	 *   The operation to invoke.
	 * @param operands
	 *   The operands of the instruction.
	 */
	private fun reinsertInstruction(
		operation: L2Operation,
		vararg operands: L2Operand)
	{
		if (currentBlock === null)
		{
			return
		}
		val replacementOperands: Array<L2Operand> = operands
			.map { it.adjustedForReinsertion(currentManifest) }.toTypedArray()
		currentBlock!!.addInstruction(
			L2Instruction(currentBlock!!, operation, *replacementOperands),
			currentManifest)
	}

	/**
	 * Replace the already-generated instruction with a code sequence produced
	 * by setting up conditions, asking the instruction to
	 * [L2Operation.generateReplacement] itself, then cleaning up afterward.
	 *
	 * @param instruction
	 *   The [L2Instruction] to replace.  Any registers that it writes must be
	 *   replaced by suitable writes in the generated replacement.
	 */
	fun replaceInstructionByGenerating(instruction: L2Instruction)
	{
		assert(!instruction.altersControlFlow)
		val startingBlock = instruction.basicBlock()
		val originalInstructions = startingBlock.instructions()
		val instructionIndex = originalInstructions.indexOf(instruction)

		// Stash the instructions before the doomed one, as well as the ones
		// after the doomed one.
		val startInstructions =
			originalInstructions.subList(0, instructionIndex).toList()
		val endInstructions =
			originalInstructions.subList(
				instructionIndex + 1, originalInstructions.size).toList()

		// Remove all instructions from the block.  Each will get sent a
		// justRemoved() just *after* a replacement has been generated.  This
		// ensures there is always a definition of every register, and allows
		// phis in the target blocks of the final instruction's edges to stay as
		// consistent phis, rather than collapsing to inappropriate moves.
		originalInstructions.clear()
		startingBlock.removedControlFlowInstruction()

		// Regenerate the start instructions.
		currentBlock = startingBlock
		// Reconstruct the manifest at the start of the block.
		currentManifest.clear()
		// Keep semantic values that are common to all incoming paths.
		val manifests = startingBlock.predecessorEdges().map { it.manifest() }
		currentManifest.populateFromIntersection(manifests, this, false, true)
		// Replay the effects on the manifest of the leading instructions.
		startInstructions.forEach {
			reinsertInstruction(it.operation(), *it.operands())
			it.justRemoved()
		}

		// Let the instruction regenerate its replacement code.  It must write
		// to all of the write operand *registers* of the original instruction.
		// Writing to the same semantic value isn't good enough.
		instruction.operation().generateReplacement(instruction, this)
		instruction.justRemoved()
		if (!currentlyReachable())
		{
			// This regenerated code should no longer reach the old flow.
			// Clean up the rest of the original instructions.
			endInstructions.forEach(L2Instruction::justRemoved)
			return
		}

		// Make sure every L2WriteOperand in the replaced instruction has been
		// written to by the replacement code.
		instruction.writeOperands().forEach {
			assert(!it.register().definitions().isEmpty()) }

		// Finally, add duplicates of the instructions that came after the
		// doomed one in the original block.  Since the regenerated code writes
		// to all the same L2Registers, no special provisions are needed for
		// translating registers or fiddling with the manifest.
		endInstructions.forEach {
			reinsertInstruction(it.operation(), *it.operands())
			it.justRemoved()
		}
	}

	/**
	 * Emit an instruction to jump to the specified [L2BasicBlock].
	 *
	 * @param targetBlock
	 *   The target [L2BasicBlock].
	 */
	fun jumpTo(targetBlock: L2BasicBlock)
	{
		addInstruction(L2_JUMP, edgeTo(targetBlock))
	}

	/**
	 * Record the fact that the chunk being created depends on the given
	 * [A_ChunkDependable].  If that `A_ChunkDependable` changes, the chunk will
	 * be invalidated.
	 *
	 * @param contingentValue
	 *   The [AvailObject] that the chunk will be contingent on.
	 */
	fun addContingentValue(contingentValue: A_ChunkDependable)
	{
		contingentValues =
			contingentValues.setWithElementCanDestroy(
				contingentValue, true)
	}

	/**
	 * Generate a [Level&#32;Two&#32;chunk][L2Chunk] from the control flow graph.  Store
	 * it in the `L2Generator`, from which it can be retrieved via [chunk].
	 *
	 * @param code
	 *   The [A_RawFunction] which is the source of chunk creation.
	 */
	fun createChunk(code: A_RawFunction)
	{
		assert(chunk === null)
		val instructions = mutableListOf<L2Instruction>()
		controlFlowGraph.generateOn(instructions)
		val registerCounter = RegisterCounter()
		for (instruction in instructions)
		{
			instruction.operandsDo { it.dispatchOperand(registerCounter) }
		}
		val afterPrimitiveOffset =
			afterOptionalInitialPrimitiveBlock.offset()
		assert(afterPrimitiveOffset >= 0)
		chunk = allocate(
			code,
			registerCounter.objectMax + 1,
			registerCounter.intMax + 1,
			registerCounter.floatMax + 1,
			afterPrimitiveOffset,
			instructions,
			controlFlowGraph,
			contingentValues)
	}

	/**
	 * Return the [L2Chunk] previously created via [createChunk].
	 *
	 * @return
	 *   The chunk.
	 */
	fun chunk(): L2Chunk = chunk!!

	/**
	 * A class for finding the highest numbered register of each time.
	 */
	class RegisterCounter : L2OperandDispatcher
	{
		/** The highest numbered boxed register encountered so far. */
		var objectMax = -1

		/** The highest numbered int register encountered so far. */
		var intMax = -1

		/** The highest numbered float register encountered so far. */
		var floatMax = -1

		override fun doOperand(operand: L2CommentOperand) = Unit

		override fun doOperand(operand: L2ConstantOperand) = Unit

		override fun doOperand(operand: L2IntImmediateOperand) = Unit

		override fun doOperand(operand: L2FloatImmediateOperand) = Unit

		override fun doOperand(operand: L2PcOperand) = Unit

		override fun doOperand(operand: L2PrimitiveOperand) = Unit

		override fun doOperand(operand: L2ReadIntOperand)
		{
			intMax = intMax.coerceAtLeast(operand.finalIndex())
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			floatMax = floatMax.coerceAtLeast(operand.finalIndex())
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			objectMax = objectMax.coerceAtLeast(operand.finalIndex())
		}

		override fun doOperand(operand: L2ReadBoxedVectorOperand)
		{
			for (register in operand.elements())
			{
				objectMax = objectMax.coerceAtLeast(register.finalIndex())
			}
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			for (register in operand.elements())
			{
				intMax = intMax.coerceAtLeast(register.finalIndex())
			}
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			for (register in operand.elements())
			{
				floatMax = floatMax.coerceAtLeast(register.finalIndex())
			}
		}

		override fun doOperand(operand: L2SelectorOperand) = Unit

		override fun doOperand(operand: L2WriteIntOperand)
		{
			intMax = intMax.coerceAtLeast(operand.finalIndex())
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			floatMax = floatMax.coerceAtLeast(operand.finalIndex())
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			objectMax = objectMax.coerceAtLeast(operand.finalIndex())
		}
	}

	companion object
	{
		/**
		 * Don't inline dispatch logic if there are more than this many possible
		 * implementations at a call site.  This may seem so small that it precludes
		 * many fruitful opportunities, but code splitting should help eliminate all
		 * but a few possibilities at many call sites.
		 */
		const val maxPolymorphismToInlineDispatch = 4

		/**
		 * Use a series of instance equality checks if we're doing type testing for
		 * method dispatch code and the type is a non-meta enumeration with at most
		 * this number of instances.  Otherwise do a type test.
		 */
		const val maxExpandedEqualityChecks = 3

		/**
		 * Create an [L2PcOperand] leading to the given [L2BasicBlock].
		 *
		 * @param targetBlock
		 *   The target [L2BasicBlock].
		 * @return
		 *   The new [L2PcOperand].
		 */
		fun edgeTo(targetBlock: L2BasicBlock): L2PcOperand
		{
			// Only back-edges may reach a block that has already been generated.
			assert(targetBlock.instructions().isEmpty())
			return L2PcOperand(targetBlock, false)
		}

		/**
		 * Create an [L2PcOperand] leading to the given [L2BasicBlock], which
		 * must be [L2BasicBlock.isLoopHead].
		 *
		 * @param targetBlock
		 *   The target [L2BasicBlock].
		 * @return
		 *   The new [L2PcOperand].
		 */
		fun backEdgeTo(targetBlock: L2BasicBlock): L2PcOperand
		{
			assert(targetBlock.isLoopHead)
			return L2PcOperand(targetBlock, true)
		}

		/**
		 * Statistics about final chunk generation from the optimized
		 * [L2ControlFlowGraph].
		 */
		val finalGenerationStat = Statistic(
			"Final chunk generation", StatisticReport.L2_OPTIMIZATION_TIME)
	}
}
