/*
 * L2SemanticUnboxedInt.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
 * {@link L2SemanticUnboxedInt} represents the unboxing of an {@code int}-valued
 * {@link L2SemanticValue}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class L2SemanticUnboxedInt
extends L2SemanticValue
{
	/**
	 * The {@link L2SemanticValue} that is wrapped with the unboxing qualifier.
	 */
	public final L2SemanticValue innerSemanticValue;

	/**
	 * Construct a new {@code L2SemanticUnboxedInt}.
	 *
	 * @param innerSemanticValue
	 *        The {@link L2SemanticValue} being wrapped.
	 */
	L2SemanticUnboxedInt (final L2SemanticValue innerSemanticValue)
	{
		assert !(innerSemanticValue instanceof L2SemanticUnboxedInt);
		this.innerSemanticValue = innerSemanticValue;
	}

	@Override
	public boolean equals (final Object obj)
	{
		if (obj instanceof L2SemanticUnboxedInt)
		{
			final L2SemanticUnboxedInt unboxedInt = (L2SemanticUnboxedInt) obj;
			return innerSemanticValue.equals(unboxedInt.innerSemanticValue);
		}
		return false;
	}

	@Override
	public int hashCode ()
	{
		return innerSemanticValue.hashCode() ^ 0xDEF56A59;
	}

	@Override
	public L2SemanticValue transform (
		final Transformer1NotNull<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Transformer1NotNull<Frame, Frame>
			frameTransformer)
	{
		final L2SemanticValue newInner =
			semanticValueTransformer.value(innerSemanticValue);
		return (newInner.equals(innerSemanticValue))
			? this
			: new L2SemanticUnboxedInt(newInner);
	}

	@Override
	public String toString ()
	{
		return "UnboxedInt(" + innerSemanticValue + ")";
	}
}
