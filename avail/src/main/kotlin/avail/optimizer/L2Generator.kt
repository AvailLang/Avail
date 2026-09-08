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
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.numbers.AbstractNumberDescriptor.Companion.numericComparator
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Character.Companion.codePoint
import avail.descriptor.representation.A_Character.Companion.isCharacter
import avail.descriptor.representation.A_ChunkDependable
import avail.descriptor.representation.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_Number.Companion.extractDouble
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_Number.Companion.isInt
import avail.descriptor.representation.A_Number.Companion.minusCanDestroy
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.setStartingChunkAndReoptimizationCountdown
import avail.descriptor.representation.A_Set.Companion.setSize
import avail.descriptor.representation.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.argsTupleType
import avail.descriptor.representation.A_Type.Companion.instanceCount
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.sizeRange
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.A_Type.Companion.typeUnion
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tuples.ByteTupleDescriptor.Companion.generateByteTupleFrom
import avail.descriptor.tuples.IntTupleDescriptor.Companion.generateIntTupleFrom
import avail.descriptor.tuples.LongTupleDescriptor.Companion.generateLongTupleFrom
import avail.descriptor.tuples.NybbleTupleDescriptor.Companion.generateNybbleTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.StringDescriptor.Companion.generateStringFromCodePoints
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
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
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2_CODEPOINT_TO_CHARACTER
import avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.interpreter.levelTwo.operation.L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_JUMP_BACK
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_OBJECTS_EQUAL
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_NOP
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE.Companion.argsOf
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.interpreter.levelTwo.operation.numbers.L2_UNBOX_FLOAT
import avail.interpreter.levelTwo.operation.numbers.L2_UNBOX_INT
import avail.interpreter.levelTwo.operation.tuples.L2_CREATE_TUPLE
import avail.interpreter.levelTwo.operation.tuples.L2_TUPLE_AT_UPDATE
import avail.interpreter.levelTwo.operation.variables.L2_SET_UNESCAPED_LOCAL_VARIABLE
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.interpreter.primitive.functions.P_ParamTypeAt
import avail.interpreter.primitive.general.P_Equality
import avail.interpreter.primitive.tuples.P_TupleAt
import avail.optimizer.L2GeneratorInterface.Companion.readInt
import avail.optimizer.L2GeneratorInterface.SpecialBlock
import avail.optimizer.L2GeneratorInterface.SpecialBlock.AFTER_OPTIONAL_PRIMITIVE
import avail.optimizer.L2Optimizer.Companion.shouldSanityCheck
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2Optimizer.GenerationMode.WithFixedRegisterMap
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import avail.utility.cast
import avail.utility.isNullOr
import avail.utility.notNullAnd
import avail.utility.structures.EnumMap.Companion.enumMap

/**
 * The `L2Generator` converts a Level One [function][FunctionDescriptor] into a
 * [Level&#32;Two&#32;chunk][L2Chunk].  It optimizes as it does so, folding and
 * inlining method invocations whenever possible.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `L2Generator`.
 *
 * @param optimizationLevel
 *   The [OptimizationLevel] for controlling code generation.
 * @param topFrame
 *   The topmost [Frame] for code generation.
 * @param mode
 *   The [GenerationMode] that controls the way in which two values are
 *   considered the same for the current optimization phase.
 */
