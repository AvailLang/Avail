/**
 * P_350_CreateAssignmentExpression.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.Arrays;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 350:</strong> Transform a variable reference and an
 * expression into an inner {@linkplain AssignmentNodeDescriptor assignment
 * node}. Such a node also produces the assigned value as its result, so it
 * can be embedded as a subexpression.
 */
public final class P_350_CreateAssignmentExpression extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_350_CreateAssignmentExpression().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase variable = args.get(0);
		final A_Phrase expression = args.get(1);

		final A_Phrase declaration = variable.declaration();
		if (!declaration.parseNodeKindIsUnder(MODULE_VARIABLE_NODE)
			&& !declaration.parseNodeKindIsUnder(LOCAL_VARIABLE_NODE))
		{
			return interpreter.primitiveFailure(
				E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT);
		}
		if (!expression.expressionType().isSubtypeOf(
			variable.expressionType()))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		final A_Phrase assignment = AssignmentNodeDescriptor.from(
			variable, expression, true);
		return interpreter.primitiveSuccess(assignment);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				VARIABLE_USE_NODE.mostGeneralType(),
				EXPRESSION_NODE.create(ANY.o())),
			ASSIGNMENT_NODE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(Arrays.asList(
				E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT.numericCode(),
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE.numericCode())));
	}
}