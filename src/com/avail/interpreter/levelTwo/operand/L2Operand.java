/**
 * L2Operand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.evaluation.*;

/**
 * An {@code L2Operand} knows its {@link L2OperandType} and any specific value
 * that needs to be captured for that type of operand.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class L2Operand
{
	/**
	 * Answer this operand's {@link L2OperandType}.
	 *
	 * @return An {@code L2OperandType}.
	 */
	public abstract L2OperandType operandType ();

	/**
	 * Dispatch this {@code L2Operand} to the provided {@link
	 * L2OperandDispatcher}.
	 *
	 * @param dispatcher The {@code L2OperandDispatcher} visiting the receiver.
	 */
	public abstract void dispatchOperand (
		final L2OperandDispatcher dispatcher);

	/**
	 * Invoke the {@link Transformer2 transformer} with each {@link L2Register}
	 * contained within me (also passing this L2Operand), producing an
	 * alternative {@link L2Operand}.  If no transformations were necessary, the
	 * receiver may be returned.
	 *
	 * @param transformer
	 *            What to do with each register.
	 * @return
	 *            The transformed version of the receiver.
	 */
	public abstract L2Operand transformRegisters (
		final Transformer2<L2Register, L2OperandType, L2Register> transformer);

	@Override
	public abstract String toString ();
}
