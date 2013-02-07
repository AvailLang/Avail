/**
 * L2_INVOKE.java
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
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.RegisterSet;

/**
 * Send the specified method and arguments.  The calling continuation is
 * provided, which allows this operation to act more like a non-local jump
 * than a call.  The continuation has the arguments popped already, with the
 * expected return type pushed instead.
 *
 * <p>
 * The appropriate function is looked up and invoked.  The function may be a
 * primitive, and the primitive may succeed, fail, or change the current
 * continuation.
 * </p>
 */
public class L2_INVOKE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_INVOKE();

	static
	{
		instance.init(
			READ_POINTER.is("continuation"),
			READ_POINTER.is("function"),
			READ_VECTOR.is("arguments"));
	}

	@Override
	public void step (final Interpreter interpreter)
	{
		// Assume the current continuation is already reified.
		final int callerIndex = interpreter.nextWord();
		final int functionIndex = interpreter.nextWord();
		final int argumentsIndex = interpreter.nextWord();
		final AvailObject caller = interpreter.pointerAt(callerIndex);
		final AvailObject function = interpreter.pointerAt(functionIndex);
		final AvailObject vect = interpreter.vectorAt(argumentsIndex);
		interpreter.argsBuffer.clear();
		final int vectSize = vect.tupleSize();
		for (int i = 1; i <= vectSize; i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(vect.tupleIntAt(i)));
		}
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			function,
			caller);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		// Restriction happens elsewhere.
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		// Returns to the pc saved in the continuation.
		return false;
	}
}