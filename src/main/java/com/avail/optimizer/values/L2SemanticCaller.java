/*
 * L2SemanticCaller.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import java.util.function.UnaryOperator;

/**
 * A semantic value which represents the fully reified caller of the current
 * {@link Frame}.  When inlining, it can be equated with parent frame's
 * {@link L2SemanticLabel}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class L2SemanticCaller
extends L2FrameSpecificSemanticValue
{
	/**
	 * Create a new {@code L2SemanticCaller} semantic value.
	 *
	 * @param frame
	 *        The frame for which this represents the reified caller.
	 */
	L2SemanticCaller (final Frame frame)
	{
		super(frame);
	}

	@Override
	public boolean equals (final Object obj)
	{
		return obj instanceof L2SemanticCaller
			&& frame().equals(((L2SemanticCaller) obj).frame());
	}

	@Override
	public int hashCode ()
	{
		return frame().hashCode() + 0x5A9556AA;
	}

	@Override
	public L2SemanticCaller transform (
		final UnaryOperator<L2SemanticValue> semanticValueTransformer,
		final UnaryOperator<Frame> frameTransformer)
	{
		final Frame newFrame = frameTransformer.apply(frame);
		return newFrame.equals(frame) ? this : new L2SemanticCaller(newFrame);
	}

	@Override
	public String toString ()
	{
		return "ReifiedCaller" +
			(frame.depth() == 1 ? "" : "[of " + frame + "]");
	}
}
