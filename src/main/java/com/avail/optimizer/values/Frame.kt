/*
 * Frame.java
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
import com.avail.descriptor.functions.A_Continuation;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.functions.CompiledCodeDescriptor;
import com.avail.interpreter.levelTwo.L2Chunk;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * An abstract representation of an invocation.  Note that this is not itself an {@link L2SemanticValue}, but is used by some specific kinds of semantic values.  The outermost {@code Frame} has an {@link #outerFrame} of {@code null}, and all other frames have a non-null outer frame.  Frames compare by identity.
 */
public final class Frame
{
	/**
	 * The frame that was active at the site of the invocation that this frame
	 * represents.
	 */
	public final @Nullable Frame outerFrame;

	/**
	 * The actual {@link CompiledCodeDescriptor raw function} that's associated
	 * with semantic values tied to this frame.
	 */
	public final A_RawFunction code;

	/**
	 * The symbolic name to use to describe this frame.  Note that it does not
	 * affect the identity of the frame, which is what's used for comparing and
	 * hashing.
	 */
	public final String debugName;

	/**
	 * Construct a new {@code Frame} representing a call within the given frame.
	 *
	 * @param outerFrame
	 *        The frame that was active at the point where an invocation of this frame occurred, or {@code null} if this is the outermost frame.
	 * @param code
	 *        The actual {@link A_RawFunction} that has the L1 code for this frame.
	 * @param debugName
	 *        What to name this frame.
	 */
	public Frame (
		final @Nullable Frame outerFrame,
		final A_RawFunction code,
		final String debugName)
	{
		this.outerFrame = outerFrame;
		this.code = code;
		this.debugName = debugName;
	}

	/**
	 * Answer the depth of this frame, which is how many invocations deep it
	 * is relative to the outermost frame represented by an {@link L2Chunk}.
	 * Note that frames compare by identity, so two frames with the same depth
	 * are not necessarily equal.
	 *
	 * @return
	 * The depth of the frame, where {@code 1} is the outermost frame of a chunk.
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
		return debugName;
	}

	/**
	 * Answer the {@link L2SemanticValue} representing this frame's function.
	 *
	 * @return
	 * This frame's {@link L2SemanticFunction}.
	 */
	public L2SemanticValue function ()
	{
		return new L2SemanticFunction(this);
	}

	/**
	 * Answer the {@link L2SemanticValue} representing this frame's label.
	 *
	 * @return
	 * This frame's {@link L2SemanticLabel}.
	 */
	public L2SemanticValue label ()
	{
		return new L2SemanticLabel(this);
	}

	/**
	 * Answer the {@link L2SemanticValue} representing one of this frame's
	 * function's captured outer values.
	 *
	 * @param outerIndex
	 *        The subscript of the outer value to retrieve from the function running for this frame.
	 * @return The {@link L2SemanticValue} representing the specified outer.
	 */
	public L2SemanticValue outer (final int outerIndex)
	{
		return new L2SemanticOuter(this, outerIndex);
	}

	/**
	 * Answer the {@link L2SemanticValue} representing one of this frame's
	 * slots, as of just after the particular nybblecode that wrote it.
	 *
	 * @param slotIndex
	 *        The subscript of the slot to retrieve from the virtual continuation running for this frame.
	 * @param afterPc
	 *        The level-one {@link A_Continuation#pc()} just after the nybblecode instruction that produced the value in this slot.
	 * @return
	 * The {@link L2SemanticValue} representing the specified slot.
	 */
	public L2SemanticValue slot (final int slotIndex, final int afterPc)
	{
		return new L2SemanticSlot(this, slotIndex, afterPc);
	}

	/**
	 * Answer the {@link L2SemanticValue} representing the return result from
	 * this frame.
	 *
	 * @return The {@link L2SemanticValue} representing the return result.
	 */
	public L2SemanticValue result ()
	{
		return new L2SemanticResult(this);
	}

	/**
	 * Answer the semantic value representing a new temporary value.
	 *
	 * @param uniqueId
	 *        The unique identifier used to identify this temporary value within its frame.
	 * @return
	 * An {@link L2SemanticTemp} representing the temporary value, generalized to an {@link L2SemanticValue}..
	 */
	public L2SemanticValue temp (
		final int uniqueId)
	{
		return new L2SemanticTemp(this, uniqueId);
	}

	/**
	 * Answer an {@link L2SemanticValue} that represents the reified caller
	 * continuation.
	 *
	 * @return
	 * The reified caller (an {@link A_Continuation} at runtime) of this  frame.
	 */
	public L2SemanticValue reifiedCaller ()
	{
		return new L2SemanticCaller(this);
	}

	/**
	 * Transform the receiver via the given {@link Function}.
	 *
	 * @param topFrameReplacement
	 *        The {@code Frame} to substitute for the top frame of the code being inlined.
	 * @param frameTransformer
	 *        How to transform {@code Frame} parts of the receiver.
	 * @return
	 * The transformed {@code Frame}, possibly the receiver if the result of the transformation would have been an equal value.
	 */
	public Frame transform (
		final Frame topFrameReplacement,
		final UnaryOperator<Frame> frameTransformer)
	{
		if (outerFrame == null)
		{
			return topFrameReplacement;
		}
		final Frame newOuterFrame = frameTransformer.apply(outerFrame);
		if (newOuterFrame.equals(outerFrame))
		{
			return this;
		}
		return new Frame(newOuterFrame, code, debugName + " (inlined)");
	}
}
