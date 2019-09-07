/*
 * P_BootstrapBlockMacro.java
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.BlockPhraseDescriptor;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG;
import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_STACK_KEY;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.STATIC_TOKENS_KEY;
import static com.avail.descriptor.BlockPhraseDescriptor.newBlockNode;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.ObjectTypeDescriptor.exceptionType;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.BLOCK_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrOneOf;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_INCONSISTENT_PREFIX_FUNCTION;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.CannotFail;

/**
 * The {@code P_BootstrapBlockMacro} primitive is used for bootstrapping
 * the {@link BlockPhraseDescriptor block} syntax for defining {@link
 * FunctionDescriptor functions}.
 *
 * <p>The strategy is to invoke prefix functions as various checkpoints are
 * reached during block parsing.  The prefix functions are invoked with all
 * arguments that have been parsed up to that point.</p>
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
 *
 * <p>When the whole macro has been parsed, the actual parsed arguments are
 * passed to the macro body (i.e., this primitive).  The body function has to
 * look up any arguments, primitive failure variable, and/or label that may have
 * entered scope due to execution of a prefix function.  The body answers a
 * suitable replacement phrase, in this case a {@linkplain
 * BlockPhraseDescriptor block phrase}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapBlockMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapBlockMacro().init(
			7, CanInline, Bootstrap);

	/** The key to the client parsing data in the fiber's environment. */
	private final A_Atom clientDataKey = CLIENT_DATA_GLOBAL_KEY.atom;

	/** The key to the variable scope map in the client parsing data. */
	private final A_Atom scopeMapKey = COMPILER_SCOPE_MAP_KEY.atom;

	/** The key to the tuple of scopes to pop as blocks complete parsing. */
	private final A_Atom scopeStackKey = COMPILER_SCOPE_STACK_KEY.atom;

	/** The key to the all tokens tuple in the fiber's environment. */
	private final A_Atom staticTokensKey = STATIC_TOKENS_KEY.atom;

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(7);
		final A_Phrase optionalArgumentDeclarations = interpreter.argument(0);
		final A_Phrase optionalPrimitive = interpreter.argument(1);
		final A_Phrase optionalLabel = interpreter.argument(2);
		final A_Phrase statements = interpreter.argument(3);
		final A_Phrase optionalReturnExpression = interpreter.argument(4);
		final A_Phrase optionalReturnType = interpreter.argument(5);
		final A_Phrase optionalExceptionTypes = interpreter.argument(6);

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
		if (!clientData.hasKey(staticTokensKey))
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
		final A_Tuple tokens = clientData.mapAt(staticTokensKey);

		assert optionalArgumentDeclarations.expressionsSize() <= 1;
		final A_Tuple argumentDeclarationPairs =
			optionalArgumentDeclarations.expressionsSize() == 0
				? emptyTuple()
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
		assert optionalPrimitive.expressionsSize() <= 1;
		final @Nullable Primitive prim;
		final int primNumber;
		final @Nullable A_Type primitiveReturnType;
		boolean canHaveStatements = true;
		final List<A_Phrase> allStatements = new ArrayList<>();
		if (optionalPrimitive.expressionsSize() == 1)
		{
			final A_Phrase primPhrase = optionalPrimitive.expressionAt(1);
			final A_Phrase primNamePhrase = primPhrase.expressionAt(1);
			if (!primNamePhrase.phraseKindIsUnder(LITERAL_PHRASE))
			{
				throw new AvailRejectedParseException(
					STRONG,
					"primitive specification to be a (compiler created) "
					+ "literal keyword token");
			}
			final A_String primName = primNamePhrase.token().string();
			prim = Primitive.primitiveByName(primName.asNativeString());
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
					STRONG,
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
		assert optionalLabel.expressionsSize() <= 1;
		@Nullable A_Type labelReturnType = null;
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
			assert returnLiteralPhrase.phraseKindIsUnder(LITERAL_PHRASE);
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
				STRONG,
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
					STRONG,
					labelReturnType == null
						? "final expression's type ("
							+ deducedReturnType
							+ ") to agree with the declared return type ("
							+ declaredReturnType
							+ ")"
						: "the union ("
							+ deducedReturnType
							+ ") of the final expression's type and the "
							+ "label's declared type to agree with the "
							+ "declared return type ("
							+ declaredReturnType
							+ ")");
			}
			if (primitiveReturnType != null
				&& !primitiveReturnType.isSubtypeOf(declaredReturnType))
			{
				throw new AvailRejectedParseException(
					STRONG,
					"primitive's intrinsic return type ("
					+ primitiveReturnType
					+ ") to agree with the declared return type ("
					+ declaredReturnType
					+ ")");
			}
			if (labelReturnType != null
				&& !labelReturnType.isSubtypeOf(declaredReturnType))
			{
				throw new AvailRejectedParseException(
					STRONG,
					"label's declared return type ("
					+ labelReturnType
					+ ") to agree with the function's declared return type ("
					+ declaredReturnType
					+ ")");
			}
		}
		else if (primitiveReturnType != null)
		{
			// If it's a primitive, then the block must declare an explicit
			// return type.
			throw new AvailRejectedParseException(
				// In case we're trying a parse that stops before the type.
				WEAK,
				"primitive function to declare its return type");
		}
		final A_Type returnType =
			declaredReturnType != null ? declaredReturnType : deducedReturnType;
		A_Set exceptionsSet = emptySet();
		if (optionalExceptionTypes.expressionsSize() == 1)
		{
			for (final A_Phrase exceptionTypePhrase
				: optionalExceptionTypes.lastExpression().expressionsTuple())
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
		final A_Phrase block = newBlockNode(
			tupleFromList(argumentDeclarationsList),
			primNumber,
			tupleFromList(allStatements),
			returnType,
			exceptionsSet,
			lineNumber,
			tokens);
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
		return functionType(
			tupleFromArray(
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional arguments section. */
					zeroOrOneOf(
						/* Arguments are present. */
						oneOrMoreOf(
							/* An argument. */
							tupleTypeForTypes(
								/* Argument name, a token. */
								TOKEN.o(),
								/* Argument type. */
								anyMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional primitive declaration. */
					zeroOrOneOf(
						/* Primitive declaration */
						tupleTypeForTypes(
							/* Primitive name. */
							TOKEN.o(),
							/* Optional failure variable declaration. */
							zeroOrOneOf(
								/* Primitive failure variable parts. */
								tupleTypeForTypes(
									/* Primitive failure variable name token */
									TOKEN.o(),
									/* Primitive failure variable type */
									anyMeta()))))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional label declaration. */
					zeroOrOneOf(
						/* Label parts. */
						tupleTypeForTypes(
							/* Label name */
							TOKEN.o(),
							/* Optional label return type. */
							zeroOrOneOf(
								/* Label return type. */
								topMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Statements and declarations so far. */
					zeroOrMoreOf(
						/* The "_!" mechanism wrapped each statement inside a
						 * literal phrase, so expect a phrase here instead of
						 * TOP.o().
						 */
						STATEMENT_PHRASE.mostGeneralType())),
				/* Optional return expression */
				LIST_PHRASE.create(
					zeroOrOneOf(
						PARSE_PHRASE.create(ANY.o()))),
				/* Optional return type */
				LIST_PHRASE.create(
					zeroOrOneOf(
						topMeta())),
				/* Optional tuple of exception types */
				LIST_PHRASE.create(
					zeroOrOneOf(
						oneOrMoreOf(
							exceptionType())))),
			/* ...and produce a block phrase. */
			BLOCK_PHRASE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_INCONSISTENT_PREFIX_FUNCTION));
	}
}
