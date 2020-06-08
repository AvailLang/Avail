/*
 * L1Decompiler.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.interpreter.levelOne

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.declaration
import com.avail.descriptor.phrases.A_Phrase.Companion.declaredType
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import com.avail.descriptor.phrases.A_Phrase.Companion.initializationExpression
import com.avail.descriptor.phrases.A_Phrase.Companion.isLastUse
import com.avail.descriptor.phrases.A_Phrase.Companion.list
import com.avail.descriptor.phrases.A_Phrase.Companion.permutation
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import com.avail.descriptor.phrases.A_Phrase.Companion.statements
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.A_Phrase.Companion.typeExpression
import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.Companion.newAssignment
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.phrases.BlockPhraseDescriptor.Companion.newBlockNode
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newArgument
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newConstant
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newLabel
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newModuleVariable
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newVariable
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.phrases.FirstOfSequencePhraseDescriptor.Companion.newFirstOfSequenceNode
import com.avail.descriptor.phrases.ListPhraseDescriptor
import com.avail.descriptor.phrases.ListPhraseDescriptor.Companion.newListNode
import com.avail.descriptor.phrases.LiteralPhraseDescriptor
import com.avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.fromTokenForDecompiler
import com.avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.literalNodeFromToken
import com.avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.syntheticLiteralNodeFor
import com.avail.descriptor.phrases.MarkerPhraseDescriptor
import com.avail.descriptor.phrases.MarkerPhraseDescriptor.MarkerTypes.DUP
import com.avail.descriptor.phrases.MarkerPhraseDescriptor.MarkerTypes.PERMUTE
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor.Companion.newPermutedListNode
import com.avail.descriptor.phrases.PhraseDescriptor
import com.avail.descriptor.phrases.ReferencePhraseDescriptor
import com.avail.descriptor.phrases.ReferencePhraseDescriptor.Companion.referenceNodeFromUse
import com.avail.descriptor.phrases.SendPhraseDescriptor.Companion.newSendNode
import com.avail.descriptor.phrases.SuperCastPhraseDescriptor
import com.avail.descriptor.phrases.SuperCastPhraseDescriptor.Companion.newSuperCastNode
import com.avail.descriptor.phrases.VariableUsePhraseDescriptor.Companion.newUse
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import com.avail.descriptor.tokens.TokenDescriptor.Companion.newToken
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.FIRST_OF_SEQUENCE_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LABEL_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MARKER_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PERMUTED_LIST_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.REFERENCE_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import com.avail.descriptor.types.VariableTypeDescriptor.variableTypeFor
import com.avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithOuterType
import com.avail.utility.PrefixSharingList.last
import java.util.*
import java.util.function.Function
import java.util.function.UnaryOperator

/**
 * The [L1Decompiler] converts a [compiled&#32;code][CompiledCodeDescriptor]
 * object into an equivalent [parse&#32;tree][PhraseDescriptor].
 *
 * @property code
 *   The [compiled&#32;code][CompiledCodeDescriptor] which is being decompiled.
 * @property tempGenerator
 *   Something to generate unique variable names from a prefix.
 * @author Mark van Gulik &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Create a new decompiler suitable for decoding the given raw function, tuple
 * of outer variable declarations, and temporary name generator.
 *
 * @param code
 *   The [code][CompiledCodeDescriptor] to decompile.
 * @param outerDeclarations
 *   The array of outer variable declarations and literal phrases.
 * @param tempGenerator
 *   A [transformer][Function] that takes a prefix and generates a suitably
 *   unique temporary variable name.
 */
