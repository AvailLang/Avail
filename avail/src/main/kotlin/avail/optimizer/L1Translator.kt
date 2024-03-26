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

import avail.AvailRuntime.HookType
import avail.AvailRuntimeSupport
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.message
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
import avail.descriptor.functions.A_RawFunction.Companion.startingChunk
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
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
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
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import avail.dispatch.InternalLookupTree
import avail.dispatch.LeafLookupTree
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import avail.exceptions.MethodDefinitionException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import avail.interpreter.Primitive.Flag
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.assignmentFunction
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelOne.L1OperationDispatcher
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk.ChunkEntryPoint
import avail.interpreter.levelTwo.L2JVMChunk.Companion.unoptimizedChunk
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2SelectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restriction
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_CREATE_FUNCTION
import avail.interpreter.levelTwo.operation.L2_CREATE_TUPLE
import avail.interpreter.levelTwo.operation.L2_CREATE_VARIABLE
import avail.interpreter.levelTwo.operation.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK_FOR_CALL
import avail.interpreter.levelTwo.operation.L2_GET_CURRENT_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_GET_CURRENT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_GET_IMPLICIT_OBSERVE_FUNCTION
import avail.interpreter.levelTwo.operation.L2_GET_INVALID_MESSAGE_SEND_FUNCTION
import avail.interpreter.levelTwo.operation.L2_GET_LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.interpreter.levelTwo.operation.L2_GET_VARIABLE
import avail.interpreter.levelTwo.operation.L2_GET_VARIABLE_CLEARING
import avail.interpreter.levelTwo.operation.L2_INTERPRET_LEVEL_ONE
import avail.interpreter.levelTwo.operation.L2_INVOKE
import avail.interpreter.levelTwo.operation.L2_INVOKE_CONSTANT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_INVOKE_UNASSIGNED_VARIABLE_READ_FUNCTION
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_INTERRUPT
import avail.interpreter.levelTwo.operation.L2_LOOKUP_BY_TYPES
import avail.interpreter.levelTwo.operation.L2_LOOKUP_BY_VALUES
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_MOVE_OUTER_VARIABLE
import avail.interpreter.levelTwo.operation.L2_PREPARE_NEW_FRAME_FOR_L1
import avail.interpreter.levelTwo.operation.L2_REENTER_L1_CHUNK_FROM_CALL
import avail.interpreter.levelTwo.operation.L2_REENTER_L1_CHUNK_FROM_INTERRUPT
import avail.interpreter.levelTwo.operation.L2_REIFY
import avail.interpreter.levelTwo.operation.L2_REIFY.StatisticCategory
import avail.interpreter.levelTwo.operation.L2_RETURN
import avail.interpreter.levelTwo.operation.L2_RETURN_FROM_REIFICATION_HANDLER
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.operation.L2_SET_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_SET_VARIABLE_NO_CHECK
import avail.interpreter.levelTwo.operation.L2_TRY_OPTIONAL_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_TRY_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_TYPE_UNION
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.optimizer.L2ControlFlowGraph.ZoneType
import avail.optimizer.L2Generator.Companion.backEdgeTo
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2GeneratorInterface.SpecialBlock.AFTER_OPTIONAL_PRIMITIVE
import avail.optimizer.L2GeneratorInterface.SpecialBlock.RESTART_LOOP_HEAD
import avail.optimizer.L2GeneratorInterface.SpecialBlock.START
import avail.optimizer.L2GeneratorInterface.SpecialBlock.UNREACHABLE
import avail.optimizer.OptimizationLevel.UNOPTIMIZED
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import avail.performance.Statistic
import avail.performance.StatisticReport.L1_NAIVE_TRANSLATION_TIME
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import avail.performance.StatisticReport.L2_TRANSLATION_VALUES
import avail.utility.removeLast
import java.util.concurrent.atomic.AtomicReference
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
 *   The [raw&#32;function][CompiledCodeDescriptor] to transliterate into level two
 *   code.
 * @constructor
 * Create a new L1 naive translator for the given [L2Generator].
 *
 * @param generator
 *   The [L2Generator] for which I'm producing an initial translation from L1.
 * @param interpreter
 *   The [Interpreter] that tripped the translation request.
 * @param code
 *   The [A_RawFunction] which is the source of the chunk being created.
 */
class L1Translator private constructor(
	val generator: L2Generator,
	private val interpreter: Interpreter,
	val code: A_RawFunction
) : L1OperationDispatcher
{
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
			.subList(0, code.numArgs() + code.numLocals + code.numConstants)
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
		Array(numSlots) { createSemanticSlot(it + 1, 1) }

	/**
	 * The current level one nybblecode position during naive translation to
	 * level two.
	 */
	val instructionDecoder = L1InstructionDecoder().also { decoder ->
		code.setUpInstructionDecoder(decoder)
		decoder.pc(1)
	}

	/**
	 * The current stack depth during naive translation to level two.
	 */
	var stackp: Int = numSlots + 1

	/**
	 * The exact function that we're translating, if known.  This is only
	 * non-null if the function captures no outers.
	 */
	private val exactFunctionOrNull: A_Function? =
		computeExactFunctionOrNullForCode(code)

	/**
	 * Return the top [Frame] for code generation.
	 *
	 * @return
	 *   The top [Frame],
	 */
	private fun topFrame(): Frame = generator.topFrame

	/**
	 * The current [L2ValueManifest], which tracks which [L2Synonym]s hold which
	 * [L2SemanticValue]s at the current code generation point.
	 */
	val currentManifest: L2ValueManifest
		get() = generator.currentManifest

	/**
	 * Get the program counter for the next instruction to be decoded.
	 */
	val pc: Int get() = instructionDecoder.pc()

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
		generator.readBoxed(semanticSlot(slotIndex))

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
		restriction: TypeRestriction): L2WriteBoxedOperand
	{
		// Create a new semantic slot at the current pc, representing this
		// newly written value.
		val semanticValue = createSemanticSlot(slotIndex, effectivePc)
		semanticSlots[slotIndex - 1] = semanticValue
		return generator.boxedWrite(semanticValue, restriction)
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
		generator.moveRegister(
			L2_MOVE.boxed,
			sourceSemanticValue,
			setOf(slotSemanticValue))
		currentManifest.setRestriction(slotSemanticValue, restriction)
	}

	/**
	 * Write nil into a new register representing the specified continuation
	 * slot.  The slots are the arguments, then the locals, then the stack
	 * entries.  The slots are numbered starting at 1.
	 *
	 * @param slotIndex
	 *   The one-based index into the virtual continuation's slots.
	 */
	private fun nilSlot(slotIndex: Int)
	{
		moveConstantToSlot(nil, slotIndex)
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
		generator.readBoxed(write)

	/**
	 * Write instructions to extract the current function, and answer an
	 * [L2ReadBoxedOperand] for the register that will hold the function
	 * afterward.
	 */
	private val currentFunction: L2ReadBoxedOperand
		get()
		{
			val semanticFunction = topFrame().function()
			if (currentManifest.hasSemanticValue(semanticFunction))
			{
				// Note the current function can't ever be an int or float.
				return generator.readBoxed(semanticFunction)
			}
			// We have to get it into a register.
			if (exactFunctionOrNull !== null)
			{
				// The exact function is known.
				return generator.boxedConstant(exactFunctionOrNull)
			}
			// The exact function isn't known, but we know the raw function, so
			// we statically know the function type.
			val restriction = boxedRestrictionForType(code.functionType())
			val functionWrite =
				generator.boxedWrite(semanticFunction, restriction)
			addInstruction(L2_GET_CURRENT_FUNCTION, functionWrite)
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
		val semanticOuter = topFrame().outer(outerIndex, outerName)
		if (currentManifest.hasSemanticValue(semanticOuter))
		{
			return generator.readBoxed(semanticOuter)
		}
		if (outerType.instanceCount.equalsInt(1)
			&& !outerType.isInstanceMeta)
		{
			// The exact outer is known statically.
			return generator.boxedConstant(outerType.instance)
		}
		val functionRead = currentFunction
		var restriction = boxedRestrictionForType(outerType)
		if (functionRead.restriction().isImmutable)
		{
			// An immutable function has immutable captured outers.
			restriction = restriction.withFlag(IMMUTABLE_FLAG)
		}
		val outerWrite = generator.boxedWrite(semanticOuter, restriction)
		addInstruction(
			L2_MOVE_OUTER_VARIABLE,
			L2IntImmediateOperand(outerIndex),
			functionRead,
			outerWrite)
		return readBoxed(outerWrite)
	}

	/**
	 * Capture the latest value returned by the [L2_RETURN] instruction in
	 * this [Interpreter].
	 *
	 * @param guaranteedType
	 *   The type the return value is guaranteed to conform to.
	 * @return
	 *   An [L2ReadBoxedOperand] that now holds the returned value.
	 */
	private fun getLatestReturnValue(guaranteedType: A_Type): L2ReadBoxedOperand
	{
		val writer = generator.boxedWriteTemp(
			boxedRestrictionForType(guaranteedType))
		addInstruction(L2_GET_LATEST_RETURN_VALUE, writer)
		return readBoxed(writer)
	}

	/**
	 * Create and add an [L2Instruction] with the given [L2Operation] and
	 * variable number of [L2Operand]s.
	 *
	 * @param operation
	 *   The operation to invoke.
	 * @param operands
	 *   The operands of the instruction.
	 */
	fun addInstruction(operation: L2Operation, vararg operands: L2Operand)
	{
		generator.addInstruction(operation, *operands)
	}

	/**
	 * Add an [L2Instruction].
	 *
	 * @param instruction
	 *   The instruction to add.
	 */
	fun addInstruction(instruction: L2Instruction)
	{
		generator.addInstruction(instruction)
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
			slotIndex, pc, generator.boxedConstant(value))
	}

	/**
	 * Generate code to create the current continuation, with a nil caller, then
	 * [L2_RETURN_FROM_REIFICATION_HANDLER] – so the calling frames will also
	 * get a chance to add their own nil-caller continuations to the current
	 * [StackReifier].  The execution machinery will then assemble the chain of
	 * continuations, connecting them to any already reified continuations in
	 * the interpreter.
	 *
	 * After reification, the interpreter's next activity depends on the flags
	 * set in the [StackReifier] (which was created via code generated prior to
	 * this clause).  If it was for interrupt processing, the continuation will
	 * be stored in the fiber while an interrupt is processed, then most likely
	 * resumed at a later time.  If it was for getting into a state suitable for
	 * creating an L1 label, the top continuation's chunk is resumed
	 * immediately, whereupon the continuation will be popped and exploded back
	 * into registers, and the actual label will be created from the
	 * continuation that was just resumed.
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
		val zone = generator.currentBlock().zone
		val newContinuationWrite = generator.boxedWriteTemp(
			boxedRestrictionForType(mostGeneralContinuationType))
		val onReturnIntoReified = generator.createBasicBlock(
			"Return into reified continuation",
			isCold = true)

		// Create readSlots for constructing the continuation.  Also create
		// writeSemanticValues and writeRestrictions for restoring the state
		// from the continuation when it's resumed.
		val readSlotsBefore = (0 ..< numSlots).map { i ->
			val semanticValue = semanticSlot(i + 1)
			if (i + 1 == stackp && expectedValueOrNull !== null)
			{
				generator.boxedConstant(expectedValueOrNull)
			}
			else
			{
				generator.readBoxed(semanticValue)
			}
		}
		// Now generate the reification instructions, ensuring that when
		// returning into the resulting continuation it will enter a block where
		// the slot registers are the new ones we just created.
		val writeOffset = generator.intWriteTemp(intRestrictionForType(i32))
		val writeRegisterDump = generator.boxedWriteTemp(
			boxedRestrictionForType(Types.ANY.o))
		val fallThrough = generator.createBasicBlock("Off-ramp", zone)
		addInstruction(
			L2_SAVE_ALL_AND_PC_TO_INT,
			edgeTo(onReturnIntoReified),
			writeOffset,
			writeRegisterDump,
			edgeTo(fallThrough))
		generator.startBlock(fallThrough)
		// We're in a reification handler here, so the caller is guaranteed to
		// contain the reified caller.
		val writeReifiedCaller = generator.boxedWrite(
			topFrame().reifiedCaller(),
			boxedRestrictionForType(mostGeneralContinuationType))
		addInstruction(
			L2_GET_CURRENT_CONTINUATION,
			writeReifiedCaller)
		val unreachable = L2BasicBlock("unreachable")
		if (typeOfEntryPoint === ChunkEntryPoint.TRANSIENT)
		{
			// L1 can never see this continuation, so it can be minimal.
			addInstruction(
				L2_CREATE_CONTINUATION,
				currentFunction,
				generator.readBoxed(writeReifiedCaller),
				L2IntImmediateOperand(Int.MAX_VALUE),
				L2IntImmediateOperand(Int.MAX_VALUE),
				L2ReadBoxedVectorOperand(emptyList()),
				newContinuationWrite,
				generator.readInt(writeOffset.onlySemanticValue(), unreachable),
				generator.readBoxed(writeRegisterDump),
				L2CommentOperand(
					"Create a dummy reification continuation."))
		}
		else
		{
			// Make an L1-complete continuation, since an invalidation can cause
			// it to resume in the L2Chunk#unoptimizedChunk, which can only see
			// L1 content.
			addInstruction(
				L2_CREATE_CONTINUATION,
				currentFunction,
				readBoxed(writeReifiedCaller),
				L2IntImmediateOperand(pc),
				L2IntImmediateOperand(stackp),
				L2ReadBoxedVectorOperand(readSlotsBefore.toList()),
				newContinuationWrite,
				generator.readInt(writeOffset.onlySemanticValue(), unreachable),
				generator.readBoxed(writeRegisterDump),
				L2CommentOperand("Create a reification continuation."))
		}
		addInstruction(
			L2_SET_CONTINUATION,
			generator.readBoxed(newContinuationWrite))

		// Right after creating the continuation.
		addInstruction(L2_RETURN_FROM_REIFICATION_HANDLER)

		generator.startBlock(unreachable)
		generator.addInstruction(L2_UNREACHABLE_CODE)

		// Here it's returning into the reified continuation.
		generator.startBlock(onReturnIntoReified)
		addInstruction(
			L2_ENTER_L2_CHUNK,
			L2IntImmediateOperand(typeOfEntryPoint.offsetInDefaultChunk),
			L2CommentOperand(
				"If invalid, reenter «default» at ${typeOfEntryPoint.name}."))
		if (expectedValueOrNull !== null && expectedValueOrNull.isVacuousType)
		{
			generator.addUnreachableCode()
		}
	}

	/**
	 * A helper that aggregates parameters for polymorphic dispatch inlining.
	 *
	 * @property bundle
	 *   The [A_Bundle] being dispatched
	 * @property superUnionType
	 *   Bottom in the normal case, but for a super-call this is a tuple type
	 *   with the same size as the number of arguments.  For the purpose of
	 *   looking up the appropriate [A_Definition], the type union of each
	 *   argument's dynamic type and the corresponding entry type from this
	 *   field is computed, and that's used for the lookup.
	 * @property expectedType
	 *   The type expected to be returned by invoking the function.  This may
	 *   be stronger than the type guaranteed by the VM, which requires a
	 *   runtime check.
	 *
	 * @constructor
	 * Create the helper, constructing basic blocks that may or may not be
	 * ultimately generated, depending on whether they're reachable.
	 *
	 * @param bundle
	 *   The [A_Bundle] being invoked.
	 * @param superUnionType
	 *   The type whose union with the arguments tuple type is used for lookup.
	 *   This is ⊥ for ordinary calls, and other types for super calls.
	 * @param expectedType
	 *   The expected result type that has been strengthened by
	 *   [A_SemanticRestriction]s at this call site.  The VM does not always
	 *   guarantee this type will be returned, but it inserts runtime checks in
	 *   the case that it can't prove it.
	 */
	inner class CallSiteHelper internal constructor(
		val bundle: A_Bundle,
		val superUnionType: A_Type,
		val expectedType: A_Type)
	{
		/** A Java [String] naming the [A_Bundle]. */
		val quotedBundleName = bundle.message.atomName.asNativeString()

		/** A counter for generating unique branch names for this dispatch. */
		var branchLabelCounter = 1

		/** Whether this call site is a super lookup. */
		val isSuper = !superUnionType.isBottom

		/** Where to jump to perform the slow lookup. */
		val onFallBackToSlowLookup = generator.createBasicBlock(
			"fall back to slow lookup during $quotedBundleName",
			isCold = true)

		/**
		 * Where to jump to perform reification, eventually leading to a return
		 * type check after completion.
		 */
		val onReificationWithCheck = generator.createBasicBlock(
			"reify with check during $quotedBundleName",
			ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
				"Continue reification leading to return check"),
			isCold = true)

		/**
		 * Where to jump to perform reification without the need for an eventual
		 * return type check.
		 */
		val onReificationNoCheck = generator.createBasicBlock(
			"reify no check during $quotedBundleName",
			ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
				"Continue reification without return check"),
			isCold = true)

		/**
		 * Where to jump to perform reification during a call that cannot ever
		 * return.
		 */
		val onReificationUnreturnable = generator.createBasicBlock(
			"reify unreturnable $quotedBundleName",
			ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
				"Continue reification for unreturnable"),
			isCold = true)

		/**
		 * Where to jump after a completed call to perform a return type check.
		 */
		val afterCallWithCheck = generator.createBasicBlock(
			if (isSuper) "after super call with check of $quotedBundleName"
			else "after call with check of $quotedBundleName")

		/**
		 * Where to jump after a completed call if a return type check isn't
		 * needed.
		 */
		val afterCallNoCheck = generator.createBasicBlock(
			if (isSuper) "after super no-check call of $quotedBundleName"
			else "after call no-check of $quotedBundleName")

		/**
		 * Where it ends up after the entire call, regardless of whether the
		 * returned value had to be checked or not.
		 */
		val afterEverything = generator.createBasicBlock(
			if (isSuper) "after entire super call of $quotedBundleName"
			else "after entire call of $quotedBundleName")

		/**
		 * A map from each reachable looked-up [A_Function] to a [Pair]
		 * containing an [L2BasicBlock] in which code generation for invocation
		 * of this function should/did take place, and a lambda which
		 * will cause that code generation to happen.
		 *
		 * This construct theoretically deals with method lookups that lead
		 * to the same function multiple ways (it's unclear if the lookup tree
		 * mechanism will ever evolve to produce this situation), but more
		 * practically, it can de-duplicate a successful inlined lookup and the
		 * success path of the fall-back slow lookup *when it knows there is
		 * only one particular definition that a successful slow lookup could
		 * produce.*
		 */
		val invocationSitesToCreate =
			mutableMapOf<A_Function, Pair<L2BasicBlock, ()->Unit>>()

		/**
		 * Answer the [L1Translator] that this [CallSiteHelper] is within.
		 */
		val translator: L1Translator get() = this@L1Translator

		/**
		 * Answer the [L2Generator] that this [CallSiteHelper] is within.
		 */
		val generator: L2Generator get() = this@L1Translator.generator

		/**
		 * Record the fact that this call has produced a value in a particular
		 * register which is to represent the new top-of-stack value.
		 *
		 * @param answerReg
		 *   The register which will already hold the return value at this
		 *   point.  The value has not yet been type checked against the
		 *   expectedType at this point, but it should comply with the type
		 *   guarantees of the VM.
		 */
		fun useAnswer(answerReg: L2ReadBoxedOperand)
		{
			val answerType = answerReg.type()
			when
			{
				answerType.isBottom ->
				{
					// The VM says we can't actually get here.  Don't bother
					// associating the return value with either the checked or
					// unchecked return result L2SemanticSlot.
					generator.addUnreachableCode()
				}
				answerType.isSubtypeOf(expectedType) ->
				{
					// Capture it as the checked value L2SemanticSlot.
					forceSlotRegister(stackp, pc, answerReg)
					generator.jumpTo(afterCallNoCheck)
				}
				else ->
				{
					// Capture it as the unchecked return value SemanticSlot by
					// using pc - 1.
					forceSlotRegister(stackp, pc - 1, answerReg)
					generator.jumpTo(afterCallWithCheck)
				}
			}
		}

		/**
		 * For every [L2BasicBlock] in my [invocationSitesToCreate] that is
		 * reachable, generate an invocation of the corresponding [A_Function].
		 * [A_Bundle.message]
		 */
		fun generateAllInvocationSites()
		{
			invocationSitesToCreate.values.forEach {
				(_, action) -> action()
			}
		}
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
		val callSiteHelper =
			CallSiteHelper(bundle, superUnionType, expectedType)
		val method: A_Method = bundle.bundleMethod
		generator.addContingentValue(method)
		val nArgs = method.numArgs
		val semanticArguments = (nArgs - 1 downTo 0).map { i ->
			semanticSlot(stackp + i).also {
				// No point nilling the first argument, since it'll be
				// overwritten below with a constant move of the expectedType.
				if (i != nArgs - 1) moveConstantToSlot(nil, stackp + i)
			}
		}
		// Pop the arguments, but push a slot for the expectedType.
		stackp += nArgs - 1
		// At this point we've captured and popped the argument registers, and
		// nilled their new SSA versions for reification.  The reification
		// clauses will explicitly ensure the expected type appears in the top
		// of stack position.

		// Determine which applicable definitions have already been expanded in
		// the lookup tree.
		val tree = method.testingTree
		val argumentRestrictions =
			semanticArguments.map(currentManifest::restrictionFor)

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
			catch (e: MethodDefinitionException)
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
			L2Generator.maxPolymorphismToInlineDispatch)
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
				if (block != null) generator.startBlock(block)
				if (!generator.currentlyReachable()) continue
				when (node)
				{
					is InternalLookupTree ->
					{
						when (val step = node.decisionStepOrNull)
						{
							null -> generator.jumpTo(
								callSiteHelper.onFallBackToSlowLookup)
							else -> edges.addAll(
								step.generateEdgesFor(
									semanticArguments,
									extraSemanticArguments,
									callSiteHelper))
						}
					}
					is LeafLookupTree -> leafVisit(
						semanticArguments, callSiteHelper, node.solutionOrNull)
				}
			}
		}
		else
		{
			// Always fall back.
			generator.jumpTo(callSiteHelper.onFallBackToSlowLookup)
		}
		assert(!generator.currentlyReachable())

		// Calculate the union of the types guaranteed to be produced by the
		// possible definitions, including analysis of primitives.  The phi
		// combining final results will produce something at least this strict.
		var tempUnion = bottom
		for (definition in method.definitionsAtOrBelow(argumentRestrictions))
		{
			if (definition.isMethodDefinition())
			{
				val function = definition.bodyBlock()
				val rawFunction = function.code()
				val primitive = rawFunction.codePrimitive()
				val returnType: A_Type = if (primitive !== null)
				{
					val signatureTupleType =
						rawFunction.functionType().argsTupleType
					val intersectedArgumentTypes = mutableListOf<A_Type>()
					for (i in argumentRestrictions.indices)
					{
						val intersection =
							argumentRestrictions[i].intersectionWithType(
								signatureTupleType.typeAtIndex(i + 1))
						intersectedArgumentTypes.add(intersection.type)
					}
					primitive.returnTypeGuaranteedByVM(
						rawFunction, intersectedArgumentTypes)
				}
				else
				{
					rawFunction.functionType().returnType
				}
				tempUnion = tempUnion.typeUnion(returnType)
			}
		}
		val unionOfPossibleResults = tempUnion

		// Now generate the reachable exit clauses for:
		//    1. default lookup,
		//    2. reification leading to return check,
		//    3. reification with no check,
		//    4. reification for unreturnable call,
		//    5. after call with return check,
		//    6. after call with no check,
		//    7. after everything.
		// Clause {2.3} entry expects the value in interpreter.latestResult.
		// Clause {4,5} entry expects the value in top-of-stack.
		// There are edges between
		//    1 -> {<2.5>, <3,6>} depending on type guarantees,
		//    2 -> {5}
		//    3 -> {6}
		//    4 -> unreachable block
		//    5 -> {7}
		//    6 -> {7}.
		// Clauses with no actual predecessors are not generated.

		// #1: Default lookup.
		generator.startBlock(callSiteHelper.onFallBackToSlowLookup)
		if (generator.currentlyReachable())
		{
			generateSlowPolymorphicCall(callSiteHelper, semanticArguments)
		}

		// #1b: At this point, invocationSitesToCreate is fully populated with
		// basic blocks in which to generate invocations of the corresponding
		// method definition bodies.  Generate them all now, as they will lead
		// to the exits that we'll generate in the next step.
		callSiteHelper.generateAllInvocationSites()

		// #2: Reification with return check.
		generator.startBlock(callSiteHelper.onReificationWithCheck)
		if (generator.currentlyReachable())
		{
			reify(expectedType, ChunkEntryPoint.TO_RETURN_INTO)
			if (generator.currentlyReachable())
			{
				// Capture the value being returned into the on-ramp.
				if (unionOfPossibleResults.isVacuousType)
				{
					generator.addUnreachableCode()
				}
				else
				{
					forceSlotRegister(
						stackp,
						pc - 1,
						getLatestReturnValue(unionOfPossibleResults))
					generator.jumpTo(callSiteHelper.afterCallWithCheck)
				}
			}
		}

		// #3: Reification without return check.
		generator.startBlock(callSiteHelper.onReificationNoCheck)
		if (generator.currentlyReachable())
		{
			reify(expectedType, ChunkEntryPoint.TO_RETURN_INTO)
			if (generator.currentlyReachable())
			{
				// Capture the value being returned into the on-ramp.
				val guaranteedType = unionOfPossibleResults.typeIntersection(
					expectedType)
				if (guaranteedType.isVacuousType)
				{
					generator.addUnreachableCode()
				}
				else
				{
					forceSlotRegister(
						stackp,
						pc,
						getLatestReturnValue(guaranteedType))
					generator.jumpTo(callSiteHelper.afterCallNoCheck)
				}
			}
		}

		// #4: Reification for unreturnable call.
		generator.startBlock(callSiteHelper.onReificationUnreturnable)
		if (generator.currentlyReachable())
		{
			reify(expectedType, ChunkEntryPoint.TO_RETURN_INTO)
			if (generator.currentlyReachable())
			{
				generator.addUnreachableCode()
			}
		}

		// #5: After call with return check.
		generator.startBlock(callSiteHelper.afterCallWithCheck)
		if (generator.currentlyReachable())
		{
			// The unchecked return value will have been put into the register
			// bound to the L2SemanticSlot for the stackp and pc just after the
			// call MINUS ONE.  Check it, moving it to a register that's bound
			// to the L2SemanticSlot for the stackp and pc just after the call.
			generateReturnTypeCheck(expectedType)
			generator.jumpTo(callSiteHelper.afterEverything)
		}

		// #6: After call without return check.
		// Make the version of the stack with the unchecked value available.
		generator.startBlock(callSiteHelper.afterCallNoCheck)
		if (generator.currentlyReachable())
		{
			// The value will have been put into a register bound to the
			// L2SemanticSlot for the stackp and pc just after the call.
			generator.jumpTo(callSiteHelper.afterEverything)
		}

		// #7: After everything.  If it's possible to return a valid value from
		// the call, this will be reachable.
		generator.startBlock(callSiteHelper.afterEverything)
	}

	/**
	 * A leaf lookup tree was found at this position in the inlined dispatch.
	 * If it's a singular method definition, embed a call to it, otherwise jump
	 * to the fallback lookup code to reproduce and handle lookup errors.
	 *
	 * @param semanticArguments
	 *   The list of [L2SemanticValue]s supplying argument values. These become
	 *   strengthened by type tests in the current manifest.
	 * @param callSiteHelper
	 *   The [CallSiteHelper] object for this dispatch.
	 * @param solutions
	 *   The [A_Tuple] of [A_Definition]s at this leaf of the lookup tree.  If
	 *   there's exactly one, and it's a method definition, the lookup is
	 *   considered successful, otherwise it's a failed lookup.
	 */
	private fun leafVisit(
		semanticArguments: List<L2SemanticBoxedValue>,
		callSiteHelper: CallSiteHelper,
		solutions: A_Tuple)
	{
		if (!generator.currentlyReachable())
		{
			return
		}
		if (solutions.tupleSize == 1)
		{
			val solution: A_Definition = solutions.tupleAt(1)
			if (solution.isMethodDefinition())
			{
				promiseToHandleCallForDefinitionBody(
					solution.bodyBlock(), semanticArguments, callSiteHelper)
				return
			}
		}
		// Failed dispatches basically never happen, so jump to the fallback
		// lookup, which will do its own problem reporting.
		generator.jumpTo(callSiteHelper.onFallBackToSlowLookup)
	}

	/**
	 * A leaf lookup tree was found at this position in the inlined dispatch.
	 * If it's a singular method definition, embed a call to it, otherwise jump
	 * to the fallback lookup code to reproduce and handle lookup errors.
	 *
	 * @param function
	 *   The [A_Definition] body [A_Function] to be invoked.
	 * @param semanticArguments
	 *   The list of [L2SemanticValue]s supplying argument values. These become
	 *   strengthened by type tests in the current manifest.
	 * @param callSiteHelper
	 *   The [CallSiteHelper] object for this dispatch.
	 */
	private fun promiseToHandleCallForDefinitionBody(
		function: A_Function,
		semanticArguments: List<L2SemanticBoxedValue>,
		callSiteHelper: CallSiteHelper)
	{
		val existingPair = callSiteHelper.invocationSitesToCreate[function]
		if (existingPair === null)
		{
			val block = generator.createBasicBlock("successful lookup")
			// Safety check.
			var ran = 0
			val newAction = {
				assert(ran == 0)
				ran++
				assert(!generator.currentlyReachable())
				if (block.predecessorEdges().isNotEmpty())
				{
					generator.startBlock(block)
					generateGeneralFunctionInvocation(
						generator.boxedConstant(function),
						semanticArguments.map(currentManifest::readBoxed),
						true,
						callSiteHelper)
					assert(!generator.currentlyReachable())
				}
			}
			callSiteHelper.invocationSitesToCreate[function] =
				block to newAction
			generator.jumpTo(block)
		}
		else
		{
			generator.jumpTo(existingPair.first)
		}
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
		arguments: List<L2ReadBoxedOperand>,
		tryToGenerateSpecialPrimitiveInvocation: Boolean,
		callSiteHelper: CallSiteHelper,
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
		val rawFunction = generator.determineRawFunction(functionToCallReg)
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
					L2ReadBoxedOperand(
						argSemanticValue, strongRestriction, manifest)
				}
				argumentTypes = strongArguments.map { it.type() }
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
				// tryToGenerateSpecialPrimitiveInvocation(), so do not
				// recurse again; just generate the best invocation possible
				// given what we know.
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
						resultType = Types.ANY.o
					}
					val writer = generator.boxedWrite(
						primitiveInvocation(
							primitive,
							arguments.map(L2ReadBoxedOperand::semanticValue)),
						boxedRestrictionForType(resultType))
					addInstruction(
						L2_RUN_INFALLIBLE_PRIMITIVE.forPrimitive(primitive),
						L2ConstantOperand(rawFunction),
						L2PrimitiveOperand(primitive),
						L2ReadBoxedVectorOperand(arguments),
						writer)
					if (willAlwaysFailPrimitive &&
						rawFunction.returnTypeIfPrimitiveFails.isBottom)
					{
						// The function will fail the primitive, and the
						// fallback is bottom-typed, so this is unreachable.
						generator.addUnreachableCode()
						return
					}
					else
					{
						callSiteHelper.useAnswer(readBoxed(writer))
					}
					generated = true
				}
				else
				{
					generated = false
				}
			}
			if (generated)
			{
				assert(!generator.currentlyReachable())
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
			guaranteedResultType =
				when (fallibility)
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
		val constantFunction: A_Function? = functionToCallReg.constantOrNull()
		val canReturn = !guaranteedResultType.isVacuousType
		val successBlock = generator.createBasicBlock("successful invocation")
		val targetBlock =
			when
			{
				!canReturn -> callSiteHelper.onReificationUnreturnable
				skipCheck -> callSiteHelper.onReificationNoCheck
				else -> callSiteHelper.onReificationWithCheck
			}
		val reificationTarget = generator.createBasicBlock(
			"invoke reification target", targetBlock.zone, isCold = true)
		val writeResult = writeSlot(
			stackp,
			if (skipCheck) pc else pc - 1,
			boxedRestrictionForType(
				if (guaranteedResultType.isBottom) Types.ANY.o // unreachable
				else guaranteedResultType))
		val unreachable = L2BasicBlock("unreachable", isCold = true)
		if (constantFunction !== null)
		{
			addInstruction(
				L2_INVOKE_CONSTANT_FUNCTION,
				L2ConstantOperand(constantFunction),
				L2ReadBoxedVectorOperand(arguments),
				writeResult,
				edgeTo(if (canReturn) successBlock else unreachable),
				edgeTo(reificationTarget))
		}
		else
		{
			addInstruction(
				L2_INVOKE,
				functionToCallReg,
				L2ReadBoxedVectorOperand(arguments),
				writeResult,
				edgeTo(if (canReturn) successBlock else unreachable),
				edgeTo(reificationTarget))
		}
		generator.startBlock(unreachable)
		generator.addInstruction(L2_UNREACHABLE_CODE)

		generator.startBlock(reificationTarget)
		generator.addInstruction(
			L2_ENTER_L2_CHUNK,
			L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			L2CommentOperand("Transient - cannot be invalid."))
		generator.jumpTo(targetBlock)

		generator.startBlock(successBlock)
		if (generator.currentlyReachable())
		{
			generator.jumpTo(
				if (skipCheck) callSiteHelper.afterCallNoCheck
				else callSiteHelper.afterCallWithCheck)
			assert(!generator.currentlyReachable())
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
	private fun generateReturnTypeCheck(expectedType: A_Type)
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
			generator.addUnreachableCode()
			return
		}

		// Check the return value against the expectedType.
		val passedCheck = generator.createBasicBlock("passed return check")
		val failedCheck = generator.createBasicBlock(
			"failed return check",
			ZoneType.DEAD_END.createZone("failed check"),
			isCold = true)
		if (!uncheckedValueRead.restriction().intersectsType(expectedType))
		{
			// It's impossible to return a valid value here, since the value's
			// type bound and the expected type don't intersect.  Always invoke
			// the bad type handler.
			generator.jumpTo(failedCheck)
		}
		else
		{
			assert(!uncheckedValueRead.type().isSubtypeOf(expectedType))
				{ "Attempting to create unnecessary type check" }
			generator.jumpIfKindOfConstant(
				uncheckedValueRead, expectedType, passedCheck, failedCheck)
		}

		// The type check failed, so report it.
		generator.startBlock(failedCheck)
		generator.addInstruction(
			L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION,
			uncheckedValueRead,
			L2ConstantOperand(expectedType),
			L2IntImmediateOperand(pc),
			L2IntImmediateOperand(stackp),
			L2ReadBoxedVectorOperand(
				(1..numSlots).map {
					when (it)
					{
						// Make it look like the expectedType has been pushed.
						stackp -> generator.boxedConstant(expectedType)
						else -> readSlot(it)
					}
				}))
		assert(!generator.currentlyReachable())

		// Generate the much more likely passed-check flow.
		generator.startBlock(passedCheck)
		if (generator.currentlyReachable())
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
		callSiteHelper: CallSiteHelper): Boolean
	{
		val argumentCount = arguments.size
		if (primitive.hasFlag(Flag.CanFold))
		{
			// It can be folded, if supplied with constants.
			val constants = mutableListOf<AvailObject>()
			for (regRead in arguments)
			{
				val constant = regRead.constantOrNull() ?: break
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
						generator.boxedConstant(
							interpreter.getLatestResult().makeImmutable()))
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
			val argument = generator.readBoxed(arguments[i].semanticValue())
			assert(
				argument.restriction().type.isSubtypeOf(
					signatureTupleType.typeAtIndex(i + 1)))
			narrowedArgTypes.add(argument.restriction().type)
			narrowedArguments.add(argument)
		}
		// Let the primitive generate specialized code if possible.
		var generated = primitive.tryToGenerateSpecialPrimitiveInvocation(
			functionToCallReg,
			rawFunction,
			narrowedArguments,
			narrowedArgTypes,
			callSiteHelper)
		if (!generated)
		{
			// Try a general infallible invocation, if possible.
			generated = primitive.tryToGenerateGeneralPrimitiveInvocation(
				rawFunction,
				narrowedArguments,
				narrowedArgTypes,
				this,
				callSiteHelper)
		}
		if (generated && generator.currentlyReachable())
		{
			// The top-of-stack was replaced, but it wasn't convenient to do
			// a jump to the appropriate exit handlers.  Do that now.
			callSiteHelper.useAnswer(readSlot(stackp))
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
	 * @param semanticArguments
	 *   The list of [L2SemanticValue]s supplying argument values. These become
	 *   strengthened by type tests in the current manifest.
	 */
	private fun generateSlowPolymorphicCall(
		callSiteHelper: CallSiteHelper,
		semanticArguments: List<L2SemanticBoxedValue>)
	{
		val bundle = callSiteHelper.bundle
		val method: A_Method = bundle.bundleMethod
		val nArgs = method.numArgs
		val lookupSucceeded = generator.createBasicBlock(
			"lookup succeeded for " + callSiteHelper.quotedBundleName)
		val lookupFailed = generator.createBasicBlock(
			"lookup failed for " + callSiteHelper.quotedBundleName,
			ZoneType.DEAD_END.createZone("lookup failed"),
			isCold = true)
		val argumentRestrictions = semanticArguments.mapIndexed { i, arg ->
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
			semanticArguments.map(currentManifest::readBoxed)

		// At some point we might want to introduce a SemanticValue for tagging
		// this register.
		if (functionTypeUnion.isBottom)
		{
			// There were no possible method definitions, so jump immediately to
			// the lookup failure clause.  Don't generate the success case.
			// For consistency, generate a jump to the lookupFailed exit point,
			// then generate it immediately.
			generator.jumpTo(lookupFailed)
			generator.startBlock(lookupFailed)
			generateLookupFailure(
				method,
				callSiteHelper,
				generator.boxedConstant(E_NO_METHOD_DEFINITION.numericCode()),
				argumentRestrictions,
				argumentReads)
			return
		}
		// It doesn't necessarily always fail, so try a lookup.
		val functionWrite = generator.boxedWriteTemp(
			boxedRestrictionForType(functionTypeUnion))
		val errorCodeWrite = generator.boxedWriteTemp(
			boxedRestrictionForType(L2_LOOKUP_BY_VALUES.lookupErrorsType))
		if (!callSiteHelper.isSuper)
		{
			// Not a super-call.
			addInstruction(
				L2_LOOKUP_BY_VALUES,
				L2SelectorOperand(bundle),
				L2ReadBoxedVectorOperand(argumentReads),
				functionWrite,
				errorCodeWrite,
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
						generator.boxedConstant(superUnionElementType)
					}
					else
					{
						val typeBound =
							argStaticType.typeUnion(superUnionElementType)
						val argTypeWrite = generator.boxedWriteTemp(
							boxedRestrictionForType(instanceMeta(typeBound)))
						if (superUnionElementType.isBottom)
						{
							// Only this argument's actual type matters.
							addInstruction(L2_GET_TYPE, argReg, argTypeWrite)
						}
						else
						{
							// The lookup is constrained by the actual
							// argument's type *and* the super-union.  This is
							// possible because this is a top-level argument,
							// but it's the leaf arguments that individually
							// specify supercasts.
							val originalArgTypeWrite =
								generator.boxedWriteTemp(
									boxedRestrictionForType(
										instanceMeta(typeBound)))
							addInstruction(
								L2_GET_TYPE, argReg, originalArgTypeWrite)
							addInstruction(
								L2_TYPE_UNION(
									readBoxed(originalArgTypeWrite),
									generator.boxedConstant(
										superUnionElementType),
									argTypeWrite))
						}
						readBoxed(argTypeWrite)
					}
				argTypeRegs.add(argTypeReg)
			}
			addInstruction(
				L2_LOOKUP_BY_TYPES,
				L2SelectorOperand(bundle),
				L2ReadBoxedVectorOperand(argTypeRegs),
				functionWrite,
				errorCodeWrite,
				edgeTo(lookupSucceeded),
				edgeTo(lookupFailed))
		}
		// At this point, we've attempted to look up the method, and either
		// jumped to lookupSucceeded with functionWrite set to the body
		// function, or jumped to lookupFailed with errorCodeWrite set to
		// the lookup error code.

		// Emit the lookup failure case.
		generator.startBlock(lookupFailed)
		generateLookupFailure(
			method,
			callSiteHelper,
			readBoxed(errorCodeWrite),
			argumentRestrictions,
			argumentReads)

		// Now invoke the method definition's body.  We've already examined all
		// possible method definition bodies to see if they all conform with the
		// expectedType, and captured that in alwaysSkipResultCheck.
		generator.startBlock(lookupSucceeded)
		val constantFunction: A_Function? =
			readBoxed(functionWrite).restriction().constantOrNull
		if (constantFunction !== null)
		{
			// Even though we couldn't prove statically that this function was
			// always looked up, we proved the slightly weaker condition that if
			// the lookup was successful, it must have produced this function.
			// Jump into the same block that will be generated for a positive
			// inlined lookup of the same function.
			promiseToHandleCallForDefinitionBody(
				constantFunction, semanticArguments, callSiteHelper)
		}
		else
		{
			generateGeneralFunctionInvocation(
				readBoxed(functionWrite),
				argumentReads,
				true,
				callSiteHelper)
		}
	}

	/**
	 * Generate code to report a lookup failure.
	 *
	 * @param method
	 *   The [A_Method] that could not be found at the call site.
	 * @param callSiteHelper
	 *   Information about the method call site.
	 * @param errorCodeRead
	 *   The register containing the numeric [AvailErrorCode] indicating the
	 *   lookup problem.
	 * @param argumentRestrictions
	 *   The [TypeRestriction]s on the arguments.
	 * @param argumentReads
	 *   The source [L2ReadBoxedVectorOperand]s supplying arguments.
	 */
	private fun generateLookupFailure(
		method: A_Method,
		callSiteHelper: CallSiteHelper,
		errorCodeRead: L2ReadBoxedOperand,
		argumentRestrictions: List<TypeRestriction>,
		argumentReads: List<L2ReadBoxedOperand>)
	{
		val currentZone = generator.currentBlock().zone
		val invalidSendReg =
			generator.boxedWriteTemp(
			boxedRestrictionForType(HookType.INVALID_MESSAGE_SEND.functionType))
		addInstruction(
			L2_GET_INVALID_MESSAGE_SEND_FUNCTION,
			invalidSendReg)
		// Collect the argument types into a tuple type.
		val argTypes = argumentRestrictions.map { it.type }
		val argumentsTupleWrite = generator.boxedWriteTemp(
			boxedRestrictionForType(tupleTypeForTypesList(argTypes)))
		addInstruction(
			L2_CREATE_TUPLE,
			L2ReadBoxedVectorOperand(argumentReads),
			argumentsTupleWrite)
		val onReificationDuringFailure =
			generator.createBasicBlock(
				"reify in method lookup failure handler for" +
					callSiteHelper.quotedBundleName,
				ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Continue reification during lookup failure handler"),
				isCold = true)
		val unreachable = L2BasicBlock("unreachable", currentZone)
		addInstruction(
			L2_INVOKE,
			readBoxed(invalidSendReg),
			L2ReadBoxedVectorOperand(
				listOf(
					errorCodeRead,
					generator.boxedConstant(method),
					readBoxed(argumentsTupleWrite))),
			generator.boxedWriteTemp(TypeRestriction.anyRestriction),  // unreachable
			edgeTo(unreachable),
			edgeTo(onReificationDuringFailure))

		generator.startBlock(unreachable)
		generator.addInstruction(L2_UNREACHABLE_CODE)

		// Reification has been requested while the failure call is in
		// progress.
		generator.startBlock(onReificationDuringFailure)
		generator.addInstruction(
			L2_ENTER_L2_CHUNK,
			L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			L2CommentOperand(
				"Transient - cannot be invalid."))
		reify(bottom, ChunkEntryPoint.TO_RETURN_INTO)
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
		val serviceInterrupt = generator.createBasicBlock(
			"service interrupt",
			isCold = true)
		val merge =
			generator.createBasicBlock("merge after possible interrupt")
		addInstruction(
			L2_JUMP_IF_INTERRUPT,
			edgeTo(serviceInterrupt),
			edgeTo(merge))
		generator.startBlock(serviceInterrupt)
		// Service the interrupt:  Generate the reification instructions,
		// ensuring that when returning into the resulting continuation, it will
		// enter a block where the slot registers are the new ones we just
		// created.  After creating the continuation, actually service the
		// interrupt.

		// Reify everybody else, starting at the caller.
		val onReification = generator.createBasicBlock(
			"On reification for interrupt",
			ZoneType.BEGIN_REIFICATION_FOR_INTERRUPT.createZone(
				"Start reification and run interrupt"),
			isCold = true)
		addInstruction(
			L2_REIFY,
			L2IntImmediateOperand(1),
			L2IntImmediateOperand(1),
			L2ArbitraryConstantOperand(
				StatisticCategory.INTERRUPT_OFF_RAMP_IN_L2.statistic),
			edgeTo(onReification))
		generator.startBlock(onReification)
		generator.addInstruction(
			L2_ENTER_L2_CHUNK,
			L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			L2CommentOperand(
				"Transient, for interrupt - cannot be invalid."))

		// When the lambda below runs, it's generating code at the point where
		// continuationReg will have the new continuation.
		reify(null, ChunkEntryPoint.TO_RESUME)
		generator.jumpTo(merge)
		// Merge the flow (reified and continued, versus not reified).
		generator.startBlock(merge)
		// And now... either we're back or we never left.
	}

	/**
	 * Emit the specified variable-reading instruction, and an off-ramp to deal
	 * with the case that the variable is unassigned.
	 *
	 * @param getOperation
	 *   The [variable reading][L2Operation.isVariableGet]
	 *   [operation][L2Operation].
	 * @param variable
	 *   The location of the [variable][A_Variable].
	 * @return
	 *   The [L2ReadBoxedOperand] into which the variable's value will be
	 *   written, including having made it immutable if requested.
	 */
	fun emitGetVariableOffRamp(
		getOperation: L2Operation,
		variable: L2ReadBoxedOperand,
		targetSemanticValue: L2SemanticBoxedValue): L2ReadBoxedOperand
	{
		assert(getOperation.isVariableGet)
		val success = generator.createBasicBlock("successfully read variable")
		val failure = generator.createBasicBlock(
			"failed to read variable",
			ZoneType.DEAD_END.createZone("failed read"),
			isCold = true)

		// Emit the specified get-variable instruction variant.
		val valueWrite = generator.boxedWrite(
			targetSemanticValue,
			boxedRestrictionForType(variable.type().readType))
		addInstruction(
			getOperation,
			variable,
			valueWrite,
			edgeTo(success),
			edgeTo(failure))

		// Emit the failure path. Unbind the destination of the variable-get in
		// this case, since it won't have been populated (by definition,
		// otherwise we wouldn't have failed).
		generator.startBlock(failure)
		generator.addInstruction(
			L2_INVOKE_UNASSIGNED_VARIABLE_READ_FUNCTION,
			L2IntImmediateOperand(pc),
			L2IntImmediateOperand(stackp),
			L2ReadBoxedVectorOperand((1..numSlots).map(this::readSlot)))
		assert(!generator.currentlyReachable())

		// End with the success path.
		generator.startBlock(success)
		return readBoxed(valueWrite)
	}

	/**
	 * Emit the specified variable-writing instruction, and an off-ramp to deal
	 * with the case that the variable has
	 * [write-reactors][VariableAccessReactor] but variable write
	 * [tracing][Interpreter.traceVariableWrites] is disabled.
	 *
	 * @param setOperation
	 *   The [variable reading][L2Operation.isVariableSet]
	 *   [operation][L2Operation].
	 * @param variable
	 *   The location of the [variable][A_Variable].
	 * @param newValue
	 *   The location of the new value.
	 */
	private fun emitSetVariableOffRamp(
		setOperation: L2Operation,
		variable: L2ReadBoxedOperand,
		newValue: L2ReadBoxedOperand)
	{
		assert(setOperation.isVariableSet)
		val success = generator.createBasicBlock("set local success")
		val failure = generator.createBasicBlock(
			"set local failure/observe",
			isCold = true)
		val onReificationDuringFailure = generator.createBasicBlock(
			"reify during set local failure",
			ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
				"Continue reification for set-variable failure handler"),
			isCold = true)
		// Emit the set-variable instruction.
		addInstruction(
			setOperation,
			variable,
			newValue,
			edgeTo(success),
			edgeTo(failure))

		// Emit the failure path.
		generator.startBlock(failure)
		val observeFunction = generator.boxedWriteTemp(
			boxedRestrictionForType(HookType.IMPLICIT_OBSERVE.functionType))
		addInstruction(
			L2_GET_IMPLICIT_OBSERVE_FUNCTION,
			observeFunction)
		val variableAndValueTupleReg = generator.boxedWriteTemp(
			boxedRestrictionForType(
				tupleTypeForTypes(variable.type(), newValue.type())))
		addInstruction(
			L2_CREATE_TUPLE,
			L2ReadBoxedVectorOperand(listOf(variable, newValue)),
			variableAndValueTupleReg)
		// Note: the handler block's value is discarded; also, since it's not a
		// method definition, it can't have a semantic restriction.
		addInstruction(
			L2_INVOKE,
			readBoxed(observeFunction),
			L2ReadBoxedVectorOperand(
				listOf(
					generator
						.boxedConstant(assignmentFunction()),
					readBoxed(variableAndValueTupleReg))),
			// Unreachable:
			generator.boxedWriteTemp(TypeRestriction.anyRestriction),
			edgeTo(success),
			edgeTo(onReificationDuringFailure))
		generator.startBlock(onReificationDuringFailure)
		generator.addInstruction(
			L2_ENTER_L2_CHUNK,
			L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			L2CommentOperand(
				"Transient - cannot be invalid."))
		reify(Types.TOP.o, ChunkEntryPoint.TO_RETURN_INTO)
		generator.jumpTo(success)

		// End with the success block.  Note that the failure path can lead here
		// if the implicit-observe function returns.
		generator.startBlock(success)
	}

	/**
	 * For each level one instruction, write a suitable transliteration into
	 * level two instructions.
	 */
	private fun translateL1Instructions()
	{
		val timeAtStartOfTranslation = AvailRuntimeSupport.captureNanos()

		/**
		 * The [L2BasicBlock] which is the entry point for a function that has
		 * just been invoked.
		 */
		val startBlock = generator.createBasicBlock(
			"START for ${generator.topFrame.codeName}")
		startBlock.makeIrremovable()
		generator.specialBlocks[START] = startBlock
		generator.startBlock(startBlock)
		val primitive = code.codePrimitive()
		if (primitive !== null)
		{
			// Try the primitive, automatically returning if successful.
			addInstruction(
				L2_TRY_PRIMITIVE,
				L2PrimitiveOperand(primitive))
			if (primitive.hasFlag(Flag.CannotFail))
			{
				// Infallible primitives don't need any other L2 code.
				return
			}
		}
		val afterPrimitive =
			generator.createLoopHeadBlock("After optional primitive")
		afterPrimitive.makeIrremovable()
		generator.specialBlocks[AFTER_OPTIONAL_PRIMITIVE] = afterPrimitive
		generator.jumpTo(afterPrimitive)
		generator.startBlock(afterPrimitive)
		currentManifest.clear()
		// While it's true that invalidation may only take place when no Avail
		// code is running (even when evicting old chunks), and it's also the
		// case that invalidation causes the chunk to be disconnected from its
		// compiled code, it's still the case that a continuation (a label, say)
		// created at an earlier time still refers to the invalid chunk.  Ensure
		// it can fall back gracefully to L1 (the default chunk) by entering it
		// at the TO_RESTART entry point.  Note that there can't be a primitive
		// for such continuations.
		// Capture the arguments.
		val numArgs = code.numArgs()
		val tupleType = code.functionType().argsTupleType
		addInstruction(
			L2_ENTER_L2_CHUNK_FOR_CALL,
			L2CommentOperand(
				"If invalid, reenter «default» at the beginning."),
			L2WriteBoxedVectorOperand(
				(1..numArgs).map { i ->
					generator.boxedWrite(
						semanticSlot(i),
						boxedRestrictionForType(tupleType.typeAtIndex(i)))
				}))

		// Do any reoptimization before capturing arguments.
		val optimization = generator.optimizationLevel
		val newCountdown = optimization.countdown
		code.countdownToReoptimize(newCountdown)
		if (newCountdown < Long.MAX_VALUE)
		{
			// Optimize it again if it's called frequently enough.
			addInstruction(
				L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO,
				L2IntImmediateOperand(optimization.ordinal + 1),
				L2IntImmediateOperand(0))
			// If it was reoptimized, it would have jumped to the
			// afterOptionalInitialPrimitiveBlock in the new chunk.
		}

		// Here's where a local P_RestartContinuationWithArguments is optimized
		// to jump to. It's expected to place the replacement arguments into
		// semantic slots n@1.
		val loopHead = generator.createLoopHeadBlock(
			"Loop head for " + code.methodName.asNativeString())
		generator.specialBlocks[RESTART_LOOP_HEAD] = loopHead
		generator.jumpTo(loopHead)
		generator.startBlock(loopHead)

		// Create the locals.
		val numLocals = code.numLocals
		for (local in 1 .. numLocals)
		{
			val localType = code.localTypeAt(local)
			addInstruction(
				L2_CREATE_VARIABLE,
				L2ConstantOperand(localType),
				writeSlot(
					numArgs + local,
					pc,
					boxedRestrictionForType(localType)))
		}

		// Capture the primitive failure value in the first local if applicable.
		if (primitive !== null)
		{
			assert(!primitive.hasFlag(Flag.CannotFail))
			// Move the primitive failure value into the first local.  This
			// doesn't need to support implicit observation, so no off-ramp
			// is generated.
			val success = generator.createBasicBlock("success")
			val unreachable = L2BasicBlock("unreachable")
			addInstruction(
				L2_SET_VARIABLE_NO_CHECK,
				readSlot(numArgs + 1),
				getLatestReturnValue(code.localTypeAt(1).writeType),
				edgeTo(success),
				edgeTo(unreachable))

			generator.startBlock(unreachable)
			generator.addInstruction(L2_UNREACHABLE_CODE)

			generator.startBlock(success)
		}

		// Nil the rest of the stack slots.
		for (i in numArgs + numLocals + 1 .. numSlots)
		{
			nilSlot(i)
		}

		// Check for interrupts. If an interrupt is discovered, then reify and
		// process the interrupt.  When the chunk resumes, it will explode the
		// continuation again.
		emitInterruptOffRamp()

		// Capture the time it took to generate the whole preamble.
		val interpreterIndex = interpreter.interpreterIndex
		preambleGenerationStat.record(
			AvailRuntimeSupport.captureNanos() - timeAtStartOfTranslation,
			interpreterIndex)

		// Transliterate each level one nybblecode into L2Instructions.
		while (!instructionDecoder.atEnd() && generator.currentlyReachable())
		{
			val before = AvailRuntimeSupport.captureNanos()
			val operation = instructionDecoder.getOperation()
			operation.dispatch(this)
			levelOneGenerationStats[operation.ordinal].record(
				AvailRuntimeSupport.captureNanos() - before,
				interpreterIndex)
		}

		// Generate the implicit return after the instruction sequence.
		if (generator.currentlyReachable())
		{
			val readResult = readSlot(stackp)
			addInstruction(L2_RETURN, readResult)
			assert(stackp == numSlots)
			stackp = Int.MIN_VALUE
		}
		val unreachableBlock = generator.specialBlocks[UNREACHABLE]
		if (unreachableBlock !== null
			&& unreachableBlock.predecessorEdges().isNotEmpty())
		{
			// Generate the unreachable block.
			generator.startBlock(unreachableBlock)
			addInstruction(L2_UNREACHABLE_CODE)
			// Now make it a loop head, just so code generated later from
			// placeholders (L2Operation#isPlaceholder) can still connect to
			// it, as long as it uses a back-edge.
			unreachableBlock.isLoopHead = true
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
	 */
	private fun translate()
	{
		val beforeL1Naive = AvailRuntimeSupport.captureNanos()
		translateL1Instructions()
		translateL1Stat.record(
			AvailRuntimeSupport.captureNanos() - beforeL1Naive,
			interpreter.interpreterIndex)
		val optimizer = L2Optimizer(generator)
		optimizer.optimize(interpreter)
		val beforeChunkGeneration = AvailRuntimeSupport.captureNanos()
		generator.createChunk(code)
		assert(code.startingChunk == generator.chunk())

		optimizer.postOptimizationCleanup()  // Remove to debug.

		L2Generator.finalGenerationStat.record(
			AvailRuntimeSupport.captureNanos() - beforeChunkGeneration,
			interpreter.interpreterIndex)
	}

	override fun L1_doCall()
	{
		val bundle = code.literalAt(instructionDecoder.getOperand())
		val expectedType = code.literalAt(instructionDecoder.getOperand())
		generateCall(bundle, expectedType, bottom)
	}

	override fun L1_doPushLiteral()
	{
		val constant = code.literalAt(instructionDecoder.getOperand())
		stackp--
		moveConstantToSlot(constant, stackp)
	}

	override fun L1_doPushLastLocal()
	{
		val localIndex = instructionDecoder.getOperand()
		stackp--
		val sourceRegister = readSlot(localIndex)
		forceSlotRegister(stackp, pc, sourceRegister)
		nilSlot(localIndex)
	}

	override fun L1_doPushLocal()
	{
		val localIndex = instructionDecoder.getOperand()
		stackp--
		val sourceRegister = readSlot(localIndex)
		forceSlotRegister(stackp, pc, sourceRegister)
		forceSlotRegister(localIndex, pc, sourceRegister)
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
		val outers = mutableListOf<L2ReadBoxedOperand>()
		for (i in 1 .. count)
		{
			outers.add(readSlot(stackp + count - i))
		}
		// Pop the outers, but reserve room for the pushed function.
		stackp += count - 1
		addInstruction(
			L2_CREATE_FUNCTION,
			L2ConstantOperand(codeLiteral),
			L2ReadBoxedVectorOperand(outers),
			writeSlot(
				stackp,
				pc,
				boxedRestrictionForType(codeLiteral.functionType())))

		// Now that the function has been constructed, clear the slots that
		// were used for outer values -- except the destination slot, which
		// is being overwritten with the resulting function anyhow.
		for (i in stackp + 1 - count until stackp)
		{
			nilSlot(i)
		}
	}

	override fun L1_doSetLocal()
	{
		val localIndex = instructionDecoder.getOperand()
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK,
			readSlot(localIndex),
			readSlot(stackp))
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(stackp, pc, generator.boxedConstant(nil))
		stackp++
	}

	override fun L1_doGetLocalClearing()
	{
		val index = instructionDecoder.getOperand()
		stackp--
		val valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING,
			readSlot(index),
			generator.newTemp())
		forceSlotRegister(stackp, pc, valueReg)
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

	override fun L1_doGetOuterClearing()
	{
		val outerIndex = instructionDecoder.getOperand()
		stackp--
		val outerType = code.outerTypeAt(outerIndex)
		val valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING,
			getOuterRegister(outerIndex, outerType),
			generator.newTemp())
		forceSlotRegister(stackp, pc, valueReg)
	}

	override fun L1_doSetOuter()
	{
		val outerIndex = instructionDecoder.getOperand()
		val outerType = code.outerTypeAt(outerIndex)
		val tempVarReg = getOuterRegister(outerIndex, outerType)
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK,
			tempVarReg,
			readSlot(stackp))
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(
			stackp,
			pc,
			generator.boxedConstant(nil))
		stackp++
	}

	override fun L1_doGetLocal()
	{
		val index = instructionDecoder.getOperand()
		stackp--
		val valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE,
			readSlot(index),
			generator.newTemp())
		forceSlotRegister(stackp, pc, valueReg)
	}

	override fun L1_doMakeTuple()
	{
		val count = instructionDecoder.getOperand()
		val elements = mutableListOf<L2ReadBoxedOperand>()
		for (i in 1 .. count)
		{
			elements.add(readSlot(stackp + count - i))
			// Clear all but the first pushed slot.
			if (i != 1)
			{
				nilSlot(stackp + count - i)
			}
		}
		stackp += count - 1
		// Fold into a constant tuple if possible
		val tupleRead = generator.createTuple(elements)
		forceSlotRegister(stackp, pc, tupleRead)
	}

	override fun L1_doGetOuter()
	{
		val outerIndex = instructionDecoder.getOperand()
		stackp--
		val outerType = code.outerTypeAt(outerIndex)
		val valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE,
			getOuterRegister(outerIndex, outerType),
			generator.newTemp())
		forceSlotRegister(stackp, pc, valueReg)
	}

	override fun L1_doExtension()
	{
		throw AssertionError("Illegal dispatch nybblecode")
	}

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
		assert(code.codePrimitive() === null)
		val semanticLabel = topFrame().label()
		if (currentManifest.hasSemanticValue(semanticLabel))
		{
			// Reuse a label that was computed for an earlier L1 pushLabel.
		}
		else
		{
			val numArgs = code.numArgs()
			val argumentsForLabel = mutableListOf<L2ReadBoxedOperand>()
			for (i in 1..numArgs)
			{
				argumentsForLabel.add(readSlot(i))
			}
			val continuationType =
				continuationTypeForFunctionType(code.functionType())
			val destinationRegister = generator.boxedWrite(
				semanticLabel, restriction(continuationType, null))
			addInstruction(
				L2_VIRTUAL_CREATE_LABEL,
				destinationRegister,
				currentFunction,
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
			&& literalVariable.valueWasStablyComputed())
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
			val valueReg = emitGetVariableOffRamp(
				L2_GET_VARIABLE,
				generator.boxedConstant(literalVariable),
				generator.newTemp())
			forceSlotRegister(stackp, pc, valueReg)
		}
	}

	override fun L1Ext_doSetLiteral()
	{
		val literalVariable: A_Variable = code.literalAt(
			instructionDecoder.getOperand())
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK,
			generator.boxedConstant(literalVariable),
			readSlot(stackp))
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(
			stackp,
			pc,
			generator.boxedConstant(nil))
		stackp++
	}

	override fun L1Ext_doDuplicate()
	{
		val source = readSlot(stackp)
		stackp--
		forceSlotRegister(stackp + 1, pc, source)
		forceSlotRegister(stackp, pc, source)
	}

	override fun L1Ext_doPermute()
	{
		// Move into the permuted temps, then back to the stack.  This puts the
		// responsibility for optimizing away extra moves (by coloring the
		// registers) on the optimizer.
		val permutation: A_Tuple = code.literalAt(
			instructionDecoder.getOperand())
		val size = permutation.tupleSize
		val temps = arrayOfNulls<L2SemanticBoxedValue>(size)
		for (i in size downTo 1)
		{
			val source = semanticSlot(stackp + size - i)
			val temp = generator.newTemp()
			generator.moveRegister(L2_MOVE.boxed, source, setOf(temp))
			temps[permutation.tupleIntAt(i) - 1] = temp
		}
		for (i in size downTo 1)
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
		val expectedType =
			code.literalAt(instructionDecoder.getOperand())
		val superUnionType =
			code.literalAt(instructionDecoder.getOperand())
		generateCall(bundle, expectedType, superUnionType)
	}

	override fun L1Ext_doSetLocalSlot()
	{
		val destinationIndex = instructionDecoder.getOperand()
		val source = readSlot(stackp)
		forceSlotRegister(destinationIndex, pc, source)
		nilSlot(stackp)
		stackp++
	}

	/**
	 * Create a semantic slot for the given one-based [index], representing the
	 * state just before reaching the specified [afterPc].
	 */
	fun createSemanticSlot(index: Int, afterPc: Int): L2SemanticBoxedValue =
		generator.topFrame.semanticSlot(
			index,
			afterPc,
			if (index <= slotNames.size) slotNames[index - 1] else null)

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

		/** Statistic for generating an L2Chunk's preamble. */
		private val preambleGenerationStat = Statistic(
			L1_NAIVE_TRANSLATION_TIME, "(generate preamble)")

		/** Statistics for timing the translation per L1Operation. */
		private val levelOneGenerationStats: Array<Statistic> =
			L1Operation.entries.map {
				Statistic(L1_NAIVE_TRANSLATION_TIME, it.name)
			}.toTypedArray()

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
				UNOPTIMIZED,
				Frame(null, nil, "default", "top frame"))

			// 0. First try to run it as a primitive.
			generator.startBlock(initialBlock)
			generator.addInstruction(
				L2_TRY_OPTIONAL_PRIMITIVE)
			generator.jumpTo(reenterFromRestartBlock)
			// Only if the primitive fails should we even consider optimizing the
			// fallback code.

			// 1. Update counter and maybe optimize *before* extracting arguments.
			generator.startBlock(reenterFromRestartBlock)
			generator.addInstruction(
				L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO,
				L2IntImmediateOperand(UNOPTIMIZED.ordinal + 1),
				L2IntImmediateOperand(1))
			// 2. Build registers, get arguments, create locals, capture primitive
			// failure value, if any.
			generator.addInstruction(L2_PREPARE_NEW_FRAME_FOR_L1)
			generator.jumpTo(loopBlock)

			// 3. The main L1 interpreter loop.
			generator.startBlock(loopBlock)
			generator.addInstruction(
				L2_INTERPRET_LEVEL_ONE,
				edgeTo(reenterFromCallBlock),
				edgeTo(reenterFromInterruptBlock))

			// 4,5. If reified, calls return here.
			generator.startBlock(reenterFromCallBlock)
			generator.addInstruction(
				L2_REENTER_L1_CHUNK_FROM_CALL)
			generator.addInstruction(
				L2_JUMP,
				backEdgeTo(loopBlock))

			// 6,7. If reified, interrupts return here.
			generator.startBlock(reenterFromInterruptBlock)
			generator.addInstruction(
				L2_REENTER_L1_CHUNK_FROM_INTERRUPT)
			generator.addInstruction(
				L2_JUMP,
				backEdgeTo(loopBlock))

			// 8. Unreachable.
			generator.startBlock(unreachableBlock)
			generator.addInstruction(L2_UNREACHABLE_CODE)
			return generator.controlFlowGraph
		}

		/** Statistic for number of instructions in L2 translations. */
		private val translationSizeStat = Statistic(
			L2_TRANSLATION_VALUES, "L2 instruction count")

		/**
		 * An array of statistics for tracking the distribution of the number of
		 * methods depended on by L2 translations.  The index into the array is
		 * the number of methods depended upon, and the count (the only part of
		 * the [Statistic] that is meaningful) is how many [L2Chunk]s had
		 * exactly that number of dependencies.
		 */
		private val translationDependenciesStat =
			AtomicReference(arrayOf<Statistic>())

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
				optimizationLevel,
				Frame(null, code, codeName, "top frame"))
			val translator = L1Translator(generator, interpreter, code)
			translator.translate()
			val chunk = generator.chunk()
			interpreter.function = savedFunction
			interpreter.argsBuffer.clear()
			interpreter.argsBuffer.addAll(savedArguments)
			interpreter.setLatestResult(savedFailureValue)
			translationSizeStat.record(
				chunk.instructions.size.toLong(),
				interpreter.interpreterIndex)
			val dependencyCount = generator.contingentValues.setSize
			var array = translationDependenciesStat.get()
			if (dependencyCount >= array.size)
			{
				array = translationDependenciesStat.updateAndGet { old ->
					if (dependencyCount >= old.size )
					{
						// Allocate a spare as well.
						Array(dependencyCount + 2) {
							if (it < old.size) old[it]
							else Statistic(
								L2_TRANSLATION_VALUES, "Dependency count = $it")
						}
					}
					else
					{
						// It was already updated by another thread.
						old
					}
				}
			}
			assert(dependencyCount < array.size)
			array[dependencyCount].record(1)
		}
	}
}
