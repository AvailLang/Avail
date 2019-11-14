/*
 * L2SemanticPrimitiveInvocation.java
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
import com.avail.interpreter.Primitive;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static java.util.Collections.unmodifiableList;

/**
 * An {@link L2SemanticValue} which represents the result produced by a {@link
 * Primitive} when supplied a list of argument {@link L2SemanticValue}s.  The
 * primitive must be stable (same result), pure (no side-effects), and
 * successful for the supplied arguments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2SemanticPrimitiveInvocation
extends L2SemanticValue
{
	/**
	 * The {@link Primitive} whose invocation is being represented.
	 */
	public final Primitive primitive;

	/**
	 * The {@link List} of {@link L2SemanticValue}s that represent the arguments
	 * to the invocation of the primitive.
	 */
	public final List<L2SemanticValue> argumentSemanticValues;

	/**
	 * The hash value of the receiver, computed during construction.
	 */
	private final int hash;

	/**
	 * Create a new {@code L2SemanticPrimitiveInvocation} semantic value.
	 *
	 * @param primitive
	 *        The primitive whose invocation is being represented.
	 * @param argumentSemanticValues
	 *        The semantic values supplied as arguments.
	 */
	L2SemanticPrimitiveInvocation (
		final Primitive primitive,
		final List<L2SemanticValue> argumentSemanticValues)
	{
		assert primitive.hasFlag(CanFold);
		this.primitive = primitive;
		this.argumentSemanticValues =
			unmodifiableList(new ArrayList<>(argumentSemanticValues));
		// Compute the hash.
		int h = primitive.getPrimitiveNumber() * multiplier;
		for (final L2SemanticValue argument : argumentSemanticValues)
		{
			h ^= argument.hashCode();
			h *= multiplier;
		}
		hash = h;
	}

	@Override
	public boolean equals (final Object obj)
	{
		if (!(obj instanceof L2SemanticPrimitiveInvocation))
		{
			return false;
		}
		if (hashCode() != obj.hashCode())
		{
			return false;
		}
		final L2SemanticPrimitiveInvocation
			invocation = (L2SemanticPrimitiveInvocation) obj;
		return primitive == invocation.primitive
			&& argumentSemanticValues.equals(invocation.argumentSemanticValues);
	}

	@Override
	public int hashCode ()
	{
		return hash;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(primitive.name());
		builder.append('(');
		boolean first = true;
		for (final L2SemanticValue arg : argumentSemanticValues)
		{
			if (!first)
			{
				builder.append(", ");
			}
			builder.append(arg);
			first = false;
		}
		builder.append(')');
		return builder.toString();
	}

	@Override
	public L2SemanticPrimitiveInvocation transform (
		final UnaryOperator<L2SemanticValue> semanticValueTransformer,
		final UnaryOperator<Frame> frameTransformer)
	{
		final int numArgs = argumentSemanticValues.size();
		final List<L2SemanticValue> newArguments = new ArrayList<>(numArgs);
		for (final L2SemanticValue argument : argumentSemanticValues)
		{
			newArguments.add(
				argument.transform(semanticValueTransformer, frameTransformer));
		}
		for (int i = 0; i < numArgs; i++)
		{
			if (!newArguments.get(i).equals(argumentSemanticValues.get(i)))
			{
				return new L2SemanticPrimitiveInvocation(
					primitive, newArguments);
			}
		}
		return this;
	}
}
