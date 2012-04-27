/**
 * L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.java
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

import static com.avail.interpreter.levelTwo.L2Interpreter.argumentOrLocalRegister;
import static com.avail.interpreter.levelTwo.register.FixedRegister.FUNCTION;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.L2Translator;

/**
 * Explicitly decrement the current compiled code's countdown via {@link
 * AvailObject#countdownToReoptimize(int)}.  If it reaches zero then
 * re-optimize the code.
 */
public class L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO();

	static
	{
		instance.init();
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final AvailObject theFunction = interpreter.pointerAt(FUNCTION);
		final AvailObject theCode = theFunction.code();
		final int newCount = theCode.invocationCount() - 1;
		assert newCount >= 0;
		if (newCount != 0)
		{
			theCode.countdownToReoptimize(newCount);
		}
		else
		{
			theCode.countdownToReoptimize(
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			final L2Translator translator = new L2Translator(theCode);
			translator.translateOptimizationFor(
				3,
				interpreter);
			interpreter.argsBuffer.clear();
			final int nArgs = theCode.numArgs();
			for (int i = 1; i <= nArgs; i++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(argumentOrLocalRegister(i)));
			}
			interpreter.invokeFunctionArguments(
				theFunction,
				interpreter.argsBuffer);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}