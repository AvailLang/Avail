/*
 * P_BootstrapLexerWholeNumberBody.java
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

package com.avail.interpreter.primitive.bootstrap.lexing;

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.IntegerDescriptor.cachedSquareOfQuintillion;
import static com.avail.descriptor.IntegerDescriptor.fromLong;
import static com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * The {@code P_BootstrapLexerWholeNumberBody} primitive is used for parsing
 * non-negative integer literal tokens.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("unused")
public final class P_BootstrapLexerWholeNumberBody
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapLexerWholeNumberBody().init(
			3, CannotFail, CanFold, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_String source = interpreter.argument(0);
		final A_Number sourcePositionInteger = interpreter.argument(1);
		final A_Number lineNumberInteger = interpreter.argument(2);

		final int startPosition = sourcePositionInteger.extractInt();
		final int digitCount = countDigits(source, startPosition);

		final A_String string = (A_String) source.copyTupleFromToCanDestroy(
			startPosition, startPosition + digitCount - 1, false);
		final A_Number number =
			readInteger(string, 1, digitCount);
		final A_Token token = literalToken(
			string, startPosition, lineNumberInteger.extractInt(), number);
		return interpreter.primitiveSuccess(set(tuple(token)));
	}

	/**
	 * Determine how many consecutive decimal digits occur in the string,
	 * starting at the given startPosition.
	 *
	 * @param string
	 *        The string to examine.
	 * @param startPosition
	 *        The start of the run of digits in the string.
	 * @return The number of consecutive digits that were found.
	 */
	private static int countDigits (
		final A_String string,
		final int startPosition)
	{
		final int stringSize = string.tupleSize();
		int position = startPosition;
		while (position <= stringSize
			&& Character.isDigit(string.tupleCodePointAt(position)))
		{
			position++;
		}
		assert position > startPosition;
		return position - startPosition;
	}

	/**
	 * Read an integer from a range of the string.
	 *
	 * @param string
	 *        The string to read digits from.
	 * @param startPosition
	 *        The position of the first digit to read.
	 * @param digitCount
	 *        The number of digits to read.
	 * @return A positive Avail {@linkplain IntegerDescriptor integer}.
	 */
	private static A_Number readInteger (
		final A_String string,
		final int startPosition,
		final int digitCount)
	{
		assert digitCount > 0;
		if (digitCount <= 18)
		{
			// Process a (potentially short) group.
			long value = 0;
			int position = startPosition;
			for (int i = digitCount; i > 0; i--)
			{
				value *= 10L;
				value += Character.digit(
					string.tupleCodePointAt(position++), 10);
			}
			return fromLong(value);
		}

		// Find N, the largest power of two times 18 that's less than the
		// digitCount.  Recurse on that many of the right digits to get R, then
		// recurse on the remaining left digits to get L, then compute
		// (10^18)^N * L + R.
		final int groupCount = (digitCount + 17) / 18;
		final int logGroupCount =
			31 - Integer.numberOfLeadingZeros(groupCount - 1);
		final int rightGroupCount = 1 << logGroupCount;
		assert rightGroupCount < groupCount;
		assert (rightGroupCount << 1) >= groupCount;
		assert logGroupCount >= 0;

		final int rightCount = 18 << logGroupCount;
		final A_Number leftPart = readInteger(
			string,
			startPosition,
			digitCount - rightCount);
		final A_Number rightPart = readInteger(
			string,
			startPosition + digitCount - rightCount,
			rightCount);
		final A_Number squareOfQuintillion =
			cachedSquareOfQuintillion(logGroupCount);
		final A_Number shiftedLeft =
			leftPart.timesCanDestroy(squareOfQuintillion, true);
		return shiftedLeft.plusCanDestroy(rightPart, true);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return lexerBodyFunctionType();
	}
}
