/**
 * P_408_BootstrapAssignmentMacro.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.primitive;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_408_BootstrapAssignmentMacro} primitive is used for
 * assignment statements.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class P_408_BootstrapAssignmentMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_408_BootstrapAssignmentMacro().init(
			2, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase variableUse = args.get(0);
		final A_Phrase valueExpression = args.get(1);

		final A_Phrase declaration = variableUse.declaration();
		if (!declaration.declarationKind().isVariable())
		{
			throw new AvailRejectedParseException(
				StringDescriptor.from(
					"a name of a variable, not a "
					+ declaration.declarationKind().nativeKindName()));
		}
		if (!valueExpression.expressionType().isSubtypeOf(
			declaration.declaredType()))
		{
			throw new AvailRejectedParseException(
				StringDescriptor.from(
					String.format(
						"assignment expression's type (%s) "
						+ "to match variable type (%s)",
						valueExpression.expressionType(),
						declaration.declaredType().expressionType())));
		}
		final A_Phrase assignment = AssignmentNodeDescriptor.from(
			variableUse,
			valueExpression,
			false);
		assignment.makeImmutable();
		return interpreter.primitiveSuccess(assignment);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Assignment variable */
				VARIABLE_USE_NODE.mostGeneralType(),
				/* Assignment value */
				EXPRESSION_NODE.mostGeneralType()),
			LOCAL_VARIABLE_NODE.mostGeneralType());
	}
}
