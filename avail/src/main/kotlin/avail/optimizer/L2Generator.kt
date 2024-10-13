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
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractDouble
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.AbstractNumberDescriptor.Companion.numericComparator
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set.Companion.setSize
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
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u4
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadMixedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import avail.interpreter.levelTwo.operation.L2ConditionalJump
import avail.interpreter.levelTwo.operation.L2_BOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_BOX_INT
import avail.interpreter.levelTwo.operation.L2_CREATE_TUPLE
import avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_OBJECTS_EQUAL
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT.L2_MOVE_CONSTANT_BOXED
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT.L2_MOVE_CONSTANT_FLOAT
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT.L2_MOVE_CONSTANT_INT
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE.Companion.argsOf
import avail.interpreter.levelTwo.operation.L2_STRIP_MANIFEST
import avail.interpreter.levelTwo.operation.L2_TUPLE_AT_CONSTANT
import avail.interpreter.levelTwo.operation.L2_TUPLE_AT_UPDATE
import avail.interpreter.levelTwo.operation.L2_UNBOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.interpreter.primitive.functions.P_ParamTypeAt
import avail.interpreter.primitive.general.P_Equality
import avail.interpreter.primitive.tuples.P_TupleAt
import avail.optimizer.L2GeneratorInterface.SpecialBlock
import avail.optimizer.L2GeneratorInterface.SpecialBlock.AFTER_OPTIONAL_PRIMITIVE
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.L2Optimizer.GenerationMode.ByRegister
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedFloat
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedFloat.Companion.boxed
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticUnboxedInt.Companion.boxed
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import avail.utility.isNullOr
import avail.utility.mapToSet
import avail.utility.notNullAnd
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
 *
 * @constructor
 * Construct a new `L2Generator`.
 *
 * @param optimizationLevel
 *   The [OptimizationLevel] for controlling code generation.
 * @param topFrame
 *   The topmost [Frame] for code generation.
 */
