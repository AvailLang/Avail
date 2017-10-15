/**
 * L1NaiveTranslator.java
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
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelOne.L1OperationDispatcher;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.controlflow
	.P_RestartContinuationWithArguments;
import com.avail.utility.Mutable;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import static com.avail.AvailRuntime.invalidMessageSendFunctionType;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.instanceTypeOrMetaOn;
import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.continuationTypeForFunctionType;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.*;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.METHOD_DEFINITION;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.FAILURE;
import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static com.avail.optimizer.L2Translator.*;
import static com.avail.utility.Nulls.stripNull;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * The {@code L1NaiveTranslator} simply transliterates a sequence of
 * {@linkplain L1Operation level one instructions} into one or more simple
 * {@linkplain L2Instruction level two instructions}, under the assumption
 * that further optimization steps will be able to transform this code into
 * something much more efficient – without altering the level one semantics.
 */
public final class L1NaiveTranslator
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
	private final A_RawFunction code;

	/**
	 * The nybblecodes being optimized.
	 */
	private final A_Tuple nybbles;

	/**
	 * The current level one nybblecode program counter during naive translation
	 * to level two.
	 */
	private int pc;

	/**
	 * The current stack depth during naive translation to level two.
	 */
	private int stackp;

	private final @Nullable A_Function exactFunctionOrNull;

	/**
	 * The {@link L2BasicBlock} which is the entry point for a function that has
	 * just been invoked.
	 */
	final L2BasicBlock initialBlock = new L2BasicBlock("start");


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
	@Nullable  L2BasicBlock afterOptionalInitialPrimitiveBlock = null;

	/**
	 * The {@link L2BasicBlock} which is the entry point with which {@link
	 * P_RestartContinuation} and {@link P_RestartContinuationWithArguments} are
	 * able to restart a continuation.  The continuation must have been created
	 * with a {@link L1NaiveTranslator#L1Ext_doPushLabel()} instruction, which
	 * corresponds to a label declaration at the start of a function.  This is
	 * only created if at least one push-label instruction is actually
	 * encountered.
	 */
	@Nullable L2BasicBlock restartLabelBlock = null;

	/**
	 * An {@link L2BasicBlock} that shouldn't actually be dynamically reachable.
	 */
	private @Nullable L2BasicBlock unreachableBlock = null;

	/**
	 * Answer an L2PcOperand that targets an {@link L2BasicBlock} which should
	 * never actually be dynamically reached.
	 *
	 * @return An {@link L2PcOperand} that should never be traversed.
	 */
	public L2PcOperand unreachablePcOperand ()
	{
		if (unreachableBlock == null)
		{
			unreachableBlock = createBasicBlock("UNREACHABLE");
			// Because we generate the initial code in control flow order, we
			// have to wait until later to generate the instructions.  We strip
			// out all phi information here.
		}
		return new L2PcOperand(
			unreachableBlock,
			new L2ReadPointerOperand[0]);
	}

	/** The control flow graph being generated. */
	public final L2ControlFlowGraph controlFlowGraph =
		new L2ControlFlowGraph(initialBlock);

	/** The {@link L2BasicBlock} that code is currently being generated into. */
	L2BasicBlock currentBlock = initialBlock;

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
	L1NaiveTranslator (final L2Translator translator)
	{
		this.translator = translator;
		code = translator.codeOrFail();
		nybbles = code.nybbles();
		pc = 1;
		stackp = code.maxStackDepth() + 1;
		slotRegisters =
			new L2ReadPointerOperand[code.numArgsAndLocalsAndStack()];
		exactFunctionOrNull = computeExactFunctionOrNullForCode(code);
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
		currentBlock = block;
		block.startIn(this);
	}

	boolean currentlyReachable ()
	{
		return currentBlock == controlFlowGraph.initialBlock
			|| currentBlock.hasPredecessors();
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
	public L2ReadPointerOperand readSlot (final int slotNumber)
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
	public L2WritePointerOperand writeSlot (
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
	public void nilSlot (final int slotNumber)
	{
		addInstruction(
			L2_MOVE_CONSTANT.instance,
			new L2ConstantOperand(nil),
			writeSlot(slotNumber, TOP.o(), nil));
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
			translator.nextUnique(), type, constantOrNull);
	}

	/**
	 * Write instructions to extract the current function, and answer an {@link
	 * L2ReadPointerOperand} for the register that will hold the function
	 * afterward.
	 */
	public L2ReadPointerOperand getCurrentFunction ()
	{
		if (exactFunctionOrNull != null)
		{
			// The exact function is known.
			return constantRegister(exactFunctionOrNull);
		}
		// The exact function isn't known, but we know the raw function, so
		// we statically know the function type.
		final L2WritePointerOperand functionWrite =
			newObjectRegisterWriter(code.functionType(), null);
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
	public L2ReadPointerOperand getCurrentContinuation ()
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
	public L2ReadPointerOperand popCurrentContinuation ()
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
	public L2ReadIntOperand getSkipReturnCheck ()
	{
		final L2IntegerRegister tempIntReg = translator.newIntegerRegister();
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
	public L2ReadPointerOperand getLatestReturnValue (
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
	public L2ReadPointerOperand getReturningFunctionRegister ()
	{
		final L2WritePointerOperand writer = newObjectRegisterWriter(
			mostGeneralFunctionType(), null);
		addInstruction(
			L2_GET_RETURNING_FUNCTION.instance,
			writer);
		return writer.read();
	}

	/**
	 * Create a {@link L2ReadVectorOperand} for reading each element of the
	 * given list of {@link L2ReadPointerOperand}s.
	 *
	 * @param sources
	 *        The list of register-read sources to aggregate.
	 * @return A new {@link L2ReadVectorOperand}.
	 */
	public static L2ReadVectorOperand readVector (
		final List<L2ReadPointerOperand> sources)
	{
		return new L2ReadVectorOperand(sources);
	}

	/**
	 * Create a {@link L2WriteVectorOperand} for writing each element of the
	 * given list of {@link L2WritePointerOperand}s.
	 *
	 * @param sources
	 *        The list of register-write destinations to aggregate.
	 * @return A new {@link L2WriteVectorOperand}.
	 */
	public static L2WriteVectorOperand writeVector (
		final List<L2WritePointerOperand> sources)
	{
		return new L2WriteVectorOperand(sources);
	}

	/**
	 * Answer the number of stack slots reserved for use by the code being
	 * optimized.
	 *
	 * @return The number of stack slots.
	 */
	public int numSlots ()
	{
		return translator.numSlots;
	}

	/**
	 * Answer a {@linkplain List list} of {@linkplain L2ObjectRegister
	 * object registers} that correspond to the slots of the current
	 * {@linkplain A_Continuation continuation}.
	 *
	 * @param nSlots
	 *        The number of continuation slots.
	 * @return A list of object registers.
	 */
	public List<L2ReadPointerOperand> continuationSlotsList (final int nSlots)
	{
		final List<L2ReadPointerOperand> slots = new ArrayList<>(nSlots);
		for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
		{
			slots.add(readSlot(slotIndex));
		}
		return slots;
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
		addInstruction(new L2Instruction(currentBlock, operation, operands));
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
		currentBlock.addInstruction(instruction);
	}

	/**
	 * Generate instruction(s) to move the given {@link AvailObject} into
	 * the specified {@link L2Register}.
	 *
	 * @param value
	 *        The value to move.
	 * @param destination
	 *        Where to move it.
	 */
	public void moveConstant (
		final A_BasicObject value,
		final L2WritePointerOperand destination)
	{
		addInstruction(
			L2_MOVE_CONSTANT.instance,
			new L2ConstantOperand(value),
			destination);
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
	public void moveConstantToSlot (
		final A_BasicObject value,
		final int slotIndex)
	{
		moveConstant(
			value,
			writeSlot(slotIndex, instanceTypeOrMetaOn(value), value));
	}

	/**
	 * Generate instructions to move {@linkplain NilDescriptor#nil nil}
	 * into each of the specified {@link L2ObjectRegister registers}.
	 *
	 * @param destinations
	 *        Which registers to clear.
	 */
	public void moveNils (
		final Collection<L2WritePointerOperand> destinations)
	{
		for (final L2WritePointerOperand destination : destinations)
		{
			addInstruction(
				L2_MOVE_CONSTANT.instance,
				new L2ConstantOperand(nil),
				destination);
		}
	}

	/**
	 * Write a constant value into a new register.  Answer an {@link
	 * L2ReadPointerOperand} for that register.
	 *
	 * @param value The constant value to write to a register.
	 * @return The {@link L2ReadPointerOperand} for the new register.
	 */
	public L2ReadPointerOperand constantRegister (final A_BasicObject value)
	{
		final A_Type type = value.equalsNil()
			? TOP.o()
			: instanceTypeOrMetaOn(value);
		final L2WritePointerOperand registerWrite =
			newObjectRegisterWriter(type, value);
		addInstruction(
			L2_MOVE_CONSTANT.instance,
			new L2ConstantOperand(value),
			registerWrite);
		return registerWrite.read();
	}

	/**
	 * Write a constant value into a new int register.  Answer an {@link
	 * L2ReadIntOperand} for that register.
	 *
	 * @param value The actual int to write to a new int register.
	 * @return The {@link L2ReadIntOperand} for the new register.
	 */
	public L2ReadIntOperand constantIntRegister (final int value)
	{
		final L2WriteIntOperand registerWrite =
			new L2WriteIntOperand(translator.newIntegerRegister());
		addInstruction(
			L2_MOVE_CONSTANT.instance,
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
	public void moveRegister (
		final L2ReadPointerOperand sourceRegister,
		final L2WritePointerOperand destinationRegister)
	{
		addInstruction(L2_MOVE.instance, sourceRegister, destinationRegister);
	}

	/**
	 * Generate code to reify a continuation in a fresh register, then {@link
	 * L2_RETURN} it to the Java code that was requesting reification.
	 *
	 * <p>Use the passed registers to populate the continuation's current slots
	 * and function, but use the state of this translator to determine the rest
	 * of the continuation (e.g., pc, stackp).</p>
	 *
	 * <p>The new continuation's caller will be nil, which will be corrected
	 * when the {@link ReifyStackThrowable} is rethrown all the way to {@link
	 * Interpreter#run()}.</p>
	 *
	 * @param slots
	 *        A {@linkplain List list} containing the {@linkplain
	 *        L2ReadPointerOperand object registers} that correspond to the
	 *        slots of the current continuation.
	 * @param function
	 *        The function that should be captured in the continuation.
	 * @param resumeBlock
	 *        Where the continuation should resume executing when it's asked to
	 *        continue.  Note that if the {@link L2Chunk} was invalidated before
	 *        resumption, the default chunk will be resumed instead.
	 */
	public void reify (
		final L2ReadPointerOperand[] slots,
		final L2ReadPointerOperand function,
		final L2BasicBlock resumeBlock)
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
		final List<L2WritePointerOperand> undefinedWriters =
			new ArrayList<>(slots.length);
		final L2ReadPointerOperand[] undefinedSlots =
			new L2ReadPointerOperand[slots.length];
		for (int i = 0; i < slots.length; i++)
		{
			final TypeRestriction originalRestriction = slots[i].restriction();
			final L2WritePointerOperand slotWriter = newObjectRegisterWriter(
				originalRestriction.type, originalRestriction.constantOrNull);
			writeSlotsOnReturnIntoReified.add(slotWriter);
			readSlotsOnReturnIntoReified[i] = slotWriter.read();

			final L2WritePointerOperand undefinedWriter =
				newObjectRegisterWriter(TOP.o(), null);
			undefinedWriters.add(undefinedWriter);
			undefinedSlots[i] = undefinedWriter.read();
		}
		// Now generate the reification instructions, ensuring that when
		// returning into the resulting continuation it will enter a block where
		// the slot registers are the new ones we just created.
		addInstruction(
			L2_UNDEFINE_REGISTERS.instance,
			writeVector(undefinedWriters));
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			constantRegister(nil),
			function,
			new L2ImmediateOperand(pc),
			new L2ImmediateOperand(stackp),
			getSkipReturnCheck(),
			readVector(asList(slots)),
			newContinuationRegister,
			new L2PcOperand(onReturnIntoReified, undefinedSlots),
			new L2PcOperand(afterCreation, slots));

		// Right after creating the continuation.
		startBlock(afterCreation);
		addInstruction(
			L2_RETURN.instance,
			newContinuationRegister.read(),
			constantIntRegister(1));

		// It's returning into the reified continuation.
		startBlock(onReturnIntoReified);
		addInstruction(
			L2_EXPLODE_CONTINUATION.instance,
			popCurrentContinuation(),
			writeVector(writeSlotsOnReturnIntoReified),
			new L2WriteIntOperand(translator.newIntegerRegister())); //ignored
		addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(resumeBlock, readSlotsOnReturnIntoReified));
	}

	/**
	 * Add an instruction that's not supposed to be reachable.
	 */
	public void addUnreachableCode ()
	{
		addInstruction(L2_JUMP.instance, unreachablePcOperand());
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
	public void forceSlotRegister (
		final int slotIndex,
		final L2ReadPointerOperand register)
	{
		slotRegisters[slotIndex] = register;
	}

	/**
	 * Generate code to extract the current {@link
	 * AvailRuntime#resultDisagreedWithExpectedTypeFunction} into a new
	 * register, which is returned here.
	 *
	 * @return The new register that will hold the invalid return function.
	 */
	public L2ReadPointerOperand getInvalidResultFunctionRegister ()
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
		/**
		 * Where to jump if the {@link InternalLookupTree}'s type test is true.
		 */
		final L2BasicBlock passCheckBasicBlock;

		/**
		 * Where to jump if the {@link InternalLookupTree}'s type test is false.
		 */
		final L2BasicBlock failCheckBasicBlock;

		/**
		 * Construct a new memento.  Make the label something meaningful to
		 * make it easier to decipher.
		 *
		 * @param argumentIndexToTest
		 *        The subscript of the argument being tested.
		 * @param typeToTest
		 *        The type to test the argument against.
		 * @param branchLabelCounter
		 *        An int unique to this dispatch tree, monotonically
		 *        allocated at each branch.
		 */
		@InnerAccess InternalNodeMemento (
			final int argumentIndexToTest,
			final A_Type typeToTest,
			final int branchLabelCounter)
		{
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
		final A_String bundleName = bundle.message().atomName();
		final L2BasicBlock afterCall = createBasicBlock(
			superUnionType.isBottom()
				? "After call of " + bundleName
				: "After super call of " + bundleName);
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
				moveConstant(nil, writeSlot(stackp + i, TOP.o(), nil));
			}
		}
		// Pop the arguments, but push a slot for the expectedType.
		stackp += nArgs - 1;
		// Write the expected type.  Note that even though it's a type, the
		// exact value is known statically, not just its meta.
		moveConstant(
			expectedType,
			writeSlot(stackp, instanceMeta(expectedType), expectedType));

		// At this point we've captured and popped the argument registers,
		// nilled their new SSA versions, and pushed the expectedType.  That's
		// the reifiable state of the continuation during the call.
		final List<A_Definition> allPossible = new ArrayList<>();
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
						bundle, arguments, expectedType, superUnionType);
					return;
				}
			}
		}
		// NOTE: Don't use the method's testing tree.  It encodes information
		// about the known types of arguments that may be too weak for our
		// purposes.  It's still correct, but it may produce extra tests that
		// this site's argumentTypes would eliminate.
		final LookupTree<A_Definition, A_Tuple, Void> tree =
			MethodDescriptor.runtimeDispatcher.createRoot(
				allPossible, argumentTypes, null);
		final Mutable<Integer> branchLabelCounter = new Mutable<>(1);
		// If a reification exception happens while an L2_INVOKE is in progress,
		// it will run the instructions at the off-ramp up to an L2_RETURN –
		// which will return the reified (but callerless) continuation.
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
						branchLabelCounter.value++);
				// If no paths lead here, don't generate code.  This can happen
				// when we short-circuit type-tests into unconditional jumps,
				// due to the complexity of super calls.  We short-circuit code
				// generation within this entire subtree by performing the same
				// test in each callback.
				if (!currentlyReachable())
				{
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
				if (constantOrNull != null)
				{
					// The argument is a constant, so test it now.  Take into
					// account any supercasts.
					final A_Type unionType =
						instanceTypeOrMetaOn(constantOrNull).typeUnion(
							superUnionElementType);
					// Unconditionally jump to either the true or the false
					// path.
					final boolean isSubtype =
						unionType.isSubtypeOf(intersection);
					addInstruction(
						L2_JUMP.instance,
						isSubtype
							? new L2PcOperand(
								memento.passCheckBasicBlock,
								slotRegisters(),
								arg.restrictedTo(intersection, null))
							: new L2PcOperand(
								memento.failCheckBasicBlock,
								slotRegisters(),
								arg.restrictedWithoutType(intersection)));
				}
				else if (existingType.isSubtypeOf(superUnionElementType))
				{
					// It's a pure supercast of this argument, not a mix of some
					// parts being supercast and others not.  Do the test once,
					// right now.
					final boolean passed =
						superUnionElementType.isSubtypeOf(intersection);
					addInstruction(
						L2_JUMP.instance,
						passed
							? new L2PcOperand(
								memento.passCheckBasicBlock,
								slotRegisters())
							: new L2PcOperand(
								memento.failCheckBasicBlock,
								slotRegisters()));
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
					final Iterator<AvailObject> iterator =
						intersection.instances().iterator();
					while (iterator.hasNext())
					{
						final A_BasicObject instance = iterator.next();
						final L2BasicBlock nextCheckOrFail =
							iterator.hasNext()
								? createBasicBlock(
									"test next case of enumeration")
								: memento.failCheckBasicBlock;
						addInstruction(
							L2_JUMP_IF_EQUALS_CONSTANT.instance,
							arg,
							new L2ConstantOperand(instance),
							new L2PcOperand(
								memento.passCheckBasicBlock,
								slotRegisters(),
								arg.restrictedToValue(instance)),
							new L2PcOperand(
								nextCheckOrFail,
								slotRegisters(),
								arg.restrictedWithoutValue(instance)));
						startBlock(nextCheckOrFail);
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
						new L2PcOperand(
							memento.failCheckBasicBlock,
							slotRegisters()),
						new L2PcOperand(
							memento.passCheckBasicBlock,
							slotRegisters()));
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
						new L2PcOperand(
							memento.passCheckBasicBlock,
							slotRegisters()),
						new L2PcOperand(
							memento.failCheckBasicBlock,
							slotRegisters()));
				}
				return memento;
			},
			// intraInternalNode
			memento ->
			{
				if (currentlyReachable())
				{
					addInstruction(
						L2_JUMP.instance,
						new L2PcOperand(afterCall, slotRegisters()));
				}
				startBlock(memento.failCheckBasicBlock);
			},
			// postInternalNode
			memento ->
			{
				if (currentlyReachable())
				{
					addInstruction(
						L2_JUMP.instance,
						new L2PcOperand(afterCall, slotRegisters()));
				}
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
						generateGeneralFunctionInvocation(
							constantRegister(solution.bodyBlock()),
							arguments,
							expectedType,
							solution.bodySignature().returnType().isSubtypeOf(
								expectedType),
							slotRegisters());
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
					readVector(arguments),
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
					slotRegisters());
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
	 * Generate code to invoke a function in a register with arguments in
	 * registers.  In the event of reification, capture the specified array
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
	 * @return The {@link L2ReadPointerOperand} holding the result of the call.
	 */
	public L2ReadPointerOperand generateGeneralFunctionInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final A_Type expectedType,
		final boolean skipReturnCheck,
		final L2ReadPointerOperand[] slotsIfReified)
	{
		final L2BasicBlock onReturn = createBasicBlock("returned from call");
		final L2BasicBlock onReification =
			createBasicBlock("reification during call");
		final @Nullable L2ReadPointerOperand specialOutputReg =
			tryToGenerateSpecialInvocation(functionToCallReg, arguments);
		if (specialOutputReg != null)
		{
			// The call was to a specific primitive function that generated
			// optimized code that left the primitive result in resultReg.
			assert !onReturn.hasPredecessors();
			assert !onReification.hasPredecessors();
			if (skipReturnCheck
				|| specialOutputReg.type().isSubtypeOf(expectedType))
			{
				return specialOutputReg;
			}
			return generateReturnTypeCheck(specialOutputReg, expectedType);
		}

		// The function isn't known to be a particular primitive function, or
		// the primitive wasn't able to special code generation for it, so just
		// invoke it like a non-primitive.
		addInstruction(
			L2_INVOKE.instance,
			functionToCallReg,
			readVector(arguments),
			new L2ImmediateOperand(skipReturnCheck ? 1 : 0),
			new L2PcOperand(onReturn, slotsIfReified),
			new L2PcOperand(onReification, slotsIfReified));

		// Reification has been requested while the call is in progress.
		startBlock(onReification);
		reify(slotsIfReified, getCurrentFunction(), onReturn);

		// This is reached either after a normal return from the invoke or after
		// reification, return into the reified continuation, and the subsequent
		// explode of the continuation into slot registers.
		startBlock(onReturn);
		final A_Type functionType = functionToCallReg.type();
		final A_Type functionReturnType =
			functionType.isInstanceOf(mostGeneralFunctionType())
				? functionType.returnType()
				: TOP.o();
		final L2ReadPointerOperand resultReg =
			getLatestReturnValue(functionReturnType);
		if (skipReturnCheck || resultReg.type().isSubtypeOf(expectedType))
		{
			return resultReg;
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
		// Check the return value against the expectedType.
		final L2BasicBlock passedCheck = createBasicBlock("passed check");
		final L2BasicBlock failedCheck = createBasicBlock("failed check");
		addInstruction(
			L2_JUMP_IF_KIND_OF_CONSTANT.instance,
			valueReg,
			new L2ConstantOperand(expectedType),
			new L2PcOperand(
				passedCheck,
				slotRegisters(),
				valueReg.restrictedTo(expectedType, null)),
			new L2PcOperand(
				failedCheck,
				slotRegisters(),
				valueReg.restrictedTo(TOP.o(), null)));

		// The type check failed, so report it.
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
			new L2PcOperand(wroteVariable, slotRegisters()),
			new L2PcOperand(wroteVariable, slotRegisters()));

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
			slotRegisters());
		addUnreachableCode();

		startBlock(passedCheck);
		final L2WritePointerOperand strongerResultWrite =
			newObjectRegisterWriter(
				expectedType.typeIntersection(valueReg.type()),
				null);
		moveRegister(valueReg, strongerResultWrite);
		return strongerResultWrite.read();
	}

	/**
	 * Attempt to create a more specific instruction sequence than just an
	 * {@link L2_INVOKE}.  In particular, see if the functionToCallReg is known
	 * to contain a constant function (a common case) which is an inlineable
	 * primitive, and if so, delegate this opportunity to the primitive.
	 *
	 * <p>We must either generate no code and answer {@code null}, or generate
	 * code that has the same effect as having run the function in the register
	 * without fear of reification or abnormal control flow.  Constant folding,
	 * for example, can output a simple {@link L2_MOVE_CONSTANT} into a suitable
	 * register answered by this method.</p>
	 *
	 * @param functionToCallReg
	 *        The register containing the function to invoke.
	 * @param arguments
	 *        The arguments to supply to the function.
	 */
	private @Nullable L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments)
	{
		final @Nullable A_Function functionIfKnown =
			(A_Function) functionToCallReg.constantOrNull();
		if (functionIfKnown != null)
		{
			// The exact function is known.
			final @Nullable Primitive primitive =
				functionIfKnown.code().primitive();
			if (primitive != null)
			{
				// It's a primitive function.
				final @Nullable Interpreter interpreter =
					translator.interpreter;
				if (primitive.hasFlag(CanFold) && interpreter != null)
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
						// Fold the primitive.  Save and restore the
						// interpreter's function field, and also load it up
						// with the known function before attempting the
						// primitive.  At least P_PushConstant requires it to
						// have been set up that way.
						final @Nullable A_Function savedFunction =
							interpreter.function;
						interpreter.function = functionIfKnown;
						final Result success = primitive.attempt(
							constants, interpreter, true);
						interpreter.function = savedFunction;
						if (success == SUCCESS)
						{
							return constantRegister(
								interpreter.latestResult().makeImmutable());
						}
						// The primitive failed with the supplied arguments,
						// which it's allowed to do even if it CanFold.
						assert success == FAILURE;
						assert !primitive.hasFlag(CannotFail);
					}
				}

				// The primitive can't be folded, so let it generate its own
				// code equivalent to invocation.
				final List<A_Type> argTypes = arguments.stream()
					.map(L2ReadPointerOperand::type)
					.collect(toList());
				return primitive.tryToGenerateSpecialInvocation(
					functionToCallReg, arguments, argTypes, this);
			}
		}
		return null;
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
	 */
	private void generateSlowPolymorphicCall (
		final A_Bundle bundle,
		final List<L2ReadPointerOperand> arguments,
		final A_Type expectedType,
		final A_Type superUnionType)
	{
		final A_Method method = bundle.bundleMethod();
		final int nArgs = method.numArgs();
		final L2BasicBlock lookupSucceeded =
			createBasicBlock("lookup succeeded");
		final L2BasicBlock lookupFailed =
			createBasicBlock("lookup failed");
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
				readVector(arguments),
				functionReg,
				errorCodeReg,
				new L2PcOperand(
					lookupSucceeded,
					slotRegisters(),
					new PhiRestriction(
						functionReg.register(),
						functionTypeUnion,
						possibleFunctions.size() == 1
							? possibleFunctions.get(0)
							: null)),
				new L2PcOperand(
					lookupFailed,
					slotRegisters(),
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
				readVector(argTypeRegs),
				functionReg,
				errorCodeReg,
				new L2PcOperand(
					lookupSucceeded,
					slotRegisters(),
					new PhiRestriction(
						functionReg.register(),
						functionTypeUnion,
						possibleFunctions.size() == 1
							? possibleFunctions.get(0)
							: null)),
				new L2PcOperand(
					lookupFailed,
					slotRegisters(),
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
		final L2ReadVectorOperand argsVector = readVector(arguments);
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
		final L2BasicBlock onReification =
			createBasicBlock("reification while reporting failed lookup");
		// Ignore result of call, which cannot actually return.
		generateGeneralFunctionInvocation(
			invalidSendReg.read(),
			asList(
				errorCodeReg.read(),
				constantRegister(method),
				argumentsTupleWrite.read()),
			bottom(),
			true,
			slotRegisters());
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
				slotRegisters());
		// Write the (already checked if necessary) result onto the stack.
		moveRegister(
			returnReg,
			writeSlot(stackp, expectedType, null));
	}

	/**
	 * Emit an interrupt {@linkplain
	 * L2_JUMP_IF_INTERRUPT check}-and-{@linkplain L2_PROCESS_INTERRUPT
	 * process} off-ramp. May only be called when the architectural
	 * registers reflect an inter-nybblecode state.
	 *
	 * @param registerSet
	 *        The {@link RegisterSet} to use and update.
	 */
	@InnerAccess void emitInterruptOffRamp (
		final RegisterSet registerSet)
	{
		final L2BasicBlock serviceInterrupt =
			createBasicBlock("service interrupt");
		final L2BasicBlock postInterrupt =
			createBasicBlock("after interrupt");
		final L2BasicBlock merge =
			createBasicBlock("merge after possible interrupt");
		final L2WritePointerOperand reifiedWriter = newObjectRegisterWriter(
			mostGeneralContinuationType(), null);
		addInstruction(
			L2_JUMP_IF_INTERRUPT.instance,
			new L2PcOperand(serviceInterrupt, slotRegisters()),
			new L2PcOperand(merge, slotRegisters()));
		final int nSlots = translator.numSlots;
		final List<L2ReadPointerOperand> slots = continuationSlotsList(nSlots);
		reify(slots, reifiedRegister, postInterruptLabel);
		addInstruction(
			L2_PROCESS_INTERRUPT.instance,
			new L2ReadPointerOperand(reifiedRegister));
		addLabel(postInterruptLabel);
		addInstruction(L2_REENTER_L2_CHUNK.instance);
		A_Map typesMap = emptyMap();
		A_Map constants = emptyMap();
		A_Set nullSlots = emptySet();
		for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
		{
			final A_Type type = savedSlotTypes.get(slotIndex - 1);
			if (type != null)
			{
				typesMap = typesMap.mapAtPuttingCanDestroy(
					fromInt(slotIndex),
					type,
					true);
			}
			final @Nullable A_BasicObject constant =
				savedSlotConstants.get(slotIndex - 1);
			if (constant != null)
			{
				if (constant.equalsNil())
				{
					nullSlots = nullSlots.setWithElementCanDestroy(
						fromInt(slotIndex),
						true);
				}
				else
				{
					constants = constants.mapAtPuttingCanDestroy(
						fromInt(slotIndex),
						constant,
						true);
				}
			}
		}
		addInstruction(
			L2_EXPLODE_CONTINUATION.instance,
			new L2ReadPointerOperand(fixed(CALLER)),
			writeVector(slots),
			new L2WriteIntOperand(skipReturnCheckRegister),
			new L2ConstantOperand(typesMap),
			new L2ConstantOperand(constants),
			new L2ConstantOperand(nullSlots),
			new L2ConstantOperand(code.functionType()));
		addLabel(noInterruptLabel);
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
			new L2PcOperand(success, slotRegisters()),
			new L2PcOperand(failure, slotRegisters()));

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
			slotRegisters());
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
			new L2PcOperand(successBlock, slotRegisters()),
			new L2PcOperand(failureBlock, slotRegisters()));

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
			readVector(asList(variable, newValue)),
			variableAndValueTupleReg);
		generateGeneralFunctionInvocation(
			observeFunction.read(),
			asList(
				constantRegister(Interpreter.assignmentFunction()),
				variableAndValueTupleReg.read()),
			TOP.o(),
			true,
			slotRegisters());
		addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(successBlock, slotRegisters()));

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
	public L2BasicBlock createBasicBlock (final String name)
	{
		return controlFlowGraph.createBasicBlock(name);
	}

	/**
	 * For each level one instruction, write a suitable transliteration into
	 * level two instructions.
	 */
	void addNaiveInstructions ()
	{
		startBlock(initialBlock);
		final @Nullable Primitive primitive = code.primitive();
		if (primitive != null)
		{
			addInstruction(L2_TRY_PRIMITIVE.instance);
			if (primitive.hasFlag(CannotFail))
			{
				return;
			}
			addInstruction(
				L2_JUMP.instance,
				new L2PcOperand(afterOptionalInitialPrimitiveBlock));
			startBlock(afterOptionalInitialPrimitiveBlock);
		}
		if (translator.optimizationLevel == OptimizationLevel.UNOPTIMIZED)
		{
			// Optimize it again if it's called frequently enough.
			code.countdownToReoptimize(
				L2Chunk.countdownForNewlyOptimizedCode());
			addInstruction(
				L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
				new L2ImmediateOperand(
					OptimizationLevel.FIRST_TRANSLATION.ordinal()));
		}
		final List<L2WritePointerOperand> initialRegisters = new ArrayList<>();
		final A_Type argsTupleType = code.functionType().argsTupleType();
		final int numArgs = code.numArgs();
		for (int i = 1; i <= numArgs; i++)
		{
			final L2WritePointerOperand r =
				writeSlot(i, argsTupleType.typeAtIndex(i), null);
			r.register().setFinalIndex(i - 1);
			initialRegisters.add(r);
		}
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			writeVector(initialRegisters));
		for (int local = 1; local <= translator.numLocals; local++)
		{
			final A_Type localType = code.localTypeAt(local);
			addInstruction(
				L2_CREATE_VARIABLE.instance,
				new L2ConstantOperand(localType),
				writeSlot(numArgs + local - 1, localType, null));
		}
		if (primitive != null)
		{
			assert !primitive.hasFlag(CannotFail);
			// Move the primitive failure value into the first local.  This
			// doesn't need to support implicit observation, so no off-ramp
			// is generated.
			final L2BasicBlock success = createBasicBlock("success");
			addInstruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				readSlot(translator.numArgs + 1),
				getPrimitiveFailureValue(),
				new L2PcOperand(success, slotRegisters()),
				unreachablePcOperand());
			startBlock(success);
		}
		// Store nil into each of the stack slots.
		for (
			int stackSlot = 1, end = code.maxStackDepth();
			stackSlot <= end;
			stackSlot++)
		{
			nilSlot(stackSlot);
		}
		// Check for interrupts. If an interrupt is discovered, then reify
		// and process the interrupt. When the chunk resumes, it will
		// explode the continuation again.
		final L2BasicBlock afterInterrupt =
			createBasicBlock("after optional interrupt");
		emitInterruptOffRamp();

		// Transliterate each level one nybblecode into L2Instructions.
		while (pc <= nybbles.tupleSize())
		{
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.all()[nybble].dispatch(this);
		}
		// Translate the implicit L1_doReturn instruction that terminates
		// the instruction sequence.
		L1Operation.L1Implied_Return.dispatch(this);
		assert pc == nybbles.tupleSize() + 1;
		assert stackp == Integer.MIN_VALUE;

		// Write a coda if necessary to support push-label instructions
		// and give the resulting continuations a chance to explode before
		// restarting them.
		if (restartLabelBlock != null)
		{
			startBlock(restartLabelBlock);
			A_Map typesMap = emptyMap();
			final List<L2ObjectRegister> slots = new ArrayList<>(
				translator.numSlots);
			final A_Type argsType = code.functionType().argsTupleType();
			A_Set nullSlots = emptySet();
			for (int i = 1; i <= translator.numSlots; i++)
			{
				slots.add(readSlot(i));
				if (i <= translator.numArgs)
				{
					typesMap = typesMap.mapAtPuttingCanDestroy(
						fromInt(i), argsType.typeAtIndex(i), true);
				}
				else
				{
					nullSlots = nullSlots.setWithElementCanDestroy(
						fromInt(i), true);
				}
			}
			addInstruction(
				L2_EXPLODE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				writeVector(slots),
				new L2WriteIntOperand(skipReturnCheckRegister),
				new L2ConstantOperand(typesMap.makeImmutable()),
				new L2ConstantOperand(emptyMap()),
				new L2ConstantOperand(nullSlots.makeImmutable()),
				new L2ConstantOperand(code.functionType()));
			addInstruction(
				L2_JUMP.instance,
				new L2PcOperand(startLabel, slotRegisters()));
		}
	}

	@Override
	public void L1_doCall ()
	{
		final A_Bundle bundle = code.literalAt(getInteger());
		final A_Type expectedType = code.literalAt(getInteger());
		generateCall(bundle, expectedType, bottom());
	}

	@Override
	public void L1_doPushLiteral ()
	{
		final AvailObject constant = code.literalAt(getInteger());
		stackp--;
		moveConstant(
			constant,
			writeSlot(stackp, instanceTypeOrMetaOn(constant), constant));
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
		final A_Type type = code.outerTypeAt(outerIndex);
		if (type.instanceCount().equalsInt(1) && !type.isInstanceMeta())
		{
			// The exact outer is known statically.
			final AvailObject outerValue = type.instance();
			moveConstant(
				outerValue,
				writeSlot(
					stackp, instanceTypeOrMetaOn(outerValue), outerValue));
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
				writeSlot(stackp, type, null));
			// Simplify the logic related to L1's nilling of mutable outers upon
			// their final use.
			addInstruction(L2_MAKE_IMMUTABLE.instance, readSlot(stackp));
		}
	}

	@Override
	public void L1_doClose ()
	{
		final int count = getInteger();
		final A_RawFunction codeLiteral = code.literalAt(getInteger());
		final List<L2ReadPointerOperand> outers = new ArrayList<>(count);
		for (int i = count; i >= 1; i--)
		{
			outers.add(readSlot(stackp + count - i));
		}
		// Pop the outers, but reserve room for the pushed function.
		stackp += count - 1;
		addInstruction(
			L2_CREATE_FUNCTION.instance,
			new L2ConstantOperand(codeLiteral),
			readVector(outers),
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
		final int localIndex = getInteger();
		stackp--;
		final A_Type type = code.localTypeAt(localIndex);
		emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			readSlot(localIndex),
			writeSlot(stackp, type, null));
	}

	@Override
	public void L1_doPushOuter ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code.outerTypeAt(outerIndex);
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
		final A_Type outerType = code.outerTypeAt(outerIndex);
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
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		final L2WritePointerOperand tempVarReg =
			newObjectRegisterWriter(outerType, null);
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			readSlot(stackp));
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
		final A_Type innerType = code.localTypeAt(index).readType();
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
			final A_Tuple tuple = tupleFromList(constants);
			moveConstant(tuple, writeSlot(stackp, instanceType(tuple), tuple));
		}
		else
		{
			final A_Type tupleType = tupleTypeForTypes(
				vector.stream()
					.map(L2ReadPointerOperand::type)
					.toArray(A_Type[]::new));
			addInstruction(
				L2_CREATE_TUPLE.instance,
				readVector(vector),
				writeSlot(stackp, tupleType, null));
		}
		// Clean up the stack slots.  That's all but the first slot,
		// which was just replaced by the tuple.
		for (int i = 2; i <= count; i++)
		{
			nilSlot(stackp + count - i);
		}
	}

	@Override
	public void L1_doGetOuter ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code.outerTypeAt(outerIndex);
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
		final int numSlots = code.numArgsAndLocalsAndStack();
		final int numArgs = code.numArgs();
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
			continuationTypeForFunctionType(code.functionType());
		final L2WritePointerOperand destReg =
			writeSlot(stackp, continuationType, null);
		if (restartLabelBlock == null)
		{
			restartLabelBlock = createBasicBlock("restart on-ramp");
		}
		final L2ReadPointerOperand functionRead = getCurrentFunction();
		final L2ReadIntOperand skipReturnCheckRead = getSkipReturnCheck();
		addInstruction(
			L2_REIFY_CALLERS.instance,
			new L2ImmediateOperand(1));
		final L2ReadPointerOperand continuationRead = getCurrentContinuation();
		final L2BasicBlock afterCreation =
			createBasicBlock("after push label");
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			continuationRead,
			functionRead,
			new L2ImmediateOperand(0),
			new L2ImmediateOperand(numSlots + 1),
			skipReturnCheckRead,
			readVector(vectorWithOnlyArgsPreserved),
			destReg,
			new L2PcOperand(restartLabelBlock, slotRegisters()),
			new L2PcOperand(afterCreation, slotRegisters()));
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
		final A_Variable literalVariable = code.literalAt(getInteger());
		stackp--;
		if (literalVariable.isInitializedWriteOnceVariable())
		{
			// It's an initialized module constant, so it can never change.  Use
			// the variable's eternal value.
			final A_BasicObject value = literalVariable.value();
			moveConstant(
				value,
				writeSlot(stackp, instanceTypeOrMetaOn(value), value));
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
		final A_Variable literalVariable = code.literalAt(getInteger());
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
		final A_Tuple permutation = code.literalAt(getInteger());
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
		final AvailObject method = code.literalAt(getInteger());
		final AvailObject expectedType = code.literalAt(getInteger());
		final AvailObject superUnionType = code.literalAt(getInteger());
		generateCall(method, expectedType, superUnionType);
	}

	@Override
	public void L1Ext_doReserved ()
	{
		// This shouldn't happen unless the compiler is out of sync with the
		// translator.
		error("That nybblecode is not supported");
	}

	@Override
	public void L1Implied_doReturn ()
	{
		final L2ReadIntOperand skipReturnCheckRead = getSkipReturnCheck();
		addInstruction(
			L2_RETURN.instance,
			readSlot(stackp),
			skipReturnCheckRead);
		assert stackp == code.maxStackDepth();
		stackp = Integer.MIN_VALUE;
	}
}
