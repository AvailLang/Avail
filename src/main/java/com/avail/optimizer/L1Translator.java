/*
 * L1Translator.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.atoms.AtomDescriptor;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.bundles.MessageBundleDescriptor;
import com.avail.descriptor.functions.A_Continuation;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.functions.CompiledCodeDescriptor;
import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder;
import com.avail.descriptor.methods.A_Definition;
import com.avail.descriptor.methods.A_Method;
import com.avail.descriptor.methods.A_SemanticRestriction;
import com.avail.descriptor.methods.MethodDescriptor;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.BottomTypeDescriptor;
import com.avail.descriptor.types.TypeDescriptor;
import com.avail.descriptor.variables.A_Variable;
import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor;
import com.avail.dispatch.InternalLookupTree;
import com.avail.dispatch.LookupTree;
import com.avail.dispatch.LookupTreeAdaptor.UnusedMemento;
import com.avail.dispatch.LookupTreeTraverser;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.interpreter.execution.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelOne.L1OperationDispatcher;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.operation.L2_REIFY.StatisticCategory;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.general.P_Equality;
import com.avail.optimizer.L2ControlFlowGraph.Zone;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.MutableInt;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static com.avail.AvailRuntime.HookType.*;
import static com.avail.AvailRuntimeSupport.captureNanos;
import static com.avail.descriptor.representation.NilDescriptor.nil;
import static com.avail.descriptor.functions.FunctionDescriptor.createFunction;
import static com.avail.descriptor.sets.SetDescriptor.setFromCollection;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.ContinuationTypeDescriptor.continuationTypeForFunctionType;
import static com.avail.descriptor.types.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.types.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.types.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.types.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.types.TupleTypeDescriptor.tupleTypeForTypes;
import static com.avail.descriptor.types.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.types.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.types.VariableTypeDescriptor.variableTypeFor;
import static com.avail.dispatch.LookupTreeAdaptor.UnusedMemento.UNUSED;
import static com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CannotFail;
import static com.avail.interpreter.Primitive.Result.FAILURE;
import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.*;
import static com.avail.optimizer.L2ControlFlowGraph.ZoneType.BEGIN_REIFICATION_FOR_INTERRUPT;
import static com.avail.optimizer.L2ControlFlowGraph.ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE;
import static com.avail.optimizer.L2Generator.*;
import static com.avail.optimizer.L2Generator.OptimizationLevel.UNOPTIMIZED;
import static com.avail.performance.StatisticReport.L2_OPTIMIZATION_TIME;
import static com.avail.performance.StatisticReport.L2_TRANSLATION_VALUES;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

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
	public final A_RawFunction code;

	/**
	 * The number of slots in the virtualized continuation.  This includes the
	 * arguments, the locals (including the optional primitive failure result),
	 * and the stack slots.
	 */
	private final int numSlots;

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
	final L1InstructionDecoder instructionDecoder =
		new L1InstructionDecoder();

	/**
	 * The current stack depth during naive translation to level two.
	 */
	int stackp;

	/**
	 * The exact function that we're translating, if known.  This is only
	 * non-null if the function captures no outers.
	 */
	private final @Nullable
	A_Function exactFunctionOrNull;

	/**
	 * Return the top {@link Frame} for code generation.
	 *
	 * @return The top {@link Frame},
	 */
	private Frame topFrame ()
	{
		return generator.topFrame;
	}

	/**
	 * Answer the current {@link L2ValueManifest}, which tracks which {@link
	 * L2Synonym}s hold which {@link L2SemanticValue}s at the current code
	 * generation point.
	 *
	 * @return The current {@link L2ValueManifest}.
	 */
	public L2ValueManifest currentManifest ()
	{
		return generator.currentManifest;
	}

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
	private L1Translator (
		final L2Generator generator,
		final Interpreter interpreter,
		final A_RawFunction code)
	{
		this.generator = generator;
		this.interpreter = interpreter;
		this.code = code;
		this.numSlots = code.numSlots();
		this.stackp = numSlots + 1;
		this.exactFunctionOrNull = computeExactFunctionOrNullForCode(code);
		this.semanticSlots = new L2SemanticValue[numSlots];
		for (int i = 1; i <= numSlots; i++)
		{
			semanticSlots[i - 1] = topFrame().slot(i, 1);
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
			if (!outerType.instanceCount().equalsInt(1)
				|| outerType.isInstanceMeta())
			{
				return null;
			}
			outerConstants.add(outerType.instance());
		}
		// This includes the case of there being no outers.
		return createFunction(theCode, tupleFromList(outerConstants));
	}

	/**
	 * Answer the register holding the latest assigned version of the specified
	 * continuation slot. The slots are the arguments, then the locals, then the
	 * stack entries. The slots are numbered starting at 1.
	 *
	 * <p>This is only public to allow primitives like
	 * {@link P_RestartContinuation} to be able to fetch the current
	 * arguments.</p>
	 *
	 * @param slotIndex
	 *        The index into the continuation's slots.
	 * @return An {@link L2ReadBoxedOperand} representing that continuation
	 *         slot.
	 */
	public L2ReadBoxedOperand readSlot (final int slotIndex)
	{
		return generator.readBoxed(semanticSlot(slotIndex));
	}

	/**
	 * Create a new semantic value to overwrite any existing value in the
	 * specified continuation slot.  Answer a write of a synonym containing just
	 * that semantic value.
	 *
	 * <p>The slots are the arguments, the local variables, the local constants,
	 * and finally the stack entries.  Slots are numbered starting at 1.</p>
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
	private L2WriteBoxedOperand writeSlot (
		final int slotIndex,
		final int effectivePc,
		final TypeRestriction restriction)
	{
		// Create a new semantic slot at the current pc, representing this
		// newly written value.
		final L2SemanticValue semanticValue =
			topFrame().slot(slotIndex, effectivePc);
		semanticSlots[slotIndex - 1] = semanticValue;
		return generator.boxedWrite(semanticValue, restriction);
	}

	/**
	 * Associate the specified {@link L2ReadBoxedOperand} with the semantic
	 * slot having the given index and effective pc.  Restrict the type based on
	 * the register-read's {@link TypeRestriction}.
	 *
	 * @param slotIndex
	 *        The slot index to replace.
	 * @param effectivePc
	 *        The effective pc.
	 * @param registerRead
	 *        The {@link L2ReadBoxedOperand} that should now be considered the
	 *        current register-read representing that slot.
	 */
	public void forceSlotRegister (
		final int slotIndex,
		final int effectivePc,
		final L2ReadBoxedOperand registerRead)
	{
		forceSlotRegister(
			slotIndex,
			effectivePc,
			registerRead.semanticValue(),
			registerRead.restriction());
	}

	/**
	 * Associate the specified register with the slot semantic value having the
	 * given index and effective pc.  Note that the given synonym is always
	 * invalidated by a merge in this method.
	 *
	 * @param slotIndex
	 *        The slot index to replace.
	 * @param effectivePc
	 *        The effective pc.
	 * @param sourceSemanticValue
	 *        The {@link L2SemanticValue} that is moved into the slot.
	 * @param restriction
	 *        The {@link TypeRestriction} that currently bounds the synonym's
	 *        possible values.
	 */
	private void forceSlotRegister (
		final int slotIndex,
		final int effectivePc,
		final L2SemanticValue sourceSemanticValue,
		final TypeRestriction restriction)
	{
		// Create a new L2SemanticSlot at the effective pc, representing this
		// newly written value.
		final L2SemanticValue slotSemanticValue =
			topFrame().slot(slotIndex, effectivePc);
		semanticSlots[slotIndex - 1] = slotSemanticValue;
		generator.moveRegister(
			L2_MOVE.boxed,
			sourceSemanticValue,
			slotSemanticValue);
		currentManifest().setRestriction(slotSemanticValue, restriction);
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
		moveConstantToSlot(nil, slotIndex);
	}

	/**
	 * Given an {@link L2WriteBoxedOperand}, produce an {@link
	 * L2ReadBoxedOperand} of the same value, but with the current manifest's
	 * {@link TypeRestriction} applied.
	 *
	 * @param write
	 *        The {@link L2WriteBoxedOperand} for which to generate a read.
	 * @return The {@link L2ReadBoxedOperand} that reads the value.
	 */
	public L2ReadBoxedOperand readBoxed (
		final L2WriteBoxedOperand write)
	{
		return generator.readBoxed(write);
	}

	/**
	 * Write instructions to extract the current function, and answer an {@link
	 * L2ReadBoxedOperand} for the register that will hold the function
	 * afterward.
	 */
	private L2ReadBoxedOperand getCurrentFunction ()
	{
		final L2SemanticValue semanticFunction = topFrame().function();
		if (currentManifest().hasSemanticValue(semanticFunction))
		{
			// Note the current function can't ever be an int or float.
			return generator.readBoxed(semanticFunction);
		}
		// We have to get it into a register.
		if (exactFunctionOrNull != null)
		{
			// The exact function is known.
			return generator.boxedConstant(exactFunctionOrNull);
		}
		// The exact function isn't known, but we know the raw function, so we
		// statically know the function type.
		final TypeRestriction restriction =
			restrictionForType(code.functionType(),
				BOXED);
		final L2WriteBoxedOperand functionWrite =
			generator.boxedWrite(semanticFunction, restriction);
		addInstruction(L2_GET_CURRENT_FUNCTION.instance, functionWrite);
		return readBoxed(functionWrite);
	}

	/**
	 * Write instructions to extract a numbered outer from the current function,
	 * and answer an {@link L2ReadBoxedOperand} for the register that will
	 * hold the outer value afterward.
	 *
	 * @param outerIndex The index of the outer to get.
	 * @param outerType The type that the outer is known to be.
	 * @return The {@link L2ReadBoxedOperand} where the outer was written.
	 */
	private L2ReadBoxedOperand getOuterRegister (
		final int outerIndex,
		final A_Type outerType)
	{
		final L2SemanticValue semanticOuter = topFrame().outer(outerIndex);
		if (currentManifest().hasSemanticValue(semanticOuter))
		{
			return generator.readBoxed(semanticOuter);
		}
		if (outerType.instanceCount().equalsInt(1)
			&& !outerType.isInstanceMeta())
		{
			// The exact outer is known statically.
			return generator.boxedConstant(outerType.instance());
		}
		final L2ReadBoxedOperand functionRead = getCurrentFunction();
		final TypeRestriction restriction =
			restrictionForType(outerType, BOXED);
		final L2WriteBoxedOperand outerWrite =
			generator.boxedWrite(semanticOuter, restriction);
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2IntImmediateOperand(outerIndex),
			functionRead,
			outerWrite);
		return readBoxed(outerWrite);
	}

	/**
	 * Capture the latest value returned by the {@link L2_RETURN} instruction in
	 * this {@link Interpreter}.
	 *
	 * @param guaranteedType
	 *        The type the return value is guaranteed to conform to.
	 * @return An {@link L2ReadBoxedOperand} that now holds the returned
	 *         value.
	 */
	private L2ReadBoxedOperand getLatestReturnValue (
		final A_Type guaranteedType)
	{
		final L2WriteBoxedOperand writer =
			generator.boxedWriteTemp(restrictionForType(guaranteedType,
				BOXED));
		addInstruction(
			L2_GET_LATEST_RETURN_VALUE.instance,
			writer);
		return readBoxed(writer);
	}

	/**
	 * Capture the function that has just attempted to return via an {@link
	 * L2_RETURN} instruction in this {@link Interpreter}.
	 *
	 * @return An {@link L2ReadBoxedOperand} that now holds the function that
	 *         is returning.
	 */
	private L2ReadBoxedOperand getReturningFunctionRegister ()
	{
		final L2WriteBoxedOperand writer =
			generator.boxedWriteTemp(
				restrictionForType(mostGeneralFunctionType(),
					BOXED));
		addInstruction(
			L2_GET_RETURNING_FUNCTION.instance,
			writer);
		return readBoxed(writer);
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
		forceSlotRegister(
			slotIndex, instructionDecoder.pc(), generator.boxedConstant(value));
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
		// Use the current block's zone for subsequent nodes that are inside
		// this reification handler.
		final @Nullable Zone zone = generator.currentBlock().zone;
		final L2WriteBoxedOperand newContinuationWrite =
			generator.boxedWriteTemp(
				restrictionForType(mostGeneralContinuationType(),
					BOXED));
		final L2BasicBlock onReturnIntoReified =
			generator.createBasicBlock("Return into reified continuation");
		// Create readSlots for constructing the continuation.  Also create
		// writeSemanticValues and writeRestrictions for restoring the state
		// from the continuation when it's resumed.
		final L2ReadBoxedOperand[] readSlotsBefore =
			new L2ReadBoxedOperand[numSlots];
		final L2SemanticValue[] writeSemanticValues =
			new L2SemanticValue[numSlots];
		final TypeRestriction[] writeRestrictions =
			new TypeRestriction[numSlots];
		for (int i = 0; i < numSlots; i++)
		{
			final L2SemanticValue semanticValue = semanticSlot(i + 1);
			final L2ReadBoxedOperand read;
			if (i + 1 == stackp && expectedValueOrNull != null)
			{
				read = generator.boxedConstant(expectedValueOrNull);
			}
			else
			{
				read = generator.readBoxed(semanticValue);
				final TypeRestriction restriction = read.restriction();
				assert restriction.isBoxed();
				writeSemanticValues[i] = semanticValue;
				// Only restore the boxed form on reentry, but preserve any
				// guarantee of immutability.
				writeRestrictions[i] = restriction
					.withoutFlag(UNBOXED_INT)
					.withoutFlag(UNBOXED_FLOAT);
			}
			readSlotsBefore[i] = read;
		}
		// Now generate the reification instructions, ensuring that when
		// returning into the resulting continuation it will enter a block where
		// the slot registers are the new ones we just created.
		final L2WriteIntOperand writeOffset = generator.intWriteTemp(
			restrictionForType(int32(), UNBOXED_INT));
		final L2WriteBoxedOperand writeRegisterDump = generator.boxedWriteTemp(
			restrictionForType(ANY.o(), BOXED));
		final L2BasicBlock fallThrough =
			generator.createBasicBlock("Off-ramp", zone);
		addInstruction(
			L2_SAVE_ALL_AND_PC_TO_INT.instance,
			edgeTo(onReturnIntoReified),
			writeOffset,
			writeRegisterDump,
			edgeTo(fallThrough));

		generator.startBlock(fallThrough);
		// We're in a reification handler here, so the caller is guaranteed to
		// contain the reified caller.
		final L2WriteBoxedOperand writeReifiedCaller =
			generator.boxedWrite(
				topFrame().reifiedCaller(),
				restrictionForType(mostGeneralContinuationType(), BOXED));
		addInstruction(
			L2_GET_CURRENT_CONTINUATION.instance,
			writeReifiedCaller);
		if (typeOfEntryPoint == TRANSIENT)
		{
			// L1 can never see this continuation, so it can be minimal.
			addInstruction(
				L2_CREATE_CONTINUATION.instance,
				getCurrentFunction(),
				generator.readBoxed(writeReifiedCaller),
				new L2IntImmediateOperand(Integer.MAX_VALUE),
				new L2IntImmediateOperand(Integer.MAX_VALUE),
				new L2ReadBoxedVectorOperand(emptyList()),
				newContinuationWrite,
				generator.readInt(
					writeOffset.onlySemanticValue(),
					generator.unreachablePcOperand().targetBlock()),
				generator.readBoxed(writeRegisterDump),
				new L2CommentOperand(
					"Create a dummy reification continuation."));
		}
		else
		{
			// Make an L1-complete continuation, since an invalidation can cause
			// it to resume in the L2Chunk#unoptimizedChunk, which can only see
			// L1 content.
			addInstruction(
				L2_CREATE_CONTINUATION.instance,
				getCurrentFunction(),
				readBoxed(writeReifiedCaller),
				new L2IntImmediateOperand(instructionDecoder.pc()),
				new L2IntImmediateOperand(stackp),
				new L2ReadBoxedVectorOperand(asList(readSlotsBefore)),
				newContinuationWrite,
				generator.readInt(
					writeOffset.onlySemanticValue(),
					generator.unreachablePcOperand().targetBlock()),
				generator.readBoxed(writeRegisterDump),
				new L2CommentOperand("Create a reification continuation."));
		}
		addInstruction(
			L2_SET_CONTINUATION.instance,
			generator.readBoxed(newContinuationWrite));

		// Right after creating the continuation.
		addInstruction(L2_RETURN_FROM_REIFICATION_HANDLER.instance);

		// Here it's returning into the reified continuation.
		generator.startBlock(onReturnIntoReified);
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(typeOfEntryPoint.offsetInDefaultChunk),
			new L2CommentOperand(
				"If invalid, reenter «default» at "
					+ typeOfEntryPoint.name() + "."));
		if (expectedValueOrNull != null && expectedValueOrNull.isVacuousType())
		{
			generator.addUnreachableCode();
		}
	}

	/**
	 * Generate code to extract the current {@link
	 * AvailRuntime#resultDisagreedWithExpectedTypeFunction()} into a new
	 * register, which is returned here.
	 *
	 * @return The new register that will hold the invalid return function.
	 */
	private L2ReadBoxedOperand getInvalidResultFunctionRegister ()
	{
		final L2WriteBoxedOperand invalidResultFunction =
			generator.boxedWriteTemp(
				restrictionForType(
					functionType(
						tuple(
							mostGeneralFunctionType(),
							topMeta(),
							variableTypeFor(ANY.o())),
						bottom()),
					BOXED));
		addInstruction(
			L2_GET_INVALID_MESSAGE_RESULT_FUNCTION.instance,
			invalidResultFunction);
		return readBoxed(invalidResultFunction);
	}

	/**
	 * A memento to be used for coordinating code generation between the
	 * branches of an {@link InternalLookupTree}.
	 */
	class InternalNodeMemento
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
		 * Construct a new memento.  Make the label something meaningful to
		 * make it easier to decipher.
		 *
		 * @param argumentIndexToTest
		 *        The one-based subscript of the argument being tested.
		 * @param typeToTest
		 *        The type to test the argument against.
		 * @param branchLabelCounter
		 *        An int unique to this dispatch tree, monotonically
		 *        allocated at each branch.
		 */
		InternalNodeMemento (
			final int argumentIndexToTest,
			final A_Type typeToTest,
			final int branchLabelCounter)
		{
			this.argumentIndexToTest = argumentIndexToTest;
			this.typeToTest = typeToTest;
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
		final String quotedBundleName;

		/** A counter for generating unique branch names for this dispatch. */
		final MutableInt branchLabelCounter = new MutableInt(1);

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
		final boolean isSuper;

		/** Where to jump to perform the slow lookup. */
		final L2BasicBlock onFallBackToSlowLookup;

		/**
		 * Where to jump to perform reification, eventually leading to a return
		 * type check after completion.
		 */
		final L2BasicBlock onReificationWithCheck;

		/**
		 * Where to jump to perform reification without the need for an eventual
		 * return type check.
		 */
		final L2BasicBlock onReificationNoCheck;

		/**
		 * Where to jump to perform reification during a call that cannot ever
		 * return.
		 */
		final L2BasicBlock onReificationUnreturnable;

		/**
		 * Where to jump after a completed call to perform a return type check.
		 */
		final L2BasicBlock afterCallWithCheck;

		/**
		 * Where to jump after a completed call if a return type check isn't
		 * needed.
		 */
		final L2BasicBlock afterCallNoCheck;

		/**
		 * Where it ends up after the entire call, regardless of whether the
		 * returned value had to be checked or not.
		 */
		final L2BasicBlock afterEverything;

		/**
		 * A map from each reachable looked-up {@link A_Function} to a {@link
		 * Pair} containing an {@link L2BasicBlock} in which code generation for
		 * invocation of this function should/did take place, and a {@link
		 * Continuation0} which will cause that code generation to happen.
		 *
		 * <p>This construct theoretically deals with method lookups that lead
		 * to the same function multiple ways (it's unclear if the lookup tree
		 * mechanism will ever evolve to produce this situation), but more
		 * practically, it can de-duplicate a successful inlined lookup and the
		 * success path of the fall-back slow lookup <em>when it knows there is
		 * only one particular definition that a successful slow lookup could
		 * produce.</em>
		 */
		final Map<A_Function, Pair<L2BasicBlock, Continuation0>>
			invocationSitesToCreate;

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
		public void useAnswer (final L2ReadBoxedOperand answerReg)
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
				generator.jumpTo(afterCallNoCheck);
			}
			else
			{
				// Capture it as the unchecked return value SemanticSlot by
				// using pc - 1.
				forceSlotRegister(
					stackp, instructionDecoder.pc() - 1, answerReg);
				generator.jumpTo(afterCallWithCheck);
			}
		}

		/**
		 * For every {@link L2BasicBlock} in my {@link #invocationSitesToCreate}
		 * that is reachable, generate an invocation of the corresponding
		 * {@link A_Function}.
		 */
		void generateAllInvocationSites ()
		{
			invocationSitesToCreate.forEach(
				(function, pair) -> pair.second().value());
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
			this.quotedBundleName = A_Atom.Companion.atomName(
				A_Bundle.Companion.message(bundle)
			).toString();
			this.onFallBackToSlowLookup = generator.createBasicBlock(
				"fall back to slow lookup during " + quotedBundleName);
			this.onReificationWithCheck = generator.createBasicBlock(
				"reify with check during " + quotedBundleName,
				PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Continue reification leading to return check"));
			this.onReificationNoCheck = generator.createBasicBlock(
				"reify no check during " + quotedBundleName,
				PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Continue reification without return check"));
			this.onReificationUnreturnable = generator.createBasicBlock(
				"reify unreturnable " + quotedBundleName,
				PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Continue reification for unreturnable"));
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
			this.invocationSitesToCreate = new HashMap<>();
		}
	}

	/**
	 * Generate code to perform a method invocation.  If a superUnionType other
	 * than {@link BottomTypeDescriptor#bottom() bottom} is supplied, produce a
	 * super-directed multimethod invocation.
	 *
	 * @param bundle
	 *        The {@linkplain MessageBundleDescriptor message bundle} to invoke.
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
		final A_Method method = A_Bundle.Companion.bundleMethod(bundle);
		generator.addContingentValue(method);
		final int nArgs = method.numArgs();
		final List<L2SemanticValue> semanticArguments = new ArrayList<>(nArgs);
		for (int i = nArgs - 1; i >= 0; i--)
		{
			semanticArguments.add(semanticSlot(stackp + i));
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
		final LookupTree<A_Definition, A_Tuple> tree = method.testingTree();
		final List<TypeRestriction> argumentRestrictions =
			semanticArguments.stream()
				.map(semValue -> currentManifest().restrictionFor(semValue))
				.collect(toList());

		// Special case: If there's only one method definition and the type tree
		// has not yet been expanded, go ahead and do so.  It takes less space
		// in L2/JVM to store the simple invocation than a full lookup.
		if (method.definitionsTuple().tupleSize() <= 1)
		{
			final List<A_Type> argTypes = argumentRestrictions.stream()
				.map(restriction -> restriction.type)
				.collect(toList());
			try
			{
				final A_Definition result =
					method.lookupByTypesFromTuple(tupleFromList(argTypes));
				assert result.equals(method.definitionsTuple().tupleAt(1));
			}
			catch (final MethodDefinitionException e)
			{
				assert false : "Couldn't look up method by its own signature";
			}
			// The tree is now warmed up for a monomorphic inline.
		}

		final List<A_Definition> applicableExpandedLeaves = new ArrayList<>();
		final LookupTreeTraverser<A_Definition, A_Tuple, UnusedMemento, Boolean>
			definitionCollector = new LookupTreeTraverser
					<A_Definition, A_Tuple, UnusedMemento, Boolean>(
				MethodDescriptor.runtimeDispatcher, UNUSED, false)
			{
				@Override
				public Boolean visitPreInternalNode (
					final int argumentIndex, final A_Type argumentType)
				{
					return true;
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
					if (signature.couldEverBeInvokedWith(argumentRestrictions)
						&& superUnionType.isSubtypeOf(
							signature.argsTupleType()))
					{
						applicableExpandedLeaves.add(definition);
					}
				}
			};
		definitionCollector.traverseEntireTree(tree);

		if (applicableExpandedLeaves.size() <= maxPolymorphismToInlineDispatch)
		{
			final LookupTreeTraverser<
				A_Definition, A_Tuple, UnusedMemento, InternalNodeMemento>
				traverser = new LookupTreeTraverser<
					A_Definition, A_Tuple, UnusedMemento, InternalNodeMemento>(
				MethodDescriptor.runtimeDispatcher, UNUSED, false)
			{
				@Override
				public InternalNodeMemento visitPreInternalNode (
					final int argumentIndex,
					final A_Type argumentType)
				{
					return preInternalVisit(
						callSiteHelper,
						semanticArguments,
						argumentIndex,
						argumentType);
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
				}

				@Override
				public void visitPostInternalNode (
					final InternalNodeMemento memento)
				{
					// The leaves already end with jumps, and the manifest
					// at the next site should have the weaker types that
					// were known before this test.
					assert !generator.currentlyReachable();
				}

				@Override
				public void visitUnexpanded ()
				{
					// This part of the lookup tree wasn't expanded yet, so fall
					// back to the slow dispatch.
					if (generator.currentlyReachable())
					{
						generator.jumpTo(callSiteHelper.onFallBackToSlowLookup);
					}
				}

				@Override
				public void visitLeafNode (final A_Tuple lookupResult)
				{
					leafVisit(semanticArguments, callSiteHelper, lookupResult);
					assert !generator.currentlyReachable();
				}
			};
			traverser.traverseEntireTree(tree);
		}
		else
		{
			// Always fall back.
			generator.jumpTo(callSiteHelper.onFallBackToSlowLookup);
		}
		assert !generator.currentlyReachable();

		// Calculate the union of the types guaranteed to be produced by the
		// possible definitions, including analysis of primitives.  The phi
		// combining final results will produce something at least this strict.
		A_Type tempUnion = bottom();
		for (final A_Definition definition :
			method.definitionsAtOrBelow(argumentRestrictions))
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
					for (int i = 0; i < argumentRestrictions.size(); i++)
					{
						final TypeRestriction intersection =
							argumentRestrictions.get(i).intersectionWithType(
								signatureTupleType.typeAtIndex(i + 1));
						intersectedArgumentTypes.add(intersection.type);
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
		generator.startBlock(callSiteHelper.onFallBackToSlowLookup);
		if (generator.currentlyReachable())
		{
			generateSlowPolymorphicCall(callSiteHelper, semanticArguments);
		}

		// #1b: At this point, invocationSitesToCreate is fully populated with
		// basic blocks in which to generate invocations of the corresponding
		// method definition bodies.  Generate them all now, as they will lead
		// to the exits that we'll generate in the next step.
		callSiteHelper.generateAllInvocationSites();

		// #2: Reification with return check.
		generator.startBlock(callSiteHelper.onReificationWithCheck);
		if (generator.currentlyReachable())
		{
			reify(expectedType, TO_RETURN_INTO);
			if (generator.currentlyReachable())
			{
				// Capture the value being returned into the on-ramp.
				forceSlotRegister(
					stackp,
					instructionDecoder.pc() - 1,
					getLatestReturnValue(unionOfPossibleResults));
				generator.jumpTo(callSiteHelper.afterCallWithCheck);
			}
		}

		// #3: Reification without return check.
		generator.startBlock(callSiteHelper.onReificationNoCheck);
		if (generator.currentlyReachable())
		{
			reify(expectedType, TO_RETURN_INTO);
			if (generator.currentlyReachable())
			{
				// Capture the value being returned into the on-ramp.
				forceSlotRegister(
					stackp,
					instructionDecoder.pc(),
					getLatestReturnValue(
						unionOfPossibleResults.typeIntersection(expectedType)));
				generator.jumpTo(callSiteHelper.afterCallNoCheck);
			}
		}

		// #4:
		generator.startBlock(callSiteHelper.onReificationUnreturnable);
		if (generator.currentlyReachable())
		{
			reify(expectedType, TO_RETURN_INTO);
			if (generator.currentlyReachable())
			{
				generator.addUnreachableCode();
			}
		}

		// #5: After call with return check.
		generator.startBlock(callSiteHelper.afterCallWithCheck);
		if (generator.currentlyReachable())
		{
			// The unchecked return value will have been put into the register
			// bound to the L2SemanticSlot for the stackp and pc just after the
			// call MINUS ONE.  Check it, moving it to a register that's bound
			// to the L2SemanticSlot for the stackp and pc just after the call.
			generateReturnTypeCheck(expectedType);
			generator.jumpTo(callSiteHelper.afterEverything);
		}

		// #6: After call without return check.
		// Make the version of the stack with the unchecked value available.
		generator.startBlock(callSiteHelper.afterCallNoCheck);
		if (generator.currentlyReachable())
		{
			// The value will have been put into a register bound to the
			// L2SemanticSlot for the stackp and pc just after the call.
			generator.jumpTo(callSiteHelper.afterEverything);
		}

		// #7: After everything.  If it's possible to return a valid value from
		// the call, this will be reachable.
		generator.startBlock(callSiteHelper.afterEverything);
	}

	/**
	 * A leaf lookup tree was found at this position in the inlined dispatch.
	 * If it's a singular method definition, embed a call to it, otherwise jump
	 * to the fallback lookup code to reproduce and handle lookup errors.
	 *
	 * @param semanticArguments
	 *        The list of {@link L2SemanticValue}s supplying argument values.
	 *        These become strengthened by type tests in the current manifest.
	 * @param callSiteHelper
	 *        The {@link CallSiteHelper} object for this dispatch.
	 * @param solutions
	 *        The {@link A_Tuple} of {@link A_Definition}s at this leaf of the
	 *        lookup tree.  If there's exactly one and it's a method definition,
	 *        the lookup is considered successful, otherwise it's a failed
	 *        lookup.
	 */
	void leafVisit (
		final List<L2SemanticValue> semanticArguments,
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
				promiseToHandleCallForDefinitionBody(
					solution.bodyBlock(), semanticArguments, callSiteHelper);
				return;
			}
		}
		// Failed dispatches basically never happen, so jump to the fallback
		// lookup, which will do its own problem reporting.
		generator.jumpTo(callSiteHelper.onFallBackToSlowLookup);
	}

	/**
	 * A leaf lookup tree was found at this position in the inlined dispatch.
	 * If it's a singular method definition, embed a call to it, otherwise jump
	 * to the fallback lookup code to reproduce and handle lookup errors.
	 *
	 * @param function
	 *        The {@link A_Definition} body {@link A_Function} to be invoked.
	 * @param semanticArguments
	 *        The list of {@link L2SemanticValue}s supplying argument values.
	 *        These become strengthened by type tests in the current manifest.
	 * @param callSiteHelper
	 *        The {@link CallSiteHelper} object for this dispatch.
	\	 */
	private void promiseToHandleCallForDefinitionBody (
		final A_Function function,
		final List<L2SemanticValue> semanticArguments,
		final CallSiteHelper callSiteHelper)
	{
		final @Nullable Pair<L2BasicBlock, Continuation0> existingPair =
			callSiteHelper.invocationSitesToCreate.get(function);
		final L2BasicBlock block;
		if (existingPair == null)
		{
			block = generator.createBasicBlock("successful lookup");
			// Safety check.
			final MutableInt ran = new MutableInt(0);
			final Continuation0 newAction = () ->
			{
				assert ran.value == 0;
				ran.value++;
				assert !generator.currentlyReachable();
				if (block.predecessorEdgesCount() > 0)
				{
					generator.startBlock(block);
					final List<L2ReadBoxedOperand> arguments =
						semanticArguments.stream()
							.map(a -> currentManifest().readBoxed(a))
							.collect(toList());
					generateGeneralFunctionInvocation(
						generator.boxedConstant(function),
						arguments,
						true,
						callSiteHelper);
					assert !generator.currentlyReachable();
				}
			};
			callSiteHelper.invocationSitesToCreate.put(
				function, new Pair<>(block, newAction));
		}
		else
		{
			block = existingPair.first();
		}
		// Whether we just created this pair or found it, emit a jump to
		// the block.
		generator.jumpTo(block);
	}

	/**
	 * An expanded internal node has been reached.  Emit a type test to
	 * determine which way to jump.  Answer a new {@link InternalNodeMemento}
	 * to pass along to other visitor operations to coordinate branch targets.
	 *
	 * @param callSiteHelper
	 *        The {@link CallSiteHelper} object for this dispatch.
	 * @param semanticArguments
	 *        The list of {@link L2SemanticValue}s supplying argument values.
	 *        These become strengthened by type tests in the current manifest.
	 * @param argumentIndexToTest
	 *        The argument number to test here.  This is a one-based index into
	 *        the list of arguments (which is zero-based).
	 * @param typeToTest
	 *        The type to check the argument against.
	 * @return An {@link InternalNodeMemento} which is made available in other
	 *         callbacks for this particular type test node.  It captures branch
	 *         labels, for example.
	 */
	InternalNodeMemento preInternalVisit (
		final CallSiteHelper callSiteHelper,
		final List<L2SemanticValue> semanticArguments,
		final int argumentIndexToTest,
		final A_Type typeToTest)
	{
		final InternalNodeMemento memento = preInternalVisitForJustTheJumps(
			callSiteHelper, semanticArguments, argumentIndexToTest, typeToTest);

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
		final L2SemanticValue semanticArgument =
			semanticArguments.get(argumentIndexToTest - 1);
		final TypeRestriction argumentRestriction =
			currentManifest().restrictionFor(semanticArgument);
		if (!argumentRestriction.intersectsType(typeToTest))
		{
			generator.addUnreachableCode();
		}
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
	 * @param semanticArguments
	 *        The list of {@link L2SemanticValue}s supplying argument values.
	 *        These become strengthened by type tests in the current manifest.
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
		final List<L2SemanticValue> semanticArguments,
		final int argumentIndexToTest,
		final A_Type typeToTest)
	{
		final L2SemanticValue semanticArgument =
			semanticArguments.get(argumentIndexToTest - 1);
		final L2ReadBoxedOperand argRead =
			currentManifest().readBoxed(semanticArgument);
		final InternalNodeMemento memento =
			new InternalNodeMemento(
				argumentIndexToTest,
				typeToTest,
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

		final TypeRestriction argRestriction = argRead.restriction();

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
			final TypeRestriction intersection =
				argRestriction.intersectionWithType(typeToTest);
			if (intersection == bottomRestriction)
			{
				// It will always fail the test.
				generator.jumpTo(memento.failCheckBasicBlock);
				return memento;
			}

			if (argRestriction.type.isSubtypeOf(typeToTest))
			{
				// It will always pass the test.
				generator.jumpTo(memento.passCheckBasicBlock);
				return memento;
			}

			// A runtime test is needed.  Try to special-case small enumeration.
			final @Nullable A_Set possibleValues =
				intersection.enumerationValuesOrNull(maxExpandedEqualityChecks);
			if (possibleValues != null)
			{
				// The restriction has a small number of values.  Use equality
				// checks rather than the more general type checks.
				final Iterator<AvailObject> iterator =
					possibleValues.iterator();
				A_BasicObject instance = iterator.next();
				while (iterator.hasNext())
				{
					final L2BasicBlock nextCheckOrFail =
						generator.createBasicBlock(
							"test next case of enumeration");
					jumpIfEqualsConstant(
						argRead,
						instance,
						memento.passCheckBasicBlock,
						nextCheckOrFail);
					generator.startBlock(nextCheckOrFail);
					instance = iterator.next();
				}
				jumpIfEqualsConstant(
					argRead,
					instance,
					memento.passCheckBasicBlock,
					memento.failCheckBasicBlock);
				return memento;
			}
			// A runtime test is needed, and it's not a small enumeration.
			jumpIfKindOfConstant(
				argRead, typeToTest, memento.passCheckBasicBlock,
				memento.failCheckBasicBlock);
			return memento;
		}

		// The argument is subject to a super-cast.
		if (argRestriction.type.isSubtypeOf(superUnionElementType))
		{
			// The argument's actual type will always be a subtype of the
			// superUnion type, so the dispatch will always be decided by only
			// the superUnion type, which does not vary at runtime.  Decide
			// the branch direction right now.
			generator.jumpTo(
				superUnionElementType.isSubtypeOf(typeToTest)
					? memento.passCheckBasicBlock
					: memento.failCheckBasicBlock);
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
		final A_Type argMeta = instanceMeta(argRestriction.type);
		final L2WriteBoxedOperand argTypeWrite =
			generator.boxedWriteTemp(argRestriction.metaRestriction());
		addInstruction(L2_GET_TYPE.instance, argRead, argTypeWrite);
		final L2ReadBoxedOperand superUnionReg =
			generator.boxedConstant(superUnionElementType);
		final L2WriteBoxedOperand unionReg =
			generator.boxedWriteTemp(
				restrictionForType(
					argMeta.typeUnion(superUnionReg.type()),
					BOXED));
		addInstruction(
			L2_TYPE_UNION.instance,
			readBoxed(argTypeWrite),
			superUnionReg,
			unionReg);
		addInstruction(
			L2_JUMP_IF_SUBTYPE_OF_CONSTANT.instance,
			readBoxed(unionReg),
			new L2ConstantOperand(typeToTest),
			edgeTo(memento.passCheckBasicBlock),
			edgeTo(memento.failCheckBasicBlock));
		return memento;
	}

	/**
	 * Generate conditional branch to either {@code passBlock} or
	 * {@code failBlock}, based on whether the given register equals the given
	 * constant value.
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
	 * @param passBlock
	 *        Where to go if the register's value equals the constant.
	 * @param failBlock
	 *        Where to go if the register's value does not equal the constant.
	 */
	public void jumpIfEqualsConstant (
		final L2ReadBoxedOperand registerToTest,
		final A_BasicObject constantValue,
		final L2BasicBlock passBlock,
		final L2BasicBlock failBlock)
	{
		if (constantValue.isBoolean())
		{
			final boolean constantBool =
				constantValue.equals(AtomDescriptor.trueObject());
			final L2Instruction boolSource =
				registerToTest.definitionSkippingMoves(true);
			if (boolSource.operation() instanceof L2_RUN_INFALLIBLE_PRIMITIVE)
			{
				final Primitive primitive =
					L2_RUN_INFALLIBLE_PRIMITIVE.primitiveOf(boolSource);
				if (primitive == P_Equality.INSTANCE)
				{
					final List<L2ReadBoxedOperand> args =
						L2_RUN_INFALLIBLE_PRIMITIVE.argsOf(boolSource);
					// If either operand of P_Equality is a constant, recurse to
					// allow deeper replacement.
					@Nullable A_BasicObject previousConstant =
						args.get(0).constantOrNull();
					final L2ReadBoxedOperand previousRegister;
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
						jumpIfEqualsConstant(
							previousRegister,
							previousConstant,
							constantBool ? passBlock : failBlock,
							constantBool ? failBlock : passBlock);
						return;
					}
					// Neither value is a constant, but we can still do the
					// compare-and-branch without involving Avail booleans.
					addInstruction(
						L2_JUMP_IF_OBJECTS_EQUAL.instance,
						args.get(0),
						args.get(1),
						edgeTo(constantBool ? passBlock : failBlock),
						edgeTo(constantBool ? failBlock : passBlock));
					return;
				}
				else if (boolSource.operation()
					== L2_JUMP_IF_SUBTYPE_OF_CONSTANT.instance)
				{
					// Instance-of testing is done by extracting the type and
					// testing if it's a subtype.  See if the operand to the
					// is-subtype test is a get-type instruction.
					final L2ReadBoxedOperand firstTypeOperand =
						boolSource.operand(0);
					final L2ConstantOperand secondConstantOperand =
						boolSource.operand(1);
					final L2Instruction firstTypeSource =
						firstTypeOperand.definitionSkippingMoves(true);
					if (firstTypeSource.operation() == L2_GET_TYPE.instance)
					{
						// There's a get-type followed by an is-subtype
						// followed by a compare-and-branch of the result
						// against a constant boolean.  Replace with a
						// branch-if-kind.
						final L2ReadBoxedOperand valueSource =
							L2_GET_TYPE.sourceValueOf(firstTypeSource);
						jumpIfKindOfConstant(
							valueSource,
							secondConstantOperand.object,
							constantBool ? passBlock : failBlock,
							constantBool ? failBlock : passBlock);
						return;
					}
					// Perform a branch-if-is-subtype-of instead of checking
					// whether the Avail boolean is true or false.
					addInstruction(
						L2_JUMP_IF_SUBTYPE_OF_CONSTANT.instance,
						firstTypeOperand,
						secondConstantOperand,
						edgeTo(constantBool ? passBlock : failBlock),
						edgeTo(constantBool ? failBlock : passBlock));
					return;
				}
				else if (boolSource.operation()
					== L2_JUMP_IF_SUBTYPE_OF_OBJECT.instance)
				{
					// Instance-of testing is done by extracting the type and
					// testing if it's a subtype.  See if the operand to the
					// is-subtype test is a get-type instruction.
					final L2ReadBoxedOperand firstTypeOperand =
						boolSource.operand(0);
					final L2ReadBoxedOperand secondTypeOperand =
						boolSource.operand(0);
					final L2Instruction firstTypeSource =
						firstTypeOperand.definitionSkippingMoves(true);
					if (firstTypeSource.operation() == L2_GET_TYPE.instance)
					{
						// There's a get-type followed by an is-subtype
						// followed by a compare-and-branch of the result
						// against a constant boolean.  Replace with a
						// branch-if-kind.
						final L2ReadBoxedOperand valueSource =
							L2_GET_TYPE.sourceValueOf(firstTypeSource);
						addInstruction(
							L2_JUMP_IF_KIND_OF_OBJECT.instance,
							valueSource,
							secondTypeOperand,
							edgeTo(constantBool ? passBlock : failBlock),
							edgeTo(constantBool ? failBlock : passBlock));
						return;
					}
					// Perform a branch-if-is-subtype-of instead of checking
					// whether the Avail boolean is true or false.
					addInstruction(
						L2_JUMP_IF_SUBTYPE_OF_OBJECT.instance,
						firstTypeOperand,
						secondTypeOperand,
						edgeTo(constantBool ? passBlock : failBlock),
						edgeTo(constantBool ? failBlock : passBlock));
					return;
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
			edgeTo(passBlock),
			edgeTo(failBlock));
	}

	/**
	 * Generate code to invoke a function in a register with arguments in
	 * registers.  Also branch to the appropriate reification and return clauses
	 * depending on whether the returned value is guaranteed to satisfy the
	 * expectedType or not.
	 *
	 * <p>The code generation position is never {@link
	 * L2Generator#currentlyReachable()} after this (Java) method completes.</p>
	 *
	 * <p>The final output from the entire polymorphic call will always be fully
	 * strengthened to the intersection of the VM-guaranteed type and the
	 * expectedType of the callSiteHelper, although an explicit type check may
	 * have to be generate along some paths.</p>
	 *
	 * @param functionToCallReg
	 *        The {@link L2ReadBoxedOperand} containing the function to
	 *        invoke.
	 * @param arguments
	 *        The {@link List} of {@link L2ReadBoxedOperand}s that supply
	 *        arguments to the function.
	 * @param tryToGenerateSpecialPrimitiveInvocation
	 *        {@code true} if an attempt should be made to generate a customized
	 *        {@link L2Instruction} sequence for a {@link Primitive} invocation,
	 *        {@code false} otherwise. This should generally be {@code false}
	 *        only to prevent recursion from {@code Primitive} customization.
	 * @param callSiteHelper
	 *        Information about the call being generated.
	 */
	public void generateGeneralFunctionInvocation (
		final L2ReadBoxedOperand functionToCallReg,
		final List<L2ReadBoxedOperand> arguments,
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

		final @Nullable A_RawFunction rawFunction =
			determineRawFunction(functionToCallReg);
		if (rawFunction != null)
		{
			final @Nullable Primitive primitive = rawFunction.primitive();
			if (primitive != null)
			{
				final boolean generated;
				final A_Type argsTupleType =
					rawFunction.functionType().argsTupleType();
				if (tryToGenerateSpecialPrimitiveInvocation)
				{
					// We are not recursing here from a primitive override of
					// tryToGenerateSpecialPrimitiveInvocation(), so try to
					// generate a special primitive invocation.  Note that this
					// lookup was monomorphic *in the event of success*, so we
					// can safely tighten the argument types here to conform to
					// the only possible found function.
					final List<L2ReadBoxedOperand> strongArguments =
						new ArrayList<>(argumentCount);
					final L2ValueManifest manifest = currentManifest();
					for (int i = 0; i < argumentCount; i++)
					{
						final L2ReadBoxedOperand arg = arguments.get(i);
						final L2SemanticValue argSemanticValue =
							arg.semanticValue();
						final TypeRestriction strongRestriction =
							arg.restriction()
								.intersection(
									manifest.restrictionFor(argSemanticValue))
								.intersectionWithType(
									argsTupleType.typeAtIndex(i + 1));
						manifest.setRestriction(
							argSemanticValue, strongRestriction);
						strongArguments.add(
							new L2ReadBoxedOperand(
								argSemanticValue, strongRestriction, manifest));
					}
					generated = tryToGenerateSpecialInvocation(
						functionToCallReg,
						rawFunction,
						primitive,
						strongArguments,
						callSiteHelper);
				}
				else
				{
					// We are recursing here from a primitive override of
					// tryToGenerateSpecialPrimitiveInvocation(), so do not
					// recurse again; just generate the best invocation possible
					// given what we know.
					final List<A_Type> argumentTypes =
						new ArrayList<>(argumentCount);
					for (int i = 0; i < argumentCount; i++)
					{
						final L2ReadBoxedOperand argument = arguments.get(i);
						final A_Type narrowedType =
							argument.type().typeIntersection(
								argsTupleType.typeAtIndex(i + 1));
						argumentTypes.add(narrowedType);
					}
					if (primitive.fallibilityForArgumentTypes(argumentTypes)
						== CallSiteCannotFail)
					{
						// The primitive cannot fail at this site. Output code
						// to run the primitive as simply as possible, feeding a
						// register with as strong a type as possible.
						final L2WriteBoxedOperand writer =
							generator.boxedWriteTemp(
								restrictionForType(
									primitive.returnTypeGuaranteedByVM(
										rawFunction, argumentTypes),
									BOXED));
						addInstruction(
							L2_RUN_INFALLIBLE_PRIMITIVE.forPrimitive(primitive),
							new L2ConstantOperand(rawFunction),
							new L2PrimitiveOperand(primitive),
							new L2ReadBoxedVectorOperand(arguments),
							writer);
						callSiteHelper.useAnswer(readBoxed(writer));
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

		final A_Type guaranteedResultType =
			functionToCallReg.type().returnType();
		final boolean skipCheck =
			guaranteedResultType.isSubtypeOf(callSiteHelper.expectedType);
		final @Nullable A_Function constantFunction =
			functionToCallReg.constantOrNull();
		final boolean canReturn = !guaranteedResultType.isVacuousType();
		final L2BasicBlock successBlock =
			generator.createBasicBlock("successful invocation");
		final L2BasicBlock targetBlock = !canReturn
			? callSiteHelper.onReificationUnreturnable
			: skipCheck
				? callSiteHelper.onReificationNoCheck
				: callSiteHelper.onReificationWithCheck;
		final L2BasicBlock reificationTarget = generator.createBasicBlock(
			"invoke reification target",
			targetBlock.zone);
		final L2WriteBoxedOperand writeResult = writeSlot(
			stackp,
			instructionDecoder.pc() + (skipCheck ? 0 : -1),
			restrictionForType(
				guaranteedResultType.isBottom()
					? ANY.o()  // unreachable
					: guaranteedResultType,
				BOXED));
		if (constantFunction != null)
		{
			addInstruction(
				L2_INVOKE_CONSTANT_FUNCTION.instance,
				new L2ConstantOperand(constantFunction),
				new L2ReadBoxedVectorOperand(arguments),
				writeResult,
				canReturn
					? edgeTo(successBlock)
					: generator.unreachablePcOperand(),
				edgeTo(reificationTarget));
		}
		else
		{
			addInstruction(
				L2_INVOKE.instance,
				functionToCallReg,
				new L2ReadBoxedVectorOperand(arguments),
				writeResult,
				canReturn
					? edgeTo(successBlock)
					: generator.unreachablePcOperand(),
				edgeTo(reificationTarget));
		}
		generator.startBlock(reificationTarget);
		generator.addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(TRANSIENT.offsetInDefaultChunk),
			new L2CommentOperand(
				"Transient - cannot be invalid."));
		generator.jumpTo(targetBlock);

		generator.startBlock(successBlock);
		if (generator.currentlyReachable())
		{
			generator.jumpTo(
				skipCheck
					? callSiteHelper.afterCallNoCheck
					: callSiteHelper.afterCallWithCheck);
			assert !generator.currentlyReachable();
		}
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
		final L2SemanticValue semanticValue =
			topFrame().slot(stackp, instructionDecoder.pc() - 1);
		final L2ReadBoxedOperand uncheckedValueRead =
			currentManifest().readBoxed(semanticValue);
		if (uncheckedValueRead.type().isVacuousType())
		{
			// There are no return values possible, so we can't get here.  It
			// would be wrong to do this based on the expectedType being bottom,
			// since that's only an erroneous semantic restriction, not a VM
			// problem.
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
		if (!uncheckedValueRead.restriction().intersectsType(expectedType))
		{
			// It's impossible to return a valid value here, since the value's
			// type bound and the expected type don't intersect.  Always invoke
			// the bad type handler.
			generator.jumpTo(failedCheck);
		}
		else
		{
			assert !uncheckedValueRead.type().isSubtypeOf(expectedType)
				: "Attempting to create unnecessary type check";
			jumpIfKindOfConstant(
				uncheckedValueRead, expectedType, passedCheck, failedCheck);
		}

		// The type check failed, so report it.
		generator.startBlock(failedCheck);
		final L2WriteBoxedOperand variableToHoldValueWrite =
			generator.boxedWriteTemp(
				restrictionForType(variableTypeFor(ANY.o()), BOXED));
		addInstruction(
			L2_CREATE_VARIABLE.instance,
			new L2ConstantOperand(variableTypeFor(ANY.o())),
			variableToHoldValueWrite);
		final L2BasicBlock wroteVariable =
			generator.createBasicBlock("wrote offending value into variable");
		addInstruction(
			L2_SET_VARIABLE_NO_CHECK.instance,
			readBoxed(variableToHoldValueWrite),
			uncheckedValueRead,
			edgeTo(wroteVariable),
			edgeTo(wroteVariable));

		// Whether the set succeeded or failed doesn't really matter, although
		// it should always succeed for this freshly created variable.
		generator.startBlock(wroteVariable);
		// Recurse to generate the call to the failure handler.  Since it's
		// bottom-valued, and can therefore skip the result check, the recursive
		// call won't exceed two levels deep.
		final L2BasicBlock onReificationInHandler =
			generator.createBasicBlock(
				"continue reification for failed return check",
				PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Reification while handling failed return check"));
		addInstruction(
			L2_INVOKE.instance,
			getInvalidResultFunctionRegister(),
			new L2ReadBoxedVectorOperand(
				asList(
					getReturningFunctionRegister(),
					generator.boxedConstant(expectedType),
					readBoxed(variableToHoldValueWrite))),
			generator.boxedWriteTemp(anyRestriction), // unreachable
			generator.unreachablePcOperand(),
			edgeTo(onReificationInHandler));

		// Reification has been requested while the call is in progress.
		generator.startBlock(onReificationInHandler);
		generator.addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(TRANSIENT.offsetInDefaultChunk),
			new L2CommentOperand(
				"Transient - cannot be invalid."));
		reify(bottom(), TO_RETURN_INTO);

		// Generate the much more likely passed-check flow.
		generator.startBlock(passedCheck);
		if (generator.currentlyReachable())
		{
			forceSlotRegister(
				stackp,
				instructionDecoder.pc(),
				uncheckedValueRead.semanticValue(),
				uncheckedValueRead.restriction().intersection(
					restrictionForType(expectedType, BOXED)));
		}
	}

	/**
	 * Generate code to test the value in {@code valueRead} against the constant
	 * {@code expectedType}, jumping to {@code passedCheck} if it conforms, or
	 * {@code failedCheck} otherwise.
	 *
	 * @param valueRead
	 *        The {@link L2ReadBoxedOperand} that provides the value to check.
	 * @param expectedType
	 *        The exact {@link A_Type} to check the value against.
	 * @param passedCheck
	 *        Where to jump if the value's type is of the expected type.
	 * @param failedCheck
	 *        Where to jump if the value's type is not of the expected type.
	 */
	public void jumpIfKindOfConstant (
		final L2ReadBoxedOperand valueRead,
		final A_Type expectedType,
		final L2BasicBlock passedCheck,
		final L2BasicBlock failedCheck)
	{
		// Check for special cases.
		if (valueRead.restriction().containedByType(expectedType))
		{
			generator.jumpTo(passedCheck);
			return;
		}
		if (!valueRead.restriction().intersectsType(expectedType))
		{
			generator.jumpTo(failedCheck);
			return;
		}
		// Trace back to the definition of the read's register, to see if it's
		// a function that's created in the current chunk.
		final @Nullable A_RawFunction rawFunction =
			determineRawFunction(valueRead);
		if (rawFunction != null)
		{
			final A_Type exactKind = rawFunction.functionType();
			if (exactKind.isSubtypeOf(expectedType))
			{
				generator.jumpTo(passedCheck);
				return;
			}
			if (!expectedType.isEnumeration())
			{
				// Don't check for vacuous type intersection here.  We know the
				// exact kind, and it's specifically *not* a subtype of the
				// expectedType, which is also a kind (i.e., not an
				// enumeration).
				generator.jumpTo(failedCheck);
				return;
			}
		}
		// We can't pin it down statically, so do the dynamic check.
		addInstruction(
			L2_JUMP_IF_KIND_OF_CONSTANT.instance,
			valueRead,
			new L2ConstantOperand(expectedType),
			edgeTo(passedCheck),
			edgeTo(failedCheck));
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
		final L2ReadBoxedOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final Primitive primitive,
		final List<L2ReadBoxedOperand> arguments,
		final CallSiteHelper callSiteHelper)
	{
		final int argumentCount = arguments.size();
		if (primitive.hasFlag(CanFold))
		{
			// It can be folded, if supplied with constants.
			final List<AvailObject> constants = new ArrayList<>(argumentCount);
			for (final L2ReadBoxedOperand regRead : arguments)
			{
				final @Nullable AvailObject constant = regRead.constantOrNull();
				if (constant == null)
				{
					break;
				}
				constants.add(constant);
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
					Interpreter.Companion.log(
						Interpreter.loggerDebugL2,
						Level.FINER,
						"{0}FOLD {1}:",
						interpreter.debugModeString,
						primitive.fieldName());
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
						generator.boxedConstant(
							interpreter.getLatestResult().makeImmutable()));
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
		final List<L2ReadBoxedOperand> narrowedArguments =
			new ArrayList<>(argumentCount);
		for (int i = 0; i < argumentCount; i++)
		{
			final L2ReadBoxedOperand argument =
				generator.readBoxed(arguments.get(i).semanticValue());
			assert argument.restriction().type.isSubtypeOf(
				signatureTupleType.typeAtIndex(i + 1));

			narrowedArgTypes.add(argument.restriction().type);
			narrowedArguments.add(argument);
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
	 *        The {@link L2ReadBoxedOperand} containing the function to
	 *        invoke.
	 * @return Either {@code null} or the function's {@link A_RawFunction}.
	 */
	private static @Nullable A_RawFunction determineRawFunction (
		final L2ReadBoxedOperand functionToCallReg)
	{
		final @Nullable A_Function functionIfKnown =
			functionToCallReg.constantOrNull();
		if (functionIfKnown != null)
		{
			// The exact function is known.
			return functionIfKnown.code();
		}
		// See if we can at least find out the raw function that the function
		// was created from.
		final L2Instruction functionDefinition =
			functionToCallReg.definitionSkippingMoves(true);
		return functionDefinition.operation().getConstantCodeFrom(
			functionDefinition);
	}

	/**
	 * Generate a slower, but much more compact invocation of a polymorphic
	 * method call.  The slots have already been adjusted to be consistent with
	 * having popped the arguments and pushed the expected type.
	 *
	 * @param callSiteHelper
	 *        Information about the method call site.
	 * @param semanticArguments
	 *        The list of {@link L2SemanticValue}s supplying argument values.
	 *        These become strengthened by type tests in the current manifest.
	 */
	private void generateSlowPolymorphicCall (
		final CallSiteHelper callSiteHelper,
		final List<L2SemanticValue> semanticArguments)
	{
		final A_Bundle bundle = callSiteHelper.bundle;
		final A_Method method = A_Bundle.Companion.bundleMethod(bundle);
		final int nArgs = method.numArgs();
		final L2BasicBlock lookupSucceeded = generator.createBasicBlock(
			"lookup succeeded for " + callSiteHelper.quotedBundleName);
		final L2BasicBlock lookupFailed = generator.createBasicBlock(
			"lookup failed for " + callSiteHelper.quotedBundleName);

		final List<TypeRestriction> argumentRestrictions =
			new ArrayList<>(nArgs);
		for (int i = 1; i <= nArgs; i++)
		{
			final TypeRestriction argumentRestriction =
				currentManifest().restrictionFor(semanticArguments.get(i - 1));
			final TypeRestriction unionRestriction = argumentRestriction.union(
				restrictionForType(
					callSiteHelper.superUnionType.typeAtIndex(i),
					BOXED));
			argumentRestrictions.add(unionRestriction);
		}

		final List<A_Function> possibleFunctions = new ArrayList<>();
		for (final A_Definition definition :
			A_Bundle.Companion.bundleMethod(bundle).definitionsAtOrBelow(argumentRestrictions))
		{
			if (definition.isMethodDefinition())
			{
				possibleFunctions.add(definition.bodyBlock());
			}
		}
		final A_Type functionTypeUnion = enumerationWith(
			setFromCollection(possibleFunctions));
		final List<L2ReadBoxedOperand> argumentReads =
			semanticArguments.stream()
				.map(a -> currentManifest().readBoxed(a))
				.collect(toList());
		// At some point we might want to introduce a SemanticValue for tagging
		// this register.
		if (functionTypeUnion.isBottom())
		{
			// There were no possible method definitions, so jump immediately to
			// the lookup failure clause.  Don't generate the success case.
			// For consistency, generate a jump to the lookupFailed exit point,
			// then generate it immediately.
			generator.jumpTo(lookupFailed);
			generator.startBlock(lookupFailed);
			generateLookupFailure(
				method,
				callSiteHelper,
				generator.boxedConstant(E_NO_METHOD_DEFINITION.numericCode()),
				argumentRestrictions,
				argumentReads);
			return;
		}
		// It doesn't necessarily always fail, so try a lookup.
		final L2WriteBoxedOperand functionWrite =
			generator.boxedWriteTemp(
				restrictionForType(functionTypeUnion, BOXED));
		final L2WriteBoxedOperand errorCodeWrite =
			generator.boxedWriteTemp(
				restrictionForType(
					L2_LOOKUP_BY_VALUES.lookupErrorsType, BOXED));
		if (!callSiteHelper.isSuper)
		{
			// Not a super-call.
			addInstruction(
				L2_LOOKUP_BY_VALUES.instance,
				new L2SelectorOperand(bundle),
				new L2ReadBoxedVectorOperand(argumentReads),
				functionWrite,
				errorCodeWrite,
				edgeTo(lookupSucceeded),
				edgeTo(lookupFailed));
		}
		else
		{
			// Extract a tuple type from the runtime types of the arguments,
			// take the type union with the superUnionType, then perform a
			// lookup-by-types using that tuple type.
			final List<L2ReadBoxedOperand> argTypeRegs = new ArrayList<>(nArgs);
			for (int i = 1; i <= nArgs; i++)
			{
				final L2ReadBoxedOperand argReg = argumentReads.get(i - 1);
				final A_Type argStaticType = argReg.type();
				final A_Type superUnionElementType =
					callSiteHelper.superUnionType.typeAtIndex(i);
				final L2ReadBoxedOperand argTypeReg;
				if (argStaticType.isSubtypeOf(superUnionElementType))
				{
					// The lookup is entirely determined by the super-union.
					argTypeReg = generator.boxedConstant(superUnionElementType);
				}
				else
				{
					final A_Type typeBound =
						argStaticType.typeUnion(superUnionElementType);
					final L2WriteBoxedOperand argTypeWrite =
						generator.boxedWriteTemp(
							restrictionForType(instanceMeta(typeBound), BOXED));
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
						final L2WriteBoxedOperand originalArgTypeWrite =
							generator.boxedWriteTemp(
								restrictionForType(
									instanceMeta(typeBound), BOXED));
						addInstruction(
							L2_GET_TYPE.instance, argReg, originalArgTypeWrite);
						addInstruction(
							L2_TYPE_UNION.instance,
							readBoxed(originalArgTypeWrite),
							generator.boxedConstant(superUnionElementType),
							argTypeWrite);
					}
					argTypeReg = readBoxed(argTypeWrite);
				}
				argTypeRegs.add(argTypeReg);
			}
			addInstruction(
				L2_LOOKUP_BY_TYPES.instance,
				new L2SelectorOperand(bundle),
				new L2ReadBoxedVectorOperand(argTypeRegs),
				functionWrite,
				errorCodeWrite,
				edgeTo(lookupSucceeded),
				edgeTo(lookupFailed));
		}
		// At this point, we've attempted to look up the method, and either
		// jumped to lookupSucceeded with functionWrite set to the body
		// function, or jumped to lookupFailed with errorCodeWrite set to
		// the lookup error code.

		// Emit the lookup failure case.
		generator.startBlock(lookupFailed);
		generateLookupFailure(
			method,
			callSiteHelper,
			readBoxed(errorCodeWrite),
			argumentRestrictions,
			argumentReads);

		// Now invoke the method definition's body.  We've already examined all
		// possible method definition bodies to see if they all conform with the
		// expectedType, and captured that in alwaysSkipResultCheck.
		generator.startBlock(lookupSucceeded);
		final @Nullable A_Function constantFunction =
			readBoxed(functionWrite).restriction().constantOrNull;
		if (constantFunction != null)
		{
			// Even though we couldn't prove statically that this function was
			// always looked up, we proved the slightly weaker condition that if
			// the lookup was successful, it must have produced this function.
			// Jump into the same block that will be generated for a positive
			// inlined lookup of the same function.
			promiseToHandleCallForDefinitionBody(
				constantFunction, semanticArguments, callSiteHelper);
		}
		else
		{
			generateGeneralFunctionInvocation(
				readBoxed(functionWrite),
				argumentReads,
				true,
				callSiteHelper);
		}
	}

	/**
	 * Generate code to report a lookup failure.
	 *
	 * @param method
	 *        The
	 * @param callSiteHelper
	 *        Information about the method call site.
	 * @param errorCodeRead
	 *        The register containing the numeric {@link AvailErrorCode}
	 *        indicating the lookup problem.
	 * @param argumentRestrictions
	 *        The {@link TypeRestriction}s on the arguments.
	 * @param argumentReads
	 *        The source {@link L2ReadBoxedVectorOperand}s supplying arguments.
	 */
	private void generateLookupFailure (
		final A_Method method,
		final CallSiteHelper callSiteHelper,
		final L2ReadBoxedOperand errorCodeRead,
		final List<TypeRestriction> argumentRestrictions,
		final List<L2ReadBoxedOperand> argumentReads)
	{
		final L2WriteBoxedOperand invalidSendReg =
			generator.boxedWriteTemp(
				restrictionForType(INVALID_MESSAGE_SEND.functionType, BOXED));
		addInstruction(
			L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
			invalidSendReg);
		// Collect the argument types into a tuple type.
		final List<A_Type> argTypes = argumentRestrictions.stream()
			.map(argumentRestriction -> argumentRestriction.type)
			.collect(toList());
		final L2WriteBoxedOperand argumentsTupleWrite =
			generator.boxedWriteTemp(
				restrictionForType(tupleTypeForTypes(argTypes), BOXED));
		addInstruction(
			L2_CREATE_TUPLE.instance,
			new L2ReadBoxedVectorOperand(argumentReads),
			argumentsTupleWrite);
		final L2BasicBlock onReificationDuringFailure =
			generator.createBasicBlock(
				"reify in method lookup failure handler for "
					+ callSiteHelper.quotedBundleName,
				PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Continue reification during lookup failure handler"));
		addInstruction(
			L2_INVOKE.instance,
			readBoxed(invalidSendReg),
			new L2ReadBoxedVectorOperand(
				asList(
					errorCodeRead,
					generator.boxedConstant(method),
					readBoxed(argumentsTupleWrite))),
			generator.boxedWriteTemp(anyRestriction), // unreachable
			generator.unreachablePcOperand(),
			edgeTo(onReificationDuringFailure));

		// Reification has been requested while the failure call is in
		// progress.
		generator.startBlock(onReificationDuringFailure);
		generator.addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(TRANSIENT.offsetInDefaultChunk),
			new L2CommentOperand(
				"Transient - cannot be invalid."));
		reify(bottom(), TO_RETURN_INTO);
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
			generator.createBasicBlock(
				"On reification for interrupt",
				BEGIN_REIFICATION_FOR_INTERRUPT.createZone(
					"Start reification and run interrupt"));
		addInstruction(
			L2_REIFY.instance,
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(
				StatisticCategory.INTERRUPT_OFF_RAMP_IN_L2.ordinal()),
			edgeTo(onReification));

		generator.startBlock(onReification);
		generator.addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(TRANSIENT.offsetInDefaultChunk),
			new L2CommentOperand(
				"Transient, for interrupt - cannot be invalid."));

		// When the lambda below runs, it's generating code at the point where
		// continuationReg will have the new continuation.
		reify(null, TO_RESUME);
		generator.jumpTo(merge);
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
	 * @return The {@link L2ReadBoxedOperand} into which the variable's value
	 *         will be written, including having made it immutable if requested.
	 */
	public L2ReadBoxedOperand emitGetVariableOffRamp (
		final L2Operation getOperation,
		final L2ReadBoxedOperand variable,
		final boolean makeImmutable)
	{
		assert getOperation.isVariableGet();
		final L2BasicBlock success =
			generator.createBasicBlock("successfully read variable");
		final L2BasicBlock failure =
			generator.createBasicBlock("failed to read variable");
		final L2BasicBlock onReificationDuringFailure =
			generator.createBasicBlock(
				"reify in read variable failure handler",
				PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Continue reification for read-variable failure handler"));

		// Emit the specified get-variable instruction variant.
		final L2WriteBoxedOperand valueWrite =
			generator.boxedWriteTemp(
				restrictionForType(variable.type().readType(), BOXED));
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
		final L2WriteBoxedOperand unassignedReadFunction =
			generator.boxedWriteTemp(
				restrictionForType(
					READ_UNASSIGNED_VARIABLE.functionType, BOXED));
		addInstruction(
			L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION.instance,
			unassignedReadFunction);
		addInstruction(
			L2_INVOKE.instance,
			readBoxed(unassignedReadFunction),
			new L2ReadBoxedVectorOperand(emptyList()),
			generator.boxedWriteTemp(anyRestriction), // unreachable
			generator.unreachablePcOperand(),
			edgeTo(onReificationDuringFailure));

		// Reification has been requested while the failure call is in progress.
		generator.startBlock(onReificationDuringFailure);
		generator.addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(TRANSIENT.offsetInDefaultChunk),
			new L2CommentOperand(
				"Transient - cannot be invalid."));
		reify(bottom(), TO_RETURN_INTO);

		// End with the success path.
		generator.startBlock(success);
		return makeImmutable
			? generator.makeImmutable(readBoxed(valueWrite))
			: readBoxed(valueWrite);
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
		final L2ReadBoxedOperand variable,
		final L2ReadBoxedOperand newValue)
	{
		assert setOperation.isVariableSet();
		final L2BasicBlock success =
			generator.createBasicBlock("set local success");
		final L2BasicBlock failure =
			generator.createBasicBlock("set local failure");
		final L2BasicBlock onReificationDuringFailure =
			generator.createBasicBlock(
				"reify during set local failure",
				PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Continue reification for set-variable failure handler"));
		// Emit the set-variable instruction.
		addInstruction(
			setOperation,
			variable,
			newValue,
			edgeTo(success),
			edgeTo(failure));

		// Emit the failure path.
		generator.startBlock(failure);
		final L2WriteBoxedOperand observeFunction =
			generator.boxedWriteTemp(
				restrictionForType(IMPLICIT_OBSERVE.functionType, BOXED));
		addInstruction(
			L2_GET_IMPLICIT_OBSERVE_FUNCTION.instance,
			observeFunction);
		final L2WriteBoxedOperand variableAndValueTupleReg =
			generator.boxedWriteTemp(
				restrictionForType(
					tupleTypeForTypes(variable.type(), newValue.type()),
					BOXED));
		addInstruction(
			L2_CREATE_TUPLE.instance,
			new L2ReadBoxedVectorOperand(asList(variable, newValue)),
			variableAndValueTupleReg);
		// Note: the handler block's value is discarded; also, since it's not a
		// method definition, it can't have a semantic restriction.
		addInstruction(
			L2_INVOKE.instance,
			readBoxed(observeFunction),
			new L2ReadBoxedVectorOperand(
				asList(
					generator
						.boxedConstant(Interpreter.assignmentFunction()),
					readBoxed(variableAndValueTupleReg))),
			generator.boxedWriteTemp(anyRestriction), // unreachable
			edgeTo(success),
			edgeTo(onReificationDuringFailure));

		generator.startBlock(onReificationDuringFailure);
		generator.addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(TRANSIENT.offsetInDefaultChunk),
			new L2CommentOperand(
				"Transient - cannot be invalid."));
		reify(TOP.o(), TO_RETURN_INTO);
		generator.jumpTo(success);

		// End with the success block.  Note that the failure path can lead here
		// if the implicit-observe function returns.
		generator.startBlock(success);
	}

	/** Statistic for generating an L2Chunk's preamble. */
	private static final Statistic preambleGenerationStat =
		new Statistic(
			"(generate preamble)",
			StatisticReport.L1_NAIVE_TRANSLATION_TIME);

	/** Statistics for timing the translation per L1Operation. */
	private static final Statistic[] levelOneGenerationStats =
		Arrays.stream(L1Operation.values())
			.map(operation -> new Statistic(
				operation.name(), StatisticReport.L1_NAIVE_TRANSLATION_TIME))
			.toArray(Statistic[]::new);

	/**
	 * For each level one instruction, write a suitable transliteration into
	 * level two instructions.
	 */
	private void translateL1Instructions ()
	{
		final long timeAtStartOfTranslation = captureNanos();
		generator.initialBlock.makeIrremovable();
		generator.startBlock(generator.initialBlock);
		final @Nullable Primitive primitive = code.primitive();
		if (primitive != null)
		{
			// Try the primitive, automatically returning if successful.
			addInstruction(
				L2_TRY_PRIMITIVE.instance,
				new L2PrimitiveOperand(primitive));
			if (primitive.hasFlag(CannotFail))
			{
				// Infallible primitives don't need any other L2 code.
				return;
			}
		}
		generator.afterOptionalInitialPrimitiveBlock.makeIrremovable();
		generator.jumpTo(generator.afterOptionalInitialPrimitiveBlock);

		generator.startBlock(generator.afterOptionalInitialPrimitiveBlock);
		currentManifest().clear();
		// While it's true that invalidation may only take place when no Avail
		// code is running (even when evicting old chunks), and it's also the
		// case that invalidation causes the chunk to be disconnected from its
		// compiled code, it's still the case that a continuation (a label, say)
		// created at an earlier time still refers to the invalid chunk.  Ensure
		// it can fall back gracefully to L1 (the default chunk) by entering it
		// at the TO_RESTART entry point.  Note that there can't be a primitive
		// for such continuations.
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(TO_RESTART.offsetInDefaultChunk),
			new L2CommentOperand(
				"If invalid, reenter «default» at the beginning."));

		// Do any reoptimization before capturing arguments.
		if (generator.optimizationLevel == UNOPTIMIZED)
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
				// Create a new semantic slot at the current pc, representing
				// this newly written value.
				final L2WriteBoxedOperand argReg = generator.boxedWrite(
					semanticSlot(i),
					restrictionForType(tupleType.typeAtIndex(i), BOXED));
				addInstruction(
					L2_GET_ARGUMENT.instance,
					new L2IntImmediateOperand(i),
					argReg);
			}
		}

		// Here's where a local P_RestartContinuationWithArguments is optimized
		// to jump to. It's expected to place the replacement arguments into
		// semantic slots n@1.
		generator.restartLoopHeadBlock = generator.createLoopHeadBlock(
			"Loop head for " + code.methodName().asNativeString());
		generator.jumpTo(generator.restartLoopHeadBlock);
		generator.startBlock(generator.restartLoopHeadBlock);

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
					restrictionForType(localType, BOXED)));
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
		while (!instructionDecoder.atEnd() && generator.currentlyReachable())
		{
			final long before = captureNanos();
			final L1Operation operation = instructionDecoder.getOperation();
			operation.dispatch(this);
			levelOneGenerationStats[operation.ordinal()].record(
				captureNanos() - before, interpreterIndex);
		}

		// Generate the implicit return after the instruction sequence.
		if (generator.currentlyReachable())
		{
			final L2ReadBoxedOperand readResult = readSlot(stackp);
			addInstruction(L2_RETURN.instance, readResult);
			assert stackp == numSlots;
			stackp = Integer.MIN_VALUE;
		}

		if (generator.unreachableBlock != null
			&& generator.unreachableBlock.predecessorEdgesCount() > 0)
		{
			// Generate the unreachable block.
			generator.startBlock(generator.unreachableBlock);
			addInstruction(L2_UNREACHABLE_CODE.instance);
			// Now make it a loop head, just so code generated later from
			// placeholders (L2Operation#isPlaceholder()) can still connect to
			// it, as long as it uses a back-edge.
			generator.unreachableBlock.isLoopHead = true;
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
	 * @param unreachableBlock
	 *        A basic block that should be dynamically unreachable.
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

		final L2Generator generator = new L2Generator(
			UNOPTIMIZED,
			new Frame(null, nil, "top frame"),
			"default chunk");

		// 0. First try to run it as a primitive.
//		final L2ControlFlowGraph controlFlowGraph = new L2ControlFlowGraph();
		generator.startBlock(initialBlock);
		generator.addInstruction(
			L2_TRY_OPTIONAL_PRIMITIVE.instance);
		generator.jumpTo(reenterFromRestartBlock);
		// Only if the primitive fails should we even consider optimizing the
		// fallback code.

		// 1. Update counter and maybe optimize *before* extracting arguments.
		generator.startBlock(reenterFromRestartBlock);
		generator.addInstruction(
			L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
			new L2IntImmediateOperand(
				OptimizationLevel.FIRST_TRANSLATION.ordinal()),
			new L2IntImmediateOperand(1));
		// 2. Build registers, get arguments, create locals, capture primitive
		// failure value, if any.
		generator.addInstruction(
			L2_PREPARE_NEW_FRAME_FOR_L1.instance);

		generator.jumpTo(loopBlock);

		// 3. The main L1 interpreter loop.
		generator.startBlock(loopBlock);
		generator.addInstruction(
			L2_INTERPRET_LEVEL_ONE.instance,
			edgeTo(reenterFromCallBlock),
			edgeTo(reenterFromInterruptBlock));

		// 4,5. If reified, calls return here.
		generator.startBlock(reenterFromCallBlock);
		generator.addInstruction(
			L2_REENTER_L1_CHUNK_FROM_CALL.instance);
		generator.addInstruction(
			L2_JUMP.instance,
			backEdgeTo(loopBlock));

		// 6,7. If reified, interrupts return here.
		generator.startBlock(reenterFromInterruptBlock);
		generator.addInstruction(
			L2_REENTER_L1_CHUNK_FROM_INTERRUPT.instance);
		generator.addInstruction(
			L2_JUMP.instance,
			backEdgeTo(loopBlock));

		// 8. Unreachable.
		generator.startBlock(unreachableBlock);
		generator.addInstruction(
			L2_UNREACHABLE_CODE.instance);
		return generator.controlFlowGraph;
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

		final L2Generator generator = new L2Generator(
			optimizationLevel,
			new Frame(null, code, "top frame"),
			code.methodName().asNativeString());
		final L1Translator translator =
			new L1Translator(generator, interpreter, code);
		translator.translate();

		final L2Chunk chunk = generator.chunk();
		interpreter.function = savedFunction;
		interpreter.argsBuffer.clear();
		interpreter.argsBuffer.addAll(savedArguments);
		interpreter.setLatestResult(savedFailureValue);
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

		final L2Optimizer optimizer = new L2Optimizer(generator);
		optimizer.optimize(interpreter);

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
		final L2ReadBoxedOperand sourceRegister = readSlot(localIndex);
		forceSlotRegister(stackp, instructionDecoder.pc(), sourceRegister);
		nilSlot(localIndex);
	}

	@Override
	public void L1_doPushLocal ()
	{
		final int localIndex = instructionDecoder.getOperand();
		stackp--;
		final L2ReadBoxedOperand sourceRegister =
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
		final List<L2ReadBoxedOperand> outers = new ArrayList<>(count);
		for (int i = 1; i <= count; i++)
		{
			outers.add(readSlot(stackp + count - i));
		}
		// Pop the outers, but reserve room for the pushed function.
		stackp += count - 1;
		addInstruction(
			L2_CREATE_FUNCTION.instance,
			new L2ConstantOperand(codeLiteral),
			new L2ReadBoxedVectorOperand(outers),
			writeSlot(
				stackp,
				instructionDecoder.pc(),
				restrictionForType(codeLiteral.functionType(), BOXED)));

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
			stackp, instructionDecoder.pc(), generator.boxedConstant(nil));
		stackp++;
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		final int index = instructionDecoder.getOperand();
		stackp--;
		final L2ReadBoxedOperand valueReg = emitGetVariableOffRamp(
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
		final L2ReadBoxedOperand valueReg = emitGetVariableOffRamp(
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
		final L2ReadBoxedOperand tempVarReg =
			getOuterRegister(outerIndex, outerType);
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			tempVarReg,
			readSlot(stackp));
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(
			stackp, instructionDecoder.pc(), generator.boxedConstant(nil));
		stackp++;
	}

	@Override
	public void L1_doGetLocal ()
	{
		final int index = instructionDecoder.getOperand();
		stackp--;
		final L2ReadBoxedOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			readSlot(index),
			true);
		forceSlotRegister(stackp, instructionDecoder.pc(), valueReg);

	}

	@Override
	public void L1_doMakeTuple ()
	{
		final int count = instructionDecoder.getOperand();
		final List<L2ReadBoxedOperand> vector = new ArrayList<>(count);
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
		for (final L2ReadBoxedOperand regRead : vector)
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
			final List<A_Type> types = vector.stream()
				.map(L2ReadOperand::type)
				.collect(toList());
			addInstruction(
				L2_CREATE_TUPLE.instance,
				new L2ReadBoxedVectorOperand(vector),
				writeSlot(
					stackp,
					instructionDecoder.pc(),
					restrictionForType(tupleTypeForTypes(types), BOXED)));
		}
	}

	@Override
	public void L1_doGetOuter ()
	{
		final int outerIndex = instructionDecoder.getOperand();
		stackp--;
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadBoxedOperand valueReg = emitGetVariableOffRamp(
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
		// Use L2_VIRTUAL_CREATE_LABEL to simplify code motion in the common
		// case that label creation can be postponed into an off-ramp (which is
		// rarely invoked).  Since a label requires its caller to be reified,
		// creating it in an off-ramp is trivial, since the caller will already
		// have been reified by the StackReifier machinery.
		//
		// We just ensured the caller is reified, and captured in reifiedCaller.
		// Create a label continuation whose caller is the reified caller, but
		// only capturing arguments (with pc=0 and stack=empty).

		assert code.primitive() == null;
		final int numArgs = code.numArgs();
		final List<L2ReadBoxedOperand> argumentsForLabel =
			new ArrayList<>(numArgs);
		for (int i = 1; i <= numArgs; i++)
		{
			argumentsForLabel.add(generator.makeImmutable(readSlot(i)));
		}

		// Now create the actual label continuation and push it.
		final A_Type continuationType =
			continuationTypeForFunctionType(code.functionType());
		final L2SemanticValue label = topFrame().label();
		final L2WriteBoxedOperand destinationRegister =
			generator.boxedWrite(label, restriction(continuationType, null));

		addInstruction(
			L2_VIRTUAL_CREATE_LABEL.instance,
			destinationRegister,
			getCurrentFunction(),
			new L2ReadBoxedVectorOperand(argumentsForLabel),
			new L2IntImmediateOperand(code.numSlots()));

		// Continue, with the label having been pushed.
		stackp--;
		forceSlotRegister(
			stackp, instructionDecoder.pc(), currentManifest().readBoxed(label));
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
			final L2ReadBoxedOperand valueReg = emitGetVariableOffRamp(
				L2_GET_VARIABLE.instance,
				generator.boxedConstant(literalVariable),
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
			generator.boxedConstant(literalVariable),
			readSlot(stackp));
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(
			stackp, instructionDecoder.pc(), generator.boxedConstant(nil));
		stackp++;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final L2ReadBoxedOperand source = readSlot(stackp);
		stackp--;
		final L2ReadBoxedOperand immutableRead =
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
		final L2SemanticValue[] temps = new L2SemanticValue[size];
		for (int i = size; i >= 1; i--)
		{
			final L2SemanticValue source = semanticSlot(stackp + size - i);
			final L2SemanticValue temp =
				generator.topFrame.temp(generator.nextUnique());
			generator.moveRegister(L2_MOVE.boxed, source, temp);
			temps[permutation.tupleIntAt(i) - 1] = temp;
		}
		for (int i = size; i >= 1; i--)
		{
			forceSlotRegister(
				stackp + size - i,
				instructionDecoder.pc(),
				currentManifest().readBoxed(temps[i - 1]));
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
		final L2ReadBoxedOperand source = readSlot(stackp);
		forceSlotRegister(destinationIndex, instructionDecoder.pc(), source);
		nilSlot(stackp);
		stackp++;
	}
}
