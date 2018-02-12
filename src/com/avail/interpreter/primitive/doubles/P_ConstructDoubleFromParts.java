/*
 * P_ConstructDoubleFromParts.java
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

package com.avail.interpreter.primitive.doubles;

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.DoubleDescriptor.fromDouble;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.integers;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.literalTokenType;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Construct a nonnegative {@linkplain A_Number
 * double} from parts supplied as {@linkplain A_Token literal tokens}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_ConstructDoubleFromParts
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ConstructDoubleFromParts().init(
			3, CannotFail, CanInline, CanFold);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Token integerPart = interpreter.argument(0);
		final A_Token fractionalPart = interpreter.argument(1);
		final A_Token exponentPart = interpreter.argument(2);

		// Since we expect that this primitive will only be used for building
		// floating-point literals, it doesn't need to be particularly
		// efficient. We therefore convert the different parts to strings,
		// compose a floating-point numeral, and then ask Java to parse and
		// convert. This is less efficient than doing the work ourselves, but
		// gives us the opportunity to leverage well-tested and tuned Java
		// library code.
		@SuppressWarnings("StringConcatenationMissingWhitespace")
		final String numeral =
			integerPart.string().asNativeString()
			+ "."
			+ fractionalPart.string().asNativeString()
			+ "e"
			+ exponentPart.string().asNativeString();
		final A_Number result;
		try
		{
			result = fromDouble(Double.valueOf(numeral));
		}
		catch (final NumberFormatException e)
		{
			assert false :
				"This shouldn't happen, since we control the numeral!";
			throw e;
		}
		return interpreter.primitiveSuccess(result);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				literalTokenType(wholeNumbers()),
				literalTokenType(wholeNumbers()),
				literalTokenType(integers())),
			DOUBLE.o());
	}
}
