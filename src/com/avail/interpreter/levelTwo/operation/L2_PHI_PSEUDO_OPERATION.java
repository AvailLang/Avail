/*
 * L2_PHI_PSEUDO_OPERATION.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import static java.util.stream.Collectors.toList;

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
public class L2_PHI_PSEUDO_OPERATION
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_PHI_PSEUDO_OPERATION().init(
			READ_VECTOR.is("potential sources"),
			WRITE_POINTER.is("destination"));

	@Override
	protected void propagateTypes (
		@NotNull final L2Instruction instruction,
		@NotNull final RegisterSet registerSet,
		final L2Translator translator)
	{
		final List<L2ReadPointerOperand> inputRegs =
			instruction.readVectorRegisterAt(0);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(1);

		@SuppressWarnings("ConstantConditions")
		final TypeRestriction restriction = inputRegs.stream()
			.map(L2ReadPointerOperand::restriction)
			.reduce(TypeRestriction::union)
			.get();
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
	public static L2Instruction withoutIndex (
		final L2Instruction instruction,
		final int inputIndex)
	{
		assert instruction.operation == instance;
		final List<L2ReadPointerOperand> oldSources =
			instruction.readVectorRegisterAt(0);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(1);

		final List<L2ReadPointerOperand> newSources =
			new ArrayList<>(oldSources);
		newSources.remove(inputIndex);
		final long distinctSourceRegisters =
			newSources.stream()
				.map(L2ReadPointerOperand::register)
				.distinct()
				.count();
		if (distinctSourceRegisters == 1)
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
		final L2Register usedRegister)
	{
		assert instruction.operation == instance;

//		final List<L2ReadPointerOperand> inputRegs =
//			instruction.readVectorRegisterAt(0);
//		final L2WritePointerOperand destinationReg =
//			instruction.writeObjectRegisterAt(1);

		final List<L2PcOperand> predecessorEdges =
			instruction.basicBlock.predecessorEdges();
		final List<L2ReadPointerOperand> sources =
			instruction.readVectorRegisterAt(0);
		return IntStream.range(0, sources.size())
			.filter(i -> sources.get(i).register() == usedRegister)
			.mapToObj(i -> predecessorEdges.get(i).sourceBlock())
			.collect(toList());
	}

	/**
	 * Answer the {@link L2WritePointerOperand} from this phi function.  This
	 * should only be used when generating phi moves (which takes the {@link
	 * L2ControlFlowGraph} out of Static Single Assignment form).
	 *
	 * @param instruction
	 *        The instruction to examine.  It must be a phi operation.
	 * @return The instruction's destination {@link L2WritePointerOperand}.
	 */
	public static L2WritePointerOperand destinationRegisterWrite (
		final L2Instruction instruction)
	{
		assert instruction.operation instanceof L2_PHI_PSEUDO_OPERATION;
		return instruction.writeObjectRegisterAt(1);
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
	public static List<L2ReadPointerOperand> sourceRegisterReads (
		final L2Instruction instruction)
	{
		return instruction.readVectorRegisterAt(0);
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
