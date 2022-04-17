/*
 * P_BootstrapBlockMacro.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.bootstrap.syntax

import avail.compiler.AvailRejectedParseException
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_STACK_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.STATIC_TOKENS_KEY
import avail.descriptor.fiber.A_Fiber.Companion.fiberGlobals
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Exceptions.exceptionType
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.declaredType
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.BlockPhraseDescriptor
import avail.descriptor.phrases.BlockPhraseDescriptor.Companion.newBlockNode
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.generateSetFrom
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.functionType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.BLOCK_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.exceptions.AvailErrorCode.E_INCONSISTENT_PREFIX_FUNCTION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.PrimitiveHolder.Companion.primitiveByName
import avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapBlockMacro` primitive is used for bootstrapping the
 * [block][BlockPhraseDescriptor] syntax for defining
 * [functions][FunctionDescriptor].
 *
 * The strategy is to invoke prefix functions as various checkpoints are reached
 * during block parsing.  The prefix functions are invoked with all arguments
 * that have been parsed up to that point.
 *
 *  * After the open square bracket ("["), push the existing scope stack.
 *  * After parsing each argument declaration, invoke a prefix function that
 *    adds it to the scope.
 *  * After parsing an optional primitive failure variable, invoke a prefix
 *    function to add it to the scope.
 *  * After parsing an optional label declaration, invoke a prefix function to
 *    add it to the scope.
 *  * When a statement is a local declaration (variable or constant), the macro
 *    that builds it also adds it to the scope.
 *  * After the close square bracket ("]"), pop the scope stack.
 *
 *
 * When the whole macro has been parsed, the actual parsed arguments are passed
 * to the macro body (i.e., this primitive).  The body function has to look up
 * any arguments, primitive failure variable, and/or label that may have entered
 * scope due to execution of a prefix function.  The body answers a suitable
 * replacement phrase, in this case a [block&#32;phrase][BlockPhraseDescriptor].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused", "GrazieInspection")
object P_BootstrapBlockMacro : Primitive(7, CanInline, Bootstrap)
{
	/** The key to the client parsing data in the fiber's environment. */
	private val clientDataKey = CLIENT_DATA_GLOBAL_KEY.atom

	/** The key to the variable scope map in the client parsing data. */
	private val scopeMapKey = COMPILER_SCOPE_MAP_KEY.atom

	/** The key to the tuple of scopes to pop as blocks complete parsing. */
	private val scopeStackKey = COMPILER_SCOPE_STACK_KEY.atom

	/** The key to the all tokens tuple in the fiber's environment. */
	private val staticTokensKey = STATIC_TOKENS_KEY.atom

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(7)
		val optionalArgumentDeclarations = interpreter.argument(0)
		val optionalPrimitive = interpreter.argument(1)
		val optionalLabel = interpreter.argument(2)
		val statements = interpreter.argument(3)
		val optionalReturnExpression = interpreter.argument(4)
		val optionalReturnType = interpreter.argument(5)
		val optionalExceptionTypes = interpreter.argument(6)

		var fiberGlobals = interpreter.fiber().fiberGlobals
		var clientData: A_Map = fiberGlobals.mapAtOrNull(clientDataKey) ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!clientData.hasKey(scopeMapKey))
		{
			// It looks like somebody removed all the scope information.
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION)
		}
		val tokens = clientData.mapAtOrNull(staticTokensKey) ?:
			// It looks like somebody removed the used tokens information.
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION)
		// Primitive P_BootstrapPrefixEndOfBlockBody already popped the scope
		// stack to the map, then pushed the map with the block's local
		// declarations back onto the stack.  So we can simply look up the local
		// declarations in the top map of the stack, then pop it to nowhere when
		// we're done.
		var scopeStack: A_Tuple = clientData.mapAtOrNull(scopeStackKey) ?:
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION)
		if (!scopeStack.isTuple || scopeStack.tupleSize == 0)
		{
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION)
		}
		val scopeMap = scopeStack.tupleAt(scopeStack.tupleSize)

		assert(optionalArgumentDeclarations.expressionsSize <= 1)

		val argumentDeclarationPairs =
			if (optionalArgumentDeclarations.expressionsSize == 0)
			{ emptyTuple }
			else
			{
				optionalArgumentDeclarations.expressionAt(1).expressionsTuple
			}

		// Look up the names of the arguments that were declared in the first
		// prefix function.
		val argumentDeclarationsList = mutableListOf<A_Phrase>()
		for (declarationPair in argumentDeclarationPairs)
		{
			val declarationName =
				declarationPair.expressionAt(1).token.string()
			val declaration = scopeMap.mapAtOrNull(declarationName) ?:
				// The argument binding is missing.
				return interpreter.primitiveFailure(
					E_INCONSISTENT_PREFIX_FUNCTION)
			argumentDeclarationsList.add(declaration)
		}

		// Deal with the primitive declaration if present.
		assert(optionalPrimitive.expressionsSize <= 1)
		val primitive: Primitive?
		val primitiveReturnType: A_Type?
		var canHaveStatements = true
		val allStatements = mutableListOf<A_Phrase>()
		if (optionalPrimitive.expressionsSize == 1)
		{
			val primPhrase = optionalPrimitive.expressionAt(1)
			val primNamePhrase = primPhrase.expressionAt(1)
			if (!primNamePhrase.phraseKindIsUnder(LITERAL_PHRASE))
			{
				throw AvailRejectedParseException(
					STRONG,
					"primitive specification to be a (compiler created) "
						+ "literal keyword token")
			}
			val primName = primNamePhrase.token.string()
			primitive = primitiveByName(primName.asNativeString())
			if (primitive === null)
			{
				return interpreter.primitiveFailure(
					E_INCONSISTENT_PREFIX_FUNCTION)
			}
			canHaveStatements = !primitive.hasFlag(CannotFail)
			val optionalFailurePair = primPhrase.expressionAt(2)
			assert(optionalFailurePair.expressionsSize <= 1)
			if (optionalFailurePair.expressionsSize == 1 != canHaveStatements)
			{
				throw AvailRejectedParseException(
					STRONG,
					if (!canHaveStatements)
						"infallible primitive function not to have statements"
					else
						"fallible primitive function to have statements")
			}
			if (optionalFailurePair.expressionsSize == 1)
			{
				val failurePair = optionalFailurePair.expressionAt(1)
				val failureToken = failurePair.expressionAt(1).token
				val failureDeclarationName = failureToken.literal().string()
				val failureDeclaration =
						scopeMap.mapAtOrNull(failureDeclarationName) ?:
					// The primitive failure variable binding is missing.
					return interpreter.primitiveFailure(
						E_INCONSISTENT_PREFIX_FUNCTION)
				allStatements.add(failureDeclaration)
			}
			primitiveReturnType = primitive.blockTypeRestriction().returnType
		}
		else
		{
			primitive = null
			primitiveReturnType = null
		}

		// Deal with the label if present.
		assert(optionalLabel.expressionsSize <= 1)
		var labelReturnType: A_Type? = null
		if (optionalLabel.expressionsSize == 1)
		{
			val presentLabel = optionalLabel.expressionAt(1)
			val labelToken = presentLabel.expressionAt(1).token
			val labelDeclarationName = labelToken.literal().string()
			val label = scopeMap.mapAtOrNull(labelDeclarationName) ?:
				// The label binding is missing.
				return interpreter.primitiveFailure(
					E_INCONSISTENT_PREFIX_FUNCTION)
			val optionalLabelReturnTypePhrase = presentLabel.expressionAt(2)
			allStatements.add(label)
			if (optionalLabelReturnTypePhrase.expressionsSize == 1)
			{
				// Label's type was explicitly provided.
				labelReturnType = label.declaredType.functionType.returnType
			}
		}

		// Deal with the statements.
		for (statement in statements.expressionsTuple)
		{
			allStatements.add(statement.token.literal())
		}
		assert(optionalReturnExpression.expressionsSize <= 1)
		val deducedReturnType: A_Type = when
		{
			optionalReturnExpression.expressionsSize == 1 ->
			{
				val returnLiteralPhrase =
					optionalReturnExpression.expressionAt(1)
				assert(returnLiteralPhrase.phraseKindIsUnder(LITERAL_PHRASE))
				val returnExpression = returnLiteralPhrase.token.literal()
				allStatements.add(returnExpression)
				if (labelReturnType === null)
				{
					returnExpression.phraseExpressionType
				}
				else
				{
					returnExpression.phraseExpressionType.typeUnion(
						labelReturnType)
				}
			}
			primitive !== null && primitive.hasFlag(CannotFail) ->
			{
				// An infallible primitive must have no statements.
				primitive.blockTypeRestriction().returnType
			}
			else -> TOP.o
		}

		if (allStatements.size > 0 && !canHaveStatements)
		{
			throw AvailRejectedParseException(
				STRONG,
				"infallible primitive function not to have statements")
		}

		val declaredReturnType = when
		{
			optionalReturnType.expressionsSize != 0 ->
				optionalReturnType.expressionAt(1).token.literal()
			else -> null
		}
		// Make sure the last expression's type ⊆ the declared return type, if
		// applicable.  Also make sure the primitive's return type ⊆ the
		// declared return type.  Finally, make sure that the label's return
		// type ⊆ the block's effective return type.
		if (declaredReturnType !== null)
		{
			if (!deducedReturnType.isSubtypeOf(declaredReturnType))
			{
				throw AvailRejectedParseException(
					STRONG,
					labelReturnType?.let {
						"the union ($deducedReturnType) of the final " +
							"expression's type and the label's declared type " +
							"to agree with the declared return type (" +
							"$declaredReturnType)"
					} ?: ("final expression's type ($deducedReturnType) to " +
						"agree with the declared return type " +
						"($declaredReturnType)"))
			}
			if (primitiveReturnType !== null
				&& !primitiveReturnType.isSubtypeOf(declaredReturnType))
			{
				throw AvailRejectedParseException(
					STRONG,
					"primitive's intrinsic return type ("
						+ primitiveReturnType
						+ ") to agree with the declared return type ("
						+ declaredReturnType
						+ ")")
			}
			if (labelReturnType !== null
				&& !labelReturnType.isSubtypeOf(declaredReturnType))
			{
				throw AvailRejectedParseException(
					STRONG,
					"label's declared return type ($labelReturnType) to agree "
					+ "with the function's declared return type "
					+ "($declaredReturnType)")
			}
		}
		else if (primitiveReturnType !== null)
		{
			// If it's a primitive, then the block must declare an explicit
			// return type.
			throw AvailRejectedParseException(
				// In case we're trying a parse that stops before the type.
				WEAK,
				"primitive function to declare its return type")
		}
		val returnType = declaredReturnType ?: deducedReturnType
		var exceptionsSet: A_Set = emptySet
		if (optionalExceptionTypes.expressionsSize == 1)
		{
			val expressions =
				optionalExceptionTypes.lastExpression.expressionsTuple
			exceptionsSet = generateSetFrom(expressions) {
				it.token.literal()
			}.makeImmutable()
		}
		// The block's line number is the line of the first token that's part of
		// the block, even if it's part of a subexpression (which it won't be
		// with the core Avail syntax).
		val lineNumber = when (tokens.tupleSize)
		{
			0 -> 0
			else -> tokens.tupleAt(1).lineNumber()
		}
		val block = newBlockNode(
			tupleFromList(argumentDeclarationsList),
			primitive,
			tupleFromList(allStatements),
			returnType,
			exceptionsSet,
			lineNumber,
			tokens)
		block.makeImmutable()
		// Pop and discard the top entry from the scope stack.
		val fiber = interpreter.fiber()
		scopeStack = scopeStack.copyTupleFromToCanDestroy(
			1, scopeStack.tupleSize - 1, true)
		clientData = clientData.mapAtPuttingCanDestroy(
			scopeStackKey, scopeStack, true)
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataKey, clientData, true)
		fiber.fiberGlobals = fiberGlobals.makeShared()
		return interpreter.primitiveSuccess(block)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
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
								TOKEN.o,
								/* Argument type. */
								anyMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional primitive declaration. */
					zeroOrOneOf(
						/* Primitive declaration */
						tupleTypeForTypes(
							/* Primitive name. */
							TOKEN.o,
							/* Optional failure variable declaration. */
							zeroOrOneOf(
								/* Primitive failure variable parts. */
								tupleTypeForTypes(
									/* Primitive failure variable name token */
									TOKEN.o,
									/* Primitive failure variable type */
									anyMeta()))))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional label declaration. */
					zeroOrOneOf(
						/* Label parts. */
						tupleTypeForTypes(
							/* Label name */
							TOKEN.o,
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
						 * TOP.o.
						 */
						STATEMENT_PHRASE.mostGeneralType)),
				/* Optional return expression */
				LIST_PHRASE.create(zeroOrOneOf(PARSE_PHRASE.create(ANY.o))),
				/* Optional return type */
				LIST_PHRASE.create(zeroOrOneOf(topMeta())),
				/* Optional tuple of exception types */
				LIST_PHRASE.create(zeroOrOneOf(oneOrMoreOf(exceptionType)))),
			/* ...and produce a block phrase. */
			BLOCK_PHRASE.mostGeneralType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_INCONSISTENT_PREFIX_FUNCTION))
}
