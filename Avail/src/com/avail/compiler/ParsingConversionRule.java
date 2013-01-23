/**
 * ParsingConversionRule.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import com.avail.compiler.AbstractAvailCompiler.ParserState;
import com.avail.descriptor.*;

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
	noConversion(0)
	{
		@Override
		public AvailObject convert (
			final AvailObject input,
			final ParserState startingParserState)
		{
			return input;
		}
	},

	/**
	 * {@code 1} - Convert a {@linkplain ListNodeDescriptor list} into a
	 * {@linkplain LiteralNodeDescriptor literal node} that yields an
	 * {@linkplain IntegerDescriptor integer} representing the {@linkplain
	 * AvailObject#tupleSize() size} of the original list.
	 */
	listToSize(1)
	{
		@Override
		public AvailObject convert (
			final AvailObject input,
			final ParserState startingParserState)
		{
			final AvailObject expressions =
				input.expressionsTuple();
			final AvailObject count = IntegerDescriptor.fromInt(
				expressions.tupleSize());
			final AvailObject token =
				LiteralTokenDescriptor.create(
					StringDescriptor.from(count.toString()),
					startingParserState.peekToken().start(),
					startingParserState.peekToken().lineNumber(),
					LITERAL,
					count);
			return LiteralNodeDescriptor.fromToken(token);
		}
	},

	/**
	 * {@code 2} - Convert a {@linkplain ListNodeDescriptor list} into a
	 * {@linkplain LiteralNodeDescriptor literal node} that yields a
	 * {@linkplain EnumerationTypeDescriptor#booleanObject() boolean}: {@link
	 * AtomDescriptor#trueObject() true} if the list was nonempty, {@link
	 * AtomDescriptor#falseObject() false} otherwise.
	 */
	listToNonemptiness(2)
	{
		@Override
		public AvailObject convert (
			final AvailObject input,
			final ParserState startingParserState)
		{
			final AvailObject expressions =
				input.expressionsTuple();
			final AvailObject nonempty =
				AtomDescriptor.objectFromBoolean(
					expressions.tupleSize() > 0);
			final AvailObject token =
				LiteralTokenDescriptor.create(
					StringDescriptor.from(nonempty.toString()),
					startingParserState.peekToken().start(),
					startingParserState.peekToken().lineNumber(),
					LITERAL,
					nonempty);
			return LiteralNodeDescriptor.fromToken(token);
		}
	},

	/**
	 * {@code 3} - Immediately evaluate the {@linkplain ParseNodeDescriptor
	 * parse node} on the stack to produce a value.  Replace the parse node with
	 * a literal node holding this value.
	 */
	evaluateExpression(3)
	{
		@Override
		public AvailObject convert (
			final AvailObject input,
			final ParserState startingParserState)
		{
			try
			{
				final AvailObject value =
					startingParserState.evaluate(input);
				return LiteralNodeDescriptor.syntheticFrom(value);
			}
			catch (final AvailRejectedParseException e)
			{
				startingParserState.expected(
					e.rejectionString().asNativeString());
				return NilDescriptor.nil();
			}
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
	 * @param input The value to be converted.
	 * @param startingParserState TODO
	 * @return The converted value.
	 */
	public abstract AvailObject convert (
		final AvailObject input,
		final ParserState startingParserState);

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
