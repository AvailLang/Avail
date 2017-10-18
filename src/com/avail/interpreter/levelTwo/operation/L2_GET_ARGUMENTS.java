/**
 * L2_GET_ARGUMENTS.java
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

import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.Continuation1NotNullThrowsReification;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_VECTOR;

/**
 * Ask the {@link Interpreter} for its {@link Interpreter#argsBuffer}, which
 * is how functions are supplied their arguments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_GET_ARGUMENTS extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_GET_ARGUMENTS().init(
			WRITE_VECTOR.is("arguments"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final List<L2WritePointerOperand> argumentRegs =
			instruction.writeVectorRegisterAt(0);

		// Pre-decode the argument registers as much as possible.
		final int numArgs = argumentRegs.size();
		final int[] argumentRegNumbers = argumentRegs.stream()
			.mapToInt(L2WritePointerOperand::finalIndex)
			.toArray();

		return interpreter ->
		{
			final List<AvailObject> arguments = interpreter.argsBuffer;
			assert arguments.size() == numArgs;
			for (int i = 0; i < numArgs; i++)
			{
				interpreter.pointerAtPut(
					argumentRegNumbers[i], arguments.get(i));
			}
		};
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Technically it doesn't have a side-effect, but this flag keeps the
		// instruction from being re-ordered to a place where the arguments are
		// no longer available.
		return true;
	}
}
