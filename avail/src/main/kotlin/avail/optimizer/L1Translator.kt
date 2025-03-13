/*
 * L1Translator.kt
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

import avail.AvailRuntime
import avail.AvailRuntime.HookType
import avail.AvailRuntimeSupport
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.countdownToReoptimize
import avail.descriptor.functions.A_RawFunction.Companion.declarationNames
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
import avail.descriptor.functions.A_RawFunction.Companion.localTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numConstants
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.functions.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.returnTypeIfPrimitiveFails
import avail.descriptor.functions.A_RawFunction.Companion.shortMethodName
import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.definitionsAtOrBelow
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.A_Method.Companion.lookupByTypesFromTuple
import avail.descriptor.methods.A_Method.Companion.numArgs
import avail.descriptor.methods.A_Method.Companion.testingTree
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.couldEverBeInvokedWith
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.A_Variable.Companion.value
import avail.descriptor.variables.A_Variable.Companion.valueWasStablyComputed
import avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import avail.descriptor.variables.VariablePlaceholderDescriptor.Companion.newPlaceholder
import avail.dispatch.InternalLookupTree
import avail.dispatch.LeafLookupTree
import avail.exceptions.MethodDefinitionException
import avail.exceptions.unsupported
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import avail.interpreter.Primitive.Flag
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.assignmentFunction
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelOne.L1Disassembler
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelOne.L1OperationDispatcher
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk.ChunkEntryPoint
import avail.interpreter.levelTwo.L2JVMChunk.Companion.unoptimizedChunk
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadMixedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.anyRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restriction
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_CREATE_FUNCTION
import avail.interpreter.levelTwo.operation.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK_FOR_CALL
import avail.interpreter.levelTwo.operation.L2_FALL_BACK_TO_L1
import avail.interpreter.levelTwo.operation.L2_GET_CURRENT_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_GET_CURRENT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_GET_IMPLICIT_OBSERVE_FUNCTION
import avail.interpreter.levelTwo.operation.L2_GET_LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.interpreter.levelTwo.operation.L2_INTERPRET_LEVEL_ONE
import avail.interpreter.levelTwo.operation.L2_INVOKE
import avail.interpreter.levelTwo.operation.L2_INVOKE_CONSTANT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_JUMP_BACK
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_INTERRUPT
import avail.interpreter.levelTwo.operation.L2_LOOKUP_BY_TYPES
import avail.interpreter.levelTwo.operation.L2_LOOKUP_BY_VALUES
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_MOVE_OUTER_VARIABLE
import avail.interpreter.levelTwo.operation.L2_NOP
import avail.interpreter.levelTwo.operation.L2_PREPARE_NEW_FRAME_FOR_L1
import avail.interpreter.levelTwo.operation.L2_REENTER_L1_CHUNK_FROM_CALL
import avail.interpreter.levelTwo.operation.L2_REENTER_L1_CHUNK_FROM_INTERRUPT
import avail.interpreter.levelTwo.operation.L2_REIFY
import avail.interpreter.levelTwo.operation.L2_RETURN
import avail.interpreter.levelTwo.operation.L2_RETURN_FROM_REIFICATION_HANDLER
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.operation.L2_STRIP_MANIFEST
import avail.interpreter.levelTwo.operation.L2_TRY_OPTIONAL_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_TRY_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_TYPE_UNION
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import avail.interpreter.levelTwo.operation.tuples.L2_CREATE_TUPLE
import avail.interpreter.levelTwo.operation.variables.GetClearMode
import avail.interpreter.levelTwo.operation.variables.GetClearMode.ClearIfMutable
import avail.interpreter.levelTwo.operation.variables.GetClearMode.NeverClear
import avail.interpreter.levelTwo.operation.variables.L2_CHECK_ESCAPED_LOCALS
import avail.interpreter.levelTwo.operation.variables.L2_CREATE_VARIABLE
import avail.interpreter.levelTwo.operation.variables.L2_GET_AND_CLEAR_UNESCAPED_LOCAL_VARIABLE
import avail.interpreter.levelTwo.operation.variables.L2_GET_UNESCAPED_LOCAL_VARIABLE
import avail.interpreter.levelTwo.operation.variables.L2_SET_UNESCAPED_LOCAL_VARIABLE
import avail.interpreter.levelTwo.operation.variables.L2_SET_VARIABLE_NO_CHECK
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.optimizer.CallSiteHelper.JunctionType.AfterCallNoCheckTestEscapes
import avail.optimizer.CallSiteHelper.JunctionType.AfterCallWithCheckTestEscapes
import avail.optimizer.CallSiteHelper.JunctionType.FallBackToSlowLookup
import avail.optimizer.CallSiteHelper.JunctionType.ReificationNoCheck
import avail.optimizer.CallSiteHelper.JunctionType.ReificationUnreturnable
import avail.optimizer.CallSiteHelper.JunctionType.ReificationWithCheck
import avail.optimizer.L2ControlFlowGraph.ZoneType
import avail.optimizer.L2Generator.Companion.backEdgeTo
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2Generator.Companion.maxPolymorphismToInlineDispatch
import avail.optimizer.L2GeneratorInterface.SpecialBlock.AFTER_OPTIONAL_PRIMITIVE
import avail.optimizer.L2GeneratorInterface.SpecialBlock.RESTART_LOOP_HEAD
import avail.optimizer.L2GeneratorInterface.SpecialBlock.START
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.OptimizationLevel.UNOPTIMIZED
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticConstant
import avail.optimizer.values.L2SemanticValue
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import java.util.IdentityHashMap
import java.util.logging.Level

/**
 * The `L1Translator` transliterates a sequence of
 * [level&#32;one&#32;instructions][L1Operation] into one or more simple
 * [level&#32;two&#32;instructions][L2Instruction], under the assumption that
 * further optimization steps will be able to transform this code into something
 * much more efficient – without altering the level one semantics.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property generator
 *   The [L2Generator] for which I'm producing an initial translation.
 * @property interpreter
 *   The [Interpreter] that tripped the translation request.
 * @property code
 *   The [raw&#32;function][CompiledCodeDescriptor] to transliterate into level
 *   two code.
 * @constructor
 * Create a new L1 naive translator for the given [L2Generator].
 *
 * @param generator
 *   The [L2Generator] on which I'm producing an initial translation from L1.
 * @param interpreter
 *   The [Interpreter] that tripped the translation request.
 * @param code
 *   The [A_RawFunction] which is the source of the chunk being created.
 */
