/**
 * P_BootstrapBlockMacro.java
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

package com.avail.interpreter.primitive.bootstrap;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.ArrayList;
import java.util.List;
import com.avail.annotations.Nullable;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

/**
 * The {@code P_BootstrapBlockMacro} primitive is used for bootstrapping
 * the {@link BlockNodeDescriptor block} syntax for defining {@link
 * FunctionDescriptor functions}.
 *
 * <p>The strategy is to invoke prefix functions as various checkpoints are
 * reached during block parsing.  The prefix functions are invoked with all
 * arguments that have been parsed up to that point.
 * <ul>
 * <li>After the open square bracket ("["), push the existing scope stack.</li>
 * <li>After parsing each argument declaration, invoke a prefix function that
 * adds it to the scope.</li>
 * <li>After parsing an optional primitive failure variable, invoke a prefix
 * function to add it to the scope.</li>
 * <li>After parsing an optional label declaration, invoke a prefix function to
 * add it to the scope.</li>
 * <li>When a statement is a local declaration (variable or constant), the macro
 * that builds it also adds it to the scope.</li>
 * <li>After the close square bracket ("]"), pop the scope stack.</li>
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
public final class P_BootstrapBlockMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_BootstrapBlockMacro().init(
			7, Unknown, Bootstrap);

	/** The key to the client parsing data in the fiber's environment. */
	final A_Atom clientDataKey = AtomDescriptor.clientDataGlobalKey();

	/** The key to the variable scope map in the client parsing data. */
	final A_Atom scopeMapKey = AtomDescriptor.compilerScopeMapKey();

	/** The key to the tuple of scopes to pop as blocks complete parsing. */
	final A_Atom scopeStackKey = AtomDescriptor.compilerScopeStackKey();

	/** The key to the all tokens tuple in the fiber's environment. */
	final A_Atom allTokensKey = AtomDescriptor.allTokensKey();

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 7;
		final A_Phrase optionalArgumentDeclarations = args.get(0);
		final A_Phrase optionalPrimitive = args.get(1);
		final A_Phrase optionalLabel = args.get(2);
		final A_Phrase statements = args.get(3);
		final A_Phrase optionalReturnExpression = args.get(4);
		final A_Phrase optionalReturnType = args.get(5);
		final A_Phrase optionalExceptionTypes = args.get(6);

		A_Map fiberGlobals = interpreter.fiber().fiberGlobals();
		if (!fiberGlobals.hasKey(clientDataKey))
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		A_Map clientData = fiberGlobals.mapAt(clientDataKey);
		if (!clientData.hasKey(scopeMapKey))
		{
			// It looks like somebody removed all the scope information.
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION);
		}
		if (!clientData.hasKey(allTokensKey))
		{
			// It looks like somebody removed the used tokens information.
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION);
		}
		// Primitive P_BootstrapPrefixEndOfBlockBody already popped the scope
		// stack to the map, then pushed the map with the block's local
		// declarations back onto the stack.  So we can simply look up the local
		// declarations in the top map of the stack, then pop it to nowhere when
		// we're done.
		if (!clientData.hasKey(scopeStackKey))
		{
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION);
		}
		A_Tuple scopeStack = clientData.mapAt(scopeStackKey);
		if (!scopeStack.isTuple() || scopeStack.tupleSize() == 0)
		{
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION);
		}
		final A_Map scopeMap = scopeStack.tupleAt(scopeStack.tupleSize());
		final A_Tuple tokens = clientData.mapAt(allTokensKey);

		final List<A_Phrase> allStatements = new ArrayList<>();

		assert optionalArgumentDeclarations.expressionsSize() <= 1;
		final A_Tuple argumentDeclarationPairs =
			optionalArgumentDeclarations.expressionsSize() == 0
				? TupleDescriptor.empty()
				: optionalArgumentDeclarations.expressionAt(1)
					.expressionsTuple();
		// Look up the names of the arguments that were declared in the first
		// prefix function.
		final List<A_Phrase> argumentDeclarationsList =
			new ArrayList<>(argumentDeclarationPairs.tupleSize());
		{
			for (final A_Phrase declarationPair : argumentDeclarationPairs)
			{
				final A_String declarationName =
					declarationPair.expressionAt(1).token().string();
				if (!scopeMap.hasKey(declarationName))
				{
					// The argument binding is missing.
					return interpreter.primitiveFailure(
						E_INCONSISTENT_PREFIX_FUNCTION);
				}
				argumentDeclarationsList.add(scopeMap.mapAt(declarationName));
			}
		}

		// Deal with the primitive declaration if present.
		boolean canHaveStatements = true;
		assert optionalPrimitive.expressionsSize() <= 1;
		final @Nullable Primitive prim;
		final int primNumber;
		final @Nullable A_Type primitiveReturnType;
		if (optionalPrimitive.expressionsSize() == 1)
		{
			final A_Phrase primPhrase = optionalPrimitive.expressionAt(1);
			final A_Phrase primNamePhrase = primPhrase.expressionAt(1);
			if (!primNamePhrase.parseNodeKindIsUnder(LITERAL_NODE))
			{
				throw new AvailRejectedParseException(
					"primitive specification to be a (compiler created) literal "
					+ "keyword token");
			}
			final A_String primName = primNamePhrase.token().string();
			prim = Primitive.byName(primName.asNativeString());
			if (prim == null)
			{
				return interpreter.primitiveFailure(
					E_INCONSISTENT_PREFIX_FUNCTION);
			}
			primNumber = prim.primitiveNumber;
			canHaveStatements = !prim.hasFlag(CannotFail);
			final A_Phrase optionalFailurePair = primPhrase.expressionAt(2);
			assert optionalFailurePair.expressionsSize() <= 1;
			if ((optionalFailurePair.expressionsSize() == 1)
				!= canHaveStatements)
			{
				throw new AvailRejectedParseException(
					!canHaveStatements
						? "infallible primitive function not to have statements"
						: "fallible primitive function to have statements");
			}
			if (optionalFailurePair.expressionsSize() == 1)
			{
				final A_Phrase failurePair =
					optionalFailurePair.expressionAt(1);
				final A_Token failureToken =
					failurePair.expressionAt(1).token();
				final A_String failureDeclarationName =
					failureToken.literal().string();
				if (!scopeMap.hasKey(failureDeclarationName))
				{
					// The primitive failure variable binding is missing.
					return interpreter.primitiveFailure(
						E_INCONSISTENT_PREFIX_FUNCTION);
				}
				final A_Phrase failureDeclaration =
					scopeMap.mapAt(failureDeclarationName);
				allStatements.add(failureDeclaration);
			}
			primitiveReturnType = prim.blockTypeRestriction().returnType();
		}
		else
		{
			prim = null;
			primNumber = 0;
			primitiveReturnType = null;
		}

		// Deal with the label if present.
		@Nullable A_Type labelReturnType = null;
		assert optionalLabel.expressionsSize() <= 1;
		if (optionalLabel.expressionsSize() == 1)
		{
			final A_Phrase presentLabel = optionalLabel.expressionAt(1);
			final A_Token labelToken = presentLabel.expressionAt(1).token();
			final A_String labelDeclarationName = labelToken.literal().string();
			if (!scopeMap.hasKey(labelDeclarationName))
			{
				// The label binding is missing.
				return interpreter.primitiveFailure(
					E_INCONSISTENT_PREFIX_FUNCTION);
			}
			final A_Phrase optionalLabelReturnTypePhrase =
				presentLabel.expressionAt(2);
			final A_Phrase label = scopeMap.mapAt(labelDeclarationName);
			allStatements.add(label);
			if (optionalLabelReturnTypePhrase.expressionsSize() == 1)
			{
				// Label's type was explicitly provided.
				labelReturnType =
					label.declaredType().functionType().returnType();
			}
		}

		// Deal with the statements.
		for (final A_Phrase statement : statements.expressionsTuple())
		{
			allStatements.add(statement.token().literal());
		}
		assert optionalReturnExpression.expressionsSize() <= 1;
		final A_Type deducedReturnType;
		if (optionalReturnExpression.expressionsSize() == 1)
		{
			final A_Phrase returnLiteralPhrase =
				optionalReturnExpression.expressionAt(1);
			assert returnLiteralPhrase.parseNodeKindIsUnder(LITERAL_NODE);
			final A_Phrase returnExpression =
				returnLiteralPhrase.token().literal();
			allStatements.add(returnExpression);
			deducedReturnType = labelReturnType == null
				? returnExpression.expressionType()
				: returnExpression.expressionType().typeUnion(labelReturnType);
		}
		else
		{
			if (prim != null && prim.hasFlag(CannotFail))
			{
				// An infallible primitive must have no statements.
				deducedReturnType = prim.blockTypeRestriction().returnType();
			}
			else
			{
				deducedReturnType = TOP.o();
			}
		}

		if (allStatements.size() > 0 && !canHaveStatements)
		{
			throw new AvailRejectedParseException(
				"infallible primitive function not to have statements");
		}

		final @Nullable A_Type declaredReturnType =
			optionalReturnType.expressionsSize() != 0
				? optionalReturnType.expressionAt(1).token().literal()
				: null;
		// Make sure the last expression's type ⊆ the declared return type, if
		// applicable.  Also make sure the primitive's return type ⊆ the
		// declared return type.  Finally, make sure that the label's return
		// type ⊆ the block's effective return type.
		if (declaredReturnType != null)
		{
			if (!deducedReturnType.isSubtypeOf(declaredReturnType))
			{
				throw new AvailRejectedParseException(
					labelReturnType == null
						? "final expression's type to agree with the declared "
							+ "return type"
						: "the union of the final expression's type and the "
							+ "label's declared type to agree with the "
							+ "declared return type");
			}
			if (primitiveReturnType != null
				&& !primitiveReturnType.isSubtypeOf(declaredReturnType))
			{
				throw new AvailRejectedParseException(
					"primitive's intrinsic return type to agree with the "
					+ "declared return type");
			}
			if (labelReturnType != null
				&& !labelReturnType.isSubtypeOf(declaredReturnType))
			{
				throw new AvailRejectedParseException(
					"label's declared return type to agree with the "
					+ "function's declared return type");
			}
		}
		else if (primitiveReturnType != null)
		{
			// If it's a primitive, then the block must declare an explicit
			// return type.
			throw new AvailRejectedParseException(
				"primitive function to declare its return type");
		}
		final A_Type returnType =
			declaredReturnType != null ? declaredReturnType : deducedReturnType;
		A_Set exceptionsSet = SetDescriptor.empty();
		if (optionalExceptionTypes.expressionsSize() == 1)
		{
			for (final A_Phrase exceptionTypePhrase
				: optionalExceptionTypes.expressionAt(1).expressionsTuple())
			{
				exceptionsSet = exceptionsSet.setWithElementCanDestroy(
					exceptionTypePhrase.token().literal(), true);
			}
			exceptionsSet = exceptionsSet.makeImmutable();
		}
		// The block's line number is the line of the first token that's part of
		// the block, even if it's part of a subexpression (which it won't be
		// with the core Avail syntax).
		final int lineNumber = tokens.tupleSize() == 0
			? 0
			: tokens.tupleAt(1).lineNumber();
		final A_Phrase block = BlockNodeDescriptor.newBlockNode(
			argumentDeclarationsList,
			primNumber,
			allStatements,
			returnType,
			exceptionsSet,
			lineNumber);
		block.makeImmutable();
		// Pop and discard the top entry from the scope stack.
		final A_Fiber fiber = interpreter.fiber();
		scopeStack = scopeStack.copyTupleFromToCanDestroy(
			1, scopeStack.tupleSize() - 1, true);
		clientData = clientData.mapAtPuttingCanDestroy(
			scopeStackKey, scopeStack, true);
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataKey, clientData, true);
		fiber.fiberGlobals(fiberGlobals.makeShared());
		return interpreter.primitiveSuccess(block);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional arguments section. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Arguments are present. */
						TupleTypeDescriptor.oneOrMoreOf(
							/* An argument. */
							TupleTypeDescriptor.forTypes(
								/* Argument name, a token. */
								TOKEN.o(),
								/* Argument type. */
								InstanceMetaDescriptor.anyMeta())))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional primitive declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Primitive declaration */
						TupleTypeDescriptor.forTypes(
							/* Primitive name. */
							TOKEN.o(),
							/* Optional failure variable declaration. */
							TupleTypeDescriptor.zeroOrOneOf(
								/* Primitive failure variable parts. */
								TupleTypeDescriptor.forTypes(
									/* Primitive failure variable name token */
									TOKEN.o(),
									/* Primitive failure variable type */
									InstanceMetaDescriptor.anyMeta()))))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional label declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Label parts. */
						TupleTypeDescriptor.forTypes(
							/* Label name */
							TOKEN.o(),
							/* Optional label return type. */
							TupleTypeDescriptor.zeroOrOneOf(
								/* Label return type. */
								InstanceMetaDescriptor.topMeta())))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Statements and declarations so far. */
					TupleTypeDescriptor.zeroOrMoreOf(
						/* The "_!" mechanism wrapped each statement inside a
						 * literal phrase, so expect a phrase here instead of
						 * TOP.o().
						 */
						STATEMENT_NODE.mostGeneralType())),
				/* Optional return expression */
				LIST_NODE.create(
					TupleTypeDescriptor.zeroOrOneOf(
						PARSE_NODE.create(ANY.o()))),
				/* Optional return type */
				LIST_NODE.create(
					TupleTypeDescriptor.zeroOrOneOf(
						InstanceMetaDescriptor.topMeta())),
				/* Optional tuple of exception types */
				LIST_NODE.create(
					TupleTypeDescriptor.zeroOrOneOf(
						TupleTypeDescriptor.oneOrMoreOf(
							ObjectTypeDescriptor.exceptionType())))),
			BLOCK_NODE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_LOADING_IS_OVER,
				E_INCONSISTENT_PREFIX_FUNCTION));
	}
}
