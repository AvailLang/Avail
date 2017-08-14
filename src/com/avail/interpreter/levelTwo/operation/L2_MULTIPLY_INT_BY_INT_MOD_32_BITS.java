/**
 * L2_MULTIPLY_INT_BY_INT_MOD_32_BITS.java
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
 * Multiply the value in one int register by the value in another int register,
 * truncating it to the low 32 bits and storing it back in the second register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_MULTIPLY_INT_BY_INT_MOD_32_BITS extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_MULTIPLY_INT_BY_INT_MOD_32_BITS().init(
			READ_INT.is("multiplier"),
			READWRITE_INT.is("multiplicand"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2IntegerRegister multiplierReg =
			instruction.readIntRegisterAt(0);
		final L2IntegerRegister multiplicandReg =
			instruction.readWriteIntRegisterAt(1);

		final int multiplier = multiplierReg.in(interpreter);
		final int multiplicand = multiplicandReg.in(interpreter);
		final long longResult = (long)multiplier * (long)multiplicand;
		final int intResult = (int)longResult;
		multiplicandReg.set(intResult, interpreter);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps if the result doesn't fit in an int.
		return true;
	}
}
