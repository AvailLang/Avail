/**
 * L1Translator.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.dispatch.InternalLookupTree;
import com.avail.dispatch.LookupTree;
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
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.general.P_Equality;
import com.avail.interpreter.primitive.types.P_IsSubtypeOf;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticConstant;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.Continuation1;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.IntStream;

import static com.avail.AvailRuntime.invalidMessageSendFunctionType;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.instanceTypeOrMetaOn;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.continuationTypeForFunctionType;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor
	.mostGeneralFunctionType;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.TupleDescriptor.*;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CannotFail;
import static com.avail.interpreter.Primitive.Result.FAILURE;
import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RESUME;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
	.TO_RETURN_INTO;
import static com.avail.optimizer.L2Translator.*;
import static com.avail.utility.Nulls.stripNull;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

/**
 * The {@code L1Translator} transliterates a sequence of {@link L1Operation
 * level one instructions} into one or more simple {@link L2Instruction level
 * two instructions}, under the assumption that further optimization steps will
 * be able to transform this code into something much more efficient – without
 * altering the level one semantics.
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
	final @Nullable A_RawFunction code;

	/**
	 * The nybblecodes being optimized.
	 */
	private final A_Tuple nybbles;

	/**
	 * The number of slots in the virtualize continuation.  This includes the
	 * arguments, the locals (including the optional primitive failure result),
	 * and the stack slots.
	 */
	final int numSlots;

	/**
	 * The current level one nybblecode program counter during naive translation
	 * to level two.
	 */
	private int pc;

	/**
	 * The current stack depth during naive translation to level two.
	 */
	private int stackp;

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
	 * L2_TRY_PRIMITIVE} instruction.  If we have just optimized such code, we
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
	 * Answer the current {@link A_RawFunction}, which must not be null here.
	 */
	private A_RawFunction code ()
	{
		return stripNull(code);
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
		return new L2PcOperand(
			unreachableBlock, new L2ReadPointerOperand[0], currentManifest);
	}

	/** The {@link L2BasicBlock} that code is currently being generated into. */
	private @Nullable L2BasicBlock currentBlock = initialBlock;

	/**
	 * During naive L1 → L2 translation, the stack-oriented L1 nybblecodes are
	 * translated into register-oriented L2 instructions, treating arguments,
	 * locals, and operand stack slots as registers.  We build a control flow
	 * graph of basic blocks, each of which contains a sequence of instructions.
	 * The graph is built in Static Single Assignment (SSA) form, where a
	 * register may only be assigned by a single instruction.  Merged control
	 * flows use "phi-functions", for which there is ample literature.  Our
	 * slight variation takes into account our complex operations that perform
	 * branches, allowing each branch direction to specify its own phi-mapping.
	 *
	 * <p>This array is indexed by slot index within an L1 continuation.  Each
	 * slot holds an {@link L2ObjectRegister} corresponding to the most recently
	 * written value.  As the L1 instructions are translated, any reads from the
	 * effective continuation are dealt with by generating a read from the
	 * register at the appropriate index (at the time of translation), and a
	 * write always involves creating a new {@link L2ObjectRegister}, replacing
	 * the previous register in the array.  This ensures SSA form, as long as
	 * the naive translation's control flow is loop-less, which it is.</p>
	 */
	private final L2ReadPointerOperand[] slotRegisters;

	/**
	 * Use this {@link L2ValueManifest} to track which {@link L2Register} holds
	 * which {@link L2SemanticValue} at the current code generation point.
	 */
	L2ValueManifest currentManifest = new L2ValueManifest();

	/**
	 * A {@link Frame} that represents the invocation of the raw function that
	 * we're translating.  Inlined functions will have a different Frame created
	 * for them at their call site.
	 */
	private final Frame topFrame = new Frame(null);

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
	 * Answer a copy of the current array of registers that represent the
	 * current state of the virtual continuation.
	 *
	 * @return An array of {@link L2ReadPointerOperand}s.
	 */
	public L2ReadPointerOperand[] slotRegisters ()
	{
		return slotRegisters.clone();
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
		if (translator.codeOrNull != null)
		{
			this.code = translator.codeOrNull;
			this.nybbles = code.nybbles();
			this.numSlots = code.numSlots();
			this.pc = 1;
			this.stackp = numSlots + 1;
			this.slotRegisters = new L2ReadPointerOperand[numSlots];
			this.exactFunctionOrNull = computeExactFunctionOrNullForCode(code);
		}
		else
		{
			this.code = null;
			this.nybbles = emptyTuple();
			this.numSlots = 0;
			this.pc = -1;
			this.stackp = -1;
			this.slotRegisters = new L2ReadPointerOperand[0];
			this.exactFunctionOrNull = null;
		}
	}

	/**
	 * Start code generation for the given {@link L2BasicBlock}.  This naive
	 * translator doesn't create loops, so ensure all predecessors blocks have
	 * already finished generation.
	 *
	 * <p>Also, reconcile the slotRegisters that were collected for each
	 * predecessor, creating an {@link L2_PHI_PSEUDO_OPERATION} if needed.</p>
	 *
	 * @param block The {@link L2BasicBlock} beginning code generation.
	 */
	public void startBlock (final L2BasicBlock block)
	{
		if (block.isIrremovable() || block.hasPredecessors())
		{
			currentBlock = block;
			controlFlowGraph.startBlock(block);
			block.startIn(this);
		}
		else
		{
			currentBlock = null;
		}
	}

	/**
	 * Determine whether the current block is probably reachable.  If it has no
	 * predecessors and is removable, it's unreachable, but otherwise we assume
	 * it's reachable, at least until dead code elimination.
	 *
	 * @return Whether the current block is probably reachable.
	 */
	private boolean currentlyReachable ()
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
	 * Answer an integer extracted at the current program counter.  The
	 * program counter will be adjusted to skip over the integer.
	 *
	 * @return The integer encoded at the current nybblecode position.
	 */
	private int getInteger ()
	{
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc++);
		final int shift = firstNybble << 2;
		int count = 0xF & (int) (0x8421_1100_0000_0000L >>> shift);
		int value = 0;
		while (count-- > 0)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc++);
		}
		final int lowOff = 0xF & (int) (0x00AA_AA98_7654_3210L >>> shift);
		final int highOff = 0xF & (int) (0x0032_1000_0000_0000L >>> shift);
		return value + lowOff + (highOff << 4);
	}

	/**
	 * Answer the register holding the latest assigned version of the specified
	 * continuation slot. The slots are the arguments, then the locals, then the
	 * stack entries. The slots are numbered starting at 1.
	 *
	 * @param slotNumber
	 *        The index into the continuation's slots.
	 * @return A register representing that continuation slot.
	 */
	private L2ReadPointerOperand readSlot (final int slotNumber)
	{
		return slotRegisters[slotNumber - 1];
	}

	/**
	 * Answer the register holding the latest assigned version of the specified
	 * continuation slot. The slots are the arguments, then the locals, then the
	 * stack entries. The slots are numbered starting at 1.
	 *
	 * @param slotNumber
	 *        The index into the continuation's slots.
	 * @return A register representing that continuation slot.
	 */
	L2WritePointerOperand writeSlot (
		final int slotNumber,
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		final L2WritePointerOperand writer = newObjectRegisterWriter(
			type, constantOrNull);
		slotRegisters[slotNumber - 1] = new L2ReadPointerOperand(
			writer.register(), null);
		return writer;
	}

	/**
	 * Write nil into a new register representing the specified continuation
	 * slot.  The slots are the arguments, then the locals, then the stack
	 * entries.  The slots are numbered starting at 1.
	 *
	 * @param slotNumber
	 *        The index into the continuation's slots.
	 */
	private void nilSlot (final int slotNumber)
	{
		slotRegisters[slotNumber - 1] = constantRegister(nil);
	}

	/**
	 * Allocate a new {@link L2ObjectRegister}.  Answer an {@link
	 * L2WritePointerOperand} that writes to it, using the given type and
	 * optional constant value information.
	 *
	 * @return The new register write operand.
	 */
	public L2WritePointerOperand newObjectRegisterWriter (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		return new L2WritePointerOperand(
			controlFlowGraph.nextUnique(), type, constantOrNull);
	}

	/**
	 * Allocate a fresh {@linkplain L2IntegerRegister integer register} that
	 * nobody else has used yet.
	 *
	 * @return The new register.
	 */
	private L2IntegerRegister newIntegerRegister ()
	{
		return new L2IntegerRegister(
			controlFlowGraph.nextUnique());
	}

	/**
	 * Write instructions to extract the current function, and answer an {@link
	 * L2ReadPointerOperand} for the register that will hold the function
	 * afterward.
	 */
	private L2ReadPointerOperand getCurrentFunction ()
	{
		if (exactFunctionOrNull != null)
		{
			// The exact function is known.
			return constantRegister(exactFunctionOrNull);
		}
		// The exact function isn't known, but we know the raw function, so
		// we statically know the function type.
		final L2WritePointerOperand functionWrite =
			newObjectRegisterWriter(code().functionType(), null);
		addInstruction(
			L2_GET_CURRENT_FUNCTION.instance,
			functionWrite);
		return functionWrite.read();
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
	 * Write instructions to extract the current skip-return-check flag into a
	 * new {@link L2IntegerRegister}, and answer an {@link L2ReadIntOperand} for
	 * that register.
	 */
	private L2ReadIntOperand getSkipReturnCheck ()
	{
		final L2IntegerRegister tempIntReg = newIntegerRegister();
		addInstruction(
			L2_GET_SKIP_RETURN_CHECK.instance,
			new L2WriteIntOperand(tempIntReg));
		return new L2ReadIntOperand(tempIntReg);
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
	void moveConstantToSlot (
		final A_BasicObject value,
		final int slotIndex)
	{
		slotRegisters[slotIndex - 1] = constantRegister(value);
	}

	/**
	 * Write a constant value into a new register.  Answer an {@link
	 * L2ReadPointerOperand} for that register.  If another register has already
	 * been assigned the same value within the same {@link L2BasicBlock}, just
	 * use that instead of emitting another constant-move.
	 *
	 * @param value The constant value to write to a register.
	 * @return The {@link L2ReadPointerOperand} for the new register.
	 */
	public L2ReadPointerOperand constantRegister (final A_BasicObject value)
	{
		final L2SemanticConstant constant = new L2SemanticConstant(value);
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
	 * L2ReadIntOperand} for that register.
	 *
	 * @param value The immediate int to write to a new int register.
	 * @return The {@link L2ReadIntOperand} for the new register.
	 */
	L2ReadIntOperand constantIntRegister (final int value)
	{
		final L2WriteIntOperand registerWrite =
			new L2WriteIntOperand(newIntegerRegister());
		addInstruction(
			L2_MOVE_INT_CONSTANT.instance,
			new L2ImmediateOperand(value),
			registerWrite);
		return registerWrite.read();
	}

	/**
	 * Generate instruction(s) to move from one register to another.
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
		final @Nullable L2SemanticValue sourceSemanticValue =
			currentManifest.registerToSemanticValue(sourceRegister.register());
		if (sourceSemanticValue == null)
		{
			currentManifest.removeBinding(destinationRegister.register());
		}
		else
		{
			currentManifest.addBinding(
				sourceSemanticValue, destinationRegister.read());
		}
	}

	/**
	 * Generate code to reify a continuation in a fresh register, then use
	 * that register in a supplied code-generating action.  Then generate code
	 * to get into an equivalent state after resuming the reified continuation,
	 * jumping to the given resumeBlock.  Code generation then continues at the
	 * resumeBlock, possibly after postamble instructions have been generated in
	 * it.
	 *
	 * <p>Use the passed registers to populate the continuation's current slots
	 * and function, but use the state of this translator to determine the rest
	 * of the continuation (e.g., pc, stackp).</p>
	 *
	 * @param slots
	 *        A {@linkplain List list} containing the {@linkplain
	 *        L2ReadPointerOperand object registers} that correspond to the
	 *        slots of the current continuation.
	 * @param function
	 *        The register holding the function that should be captured in the
	 *        continuation.
	 * @param caller
	 *        The register holding the caller that should be captured in the
	 *        continuation.  This may safely be {@link NilDescriptor#nil}.
	 * @param actionForContinuation
	 *        What code generation action to perform when the continuation has
	 *        been assembled.  The register holding the new continuation is
	 *        passed to the action.
	 * @param resumeBlock
	 *        Where the continuation should resume executing when it's asked to
	 *        continue.  Note that if the {@link L2Chunk} was invalidated before
	 *        resumption, the default chunk will be resumed instead.
	 */
	public void reify (
		final L2ReadPointerOperand[] slots,
		final L2ReadPointerOperand function,
		final L2ReadPointerOperand caller,
		final Continuation1<L2ReadPointerOperand> actionForContinuation,
		final L2BasicBlock resumeBlock,
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
		// to indicate the registers hold nothing until after the explode.
		final List<L2WritePointerOperand> writeSlotsOnReturnIntoReified =
			new ArrayList<>(slots.length);
		final L2ReadPointerOperand[] readSlotsOnReturnIntoReified =
			new L2ReadPointerOperand[slots.length];
		for (int i = 0; i < slots.length; i++)
		{
			final TypeRestriction originalRestriction = slots[i].restriction();
			final L2WritePointerOperand slotWriter = newObjectRegisterWriter(
				originalRestriction.type, originalRestriction.constantOrNull);
			writeSlotsOnReturnIntoReified.add(slotWriter);
			readSlotsOnReturnIntoReified[i] = slotWriter.read();
		}
		// Now generate the reification instructions, ensuring that when
		// returning into the resulting continuation it will enter a block where
		// the slot registers are the new ones we just created.
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			caller,
			function,
			new L2ImmediateOperand(pc),
			new L2ImmediateOperand(stackp),
			getSkipReturnCheck(),
			new L2ReadVectorOperand(asList(slots)),
			newContinuationRegister,
			new L2PcOperand(
				onReturnIntoReified,
				readSlotsOnReturnIntoReified,
				currentManifest),
			new L2PcOperand(afterCreation, slots, currentManifest));

		startBlock(afterCreation);
		// Right after creating the continuation.
		actionForContinuation.value(newContinuationRegister.read());

		// It's returning into the reified continuation.
		startBlock(onReturnIntoReified);
		currentManifest.clear();
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2ImmediateOperand(typeOfEntryPoint.offsetInDefaultChunk));
		addInstruction(
			L2_EXPLODE_CONTINUATION.instance,
			popCurrentContinuation(),
			new L2WriteVectorOperand(writeSlotsOnReturnIntoReified),
			new L2WriteIntOperand(newIntegerRegister())); //TODO MvG - Fix this when we have int phis
		addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(
				resumeBlock, readSlotsOnReturnIntoReified, currentManifest));
		// Merge the flow (reified and continued, versus not reified).
		startBlock(resumeBlock);
		for (int i = 0; i < slots.length; i++)
		{
			final L2ReadPointerOperand slotReader =
				readSlotsOnReturnIntoReified[i];
			final @Nullable A_BasicObject constant =
				slotReader.constantOrNull();
			if (constant != null)
			{
				// This slot is constant-valued.  Even though we've already
				// populated it via an explode instruction in the reification
				// path, write a move-constant into the slot register to
				// (1) potentially make the explode instruction dead, and
				// (2) allow the move-constant to drift forward safely in the
				// instruction stream.
				moveConstantToSlot(constant, i + 1);
			}
		}
	}

	/**
	 * Add an instruction that's not supposed to be reachable.
	 */
	private void addUnreachableCode ()
	{
		addInstruction(L2_JUMP.instance, unreachablePcOperand());
		startBlock(createBasicBlock("an unreachable block"));
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
	void forceSlotRegister (
		final int slotIndex,
		final @Nullable L2ReadPointerOperand register)
	{
		slotRegisters[slotIndex - 1] = register;
	}

	/**
	 * Create an {@link L2PcOperand} with suitable defaults.
	 *
	 * @param targetBlock The target {@link L2BasicBlock}.
	 * @return The new {@link L2PcOperand}.
	 */
	@InnerAccess
	public L2PcOperand edgeTo (
		final L2BasicBlock targetBlock)
	{
		return new L2PcOperand(targetBlock, slotRegisters(), currentManifest);
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
		 *        The register holding the argument prior to the type test.
		 *        The test produces new registers with narrowed types to hold
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
			final String shortTypeName =
				branchLabelCounter
					+ " (arg#"
					+ argumentIndexToTest
					+ " is a "
					+ typeToTest.traversed().descriptor().typeTag.name()
					+ ")";
			this.passCheckBasicBlock = createBasicBlock(
				"pass lookup test #" + shortTypeName);
			this.failCheckBasicBlock = createBasicBlock(
				"fail lookup test #" + shortTypeName);
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
		// Write the expected type.  Note that even though it's a type, the
		// exact value is known statically, not just its meta.
		moveConstantToSlot(expectedType, stackp);

		// At this point we've captured and popped the argument registers,
		// nilled their new SSA versions, and pushed the expectedType.  That's
		// the reifiable state of the continuation during the call.
		final List<A_Definition> allPossible = new ArrayList<>();
		final String quotedBundleName = bundle.message().atomName().toString();
		for (final A_Definition definition : method.definitionsTuple())
		{
			final A_Type signature = definition.bodySignature();
			if (signature.couldEverBeInvokedWith(argumentTypes)
				&& superUnionType.isSubtypeOf(signature.argsTupleType()))
			{
				allPossible.add(definition);
				if (allPossible.size() > maxPolymorphismToInlineDispatch)
				{
					// It has too many applicable implementations to be worth
					// inlining all of them.
					generateSlowPolymorphicCall(
						bundle,
						arguments,
						expectedType,
						superUnionType,
						quotedBundleName);
					return;
				}
			}
		}

		// NOTE: Don't use the method's testing tree.  It encodes information
		// about the known types of arguments that may be too weak for our
		// purposes.  It's still correct, but it may produce extra tests that
		// this site's argumentTypes would eliminate.
		final L2BasicBlock afterCall = createBasicBlock(
			superUnionType.isBottom()
				? "after call of " + quotedBundleName
				: "after super call of " + quotedBundleName);
		final LookupTree<A_Definition, A_Tuple, Void> tree =
			MethodDescriptor.runtimeDispatcher.createRoot(
				allPossible, argumentTypes, null);
		final Mutable<Integer> branchLabelCounter = new Mutable<>(1);
		// If a reification exception happens while an L2_INVOKE is in progress,
		// it will run the instructions at the off-ramp up to an L2_RETURN –
		// which will return the reified (but callerless) continuation.
		//TODO MvG - Take into account any arguments constrained to be constants
		//even though they're types.  At the moment, some calls still have to
		//check for ⊥'s type (not ⊥), just to report ambiguity, even though the
		//argument's type restriction says it can't actually be ⊥'s type.
		tree.traverseEntireTree(
			MethodDescriptor.runtimeDispatcher,
			null,
			// preInternalNode
			(argumentIndexToTest, typeToTest) ->
			{
				final InternalNodeMemento memento =
					new InternalNodeMemento(
						argumentIndexToTest,
						typeToTest,
						arguments.get(argumentIndexToTest - 1),
						branchLabelCounter.value++);
				// If no paths lead here, don't generate code.  This can happen
				// when we short-circuit type-tests into unconditional jumps,
				// due to the complexity of super calls.  We short-circuit code
				// generation within this entire subtree by performing the same
				// check in each callback.
				if (!currentlyReachable())
				{
					startBlock(memento.passCheckBasicBlock);
					return memento;
				}
				final L2ReadPointerOperand arg =
					arguments.get(argumentIndexToTest - 1);
				final A_Type existingType = arg.type();
				// Strengthen the test based on what's already known about the
				// argument.  Eventually we can decide whether to strengthen
				// based on the expected cost of the type check.
				final A_Type intersection =
					existingType.typeIntersection(typeToTest);
				assert !intersection.isBottom()
					: "Impossible condition should have been excluded";
				// Tricky here.  We have the type we want to test for, and we
				// have the argument for which we want to test the type, but we
				// also have an element of the superUnionType to consider.  And
				// that element might be a combination of restrictions and
				// bottoms.  Deal with the easy, common cases first.
				final A_Type superUnionElementType =
					superUnionType.typeAtIndex(argumentIndexToTest);
				final boolean superUnionElementTypeIsBottom =
					superUnionElementType.isBottom();
				final @Nullable A_BasicObject constantOrNull =
					arg.constantOrNull();
				if (constantOrNull != null && superUnionElementTypeIsBottom)
				{
					// The argument is a constant, and it isn't being
					// super-cast, so test it now.
					// Unconditionally jump to either the true or the false
					// path.  Don't bother restricting the resulting argument
					// type, since it's a known constant either way.
					addInstruction(
						L2_JUMP.instance,
						edgeTo(
							constantOrNull.isInstanceOf(intersection)
								? memento.passCheckBasicBlock
								: memento.failCheckBasicBlock));
				}
				else if (existingType.isSubtypeOf(superUnionElementType))
				{
					// It's a pure supercast of this argument, not a mix of some
					// parts being supercast and others not.  Do the test once,
					// right now, only looking at the super-union type.
					final boolean passed =
						superUnionElementType.isSubtypeOf(intersection);
					addInstruction(
						L2_JUMP.instance,
						edgeTo(
							passed
								? memento.passCheckBasicBlock
								: memento.failCheckBasicBlock));
				}
				else if (superUnionElementTypeIsBottom
					&& intersection.isEnumeration()
					&& !intersection.isInstanceMeta()
					&& intersection.instanceCount().extractInt() <=
						maxExpandedEqualityChecks)
				{
					// It doesn't contain a supercast, and the type is a small
					// non-meta enumeration.  Use equality checks rather than
					// the more general type checks.
					//
					// TODO MvG - Eventually we can do this in such a way that a
					// phi function gets built at the success point.  Later, if
					// a user of the phi-merged argument indicates it would
					// benefit from knowing statically which of the values it
					// was, it can trigger code splitting.
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
						final L2PcOperand passEdge = edgeTo(
							memento.passCheckBasicBlock);
						final L2PcOperand failEdge = edgeTo(nextCheckOrFail);
						generateJumpIfEqualsConstant(
							arg, instance, passEdge, failEdge);
						if (!last)
						{
							startBlock(nextCheckOrFail);
						}
					}
				}
				else if (superUnionElementTypeIsBottom)
				{
					// Use the argument's type unaltered.  In fact, just check
					// if the argument is an instance of the type.
					addInstruction(
						L2_JUMP_IF_KIND_OF_CONSTANT.instance,
						arg,
						new L2ConstantOperand(intersection),
						edgeTo(memento.passCheckBasicBlock),
						edgeTo(memento.failCheckBasicBlock));
				}
				else
				{
					// This argument dispatch type is a mixture of supercasts
					// and non-supercasts.  Do it the slow way with a type
					// union.  Technically, the superUnionElementType's
					// recursive tuple structure mimics the call site, so it
					// must have a fixed, finite structure corresponding with
					// occurrences of supercasts syntactically.  Thus, in theory
					// we could analyze the superUnionElementType and generate a
					// more complex collection of branches – but this is already
					// a pretty rare case.
					final A_Type argMeta = instanceMeta(arg.type());
					final L2WritePointerOperand argTypeWrite =
						newObjectRegisterWriter(argMeta, null);
					addInstruction(
						L2_GET_TYPE.instance, arg, argTypeWrite);
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
				}

				// Prepare to generate the pass block.  In particular, replace
				// the current argument with a pass-strengthened reader.  It'll
				// be replaced with a fail-strengthened reader during the
				// intraInternalNode, then replaced with whatever it was upon
				// entry to this subtree during the postInternalNode.
				final TypeRestriction tested =
					new TypeRestriction(intersection, null);
				final L2ReadPointerOperand passedTestArg =
					new L2ReadPointerOperand(
						arg.register(),
						arg.restriction().intersection(tested));
				arguments.set(argumentIndexToTest - 1, passedTestArg);
				startBlock(memento.passCheckBasicBlock);
				return memento;
			},
			// intraInternalNode
			memento ->
			{
				if (currentlyReachable())
				{
					addInstruction(
						L2_JUMP.instance,
						edgeTo(afterCall));
				}
				final L2ReadPointerOperand argBeforeTest =
					memento.argumentBeforeComparison;
				final TypeRestriction tested =
					new TypeRestriction(memento.typeToTest, null);
				final TypeRestriction failed =
					argBeforeTest.restriction().minus(tested);
				final L2ReadPointerOperand argUponFailure =
					new L2ReadPointerOperand(
						memento.argumentBeforeComparison.register(),
						failed);
				arguments.set(memento.argumentIndexToTest - 1, argUponFailure);
				startBlock(memento.failCheckBasicBlock);
			},
			// postInternalNode
			memento ->
			{
				// Restore the argument prior to encountering this internal
				// node.
				arguments.set(
					memento.argumentIndexToTest - 1,
					memento.argumentBeforeComparison);
				// The leaves already jump to afterCall (or unreachableBlock).
			},
			// forEachLeafNode
			solutions ->
			{
				if (!currentlyReachable())
				{
					return;
				}
				if (solutions.tupleSize() == 1)
				{
					final A_Definition solution = solutions.tupleAt(1);
					if (solution.isInstanceOf(METHOD_DEFINITION.o()))
					{
						final L2ReadPointerOperand resultReg =
							generateGeneralFunctionInvocation(
								constantRegister(solution.bodyBlock()),
								arguments,
								expectedType,
								solution.bodySignature().returnType()
									.isSubtypeOf(expectedType),
								slotRegisters(),
								quotedBundleName);
						// Propagate the type into the write slot, so that code
						// downstream can determine if it's worth splitting the
						// control flow graph to keep the stronger type in some
						// of the branches.
						moveRegister(
							resultReg,
							writeSlot(
								stackp,
								resultReg.type(),
								resultReg.constantOrNull()));
						addInstruction(
							L2_JUMP.instance,
							edgeTo(afterCall));
						return;
					}
				}
				// Collect the arguments into a tuple and invoke the handler
				// for failed method lookups.
				final A_Set solutionsSet = solutions.asSet();
				final L2WritePointerOperand errorCodeWrite =
					newObjectRegisterWriter(naturalNumbers(), null);
				addInstruction(
					L2_DIAGNOSE_LOOKUP_FAILURE.instance,
					new L2ConstantOperand(solutionsSet),
					errorCodeWrite);
				final L2WritePointerOperand invalidSendReg =
					newObjectRegisterWriter(mostGeneralFunctionType(), null);
				addInstruction(
					L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
					invalidSendReg);
				// Make the method itself accessible to the code.
				final L2ReadPointerOperand methodReg = constantRegister(method);
				// Collect the arguments into a tuple.
				final L2WritePointerOperand argumentsTupleWrite =
					newObjectRegisterWriter(mostGeneralTupleType(), null);
				addInstruction(
					L2_CREATE_TUPLE.instance,
					new L2ReadVectorOperand(arguments),
					argumentsTupleWrite);
				// Ignore the result register, since the function can't return.
				generateGeneralFunctionInvocation(
					invalidSendReg.read(),
					asList(
						errorCodeWrite.read(),
						methodReg,
						argumentsTupleWrite.read()),
					bottom(),
					true,
					slotRegisters(),
					"failed lookup of " + quotedBundleName);
				addUnreachableCode();
			});

		// This is the merge point from each of the polymorphic invocations.
		// They each have their own dedicated off-ramps and on-ramps, since they
		// may have differing requirements about whether they need their return
		// values checked, and whether reification can even happen (e.g., for
		// contextually infallible, inlineable primitives, it can't).
		startBlock(afterCall);
	}

	/**
	 * Generate conditional branch to either passEdge or failEdge based on
	 * whether the given register equals the given constant value.
	 *
	 * <p>If the constant to compare against is a boolean, check the proveance
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
	 * registers.  In the usual case that reification is avoided, the result of
	 * the call will be
	 *
	 * In the event of reification, capture the specified array
	 * of slot registers into a continuation.  On return into the reified
	 * continuation, explode the slots into registers, then capture the returned
	 * value in a new register answered by this method.  On normal return, just
	 * capture the returned value in the answered register.
	 *
	 * @param functionToCallReg
	 *        The {@link L2ReadPointerOperand} containing the function to
	 *        invoke.
	 * @param arguments
	 *        The {@link List} of {@link L2ReadPointerOperand}s that supply
	 *        arguments to the function.
	 * @param expectedType
	 *        The type of value this call must produce.  This can be stronger
	 *        than the function's declared return type, but that requires a
	 *        run-time check upon return.
	 * @param skipReturnCheck
	 *        Whether to elide the code sequence that checks whether the
	 *        returned type agrees with the expected return type, which may be
	 *        stronger than the function's declared return type due to semantic
	 *        restrictions.
	 * @param slotsIfReified
	 *        An array of {@link L2ReadPointerOperand}s corresponding to the
	 *        slots that would be populated during reification.
	 * @param invocationName
	 *        A {@link String} describing the purpose of this call.
	 * @return The {@link L2ReadPointerOperand} holding the result of the call.
	 */
	public L2ReadPointerOperand generateGeneralFunctionInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final A_Type expectedType,
		final boolean skipReturnCheck,
		final L2ReadPointerOperand[] slotsIfReified,
		final String invocationName)
	{
		if (functionToCallReg.type().isSubtypeOf(mostGeneralFunctionType()))
		{
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
			final A_Type sizeRange =
				functionToCallReg.type().argsTupleType().sizeRange();
			assert sizeRange.isBottom()
				|| !sizeRange.lowerBound().equals(sizeRange.upperBound())
				|| sizeRange.rangeIncludesInt(arguments.size());
		}
		final @Nullable Primitive primitiveOrNull =
			determinePrimitive(functionToCallReg);
		final @Nullable L2ReadPointerOperand specialOutputReg =
			tryToGenerateSpecialInvocation(
				functionToCallReg, primitiveOrNull, arguments);
		if (specialOutputReg != null)
		{
			// The call was to a specific primitive function that generated
			// optimized code that left the primitive result in resultReg.
			if (skipReturnCheck
				|| specialOutputReg.type().isSubtypeOf(expectedType))
			{
				if (specialOutputReg.type().isBottom())
				{
					addUnreachableCode();
				}
				return specialOutputReg;
			}
			return generateReturnTypeCheck(specialOutputReg, expectedType);
		}

		// The function isn't known to be a particular primitive function, or
		// the primitive wasn't able to special code generation for it, so just
		// invoke it like a non-primitive.
		final L2BasicBlock onReturn =
			createBasicBlock("returned from call of " + invocationName);
		final L2BasicBlock onReification =
			createBasicBlock("reification during call of " + invocationName);
		addInstruction(
			L2_INVOKE.instance,
			functionToCallReg,
			new L2ReadVectorOperand(arguments),
			new L2ImmediateOperand(skipReturnCheck ? 1 : 0),
			new L2PcOperand(onReturn, slotsIfReified, currentManifest),
			new L2PcOperand(onReification, slotsIfReified, currentManifest));

		// Reification has been requested while the call is in progress.
		startBlock(onReification);
		reify(
			slotsIfReified,
			getCurrentFunction(),
			constantRegister(nil),
			newContinuationReg ->
				addInstruction(
					L2_RETURN_FROM_REIFICATION_HANDLER.instance,
					newContinuationReg),
			onReturn,
			TO_RETURN_INTO);
		// This is reached either (1) after a normal return from the invoke, or
		// (2) after reification, a return into the reified continuation, and
		// the subsequent explosion of the continuation into slot registers.

		final A_Type functionType = functionToCallReg.type();
		final A_Type functionReturnType =
			functionType.isSubtypeOf(mostGeneralFunctionType())
				? functionType.returnType()
				: TOP.o();
		final L2ReadPointerOperand resultReg =
			getLatestReturnValue(functionReturnType);
		if (skipReturnCheck || resultReg.type().isSubtypeOf(expectedType))
		{
			if (resultReg.type().isBottom())
			{
				addUnreachableCode();
			}
			return resultReg;
		}
		if (primitiveOrNull != null)
		{
			final List<A_Type> argTypes = new ArrayList<>(arguments.size());
			for (final L2ReadPointerOperand arg : arguments)
			{
				argTypes.add(arg.type());
			}
			final A_Type guaranteedType =
				primitiveOrNull.returnTypeGuaranteedByVM(argTypes);
			if (guaranteedType.isSubtypeOf(expectedType))
			{
				// Elide the return check, since the primitive guaranteed it.
				// Do a move to strengthen the type.
				final L2WritePointerOperand strongerResultWrite =
					newObjectRegisterWriter(guaranteedType, null);
				moveRegister(resultReg, strongerResultWrite);
				return strongerResultWrite.read();
			}
		}
		return generateReturnTypeCheck(resultReg, expectedType);
	}

	/**
	 * Generate code to perform a type check of the valueReg's content against
	 * the expectedType (an {@link A_Type}).  If the check fails, invoke the
	 * bottom-valued function accessed via {@link
	 * #getInvalidResultFunctionRegister()}, never to return (but synthesizing
	 * a proper continuation in the event of reification while it's running).
	 * If the check passes, the value will be written to the {@link
	 * L2ReadPointerOperand} answered by this method.
	 *
	 * @param valueReg
	 *        The {@link L2ReadPointerOperand} containing the value to check.
	 * @param expectedType
	 *        The {@link A_Type} to check the value against.
	 * @return The {@link L2ReadPointerOperand} containing the checked value,
	 *         having a static type at least as strong as expectedType.
	 */
	private L2ReadPointerOperand generateReturnTypeCheck (
		final L2ReadPointerOperand valueReg,
		final A_Type expectedType)
	{
		if (valueReg.type().isBottom())
		{
			// Bottom has no instances, so we can't get here.  It would be wrong
			// to do this based on the expectedTYpe being bottom, since that's
			// only an erroneous semantic restriction, not a VM problem.
			addUnreachableCode();
			return valueReg;
		}

		// Check the return value against the expectedType.
		final L2BasicBlock passedCheck =
			createBasicBlock("passed return check");
		final L2BasicBlock failedCheck =
			createBasicBlock("failed return check");
		if (valueReg.type().typeIntersection(expectedType).isBottom())
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
			addInstruction(
				L2_JUMP_IF_KIND_OF_CONSTANT.instance,
				valueReg,
				new L2ConstantOperand(expectedType),
				new L2PcOperand(
					passedCheck,
					slotRegisters(),
					currentManifest,
					valueReg.restrictedTo(expectedType, null)),
				new L2PcOperand(
					failedCheck,
					slotRegisters(),
					currentManifest,
					valueReg.restrictedTo(TOP.o(), null)));
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
			valueReg,
			edgeTo(wroteVariable),
			edgeTo(wroteVariable));

		// Whether the set succeeded or failed doesn't really matter, although
		// it should always succeed for this freshly created variable.
		startBlock(wroteVariable);
		// Recurse to generate the call to the failure handler.  Since it's
		// bottom-valued, and can therefore skip any the result check, the
		// recursive call won't exceed two levels deep.
		generateGeneralFunctionInvocation(
			getInvalidResultFunctionRegister(),
			asList(
				getReturningFunctionRegister(),
				constantRegister(expectedType),
				variableToHoldValueWrite.read()),
			bottom(),
			true,
			slotRegisters(),
			"failed return check");
		addUnreachableCode();

		if (passedCheck.currentlyReachable())
		{
			startBlock(passedCheck);
			final L2WritePointerOperand strongerResultWrite =
				newObjectRegisterWriter(
					expectedType.typeIntersection(valueReg.type()),
					null);
			moveRegister(valueReg, strongerResultWrite);
			return strongerResultWrite.read();
		}
		// It's not reachable, but we still need to return a register.  Create
		// one uninitialized and return it.
		return newObjectRegisterWriter(bottom(), null).read();
	}

	/**
	 * Attempt to create a more specific instruction sequence than just an
	 * {@link L2_INVOKE}.  In particular, see if the functionToCallReg is known
	 * to contain a constant function (a common case) which is an inlineable
	 * primitive, and if so, delegate this opportunity to the primitive.
	 *
	 * <p>We must either generate no code and answer {@code null}, or generate
	 * code that has the same effect as having run the function in the register
	 * without fear of reification or abnormal control flow.  L2SemanticConstant folding,
	 * for example, can output a simple {@link L2_MOVE_CONSTANT} into a suitable
	 * register answered by this method.</p>
	 *
	 * @param functionToCallReg
	 *        The register containing the function to invoke.
	 * @param primitiveOrNull
	 *        The {@link Primitive} that the function in the register is
	 *        guaranteed to be, or {@code null}.
	 * @param arguments
	 *        The arguments to supply to the function.
	 * @return The register that holds the result of the invocation.
	 */
	private @Nullable L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final @Nullable Primitive primitiveOrNull,
		final List<L2ReadPointerOperand> arguments)
	{
		if (primitiveOrNull != null)
		{
			// It's a primitive function.
			final Interpreter interpreter = translator.interpreter();
			if (primitiveOrNull.hasFlag(CanFold))
			{
				// It can be folded, if supplied with constants.
				final int count = arguments.size();
				final List<AvailObject> constants = new ArrayList<>(count);
				for (final L2ReadPointerOperand regRead : arguments)
				{
					if (regRead.constantOrNull() == null)
					{
						break;
					}
					constants.add((AvailObject) regRead.constantOrNull());
				}
				if (constants.size() == count)
				{
					// Fold the primitive.  A foldable primitive must not
					// require access to the enclosing function or its code.
					final @Nullable A_Function savedFunction =
						interpreter.function;
					interpreter.function = null;
					final String savedDebugModeString =
						interpreter.debugModeString;
					if (Interpreter.debugL2)
					{
						Interpreter.log(
							Interpreter.loggerDebugL2,
							Level.FINER,
							"{0}FOLD {1}:",
							interpreter.debugModeString,
							primitiveOrNull.name());
					}
					final Result success;
					try
					{
						success = primitiveOrNull.attempt(
							constants, interpreter, true);
					}
					finally
					{
						interpreter.debugModeString = savedDebugModeString;
						interpreter.function = savedFunction;
					}

					if (success == SUCCESS)
					{
						return constantRegister(
							interpreter.latestResult().makeImmutable());
					}
					// The primitive failed with the supplied arguments,
					// which it's allowed to do even if it CanFold.
					assert success == FAILURE;
					assert !primitiveOrNull.hasFlag(CannotFail);
				}
			}

			// The primitive can't be folded, so let it generate its own
			// code equivalent to invocation.
			final List<A_Type> argTypes = arguments.stream()
				.map(L2ReadPointerOperand::type)
				.collect(toList());
			return primitiveOrNull.tryToGenerateSpecialInvocation(
				functionToCallReg, arguments, argTypes, this);
		}
		return null;
	}

	/**
	 * Given a register that holds the function to invoke, answer either the
	 * {@link Primitive} it will be known to run, or {@code null}.
	 *
	 * @param functionToCallReg
	 *        The {@link L2ReadPointerOperand} containing the function to
	 *        invoke.
	 * @return Either {@code null} or the function's {@link Primitive}.
	 */
	private static @Nullable Primitive determinePrimitive (
		final L2ReadPointerOperand functionToCallReg)
	{
		final @Nullable A_Function functionIfKnown =
			(A_Function) functionToCallReg.constantOrNull();
		final @Nullable Primitive primitive;
		if (functionIfKnown != null)
		{
			// The exact function is known.
			primitive = functionIfKnown.code().primitive();
		}
		else
		{
			// See if we can at least find out the code that the function was
			// created from.
			final L2Instruction functionDefinition =
				functionToCallReg.register().definitionSkippingMoves();
			final @Nullable A_RawFunction constantCode =
				functionDefinition.operation.getConstantCodeFrom(
					functionDefinition);
			primitive = constantCode == null ? null : constantCode.primitive();
		}
		return primitive;
	}

	/**
	 * Generate a slower, but much more compact invocation of a polymorphic
	 * method call.  The slots have already been adjusted to be consistent with
	 * having popped the arguments and pushed the expected type.
	 *
	 * @param bundle
	 *        The {@linkplain MessageBundleDescriptor message bundle} containing
	 *        the definition to invoke.
	 * @param arguments
	 *        The list of argument registers to use for the call.
	 * @param expectedType
	 *        The expected return {@link A_Type}.
	 * @param superUnionType
	 *        A tuple type whose union with the type of the arguments tuple at
	 *        runtime is used for lookup.  The type ⊥ ({@link
	 *        BottomTypeDescriptor#bottom() bottom}) is used to indicate this is
	 *        not a super call.
	 * @param invocationName
	 *        A {@link String} describing the purpose of this call.
	 */
	private void generateSlowPolymorphicCall (
		final A_Bundle bundle,
		final List<L2ReadPointerOperand> arguments,
		final A_Type expectedType,
		final A_Type superUnionType,
		final String invocationName)
	{
		final A_Method method = bundle.bundleMethod();
		final int nArgs = method.numArgs();
		final L2BasicBlock lookupSucceeded =
			createBasicBlock("lookup succeeded for " + invocationName);
		final L2BasicBlock lookupFailed =
			createBasicBlock("lookup failed for " + invocationName);
		final L2WritePointerOperand functionReg = newObjectRegisterWriter(
			TOP.o(), null);
		final L2WritePointerOperand errorCodeReg = newObjectRegisterWriter(
			TOP.o(), null);

		final List<A_Type> argumentTypes = IntStream.rangeClosed(1, nArgs)
			.mapToObj(
				i -> arguments.get(i - 1).type().typeUnion(
					superUnionType.typeAtIndex(i)))
			.collect(toList());
		final List<A_Function> possibleFunctions =
			bundle.bundleMethod().filterByTypes(argumentTypes).stream()
				.filter(A_Definition::isMethodDefinition)
				.map(A_Definition::bodyBlock)
				.collect(toList());
		final A_Type functionTypeUnion = enumerationWith(
			setFromCollection(possibleFunctions));
		if (superUnionType.isBottom())
		{
			// Not a super-call.
			addInstruction(
				L2_LOOKUP_BY_VALUES.instance,
				new L2SelectorOperand(bundle),
				new L2ReadVectorOperand(arguments),
				functionReg,
				errorCodeReg,
				new L2PcOperand(
					lookupSucceeded,
					slotRegisters(),
					currentManifest,
					new PhiRestriction(
						functionReg.register(),
						functionTypeUnion,
						possibleFunctions.size() == 1
							? possibleFunctions.get(0)
							: null)),
				new L2PcOperand(
					lookupFailed,
					slotRegisters(),
					currentManifest,
					new PhiRestriction(
						errorCodeReg.register(),
						L2_LOOKUP_BY_VALUES.lookupErrorsType,
						null)));
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
					superUnionType.typeAtIndex(i);
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
				new L2ReadVectorOperand(argTypeRegs),
				functionReg,
				errorCodeReg,
				new L2PcOperand(
					lookupSucceeded,
					slotRegisters(),
					currentManifest,
					new PhiRestriction(
						functionReg.register(),
						functionTypeUnion,
						possibleFunctions.size() == 1
							? possibleFunctions.get(0)
							: null)),
				new L2PcOperand(
					lookupFailed,
					slotRegisters(),
					currentManifest,
					new PhiRestriction(
						errorCodeReg.register(),
						L2_LOOKUP_BY_VALUES.lookupErrorsType,
						null)));
		}
		// At this point, we've attempted to look up the method, and either
		// jumped to lookupSucceeded with functionReg set to the body function,
		// or jumped to lookupFailed with errorCodeReg set to the lookup error
		// code.

		// Emit the lookup failure case.
		startBlock(lookupFailed);
		final L2WritePointerOperand invalidSendReg = newObjectRegisterWriter(
			invalidMessageSendFunctionType, null);
		addInstruction(
			L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
			invalidSendReg);
		// Collect the arguments into a tuple.
		final L2ReadVectorOperand argsVector =
			new L2ReadVectorOperand(arguments);
		final L2WritePointerOperand argumentsTupleWrite =
			newObjectRegisterWriter(
				tupleTypeForSizesTypesDefaultType(
					singleInt(nArgs),
					tupleFromList(argsVector.types()),
					bottom()),
				null);
		addInstruction(
			L2_CREATE_TUPLE.instance,
			argsVector,
			argumentsTupleWrite);
		// Ignore result of call, which cannot actually return.
		generateGeneralFunctionInvocation(
			invalidSendReg.read(),
			asList(
				errorCodeReg.read(),
				constantRegister(method),
				argumentsTupleWrite.read()),
			bottom(),
			true,
			slotRegisters(),
			"report failed megamorphic lookup of " + invocationName);
		addUnreachableCode();

		startBlock(lookupSucceeded);
		// Now invoke the method definition's body.  Without looking at all of
		// the definitions we can't determine if the return type check can be
		// skipped.
		final L2ReadPointerOperand returnReg =
			generateGeneralFunctionInvocation(
				functionReg.read(),
				arguments,
				expectedType,
				false,
				slotRegisters(),
				"invocation for megamorphic " + invocationName);
		// Write the (already checked if necessary) result onto the stack.
		moveRegister(
			returnReg,
			writeSlot(stackp, expectedType, null));
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
		addInstruction(
			L2_REIFY_CALLERS.instance,
			new L2ImmediateOperand(1));
		// Extract that caller.
		final L2ReadPointerOperand callerReg = getCurrentContinuation();

		// When the lambda below runs, it's generating code at the point where
		// continuationReg will have the new continuation.
		reify(
			slotRegisters(),
			getCurrentFunction(),
			callerReg,
			continuationReg ->
				addInstruction(
					L2_PROCESS_INTERRUPT.instance,
					continuationReg),
			merge,
			TO_RESUME);
		// And now... either we're back or we never left.
	}

	/**
	 * Emit the specified variable-reading instruction, and an off-ramp to
	 * deal with the case that the variable is unassigned.
	 *
	 * @param getOperation
	 *        The {@linkplain L2Operation#isVariableGet() variable reading}
	 *        {@linkplain L2Operation operation}.
	 * @param variable
	 *        The location of the {@linkplain A_Variable variable}.
	 * @param destination
	 *        The destination of the extracted value.
	 */
	public void emitGetVariableOffRamp (
		final L2Operation getOperation,
		final L2ReadPointerOperand variable,
		final L2WritePointerOperand destination)
	{
		assert getOperation.isVariableGet();
		final L2BasicBlock success =
			createBasicBlock("successfully read variable");
		final L2BasicBlock failure =
			createBasicBlock("failed to read variable");
		// Emit the get-variable instruction variant.
		addInstruction(
			getOperation,
			variable,
			destination,
			edgeTo(success),
			edgeTo(failure));

		// Emit the failure path.
		startBlock(failure);
		final L2WritePointerOperand unassignedReadFunction =
			newObjectRegisterWriter(
				functionType(emptyTuple(), bottom()),
				null);
		addInstruction(
			L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION.instance,
			unassignedReadFunction);
		generateGeneralFunctionInvocation(
			unassignedReadFunction.read(),
			emptyList(),
			bottom(),
			true,
			slotRegisters(),
			"invoke failed variable get handler");
		addUnreachableCode();

		// End with the success path.
		startBlock(success);
	}

	/**
	 * Emit the specified variable-writing instruction, and an off-ramp to
	 * deal with the case that the variable has {@linkplain
	 * VariableAccessReactor write reactors} but {@linkplain
	 * Interpreter#traceVariableWrites() variable write tracing} is
	 * disabled.
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
		final L2BasicBlock successBlock =
			createBasicBlock("set local success");
		final L2BasicBlock failureBlock =
			createBasicBlock("set local failure");
		// Emit the set-variable instruction.
		addInstruction(
			setOperation,
			variable,
			newValue,
			edgeTo(successBlock),
			edgeTo(failureBlock));

		// Emit the failure off-ramp.
		startBlock(failureBlock);
		final L2WritePointerOperand observeFunction = newObjectRegisterWriter(
			functionType(
				tuple(
					mostGeneralFunctionType(),
					mostGeneralTupleType()),
				TOP.o()),
			null);
		addInstruction(
			L2_GET_IMPLICIT_OBSERVE_FUNCTION.instance,
			observeFunction);
		final L2WritePointerOperand variableAndValueTupleReg =
			newObjectRegisterWriter(
				tupleTypeForTypes(variable.type(), newValue.type()),
				null);
		addInstruction(
			L2_CREATE_TUPLE.instance,
			new L2ReadVectorOperand(asList(variable, newValue)),
			variableAndValueTupleReg);
		generateGeneralFunctionInvocation(
			observeFunction.read(),
			asList(
				constantRegister(Interpreter.assignmentFunction()),
				variableAndValueTupleReg.read()),
			TOP.o(),
			true,
			slotRegisters(),
			"set variable failure");
		addInstruction(
			L2_JUMP.instance,
			edgeTo(successBlock));

		// End with the success block.  Note that the failure path can lead here
		// if the implicit-observe function returns.
		startBlock(successBlock);
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

	/**
	 * For each level one instruction, write a suitable transliteration into
	 * level two instructions.
	 */
	void translateL1Instructions ()
	{
		initialBlock.makeIrremovable();
		startBlock(initialBlock);
		final @Nullable Primitive primitive = code().primitive();
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
		// offset 1 (after the L2_TRY_PRIMITIVE) would also work.
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2ImmediateOperand(0));

		// Do any reoptimization before capturing arguments.
		if (translator.optimizationLevel == OptimizationLevel.UNOPTIMIZED)
		{
			// Optimize it again if it's called frequently enough.
			code().countdownToReoptimize(
				L2Chunk.countdownForNewlyOptimizedCode());
			addInstruction(
				L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
				new L2ImmediateOperand(
					OptimizationLevel.FIRST_TRANSLATION.ordinal()));
			// If it was reoptimized, it would have jumped to the
			// afterOptionalInitialPrimitiveBlock in the new chunk.
		}

		// Capture the arguments.
		final int numArgs = code().numArgs();
		if (numArgs > 0)
		{
			final A_Type tupleType = code().functionType().argsTupleType();
			final List<L2WritePointerOperand> argRegs =
				IntStream.rangeClosed(1, numArgs)
					.mapToObj(
						i -> writeSlot(i, tupleType.typeAtIndex(i), null))
					.collect(toList());
			addInstruction(
				L2_GET_ARGUMENTS.instance,
				new L2WriteVectorOperand(argRegs));
		}

		// Create the locals.
		final int numLocals = code().numLocals();
		for (int local = 1; local <= numLocals; local++)
		{
			final A_Type localType = code().localTypeAt(local);
			addInstruction(
				L2_CREATE_VARIABLE.instance,
				new L2ConstantOperand(localType),
				writeSlot(numArgs + local, localType, null));
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
				getLatestReturnValue(code().localTypeAt(1).writeType()),
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

		// Transliterate each level one nybblecode into L2Instructions.
		while (pc <= nybbles.tupleSize())
		{
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.all()[nybble].dispatch(this);
		}
		// Generate the implicit return after the instruction sequence.
		addInstruction(
			L2_RETURN.instance,
			readSlot(stackp),
			getSkipReturnCheck());
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
	 * {@link L2Chunk#unoptimizedChunk()}.
	 *
	 * @param reenterFromRestartBlock
	 *        The block to reenter to {@link P_RestartContinuation} an
	 *        {@link A_Continuation}.
	 * @param loopBlock
	 *        The main loop of the interpreter.
	 * @param reenterFromCallBlock
	 *        The entry point for returning into a reified continuation.
	 * @param reenterFromInterruptBlock
	 *        The entry point for resuming from an interrupt.
	 */
	void generateDefaultChunk (
		final L2BasicBlock reenterFromRestartBlock,
		final L2BasicBlock loopBlock,
		final L2BasicBlock reenterFromCallBlock,
		final L2BasicBlock reenterFromInterruptBlock)
	{
		initialBlock.makeIrremovable();
		loopBlock.makeIrremovable();
		reenterFromRestartBlock.makeIrremovable();
		reenterFromCallBlock.makeIrremovable();
		reenterFromInterruptBlock.makeIrremovable();

		// 0. First try to run it as a primitive.
		startBlock(initialBlock);
		addInstruction(
			L2_TRY_PRIMITIVE.instance);
		// This instruction gets stripped out.
//		addInstruction(
//			L2_JUMP.instance,
//			edgeTo(reenterFromCallBlock, emptySlots));
		// Only if the primitive fails should we even consider optimizing the
		// fallback code.

		// 1. Update counter and maybe optimize *before* extracting arguments.
		afterOptionalInitialPrimitiveBlock = reenterFromRestartBlock;
		startBlock(reenterFromRestartBlock);
		addInstruction(
			L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
			new L2ImmediateOperand(
				OptimizationLevel.FIRST_TRANSLATION.ordinal()));

		// 2. Build registers, get arguments, create locals, capture primitive
		// failure value, if any.
		addInstruction(
			L2_PREPARE_NEW_FRAME_FOR_L1.instance);

		// 3. The main L1 interpreter loop.
		startBlock(loopBlock);
		final L2ReadPointerOperand[] emptySlots = new L2ReadPointerOperand[0];
		addInstruction(
			L2_INTERPRET_LEVEL_ONE.instance,
			new L2PcOperand(reenterFromCallBlock, emptySlots, currentManifest),
			new L2PcOperand(
				reenterFromInterruptBlock, emptySlots, currentManifest));

		// 4,5. If reified, calls return here.
		startBlock(reenterFromCallBlock);
		addInstruction(
			L2_REENTER_L1_CHUNK_FROM_CALL.instance);
		addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(loopBlock, emptySlots, currentManifest));

		// 6,7. If reified, interrupts return here.
		startBlock(reenterFromInterruptBlock);
		addInstruction(
			L2_REENTER_L1_CHUNK_FROM_INTERRUPT.instance);
		addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(loopBlock, emptySlots, currentManifest));
	}

	@Override
	public void L1_doCall ()
	{
		final A_Bundle bundle = code().literalAt(getInteger());
		final A_Type expectedType = code().literalAt(getInteger());
		generateCall(bundle, expectedType, bottom());
	}

	@Override
	public void L1_doPushLiteral ()
	{
		final AvailObject constant = code().literalAt(getInteger());
		stackp--;
		moveConstantToSlot(constant, stackp);
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		final int localIndex = getInteger();
		stackp--;
		final L2ReadPointerOperand source = readSlot(localIndex);
		moveRegister(
			source,
			writeSlot(stackp, source.type(), source.constantOrNull()));
		nilSlot(localIndex);
	}

	@Override
	public void L1_doPushLocal ()
	{
		final int localIndex = getInteger();
		stackp--;
		final L2ReadPointerOperand source = readSlot(localIndex);
		moveRegister(
			source,
			writeSlot(stackp, source.type(), source.constantOrNull()));
		addInstruction(L2_MAKE_IMMUTABLE.instance, readSlot(stackp));
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		final int outerIndex = getInteger();
		stackp--;
		final A_Type outerType = code().outerTypeAt(outerIndex);
		if (outerType.instanceCount().equalsInt(1)
			&& !outerType.isInstanceMeta())
		{
			// The exact outer is known statically.
			moveConstantToSlot(outerType.instance(), stackp);
		}
		else
		{
			// The exact outer isn't known statically, but we still have its
			// type information.
			final L2ReadPointerOperand functionTempRead = getCurrentFunction();
			addInstruction(
				L2_MOVE_OUTER_VARIABLE.instance,
				new L2ImmediateOperand(outerIndex),
				functionTempRead,
				writeSlot(stackp, outerType, null));
			// Simplify the logic related to L1's nilling of mutable outers upon
			// their final use.
			addInstruction(L2_MAKE_IMMUTABLE.instance, readSlot(stackp));
		}
	}

	@Override
	public void L1_doClose ()
	{
		final int count = getInteger();
		final A_RawFunction codeLiteral = code().literalAt(getInteger());
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
			new L2ReadVectorOperand(outers),
			writeSlot(stackp, codeLiteral.functionType(), null));

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
		final int localIndex = getInteger();
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			readSlot(localIndex),
			readSlot(stackp));
		stackp++;
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		final int index = getInteger();
		stackp--;
		final A_Type outerType = code().localTypeAt(index - code().numArgs());
		final A_Type innerType = outerType.readType();
		emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			readSlot(index),
			writeSlot(stackp, innerType, null));

	}

	@Override
	public void L1_doPushOuter ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code().outerTypeAt(outerIndex);
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		stackp--;
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionReg,
			writeSlot(stackp, outerType, null));
		addInstruction(
			L2_MAKE_IMMUTABLE.instance, readSlot(stackp));
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
		final int outerIndex = getInteger();
		final A_Type outerType = code().outerTypeAt(outerIndex);
		final A_Type innerType = outerType.readType();
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		final L2WritePointerOperand tempVarReg =
			newObjectRegisterWriter(outerType, null);
		stackp--;
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionReg,
			tempVarReg);
		emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			tempVarReg.read(),
			writeSlot(stackp, innerType, null));
	}

	@Override
	public void L1_doSetOuter ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code().outerTypeAt(outerIndex);
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		final L2WritePointerOperand tempVarReg =
			newObjectRegisterWriter(outerType, null);
