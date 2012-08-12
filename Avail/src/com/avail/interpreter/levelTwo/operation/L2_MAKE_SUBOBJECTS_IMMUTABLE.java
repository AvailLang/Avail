/**
 * L2_MAKE_SUBOBJECTS_IMMUTABLE.java
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

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.*;

/**
 * Mark as immutable all objects referred to from the specified object.
 * Copying a continuation as part of the {@link
 * L1Operation#L1Ext_doPushLabel} can make good use of this peculiar
 * instruction.
 */
public class L2_MAKE_SUBOBJECTS_IMMUTABLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_MAKE_SUBOBJECTS_IMMUTABLE();

	static
	{
		instance.init(
			READ_POINTER.is("object"));
	}

	@Override
	public void step (final L2Interpreter interpreter)
	{
		final int objectIndex = interpreter.nextWord();
		interpreter.pointerAt(objectIndex).makeSubobjectsImmutable();
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Marking the object immutable is a side effect, but unfortunately
		// this could keep extra instructions around to create an object
		// that nobody wants.
		// [MvG] - maybe use a pseudo-copy operation from linear languages?
		return true;
	}
}