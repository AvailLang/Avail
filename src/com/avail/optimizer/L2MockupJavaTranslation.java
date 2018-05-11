/*
 * L2MockupJavaTranslation.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.optimizer;

import com.avail.descriptor.A_Continuation;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;

import javax.annotation.Nullable;

/**
 * This is a mockup subclass of {@link L2JavaTranslation}, used only as a proof
 * of concept of the code generation technique.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2MockupJavaTranslation extends L2JavaTranslation
{
	/**
	 * Ultimately we want a separate class generated at runtime for each {@link
	 * L2Chunk}, but for now we capture it in the constructor.
	 */
	private final L2Chunk chunk;

	@Override
	@Nullable StackReifier startOrResume (
		final Interpreter interpreter,
		final @Nullable A_Continuation thisContinuation)
	{
		//TODO MvG - Implement before L2->JVM translation.
		throw new UnsupportedOperationException("Not yet implemented");

//		if (thisContinuation != null)
//		{
//			// We're resuming the continuation.  The continuation's
//			// levelTwoOffset() indicates the instruction that was interrupted.
//			// The instruction's operation knows what to do to re-enter safely.
//			// Note that the chunk has already been checked for validity.
//			final L2Instruction interruptedInstruction =
//				chunk.executableInstructions[thisContinuation.levelTwoOffset()];
//			interruptedInstruction.operation.reenter(
//				interruptedInstruction, thisContinuation, interpreter);
//		}
//		// This will take the form of a while loop containing a switch based on
//		// the current offset.  Every jump instruction sets the offset and
//		// continues, implementing an efficient arbitrary goto.
//		while (!interpreter.returnNow)
//		{
//			final L2Instruction instruction =
//				chunk.executableInstructions[interpreter.offset++];
//			instruction.action.value(interpreter);
//		}
	}

	/**
	 * Ultimately we want a separate class generated at runtime for each {@link
	 * L2Chunk}, but for now we capture it in the constructor.
	 *
	 *  @param chunk
	 */
	L2MockupJavaTranslation (
		final L2Chunk chunk)
	{
		this.chunk = chunk;
	}
}
