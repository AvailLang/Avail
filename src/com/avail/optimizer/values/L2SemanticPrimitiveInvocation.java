/**
 * L2SemanticPrimitiveInvocation.java
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
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Instruction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.AvailObject.multiplier;

/**
 * An {@link L2SemanticValue} which represents the invocation of some {@link
 * Primitive}.  The primitive doesn't have to be stable or side-effect free, but
 * in that case the actual {@link L2Instruction} must be supplied, to ensure it
 * isn't executed too many or too few times.
 */
final class L2SemanticPrimitiveInvocation extends L2SemanticValue
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
	 * The optional {@link L2Instruction} which executes the primitive.  Note
	 * that this can be {@code null} if the primitive is infallible, stable, and
	 * side-effect free for the given arguments.
	 */
	public final @Nullable L2Instruction optionalInstruction;

	/**
	 * The hash value of the receiver, computed during construction.
	 */
	public int hashOrZero;

	/**
	 * Create a new {@code L2SemanticArgument} semantic value.
	 *
	 * @param primitive
	 *        The primitive whose invocation is being represented.
	 * @param argumentSemanticValues
	 *        The argument semantic values.
	 * @param optionalInstruction
	 *        An {@link L2Instruction} that is the invocation of this primitive,
	 *        or {@code null} if the primitive is infallible, stable, and
	 *        side-effect free for the given arguments.
	 */
	public L2SemanticPrimitiveInvocation (
		final Primitive primitive,
		final List<L2SemanticValue> argumentSemanticValues,
		final @Nullable L2Instruction optionalInstruction)
	{
		this.primitive = primitive;
		this.argumentSemanticValues = new ArrayList<>(argumentSemanticValues);
		this.optionalInstruction = optionalInstruction;
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
			&& argumentSemanticValues.equals(invocation.argumentSemanticValues)
			&& optionalInstruction == invocation.optionalInstruction;
	}

	@Override
	public int hashCode ()
	{
		if (hashOrZero == 0)
		{
			int h = primitive.primitiveNumber;
			h *= multiplier;
			for (final L2SemanticValue argument : argumentSemanticValues)
			{
				h ^= argument.hashCode();
				h *= multiplier;
			}
			hashOrZero = h;
		}
		return hashOrZero;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("Invoke ");
		builder.append(primitive.name());
		builder.append("(");
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
		builder.append(")");
		if (optionalInstruction != null)
		{
			builder.append("[for instruction: ");
			builder.append(optionalInstruction.operation);
			if (optionalInstruction.offset() != -1)
			{
				builder.append("@");
				builder.append(optionalInstruction.offset());
			}
			builder.append("]");
		}
		return builder.toString();
	}
}
