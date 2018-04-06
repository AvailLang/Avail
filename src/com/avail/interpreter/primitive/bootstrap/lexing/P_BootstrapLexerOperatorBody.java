/*
 * P_BootstrapLexerOperatorBody.java
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
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TokenDescriptor.TokenType.OPERATOR;
import static com.avail.descriptor.TokenDescriptor.newToken;
import static com.avail.interpreter.Primitive.Flag.*;

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
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapLexerOperatorBody().init(
			3, CannotFail, CanFold, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_String source = interpreter.argument(0);
		final A_Number sourcePositionInteger = interpreter.argument(1);
		final A_Number lineNumberInteger = interpreter.argument(2);

		final int sourceSize = source.tupleSize();
		final int startPosition = sourcePositionInteger.extractInt();

		final int c = source.tupleCodePointAt(startPosition);
		if (c == '/')
		{
			if (startPosition < sourceSize
				&& source.tupleCodePointAt(startPosition + 1) == '*')
			{
				// No solution in this case, but don't complain.
				return interpreter.primitiveSuccess(emptySet());
			}
		}
		final A_Token token = newToken(
			(A_String) source.copyTupleFromToCanDestroy(
				startPosition, startPosition, false),
			startPosition,
			lineNumberInteger.extractInt(),
			OPERATOR);
		return interpreter.primitiveSuccess(set(tuple(token.makeShared())));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return lexerBodyFunctionType();
	}
}