class L2Generator
constructor(
	val optimizationLevel: OptimizationLevel,
	override val topFrame: Frame
): L2GeneratorInterface
{
	/**
	 * By default we automatically generate [L2_PHI] instructions.  In later
	 * optimization passes, the [L2ControlFlowGraph] is held together by
	 * [L2Register]s instead of [L2SemanticValue]s, so we override this with
	 * [ByRegister] in the [L2Regenerator] subclass.
	 */
	override val mode: GenerationMode get() = BySemanticValue

	/**
	 * The [SpecialBlock]s and corresponding [L2BasicBlock]s in this generator.
	 */
	override val specialBlocks = enumMap<SpecialBlock, L2BasicBlock>()

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

	override fun nextUnique(): Int = uniqueCounter++

	/**
	 * The [Level&#32;Two&#32;chunk][L2Chunk] generated by [createChunk].  It
	 * can be retrieved via [chunk].
	 */
	private var chunk: L2Chunk? = null

	/** The [L2BasicBlock] that code is currently being generated into. */
	private var currentBlock: L2BasicBlock? = null

	override var currentManifest = L2ValueManifest(BySemanticValue)

	override fun restrictionFor(
		semanticValue: L2SemanticValue<*>
	): TypeRestriction =
		currentManifest.restrictionFor(semanticValue)

	/** The control flow graph being generated. */
	val controlFlowGraph = L2ControlFlowGraph()

	override fun addUnreachableCode() = addInstruction(L2_UNREACHABLE_CODE())

	/**
	 * Create a new [L2SemanticValue] to use as a temporary value.
	 */
	fun newTemp() = topFrame.temp(nextUnique())

	override fun boxedWriteTemp(restriction: TypeRestriction): L2WriteBoxedOperand =
		boxedWrite(newTemp(), restriction)

	override fun boxedWrite(
		semanticValues: Set<L2SemanticValue<BOXED_KIND>>,
		restriction: TypeRestriction
	): L2WriteBoxedOperand
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
		semanticValue: L2SemanticBoxedValue,
		restriction: TypeRestriction
	): L2WriteBoxedOperand = boxedWrite(setOf(semanticValue), restriction)

	override fun intWriteTemp(restriction: TypeRestriction): L2WriteIntOperand =
		intWrite(setOf(newTemp().unboxedInt), restriction)

	override fun intWrite(
		semanticValues: Set<L2SemanticValue<INTEGER_KIND>>,
		restriction: TypeRestriction,
		forceRegister: L2Register<INTEGER_KIND>?
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
		floatWrite(setOf(newTemp().unboxedFloat), restriction)

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
		semanticValues: Set<L2SemanticValue<FLOAT_KIND>>,
		restriction: TypeRestriction,
		forceRegister: L2Register<FLOAT_KIND>? = null
	): L2WriteFloatOperand
	{
		assert(restriction.isUnboxedFloat)
		return L2WriteFloatOperand(
			semanticValues,
			restriction,
			forceRegister ?: L2FloatRegister(nextUnique()))
	}

	override fun boxedConstant(value: A_BasicObject): L2ReadBoxedOperand
	{
		// We require that the current code position is reachable, since
		// otherwise the addInstruction() below will produce nothing, and the
		// attempt to use the (not) written value will fail.
		assert(currentlyReachable())
		val semanticConstant = constant(value)
		val populated =
			currentManifest.equivalentPopulatedSemanticValue(semanticConstant)
		populated?.let { return readBoxed(it) }
		addInstruction(
			L2_MOVE_CONSTANT_BOXED(
				L2ConstantOperand(value),
				boxedWrite(
					semanticConstant,
					boxedRestrictionForConstant(value))))
		return readBoxed(semanticConstant)
	}

	override fun unboxedIntConstant(value: Int): L2ReadIntOperand
	{
		// We require that the current code position is reachable, since
		// otherwise the addInstruction() below will produce nothing, and the
		// attempt to use the (not) written value will fail.
		assert(currentlyReachable())
		val boxedValue: A_Number = fromInt(value)
		val semanticConstant = constant(boxedValue)
		val semanticUnboxedValue = semanticConstant.unboxedInt
		if (currentManifest.hasSemanticValue(semanticUnboxedValue))
		{
			return currentManifest.readInt(semanticUnboxedValue)
		}
		val unboxedSet = setOf(semanticUnboxedValue)
		val synonym = L2Synonym(unboxedSet)
		val restriction = intRestrictionForConstant(value)
		currentManifest.introduceSynonym(synonym, restriction)
		addInstruction(
			L2_MOVE_CONSTANT_INT(
				L2IntImmediateOperand(value),
				intWrite(unboxedSet, restriction)))
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
	override fun unboxedFloatConstant(value: Double): L2ReadFloatOperand
	{
		// We require that the current code position is reachable, since
		// otherwise the addInstruction() below will produce nothing, and the
		// attempt to use the (not) written value will fail.
		assert(currentlyReachable())
		val boxedValue: A_Number = fromDouble(value)
		val semanticConstant = constant(boxedValue)
		val semanticUnboxedValue = semanticConstant.unboxedFloat
		if (currentManifest.hasSemanticValue(semanticUnboxedValue))
		{
			return currentManifest.readFloat(semanticUnboxedValue)
		}
		val unboxedSet = setOf(semanticUnboxedValue)
		val synonym = L2Synonym(unboxedSet)
		val restriction = restrictionForConstant(boxedValue, UNBOXED_FLOAT_FLAG)
		currentManifest.introduceSynonym(synonym, restriction)
		addInstruction(
			L2_MOVE_CONSTANT_FLOAT(
				L2FloatImmediateOperand(value),
				floatWrite(unboxedSet, restriction)))
		return L2ReadFloatOperand(
			semanticUnboxedValue, restriction, currentManifest)
	}

	override fun readBoxed(
		write: L2WriteOperand<BOXED_KIND>
	): L2ReadBoxedOperand =
		currentManifest.readBoxed(write.pickSemanticValue())

	override fun readBoxed(
		semanticBoxed: L2SemanticValue<BOXED_KIND>
	): L2ReadBoxedOperand
	{
		val populated =
			currentManifest.equivalentPopulatedSemanticValue(semanticBoxed)
		if (populated !== null)
		{
			if (populated != semanticBoxed)
			{
				moveRegister(populated, listOf(semanticBoxed))
			}
			return currentManifest.readBoxed(semanticBoxed)
		}
		val unboxedInt = semanticBoxed.unboxedInt
		if (currentManifest.hasSemanticValue(unboxedInt))
		{
			val restriction = currentManifest.restrictionFor(unboxedInt)
			val writer = L2WriteBoxedOperand(
				currentManifest.semanticValueToSynonym(unboxedInt)
					.semanticValues()
					.mapToSet { it.boxed },
				restriction.forBoxed(),
				L2BoxedRegister(nextUnique()))
			addInstruction(
				L2_BOX_INT(
					currentManifest.readInt(unboxedInt),
					writer))
			return currentManifest.readBoxed(semanticBoxed)
		}
		val unboxedFloat = semanticBoxed.unboxedFloat
		if (currentManifest.hasSemanticValue(unboxedFloat))
		{
			val restriction = currentManifest.restrictionFor(unboxedFloat)
			val writer = L2WriteBoxedOperand(
				currentManifest.semanticValueToSynonym(unboxedFloat)
					.semanticValues()
					.mapToSet { it.boxed },
				restriction.forBoxed(),
				L2BoxedRegister(nextUnique()))
			addInstruction(
				L2_BOX_FLOAT(
					currentManifest.readFloat(unboxedFloat),
					writer))
			return currentManifest.readBoxed(semanticBoxed)
		}
		throw AssertionError(
			"Boxed value not available, even from unboxed versions")
	}

	override fun readInt(
		semanticUnboxed: L2SemanticUnboxedInt,
		onFailure: L2BasicBlock
	): L2ReadIntOperand
	{
		if (currentManifest.hasSemanticValue(semanticUnboxed))
		{
			// It already exists in an unboxed int register.
			return currentManifest.readInt(semanticUnboxed)
		}
		// Synonyms of ints are tricky, so check if there's an int version of a
		// synonym available.
		val boxedSynonym =
			currentManifest.semanticValueToSynonym(semanticUnboxed.boxed)
		for (alternateBoxed in boxedSynonym.semanticValues())
		{
			val alternateInt = alternateBoxed.unboxedInt
			if (currentManifest.hasSemanticValue(alternateInt))
			{
				// An unboxed int was found that we can use.
				val unassignedUnboxed = boxedSynonym.semanticValues()
					.map { it.unboxedInt }
					.filterNot(currentManifest::hasSemanticValue)
				if (unassignedUnboxed.isNotEmpty())
				{
					// Propagate the found int value into an int value of each
					// boxed value in the synonym.
					moveIntRegister(alternateInt, unassignedUnboxed)
				}
				return currentManifest.readInt(alternateInt)
			}
		}
		// Because of the way synonyms work, the boxed form might have
		// synonymous boxed semantic values, without the unboxed form having all
		// the same corresponding unboxed values.  Do a slower check for this
		// case.
		val semanticBoxed = semanticUnboxed.boxed
		currentManifest.semanticValueToSynonym(semanticBoxed).semanticValues()
			.forEach { equivalentBoxedSemanticValue ->
				val equivalentUnboxed = equivalentBoxedSemanticValue.unboxedInt
				if (currentManifest.hasSemanticValue(equivalentUnboxed))
				{
					moveIntRegister(equivalentUnboxed, setOf(semanticUnboxed))
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
			addInstruction(L2_UNBOX_INT(boxedRead, intWrite))
		}
		else
		{
			// Conversion may succeed or fail at runtime.
			val onSuccess = createBasicBlock("successfully unboxed")
			addInstruction(
				L2_JUMP_IF_UNBOX_INT(
					boxedRead,
					intWrite,
					edgeTo(onFailure),
					edgeTo(onSuccess)))
			startBlock(onSuccess)
		}
		return currentManifest.readInt(semanticUnboxed)
	}

	/**
	 * Produce the [L2SemanticUnboxedInt]'s value into an [L2ReadIntOperand],
	 * under the assumption that it cannot fail.  Instructions may be generated
	 * by this request.
	 *
	 * @param semanticUnboxed
	 *   The [L2SemanticUnboxedInt] to look up.
	 * @return
	 *   An [L2ReadIntOperand] that produces the looked up value.
	 */
	override fun readIntNoFail(
		semanticUnboxed: L2SemanticValue<INTEGER_KIND>
	): L2ReadIntOperand
	{
		val restriction = currentManifest.restrictionFor(semanticUnboxed)
		assert(restrictionFor(semanticUnboxed).containedByType(i32))

		if (currentManifest.hasSemanticValue(semanticUnboxed))
		{
			// It already exists in an unboxed int register.
			return currentManifest.readInt(semanticUnboxed)
		}
		// Check for constant.  It can be infallibly converted.
		restriction.constantOrNull?.let { constant ->
			// Make it available as a constant in an int register.
			return unboxedIntConstant(constant.extractInt)
		}
		// Synonyms of ints are tricky, so check if there's an int version of a
		// synonym available.
		for (otherBoxed in
			currentManifest.semanticValueToSynonym(semanticUnboxed.boxed)
				.semanticValues())
		{
			val otherUnboxed = otherBoxed.unboxedInt
			if (currentManifest.hasSemanticValue(otherUnboxed))
			{
				return currentManifest.readInt(otherUnboxed)
			}
		}
		// Because of the way synonyms work, the boxed form might have
		// synonymous boxed semantic values, without the unboxed form having all
		// the same corresponding unboxed values.  Do a slower check for this
		// case.
		val semanticBoxed = semanticUnboxed.boxed
		currentManifest.semanticValueToSynonym(semanticBoxed).semanticValues()
			.forEach { equivalentBoxedSemanticValue ->
				val equivalentUnboxed = equivalentBoxedSemanticValue.unboxedInt
				if (currentManifest.hasSemanticValue(equivalentUnboxed))
				{
					moveIntRegister(equivalentUnboxed, setOf(semanticUnboxed))
					return currentManifest.readInt(semanticUnboxed)
				}
			}

		// It's not available as an unboxed int, so generate code to unbox it.
		// Extract it to a new int register.
		val intWrite = L2WriteIntOperand(
			setOf(semanticUnboxed),
			restriction.forUnboxedInt(),
			L2IntRegister(nextUnique()))
		val boxedRead = currentManifest.readBoxed(semanticBoxed)
		addInstruction(L2_UNBOX_INT(boxedRead, intWrite))
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
		val semanticBoxed = semanticUnboxed.boxed
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
				.semanticValues(),
			restriction
				.intersectionWithType(Types.DOUBLE.o)
				.withFlag(UNBOXED_FLOAT_FLAG),
			L2FloatRegister(nextUnique()))
		val boxedRead = currentManifest.readBoxed(semanticBoxed)
		if (restriction.containedByType(Types.DOUBLE.o))
		{
			addInstruction(L2_UNBOX_FLOAT(boxedRead, floatWrite))
		}
		else
		{
			// Conversion may succeed or fail at runtime.
			val onSuccess = createBasicBlock("successfully unboxed")
			addInstruction(
				L2_JUMP_IF_UNBOX_FLOAT(
					boxedRead,
					floatWrite,
					edgeTo(onFailure),
					edgeTo(onSuccess)))
			startBlock(onSuccess)
		}
		return currentManifest.readFloat(semanticUnboxed)
	}

	override fun <K: RegisterKind<K>> readIfAvailable(
		semanticValue: L2SemanticValue<K>
	): L2ReadOperand<K>?
	{
		if (currentManifest.hasSemanticValue(semanticValue)
			&& currentManifest.getDefinitions(semanticValue).isNotEmpty())
		{
			return semanticValue.kind.createRead(semanticValue, currentManifest)
		}
		val equivalent =
			currentManifest.equivalentPopulatedSemanticValue(semanticValue)
		equivalent?.let {
			moveRegister(equivalent, listOf(semanticValue))
			return semanticValue.kind.createRead(semanticValue, currentManifest)
		}
		return null
	}

	override fun <K: RegisterKind<K>> moveRegister(
		sourceSemanticValue: L2SemanticValue<K>,
		targetSemanticValues: Iterable<L2SemanticValue<K>>)
	{
		val block = currentBlock()
		val sourceRegisters =
			currentManifest.getDefinitions(sourceSemanticValue)
		val sourceWritesInBlock = sourceRegisters
			.flatMap(L2Register<K>::definitions)
			.filter { it.instruction.basicBlock() == block }
		if (sourceWritesInBlock.isNotEmpty())
		{
			// Find the latest equivalent write in this block.
			val latestWrite = sourceWritesInBlock.maxBy {
				it.instruction.basicBlock().instructions()
					.indexOf(it.instruction)
			}
			if (latestWrite.instruction !is L2_PHI<*>)
			{
				// Walk backward through instructions until the latest
				// equivalent write, watching for disqualifying pitfalls.
				for (i in block.instructions().indices.reversed())
				{
					val eachInstruction = block.instructions()[i]
					// Don't allow an L2_STRIP_MANIFEST to intervene, because
					// the register and semantic value written in prior
					// instructions may not be visible (that's literally what
					// the instruction is there to ensure).
					if (eachInstruction is L2_STRIP_MANIFEST) break
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
						targetSemanticValues.forEach { targetSemanticValue ->
							latestWrite.retroactivelyIncludeSemanticValue(
								targetSemanticValue)
							if (currentManifest
								.hasSemanticValue(targetSemanticValue))
							{
								currentManifest.mergeExistingSemanticValues(
									pickedSemanticValue,
									targetSemanticValue)
							}
							else
							{
								currentManifest.extendSynonym(
									currentManifest.semanticValueToSynonym(
										pickedSemanticValue),
									targetSemanticValue)
							}
						}
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
		val register = currentManifest.getDefinition(sourceSemanticValue)
		val kind = sourceSemanticValue.kind
		addInstruction(
			kind.move(
				kind.readOperand(sourceSemanticValue, restriction, register),
				kind.createWrite(
					::nextUnique,
					targetSemanticValues.toSet(),
					restriction)))
	}

	override fun moveBoxedRegister(
		sourceSemanticValue: L2SemanticBoxedValue,
		targetSemanticValues: Iterable<L2SemanticBoxedValue>
	) = moveRegister(sourceSemanticValue, targetSemanticValues)

	override fun moveIntRegister(
		sourceSemanticValue: L2SemanticValue<INTEGER_KIND>,
		targetSemanticValues: Iterable<L2SemanticValue<INTEGER_KIND>>
	) = moveRegister(sourceSemanticValue, targetSemanticValues)

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
					elements[oneBasedIndex - 1].constantOrNull.let {
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
					it.constantOrNull ?: zero
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
			elements.all { it.constantOrNull === null } ->
			{
				// We expect the tuple to use [ObjectTupleDescriptor], but there
				// are no constant values in it.  Build it all at once at
				// runtime.
				val write = boxedWriteTemp(
					boxedRestrictionForType(
						tupleTypeForTypesList(elements.map { it.type() })))
				addInstruction(
					L2_CREATE_TUPLE(L2ReadBoxedVectorOperand(elements), write))
				return readBoxed(write)
			}
			else ->
			{
				// We expect the tuple to use [ObjectTupleDescriptor], and there
				// is at least one constant value.  Build a template tuple with
				// 'false' in the unknown fields as an eye-catcher.
				generateObjectTupleFrom(size) { oneIndex ->
					elements[oneIndex - 1].constantOrNull ?: falseObject
				}
			}
		}.makeShared()

		var latestRead = boxedConstant(template)
		val typesList = template.map(::instanceTypeOrMetaOn).toMutableList()
		// Generate the updates for the non-constant parts (if any).
		elements.forEachIndexed { zeroIndex, read ->
			if (read.constantOrNull === null)
			{
				typesList[zeroIndex] = read.type()
				val newWrite = boxedWriteTemp(
					boxedRestrictionForType(tupleTypeForTypesList(typesList)))
				addInstruction(
					L2_TUPLE_AT_UPDATE(
						latestRead,
						L2IntImmediateOperand(zeroIndex + 1),
						read,
						newWrite))
				latestRead = readBoxed(newWrite)
			}
		}
		return latestRead
	}

	override fun extractTupleElement(
		tupleRead: L2ReadBoxedOperand,
		index: Int,
		destinationSemanticValues: Set<L2SemanticBoxedValue>)
	{
		val tupleDefinitions = tupleRead.register().definitions()
		if (tupleDefinitions.size == 1)
		{
			// Either the graph is still SSA or at least this particular
			// register has one defining write.
			tupleDefinitions.single().instruction.extractTupleElement(
				tupleRead, index, destinationSemanticValues, this)
			return
		}
		// The graph is not in SSA, so just emit the default tuple element
		// extraction instruction.
		val elementType = tupleRead.type().typeAtIndex(index)
		val write = boxedWrite(
			destinationSemanticValues, boxedRestrictionForType(elementType))
		addInstruction(
			L2_TUPLE_AT_CONSTANT(
				tupleRead, L2IntImmediateOperand(index), write))
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
	 * @param tupleRead
	 *   The [L2ReadBoxedOperand] providing the tuple.
	 * @param requiredTypes
	 *   The required [types][A_Type] against which to check the tuple's own
	 *   type.
	 * @return
	 *   A [List] of [L2ReadBoxedOperand]s corresponding to the tuple's
	 *   elements, or `null` if the tuple could not be proven to have the
	 *   required shape and type.
	 */
	fun explodeTupleIfPossible(
		tupleRead: L2ReadBoxedOperand,
		requiredTypes: List<A_Type>
	): List<L2ReadBoxedOperand>?
	{
		// First see if there's enough type information available about the
		// tuple.
		val tupleType = tupleRead.type()
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
		return (1 .. tupleSize).map { i ->
			val writeValue = P_TupleAt.semanticInvocation(
				tupleRead.semanticValue(),
				constant(i))
			val write = boxedWrite(
				writeValue, boxedRestrictionForType(tupleType.typeAtIndex(i)))
			extractTupleElement(tupleRead, i, write.semanticValues())
			readBoxed(write)
		}
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
		// Extract it at runtime instead.  Note that an actual function's
		// argument type can't be bottom, so we specifically exclude it.
		val semanticParamaterType = L2SemanticPrimitiveInvocation(
			P_ParamTypeAt,
			listOf(
				functionRead.semanticValue(),
				constant(parameterIndex)))
		currentManifest.equivalentSemanticValue(semanticParamaterType)?.let {
			// Use the already extracted parameter type.
			moveRegister(it, listOf(semanticParamaterType))
			return readBoxed(semanticParamaterType)
		}
		val parameterTypeWrite = boxedWrite(
			semanticParamaterType,
			boxedRestrictionForType(anyMeta).minusValue(bottom))
		addInstruction(
			L2_FUNCTION_PARAMETER_TYPE(
				functionRead,
				L2IntImmediateOperand(parameterIndex),
				parameterTypeWrite))
		return readBoxed(parameterTypeWrite)
	}

	/**
	 * Create an [L2BasicBlock], and mark it as a loop head.
	 *
	 * @param name
	 *   The name of the new loop head block.
	 * @return
	 *   The loop head block.
	 */
	fun createLoopHeadBlock(name: String): L2BasicBlock =
		L2BasicBlock(name, null, isLoopHead = true)

	override fun createBasicBlock(
		name: String,
		zone: L2ControlFlowGraph.Zone?,
		isCold: Boolean
	): L2BasicBlock = L2BasicBlock(
		name = name,
		zone = zone,
		isCold = isCold)

	/**
	 * Start code generation for the given [L2BasicBlock].  Unless this is a
	 * loop head, ensure all predecessor blocks have already finished
	 * generation.
	 *
	 * If [mode] is [L2SemanticValue] (the default), reconcile the live
	 * [L2SemanticValue]s and how they're grouped into [L2Synonym]s in each
	 * predecessor edge, creating [L2_PHI]s as needed.
	 *
	 * @param block
	 *   The [L2BasicBlock] beginning code generation.
	 * @param regenerator
	 *   The optional [L2Regenerator] to use.
	 */
	fun startBlock(
		block: L2BasicBlock,
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
				if (jump is L2_JUMP
					&& regenerator.isNullOr { canCollapseUnconditionalJumps })
				{
					// The new block has only one predecessor, which
					// unconditionally jumps to it.  Remove the jump and
					// continue generation in the predecessor block.  Restore
					// the manifest from the jump edge.
					currentManifest.clear()
					currentManifest.populateFromIntersection(
						listOf(predecessorEdge.manifest()),
						regenerator ?: this,
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
		block.startIn(regenerator ?: this)
	}

	override fun currentBlock(): L2BasicBlock = currentBlock!!

	override fun currentlyReachable(): Boolean =
		currentBlock.notNullAnd(L2BasicBlock::currentlyReachable)

	override fun addInstruction(instruction: L2Instruction)
	{
		currentBlock?.let { block ->
			block.addInstruction(instruction.cloneFor(block), currentManifest)
		}
	}

	override fun addInstruction(instruction: L2ConditionalJump)
	{
		val block = currentBlock
		if (block == null) return
		block.addInstruction(instruction.cloneFor(block), currentManifest)
		// Now that the instruction has been added, and at the same time has had
		// a chance to narrow values in the new edges' manifests, check for
		// impossible edges due to impossible restrictions.
		val edges = block.successorEdges()
		assert(edges.size > 1) { "Should not emit one-way branches." }
		val possibleEdges = edges.filterNot {
			it.manifest().hasImpossibleRestriction
		}
		if (possibleEdges.size != 1) return
		val possibleEdge = possibleEdges.single()
		// Replace the branch with an unconditional jump.  Seed the jump with
		// the possibleEdge's manifest, in case there was a useful restriction
		// applied to it in this happy path.
		val finalInstruction = block.instructions().removeLast()
		finalInstruction.justRemoved()
		block.addInstruction(
			L2_JUMP(possibleEdge).cloneFor(block),
			possibleEdge.manifest())
	}

	override fun jumpTo(
		targetBlock: L2BasicBlock,
		optionalName: String?)
	{
		addInstruction(L2_JUMP(edgeTo(targetBlock, optionalName)))
	}

	override fun compareAndBranchInt(
		comparator: NumericComparator,
		int1Reg: L2ReadIntOperand,
		int2Reg: L2ReadIntOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand
	): Unit =
		comparator.compareAndBranchInt(this, int1Reg, int2Reg, ifTrue, ifFalse)

	override fun compareAndBranchBoxed(
		comparator: NumericComparator,
		number1Reg: L2ReadBoxedOperand,
		number2Reg: L2ReadBoxedOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand
	): Unit = comparator.compareAndBranchBoxed(
		this, number1Reg, number2Reg, ifTrue, ifFalse)

	override fun jumpIfEqualsConstant(
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
			val boolSource = registerToTest.definitionSkippingMoves()
			when
			{
				boolSource is L2_RUN_INFALLIBLE_PRIMITIVE &&
					boolSource.primitive.constant === P_Equality ->
				{
					val (read1, read2) = argsOf(boolSource)
					// If either operand of P_Equality is a constant, recurse to
					// allow deeper replacement.
					var previousConstant = read1.constantOrNull
					var previousRegister = read2
					if (previousConstant === null)
					{
						previousConstant = read2.constantOrNull
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
						L2_JUMP_IF_OBJECTS_EQUAL(
							read1,
							read2,
							edgeTo(if (constantBool) passBlock else failBlock),
							edgeTo(if (constantBool) failBlock else passBlock)))
					return
				}
				boolSource is L2_JUMP_IF_SUBTYPE ->
				{
					// Instance-of testing is done by extracting the type and
					// testing if it's a subtype.  See if the operand to the
					// is-subtype test is a get-type instruction.
					val firstTypeOperand = boolSource.firstType
					val secondTypeOperand = boolSource.seccondType
					val firstTypeSource =
						firstTypeOperand.definitionSkippingMoves()
					if (firstTypeSource is L2_GET_TYPE)
					{
						// There's a get-type followed by an is-subtype followed
						// by a compare-and-branch of the result against a
						// constant boolean.  Replace with a branch-if-kind.
						addInstruction(
							L2_JUMP_IF_KIND_OF_OBJECT(
								firstTypeSource.value,
								secondTypeOperand,
								edgeTo(
									if (constantBool) passBlock
									else failBlock),
								edgeTo(
									if (constantBool) failBlock
									else passBlock)))
						return
					}
					// Perform a branch-if-is-subtype-of instead of checking
					// whether the Avail boolean is true or false.
					addInstruction(
						L2_JUMP_IF_SUBTYPE(
							firstTypeOperand,
							secondTypeOperand,
							edgeTo(if (constantBool) passBlock else failBlock),
							edgeTo(if (constantBool) failBlock else passBlock)))
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
			NumericComparator.Equal.compareAndBranchInt(
				this,
				readInt(
					registerToTest.semanticValue().unboxedInt,
					trulyUnreachable),
				unboxedIntConstant(constantValueStrong.extractInt),
				edgeTo(innerPass),
				edgeTo(failBlock))
			assert(trulyUnreachable.predecessorEdges().isEmpty())
		}
		else
		{
			addInstruction(
				L2_JUMP_IF_EQUALS_CONSTANT(
					registerToTest,
					L2ConstantOperand(constantValue),
					edgeTo(innerPass),
					edgeTo(failBlock)))
		}
		startBlock(innerPass)
		val semanticConstant = constant(constantValue)
		if (!currentManifest.hasSemanticValue(semanticConstant))
		{
			moveBoxedRegister(
				registerToTest.semanticValue(), setOf(semanticConstant))
		}
		jumpTo(passBlock)
	}

	override fun jumpIfKindOfConstant(
		valueRead: L2ReadBoxedOperand,
		expectedType: A_Type,
		passedCheck: L2BasicBlock,
		failedCheck: L2BasicBlock)
	{
		// Check for special cases.
		val semanticValue = valueRead.semanticValue()
		val restriction = currentManifest.restrictionFor(semanticValue)
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
		// We can also know the value is in, say [1..4], but are being asked to
		// verify that it's in {1, 2, 3, 4}ᵀ, which has the same membership but
		// is strictly stronger.  Deal with contiguous integer ranges here.
		if (expectedType.isEnumeration && expectedType.isSubtypeOf(integers))
		{
			val values = expectedType.instances
			val low = values.minWithOrNull(numericComparator)!!
			val high = values.maxWithOrNull(numericComparator)!!
			if (high.minusCanDestroy(low, false).equalsInt(values.setSize - 1))
			{
				// There are N integer values in the set, and they range from
				// low to low + N - 1.  Therefore they cover a contiguous range.
				if (restriction.type.isIntegerRangeType &&
					restriction.containedByType(inclusive(low, high)))
				{
					// The value is already known to be in range, so we can
					// strengthen it by the enumeration.
					currentManifest.intersectType(valueRead, expectedType)
					jumpTo(passedCheck)
					return
				}
			}
		}
		// Trace back to the definition of the read's register, to see if it's a
		// function that's created in the current chunk.
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
		currentManifest.equivalentSemanticValue(semanticValue.unboxedInt)?.let {
				unboxed ->
			// We have the value in an unboxed int.  Use it.
			val constantIntType = expectedType.typeIntersection(i32)
			val low = constantIntType.lowerBound.extractInt
			val high = constantIntType.upperBound.extractInt
			val isContiguous = !constantIntType.isEnumeration
				|| constantIntType.instanceCount.equalsInt(high - low + 1)
			if (isContiguous)
			{
				val firstSuccess = L2BasicBlock("low bound ok")
				compareAndBranchInt(
					NumericComparator.GreaterOrEqual,
					currentManifest.readInt(unboxed),
					unboxedIntConstant(low),
					edgeTo(firstSuccess),
					edgeTo(failedCheck))
				startBlock(firstSuccess)
				compareAndBranchInt(
					NumericComparator.LessOrEqual,
					currentManifest.readInt(unboxed),
					unboxedIntConstant(high),
					edgeTo(passedCheck),
					edgeTo(failedCheck))
				return
			}
			// Rather than do spot-checks here, just fall through.
		}
		// We can't pin it down statically, so do the dynamic check.
		addInstruction(
			L2_JUMP_IF_KIND_OF_OBJECT(
				valueRead,
				boxedConstant(expectedType),
				edgeTo(passedCheck),
				edgeTo(failedCheck)))
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
		functionToCallReg.constantOrNull?.let { function ->
			return function.code()
		}
		// See if we can at least find out the raw function that the function
		// was created from.
		return functionToCallReg.definitionSkippingMoves().constantCode
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

	override fun visualize() = controlFlowGraph.visualize(this)

	override fun simplyVisualize() = controlFlowGraph.simplyVisualize(this)

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

		override fun doOperand(operand: L2ArbitraryConstantOperand<*>) = Unit

		override fun doOperand(operand: L2CommentOperand) = Unit

		override fun doOperand(operand: L2ConstantOperand) = Unit

		override fun doOperand(operand: L2IntImmediateOperand) = Unit

		override fun doOperand(operand: L2FloatImmediateOperand) = Unit

		override fun doOperand(operand: L2PcOperand) = Unit

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
			for (read in operand.elements)
			{
				objectMax = objectMax.coerceAtLeast(read.finalIndex())
			}
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			for (read in operand.elements)
			{
				intMax = intMax.coerceAtLeast(read.finalIndex())
			}
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			for (read in operand.elements)
			{
				floatMax = floatMax.coerceAtLeast(read.finalIndex())
			}
		}

		override fun doOperand(operand: L2ReadMixedVectorOperand)
		{
			for (read in operand.elements)
			{
				read.dispatchOperand(this)
			}
		}

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

		override fun doOperand(operand: L2WriteBoxedVectorOperand)
		{
			for (register in operand.elements)
			{
				objectMax = objectMax.coerceAtLeast(register.finalIndex())
			}
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
		 * @param optionalName
		 *   An optional name for this edge.  If omitted or null, the name that
		 *   will be presented for this edge will depend on the
		 *   [L2Instruction]'s list of [L2NamedOperandType]s, generated from
		 *   that subclass's var field declarations that use a subtype of
		 *   [L2Operand].
		 * @return
		 *   The new [L2PcOperand].
		 */
		fun edgeTo(
			targetBlock: L2BasicBlock,
			optionalName: String? = null
		): L2PcOperand
		{
			// Only back-edges may reach a block that has already been
			// generated.
			assert(targetBlock.instructions().isEmpty())
			return L2PcOperand(targetBlock, false, optionalName = optionalName)
		}

		/**
		 * Create an [L2PcOperand] leading to the given [L2BasicBlock], which
		 * must be [L2BasicBlock.isLoopHead].
		 *
		 * @param targetBlock
		 *   The target [L2BasicBlock].
		 * @param forcedClamped
		 *   A [Set] of [L2Entity] that limit the information that can propagate
		 *   arcoss the new back-edge.
		 * @return
		 *   The new [L2PcOperand].
		 */
		fun backEdgeTo(
			targetBlock: L2BasicBlock,
			forcedClamped: Set<L2Entity<*>>
		): L2PcOperand
		{
			assert(targetBlock.isLoopHead)
			val backEdge = L2PcOperand(targetBlock, true)
			backEdge.forcedClampedEntities = forcedClamped
			return backEdge
		}

		/**
		 * Statistics about final chunk generation from the optimized
		 * [L2ControlFlowGraph].
		 */
		val finalGenerationStat = Statistic(
			L2_OPTIMIZATION_TIME, "Final chunk generation")
	}
}
