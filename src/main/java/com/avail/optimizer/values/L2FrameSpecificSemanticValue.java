/*
 * L2FrameSpecificSemanticValue.java
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

import static com.avail.descriptor.representation.AvailObject.multiplier;

/**
 * A semantic value which is specific to a {@link Frame}.
 */
@SuppressWarnings("EqualsAndHashcode")
abstract class L2FrameSpecificSemanticValue extends L2SemanticValue
{
	/** The frame in which this is a semantic value. */
	public final Frame frame;

	/**
	 * Create a new instance.
	 *
	 * @param frame
	 *        The frame for which this is a semantic value.
	 * @param hash
	 *        A hash value of this semantic value, which this constructor will
	 *        combine with the frame's hash.
	 */
	L2FrameSpecificSemanticValue (
		final Frame frame,
		final int hash)
	{
		super(hash + frame.hashCode() * multiplier);
		this.frame = frame;
	}

	/**
	 * Answer the {@link Frame} in which this invocation takes place.
	 *
	 * @return The frame.
	 */
	public final Frame frame ()
	{
		return frame;
	}

	@Override
	public boolean equals (final Object obj)
	{
		return obj instanceof L2FrameSpecificSemanticValue
			&& frame().equals(((L2FrameSpecificSemanticValue) obj).frame());
	}

	@Override
	public abstract L2FrameSpecificSemanticValue transform (
		final UnaryOperator<L2SemanticValue> semanticValueTransformer,
		final UnaryOperator<Frame> frameTransformer);

	@Override
	public abstract String toString ();
}
