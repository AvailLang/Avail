/**
 * P_BootstrapLexerOperatorBody.java
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

package com.avail.interpreter.primitive.bootstrap.lexing;

import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;

import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.CannotFail;

/**
 * The {@code P_BootstrapLexerOperatorBody} primitive is used for parsing
 * operator tokens.  These currently are a single character long.
 *
 * <p>Note that if a slash is encountered, and it's followed an asterisk, it
 * should reject the lexical scan, allowing the comment lexer to deal with it
 * instead.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapLexerOperatorBody extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_BootstrapLexerOperatorBody().init(
			3, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_String source = args.get(0);
		final A_Number sourcePositionInteger = args.get(1);
		final A_Number lineNumberInteger = args.get(2);

		final int sourceSize = source.tupleSize();
		final int startPosition = sourcePositionInteger.extractInt();

		final int c = source.tupleCodePointAt(startPosition);
		if (c == '/')
		{
			if (startPosition < sourceSize
				&& source.tupleCodePointAt(startPosition + 1) == '*')
			{
				// No solution in this case, but don't complain.
				return interpreter.primitiveSuccess(TupleDescriptor.empty());
			}
		}
		final A_Token token = TokenDescriptor.create(
			(A_String)source.copyTupleFromToCanDestroy(
				startPosition, startPosition, false),
			TupleDescriptor.empty(),
			TupleDescriptor.empty(),
			startPosition,
			lineNumberInteger.extractInt(),
			TokenType.OPERATOR);
		return interpreter.primitiveSuccess(
			TupleDescriptor.from(token.makeShared()));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringType(),
				IntegerRangeTypeDescriptor.naturalNumbers(),
				IntegerRangeTypeDescriptor.naturalNumbers()),
			TupleTypeDescriptor.zeroOrMoreOf(
				Types.TOKEN.o()));
	}
}
