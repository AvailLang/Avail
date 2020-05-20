/*
 * L2SemanticTemp.java
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
 * A semantic value which holds a temporary value in a {@link Frame}.  The scope
 * of this value is usually local to a section of Java code that both produces
 * and consumes the value, and it might have no meaning beyond this simple
 * correlation of production and use.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("EqualsAndHashcode")
final class L2SemanticTemp
extends L2FrameSpecificSemanticValue
{
	/**
	 * An integer which should be unique across all other instances for the
	 * same {@link Frame}.
	 */
	final int uniqueId;

	/**
	 * Create a new {@code L2SemanticTemp} semantic value.
	 *
	 * @param frame
	 *        The frame for which this represents a temporary value.
	 * @param uniqueId
	 *        An integer which should be unique across all other instances of this class created for this {@link Frame}.
	 */
	L2SemanticTemp (final Frame frame, final int uniqueId)
	{
		super(frame, uniqueId ^ 0xA29C9F1C);
		this.uniqueId = uniqueId;
	}

	@Override
	public boolean equals (final Object obj)
	{
		if (!(obj instanceof L2SemanticTemp))
		{
			return false;
		}
		return super.equals(obj)
			&& uniqueId == ((L2SemanticTemp) obj).uniqueId;
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
			: new L2SemanticTemp(newFrame, uniqueId);
	}

	@Override
	public String toString ()
	{
		if (getFrame().depth() == 1)
		{
			return "Temp#" + uniqueId;
		}
		else
		{
			return "Temp#" + uniqueId + " in " + getFrame();
		}
	}
}
