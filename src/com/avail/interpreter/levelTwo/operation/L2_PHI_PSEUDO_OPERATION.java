/*
 * L2_PHI_PSEUDO_OPERATION.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.utility.Casts.cast;

/**
 * The {@code L2_PHI_PSEUDO_OPERATION} occurs at the start of a {@link
 * L2BasicBlock}.  It's a convenient fiction that allows an {@link
 * L2ControlFlowGraph} to be in Static Single Assignment form (SSA), where each
 * {@link L2Register} has exactly one instruction that writes to it.
 *
 * <p>The vector of source registers are in the same order as the corresponding
 * predecessors of the containing {@link L2BasicBlock}.  The runtime effect
 * would be to select from that vector, based on the predecessor from which
 * control arrives, and move that register's value to the destination register.
 * However, that's a fiction, and the phi operation is instead removed during
 * the transition of the control flow graph out of SSA, being replaced by move
 * instructions along each incoming edge.</p>
 *
 * @param <R> The kind of {@link L2Register} to merge.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_PHI_PSEUDO_OPERATION <R extends L2Register>
extends L2Operation
{
	/**
	 * Construct an {@code L2_PHI_PSEUDO_OPERATION}.
	 *
	 * @param moveOperation
	 *        The {@link L2_MOVE} operation to substitute for this instruction
	 *        on incoming split edges.
	 * @param theNamedOperandTypes
	 *        An array of {@link L2NamedOperandType}s that describe this
	 *        particular L2Operation, allowing it to be specialized by register
	 *        type.
	 */
	private L2_PHI_PSEUDO_OPERATION (
		final L2_MOVE<R> moveOperation,
		final L2NamedOperandType... theNamedOperandTypes)
	{
		super(theNamedOperandTypes);
		this.moveOperation = moveOperation;
	}

	/**
	 * The {@link L2_MOVE} operation to substitute for this instruction on
	 * incoming split edges.
	 */
	public final L2_MOVE<R> moveOperation;

	/**
	 * Initialize the instance used for merging boxed values.
	 */
	public static final L2_PHI_PSEUDO_OPERATION<L2BoxedRegister> boxed =
		new L2_PHI_PSEUDO_OPERATION<>(
			L2_MOVE.boxed,
			READ_BOXED_VECTOR.is("potential boxed sources"),
			WRITE_BOXED.is("boxed destination"));

	/**
	 * Initialize the instance used for merging boxed values.
	 */
	public static final L2_PHI_PSEUDO_OPERATION<L2IntRegister> unboxedInt =
		new L2_PHI_PSEUDO_OPERATION<>(
			L2_MOVE.unboxedInt,
			READ_INT_VECTOR.is("potential int sources"),
			WRITE_INT.is("int destination"));

	/**
	 * Initialize the instance used for merging boxed values.
	 */
	public static final L2_PHI_PSEUDO_OPERATION<L2FloatRegister> unboxedFloat =
		new L2_PHI_PSEUDO_OPERATION<>(
			L2_MOVE.unboxedFloat,
			READ_BOXED_VECTOR.is("potential float sources"),
			WRITE_FLOAT.is("float destination"));

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final List<? extends L2ReadOperand<?>> inputRegs =
			instruction.readVectorRegisterAt(0);
		final L2WriteOperand<?> destinationReg = cast(instruction.operand(1));

		final Iterator<? extends L2ReadOperand<?>> iterator =
			inputRegs.iterator();
		assert iterator.hasNext();
		TypeRestriction restriction = iterator.next().restriction();
		while (iterator.hasNext())
		{
			restriction = restriction.union(iterator.next().restriction());
		}

		registerSet.removeConstantAt(destinationReg.register());
		registerSet.removeTypeAt(destinationReg.register());
		registerSet.typeAtPut(
			destinationReg.register(), restriction.type, instruction);
		if (restriction.constantOrNull != null)
		{
			registerSet.constantAtPut(
				destinationReg.register(),
				restriction.constantOrNull,
				instruction);
		}
	}

	@Override
	public boolean isPhi ()
	{
		return true;
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		// The reads in the input vector are from the positionally corresponding
		// incoming edges, which carry the manifests that should be used to
		// look up the best source semantic values.
		final List<? extends L2ReadOperand<?>> oldVector =
			instruction.readVectorRegisterAt(0);
		final L2WriteOperand<?> destinationReg = cast(instruction.operand(1));

		final List<L2PcOperand> predecessorEdges =
			instruction.basicBlock.predecessorEdgesCopy();
		final int fanIn = oldVector.size();
		assert fanIn == predecessorEdges.size();
		for (int i = 0; i < fanIn; i++)
		{
			// The read operand should use the corresponding incoming manifest.
			oldVector.get(i).instructionWasAdded(
				instruction,
				predecessorEdges.get(i).manifest());
		}
		destinationReg.instructionWasAdded(instruction, manifest);
	}

	@Override
	public boolean shouldEmit (
		final L2Instruction instruction)
	{
		// Phi instructions are converted to moves along predecessor edges.
		return false;
	}

	/**
	 * One of this phi function's predecessors has been removed because it's
	 * dead code.  Clean up its vector of inputs by removing the specified
	 * index.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} whose operation has this type.
	 * @param inputIndex
	 *        The index to remove.
	 */
	public static <
		RR extends L2ReadOperand<R>,
		R extends L2Register>
	L2Instruction withoutIndex (
		final L2Instruction instruction,
		final int inputIndex)
	{
		final L2_PHI_PSEUDO_OPERATION<R> operation =
			cast(instruction.operation());
		final L2ReadVectorOperand<RR, R> oldVector =
			cast(instruction.operand(0));
		final L2WriteOperand<R> destinationReg = cast(instruction.operand(1));

		final List<RR> newSources = new ArrayList<>(oldVector.elements());
		newSources.remove(inputIndex);

		final boolean onlyOneRegister = new HashSet<>(newSources).size() == 1;
		if (onlyOneRegister)
		{
			// Replace the phi function with a simple move.
			return new L2Instruction(
				instruction.basicBlock,
				operation.moveOperation,
				newSources.get(0),
				destinationReg);
		}
		return new L2Instruction(
			instruction.basicBlock,
			instruction.operation(),
			oldVector.clone(newSources),
			destinationReg);
	}

	/**
	 * Examine the instruction and answer the predecessor {@link L2BasicBlock}s
	 * that supply a value from the specified register.
	 *
	 * @param instruction
	 *        The phi-instruction to examine.
	 * @param usedRegister
	 *        The {@link L2Register} whose use we're trying to trace back to its
	 *        definition.
	 * @return A {@link List} of predecessor blocks that supplied the
	 *         usedRegister as an input to this phi operation.
	 */
	public static <
		RR extends L2ReadOperand<R>,
		R extends L2Register>
	List<L2BasicBlock> predecessorBlocksForUseOf (
		final L2Instruction instruction,
		final L2Register usedRegister)
	{
		assert instruction.operation() instanceof L2_PHI_PSEUDO_OPERATION;

		final List<RR> sources = instruction.readVectorRegisterAt(0);
		assert sources.size() == instruction.basicBlock.predecessorEdgesCount();
		final Iterator<L2PcOperand> predecessorEdgesIterator =
			instruction.basicBlock.predecessorEdgesIterator();
		final List<L2BasicBlock> list = new ArrayList<>();
		int i = 0;
		while (predecessorEdgesIterator.hasNext())
		{
			if (sources.get(i).register() == usedRegister)
			{
				list.add(predecessorEdgesIterator.next().sourceBlock());
			}
			i++;
		}
		return list;
	}

	/**
	 * Answer the {@link L2WriteOperand} from this phi function.  This should
	 * only be used when generating phi moves (which takes the {@link
	 * L2ControlFlowGraph} out of Static Single Assignment form).
	 *
	 * @param <W>
	 *        The type of the {@link L2WriteOperand}.
	 * @param <R>
	 *        The type of {@link L2Register} being written.
	 * @param instruction
	 *        The instruction to examine.  It must be a phi operation.
	 * @return The instruction's destination {@link L2WriteOperand}.
	 */
	public static <W extends L2WriteOperand<R>, R extends L2Register>
	W destinationRegisterWrite (final L2Instruction instruction)
	{
		assert instruction.operation() instanceof L2_PHI_PSEUDO_OPERATION;
		return cast(instruction.operand(1));
	}

	/**
	 * Answer the {@link List} of {@link L2ReadBoxedOperand}s for this phi
	 * function.  The order is correlated to the instruction's blocks
	 * predecessorEdges.
	 *
	 * @param instruction
	 *        The phi instruction.
	 * @return The instruction's list of sources.
	 */
	public static <
		RR extends L2ReadOperand<R>,
		R extends L2Register>
	List<RR> sourceRegisterReads (
		final L2Instruction instruction)
	{
		return instruction.readVectorRegisterAt(0);
	}

	@Override
	public String toString ()
	{
		final String kind =
			(this == boxed)
				? "boxed"
				: (this == unboxedInt)
					? "int"
					: (this == unboxedFloat)
						? "float"
						: "unknown";
		return super.toString() + "(" + kind + ")";
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2Operand vector = instruction.operand(0);
		final L2Operand target = instruction.operand(1);
		builder.append("ϕ ");
		builder.append(target);
		builder.append(" ← ");
		builder.append(vector);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		throw new UnsupportedOperationException(
			"This instruction should be factored out before JVM translation");
	}
}
