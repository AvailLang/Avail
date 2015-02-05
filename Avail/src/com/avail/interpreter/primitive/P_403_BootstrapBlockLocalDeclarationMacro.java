/**
 * P_403_BootstrapBlockLocalDeclarationMacro.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.*;

/**
 * The {@code P_403_BootstrapBlockLocalDeclarationMacro} primitive is used for
 * bootstrapping a {@linkplain DeclarationNodeDescriptor declaration} of a
 * {@link #LOCAL_VARIABLE} or {@link #LOCAL_CONSTANT} within a block.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_403_BootstrapBlockLocalDeclarationMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_403_BootstrapBlockLocalDeclarationMacro().init(
			4, Unknown, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 4;
		@SuppressWarnings("unused")
		final A_Tuple optionalArgumentDeclarations = args.get(0);
		@SuppressWarnings("unused")
		final A_Tuple optionalPrimitive = args.get(1);
		@SuppressWarnings("unused")
		final A_Tuple optionalLabel = args.get(2);
		final A_Tuple statementsSoFar = args.get(3);

		final A_Fiber fiber = interpreter.fiber();
		final AvailLoader loader = fiber.availLoader();
		final A_Map fiberGlobals = fiber.fiberGlobals();
		final A_Atom clientDataKey = AtomDescriptor.clientDataGlobalKey();
		if (loader == null || !fiberGlobals.hasKey(clientDataKey))
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}

		final A_Phrase latestStatement =
			statementsSoFar.tupleAt(statementsSoFar.tupleSize());
		// If it's a local variable or constant declaration then add it to the
		// scope; otherwise do nothing.
		if (latestStatement.parseNodeKindIsUnder(DECLARATION_NODE))
		{
			final DeclarationKind kind = latestStatement.declarationKind();
			if (kind == LOCAL_VARIABLE || kind == LOCAL_CONSTANT)
			{
				loader.addDeclaration(latestStatement);
			}
		}

		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Optional arguments section */
				TupleTypeDescriptor.zeroOrOneOf(
					/* Arguments are present */
					TupleTypeDescriptor.oneOrMoreOf(
						/* An argument */
						TupleTypeDescriptor.forTypes(
							/* Argument name */
							LITERAL_NODE.create(TOKEN.o()),
							/* Argument type */
							LITERAL_NODE.create(
								InstanceMetaDescriptor.anyMeta())))),
				/* Optional primitive section with optional failure variable:
				 * <<[0..65535], <primitive failure phrase |0..1> |2> |0..1>,
				 */
				TupleTypeDescriptor.zeroOrOneOf(
					TupleTypeDescriptor.forTypes(
						LITERAL_NODE.create(
							/* Primitive number */
							IntegerRangeTypeDescriptor.unsignedShorts()),
						TupleTypeDescriptor.zeroOrOneOf(
							/* The primitive failure variable is present */
							TupleTypeDescriptor.forTypes(
								/* Primitive failure variable name */
								LITERAL_NODE.create(TOKEN.o()),
								/* Primitive failure variable type */
								LITERAL_NODE.create(
									InstanceMetaDescriptor.anyMeta()))))),
				/* Optional label */
				TupleTypeDescriptor.zeroOrOneOf(
					/* Label is present */
					TupleTypeDescriptor.forTypes(
						/* Label name */
						LITERAL_NODE.create(TOKEN.o()),
						/* Label return type */
						LITERAL_NODE.create(InstanceMetaDescriptor.anyMeta()))),
				/* Statements */
				TupleTypeDescriptor.zeroOrMoreOf(
					EXPRESSION_NODE.mostGeneralType())),
			BLOCK_NODE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_OPERATION_NOT_SUPPORTED.numericCode(),
				E_LOADING_IS_OVER.numericCode(),
				E_PRIMITIVE_FALLIBILITY_DISAGREES_WITH_FAILURE_VARIABLE.numericCode(),
				E_INFALLIBLE_PRIMITIVE_MUST_NOT_HAVE_STATEMENTS.numericCode(),
				E_FINAL_EXPRESSION_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE.numericCode(),
				E_PRIMITIVE_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE.numericCode(),
				E_LABEL_TYPE_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE.numericCode(),
				E_RETURN_TYPE_IS_MANDATORY_WITH_PRIMITIVES_OR_LABELS.numericCode()
			).asSet());
	}
}
