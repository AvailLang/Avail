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
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
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
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.operation.L2_REIFY.StatisticCategory;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
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

import static com.avail.AvailRuntime.*;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor.continuationTypeForFunctionType;
import static com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CannotFail;
import static com.avail.interpreter.Primitive.Result.FAILURE;
import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restriction;
import static com.avail.optimizer.L2Translator.*;
import static com.avail.utility.Nulls.stripNull;
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
	 * The {@link L2Translator} for which I'm producing an initial translation.
	 */
	private final L2Translator translator;

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
	private final Frame topFrame;

	/**
	 * The {@link L2SemanticValue}s corresponding with to slots of the virtual
	 * continuation.  These indices are zero-based, but the slot numbering is
	 * one-based.
	 */
	private final L2SemanticValue[] semanticSlots;

	/**
	 * The current level one nybblecode program counter during naive translation
	 * to level two.
	 */
	@InnerAccess final MutableInt pc = new MutableInt(1);

	/**
	 * The current stack depth during naive translation to level two.
	 */
	@InnerAccess int stackp;

	/**
	 * The exact function that we're translating, if known.  This is only
	 * non-null if the function captures no outers.
	 */
	private final @Nullable A_Function exactFunctionOrNull;

	/** The control flow graph being generated. */
	final L2ControlFlowGraph controlFlowGraph = new L2ControlFlowGraph();

	/**
	 * The {@link L2BasicBlock} which is the entry point for a function that has
	 * just been invoked.
	 */
	final L2BasicBlock initialBlock = createBasicBlock("START");

	/**
	 * Primitive {@link A_RawFunction}s will start with a {@link
	 * L2_TRY_OPTIONAL_PRIMITIVE} instruction.  If we have just optimized such code, we
	 * must have already tried and failed the primitive (because we try the
	 * primitive prior to {@link L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO}).
	 * In either case, this block is where to continue in the new chunk right
	 * after optimizing.
	 *
	 * <p>In addition, this block is published so that a caller that inlines
	 * a fallible primitive can safely attempt the primitive, then if it fails
	 * invoke the fallback code safely.</p>
	 */
	@Nullable L2BasicBlock afterOptionalInitialPrimitiveBlock = null;

	/**
	 * An {@link L2BasicBlock} that shouldn't actually be dynamically reachable.
	 */
	private @Nullable L2BasicBlock unreachableBlock = null;

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
	 * Answer an L2PcOperand that targets an {@link L2BasicBlock} which should
	 * never actually be dynamically reached.
	 *
	 * @return An {@link L2PcOperand} that should never be traversed.
	 */
	private L2PcOperand unreachablePcOperand ()
	{
		if (unreachableBlock == null)
		{
			unreachableBlock = createBasicBlock("UNREACHABLE");
			// Because we generate the initial code in control flow order, we
			// have to wait until later to generate the instructions.  We strip
			// out all phi information here.
		}
		return new L2PcOperand(unreachableBlock, currentManifest);
	}

	/** The {@link L2BasicBlock} that code is currently being generated into. */
	private @Nullable L2BasicBlock currentBlock = initialBlock;

	/**
	 * Use this {@link L2ValueManifest} to track which {@link L2Register} holds
	 * which {@link L2SemanticValue} at the current code generation point.
	 */
	final L2ValueManifest currentManifest = new L2ValueManifest();

	/**
	 * Answer the current {@link L2ValueManifest}, which tracks which {@link
	 * L2Register} holds which {@link L2SemanticValue} at the current code
	 * generation point.
	 *
	 * @return The current {@link L2ValueManifest}.
	 */
	public L2ValueManifest currentManifest ()
	{
		return currentManifest;
	}

	/**
	 * Create a new L1 naive translator for the given {@link L2Translator}.
	 * @param translator
	 *        The {@link L2Translator} for which I'm producing an initial
	 *        translation from L1.
	 */
	L1Translator (final L2Translator translator)
	{
		this.translator = translator;
		this.code = translator.code;
		this.topFrame = new Frame(null, this.code, "top frame");
		this.numSlots = code.numSlots();
		assert pc.value == 1;
		this.stackp = numSlots + 1;
		this.exactFunctionOrNull = computeExactFunctionOrNullForCode(code);
		this.semanticSlots = new L2SemanticValue[numSlots];
		for (int i = 1; i <= numSlots; i++)
		{
			semanticSlots[i - 1] = topFrame.slot(i, 1);
		}
	}

	/**
	 * Start code generation for the given {@link L2BasicBlock}.  This naive
	 * translator doesn't create loops, so ensure all predecessor blocks have
	 * already finished generation.
	 *
	 * <p>Also, reconcile the slot registers that were collected for each
	 * predecessor, creating an {@link L2_PHI_PSEUDO_OPERATION} if needed.</p>
	 *
	 * @param block The {@link L2BasicBlock} beginning code generation.
	 */
	public void startBlock (final L2BasicBlock block)
	{
		if (!block.isIrremovable())
		{
			if (!block.hasPredecessors())
			{
				currentBlock = null;
				return;
			}
			final List<L2PcOperand> predecessorEdges = block.predecessorEdges();
			if (predecessorEdges.size() == 1)
			{
				final L2PcOperand predecessorEdge = predecessorEdges.get(0);
				final L2BasicBlock predecessorBlock =
					predecessorEdge.sourceBlock();
				final L2Instruction jump = predecessorBlock.finalInstruction();
				if (jump.operation == L2_JUMP.instance)
				{
					// The new block has only one predecessor, which
					// unconditionally jumps to it.  Remove the jump and
					// continue generation in the predecessor block.  Restore
					// the manifest from the jump edge.
					currentManifest.clear();
					currentManifest.populateFromIntersection(
						singletonList(predecessorEdge.manifest()), this);
					predecessorBlock.instructions().remove(
						predecessorBlock.instructions().size() - 1);
					jump.justRemoved();
					currentBlock = predecessorBlock;
					return;
				}
			}
		}
		currentBlock = block;
		controlFlowGraph.startBlock(block);
		block.startIn(this);
	}

	/**
	 * Determine whether the current block is probably reachable.  If it has no
	 * predecessors and is removable, it's unreachable, but otherwise we assume
	 * it's reachable, at least until dead code elimination.
	 *
	 * @return Whether the current block is probably reachable.
	 */
	public boolean currentlyReachable ()
	{
		return currentBlock != null && currentBlock.currentlyReachable();
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
		return stripNull(
			currentManifest.semanticValueToRegister(semanticSlot(slotIndex)));
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
	 * @param type
	 *        The bounding type for the new register.
	 * @param constantOrNull
	 *        The constant value that will be written, if known, otherwise
	 *        {@code null}.
	 * @return A register write representing that continuation slot.
	 */
	private L2WritePointerOperand writeSlot (
		final int slotIndex,
		final int effectivePc,
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		// Create a new L2SemanticSlot at the current pc, representing this
		// newly written value.
		final L2SemanticValue semanticValue =
			topFrame.slot(slotIndex, effectivePc);
		semanticSlots[slotIndex - 1] = semanticValue;
		final L2WritePointerOperand writer =
			newObjectRegisterWriter(type, constantOrNull);
		currentManifest.addBinding(
			semanticValue, new L2ReadPointerOperand(writer.register(), null));
		return writer;
	}

	/**
	 * Set the specified slot register to the provided register.  This is a low
	 * level operation, used when {@link #startBlock(L2BasicBlock) starting
	 * generation} of basic blocks.
	 *
	 * @param slotIndex
	 *        The slot index to replace.
	 * @param register
	 *        The {@link L2ReadPointerOperand} that should now be considered the
	 *        current register representing that slot.
	 */
	@InnerAccess void forceSlotRegister (
		final int slotIndex,
		final L2ReadPointerOperand register)
	{
		forceSlotRegister(slotIndex, pc.value, register);
	}

	/**
	 * Set the specified slot register to the provided register.  This is a low
	 * level operation, used when {@link #startBlock(L2BasicBlock) starting
	 * generation} of basic blocks.
	 *
	 * @param slotIndex
	 *        The slot index to replace.
	 * @param effectivePc
	 *        The effective pc.
	 * @param register
	 *        The {@link L2ReadPointerOperand} that should now be considered the
	 *        current register representing that slot.
	 */
	@InnerAccess void forceSlotRegister (
		final int slotIndex,
		final int effectivePc,
		final L2ReadPointerOperand register)
	{
		// Create a new L2SemanticSlot at the current pc, representing this
		// newly written value.
		final L2SemanticValue semanticValue =
			topFrame.slot(slotIndex, effectivePc);
		semanticSlots[slotIndex - 1] = semanticValue;
		currentManifest.addBinding(semanticValue, register);
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
		forceSlotRegister(slotIndex, constantRegister(nil));
	}

	/**
	 * Answer the next register number, unique within the chunk.
	 *
	 * @return An integer unique within this translator.
	 */
	public int nextUnique ()
	{
		return controlFlowGraph.nextUnique();
	}

	/**
	 * Allocate a new {@link L2ObjectRegister}.  Answer an {@link
	 * L2WritePointerOperand} that writes to it, using the given type and
	 * optional constant value information.
	 *
	 * @param type
	 *        The type of value that the register can hold.
	 * @param constantOrNull
	 *        The exact value in the register, or {@code null} if not known
	 *        statically.
	 * @return The new register write operand.
	 */
	public L2WritePointerOperand newObjectRegisterWriter (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		return new L2WritePointerOperand(new L2ObjectRegister(
			nextUnique(), restriction(type, constantOrNull)));
	}

	/**
	 * Allocate a new {@link L2IntRegister}.  Answer an {@link
	 * L2WriteIntOperand} that writes to it, using the given type and optional
	 * constant value information.
	 *
	 * @param type
	 *        The type of value that the register can hold.
	 * @param constantOrNull
	 *        The exact value in the register, or {@code null} if not known
	 *        statically.
	 * @return The new register write operand.
	 */
	public L2WriteIntOperand newIntRegisterWriter (
		final A_Type type,
		final @Nullable A_Number constantOrNull)
	{
		return new L2WriteIntOperand(new L2IntRegister(
			nextUnique(), restriction(type, constantOrNull)));
	}

	/**
	 * Allocate a new {@link L2FloatRegister}.  Answer an {@link
	 * L2WriteFloatOperand} that writes to it, using the given type and optional
	 * constant value information.
	 *
	 * @param type
	 *        The type of value that the register can hold.
	 * @param constantOrNull
	 *        The exact value in the register, or {@code null} if not known
	 *        statically.
	 * @return The new register write operand.
	 */
	public L2WriteFloatOperand newFloatRegisterWriter (
		final A_Type type,
		final @Nullable A_Number constantOrNull)
	{
		return new L2WriteFloatOperand(new L2FloatRegister(
			nextUnique(), restriction(type, constantOrNull)));
	}

	/**
	 * Answer a {@link L2WritePhiOperand} that writes to the specified
	 * {@link L2Register}.
	 *
	 * @param register
	 *        The register.
	 * @return The new register write operand.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public <R extends L2Register<T>, T extends A_BasicObject>
		L2WritePhiOperand<R, T> newPhiRegisterWriter (final R register)
	{
		return new L2WritePhiOperand<>(register);
	}

	/**
	 * Write instructions to extract the current function, and answer an {@link
	 * L2ReadPointerOperand} for the register that will hold the function
	 * afterward.
	 */
	private L2ReadPointerOperand getCurrentFunction ()
	{
		final L2SemanticValue semanticFunction = topFrame.function();
		final @Nullable L2ReadPointerOperand existingRead =
			currentManifest.semanticValueToRegister(semanticFunction);
		if (existingRead != null)
		{
			return existingRead;
		}
		// We have to get it into a register.
		final L2ReadPointerOperand functionRead;
		if (exactFunctionOrNull != null)
		{
			// The exact function is known.
			functionRead = constantRegister(exactFunctionOrNull);
		}
		else
		{
			// The exact function isn't known, but we know the raw function, so
			// we statically know the function type.
			final L2WritePointerOperand functionWrite =
				newObjectRegisterWriter(code.functionType(), null);
			addInstruction(
				L2_GET_CURRENT_FUNCTION.instance,
				functionWrite);
			functionRead = functionWrite.read();
		}
		currentManifest.addBinding(semanticFunction, functionRead);
		return functionRead;
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
		@Nullable L2ReadPointerOperand outerRead =
			currentManifest.semanticValueToRegister(semanticOuter);
		if (outerRead != null)
		{
			return outerRead;
		}
		if (outerType.instanceCount().equalsInt(1)
			&& !outerType.isInstanceMeta())
		{
			// The exact outer is known statically.
			return constantRegister(outerType.instance());
		}
		final L2ReadPointerOperand functionRead = getCurrentFunction();
		final L2WritePointerOperand outerWrite =
			newObjectRegisterWriter(outerType, null);
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2IntImmediateOperand(outerIndex),
			functionRead,
			outerWrite);
		outerRead = outerWrite.read();
		currentManifest.addBinding(semanticOuter, outerRead);
		return outerRead;
	}

	/**
	 * Write instructions to extract the current reified continuation, and
	 * answer an {@link L2ReadPointerOperand} for the register that will hold
	 * the continuation afterward.
	 */
	private L2ReadPointerOperand getCurrentContinuation ()
	{
		final L2WritePointerOperand continuationTempReg =
			newObjectRegisterWriter(mostGeneralContinuationType(), null);
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
			newObjectRegisterWriter(mostGeneralContinuationType(), null);
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
		final L2WritePointerOperand writer = newObjectRegisterWriter(
			guaranteedType, null);
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
		final L2WritePointerOperand writer = newObjectRegisterWriter(
			mostGeneralFunctionType(), null);
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
		if (currentBlock != null)
		{
			currentBlock.addInstruction(
				new L2Instruction(currentBlock, operation, operands));
		}
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
		if (currentBlock != null)
		{
			currentBlock.addInstruction(instruction);
		}
	}

	/**
	 * Generate instruction(s) to move the given {@link AvailObject} into a
	 * fresh writable slot {@link L2Register} with the given index.
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
		forceSlotRegister(slotIndex, constantRegister(value));
	}

	/**
	 * Write a constant value into a new register. Answer an {@link
	 * L2ReadPointerOperand} for that register. If another register has already
	 * been assigned the same value within the same {@link L2BasicBlock}, just
	 * use that instead of emitting another constant-move.
	 *
	 * @param value
	 *        The constant value to write to a register.
	 * @return The {@link L2ReadPointerOperand} for the new register.
	 */
	public L2ReadPointerOperand constantRegister (final A_BasicObject value)
	{
		final L2SemanticValue constant = L2SemanticValue.constant(value);
		final @Nullable L2ReadPointerOperand existingConstant =
			currentManifest.semanticValueToRegister(constant);
		if (existingConstant != null)
		{
			return existingConstant;
		}
		final A_Type type = value.equalsNil()
			? TOP.o()
			: instanceTypeOrMetaOn(value);
		final L2WritePointerOperand registerWrite =
			newObjectRegisterWriter(type, value);
		addInstruction(
			L2_MOVE_CONSTANT.instance,
			new L2ConstantOperand(value),
			registerWrite);
		final L2ReadPointerOperand read = registerWrite.read();
		currentManifest.addBinding(constant, read);
		return read;
	}

	/**
	 * Write a constant value into a new int register.  Answer an {@link
	 * L2ReadIntOperand} for that register. If another register has already
	 * been assigned the same value within the same {@link L2BasicBlock}, just
	 * use that instead of emitting another constant-move.
	 *
	 * @param value
	 *        The immediate int to write to a new int register.
	 * @return The {@link L2ReadIntOperand} for the new register.
	 */
	public L2ReadIntOperand constantIntRegister (final int value)
	{
		final A_Number boxed = IntegerDescriptor.fromInt(value);
		final L2SemanticValue constant = L2SemanticValue.constant(boxed);
		final @Nullable L2ReadOperand<?, A_Number>
			existingConstant = currentManifest.semanticValueToRegister(
				constant);
		if (existingConstant instanceof L2ReadIntOperand)
		{
			return (L2ReadIntOperand) existingConstant;
		}
		final L2WriteIntOperand registerWrite =
			newIntRegisterWriter(
				InstanceTypeDescriptor.instanceType(boxed),
				boxed);
		addInstruction(
			L2_MOVE_INT_CONSTANT.instance,
			new L2IntImmediateOperand(value),
			registerWrite);
		final L2ReadIntOperand read = registerWrite.read();
		currentManifest.addBinding(constant.unboxedAsInt(), read);
		return read;
	}

	/**
	 * Write a constant value into a new double register.  Answer an {@link
	 * L2ReadFloatOperand} for that register. If another register has already
	 * been assigned the same value within the same {@link L2BasicBlock}, just
	 * use that instead of emitting another constant-move.
	 *
	 * @param value
	 *        The immediate double to write to a new double register.
	 * @return The {@link L2ReadFloatOperand} for the new register.
	 */
	public L2ReadFloatOperand constantFloatRegister (final double value)
	{
		final A_Number boxed = DoubleDescriptor.fromDouble(value);
		final L2SemanticValue constant = L2SemanticValue.constant(boxed);
		final @Nullable L2ReadOperand<?, A_Number>
			existingConstant = currentManifest.semanticValueToRegister(
				constant);
		if (existingConstant instanceof L2ReadFloatOperand)
		{
			return (L2ReadFloatOperand) existingConstant;
		}
		final L2WriteFloatOperand registerWrite =
			newFloatRegisterWriter(
				InstanceTypeDescriptor.instanceType(boxed),
				boxed);
		addInstruction(
			L2_MOVE_FLOAT_CONSTANT.instance,
			new L2FloatImmediateOperand(value),
			registerWrite);
		final L2ReadFloatOperand read = registerWrite.read();
		currentManifest.addBinding(constant.unboxedAsFloat(), read);
		return read;
	}

	/**
	 * Write an unboxed {@code int} value into a new {@link L2IntRegister}, if
	 * necessary, but prefer to answer an existing register that already has an
	 * appropriate value. Use the most efficient technique available, based on
	 * the supplied type information.
	 *
	 * @param read
	 *        The boxed {@link L2ReadPointerOperand}.
	 * @param restrictedType
	 *        The restricted {@linkplain A_Type type} for the reader, which is
	 *        required to intersect {@link
	 *        IntegerRangeTypeDescriptor#int32() int32()}.
	 * @param onSuccess
	 *        Where to jump in the event that an {@link L2_JUMP_IF_UNBOX_INT}
	 *        succeeds. The {@link L2ValueManifest manifest} at this location
	 *        will contain bindings for the unboxed {@code int}. {@linkplain
	 *        #startBlock(L2BasicBlock) Start} this block if a {@code
	 *        L2_JUMP_IF_UNBOX_INT} was needed.
	 * @param onFailure
	 *        Where to jump in the event that an {@link L2_JUMP_IF_UNBOX_INT}
	 *        fails. The manifest at this location will not contain bindings for
	 *        the unboxed {@code int} (since unboxing was not possible).
	 * @return The unboxed {@link L2ReadIntOperand}.
	 */
	public L2ReadIntOperand unboxIntoIntRegister (
		final L2ReadPointerOperand read,
		final A_Type restrictedType,
		final L2BasicBlock onSuccess,
		final L2BasicBlock onFailure)
	{
		assert !restrictedType.typeIntersection(int32()).isBottom();
		@Nullable L2ReadIntOperand unboxed =
			currentManifest.alreadyUnboxedInt(read);
		if (unboxed == null)
		{
			if (read.constantOrNull() != null)
			{
				// The reader is a constant.
				final A_Number value =
					(A_Number) stripNull(read.constantOrNull());
				final L2WriteIntOperand unboxedWriter =
					newIntRegisterWriter(
						InstanceTypeDescriptor.instanceType(value),
						value);
				addInstruction(
					L2_MOVE_INT_CONSTANT.instance,
					new L2IntImmediateOperand(value.extractInt()),
					unboxedWriter);
				unboxed = unboxedWriter.read();
			}
			else if (restrictedType.isSubtypeOf(int32()))
			{
				// The reader is guaranteed to be unboxable. Create unboxed
				// variants for each relevant semantic value.
				final L2WriteIntOperand unboxedWriter =
					newIntRegisterWriter(restrictedType, null);
				addInstruction(
					L2_UNBOX_INT.instance,
					read,
					unboxedWriter);
				unboxed = unboxedWriter.read();
			}
			else
			{
				// The reader may be unboxable. Copy the manifest for the
				// success case, adding unboxed variants for the unboxed
				// reader. Do not add these bindings to the failure case.
				final L2WriteIntOperand unboxedWriter =
					newIntRegisterWriter(restrictedType, null);
				addInstruction(
					L2_JUMP_IF_UNBOX_INT.instance,
					read,
					unboxedWriter,
					new L2PcOperand(onSuccess, currentManifest),
					new L2PcOperand(onFailure, currentManifest));
				unboxed = unboxedWriter.read();
				startBlock(onSuccess);
			}
			// Now create unboxed variants for each relevant semantic value.
			for (final L2SemanticValue semanticValue :
				new ArrayList<>(currentManifest.registerToSemanticValues(
					read.register())))
			{
				currentManifest.addBinding(
					semanticValue.unboxedAsInt(), unboxed);
			}
		}
		return stripNull(unboxed);
	}

	/**
	 * Write an unboxed {@code double} value into a new {@link L2FloatRegister},
	 * if necessary, but prefer to answer an existing register that already has
	 * an appropriate value.
	 *
	 * @param read
	 *        The boxed {@link L2ReadPointerOperand}.
	 * @param restrictedType
	 *        The restricted {@linkplain A_Type type} for the reader, which is
	 *        required to intersect {@link Types#DOUBLE DOUBLE}.
	 * @param onSuccess
	 *        Where to jump in the event that an {@link L2_JUMP_IF_UNBOX_FLOAT}
	 *        succeeds. The {@link L2ValueManifest manifest} at this location
	 *        will contain bindings for the unboxed {@code double}. {@linkplain
	 *        #startBlock(L2BasicBlock) Start} this block if a {@code
	 *        L2_JUMP_IF_UNBOX_FLOAT} was needed.
	 * @param onFailure
	 *        Where to jump in the event that an {@link L2_JUMP_IF_UNBOX_FLOAT}
	 *        fails. The manifest at this location will not contain bindings for
	 *        the unboxed {@code double} (since unboxing was not possible).
	 * @return The unboxed {@link L2ReadFloatOperand}.
	 */
	public L2ReadFloatOperand unboxIntoFloatRegister (
		final L2ReadPointerOperand read,
		final A_Type restrictedType,
		final L2BasicBlock onSuccess,
		final L2BasicBlock onFailure)
	{
		assert !restrictedType.typeIntersection(DOUBLE.o()).isBottom();
		@Nullable L2ReadFloatOperand unboxed =
			currentManifest.alreadyUnboxedFloat(read);
		if (unboxed == null)
		{
			if (read.constantOrNull() != null)
			{
				// The reader is a constant.
				final A_Number value =
					(A_Number) stripNull(read.constantOrNull());
				final L2WriteFloatOperand unboxedWriter =
					newFloatRegisterWriter(
						InstanceTypeDescriptor.instanceType(value),
						value);
				addInstruction(
					L2_MOVE_FLOAT_CONSTANT.instance,
					new L2FloatImmediateOperand(value.extractDouble()),
					unboxedWriter);
				unboxed = unboxedWriter.read();
			}
			else if (restrictedType.isSubtypeOf(DOUBLE.o()))
			{
				// The reader is guaranteed to be unboxable. Create unboxed
				// variants for each relevant semantic value.
				final L2WriteFloatOperand unboxedWriter =
					newFloatRegisterWriter(restrictedType, null);
				addInstruction(
					L2_UNBOX_FLOAT.instance,
					read,
					unboxedWriter);
				unboxed = unboxedWriter.read();
			}
			else
			{
				// The reader may be unboxable. Copy the manifest for the
				// success case, adding unboxed variants for the unboxed
				// reader. Do not add these bindings to the failure case.
				final L2WriteFloatOperand unboxedWriter =
					newFloatRegisterWriter(restrictedType, null);
				addInstruction(
					L2_JUMP_IF_UNBOX_FLOAT.instance,
					read,
					unboxedWriter,
					new L2PcOperand(onSuccess, currentManifest),
					new L2PcOperand(onFailure, currentManifest));
				unboxed = unboxedWriter.read();
				startBlock(onSuccess);
			}
			// Now create unboxed variants for each relevant semantic value.
			for (final L2SemanticValue semanticValue :
				new ArrayList<>(currentManifest.registerToSemanticValues(
					read.register())))
			{
				currentManifest.addBinding(
					semanticValue.unboxedAsFloat(), unboxed);
			}
		}
		return stripNull(unboxed);
	}

	/**
	 * Write a boxed {@code int} value into a new {@link L2ObjectRegister}, if
	 * necessary, but prefer to answer an existing register that already has an
	 * appropriate value.
	 *
	 * @param read
	 *        The boxed {@link L2ReadIntOperand}.
	 * @param restrictedType
	 *        The restricted {@linkplain A_Type type} for the reader.
	 * @return The boxed {@link L2ReadPointerOperand}.
	 */
	public L2ReadPointerOperand box (
		final L2ReadIntOperand read,
		final A_Type restrictedType)
	{
		@Nullable L2ReadPointerOperand boxed =
			currentManifest.alreadyBoxed(read);
		if (boxed == null)
		{
			if (read.constantOrNull() != null)
			{
				// The reader is a constant.
				boxed = constantRegister(stripNull(read.constantOrNull()));
			}
			else
			{
				// The read must be boxed.
				final L2WritePointerOperand boxedWriter =
					newObjectRegisterWriter(restrictedType, null);
				addInstruction(
					L2_BOX_INT.instance,
					read,
					boxedWriter);
				boxed = boxedWriter.read();
			}
			// Now create boxed variants for each relevant semantic value.
			for (final L2SemanticValue semanticValue :
				new ArrayList<>(currentManifest.registerToSemanticValues(
					read.register())))
			{
				currentManifest.addBinding(semanticValue, read);
				currentManifest.addBinding(semanticValue.boxed(), boxed);
			}
		}
		return boxed;
	}

	/**
	 * Write a boxed {@code double} value into a new {@link L2ObjectRegister},
	 * if necessary, but prefer to answer an existing register that already has
	 * an appropriate value.
	 *
	 * @param read
	 *        The boxed {@link L2ReadFloatOperand}.
	 * @param restrictedType
	 *        The restricted {@linkplain A_Type type} for the reader.
	 * @return The boxed {@link L2ReadPointerOperand}.
	 */
	public L2ReadPointerOperand box (
		final L2ReadFloatOperand read,
		final A_Type restrictedType)
	{
		@Nullable L2ReadPointerOperand boxed =
			currentManifest.alreadyBoxed(read);
		if (boxed == null)
		{
			if (read.constantOrNull() != null)
			{
				// The reader is a constant.
				boxed = constantRegister(stripNull(read.constantOrNull()));
			}
			else
			{
				// The read must be boxed.
				final L2WritePointerOperand boxedWriter =
					newObjectRegisterWriter(restrictedType, null);
				addInstruction(
					L2_BOX_FLOAT.instance,
					read,
					boxedWriter);
				boxed = boxedWriter.read();
			}
			// Now create boxed variants for each relevant semantic value.
			for (final L2SemanticValue semanticValue :
				new ArrayList<>(currentManifest.registerToSemanticValues(
					read.register())))
			{
				currentManifest.addBinding(semanticValue.boxed(), boxed);
			}
		}
		return boxed;
	}

	/**
	 * Generate instruction(s) to move from one register to another.  Remove the
	 * associations between the source register and its {@link
	 * L2SemanticValue}s, and replace them with associations to the destination
	 * register.
	 *
	 * @param sourceRegister
	 *        Where to read the AvailObject.
	 * @param destinationRegister
	 *        Where to write the AvailObject.
	 */
	private void moveRegister (
		final L2ReadPointerOperand sourceRegister,
		final L2WritePointerOperand destinationRegister)
	{
		addInstruction(L2_MOVE.instance, sourceRegister, destinationRegister);
		final boolean sourceIsImmutable =
			currentManifest.isAlreadyImmutable(sourceRegister);
		currentManifest.replaceRegister(sourceRegister, destinationRegister);
		// The source of the move was immutable, so ensure the destination is
		// also recognized as being immutable.
		if (sourceIsImmutable)
		{
			currentManifest.semanticValuesKnownToBeImmutable.addAll(
				currentManifest.registerToSemanticValues(
					destinationRegister.register()));
		}
	}

	/**
	 * Generate code to ensure an immutable version of the given register is
	 * written to the returned register.  Update the {@link #currentManifest} to
	 * indicate the returned register should be used for all of given register's
	 * semantic values, as well as the immutable forms of each of the semantic
	 * values.
	 *
	 * @param sourceRegister
	 *        The register that was given.
	 * @return The resulting register, holding an immutable version of the given
	 *         register.
	 */
	private L2ReadPointerOperand makeImmutable (
		final L2ReadPointerOperand sourceRegister)
	{
		if (currentManifest.isAlreadyImmutable(sourceRegister))
		{
			return sourceRegister;
		}
		final L2WritePointerOperand destinationWrite =
			newObjectRegisterWriter(
				sourceRegister.type(), sourceRegister.constantOrNull());
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			sourceRegister,
			destinationWrite);
		final L2ReadPointerOperand destinationRead = destinationWrite.read();

		// Ensure attempts to look up semantic values that were bound to the
		// sourceRegister now produce the destination register.  Also ensure
		// attempts to look up the immutable-wrapped versions of the semantic
		// values that were bound to the sourceRegister will produce the
		// destination register.
		currentManifest.replaceRegister(sourceRegister, destinationWrite);
		final Set<L2SemanticValue> semanticValues =
			currentManifest.registerToSemanticValues(
				destinationRead.register());
		currentManifest.semanticValuesKnownToBeImmutable.addAll(semanticValues);
		return destinationRead;
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
			newObjectRegisterWriter(mostGeneralContinuationType(), null);
		final L2BasicBlock onReturnIntoReified = createBasicBlock(
			"return into reified continuation");
		final L2BasicBlock afterCreation = createBasicBlock(
			"after creation of reified continuation");
		// Create write-slots for when it returns into the reified continuation
		// and explodes the slots into registers.  Also create undefinedSlots
		// to indicate the registers hold nothing until after the explosion.
		final List<L2ReadPointerOperand> readSlotsBefore =
			new ArrayList<>(numSlots);
		final List<L2WritePointerOperand> writeSlotsAfter =
			new ArrayList<>(numSlots);
		for (int i = 1; i <= numSlots; i++)
		{
			final L2SemanticValue semanticValue = semanticSlot(i);
			if (i == stackp && expectedValueOrNull != null)
			{
				readSlotsBefore.add(constantRegister(expectedValueOrNull));
				writeSlotsAfter.add(null);
			}
			else
			{
				final L2ReadPointerOperand read = stripNull(
					currentManifest.semanticValueToRegister(semanticValue));
				readSlotsBefore.add(read);
				final TypeRestriction<?> originalRestriction =
					read.restriction();
				final L2WritePointerOperand slotWriter =
					newObjectRegisterWriter(
						originalRestriction.type,
						originalRestriction.constantOrNull);
				writeSlotsAfter.add(slotWriter);
			}
		}
		// Capture which semantic values are known to be immutable prior to the
		// reification, because they'll still be immutable (even if not held in
		// any register) after reaching the on-ramp.  But only preserve the
		// immutability for semantic values that indicate they're consistent
		// this way, rather than regenerating a new mutable value if needed.
		final Set<L2SemanticValue> knownImmutables = new HashSet<>();
		for (final L2SemanticValue semanticValue :
			currentManifest.semanticValuesKnownToBeImmutable)
		{
			if (semanticValue.immutabilityTranscendsReification())
			{
				knownImmutables.add(semanticValue);
			}
		}
		// Now generate the reification instructions, ensuring that when
		// returning into the resulting continuation it will enter a block where
		// the slot registers are the new ones we just created.
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			getCurrentFunction(),
			constantRegister(nil),
			new L2IntImmediateOperand(pc.value),
			new L2IntImmediateOperand(stackp),
			new L2ReadVectorOperand<>(readSlotsBefore),
			newContinuationRegister,
			new L2PcOperand(onReturnIntoReified, new L2ValueManifest()),
			new L2PcOperand(afterCreation, currentManifest),
			new L2CommentOperand("Create a reification continuation."));

		// Right after creating the continuation.
		startBlock(afterCreation);
		addInstruction(
			L2_RETURN_FROM_REIFICATION_HANDLER.instance,
			new L2ReadVectorOperand<>(
				singletonList(newContinuationRegister.read())));

		// Here it's returning into the reified continuation.
		startBlock(onReturnIntoReified);
		currentManifest.clear();
		currentManifest.semanticValuesKnownToBeImmutable.addAll(
			knownImmutables);
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(typeOfEntryPoint.offsetInDefaultChunk),
			new L2CommentOperand(
				"If invalid, reenter «default» at "
					+ typeOfEntryPoint.name() + "."));
		final L2ReadPointerOperand popped = popCurrentContinuation();
		for (int i = 1; i <= numSlots; i++)
		{
			final L2WritePointerOperand writeSlot =
				writeSlotsAfter.get(i - 1);
			if (writeSlot != null)
			{
				final TypeRestriction<?> restriction =
					writeSlot.register().restriction();
				// If the restriction is known to be a constant, don't take it
				// from the continuation.
				final @Nullable A_BasicObject constant =
					restriction.constantOrNull;
				if (constant != null)
				{
					// We know the slot contains a particular constant, so don't
					// read it from the continuation.
					currentManifest.addBinding(
						semanticSlot(i),
						constantRegister(constant));
				}
				else
				{
					addInstruction(
						L2_EXTRACT_CONTINUATION_SLOT.instance,
						popped,
						new L2IntImmediateOperand(i),
						writeSlot);
					currentManifest.addBinding(
						semanticSlot(i),
						writeSlot.read());
				}
			}
		}
	}

	/**
	 * Add an instruction that's not supposed to be reachable.
	 */
	@InnerAccess void addUnreachableCode ()
	{
		addInstruction(L2_JUMP.instance, unreachablePcOperand());
		startBlock(createBasicBlock("an unreachable block"));
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
		return new L2PcOperand(targetBlock, currentManifest, phiRestrictions);
	}

	/**
	 * Generate code to extract the current {@link
	 * AvailRuntime#resultDisagreedWithExpectedTypeFunction} into a new
	 * register, which is returned here.
	 *
	 * @return The new register that will hold the invalid return function.
	 */
	private L2ReadPointerOperand getInvalidResultFunctionRegister ()
	{
		final L2WritePointerOperand invalidResultFunction =
			newObjectRegisterWriter(
				functionType(
					tuple(
						mostGeneralFunctionType(),
						topMeta(),
						variableTypeFor(ANY.o())),
					bottom()),
				null);
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
			this.passCheckBasicBlock = createBasicBlock(
				"pass lookup test #" + shortTypeName);
			this.failCheckBasicBlock = createBasicBlock(
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
				addUnreachableCode();
			}
			else if (answerType.isSubtypeOf(expectedType))
			{
				// Capture it as the checked value L2SemanticSlot.
				forceSlotRegister(stackp, pc.value, answerReg);
				addInstruction(L2_JUMP.instance, edgeTo(afterCallNoCheck));
			}
			else
			{
				// Capture it as the unchecked return value SemanticSlot by
				// using pc - 1.
				forceSlotRegister(stackp, pc.value - 1, answerReg);
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
			this.onFallBackToSlowLookup = createBasicBlock(
				"fall back to slow lookup during " + quotedBundleName);
			this.onReificationWithCheck = createBasicBlock(
				"reify with check during " + quotedBundleName);
			this.onReificationNoCheck = createBasicBlock(
				"reify no check during " + quotedBundleName);
			this.afterCallNoCheck = createBasicBlock(
				isSuper
					? "after super no-check call of " + quotedBundleName
					: "after call no-check of " + quotedBundleName);
			this.afterCallWithCheck = createBasicBlock(
				isSuper
					? "after super call with check of " + quotedBundleName
					: "after call with check of " + quotedBundleName);
			this.afterEverything = createBasicBlock(
				isSuper
					? "after entire super call of " + quotedBundleName
					: "after entire call of " + quotedBundleName);
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
		translator.contingentValues =
			translator.contingentValues.setWithElementCanDestroy(method, true);
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
		final LookupTree<A_Definition, A_Tuple, Void> tree =
			method.testingTree();
		final List<A_Definition> applicableExpandedLeaves = new ArrayList<>();

		final LookupTreeTraverser<A_Definition, A_Tuple, Void, Boolean>
			definitionCollector =
				new LookupTreeTraverser<A_Definition, A_Tuple, Void, Boolean>(
					MethodDescriptor.runtimeDispatcher, null, false)
		{
			@Override
			public Boolean visitPreInternalNode (
				final int argumentIndex, final A_Type argumentType)
			{
				// Ignored.
				return Boolean.TRUE;
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
					A_Definition, A_Tuple, Void, InternalNodeMemento>
				traverser = new LookupTreeTraverser<
						A_Definition, A_Tuple, Void, InternalNodeMemento>(
					MethodDescriptor.runtimeDispatcher, null, false)
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
					assert !currentlyReachable();
					startBlock(memento.failCheckBasicBlock);
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
					assert !currentlyReachable();
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
					assert !currentlyReachable();
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
		assert !currentlyReachable();

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
		startBlock(callSiteHelper.onFallBackToSlowLookup);
		if (currentlyReachable())
		{
			generateSlowPolymorphicCall(
				callSiteHelper, arguments, unionOfPossibleResults);
		}

		// #2: Reification with return check.
		startBlock(callSiteHelper.onReificationWithCheck);
		if (currentlyReachable())
		{
			reify(expectedType, TO_RETURN_INTO);
			// Capture the value being returned into the on-ramp.
			forceSlotRegister(
				stackp,
				pc.value - 1,
				getLatestReturnValue(unionOfPossibleResults));
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterCallWithCheck));
		}

		// #3: Reification without return check.
		startBlock(callSiteHelper.onReificationNoCheck);
		if (currentlyReachable())
		{
			reify(expectedType, TO_RETURN_INTO);
			// Capture the value being returned into the on-ramp.
			forceSlotRegister(
				stackp,
				pc.value,
				getLatestReturnValue(unionOfPossibleResults));
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterCallNoCheck));
		}

		// #4: After call with return check.
		startBlock(callSiteHelper.afterCallWithCheck);
		if (currentlyReachable())
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
		startBlock(callSiteHelper.afterCallNoCheck);
		if (currentlyReachable())
		{
			// The value will have been put into a register bound to the
			// L2SemanticSlot for the stackp and pc just after the call.
			assert
				currentManifest.semanticValueToRegister(
						topFrame.slot(stackp, pc.value))
					!= null;
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterEverything));
		}

		// #6: After everything.  If it's possible to return a valid value from
		// the call, this will be reachable.
		startBlock(callSiteHelper.afterEverything);
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
		if (!currentlyReachable())
		{
			return;
		}
		if (solutions.tupleSize() == 1)
		{
			final A_Definition solution = solutions.tupleAt(1);
			if (solution.isMethodDefinition())
			{
				generateGeneralFunctionInvocation(
					constantRegister(solution.bodyBlock()),
					arguments,
					expectedType,
					true,
					callSiteHelper);
				assert !currentlyReachable();
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
		startBlock(memento.passCheckBasicBlock);
		if (!currentlyReachable())
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
		if (!currentlyReachable())
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
							: createBasicBlock(
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
						startBlock(nextCheckOrFail);
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
			newObjectRegisterWriter(argMeta, null);
		addInstruction(L2_GET_TYPE.instance, arg, argTypeWrite);
		final L2ReadPointerOperand superUnionReg =
			constantRegister(superUnionElementType);
		final L2WritePointerOperand unionReg =
			newObjectRegisterWriter(
				argMeta.typeUnion(superUnionReg.type()), null);
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
			if (boolSource.operation == L2_RUN_INFALLIBLE_PRIMITIVE.instance)
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
						if (typeSource.operation == L2_GET_TYPE.instance)
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
	 * <p>The code generation position is never {@link #currentlyReachable()}
	 * after this (Java) generate method runs.</p>
	 *
	 * <p>The final output from the entire polymorphic call will always be fully
	 * strengthened to the intersection of the VM-guaranteed type and the
	 * expectedType</p>.
	 *
	 * @param functionToCallReg
	 *        The {@link L2ReadPointerOperand} containing the function to
	 *        invoke.
	 * @param arguments
	 *        The {@link List} of {@link L2ReadPointerOperand}s that supply
	 *        arguments to the function.
	 * @param givenGuaranteedResultType
 *            The type guaranteed by the VM to be returned by the call.
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
		// indicating the size is not known.  It may also be a singular
		// integer range (e.g., [3..3]), indicating exactly how many
		// arguments must be supplied.  If it's a variable size, then by
		// the argument contravariance rules, it would require each (not
		// just any) of those sizes on every call, which is a contradiction,
		// although it's allowed as a denormalized uninstantiable type.  For
		// now just treat a spread of sizes like bottom (i.e., the count is
		// not known).
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
							newObjectRegisterWriter(
								primitive.returnTypeGuaranteedByVM(
									rawFunction, argumentTypes),
								null);
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
					assert !currentlyReachable();
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
			createBasicBlock("successful invocation");
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
		startBlock(successBlock);
		if (skipCheck)
		{
			addInstruction(
				L2_GET_LATEST_RETURN_VALUE.instance,
				writeSlot(stackp, pc.value, guaranteedResultType, null));
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterCallNoCheck));
		}
		else
		{
			addInstruction(
				L2_GET_LATEST_RETURN_VALUE.instance,
				writeSlot(stackp, pc.value - 1, guaranteedResultType, null));
			addInstruction(
				L2_JUMP.instance, edgeTo(callSiteHelper.afterCallWithCheck));
		}
		assert !currentlyReachable();
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
		final L2ReadPointerOperand uncheckedValueReg =
			stripNull(
				currentManifest.semanticValueToRegister(
					topFrame.slot(stackp, pc.value - 1)));
		if (uncheckedValueReg.type().isBottom())
		{
			// Bottom has no instances, so we can't get here.  It would be wrong
			// to do this based on the expectedType being bottom, since that's
			// only an erroneous semantic restriction, not a VM problem.
			// NOTE that this test terminates a mutual recursion between this
			// method and generateGeneralFunctionInvocation().
			addUnreachableCode();
			return;
		}

		// Check the return value against the expectedType.
		final L2BasicBlock passedCheck =
			createBasicBlock("passed return check");
		final L2BasicBlock failedCheck =
			createBasicBlock("failed return check");
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
					currentManifest,
					uncheckedValueReg.restrictedToType(expectedType)),
				new L2PcOperand(
					failedCheck,
					currentManifest,
					uncheckedValueReg.restrictedWithoutType(expectedType)));
		}

		// The type check failed, so report it.
		startBlock(failedCheck);
		final L2WritePointerOperand variableToHoldValueWrite =
			newObjectRegisterWriter(variableTypeFor(ANY.o()), null);
		addInstruction(
			L2_CREATE_VARIABLE.instance,
			new L2ConstantOperand(variableTypeFor(ANY.o())),
			variableToHoldValueWrite);
		final L2BasicBlock wroteVariable =
			createBasicBlock("wrote offending value into variable");
		addInstruction(
			L2_SET_VARIABLE_NO_CHECK.instance,
			variableToHoldValueWrite.read(),
			uncheckedValueReg,
			edgeTo(wroteVariable),
			edgeTo(wroteVariable));

		// Whether the set succeeded or failed doesn't really matter, although
		// it should always succeed for this freshly created variable.
		startBlock(wroteVariable);
		// Recurse to generate the call to the failure handler.  Since it's
		// bottom-valued, and can therefore skip the result check, the recursive
		// call won't exceed two levels deep.
		final L2BasicBlock onReificationInHandler = new L2BasicBlock(
			"reification in failed return check handler");
		addInstruction(
			L2_INVOKE.instance,
			getInvalidResultFunctionRegister(),
			new L2ReadVectorOperand<>(
				asList(
					getReturningFunctionRegister(),
					constantRegister(expectedType),
					variableToHoldValueWrite.read())),
			unreachablePcOperand(),
			edgeTo(onReificationInHandler));

		// Reification has been requested while the call is in progress.
		startBlock(onReificationInHandler);
		reify(bottom(), TO_RETURN_INTO);
		addUnreachableCode();

		// Generate the much more likely passed-check flow.
		startBlock(passedCheck);
		if (currentlyReachable())
		{
			forceSlotRegister(
				stackp,
				new L2ReadPointerOperand(
					uncheckedValueReg.register(),
					uncheckedValueReg.restriction().intersection(
						restriction(expectedType, null))));
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
				if (regRead.constantOrNull() == null)
				{
					break;
				}
				constants.add((AvailObject) regRead.constantOrNull());
			}
			if (constants.size() == argumentCount)
			{
				// Fold the primitive.  A foldable primitive must not
				// require access to the enclosing function or its code.
				final Interpreter interpreter = translator.interpreter;
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
						constantRegister(
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
						restriction(narrowedType, null))));
		}
		final boolean generated =
			primitive.tryToGenerateSpecialPrimitiveInvocation(
				functionToCallReg,
				rawFunction,
				narrowedArguments,
				narrowedArgTypes,
				this,
				callSiteHelper);
		if (generated && currentlyReachable())
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
		return functionDefinition.operation.getConstantCodeFrom(
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
		if (tupleDefinitionInstruction.operation == L2_CREATE_TUPLE.instance)
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
				newObjectRegisterWriter(elementType, null);
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
		final L2BasicBlock lookupSucceeded = createBasicBlock(
			"lookup succeeded for " + callSiteHelper.quotedBundleName);
		final L2BasicBlock lookupFailed = createBasicBlock(
			"lookup failed for " + callSiteHelper.quotedBundleName);
		final L2BasicBlock onReificationDuringFailure = createBasicBlock(
			"reify in method lookup failure handler for "
				+ callSiteHelper.quotedBundleName);
		final L2WritePointerOperand errorCodeReg =
			newObjectRegisterWriter(TOP.o(), null);

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
			newObjectRegisterWriter(functionTypeUnion, null);

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
					argTypeReg = constantRegister(superUnionElementType);
				}
				else
				{
					final A_Type typeBound =
						argStaticType.typeUnion(superUnionElementType);
					final L2WritePointerOperand argTypeWrite =
						newObjectRegisterWriter(instanceMeta(typeBound), null);
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
							newObjectRegisterWriter(
								instanceMeta(typeBound), null);
						addInstruction(
							L2_GET_TYPE.instance, argReg, originalArgTypeWrite);
						addInstruction(
							L2_TYPE_UNION.instance,
							originalArgTypeWrite.read(),
							constantRegister(superUnionElementType),
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
		startBlock(lookupFailed);
		final L2WritePointerOperand invalidSendReg =
			newObjectRegisterWriter(invalidMessageSendFunctionType, null);
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
			newObjectRegisterWriter(
				tupleTypeForSizesTypesDefaultType(
					singleInt(nArgs),
					tupleFromList(argTypes),
					bottom()),
				null);
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
					constantRegister(method),
					argumentsTupleWrite.read())),
			unreachablePcOperand(),
			edgeTo(onReificationDuringFailure));

		// Reification has been requested while the failure call is in progress.
		startBlock(onReificationDuringFailure);
		reify(bottom(), TO_RETURN_INTO);
		addUnreachableCode();

		// Now invoke the method definition's body.  We've already examined all
		// possible method definition bodies to see if they all conform with the
		// expectedType, and captured that in alwaysSkipResultCheck.
		startBlock(lookupSucceeded);
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
			createBasicBlock("service interrupt");
		final L2BasicBlock merge =
			createBasicBlock("merge after possible interrupt");

		addInstruction(
			L2_JUMP_IF_INTERRUPT.instance,
			edgeTo(serviceInterrupt),
			edgeTo(merge));

		startBlock(serviceInterrupt);
		// Service the interrupt:  Generate the reification instructions,
		// ensuring that when returning into the resulting continuation, it will
		// enter a block where the slot registers are the new ones we just
		// created.  After creating the continuation, actually service the
		// interrupt.

		// Reify everybody else, starting at the caller.
		final L2BasicBlock onReification =
			createBasicBlock("on reification");
		addInstruction(
			L2_REIFY.instance,
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(
				StatisticCategory.INTERRUPT_OFF_RAMP_IN_L2.ordinal()),
			new L2PcOperand(onReification, currentManifest));
		startBlock(onReification);

		// When the lambda below runs, it's generating code at the point where
		// continuationReg will have the new continuation.
		reify(null, TO_RESUME);
		addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(merge, currentManifest));
		// Merge the flow (reified and continued, versus not reified).
		startBlock(merge);
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
			createBasicBlock("successfully read variable");
		final L2BasicBlock failure =
			createBasicBlock("failed to read variable");
		final L2BasicBlock onReificationDuringFailure =
			createBasicBlock("reify in read variable failure handler");

		// Emit the specified get-variable instruction variant.
		final L2WritePointerOperand valueWrite =
			newObjectRegisterWriter(variable.type().readType(), null);
		addInstruction(
			getOperation,
			variable,
			valueWrite,
			edgeTo(success),
			edgeTo(failure));

		// Emit the failure path. Unbind the destination of the variable get in
		// this case, since it won't have been populated (by definition,
		// otherwise we wouldn't have failed).
		startBlock(failure);
		final L2WritePointerOperand unassignedReadFunction =
			newObjectRegisterWriter(unassignedVariableReadFunctionType, null);
		addInstruction(
			L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION.instance,
			unassignedReadFunction);
		addInstruction(
			L2_INVOKE.instance,
			unassignedReadFunction.read(),
			new L2ReadVectorOperand<>(emptyList()),
			unreachablePcOperand(),
			edgeTo(onReificationDuringFailure));

		// Reification has been requested while the failure call is in progress.
		startBlock(onReificationDuringFailure);
		reify(bottom(), TO_RETURN_INTO);
		addUnreachableCode();

		// End with the success path.
		startBlock(success);
		return makeImmutable
			? makeImmutable(valueWrite.read())
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
			createBasicBlock("set local success");
		final L2BasicBlock failure =
			createBasicBlock("set local failure");
		final L2BasicBlock onReificationDuringFailure =
			createBasicBlock("reify during set local failure");
		// Emit the set-variable instruction.
		addInstruction(
			setOperation,
			variable,
			newValue,
			edgeTo(success),
			edgeTo(failure));

		// Emit the failure path.
		startBlock(failure);
		final L2WritePointerOperand observeFunction =
			newObjectRegisterWriter(implicitObserveFunctionType, null);
		addInstruction(
			L2_GET_IMPLICIT_OBSERVE_FUNCTION.instance,
			observeFunction);
		final L2WritePointerOperand variableAndValueTupleReg =
			newObjectRegisterWriter(
				tupleTypeForTypes(variable.type(), newValue.type()),
				null);
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
					constantRegister(Interpreter.assignmentFunction()),
					variableAndValueTupleReg.read())),
			edgeTo(success),
			edgeTo(onReificationDuringFailure));

		startBlock(onReificationDuringFailure);
		reify(TOP.o(), TO_RETURN_INTO);
		addInstruction(
			L2_JUMP.instance,
			edgeTo(success));

		// End with the success block.  Note that the failure path can lead here
		// if the implicit-observe function returns.
		startBlock(success);
	}

	/**
	 * Create a new {@link L2BasicBlock}.  It's initially not connected to
	 * anything, and is ignored if it is never actually added with {@link
	 * #startBlock(L2BasicBlock)}.
	 *
	 * @param name The descriptive name of the new basic block.
	 * @return The new {@link L2BasicBlock}.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public L2BasicBlock createBasicBlock (final String name)
	{
		return new L2BasicBlock(name);
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
		initialBlock.makeIrremovable();
		startBlock(initialBlock);
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
		afterOptionalInitialPrimitiveBlock =
			createBasicBlock("after optional primitive");
		afterOptionalInitialPrimitiveBlock.makeIrremovable();
		addInstruction(
			L2_JUMP.instance,
			edgeTo(afterOptionalInitialPrimitiveBlock));

		startBlock(afterOptionalInitialPrimitiveBlock);
		currentManifest.clear();
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
		if (translator.optimizationLevel == OptimizationLevel.UNOPTIMIZED)
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
					i, pc.value, tupleType.typeAtIndex(i), null);
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
				writeSlot(numArgs + local, pc.value, localType, null));
		}

		// Capture the primitive failure value in the first local if applicable.
		if (primitive != null)
		{
			assert !primitive.hasFlag(CannotFail);
			// Move the primitive failure value into the first local.  This
			// doesn't need to support implicit observation, so no off-ramp
			// is generated.
			final L2BasicBlock success = createBasicBlock("success");
			addInstruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				readSlot(numArgs + 1),
				getLatestReturnValue(code.localTypeAt(1).writeType()),
				edgeTo(success),
				unreachablePcOperand());
			startBlock(success);
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
		final int interpreterIndex = translator.interpreter.interpreterIndex;
		preambleGenerationStat.record(
			captureNanos() - timeAtStartOfTranslation, interpreterIndex);

		// Transliterate each level one nybblecode into L2Instructions.
		final int numNybbles = code.numNybbles();
		while (pc.value <= numNybbles)
		{
			final long before = captureNanos();
			final L1Operation operation = code.nextNybblecodeOperation(pc);
			operation.dispatch(this);
			levelOneGenerationStats[operation.ordinal()].record(
				captureNanos() - before, interpreterIndex);
		}

		// Generate the implicit return after the instruction sequence.
		final L2ReadPointerOperand readResult = readSlot(stackp);
		addInstruction(
			L2_RETURN.instance,
			readResult);
		currentManifest.addBinding(topFrame.result(), readResult);
		assert stackp == numSlots;
		stackp = Integer.MIN_VALUE;

		if (unreachableBlock != null && unreachableBlock.hasPredecessors())
		{
			// Generate the unreachable block.
			startBlock(unreachableBlock);
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
		final L2ControlFlowGraph controlFlowGraph = new L2ControlFlowGraph();
		initialBlock.makeIrremovable();
		loopBlock.makeIrremovable();
		reenterFromRestartBlock.makeIrremovable();
		reenterFromCallBlock.makeIrremovable();
		reenterFromInterruptBlock.makeIrremovable();
		unreachableBlock.makeIrremovable();

		// 0. First try to run it as a primitive.
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

	@Override
	public void L1_doCall ()
	{
		final A_Bundle bundle =
			code.literalAt(code.nextNybblecodeOperand(pc));
		final A_Type expectedType =
			code.literalAt(code.nextNybblecodeOperand(pc));
		generateCall(bundle, expectedType, bottom());
	}

	@Override
	public void L1_doPushLiteral ()
	{
		final AvailObject constant = code.literalAt(
			code.nextNybblecodeOperand(pc));
		stackp--;
		moveConstantToSlot(constant, stackp);
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		final int localIndex = code.nextNybblecodeOperand(pc);
		stackp--;
		final L2ReadPointerOperand sourceRegister = readSlot(localIndex);
		forceSlotRegister(stackp, sourceRegister);
		nilSlot(localIndex);
	}

	@Override
	public void L1_doPushLocal ()
	{
		final int localIndex = code.nextNybblecodeOperand(pc);
		stackp--;
		final L2ReadPointerOperand sourceRegister =
			makeImmutable(readSlot(localIndex));
		forceSlotRegister(stackp, sourceRegister);
		forceSlotRegister(localIndex, sourceRegister);
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		final int outerIndex = code.nextNybblecodeOperand(pc);
		final A_Type outerType = code.outerTypeAt(outerIndex);
		stackp--;
		// For now, simplify the logic related to L1's nilling of mutable outers
		// upon their final use.  Just make it immutable instead.
		forceSlotRegister(
			stackp,
			makeImmutable(getOuterRegister(outerIndex, outerType)));
	}

	@Override
	public void L1_doClose ()
	{
		final int count = code.nextNybblecodeOperand(pc);
		final A_RawFunction codeLiteral = code.literalAt(
			code.nextNybblecodeOperand(pc));
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
			writeSlot(stackp, pc.value, codeLiteral.functionType(), null));

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
		final int localIndex = code.nextNybblecodeOperand(pc);
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			readSlot(localIndex),
			readSlot(stackp));
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(stackp, constantRegister(nil));
		stackp++;
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		final int index = code.nextNybblecodeOperand(pc);
		stackp--;
		final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			readSlot(index),
			false);
		forceSlotRegister(stackp, pc.value, valueReg);
	}

	@Override
	public void L1_doPushOuter ()
	{
		final int outerIndex = code.nextNybblecodeOperand(pc);
		final A_Type outerType = code.outerTypeAt(outerIndex);
		stackp--;
		forceSlotRegister(
			stackp,
			makeImmutable(getOuterRegister(outerIndex, outerType)));
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
		final int outerIndex = code.nextNybblecodeOperand(pc);
		stackp--;
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			getOuterRegister(outerIndex, outerType),
			false);
		forceSlotRegister(stackp, pc.value, valueReg);
	}

	@Override
	public void L1_doSetOuter ()
	{
		final int outerIndex = code.nextNybblecodeOperand(pc);
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
		forceSlotRegister(stackp, constantRegister(nil));
		stackp++;
	}

	@Override
	public void L1_doGetLocal ()
	{
		final int index = code.nextNybblecodeOperand(pc);
		stackp--;
		final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			readSlot(index),
			true);
		forceSlotRegister(stackp, pc.value, valueReg);

	}

	@Override
	public void L1_doMakeTuple ()
	{
		final int count = code.nextNybblecodeOperand(pc);
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
				writeSlot(stackp, pc.value, tupleType, null));
		}
	}

	@Override
	public void L1_doGetOuter ()
	{
		final int outerIndex = code.nextNybblecodeOperand(pc);
		stackp--;
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadPointerOperand valueReg = emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			getOuterRegister(outerIndex, outerType),
			false);
		forceSlotRegister(stackp, pc.value, valueReg);
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
		final L2BasicBlock onReification = createBasicBlock("on reification");
		addInstruction(
			L2_REIFY.instance,
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(0),
			new L2IntImmediateOperand(
				StatisticCategory.PUSH_LABEL_IN_L2.ordinal()),
			new L2PcOperand(onReification, currentManifest));

		startBlock(onReification);
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
		final L2ReadPointerOperand nilTemp = constantRegister(nil);
		for (int i = numArgs + 1; i <= numSlots; i++)
		{
			slotsForLabel.add(nilTemp);
		}
		// Now create the actual label continuation and push it.
		stackp = oldStackp - 1;
		final A_Type continuationType =
			continuationTypeForFunctionType(code.functionType());
		final L2WritePointerOperand destinationRegister =
			writeSlot(stackp, pc.value, continuationType, null);
		final L2BasicBlock afterCreation =
			createBasicBlock("after creating label");
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			getCurrentFunction(),
			getCurrentContinuation(),  // the caller, since we popped already.
			new L2IntImmediateOperand(0),  // indicates a label.
			new L2IntImmediateOperand(numSlots + 1),  // empty stack
			new L2ReadVectorOperand<>(slotsForLabel),
			destinationRegister,
			new L2PcOperand(initialBlock, new L2ValueManifest()),
			edgeTo(afterCreation),
			new L2CommentOperand("Create a label continuation."));

		// Continue, with the label having been pushed.
		startBlock(afterCreation);
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
			code.nextNybblecodeOperand(pc));
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
				constantRegister(literalVariable),
				false);
			forceSlotRegister(stackp, pc.value, valueReg);
		}
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		final A_Variable literalVariable = code.literalAt(
			code.nextNybblecodeOperand(pc));
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			constantRegister(literalVariable),
			readSlot(stackp));
		// Now we have to nil the stack slot which held the value that we
		// assigned.  This same slot potentially captured the expectedType in a
		// continuation if we needed to reify during the failure path.
		forceSlotRegister(stackp, constantRegister(nil));
		stackp++;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final L2ReadPointerOperand source = readSlot(stackp);
		stackp--;
		forceSlotRegister(stackp, makeImmutable(source));
	}

	@Override
	public void L1Ext_doPermute ()
	{
		// Move into the permuted temps, then back to the stack.  This puts the
		// responsibility for optimizing away extra moves (by coloring the
		// registers) on the optimizer.
		final A_Tuple permutation = code.literalAt(
			code.nextNybblecodeOperand(pc));
		final int size = permutation.tupleSize();
		final L2WritePointerOperand[] temps = new L2WritePointerOperand[size];
		for (int i = size; i >= 1; i--)
		{
			final L2ReadPointerOperand source = readSlot(stackp + size - i);
			final L2WritePointerOperand temp = newObjectRegisterWriter(
				source.type(), source.constantOrNull());
			moveRegister(source, temp);
			temps[permutation.tupleIntAt(i) - 1] = temp;
		}
		for (int i = size; i >= 1; i--)
		{
			final L2ReadPointerOperand temp = temps[i - 1].read();
			moveRegister(
				temp,
				writeSlot(
					stackp + size - i,
					pc.value,
					temp.type(),
					temp.constantOrNull()));
		}
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		final A_Bundle bundle =
			code.literalAt(code.nextNybblecodeOperand(pc));
		final AvailObject expectedType =
			code.literalAt(code.nextNybblecodeOperand(pc));
		final AvailObject superUnionType =
			code.literalAt(code.nextNybblecodeOperand(pc));
		generateCall(bundle, expectedType, superUnionType);
	}

	@Override
	public void L1Ext_doSetSlot ()
	{
		final int destinationIndex = code.nextNybblecodeOperand(pc);
		final L2ReadPointerOperand source = readSlot(stackp);
		moveRegister(
			source,
			writeSlot(
				destinationIndex,
				pc.value,
				source.type(),
				source.constantOrNull()));
		nilSlot(stackp);
		stackp++;
	}
}