class L1Decompiler constructor(
	internal val code: A_RawFunction,
	outerDeclarations: Array<A_Phrase>,
	internal val tempGenerator: UnaryOperator<String>)
{
	/**
	 * The number of nybbles in the nybblecodes of the raw function being
	 * decompiled.
	 */
	internal val numNybbles: Int = code.numNybbles()

	/**
	 * [Phrases][PhraseDescriptor] which correspond with the lexically captured
	 * variables.  These can be [ declaration
	 * phrases][DeclarationPhraseDescriptor] or
	 * [literal][LiteralPhraseDescriptor], but the latter may be phased out in
	 * favor of module constants and module variables.
	 */
	internal val outers: Array<A_Phrase> = outerDeclarations.clone()

	/**
	 * The [arguments&#32;declarations][DeclarationKind.ARGUMENT] for this code.
	 */
	internal val args: Array<A_Phrase>

	/**
	 * The [local&#32;variables][DeclarationKind.LOCAL_VARIABLE] defined by this
	 * code.
	 */
	internal val locals: Array<A_Phrase>

	/**
	 * Flags to indicate which local variables have been mentioned.  Upon first
	 * mention, the corresponding local declaration should be emitted.
	 */
	private val mentionedLocals: BooleanArray

	/**
	 * The [local&#32;constants][DeclarationKind.LOCAL_CONSTANT] defined by this
	 * code.
	 */
	internal val constants: Array<A_Phrase?> = arrayOfNulls(code.numConstants())

	/**
	 * The current position in the instruction stream at which decompilation is
	 * occurring.
	 */
	internal val instructionDecoder = L1InstructionDecoder()

	/**
	 * The stack of expressions roughly corresponding to the subexpressions that
	 * have been parsed but not yet integrated into their parent expressions.
	 */
	internal val expressionStack: MutableList<A_Phrase> = ArrayList()

	/**
	 * The list of completely decompiled [ statements][PhraseDescriptor].
	 */
	internal val statements: MutableList<A_Phrase> = ArrayList()

	/**
	 * A flag to indicate that the last instruction was a push of the null
	 * object.
	 */
	internal var endsWithPushNil = false

	/**
	 * The decompiled [block&#32;phrase][BlockPhraseDescriptor].
	 */
	internal var block: A_Phrase
		private set

	init
	{
		code.setUpInstructionDecoder(instructionDecoder)
		instructionDecoder.pc(1)
		val tupleType = code.functionType().argsTupleType()
		args = Array(code.numArgs()) {
			val token = newToken(
				stringFrom(tempGenerator.apply("arg")),
				0,
				0,
				KEYWORD)
			newArgument(token, tupleType.typeAtIndex(it + 1), nil)
		}
		mentionedLocals = BooleanArray(code.numLocals())
		locals = Array(code.numLocals()) {
			val token = newToken(
				stringFrom(tempGenerator.apply("local")),
				0,
				0,
				KEYWORD)
			val declaration = newVariable(
				token, code.localTypeAt(it + 1).writeType(), nil, nil)
			declaration
		}
		// Don't initialize the constants – we may as well wait until we reach
		// the initialization for each, identified by an L1Ext_doSetSlot().

		val dispatcher = DecompilerDispatcher()
		while (!instructionDecoder.atEnd())
		{
			instructionDecoder.getOperation().dispatch(dispatcher)
		}
		// Infallible primitives don't have nybblecodes, except ones marked as
		// Primitive.Flag.SpecialForm.
		if (numNybbles > 0)
		{
			assert(instructionDecoder.pc() == numNybbles + 1)
			if (!endsWithPushNil)
			{
				statements.add(popExpression())
			}
			// Otherwise nothing was left on the expression stack.
		}
		assert(expressionStack.size == 0) {
			"There should be nothing on the stack after the final return"
		}
		mentionedLocals.forEach {
			assert(it) { "Local constant was not mentioned" }
		}
		block = newBlockNode(
			tupleFromArray(*args),
			code.primitiveNumber(),
			tupleFromList(statements),
			code.functionType().returnType(),
			code.functionType().declaredExceptions(),
			0,
			emptyTuple())
	}

	/**
	 * Look up the declaration for the argument, local, or constant at the
	 * specified one-based offset in what will be the continuation's slots.
	 *
	 * @param index
	 *   The one-based index into the args/locals/constants.
	 * @return
	 *   The declaration.
	 */
	internal fun argOrLocalOrConstant(index: Int): A_Phrase
	{
		if (index <= args.size)
		{
			return args[index - 1]
		}
		if (index <= args.size + locals.size)
		{
			val localSubscript = index - args.size - 1
			if (!mentionedLocals[localSubscript])
			{
				// This was the first mention of the local.  Emit its
				// declaration as a statement.  If this was being looked up for
				// a write, simply set the declaration's initializing expression
				// later instead of emitting a separate assignment statement.
				mentionedLocals[localSubscript] = true
				statements.add(locals[localSubscript])
			}
			return locals[localSubscript]
		}
		val constSubscript = index - args.size - locals.size - 1
		return constants[constSubscript]!!
	}

	/**
	 * The top value of the expression stack (without removing it).
	 */
	internal val peekExpression: A_Phrase
		get() = expressionStack[expressionStack.size - 1]

	/**
	 * Pop one [phrase][PhraseDescriptor] off the expression stack and return
	 * it.
	 *
	 * @return
	 *   The phrase popped off the stack.
	 */
	internal fun popExpression(): A_Phrase =
		expressionStack.removeAt(expressionStack.size - 1)

	/**
	 * Pop some [phrases][PhraseDescriptor] off the expression stack and return
	 * them in a [list][List].
	 *
	 * @param count
	 *   The number of phrases to pop.
	 * @return
	 *   The list of `count` phrases, in the order they were added to the stack.
	 */
	internal fun popExpressions(count: Int): List<A_Phrase>
	{
		val result = ArrayList<A_Phrase>(count)
		(1 .. count).forEach { _ ->
			result.add(0, popExpression())
		}
		return result
	}

	/**
	 * Push the given [phrase][PhraseDescriptor] onto the expression stack.
	 *
	 * @param expression
	 *   The expression to push.
	 */
	internal fun pushExpression(expression: A_Phrase)
	{
		expressionStack.add(expression)
	}

	internal inner class DecompilerDispatcher : L1OperationDispatcher
	{
		override fun L1_doCall()
		{
			val bundle = code.literalAt(instructionDecoder.getOperand())
			val type = code.literalAt(instructionDecoder.getOperand())
			val method = bundle.bundleMethod()
			val nArgs = method.numArgs()
			var permutationTuple: A_Tuple? = null
			if (nArgs > 1 && peekExpression.equals(PERMUTE.marker))
			{
				// We just hit a permute of the top N items of the stack.  This
				// is due to a permutation of the top-level arguments of a call,
				// not an embedded guillemet expression (otherwise we would have
				// hit an L1_doMakeTuple instead of this call).  A literal
				// phrase containing the permutation to apply was pushed before
				// the marker.
				popExpression()
				val permutationLiteral = popExpression()
				assert(permutationLiteral.phraseKindIsUnder(LITERAL_PHRASE))
				permutationTuple = permutationLiteral.token().literal()
			}
			val argsTuple = tupleFromList(popExpressions(nArgs))
			var listNode: A_Phrase = newListNode(argsTuple)
			if (permutationTuple !== null)
			{
				listNode = newPermutedListNode(listNode, permutationTuple)
			}
			val sendNode = newSendNode(emptyTuple(), bundle, listNode, type)
			pushExpression(sendNode)
		}

		override fun L1_doPushLiteral()
		{
			val value = code.literalAt(instructionDecoder.getOperand())
			if (value.isInstanceOfKind(mostGeneralFunctionType()))
			{
				val functionOuters = Array(value.numOuterVars()) {
					// Due to stub-building primitives, it's possible for a
					// non-clean function to be a literal, so deal with it here.
					val i = it + 1
					val varObject = value.outerVarAt(i)
					val token = literalToken(
						stringFrom(
							"OuterOfUncleanConstantFunction#"
							+ i
							+ " (with value "
							+ varObject
							+ ")"),
						0,
						0,
						varObject)
					literalNodeFromToken(token)
				}
				val decompiler = L1Decompiler(
					value.code(), functionOuters, tempGenerator)
				pushExpression(decompiler.block)
			}
			else
			{
				if (value.equalsNil())
				{
					// The last "statement" may just push nil. Such a statement
					// will be re-synthesized during code generation, so don't
					// bother reconstructing it now.
					assert(instructionDecoder.pc() > numNybbles) {
						"nil can only be (implicitly) pushed at the end of a " +
						"sequence of statements"
					}
					endsWithPushNil = true
				}
				else
				{
					val token = literalToken(
						stringFrom(value.toString()),
						0,
						0,
						value)
					pushExpression(literalNodeFromToken(token))
				}
			}
		}

		override fun L1_doPushLastLocal()
		{
			val declaration = argOrLocalOrConstant(instructionDecoder.getOperand())
			val use = newUse(declaration.token(), declaration)
			use.isLastUse(true)
			if (declaration.declarationKind().isVariable)
			{
				pushExpression(use)
			}
			else
			{
				pushExpression(referenceNodeFromUse(use))
			}
		}

		override fun L1_doPushLocal()
		{
			val declaration = argOrLocalOrConstant(instructionDecoder.getOperand())
			val use = newUse(declaration.token(), declaration)
			if (declaration.declarationKind().isVariable)
			{
				pushExpression(use)
			}
			else
			{
				pushExpression(referenceNodeFromUse(use))
			}
		}

		override fun L1_doPushLastOuter()
		{
			L1_doPushOuter()
		}

		override fun L1_doClose()
		{
			val nOuters = instructionDecoder.getOperand()
			val theCode = code.literalAt(instructionDecoder.getOperand())
			val theOuters = popExpressions(nOuters)
			for (outer in theOuters)
			{
				val kind = outer.phraseKind()
				assert(kind === VARIABLE_USE_PHRASE
				   || kind === REFERENCE_PHRASE
				   || kind === LITERAL_PHRASE)
			}
			val decompiler = L1Decompiler(
				theCode,
				theOuters.toTypedArray(),
				tempGenerator)
			pushExpression(decompiler.block)
		}

		override fun L1_doSetLocal()
		{
			val previousStatementCount = statements.size
			val indexInFrame = instructionDecoder.getOperand()
			val declaration = argOrLocalOrConstant(indexInFrame)
			assert(declaration.declarationKind().isVariable)
			if (statements.size > previousStatementCount)
			{
				assert(statements.size == previousStatementCount + 1)
				assert(last(statements) === declaration)
				// This was the first use of the variable, so it was written as
				// a statement automatically.  Create a new variable with an
				// initialization expression, and replace the old declaration in
				// the locals list and the last emitted statement, which are the
				// only places that could have a reference to the old
				// declaration.
				assert(declaration.initializationExpression().equalsNil())
				val replacementDeclaration = newVariable(
					declaration.token(),
					declaration.declaredType(),
					declaration.typeExpression(),
					popExpression())
				locals[indexInFrame - code.numArgs() - 1] =
					replacementDeclaration
				statements[statements.size - 1] = replacementDeclaration
				return
			}
			// This assignment wasn't the first mention of the variable.
			val valueNode = popExpression()
			val variableUse = newUse(declaration.token(), declaration)
			val assignmentNode = newAssignment(
				variableUse, valueNode, emptyTuple(), false)
			if (expressionStack.isEmpty()
				|| peekExpression.phraseKind() !== MARKER_PHRASE)
			{
				statements.add(assignmentNode)
			}
			else
			{
				// We had better see a dup marker, otherwise the code generator
				// didn't do what we expected.  Remove the marker and replace it
				// with the (embedded) assignment phrase itself.
				val duplicateExpression = popExpression()
				assert(duplicateExpression.equals(DUP.marker))
				expressionStack.add(assignmentNode)
			}
		}

		override fun L1_doGetLocalClearing()
		{
			L1_doGetLocal()
		}

		override fun L1_doPushOuter()
		{
			pushExpression(outers[instructionDecoder.getOperand() - 1])
		}

		override fun L1_doPop()
		{
			if (expressionStack.size == 1)
			{
				statements.add(popExpression())
			}
			else
			{
				// This is very rare – it's a non-first statement of a
				// FirstOfSequence phrase.  Determine if we're constructing or
				// extending an existing FirstOfSequence.
				val lastExpression = popExpression()
				val penultimateExpression = popExpression()
				val newStatements: A_Tuple
				newStatements =
					if (penultimateExpression.phraseKind()
						=== FIRST_OF_SEQUENCE_PHRASE)
					{
						// Extend an existing FirstOfSequence phrase.
						penultimateExpression.statements().appendCanDestroy(
							lastExpression, false)
					}
					else
					{
						// Create a two-element FirstOfSequence phrase.
						tuple(penultimateExpression, lastExpression)
					}
				pushExpression(newFirstOfSequenceNode(newStatements))
			}
		}

		override fun L1_doGetOuterClearing()
		{
			L1_doGetOuter()
		}

		override fun L1_doSetOuter()
		{
			val outer = outers[instructionDecoder.getOperand() - 1]
			val declaration: A_Phrase
			declaration =
				if (outer.phraseKindIsUnder(LITERAL_PHRASE))
				{
					// Writing into a synthetic literal (a byproduct of
					// decompiling a block without decompiling its outer
					// scopes).
					val token = outer.token()
					val variableObject = token.literal()
					newModuleVariable(token, variableObject, nil, nil)
				}
				else
				{
					assert(outer.phraseKindIsUnder(VARIABLE_USE_PHRASE))
					outer.declaration()
				}
			val use = newUse(declaration.token(), declaration)
			val valueExpr = popExpression()
			val assignmentNode = newAssignment(
				use, valueExpr, emptyTuple(), false)
			if (expressionStack.isEmpty())
			{
				statements.add(assignmentNode)
			}
			else
			{
				// We had better see a dup marker, otherwise the code generator
				// didn't do what we expected.  Remove that marker and replace
				// it with the (embedded) assignment phrase itself.
				val duplicateExpression = popExpression()
				assert(duplicateExpression.equals(DUP.marker))
				expressionStack.add(assignmentNode)
			}
		}

		override fun L1_doGetLocal()
		{
			val localDeclaration = argOrLocalOrConstant(
				instructionDecoder.getOperand())
			assert(localDeclaration.declarationKind().isVariable)
			val useNode = newUse(localDeclaration.token(), localDeclaration)
			pushExpression(useNode)
		}

		override fun L1_doMakeTuple()
		{
			val count = instructionDecoder.getOperand()
			var permutationTuple: A_Tuple? = null
			if (count > 1 && peekExpression.equals(PERMUTE.marker))
			{
				// We just hit a permute of the top N items of the stack.  This
				// is due to a permutation within a guillemet expression.  A
				// literal phrase containing the permutation to apply was pushed
				// before the marker.
				popExpression()
				val permutationLiteral = popExpression()
				assert(permutationLiteral.phraseKindIsUnder(LITERAL_PHRASE))
				permutationTuple = permutationLiteral.token().literal()
			}
			val expressions = popExpressions(count)
			var listNode: A_Phrase = newListNode(tupleFromList(expressions))
			if (permutationTuple !== null)
			{
				listNode = newPermutedListNode(listNode, permutationTuple)
			}
			pushExpression(listNode)
		}

		override fun L1_doGetOuter()
		{
			val outer = outers[instructionDecoder.getOperand() - 1]
			if (outer.phraseKindIsUnder(LITERAL_PHRASE))
			{
				pushExpression(outer)
				return
			}
			assert(outer.phraseKindIsUnder(VARIABLE_USE_PHRASE))
			val declaration = outer.declaration()
			val use = newUse(declaration.token(), declaration)
			pushExpression(use)
		}

		override fun L1_doExtension()
		{
			assert(false) { "Illegal dispatch nybblecode" }
		}

		override fun L1Ext_doPushLabel()
		{
			val label: A_Phrase
			if (statements.size > 0 && statements[0].isInstanceOfKind(
					LABEL_PHRASE.mostGeneralType()))
			{
				label = statements[0]
			}
			else
			{
				val labelToken = newToken(
					stringFrom(tempGenerator.apply("label")),
					0,
					0,
					KEYWORD)
				label = newLabel(
					labelToken,
					nil,
					continuationTypeForFunctionType(code.functionType()))
				statements.add(0, label)
			}
			pushExpression(newUse(label.token(), label))
		}

		override fun L1Ext_doGetLiteral()
		{
			val globalToken = newToken(
				stringFrom("SomeGlobal"),
				0,
				0,
				KEYWORD)
			val globalVar = code.literalAt(
				instructionDecoder.getOperand())
			val declaration = newModuleVariable(
				globalToken, globalVar, nil, nil)
			pushExpression(newUse(globalToken, declaration))
		}

		override fun L1Ext_doSetLiteral()
		{
			val globalToken = newToken(
				stringFrom("SomeGlobal"),
				0,
				0,
				KEYWORD)
			val globalVar = code.literalAt(
				instructionDecoder.getOperand())
			val declaration = newModuleVariable(
				globalToken, globalVar, nil, nil)
			val varUse = newUse(globalToken, declaration)
			val assignmentNode = newAssignment(
				varUse, popExpression(), emptyTuple(), false)
			if (expressionStack.isEmpty())
			{
				statements.add(assignmentNode)
			}
			else
			{
				// We had better see a dup marker, otherwise the code generator
				// didn't do what we expected.  Remove that marker and replace
				// it with the (embedded) assignment phrase itself.
				val duplicateExpression = popExpression()
				assert(duplicateExpression.equals(DUP.marker))
				expressionStack.add(assignmentNode)
			}
		}

		/**
		 * The presence of the [operation][L1Operation] indicates that an
		 * assignment is being used as a subexpression or final statement from a
		 * non-void valued [block][FunctionDescriptor].
		 *
		 * Pop the expression (that represents the right hand side of the
		 * assignment), push a special
		 * [marker&#32;phrase][MarkerPhraseDescriptor] representing the dup,
		 * then push the right-hand side expression back onto the expression
		 * stack.
		 */
		override fun L1Ext_doDuplicate()
		{
			val rightSide = popExpression()
			pushExpression(DUP.marker)
			pushExpression(rightSide)
		}

		override fun L1Ext_doPermute()
		{
			// Note that this applies to any guillemet group, not just the top
			// level implicit list of arguments to a call.  It's also used for
			// permuting both the arguments and their types in the case of a
			// super-call to a bundle containing permutations.
			val permutation = code.literalAt(instructionDecoder.getOperand())
			pushExpression(syntheticLiteralNodeFor(permutation))
			pushExpression(PERMUTE.marker)
		}

		override fun L1Ext_doSuperCall()
		{
			val bundle = code.literalAt(instructionDecoder.getOperand())
			val type = code.literalAt(instructionDecoder.getOperand())
			val superUnionType = code.literalAt(instructionDecoder.getOperand())
			val method = bundle.bundleMethod()
			val nArgs = method.numArgs()
			val argsNode = reconstructListWithSuperUnionType(nArgs, superUnionType)
			val sendNode = newSendNode(emptyTuple(), bundle, argsNode, type)
			pushExpression(sendNode)
		}

		override fun L1Ext_doSetSlot()
		{
			// This instruction is only used to initialize local constants.
			// In fact, the constant declaration isn't even created until we
			// reach this corresponding instruction.  Also note that there is no
			// inline-assignment form of constant declaration, so we don't need
			// to look for a DUP marker.
			val constSubscript =
				(instructionDecoder.getOperand() - code.numArgs()
	- code.numLocals() - 1)
			val token = newToken(
				stringFrom(tempGenerator.apply("const")),
				0,
				0,
				KEYWORD)
			val constantDeclaration = newConstant(token, popExpression())
			constants[constSubscript] = constantDeclaration
			statements.add(constantDeclaration)
		}
	}

	/**
	 * There are `nArgs` values on the stack.  Pop them all, and build a
	 * suitable list phrase (or permuted list phrase if there was also a
	 * permutation literal and permute marker pushed after).  The passed
	 * superUnionType is a tuple type that says how to adjust the lookup by
	 * first producing a type union with it and the actual arguments' types.
	 *
	 * @param nArgs
	 *   The number of arguments to expect on the stack.  This is also the size
	 *   of the resulting list phrase.
	 * @param superUnionType
	 *   The tuple type which will be type unioned with the runtime argument
	 *   types prior to lookup.
	 * @return
	 *   A list phrase or permuted list phrase containing at least one supercast
	 *   somewhere within the recursive list structure.
	 */
	internal fun reconstructListWithSuperUnionType(
		nArgs: Int,
		superUnionType: A_Type): A_Phrase
	{
		var permutationTuple: A_Tuple? = null
		if (nArgs > 1 && peekExpression.equals(PERMUTE.marker))
		{
			// We just hit a permute of the top N items of the stack.  This is
			// due to a permutation of the top-level arguments of a call, not an
			// embedded guillemet expression (otherwise we would have hit an
			// L1_doMakeTuple instead of this call).  A literal phrase
			// containing the permutation to apply was pushed before the marker.
			popExpression()
			val permutationLiteral = popExpression()
			assert(permutationLiteral.phraseKindIsUnder(LITERAL_PHRASE))
			permutationTuple = permutationLiteral.token().literal()
		}
		val argsList = popExpressions(nArgs)
		val listNode = newListNode(tupleFromList(argsList))
		val argsNode =
			if (permutationTuple !== null)
				newPermutedListNode(listNode, permutationTuple)
			else
				listNode
		return adjustSuperCastsIn(argsNode, superUnionType)
	}

	companion object
	{
		/**
		 * Convert some of the descendants within a
		 * [list&#32;phrase][ListPhraseDescriptor] into
		 * [supercasts][SuperCastPhraseDescriptor], based on the given
		 * superUnionType.  Because the phrase is processed recursively, some
		 * invocations will pass a non-list phrase.
		 */
		private fun adjustSuperCastsIn(
			phrase: A_Phrase,
			superUnionType: A_Type): A_Phrase
		{
			when
			{
				superUnionType.isBottom ->
					// No supercasts in this argument.
					return phrase
				phrase.phraseKindIsUnder(PERMUTED_LIST_PHRASE) ->
				{
					// Apply the superUnionType's elements to the permuted list.
					val permutation = phrase.permutation()
					val list = phrase.list()
					val size = list.expressionsSize()
					val outputArray = Array<A_Phrase>(size) { nil }
					for (i in 1..size)
					{
						val permutedIndex = permutation.tupleIntAt(i)
						outputArray[permutedIndex - 1] = adjustSuperCastsIn(
							list.expressionAt(permutedIndex),
							superUnionType.typeAtIndex(i))
					}
					return newPermutedListNode(
						newListNode(tupleFromArray(*outputArray)), permutation)
				}
				phrase.phraseKindIsUnder(LIST_PHRASE) ->
				{
					// Apply the superUnionType's elements to the list.
					return newListNode(
						generateObjectTupleFrom(phrase.expressionsSize()) {
							adjustSuperCastsIn(
								phrase.expressionAt(it),
								superUnionType.typeAtIndex(it))
						})
				}
				else -> return newSuperCastNode(phrase, superUnionType)
			}
		}

		/**
		 * Create a suitable dummy phrase to indicate an outer variable which is
		 * defined in a scope outside the scope of decompilation.
		 *
		 * @param outerIndex
		 *   The index of the outer.
		 * @param type
		 *   The type of the outer.
		 * @return
		 *   A [variable&#32;reference&#32;phrase][ReferencePhraseDescriptor].
		 */
		private fun outerPhraseForDecompiler(
			outerIndex: Int, type: A_Type): A_Phrase
		{
			val name = stringFrom("Outer#$outerIndex")
			val variable = newVariableWithOuterType(variableTypeFor(type))
			val token = literalToken(name, 0, 0, variable)
			return fromTokenForDecompiler(token)
		}

		/**
		 * Decompile the given [A_RawFunction].  It treats outer variables as
		 * private [atom][A_Atom] literals.  Answer the resulting
		 * [block][BlockPhraseDescriptor].
		 *
		 * @param code
		 *   The [A_RawFunction] to decompile.
		 * @return
		 *   The [block][BlockPhraseDescriptor] that is the decompilation of the
		 *   provided raw function.
		 */
		@JvmStatic
		fun decompile(code: A_RawFunction): A_Phrase
		{
			val counts = HashMap<String, Int>()
			// Synthesize fake outers to allow decompilation.
			val outerCount = code.numOuters()
			val functionOuters = Array(outerCount) {
				val i = it + 1
				outerPhraseForDecompiler(i, code.outerTypeAt(i))
			}
			val decompiler = L1Decompiler(code, functionOuters, UnaryOperator {
				var newCount: Int? = counts[it]
				newCount = if (newCount === null) 1 else newCount + 1
				counts[it] = newCount
				it + newCount
			})
			return decompiler.block
		}
	}
}
