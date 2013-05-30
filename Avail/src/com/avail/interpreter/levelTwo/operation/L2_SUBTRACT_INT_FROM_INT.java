/**
 * L2_SUBTRACT_INT_FROM_INT.java
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

package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;


/**
 * Subtract the subtrahend from the minuend, jumping to the specified target if
 * the result does not fit in an int.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_SUBTRACT_INT_FROM_INT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_ADD_INT_TO_INT().init(
			READ_INT.is("subtrahend"),
			READWRITE_INT.is("minuend"),
			PC.is("if out of range"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2IntegerRegister subtrahendReg =
			instruction.readIntRegisterAt(0);
		final L2IntegerRegister minuendReg =
			instruction.readWriteIntRegisterAt(1);
		final int outOfRangeOffset = instruction.pcAt(2);

		final int subtrahend = subtrahendReg.in(interpreter);
		final int minuend = minuendReg.in(interpreter);
		final long longResult = (long)minuend - (long)subtrahend;
		final int intResult = (int)longResult;
		if (longResult == intResult)
		{
			minuendReg.set(intResult, interpreter);
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
