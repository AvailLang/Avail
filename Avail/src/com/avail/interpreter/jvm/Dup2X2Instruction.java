/**
 * Dup2X2Instruction.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.interpreter.jvm.JavaOperand.*;
import java.util.List;

/**
 * A {@code Dup2X2Instruction} requires special {@linkplain JavaOperand operand}
 * management logic.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class Dup2X2Instruction
extends SimpleInstruction
{
	@Override
	boolean canConsumeOperands (final List<JavaOperand> operands)
	{
		final int size = operands.size();
		try
		{
			final JavaOperand topOperand = operands.get(size - 1);
			final JavaOperand secondOperand = operands.get(size - 2);
			if (topOperand.computationalCategory() == CATEGORY_1)
			{
				final JavaOperand thirdOperand = operands.get(size - 3);
				if (thirdOperand.computationalCategory() == CATEGORY_1)
				{
					final JavaOperand fourthOperand = operands.get(size - 4);
					return secondOperand.computationalCategory() == CATEGORY_1
						&& fourthOperand.computationalCategory() == CATEGORY_1;
				}
				return secondOperand.computationalCategory() == CATEGORY_1
					&& thirdOperand.computationalCategory() == CATEGORY_2;
			}
			else if (topOperand.computationalCategory() == CATEGORY_2)
			{
				if (secondOperand.computationalCategory() == CATEGORY_1)
				{
					final JavaOperand thirdOperand = operands.get(size - 3);
					return thirdOperand.computationalCategory() == CATEGORY_1;
				}
				return secondOperand.computationalCategory() == CATEGORY_2;
			}
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
		final JavaOperand secondOperand = operandStack.get(size - 2);
		final JavaOperand[] out;
		if (topOperand.computationalCategory() == CATEGORY_1)
		{
			final JavaOperand thirdOperand = operandStack.get(size - 3);
			if (thirdOperand.computationalCategory() == CATEGORY_1)
			{
				final JavaOperand fourthOperand = operandStack.get(size - 4);
				out =
					new JavaOperand[]
					{
						secondOperand,
						topOperand,
						fourthOperand,
						thirdOperand,
						secondOperand,
						topOperand
					};
			}
			else
			{
				assert secondOperand.computationalCategory() == CATEGORY_1;
				assert thirdOperand.computationalCategory() == CATEGORY_2;
				out =
					new JavaOperand[]
					{
						secondOperand,
						topOperand,
						thirdOperand,
						secondOperand,
						topOperand
					};
			}
		}
		else
		{
			assert topOperand.computationalCategory() == CATEGORY_2;
			if (secondOperand.computationalCategory() == CATEGORY_1)
			{
				final JavaOperand thirdOperand = operandStack.get(size - 3);
				out = new JavaOperand[]
					{topOperand, thirdOperand, secondOperand, topOperand};
			}
			else
			{
				assert secondOperand.computationalCategory() == CATEGORY_2;
				out = new JavaOperand[] {topOperand, secondOperand, topOperand};
			}
		}
		return out;
	}

	/**
	 * Construct a new {@link Dup2X2Instruction}.
	 */
	Dup2X2Instruction ()
	{
		super(JavaBytecode.dup2_x2);
	}
}
