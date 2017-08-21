/**
 * L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.java
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
package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.OptimizationLevel;
import com.avail.utility.*;

/**
 * Explicitly decrement the current compiled code's countdown via {@link
 * AvailObject#countdownToReoptimize(int)}.  If it reaches zero then
 * re-optimize the code.
 */
public class L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO().init(
			IMMEDIATE.is("New optimization level"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final int targetOptimizationLevel = instruction.immediateAt(0);
		return interpreter ->
		{
			final A_Function function = interpreter.function;
			assert function != null;
			final A_RawFunction theCode = function.code();
			final Mutable<Boolean> translated = new Mutable<>(false);
			theCode.decrementCountdownToReoptimize(() ->
			{
				theCode.countdownToReoptimize(
					L2Chunk.countdownForNewlyOptimizedCode());
				L2Translator.translateToLevelTwo(
					theCode,
					OptimizationLevel.all()[targetOptimizationLevel],
					interpreter);
				translated.value = true;
			});
			// If translation actually happened, then run the function in L2.
			if (translated.value)
			{
				interpreter.chunk = theCode.startingChunk();
				interpreter.offset =
					interpreter.chunk.offsetAfterInitialTryPrimitive();
			}
		};
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}
