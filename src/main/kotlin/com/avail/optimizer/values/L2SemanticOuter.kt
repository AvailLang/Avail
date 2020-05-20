/*
 * L2SemanticOuter.java
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

import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

/**
 * A semantic value which represents a numbered outer variable in the function
 * of some {@link Frame}.
 */
@SuppressWarnings("EqualsAndHashcode")
final class L2SemanticOuter
extends L2FrameSpecificSemanticValue
{
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
		super(frame, outerIndex ^ 0xDD03C87A);
		this.outerIndex = outerIndex;
	}

	@Override
	public boolean equals (final Object obj)
	{
		return obj instanceof L2SemanticOuter
			&& super.equals(obj)
			&& outerIndex == ((L2SemanticOuter) obj).outerIndex;
	}

	@NotNull
	@Override
	public L2SemanticValue transform (
		@NotNull final Function1<? super L2SemanticValue, ? extends L2SemanticValue> semanticValueTransformer,
		@NotNull final Function1<? super Frame, Frame> frameTransformer)
	{
		final Frame newFrame = frameTransformer.invoke(getFrame());
		return newFrame.equals(getFrame())
			? this
			: new L2SemanticOuter(newFrame, outerIndex);
	}

	@Override
	public String toString ()
	{
		return "Outer#" + outerIndex +
			(getFrame().depth() == 1 ? "" : "[" + getFrame() + "]");
	}
}
