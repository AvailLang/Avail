/*
 * L2SemanticConstant.java
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
import com.avail.descriptor.A_BasicObject;
import com.avail.utility.evaluation.Transformer1NotNull;

/**
 * A semantic value which is a particular actual constant value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class L2SemanticConstant
extends L2SemanticValue
{
	public final A_BasicObject value;

	/**
	 * Create a new {@code L2SemanticConstant} semantic value.
	 *
	 * @param value The actual value of the constant.
	 */
	L2SemanticConstant (final A_BasicObject value)
	{
		this.value = value;
	}

	@Override
	public boolean equals (final Object obj)
	{
		return obj instanceof L2SemanticConstant
			&& value.equals(((L2SemanticConstant) obj).value);
	}

	@Override
	public int hashCode ()
	{
		return value.hashCode();
	}

	@Override
	public L2SemanticConstant transform (
		final Transformer1NotNull<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Transformer1NotNull<Frame, Frame> frameTransformer)
	{
		// Semantic constants need no transformation when inlining.
		return this;
	}

	@Override
	public String toString ()
	{
		String valueString = value.toString();
		if (valueString.length() > 50)
		{
			valueString = valueString.substring(0, 50) + "…";
		}
		//noinspection DynamicRegexReplaceableByCompiledPattern
		valueString = valueString
			.replace("\n", "\\n")
			.replace("\t", "\\t");
		return "Constant(" + valueString + ")";
	}

	@Override
	public boolean immutabilityTranscendsReification ()
	{
		// In fact, constants are *always* immutable (and shared).
		return true;
	}
}
