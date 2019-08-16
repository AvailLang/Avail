/*
 * P_CreateLiteralToken.java
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

package com.avail.interpreter.primitive.phrases;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.LiteralTokenDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.literalTokenType;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.mostGeneralLiteralTokenType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Create a {@linkplain LiteralTokenDescriptor
 * literal token} with the specified literal value, {@linkplain A_Token#string()
 * lexeme}, {@linkplain A_Token#start() starting character position}, and
 * {@linkplain A_Token#lineNumber() line number}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreateLiteralToken
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreateLiteralToken().init(
			4, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(4);
		final A_BasicObject value = interpreter.argument(0);
		final A_String lexeme = interpreter.argument(1);
		final A_Number start = interpreter.argument(2);
		final A_Number line = interpreter.argument(3);
		return interpreter.primitiveSuccess(
			literalToken(
				lexeme,
				start.extractInt(),
				line.extractInt(),
				value));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type valueType = argumentTypes.get(0);
//		final A_Type lexemeType = argumentTypes.get(1);
//		final A_Type startType = argumentTypes.get(2);
//		final A_Type lineType = argumentTypes.get(3);

		return literalTokenType(valueType);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ANY.o(),
				stringType(),
				inclusive(0, (1L << 32) - 1),
				inclusive(0, (1L << 28) - 1)),
			mostGeneralLiteralTokenType());
	}
}
