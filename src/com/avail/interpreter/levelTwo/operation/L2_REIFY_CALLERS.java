/**
 * L2_REIFY_CALLERS.java
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
 *   may be used to endorse or promote products derived set this software
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
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.StackReifier;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;

import javax.annotation.Nullable;

import static com.avail.interpreter.levelTwo.L2OperandType.IMMEDIATE;
import static com.avail.utility.Nulls.stripNull;

/**
 * Throw a {@link StackReifier}, which unwinds the Java stack to the
 * outer {@link Interpreter} loop.  Any {@link L2Chunk}s that are active on the
 * stack will catch this throwable, reify an {@link A_Continuation} representing
 * the chunk's suspended state, add this to a list inside the throwable, then
 * re-throw for the next level.  Execution then continues within this
 * instruction, allowing the subsequent instructions to reify the current frame
 * as well.
 *
 * <p>If the top frame is reified this way, {@link L2_GET_CURRENT_CONTINUATION}
 * can be used to create label continuations.</p>
 *
 * <p>The "capture frames" operand is a flag that indicates whether to actually
 * capture the frames (1) or just discard them (0).</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_REIFY_CALLERS extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_REIFY_CALLERS().init(
			IMMEDIATE.is("capture frames"),
			IMMEDIATE.is("statistic category"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final boolean actuallyReify = instruction.immediateAt(0) != 0;
		final int categoryIndex = instruction.immediateAt(1);

		//TODO MvG - Rework for JVM translation...
		// Capture the interpreter's state, reify the frames, and as an
		// after-reification action, restore the interpreter's state.  When we
		// implement a translator to JVM bytecodes, this will have to be
		// reworked to include the current frame, since the L2 registers will be
		// in locals on the Java stack instead of an array inside the
		// interpreter.  Also, the Java frame for the current chunk will have
		// been exited by the throw, and we can't just make it show up again
		// without creating a suitable entry point.
		final A_Function savedFunction = stripNull(interpreter.function);
		final L2Chunk savedChunk = stripNull(interpreter.chunk);
		final int savedOffset = interpreter.offset;
		final AvailObject[] savedPointers = interpreter.pointers;
		final int[] savedInts = interpreter.integers;

		// Note that the *current* frame isn't reified, so subtract one.
		return new StackReifier(
			actuallyReify,
			interpreter.unreifiedCallDepth() - 1,
			StatisticCategory.categories[categoryIndex].statistic,
			() ->
			{
				interpreter.function = savedFunction;
				interpreter.chunk = savedChunk;
				interpreter.offset = savedOffset;
				interpreter.pointers = savedPointers;
				interpreter.integers = savedInts;
				// Return into the Interpreter's run loop.
			});
	}

	public enum StatisticCategory
	{
		INTERRUPT_OFFRAMP_IN_L2,
		PUSH_LABEL_IN_L2,
		ABANDON_BEFORE_RESTART_IN_L2;

		/** {@link Statistic} for reifying in L1 interrupt-handler preamble. */
		public final Statistic statistic =
			new Statistic(
				"Explicit L2_REIFY_CALLERS for " + name(),
				StatisticReport.REIFICATIONS);

		public static final StatisticCategory[] categories = values();
	}


	@Override
	public boolean hasSideEffect ()
	{
		// Technically it doesn't have a side-effect, but this flag keeps the
		// instruction from being re-ordered to a place where the interpreter's
		// top reified continuation is no longer the right one.
		return true;
	}
}