//		addInstruction(
//			L2_MAKE_IMMUTABLE.instance,
//			readSlot(stackp));
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionReg,
			tempVarReg);
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			tempVarReg.read(),
			readSlot(stackp));
		stackp++;
	}

	@Override
	public void L1_doGetLocal ()
	{
		final int index = getInteger();
		final A_Type outerType = code().localTypeAt(index - code().numArgs());
		final A_Type innerType = outerType.readType();
		stackp--;
		emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			readSlot(index),
			writeSlot(stackp, innerType, null));
	}

	@Override
	public void L1_doMakeTuple ()
	{
		final int count = getInteger();
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
				new L2ReadVectorOperand(vector),
				writeSlot(stackp, tupleType, null));
		}
	}

	@Override
	public void L1_doGetOuter ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code().outerTypeAt(outerIndex);
		final A_Type innerType = outerType.readType();
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		stackp--;
		final L2WritePointerOperand tempVarReg =
			newObjectRegisterWriter(outerType, null);
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionReg,
			tempVarReg);
		emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			tempVarReg.read(),
			writeSlot(stackp, innerType, null));
	}

	@Override
	public void L1_doExtension ()
	{
		// The extension nybblecode was encountered.  Read another nybble and
		// add 16 to get the L1Operation's ordinal.
		final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		L1Operation.all()[nybble + 16].dispatch(this);
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		stackp--;
		assert code().primitive() == null;
		final int numArgs = code().numArgs();
		final List<L2ReadPointerOperand> vectorWithOnlyArgsPreserved =
			new ArrayList<>(numSlots);
		for (int i = 1; i <= numArgs; i++)
		{
			vectorWithOnlyArgsPreserved.add(readSlot(i));
		}
		final L2ReadPointerOperand nilTemp = constantRegister(nil);
		for (int i = numArgs + 1; i <= numSlots; i++)
		{
			vectorWithOnlyArgsPreserved.add(nilTemp);
		}
		final A_Type continuationType =
			continuationTypeForFunctionType(code().functionType());
		final L2WritePointerOperand destReg =
			writeSlot(stackp, continuationType, null);
		final L2ReadPointerOperand functionRead = getCurrentFunction();
		final L2ReadIntOperand skipReturnCheckRead = getSkipReturnCheck();
		addInstruction(
			L2_REIFY_CALLERS.instance,
			new L2ImmediateOperand(1));
		final L2ReadPointerOperand continuationRead = getCurrentContinuation();
		final L2BasicBlock afterCreation =
			createBasicBlock("after push label");
		final L2ReadPointerOperand[] allUndefinedSlots =
			new L2ReadPointerOperand[slotRegisters.length];
		for (int i = 1; i < allUndefinedSlots.length; i++)
		{
			allUndefinedSlots[i] =
				newObjectRegisterWriter(TOP.o(), null).read();
		}
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			continuationRead,
			functionRead,
			new L2ImmediateOperand(0),
			new L2ImmediateOperand(numSlots + 1),
			skipReturnCheckRead,
			new L2ReadVectorOperand(vectorWithOnlyArgsPreserved),
			destReg,
			new L2PcOperand(
				initialBlock,
				new L2ReadPointerOperand[0],
				new L2ValueManifest()),
			edgeTo(afterCreation));
		startBlock(afterCreation);

		// Freeze all fields of the new object, including its caller,
		// function, and arguments.
		addInstruction(
			L2_MAKE_SUBOBJECTS_IMMUTABLE.instance,
			destReg.read());
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		final A_Variable literalVariable = code().literalAt(getInteger());
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
			final A_Type innerType = literalVariable.kind().readType();
			emitGetVariableOffRamp(
				L2_GET_VARIABLE.instance,
				constantRegister(literalVariable),
				writeSlot(stackp, innerType, null));
		}
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		final A_Variable literalVariable = code().literalAt(getInteger());
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			constantRegister(literalVariable),
			readSlot(stackp));
		stackp++;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final L2ReadPointerOperand sourceReg = readSlot(stackp);
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			sourceReg);
		stackp--;
		moveRegister(
			sourceReg,
			writeSlot(
				stackp,
				sourceReg.type(),
				sourceReg.constantOrNull()));
	}

	@Override
	public void L1Ext_doPermute ()
	{
		// Move into the permuted temps, then back to the stack.  This puts the
		// responsibility for optimizing away extra moves (by coloring the
		// registers) on the optimizer.
		final A_Tuple permutation = code().literalAt(getInteger());
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
					stackp + size - i, temp.type(), temp.constantOrNull()));
		}
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		final AvailObject method = code().literalAt(getInteger());
		final AvailObject expectedType = code().literalAt(getInteger());
		final AvailObject superUnionType = code().literalAt(getInteger());
		generateCall(method, expectedType, superUnionType);
	}

	@Override
	public void L1Ext_doSetSlot ()
	{
		final int destIndex = getInteger();
		final L2ReadPointerOperand source = readSlot(stackp);
		moveRegister(
			source,
			writeSlot(destIndex, source.type(), source.constantOrNull()));
		nilSlot(stackp);
		stackp++;
	}
}
