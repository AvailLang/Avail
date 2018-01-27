/*
 * L2SemanticFunction.java
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
package com.avail.optimizer.values;
import com.avail.utility.evaluation.Transformer1NotNull;

/**
 * A semantic value which represents the current function while running code for
 * a particular {@link Frame}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class L2SemanticFunction
extends L2SemanticValue
{
	/** The frame for which this represents the current function. */
	public final Frame frame;

	/**
	 * Create a new {@code L2SemanticFunction} semantic value.
	 *
	 * @param frame
	 *        The frame for which this represents the invoked function.
	 */
	L2SemanticFunction (final Frame frame)
	{
		this.frame = frame;
	}

	@Override
	public boolean equals (final Object obj)
	{
		return obj instanceof L2SemanticFunction
			&& frame.equals(((L2SemanticFunction) obj).frame);
	}

	@Override
	public int hashCode ()
	{
		return frame.hashCode() + 0xF1AE6003;
	}

	@Override
	public L2SemanticFunction transform (
		final Transformer1NotNull<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Transformer1NotNull<Frame, Frame> frameTransformer)
	{
		final Frame newFrame = frameTransformer.value(frame);
		return newFrame.equals(frame) ? this : new L2SemanticFunction(newFrame);
	}

	@Override
	public String toString ()
	{
		return "Function of " + frame;
	}
}
