/*
 * L2SemanticValue.java
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
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2Synonym;

import java.util.function.Function;

/**
 * An {@code L2SemanticValue} represents a value stably computed from constants,
 * arguments, and potentially unstable values acquired by specific previous
 * instructions – e.g., fetching the current time at a specific position in a
 * sequence of L2 instructions, or the result of a non-primitive call to another
 * function.
 */
@SuppressWarnings("AbstractClassWithoutAbstractMethods")
public abstract class L2SemanticValue
{
	/**
	 * Answer the semantic value representing a particular constant value.
	 *
	 * @param value The actual Avail value.
	 * @return A {@link L2SemanticConstant} representing the constant.
	 */
	public static L2SemanticValue constant (final A_BasicObject value)
	{
		return new L2SemanticConstant(value);
	}

	/**
	 * Answer the semantic value like the receiver, but wrapped to qualify that
	 * it's been unboxed as an {@code int}.
	 *
	 * @return The {@link L2SemanticUnboxedInt}.
	 */
	public L2SemanticUnboxedInt unboxedAsInt ()
	{
		return new L2SemanticUnboxedInt(this);
	}

	/**
	 * Answer the semantic value like the receiver, but wrapped to qualify that
	 * it's been unboxed as a {@code double}.
	 *
	 * @return The {@link L2SemanticUnboxedFloat}.
	 */
	public L2SemanticUnboxedFloat unboxedAsFloat ()
	{
		return new L2SemanticUnboxedFloat(this);
	}

	/**
	 * Answer the semantic value like the receiver, but boxed.  Subclasses
	 * should override if they represent unboxed values.
	 *
	 * @return The {@code L2SemanticValue}.
	 */
	public L2SemanticValue boxed ()
	{
		throw new UnsupportedOperationException(
			"Semantic value is already boxed.");
	}

	/**
	 * Transform the receiver.  If it's composed of parts, transform them with
	 * the supplied {@link Function}s.
	 *
	 * @param semanticValueTransformer
	 *        How to transform {@code L2SemanticValue} parts of the receiver,
	 *        (not the receiver itself).
	 * @param frameTransformer
	 *        How to transform {@link Frame} parts of the receiver.
	 * @return The transformed {@code L2SemanticValue}, possibly the receiver if
	 *         the result of the transformation would have been an equal value.
	 */
	public abstract L2SemanticValue transform (
		final Function<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Function<Frame, Frame> frameTransformer);

	/**
	 * Transform the receiver by rewriting any internal reference to the given
	 * original {@link L2Synonym} to refer instead to the replacement synonym.
	 * Answer the receiver if no changes were actually needed.
	 *
	 * @param original
	 *        The {@link L2Synonym} to look for recursively.
	 * @param replacement
	 *        The {@link L2Synonym} to replace each occurrence of the original.
	 * @param <R>
	 *        The {@link L2Register} subclass for registers of either synonym.
	 * @param <T>
	 *        The {@link A_BasicObject} subclass for {@link TypeRestriction}s of
	 *        the registers.
	 * @return Either the receiver if no transformation was needed, or semantic
	 *         value to replace the receiver.
	 */
	public <R extends L2Register<T>, T extends A_BasicObject>
	L2SemanticValue transformInnerSynonym (
		final L2Synonym<R, T> original,
		final L2Synonym<R, T> replacement)
	{
		return this;
	}

	/**
	 * Is the receiver an unboxed {@code int}?
	 *
	 * @return {@code true} if the receiver is an unboxed {@code int}, {@code
	 *         false} otherwise.
	 */
	public boolean isUnboxedInt ()
	{
		return false;
	}

	/**
	 * Is the receiver an unboxed {@code double}?
	 *
	 * @return {@code true} if the receiver is an unboxed {@code double}, {@code
	 *         false} otherwise.
	 */
	public boolean isUnboxedFloat ()
	{
		return false;
	}
}
