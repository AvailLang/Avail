/**
 * L2ControlFlowGraph.java
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

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2Register;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.avail.utility.Strings.increaseIndentation;

/**
 * This is a control graph.  The vertices are {@link L2BasicBlock}s, which are
 * connected via their successor and predecessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2ControlFlowGraph
{
	/**
	 * The basic blocks of the graph.  They're either in the order they were
	 * generated, or in a suitable order for final L2 instruction emission.
	 */
	private final List<L2BasicBlock> basicBlockOrder = new ArrayList<>();

	/**
	 * An {@link AtomicInteger} used to quickly generate unique integers which
	 * serve to visually distinguish new registers.
	 */
	private int uniqueCounter = 0;

	/**
	 * Answer the next value from the unique counter.  This is only used to
	 * distinguish registers for visual debugging.
	 *
	 * @return A int.
	 */
	int nextUnique ()
	{
		return uniqueCounter++;
	}

	/**
	 * Begin code generation in the given block.
	 *
	 * @param block
	 *        The {@link L2BasicBlock} in which to start generating {@link
	 *        L2Instruction}s.
	 */
	public void startBlock (final L2BasicBlock block)
	{
		assert block.instructions().isEmpty();
		assert !basicBlockOrder.contains(block);
		if (block.isIrremovable() || block.hasPredecessors())
		{
			basicBlockOrder.add(block);
		}
	}

	/**
	 * Collect the list of all distinct {@link L2Register}s assigned anywhere
	 * within this control flow graph.
	 *
	 * @return A {@link List} of {@link L2Register}s without repetitions.
	 */
	public List<L2Register> allRegisters ()
	{
		final List<L2Register> allRegisters = new ArrayList<>();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			for (final L2Instruction instruction : block.instructions())
			{
				allRegisters.addAll(instruction.destinationRegisters());
			}
		}
		// This should only be used when the control flow graph is in SSA form,
		// so there should be no duplicates
		return new ArrayList<>(new HashSet<>(allRegisters));
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			builder.append(block.name());
			builder.append(":\n");
			for (final L2Instruction instruction : block.instructions())
			{
				builder.append("\t");
				builder.append(increaseIndentation(instruction.toString(), 1));
				builder.append("\n");
			}
			builder.append("\n");
		}
		return builder.toString();
	}

	/**
	 * Optimize the graph of instructions.
	 */
	public void optimize (final Interpreter interpreter)
	{
		final L2Optimizer optimizer = new L2Optimizer(this, basicBlockOrder);
		optimizer.optimize(interpreter);
	}

	/**
	 * Produce the final list of instructions.  Should only be called after all
	 * optimizations have been performed.
	 *
	 * @param instructions
	 *        The list of instructions to populate.
	 */
	public void generateOn (final List<L2Instruction> instructions)
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			block.generateOn(instructions);
		}
	}
}
