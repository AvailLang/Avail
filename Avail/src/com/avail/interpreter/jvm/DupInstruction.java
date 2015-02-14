/**
 * DupInstruction.java
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

import static com.avail.interpreter.jvm.JavaOperand.CATEGORY_1;
import java.util.List;

/**
 * A {@code DupInstruction} requires special {@linkplain JavaOperand operand}
 * management logic.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class DupInstruction
extends SimpleInstruction
{
	@Override
	boolean canConsumeOperands (final List<VerificationTypeInfo> operands)
	{
		final int size = operands.size();
		try
		{
			final VerificationTypeInfo topOperand = operands.get(size - 1);
			if (topOperand.computationalCategory() == CATEGORY_1)
			{
				return true;
			}
		}
		catch (final IndexOutOfBoundsException e)
		{
			// Do nothing.
		}
		return false;
	}

	@Override
	VerificationTypeInfo[] outputOperands (
		final List<VerificationTypeInfo> operandStack)
	{
		assert canConsumeOperands(operandStack);
		final int size = operandStack.size();
		final VerificationTypeInfo topOperand = operandStack.get(size - 1);
		return new VerificationTypeInfo[] {topOperand, topOperand};
	}

	/**
	 * Construct a new {@link DupInstruction}.
	 */
	DupInstruction ()
	{
		super(JavaBytecode.dup);
	}
}
