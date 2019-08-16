/*
 * P_ListExpressions.java
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
import com.avail.descriptor.A_Type;
import com.avail.descriptor.ListPhraseDescriptor;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Answer the specified {@linkplain
 * ListPhraseDescriptor list}'s {@linkplain TupleDescriptor tuple} of
 * {@linkplain PhraseKind#EXPRESSION_PHRASE expressions}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_ListExpressions
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ListExpressions().init(
			1, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Phrase list = interpreter.argument(0);
		return interpreter.primitiveSuccess(list.expressionsTuple());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(
				tuple(
					LIST_PHRASE.mostGeneralType()),
				zeroOrMoreOf(EXPRESSION_PHRASE.create(ANY.o())));
	}
}
