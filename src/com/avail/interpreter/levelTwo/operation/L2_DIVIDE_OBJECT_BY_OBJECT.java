/**
 * L2_DIVIDE_OBJECT_BY_OBJECT.java
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

import com.avail.descriptor.A_Number;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.StackReifier;

import javax.annotation.Nullable;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Divide the dividend value by the divisor value.  If the calculation causes an
 * {@link ArithmeticException}, jump to the specified label, otherwise set the
 * quotient and remainder registers and continue with the next instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_DIVIDE_OBJECT_BY_OBJECT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_DIVIDE_OBJECT_BY_OBJECT().init(
			READ_POINTER.is("dividend"),
			READ_POINTER.is("divisor"),
			WRITE_POINTER.is("quotient"),
			WRITE_POINTER.is("remainder"),
			PC.is("if undefined"),
			PC.is("success"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand dividendReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand divisorReg =
			instruction.readObjectRegisterAt(1);
		final L2WritePointerOperand quotientReg =
			instruction.writeObjectRegisterAt(2);
		final L2WritePointerOperand remainderReg =
			instruction.writeObjectRegisterAt(3);
		final int undefinedIndex = instruction.pcOffsetAt(4);
		final int successIndex = instruction.pcOffsetAt(5);

		final A_Number dividend = dividendReg.in(interpreter);
		final A_Number divisor = divisorReg.in(interpreter);
		try
		{
			final A_Number quotient = dividend.divideCanDestroy(divisor, false);
			final A_Number remainder = dividend.minusCanDestroy(
				quotient.timesCanDestroy(divisor, false), false);
			quotientReg.set(quotient, interpreter);
			remainderReg.set(remainder, interpreter);
			interpreter.offset(successIndex);
		}
		catch (final ArithmeticException e)
		{
			interpreter.offset(undefinedIndex);
		}
		return null;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps for division by zero.
		return true;
	}
}