class L1Translator private constructor(
	val generator: L2Generator,
	private val interpreter: Interpreter,
	val code: A_RawFunction
) : L1OperationDispatcher, L2GeneratorInterface by generator
{
	/**
	 * Capture the number of arguments that is expected by the [A_RawFunction]
	 * being translated.
	 */
	private val numArgs: Int = code.numArgs()

	/**
	 * Capture the number of local variables (not arguments or constants) that
	 * the [A_RawFunction] builds.
	 */
	private val numLocals: Int = code.numLocals

	/**
	 * The number of slots in the virtualized continuation.  This includes the
	 * arguments, the locals (including the optional primitive failure result),
	 * and the stack slots.
	 */
	private val numSlots: Int = code.numSlots

	/**
	 * An array of names of arguments/locals/constants/labels, if available, to
	 * make it easier to follow [L2ControlFlowGraph]s.
	 */
	private val slotNames: Array<String>

	/**
	 * An array of names of outers, if available, to make it easier to follow
	 * [L2ControlFlowGraph]s.
	 */
	private val outerNames: Array<String>

	init
	{
		val allNames = code.declarationNames.map { it.asNativeString() }
		// Omit the label, since it gets its own subclass of L2SemanticValue.
		slotNames = allNames
			.subList(0, numArgs + numLocals + code.numConstants)
			.withIndex()
			.groupBy(IndexedValue<String>::value, IndexedValue<*>::index)
			// Generated phrases can contain duplicate names, so disambiguate
			// them here.
			.flatMap { (name, indices) ->
				when (indices.size)
				{
					1 -> listOf(IndexedValue(indices[0], name))
					else -> indices.map { IndexedValue(it, "$name/${it+1}") }
				}
			}
			.sortedBy(IndexedValue<String>::index)
			.map(IndexedValue<String>::value)
			.toTypedArray()
		outerNames = allNames
			.subList(allNames.size - code.numOuters, allNames.size)
			.toTypedArray()
	}

	/**
	 * The [L2SemanticValue]s corresponding with the slots of the virtual
	 * continuation.  These indices are zero-based, but the slot numbering is
	 * one-based.
	 */
	private val semanticSlots: Array<L2SemanticBoxedValue> =
		Array(numSlots) { createSemanticSlot(1 + it, 1) }

	/**
	 * The current level one nybblecode position during naive translation to
	 * level two.
	 */
	val instructionDecoder = L1InstructionDecoder().also { decoder ->
		code.setUpInstructionDecoder(decoder)
		decoder.pc = 1
	}

	/**
	 * While translating an L1 nybblecode, this holds the pc of that
	 * instruction.
	 */
	var pcOfCurrentInstruction: Int = -1

	/**
	 * The current stack depth during naive translation to level two.
	 */
	var stackp: Int = numSlots + 1

	/** The stack depth at the start of the current instruction. */
	var stackpOfCurrentInstruction: Int = -1

	/**
	 * The exact function that we're translating, if known.  This is only
	 * non-null if the function captures no outers.
	 */
	private val exactFunctionOrNull: A_Function? =
		computeExactFunctionOrNullForCode(code)

	/**
	 * Get the program counter for the next instruction to be decoded.
	 */
	val pc: Int get() = instructionDecoder.pc

	/**
	 * Create a semantic slot for the given one-based [index], representing the
	 * state just before reaching the specified [afterPc].
	 */
	fun createSemanticSlot(index: Int, afterPc: Int): L2SemanticBoxedValue =
		topFrame.semanticSlot(
			index,
			afterPc,
			if (index <= slotNames.size) slotNames[index - 1] else null)

	/**
	 * Answer the [L2SemanticValue] representing the virtual continuation slot
	 * having the given one-based index.
	 *
	 * @param index
	 *   The one-based slot number.
	 * @return
	 *   The [L2SemanticValue] for that slot.
	 */
	private fun semanticSlot(index: Int): L2SemanticBoxedValue =
		semanticSlots[index - 1]

	/**
	 * Answer the register holding the latest assigned version of the specified
	 * continuation slot. The slots are the arguments, then the locals, then the
	 * stack entries. The slots are numbered starting at 1.
	 *
	 * This is only public to allow primitives like [P_RestartContinuation] to
	 * be able to fetch the current arguments.
	 *
	 * @param slotIndex
	 *   The index into the continuation's slots.
	 * @return
	 *   An [L2ReadBoxedOperand] representing that continuation slot.
	 */
	fun readSlot(slotIndex: Int): L2ReadBoxedOperand =
		readBoxed(semanticSlot(slotIndex))

	/**
	 * Create a new semantic value to overwrite any existing value in the
	 * specified continuation slot.  Answer a write of a synonym containing just
	 * that semantic value.
	 *
	 * The slots are the arguments, the local variables, the local constants,
	 * and finally the stack entries.  Slots are numbered starting at 1.
	 *
	 * @param slotIndex
	 *   The index into the continuation's slots.
	 * @param effectivePc
	 *   The Level One pc at which this write should be considered effective.
	 * @param restriction
	 *   The bounding [TypeRestriction] for the new register.
	 * @return
	 *   A register write representing that continuation slot.
	 */
	private fun writeSlot(
		slotIndex: Int,
		effectivePc: Int,
		restriction: TypeRestriction
	): L2WriteBoxedOperand
	{
		// Create a new semantic slot at the effectivePc, representing this
		// newly written value.
		val semanticValue = createSemanticSlot(slotIndex, effectivePc)
		semanticSlots[slotIndex - 1] = semanticValue
		return boxedWrite(semanticValue, restriction)
	}

	/**
	 * Associate the specified [L2ReadBoxedOperand] with the semantic slot
	 * having the given index and effective pc.  Restrict the type based on the
	 * register-read's [TypeRestriction].
	 *
	 * @param slotIndex
	 *   The slot index to replace.
	 * @param effectivePc
	 *   The effective pc.
	 * @param registerRead
	 *   The [L2ReadBoxedOperand] that should now be considered the current
	 *   register-read representing that slot.
	 */
	fun forceSlotRegister(
		slotIndex: Int,
		effectivePc: Int,
		registerRead: L2ReadBoxedOperand)
	{
		forceSlotRegister(
			slotIndex,
			effectivePc,
			registerRead.semanticValue(),
			registerRead.restriction())
	}

	/**
	 * Associate the specified register with the slot semantic value having the
	 * given index and effective pc.  Note that the given synonym is always
	 * invalidated by a merge in this method.
	 *
	 * @param slotIndex
	 *   The slot index to replace.
	 * @param effectivePc
	 *   The effective pc.
	 * @param sourceSemanticValue
	 *   The [L2SemanticValue] that is moved into the slot.
	 * @param restriction
	 *   The [TypeRestriction] that currently bounds the synonym's possible
	 *   values.
	 */
	private fun forceSlotRegister(
		slotIndex: Int,
		effectivePc: Int,
		sourceSemanticValue: L2SemanticBoxedValue,
		restriction: TypeRestriction)
	{
		// Create a new L2SemanticSlot at the effective pc, representing this
		// newly written value.
		val slotSemanticValue = createSemanticSlot(slotIndex, effectivePc)
		semanticSlots[slotIndex - 1] = slotSemanticValue
		moveBoxedRegister(
			sourceSemanticValue,
			setOf(slotSemanticValue))
		currentManifest.setRestriction(slotSemanticValue, restriction)
	}

	/**
	 * Make the specified slot take its value from the given [semanticValue].
	 *
	 * @param slotIndex
	 *   The slot index to replace.
	 * @param semanticValue
	 *   The [L2SemanticValue] that should be used when subsequently reading
	 *   from this slot.  It must already be available in the current
	 *   [L2ValueManifest].
	 */
	private fun forceSlot(
		slotIndex: Int,
		semanticValue: L2SemanticBoxedValue)
	{
		semanticSlots[slotIndex - 1] = semanticValue
	}

	/**
	 * Replace slot [slotIndex] with an [L2SemanticConstant], possibly
	 * generating a constant move into that semantic value.
	 *
	 * @param slotIndex
	 *   The one-based index into the virtual continuation's slots.
	 * @param constant
	 *   The [AvailObject] that will be found as a constant in the slot.
	 */
	private fun forceConstantSlot(slotIndex: Int, constant: AvailObject)
	{
		forceSlot(slotIndex, boxedConstant(constant).semanticValue())
	}

	/**
	 * Write [nil] to slot [slotIndex].
	 *
	 * @param slotIndex
	 *   The slot to overwrite with the constant [nil].
	 */
	private fun nilSlot(slotIndex: Int)
	{
		forceConstantSlot(slotIndex, nil)
	}

	/**
	 * Write instructions to extract the current function, and answer an
	 * [L2ReadBoxedOperand] for the register that will hold the function
	 * afterward.
	 */
	private val currentFunction: L2ReadBoxedOperand
		get()
		{
			val semanticFunction = topFrame.function()
			if (currentManifest.hasSemanticValue(semanticFunction))
			{
				// Note the current function can't ever be an int or float.
				return readBoxed(semanticFunction)
			}
			// We have to get it into a register.
			if (exactFunctionOrNull !== null)
			{
				// The exact function is known.
				return boxedConstant(exactFunctionOrNull)
			}
			// The exact function isn't known, but we know the raw function, so
			// we statically know the function type.
			val restriction = boxedRestrictionForType(code.functionType())
			val functionWrite =
				boxedWrite(semanticFunction, restriction)
			+L2_GET_CURRENT_FUNCTION(functionWrite)
			return readBoxed(functionWrite)
		}

	/**
	 * Write instructions to extract a numbered outer from the current function,
	 * and answer an [L2ReadBoxedOperand] for the register that will hold the
	 * outer value afterward.
	 *
	 * @param outerIndex
	 *   The index of the outer to get.
	 * @param outerType
	 *   The type that the outer is known to be.
	 * @return
	 *   The [L2ReadBoxedOperand] where the outer was written.
	 */
	private fun getOuterRegister(
		outerIndex: Int,
		outerType: A_Type): L2ReadBoxedOperand
	{
		val outerName =
			if (outerIndex <= outerNames.size) outerNames[outerIndex - 1]
			else null
		val semanticOuter = topFrame.outer(outerIndex, outerName)
		if (currentManifest.hasSemanticValue(semanticOuter))
		{
			return readBoxed(semanticOuter)
		}
		if (outerType.instanceCount.equalsInt(1)
			&& !outerType.isInstanceMeta)
		{
			// The exact outer is known statically.
			return boxedConstant(outerType.instance)
		}
		val functionRead = currentFunction
		var restriction = boxedRestrictionForType(outerType)
		if (functionRead.restriction().isImmutable)
		{
			// An immutable function has immutable captured outers.
			restriction = restriction.withFlag(IMMUTABLE_FLAG)
		}
		val outerWrite = boxedWrite(semanticOuter, restriction)
		+L2_MOVE_OUTER_VARIABLE(
			L2CommentOperand(outerName ?: ""),
			L2IntImmediateOperand(outerIndex),
			functionRead,
			outerWrite)
		return readBoxed(outerWrite)
	}

	/**
	 * Capture the latest value returned by the [L2_RETURN] instruction in
	 * this [Interpreter].
	 *
	 * @param name
	 *   An optional short name that describes the purpose of the temp created
	 *   to hold the latest return value.  It does not need to be unique.
	 * @param guaranteedType
	 *   The type the return value is guaranteed to conform to.
	 * @return
	 *   An [L2ReadBoxedOperand] that now holds the returned value.
	 */
	fun getLatestReturnValue(
		name: String,
		guaranteedType: A_Type
	): L2ReadBoxedOperand
	{
		val writer = boxedWriteTemp(
			name,
			boxedRestrictionForType(guaranteedType))
		+L2_GET_LATEST_RETURN_VALUE(writer)
		return readBoxed(writer)
	}

	/**
	 * Emit code that checks if any local variable has become shared or has had
	 * a reactor set on it, and if so, falls back to L1 code.  Later, during a
	 * postponement optimization, local variable creation instructions may drift
	 * past this check, removing themselves from the list as they pass, and
	 * eliminating the check entirely if they are all able to slip past.
	 *
	 * @param actuallyCheck
	 *   Whether to generate check code for the locals, versus simple moves.
	 * @param comment
	 *   A [String] describing why this instruction was generated.
	 */
	fun emitCheckLocals(actuallyCheck: Boolean, comment: String)
	{
		if (numLocals == 0) return
		val ifSafe = createBasicBlock("locals are safe")
		val ifFallBack = createBasicBlock(
			"fall back to L1",
			ZoneType.DEAD_END.createZone("Fall back"),
			isCold = true)
		val localIndices = (numArgs + 1 .. numArgs + numLocals).filter {
			// The only constant locals are placeholder variables (see
			// [VariablePlaceholderDescriptor]) indicating elision, or a `nil`
			// representing the variable has been used for the last time and
			// removed even at the L1 level.  Both of those cases should be
			// excluded from the check (or move).
			readSlot(it).constantOrNull == null
		}
		if (localIndices.isEmpty()) return
		if (actuallyCheck)
		{
			+L2_CHECK_ESCAPED_LOCALS(
				L2CommentOperand(comment),
				L2ReadBoxedVectorOperand(
					localIndices.map(::readSlot)),
				L2WriteBoxedVectorOperand(
					localIndices.map { slotIndex ->
						boxedWrite(
							createSemanticSlot(slotIndex, pc),
							boxedRestrictionForType(
								code.localTypeAt(slotIndex - numArgs)))
					}),
				edgeTo(ifSafe),
				edgeTo(ifFallBack))
			assert(!currentlyReachable())

			startBlock(ifFallBack)
			+L2_FALL_BACK_TO_L1(
				L2IntImmediateOperand(pc),
				L2IntImmediateOperand(stackp),
				L2ReadBoxedVectorOperand((1..numSlots).map(::readSlot)))
			assert(!currentlyReachable())
			startBlock(ifSafe)
		}
		else
		{
			// Move the locals into new semantic slots, as part of the mechanism
			// that prevents reordering reads and writes.
			localIndices.forEach { slotIndex ->
				moveBoxedRegister(
					semanticSlot(slotIndex),
					listOf(createSemanticSlot(slotIndex, pc)))
			}
		}
	}

	/**
	 * Generate instruction(s) to move the given [AvailObject] into a fresh
	 * writable slot [L2Register] with the given slot index.  The slot it
	 * occupies is tagged with the current pc.
	 *
	 * @param value
	 *   The value to move.
	 * @param slotIndex
	 *   The index of the slot in which to write it.
	 */
	private fun moveConstantToSlot(value: A_BasicObject, slotIndex: Int)
	{
		forceSlotRegister(
			slotIndex, pc, boxedConstant(value))
	}

	/**
	 * Generate code to capture live register data in an [A_RegisterDump] inside
	 * a dummy [A_Continuation], return a reifier that accumulates the outer
	 * lambdas to run in the reverse order of popping, and after the interpreter
	 * loop reaches the outermost JVM stack level, resume those dummy
	 * continuations, each of which should create a real continuation (with the
	 * correct caller) and push ito onto the call chain, then
	 * [L2_RETURN_FROM_REIFICATION_HANDLER].
	 *

	 * After reification, the interpreter's next activity depends on the flags
	 * set in the [StackReifier] (which was created via code generated prior to
	 * this clause).  If it was for interrupt processing, the continuation will
	 * be stored in the fiber while an interrupt is processed, then most likely
	 * resumed at a later time.
	 *
	 * If the reification was for handling reification within some called chain
	 * of functions, the chunk will be restarted in a way that it treats as
	 * being "returned into" (see [L2_ENTER_L2_CHUNK]), getting the returned
	 * value from [Interpreter.latestResult], and the rest of the registers'
	 * state from the [A_RegisterDump] in the continuation, which gets popped.
	 *
	 * If the reification was for getting into a state suitable for creating an
	 * L1 label, the top continuation's chunk is resumed immediately, whereupon
	 * the continuation will be popped and exploded back into registers, and the
	 * actual label will be created from the continuation that was just resumed
	 * (making use of [Interpreter.getReifiedContinuation] which now represents
	 * the reified caller of the current method).
	 *
	 * @param expectedValueOrNull
	 *   A constant type to replace the top-of-stack in the reified
	 *   continuation.  If `null`, don't replace the top-of-stack.
	 * @param typeOfEntryPoint
	 *   The kind of [ChunkEntryPoint] to re-enter at.
	 */
	fun reify(expectedValueOrNull: A_Type?, typeOfEntryPoint: ChunkEntryPoint)
	{
		// Use the current block's zone for subsequent nodes that are inside
		// this reification handler.
		val zone = currentBlock().zone
		val onReturnIntoReified = createBasicBlock(
			"Return into reified continuation",
			isCold = true)

		// Create readSlots for constructing the continuation.  Also create
		// writeSemanticValues and writeRestrictions for restoring the state
		// from the continuation when it's resumed.
		val readSlotsBefore = (1 .. numSlots).map { i ->
			if (i == stackp && expectedValueOrNull !== null)
			{
				boxedConstant(expectedValueOrNull)
			}
			else
			{
				readBoxed(semanticSlot(i))
			}
		}
		// Now generate the reification instructions, ensuring that when
		// returning into the resulting continuation it will enter a block where
		// the slot registers are the new ones we just created.
		val writeOffset = intWriteTemp(
			"resumption offset", intRestrictionForType(i32))
		val writeRegisterDump = boxedWriteTemp(
			"register dump",
			boxedRestrictionForType(Types.ANY()))
		val fallThrough = createBasicBlock("Off-ramp", zone)
		+L2_SAVE_ALL_AND_PC_TO_INT(
			ifFallThrough = edgeTo(fallThrough),
			reference = edgeTo(onReturnIntoReified),
			referenceOffset = writeOffset,
			registerDump = writeRegisterDump,
			finalSavedBoxedRegisters = L2ReadBoxedVectorOperand(emptyList()),
			dirtyLocals = L2ReadMixedVectorOperand(emptyList()),
			dirtyLocalIndices = L2ArbitraryConstantOperand(intArrayOf()))
		startBlock(fallThrough)
		// We're in a reification handler here, so the caller is guaranteed to
		// contain the reified caller.
		val writeReifiedCaller = boxedWrite(
			topFrame.reifiedCaller(),
			boxedRestrictionForType(mostGeneralContinuationType))
		+L2_GET_CURRENT_CONTINUATION(writeReifiedCaller)
		val newContinuationWrite = boxedWriteTemp(
			"new continuation",
			boxedRestrictionForType(mostGeneralContinuationType))
		if (typeOfEntryPoint === ChunkEntryPoint.TRANSIENT)
		{
			// L1 can never see this continuation, so it can be minimal.
			+L2_CREATE_CONTINUATION(
				function = currentFunction,
				code = L2ConstantOperand(code),
				caller = readBoxed(writeReifiedCaller),
				levelOnePc = L2IntImmediateOperand(Int.MAX_VALUE),
				levelOneStackp = L2IntImmediateOperand(Int.MAX_VALUE),
				slotValues = L2ReadBoxedVectorOperand(emptyList()),
				destination = newContinuationWrite,
				labelAddress = L2ReadIntOperand(
					writeOffset.onlySemanticValue(),
					intRestrictionForType(i32)),
				registerDump = readBoxed(writeRegisterDump),
				comment = L2CommentOperand(
					"Create a dummy reification continuation."))
		}
		else
		{
			// Make an L1-complete continuation, since an invalidation can cause
			// it to resume in the L2Chunk#unoptimizedChunk, which can only see
			// L1 content.
			+L2_CREATE_CONTINUATION(
				function = currentFunction,
				code = L2ConstantOperand(code),
				caller = readBoxed(writeReifiedCaller),
				levelOnePc = L2IntImmediateOperand(pc),
				levelOneStackp = L2IntImmediateOperand(stackp),
				slotValues = L2ReadBoxedVectorOperand(readSlotsBefore.toList()),
				destination = newContinuationWrite,
				labelAddress = L2ReadIntOperand(
					writeOffset.onlySemanticValue(),
					intRestrictionForType(i32)),
				registerDump = readBoxed(writeRegisterDump),
				comment = L2CommentOperand(
					"Create a reification continuation."))
		}
		+L2_RETURN_FROM_REIFICATION_HANDLER(readBoxed(newContinuationWrite))

		// Here it's returning into the reified continuation.
		startBlock(onReturnIntoReified)
		+L2_ENTER_L2_CHUNK(
			L2IntImmediateOperand(typeOfEntryPoint.offsetInDefaultChunk),
			L2CommentOperand(
				"If invalid, reenter «default» " +
					"at ${typeOfEntryPoint.name}."))
		if (expectedValueOrNull !== null && expectedValueOrNull.isVacuousType)
		{
			addUnreachableCode()
		}
	}

	/**
	 * We've reached a position in code generation where we know the current
	 * continuation (a label) is being restarted.  Generate code to strip the
	 * manifest and jump back to the [RESTART_LOOP_HEAD].
	 *
	 * @param restartArguments
	 *   The [L2ReadBoxedOperand]s providing values with which to restart the
	 *   current frame.
	 */
	fun generateRestartContinuation(
		restartArguments: List<L2ReadBoxedOperand>)
	{
		val indices = 0 ..< numArgs
		val restrictions = restartArguments.map(L2ReadBoxedOperand::restriction)
		val temps = restartArguments.indices.map { i ->
			newTemp("arg #${i+1} for restart")
		}
		val tempWrites = indices.map { i ->
			boxedWrite(temps[i], restrictions[i])
		}
		// For safety, first copy the arguments into temps.
		+L2_STRIP_MANIFEST(
			L2ReadBoxedVectorOperand(restartArguments),
			L2WriteBoxedVectorOperand(tempWrites))
		// Now copy from the temps into the arguments.
		val finalSlots = indices.map { i -> createSemanticSlot(i + 1, 1) }
		val finalWrites = indices.map { i ->
			L2WriteBoxedOperand(
				setOf(finalSlots[i]),
				restrictions[i])
		}
		+L2_STRIP_MANIFEST(
			L2ReadBoxedVectorOperand(tempWrites.map(::readBoxed)),
			L2WriteBoxedVectorOperand(finalWrites))
		val liveEntities = mutableSetOf<L2Entity<*>>()
		liveEntities.addAll(finalSlots)
		finalSlots.mapTo(liveEntities) { sv ->
			currentManifest.getDefinition(sv)
		}

		// Jump back to the RESTART_LOOP_HEAD, where only the n@1 semantic slots
		// and registers will be live and added to the phis.
		+L2_JUMP_BACK(
			backEdgeTo(
				specialBlocks[RESTART_LOOP_HEAD]!!,
				liveEntities.toMutableSet()),
			L2ReadBoxedVectorOperand(
				indices.map {
					readBoxed(createSemanticSlot(it + 1, 1))
				}))
	}

	/**
	 * Information about one invocation site, for use by polymorphic calls.
	 *
	 * @property block
	 *   The [L2BasicBlock] where code generation for the call should/did take
	 *   place.
	 * @property generateAction
	 *   The action that will generate code for the invocation.  The [block]
	 *   should be started before running the action.
	 */
	class InvocationSite(
		val block: L2BasicBlock,
		val generateAction: InvocationSite.()->Unit)
	{
		/** A safety check to ensure the action only runs once. */
		var ran = 0
	}

	/**
	 * Generate code to perform a method invocation.  If a superUnionType other
	 * than [bottom] is supplied, produce a super-directed multimethod
	 * invocation.
	 *
	 * @param bundle
	 *   The [message bundle][MessageBundleDescriptor] to invoke.
	 * @param expectedType
	 *   The expected return [type][TypeDescriptor].
	 * @param superUnionType
	 *   A tuple type to combine through a type union with the pushed arguments'
	 *   dynamic types, to use during method lookup.  This is [bottom] for
	 *   non-super calls.
	 */
	private fun generateCall(
		bundle: A_Bundle,
		expectedType: A_Type,
		superUnionType: A_Type)
	{
		val method: A_Method = bundle.bundleMethod
		addContingentValue(method)
		val nArgs = method.numArgs
		val semanticArguments =
			(stackp + nArgs - 1 downTo stackp).map(::semanticSlot)
		// Clear and pop the arguments, but push a slot for the expectedType.
		(stackp until stackp + nArgs).forEach(::nilSlot)
		stackp += nArgs - 1
		// At this point we've captured and popped the argument registers, and
		// nilled their new SSA versions for reification.  The reification
		// clauses will explicitly ensure the expected type appears in the top
		// of stack position.

		val argumentRestrictions =
			semanticArguments.map(currentManifest::restrictionFor)

		// Calculate the union of the types guaranteed to be produced by the
		// possible definitions, including analysis of primitives.  The phi
		// combining final results will produce something at least this strict.
		var tempUnion = bottom
		val reachableDefinitions =
			method.definitionsAtOrBelow(argumentRestrictions)
		for (definition in reachableDefinitions)
		{
			if (definition.isMethodDefinition())
			{
				val function = definition.bodyBlock()
				val rawFunction = function.code()
				val primitive = rawFunction.codePrimitive()
				val functionType = rawFunction.functionType()
				val functionResultType = functionType.returnType
				val returnType: A_Type = if (primitive !== null)
				{
					val signatureTupleType = functionType.argsTupleType
					val intersectedArgumentTypes = argumentRestrictions
						.mapIndexed { i, restriction ->
							restriction.intersectionWithType(
								signatureTupleType.typeAtIndex(i + 1)).type
						}
					val failedFunctionType =
						rawFunction.returnTypeIfPrimitiveFails
					val primResultType = primitive.returnTypeGuaranteedByVM(
						rawFunction, intersectedArgumentTypes)
					when (primitive.fallibilityForArgumentTypes(
						intersectedArgumentTypes))
					{
						CallSiteCannotFail -> primResultType
						CallSiteMustFail -> failedFunctionType
						else -> primResultType.typeUnion(failedFunctionType)
					}
				}
				else
				{
					functionResultType
				}
				tempUnion = tempUnion.typeUnion(returnType)
			}
		}
		val callSiteHelper = CallSiteHelper(
			this,
			bundle,
			semanticArguments,
			superUnionType,
			expectedType,
			tempUnion)

		// Special case: If there's only one method definition and the type tree
		// has not yet been expanded, go ahead and do so.  It takes less space
		// in L2/JVM to store the simple invocation than a full lookup.
		if (method.definitionsTuple.tupleSize <= 1)
		{
			val argTypes = argumentRestrictions.map(TypeRestriction::type)
			try
			{
				val result =
					method.lookupByTypesFromTuple(tupleFromList(argTypes))
				assert(result.equals(method.definitionsTuple.tupleAt(1)))
			}
			catch (_: MethodDefinitionException)
			{
				throw AssertionError(
					"Couldn't look up method by its own signature")
			}
			// The tree is now warmed up for a monomorphic inline.
		}
		// Visit the expanded parts of the tree and collect the leaves that
		// indicate a singular answer, keeping only those that are possible at
		// this call site.
		val applicableExpandedLeaves = mutableListOf<A_Definition>()
		val tree = method.testingTree
		val workList = mutableListOf(tree)
		while (workList.isNotEmpty())
		{
			when (val node = workList.removeLast())
			{
				is InternalLookupTree ->
					node.decisionStepOrNull?.simplyAddChildrenTo(workList)
				is LeafLookupTree ->
				{
					val lookupResult = node.solutionOrNull
					if (lookupResult.tupleSize != 1) continue
					val definition: A_Definition = lookupResult.tupleAt(1)
					// Only inline successful lookups.
					if (!definition.isMethodDefinition()) continue
					val signature = definition.bodySignature()
					if (signature.couldEverBeInvokedWith(argumentRestrictions)
						&& superUnionType.isSubtypeOf(signature.argsTupleType))
					{
						applicableExpandedLeaves.add(definition)
					}
				}
			}
		}
		if (applicableExpandedLeaves.toSet().size <=
			maxPolymorphismToInlineDispatch)
		{
			// Generate all the branches and corresponding target blocks.
			val edges = mutableListOf(
				Triple(
					null as L2BasicBlock?,
					tree,
					emptyList<L2SemanticBoxedValue>()))
			while (edges.isNotEmpty())
			{
				val (block, node, extraSemanticArguments) = edges.removeLast()
				if (block != null) startBlock(block)
				if (!currentlyReachable()) continue
				when (node)
				{
					is InternalLookupTree ->
					{
						when (val step = node.decisionStepOrNull)
						{
							null -> jumpTo(callSiteHelper[FallBackToSlowLookup])
							else -> edges.addAll(
								step.run {
									generateEdgesFor(
										semanticArguments,
										extraSemanticArguments,
										callSiteHelper)
								})
						}
					}
					is LeafLookupTree ->
						leafVisit(callSiteHelper, node.solutionOrNull)
				}
			}
		}
		else
		{
			// There are too many definitions, so just fall back.  DO NOT
			// cause (successful) slow lookups to trigger reoptimization.
			callSiteHelper.tooComplexToInline = true
			jumpTo(callSiteHelper[FallBackToSlowLookup])
		}
		assert(!currentlyReachable())

		// At this point, invocationSitesToCreate is fully populated with basic
		// blocks in which to generate invocations of the corresponding method
		// definition bodies.  Generate them all now, as they will lead to the
		// junctions that we'll generate in the next step.
		callSiteHelper.generateAllInvocationSites()

		// Step through the reachable junctions, generating suitable code for
		// each.  Note that junctions can lead to downstream junctions that will
		// have blocks built for them as needed.  They're fully ordered, so
		// when the downstream one gets its turn, it'll see that it has a block
		// built for it and that it's reachable.
		callSiteHelper.generateReachableJunctions()
	}

	/**
	 * A leaf lookup tree was found at this position in the inlined dispatch.
	 * If it's a singular method definition, embed a call to it, otherwise jump
	 * to the fallback lookup code to reproduce and handle lookup errors.
	 *
	 * @param callSiteHelper
	 *   The [CallSiteHelper] object for this dispatch.
	 * @param solutions
	 *   The [A_Tuple] of [A_Definition]s at this leaf of the lookup tree.  If
	 *   there's exactly one, and it's a method definition, the lookup is
	 *   considered successful, otherwise it's a failed lookup.
	 */
	private fun leafVisit(
		callSiteHelper: CallSiteHelper,
		solutions: A_Tuple)
	{
		if (!currentlyReachable())
		{
			return
		}
		if (solutions.tupleSize == 1)
		{
			val solution: A_Definition = solutions.tupleAt(1)
			if (solution.isMethodDefinition())
			{
				promiseToHandleCallForDefinitionBody(
					solution.bodyBlock(), callSiteHelper)
				return
			}
		}
		// Failed dispatches basically never happen, so jump to the fallback
		// lookup, which will do its own problem reporting.
		jumpTo(callSiteHelper[FallBackToSlowLookup])
	}

	/**
	 * A leaf lookup tree was found at this position in the inlined dispatch.
	 * If it's a singular method definition, embed a call to it, otherwise jump
	 * to the fallback lookup code to reproduce and handle lookup errors.
	 *
	 * @param function
	 *   The [A_Definition] body [A_Function] to be invoked.
	 * @param callSiteHelper
	 *   The [CallSiteHelper] object for this dispatch.
	 */
	private fun promiseToHandleCallForDefinitionBody(
		function: A_Function,
		callSiteHelper: CallSiteHelper)
	{
		var invocation = callSiteHelper.invocationSitesToCreate[function]
		if (invocation === null)
		{
			val shortName = function.code().shortMethodName
			invocation = InvocationSite(
				createBasicBlock("successful lookup: $shortName"))
			{
				// Safety check.
				assert(ran++ == 0)
				assert(!currentlyReachable())
				if (block.predecessorEdges().isNotEmpty())
				{
					startBlock(block)
					generateGeneralFunctionInvocation(
						boxedConstant(function),
						true,
						callSiteHelper,
						callSiteHelper.semanticArguments.map(
							currentManifest::readBoxed))
					assert(!currentlyReachable())
				}
			}
			callSiteHelper.invocationSitesToCreate[function] = invocation
		}
		jumpTo(invocation.block)
	}

	/**
	 * Generate code to invoke a function in a register with arguments in
	 * registers.  Also branch to the appropriate reification and return clauses
	 * depending on whether the returned value is guaranteed to satisfy the
	 * expectedType or not.
	 *
	 * The code generation position is never [L2Generator.currentlyReachable]
	 * after this (Kotlin) method completes.
	 *
	 * The final output from the entire polymorphic call will always be fully
	 * strengthened to the intersection of the VM-guaranteed type and the
	 * expectedType of the callSiteHelper, although an explicit type check may
	 * have to be generated along some paths.
	 *
	 * @param functionToCallReg
	 *   The [L2ReadBoxedOperand] containing the function to invoke.
	 * @param arguments
	 *   The [List] of [L2ReadBoxedOperand]s that supply arguments to the
	 *   function.
	 * @param tryToGenerateSpecialPrimitiveInvocation
	 *   `true` if an attempt should be made to generate a customized
	 *   [L2Instruction] sequence for a [Primitive] invocation, `false`
	 *   otherwise. This should generally be `false` only to prevent recursion
	 *   from `Primitive` customization.
	 * @param callSiteHelper
	 *   Information about the call being generated.
	 * @param willAlwaysFailPrimitive
	 *   If `true`, the primitive will definitely fail, and the fallback code of
	 *   the function will run, so use that information to strengthen the output
	 *   value.  If `false`, the default, it's not known whether the primitive
	 *   will succeed or fail, or if there even is a primitive.
	 */
	fun generateGeneralFunctionInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		tryToGenerateSpecialPrimitiveInvocation: Boolean,
		callSiteHelper: CallSiteHelper,
		arguments: List<L2ReadBoxedOperand>,
		willAlwaysFailPrimitive: Boolean = false)
	{
		assert(functionToCallReg.type().isSubtypeOf(mostGeneralFunctionType()))

		// Sanity check the number of arguments against the function.  The
		// function type's acceptable arguments tuple type may be bottom,
		// indicating the size is not known.  It may also be a singular integer
		// range (e.g., [3..3]), indicating exactly how many arguments must be
		// supplied.  If it's a variable size, then by the argument
		// contravariance rules, it would require each (not just any) of those
		// sizes on every call, which is a contradiction, although it's allowed
		// as a denormalized uninstantiable type.  For now just treat a spread
		// of sizes like bottom (i.e., the count is not known).
		val argumentCount = arguments.size
		val sizeRange = functionToCallReg.type().argsTupleType.sizeRange
		assert(
			sizeRange.isBottom
				|| !sizeRange.lowerBound.equals(sizeRange.upperBound)
				|| sizeRange.rangeIncludesLong(argumentCount.toLong()))
		val guaranteedResultType: A_Type
		val rawFunction = determineRawFunction(functionToCallReg)
		val primitive = rawFunction?.codePrimitive()
		if (primitive !== null)
		{
			val argsTupleType = rawFunction.functionType().argsTupleType
			val argumentTypes: List<A_Type>
			val generated: Boolean
			if (tryToGenerateSpecialPrimitiveInvocation)
			{
				// We are not recursing here from a primitive override of
				// tryToGenerateSpecialPrimitiveInvocation(), so try to generate
				// a special primitive invocation.  Note that this lookup was
				// monomorphic *in the event of success*, so we can safely
				// tighten the argument types here to conform to the only
				// possible found function.
				val manifest = currentManifest
				val strongArguments = (0 until argumentCount).map {
					val arg = arguments[it]
					val argSemanticValue = arg.semanticValue()
					val strongRestriction = arg.restriction()
						.intersection(manifest.restrictionFor(argSemanticValue))
						.intersectionWithType(argsTupleType.typeAtIndex(it + 1))
					manifest.setRestriction(argSemanticValue, strongRestriction)
					L2ReadBoxedOperand(argSemanticValue, strongRestriction)
				}
				// Plug in the source registers for the convenience of the
				// primitive generating code.
				strongArguments.forEach {
					it.setRegister(manifest.getDefinition(it.semanticValue()))
				}
				argumentTypes = strongArguments.map { it.type() }
				if (primitive.hasFlag(Flag.CanFold))
				{
					// See if there's an equivalent semantic value already in
					// the manifest, and just reuse that if possible.
					val semanticPrimitive = primitive.semanticInvocation(
						arguments.map(L2ReadBoxedOperand::semanticValue))
					manifest.equivalentSemanticValue(semanticPrimitive)?.let {
							equivalent ->
						moveRegister(
							equivalent, listOf(semanticPrimitive))
						callSiteHelper.useAnswer(
							readBoxed(semanticPrimitive),
							false)
						return
					}
				}
				generated = tryToGenerateSpecialInvocation(
					functionToCallReg,
					rawFunction,
					primitive,
					strongArguments,
					callSiteHelper)
			}
			else
			{
				// We are recursing here from a primitive override of
				// tryToGenerateSpecialPrimitiveInvocation(), so do not recurse
				// again; just generate the best invocation possible given what
				// we know.
				argumentTypes = arguments.mapIndexed { zeroIndex, argument ->
					argument.type().typeIntersection(
						argsTupleType.typeAtIndex(zeroIndex + 1))
				}
				if (primitive.fallibilityForArgumentTypes(argumentTypes)
					=== CallSiteCannotFail)
				{
					// The primitive cannot fail at this site. Output code
					// to run the primitive as simply as possible, feeding a
					// register with as strong a type as possible.
					var resultType = primitive.returnTypeGuaranteedByVM(
						rawFunction, argumentTypes)
					if (resultType.isBottom)
					{
						// Even though the P_InvokeWithTuple primitive can't
						// fail, the ultimately called function won't return.
						// In this case, weaken the resultType to avoid ⊥, just
						// to keep the call machinery happy.
						resultType = Types.ANY()
					}
					val writer = boxedWrite(
						primitive.semanticInvocation(
							arguments.map(L2ReadBoxedOperand::semanticValue)),
						boxedRestrictionForType(resultType))
					val invoke = L2_RUN_INFALLIBLE_PRIMITIVE.createInstruction(
						L2ConstantOperand(rawFunction),
						primitive,
						L2ReadBoxedVectorOperand(arguments),
						writer)
					+invoke
					if (willAlwaysFailPrimitive &&
						rawFunction.returnTypeIfPrimitiveFails.isBottom)
					{
						// The function will fail the primitive, and the
						// fallback is bottom-typed, so this is unreachable.
						addUnreachableCode()
						return
					}
					callSiteHelper.useAnswer(
						readBoxed(writer),
						invoke.mightMakeEscapedVariableShared())
					generated = true
				}
				else
				{
					generated = false
				}
			}
			if (generated)
			{
				assert(!currentlyReachable())
				return
			}
			// The raw function is known.  Ask the primitive what it guarantees
			// if successful, and take the union with what the raw function says
			// it'll produce if the primitive is unsuccessful.  Take into
			// account whether the primitive will never, always, or sometimes
			// fail for the given argument types.
			val fallibility = when
			{
				willAlwaysFailPrimitive -> CallSiteMustFail
				else -> primitive.fallibilityForArgumentTypes(argumentTypes)
			}
			guaranteedResultType = when (fallibility)
			{
				CallSiteCannotFail -> primitive.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes)
				CallSiteMustFail -> rawFunction.returnTypeIfPrimitiveFails
				else -> rawFunction.returnTypeIfPrimitiveFails.typeUnion(
					primitive.returnTypeGuaranteedByVM(
						rawFunction, argumentTypes))
			}
		}
		else
		{
			// Exact function was unknown, or it wasn't a primitive.
			guaranteedResultType = functionToCallReg.type().returnType
		}

		// The function isn't known to be a particular primitive function, or
		// the primitive wasn't able to generate special code for it, so just
		// invoke it like a non-primitive.
		val skipCheck =
			guaranteedResultType.isSubtypeOf(callSiteHelper.expectedType)
		val constantFunction: A_Function? = functionToCallReg.constantOrNull
		val canReturn = !guaranteedResultType.isVacuousType
		val successBlock = createBasicBlock("successful invocation")
		val targetBlock =
			when
			{
				!canReturn -> callSiteHelper[ReificationUnreturnable]
				skipCheck -> callSiteHelper[ReificationNoCheck]
				else -> callSiteHelper[ReificationWithCheck]
			}
		val reificationTarget = createBasicBlock(
			"invoke reification target", targetBlock.zone, isCold = true)
		val writeResult = writeSlot(
			stackp,
			if (skipCheck) pc else pc - 1,
			boxedRestrictionForType(
				if (guaranteedResultType.isBottom) Types.ANY() // unreachable
				else guaranteedResultType))
		val unreachable = L2BasicBlock("unreachable", isCold = true)
		if (constantFunction !== null)
		{
			+L2_INVOKE_CONSTANT_FUNCTION(
				L2ConstantOperand(constantFunction),
				L2ReadBoxedVectorOperand(arguments),
				writeResult,
				edgeTo(if (canReturn) successBlock else unreachable),
				edgeTo(reificationTarget))
		}
		else
		{
			+L2_INVOKE(
				functionToCallReg,
				L2ReadBoxedVectorOperand(arguments),
				writeResult,
				edgeTo(if (canReturn) successBlock else unreachable),
				edgeTo(reificationTarget))
		}
		startBlock(unreachable)
		+L2_UNREACHABLE_CODE()

		startBlock(reificationTarget)
		+L2_ENTER_L2_CHUNK(
			L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			L2CommentOperand("Transient - cannot be invalid."))
		jumpTo(targetBlock)

		startBlock(successBlock)
		if (currentlyReachable())
		{
			jumpTo(
				if (skipCheck) callSiteHelper[AfterCallNoCheckTestEscapes]
				else callSiteHelper[AfterCallWithCheckTestEscapes])
			assert(!currentlyReachable())
		}
	}

	/**
	 * Generate code to perform a type check of the top-of-stack register
	 * against the given expectedType (an [A_Type] that has been strengthened by
	 * semantic restrictions).
	 *
	 * If the check fails, invoke the out-of-line ⊥-valued helper method
	 * [Interpreter.reportWrongReturnType], passing enough information for it to
	 * be able to construct a reified continuation if required.
	 *
	 * If the check passes, the value will be strengthened in the top-of-stack
	 * register.
	 *
	 * It's incorrect to call this if the register's type is already strong
	 * enough to satisfy the expectedType.
	 *
	 * @param expectedType
	 *   The [A_Type] to check the value against.
	 */
	fun generateReturnTypeCheck(expectedType: A_Type)
	{
		// The unchecked return value is associated with the nybble just before
		// the instruction after the call (which takes at least three nybbles).
		val semanticValue = createSemanticSlot(stackp, pc - 1)
		val uncheckedValueRead = currentManifest.readBoxed(semanticValue)
		if (uncheckedValueRead.type().isVacuousType)
		{
			// There are no return values possible, so we can't get here.  It
			// would be wrong to do this based on the expectedType being bottom,
			// since that's only an erroneous semantic restriction, not a VM
			// problem.
			// NOTE that this test terminates a mutual recursion between this
			// method and generateGeneralFunctionInvocation().
			addUnreachableCode()
			return
		}

		// Check the return value against the expectedType.
		val passedCheck = createBasicBlock("passed return check")
		val failedCheck = createBasicBlock(
			"failed return check",
			ZoneType.DEAD_END.createZone("failed check"),
			isCold = true)
		if (!uncheckedValueRead.restriction().intersectsType(expectedType))
		{
			// It's impossible to return a valid value here, since the value's
			// type bound and the expected type don't intersect.  Always invoke
			// the bad type handler.
			jumpTo(failedCheck)
		}
		else
		{
			assert(!uncheckedValueRead.type().isSubtypeOf(expectedType))
				{ "Attempting to create unnecessary type check" }
			jumpIfKindOfConstant(
				uncheckedValueRead, expectedType, passedCheck, failedCheck)
		}

		// The type check failed, so report it.
		startBlock(failedCheck)
		if (currentlyReachable())
		{
			// Save the semantic slots, since flushing locals writes to them,
			// and we don't want the success path to be affected – because we're
			// emitting a dead end.
			val oldSlots = semanticSlots.clone()
			+L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION(
				uncheckedValueRead,
				L2ConstantOperand(expectedType),
				L2IntImmediateOperand(pc),
				L2IntImmediateOperand(stackp),
				L2ReadBoxedVectorOperand(
					(1..numSlots).map {
						when (it)
						{
							// Make it look like the expectedType has been
							// pushed.
							stackp -> boxedConstant(expectedType)
							else -> readSlot(it)
						}
					}))
			// Restore the semantic slots so the success path is unaffected.
			System.arraycopy(oldSlots, 0, semanticSlots, 0, oldSlots.size)
		}
		assert(!currentlyReachable())

		// Generate the much more likely passed-check flow.
		startBlock(passedCheck)
		if (currentlyReachable())
		{
			forceSlotRegister(
				stackp,
				pc,
				uncheckedValueRead.semanticValue(),
				uncheckedValueRead.restriction().intersection(
					boxedRestrictionForType(expectedType)))
		}
	}

	/**
	 * Attempt to create a more specific instruction sequence than just an
	 * [L2_INVOKE].  In particular, see if the `functionToCallReg` is known to
	 * contain a constant function (a common case) which is an inlineable
	 * primitive, and if so, delegate this opportunity to the primitive.
	 *
	 * We must either answer `false` and generate no code, or answer `true` and
	 * generate code that has the same effect as having run the function in the
	 * register without fear of reification or abnormal control flow.  A folded
	 * primitive, for example, can generate a simple [L2_MOVE_CONSTANT] into the
	 * top-of-stack register and answer true.
	 *
	 * @param functionToCallReg
	 *   The register containing the [function][A_Function] to invoke.
	 * @param rawFunction
	 *   The [raw function][A_RawFunction] being invoked.
	 * @param primitive
	 *   The [Primitive] being invoked.
	 * @param arguments
	 *   The arguments to supply to the function.
	 * @param callSiteHelper
	 *   Information about the method call site having its dispatch tree
	 *   inlined.  It also contains merge points for this call, so if a specific
	 *   code generation happens it should jump to one of these.
	 * @return
	 *   `true` if a special instruction sequence was generated, `false`
	 *   otherwise.
	 */
	private fun tryToGenerateSpecialInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		primitive: Primitive,
		arguments: List<L2ReadBoxedOperand>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val argumentCount = arguments.size
		if (primitive.hasFlag(Flag.CanFold))
		{
			// It can be folded, if supplied with constants.
			val constants = mutableListOf<AvailObject>()
			for (regRead in arguments)
			{
				val constant = regRead.constantOrNull ?: break
				constants.add(constant)
			}
			if (constants.size == argumentCount)
			{
				// Fold the primitive.  A foldable primitive must not
				// require access to the enclosing function or its code.
				val savedFunction = interpreter.function
				interpreter.function = null
				val savedDebugModeString = interpreter.debugModeString
				if (Interpreter.debugL2)
				{
					log(
						Interpreter.loggerDebugL2,
						Level.FINER,
						"{0}FOLD {1}:",
						interpreter.debugModeString,
						primitive.name)
				}
				val success: Primitive.Result = try
				{
					interpreter.argsBuffer.clear()
					interpreter.argsBuffer.addAll(constants)
					primitive.attempt(interpreter)
				}
				finally
				{
					interpreter.debugModeString = savedDebugModeString
					interpreter.function = savedFunction
				}
				if (success === Primitive.Result.SUCCESS)
				{
					callSiteHelper.useAnswer(
						boxedConstant(
							interpreter.getLatestResult().makeImmutable()),
						false)
					return true
				}
				assert(success === Primitive.Result.FAILURE)
				assert(!primitive.hasFlag(Flag.CannotFail))
			}
		}

		// The primitive can't be folded, so let it generate its own code
		// equivalent to invocation.
		val signatureTupleType = rawFunction.functionType().argsTupleType
		val narrowedArgTypes = mutableListOf<A_Type>()
		val narrowedArguments = mutableListOf<L2ReadBoxedOperand>()
		for (i in 0 until argumentCount)
		{
			val argument = readBoxed(arguments[i].semanticValue())
			// Preserve the source registers that were provided.
			argument.setRegister(arguments[i].register())
			assert(
				argument.restriction().type.isSubtypeOf(
					signatureTupleType.typeAtIndex(i + 1)))
			narrowedArgTypes.add(argument.restriction().type)
			narrowedArguments.add(argument)
		}
		// Let the primitive generate specialized code if possible.
		var generated = primitive.run {
			tryToGenerateSpecialPrimitiveInvocation(
				functionToCallReg,
				rawFunction,
				narrowedArguments,
				narrowedArgTypes,
				callSiteHelper)
		}
		if (!generated)
		{
			// Try a general infallible invocation, if possible.
			generated = primitive.run {
				tryToGenerateGeneralPrimitiveInvocation(
					rawFunction,
					narrowedArguments,
					narrowedArgTypes,
					callSiteHelper)
			}
		}
		if (generated)
		{
			// We don't allow the primitive generation to leave the generator at
			// a reachable point, since the callSiteHelper was supposed to be
			// told when a result was produced.
			assert(!currentlyReachable())
		}
		return generated
	}

	/**
	 * Generate a slower, but much more compact invocation of a polymorphic
	 * method call.  The slots have already been adjusted to be consistent with
	 * having popped the arguments and pushed the expected type.
	 *
	 * @param callSiteHelper
	 *   Information about the method call site.
	 */
	fun generateSlowPolymorphicCall(
		callSiteHelper: CallSiteHelper)
	{
		val bundle = callSiteHelper.bundle
		val method: A_Method = bundle.bundleMethod
		val nArgs = method.numArgs
		val lookupSucceeded = createBasicBlock(
			"lookup succeeded for " + callSiteHelper.quotedBundleName)
		val lookupFailed = createBasicBlock(
			"lookup failed for " + callSiteHelper.quotedBundleName,
			ZoneType.DEAD_END.createZone("lookup failed"),
			isCold = true)
		val argumentRestrictions =
			callSiteHelper.semanticArguments.mapIndexed { i, arg ->
				boxedRestrictionForType(
					callSiteHelper.superUnionType.typeAtIndex(i + 1)
				).union(currentManifest.restrictionFor(arg))
		}
		val possibleFunctions = bundle.bundleMethod
			.definitionsAtOrBelow(argumentRestrictions)
			.filter { it.isMethodDefinition() }
			.map { it.bodyBlock() }
		val functionTypeUnion =
			enumerationWith(setFromCollection(possibleFunctions))
		val argumentReads =
			callSiteHelper.semanticArguments.map(currentManifest::readBoxed)

		// At some point we might want to introduce a SemanticValue for tagging
		// this register.
		if (functionTypeUnion.isBottom)
		{
			// There were no possible method definitions, so jump immediately to
			// the lookup failure clause.  Don't generate the success case.
			// For consistency, generate a jump to the lookupFailed exit point,
			// then generate it immediately.
			jumpTo(lookupFailed)
			startBlock(lookupFailed)
			fallbackToL1AndRedoCall(argumentReads)
			return
		}
		// It doesn't necessarily always fail, so try a lookup.
		val functionWrite = boxedWriteTemp(
			"looked-up function",
			boxedRestrictionForType(functionTypeUnion))
		if (!callSiteHelper.isSuper)
		{
			// Not a super-call.
			+L2_LOOKUP_BY_VALUES(
				L2ConstantOperand(bundle),
				L2ReadBoxedVectorOperand(argumentReads),
				L2ArbitraryConstantOperand(!callSiteHelper.tooComplexToInline),
				functionWrite,
				edgeTo(lookupSucceeded),
				edgeTo(lookupFailed))
		}
		else
		{
			// Extract a tuple type from the runtime types of the arguments,
			// take the type union with the superUnionType, then perform a
			// lookup-by-types using that tuple type.
			val argTypeRegs = mutableListOf<L2ReadBoxedOperand>()
			for (i in 1 .. nArgs)
			{
				val argReg = argumentReads[i - 1]
				val argStaticType = argReg.type()
				val superUnionElementType =
					callSiteHelper.superUnionType.typeAtIndex(i)
				val argTypeReg: L2ReadBoxedOperand =
					if (argStaticType.isSubtypeOf(superUnionElementType))
					{
						// The lookup is entirely determined by the super-union.
						boxedConstant(superUnionElementType)
					}
					else
					{
						val typeBound =
							argStaticType.typeUnion(superUnionElementType)
						val argTypeWrite = boxedWriteTemp(
							"lookup arg type",
							boxedRestrictionForType(instanceMeta(typeBound)))
						if (superUnionElementType.isBottom)
						{
							// Only this argument's actual type matters.
							+L2_GET_TYPE(argReg, argTypeWrite)
						}
						else
						{
							// The lookup is constrained by the actual
							// argument's type *and* the super-union.  This is
							// possible because this is a top-level argument,
							// but it's the leaf arguments that individually
							// specify supercasts.
							val originalArgTypeWrite =
								boxedWriteTemp(
									"original arg type",
									boxedRestrictionForType(
										instanceMeta(typeBound)))
							+L2_GET_TYPE(argReg, originalArgTypeWrite)
							+L2_TYPE_UNION(
								readBoxed(originalArgTypeWrite),
								boxedConstant(
									superUnionElementType),
								argTypeWrite)
						}
						readBoxed(argTypeWrite)
					}
				argTypeRegs.add(argTypeReg)
			}
			+L2_LOOKUP_BY_TYPES(
				L2ConstantOperand(bundle),
				L2ReadBoxedVectorOperand(argTypeRegs),
				functionWrite,
				edgeTo(lookupSucceeded),
				edgeTo(lookupFailed))
		}
		// At this point, we've attempted to look up the method, and either
		// jumped to lookupSucceeded with functionWrite set to the body
		// function, or jumped to lookupFailed with errorCodeWrite set to
		// the lookup error code.

		// Emit the lookup failure case.
		startBlock(lookupFailed)
		fallbackToL1AndRedoCall(argumentReads)

		// Now invoke the method definition's body.  If any of the possible
		// definition bodies being invoked requires a result check, *always*
		// perform the result check.
		startBlock(lookupSucceeded)
		generateGeneralFunctionInvocation(
			readBoxed(functionWrite),
			true,
			callSiteHelper,
			argumentReads)
	}

	/**
	 * Generate code to report a lookup failure.
	 *
	 * We could handle the failed lookup inline by accessing the
	 * [AvailRuntime.invalidMessageSendFunction] and then invoking it, catching
	 * reification, etc., but this is very, very cold code.  Fall out to L1 and
	 * let it attempt the call again, since nothing has been modified at this
	 * point.
	 *
	 * @param argumentReads
	 *   The source [L2ReadBoxedVectorOperand]s supplying arguments.
	 */
	fun fallbackToL1AndRedoCall(
		argumentReads: List<L2ReadBoxedOperand>)
	{
		if (!currentlyReachable()) return
		// Recreate the content of the stack with the arguments still present.
		val originalStackp = stackp - argumentReads.size + 1
		assert(originalStackp == stackpOfCurrentInstruction)

		// Watch out for the List zero-based indexing here.
		val restoredSemanticSlots =
			(1..numSlots).mapTo(mutableListOf(), ::semanticSlot)

		restoredSemanticSlots
			.subList(originalStackp - 1, stackp)
			.let { argsArea ->
				argumentReads
					.map(L2ReadBoxedOperand::semanticValue)
					.forEachIndexed(argsArea::set)
			}
		if (argumentReads.isEmpty())
		{
			// There were no arguments, but we still pushed the expected type
			// for the call.  There couldn't have been anything in that slot,
			// so nil it.  Adjust for zero-based index.
			restoredSemanticSlots[originalStackp - 1] =
				boxedConstant(nil).semanticValue()
		}
		+L2_FALL_BACK_TO_L1(
			L2IntImmediateOperand(pcOfCurrentInstruction),
			L2IntImmediateOperand(originalStackp),
			L2ReadBoxedVectorOperand(restoredSemanticSlots.map(::readBoxed)))
	}

	/**
	 * Emit code to check for an interrupt and service it if necessary,
	 * including generation of the subsequently continued on-ramp.  The
	 * generated code should only be reachable at positions that are effectively
	 * between L1 nybblecodes, since during such an interrupt any [L2Chunk]s can
	 * be invalidated.  Not *all* positions between nybblecodes need to check
	 * for interrupts, but there shouldn't be an arbitrarily large amount of
	 * time that passes between when an interrupt is indicated and when it is
	 * serviced.
	 */
	private fun emitInterruptOffRamp()
	{
		val serviceInterrupt = createBasicBlock(
			"service interrupt",
			isCold = true)
		val merge = createBasicBlock("merge after possible interrupt")
		+L2_JUMP_IF_INTERRUPT(
			edgeTo(serviceInterrupt),
			edgeTo(merge))
		startBlock(serviceInterrupt)
		// Service the interrupt:  Generate the reification instructions,
		// ensuring that when returning into the resulting continuation, it will
		// enter a block where the slot registers are the new ones we just
		// created.  After creating the continuation, actually service the
		// interrupt.

		// Reify everybody else, starting at the caller.
		val onReification = createBasicBlock(
			"On reification for interrupt",
			ZoneType.BEGIN_REIFICATION_FOR_INTERRUPT.createZone(
				"Start reification and run interrupt"),
			isCold = true)
		+L2_REIFY(
			L2IntImmediateOperand(1),
			L2ConstantOperand(nil),
			edgeTo(onReification))
		startBlock(onReification)
		+L2_ENTER_L2_CHUNK(
			L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			L2CommentOperand(
				"Transient, for interrupt - cannot be invalid."))

		// When the lambda below runs, it's generating code at the point where
		// continuationReg will have the new continuation.
		reify(null, ChunkEntryPoint.TO_RESUME)
		jumpTo(merge)
		// Merge the flow (reified and continued, versus not reified).
		startBlock(merge)
		// And now... either we're back or we never left.
	}

	/**
	 * Emit the specified variable-reading instruction, and an off-ramp to deal
	 * with the case that the variable is unassigned.  Leave the value in the
	 * top stack slot if the event that the read was successful, and continue
	 * code generation on the success path.
	 *
	 * @param clearMode
	 *   The [GetClearMode] that controls under what circumstance the variable
	 *   should be cleared and/or the extracted value should be made immutable.
	 * @param variable
	 *   The location of the [variable][A_Variable].
	 * @param type
	 *   The type of value that is to be read.
	 * @param destination
	 *   The optional [L2SemanticBoxedValue] into which to write the result.
	 *   If omitted or null, write to the top of stack at the current pc, and
	 *   update the current semantic slot at that stack pointer.
	 */
	fun emitGetVariableOffRamp(
		clearMode: GetClearMode,
		variable: L2ReadBoxedOperand,
		type: A_Type,
		destination: L2SemanticBoxedValue? = null)
	{
		val value = destination ?: createSemanticSlot(stackp, pc)
		val write = boxedWrite(value, boxedRestrictionForType(type))

		val success = createBasicBlock("successfully read variable")
		val rw = when (clearMode)
		{
			GetClearMode.NeverClear -> "read"
			GetClearMode.AlwaysClear -> "read and clear"
			GetClearMode.ClearIfMutable -> "read and optionally clear"
		}
		val failure = createBasicBlock(
			"failed to $rw variable",
			ZoneType.DEAD_END.createZone("failed $rw"),
			isCold = true)

		// Emit the specified get-variable instruction variant.
		+clearMode.createInstruction(
			variable, write, edgeTo(success), edgeTo(failure))

		startBlock(failure)
		+L2_FALL_BACK_TO_L1(
			L2IntImmediateOperand(pcOfCurrentInstruction),
			L2IntImmediateOperand(stackpOfCurrentInstruction),
			L2ReadBoxedVectorOperand(semanticSlots.map(::readBoxed)))
		assert(!currentlyReachable())

		// Continue generation along the success path.
		startBlock(success)
		destination ?: forceSlot(stackp, value)
	}

	/**
	 * Emit a local-variable-reading instruction, and an off-ramp to deal with
	 * the case that the variable is unassigned.  Leave the value in the top
	 * stack slot if the event that the read was successful, and continue code
	 * generation on the success path.  Also update the semantic slot holding
	 * that variable, to ensure causally correct access.
	 *
	 * @param localSlotIndex
	 *   The slot index of the local to read.
	 * @param clear
	 *   Whether the variale should also be cleared.
	 */
	fun emitGetUnescapedLocalVariableAndOffRamp(
		localSlotIndex: Int,
		clear: Boolean)
	{
		val extractedValue = createSemanticSlot(stackp, pc)
		val variable = semanticSlot(localSlotIndex)
		val variableRestriction = currentManifest.restrictionFor(variable)
		val variableOut = createSemanticSlot(localSlotIndex, pc)
		forceSlot(localSlotIndex, variableOut)

		val success = createBasicBlock("successfully read variable")
		val failure = createBasicBlock(
			"failed to read variable",
			ZoneType.DEAD_END.createZone("failed read"),
			isCold = true)

		// Emit the specified get-variable instruction variant.
		if (clear)
		{
			+L2_GET_AND_CLEAR_UNESCAPED_LOCAL_VARIABLE(
				frame = L2ArbitraryConstantOperand(topFrame),
				variable = readBoxed(variable),
				variableOut = boxedWrite(variableOut, variableRestriction),
				extractedValue = boxedWrite(
					extractedValue,
					boxedRestrictionForType(variableRestriction.type.readType)),
				ifReadSucceeded = edgeTo(success),
				ifReadFailed = edgeTo(failure))
		}
		else
		{
			+L2_GET_UNESCAPED_LOCAL_VARIABLE(
				frame = L2ArbitraryConstantOperand(topFrame),
				variable = readBoxed(variable),
				variableOut = boxedWrite(variableOut, variableRestriction),
				extractedValue = boxedWrite(
					extractedValue,
					boxedRestrictionForType(variableRestriction.type.readType)),
				ifReadSucceeded = edgeTo(success),
				ifReadFailed = edgeTo(failure))
		}
		startBlock(failure)
		if (currentlyReachable())
		{
			+L2_FALL_BACK_TO_L1(
				L2IntImmediateOperand(pcOfCurrentInstruction),
				L2IntImmediateOperand(stackpOfCurrentInstruction),
				L2ReadBoxedVectorOperand(semanticSlots.map(::readBoxed)))
		}
		assert(!currentlyReachable())

		// Continue generation along the success path.
		startBlock(success)
		forceSlot(stackp, extractedValue)
	}

	/**
	 * Emit the specified variable-writing instruction, and an off-ramp to deal
	 * with the case that the variable has
	 * [write-reactors][VariableAccessReactor] but variable write
	 * [tracing][Interpreter.traceVariableWrites] is disabled.
	 *
	 * @param variable
	 *   The location of the [variable][A_Variable].
	 * @param newValue
	 *   The location of the new value.
	 */
	private fun emitSetVariableOffRamp(
		variable: L2ReadBoxedOperand,
		newValue: L2ReadBoxedOperand)
	{
		val success = createBasicBlock("set local success")
		val failure = createBasicBlock(
			"set local failure/observe",
			isCold = true)
		val onReificationDuringFailure = createBasicBlock(
			"reify during set local failure",
			ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
				"Continue reification for set-variable failure handler"),
			isCold = true)
		// Emit the set-variable instruction.
		+L2_SET_VARIABLE_NO_CHECK(
			variable,
			newValue,
			edgeTo(success),
			edgeTo(failure))

		// Emit the failure path.
		startBlock(failure)
		val observeFunction = boxedWriteTemp(
			"observeFun",
			boxedRestrictionForType(HookType.IMPLICIT_OBSERVE.functionType))
		+L2_GET_IMPLICIT_OBSERVE_FUNCTION(observeFunction)
		val variableAndValueTupleReg = boxedWriteTemp(
			"var/val pair",
			boxedRestrictionForType(
				tupleTypeForTypes(variable.type(), newValue.type())))
		+L2_CREATE_TUPLE(
			L2ReadBoxedVectorOperand(listOf(variable, newValue)),
			variableAndValueTupleReg)
		// Note: the handler block's value is discarded; also, since it's not a
		// method definition, it can't have a semantic restriction.
		+L2_INVOKE(
			readBoxed(observeFunction),
			L2ReadBoxedVectorOperand(
				listOf(
					boxedConstant(assignmentFunction()),
					readBoxed(variableAndValueTupleReg))),
			// Unreachable:
			boxedWriteTemp(
				"unreachable value", anyRestriction),
			edgeTo(success),
			edgeTo(onReificationDuringFailure))
		startBlock(onReificationDuringFailure)
		+L2_ENTER_L2_CHUNK(
			L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			L2CommentOperand(
				"Transient - cannot be invalid."))
		reify(Types.TOP(), ChunkEntryPoint.TO_RETURN_INTO)
		jumpTo(success)
		// End with the success block.  Note that the failure path can lead here
		// if the implicit-observe function returns.
		startBlock(success)
		// We may have written something into this non-local variable that
		// caused one of our locals to become shared.
		emitCheckLocals(
			true,
			"after emit setvariable offramp (${variable.semanticValue()})")
	}

	override fun toString(): String =
		"${javaClass.simpleName} ($generator.debugName)"

	/**
	 * For each level one instruction, write a suitable transliteration into
	 * level two instructions.
	 */
	private fun translateL1Instructions()
	{
		// The [L2BasicBlock] which is the entry point for a function that has
		// just been invoked.
		val startBlock = createBasicBlock("START for ${topFrame.codeName}")
		startBlock.makeIrremovable()
		specialBlocks[START] = startBlock
		startBlock(startBlock)
		val primitive = code.codePrimitive()
		if (primitive !== null)
		{
			// Try the primitive, automatically returning if successful.
			+L2_TRY_PRIMITIVE(L2ArbitraryConstantOperand(primitive))
			if (primitive.hasFlag(Flag.CannotFail))
			{
				// Infallible primitives don't need any other L2 code.
				return
			}
		}
		val afterPrimitive = createLoopHeadBlock("After optional primitive")
		afterPrimitive.makeIrremovable()
		specialBlocks[AFTER_OPTIONAL_PRIMITIVE] = afterPrimitive
		jumpTo(afterPrimitive)
		startBlock(afterPrimitive)
		// While it's true that invalidation may only take place when no Avail
		// code is running (even when evicting old chunks), and it's also the
		// case that invalidation causes the chunk to be disconnected from its
		// compiled code, it's still the case that a continuation (a label, say)
		// created at an earlier time still refers to the invalid chunk.  Ensure
		// it can fall back gracefully to L1 (the default chunk) by entering it
		// at the TO_RESTART entry point.  Note that there can't be a primitive
		// for such continuations.
		// Capture the arguments, but don't consume them, in case the
		// decrement-and-reoptimize has to create and run a different chunk.
		val tupleType = code.functionType().argsTupleType
		+L2_ENTER_L2_CHUNK_FOR_CALL(
			L2CommentOperand(
				"If invalid, reenter «default» at the beginning."),
			L2WriteBoxedVectorOperand(
				(1..numArgs).map { i ->
					boxedWrite(
						semanticSlot(i),
						boxedRestrictionForType(tupleType.typeAtIndex(i)))
				}))
		// Insulate the jump to the loop head, so that the original registers
		// won't be used.  Note that the same semantic values are being written,
		// but strip-manifest clears the whole manifest before adding them, so
		// they won't interfere with the ones populated above.
		+L2_STRIP_MANIFEST(
			L2ReadBoxedVectorOperand((1..numArgs).map(::readSlot)),
			L2WriteBoxedVectorOperand((1..numArgs).map { i ->
				boxedWrite(
					semanticSlot(i),
					boxedRestrictionForType(tupleType.typeAtIndex(i)))
			}))

		// Do any reoptimization before capturing arguments.
		val optimization = optimizationLevel
		val newCountdown = optimization.countdown
		code.countdownToReoptimize(newCountdown)
		// Optimize it again if it's called frequently enough.
		+L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO(
			L2IntImmediateOperand(optimization.ordinal),
			L2IntImmediateOperand(0))
		// If it was reoptimized, it would have jumped to the
		// afterOptionalInitialPrimitiveBlock in the new chunk.

		// Here's where a local P_RestartContinuationWithArguments is optimized
		// to jump to. It's expected to place the replacement arguments into
		// semantic slots n@1.
		val loopHead = createLoopHeadBlock(
			"Loop head for " + code.methodName.asNativeString())
		specialBlocks[RESTART_LOOP_HEAD] = loopHead
		jumpTo(loopHead)
		startBlock(loopHead)

		// Let the interrupt routine see postponed variables for each local
		// variable, to avoid having to slide them through that part of the
		// graph.  We'll set them to new variables right after the interrupt has
		// been serviced and resumed safely (from a continuation that isn't
		// shared or immutable).
		val elidedLocalPlaceholders = (1..numLocals).map { localindex ->
			newPlaceholder(code.localTypeAt(localindex), localindex)
		}
		for (localIndex in 1 .. numLocals)
		{
			forceConstantSlot(
				numArgs + localIndex,
				elidedLocalPlaceholders[localIndex - 1])
		}
		var startOfConstantsToClear = numArgs + numLocals + 1
		primitive?.let {
			// Capture the primitive failure value in the first local constant.
			assert(!primitive.hasFlag(Flag.CannotFail))
			val failureType = primitive.failureVariableType
			moveBoxedRegister(
				getLatestReturnValue(
					"failure code", failureType).semanticValue(),
				writeSlot(
					startOfConstantsToClear++,
					pc,
					boxedRestrictionForType(failureType)
				).semanticValues())
		}

		// Clear the rest of the stack slots.
		(startOfConstantsToClear .. numSlots).forEach(::nilSlot)

		// Check for interrupts.  If an interrupt is discovered, then reify and
		// process the interrupt.  If the reified continuation becomes immutable
		// or shared, all missing locals will be created and it will resume in
		// L1, not here.  If it was still mutable, however, all registers will
		// be restored and it will continue this chunk.
		emitInterruptOffRamp()

		// Finally, create the remaining local variables.  These creation
		// instructions will migrate through the graph during optimization,
		// allowing some to be elided entirely.
		for (localIndex in 1 .. numLocals)
		{
			val localType = code.localTypeAt(localIndex)
			+L2_CREATE_VARIABLE(
				localIndex = L2IntImmediateOperand(localIndex),
				outerType = L2ConstantOperand(localType),
				variable = writeSlot(
					numArgs + localIndex,
					pc,
					boxedRestrictionForType(localType)),
				initialValueOrNil = boxedConstant(nil),
				constantVariableIfElided =
					L2ConstantOperand(elidedLocalPlaceholders[localIndex - 1]))
		}

		val nybblecodeMap = mutableMapOf<Int, String>()
		L1Disassembler(code).printInstructions(
			IdentityHashMap<A_BasicObject, Unit>(10), 0
		) { pc, line, string -> nybblecodeMap[pc] = "$pc. [:$line] $string" }

		// Transliterate each level one nybblecode into L2Instructions.
		while (!instructionDecoder.atEnd() && currentlyReachable())
		{
			pcOfCurrentInstruction = instructionDecoder.pc
			stackpOfCurrentInstruction = stackp
			val operation = instructionDecoder.getOperation()
			+L2_NOP(L2CommentOperand(nybblecodeMap[pcOfCurrentInstruction]!!))
			operation.dispatch(this)
		}

		// Generate the implicit return after the instruction sequence.
		if (currentlyReachable())
		{
			val readResult = readSlot(stackp)
			+L2_RETURN(readResult)
			assert(stackp == numSlots)
			stackp = Int.MIN_VALUE
		}
	}

	/**
	 * Translate the supplied [A_RawFunction] into a sequence of
	 * [L2Instruction]s.  The optimization level specifies how hard to try to
	 * optimize this method.  It is roughly equivalent to the level of inlining
	 * to attempt, or the ratio of code expansion that is permitted. An
	 * optimization level of zero is the bare minimum, which produces a naïve
	 * translation to [Level&#32;Two&#32;code][L2Chunk].  The translation may
	 * include code to decrement a counter and reoptimize with greater effort
	 * when the counter reaches zero.
	 *
	 * Install Answer the created [L2Chunk].
	 *
	 * @return
	 *   The created [L2Chunk].
	 */
	private fun translate(): L2Chunk
	{
		val beforeL1Naive = AvailRuntimeSupport.captureNanos()
		translateL1Instructions()
		translateL1Stat.record(
			AvailRuntimeSupport.captureNanos() - beforeL1Naive,
			interpreter.interpreterIndex)
		val optimizer = L2Optimizer(generator)
		optimizer.optimize(interpreter)
		val beforeChunkGeneration = AvailRuntimeSupport.captureNanos()
		val chunk = createChunk(code)

		optimizer.postOptimizationCleanup()  // Remove to debug.

		L2Generator.finalGenerationStat.record(
			AvailRuntimeSupport.captureNanos() - beforeChunkGeneration,
			interpreter.interpreterIndex)
		return chunk
	}

	override fun L1_doCall()
	{
		val bundle = code.literalAt(instructionDecoder.getOperand())
		val expectedType = code.literalAt(instructionDecoder.getOperand())

		generateCall(bundle, expectedType, bottom)
		// Now we're after the call.  Along every path here, each local was
		// either checked and moved into a new semantic slot, or just moved. The
		// semanticSlots array must not have changed.  Now update it to use
		// those new semantic slots.
		if (currentlyReachable())
		{
			(numArgs + 1 .. numArgs + numLocals).forEach { slotIndex ->
				currentManifest
					.restrictionFor(semanticSlot(slotIndex))
					.constantOrNull
					?: run {
						val newSlot = createSemanticSlot(slotIndex, pc)
						assert(currentManifest.hasSemanticValue(newSlot))
						forceSlot(slotIndex, newSlot)
					}
			}
		}
	}

	override fun L1_doPushLiteral()
	{
		val constant = code.literalAt(instructionDecoder.getOperand())
		stackp--
		moveConstantToSlot(constant, stackp)
	}

	override fun L1_doPushLastLocal()
	{
		val slotIndex = instructionDecoder.getOperand()
		stackp--
		forceSlotRegister(stackp, pc, readSlot(slotIndex))
		// Even if this is a local variable, we don't have to worry about slot
		// versioning to maintain causality, because no L1 code after here will
		// access that slot.
		nilSlot(slotIndex)
	}

	override fun L1_doPushLocal()
	{
		val slotIndex = instructionDecoder.getOperand()
		stackp--
		forceSlotRegister(stackp, pc, readSlot(slotIndex))
		if (slotIndex in numArgs + 1 .. numArgs + numLocals)
		{
			// We're pushing a local variable.  Update the slot versioning to
			// ensure the variable continues to be accessed causally.  Any L1
			// instructions after this point will use the new semantic value.
			val newSlot = createSemanticSlot(slotIndex, pc)
			moveBoxedRegister(
				semanticSlot(slotIndex), listOf(newSlot))
			forceSlot(slotIndex, newSlot)
		}
	}

	override fun L1_doPushLastOuter()
	{
		val outerIndex = instructionDecoder.getOperand()
		val outerType = code.outerTypeAt(outerIndex)
		stackp--
		// For now, simplify the logic related to L1's nilling of mutable outers
		// upon their final use.  Just make it immutable instead.
		forceSlotRegister(
			stackp,
			pc,
			getOuterRegister(outerIndex, outerType))
	}

	override fun L1_doClose()
	{
		val count = instructionDecoder.getOperand()
		val codeLiteral: A_RawFunction = code.literalAt(
			instructionDecoder.getOperand())
		val outers = (stackp + count - 1 downTo stackp).map(::readSlot)
		// Pop the outers, but reserve room for the pushed function.
		stackp += count - 1
		+L2_CREATE_FUNCTION(
			L2ConstantOperand(codeLiteral),
			L2ReadBoxedVectorOperand(outers),
			writeSlot(
				stackp,
				pc,
				boxedRestrictionForType(codeLiteral.functionType())))

		// Now that the function has been constructed, clear the slots that
		// were used for outer values -- except the destination slot, which
		// is being overwritten with the resulting function anyhow.
		(stackp + 1 - count until stackp).forEach(::nilSlot)
	}

	override fun L1_doSetLocal()
	{
		// Output an [L2_VIRTUAL_SET_VARIABLE] instruction, which is free to be
		// postponed by an optimization pass.
		val slotIndex = instructionDecoder.getOperand()
		// Note that L1 raw functions statically guarantee that the value will
		// always satisfy a local variable's content type.
		+L2_SET_UNESCAPED_LOCAL_VARIABLE(
			readSlot(slotIndex),
			readSlot(stackp),
			writeSlot(
				slotIndex,
				pc,
				boxedRestrictionForType(
					code.localTypeAt(slotIndex - numArgs))))
		nilSlot(stackp)
		stackp++
	}

	override fun L1_doGetLocalClearing()
	{
		val slotIndex = instructionDecoder.getOperand()
		stackp--
		emitGetUnescapedLocalVariableAndOffRamp(slotIndex, true)
	}

	override fun L1_doPushOuter()
	{
		val outerIndex = instructionDecoder.getOperand()
		val outerType = code.outerTypeAt(outerIndex)
		stackp--
		forceSlotRegister(
			stackp,
			pc,
			getOuterRegister(outerIndex, outerType))
	}

	override fun L1_doPop()
	{
		nilSlot(stackp)
		stackp++
	}

	override fun L1_doGetLastOuter()
	{
		val outerIndex = instructionDecoder.getOperand()
		stackp--
		val outerType = code.outerTypeAt(outerIndex)
		emitGetVariableOffRamp(
			ClearIfMutable,
			getOuterRegister(outerIndex, outerType),
			outerType.readType)
	}

	override fun L1_doSetOuter()
	{
		val outerIndex = instructionDecoder.getOperand()
		val outerType = code.outerTypeAt(outerIndex)
		val tempVarReg = getOuterRegister(outerIndex, outerType)
		val valueReg = readSlot(stackp)
		// Nil the stack slot that held the value.
		nilSlot(stackp)
		emitSetVariableOffRamp(tempVarReg, valueReg)
		// Nil the top-of-stack slot *again*, which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		nilSlot(stackp)
		// Pop the slot that held the value to assign.
		stackp++
	}

	override fun L1_doGetLocal()
	{
		val slotIndex = instructionDecoder.getOperand()
		stackp--
		emitGetUnescapedLocalVariableAndOffRamp(slotIndex, false)
	}

	override fun L1_doMakeTuple()
	{
		val count = instructionDecoder.getOperand()
		val elements = (stackp + count - 1 downTo stackp).map(::readSlot)
		(stackp + count - 2 downTo stackp).forEach(::nilSlot)
		stackp += count - 1
		// Fold into a constant tuple if possible.
		val tupleRead = createTuple(elements)
		forceSlotRegister(stackp, pc, tupleRead)
	}

	override fun L1_doGetOuter()
	{
		val outerIndex = instructionDecoder.getOperand()
		stackp--
		val outerType = code.outerTypeAt(outerIndex)
		emitGetVariableOffRamp(
			NeverClear,
			getOuterRegister(outerIndex, outerType),
			outerType.readType)
	}

	override fun L1_doExtension() = unsupported

	override fun L1Ext_doPushLabel()
	{
		// Use L2_VIRTUAL_CREATE_LABEL to simplify code motion in the common
		// case that label creation can be postponed into an off-ramp (which is
		// rarely invoked).  Since a label requires its caller to be reified,
		// creating it in an off-ramp is trivial, since the caller will already
		// have been reified by the StackReifier machinery.
		//
		// After code motion, the L2_VIRTUAL_CREATE_LABEL instruction will be
		// replaced by code to force reification of the caller (or do nothing if
		// it has migrated into an off-ramp), and then create the label using
		// the up-to-date caller.  Since label building only preserves the
		// frame's function and arguments, only those are used by the virtual
		// instruction.  The label continuation's pc will be 0, and its stack
		// will be empty.
		assert(code.codePrimitive() == null)
		val semanticLabel = topFrame.label()
		if (currentManifest.hasSemanticValue(semanticLabel))
		{
			// Reuse a label that was computed for an earlier L1 pushLabel.
		}
		else
		{
			val argumentsForLabel = mutableListOf<L2ReadBoxedOperand>()
			for (i in 1..numArgs)
			{
				argumentsForLabel.add(readSlot(i))
			}
			val continuationType =
				continuationTypeForFunctionType(code.functionType())
			val destinationRegister = boxedWrite(
				semanticLabel, restriction(continuationType, null))
			+L2_VIRTUAL_CREATE_LABEL(
				destinationRegister,
				currentFunction,
				L2ConstantOperand(code),
				L2ReadBoxedVectorOperand(argumentsForLabel),
				L2IntImmediateOperand(code.numSlots))
		}
		// Now push the label.
		stackp--
		forceSlotRegister(
			stackp,
			pc,
			currentManifest.readBoxed(semanticLabel))
	}

	override fun L1Ext_doGetLiteral()
	{
		val literalVariable: A_Variable = code.literalAt(
			instructionDecoder.getOperand())
		stackp--
		if (literalVariable.isInitializedWriteOnceVariable
			&& literalVariable.valueWasStablyComputed)
		{
			// It's an initialized module constant, so it can never change,
			// *and* the value was computed only via stable steps from other
			// stable values.  Use the variable's eternal value.  If we allowed
			// an unstable constant value to avoid triggering a get, we wouldn't
			// properly detect the access to an unstable value, so a new module
			// constant might not notice that its value was actually computed
			// from unstable values, and accidentally mark itself as stably
			// computed.  That would break the fast-loader optimization.
			moveConstantToSlot(literalVariable.value(), stackp)
		}
		else
		{
			emitGetVariableOffRamp(
				NeverClear,
				boxedConstant(literalVariable),
				literalVariable.kind().readType)
		}
	}

	override fun L1Ext_doSetLiteral()
	{
		val literalVariable: A_Variable = code.literalAt(
			instructionDecoder.getOperand())
		emitSetVariableOffRamp(
			boxedConstant(literalVariable),
			readSlot(stackp))
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		nilSlot(stackp)
		stackp++
		// We just wrote to a shared variable.  Check if any local has become
		// shared (or had reactors added somehow).
		emitCheckLocals(true, "after set literal")
	}

	override fun L1Ext_doDuplicate()
	{
		val source = readSlot(stackp)
		stackp--
		forceSlotRegister(stackp, pc, source)
	}

	override fun L1Ext_doPermute()
	{
		val permutation: A_Tuple =
			code.literalAt(instructionDecoder.getOperand())
		val size = permutation.tupleSize
		val temps = arrayOfNulls<L2SemanticBoxedValue>(size)
		// Read each semantic slot to be permuted.
		permutation.forEachIndexed { i, value ->
			temps[value.extractInt - 1] =
				semanticSlot(stackp + size - i - 1)
		}
		// Replace them with the permuted semantic values that were just read.
		for (i in 1..size)
		{
			forceSlotRegister(
				stackp + size - i,
				pc,
				currentManifest.readBoxed(temps[i - 1]!!))
		}
	}

	override fun L1Ext_doSuperCall()
	{
		val bundle: A_Bundle =
			code.literalAt(instructionDecoder.getOperand())
		val expectedType: A_Type =
			code.literalAt(instructionDecoder.getOperand())
		val superUnionType: A_Type =
			code.literalAt(instructionDecoder.getOperand())
		generateCall(bundle, expectedType, superUnionType)
		// Now we're after the (super) call.  Along every path here, each local
		// was either checked and moved into a new semantic slot, or just moved.
		// The semanticSlots array must not have changed.  Now update it to use
		// those new semantic slots.
		(numArgs + 1 .. numArgs + numLocals).forEach { slotIndex ->
			forceSlot(slotIndex, createSemanticSlot(slotIndex, pc))
		}
	}

	override fun L1Ext_doSetLocalSlot()
	{
		val destinationIndex = instructionDecoder.getOperand()
		val source = readSlot(stackp)
		forceSlotRegister(destinationIndex, pc, source)
		nilSlot(stackp)
		stackp++
	}

	companion object
	{
		/**
		 * Determine if the given [A_RawFunction]'s instantiations as
		 * [A_Function]s must be mutually equal.
		 *
		 * @param theCode
		 *   The [A_RawFunction].
		 * @return
		 *   Either a canonical [A_Function] or `null`.
		 */
		private fun computeExactFunctionOrNullForCode(
			theCode: A_RawFunction): A_Function?
		{
			val numOuters = theCode.numOuters
			val outerConstants = mutableListOf<AvailObject>()
			for (i in 1 .. numOuters)
			{
				val outerType = theCode.outerTypeAt(i)
				if (!outerType.instanceCount.equalsInt(1)
					|| outerType.isInstanceMeta)
				{
					return null
				}
				outerConstants.add(outerType.instance)
			}
			// This includes the case of there being no outers.
			return createFunction(theCode, tupleFromList(outerConstants))
		}

		/**
		 * Generate the [L2ControlFlowGraph] of [L2Instruction]s for the
		 * [unoptimizedChunk].
		 *
		 * @param initialBlock
		 *   The block to initially entry the default chunk for a call.
		 * @param reenterFromRestartBlock
		 *   The block to reenter to [P_RestartContinuation] an [A_Continuation].
		 * @param loopBlock
		 *   The main loop of the interpreter.
		 * @param reenterFromCallBlock
		 *   The entry point for returning into a reified continuation.
		 * @param reenterFromInterruptBlock
		 *   The entry point for resuming from an interrupt.
		 * @param unreachableBlock
		 *   A basic block that should be dynamically unreachable.
		 * @return
		 *   The [L2ControlFlowGraph] for the default chunk.
		 */
		fun generateDefaultChunkControlFlowGraph(
			initialBlock: L2BasicBlock,
			reenterFromRestartBlock: L2BasicBlock,
			loopBlock: L2BasicBlock,
			reenterFromCallBlock: L2BasicBlock,
			reenterFromInterruptBlock: L2BasicBlock,
			unreachableBlock: L2BasicBlock
		): L2ControlFlowGraph
		{
			initialBlock.makeIrremovable()
			loopBlock.makeIrremovable()
			reenterFromRestartBlock.makeIrremovable()
			reenterFromCallBlock.makeIrremovable()
			reenterFromInterruptBlock.makeIrremovable()
			unreachableBlock.makeIrremovable()
			val generator = L2Generator(
				"Default",
				UNOPTIMIZED,
				Frame(null, nil, -1, "default", "top frame"),
				GenerationMode.ByRegister)
			generator.run {
				// 0. First try to run it as a primitive.
				startBlock(initialBlock)
				+L2_TRY_OPTIONAL_PRIMITIVE()
				jumpTo(reenterFromRestartBlock)
				// Only if the primitive fails should we even consider
				// optimizing the fallback code.

				// 1. Update counter and maybe optimize *before* extracting
				// arguments.
				startBlock(reenterFromRestartBlock)
				+L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO(
					L2IntImmediateOperand(UNOPTIMIZED.ordinal),
					L2IntImmediateOperand(1))
				// 2. Build registers, get arguments, create locals, capture
				// primitive failure value, if any.
				+L2_PREPARE_NEW_FRAME_FOR_L1()
				jumpTo(loopBlock)

				// 3. The main L1 interpreter loop.
				startBlock(loopBlock)
				+L2_INTERPRET_LEVEL_ONE(
					edgeTo(reenterFromCallBlock),
					edgeTo(reenterFromInterruptBlock))

				// 4,5. If reified, calls return here.
				startBlock(reenterFromCallBlock)
				+L2_REENTER_L1_CHUNK_FROM_CALL()
				+L2_JUMP(backEdgeTo(loopBlock, mutableSetOf()))

				// 6,7. If reified, interrupts return here.
				startBlock(reenterFromInterruptBlock)
				+L2_REENTER_L1_CHUNK_FROM_INTERRUPT()
				+L2_JUMP(backEdgeTo(loopBlock, mutableSetOf()))

				// 8. Unreachable.
				startBlock(unreachableBlock)
				+L2_UNREACHABLE_CODE()
			}
			return generator.controlFlowGraph
		}

		/** Statistics about the naive L1 to L2 translation. */
		private val translateL1Stat = Statistic(
			L2_OPTIMIZATION_TIME, "L1 naive translation")

		/**
		 * Translate the provided [A_RawFunction] to produce an optimized
		 * [L2Chunk] that is then written back into the code for subsequent
		 * executions.  Also update the [Interpreter]'s chunk and offset to use
		 * this new chunk right away.  If the code was a primitive, make sure to
		 * adjust the offset to just beyond its [L2_TRY_PRIMITIVE] instruction,
		 * which must have *already* been attempted and failed for us to have
		 * reached the [L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO] that caused
		 * this optimization to happen.
		 *
		 * The "🌼" in the name is an eye catcher.  The line having this flower
		 * is the one that should be restarted in the debugger after fixing a
		 * debugger problem and reloading classes (if possible) in the running
		 * process.
		 *
		 * @param code
		 *   The [A_RawFunction] to optimize.
		 * @param optimizationLevel
		 *   How much optimization to attempt.
		 * @param interpreter
		 *   The [Interpreter] used for folding expressions, and to be updated
		 *   with the new chunk and post-primitive offset.
		 */
		fun `🌼translateToLevelTwo`(
			code: A_RawFunction,
			optimizationLevel: OptimizationLevel,
			interpreter: Interpreter)
		{
			val savedFunction = interpreter.function
			val savedArguments = interpreter.argsBuffer.toList()
			val savedFailureValue = interpreter.latestResultOrNull()
			val codeName = buildString {
				append(code.methodName.asNativeString())
				val module = code.module
				if (module.notNil)
				{
					append("\n")
					append(module.shortModuleNameNative)
					val line = code.codeStartingLineNumber
					if (line != 0)
					{
						append(":$line")
					}
				}
			}
			val generator = L2Generator(
				codeName.replace('\n', ' '),
				optimizationLevel,
				Frame(null, code, -1, codeName, "top frame"),
				GenerationMode.BySemanticValue)
			val translator = L1Translator(generator, interpreter, code)
			translator.translate()
			interpreter.function = savedFunction
			interpreter.argsBuffer.clear()
			interpreter.argsBuffer.addAll(savedArguments)
			savedFailureValue ?: interpreter.clearLatestResult()
			savedFailureValue?.let(interpreter::setLatestResult)
		}
	}
}
