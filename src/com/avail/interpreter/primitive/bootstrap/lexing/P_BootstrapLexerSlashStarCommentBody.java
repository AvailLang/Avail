/**
 * P_BootstrapLexerSlashStarCommentBody.java
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

import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;

import static com.avail.descriptor.CommentTokenDescriptor.newCommentToken;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * The {@code P_BootstrapLexerSlashStarCommentBody} primitive is used for
 * parsing slash-star star-slash delimited comment tokens.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapLexerSlashStarCommentBody extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_BootstrapLexerSlashStarCommentBody().init(
			3, CannotFail, CanFold, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_String source = args.get(0);
		final A_Number sourcePositionInteger = args.get(1);
		final A_Number startingLineNumber = args.get(2);

		final int sourceSize = source.tupleSize();
		final int startPosition = sourcePositionInteger.extractInt();
		int position = startPosition + 1;

		if (position > sourceSize || source.tupleCodePointAt(position) != '*')
		{
			// It didn't start with "/*", so it's not a comment.
			return interpreter.primitiveSuccess(emptyTuple());
		}
		position++;

		int depth = 1;
		while (true)
		{
			final int c = source.tupleCodePointAt(position);
			if (position >= sourceSize)
			{
				// There aren't two characters left, so it can't close the outer
				// nesting of the comment (with "*/").  Reject the lexing with a
				// suitable warning.
				throw new AvailRejectedParseException(
					"Missing '*/' to close (nestable) block comment");
			}

			// At least two characters are available to examine.
			if (c == '*' && source.tupleCodePointAt(position + 1) == '/')
			{
				// Close a nesting level.
				position += 2;
				depth--;
				if (depth == 0)
				{
					break;
				}
			}
			else if (c == '/' && source.tupleCodePointAt(position + 1) == '*')
			{
				// Open a new nesting level.
				position += 2;
				depth++;
			}
			else
			{
				position++;
			}
		}

		// A comment was successfully parsed.
		final A_Token token = newCommentToken(
			(A_String) source.copyTupleFromToCanDestroy(
				startPosition, position - 1, false),
			emptyTuple(),
			emptyTuple(),
			startPosition,
			startingLineNumber.extractInt());
		token.makeShared();
		return interpreter.primitiveSuccess(tuple(token));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(stringType(), naturalNumbers(), naturalNumbers()),
			zeroOrMoreOf(TOKEN.o()));
	}
}
