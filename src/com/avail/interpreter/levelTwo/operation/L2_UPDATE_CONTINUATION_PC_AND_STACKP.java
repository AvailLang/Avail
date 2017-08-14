/**
 * L2_UPDATE_CONTINUATION_PC_AND_STACKP.java
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
import com.avail.descriptor.A_Continuation;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;

/**
 * Update an existing continuation's level one program counter and stack
 * pointer to the provided immediate integers.  If the continuation is
 * mutable then change it in place, otherwise use a mutable copy.  Write
 * the resulting continuation back to the register that provided the
 * original.
 */
public class L2_UPDATE_CONTINUATION_PC_AND_STACKP extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_UPDATE_CONTINUATION_PC_AND_STACKP().init(
			READWRITE_POINTER.is("continuation"),
			IMMEDIATE.is("new pc"),
			IMMEDIATE.is("new stack pointer"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ObjectRegister continuationReg =
			instruction.readWriteObjectRegisterAt(0);
		final int newPc = instruction.immediateAt(1);
		final int newStackp = instruction.immediateAt(2);

		A_Continuation continuation = continuationReg.in(interpreter);
		continuation = continuation.ensureMutable();
		continuation.adjustPcAndStackp(newPc, newStackp);
		continuationReg.set(continuation,interpreter);
	}
}
