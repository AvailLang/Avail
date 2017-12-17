/**
 * L2SemanticArgument.java
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
import static com.avail.descriptor.AvailObject.multiplier;

/**
 * A semantic value which represents an argument of some {@link Frame}.
 */
public final class L2SemanticArgument extends L2SemanticValue
{
	/** The {@link Frame} for which this is an argument. */
	public final Frame frame;

	/** The one-based index of the argument of this {@link Frame}. */
	public final int argumentIndex;

	/**
	 * Create a new {@code L2SemanticArgument} semantic value.
	 *
	 * @param frame
	 *        The frame for which this represents an argument.
	 * @param argumentIndex
	 *        The one-based index of the argument to that frame.
	 */
	public L2SemanticArgument (final Frame frame, final int argumentIndex)
	{
		this.frame = frame;
		this.argumentIndex = argumentIndex;
	}

	@Override
	public boolean equals (final Object obj)
	{
		if (!(obj instanceof L2SemanticArgument))
		{
			return false;
		}
		final L2SemanticArgument
			arg = (L2SemanticArgument) obj;
		return frame.equals(arg.frame)
			&& argumentIndex == arg.argumentIndex;
	}

	@Override
	public int hashCode ()
	{
		return (frame.hashCode() ^ argumentIndex) * multiplier;
	}

	@Override
	public String toString ()
	{
		return "Argument #" + argumentIndex + " of " + frame;
	}
}
