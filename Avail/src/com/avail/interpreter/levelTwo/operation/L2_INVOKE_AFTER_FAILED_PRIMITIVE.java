/**
 * L2_INVOKE_AFTER_FAILED_PRIMITIVE.java
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
import static com.avail.interpreter.levelTwo.register.FixedRegister.PRIMITIVE_FAILURE;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Invoke the specified <em>primitive</em> function with the supplied
 * arguments, ignoring the primitive designation.  The calling continuation
 * is provided, which allows this operation to act more like a non-local
 * jump than a call.  The continuation has the arguments popped already,
 * with the expected return type pushed instead.
 *
 * <p>
 * The function must be a primitive which has already failed at this point,
 * so set up the failure code of the function without trying the primitive.
 * The failure value from the failed primitive attempt is provided and will
 * be saved in the architectural {@link FixedRegister#PRIMITIVE_FAILURE}
 * register for use by subsequent L1 or L2 code.
 * </p>
 */
public class L2_INVOKE_AFTER_FAILED_PRIMITIVE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_INVOKE_AFTER_FAILED_PRIMITIVE().init(
			READ_POINTER.is("continuation"),
			READ_POINTER.is("function"),
			READ_VECTOR.is("arguments"),
			READ_POINTER.is("primitive failure value"));

	@Override
	public void step (final Interpreter interpreter)
	{
		// The continuation is required to have already been reified.
		final int callerIndex = interpreter.nextWord();
		final int functionIndex = interpreter.nextWord();
		final int argumentsIndex = interpreter.nextWord();
		final int failureValueIndex = interpreter.nextWord();
		final AvailObject caller = interpreter.pointerAt(callerIndex);
		final A_Function function = interpreter.pointerAt(functionIndex);
		final AvailObject failureValue =
			interpreter.pointerAt(failureValueIndex);
		final A_Tuple vect = interpreter.vectorAt(argumentsIndex);
		interpreter.argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(vect.tupleIntAt(i)));
		}
		final A_BasicObject codeToCall = function.code();
		final int primNum = codeToCall.primitiveNumber();
		assert primNum != 0;
		assert !Primitive.byPrimitiveNumberOrFail(primNum).hasFlag(
			Flag.CannotFail);
		interpreter.invokeWithoutPrimitiveFunctionArguments(
			function,
			interpreter.argsBuffer,
			caller);
		// Put the primitive failure value somewhere that both L1 and L2
		// will find it.
		interpreter.pointerAtPut(PRIMITIVE_FAILURE, failureValue);
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
		// Never remove this send, since it's due to a failed primitive.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		// Returns to the pc saved in the continuation.
		return false;
	}
}
