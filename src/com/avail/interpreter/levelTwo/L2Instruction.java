/**
 * L2Instruction.java
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

package com.avail.interpreter.levelTwo;

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.utility.evaluation.Transformer1NotNullArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;

/**
 * {@code L2Instruction} is the foundation for all instructions understood by
 * the {@linkplain Interpreter level two Avail interpreter}. These
 * instructions are model objects generated and manipulated by the {@linkplain
 * L2Translator translator}.
 *
 * <p>It used to be the case that the instructions were flattened into a stream
 * of integers, operation followed by operands.  That is no longer the case, as
 * of 2013-05-01 [MvG].  Instead, the L2Instructions themselves are kept around.
 * To execute an L2Instruction, its L2Operation is extracted and asked to
 * {@linkplain L2Operation#step(L2Instruction, Interpreter) step}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2Instruction
{
	/**
	 * The {@link L2Operation} whose execution this instruction represents.
	 */
	public final L2Operation operation;

	/**
	 * The {@link L2Operand}s to supply to the operation.
	 */
	public final L2Operand[] operands;

	/**
	 * The position of this instruction within its array of instructions.
	 * Only valid near the end of translation.
	 */
	private int offset = -1;

	public final L2BasicBlock basicBlock;

	@InnerAccess final List<L2Register> sourceRegisters = new ArrayList<>();

	@InnerAccess final List<L2Register> destinationRegisters = new ArrayList<>();

	/**
	 * Answer the position of this instruction within its array of instructions.
	 *
	 * @return The position of the instruction in its chunk's instruction array.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * Set the final position of this instruction within its {@link L2Chunk}'s
	 * array of instructions.
	 *
	 * @param offset
	 *        The final position of the instruction within the array.
	 */
	public void setOffset (final int offset)
	{
		this.offset = offset;
	}

	/**
	 * Each {@link L2Operation} is responsible for generating this {@link
	 * Transformer1NotNullArg} as an action to directly invoke to accomplish the
	 * effect of this instruction.  This interim measure helps alleviate some of
	 * the runtime instruction decoding cost, until we're able to generate JVM
	 * instructions directly.
	 *
	 * <p>Note that we have to wait until code generation of an {@link L2Chunk}
	 * has completed before being able to set this action meaningfully, since
	 * the point of it is to cache register numbering and target offsets.</p>
	 */
	public Transformer1NotNullArg<Interpreter, StackReifier> action;

	/**
	 * Construct a new {@code L2Instruction}.
	 *
	 * @param operation
	 *        The {@link L2Operation} that this instruction performs.
	 * @param operands
	 *        The array of {@link L2Operand}s on which this instruction
	 *        operates.  These must agree with the operation's array of {@link
	 *        L2OperandType}s.
	 * @param basicBlock
	 *        The {@link L2BasicBlock} which will contain this instruction.
	 */
	public L2Instruction (
		final L2BasicBlock basicBlock,
		final L2Operation operation,
		final L2Operand... operands)
	{
		final L2NamedOperandType[] operandTypes =
			stripNull(operation.namedOperandTypes);
		assert operandTypes.length == operands.length;
		for (int i = 0; i < operands.length; i++)
		{
			assert operands[i].operandType() == operandTypes[i].operandType();
		}
		this.operation = operation;
		this.operands = new L2Operand[operands.length];
		this.basicBlock = basicBlock;
		for (int i = 0; i < operands.length; i++)
		{
			final L2Operand operand = operands[i].clone();
			this.operands[i] = operand;
			operand.addSourceRegistersTo(sourceRegisters);
			operand.addDestinationRegistersTo(destinationRegisters);
		}
		//noinspection ThisEscapedInObjectConstruction
		action = uninitializedAction;
	}

	private static final Transformer1NotNullArg<Interpreter, StackReifier>
		uninitializedAction = interpreter ->
		{
			assert false : "Instruction has not had its action initialized yet";
			return null;
		};

	/**
	 * Now that this instruction has been generated in an {@link L2Chunk}, set
	 * up its action.
	 */
	public void setAction ()
	{
		action = operation.actionFor(this);
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain L2Register registers}
	 * read by this {@code L2Instruction instruction}.
	 *
	 * @return The source {@linkplain L2Register registers}.
	 */
	public List<L2Register> sourceRegisters ()
	{
		return sourceRegisters;
	}

	/**
	 * Answer the {@link List list} of {@link L2Register} modified by this
	 * {@code L2Instruction instruction}.
	 *
	 * @return The source {@linkplain L2Register}s.
	 */
	public List<L2Register> destinationRegisters ()
	{
		return destinationRegisters;
	}

	/**
	 * Answer all possible {@link L2PcOperand}s of this instruction.  These
	 * edges lead to other {@link L2BasicBlock}s, and carry both the basic slot
	 * register information and additional {@link PhiRestriction}s for other
	 * registers that are strengthened along that edge.
	 *
	 * <p>This is empty for instructions that don't alter control flow and just
	 * fall through to the next instruction of the same basic block.</p>
	 *
	 * @return A {@link List} of {@link L2PcOperand}s leading to the successor
	 *         {@link L2BasicBlock}s.
	 */
	public List<L2PcOperand> targetEdges ()
	{
		return operation.targetEdges(this);
	}

	/**
	 * Answer whether this instruction can alter control flow.  That's true for
	 * any kind of instruction that has more than one successor (e.g., a branch)
	 * or no successors at all (e.g., a return).
	 *
	 * <p>An instruction for which this is true must occur at the end of each
	 * {@link L2BasicBlock}, but never before the end.</p>
	 *
	 * @return Whether this instruction can do something other than fall through
	 *         to the next instruction of its basic block.
	 */
	public boolean altersControlFlow ()
	{
		return operation.altersControlFlow;
	}

	/**
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return Whether this instruction has side effects.
	 */
	public boolean hasSideEffect ()
	{
		return operation.hasSideEffect(this);
	}

	/**
	 * Propagate {@linkplain TypeDescriptor type} and constant value information
	 * from source {@link L2Register}s to destination registers within the
	 * provided {@link RegisterSet}s.  There is one RegisterSet for each target
	 * L2Instruction, including the instruction that follows this one.  They
	 * occur in the same order as the {@link #targetEdges()}, with the
	 * successor instruction's RegisterSet prepended if it {@link
	 * L2Operation#reachesNextInstruction()}.
	 *
	 * @param registerSets
	 *            A list of RegisterSets in the above-specified order.
	 * @param translator
	 *            The L2Translator on behalf of which to propagate types.
	 */
	public void propagateTypes (
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final int count = (operation.reachesNextInstruction() ? 1 : 0)
			+ targetEdges().size();
		assert registerSets.size() == count;
		if (count == 1)
		{
			operation.propagateTypes(
				this, registerSets.get(0), translator);
		}
		else
		{
			operation.propagateTypes(
				this, registerSets, translator);
		}
	}

	/**
	 * Replace all registers in this instruction using the registerRemap.  If a
	 * register is not present as a key of that map, leave it alone.  Do not
	 * assume SSA form.
	 *
	 * @param registerRemap
	 *        A mapping from existing {@link L2Register}s to replacement {@link
	 *        L2Register}s having the same {@link L2Register#registerKind()}.
	 */
	public void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap)
	{
		final List<L2Register> sourcesBefore = new ArrayList<>(sourceRegisters);
		final List<L2Register> destinationsBefore =
			new ArrayList<>(destinationRegisters);
		for (final L2Operand operand : operands)
		{
			operand.replaceRegisters(registerRemap, this);
		}
		sourceRegisters.replaceAll(r -> registerRemap.getOrDefault(r, r));
		destinationRegisters.replaceAll(r -> registerRemap.getOrDefault(r, r));
		assert sourceRegisters.size() == sourcesBefore.size();
		assert destinationRegisters.size() == destinationsBefore.size();
	}


	/**
	 * This instruction was just added to its {@link L2BasicBlock}.
	 */
	public void justAdded ()
	{
		for (final L2Operand operand : operands)
		{
			operand.instructionWasAdded(this);
		}
	}

	/**
	 * This instruction was just removed from its {@link L2BasicBlock}.
	 */
	public void justRemoved ()
	{
		for (final L2Operand operand : operands)
		{
			operand.instructionWasRemoved(this);
		}
	}

	/**
	 * Answer whether this instruction should be emitted during final code
	 * generation (from the non-SSA {@link L2ControlFlowGraph} into a flat
	 * sequence of {@code L2Instruction}s.  Allow the operation to decide.
	 *
	 * @return Whether to preserve this instruction during final code
	 *         generation.
	 */
	public boolean shouldEmit ()
	{
		return operation.shouldEmit(this);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(offset);
		builder.append(". ");
		builder.append(operation.name());
		final L2NamedOperandType[] types = operation.operandTypes();
		assert operands.length == types.length;
		for (int i = 0; i < operands.length; i++)
		{
			builder.append(i == 0 ? ":\n\t" : ",\n\t");
			assert operands[i].operandType() == types[i].operandType();
			builder.append(types[i].name());
			builder.append(" = ");
			builder.append(increaseIndentation(operands[i].toString(), 1));
		}
		return builder.toString();
	}

	/**
	 * Extract the constant {@link AvailObject} from the {@link
	 * L2ConstantOperand} having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds the constant.
	 * @return The constant value.
	 */
	public AvailObject constantAt (final int operandIndex)
	{
		return ((L2ConstantOperand) operands[operandIndex]).object;
	}

	/**
	 * Extract the {@link A_Bundle} from the {@link L2SelectorOperand} having
	 * the specified position in my array of operands.  Should only be used if
	 * it's known that the constant is in fact an {@link A_Bundle}, and the
	 * resulting {@link L2Chunk} should be dependent upon changes to its {@link
	 * A_Method}.
	 *
	 * @param operandIndex Which operand holds the message bundle.
	 * @return The message bundle.
	 */
	public A_Bundle bundleAt (final int operandIndex)
	{
		return ((L2SelectorOperand) operands[operandIndex]).bundle;
	}

	/**
	 * Extract the immediate {@code int} from the {@link L2ImmediateOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds the immediate value.
	 * @return The immediate value.
	 */
	public int immediateAt (final int operandIndex)
	{
		return ((L2ImmediateOperand) operands[operandIndex]).value;
	}

	/**
	 * Extract the {@link L2PcOperand} having the specified position in my array
	 * of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds the {@link L2PcOperand}.
	 * @return The {@link L2PcOperand} representing the destination {@link
	 *         L2BasicBlock} that will be reached if this branch direction is
	 *         taken.
	 */
	public L2PcOperand pcAt (final int operandIndex)
	{
		return ((L2PcOperand) operands[operandIndex]);
	}

	/**
	 * Extract the program counter {@code int} from the {@link L2PcOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds the program counter value.
	 * @return An int representing a target offset into a chunk's instructions.
	 */
	public int pcOffsetAt (final int operandIndex)
	{
		return pcAt(operandIndex).targetBlock().offset();
	}

	/**
	 * Extract the {@link Primitive} from the {@link L2PrimitiveOperand} having
	 * the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds a primitive.
	 * @return The specified {@link Primitive}.
	 */
	public Primitive primitiveAt (final int operandIndex)
	{
		return ((L2PrimitiveOperand) operands[operandIndex]).primitive;
	}

	/**
	 * Extract the {@link L2ReadIntOperand} having the specified position in my
	 * array of operands.
	 *
	 * @param operandIndex Which operand holds a read of an integer register.
	 * @return The specified {@link L2ReadIntOperand} to read.
	 */
	public L2ReadIntOperand readIntRegisterAt (final int operandIndex)
	{
		return (L2ReadIntOperand) operands[operandIndex];
	}

	/**
	 * Extract the {@link L2WriteIntOperand} having the specified position in my
	 * array of operands.
	 *
	 * @param operandIndex Which operand holds a write of an integer register.
	 * @return The specified {@link L2WriteIntOperand} to write.
	 */
	public L2WriteIntOperand writeIntRegisterAt (final int operandIndex)
	{
		return (L2WriteIntOperand) operands[operandIndex];
	}

	/**
	 * Extract the {@link L2ReadPointerOperand} having the specified position in
	 * my array of operands.
	 *
	 * @param operandIndex Which operand holds a read of an object register.
	 * @return The specified {@link L2ObjectRegister} to read.
	 */
	public L2ReadPointerOperand readObjectRegisterAt (final int operandIndex)
	{
		return (L2ReadPointerOperand) operands[operandIndex];
	}

	/**
	 * Extract the {@link L2WritePointerOperand} having the specified position
	 * in my array of operands.
	 *
	 * @param operandIndex Which operand holds a write of an object register.
	 * @return The specified {@link L2ObjectRegister} to write.
	 */
	public L2WritePointerOperand writeObjectRegisterAt (final int operandIndex)
	{
		return (L2WritePointerOperand) operands[operandIndex];
	}

	/**
	 * Extract the {@link List} of {@link L2ReadPointerOperand}s from the {@link
	 * L2ReadVectorOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param operandIndex Which operand holds a read of a register vector.
	 * @return The list of {@link L2ReadPointerOperand}s.
	 */
	public List<L2ReadPointerOperand> readVectorRegisterAt (
		final int operandIndex)
	{
		return ((L2ReadVectorOperand) operands[operandIndex]).elements();
	}

	/**
	 * Extract the {@link List} of {@link L2WritePointerOperand}s from the
	 * {@link L2WriteVectorOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param operandIndex Which operand holds a write of a register vector.
	 * @return The list of {@link L2WritePointerOperand}s.
	 */
	public List<L2WritePointerOperand> writeVectorRegisterAt (
		final int operandIndex)
	{
		return ((L2WriteVectorOperand) operands[operandIndex]).elements();
	}
}
