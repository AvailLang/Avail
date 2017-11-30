/**
 * L2_REENTER_L1_CHUNK_FROM_CALL.java
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L1InstructionStepper;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.StackReifier;

import javax.annotation.Nullable;

import java.util.logging.Level;

import static com.avail.interpreter.Interpreter.debugL1;
import static com.avail.utility.Nulls.stripNull;

/**
 * <p>This is the first instruction of the L1 interpreter's on-ramp for resuming
 * after a callee returns.  The reified {@link A_Continuation} that was captured
 * (and is now being resumed) pointed to this {@link L2Instruction}.  That
 * continuation is current in the {@link Interpreter#reifiedContinuation}.  Pop
 * it from that continuation chain, create suitable pointer and integer
 * registers as expected by {@link L2_INTERPRET_LEVEL_ONE}, then explode the
 * continuation's slots into those registers.  The {@link Interpreter#function}
 * should also have already been set up to agree with the continuation's
 * function.</p>
 *
 * <p>The value being returned is in {@link Interpreter#latestResult()}, and the
 * top-of-stack of the continuation contains the type to check it against.  If
 * {@link Interpreter#skipReturnCheck} is true, don't bother to do the check,
 * since it's guaranteed by the VM to be correct.</p>
 */
public class L2_REENTER_L1_CHUNK_FROM_CALL extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_REENTER_L1_CHUNK_FROM_CALL().init();

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		if (debugL1)
		{
			Interpreter.log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}Reenter L1 from call",
				interpreter.debugModeString);
		}
		final A_Continuation continuation =
			stripNull(interpreter.reifiedContinuation);
		interpreter.reifiedContinuation = continuation.caller();
		final AvailObject returnValue = interpreter.latestResult();

		final A_Function returneeFunction = stripNull(interpreter.function);
		assert returneeFunction == continuation.function();
		final int numSlots = continuation.numArgsAndLocalsAndStack();
		// Should agree with L2_PREPARE_NEW_FRAME_FOR_L1.
		interpreter.pointers = new AvailObject[numSlots + 1];
		int dest = 1;
		for (int i = 1; i <= numSlots; i++)
		{
			interpreter.pointerAtPut(dest++, continuation.stackAt(i));
		}
		final L1InstructionStepper stepper = interpreter.levelOneStepper;
		stepper.pc = continuation.pc();
		stepper.stackp = continuation.stackp();
		if (!interpreter.skipReturnCheck)
		{
			final A_Type expectedType = interpreter.pointerAt(stepper.stackp);
			interpreter.checkReturnType(
				returnValue, expectedType, returneeFunction);
		}
		interpreter.pointerAtPut(stepper.stackp, returnValue);
		return null;
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}
