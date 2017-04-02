/**
 * WrapState.java
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

package com.avail.compiler.splitter;
import static com.avail.compiler.ParsingOperation.APPEND_ARGUMENT;

/**
 * An indication of the desired and actual stack state.
 */
enum WrapState
{
	/**
	 * A list has already been pushed for the current sequence.
	 */
	PUSHED_LIST
	{
		@Override
		WrapState processAfterPushedArgument (
			final Expression expression,
			final InstructionGenerator generator)
		{
			generator.flushDelayed();
			generator.emit(expression, APPEND_ARGUMENT);
			return this;
		}
	},

	/**
	 * A list has not yet been pushed for the current sequence, but it will
	 * be.
	 */
	NEEDS_TO_PUSH_LIST
	{
		@Override
		WrapState processAfterPushedArgument (
			final Expression expression,
			final InstructionGenerator generator)
		{
			generator.flushDelayed();
			generator.emitWrapped(expression, 1);
			return PUSHED_LIST;
		}
	},

	/**
	 * A list collecting the arguments for the current sequence should not
	 * be constructed.  Instead, the arguments should be individually
	 * pushed.
	 */
	SHOULD_NOT_PUSH_LIST
	{
		@Override
		WrapState processAfterPushedArgument (
			final Expression expression,
			final InstructionGenerator generator)
		{
			return this;
		}
	},

	/**
	 * The sequence should not have any arguments to push, and it must not
	 * affect the stack at all.
	 */
	SHOULD_NOT_HAVE_ARGUMENTS
	{
		@Override
		WrapState processAfterPushedArgument (
			final Expression expression,
			final InstructionGenerator generator)
		{
			throw new RuntimeException(
				"Not expecting an argument/group, but got one.");
		}
	};

	/**
	 * An argument has just been pushed.  Do any necessary stack adjustments
	 * and
	 *
	 * @return The new state of the stack.
	 */
	abstract WrapState processAfterPushedArgument (
		final Expression expression,
		final InstructionGenerator generator);
}
