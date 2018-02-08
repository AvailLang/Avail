/*
 * P_DeclarationInitializingExpression.java
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
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.DeclarationNodeDescriptor;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.VariableTypeDescriptor;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.objectFromBoolean;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind
	.DECLARATION_NODE;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.variableReadWriteType;
import static com.avail.exceptions.AvailErrorCode
	.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE;
import static com.avail.exceptions.AvailErrorCode
	.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> If the specified {@linkplain
 * DeclarationNodeDescriptor declaration} has an initializing {@linkplain
 * ParseNodeKind#EXPRESSION_NODE expression}, then store it in the provided
 * {@linkplain VariableTypeDescriptor variable}. Answer {@linkplain
 * AtomDescriptor#trueObject()} if a value was stored.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_DeclarationInitializingExpression
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_DeclarationInitializingExpression().init(
			2, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Variable var = interpreter.argument(0);
		final A_Phrase decl = interpreter.argument(1);
		final A_Phrase initializer = decl.initializationExpression();
		try
		{
			boolean stored = false;
			if (!initializer.equalsNil())
			{
				var.setValue(initializer);
				stored = true;
			}
			return interpreter.primitiveSuccess(objectFromBoolean(stored));
		}
		catch (final VariableSetException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				variableReadWriteType(TOP.o(), bottom()),
				DECLARATION_NODE.mostGeneralType()),
			booleanType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(
			E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
			E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED));
	}
}
