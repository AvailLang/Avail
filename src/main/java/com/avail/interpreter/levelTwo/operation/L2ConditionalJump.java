/*
 * L2ConditionalJump.java
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

package com.avail.interpreter.levelTwo.operation;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static com.avail.interpreter.levelTwo.operation.L2ConditionalJump.BranchReduction.SometimesTaken;
import static com.avail.utility.Nulls.stripNull;

/**
 * Jump to {@code "if satisfied"} if some condition is met, otherwise jump to
 * {@code "if unsatisfied"}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class L2ConditionalJump
extends L2ControlFlowOperation
{
	/**
	 * Protect the constructor so the subclasses can maintain a fly-weight
	 * pattern (or arguably a singleton).
	 *
	 * <p>By convention, there are always 2 {@link #targetEdges(L2Instruction)},
	 * the first of which is the "taken" branch, and the second of which is the
	 * "not taken" branch.</p>
	 *
	 * @param theNamedOperandTypes
	 *        The vararg array of {@link L2NamedOperandType}s that describe the
	 *        operands of such an instruction.
	 */
	protected L2ConditionalJump (
		final L2NamedOperandType... theNamedOperandTypes)
	{
		super(theNamedOperandTypes);
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction, final L2ValueManifest manifest)
	{
		super.instructionWasAdded(instruction, manifest);
		targetEdges(instruction).forEach(L2PcOperand::installCounter);
	}

	/**
	 * Emit a conditional branch, including an increment for the counters along
	 * each path.
	 *
	 * @param translator
	 *        The {@link JVMTranslator} controlling code generation.
	 * @param method
	 *        The {@link MethodVisitor} on which to write the instructions.
	 * @param instruction
	 *        The {@link L2Instruction} causing this code generation.
	 * @param opcode
	 *        The Java bytecode to emit for the branch instruction.
	 * @param conditionHolds
	 *        Where to jump if the condition holds.
	 * @param conditionDoesNotHold
	 *        Where to jump if the condition does not hold.
	 */
	protected static void emitBranch (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction,
		final int opcode,
		final L2PcOperand conditionHolds,
		final L2PcOperand conditionDoesNotHold)
	{
		translator.branch(
			method,
			instruction,
			opcode,
			conditionHolds,
			conditionDoesNotHold,
			stripNull(conditionHolds.counter),
			stripNull(conditionDoesNotHold.counter));
	}

	/**
	 * An {@code enum} indicating whether the decision whether to branch or not
	 * can be reduced to a static decision.
	 */
	public enum BranchReduction
	{
		/** The branch will always be taken. */
		AlwaysTaken,

		/** The branch will never be taken. */
		NeverTaken,

		/** It could not be determined if the branch will be taken or not. */
		SometimesTaken
	}

	/**
	 * Determine if the branch can be eliminated.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} being examined.
	 * @param registerSet
	 *        The {@link RegisterSet} at the current code position.
	 * @param generator
	 *        The {@link L2Generator} in which code (re)generation is taking
	 *        place.
	 * @return A {@link BranchReduction} indicating whether the branch direction
	 *         can be statically decided.
	 */
	public BranchReduction branchReduction (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		return SometimesTaken;
	}

	@Override
	public final boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final BranchReduction reduction =
			branchReduction(instruction, registerSet, generator);
		final List<L2PcOperand> edges = targetEdges(instruction);
		assert edges.size() == 2;
		switch (reduction)
		{
			case AlwaysTaken:
			{
				generator.addInstruction(
					L2_JUMP.instance,
					edges.get(0));
				return true;
			}
			case NeverTaken:
			{
				generator.addInstruction(
					L2_JUMP.instance,
					edges.get(1));
				return true;
			}
			case SometimesTaken:  // Fall-through
		}
		generator.addInstruction(instruction);
		return false;
	}

	@Override
	public final boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}
