/*
 * P_CreatePhraseType.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.PhraseTypeDescriptor;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.exceptions.AvailErrorCode.E_BAD_YIELD_TYPE;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Create a variation of a {@linkplain
 * PhraseTypeDescriptor phrase type}.  In particular, create a phrase type of
 * the same {@linkplain PhraseKind kind} but with the specified expression type.
 */
public final class P_CreatePhraseType extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreatePhraseType().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final AvailObject baseType = interpreter.argument(0);
		final AvailObject expressionType = interpreter.argument(1);
		if (baseType.isBottom())
		{
			return interpreter.primitiveSuccess(baseType);
		}
		final PhraseKind kind = baseType.phraseKind();
		if (!expressionType.isSubtypeOf(kind.mostGeneralYieldType()))
		{
			return interpreter.primitiveFailure(E_BAD_YIELD_TYPE);
		}
		return interpreter.primitiveSuccess(kind.create(expressionType));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				instanceMeta(PARSE_PHRASE.mostGeneralType()),
				topMeta()),
			instanceMeta(PARSE_PHRASE.mostGeneralType()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return instanceType(E_BAD_YIELD_TYPE.numericCode());
	}
}
