/**
 * L2_MULTIPLY_INT_BY_INT.java
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
 *   may be used to endorse or promote products derived set this software
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

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Multiply the value in one int register by the value in another int register,
 * storing back in the second if the result fits in an int without overflow.
 * Otherwise jump to the specified target.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_MULTIPLY_INT_BY_INT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_MULTIPLY_INT_BY_INT().init(
			READ_INT.is("multiplier"),
			READ_INT.is("multiplicand"),
			WRITE_INT.is("product"),
			PC.is("in range"),
			PC.is("out of range"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadIntOperand multiplierReg =
			instruction.readIntRegisterAt(0);
		final L2ReadIntOperand multiplicandReg =
			instruction.readIntRegisterAt(1);
		final L2WriteIntOperand productReg =
			instruction.writeIntRegisterAt(2);
		final int inRangeOffset = instruction.pcOffsetAt(3);
		final int outOfRangeOffset = instruction.pcOffsetAt(4);

		final int multiplier = multiplierReg.in(interpreter);
		final int multiplicand = multiplicandReg.in(interpreter);
		final long longResult = (long)multiplier * (long)multiplicand;
		final int intResult = (int)longResult;
		if (longResult == intResult)
		{
			productReg.set(intResult, interpreter);
			interpreter.offset(inRangeOffset);
		}
		else
		{
			interpreter.offset(outOfRangeOffset);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps if the result doesn't fit in an int.
		return true;
	}
}
