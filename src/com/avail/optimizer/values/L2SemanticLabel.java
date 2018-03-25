/*
 * L2SemanticLabel.java
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
import com.avail.interpreter.primitive.controlflow
	.P_RestartContinuationWithArguments;

import java.util.function.Function;

/**
 * A semantic value which represents a label continuation created for the
 * indicated {@link Frame}.
 *
 * <p>TODO MvG - It's unclear how to deal with replacement arguments provided by
 * {@link P_RestartContinuationWithArguments}.  Perhaps the approach is to
 * create a duplicate Label using a new Frame.  It would have to merge control
 * flow into a loop, so maybe this just falls under the general case of phis
 * within loops.  Or maybe it should use the very same Arguments, since a
 * semantic value doesn't have a notion of value or register <em>directly</em>
 * associated with it, only through a manifest.
 */
final class L2SemanticLabel extends L2SemanticValue
{
	/** The frame for which this is a {@code Label}. */
	public final Frame frame;

	/**
	 * Create a new {@code L2SemanticLabel} semantic value.
	 *
	 * @param frame
	 *        The frame for which this represents a label.
	 */
	L2SemanticLabel (final Frame frame)
	{
		this.frame = frame;
	}

	@Override
	public boolean equals (final Object obj)
	{
		return obj instanceof L2SemanticLabel
			&& frame.equals(((L2SemanticLabel) obj).frame);
	}

	@Override
	public int hashCode ()
	{
		return frame.hashCode() ^ 0x3D9D8132;
	}

	@Override
	public L2SemanticLabel transform (
		final Function<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Function<Frame, Frame> frameTransformer)
	{
		final Frame newFrame = frameTransformer.apply(frame);
		return newFrame.equals(frame) ? this : new L2SemanticLabel(newFrame);
	}

	@Override
	public String toString ()
	{
		return "Label for " + frame;
	}
}
