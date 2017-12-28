/**
 * L2SemanticOuter.java
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
import com.avail.utility.evaluation.Transformer1NotNull;

import static com.avail.descriptor.AvailObject.multiplier;

/**
 * A semantic value which represents a numbered outer variable in the function
 * of some {@link Frame}.
 */
final class L2SemanticOuter extends L2SemanticValue
{
	/** The {@link Frame} for which this is an outer of its function. */
	public final Frame frame;

	/** The one-based index of the outer of the function. */
	public final int outerIndex;

	/**
	 * Create a new {@code L2SemanticOuter} semantic value.
	 *
	 * @param frame
	 *        The frame for which this represents an outer.
	 * @param outerIndex
	 *        The one-based index of the outer in the frame's function.
	 */
	L2SemanticOuter (final Frame frame, final int outerIndex)
	{
		this.frame = frame;
		this.outerIndex = outerIndex;
	}

	@Override
	public boolean equals (final Object obj)
	{
		if (!(obj instanceof L2SemanticOuter))
		{
			return false;
		}
		final L2SemanticOuter outer = (L2SemanticOuter) obj;
		return frame.equals(outer.frame)
			&& outerIndex == outer.outerIndex;
	}

	@Override
	public int hashCode ()
	{
		return (frame.hashCode() + outerIndex) * multiplier;
	}

	@Override
	public L2SemanticOuter transform (
		final Transformer1NotNull<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Transformer1NotNull<Frame, Frame> frameTransformer)
	{
		final Frame newFrame = frameTransformer.value(frame);
		return newFrame.equals(frame)
			? this
			: new L2SemanticOuter(newFrame, outerIndex);
	}

	@Override
	public String toString ()
	{
		return "Outer #" + outerIndex + " of " + frame;
	}
}
