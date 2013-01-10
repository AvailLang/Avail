/**
 * L2_EXPLODE_CONTINUATION.java
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
import com.avail.interpreter.levelTwo.*;

/**
 * Given a continuation, extract its caller, function, and all of its slots
 * into the specified registers.  The level one program counter and stack
 * pointer are ignored, since they're always implicitly correlated with the
 * level two program counter.
 */
public class L2_EXPLODE_CONTINUATION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_EXPLODE_CONTINUATION();

	static
	{
		instance.init(
			READ_POINTER.is("continuation to explode"),
			WRITE_VECTOR.is("exploded continuation slots"),
			WRITE_POINTER.is("exploded caller"),
			WRITE_POINTER.is("exploded function"));
	}

	@Override
	public void step (final L2Interpreter interpreter)
	{
		// Expand the current continuation's slots into the specified vector
		// of destination registers.  Also explode the level one pc, stack
		// pointer, the current function and the caller.
		final int continuationToExplodeIndex = interpreter.nextWord();
		final int explodedSlotsVectorIndex = interpreter.nextWord();
		final int explodedCallerIndex = interpreter.nextWord();
		final int explodedFunctionIndex = interpreter.nextWord();

		final AvailObject slots =
			interpreter.vectorAt(explodedSlotsVectorIndex);
		final int slotsCount = slots.tupleSize();
		final AvailObject continuation =
			interpreter.pointerAt(continuationToExplodeIndex);
		assert continuation.numArgsAndLocalsAndStack() == slotsCount;
		for (int i = 1; i <= slotsCount; i++)
		{
			final AvailObject slotValue =
				continuation.argOrLocalOrStackAt(i);
			interpreter.pointerAtPut(slots.tupleIntAt(i), slotValue);
		}
		interpreter.pointerAtPut(
			explodedCallerIndex,
			continuation.caller());
		interpreter.pointerAtPut(
			explodedFunctionIndex,
			continuation.function());
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}