/**
 * L2_SUBTRACT_INT_FROM_INT_MOD_32_BITS.java
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

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;

/**
 * Subtract the subtrahend from the minuend, converting the result to a signed
 * 32-bit int through signed truncation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_SUBTRACT_INT_FROM_INT_MOD_32_BITS extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_SUBTRACT_INT_FROM_INT_MOD_32_BITS().init(
			READ_INT.is("subtrahend"),
			READWRITE_INT.is("minuend"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2IntegerRegister subtrahendReg =
			instruction.readIntRegisterAt(0);
		final L2IntegerRegister minuendReg =
			instruction.readWriteIntRegisterAt(1);

		final int subtrahend = subtrahendReg.in(interpreter);
		final int minuend = minuendReg.in(interpreter);
		final int intResult = minuend - subtrahend;
		minuendReg.set(intResult, interpreter);
	}
}
