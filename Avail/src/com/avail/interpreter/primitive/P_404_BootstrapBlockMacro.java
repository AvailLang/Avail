/**
 * P_404_BootstrapBlockMacro.java
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

package com.avail.interpreter.primitive;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_404_BootstrapBlockMacro} primitive is used for bootstrapping
 * the {@link BlockNodeDescriptor block} syntax for defining {@link
 * FunctionDescriptor functions}.
 *
 * <p>The strategy is to invoke prefix functions as various checkpoints are
 * reached during block parsing.  The prefix functions are invoked with all
 * arguments that have been parsed up to that point.
 * <ul>
 * <li>After parsing each argument declaration, invoke a prefix function that
 * adds it to the scope.</li>
 * <li>After parsing an optional primitive failure variable, invoke a prefix
 * function to add it to the scope.</li>
 * <li>After parsing an optional label declaration, invoke a prefix function to
 * add it to the scope.</li>
 * <li>After parsing a statement, invoke a prefix function to check if it's a
 * declaration, and if so add it to the scope.
 * </ul>
 * </p>
 *
 * <p>When the whole macro has been parsed, the actual parsed arguments are
 * passed to the macro body (i.e., this primitive).  The body function has to
 * look up any arguments, primitive failure variable, and/or label that may have
 * entered scope due to execution of a prefix function.  The body answers a
 * suitable replacement parse node, in this case a {@linkplain
 * BlockNodeDescriptor block node}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_404_BootstrapBlockMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_404_BootstrapBlockMacro().init(
			7, Unknown, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 7;
		final A_Tuple optionalArgumentDeclarations = args.get(0);
		final A_Tuple optionalPrimitive = args.get(1);
		final A_Tuple optionalLabel = args.get(2);
		final A_Tuple statements = args.get(3);
		final A_Tuple optionalReturnExpression = args.get(4);
		final A_Tuple optionalReturnType = args.get(5);
		final A_Tuple optionalExceptionTypes = args.get(6);

		final A_Map fiberGlobals = interpreter.fiber().fiberGlobals();
		final A_Atom clientDataKey = AtomDescriptor.clientDataGlobalKey();
		if (!fiberGlobals.hasKey(clientDataKey))
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Map clientData = fiberGlobals.mapAt(clientDataKey);
		final A_Atom scopeMapKey = AtomDescriptor.compilerScopeMapKey();
		if (!clientData.hasKey(scopeMapKey))
		{
			// It looks like somebody removed all the scope information.
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Map scopeMap = clientData.mapAt(scopeMapKey);

		final List<A_Phrase> allStatements = new ArrayList<>();

		final A_Tuple argumentDeclarationPairs =
			optionalArgumentDeclarations.tupleSize() == 0
				? TupleDescriptor.empty()
				: optionalArgumentDeclarations.tupleAt(1);
		// Look up the names of the arguments that were declared in the first
		// prefix function.
		final List<A_Phrase> argumentDeclarationsList =
			new ArrayList<>(argumentDeclarationPairs.tupleSize());
		{
			for (final A_Tuple declarationPair : argumentDeclarationPairs)
			{
				final A_String declarationName =
					declarationPair.tupleAt(1).token().string();
				if (!scopeMap.hasKey(declarationName))
				{
					// The argument binding is missing.
					return interpreter.primitiveFailure(E_LOADING_IS_OVER);
				}
				argumentDeclarationsList.add(scopeMap.mapAt(declarationName));
			}
		}

		// Deal with the primitive declaration if present.
		int primNumber = 0;
		boolean canHaveStatements = true;
		assert optionalPrimitive.tupleSize() <= 1;
		@Nullable A_Type primitiveReturnType = null;
		if (optionalPrimitive.tupleSize() != 0)
		{
			final A_Tuple primitivePart = optionalPrimitive.tupleAt(1);
			primNumber = primitivePart.tupleIntAt(1);
			final Primitive primitive =
				Primitive.byPrimitiveNumberOrNull(primNumber);
			if (primitive == null)
			{
				return interpreter.primitiveFailure(E_OPERATION_NOT_SUPPORTED);
			}
			canHaveStatements = !primitive.hasFlag(CannotFail);
			final A_Tuple optionalFailurePairs = optionalPrimitive.tupleAt(2);
			assert optionalFailurePairs.tupleSize() <= 1;
			if ((optionalFailurePairs.tupleSize() == 1) != canHaveStatements)
			{
				return interpreter.primitiveFailure(
					E_PRIMITIVE_FALLIBILITY_DISAGREES_WITH_FAILURE_VARIABLE);
			}
			if (optionalFailurePairs.tupleSize() == 1)
			{
				final A_String failureDeclarationName =
					optionalFailurePairs.tupleAt(1).tupleAt(1);
				if (!scopeMap.hasKey(failureDeclarationName))
				{
					// The primitive failure variable binding is missing.
					return interpreter.primitiveFailure(E_LOADING_IS_OVER);
				}
				final A_Phrase failureDeclaration =
					scopeMap.mapAt(failureDeclarationName);
				allStatements.add(failureDeclaration);
			}
			primitiveReturnType = primitive.blockTypeRestriction().returnType();
		}

		// Deal with the label if present.
		@Nullable A_Type labelReturnType = null;
		assert optionalLabel.tupleSize() <= 1;
		if (optionalLabel.tupleSize() == 1)
		{
			final A_String labelDeclarationName =
				optionalLabel.tupleAt(1).tupleAt(1);
			if (!scopeMap.hasKey(labelDeclarationName))
			{
				// The label binding is missing.
				return interpreter.primitiveFailure(E_LOADING_IS_OVER);
			}
			final A_Phrase label = scopeMap.mapAt(labelDeclarationName);
			allStatements.add(label);
			labelReturnType = label.declaredType().functionType().returnType();
		}

		for (final A_Phrase statement : statements)
		{
			allStatements.add(statement);
		}
		assert optionalReturnExpression.tupleSize() <= 1;
		final A_Type deducedReturnType;
		if (optionalReturnExpression.tupleSize() == 1)
		{
			final A_Phrase returnExpression =
				optionalReturnExpression.tupleAt(1);
			allStatements.add(returnExpression);
			deducedReturnType = returnExpression.expressionType();
		}
		else
		{
			deducedReturnType = TOP.o();
		}

		if (allStatements.size() > 0 && !canHaveStatements)
		{
			return interpreter.primitiveFailure(
				E_INFALLIBLE_PRIMITIVE_MUST_NOT_HAVE_STATEMENTS);
		}

		final @Nullable A_Type declaredReturnType =
			optionalReturnType.tupleSize() != 0
				? optionalReturnType.tupleAt(1)
				: null;
		// Make sure the last expression's type <= the declared return type, if
		// applicable.  Also make sure the primitive's return type <= the
		// declared return type.  Finally, make sure that the label's return
		// type <= the block's effective return type.
		if (declaredReturnType != null)
		{
			if (!deducedReturnType.isSubtypeOf(declaredReturnType))
			{
				return interpreter.primitiveFailure(
					E_FINAL_EXPRESSION_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE);
			}
			if (primitiveReturnType != null
				&& !primitiveReturnType.isSubtypeOf(declaredReturnType))
			{
				return interpreter.primitiveFailure(
					E_PRIMITIVE_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE);
			}
			if (labelReturnType != null
				&& !labelReturnType.isSubtypeOf(declaredReturnType))
			{
				return interpreter.primitiveFailure(
					E_LABEL_TYPE_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE);
			}
		}
		else
		{
			if (labelReturnType != null || primitiveReturnType != null)
			{
				return interpreter.primitiveFailure(
					E_RETURN_TYPE_IS_MANDATORY_WITH_PRIMITIVES_OR_LABELS);
			}
		}
		final A_Type returnType =
			declaredReturnType != null
				? declaredReturnType
				: deducedReturnType;
		final A_Set exceptionsSet =
			optionalExceptionTypes.tupleSize() == 0
				? SetDescriptor.empty()
				: optionalExceptionTypes.tupleAt(1).asSet();
		final A_Phrase block = BlockNodeDescriptor.newBlockNode(
			allStatements,
			primNumber,
			allStatements,
			returnType,
			exceptionsSet,
			primNumber);
		block.makeImmutable();
		return interpreter.primitiveSuccess(block);
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
