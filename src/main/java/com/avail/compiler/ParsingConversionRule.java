/*
 * ParsingConversionRule.java
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

package com.avail.compiler;

import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.ListPhraseDescriptor;
import com.avail.descriptor.LiteralPhraseDescriptor;
import com.avail.descriptor.PhraseDescriptor;
import com.avail.utility.evaluation.Continuation1NotNull;

import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.LiteralPhraseDescriptor.literalNodeFromToken;
import static com.avail.descriptor.LiteralPhraseDescriptor.syntheticLiteralNodeFor;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.MacroSubstitutionPhraseDescriptor.newMacroSubstitution;
import static com.avail.descriptor.StringDescriptor.stringFrom;

/**
 * A {@code ParsingConversionRule} describes how to convert the argument at the
 * top of the parsing stack from one {@linkplain PhraseDescriptor phrase} to
 * another.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public enum ParsingConversionRule
{
	/**
	 * {@code 0} - No conversion.
	 */
	NO_CONVERSION(0)
	{
		@Override
		public void convert (
			final CompilationContext compilationContext,
			final LexingState lexingState,
			final A_Phrase input,
			final Continuation1NotNull<A_Phrase> continuation,
			final Continuation1NotNull<Throwable> onProblem)
		{
			continuation.value(input);
		}
	},

	/**
	 * {@code 1} - Convert a {@linkplain ListPhraseDescriptor list} into a
	 * {@linkplain LiteralPhraseDescriptor literal phrase} that yields an
	 * {@linkplain IntegerDescriptor integer} representing the {@linkplain
	 * AvailObject#tupleSize() size} of the original list.
	 */
	LIST_TO_SIZE(1)
	{
		@Override
		public void convert (
			final CompilationContext compilationContext,
			final LexingState lexingState,
			final A_Phrase input,
			final Continuation1NotNull<A_Phrase> continuation,
			final Continuation1NotNull<Throwable> onProblem)
		{
			final A_Tuple expressions = input.expressionsTuple();
			final A_Number count = fromInt(expressions.tupleSize());
			final A_Token token =
				literalToken(
					stringFrom(count.toString()),
					lexingState.position,
					lexingState.lineNumber,
					count);
			continuation.value(literalNodeFromToken(token));
		}
	},

	/**
	 * {@code 2} - Immediately evaluate the {@linkplain PhraseDescriptor
	 * phrase} on the stack to produce a value.  Replace the phrase with
	 * a literal phrase holding this value.
	 */
	EVALUATE_EXPRESSION(2)
	{
		@Override
		public void convert (
			final CompilationContext compilationContext,
			final LexingState lexingState,
			final A_Phrase input,
			final Continuation1NotNull<A_Phrase> continuation,
			final Continuation1NotNull<Throwable> onProblem)
		{
			compilationContext.evaluatePhraseAtThen(
				lexingState,
				input,
				value -> continuation.value(
					newMacroSubstitution(
						input, syntheticLiteralNodeFor(value))),
				onProblem);
		}
	};

	/** The rule number. */
	private final int number;

	/**
	 * Answer the rule number.
	 *
	 * @return The rule number.
	 */
	public int number ()
	{
		return number;
	}

	/**
	 * Convert an input {@link AvailObject} into an output AvailObject, using
	 * the specific conversion rule's implementation.
	 *
	 * @param compilationContext
	 *        The {@link CompilationContext} to use during conversion, if
	 *        needed.
	 * @param lexingState
	 *        The {@link LexingState} after the phrase.
	 * @param input
	 *        The phrase to be converted.
	 * @param continuation
	 *        What to do with the replacement phrase.
	 * @param onProblem
	 *        What to do if there's a problem.
	 */
	public abstract void convert (
		final CompilationContext compilationContext,
		final LexingState lexingState,
		final A_Phrase input,
		final Continuation1NotNull<A_Phrase> continuation,
		final Continuation1NotNull<Throwable> onProblem);

	/**
	 * Construct a new {@code ParsingConversionRule}.
	 *
	 * @param number The rule number.
	 */
	ParsingConversionRule (final int number)
	{
		this.number = number;
	}

	/** An array of all {@link ParsingConversionRule}s. */
	private static final ParsingConversionRule[] all = values();

	/**
	 * Lookup the specified {@code ParsingConversionRule conversion rule} by
	 * number.
	 *
	 * @param number The rule number.
	 * @return The appropriate parsing conversion rule.
	 */
	public static ParsingConversionRule ruleNumber (final int number)
	{
		if (number < all.length)
		{
			return all[number];
		}
		throw new RuntimeException("reserved conversion rule number");
	}
}
