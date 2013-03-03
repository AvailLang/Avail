/**
 * L2_PREPARE_NEW_FRAME.java
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

import static com.avail.interpreter.Interpreter.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.RegisterSet;

/**
 * This operation is only used when entering a function that uses the
 * default chunk.  A new function has been set up for execution.  Its
 * arguments have been written to the architectural registers.  If this is a
 * primitive, then the primitive has already been attempted and failed,
 * writing the failure value into the failureValueRegister().  Set up the pc
 * and stackp, as well as local variables.  Also transfer the primitive
 * failure value into the first local variable if this is a primitive (and
 * therefore failed).
 */
public class L2_PREPARE_NEW_FRAME extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_PREPARE_NEW_FRAME().init();

	@Override
	public void step (final Interpreter interpreter)
	{
		final A_Function function = interpreter.pointerAt(FUNCTION);
		final A_RawFunction code = function.code();
		final int numArgs = code.numArgs();
		final int numLocals = code.numLocals();
		final int numSlots = code.numArgsAndLocalsAndStack();
		// Create locals...
		int dest = argumentOrLocalRegister(numArgs + 1);
		for (int i = 1; i <= numLocals; i++)
		{
			interpreter.pointerAtPut(
				dest,
				VariableDescriptor.forOuterType(code.localTypeAt(i)));
			dest++;
		}
		// Write nil into the remaining stack slots.  These
		// values should not encounter any kind of ordinary use, but they
		// must still be transferred into a continuation during reification.
		// Therefore don't use Java nulls here.
		for (int i = numArgs + numLocals + 1; i <= numSlots; i++)
		{
			interpreter.pointerAtPut(dest, NilDescriptor.nil());
			dest++;
		}
		interpreter.integerAtPut(pcRegister(), 1);
		interpreter.integerAtPut(
			stackpRegister(),
			argumentOrLocalRegister(numSlots + 1));
		if (code.primitiveNumber() != 0)
		{
			// A failed primitive.
			assert !Primitive.byPrimitiveNumberOrFail(code.primitiveNumber())
				.hasFlag(Flag.CannotFail);
			final AvailObject primitiveFailureValue =
				interpreter.pointerAt(PRIMITIVE_FAILURE);
			final A_BasicObject primitiveFailureVariable =
				interpreter.pointerAt(argumentOrLocalRegister(numArgs + 1));
			primitiveFailureVariable.setValue(primitiveFailureValue);
		}
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		// No real optimization should ever be done near this wordcode.
		// Do nothing.
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Keep this instruction from being removed, since it's only used
		// by the default chunk.
		return true;
	}
}
