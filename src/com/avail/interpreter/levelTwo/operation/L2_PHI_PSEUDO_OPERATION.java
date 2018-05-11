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

import com.avail.descriptor.A_BasicObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_PHI;

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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_PHI_PSEUDO_OPERATION
extends L2Operation
{
	/**
	 * Construct an {@code L2_PHI_PSEUDO_OPERATION}.
	 */
	private L2_PHI_PSEUDO_OPERATION ()
	{
		super(
			READ_VECTOR.is("potential sources"),
			WRITE_PHI.is("destination"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_PHI_PSEUDO_OPERATION instance =
		new L2_PHI_PSEUDO_OPERATION();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final List<? extends L2ReadOperand<?, A_BasicObject>> inputRegs =
			instruction.readVectorRegisterAt(0);
		final L2WritePhiOperand<?, ?> destinationReg =
			instruction.writePhiRegisterAt(1);

		final Iterator<? extends L2ReadOperand<?, A_BasicObject>> iterator =
			inputRegs.iterator();
		assert iterator.hasNext();
		TypeRestriction<A_BasicObject> restriction =
			iterator.next().restriction();
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
	public final boolean isPhi ()
	{
		return true;
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
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static L2Instruction withoutIndex (
		final L2Instruction instruction,
		final int inputIndex)
	{
		assert instruction.operation == instance;
		final List<? extends L2ReadOperand<?, ?>> oldSources =
			instruction.readVectorRegisterAt(0);
		final L2WritePhiOperand<?, ?> destinationReg =
			instruction.writePhiRegisterAt(1);

		final List<L2ReadOperand<?, ?>> newSources =
			new ArrayList<>(oldSources);
		newSources.remove(inputIndex);

		final Iterator<L2ReadOperand<?, ?>> iterator = newSources.iterator();
		assert iterator.hasNext();
		final L2Register<?> register = iterator.next().register();
		boolean onlyOneRegister = true;
		while (iterator.hasNext())
		{
			if (!iterator.next().register().equals(register))
			{
				onlyOneRegister = false;
				break;
			}
		}
		if (onlyOneRegister)
		{
			// Replace the phi function with a simple move.
			return new L2Instruction(
				instruction.basicBlock,
				L2_MOVE.instance,
				newSources.get(0),
				destinationReg);
		}
		return new L2Instruction(
			instruction.basicBlock,
			L2_PHI_PSEUDO_OPERATION.instance,
			new L2ReadVectorOperand(newSources),
			destinationReg);
	}

	/**
	 * Examine the instruction and answer the predecessor {@link L2BasicBlock}s
	 * that reach this phi instruction and
	 *
	 * @param instruction
	 *        The phi-instruction to examine.
	 * @param usedRegister
	 *        The {@link L2Register} whose use we're trying to trace back to its
	 *        definition.
	 * @return A {@link List} of predecessor blocks that supplied the
	 *         usedRegister as an input to this phi operation.
	 */
	public static List<L2BasicBlock> predecessorBlocksForUseOf (
		final L2Instruction instruction,
		final L2Register<?> usedRegister)
	{
		assert instruction.operation == instance;
//		final List<L2ReadOperand<?, ?>> inputRegs =
//			instruction.readVectorRegisterAt(0);
//		final L2WritePhiOperand<?, ?> destinationReg =
//			instruction.writePhiRegisterAt(1);

		final List<L2PcOperand> predecessorEdges =
			instruction.basicBlock.predecessorEdges();
		final List<? extends L2ReadOperand<?, ?>> sources =
			instruction.readVectorRegisterAt(0);
		final List<L2BasicBlock> list = new ArrayList<>();
		for (int bound = sources.size(), i = 0; i < bound; i++)
		{
			if (sources.get(i).register() == usedRegister)
			{
				list.add(predecessorEdges.get(i).sourceBlock());
			}
		}
		return list;
	}

	/**
	 * Answer the {@link L2WritePhiOperand} from this phi function.  This
	 * should only be used when generating phi moves (which takes the {@link
	 * L2ControlFlowGraph} out of Static Single Assignment form).
	 *
	 * @param <U>
	 *        The type of the {@link L2WritePhiOperand}.
	 * @param instruction
	 *        The instruction to examine.  It must be a phi operation.
	 * @return The instruction's destination {@link L2WritePhiOperand}.
	 */
	@SuppressWarnings("unchecked")
	public static <U extends L2WritePhiOperand<?, ?>>
	U destinationRegisterWrite (final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return (U) instruction.writePhiRegisterAt(1);
	}

	/**
	 * Answer the {@link List} of {@link L2ReadPointerOperand}s for this phi
	 * function.  The order is correlated to the instruction's blocks
	 * predecessorEdges.
	 *
	 * @param instruction
	 *        The phi instruction.
	 * @return The instruction's list of sources.
	 */
	public static <
		RR extends L2ReadOperand<R, T>,
		R extends L2Register<T>,
		T extends A_BasicObject>
	List<RR> sourceRegisterReads (
		final L2Instruction instruction)
	{
		return instruction.readVectorRegisterAt(0);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation;
		final L2Operand vector = instruction.operands[0];
		final L2Register<?> target =
			instruction.writePhiRegisterAt(1).register();
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
