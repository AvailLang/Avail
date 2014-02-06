/**
 * L2ReadPointerOperand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.utility.evaluation.*;

/**
 * An {@code L2ReadPointerOperand} is an operand of type {@link
 * L2OperandType#READ_POINTER}.  It holds the actual {@link L2ObjectRegister}
 * that is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2ReadPointerOperand extends L2Operand
{
	/**
	 * The actual {@link L2ObjectRegister}.
	 */
	public final L2ObjectRegister register;

	/**
	 * Construct a new {@link L2ReadPointerOperand} with the specified {@link
	 * L2ObjectRegister}.
	 *
	 * @param register The object register.
	 */
	public L2ReadPointerOperand (
		final L2ObjectRegister register)
	{
		this.register = register;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.READ_POINTER;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public L2ReadPointerOperand transformRegisters (
		final Transformer2<L2Register, L2OperandType, L2Register> transformer)
	{
		return new L2ReadPointerOperand(
			(L2ObjectRegister)transformer.value(register, operandType()));
	}

	@Override
	public String toString ()
	{
		return String.format("ReadObject(%s)", register);
	}
}
