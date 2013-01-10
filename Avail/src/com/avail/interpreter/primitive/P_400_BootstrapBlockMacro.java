/**
 * P_400_BootstrapBlockMacro.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_400_BootstrapBlockMacro} primitive is used for bootstrapping
 * the {@link BlockNodeDescriptor block} syntax for defining {@link
 * FunctionDescriptor functions}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class P_400_BootstrapBlockMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_400_BootstrapBlockMacro().init(7, Unknown, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 7;
		final AvailObject optionalArgumentDeclarations = args.get(0);
		final AvailObject optionalPrimitive = args.get(1);
		final AvailObject optionalLabel = args.get(2);
		final AvailObject statements = args.get(3);
		final AvailObject optionalReturnExpression = args.get(4);
		final AvailObject optionalReturnType = args.get(5);
		final AvailObject optionalExceptionTypes = args.get(6);

		final AvailObject argumentDeclarations =
			optionalArgumentDeclarations.tupleSize() == 0
				? TupleDescriptor.empty()
				: optionalArgumentDeclarations.tupleAt(1);
		int primitive = 0;
		@Nullable AvailObject optionalFailureDeclaration = null;
		if (optionalPrimitive.tupleSize() != 0)
		{
			primitive = optionalPrimitive.tupleIntAt(1);
			if (Primitive.byPrimitiveNumberOrNull(primitive) == null)
			{
				return interpreter.primitiveFailure(
					E_PRIMITIVE_NOT_SUPPORTED);
			}
			optionalFailureDeclaration = optionalPrimitive.tupleAt(2);
		}
		final AvailObject allStatements =
			TupleDescriptor.from(
				optionalFailureDeclaration,
				optionalLabel,
				statements,
				optionalReturnExpression
			).concatenateTuplesCanDestroy(false);
		final AvailObject returnType =
			optionalReturnType.tupleSize() != 0
				? optionalReturnType.tupleAt(1)
				: statements.tupleSize() != 0
					? statements.tupleAt(statements.tupleSize()).expressionType()
					: TOP.o();
		final AvailObject exceptionsSet =
			optionalExceptionTypes.tupleSize() == 0
				? SetDescriptor.empty()
				: optionalExceptionTypes.tupleAt(1).asSet();
		final AvailObject block = BlockNodeDescriptor.newBlockNode(
			argumentDeclarations,
			IntegerDescriptor.fromInt(primitive),
			allStatements,
			returnType,
			exceptionsSet,
			primitive);
		block.makeImmutable();
		return interpreter.primitiveSuccess(block);
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Optional tuple of arguments declarations: */
				TupleTypeDescriptor.zeroOrOneOf(
					TupleTypeDescriptor.zeroOrMoreOf(
						ARGUMENT_NODE.mostGeneralType())),
				/* Optional primitive section with optional failure variable:
				 * <<[0..65535], <primitive failure phrase |0..1> |2> |0..1>,
				 */
				TupleTypeDescriptor.zeroOrOneOf(
					TupleTypeDescriptor.forTypes(
						IntegerRangeTypeDescriptor.unsignedShorts(),
						TupleTypeDescriptor.zeroOrOneOf(
							PRIMITIVE_FAILURE_REASON_NODE.mostGeneralType()))),
				/* Optional label */
				TupleTypeDescriptor.zeroOrOneOf(
					LABEL_NODE.create(
						ContinuationTypeDescriptor.mostGeneralType())),
				/* Statements */
				TupleTypeDescriptor.zeroOrMoreOf(
					EXPRESSION_NODE.mostGeneralType()),
				/* Optional return expression */
				TupleTypeDescriptor.zeroOrOneOf(
					EXPRESSION_NODE.create(ANY.o())),
				/* Optional return type */
				TupleTypeDescriptor.zeroOrOneOf(
					InstanceMetaDescriptor.topMeta()),
				/* Optional tuple of exception types */
				TupleTypeDescriptor.zeroOrOneOf(
					TupleTypeDescriptor.oneOrMoreOf(
						ObjectTypeDescriptor.exceptionType()))),
			BLOCK_NODE.mostGeneralType());
	}

	@Override
	protected AvailObject privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_PRIMITIVE_NOT_SUPPORTED.numericCode()
			).asSet());
	}
}
