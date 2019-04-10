/*
 * L1Translator.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
package com.avail.optimizer;

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_SemanticRestriction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BottomTypeDescriptor;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.CompiledCodeDescriptor.L1InstructionDecoder;
import com.avail.descriptor.MessageBundleDescriptor;
import com.avail.descriptor.MethodDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.dispatch.InternalLookupTree;
import com.avail.dispatch.LookupTree;
import com.avail.dispatch.LookupTreeTraverser;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelOne.L1OperationDispatcher;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2CommentOperand;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.PhiRestriction;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION;
import com.avail.interpreter.levelTwo.operation.L2_CREATE_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_CREATE_TUPLE;
import com.avail.interpreter.levelTwo.operation.L2_CREATE_VARIABLE;
import com.avail.interpreter.levelTwo.operation.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO;
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK;
import com.avail.interpreter.levelTwo.operation.L2_EXTRACT_CONTINUATION_SLOT;
import com.avail.interpreter.levelTwo.operation.L2_GET_ARGUMENT;
import com.avail.interpreter.levelTwo.operation.L2_GET_CURRENT_CONTINUATION;
import com.avail.interpreter.levelTwo.operation.L2_GET_CURRENT_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_GET_IMPLICIT_OBSERVE_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_GET_INVALID_MESSAGE_RESULT_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_GET_INVALID_MESSAGE_SEND_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_GET_LATEST_RETURN_VALUE;
import com.avail.interpreter.levelTwo.operation.L2_GET_RETURNING_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_GET_TYPE;
import com.avail.interpreter.levelTwo.operation.L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_GET_VARIABLE;
import com.avail.interpreter.levelTwo.operation.L2_GET_VARIABLE_CLEARING;
import com.avail.interpreter.levelTwo.operation.L2_INTERPRET_LEVEL_ONE;
import com.avail.interpreter.levelTwo.operation.L2_INVOKE;
import com.avail.interpreter.levelTwo.operation.L2_INVOKE_CONSTANT_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_INTERRUPT;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_OBJECTS_EQUAL;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_LOOKUP_BY_TYPES;
import com.avail.interpreter.levelTwo.operation.L2_LOOKUP_BY_VALUES;
import com.avail.interpreter.levelTwo.operation.L2_MAKE_SUBOBJECTS_IMMUTABLE;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_OUTER_VARIABLE;
import com.avail.interpreter.levelTwo.operation.L2_POP_CURRENT_CONTINUATION;
import com.avail.interpreter.levelTwo.operation.L2_PREPARE_NEW_FRAME_FOR_L1;
import com.avail.interpreter.levelTwo.operation.L2_REENTER_L1_CHUNK_FROM_CALL;
import com.avail.interpreter.levelTwo.operation.L2_REENTER_L1_CHUNK_FROM_INTERRUPT;
import com.avail.interpreter.levelTwo.operation.L2_REIFY;
import com.avail.interpreter.levelTwo.operation.L2_REIFY.StatisticCategory;
import com.avail.interpreter.levelTwo.operation.L2_RETURN;
import com.avail.interpreter.levelTwo.operation.L2_RETURN_FROM_REIFICATION_HANDLER;
import com.avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE;
import com.avail.interpreter.levelTwo.operation.L2_SET_VARIABLE_NO_CHECK;
import com.avail.interpreter.levelTwo.operation.L2_TRY_OPTIONAL_PRIMITIVE;
import com.avail.interpreter.levelTwo.operation.L2_TRY_PRIMITIVE;
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_AT_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_TYPE_UNION;
import com.avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.general.P_Equality;
import com.avail.interpreter.primitive.types.P_IsSubtypeOf;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.MutableInt;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static com.avail.AvailRuntime.captureNanos;
import static com.avail.AvailRuntime.implicitObserveFunctionType;
import static com.avail.AvailRuntime.invalidMessageSendFunctionType;
import static com.avail.AvailRuntime.unassignedVariableReadFunctionType;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor.continuationTypeForFunctionType;
import static com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CannotFail;
import static com.avail.interpreter.Primitive.Result.FAILURE;
import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RESUME;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RETURN_INTO;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.UNREACHABLE;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restriction;
import static com.avail.optimizer.L2Generator.OptimizationLevel;
import static com.avail.optimizer.L2Generator.finalGenerationStat;
import static com.avail.optimizer.L2Generator.maxExpandedEqualityChecks;
import static com.avail.optimizer.L2Generator.maxPolymorphismToInlineDispatch;
import static com.avail.optimizer.L2Synonym.SynonymFlag.KNOWN_IMMUTABLE;
import static com.avail.performance.StatisticReport.L2_OPTIMIZATION_TIME;
import static com.avail.performance.StatisticReport.L2_TRANSLATION_VALUES;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * The {@code L1Translator} transliterates a sequence of {@link L1Operation
 * level one instructions} into one or more simple {@link L2Instruction level
 * two instructions}, under the assumption that further optimization steps will
 * be able to transform this code into something much more efficient – without
 * altering the level one semantics.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L1Translator
implements L1OperationDispatcher
{
	/**
	 * The {@link L2Generator} for which I'm producing an initial translation.
	 */
	public final L2Generator generator;


	/** The {@link Interpreter} that tripped the translation request. */
	private final Interpreter interpreter;

	/**
	 * The {@linkplain CompiledCodeDescriptor raw function} to transliterate
	 * into level two code.
	 */
	final A_RawFunction code;

	/**
	 * The number of slots in the virtualized continuation.  This includes the
	 * arguments, the locals (including the optional primitive failure result),
	 * and the stack slots.
	 */
	private final int numSlots;

	/**
	 * The topmost Frame for translation, which corresponds with the provided
	 * {@link A_RawFunction} in {@link #code}.
	 */
	public final Frame topFrame;

	/**
	 * The {@link L2SemanticValue}s corresponding with the slots of the virtual
	 * continuation.  These indices are zero-based, but the slot numbering is
	 * one-based.
	 */
	private final L2SemanticValue[] semanticSlots;

	/**
	 * The current level one nybblecode position during naive translation to
	 * level two.
	 */
	@InnerAccess final L1InstructionDecoder instructionDecoder =
		new L1InstructionDecoder();

	/**
	 * The current stack depth during naive translation to level two.
	 */
	@InnerAccess int stackp;

	/**
	 * The exact function that we're translating, if known.  This is only
	 * non-null if the function captures no outers.
	 */
	private final @Nullable A_Function exactFunctionOrNull;

	/**
	 * Answer the {@link L2SemanticValue} representing the virtual continuation
	 * slot having the given one-based index.
	 *
	 * @param index The one-based slot number.
	 * @return The {@link L2SemanticValue} for that slot.
	 */
	private L2SemanticValue semanticSlot (final int index)
	{
		return semanticSlots[index - 1];
	}

	/**
	 * Create a new L1 naive translator for the given {@link L2Generator}.
	 *
	 * @param generator
	 *        The {@link L2Generator} for which I'm producing an initial
	 *        translation from L1.
	 * @param interpreter
	 *        The {@link Interpreter} that tripped the translation request.
	 * @param code
	 *        The {@link A_RawFunction} which is the source of the chunk being
	 *        created.
	 */
	L1Translator (
		final L2Generator generator,
		final Interpreter interpreter,
		final A_RawFunction code)
	{
		this.generator = generator;
		this.interpreter = interpreter;
		this.code = code;
		this.topFrame = new Frame(null, this.code, "top frame");
		this.numSlots = code.numSlots();
		this.stackp = numSlots + 1;
		this.exactFunctionOrNull = computeExactFunctionOrNullForCode(code);
		this.semanticSlots = new L2SemanticValue[numSlots];
		for (int i = 1; i <= numSlots; i++)
		{
			semanticSlots[i - 1] = topFrame.slot(i, 1);
		}
		code.setUpInstructionDecoder(instructionDecoder);
		instructionDecoder.pc(1);
	}

	/**
	 * Determine if the given {@link A_RawFunction}'s instantiations as {@link
	 * A_Function}s must be mutually equal.
	 *
	 * @param theCode The {@link A_RawFunction}.
	 * @return Either a canonical {@link A_Function} or {@code null}.
	 */
	private static @Nullable A_Function computeExactFunctionOrNullForCode (
		final A_RawFunction theCode)
	{
		final int numOuters = theCode.numOuters();
		final List<AvailObject> outerConstants = new ArrayList<>(numOuters);
		for (int i = 1; i <= numOuters; i++)
		{
			final A_Type outerType = theCode.outerTypeAt(i);
			if (outerType.instanceCount().equalsInt(1)
				&& !outerType.isInstanceMeta())
			{
				outerConstants.add(outerType.instance());
			}
			else
			{
				return null;
			}
		}
		// This includes the case of there being no outers.
		return createFunction(theCode, tupleFromList(outerConstants));
	}

	/**
	 * Answer the register holding the latest assigned version of the specified
	 * continuation slot. The slots are the arguments, then the locals, then the
	 * stack entries. The slots are numbered starting at 1.
	 *
	 * @param slotIndex
	 *        The index into the continuation's slots.
	 * @return A register representing that continuation slot.
	 */
	private L2ReadPointerOperand readSlot (final int slotIndex)
	{
		final L2Synonym<L2ObjectRegister, A_BasicObject> synonym =
			stripNull(
				generator.currentManifest().semanticValueToSynonym(
					semanticSlot(slotIndex)));
		return synonym.defaultRegisterRead();
	}

	/**
	 * Create a new register to overwrite any existing value in the specified
	 * continuation slot.  Answer a write of that new register.  The slots are
	 * the arguments, the local variables, the local constants, and finally the
	 * stack entries.  Slots are numbered starting at 1.
	 *
	 * @param slotIndex
	 *        The index into the continuation's slots.
	 * @param effectivePc
	 *        The Level One pc at which this write should be considered
	 *        effective.
	 * @param restriction
	 *        The bounding {@link TypeRestriction} for the new register.
	 * @return A register write representing that continuation slot.
	 */
	private L2WritePointerOperand writeSlot (
		final int slotIndex,
		final int effectivePc,
		final TypeRestriction<A_BasicObject> restriction)
	{
		// Create a new semantic slot at the current pc, representing this
		// newly written value.
		final L2SemanticValue semanticValue =
			topFrame.slot(slotIndex, effectivePc);
		semanticSlots[slotIndex - 1] = semanticValue;
		final L2WritePointerOperand writer =
			generator.newObjectRegisterWriter(restriction);
		generator.currentManifest().addBinding(semanticValue, writer.register());
		return writer;
	}

	/**
	 * Associate the specified {@link L2ReadPointerOperand} with the semantic
	 * slot having the given index and effective pc.  Restrict the type based on
	 * the register-read's {@link TypeRestriction}.
	 *
	 * @param slotIndex
	 *        The slot index to replace.
	 * @param effectivePc
	 *        The effective pc.
	 * @param registerRead
	 *        The {@link L2ReadPointerOperand} that should now be considered the
	 *        current register representing that slot.
	 */
	@InnerAccess void forceSlotRegister (
		final int slotIndex,
		final int effectivePc,
		final L2ReadPointerOperand registerRead)
	{
		forceSlotRegister(
			slotIndex,
			effectivePc,
			registerRead.register(),
			registerRead.restriction());
	}

	/**
	 * Associate the specified register with the slot semantic value having the
	 * given index and effective pc.
	 *
	 * @param slotIndex
	 *        The slot index to replace.
	 * @param effectivePc
	 *        The effective pc.
	 * @param register
	 *        The {@link L2Register} that should now be considered the current
	 *        register representing that slot.
	 * @param <T>
	 *        The {@link A_BasicObject} that constrains the register's type.
	 */
	@InnerAccess <T extends A_BasicObject> void forceSlotRegister (
		final int slotIndex,
		final int effectivePc,
		final L2Register<T> register,
		final TypeRestriction<T> restriction)
	{
		// Create a new L2SemanticSlot at the effective pc, representing this
		// newly written value.
		final L2SemanticValue semanticValue =
			topFrame.slot(slotIndex, effectivePc);
		semanticSlots[slotIndex - 1] = semanticValue;
		generator.currentManifest().addBinding(semanticValue, register);
		final @Nullable L2Synonym<L2Register<T>, T> synonym =
			generator.currentManifest().registerToSynonym(register);
		assert synonym != null;
		synonym.setRestriction(restriction);
	}

	/**
	 * Write nil into a new register representing the specified continuation
	 * slot.  The slots are the arguments, then the locals, then the stack
	 * entries.  The slots are numbered starting at 1.
	 *
	 * @param slotIndex
	 *        The one-based index into the virtual continuation's slots.
	 */
	private void nilSlot (final int slotIndex)
	{
		forceSlotRegister(
			slotIndex, instructionDecoder.pc(), generator.constantRegister(nil));
	}

	/**
	 * Answer the next register number, unique within the chunk.
	 *
	 * @return An integer unique within this translator.
	 */
	public int nextUnique ()
	{
		return generator.nextUnique();
	}

	/**
	 * Write instructions to extract the current function, and answer an {@link
	 * L2ReadPointerOperand} for the register that will hold the function
	 * afterward.
	 */
	private L2ReadPointerOperand getCurrentFunction ()
	{
		final L2SemanticValue semanticFunction = topFrame.function();
		final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject> synonym =
			generator.currentManifest().semanticValueToSynonym(
				semanticFunction);
		if (synonym != null)
		{
			return synonym.defaultRegisterRead();
		}
		// We have to get it into a register.
		if (exactFunctionOrNull != null)
		{
			// The exact function is known.
			return generator.constantRegister(exactFunctionOrNull);
		}
		// The exact function isn't known, but we know the raw function, so we
		// statically know the function type.
		final L2WritePointerOperand functionWrite =
			generator.newObjectRegisterWriter(restriction(code.functionType()));
		addInstruction(L2_GET_CURRENT_FUNCTION.instance, functionWrite);
		generator.currentManifest().addBinding(
			semanticFunction, functionWrite.register());
		return functionWrite.read();
	}

	/**
	 * Write instructions to extract a numbered outer from the current function,
	 * and answer an {@link L2ReadPointerOperand} for the register that will
	 * hold the outer value afterward.
	 */
	private L2ReadPointerOperand getOuterRegister (
		final int outerIndex,
		final A_Type outerType)
	{
		final L2SemanticValue semanticOuter = topFrame.outer(outerIndex);
		final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject> synonym =
			generator.currentManifest().semanticValueToSynonym(semanticOuter);
		if (synonym != null)
		{
			return synonym.defaultRegisterRead();
		}
		if (outerType.instanceCount().equalsInt(1)
			&& !outerType.isInstanceMeta())
		{
			// The exact outer is known statically.
			return generator.constantRegister(outerType.instance());
		}
		final L2ReadPointerOperand functionRead = getCurrentFunction();
		final L2WritePointerOperand outerWrite =
			generator.newObjectRegisterWriter(restriction(outerType));
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2IntImmediateOperand(outerIndex),
			functionRead,
			outerWrite);
		generator.currentManifest().addBinding(
			semanticOuter, outerWrite.register());
		return outerWrite.read();
	}

	/**
	 * Write instructions to extract the current reified continuation, and
	 * answer an {@link L2ReadPointerOperand} for the register that will hold
	 * the continuation afterward.
	 */
	private L2ReadPointerOperand getCurrentContinuation ()
	{
		final L2WritePointerOperand continuationTempReg =
			generator.newObjectRegisterWriter(
				restriction(mostGeneralContinuationType()));
		addInstruction(
			L2_GET_CURRENT_CONTINUATION.instance,
			continuationTempReg);
		return continuationTempReg.read();
	}

	/**
	 * Write instructions to extract the current reified continuation, and
	 * answer an {@link L2ReadPointerOperand} for the register that will hold
	 * the continuation afterward.  Move the caller of this continuation back
	 * into the interpreter's current reified continuation field.
	 */
	private L2ReadPointerOperand popCurrentContinuation ()
	{
		final L2WritePointerOperand continuationTempReg =
			generator.newObjectRegisterWriter(
				restriction(mostGeneralContinuationType()));
		addInstruction(
			L2_POP_CURRENT_CONTINUATION.instance,
			continuationTempReg);
		return continuationTempReg.read();
	}

	/**
	 * Capture the latest value returned by the {@link L2_RETURN} instruction in
	 * this {@link Interpreter}.
	 *
	 * @param guaranteedType
	 *        The type the return value is guaranteed to conform to.
	 * @return An {@link L2ReadPointerOperand} that now holds the returned
	 *         value.
	 */
	private L2ReadPointerOperand getLatestReturnValue (
		final A_Type guaranteedType)
	{
		final L2WritePointerOperand writer =
			generator.newObjectRegisterWriter(restriction(guaranteedType));
		addInstruction(
			L2_GET_LATEST_RETURN_VALUE.instance,
			writer);
		return writer.read();
	}

	/**
	 * Capture the function that has just attempted to return via an {@link
	 * L2_RETURN} instruction in this {@link Interpreter}.
	 *
	 * @return An {@link L2ReadPointerOperand} that now holds the function that
	 *         is returning.
	 */
	private L2ReadPointerOperand getReturningFunctionRegister ()
	{
		final L2WritePointerOperand writer =
			generator.newObjectRegisterWriter(
				restriction(mostGeneralFunctionType()));
		addInstruction(
			L2_GET_RETURNING_FUNCTION.instance,
			writer);
		return writer.read();
	}

	/**
	 * Create and add an {@link L2Instruction} with the given {@link
	 * L2Operation} and variable number of {@link L2Operand}s.
	 *
	 * @param operation
	 *        The operation to invoke.
	 * @param operands
	 *        The operands of the instruction.
	 */
	public void addInstruction (
		final L2Operation operation,
		final L2Operand... operands)
	{
		generator.addInstruction(operation, operands);
	}

	/**
	 * Add an {@link L2Instruction}.
	 *
	 * @param instruction
	 *        The instruction to add.
	 */
	public void addInstruction (
		final L2Instruction instruction)
	{
		generator.addInstruction(instruction);
	}

	/**
	 * Generate instruction(s) to move the given {@link AvailObject} into a
	 * fresh writable slot {@link L2Register} with the given slot index.  The
	 * slot it occupies is tagged with the current pc.
	 *
	 * @param value
	 *        The value to move.
	 * @param slotIndex
	 *        The index of the slot in which to write it.
	 */
	private void moveConstantToSlot (
		final A_BasicObject value,
		final int slotIndex)
	{
		final L2ReadPointerOperand constantRegister =
			generator.constantRegister(value);
		forceSlotRegister(slotIndex, instructionDecoder.pc(), constantRegister);
	}

	/**
	 * Generate code to create the current continuation, with a nil caller, then
	 * {@link L2_RETURN_FROM_REIFICATION_HANDLER} – so the calling frames will
	 * also get a chance to add their own nil-caller continuations to the
	 * current {@link StackReifier}.  The execution machinery will then assemble
	 * the chain of continuations, connecting them to any already reified
	 * continuations in the interpreter.
	 *
	 * <p>After reification, the interpreter's next activity depends on the
	 * flags set in the {@link StackReifier} (which was created via code
	 * generated prior to this clause).  If it was for interrupt processing, the
	 * continuation will be stored in the fiber while an interrupt is processed,
	 * then most likely resumed at a later time.  If it was for getting into a
	 * state suitable for creating an L1 label, the top continuation's chunk is
	 * resumed immediately, whereupon the continuation will be popped and
	 * exploded back into registers, and the actual label will be created from
	 * the continuation that was just resumed.</p>
	 *
	 * @param expectedValueOrNull
	 *        A constant type to replace the top-of-stack in the reified
	 *        continuation.  If {@code null}, don't replace the top-of-stack.
	 * @param typeOfEntryPoint
	 *        The kind of {@link ChunkEntryPoint} to re-enter at.
	 */
	public void reify (
		final @Nullable A_Type expectedValueOrNull,
		final ChunkEntryPoint typeOfEntryPoint)
	{
		final L2WritePointerOperand newContinuationRegister =
			generator.newObjectRegisterWriter(
				restriction(mostGeneralContinuationType()));
		final L2BasicBlock onReturnIntoReified =
			generator.createBasicBlock("return into reified continuation");
		final L2BasicBlock afterCreation = generator
			.createBasicBlock("after creation of reified continuation");
		// Create write-slots for when it returns into the reified continuation
		// and explodes the slots into registers.  Also create undefinedSlots to
		// indicate the registers hold nothing until after the explosion. Also
		// capture which semantic values are known to be immutable prior to the
		// reification, because they'll still be immutable (even if not held in
		// any register) after reaching the on-ramp.
		final Set<L2SemanticValue> knownImmutables = new HashSet<>();
		final List<L2ReadPointerOperand> readSlotsBefore =
			new ArrayList<>(numSlots);
		final List<L2WritePointerOperand> writeSlotsAfter =
			new ArrayList<>(numSlots);
		for (int i = 1; i <= numSlots; i++)
		{
			final L2SemanticValue semanticValue = semanticSlot(i);
			if (i == stackp && expectedValueOrNull != null)
			{
				readSlotsBefore.add(
					generator.constantRegister(expectedValueOrNull));
				writeSlotsAfter.add(null);
			}
			else
			{
				final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject>
					synonym = stripNull(
						generator.currentManifest().semanticValueToSynonym(
							semanticValue));
				final L2ReadPointerOperand read = synonym.defaultRegisterRead();
				readSlotsBefore.add(read);
				final TypeRestriction<A_BasicObject> originalRestriction =
					read.restriction();
				final L2WritePointerOperand slotWriter =
					generator.newObjectRegisterWriter(originalRestriction);
				writeSlotsAfter.add(slotWriter);
				if (synonym.hasFlag(KNOWN_IMMUTABLE))
				{
					final Iterator<L2SemanticValue> iterator =
						synonym.semanticValuesIterator();
					while (iterator.hasNext())
					{
						knownImmutables.add(iterator.next());
					}
				}
			}
		}
		// Now generate the reification instructions, ensuring that when
		// returning into the resulting continuation it will enter a block where
		// the slot registers are the new ones we just created.
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			getCurrentFunction(),
			generator.constantRegister(nil),
			new L2IntImmediateOperand(instructionDecoder.pc()),
			new L2IntImmediateOperand(stackp),
			new L2ReadVectorOperand<>(readSlotsBefore),
			newContinuationRegister,
			new L2PcOperand(onReturnIntoReified, new L2ValueManifest()),
			new L2PcOperand(afterCreation, generator.currentManifest()),
			new L2CommentOperand("Create a reification continuation."));

		// Right after creating the continuation.
		generator.startBlock(afterCreation);
		addInstruction(
			L2_RETURN_FROM_REIFICATION_HANDLER.instance,
			new L2ReadVectorOperand<>(
				singletonList(newContinuationRegister.read())));

		// Here it's returning into the reified continuation.
		generator.startBlock(onReturnIntoReified);
		generator.currentManifest().clear();
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(typeOfEntryPoint.offsetInDefaultChunk),
			new L2CommentOperand(
				"If invalid, reenter «default» at "
					+ typeOfEntryPoint.name() + "."));
		final L2ReadPointerOperand popped = popCurrentContinuation();
		for (int i = 1; i <= numSlots; i++)
		{
			final L2WritePointerOperand writeSlot = writeSlotsAfter.get(i - 1);
			if (writeSlot != null)
			{
				final @Nullable A_BasicObject constant =
					writeSlot.register().restriction().constantOrNull;
				if (constant != null)
				{
					// We know the slot contains a particular constant, so don't
					// read it from the continuation.
					generator.currentManifest().addBinding(
						semanticSlot(i),
						generator.constantRegister(constant).register());
				}
				else
				{
					addInstruction(
						L2_EXTRACT_CONTINUATION_SLOT.instance,
						popped,
						new L2IntImmediateOperand(i),
						writeSlot);
					generator.currentManifest().addBinding(
						semanticSlot(i),
						writeSlot.register());
				}
			}
		}
		for (final L2SemanticValue immutableSemanticValue : knownImmutables)
		{
			final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject> synonym =
				generator.currentManifest().semanticValueToSynonym(
					immutableSemanticValue);
			if (synonym != null)
			{
				synonym.setFlag(KNOWN_IMMUTABLE);
			}
		}
	}

	/**
	 * Create an {@link L2PcOperand} with suitable defaults.
	 *
	 * @param targetBlock
	 *        The target {@link L2BasicBlock}.
	 * @param phiRestrictions
	 *        Any restrictions for the manifest along this edge.
	 * @return The new {@link L2PcOperand}.
	 */
	public L2PcOperand edgeTo (
		final L2BasicBlock targetBlock,
		final PhiRestriction... phiRestrictions)
	{
		return new L2PcOperand(
			targetBlock, generator.currentManifest(), phiRestrictions);
	}

	/**
	 * Generate code to extract the current {@link
	 * AvailRuntime#resultDisagreedWithExpectedTypeFunction()} into a new
	 * register, which is returned here.
	 *
	 * @return The new register that will hold the invalid return function.
	 */
	private L2ReadPointerOperand getInvalidResultFunctionRegister ()
	{
		final L2WritePointerOperand invalidResultFunction =
			generator.newObjectRegisterWriter(restriction(
				functionType(
					tuple(
						mostGeneralFunctionType(),
						topMeta(),
						variableTypeFor(ANY.o())),
					bottom())));
		addInstruction(
			L2_GET_INVALID_MESSAGE_RESULT_FUNCTION.instance,
			invalidResultFunction);
		return invalidResultFunction.read();
	}

	/**
	 * A memento to be used for coordinating code generation between the
	 * branches of an {@link InternalLookupTree}.
	 */
	@InnerAccess class InternalNodeMemento
	{
		/** The one-based index of the argument being tested. */
		final int argumentIndexToTest;

		/**
		 * Where to jump if the {@link InternalLookupTree}'s type test is true.
		 */
		final L2BasicBlock passCheckBasicBlock;

		/**
		 * The {@link A_Type} that should be subtracted from argument's possible
		 * type along the path where the type test fails.
		 */
		final A_Type typeToTest;

		/**
		 * Where to jump if the {@link InternalLookupTree}'s type test is false.
		 */
		final L2BasicBlock failCheckBasicBlock;

		/**
		 * A read of the argument register being tested.  This register is the
		 * original input, and has not been strengthened for having passed or
		 * failed the type test.
		 */
		final L2ReadPointerOperand argumentBeforeComparison;

		/**
		 * Construct a new memento.  Make the label something meaningful to
		 * make it easier to decipher.
		 *
		 * @param argumentIndexToTest
		 *        The one-based subscript of the argument being tested.
		 * @param typeToTest
		 *        The type to test the argument against.
		 * @param argumentBeforeComparison
		 *        The register holding the argument prior to the type test.  The
		 *        test produces new register-reads with narrowed types to hold
		 *        the stronger-typed argument.
		 * @param branchLabelCounter
		 *        An int unique to this dispatch tree, monotonically
		 *        allocated at each branch.
		 */
		@InnerAccess InternalNodeMemento (
			final int argumentIndexToTest,
			final A_Type typeToTest,
			final L2ReadPointerOperand argumentBeforeComparison,
			final int branchLabelCounter)
		{
			this.argumentIndexToTest = argumentIndexToTest;
			this.typeToTest = typeToTest;
			this.argumentBeforeComparison = argumentBeforeComparison;
			//noinspection DynamicRegexReplaceableByCompiledPattern
			final String shortTypeName =
				branchLabelCounter
					+ " (arg#"
					+ argumentIndexToTest
					+ " is "
					+ typeToTest.traversed().descriptor().typeTag.name()
						.replace("_TAG", "")
					+ ")";
			this.passCheckBasicBlock = generator.createBasicBlock(
				"pass lookup test #" + shortTypeName);
			this.failCheckBasicBlock = generator.createBasicBlock(
				"fail lookup test #" + shortTypeName);
		}
	}

	/**
	 * A helper that aggregates parameters for polymorphic dispatch inlining.
	 */
	public class CallSiteHelper
	{
		/** The {@link A_Bundle} being dispatched */
		public final A_Bundle bundle;

		/** A Java {@link String} naming the {@link A_Bundle}. */
		public final String quotedBundleName;

		/** A counter for generating unique branch names for this dispatch. */
		public final MutableInt branchLabelCounter = new MutableInt(1);

		/**
		 * The type expected to be returned by invoking the function.  This may
		 * be stronger than the type guaranteed by the VM, which requires a
		 * runtime check.
		 */
		public final A_Type expectedType;

		/**
		 * Bottom in the normal case, but for a super-call this is a tuple type
		 * with the same size as the number of arguments.  For the purpose of
		 * looking up the appropriate {@link A_Definition}, the type union of
		 * each argument's dynamic type and the corresponding entry type from
		 * this field is computed, and that's used for the lookup.
		 */
		public final A_Type superUnionType;

		/** Whether this call site is a super lookup. */
		public final boolean isSuper;

		/** Where to jump to perform the slow lookup. */
		public final L2BasicBlock onFallBackToSlowLookup;

		/**
		 * Where to jump to perform reification, eventually leading to a return
		 * type check after completion.
		 */
		public final L2BasicBlock onReificationWithCheck;

		/**
		 * Where to jump to perform reification without the need for an eventual
		 * return type check.
		 */
		public final L2BasicBlock onReificationNoCheck;

		/**
		 * Where to jump after a completed call to perform a return type check.
		 */
		public final L2BasicBlock afterCallWithCheck;

		/**
		 * Where to jump after a completed call if a return type check isn't
		 * needed.
		 */
		public final L2BasicBlock afterCallNoCheck;

		/**
		 * Where it ends up after the entire call, regardless of whether the
		 * returned value had to be checked or not.
		 */
		public final L2BasicBlock afterEverything;

		/**
		 * Record the fact that this call has produced a value in a particular
		 * register which is to represent the new top-of-stack value.
		 *
		 * @param answerReg
		 *        The register which will already hold the return value at this
		 *        point.  The value has not yet been type checked against the
		 *        expectedType at this point, but it should comply with the type
		 *        guarantees of the VM.
		 */
		public void useAnswer (final L2ReadPointerOperand answerReg)
		{
			final A_Type answerType = answerReg.type();
			if (answerType.isBottom())
			{
				// The VM says we can't actually get here.  Don't bother
				// associating the return value with either the checked or
				// unchecked return result L2SemanticSlot.
				generator.addUnreachableCode();
			}
			else if (answerType.isSubtypeOf(expectedType))
			{
				// Capture it as the checked value L2SemanticSlot.
				forceSlotRegister(stackp, instructionDecoder.pc(), answerReg);
				addInstruction(L2_JUMP.instance, edgeTo(afterCallNoCheck));
			}
			else
			{
				// Capture it as the unchecked return value SemanticSlot by
				// using pc - 1.
				forceSlotRegister(
					stackp, instructionDecoder.pc() - 1, answerReg);
				addInstruction(L2_JUMP.instance, edgeTo(afterCallWithCheck));
			}
		}

		/**
		 * Create the helper, constructing basic blocks that may or may not be
		 * ultimately generated, depending on whether they're reachable.
		 *
		 * @param bundle
		 *        The {@link A_Bundle} being invoked.
		 * @param superUnionType
		 *        The type whose union with the arguments tuple type is used for
		 *        lookup.  This is ⊥ for ordinary calls, and other types for
		 *        super calls.
		 * @param expectedType
		 *        The expected result type that has been strengthened by {@link
		 *        A_SemanticRestriction}s at this call site.  The VM does not
		 *        always guarantee this type will be returned, but it inserts
		 *        runtime checks in the case that it can't prove it.
		 */
		CallSiteHelper (
			final A_Bundle bundle,
			final A_Type superUnionType,
			final A_Type expectedType)
		{
			this.bundle = bundle;
			this.expectedType = expectedType;
			this.superUnionType = superUnionType;
			this.isSuper = !superUnionType.isBottom();
			this.quotedBundleName = bundle.message().atomName().toString();
			this.onFallBackToSlowLookup = generator.createBasicBlock(
				"fall back to slow lookup during " + quotedBundleName);
			this.onReificationWithCheck = generator.createBasicBlock(
				"reify with check during " + quotedBundleName);
			this.onReificationNoCheck = generator
				.createBasicBlock("reify no check during " + quotedBundleName);
			final String string2 = isSuper
				? "after super no-check call of " + quotedBundleName
				: "after call no-check of " + quotedBundleName;
			this.afterCallNoCheck = generator.createBasicBlock(string2);
			final String string1 = isSuper
				? "after super call with check of " + quotedBundleName
				: "after call with check of " + quotedBundleName;
			this.afterCallWithCheck = generator.createBasicBlock(string1);
			final String string = isSuper
				? "after entire super call of " + quotedBundleName
				: "after entire call of " + quotedBundleName;
			this.afterEverything = generator.createBasicBlock(string);
		}
	}

	/**
	 * Generate code to perform a method invocation.  If a superUnionType other
	 * than {@link BottomTypeDescriptor#bottom() bottom} is supplied, produce a
	 * super-directed multimethod invocation.
	 *
	 * @param bundle
	 *        The {@linkplain MessageBundleDescriptor message bundle} to
	 *        invoke.
	 * @param expectedType
	 *        The expected return {@linkplain TypeDescriptor type}.
	 * @param superUnionType
	 *        A tuple type to combine through a type union with the pushed
	 *        arguments' dynamic types, to use during method lookup.  This is
	 *        {@link BottomTypeDescriptor#bottom() bottom} for non-super calls.
	 */
	private void generateCall (
		final A_Bundle bundle,
		final A_Type expectedType,
		final A_Type superUnionType)
	{
		final CallSiteHelper callSiteHelper = new CallSiteHelper(
			bundle, superUnionType, expectedType);
		final A_Method method = bundle.bundleMethod();
		generator.contingentValues =
			generator.contingentValues.setWithElementCanDestroy(method, true);
		final int nArgs = method.numArgs();
		final List<L2ReadPointerOperand> arguments = new ArrayList<>(nArgs);
		final List<A_Type> argumentTypes = new ArrayList<>(nArgs);
		for (int i = nArgs - 1; i >= 0; i--)
		{
			final L2ReadPointerOperand argument = readSlot(stackp + i);
			arguments.add(argument);
			argumentTypes.add(argument.type());
			// No point nilling the first argument, since it'll be overwritten
			// below with a constant move of the expectedType.
			if (i != nArgs - 1)
			{
				moveConstantToSlot(nil, stackp + i);
			}
		}
		// Pop the arguments, but push a slot for the expectedType.
		stackp += nArgs - 1;
		// At this point we've captured and popped the argument registers, and
		// nilled their new SSA versions for reification.  The reification
		// clauses will explicitly ensure the expected type appears in the top
		// of stack position.

		// Determine which applicable definitions have already been expanded in
		// the lookup tree.
		final LookupTree<A_Definition, A_Tuple, Boolean> tree =
			method.testingTree();
		final List<A_Definition> applicableExpandedLeaves = new ArrayList<>();

		final LookupTreeTraverser<A_Definition, A_Tuple, Boolean, Boolean>
			definitionCollector =
				new LookupTreeTraverser<
						A_Definition, A_Tuple, Boolean, Boolean>(
					MethodDescriptor.runtimeDispatcher, TRUE, false)
		{
			@Override
			public Boolean visitPreInternalNode (
				final int argumentIndex, final A_Type argumentType)
			{
				// Ignored.
				return TRUE;
			}

			@Override
			public void visitLeafNode (final A_Tuple lookupResult)
			{
				if (lookupResult.tupleSize() != 1)
				{
					return;
				}
				final A_Definition definition = lookupResult.tupleAt(1);
				// Only inline successful lookups.
				if (!definition.isMethodDefinition())
				{
					return;
				}
				final A_Type signature = definition.bodySignature();
				if (signature.couldEverBeInvokedWith(argumentTypes)
					&& superUnionType.isSubtypeOf(signature.argsTupleType()))
				{
					applicableExpandedLeaves.add(definition);
				}
			}
		};
		definitionCollector.traverseEntireTree(tree);

		if (applicableExpandedLeaves.size() <= maxPolymorphismToInlineDispatch)
		{
			// TODO MvG - Take into account any arguments constrained to be
			// constants even though they're types.  At the moment, some calls
			// still have to check for ⊥'s type (not ⊥), just to report
			// ambiguity, even though the argument's type restriction says it
			// can't actually be ⊥'s type.
			final LookupTreeTraverser<
					A_Definition, A_Tuple, Boolean, InternalNodeMemento>
				traverser = new LookupTreeTraverser<
						A_Definition, A_Tuple, Boolean, InternalNodeMemento>(
					MethodDescriptor.runtimeDispatcher, TRUE, false)
			{
				@Override
				public InternalNodeMemento visitPreInternalNode (
					final int argumentIndex,
					final A_Type argumentType)
				{
					return preInternalVisit(
						callSiteHelper, arguments, argumentIndex, argumentType);
				}

				@Override
				public void visitIntraInternalNode (
					final InternalNodeMemento memento)
				{
					// Every leaf and unexpanded internal node ends with an edge
					// to afterCall* and/or onReification* and/or the
					// unreachableBlock.
					assert !generator.currentlyReachable();
					generator.startBlock(memento.failCheckBasicBlock);
					final L2ReadPointerOperand argBeforeTest =
						memento.argumentBeforeComparison;
					final L2ReadPointerOperand argUponFailure =
						new L2ReadPointerOperand(
							argBeforeTest.register(),
							argBeforeTest.restriction().minusType(
								memento.typeToTest));
					arguments.set(
						memento.argumentIndexToTest - 1, argUponFailure);
				}

				@Override
				public void visitPostInternalNode (
					final InternalNodeMemento memento)
				{
					// Restore the argument type restriction to what it was
					// prior to this node's type test.
					arguments.set(
						memento.argumentIndexToTest - 1,
						memento.argumentBeforeComparison);
					// The leaves already end with jumps.
					assert !generator.currentlyReachable();
				}

				@Override
				public void visitUnexpanded ()
				{
					// This part of the lookup tree wasn't expanded yet, so fall
					// back to the slow dispatch.
					addInstruction(
						L2_JUMP.instance,
						edgeTo(callSiteHelper.onFallBackToSlowLookup));
				}

				@Override
				public void visitLeafNode (final A_Tuple lookupResult)
				{
					leafVisit(
						expectedType, arguments, callSiteHelper, lookupResult);
					assert !generator.currentlyReachable();
				}
			};
			traverser.traverseEntireTree(tree);
		}
		else
		{
			// Always fall back.
			addInstruction(
				L2_JUMP.instance,
				edgeTo(callSiteHelper.onFallBackToSlowLookup));
		}
		assert !generator.currentlyReachable();

		// TODO MvG - I'm not sure this calculation is needed, since the phi
		// for the result types should already produce a type union.
		A_Type tempUnion = bottom();
		for (final A_Definition definition :
			method.definitionsAtOrBelow(argumentTypes))
		{
			if (definition.isMethodDefinition())
			{
				final A_Function function = definition.bodyBlock();
				final A_RawFunction rawFunction = function.code();
				final @Nullable Primitive primitive = rawFunction.primitive();
				final A_Type returnType;
				if (primitive != null)
				{
					final A_Type signatureTupleType =
						rawFunction.functionType().argsTupleType();
					final List<A_Type> intersectedArgumentTypes =
						new ArrayList<>();
					for (int i = 0; i < argumentTypes.size(); i++)
					{
						intersectedArgumentTypes.add(
							argumentTypes.get(i).typeIntersection(
								signatureTupleType.typeAtIndex(i + 1)));
					}
					returnType = primitive.returnTypeGuaranteedByVM(
						rawFunction, intersectedArgumentTypes);
				}
				else
				{
					returnType = rawFunction.functionType().returnType();
				}
				tempUnion = tempUnion.typeUnion(returnType);
			}
		}
		final A_Type unionOfPossibleResults = tempUnion;

		// Now generate the reachable exit clauses for:
		//    1. default lookup,
		//    2. reification leading to return check,
		//    3. reification with no check,
		//    4. after call with return check,
		//    5. after call with no check,
		//    6. after everything.
		// Clause {2.3} entry expects the value in interpreter.latestResult.
		// Clause {4,5} entry expects the value in top-of-stack.
		// There are edges between
		//    1 -> {<2.4>, <3,5>} depending on type guarantees,
		//    2 -> {4}
		//    3 -> {5}
		//    4 -> {6}
		//    5 -> {6}.
		// Clauses with no actual predecessors are not generated.

		// #1: Default lookup.
		generator.startBlock(callSiteHelper.onFallBackToSlowLookup);
		if (generator.currentlyReachable())
		{
			generateSlowPolymorphicCall(
				callSiteHelper, arguments, unionOfPossibleResults);
		}

		// #2: Reification with return check.
		generator.startBlock(callSiteHelper.onReificationWithCheck);
		if (generator.currentlyReachable())
		{
			reify(expectedType, TO_RETURN_INTO);
			// Capture the value being returned into the on-ramp.
			forceSlotRegister(
				stackp,
				instructionDecoder.pc() - 1,
				getLatestReturnValue(unionOfPossibleResults));
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterCallWithCheck));
		}

		// #3: Reification without return check.
		generator.startBlock(callSiteHelper.onReificationNoCheck);
		if (generator.currentlyReachable())
		{
			reify(expectedType, TO_RETURN_INTO);
			// Capture the value being returned into the on-ramp.
			forceSlotRegister(
				stackp,
				instructionDecoder.pc(),
				getLatestReturnValue(unionOfPossibleResults));
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterCallNoCheck));
		}

		// #4: After call with return check.
		generator.startBlock(callSiteHelper.afterCallWithCheck);
		if (generator.currentlyReachable())
		{
			// The unchecked return value will have been put into the register
			// bound to the L2SemanticSlot for the stackp and pc just after the
			// call MINUS ONE.  Check it, moving it to a register that's bound
			// to the L2SemanticSlot for the stackp and pc just after the call.
			generateReturnTypeCheck(expectedType);
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterEverything));
		}

		// #5: After call without return check.
		// Make the version of the stack with the unchecked value available.
		generator.startBlock(callSiteHelper.afterCallNoCheck);
		if (generator.currentlyReachable())
		{
			// The value will have been put into a register bound to the
			// L2SemanticSlot for the stackp and pc just after the call.
			assert generator.currentManifest().semanticValueToSynonym(
					topFrame.slot(stackp, instructionDecoder.pc()))
				!= null;
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterEverything));
		}

		// #6: After everything.  If it's possible to return a valid value from
		// the call, this will be reachable.
		generator.startBlock(callSiteHelper.afterEverything);
	}

	/**
	 * A leaf lookup tree was found at this position in the inlined dispatch.
	 * If it's a singular method definition, embed a call to it, otherwise jump
	 * to the fallback lookup code to reproduce and handle lookup errors.
	 *
	 * @param expectedType
	 *        The expected type from the call.
	 *        // TODO MvG - This looks very fishy, since it's passed as the
	 *        // VM-guaranteed type.
	 * @param arguments
	 *        The list of argument register readers, strengthened by prior type
	 *        tests.
	 * @param callSiteHelper
	 *        The {@link CallSiteHelper} object for this dispatch.
	 * @param solutions
	 *        The {@link A_Tuple} of {@link A_Definition}s at this leaf of the
	 *        lookup tree.  If there's exactly one and it's a method definition,
	 *        the lookup is considered successful, otherwise it's a failed
	 *        lookup.
	 */
	@InnerAccess void leafVisit (
		final A_Type expectedType,
		final List<L2ReadPointerOperand> arguments,
		final CallSiteHelper callSiteHelper,
		final A_Tuple solutions)
	{
		if (!generator.currentlyReachable())
		{
			return;
		}
		if (solutions.tupleSize() == 1)
		{
			final A_Definition solution = solutions.tupleAt(1);
			if (solution.isMethodDefinition())
			{
				generateGeneralFunctionInvocation(
					generator.constantRegister(solution.bodyBlock()),
					arguments,
					expectedType,
					true,
					callSiteHelper);
				assert !generator.currentlyReachable();
				return;
			}
		}
		// Failed dispatches basically never happen, so do the fallback lookup,
		// which will do its own problem reporting.
		addInstruction(
			L2_JUMP.instance,
			edgeTo(callSiteHelper.onFallBackToSlowLookup));
	}

	/**
	 * An expanded internal node has been reached.  Emit a type test to
	 * determine which way to jump.  Answer a new {@link InternalNodeMemento}
	 * to pass along to other visitor operations to coordinate branch targets.
	 *
	 * @param callSiteHelper
	 *        The {@link CallSiteHelper} object for this dispatch.
	 * @param arguments
	 *        The list of argument register readers, strengthened by prior type
	 *        tests.
	 * @param argumentIndexToTest
	 *        The argument number to test here.  This is a one-based index into
	 *        the list of arguments (which is zero-based).
	 * @param typeToTest
	 *        The type to check the argument against.
	 * @return An {@link InternalNodeMemento} which is made available in other
	 *         callbacks for this particular type test node.  It captures branch
	 *         labels, for example.
	 */
	@InnerAccess InternalNodeMemento preInternalVisit (
		final CallSiteHelper callSiteHelper,
		final List<L2ReadPointerOperand> arguments,
		final int argumentIndexToTest,
		final A_Type typeToTest)
	{
		final L2ReadPointerOperand arg = arguments.get(argumentIndexToTest - 1);
		final InternalNodeMemento memento = preInternalVisitForJustTheJumps(
			callSiteHelper, arguments, argumentIndexToTest, typeToTest);

		// Prepare to generate the pass block, if reachable.
		generator.startBlock(memento.passCheckBasicBlock);
		if (!generator.currentlyReachable())
		{
			return memento;
		}
		// Replace the current argument with a pass-strengthened reader.  It'll
		// be replaced with a fail-strengthened reader during the
		// intraInternalNode, then replaced with whatever it was upon entry to
		// this subtree during the postInternalNode.
		final L2ReadPointerOperand passedTestArg =
			new L2ReadPointerOperand(
				arg.register(),
				arg.restriction().intersectionWithType(typeToTest));
		arguments.set(argumentIndexToTest - 1, passedTestArg);
		return memento;
	}

	/**
	 * An expanded internal node has been reached.  Emit a type test to
	 * determine which way to jump.  Answer a new {@link InternalNodeMemento}
	 * to pass along to other visitor operations to coordinate branch targets.
	 * Don't strengthen the tested argument type yet.
	 *
	 * @param callSiteHelper
	 *        The {@link CallSiteHelper} object for this dispatch.
	 * @param arguments
	 *        The list of argument register readers, strengthened by prior type
	 *        tests.
	 * @param argumentIndexToTest
	 *        The argument number to test here.  This is a one-based index into
	 *        the list of arguments (which is zero-based).
	 * @param typeToTest
	 *        The type to check the argument against.
	 * @return An {@link InternalNodeMemento} which is made available in other
	 *         callbacks for this particular type test node.  It captures branch
	 *         labels, for example.
	 */
	private InternalNodeMemento preInternalVisitForJustTheJumps (
		final CallSiteHelper callSiteHelper,
		final List<L2ReadPointerOperand> arguments,
		final int argumentIndexToTest,
		final A_Type typeToTest)
	{
		final L2ReadPointerOperand arg = arguments.get(argumentIndexToTest - 1);
		final InternalNodeMemento memento =
			new InternalNodeMemento(
				argumentIndexToTest,
				typeToTest,
				arg,
				callSiteHelper.branchLabelCounter.value++);
		if (!generator.currentlyReachable())
		{
			// If no paths lead here, don't generate code.  This can happen when
			// we short-circuit type-tests into unconditional jumps, due to the
			// complexity of super calls.  We short-circuit code generation
			// within this entire subtree by performing the same check in each
			// callback.
			return memento;
		}

		final A_Type argType = arg.type();
		final A_Type intersection = argType.typeIntersection(typeToTest);
		if (intersection.isBottom())
		{
			// It will always fail the test.
			addInstruction(
				L2_JUMP.instance, edgeTo(memento.failCheckBasicBlock));
			return memento;
		}

		// Tricky here.  We have the type we want to test for, and we have the
		// argument for which we want to test the type, but we also have an
		// element of the superUnionType to consider.  And that element might be
		// a combination of restrictions and bottoms.  Deal with the easy,
		// common cases first.
		final A_Type superUnionElementType =
			callSiteHelper.superUnionType.typeAtIndex(argumentIndexToTest);

		if (superUnionElementType.isBottom())
		{
			// It's not a super call, or at least this test isn't related to any
			// parts that are supercast upward.
			if (argType.isSubtypeOf(typeToTest))
			{
				// It will always pass the test.
				addInstruction(
					L2_JUMP.instance, edgeTo(memento.passCheckBasicBlock));
				return memento;
			}

			// A runtime test is needed.  Try to special-case small enumeration.
			if (intersection.isEnumeration()
				&& !intersection.isInstanceMeta()
				&& intersection.instanceCount().extractInt()
					<= maxExpandedEqualityChecks)
			{
				// The type is a small non-meta enumeration.  Use equality
				// checks rather than the more general type checks.
				final Iterator<AvailObject> iterator =
					intersection.instances().iterator();
				while (iterator.hasNext())
				{
					final A_BasicObject instance = iterator.next();
					final boolean last = !iterator.hasNext();
					final L2BasicBlock nextCheckOrFail =
						last
							? memento.failCheckBasicBlock
							: generator.createBasicBlock(
								"test next case of enumeration");
					generateJumpIfEqualsConstant(
						arg,
						instance,
						edgeTo(
							memento.passCheckBasicBlock,
							arg.restrictedToValue(instance)),
						edgeTo(
							nextCheckOrFail,
							arg.restrictedWithoutValue(instance)));
					if (!last)
					{
						generator.startBlock(nextCheckOrFail);
					}
				}
				return memento;
			}
			// A runtime test is needed, and it's not a small enumeration.
			addInstruction(
				L2_JUMP_IF_KIND_OF_CONSTANT.instance,
				arg,
				new L2ConstantOperand(typeToTest),
				edgeTo(
					memento.passCheckBasicBlock,
					arg.restrictedToType(typeToTest)),
				edgeTo(
					memento.failCheckBasicBlock,
					arg.restrictedWithoutType(typeToTest)));
			return memento;
		}

		// The argument is subject to a super-cast.
		if (argType.isSubtypeOf(superUnionElementType))
		{
			// The argument's actual type will always be a subtype of the
			// superUnion type, so the dispatch will always be decided by only
			// the superUnion type, which does not vary at runtime.  Decide
			// the branch direction right now.
			addInstruction(
				L2_JUMP.instance,
				edgeTo(
					superUnionElementType.isSubtypeOf(typeToTest)
						? memento.passCheckBasicBlock
						: memento.failCheckBasicBlock));
			return memento;
		}

		// This is the most complex case, where the argument dispatch type is a
		// mixture of supercasts and non-supercasts.  Do it the slow way with a
		// type union.  Technically, the superUnionElementType's recursive tuple
		// structure mimics the call site, so it must have a fixed, finite
		// structure corresponding with occurrences of supercasts syntactically.
		// Thus, in theory we could analyze the superUnionElementType and
		// generate a more complex collection of branches – but this is already
		// a pretty rare case.
		final A_Type argMeta = instanceMeta(argType);
		final L2WritePointerOperand argTypeWrite =
			generator.newObjectRegisterWriter(restriction(argMeta));
		addInstruction(L2_GET_TYPE.instance, arg, argTypeWrite);
		final L2ReadPointerOperand superUnionReg =
			generator.constantRegister(superUnionElementType);
		final L2WritePointerOperand unionReg =
			generator.newObjectRegisterWriter(
				restriction(argMeta.typeUnion(superUnionReg.type())));
		addInstruction(
			L2_TYPE_UNION.instance,
			argTypeWrite.read(),
			superUnionReg,
			unionReg);
		addInstruction(
			L2_JUMP_IF_SUBTYPE_OF_CONSTANT.instance,
			unionReg.read(),
			new L2ConstantOperand(intersection),
			edgeTo(memento.passCheckBasicBlock),
			edgeTo(memento.failCheckBasicBlock));
		return memento;
	}

	/**
	 * Generate conditional branch to either passEdge or failEdge based on
	 * whether the given register equals the given constant value.
	 *
	 * <p>If the constant to compare against is a boolean, check the provenance
	 * of the register.  If it's the result of a suitable comparison primitive,
	 * generate a more efficient compare-and-branch instruction instead of
	 * creating the boolean only to have it compared to a boolean constant.</p>
	 *
	 * <p>If the value of the boolean-producing instruction is not used, it will
	 * eventually be removed as dead code.</p>
	 *
	 * @param registerToTest
	 *        The register whose content should be compared.
	 * @param constantValue
	 *        The constant value to compare against.
	 * @param passEdge
	 *        Where to go if the register's value equals the constant.
	 * @param failEdge
	 *        Where to go if the register's value does not equal the constant.
	 */
	private void generateJumpIfEqualsConstant (
		final L2ReadPointerOperand registerToTest,
		final A_BasicObject constantValue,
		final L2PcOperand passEdge,
		final L2PcOperand failEdge)
	{
		if (constantValue.isBoolean())
		{
			final boolean constantBool =
				constantValue.equals(AtomDescriptor.trueObject());
			final L2Instruction boolSource =
				registerToTest.register().definitionSkippingMoves();
			if (boolSource.operation() == L2_RUN_INFALLIBLE_PRIMITIVE.instance)
			{
				final Primitive primitive =
					L2_RUN_INFALLIBLE_PRIMITIVE.primitiveOf(boolSource);
				if (primitive == P_Equality.instance)
				{
					final List<L2ReadPointerOperand> args =
						L2_RUN_INFALLIBLE_PRIMITIVE.argsOf(boolSource);
					// If either operand of P_Equality is a constant, recurse to
					// allow deeper replacement.
					@Nullable A_BasicObject previousConstant =
						args.get(0).constantOrNull();
					final L2ReadPointerOperand previousRegister;
					if (previousConstant != null)
					{
						previousRegister = args.get(1);
					}
					else
					{
						previousConstant = args.get(1).constantOrNull();
						previousRegister = args.get(0);
					}
					if (previousConstant != null)
					{
						// It's a comparison against a constant.  Recurse to
						// deal with comparing the result of a prior comparison
						// to a boolean.
						generateJumpIfEqualsConstant(
							previousRegister,
							previousConstant,
							constantBool ? passEdge : failEdge,
							constantBool ? failEdge : passEdge);
						return;
					}
					// Neither value is a constant, but we can still do the
					// compare-and-branch without involving Avail booleans.
					addInstruction(
						L2_JUMP_IF_OBJECTS_EQUAL.instance,
						args.get(0),
						args.get(1),
						constantBool ? passEdge : failEdge,
						constantBool ? failEdge : passEdge);
					return;
				}
				else if (primitive == P_IsSubtypeOf.instance)
				{
					// Instance-of testing is done by extracting the type and
					// testing if it's a subtype.  See if the operand to the
					// is-subtype test is a get-type instruction.
					final List<L2ReadPointerOperand> args =
						L2_RUN_INFALLIBLE_PRIMITIVE.argsOf(boolSource);
					final @Nullable A_BasicObject constantType =
						args.get(1).constantOrNull();
					if (constantType != null)
					{
						final L2Instruction typeSource =
							args.get(0).register().definitionSkippingMoves();
						if (typeSource.operation() == L2_GET_TYPE.instance)
						{
							// There's a get-type followed by an is-subtype
							// followed by a compare-and-branch of the result
							// against a constant boolean.  Replace with a
							// branch-if-instance.
							final L2ReadPointerOperand valueSource =
								L2_GET_TYPE.sourceValueOf(typeSource);
							addInstruction(
								L2_JUMP_IF_KIND_OF_CONSTANT.instance,
								valueSource,
								new L2ConstantOperand(constantType),
								constantBool ? passEdge : failEdge,
								constantBool ? failEdge : passEdge);
							return;
						}
						// Perform a branch-if-is-subtype-of instead of checking
						// whether the Avail boolean is true or false.
						addInstruction(
							L2_JUMP_IF_SUBTYPE_OF_CONSTANT.instance,
							args.get(0),
							new L2ConstantOperand(constantType),
							constantBool ? passEdge : failEdge,
							constantBool ? failEdge : passEdge);
						return;
					}
					// We don't have a special branch that compares two
					// non-constant types, so fall through.
				}
				// TODO MvG - We could check for other special cases here, like
				// numeric less-than.  For now, fall through to compare the
				// value against the constant.
			}
		}
		// Generate the general case.
		addInstruction(
			L2_JUMP_IF_EQUALS_CONSTANT.instance,
			registerToTest,
			new L2ConstantOperand(constantValue),
			passEdge,
			failEdge);
	}

	/**
	 * Generate code to invoke a function in a register with arguments in
	 * registers.  Also branch to the appropriate reification and return clauses
	 * depending on whether the returned value is guaranteed to satisfy the
	 * expectedType or not.
	 *
	 * <p>The code generation position is never {@link
	 * L2Generator#currentlyReachable()} after this (Java) generate method
	 * runs.</p>
	 *
	 * <p>The final output from the entire polymorphic call will always be fully
	 * strengthened to the intersection of the VM-guaranteed type and the
	 * expectedType.</p>
	 *
	 * @param functionToCallReg
	 *        The {@link L2ReadPointerOperand} containing the function to
	 *        invoke.
	 * @param arguments
	 *        The {@link List} of {@link L2ReadPointerOperand}s that supply
	 *        arguments to the function.
	 * @param givenGuaranteedResultType
	 *        The type guaranteed by the VM to be returned by the call.
	 * @param tryToGenerateSpecialPrimitiveInvocation
	 *        {@code true} if an attempt should be made to generate a customized
	 *        {@link L2Instruction} sequence for a {@link Primitive} invocation,
	 *        {@code false} otherwise. This should generally be {@code false}
	 *        only to prevent recursion from {@code Primitive} customization.
	 * @param callSiteHelper
	 *        Information about the call being generated.
	 */
	public void generateGeneralFunctionInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final A_Type givenGuaranteedResultType,
		final boolean tryToGenerateSpecialPrimitiveInvocation,
		final CallSiteHelper callSiteHelper)
	{
		assert functionToCallReg.type().isSubtypeOf(mostGeneralFunctionType());

		// Sanity check the number of arguments against the function.  The
		// function type's acceptable arguments tuple type may be bottom,
		// indicating the size is not known.  It may also be a singular integer
		// range (e.g., [3..3]), indicating exactly how many arguments must be
		// supplied.  If it's a variable size, then by the argument
		// contravariance rules, it would require each (not just any) of those
		// sizes on every call, which is a contradiction, although it's allowed
		// as a denormalized uninstantiable type.  For now just treat a spread
		// of sizes like bottom (i.e., the count is not known).
		final int argumentCount = arguments.size();
		final A_Type sizeRange =
			functionToCallReg.type().argsTupleType().sizeRange();
		assert sizeRange.isBottom()
			|| !sizeRange.lowerBound().equals(sizeRange.upperBound())
			|| sizeRange.rangeIncludesInt(argumentCount);

		A_Type guaranteedResultType = givenGuaranteedResultType;
		guaranteedResultType = guaranteedResultType.typeIntersection(
			functionToCallReg.type().returnType());

		final @Nullable A_RawFunction rawFunction =
			determineRawFunction(functionToCallReg);
		if (rawFunction != null)
		{
			final @Nullable Primitive primitive = rawFunction.primitive();
			if (primitive != null)
			{
				final boolean generated;
				if (tryToGenerateSpecialPrimitiveInvocation)
				{
					// We are not recursing here from a primitive override of
					// tryToGenerateSpecialPrimitiveInvocation(), so try to
					// generate a special primitive invocation.
					generated = tryToGenerateSpecialInvocation(
						functionToCallReg,
						rawFunction,
						primitive,
						arguments,
						callSiteHelper);
				}
				else
				{
					// We are recursing here from a primitive override of
					// tryToGenerateSpecialPrimitiveInvocation(), so do not
					// recurse again; just generate the best invocation possible
					// given what we know.
					final A_Type signatureTupleType =
						rawFunction.functionType().argsTupleType();
					final List<A_Type> argumentTypes =
						new ArrayList<>(argumentCount);
					for (int i = 0; i < argumentCount; i++)
					{
						final L2ReadPointerOperand argument = arguments.get(i);
						final A_Type narrowedType =
							argument.type().typeIntersection(
								signatureTupleType.typeAtIndex(i + 1));
						argumentTypes.add(narrowedType);
					}
					if (primitive.fallibilityForArgumentTypes(argumentTypes)
						== CallSiteCannotFail)
					{
						// The primitive cannot fail at this site. Output code
						// to run the primitive as simply as possible, feeding a
						// register with as strong a type as possible.
						final L2WritePointerOperand writer =
							generator.newObjectRegisterWriter(restriction(
								primitive.returnTypeGuaranteedByVM(
									rawFunction, argumentTypes)));
						addInstruction(
							L2_RUN_INFALLIBLE_PRIMITIVE.instance,
							new L2ConstantOperand(rawFunction),
							new L2PrimitiveOperand(primitive),
							new L2ReadVectorOperand<>(arguments),
							writer);
						callSiteHelper.useAnswer(writer.read());
						generated = true;
					}
					else
					{
						generated = false;
					}
				}
				if (generated)
				{
					assert !generator.currentlyReachable();
					return;
				}
			}
		}

		// The function isn't known to be a particular primitive function, or
		// the primitive wasn't able to generate special code for it, so just
		// invoke it like a non-primitive.

		final boolean skipCheck =
			guaranteedResultType.isSubtypeOf(callSiteHelper.expectedType);
		final @Nullable A_Function constantFunction =
			(A_Function) functionToCallReg.constantOrNull();
		final L2BasicBlock successBlock =
			generator.createBasicBlock("successful invocation");
		if (constantFunction != null)
		{
			addInstruction(
				L2_INVOKE_CONSTANT_FUNCTION.instance,
				new L2ConstantOperand(constantFunction),
				new L2ReadVectorOperand<>(arguments),
				edgeTo(successBlock),
				edgeTo(skipCheck
					? callSiteHelper.onReificationNoCheck
					: callSiteHelper.onReificationWithCheck));
		}
		else
		{
			addInstruction(
				L2_INVOKE.instance,
				functionToCallReg,
				new L2ReadVectorOperand<>(arguments),
				edgeTo(successBlock),
				edgeTo(skipCheck
					? callSiteHelper.onReificationNoCheck
					: callSiteHelper.onReificationWithCheck));
		}
		generator.startBlock(successBlock);
		if (skipCheck)
		{
			addInstruction(
				L2_GET_LATEST_RETURN_VALUE.instance,
				writeSlot(
					stackp,
					instructionDecoder.pc(),
					restriction(guaranteedResultType)));
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterCallNoCheck));
		}
		else
		{
			addInstruction(
				L2_GET_LATEST_RETURN_VALUE.instance,
				writeSlot(
					stackp,
					instructionDecoder.pc() - 1,
					restriction(guaranteedResultType)));
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterCallWithCheck));
		}
		assert !generator.currentlyReachable();
	}

	/**
	 * Generate code to perform a type check of the top-of-stack register
	 * against the given expectedType (an {@link A_Type} that has been
	 * strengthened by semantic restrictions).  If the check fails, invoke the
	 * bottom-valued function accessed via {@link
	 * #getInvalidResultFunctionRegister()}, never to return – but synthesizing
	 * a proper continuation in the event of reification while it's running.  If
	 * the check passes, the value will be strengthened in the top-of-stack
	 * register.
	 *
	 * <p>It's incorrect to call this if the register's type is already strong
	 * enough to satisfy the expectedType.</p>
	 *
	 * @param expectedType
	 *        The {@link A_Type} to check the value against.
	 */
	private void generateReturnTypeCheck (final A_Type expectedType)
	{
		final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject> synonym =
			generator.currentManifest().semanticValueToSynonym(
				topFrame.slot(stackp, instructionDecoder.pc() - 1));
		final L2ReadPointerOperand uncheckedValueReg =
			stripNull(synonym).defaultRegisterRead();
		if (uncheckedValueReg.type().isBottom())
		{
			// Bottom has no instances, so we can't get here.  It would be wrong
			// to do this based on the expectedType being bottom, since that's
			// only an erroneous semantic restriction, not a VM problem.
			// NOTE that this test terminates a mutual recursion between this
			// method and generateGeneralFunctionInvocation().
			generator.addUnreachableCode();
			return;
		}

		// Check the return value against the expectedType.
		final L2BasicBlock passedCheck =
			generator.createBasicBlock("passed return check");
		final L2BasicBlock failedCheck =
			generator.createBasicBlock("failed return check");
		if (uncheckedValueReg.type().typeIntersection(expectedType).isBottom())
		{
			// It's impossible to return a valid value here, since the value's
			// type bound and the expected type don't intersect.  Always invoke
			// the bad type handler.
			addInstruction(
				L2_JUMP.instance,
				edgeTo(failedCheck));
		}
		else
		{
			assert !uncheckedValueReg.type().isSubtypeOf(expectedType)
				: "Attempting to create unnecessary type check";
			addInstruction(
				L2_JUMP_IF_KIND_OF_CONSTANT.instance,
				uncheckedValueReg,
				new L2ConstantOperand(expectedType),
				new L2PcOperand(
					passedCheck,
					generator.currentManifest(),
					uncheckedValueReg.restrictedToType(expectedType)),
				new L2PcOperand(
					failedCheck,
					generator.currentManifest(),
					uncheckedValueReg.restrictedWithoutType(expectedType)));
		}

		// The type check failed, so report it.
		generator.startBlock(failedCheck);
		final L2WritePointerOperand variableToHoldValueWrite =
			generator.newObjectRegisterWriter(
				restriction(variableTypeFor(ANY.o())));
		addInstruction(
			L2_CREATE_VARIABLE.instance,
			new L2ConstantOperand(variableTypeFor(ANY.o())),
			variableToHoldValueWrite);
		final L2BasicBlock wroteVariable =
			generator.createBasicBlock("wrote offending value into variable");
		addInstruction(
			L2_SET_VARIABLE_NO_CHECK.instance,
			variableToHoldValueWrite.read(),
			uncheckedValueReg,
			edgeTo(wroteVariable),
			edgeTo(wroteVariable));

		// Whether the set succeeded or failed doesn't really matter, although
		// it should always succeed for this freshly created variable.
		generator.startBlock(wroteVariable);
		// Recurse to generate the call to the failure handler.  Since it's
		// bottom-valued, and can therefore skip the result check, the recursive
		// call won't exceed two levels deep.
		final L2BasicBlock onReificationInHandler = generator
			.createBasicBlock("reification in failed return check handler");
		addInstruction(
			L2_INVOKE.instance,
			getInvalidResultFunctionRegister(),
			new L2ReadVectorOperand<>(
				asList(
					getReturningFunctionRegister(),
					generator.constantRegister(expectedType),
					variableToHoldValueWrite.read())),
			generator.unreachablePcOperand(),
			edgeTo(onReificationInHandler));

		// Reification has been requested while the call is in progress.
		generator.startBlock(onReificationInHandler);
		reify(bottom(), TO_RETURN_INTO);
		generator.addUnreachableCode();

		// Generate the much more likely passed-check flow.
		generator.startBlock(passedCheck);
		if (generator.currentlyReachable())
		{
			forceSlotRegister(
				stackp,
				instructionDecoder.pc(),
				uncheckedValueReg.register(),
				uncheckedValueReg.restriction().intersection(
					restriction(expectedType)));
		}
	}

	/**
	 * Attempt to create a more specific instruction sequence than just an
	 * {@link L2_INVOKE}.  In particular, see if the {@code functionToCallReg}
	 * is known to contain a constant function (a common case) which is an
	 * inlineable primitive, and if so, delegate this opportunity to the
	 * primitive.
	 *
	 * <p>We must either answer {@code false} and generate no code, or answer
	 * {@code true} and generate code that has the same effect as having run the
	 * function in the register without fear of reification or abnormal control
	 * flow.  A folded primitive, for example, can generate a simple {@link
	 * L2_MOVE_CONSTANT} into the top-of-stack register and answer true.</p>
	 *
	 * @param functionToCallReg
	 *        The register containing the {@linkplain A_Function function} to
	 *        invoke.
	 * @param rawFunction
	 *        The {@linkplain A_RawFunction raw function} being invoked.
	 * @param primitive
	 *        The {@link Primitive} being invoked.
	 * @param arguments
	 *        The arguments to supply to the function.
	 * @param callSiteHelper
	 *        Information about the method call site having its dispatch tree
	 *        inlined.  It also contains merge points for this call, so if a
	 *        specific code generation happens it should jump to one of these.
	 * @return {@code true} if a special instruction sequence was generated,
	 *         {@code false} otherwise.
	 */
	private boolean tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final Primitive primitive,
		final List<L2ReadPointerOperand> arguments,
		final CallSiteHelper callSiteHelper)
	{
		final int argumentCount = arguments.size();
		if (primitive.hasFlag(CanFold))
		{
			// It can be folded, if supplied with constants.
			final List<AvailObject> constants = new ArrayList<>(argumentCount);
			for (final L2ReadPointerOperand regRead : arguments)
			{
				final @Nullable A_BasicObject constant =
					regRead.constantOrNull();
				if (constant == null)
				{
					break;
				}
				constants.add((AvailObject) constant);
			}
			if (constants.size() == argumentCount)
			{
				// Fold the primitive.  A foldable primitive must not
				// require access to the enclosing function or its code.
				final @Nullable A_Function savedFunction = interpreter.function;
				interpreter.function = null;
				final String savedDebugModeString = interpreter.debugModeString;
				if (Interpreter.debugL2)
				{
					Interpreter.log(
						Interpreter.loggerDebugL2,
						Level.FINER,
						"{0}FOLD {1}:",
						interpreter.debugModeString,
						primitive.name());
				}
				final Result success;
				try
				{
					interpreter.argsBuffer.clear();
					interpreter.argsBuffer.addAll(constants);
					success = primitive.attempt(interpreter);
				}
				finally
				{
					interpreter.debugModeString = savedDebugModeString;
					interpreter.function = savedFunction;
				}

				if (success == SUCCESS)
				{
					callSiteHelper.useAnswer(
						generator.constantRegister(
							interpreter.latestResult().makeImmutable()));
					return true;
				}
				// The primitive failed with the supplied arguments,
				// which it's allowed to do even if it CanFold.
				assert success == FAILURE;
				assert !primitive.hasFlag(CannotFail);
			}
		}

		// The primitive can't be folded, so let it generate its own code
		// equivalent to invocation.
		final A_Type signatureTupleType =
			rawFunction.functionType().argsTupleType();
		final List<A_Type> narrowedArgTypes = new ArrayList<>(argumentCount);
		final List<L2ReadPointerOperand> narrowedArguments =
			new ArrayList<>(argumentCount);
		for (int i = 0; i < argumentCount; i++)
		{
			final L2ReadPointerOperand argument = arguments.get(i);
			final A_Type narrowedType = argument.type().typeIntersection(
				signatureTupleType.typeAtIndex(i + 1));
			narrowedArgTypes.add(narrowedType);
			narrowedArguments.add(
				new L2ReadPointerOperand(
					argument.register(),
					argument.restriction().intersection(
						restriction(narrowedType))));
		}
		final boolean generated =
			primitive.tryToGenerateSpecialPrimitiveInvocation(
				functionToCallReg,
				rawFunction,
				narrowedArguments,
				narrowedArgTypes,
				this,
				callSiteHelper);
		if (generated && generator.currentlyReachable())
		{
			// The top-of-stack was replaced, but it wasn't convenient to do
			// a jump to the appropriate exit handlers.  Do that now.
			callSiteHelper.useAnswer(readSlot(stackp));
		}
		return generated;
	}

	/**
	 * Given a register that holds the function to invoke, answer either the
	 * {@link A_RawFunction} it will be known to run, or {@code null}.
	 *
	 * @param functionToCallReg
	 *        The {@link L2ReadPointerOperand} containing the function to
	 *        invoke.
	 * @return Either {@code null} or the function's {@link A_RawFunction}.
	 */
	private static @Nullable A_RawFunction determineRawFunction (
		final L2ReadPointerOperand functionToCallReg)
	{
		final @Nullable A_Function functionIfKnown =
			(A_Function) functionToCallReg.constantOrNull();
		if (functionIfKnown != null)
		{
			// The exact function is known.
			return functionIfKnown.code();
		}
		// See if we can at least find out the raw function that the function
		// was created from.
		final L2Instruction functionDefinition =
			functionToCallReg.register().definitionSkippingMoves();
		return functionDefinition.operation().getConstantCodeFrom(
			functionDefinition);
	}

	/**
	 * Given a register that will hold a tuple, check that the tuple has the
	 * number of elements and statically satisfies the corresponding provided
	 * type constraints.  If so, generate code and answer a list of register
	 * reads corresponding to the elements of the tuple; otherwise, generate no
	 * code and answer null.
	 *
	 * <p>Depending on the source of the tuple, this may cause the creation of
	 * the tuple to be entirely elided.</p>
	 *
	 * @param tupleReg
	 *        The {@link L2ObjectRegister} containing the tuple.
	 * @param requiredTypes
	 *        The required {@linkplain A_Type types} against which to check the
	 *        tuple's own type.
	 * @return A {@link List} of {@link L2ReadPointerOperand}s corresponding to
	 *         the tuple's elements, or {@code null} if the tuple could not be
	 *         proven to have the required shape and type.
	 */
	public @Nullable List<L2ReadPointerOperand> explodeTupleIfPossible (
		final L2ReadPointerOperand tupleReg,
		final List<A_Type> requiredTypes)
	{
		// First see if there's enough type information available about the
		// tuple.
		final A_Type tupleType = tupleReg.type();
		final A_Type tupleTypeSizes = tupleType.sizeRange();
		if (!tupleTypeSizes.upperBound().isInt()
			|| !tupleTypeSizes.lowerBound().equals(tupleTypeSizes.upperBound()))
		{
			// The exact tuple size is not known.  Give up.
			return null;
		}
		final int tupleSize = tupleTypeSizes.upperBound().extractInt();
		if (tupleSize != requiredTypes.size())
		{
			// The tuple is the wrong size.
			return null;
		}

		// Check the tuple elements for type safety.
		for (int i = 1; i <= tupleSize; i++)
		{
			if (!tupleType.typeAtIndex(i).isInstanceOf(
				requiredTypes.get(i - 1)))
			{
				// This tuple element's type isn't strong enough.
				return null;
			}
		}

		// Check the tuple element types against the required types.
		for (int i = 1; i <= tupleSize; i++)
		{
			if (!tupleType.typeAtIndex(i).isInstanceOf(
				requiredTypes.get(i - 1)))
			{
				// This tuple element's type isn't strong enough.  Give up.
				return null;
			}
		}

		// At this point we know the tuple has the right type.  If we know where
		// the tuple was created, use the registers that provided values to the
		// creation.
		final L2Instruction tupleDefinitionInstruction =
			tupleReg.register().definitionSkippingMoves();
		if (tupleDefinitionInstruction.operation() == L2_CREATE_TUPLE.instance)
		{
			return L2_CREATE_TUPLE.tupleSourceRegistersOf(
				tupleDefinitionInstruction);
		}

		// We have to extract the elements.
		final List<L2ReadPointerOperand> elementReaders =
			new ArrayList<>(tupleSize);
		for (int i = 1; i <= tupleSize; i++)
		{
			final A_Type elementType = tupleType.typeAtIndex(i);
			final L2WritePointerOperand elementWriter =
				generator.newObjectRegisterWriter(restriction(elementType));
			addInstruction(
				L2_TUPLE_AT_CONSTANT.instance,
				tupleReg,
				new L2IntImmediateOperand(i),
				elementWriter);
			elementReaders.add(elementWriter.read());
		}
		return elementReaders;
	}

	/**
	 * Generate a slower, but much more compact invocation of a polymorphic
	 * method call.  The slots have already been adjusted to be consistent with
	 * having popped the arguments and pushed the expected type.
	 *
	 * @param callSiteHelper
	 *        Information about the method call site.
	 * @param arguments
	 *        The list of argument registers to use for the call.
	 * @param guaranteedReturnType
	 *        The type that the VM guarantees to produce for this call site.
	 */
	private void generateSlowPolymorphicCall (
		final CallSiteHelper callSiteHelper,
		final List<L2ReadPointerOperand> arguments,
		final A_Type guaranteedReturnType)
	{
		final A_Bundle bundle = callSiteHelper.bundle;
		final A_Method method = bundle.bundleMethod();
		final int nArgs = method.numArgs();
		final L2BasicBlock lookupSucceeded = generator.createBasicBlock(
			"lookup succeeded for " + callSiteHelper.quotedBundleName);
		final L2BasicBlock lookupFailed = generator.createBasicBlock(
			"lookup failed for " + callSiteHelper.quotedBundleName);
		final L2BasicBlock onReificationDuringFailure =
			generator.createBasicBlock(
				"reify in method lookup failure handler for "
				+ callSiteHelper.quotedBundleName);
		final L2WritePointerOperand errorCodeReg =
			generator.newObjectRegisterWriter(restriction(TOP.o()));

		final List<A_Type> argumentTypes = new ArrayList<>();
		for (int i = 1; i <= nArgs; i++)
		{
			argumentTypes.add(
				arguments.get(i - 1).type().typeUnion(
					callSiteHelper.superUnionType.typeAtIndex(i)));
		}

		final List<A_Function> possibleFunctions = new ArrayList<>();
		for (final A_Definition definition :
			bundle.bundleMethod().definitionsAtOrBelow(argumentTypes))
		{
			if (definition.isMethodDefinition())
			{
				possibleFunctions.add(definition.bodyBlock());
			}
		}
		final A_Type functionTypeUnion = enumerationWith(
			setFromCollection(possibleFunctions));
		final L2WritePointerOperand functionReg =
			generator.newObjectRegisterWriter(restriction(functionTypeUnion));

		if (!callSiteHelper.isSuper)
		{
			// Not a super-call.
			addInstruction(
				L2_LOOKUP_BY_VALUES.instance,
				new L2SelectorOperand(bundle),
				new L2ReadVectorOperand<>(arguments),
				functionReg,
				errorCodeReg,
				edgeTo(
					lookupSucceeded,
					functionReg.read().restrictedToType(functionTypeUnion)),
				edgeTo(
					lookupFailed,
					errorCodeReg.read().restrictedToType(
						L2_LOOKUP_BY_VALUES.lookupErrorsType)));
		}
		else
		{
			// Extract a tuple type from the runtime types of the arguments,
			// take the type union with the superUnionType, then perform a
			// lookup-by-types using that tuple type.
			final List<L2ReadPointerOperand> argTypeRegs =
				new ArrayList<>(nArgs);
			for (int i = 1; i <= nArgs; i++)
			{
				final L2ReadPointerOperand argReg = arguments.get(i - 1);
				final A_Type argStaticType = argReg.type();
				final A_Type superUnionElementType =
					callSiteHelper.superUnionType.typeAtIndex(i);
				final L2ReadPointerOperand argTypeReg;
				if (argStaticType.isSubtypeOf(superUnionElementType))
				{
					// The lookup is entirely determined by the super-union.
					argTypeReg =
						generator.constantRegister(superUnionElementType);
				}
				else
				{
					final A_Type typeBound =
						argStaticType.typeUnion(superUnionElementType);
					final L2WritePointerOperand argTypeWrite =
						generator.newObjectRegisterWriter(
							restriction(instanceMeta(typeBound)));
					if (superUnionElementType.isBottom())
					{
						// Only this argument's actual type matters.
						addInstruction(
							L2_GET_TYPE.instance, argReg, argTypeWrite);
					}
					else
					{
						// The lookup is constrained by the actual argument's
						// type *and* the super-union.  This is possible because
						// this is a top-level argument, but it's the leaf
						// arguments that individually specify supercasts.
						final L2WritePointerOperand originalArgTypeWrite =
							generator.newObjectRegisterWriter(
								restriction(instanceMeta(typeBound)));
						addInstruction(
							L2_GET_TYPE.instance, argReg, originalArgTypeWrite);
						addInstruction(
							L2_TYPE_UNION.instance,
							originalArgTypeWrite.read(),
							generator.constantRegister(superUnionElementType),
							argTypeWrite);
					}
					argTypeReg = argTypeWrite.read();
				}
				argTypeRegs.add(argTypeReg);
			}
			addInstruction(
				L2_LOOKUP_BY_TYPES.instance,
				new L2SelectorOperand(bundle),
				new L2ReadVectorOperand<>(argTypeRegs),
				functionReg,
				errorCodeReg,
				edgeTo(
					lookupSucceeded,
					functionReg.read().restrictedToType(functionTypeUnion)),
				edgeTo(
					lookupFailed,
					errorCodeReg.read().restrictedToType(
						L2_LOOKUP_BY_VALUES.lookupErrorsType)));
		}
		// At this point, we've attempted to look up the method, and either
		// jumped to lookupSucceeded with functionReg set to the body function,
		// or jumped to lookupFailed with errorCodeReg set to the lookup error
		// code.

		// Emit the lookup failure case.
		generator.startBlock(lookupFailed);
		final L2WritePointerOperand invalidSendReg =
			generator.newObjectRegisterWriter(
				restriction(invalidMessageSendFunctionType));
		addInstruction(
			L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
			invalidSendReg);
		// Collect the argument types into a tuple type.
		final List<A_Type> argTypes = new ArrayList<>(arguments.size());
		for (final L2ReadPointerOperand argument : arguments)
		{
			argTypes.add(argument.type());
		}
		final L2WritePointerOperand argumentsTupleWrite =
			generator.newObjectRegisterWriter(restriction(
				tupleTypeForSizesTypesDefaultType(
					singleInt(nArgs), tupleFromList(argTypes), bottom())));
		addInstruction(
			L2_CREATE_TUPLE.instance,
			new L2ReadVectorOperand<>(arguments),
			argumentsTupleWrite);
		addInstruction(
			L2_INVOKE.instance,
			invalidSendReg.read(),
			new L2ReadVectorOperand<>(
				asList(
					errorCodeReg.read(),
					generator.constantRegister(method),
					argumentsTupleWrite.read())),
			generator.unreachablePcOperand(),
			edgeTo(onReificationDuringFailure));

		// Reification has been requested while the failure call is in progress.
		generator.startBlock(onReificationDuringFailure);
		reify(bottom(), TO_RETURN_INTO);
		generator.addUnreachableCode();

		// Now invoke the method definition's body.  We've already examined all
		// possible method definition bodies to see if they all conform with the
		// expectedType, and captured that in alwaysSkipResultCheck.
		generator.startBlock(lookupSucceeded);
		generateGeneralFunctionInvocation(
			functionReg.read(),
			arguments,
			guaranteedReturnType,
			true,
			callSiteHelper);
	}

	/**
	 * Emit code to check for an interrupt and service it if necessary,
	 * including generation of the subsequently continued on-ramp.  The
	 * generated code should only be reachable at positions that are effectively
	 * between L1 nybblecodes, since during such an interrupt any {@link
	 * L2Chunk}s can be invalidated.  Not *all* positions between nybblecodes
	 * need to check for interrupts, but there shouldn't be an arbitrarily large
	 * amount of time that passes between when an interrupt is indicated and
	 * when it is serviced.
	 */
	private void emitInterruptOffRamp ()
	{
		final L2BasicBlock serviceInterrupt =
			generator.createBasicBlock("service interrupt");
		final L2BasicBlock merge =
			generator.createBasicBlock("merge after possible interrupt");

		addInstruction(
			L2_JUMP_IF_INTERRUPT.instance,
			edgeTo(serviceInterrupt),
			edgeTo(merge));

		generator.startBlock(serviceInterrupt);
		// Service the interrupt:  Generate the reification instructions,
		// ensuring that when returning into the resulting continuation, it will
		// enter a block where the slot registers are the new ones we just
		// created.  After creating the continuation, actually service the
		// interrupt.

		// Reify everybody else, starting at the caller.
		final L2BasicBlock onReification =
			generator.createBasicBlock("on reification");
		addInstruction(
			L2_REIFY.instance,
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(
				StatisticCategory.INTERRUPT_OFF_RAMP_IN_L2.ordinal()),
			new L2PcOperand(onReification, generator.currentManifest()));
		generator.startBlock(onReification);

		// When the lambda below runs, it's generating code at the point where
		// continuationReg will have the new continuation.
		reify(null, TO_RESUME);
		addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(merge, generator.currentManifest()));
		// Merge the flow (reified and continued, versus not reified).
		generator.startBlock(merge);
		// And now... either we're back or we never left.
	}

	/**
	 * Emit the specified variable-reading instruction, and an off-ramp to deal
	 * with the case that the variable is unassigned.
	 *
	 * @param getOperation
	 *        The {@linkplain L2Operation#isVariableGet() variable reading}
	 *        {@linkplain L2Operation operation}.
	 * @param variable
	 *        The location of the {@linkplain A_Variable variable}.
	 * @param makeImmutable
	 *        {@code true} if the extracted value should be made immutable,
	 *        otherwise {@code false}.
	 * @return The {@link L2ReadPointerOperand} into which the variable's value
	 *         will be written, including having made it immutable if requested.
	 */
	public L2ReadPointerOperand emitGetVariableOffRamp (
		final L2Operation getOperation,
		final L2ReadPointerOperand variable,
		final boolean makeImmutable)
	{
		assert getOperation.isVariableGet();
		final L2BasicBlock success =
			generator.createBasicBlock("successfully read variable");
		final L2BasicBlock failure =
			generator.createBasicBlock("failed to read variable");
		final L2BasicBlock onReificationDuringFailure =
			generator.createBasicBlock(
				"reify in read variable failure handler");

		// Emit the specified get-variable instruction variant.
		final L2WritePointerOperand valueWrite =
			generator.newObjectRegisterWriter(
				restriction(variable.type().readType()));
		addInstruction(
			getOperation,
			variable,
			valueWrite,
			edgeTo(success),
			edgeTo(failure));

		// Emit the failure path. Unbind the destination of the variable get in
		// this case, since it won't have been populated (by definition,
		// otherwise we wouldn't have failed).
		generator.startBlock(failure);
		final L2WritePointerOperand unassignedReadFunction =
			generator.newObjectRegisterWriter(
				restriction(unassignedVariableReadFunctionType));
		addInstruction(
			L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION.instance,
			unassignedReadFunction);
		addInstruction(
			L2_INVOKE.instance,
			unassignedReadFunction.read(),
			new L2ReadVectorOperand<>(emptyList()),
			generator.unreachablePcOperand(),
			edgeTo(onReificationDuringFailure));

		// Reification has been requested while the failure call is in progress.
		generator.startBlock(onReificationDuringFailure);
		reify(bottom(), TO_RETURN_INTO);
		generator.addUnreachableCode();

		// End with the success path.
		generator.startBlock(success);
		return makeImmutable
			? generator.makeImmutable(valueWrite.read())
			: valueWrite.read();
	}

	/**
	 * Emit the specified variable-writing instruction, and an off-ramp to deal
	 * with the case that the variable has {@linkplain VariableAccessReactor
	 * write reactors} but {@linkplain Interpreter#traceVariableWrites()
	 * variable write tracing} is disabled.
	 *
	 * @param setOperation
	 *        The {@linkplain L2Operation#isVariableSet() variable reading}
	 *        {@linkplain L2Operation operation}.
	 * @param variable
	 *        The location of the {@linkplain A_Variable variable}.
	 * @param newValue
	 *        The location of the new value.
	 */
	public void emitSetVariableOffRamp (
		final L2Operation setOperation,
		final L2ReadPointerOperand variable,
		final L2ReadPointerOperand newValue)
	{
		assert setOperation.isVariableSet();
		final L2BasicBlock success =
			generator.createBasicBlock("set local success");
		final L2BasicBlock failure =
			generator.createBasicBlock("set local failure");
		final L2BasicBlock onReificationDuringFailure =
			generator.createBasicBlock("reify during set local failure");
		// Emit the set-variable instruction.
		addInstruction(
			setOperation,
			variable,
			newValue,
			edgeTo(success),
			edgeTo(failure));

		// Emit the failure path.
		generator.startBlock(failure);
		final L2WritePointerOperand observeFunction =
			generator.newObjectRegisterWriter(
				restriction(implicitObserveFunctionType));
		addInstruction(
			L2_GET_IMPLICIT_OBSERVE_FUNCTION.instance,
			observeFunction);
		final L2WritePointerOperand variableAndValueTupleReg =
			generator.newObjectRegisterWriter(restriction(
				tupleTypeForTypes(variable.type(), newValue.type())));
		addInstruction(
			L2_CREATE_TUPLE.instance,
			new L2ReadVectorOperand<>(asList(variable, newValue)),
			variableAndValueTupleReg);
		// Note: the handler block's value is discarded; also, since it's not a
		// method definition, it can't have a semantic restriction.
		addInstruction(
			L2_INVOKE.instance,
			observeFunction.read(),
			new L2ReadVectorOperand<>(
				asList(
					generator
						.constantRegister(Interpreter.assignmentFunction()),
					variableAndValueTupleReg.read())),
			edgeTo(success),
			edgeTo(onReificationDuringFailure));

		generator.startBlock(onReificationDuringFailure);
		reify(TOP.o(), TO_RETURN_INTO);
		addInstruction(
			L2_JUMP.instance,
			edgeTo(success));

		// End with the success block.  Note that the failure path can lead here
		// if the implicit-observe function returns.
		generator.startBlock(success);
	}

	/** Statistic for generating an L2Chunk's preamble. */
	private static final Statistic preambleGenerationStat =
		new Statistic(
			"(generate preamble)",
			StatisticReport.L1_NAIVE_TRANSLATION_TIME);

	private static final Statistic[] levelOneGenerationStats =
		Arrays.stream(L1Operation.values())
			.map(operation -> new Statistic(
				operation.name(), StatisticReport.L1_NAIVE_TRANSLATION_TIME))
			.toArray(Statistic[]::new);

	/**
	 * For each level one instruction, write a suitable transliteration into
	 * level two instructions.
	 */
	void translateL1Instructions ()
	{
		final long timeAtStartOfTranslation = captureNanos();
		generator.initialBlock.makeIrremovable();
		generator.startBlock(generator.initialBlock);
		final @Nullable Primitive primitive = code.primitive();
		if (primitive != null)
		{
			// Try the primitive, automatically returning if successful.
			addInstruction(L2_TRY_PRIMITIVE.instance);
			if (primitive.hasFlag(CannotFail))
			{
				// Infallible primitives don't need any other L2 code.
				return;
			}
		}
		// Fallible primitives and non-primitives need this additional entry
		// point.
		generator.afterOptionalInitialPrimitiveBlock =
			generator.createBasicBlock("after optional primitive");
		generator.afterOptionalInitialPrimitiveBlock.makeIrremovable();
		addInstruction(
			L2_JUMP.instance,
			edgeTo(generator.afterOptionalInitialPrimitiveBlock));

		generator.startBlock(generator.afterOptionalInitialPrimitiveBlock);
		generator.currentManifest().clear();
		// While it's true that invalidation may only take place when no Avail
		// code is running (even when evicting old chunks), and it's also the
		// case that invalidation causes the chunk to be disconnected from its
		// compiled code, it's still the case that a *label* continuation
		// created at an earlier time still refers to the invalid chunk.  Ensure
		// it can fall back gracefully to L1 (the default chunk) by entering it
		// at offset 0.  There can't be a primitive for such continuations, so
		// offset 1 (after the L2_TRY_OPTIONAL_PRIMITIVE) would also work.
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(0),
			new L2CommentOperand(
				"If invalid, reenter «default» at the beginning."));

		// Do any reoptimization before capturing arguments.
		if (generator.optimizationLevel == OptimizationLevel.UNOPTIMIZED)
		{
			// Optimize it again if it's called frequently enough.
			code.countdownToReoptimize(
				L2Chunk.countdownForNewlyOptimizedCode());
			addInstruction(
				L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
				new L2IntImmediateOperand(
					OptimizationLevel.FIRST_TRANSLATION.ordinal()),
				new L2IntImmediateOperand(0));
			// If it was reoptimized, it would have jumped to the
			// afterOptionalInitialPrimitiveBlock in the new chunk.
		}

		// Capture the arguments.
		final int numArgs = code.numArgs();
		if (numArgs > 0)
		{
			final A_Type tupleType = code.functionType().argsTupleType();
			for (int i = 1; i <= numArgs; i++)
			{
				final L2WritePointerOperand argReg = writeSlot(
					i,
					instructionDecoder.pc(),
					restriction(tupleType.typeAtIndex(i)));
				addInstruction(
					L2_GET_ARGUMENT.instance,
					new L2IntImmediateOperand(i),
					argReg);
			}
		}

		// Create the locals.
		final int numLocals = code.numLocals();
		for (int local = 1; local <= numLocals; local++)
		{
			final A_Type localType = code.localTypeAt(local);
			addInstruction(
				L2_CREATE_VARIABLE.instance,
				new L2ConstantOperand(localType),
				writeSlot(
					numArgs + local,
					instructionDecoder.pc(),
					restriction(localType)));
		}

		// Capture the primitive failure value in the first local if applicable.
		if (primitive != null)
		{
			assert !primitive.hasFlag(CannotFail);
			// Move the primitive failure value into the first local.  This
			// doesn't need to support implicit observation, so no off-ramp
			// is generated.
			final L2BasicBlock success = generator.createBasicBlock("success");
			addInstruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				readSlot(numArgs + 1),
				getLatestReturnValue(code.localTypeAt(1).writeType()),
				edgeTo(success),
				generator.unreachablePcOperand());
			generator.startBlock(success);
		}

		// Nil the rest of the stack slots.
		for (int i = numArgs + numLocals + 1; i <= numSlots; i++)
		{
			nilSlot(i);
		}

		// Check for interrupts. If an interrupt is discovered, then reify and
		// process the interrupt.  When the chunk resumes, it will explode the
		// continuation again.
		emitInterruptOffRamp();

		// Capture the time it took to generate the whole preamble.
		final int interpreterIndex = interpreter.interpreterIndex;
		preambleGenerationStat.record(
			captureNanos() - timeAtStartOfTranslation, interpreterIndex);

		// Transliterate each level one nybblecode into L2Instructions.
		while (!instructionDecoder.atEnd())
		{
			final long before = captureNanos();
			final L1Operation operation = instructionDecoder.getOperation();
			operation.dispatch(this);
			levelOneGenerationStats[operation.ordinal()].record(
				captureNanos() - before, interpreterIndex);
		}

		// Generate the implicit return after the instruction sequence.
		final L2ReadPointerOperand readResult = readSlot(stackp);
		addInstruction(L2_RETURN.instance, readResult);
		generator.currentManifest().addBinding(
			topFrame.result(), readResult.register());
		assert stackp == numSlots;
		stackp = Integer.MIN_VALUE;

		if (generator.unreachableBlock != null
			&& generator.unreachableBlock.predecessorEdgesCount() > 0)
		{
			// Generate the unreachable block.
			generator.startBlock(generator.unreachableBlock);
			addInstruction(L2_UNREACHABLE_CODE.instance);
		}
	}

	/**
	 * Generate the {@link L2ControlFlowGraph} of {@link L2Instruction}s for the
	 * {@link L2Chunk#unoptimizedChunk}.
	 *
	 * @param initialBlock
	 *        The block to initially entry the default chunk for a call.
	 * @param reenterFromRestartBlock
	 *        The block to reenter to {@link P_RestartContinuation} an
	 *        {@link A_Continuation}.
	 * @param loopBlock
	 *        The main loop of the interpreter.
	 * @param reenterFromCallBlock
	 *        The entry point for returning into a reified continuation.
	 * @param reenterFromInterruptBlock
	 *        The entry point for resuming from an interrupt.
	 * @return The {@link L2ControlFlowGraph} for the default chunk.
	 */
	public static L2ControlFlowGraph generateDefaultChunkControlFlowGraph (
		final L2BasicBlock initialBlock,
		final L2BasicBlock reenterFromRestartBlock,
		final L2BasicBlock loopBlock,
		final L2BasicBlock reenterFromCallBlock,
		final L2BasicBlock reenterFromInterruptBlock,
		final L2BasicBlock unreachableBlock)
	{
		initialBlock.makeIrremovable();
		loopBlock.makeIrremovable();
		reenterFromRestartBlock.makeIrremovable();
		reenterFromCallBlock.makeIrremovable();
		reenterFromInterruptBlock.makeIrremovable();
		unreachableBlock.makeIrremovable();

		// 0. First try to run it as a primitive.
		final L2ControlFlowGraph controlFlowGraph = new L2ControlFlowGraph();
		controlFlowGraph.startBlock(initialBlock);
		initialBlock.addInstruction(new L2Instruction(
			initialBlock, L2_TRY_OPTIONAL_PRIMITIVE.instance));
		// Only if the primitive fails should we even consider optimizing the
		// fallback code.

		// 1. Update counter and maybe optimize *before* extracting arguments.
		controlFlowGraph.startBlock(reenterFromRestartBlock);
		reenterFromRestartBlock.addInstruction(
			new L2Instruction(
				reenterFromRestartBlock,
				L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
				new L2IntImmediateOperand(
					OptimizationLevel.FIRST_TRANSLATION.ordinal()),
				new L2IntImmediateOperand(1)));
		// 2. Build registers, get arguments, create locals, capture primitive
		// failure value, if any.
		reenterFromRestartBlock.addInstruction(
			new L2Instruction(
				reenterFromRestartBlock,
				L2_PREPARE_NEW_FRAME_FOR_L1.instance));

		// 3. The main L1 interpreter loop.
		controlFlowGraph.startBlock(loopBlock);
		loopBlock.addInstruction(
			new L2Instruction(
				loopBlock,
				L2_INTERPRET_LEVEL_ONE.instance,
				new L2PcOperand(reenterFromCallBlock, new L2ValueManifest()),
				new L2PcOperand(
					reenterFromInterruptBlock, new L2ValueManifest())));

		// 4,5. If reified, calls return here.
		controlFlowGraph.startBlock(reenterFromCallBlock);
		reenterFromCallBlock.addInstruction(
			new L2Instruction(
				reenterFromCallBlock,
				L2_REENTER_L1_CHUNK_FROM_CALL.instance));
		reenterFromCallBlock.addInstruction(
			new L2Instruction(
				reenterFromCallBlock,
				L2_JUMP.instance,
				new L2PcOperand(loopBlock, new L2ValueManifest())));

		// 6,7. If reified, interrupts return here.
		controlFlowGraph.startBlock(reenterFromInterruptBlock);
		reenterFromInterruptBlock.addInstruction(
			new L2Instruction(
				reenterFromInterruptBlock,
				L2_REENTER_L1_CHUNK_FROM_INTERRUPT.instance));
		reenterFromInterruptBlock.addInstruction(
			new L2Instruction(
				reenterFromInterruptBlock,
				L2_JUMP.instance,
				new L2PcOperand(loopBlock, new L2ValueManifest())));

		// 8. Unreachable.
		controlFlowGraph.startBlock(unreachableBlock);
		unreachableBlock.addInstruction(
			new L2Instruction(
				unreachableBlock,
				L2_UNREACHABLE_CODE.instance));
		return controlFlowGraph;
	}

	/** Statistic for number of instructions in L2 translations. */
	private static final Statistic translationSizeStat =
		new Statistic("L2 instruction count", L2_TRANSLATION_VALUES);

	/** Statistic for number of methods depended on by L2 translations. */
	private static final Statistic translationDependenciesStat =
		new Statistic("Number of methods depended upon", L2_TRANSLATION_VALUES);

	/** Statistics about the naive L1 to L2 translation. */
	private static final Statistic translateL1Stat =
		new Statistic("L1 naive translation", L2_OPTIMIZATION_TIME);

	/**
	 * Translate the provided {@link A_RawFunction} to produce an optimized
	 * {@link L2Chunk} that is then written back into the code for subsequent
	 * executions.  Also update the {@link Interpreter}'s chunk and offset to
	 * use this new chunk right away.  If the code was a primitive, make sure to
	 * adjust the offset to just beyond its {@link L2_TRY_PRIMITIVE}
	 * instruction, which must have <em>already</em> been attempted and failed
	 * for us to have reached the {@link
	 * L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO} that caused this
	 * optimization to happen.
	 *
	 * @param code
	 *        The {@link A_RawFunction} to optimize.
	 * @param optimizationLevel
	 *        How much optimization to attempt.
	 * @param interpreter
	 *        The {@link Interpreter} used for folding expressions, and to be
	 *        updated with the new chunk and post-primitive offset.
	 */
	public static void translateToLevelTwo (
		final A_RawFunction code,
		final OptimizationLevel optimizationLevel,
		final Interpreter interpreter)
	{
		final @Nullable A_Function savedFunction = interpreter.function;
		final List<AvailObject> savedArguments =
			new ArrayList<>(interpreter.argsBuffer);
		final @Nullable AvailObject savedFailureValue =
			interpreter.latestResultOrNull();

		final L2Generator generator = new L2Generator(optimizationLevel);
		final L1Translator translator =
			new L1Translator(generator, interpreter, code);
		translator.translate();

		final L2Chunk chunk = generator.chunk();
		interpreter.function = savedFunction;
		interpreter.argsBuffer.clear();
		interpreter.argsBuffer.addAll(savedArguments);
		interpreter.latestResult(savedFailureValue);
		translationSizeStat.record(
			chunk.instructions.length,
			interpreter.interpreterIndex);
		translationDependenciesStat.record(
			generator.contingentValues.setSize(),
			interpreter.interpreterIndex);
	}

	/**
	 * Translate the supplied {@link A_RawFunction} into a sequence of {@link
	 * L2Instruction}s.  The optimization level specifies how hard to try to
	 * optimize this method.  It is roughly equivalent to the level of inlining
	 * to attempt, or the ratio of code expansion that is permitted. An
	 * optimization level of zero is the bare minimum, which produces a naïve
	 * translation to {@linkplain L2Chunk Level Two code}.  The translation may
	 * include code to decrement a counter and reoptimize with greater effort
	 * when the counter reaches zero.
	 */
	private void translate ()
	{
		final long beforeL1Naive = captureNanos();
		translateL1Instructions();
		translateL1Stat.record(
			captureNanos() - beforeL1Naive,
			interpreter.interpreterIndex);

		generator.controlFlowGraph.optimize(interpreter);

		final long beforeChunkGeneration = captureNanos();
		generator.createChunk(code);
		assert code.startingChunk() == generator.chunk();
		finalGenerationStat.record(
			captureNanos() - beforeChunkGeneration,
			interpreter.interpreterIndex);
	}

	@Override
	public void L1_doCall ()
	{
		final A_Bundle bundle =
			code.literalAt(instructionDecoder.getOperand());
		final A_Type expectedType =
			code.literalAt(instructionDecoder.getOperand());
		generateCall(bundle, expectedType, bottom());
	}

	@Override
	public void L1_doPushLiteral ()
	{
		final AvailObject constant = code.literalAt(
			instructionDecoder.getOperand());
		stackp--;
		moveConstantToSlot(constant, stackp);
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		final int localIndex = instructionDecoder.getOperand();
		stackp--;
		final L2ReadPointerOperand sourceRegister = readSlot(localIndex);
		forceSlotRegister(stackp, instructionDecoder.pc(), sourceRegister);
		nilSlot(localIndex);
	}

	@Override
	public void L1_doPushLocal ()
	{
		final int localIndex = instructionDecoder.getOperand();
		stackp--;
		final L2ReadPointerOperand sourceRegister =
			generator.makeImmutable(readSlot(localIndex));
		forceSlotRegister(stackp, instructionDecoder.pc(), sourceRegister);
		forceSlotRegister(localIndex, instructionDecoder.pc(), sourceRegister);
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		final int outerIndex = instructionDecoder.getOperand();
		final A_Type outerType = code.outerTypeAt(outerIndex);
		stackp--;
		// For now, simplify the logic related to L1's nilling of mutable outers
		// upon their final use.  Just make it immutable instead.
		forceSlotRegister(
			stackp,
			instructionDecoder.pc(),
			generator.makeImmutable(getOuterRegister(outerIndex, outerType)));
	}

	@Override
	public void L1_doClose ()
	{
		final int count = instructionDecoder.getOperand();
		final A_RawFunction codeLiteral = code.literalAt(
			instructionDecoder.getOperand());
		final List<L2ReadPointerOperand> outers = new ArrayList<>(count);
		for (int i = 1; i <= count; i++)
		{
			outers.add(readSlot(stackp + count - i));
		}
		// Pop the outers, but reserve room for the pushed function.
		stackp += count - 1;
		addInstruction(
			L2_CREATE_FUNCTION.instance,
			new L2ConstantOperand(codeLiteral),
			new L2ReadVectorOperand<>(outers),
			writeSlot(
				stackp,
				instructionDecoder.pc(),
				restriction(codeLiteral.functionType())));

		// Now that the function has been constructed, clear the slots that
		// were used for outer values -- except the destination slot, which
		// is being overwritten with the resulting function anyhow.
		for (int i = stackp + 1 - count; i <= stackp - 1; i++)
		{
			nilSlot(i);
		}
	}

	@Override
	public void L1_doSetLocal ()
	{
		final int localIndex = instructionDecoder.getOperand();
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			readSlot(localIndex),
			readSlot(stackp));
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(
			stackp, instructionDecoder.pc(), generator.constantRegister(nil));
		stackp++;
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		final int index = instructionDecoder.getOperand();
		stackp--;
		final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			readSlot(index),
			false);
		forceSlotRegister(stackp, instructionDecoder.pc(), valueReg);
	}

	@Override
	public void L1_doPushOuter ()
	{
		final int outerIndex = instructionDecoder.getOperand();
		final A_Type outerType = code.outerTypeAt(outerIndex);
		stackp--;
		forceSlotRegister(
			stackp,
			instructionDecoder.pc(),
			generator.makeImmutable(getOuterRegister(outerIndex, outerType)));
	}

	@Override
	public void L1_doPop ()
	{
		nilSlot(stackp);
		stackp++;
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		final int outerIndex = instructionDecoder.getOperand();
		stackp--;
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			getOuterRegister(outerIndex, outerType),
			false);
		forceSlotRegister(stackp, instructionDecoder.pc(), valueReg);
	}

	@Override
	public void L1_doSetOuter ()
	{
		final int outerIndex = instructionDecoder.getOperand();
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadPointerOperand tempVarReg =
			getOuterRegister(outerIndex, outerType);
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			tempVarReg,
			readSlot(stackp));
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(
			stackp, instructionDecoder.pc(), generator.constantRegister(nil));
		stackp++;
	}

	@Override
	public void L1_doGetLocal ()
	{
		final int index = instructionDecoder.getOperand();
		stackp--;
		final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			readSlot(index),
			true);
		forceSlotRegister(stackp, instructionDecoder.pc(), valueReg);

	}

	@Override
	public void L1_doMakeTuple ()
	{
		final int count = instructionDecoder.getOperand();
		final List<L2ReadPointerOperand> vector = new ArrayList<>(count);
		for (int i = 1; i <= count; i++)
		{
			vector.add(readSlot(stackp + count - i));
			// Clear all but the first pushed slot.
			if (i != 1)
			{
				nilSlot(stackp + count - i);
			}
		}
		stackp += count - 1;
		// Fold into a constant tuple if possible
		final List<A_BasicObject> constants = new ArrayList<>(count);
		for (final L2ReadPointerOperand regRead : vector)
		{
			if (regRead.constantOrNull() == null)
			{
				break;
			}
			constants.add(regRead.constantOrNull());
		}
		if (constants.size() == count)
		{
			// The tuple elements are all constants.  Fold it.
			moveConstantToSlot(tupleFromList(constants), stackp);
		}
		else
		{
			final A_Type tupleType = tupleTypeForTypes(
				vector.stream()
					.map(L2ReadPointerOperand::type)
					.toArray(A_Type[]::new));
			addInstruction(
				L2_CREATE_TUPLE.instance,
				new L2ReadVectorOperand<>(vector),
				writeSlot(
					stackp, instructionDecoder.pc(), restriction(tupleType)));
		}
	}

	@Override
	public void L1_doGetOuter ()
	{
		final int outerIndex = instructionDecoder.getOperand();
		stackp--;
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			getOuterRegister(outerIndex, outerType),
			false);
		forceSlotRegister(stackp, instructionDecoder.pc(), valueReg);
	}

	@Override
	public void L1_doExtension ()
	{
		assert false : "Illegal dispatch nybblecode";
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		// Force reification of the current continuation and all callers, then
		// resume that continuation right away, which also makes it available.
		// Create a new continuation like it, with only the caller, function,
		// and arguments present, and having an empty stack and an L1 pc of 0.
		// Then push that new continuation on the virtual stack.
		final int oldStackp = stackp;
		// The initially constructed continuation is always immediately resumed,
		// so this should never be observed.
		stackp = Integer.MAX_VALUE;
		final L2BasicBlock onReification =
			generator.createBasicBlock("on reification");
		addInstruction(
			L2_REIFY.instance,
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(0),
			new L2IntImmediateOperand(
				StatisticCategory.PUSH_LABEL_IN_L2.ordinal()),
			new L2PcOperand(onReification, generator.currentManifest()));

		generator.startBlock(onReification);
		reify(null, UNREACHABLE);

		// We just continued the reified continuation, having exploded the
		// continuation into slot registers.  Create a label based on it, but
		// capturing only the arguments (with pc=0, stack=empty).
		assert code.primitive() == null;
		final int numArgs = code.numArgs();
		final List<L2ReadPointerOperand> slotsForLabel =
			new ArrayList<>(numSlots);
		for (int i = 1; i <= numArgs; i++)
		{
			slotsForLabel.add(readSlot(i));
		}
		final L2ReadPointerOperand nilTemp = generator.constantRegister(nil);
		for (int i = numArgs + 1; i <= numSlots; i++)
		{
			slotsForLabel.add(nilTemp);
		}
		// Now create the actual label continuation and push it.
		stackp = oldStackp - 1;
		final A_Type continuationType =
			continuationTypeForFunctionType(code.functionType());
		final L2WritePointerOperand destinationRegister =
			writeSlot(
				stackp,
				instructionDecoder.pc(),
				restriction(continuationType, null));
		final L2BasicBlock afterCreation =
			generator.createBasicBlock("after creating label");
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			getCurrentFunction(),
			getCurrentContinuation(),  // the caller, since we popped already.
			new L2IntImmediateOperand(0),  // indicates a label.
			new L2IntImmediateOperand(numSlots + 1),  // empty stack
			new L2ReadVectorOperand<>(slotsForLabel),
			destinationRegister,
			new L2PcOperand(generator.initialBlock, new L2ValueManifest()),
			edgeTo(afterCreation),
			new L2CommentOperand("Create a label continuation."));

		// Continue, with the label having been pushed.
		generator.startBlock(afterCreation);
		// Freeze all fields of the new object, including its caller,
		// function, and arguments.
		addInstruction(
			L2_MAKE_SUBOBJECTS_IMMUTABLE.instance,
			destinationRegister.read());
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		final A_Variable literalVariable = code.literalAt(
			instructionDecoder.getOperand());
		stackp--;
		if (literalVariable.isInitializedWriteOnceVariable()
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
			moveConstantToSlot(literalVariable.value(), stackp);
		}
		else
		{
			final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
				L2_GET_VARIABLE.instance,
				generator.constantRegister(literalVariable),
				false);
			forceSlotRegister(stackp, instructionDecoder.pc(), valueReg);
		}
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		final A_Variable literalVariable = code.literalAt(
			instructionDecoder.getOperand());
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			generator.constantRegister(literalVariable),
			readSlot(stackp));
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(
			stackp, instructionDecoder.pc(), generator.constantRegister(nil));
		stackp++;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final L2ReadPointerOperand source = readSlot(stackp);
		stackp--;
		final L2ReadPointerOperand immutableRead =
			generator.makeImmutable(source);
		forceSlotRegister(stackp + 1, instructionDecoder.pc(), immutableRead);
		forceSlotRegister(stackp, instructionDecoder.pc(), immutableRead);
	}

	@Override
	public void L1Ext_doPermute ()
	{
		// Move into the permuted temps, then back to the stack.  This puts the
		// responsibility for optimizing away extra moves (by coloring the
		// registers) on the optimizer.
		final A_Tuple permutation = code.literalAt(
			instructionDecoder.getOperand());
		final int size = permutation.tupleSize();
		final L2WritePointerOperand[] temps = new L2WritePointerOperand[size];
		for (int i = size; i >= 1; i--)
		{
			final L2ReadPointerOperand source = readSlot(stackp + size - i);
			final L2WritePointerOperand temp =
				generator.newObjectRegisterWriter(source.restriction());
			generator.moveRegister(source, temp);
			temps[permutation.tupleIntAt(i) - 1] = temp;
		}
		for (int i = size; i >= 1; i--)
		{
			final L2ReadPointerOperand temp = temps[i - 1].read();
			generator.moveRegister(temp, writeSlot(
						stackp + size - i,
						instructionDecoder.pc(),
						temp.restriction()));
		}
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		final A_Bundle bundle =
			code.literalAt(instructionDecoder.getOperand());
		final AvailObject expectedType =
			code.literalAt(instructionDecoder.getOperand());
		final AvailObject superUnionType =
			code.literalAt(instructionDecoder.getOperand());
		generateCall(bundle, expectedType, superUnionType);
	}

	@Override
	public void L1Ext_doSetSlot ()
	{
		final int destinationIndex = instructionDecoder.getOperand();
		final L2ReadPointerOperand source = readSlot(stackp);
		forceSlotRegister(destinationIndex, instructionDecoder.pc(), source);
		nilSlot(stackp);
		stackp++;
	}
}
