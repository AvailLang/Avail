/**
 * InstructionWriter.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.jvm;

import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * An {@code InstructionWriter} accumulates {@linkplain JavaInstruction Java
 * instructions} emitted by a {@linkplain CodeGenerator code generator}. Once
 * all instructions have been emitted, their {@linkplain
 * JavaInstruction#address() addresses} must be {@linkplain #fixInstructions()
 * fixed} prior to final {@linkplain #writeTo(DataOutput) emission} to a
 * {@linkplain DataOutput binary output stream}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class InstructionWriter
{
	/** The {@linkplain JavaInstruction instructions}. */
	private final Deque<JavaInstruction> instructions = new LinkedList<>();

	/**
	 * Answer the count of {@linkplain JavaInstruction instructions} already
	 * emitted to the {@linkplain InstructionWriter instruction writer}.
	 *
	 * @return The instruction count.
	 */
	int instructionCount ()
	{
		return instructions.size();
	}

	/** The code size of the method, in bytes. */
	private long codeSize = -1L;

	/**
	 * Answer the code size of the method, in bytes.
	 *
	 * @return The method's code size.
	 */
	int codeSize ()
	{
		assert (codeSize & 0xFFFFFFFFL) == codeSize;
		return (int) codeSize;
	}

	/** The maximum stack depth. */
	private int maxStackDepth = 0;

	/**
	 * Answer the maximum stack depth.
	 *
	 * @return The maximum stack depth.
	 */
	public int maxStackDepth ()
	{
		return maxStackDepth;
	}

	/**
	 * Answer the state of the {@linkplain JavaOperand stack} after considering
	 * the side effects of the most recent {@linkplain JavaInstruction
	 * instruction}.
	 *
	 * @return The new operand stack.
	 */
	private List<JavaOperand> newOperandStack ()
	{
		final JavaInstruction instr = instructions.peekLast();
		if (instr != null)
		{
			if (!instr.isReturn())
			{
				final List<JavaOperand> operands = instr.operandStack();
				assert operands != null;
				final List<JavaOperand> after = new ArrayList<>(
					operands.subList(
						0,
						operands.size() - instr.inputOperands().length));
				after.addAll(Arrays.asList(instr.outputOperands()));
				if (after.size() > maxStackDepth)
				{
					maxStackDepth = after.size();
				}
				return after;
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Append the specified {@linkplain JavaInstruction instruction}.
	 *
	 * @param instruction
	 *        An instruction.
	 */
	void append (final JavaInstruction instruction)
	{
		assert !instruction.emitted;
		assert codeSize == -1L;
		final List<JavaOperand> operands = instruction.operandStack();
		if (operands == null)
		{
			final List<JavaOperand> newOperandStack = newOperandStack();
			assert instruction.canConsumeOperands(newOperandStack);
			instruction.setOperandStack(newOperandStack);
		}
		else
		{
			assert instruction.isLabel();
		}
		instructions.add(instruction);
		instruction.emitted = true;
	}

	/**
	 * Fix the {@linkplain JavaInstruction instructions} at method-relative
	 * addresses.
	 */
	void fixInstructions ()
	{
		// Since the variable width instructions conservatively assume the
		// smallest possible sizes for themselves, reiterating the fixation
		// process can only cause instructions to transition from short forms
		// to wide forms. Fixation is therefore complete when no change is
		// detected in the total code size between two successive attempts.
		long estimatedCodeSize = -1L;
		long address = 0L;
		while (address != estimatedCodeSize)
		{
			estimatedCodeSize = address;
			address = 0;
			for (final JavaInstruction instruction : instructions)
			{
				instruction.setAddress(address);
				address += instruction.size();
			}
		}
		codeSize = estimatedCodeSize;
	}

	/**
	 * Write all accumulated {@linkplain JavaInstruction instructions} to the
	 * specified {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	void writeTo (final DataOutput out) throws IOException
	{
		out.writeInt(codeSize());
		for (final JavaInstruction instruction : instructions)
		{
			instruction.writeTo(out);
		}
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder(1000);
		for (final JavaInstruction instruction : instructions)
		{
			if (instruction.isLabel())
			{
				builder.append(instruction);
				builder.append(':');
			}
			else
			{
				builder.append('\t');
				if (instruction.hasValidAddress())
				{
					builder.append(String.format("%5d", instruction.address()));
					builder.append(": ");
				}
				builder.append(instruction);
			}
			builder.append('\n');
		}
		return builder.toString();
	}
}
