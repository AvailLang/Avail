/**
 * ParsingConversionRule.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.TokenDescriptor.TokenType.LITERAL;
import com.avail.annotations.Nullable;
import com.avail.compiler.AbstractAvailCompiler.ParserState;
import com.avail.descriptor.*;
import com.avail.utility.evaluation.*;

/**
 * A {@code ParsingConversionRule} describes how to convert the argument at the
 * top of the parsing stack from one {@linkplain ParseNodeDescriptor phrase} to
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
			final A_Phrase input,
			final ParserState currentParserState,
			final ParserState initialParserState,
			final Continuation1<A_Phrase> continuation,
			final Continuation1<Throwable> onFailure)
		{
			continuation.value(input);
		}
	},

	/**
	 * {@code 1} - Convert a {@linkplain ListNodeDescriptor list} into a
	 * {@linkplain LiteralNodeDescriptor literal node} that yields an
	 * {@linkplain IntegerDescriptor integer} representing the {@linkplain
	 * AvailObject#tupleSize() size} of the original list.
	 */
	LIST_TO_SIZE(1)
	{
		@Override
		public void convert (
			final A_Phrase input,
			final ParserState currentParserState,
			final ParserState initialParserState,
			final Continuation1<A_Phrase> continuation,
			final Continuation1<Throwable> onFailure)
		{
			final A_Tuple expressions = input.expressionsTuple();
			final A_Number count = IntegerDescriptor.fromInt(
				expressions.tupleSize());
			final AvailObject token =
				LiteralTokenDescriptor.create(
					StringDescriptor.from(count.toString()),
					initialParserState.peekToken().leadingWhitespace(),
					initialParserState.peekToken().trailingWhitespace(),
					initialParserState.peekToken().start(),
					initialParserState.peekToken().lineNumber(),
					LITERAL,
					count);
			continuation.value(LiteralNodeDescriptor.fromToken(token));
		}
	},

	/**
	 * {@code 2} - Immediately evaluate the {@linkplain ParseNodeDescriptor
	 * parse node} on the stack to produce a value.  Replace the parse node with
	 * a literal node holding this value.
	 */
	EVALUATE_EXPRESSION(2)
	{
		@Override
		public void convert (
			final A_Phrase input,
			final ParserState currentParserState,
			final ParserState initialParserState,
			final Continuation1<A_Phrase> continuation,
			final Continuation1<Throwable> onProblem)
		{
			currentParserState.evaluatePhraseThen(
				input,
				new Continuation1<AvailObject>()
				{
					@Override
					public void value (final @Nullable AvailObject value)
					{
						// Wrap it as a literal phrase and pass it along.
						assert value != null;
						continuation.value(
							LiteralNodeDescriptor.syntheticFrom(value));
					}
				},
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
	 * @param input
	 *            The parse node to be converted.
	 * @param currentParserState
	 *            The parser state at which the conversion takes place.
	 * @param startingParserState
	 *            The parser state at which the provided parse node started.
	 * @param continuation
	 *            What to do with the replacement parse node.
	 * @param onFailure
	 *            What to do if a problem happens during conversion.
	 */
	public abstract void convert (
		final A_Phrase input,
		final ParserState currentParserState,
		final ParserState startingParserState,
		final Continuation1<A_Phrase> continuation,
		final Continuation1<Throwable> onFailure);

	/**
	 * Construct a new {@link ParsingConversionRule}.
	 *
	 * @param number The rule number.
	 */
	private ParsingConversionRule (final int number)
	{
		this.number = number;
	}

	/**
	 * Lookup the specified {@linkplain ParsingConversionRule conversion rule}
	 * by number.
	 *
	 * @param number The rule number.
	 * @return The appropriate parsing conversion rule.
	 */
	public static ParsingConversionRule ruleNumber (final int number)
	{
		if (number < values().length)
		{
			return values()[number];
		}
		throw new RuntimeException("reserved conversion rule number");
	}
}
