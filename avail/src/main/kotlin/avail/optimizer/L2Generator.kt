/*
 * L2Generator.kt
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
package avail.optimizer

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.character.A_Character.Companion.codePoint
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.setStartingChunkAndReoptimizationCountdown
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_ChunkDependable
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractDouble
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tuples.ByteTupleDescriptor.Companion.generateByteTupleFrom
import avail.descriptor.tuples.IntTupleDescriptor.Companion.generateIntTupleFrom
import avail.descriptor.tuples.LongTupleDescriptor.Companion.generateLongTupleFrom
import avail.descriptor.tuples.NybbleTupleDescriptor.Companion.generateNybbleTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.StringDescriptor.Companion.generateStringFromCodePoints
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u4
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2SelectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_BOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_BOX_INT
import avail.interpreter.levelTwo.operation.L2_CREATE_TUPLE
import avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.interpreter.levelTwo.operation.L2_GET_TYPE.sourceValueOf
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_COMPARE_INT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_CONSTANT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_OBJECTS_EQUAL
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_CONSTANT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_OBJECT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE.Companion.argsOf
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE.Companion.primitiveOf
import avail.interpreter.levelTwo.operation.L2_TUPLE_AT_UPDATE
import avail.interpreter.levelTwo.operation.L2_UNBOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.interpreter.primitive.general.P_Equality
import avail.optimizer.L2Generator.SpecialBlock.AFTER_OPTIONAL_PRIMITIVE
import avail.optimizer.L2Generator.SpecialBlock.UNREACHABLE
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticConstant
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import avail.utility.cast
import avail.utility.isNullOr
import avail.utility.mapToSet
import avail.utility.notNullAnd
import avail.utility.removeLast
import avail.utility.structures.EnumMap
import avail.utility.structures.EnumMap.Companion.enumMap

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
	 * An enumeration of symbolic names of key blocks of the [controlFlowGraph].
	 * These are associated with optional [L2BasicBlock]s within the generator's
	 * [specialBlocks].
	 */
	enum class SpecialBlock
	{
		/**
		 * The initial block of the control flow graph, which is where the
		 * control flow implicitly starts when the [A_Function] underlying the
		 * [L2Chunk] is ultimately invoked.
		 */
		START,

		/** The block at which to resume execution after a failed primitive. */
		AFTER_OPTIONAL_PRIMITIVE,

		/**
		 * The head of the loop formed when a [P_RestartContinuation] is invoked
		 * on a label created for the current frame.
		 */
		RESTART_LOOP_HEAD,

		/**
		 * An [L2BasicBlock] that shouldn't actually be dynamically reachable.
		 */
		UNREACHABLE
	}

	/**
	 * An [EnumMap] from symbolic [SpecialBlock] to optional [L2BasicBlock].
	 */
	val specialBlocks = enumMap<SpecialBlock, L2BasicBlock>()

	/**
	 * All [contingent&#32;values][A_ChunkDependable] for which changes should
	 * cause the current [Level&#32;Two&#32;chunk][L2Chunk] to be invalidated.
	 */
	var contingentValues = emptySet

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
	 * The [Level&#32;Two&#32;chunk][L2Chunk] generated by [createChunk].  It
	 * can be retrieved via [chunk].
	 */
	private var chunk: L2Chunk? = null

	/** The [L2BasicBlock] that code is currently being generated into. */
	private var currentBlock: L2BasicBlock? = null

	/**
	 * Use this [L2ValueManifest] to track which [L2Register] holds which
	 * [L2SemanticValue] at the current code generation point.
	 */
	var currentManifest = L2ValueManifest()

	/** The control flow graph being generated. */
	val controlFlowGraph = L2ControlFlowGraph()

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
		var unreachableBlock = specialBlocks.getOrNull(UNREACHABLE)
		if (unreachableBlock === null)
		{
			// Create it as a normal node, so L1 translation can produce simple
			// edges to it, then switch it to be a loop head so that placeholder
			// instructions can still connect to it with back-edges when they
			// generate their replacement code.
			unreachableBlock = createBasicBlock("UNREACHABLE")
			specialBlocks[UNREACHABLE] = unreachableBlock
		}
		return unreachableBlock.let {
			if (it.isLoopHead) backEdgeTo(it)
			else edgeTo(it)
		}
	}

	/**
	 * Create a new [L2SemanticValue] to use as a temporary value.
	 */
	fun newTemp() = topFrame.temp(nextUnique())

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
		boxedWrite(newTemp(), restriction)

	/**
	 * Allocate a new [L2BoxedRegister].  Answer an [L2WriteBoxedOperand] that
	 * writes to it as the given [L2SemanticValue]s, restricting it with the
	 * given [TypeRestriction].
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new boxed write operand.
	 */
	fun boxedWrite(
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction): L2WriteBoxedOperand
	{
		assert(restriction.isBoxed)
		return L2WriteBoxedOperand(
			semanticValues,
			restriction,
			L2BoxedRegister(nextUnique()))
	}

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
		restriction: TypeRestriction
	): L2WriteBoxedOperand = boxedWrite(setOf(semanticValue), restriction)

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
		intWrite(setOf(L2SemanticUnboxedInt(newTemp())), restriction)

	/**
	 * Allocate a new [L2IntRegister].  Answer an [L2WriteIntOperand] that
	 * writes to it as the given [L2SemanticValue]s, restricted with the given
	 * [TypeRestriction].
	 *
	 * @param semanticValues
	 *   The [L2SemanticUnboxedInt]s to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new unboxed int write operand.
	 */
	fun intWrite(
		semanticValues: Set<L2SemanticUnboxedInt>,
		restriction: TypeRestriction,
		forceRegister: L2IntRegister? = null
	): L2WriteIntOperand
	{
		assert(restriction.isUnboxedInt)
		return L2WriteIntOperand(
			semanticValues,
			restriction,
			forceRegister ?: L2IntRegister(nextUnique()))
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
		floatWrite(setOf(L2SemanticUnboxedFloat(newTemp())), restriction)

	/**
	 * Allocate a new [L2FloatRegister].  Answer an [L2WriteFloatOperand] that
	 * writes to it as the given [L2SemanticValue], restricting it with the
	 * given [TypeRestriction].
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new unboxed float write operand.
	 */
	fun floatWrite(
		semanticValues: Set<L2SemanticUnboxedFloat>,
		restriction: TypeRestriction,
		forceRegister: L2FloatRegister? = null
	): L2WriteFloatOperand
	{
		assert(restriction.isUnboxedFloat)
		return L2WriteFloatOperand(
			semanticValues,
			restriction,
			forceRegister ?: L2FloatRegister(nextUnique()))
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
			val restriction = currentManifest.restrictionFor(semanticConstant)
			if (restriction.isBoxed && restriction.isImmutable)
			{
				return readBoxed(semanticConstant)
			}
			// Even though the exact value is known up to equality, the Java
			// structure that implements it might not be immutable.  If not,
			// fall through and let the L2_MOVE_CONSTANT ensure it.
		}
		val restriction = restrictionForConstant(value, BOXED_FLAG)
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
		val semanticUnboxedValue = L2SemanticUnboxedInt(semanticConstant)
		if (currentManifest.hasSemanticValue(semanticUnboxedValue))
		{
			return currentManifest.readInt(semanticUnboxedValue)
		}
		val unboxedSet = setOf(semanticUnboxedValue)
		val synonym = L2Synonym(unboxedSet)
		val restriction = restrictionForConstant(boxedValue, UNBOXED_INT_FLAG)
		currentManifest.introduceSynonym(synonym, restriction)
		addInstruction(
			L2_MOVE_CONSTANT.unboxedInt,
			L2IntImmediateOperand(value),
			intWrite(unboxedSet, restriction))
		return L2ReadIntOperand(
			semanticUnboxedValue, restriction, currentManifest)
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
		val boxedValue: A_Number = fromDouble(value)
		val semanticConstant = constant(boxedValue)
		val semanticUnboxedValue = L2SemanticUnboxedFloat(semanticConstant)
		if (currentManifest.hasSemanticValue(semanticUnboxedValue))
		{
			return currentManifest.readFloat(semanticUnboxedValue)
		}
		val unboxedSet = setOf(semanticUnboxedValue)
		val synonym = L2Synonym(unboxedSet)
		val restriction = restrictionForConstant(boxedValue, UNBOXED_FLOAT_FLAG)
		currentManifest.introduceSynonym(synonym, restriction)
		addInstruction(
			L2_MOVE_CONSTANT.unboxedFloat,
			L2FloatImmediateOperand(value),
			floatWrite(unboxedSet, restriction))
		return L2ReadFloatOperand(
			semanticUnboxedValue, restriction, currentManifest)
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
		assert(semanticValue !is L2SemanticUnboxedInt)
		assert(semanticValue !is L2SemanticUnboxedFloat)
		if (currentManifest.hasSemanticValue(semanticValue))
		{
			return currentManifest.readBoxed(semanticValue)
		}
		val unboxedInt = L2SemanticUnboxedInt(semanticValue)
		if (currentManifest.hasSemanticValue(unboxedInt))
		{
			val restriction = currentManifest.restrictionFor(unboxedInt)
			val writer = L2WriteBoxedOperand(
				currentManifest.semanticValueToSynonym(unboxedInt)
					.semanticValues()
					.mapToSet { (it as L2SemanticUnboxedInt).base },
				restriction.forBoxed(),
				L2BoxedRegister(nextUnique()))
			addInstruction(
				L2_BOX_INT,
				currentManifest.readInt(unboxedInt),
				writer)
			return currentManifest.readBoxed(semanticValue)
		}
		val unboxedFloat = L2SemanticUnboxedFloat(semanticValue)
		if (currentManifest.hasSemanticValue(unboxedFloat))
		{
			val restriction = currentManifest.restrictionFor(unboxedFloat)
			val writer = L2WriteBoxedOperand(
				currentManifest.semanticValueToSynonym(unboxedFloat)
					.semanticValues()
					.mapToSet { (it as L2SemanticUnboxedFloat).base },
				restriction.forBoxed(),
				L2BoxedRegister(nextUnique()))
			addInstruction(
				L2_BOX_FLOAT,
				currentManifest.readFloat(unboxedFloat),
				writer)
			return currentManifest.readBoxed(semanticValue)
		}
		error("Boxed value not available, even from unboxed versions")
	}

	/**
	 * Return an [L2ReadIntOperand] for the given [L2SemanticUnboxedInt]. The
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
	 * @param semanticUnboxed
	 *   The [L2SemanticUnboxedInt] to read as an unboxed int.
	 * @param onFailure
	 *   Where to jump in the event that an [L2_JUMP_IF_UNBOX_INT] fails. The
	 *   manifest at this location will not contain bindings for the unboxed
	 *   `int` (since unboxing was not possible).
	 * @return
	 *   The unboxed [L2ReadIntOperand].
	 */
	fun readInt(
		semanticUnboxed: L2SemanticUnboxedInt,
		onFailure: L2BasicBlock
	): L2ReadIntOperand
	{
		if (currentManifest.hasSemanticValue(semanticUnboxed))
		{
			// It already exists in an unboxed int register.
			return currentManifest.readInt(semanticUnboxed)
		}
		// Synonyms of ints are tricky, so check if there's an int version of
		// a synonym available.
		for (otherBoxed in
			currentManifest.semanticValueToSynonym(semanticUnboxed.base)
				.semanticValues())
		{
			val otherUnboxed = L2SemanticUnboxedInt(otherBoxed)
			if (currentManifest.hasSemanticValue(otherUnboxed))
			{
				return currentManifest.readInt(otherUnboxed)
			}
		}
		// Because of the way synonyms work, the boxed form might have
		// synonymous boxed semantic values, without the unboxed form having all
		// the same corresponding unboxed values.  Do a slower check for this
		// case.
		val semanticBoxed = semanticUnboxed.base
		currentManifest.semanticValueToSynonym(semanticBoxed).semanticValues()
			.forEach { equivalentBoxedSemanticValue ->
				val equivalentUnboxed =
					L2SemanticUnboxedInt(equivalentBoxedSemanticValue)
				if (currentManifest.hasSemanticValue(equivalentUnboxed))
				{
					moveRegister(
						L2_MOVE.unboxedInt,
						equivalentUnboxed,
						semanticUnboxed)
					return currentManifest.readInt(semanticUnboxed)
				}
			}

		// It's not available as an unboxed int, so generate code to unbox it.
		val restriction = currentManifest.restrictionFor(semanticBoxed)
		if (!restriction.intersectsType(i32))
		{
			// It's not an unboxed int, and the boxed form can never be an
			// int32, so it must always fail.
			jumpTo(onFailure)
			// Return a dummy, which should get suppressed or optimized away.
			return unboxedIntConstant(-999)
		}
		// Check for constant.  It can be infallibly converted.
		restriction.constantOrNull?.let { constant ->
			// Make it available as a constant in an int register.
			return unboxedIntConstant(constant.extractInt)
		}
		// Extract it to a new int register.
		val intWrite = L2WriteIntOperand(
			setOf(semanticUnboxed),
			restriction.forUnboxedInt(),
			L2IntRegister(nextUnique()))
		val boxedRead = currentManifest.readBoxed(semanticBoxed)
		if (restriction.containedByType(i32))
		{
			addInstruction(L2_UNBOX_INT, boxedRead, intWrite)
		}
		else
		{
			// Conversion may succeed or fail at runtime.
			val onSuccess = createBasicBlock("successfully unboxed")
			addInstruction(
				L2_JUMP_IF_UNBOX_INT,
				boxedRead,
				intWrite,
				edgeTo(onFailure),
				edgeTo(onSuccess))
			startBlock(onSuccess)
		}
		return currentManifest.readInt(semanticUnboxed)
	}

	/**
	 * Return an [L2ReadFloatOperand] for the given [L2SemanticUnboxedFloat].
	 * The [TypeRestriction] must have been proven by the VM.  If the semantic
	 * value only has a boxed form, generate code to unbox it.
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
	 * @param semanticUnboxed
	 *   The [L2SemanticUnboxedFloat] to read as an unboxed float.
	 * @param onFailure
	 *   Where to jump in the event that an [L2_JUMP_IF_UNBOX_FLOAT] fails. The
	 *   manifest at this location will not contain bindings for the unboxed
	 *   `float` (since unboxing was not possible).
	 * @return
	 *   The unboxed [L2ReadFloatOperand].
	 */
	@Suppress("unused")
	fun readFloat(
		semanticUnboxed: L2SemanticUnboxedFloat,
		onFailure: L2BasicBlock): L2ReadFloatOperand
	{
		if (currentManifest.hasSemanticValue(semanticUnboxed))
		{
			// It already exists in an unboxed float register.
			return currentManifest.readFloat(semanticUnboxed)
		}
		// It's not available as an unboxed float, so generate code to unbox it.
		val semanticBoxed = semanticUnboxed.base
		val restriction = currentManifest.restrictionFor(semanticBoxed)
		if (!restriction.intersectsType(Types.DOUBLE.o))
		{
			// It's not an unboxed float, and the boxed form can never be a
			// double, so it must always fail.
			jumpTo(onFailure)
			// Return a dummy, which should get suppressed or optimized away.
			return unboxedFloatConstant(-99.9)
		}
		// Check for constant.  It can be infallibly converted.
		restriction.constantOrNull?.let { constant ->
			// Make it available as a constant in a float register.
			return unboxedFloatConstant(constant.extractDouble)
		}
		// Extract it to a new float register.
		val floatWrite = L2WriteFloatOperand(
			currentManifest.semanticValueToSynonym(semanticUnboxed)
				.semanticValues().cast(),
			restriction
				.intersectionWithType(Types.DOUBLE.o)
				.withFlag(UNBOXED_FLOAT_FLAG),
			L2FloatRegister(nextUnique()))
		val boxedRead = currentManifest.readBoxed(semanticBoxed)
		if (restriction.containedByType(Types.DOUBLE.o))
		{
			addInstruction(L2_UNBOX_FLOAT, boxedRead, floatWrite)
		}
		else
		{
			// Conversion may succeed or fail at runtime.
			val onSuccess = createBasicBlock("successfully unboxed")
			addInstruction(
				L2_JUMP_IF_UNBOX_FLOAT,
				boxedRead,
				floatWrite,
				edgeTo(onFailure),
				edgeTo(onSuccess))
			startBlock(onSuccess)
		}
		return currentManifest.readFloat(semanticUnboxed)
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
	 * @param <RV>
	 *   The kind of [L2ReadVectorOperand] for creating read vectors.
	 * @param moveOperation
	 *   The [L2_MOVE] operation to generate.
	 * @param sourceSemanticValue
	 *   Which [L2SemanticValue] to read.
	 * @param targetSemanticValue
	 *   Which [L2SemanticValue] will have the same value as the source semantic
	 *   value.
	 */
	fun <
		R : L2Register,
		RR : L2ReadOperand<R>,
		WR : L2WriteOperand<R>,
		RV : L2ReadVectorOperand<R, RR>>
	moveRegister(
		moveOperation: L2_MOVE<R, RR, WR, RV>,
		sourceSemanticValue: L2SemanticValue,
		targetSemanticValue: L2SemanticValue)
	{
		assert(!currentManifest.hasSemanticValue(targetSemanticValue))
		val block = currentBlock()
		val sourceRegisters =
			currentManifest.getDefinitions<L2Register>(sourceSemanticValue)
		val sourceWritesInBlock = sourceRegisters
			.flatMap(L2Register::definitions)
			.filter { it.instruction.basicBlock() == block }
			.map(L2WriteOperand<*>::cast)
		if (sourceWritesInBlock.isNotEmpty())
		{
			// Find the latest equivalent write in this block.
			val latestWrite = sourceWritesInBlock.maxByOrNull {
				it!!.instruction.basicBlock().instructions()
					.indexOf(it.instruction)
			}!!
			if (!latestWrite.instruction.operation.isPhi)
			{
				// Walk backward through instructions until the latest
				// equivalent write, watching for disqualifying pitfalls.
				for (i in block.instructions().indices.reversed())
				{
					val eachInstruction = block.instructions()[i]
					if (eachInstruction == latestWrite.instruction)
					{
						// We reached the writing instruction without trouble.
						// Augment the write's semantic values retroactively to
						// include the targetSemanticValue.
						val pickedSemanticValue =
							latestWrite.pickSemanticValue()
						// This line must be after we pick a representative
						// semantic value, otherwise it might choose the new
						// one.
						latestWrite.retroactivelyIncludeSemanticValue(
							targetSemanticValue)
						currentManifest.extendSynonym(
							currentManifest.semanticValueToSynonym(
								pickedSemanticValue),
							targetSemanticValue)
						return
					}
					// Here's where we would check eachInstruction to see if
					// it's a pitfall that prevents us from retroactively
					// updating an earlier write.  Break if this happens.
				}
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
			moveOperation.createWrite(
				this, setOf(targetSemanticValue), restriction))
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
		val readSynonym = currentManifest.semanticValueToSynonym(
			read.semanticValue())
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
		val temp = newTemp()
		val writeSemanticValues = readSynonym.semanticValues().toMutableSet()
		writeSemanticValues.add(temp)
		val immutableRestriction = restriction.withFlag(IMMUTABLE_FLAG)
		assert(immutableRestriction.isBoxed)
		addInstruction(
			L2_MAKE_IMMUTABLE,
			read,
			boxedWrite(writeSemanticValues, immutableRestriction))
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
						if (it === null) '?'.code else it.codePoint
					}
				}
			}
			unionType.isSubtypeOf(i64) ->
			{
				// It'll be a numeric tuple that we're able to optimize. Build a
				// template of suitable representation to copy, with constants
				// included.
				val constantsWithZeros = elements.map {
					it.constantOrNull() ?: zero
				}
				when
				{
					unionType.isSubtypeOf(u4) ->
						generateNybbleTupleFrom(size) { oneIndex ->
							constantsWithZeros[oneIndex - 1].extractInt
						}
					unionType.isSubtypeOf(u8) ->
						generateByteTupleFrom(size) { oneIndex ->
							constantsWithZeros[oneIndex - 1].extractInt
						}
					unionType.isSubtypeOf(i32) ->
						generateIntTupleFrom(size) { oneIndex ->
							constantsWithZeros[oneIndex - 1].extractInt
						}
					else ->
						generateLongTupleFrom(size) { oneIndex ->
							constantsWithZeros[oneIndex - 1].extractLong
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
						BOXED_FLAG))
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
						tupleTypeForTypesList(typesList), BOXED_FLAG))
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
		return tupleReg.definition().instruction.operation
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
		val tupleTypeSizes = tupleType.sizeRange
		if (!tupleTypeSizes.upperBound.isInt
			|| !tupleTypeSizes.lowerBound.equals(tupleTypeSizes.upperBound))
		{
			// The exact tuple size is not known.  Give up.
			return null
		}
		val tupleSize = tupleTypeSizes.upperBound.extractInt
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
				exactFunctionType.argsTupleType.typeAtIndex(parameterIndex))
		}
		// Extract it at runtime instead.
		val parameterTypeWrite = boxedWriteTemp(
			restrictionForType(anyMeta(), BOXED_FLAG))
		addInstruction(
			L2_FUNCTION_PARAMETER_TYPE,
			functionRead,
			L2IntImmediateOperand(parameterIndex),
			parameterTypeWrite)
		return readBoxed(parameterTypeWrite)
	}

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
	 * If [generatePhis] is `true` (the default), reconcile the live
	 * [L2SemanticValue]s and how they're grouped into [L2Synonym]s in each
	 * predecessor edge, creating [L2_PHI_PSEUDO_OPERATION]s as needed.
	 *
	 * @param block
	 *   The [L2BasicBlock] beginning code generation.
	 * @param generatePhis
	 *   Whether to automatically generate [L2_PHI_PSEUDO_OPERATION]s if there
	 *   are multiple incoming edges with different [L2Register]s associated
	 *   with the same [L2SemanticValue]s.
	 * @param regenerator
	 *   The optional [L2Regenerator] to use.
	 */
	fun startBlock(
		block: L2BasicBlock,
		generatePhis: Boolean = true,
		regenerator: L2Regenerator? = null)
	{
		currentBlock?.instructions()?.run {
			assert(isNotEmpty())
			assert(last().altersControlFlow) {
				"Previous block was not finished: ${currentBlock!!.name()}"
			}
		}
		// Verify that all predecessor blocks have been finished.
		block.predecessorEdges().forEach { it.instruction }
		if (!block.isIrremovable)
		{
			val predecessorCount = block.predecessorEdges().size
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
				if (jump.operation === L2_JUMP
					&& regenerator.isNullOr { canCollapseUnconditionalJumps })
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
						false,
						regenerator)
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
		block.startIn(this, generatePhis, regenerator)
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
		currentBlock.notNullAnd(L2BasicBlock::currentlyReachable)

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
	 * Generate a conditional branch to either `passBlock` or `failBlock`, based
	 * on whether the given register equals the given constant value.
	 *
	 * If the constant to compare against is a boolean, check the provenance
	 * of the register.  If it's the result of a suitable comparison primitive,
	 * generate a more efficient compare-and-branch instruction instead of
	 * creating the boolean only to have it compared to a boolean constant.
	 *
	 * If the value of the boolean-producing instruction is not used, it will
	 * eventually be removed as dead code.
	 *
	 * @param registerToTest
	 *   The register whose content should be compared.
	 * @param constantValue
	 *   The constant value to compare against.
	 * @param passBlock
	 *   Where to go if the register's value equals the constant.
	 * @param failBlock
	 *   Where to go if the register's value does not equal the constant.
	 */
	fun jumpIfEqualsConstant(
		registerToTest: L2ReadBoxedOperand,
		constantValue: A_BasicObject,
		passBlock: L2BasicBlock,
		failBlock: L2BasicBlock)
	{
		val restriction = registerToTest.restriction()
		when (restriction.constantOrNull)
		{
			constantValue -> {
				// Always true.
				jumpTo(passBlock)
				return
			}
			is Any -> {
				// Always false.
				jumpTo(failBlock)
				return
			}
		}
		if (constantValue.isBoolean)
		{
			val constantBool = constantValue.equals(trueObject)
			val boolSource = registerToTest.definitionSkippingMoves(true)
			when
			{
				boolSource.operation !is L2_RUN_INFALLIBLE_PRIMITIVE ->
				{
				}
				primitiveOf(boolSource) === P_Equality ->
				{
					val (read1, read2) = argsOf(boolSource)
					// If either operand of P_Equality is a constant, recurse to
					// allow deeper replacement.
					var previousConstant = read1.constantOrNull()
					var previousRegister = read2
					if (previousConstant === null)
					{
						previousConstant = read2.constantOrNull()
						previousRegister = read1
					}
					if (previousConstant !== null)
					{
						// It's a comparison against a constant.  Recurse to
						// deal with comparing the result of a prior comparison
						// to some boolean.
						jumpIfEqualsConstant(
							previousRegister,
							previousConstant,
							if (constantBool) passBlock else failBlock,
							if (constantBool) failBlock else passBlock)
						return
					}
					// Neither value is a constant, but we can still do the
					// compare-and-branch without involving Avail booleans.
					addInstruction(
						L2_JUMP_IF_OBJECTS_EQUAL,
						read1,
						read2,
						edgeTo(if (constantBool) passBlock else failBlock),
						edgeTo(if (constantBool) failBlock else passBlock))
					return
				}
				boolSource.operation === L2_JUMP_IF_SUBTYPE_OF_CONSTANT ->
				{
					// Instance-of testing is done by extracting the type and
					// testing if it's a subtype.  See if the operand to the
					// is-subtype test is a get-type instruction.
					val firstTypeOperand =
						boolSource.operand<L2ReadBoxedOperand>(0)
					val secondConstantOperand =
						boolSource.operand<L2ConstantOperand>(1)
					val firstTypeSource =
						firstTypeOperand.definitionSkippingMoves(true)
					if (firstTypeSource.operation === L2_GET_TYPE)
					{
						// There's a get-type followed by an is-subtype
						// followed by a compare-and-branch of the result
						// against a constant boolean.  Replace with a
						// branch-if-kind.
						val valueSource = sourceValueOf(firstTypeSource)
						jumpIfKindOfConstant(
							valueSource,
							secondConstantOperand.constant,
							if (constantBool) passBlock else failBlock,
							if (constantBool) failBlock else passBlock)
						return
					}
					// Perform a branch-if-is-subtype-of instead of checking
					// whether the Avail boolean is true or false.
					addInstruction(
						L2_JUMP_IF_SUBTYPE_OF_CONSTANT,
						firstTypeOperand,
						secondConstantOperand,
						edgeTo(if (constantBool) passBlock else failBlock),
						edgeTo(if (constantBool) failBlock else passBlock))
					return
				}
				boolSource.operation === L2_JUMP_IF_SUBTYPE_OF_OBJECT ->
				{
					// Instance-of testing is done by extracting the type and
					// testing if it's a subtype.  See if the operand to the
					// is-subtype test is a get-type instruction.
					val firstTypeOperand =
						boolSource.operand<L2ReadBoxedOperand>(0)
					val secondTypeOperand =
						boolSource.operand<L2ReadBoxedOperand>(0)
					val firstTypeSource =
						firstTypeOperand.definitionSkippingMoves(true)
					if (firstTypeSource.operation === L2_GET_TYPE)
					{
						// There's a get-type followed by an is-subtype
						// followed by a compare-and-branch of the result
						// against a constant boolean.  Replace with a
						// branch-if-kind.
						val valueSource = sourceValueOf(firstTypeSource)
						addInstruction(
							L2_JUMP_IF_KIND_OF_OBJECT,
							valueSource,
							secondTypeOperand,
							edgeTo(if (constantBool) passBlock else failBlock),
							edgeTo(if (constantBool) failBlock else passBlock))
						return
					}
					// Perform a branch-if-is-subtype-of instead of checking
					// whether the Avail boolean is true or false.
					addInstruction(
						L2_JUMP_IF_SUBTYPE_OF_OBJECT,
						firstTypeOperand,
						secondTypeOperand,
						edgeTo(if (constantBool) passBlock else failBlock),
						edgeTo(if (constantBool) failBlock else passBlock))
					return
				}
				// TODO MvG - We could check for other special cases here, like
				// numeric less-than.  For now, fall through to compare the
				// value against the constant.
			}
		}
		// Generate the general case.  In the pass case, flow through an
		// intermediate block that uses a move to a temp to force the constant
		// value to be visible in a register.
		val innerPass = L2BasicBlock("strengthen to constant")
		val constantValueStrong = constantValue as AvailObject
		if (constantValueStrong.isInt
			&& registerToTest.restriction().containedByType(i32))
		{
			// The constant and the value are both int32s.  Use the quicker int
			// test, unboxing the int register if needed.
			val trulyUnreachable = L2BasicBlock("truly unreachable")
			L2_JUMP_IF_COMPARE_INT.equal.compareAndBranch(
				this,
				readInt(
					L2SemanticUnboxedInt(registerToTest.semanticValue()),
					trulyUnreachable),
				unboxedIntConstant(constantValueStrong.extractInt),
				edgeTo(innerPass),
				edgeTo(failBlock))
			assert(trulyUnreachable.predecessorEdges().isEmpty())
		}
		else
		{
			addInstruction(
				L2_JUMP_IF_EQUALS_CONSTANT,
				registerToTest,
				L2ConstantOperand(constantValue),
				edgeTo(innerPass),
				edgeTo(failBlock))
		}
		startBlock(innerPass)
		val semanticConstant = L2SemanticConstant(constantValue)
		if (!currentManifest.hasSemanticValue(semanticConstant))
		{
			moveRegister(
				L2_MOVE.boxed,
				registerToTest.semanticValue(),
				semanticConstant)
		}
		jumpTo(passBlock)
	}

	/**
	 * Generate code to test the value in `valueRead` against the constant
	 * `expectedType`, jumping to `passedCheck` if it conforms, or `failedCheck`
	 * otherwise.
	 *
	 * @param valueRead
	 *   The [L2ReadBoxedOperand] that provides the value to check.
	 * @param expectedType
	 *   The exact [A_Type] to check the value against.
	 * @param passedCheck
	 *   Where to jump if the value's type is of the expected type.
	 * @param failedCheck
	 *   Where to jump if the value's type is not of the expected type.
	 */
	fun jumpIfKindOfConstant(
		valueRead: L2ReadBoxedOperand,
		expectedType: A_Type,
		passedCheck: L2BasicBlock,
		failedCheck: L2BasicBlock)
	{
		// Check for special cases.
		val restriction =
			currentManifest.restrictionFor(valueRead.semanticValue())
		if (restriction.containedByType(expectedType))
		{
			jumpTo(passedCheck)
			return
		}
		if (!restriction.intersectsType(expectedType))
		{
			jumpTo(failedCheck)
			return
		}
		// Trace back to the definition of the read's register, to see if it's
		// a function that's created in the current chunk.
		val rawFunction = determineRawFunction(valueRead)
		if (rawFunction !== null)
		{
			val exactKind = rawFunction.functionType()
			if (exactKind.isSubtypeOf(expectedType))
			{
				jumpTo(passedCheck)
				return
			}
			if (!expectedType.isEnumeration)
			{
				// Don't check for vacuous type intersection here.  We know the
				// exact kind, and it's specifically *not* a subtype of the
				// expectedType, which is also a kind (i.e., not an
				// enumeration).
				jumpTo(failedCheck)
				return
			}
		}
		// We can't pin it down statically, so do the dynamic check.
		addInstruction(
			L2_JUMP_IF_KIND_OF_CONSTANT,
			valueRead,
			L2ConstantOperand(expectedType),
			edgeTo(passedCheck),
			edgeTo(failedCheck))
	}

	/**
	 * Given a register that holds the function to invoke, answer either the
	 * [A_RawFunction] it will be known to run, or `null`.
	 *
	 * @param functionToCallReg
	 *   The [L2ReadBoxedOperand] containing the function to invoke.
	 * @return
	 *   Either `null` or the function's [A_RawFunction].
	 */
	fun determineRawFunction(
		functionToCallReg: L2ReadBoxedOperand): A_RawFunction?
	{
		val functionIfKnown: A_Function? =
			functionToCallReg.constantOrNull()
		if (functionIfKnown !== null)
		{
			// The exact function is known.
			return functionIfKnown.code()
		}
		// See if we can at least find out the raw function that the function
		// was created from.
		val functionDefinition =
			functionToCallReg.definitionSkippingMoves(true)
		return functionDefinition.operation.getConstantCodeFrom(
			functionDefinition)
	}

	/**
	 * Temporarily switch my state to generate code just prior to the control
	 * flow altering instruction leading to this edge.  Update the edge's
	 * manifest, under the assumption that the newly generated code and the
	 * (existing) final instruction of the block do not interfere in terms of
	 * the semantic values they populate and consume.
	 */
	fun generateRetroactivelyBeforeEdge(
		edge: L2PcOperand,
		body: L2Generator.()->Unit)
	{
		val sourceBlock = edge.sourceBlock()

		val savedManifest = currentManifest
		val savedBlock = currentBlock
		val savedFinalInstruction = sourceBlock.instructions().removeLast()
		currentManifest = edge.manifest()
		currentBlock = sourceBlock
		sourceBlock.removedControlFlowInstruction()
		try
		{
			this.body()
		}
		finally
		{
			sourceBlock.instructions().add(savedFinalInstruction)
			sourceBlock.readdedControlFlowInstruction()
			currentManifest = savedManifest
			currentBlock = savedBlock
		}
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
			contingentValues.setWithElementCanDestroy(contingentValue, true)
	}

	/**
	 * Generate a [Level&#32;Two&#32;chunk][L2Chunk] from the control flow
	 * graph.  Store it in the `L2Generator`, from which it can be retrieved via
	 * [chunk].
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
		instructions.forEach { instruction ->
			instruction.operands.forEach {
				it.dispatchOperand(registerCounter)
			}
		}
		val afterPrimitiveOffset =
			specialBlocks[AFTER_OPTIONAL_PRIMITIVE]!!.offset()
		assert(afterPrimitiveOffset >= 0)
		chunk = L2JVMChunk.allocate(
			code,
			afterPrimitiveOffset,
			instructions,
			controlFlowGraph,
			contingentValues)
		code.setStartingChunkAndReoptimizationCountdown(
			chunk!!, optimizationLevel.countdown)
	}

	/**
	 * Return the [L2Chunk] previously created via [createChunk].
	 *
	 * @return
	 *   The chunk.
	 */
	fun chunk(): L2Chunk = chunk!!

	/** Pass-through to [L2ControlFlowGraph]. */
	@Suppress("Unused")
	fun visualize() = controlFlowGraph.visualize()

	/** Pass-through to [L2ControlFlowGraph]. */
	@Suppress("Unused")
	fun simplyVisualize() = controlFlowGraph.simplyVisualize()

	/**
	 * A class for finding the highest numbered register of each time.
	 */
	class RegisterCounter : L2OperandDispatcher
	{
		/** The highest numbered boxed register encountered so far. */
		private var objectMax = -1

		/** The highest numbered int register encountered so far. */
		private var intMax = -1

		/** The highest numbered float register encountered so far. */
		private var floatMax = -1

		override fun doOperand(operand: L2ArbitraryConstantOperand) = Unit

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
			for (register in operand.elements)
			{
				objectMax = objectMax.coerceAtLeast(register.finalIndex())
			}
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			for (register in operand.elements)
			{
				intMax = intMax.coerceAtLeast(register.finalIndex())
			}
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			for (register in operand.elements)
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

		override fun doOperand(operand: L2PcVectorOperand) = Unit
	}

	companion object
	{
		/**
		 * Don't inline dispatch logic if there are more than this many possible
		 * implementations at a call site.  This may seem so small that it
		 * precludes many fruitful opportunities, but code splitting should help
		 * eliminate all but a few possibilities at many call sites.
		 */
		const val maxPolymorphismToInlineDispatch = 8

		/**
		 * Use a series of instance equality checks if we're doing type testing
		 * for method dispatch code and the type is a non-meta enumeration with
		 * at most this number of instances.  Otherwise do a type test.
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
			// Only back-edges may reach a block that has already been
			// generated.
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
			L2_OPTIMIZATION_TIME, "Final chunk generation")
	}
}
