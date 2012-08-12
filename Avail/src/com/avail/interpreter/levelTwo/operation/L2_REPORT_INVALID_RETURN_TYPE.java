/**
 * L2_REPORT_INVALID_RETURN_TYPE.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.*;

/**
 * A value is known at this point to disagree with the type that it is
 * expected to be.  Report this problem and stop execution.  Note that this
 * instruction might be in a branch of (potentially inlined) code that
 * happens to be unreachable in actuality, despite the compiler being unable
 * to prove this.
 *
 * <p>
 * TODO [MvG] - Of course, this will ultimately need to be handled in a much
 * better way than just stopping the whole VM.
 * </p>
 */
public class L2_REPORT_INVALID_RETURN_TYPE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_REPORT_INVALID_RETURN_TYPE();

	static
	{
		instance.init(
			PRIMITIVE.is("failed primitive"),
			READ_POINTER.is("actual value"),
			CONSTANT.is("expected type"));
	}

	@Override
	public void step (final L2Interpreter interpreter)
	{
		final int primitiveNumber = interpreter.nextWord();
		final int actualValueRegister = interpreter.nextWord();
		final int expectedTypeIndex = interpreter.nextWord();
		final AvailObject actualValue =
			interpreter.pointerAt(actualValueRegister);
		final AvailObject expectedType =
			interpreter.chunk().literalAt(expectedTypeIndex);
		assert !interpreter.primitiveResult.isInstanceOf(expectedType);
		error(
			"primitive %s's result (%s) did not agree with"
			+ " semantic restriction's expected type (%s)",
			Primitive.byPrimitiveNumber(primitiveNumber).name(),
			actualValue,
			expectedType);
	}

	@Override
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		return false;
	}
}