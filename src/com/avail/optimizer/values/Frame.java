/**
 * Frame.java
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
package com.avail.optimizer.values;
import com.avail.interpreter.levelTwo.L2Chunk;

import javax.annotation.Nullable;

/**
 * An abstract representation of an invocation.  Note that this is not itself an
 * {@link L2SemanticValue}, but is used by some specific kinds of semantic
 * values.  The outermost {@code Frame} has an {@link #outerFrame} of {@code
 * null}, and all other frames have a non-null outer frame.  Frames compare by
 * identity.
 */
public final class Frame
{
	/**
	 * The frame that was active at the site of the invocation that this
	 * frame represents.
	 */
	public final @Nullable Frame outerFrame;

	/**
	 * Construct a new {@code Frame} representing a call within the given
	 * frame.
	 *
	 * @param outerFrame
	 *        The frame that was active at the point where an invocation of
	 *        this frame occurred.
	 */
	public Frame (final @Nullable Frame outerFrame)
	{
		this.outerFrame = outerFrame;
	}

	/**
	 * Answer the depth of this frame, which is how many invocations deep it
	 * is relative to the outermost frame represented by an {@link L2Chunk}.
	 * Note that frames compare by identity, so two frames with the same
	 * depth are not necessarily equal.
	 *
	 * @return The depth of the frame, where {@code 1} is the outermost
	 *         frame of a chunk.
	 */
	public int depth ()
	{
		@Nullable Frame f = outerFrame;
		int depth = 1;
		while (f != null)
		{
			depth++;
			f = f.outerFrame;
		}
		return depth;
	}

	@Override
	public String toString ()
	{
		final int depth = depth();
		if (depth == 1)
		{
			return "top frame";
		}
		return "frame at depth " + depth;
	}

	/**
	 * Answer the {@link L2SemanticValue} representing this frame's function.
	 *
	 * @return This frame's {@link L2SemanticFunction}.
	 */
	public L2SemanticValue function ()
	{
		return new L2SemanticFunction(this);
	}

	/**
	 * Answer the {@link L2SemanticValue} representing one of this frame's
	 * function's captured outer values.
	 *
	 * @param outerIndex
	 *        The subscript of the outer value to retrieve from the function
	 *        running for this frame.
	 * @return The {@link L2SemanticValue} representing the specified outer.
	 */
	public L2SemanticValue outer (final int outerIndex)
	{
		return new L2SemanticOuter(this, outerIndex);
	}

	/**
	 * Answer the {@link L2SemanticValue} representing one of this frame's
	 * arguments.
	 *
	 * @param argumentIndex
	 *        The subscript of the argument to retrieve from the virtual
	 *        continuation running for this frame.
	 * @return The {@link L2SemanticValue} representing the specified argument.
	 */
	public L2SemanticValue argument (final int argumentIndex)
	{
		return new L2SemanticArgument(this, argumentIndex);
	}
}
