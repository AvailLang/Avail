/*
 * L2Instruction.java
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

package com.avail.interpreter.levelTwo;

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.reoptimizer.L2Inliner;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.avail.utility.Casts.cast;

/**
 * {@code L2Instruction} is the foundation for all instructions understood by
 * the {@linkplain Interpreter level two Avail interpreter}. These instructions
 * are model objects generated and manipulated by the {@link L2Generator}.
 *
 * <p>It used to be the case that the instructions were flattened into a stream
 * of integers, operation followed by operands.  That is no longer the case, as
 * of 2013-05-01 [MvG].  Instead, the L2Instructions themselves are kept around
 * for reoptimization and {@linkplain #translateToJVM(JVMTranslator,
 * MethodVisitor) JVM code generation}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2Instruction
{
	/**
	 * The {@link L2Operation} whose execution this instruction represents.
	 */
	private final L2Operation operation;

	/**
	 * The {@link L2Operand}s to supply to the operation.
	 */
	private final L2Operand[] operands;

	/**
	 * The position of this instruction within its array of instructions.
	 * Only valid near the end of translation.
	 */
	private int offset = -1;

	/**
	 * The {@link L2BasicBlock} to which the instruction belongs.
	 */
	public final L2BasicBlock basicBlock;

	/**
	 * The source {@link L2Register}s.
	 */
	final List<L2Register> sourceRegisters = new ArrayList<>();

	/**
	 * The destination {@link L2Register}s.
	 */
	final List<L2Register> destinationRegisters =
		new ArrayList<>();

	/**
	 * The {@link L2Operation} whose execution this instruction represents.
	 *
	 * @return The instruction's {@link L2Operation}.
	 */
	public L2Operation operation ()
	{
		return operation;
	}

	/**
	 * Answer the {@link L2Operand}s to supply to the operation.
	 *
	 * @return The instruction's array of {@link L2Operand}s.
	 */
	public L2Operand[] operands ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return operands;
	}

	/**
	 * Evaluate the given function with each operand.
	 *
	 * @param consumer The {@link Consumer} to evaluate.
	 */
	public void operandsDo (final Consumer<L2Operand> consumer)
	{
		for (final L2Operand operand : operands)
		{
			consumer.accept(operand);
		}
	}

	/**
	 * Answer the Nth {@link L2Operand} to supply to the operation.
	 *
	 * @param index
	 *        The zero-based operand index.
	 * @param <O>
	 *        The specialization of {@link L2Operand} to return.
	 * @return The specified operand.
	 */
	public <O extends L2Operand>
	O operand (final int index)
	{
		return cast(operands[index]);
	}

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
	 * Construct a new {@code L2Instruction}.  The instruction will be added
	 * somewhere within the given {@link L2Generator}'s current {@link
	 * L2BasicBlock}.
	 *
	 * @param generator
	 *        The {@link L2Generator} in which this instruction is being
	 *        regenerated.
	 * @param operation
	 *        The {@link L2Operation} that this instruction performs.
	 * @param theOperands
	 *        The array of {@link L2Operand}s on which this instruction
	 *        operates.  These must agree with the operation's array of {@link
	 *        L2NamedOperandType}s.  The operation is given an opportunity to
	 *        augment this array, just as it may be given the opportunity to
	 *        augment the named operand types.
	 */
	public L2Instruction (
		final L2Generator generator,
		final L2Operation operation,
		final L2Operand... theOperands)
	{
		this(generator.currentBlock(), operation, theOperands);
	}

	/**
	 * Construct a new {@code L2Instruction}.
	 *
	 * @param operation
	 *        The {@link L2Operation} that this instruction performs.
	 * @param theOperands
	 *        The array of {@link L2Operand}s on which this instruction
	 *        operates.  These must agree with the operation's array of {@link
	 *        L2NamedOperandType}s.  The operation is given an opportunity to
	 *        augment this array, just as it may be given the opportunity to
	 *        augment the named operand types.
	 * @param basicBlock
	 *        The {@link L2BasicBlock} which will contain this instruction.
	 */
	public L2Instruction (
		final L2BasicBlock basicBlock,
		final L2Operation operation,
		final L2Operand... theOperands)
	{
		final L2Operand[] augmentedOperands = operation.augment(theOperands);
		final L2NamedOperandType[] operandTypes = operation.namedOperandTypes;
		assert operandTypes.length == augmentedOperands.length;
		for (int i = 0; i < augmentedOperands.length; i++)
		{
			assert augmentedOperands[i].operandType()
				== operandTypes[i].operandType();
		}
		this.operation = operation;
		this.operands = new L2Operand[augmentedOperands.length];
		this.basicBlock = basicBlock;
		for (int i = 0; i < operands.length; i++)
		{
			final L2Operand operand = augmentedOperands[i].clone();
			this.operands[i] = operand;
			operand.addSourceRegistersTo(sourceRegisters);
			operand.addDestinationRegistersTo(destinationRegisters);
		}
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain L2Register registers}
	 * read by this {@code L2Instruction instruction}.
	 *
	 * @return The source {@linkplain L2Register registers}.
	 */
	public List<L2Register> sourceRegisters ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
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
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return destinationRegisters;
	}

	/**
	 * Answer all possible {@link L2PcOperand}s within this instruction.  These
	 * edges lead to other {@link L2BasicBlock}s, and carry a {@link
	 * L2ValueManifest}.
	 *
	 * <p>This is empty for instructions that don't alter control flow and just
	 * fall through to the next instruction of the same basic block.</p>
	 *
	 * @return A {@link List} of {@link L2PcOperand}s leading to the successor
	 *         {@link L2BasicBlock}s.
	 */
	public List<L2PcOperand> targetEdges ()
	{
		return operation().targetEdges(this);
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
		return operation().altersControlFlow();
	}

	/**
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return Whether this instruction has side effects.
	 */
	public boolean hasSideEffect ()
	{
		return operation().hasSideEffect(this);
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
		operandsDo(operand -> operand.replaceRegisters(registerRemap, this));
		sourceRegisters.replaceAll(r -> registerRemap.getOrDefault(r, r));
		destinationRegisters.replaceAll(r -> registerRemap.getOrDefault(r, r));
		assert sourceRegisters.size() == sourcesBefore.size();
		assert destinationRegisters.size() == destinationsBefore.size();
	}

	/**
	 * This instruction was just added to its {@link L2BasicBlock}.
	 *
	 * @param manifest
	 *        The {@link L2ValueManifest} that is active where this instruction
	 *        wos just added to its {@link L2BasicBlock}.
	 */
	public void justAdded (final L2ValueManifest manifest)
	{
		operation().instructionWasAdded(this, manifest);
	}

	/**
	 * This instruction was just added to its {@link L2BasicBlock} as part of an
	 * optimization pass.
	 *
	 * @param manifest
	 *        The {@link L2ValueManifest} that is active where this instruction
	 *        wos just added to its {@link L2BasicBlock}.
	 */
	public void justInserted (final L2ValueManifest manifest)
	{
		operation().instructionWasInserted(this, manifest);
	}

	/**
	 * This instruction was just removed from its {@link L2BasicBlock}.
	 */
	public void justRemoved ()
	{
		operandsDo(operand -> operand.instructionWasRemoved(this));
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
		return operation().shouldEmit(this);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		operation().toString(
			this, EnumSet.allOf(L2OperandType.class), builder);
		return builder.toString();
	}

	/**
	 * Transform this instruction's operands for the given {@link
	 * L2Inliner}.
	 *
	 * @param inliner
	 *        The {@link L2Inliner} through which to write this
	 *        instruction's equivalent effect.
	 * @return The array of transformed {@link L2Operand}s.
	 */
	public L2Operand[] transformOperands (final L2Inliner inliner)
	{
		final L2Operand[] newOperands = new L2Operand[operands().length];
		for (int i = 0; i < newOperands.length; i++)
		{
			newOperands[i] = inliner.transformOperand(operand(i));
		}
		return newOperands;
	}

	/**
	 * Write the equivalent of this instruction through the given {@link
	 * L2Inliner}.  Certain types of {@link L2Operation}s are transformed
	 * in ways specific to inlining.
	 *
	 * @param inliner
	 *        The {@link L2Inliner} through which to write this
	 *        instruction's equivalent effect.
	 */
	public void transformAndEmitOn (final L2Inliner inliner)
	{
		operation().emitTransformedInstruction(
			this, transformOperands(inliner), inliner);
	}

	/**
	 * Translate the {@code L2Instruction} into corresponding JVM instructions.
	 *
	 * @param translator
	 *        The {@link JVMTranslator} responsible for the translation.
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 */
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method)
	{
		operation().translateToJVM(translator, method, this);
	}
}
