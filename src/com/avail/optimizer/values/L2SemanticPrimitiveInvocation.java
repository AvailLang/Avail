/*
 * L2SemanticPrimitiveInvocation.java
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
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2Synonym;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.utility.Nulls.stripNull;

/**
 * An {@link L2SemanticValue} which represents the invocation of some {@link
 * Primitive}.  The primitive doesn't have to be stable or side-effect free, but
 * in that case the actual {@link L2Instruction} must be supplied, to ensure it
 * isn't executed too many or too few times.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class L2SemanticPrimitiveInvocation
extends L2SemanticValue
{
	/**
	 * The {@link Frame} responsible for this invocation, or {@code null} for a
	 * pure {@link Primitive}. */
	private final @Nullable Frame frame;

	/**
	 * The program counter, or {@code 0} for pure {@link Primitive}s.
	 */
	public final int pc;

	/**
	 * The {@link Primitive} whose invocation is being represented.
	 */
	public final Primitive primitive;

	/**
	 * The {@link List} of {@link L2Synonym}s that represent the arguments to
	 * the invocation of the primitive.
	 */
	private final List<L2Synonym<L2ObjectRegister, A_BasicObject>>
		argumentSynonyms;

	/**
	 * The hash value of the receiver, computed during construction.
	 */
	private final int hash;

	/**
	 * Create a new {@code L2SemanticPrimitiveInvocation} semantic value.
	 *
	 * @param frame
	 *        The frame in which the primitive invocation occurs, or {@code
	 *        null} for pure {@link Primitive}s.
	 * @param pc
	 *        The program counter, or {@code 0} for pure {@link Primitive}s.
	 * @param primitive
	 *        The primitive whose invocation is being represented.
	 * @param argumentSynonyms
	 *        The semantic values.
	 */
	L2SemanticPrimitiveInvocation (
		final @Nullable Frame frame,
		final int pc,
		final Primitive primitive,
		final List<L2Synonym<L2ObjectRegister, A_BasicObject>> argumentSynonyms)
	{
		this.frame = frame;
		this.primitive = primitive;
		this.argumentSynonyms = new ArrayList<>(argumentSynonyms);
		this.pc = pc;
		// Compute the hash.
		int h = primitive.primitiveNumber * multiplier;
		h += frame == null ? 0x78296C0C : frame.hashCode();
		h ^= pc;
		h *= multiplier;
		for (final L2Synonym<?, ?> argument : argumentSynonyms)
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
		return pc == invocation.pc
			&& primitive == invocation.primitive
			&& argumentSynonyms.equals(invocation.argumentSynonyms);
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
		builder.append("Invoke");
		builder.append(primitive.name());
		builder.append('(');
		boolean first = true;
		for (final L2Synonym<?, ?> arg : argumentSynonyms)
		{
			if (!first)
			{
				builder.append(", ");
			}
			builder.append("syn(");
			builder.append(arg.defaultRegisterRead());
			builder.append(")");
			first = false;
		}
		builder.append(')');
		return builder.toString();
	}

	@Override
	public L2SemanticPrimitiveInvocation transform (
		final Function<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Function<Frame, Frame> frameTransformer)
	{
		final int numArgs = argumentSynonyms.size();
		final List<L2Synonym<L2ObjectRegister, A_BasicObject>> newArguments =
			new ArrayList<>(numArgs);
		for (final L2Synonym<L2ObjectRegister, A_BasicObject> argument :
			argumentSynonyms)
		{
			newArguments.add(argument.transform(semanticValueTransformer));
		}
		for (int i = 0; i < numArgs; i++)
		{
			if (!newArguments.get(i).equals(argumentSynonyms.get(i)))
			{
				return new L2SemanticPrimitiveInvocation(
					frameTransformer.apply(stripNull(frame)),
					pc,
					primitive,
					newArguments);
			}
		}
		return this;
	}

	@Override
	public <R extends L2Register<T>, T extends A_BasicObject>
	L2SemanticValue transformInnerSynonym (
		final L2Synonym<R, T> original,
		final L2Synonym<R, T> replacement)
	{
		// My arguments are synonyms, and they may contain additional semantic
		// values that are also synonyms.
		@Nullable List<L2Synonym<L2ObjectRegister, A_BasicObject>>
			newArguments = null;
		final int argCount = argumentSynonyms.size();
		for (int i = 0; i < argCount; i++)
		{
			final L2Synonym<L2ObjectRegister, A_BasicObject> oldSynonym =
				argumentSynonyms.get(i);
			final L2Synonym<L2ObjectRegister, A_BasicObject> newSynonym =
				oldSynonym.transformInnerSynonym(original, replacement);
			if (oldSynonym != newSynonym)
			{
				// It changed.
				if (newArguments == null)
				{
					newArguments = new ArrayList<>(argumentSynonyms);
				}
				newArguments.set(i, newSynonym);
			}
		}
		if (newArguments != null)
		{
			// At least one replacement happened.
			return new L2SemanticPrimitiveInvocation(
				frame, pc, primitive, newArguments);
		}
		return this;
	}
}