class L2Generator
constructor(
	val debugName: String,
	override val optimizationLevel: OptimizationLevel,
	override val topFrame: Frame,
	override var mode: GenerationMode
): L2GeneratorInterface
{
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
	 * The [Level&#32;Two&#32;chunk][L2Chunk] generated and installed by
	 * [createChunk].
	 */
	private var chunk: L2Chunk? = null

	/** The [L2BasicBlock] that code is currently being generated into. */
	private var currentBlock: L2BasicBlock? = null

	override var currentManifest = L2ValueManifest(BySemanticValue)

	override var isGeneratingRetroactively: Boolean = false

	override fun restrictionFor(
		semanticValue: L2SemanticValue
	): TypeRestriction =
		currentManifest.restrictionFor(semanticValue)

	/** The control flow graph being generated. */
	val controlFlowGraph = L2ControlFlowGraph()

	override fun toString(): String = "${javaClass.simpleName} ($debugName)"

	override fun addUnreachableCode(): Unit = +L2_UNREACHABLE_CODE()

	override fun newTemp(name: String?) = topFrame.temp(name, nextUnique())

	override fun boxedWriteTemp(
		name: String?,
		restriction: TypeRestriction
	): L2WriteBoxedOperand =
		boxedWrite(newTemp(name), restriction)

	override fun boxedWrite(
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction
	): L2WriteBoxedOperand
	{
		return L2WriteBoxedOperand(semanticValues, restriction)
	}

	override fun boxedWrite(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction
	): L2WriteBoxedOperand = boxedWrite(setOf(semanticValue), restriction)

	override fun intWriteTemp(
		name: String?,
		restriction: TypeRestriction
	): L2WriteIntOperand =
		intWrite(
			setOf(newTemp(name)),
			restriction)

	override fun intWrite(
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		forceRegister: L2Register<INTEGER_KIND>?
	): L2WriteIntOperand
	{
		return L2WriteIntOperand(
			semanticValues,
			restriction,
			forceRegister ?: L2IntRegister(nextUnique()))
	}

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
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		forceRegister: L2Register<FLOAT_KIND>? = null
	): L2WriteFloatOperand
	{
		return L2WriteFloatOperand(
			semanticValues,
			restriction,
			forceRegister ?: L2FloatRegister(nextUnique()))
	}

	override fun boxedConstant(value: A_BasicObject): L2ReadBoxedOperand
	{
		val constant = constant(value)
		val restriction = constant.defaultRestriction
		currentManifest.agglomerateSynonym(setOf(constant), restriction)
		return L2ReadBoxedOperand(constant, restriction)
	}

	override fun unboxedIntConstant(value: Int): L2ReadIntOperand
	{
		val constant = constant(fromInt(value))
		val restriction = constant.defaultRestriction
		currentManifest.agglomerateSynonym(setOf(constant), restriction)
		return L2ReadIntOperand(constant, restriction)
	}

	override fun unboxedFloatConstant(value: Double): L2ReadFloatOperand
	{
		val constant = constant(fromDouble(value))
		val restriction = constant.defaultRestriction
		currentManifest.agglomerateSynonym(setOf(constant), restriction)
		return L2ReadFloatOperand(constant, restriction)
	}

	override fun <K: RegisterKind<K>> ensureDefinedOrEmitMove(
		semanticValue: L2SemanticValue,
		kind: K
	): Unit
	{
		// Happy path – there's already a definition/register backing it.
		if (currentManifest.hasLiveSemanticValue(semanticValue, kind)) return
		val synonym = currentManifest.semanticValueToSynonym(semanticValue)
		var restriction = currentManifest.restrictionFor(semanticValue)
		val defined = currentManifest.getAllDefinitions(semanticValue, kind)
			.flatMap(L2Register<K>::definitions)
			.flatMap(L2WriteOperand<K>::semanticValues)
			.toSet()
		val notDefined = synonym.semanticValues() - defined
		// We already concluded that (at least) semanticValue is not yet live.
		assert(semanticValue in notDefined)
		val origin: L2SemanticValue? = when
		{
			defined.isNotEmpty() -> defined.first()
			// Look for a value already computed in something we can determine
			// is equivalent, even if it's currently in another synonym.
			else -> currentManifest
				.equivalentPopulatedSemanticValue(semanticValue, kind)
		}
		// Always clear the postponed instruction, if any, perhaps emitting it
		// below.
		val postponed =
			currentManifest.removePostponedInstructionFor(semanticValue, kind)
		if (origin != null)
		{
			restriction = restriction.intersection(restrictionFor(origin))
		}

		val newInstruction = when
		{
			// Contradictory restrictions led to an impossible situation, so
			// emit either an L2_IMPOSSIBLE_CODE, or if we're retroactively
			// generating before an edge, emit an
			// L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW.
			restriction.isImpossible -> impossibleCodeInstruction()
			// The value is populated within the synonym, or in an equivalent
			// semantic value, so move the value into the notDefined set.
			origin != null -> kind.dynamicMove(
				origin,
				notDefined.toSet(),
				currentManifest,
				restrictionFor(semanticValue))
			// It's constant, so emit a constant move.
			restriction.isConstant -> kind.moveConstant(
				restriction.constantOrNull!!,
				notDefined)
			// Otherwise there must be a postponed instruction to emit.  Its
			// operands were captured when it was postponed, which may have been
			// well above branches that have since narrowed what it reads, so
			// re-derive the restrictions from the current manifest.  Note that
			// the clone is essential: the postponed instruction itself is
			// shared with every other manifest descended from the one that
			// recorded it, including the opposite edge of any branch.
			else -> postponed!!.clone().apply {
				refreshReadRestrictionsFrom(currentManifest)
				writeOperands.single().restrict {
					impliedWriteRestriction(
						readOperands.map { it.restriction() })
				}
				writeOperands.single()
					.retroactivelySetSemanticValues(notDefined)
			}
		}
		addInstruction(newInstruction)
	}

	override fun readBoxed(
		write: L2WriteOperand<BOXED_KIND>
	): L2ReadBoxedOperand =
		readBoxed(write.pickSemanticValue()).also { read ->
			write.registerIfKnown()?.let(read::setRegister)
		}

	override fun readBoxed(
		semanticBoxed: L2SemanticValue
	): L2ReadBoxedOperand =
		currentManifest.read(semanticBoxed, BOXED_KIND).cast()

	override fun readIntInternal(
		semanticValue: L2SemanticValue,
		onFailure: L2BasicBlock
	): L2ReadIntOperand?
	{
		if (!currentlyReachable()) return null
		if (currentManifest.hasLiveSemanticValue(semanticValue, INTEGER_KIND))
		{
			// It already exists in an unboxed int register.
			return currentManifest.read(semanticValue, INTEGER_KIND).cast()
		}
		// See if we can use an equivalent int value
		val synonym = currentManifest.semanticValueToSynonym(semanticValue)
		val values = synonym.semanticValues()
		values.forEach { value ->
			val equivalentUnboxed = currentManifest
				.equivalentPopulatedSemanticValue(value, INTEGER_KIND)
			if (equivalentUnboxed != null)
			{
				move(equivalentUnboxed, synonym.semanticValues())
				return currentManifest.read(semanticValue, INTEGER_KIND).cast()
			}
		}

		// It's not available as an unboxed int, so generate code to unbox it.
		val restriction = currentManifest.restrictionFor(semanticValue)
		if (!restriction.intersectsType(i32))
		{
			// It's not an unboxed int, and the boxed form can never be an
			// int32, so it must always fail.
			jumpTo(onFailure)
			return null
		}
		// Check for constant.  It can be infallibly converted.
		restriction.constantOrNull?.let { constant ->
			// Make it available as a constant in an int register.
			return unboxedIntConstant(constant.extractInt)
		}
		// Extract it to a new int register.
		val intWrite = L2WriteIntOperand(
			values,
			restriction,
			L2IntRegister(nextUnique()))
		val readBoxed =
			currentManifest.read(semanticValue, BOXED_KIND) as L2ReadBoxedOperand
		if (!restriction.containedByType(i32))
		{
			// Conversion may succeed or fail at runtime.
			val onSuccess = createBasicBlock("isInt $semanticValue")
			jumpIfKindOfConstant(
				readBoxed,
				i32,
				onSuccess,
				onFailure)
			startBlock(onSuccess)
			if (!currentlyReachable())
			{
				// The success path might have ended up being impossible.
				return null
			}
		}
		+L2_UNBOX_INT(readBoxed, intWrite)
		return currentManifest.read(semanticValue, INTEGER_KIND).cast()
	}

	/**
	 * Produce the [L2SemanticValue]'s value into an [L2ReadIntOperand],
	 * under the assumption that it cannot fail.  Instructions may be generated
	 * by this request.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   An [L2ReadIntOperand] that produces the looked up value as an int.
	 */
	override fun readIntNoFail(
		semanticValue: L2SemanticValue
	): L2ReadIntOperand
	{
		val restriction = currentManifest.restrictionFor(semanticValue)
		assert(restriction.containedByType(i32))

		if (currentManifest.hasLiveSemanticValue(semanticValue, INTEGER_KIND))
		{
			// It already exists in an unboxed int register.
			return currentManifest.read(semanticValue, INTEGER_KIND).cast()
		}
		// Check for constant.  It can be infallibly converted.
		restriction.constantOrNull?.let { constant ->
			// Make it available as a constant in an int register.
			return unboxedIntConstant(constant.extractInt)
		}
		// It's not available as an unboxed int, so generate code to unbox it.
		// Extract it to a new int register.
		+L2_UNBOX_INT(
			currentManifest.read(semanticValue, INTEGER_KIND).cast(),
			L2WriteIntOperand(
				setOf(semanticValue),
				restriction,
				L2IntRegister(nextUnique())))
		return currentManifest.read(semanticValue, INTEGER_KIND).cast()
	}

	/**
	 * Return an [L2ReadFloatOperand] for the given [L2SemanticValue].
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
	 * @param semanticValue
	 *   The [L2SemanticValue] to be read as an unboxed float.
	 * @param onFailure
	 *   Where to jump in the event that an [isDouble] fails. The manifest at
	 *   this location will not contain bindings for the unboxed `float` (since
	 *   unboxing was not possible).
	 * @return
	 *   The unboxed [L2ReadFloatOperand].
	 */
	fun readFloat(
		semanticValue: L2SemanticValue,
		onFailure: L2BasicBlock
	): L2ReadFloatOperand
	{
		if (currentManifest.hasSemanticValue(semanticValue))
		{
			// It already exists in an unboxed float register.
			return currentManifest.read(semanticValue, FLOAT_KIND).cast()
		}
		// It's not available as an unboxed float, so generate code to unbox it.
		val restriction = currentManifest.restrictionFor(semanticValue)
		if (!restriction.intersectsType(Types.DOUBLE()))
		{
			// It's not an unboxed float, and the boxed form can never be a
			// double, so it must always fail.
			jumpTo(onFailure)
			// Return a dummy, which should get suppressed or optimized away.
			return unboxedFloatConstant(-999.999)
		}
		// Check for constant.  It can be infallibly converted.
		restriction.constantOrNull?.let { constant ->
			// Make it available as a constant in a float register.
			return unboxedFloatConstant(constant.extractDouble)
		}
		// Extract it to a new float register.
		val floatWrite = L2WriteFloatOperand(
			buildSet {
				add(semanticValue)
				currentManifest.semanticValueToSynonymOrNull(semanticValue)
					?.let { addAll(it.semanticValues()) }
			},
			restriction,
			L2FloatRegister(nextUnique()))
		val readBoxed = currentManifest.read(semanticValue, FLOAT_KIND)
		if (!restriction.containedByType(Types.DOUBLE()))
		{
			// Conversion may succeed or fail at runtime.
			val onSuccess = createBasicBlock("isDouble $semanticValue")
			jumpIfKindOfConstant(
				readBoxed.cast(),
				Types.DOUBLE(),
				onSuccess,
				onFailure)
			startBlock(onSuccess)
			if (!currentlyReachable())
			{
				// The success path might have ended up being impossible. Return
				// a dummy, which should get suppressed or optimized away.
				return unboxedFloatConstant(-999.999)
			}
		}
		+L2_UNBOX_FLOAT(readBoxed.cast(), floatWrite)
		return currentManifest.read(semanticValue, FLOAT_KIND).cast()
	}

	override fun readFloatNoFail(
		semanticValue: L2SemanticValue
	): L2ReadFloatOperand
	{
		val ifNotFloat = createBasicBlock("not a double")
		val result = readFloat(semanticValue, ifNotFloat)
		assert(ifNotFloat.predecessorEdges().isEmpty())
		return result
	}

	override fun <K: RegisterKind<K>> readIfAvailable(
		semanticValue: L2SemanticValue,
		kind: K
	): L2ReadOperand<K>?
	{
		if (currentManifest.hasSemanticValue(semanticValue)
			&& currentManifest.getDefinitions(semanticValue, kind).isNotEmpty())
		{
			return currentManifest.read(semanticValue, kind)
		}
		val equivalent =
			currentManifest.equivalentSemanticValue(semanticValue)
		equivalent?.let {
			move(equivalent, listOf(semanticValue))
			return currentManifest.read(semanticValue, kind)
		}
		return null
	}

	override fun move(
		sourceSemanticValue: L2SemanticValue,
		targetSemanticValues: Iterable<L2SemanticValue>)
	{
		currentManifest.agglomerateSynonym(
			targetSemanticValues + sourceSemanticValue,
			currentManifest.restrictionFor(sourceSemanticValue))
	}

	override fun createTuple(
		elements: List<L2ReadBoxedOperand>
	): L2ReadBoxedOperand
	{
		val size = elements.size
		if (elements.isEmpty()) return boxedConstant(emptyTuple())

		// Special cases for characters and integers
		val unionType = elements.fold(bottom) { t, read ->
			t.typeUnion(read.type())
		}
		val template = when
		{
			unionType.isSubtypeOf(Types.CHARACTER()) ->
			{
				// The string contains only characters.
				// Create a (shared) Avail string statically, and use that as
				// the basis for the string that will be built, only editing the
				// necessary parts.
				generateStringFromCodePoints(size) { oneBasedIndex ->
					elements[oneBasedIndex - 1].constantOrNull?.codePoint ?:
						'?'.code
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
					"new tuple",
					restrictionForType(
						tupleTypeForTypesList(elements.map { it.type() })))
				+L2_CREATE_TUPLE(L2ReadBoxedVectorOperand(elements), write)
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
		val typesList = template.mapTo(mutableListOf(), ::instanceTypeOrMetaOn)
		// Generate the updates for the non-constant parts (if any).
		elements.forEachIndexed { zeroIndex, read ->
			if (read.constantOrNull === null)
			{
				typesList[zeroIndex] = read.type()
				val newWrite = boxedWriteTemp(
					"with update @${zeroIndex + 1}",
					restrictionForType(tupleTypeForTypesList(typesList)))
				+L2_TUPLE_AT_UPDATE(
					latestRead,
					L2IntImmediateOperand(zeroIndex + 1),
					read,
					newWrite)
				latestRead = readBoxed(newWrite)
			}
		}
		return latestRead
	}

	override fun extractTupleElement(
		tupleRead: L2ReadBoxedOperand,
		index: Int,
		destinationSemanticValues: Set<L2SemanticValue>)
	{
		assert(currentManifest.caresAboutSemanticValues)
		val tupleInstruction =
			tupleRead.definitionSkippingMoves(currentManifest)
		val tupleSynonym =
			currentManifest.semanticValueToSynonym(tupleRead.semanticValue())
		tupleInstruction.run {
			extractTupleElement(tupleSynonym, index, destinationSemanticValues)
		}
	}

	override fun explodeTupleIfPossible(
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
				writeValue, restrictionForType(tupleType.typeAtIndex(i)))
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
	): A_Type? = functionReg.exactFunctionType(currentManifest)

	override fun extractParameterTypeFromFunction(
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
		val semanticParameterType = L2SemanticPrimitiveInvocation(
			P_ParamTypeAt,
			listOf(
				functionRead.semanticValue(),
				constant(parameterIndex)))
		currentManifest.equivalentSemanticValue(semanticParameterType)?.let {
			// Use the already extracted parameter type.
			move(it, listOf(semanticParameterType))
			return readBoxed(semanticParameterType)
		}
		val parameterTypeWrite = boxedWrite(
			semanticParameterType,
			restrictionForType(anyMeta).minusValue(bottom))
		+L2_FUNCTION_PARAMETER_TYPE(
			functionRead,
			L2IntImmediateOperand(parameterIndex),
			parameterTypeWrite)
		return readBoxed(parameterTypeWrite)
	}

	override fun createLoopHeadBlock(name: String): L2BasicBlock =
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
	override fun startBlock(
		block: L2BasicBlock,
		regenerator: L2Regenerator?)
	{
		currentBlock?.instructions()?.run {
			assert(isNotEmpty())
			assert(last().altersControlFlow) {
				"Previous block was not finished: ${currentBlock!!.name()}"
			}
		}
		// Verify that all predecessor blocks have been finished.
		assert(block.predecessorEdges().all { it.instructionHasBeenEmitted })
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
					&& regenerator.isNullOr { canCollapseUnconditionalJumps }
					&& predecessorBlock.zone == block.zone)
				{
					// The new block has only one predecessor, which
					// unconditionally jumps to it.  Remove the jump and
					// continue generation in the predecessor block.  Restore
					// the manifest from the jump edge.
					predecessorBlock.debugNote.appendLine(
						"Eliding jump to ${block.name()}")
					currentManifest =
						L2ValueManifest(predecessorEdge.manifest())
					predecessorBlock.instructions().removeAt(
						predecessorBlock.instructions().size - 1)
					jump.justRemoved()
					assert(predecessorBlock.successorEdges().isEmpty())
					assert(!predecessorBlock.hasControlFlowAtEnd)
					currentBlock = predecessorBlock
					return
				}
			}
		}
		currentBlock = block
		controlFlowGraph.startBlock(block)
		block.startIn(regenerator ?: this)
	}

	override fun currentBlockOrNull(): L2BasicBlock? = currentBlock

	override fun currentlyReachable(): Boolean =
		currentBlock.notNullAnd(L2BasicBlock::currentlyReachable)

	override fun addInstruction(instruction: L2Instruction)
	{
		if (currentBlock.isNullOr { hasControlFlowAtEnd }) return
		// Force emission of any postponed instructions that produce values
		// consumed by this instruction.
		if (!currentManifest.caresAboutSemanticValues
			|| instruction is L2_MOVE<*>
			|| instruction is L2_MOVE_CONSTANT<*, *>
			|| instruction is L2_PHI<*>
			|| instruction.hasSideEffect)
		{
			if (currentManifest.hasImpossibleRestriction
				&& mode !is WithFixedRegisterMap)
			{
				addToCurrentBlock(impossibleCodeInstruction())
				return
			}
		}

		// Actually emit the instruction.
		addToCurrentBlock(instruction)
	}

	/**
	 * Clone the given instruction for this generator, give it a chance to force
	 * needed postponed instructions or do other setup, then write the clone to
	 * the current block.
	 */
	private fun addToCurrentBlock(instruction: L2Instruction)
	{
		val clone = instruction.cloneFor(this)
		val keep = clone.aboutToAdd(this)
		if (keep)
		{
			currentBlock!!.addInstruction(clone, currentManifest)
		}
	}

	override fun <K : RegisterKind<K>> populateForRead(read: L2ReadOperand<K>)
	{
		val value = read.semanticValue()
		if (!currentManifest.hasLiveSemanticValue(value, read.kind))
		{
			// The requested semantic value isn't defined yet.  Create a
			// suitable clone of the postponed instruction with all
			// not-yet-defined semantic values plugged into the write operand,
			// remove the original, and emit the clone.
			ensureDefinedOrEmitMove(value, read.kind)
			assert(currentManifest.hasLiveSemanticValue(value, read.kind))
		}
		read.restrict { restrictionFor(value) }
	}

	override fun jumpTo(
		targetBlock: L2BasicBlock,
		optionalName: String?)
	{
		+L2_JUMP(edgeTo(targetBlock, optionalName))
	}

	override fun compareAndBranchInt(
		comparator: NumericComparator,
		int1Reg: L2ReadIntOperand,
		int2Reg: L2ReadIntOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand
	): Unit = comparator.run {
		generateCompareAndBranchInt(int1Reg, int2Reg, ifTrue, ifFalse)
	}

	override fun compareAndBranchBoxed(
		comparator: NumericComparator,
		number1Reg: L2ReadBoxedOperand,
		number2Reg: L2ReadBoxedOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand
	): Unit = comparator.run {
		generateCompareAndBranchBoxed(number1Reg, number2Reg, ifTrue, ifFalse)
	}

	override fun jumpIfEqualsObjects(
		firstValue: L2ReadBoxedOperand,
		secondValue: L2ReadBoxedOperand,
		equalBlock: L2BasicBlock,
		unequalBlock: L2BasicBlock)
	{
		firstValue.constantOrNull?.let { constant ->
			jumpIfEqualsConstant(
				secondValue,
				constant,
				equalBlock,
				unequalBlock)
			return
		}
		secondValue.constantOrNull?.let { constant ->
			jumpIfEqualsConstant(
				firstValue,
				constant,
				equalBlock,
				unequalBlock)
			return
		}
		if (firstValue.restriction().intersection(secondValue.restriction())
				.isImpossible)
		{
			jumpTo(unequalBlock)
			return
		}
		if (currentManifest.semanticValueToSynonym(firstValue.semanticValue())
			== currentManifest.semanticValueToSynonym(
				secondValue.semanticValue()))
		{
			jumpTo(equalBlock)
			return
		}
		+L2_JUMP_IF_OBJECTS_EQUAL(
			firstValue, secondValue, edgeTo(equalBlock), edgeTo(unequalBlock))
	}

	override fun jumpIfEqualsConstant(
		readToTest: L2ReadBoxedOperand,
		constantValue: A_BasicObject,
		passBlock: L2BasicBlock,
		failBlock: L2BasicBlock)
	{
		val restriction = readToTest.restriction()
		restriction.constantOrNull?.let { constant ->
			// The value is constant.  Always succeed or always fail.
			jumpTo(
				if (constant.equals(constantValue)) passBlock
				else failBlock)
			return
		}
		val valueSource = readToTest.definitionSkippingMoves(currentManifest)
		if (constantValue.isBoolean)
		{
			val constantBool = constantValue.equals(trueObject)
			when (valueSource)
			{
				is L2_RUN_INFALLIBLE_PRIMITIVE
					if valueSource.primitive.constant === P_Equality ->
				{
					val (read1, read2) = argsOf(valueSource)
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
					jumpIfEqualsObjects(
						read1,
						read2,
						if (constantBool) passBlock else failBlock,
						if (constantBool) failBlock else passBlock)
					return
				}

				is L2_JUMP_IF_SUBTYPE ->
				{
					// Instance-of testing is done by extracting the type and
					// testing if it's a subtype.  See if the operand to the
					// is-subtype test is a get-type instruction.
					val firstTypeOperand = valueSource.firstType
					val secondTypeOperand = valueSource.seccondType
					val firstTypeSource =
						firstTypeOperand.definitionSkippingMoves(currentManifest)
					if (firstTypeSource is L2_GET_TYPE)
					{
						// There's a get-type followed by an is-subtype followed
						// by a compare-and-branch of the result against a
						// constant boolean.  Replace with a branch-if-kind.
						+L2_JUMP_IF_KIND_OF_OBJECT(
							firstTypeSource.value,
							secondTypeOperand,
							edgeTo(
								if (constantBool) passBlock
								else failBlock),
							edgeTo(
								if (constantBool) failBlock
								else passBlock))
						return
					}
					// Perform a branch-if-is-subtype-of instead of checking
					// whether the Avail boolean is true or false.
					+L2_JUMP_IF_SUBTYPE(
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
		// intermediate block that uses a move to a temp to F the constant
		// value to be visible in a register.
		val innerPass = L2BasicBlock("strengthen to constant")
		val constantValueStrong = constantValue as AvailObject
		if (constantValueStrong.isInt
			&& readToTest.restriction().containedByType(i32))
		{
			// The constant and the value are both int32s.  Use the quicker int
			// test, unboxing the int register if needed.
			compareAndBranchInt(
				NumericComparator.Equal,
				readInt(readToTest.semanticValue(), failBlock) {
					// It wasn't really an int, so it can't equal the constant
					// int.  We've already jumped to failBlock in that case, so
					// we're done rewriting the branch.
					return
				},
				unboxedIntConstant(constantValueStrong.extractInt),
				edgeTo(innerPass),
				edgeTo(failBlock))
		}
		else if (constantValueStrong.isCharacter
			&& readToTest.restriction().containedByType(Types.CHARACTER())
			&& valueSource is L2_CODEPOINT_TO_CHARACTER)
		{
			// Rather than compare characters, we have access to the codepoint
			// i32 that we can compare to the constant's codepoint instead.
			compareAndBranchInt(
				NumericComparator.Equal,
				valueSource.source,
				unboxedIntConstant(constantValueStrong.codePoint),
				edgeTo(innerPass),
				edgeTo(failBlock))
		}
		else
		{
			+L2_JUMP_IF_EQUALS_CONSTANT(
				readToTest,
				L2ConstantOperand(constantValue),
				edgeTo(innerPass),
				edgeTo(failBlock))
		}
		startBlock(innerPass)
		if (currentlyReachable())
		{
			val semanticConstant = constant(constantValue)
			if (!currentManifest.hasSemanticValue(semanticConstant))
			{
				move(readToTest.semanticValue(), setOf(semanticConstant))
			}
			jumpTo(passBlock)
		}
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
				// low to low + N - 1.  So they cover a contiguous range.
				if (restriction.type.isIntegerRangeType &&
					restriction.containedByType(inclusive(low, high)))
				{
					// The value is already known to be in range, so we can
					// strengthen it by the enumeration.
					currentManifest.intersectType(semanticValue, expectedType)
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
		val equivalentUnboxed = currentManifest
			.equivalentPopulatedSemanticValue(semanticValue, BOXED_KIND)
		if (equivalentUnboxed != null
			&& currentManifest.restrictionFor(semanticValue)
				.containedByType(i32))
		{
			// The value is definitely an i32, and we have the value in an
			// equivarent int.  Use it.
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
					readIntNoFail(equivalentUnboxed),
					unboxedIntConstant(low),
					edgeTo(firstSuccess),
					edgeTo(failedCheck))
				startBlock(firstSuccess)
				compareAndBranchInt(
					NumericComparator.LessOrEqual,
					readIntNoFail(equivalentUnboxed),
					unboxedIntConstant(high),
					edgeTo(passedCheck),
					edgeTo(failedCheck))
				return
			}
		}
		// We can't pin it down statically, so do the dynamic check.
		+L2_JUMP_IF_KIND_OF_OBJECT(
			valueRead,
			boxedConstant(expectedType),
			edgeTo(passedCheck),
			edgeTo(failedCheck))
	}

	override fun determineRawFunction(
		functionToCallRead: L2ReadBoxedOperand
	): A_RawFunction?
	{
		functionToCallRead.constantOrNull?.let { function ->
			return function.code()
		}
		// See if we can at least find out the raw function that the function
		// was created from.
		return functionToCallRead
			.definitionSkippingMoves(currentManifest)
			.getConstantCode(currentManifest)
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
		comment: String?,
		body: L2Generator.()->Unit)
	{
		assert(!isGeneratingRetroactively)
		assert(edge.sourceBlock().successorEdges().size == 1) {
			"Can't generate retroactively before an unsplit edge: " +
				edge.sourceBlock().successorEdges()
		}
		val sourceBlock = edge.sourceBlock()

		val savedManifest = currentManifest
		val savedBlock = currentBlock
		val savedFinalInstruction = sourceBlock.instructions().removeLast()
		currentManifest = edge.manifest()
		currentBlock = sourceBlock
		sourceBlock.removedControlFlowInstruction()
		isGeneratingRetroactively = true

		currentManifest.check()
		comment?.let {
			addInstruction(L2_NOP("Start retroactive generation$it"))
		}
		try
		{
			body()
			assert(!currentBlock!!.hasControlFlowAtEnd)
			comment?.let {
				addInstruction(L2_NOP("...End retroactive generation"))
			}
		}
		finally
		{
			isGeneratingRetroactively = false
		}
		currentManifest.check()
		// Put back the final instruction.
		sourceBlock.instructions().add(savedFinalInstruction)
		sourceBlock.readdedControlFlowInstruction()
		currentManifest = savedManifest
		currentBlock = savedBlock
	}

	override fun addContingentValue(contingentValue: A_ChunkDependable)
	{
		contingentValues =
			contingentValues.setWithElementCanDestroy(contingentValue, true)
	}

	override fun createChunk(code: A_RawFunction): L2Chunk
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
			optimizationLevel.countdown < Long.MAX_VALUE,
			contingentValues)
		code.setStartingChunkAndReoptimizationCountdown(
			chunk!!, optimizationLevel.countdown)
		return chunk!!
	}

	override fun <K: RegisterKind<K>> forceTranslationForRead(
		semanticValue: L2SemanticValue,
		kind: K)
	{
		ensureDefinedOrEmitMove(semanticValue, kind)
	}

	override fun forceAllPostponedTranslationsExceptConstantMoves(
		omitConstantMoves: Boolean)
	{
		// Copy the collection of postponed instructions to visit.
		val initialInstructions =
			currentManifest.allPostponedInstructions().toList()
		initialInstructions.forEach { instructionEquivalence ->
			val synonym = instructionEquivalence.synonym
			val value = synonym.pickSemanticValue()
			// We're modifying postponedInstructions, so check if it's still
			// present.
			val kind = instructionEquivalence.kind
			currentManifest
				.postponedInstructionFor(value, kind.cast())
				?.let { postponed ->
					if (!omitConstantMoves
						|| postponed !is L2_MOVE_CONSTANT<*, *>)
					{
						forceTranslationForRead(value, kind.cast())
					}
			}
		}
		if (shouldSanityCheck)
		{
			currentManifest.allPostponedInstructions()
				.forEach { instructionEquivalence ->
					val instruction = instructionEquivalence.instruction
					assert(
						instruction is L2_MOVE<*> ||
							instruction is L2_MOVE_CONSTANT<*, *>)
			}
		}
	}

	override fun forcePostponedTranslationsBeforeEdge(
		edge: L2PcOperand,
		semanticValuesAndKinds:
			Iterable<Pair<L2SemanticValue, RegisterKind<*>>>)
	{
		assert(!isGeneratingRetroactively)
		assert(currentManifest.caresAboutSemanticValues)
		// Skip if we already have the value live.
		val filtered = semanticValuesAndKinds.filterNot { (sv, kind) ->
			edge.manifest().hasLiveSemanticValue(sv, kind.cast())
		}
		if (filtered.isEmpty()) return
		val grouped = filtered
			.groupBy { (sv, _) -> edge.manifest().semanticValueToSynonym(sv) }
			.values
		val groupedFormatted = grouped.joinToString(",\n\t", ":\n\t") {
			group -> group.joinToString()
		}
		generateRetroactivelyBeforeEdge(
			edge, groupedFormatted
		) {
			filtered.forEach { (sv, kind) ->
				// Recheck, in case a previous generated instruction populated
				// the semantic value.  Note that the edge's manifest is current
				// here.
				if (!currentManifest.hasLiveSemanticValue(sv, kind.cast()))
					forceTranslationForRead(sv, kind.cast())
				// An impossible restriction was uncovered while generating
				// in the predecessor node.  Make this obvious for the next
				// pass, to eliminate any path leading only to such
				// instructions.
				if (currentManifest.hasImpossibleRestriction)
				{
					addInstruction(L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW())
				}
			}
		}
	}

	override fun splitEdge(edge: L2PcOperand)
	{
		assert(edge.instructionHasBeenEmitted)
		// Don't split if the edge is just a jump target.
		when (edge.sourceBlock().instructions().last())
		{
			is L2_JUMP -> return
			is L2_JUMP_BACK -> return
		}

		currentManifest.check()
		edge.manifest().check()

		// Capture where this edge originated.
		val originalSourceInstruction = edge.instruction
		val originalSourceBlock = originalSourceInstruction.basicBlock()
		val originalTargetBlock = edge.targetBlock()

		// Create a new intermediary block that initially just contains a jump
		// to itself.
		val newBlock = L2BasicBlock(
			"edge-split ${nextUnique()} to ${originalTargetBlock.name()}",
			originalTargetBlock.zone,
			isCold = originalTargetBlock.isCold)
		newBlock.postPhiMap = edge.manifest().extractPostPhiMap()
		var prototypeJump = L2_JUMP(
			L2PcOperand(newBlock, false, null, edge.optionalName)
		).cloneFor(this, newBlock) as L2_JUMP
		prototypeJump.target.setManifestToCloneOf(edge.manifest())
		newBlock.insertInstruction(0, prototypeJump)
		val jump = newBlock.instructions()[0] as L2_JUMP
		val jumpEdge = jump.target

		// Add the newBlock somewhere that looks sensible for debugging,
		// although we'll order the blocks later.
		val blocks = controlFlowGraph.basicBlockOrder
		blocks.add(blocks.indexOf(originalSourceBlock) + 1, newBlock)

		// At this point, nothing previously in the graph has been modified:
		//
		// ```A --e1-> C```
		//
		// plus block B, not yet in the graph, containing a jump to itself with
		// a copy of e1's manifest.
		//
		// ```B --e2-> B (a loop)```
		//
		// Now swap edge's target field with jumpEdge's target field, to get:
		//
		// ```A --e2-> B --e1-> C```
		//
		// Note how e1->C and e2->B are unaffected.  We just have to switch
		// the operands in the two instructions (the one ending A,
		// originalSourceInstruction, and the one ending B, jump).  This also
		// adjusts the L2PcOperands' instruction backpointers.
		jump.replaceEdgeWith(jumpEdge, edge)
		originalSourceInstruction.replaceEdgeWith(edge, jumpEdge)

		// The block caches predecessor and successor edges, so update them.
		newBlock.replaceSuccessorEdge(jumpEdge, edge)
		originalSourceBlock.replaceSuccessorEdge(edge, jumpEdge)

		// Now make sure we did it all correctly.  Start with the new edge.
		assert(jumpEdge.sourceBlock() === originalSourceBlock)
		assert(jumpEdge.targetBlock() === newBlock)
		assert(jumpEdge in originalSourceBlock.successorEdges())
		assert(jumpEdge in newBlock.predecessorEdges())
		assert(jumpEdge.instruction.basicBlock() === originalSourceBlock)
		assert(jumpEdge in
			originalSourceBlock.instructions().last().targetEdges)
		// Now check the original edge, which should lead from the newBlock to
		// the originalTargetBlock.
		assert(edge.sourceBlock() === newBlock)
		assert(edge.targetBlock() === originalTargetBlock)
		assert(edge in newBlock.successorEdges())
		assert(edge in originalTargetBlock.predecessorEdges())
		assert(edge.instruction.basicBlock() === newBlock)
		assert(edge in newBlock.instructions().last().targetEdges)
		//
		jumpEdge.manifest().check()
		edge.manifest().check()
	}

	override fun forcePostponedWritesToLocals()
	{
		currentManifest.allPostponedInstructions()
			.filter { it.instruction is L2_SET_UNESCAPED_LOCAL_VARIABLE }
			.forEach { equivalence ->
				forceTranslationForRead(
					equivalence.synonym.pickSemanticValue(),
					equivalence.kind.cast())
			}
	}

	override fun visualize(
		generator: L2Generator?,
		focusValue: L2SemanticValue?)
	{
		controlFlowGraph.visualize(generator ?: this, focusValue)
	}

	override fun simplyVisualize(
		generator: L2Generator?,
		focusValue: L2SemanticValue?)
	{
		controlFlowGraph.simplyVisualize(generator ?: this, focusValue)
	}

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

		override fun doOperand(operand: L2CommentOperand) = Unit

		override fun doOperand(operand: L2ConstantOperand) = Unit

		override fun doOperand(operand: L2IntImmediateOperand) = Unit

		override fun doOperand(operand: L2FloatImmediateOperand) = Unit

		override fun doOperand(operand: L2ArbitraryConstantOperand<*>) = Unit

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
		 *
		 * TODO - Once we start tracking actual lookup results per call site,
		 *  we can decrease this.
		 */
		const val maxPolymorphismToInlineDispatch = 50

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
		 * @param forcedClampedRegisters
		 *   A [Set] of [L2Register]s that limit the information that can propagate
		 *   arcoss the new back-edge.
		 * @param forcedClampedSemanticValues
		 *   A [Set] of [L2SemanticValue]s that limit the information that can
		 *   propagate arcoss the new back-edge.
		 * @return
		 *   The new [L2PcOperand].
		 */
		fun backEdgeTo(
			targetBlock: L2BasicBlock,
			forcedClampedRegisters: Set<L2Register<*>>,
			forcedClampedSemanticValues: Set<L2SemanticValue>
		): L2PcOperand
		{
			assert(targetBlock.isLoopHead)
			val backEdge = L2PcOperand(targetBlock, true)
			backEdge.forcedClampedRegisters = forcedClampedRegisters
			backEdge.forcedClampedSemanticValues = forcedClampedSemanticValues
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
