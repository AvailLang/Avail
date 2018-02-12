/*
 * L2JavaTranslation.java
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
import com.avail.interpreter.levelTwo.L2Instruction;

import javax.annotation.Nullable;

/**
 * This class represents a translation of a level two continuation into
 * equivalent Java code.  Its execution should be the equivalent of interpreting
 * the level two code, which is itself an optimized version of the corresponding
 * level one raw function.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class L2JavaTranslation
{
	/**
	 * Start or resume execution of this translation of an L2Chunk.  If the
	 * provided continuation is {@code null}, start running the function, under
	 * the assumption that the interpreter's registers have been set up for a
	 * call, including setting the architectural argument registers.  If the
	 * provided continuation is not null, locate the {@link L2Instruction} that
	 * was responsible for originally leaving this context, and ask it to
	 * restore the execution state from the continuation, then continue
	 * executing it.
	 *
	 * <p>In the majority of circumstances, this Java method will only return
	 * when the corresponding Avail function has completed.  However, to support
	 * some of Avail's more powerful features like backtracking, exceptions,
	 * very deep stacks, and stackless context switching, we may instead return
	 * a {@link StackReifier}.  During this "unwinding", all unreified frames
	 * still on the Java stack will be converted to mutable level one {@link
	 * A_Continuation}s, which will subsequently be linked together in the
	 * orrect order by the {@link Interpreter}'s outermost run loop.
	 *
	 * @param thisContinuation
	 *        Either {@code null} in the case that this is an initial call, or
	 *        else the {@link A_Continuation} to resume.
	 * @return StackReifier If the stack needs to be reified.
	 */
	abstract @Nullable
	StackReifier startOrResume (
		final Interpreter interpreter,
		final @Nullable A_Continuation thisContinuation);
}
