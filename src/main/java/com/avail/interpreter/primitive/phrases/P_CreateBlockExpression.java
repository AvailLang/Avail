/*
 * P_CreateBlockExpression.java
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

import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BlockPhraseDescriptor;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BlockPhraseDescriptor.newBlockNode;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTypeDescriptor.exceptionType;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.ARGUMENT_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.BLOCK_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.containsOnlyStatements;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.exceptions.AvailErrorCode.E_BLOCK_CONTAINS_INVALID_STATEMENTS;
import static com.avail.exceptions.AvailErrorCode.E_INVALID_PRIMITIVE_NUMBER;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Create a {@linkplain BlockPhraseDescriptor
 * block expression} from the specified {@linkplain PhraseKind#ARGUMENT_PHRASE
 * argument declarations}, primitive number, statements, result type, and
 * exception set.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreateBlockExpression
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreateBlockExpression().init(
			5, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(5);
		final A_Tuple argDecls = interpreter.argument(0);
		final A_String primitiveName = interpreter.argument(1);
		final A_Tuple statements = interpreter.argument(2);
		final A_Type resultType = interpreter.argument(3);
		final A_Set exceptions = interpreter.argument(4);
		// Verify that each element of "statements" is actually a statement,
		// and that the last statement's expression type agrees with
		// "resultType".
		final List<A_Phrase> flat =
			new ArrayList<>(statements.tupleSize() + 3);
		for (final A_Phrase statement : statements)
		{
			statement.flattenStatementsInto(flat);
		}
		final int primNumber;
		if (primitiveName.tupleSize() > 0)
		{
			final @Nullable Primitive primitive =
				Primitive.primitiveByName(primitiveName.asNativeString());
			if (primitive == null)
			{
				return interpreter.primitiveFailure(E_INVALID_PRIMITIVE_NUMBER);
			}
			primNumber = primitive.primitiveNumber;
		}
		else
		{
			primNumber = 0;
		}
		if (!containsOnlyStatements(flat, resultType))
		{
			return interpreter.primitiveFailure(
				E_BLOCK_CONTAINS_INVALID_STATEMENTS);
		}
		final AvailObject block = newBlockNode(
			argDecls,
			primNumber,
			statements,
			resultType,
			exceptions,
			0,
			emptyTuple());
		return interpreter.primitiveSuccess(block);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				zeroOrMoreOf(ARGUMENT_PHRASE.mostGeneralType()),
				stringType(),
				zeroOrMoreOf(PARSE_PHRASE.mostGeneralType()),
				topMeta(),
				setTypeForSizesContentType(wholeNumbers(), exceptionType())),
			BLOCK_PHRASE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_BLOCK_CONTAINS_INVALID_STATEMENTS,
				E_INVALID_PRIMITIVE_NUMBER));
	}
}
