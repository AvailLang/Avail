/**
 * Dup2Instruction.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.interpreter.jvm.JavaOperand.CATEGORY_1;
import static com.avail.interpreter.jvm.JavaOperand.CATEGORY_2;
import java.util.List;

/**
 * A {@code Dup2Instruction} requires special {@linkplain JavaOperand operand}
 * management logic.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class Dup2Instruction
extends SimpleInstruction
{
	@Override
	boolean canConsumeOperands (final List<JavaOperand> operands)
	{
		final int size = operands.size();
		try
		{
			final JavaOperand topOperand = operands.get(size - 1);
			if (topOperand.computationalCategory() == CATEGORY_1)
			{
				final JavaOperand nextOperand = operands.get(size - 2);
				return nextOperand.computationalCategory() == CATEGORY_1;
			}
			return topOperand.computationalCategory() == CATEGORY_2;
		}
		catch (final IndexOutOfBoundsException e)
		{
			// Do nothing.
		}
		return false;
	}

	@Override
	JavaOperand[] outputOperands (final List<JavaOperand> operandStack)
	{
		assert canConsumeOperands(operandStack);
		final int size = operandStack.size();
		final JavaOperand topOperand = operandStack.get(size - 1);
		final JavaOperand[] out;
		if (topOperand.computationalCategory() == CATEGORY_1)
		{
			final JavaOperand nextOperand = operandStack.get(size - 2);
			out = new JavaOperand[]
				{nextOperand, topOperand, nextOperand, topOperand};
		}
		else
		{
			assert topOperand.computationalCategory() == CATEGORY_2;
			out = new JavaOperand[] {topOperand, topOperand};
		}
		return out;
	}

	/**
	 * Construct a new {@link Dup2Instruction}.
	 */
	Dup2Instruction ()
	{
		super(JavaBytecode.dup2);
	}
}
