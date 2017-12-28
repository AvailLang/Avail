/**
 * L2_PROCESS_INTERRUPT.java
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

import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;

/**
 * Handle an interrupt that has been requested.  The reified continuation is
 * provided as an operand.  The continuation should be in a state that's ready
 * to continue executing after the interrupt (expecting this continuation to be
 * the top reified frame, which will typically be exploded back into registers
 * as a first step).
 */
public class L2_PROCESS_INTERRUPT
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_PROCESS_INTERRUPT().init(
			READ_POINTER.is("continuation"));

	/**
	 * {@link Statistic} for recording the stack abandonment that this operation
	 * causes, always popping a single frame from the stack.
	 */
	private static Statistic abandonmentStat =
		new Statistic(
			"(abandon one layer for L2_PROCESS_INTERRUPT)",
			StatisticReport.REIFICATIONS);

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand continuationReg =
			instruction.readObjectRegisterAt(0);

		final AvailObject continuation = continuationReg.in(interpreter);
		interpreter.reifiedContinuation = continuation;
		assert interpreter.unreifiedCallDepth() == 1;
		return interpreter.abandonStackThen(
			abandonmentStat,
			() -> interpreter.processInterrupt(continuation));
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Don't remove this kind of instruction.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		// Fiber will eventually resume with the provided continuation.
		return false;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// It doesn't reach the next instruction, and it doesn't mention where
		// to resume.  That was dealt with by previous instructions that
		// assembled a continuation to resume.
		assert registerSets.size() == 0;
	}
}
