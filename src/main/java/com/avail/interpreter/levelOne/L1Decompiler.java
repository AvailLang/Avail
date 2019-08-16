/*
 * L1Decompiler.java
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

package com.avail.interpreter.levelOne;

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;
import com.avail.descriptor.CompiledCodeDescriptor.L1InstructionDecoder;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.avail.descriptor.AssignmentPhraseDescriptor.newAssignment;
import static com.avail.descriptor.BlockPhraseDescriptor.newBlockNode;
import static com.avail.descriptor.ContinuationTypeDescriptor.continuationTypeForFunctionType;
import static com.avail.descriptor.DeclarationPhraseDescriptor.*;
import static com.avail.descriptor.FirstOfSequencePhraseDescriptor.newFirstOfSequenceNode;
import static com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.ListPhraseDescriptor.newListNode;
import static com.avail.descriptor.LiteralPhraseDescriptor.*;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.MarkerPhraseDescriptor.MarkerTypes.DUP;
import static com.avail.descriptor.MarkerPhraseDescriptor.MarkerTypes.PERMUTE;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.*;
import static com.avail.descriptor.PermutedListPhraseDescriptor.newPermutedListNode;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*;
import static com.avail.descriptor.ReferencePhraseDescriptor.referenceNodeFromUse;
import static com.avail.descriptor.SendPhraseDescriptor.newSendNode;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.SuperCastPhraseDescriptor.newSuperCastNode;
import static com.avail.descriptor.TokenDescriptor.TokenType.KEYWORD;
import static com.avail.descriptor.TokenDescriptor.newToken;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.VariableDescriptor.newVariableWithOuterType;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.descriptor.VariableUsePhraseDescriptor.newUse;
import static com.avail.utility.PrefixSharingList.last;

/**
 * The {@link L1Decompiler} converts a {@linkplain CompiledCodeDescriptor
 * compiled code} object into an equivalent {@linkplain PhraseDescriptor
 * parse tree}.
 *
 * @author Mark van Gulik &lt;todd@availlang.org&gt;
 */
public class L1Decompiler
{
	/**
	 * The {@linkplain CompiledCodeDescriptor compiled code} which is being
	 * decompiled.
	 */
	@InnerAccess final A_RawFunction code;

	/**
	 * The number of nybbles in the nybblecodes of the raw function being
	 * decompiled.
	 */
	@InnerAccess final int numNybbles;

	/**
	 * {@linkplain PhraseDescriptor Phrases} which correspond with the lexically
	 * captured variables.  These can be {@linkplain DeclarationPhraseDescriptor
	 * declaration phrases} or {@linkplain LiteralPhraseDescriptor literal
	 * phrases}, but the latter may be phased out in favor of module constants
	 * and module variables.
	 */
	@InnerAccess final A_Phrase[] outers;

	/**
	 * The {@linkplain DeclarationKind#ARGUMENT
	 * arguments declarations} for this code.
	 */
	@InnerAccess final A_Phrase[] args;

	/**
	 * The {@linkplain DeclarationKind#LOCAL_VARIABLE local variables} defined
	 * by this code.
	 */
	@InnerAccess final A_Phrase[] locals;

	/**
	 * Flags to indicate which local variables have been mentioned.  Upon first
	 * mention, the corresponding local declaration should be emitted.
	 */
	@InnerAccess final boolean[] mentionedLocals;

	/**
	 * The {@linkplain DeclarationKind#LOCAL_CONSTANT local constants} defined
	 * by this code.
	 */
	@InnerAccess final A_Phrase[] constants;

	/**
	 * The current position in the instruction stream at which decompilation is
	 * occurring.
	 */
	@InnerAccess final L1InstructionDecoder instructionDecoder =
		new L1InstructionDecoder();

	/**
	 * Something to generate unique variable names from a prefix.
	 */
	@InnerAccess final UnaryOperator<String> tempGenerator;

	/**
	 * The stack of expressions roughly corresponding to the subexpressions that
	 * have been parsed but not yet integrated into their parent expressions.
	 */
	@InnerAccess final List<A_Phrase> expressionStack = new ArrayList<>();

	/**
	 * The list of completely decompiled {@linkplain PhraseDescriptor
	 * statements}.
	 */
	@InnerAccess final List<A_Phrase> statements = new ArrayList<>();

	/**
	 * A flag to indicate that the last instruction was a push of the null
	 * object.
	 */
	@InnerAccess boolean endsWithPushNil = false;

	/**
	 * The decompiled {@linkplain BlockPhraseDescriptor block phrase}.
	 */
	@InnerAccess A_Phrase block;

	/**
	 * Create a new decompiler suitable for decoding the given raw function,
	 * tuple of outer variable declarations, and temporary name generator.
	 *
	 * @param aCodeObject
	 *        The {@linkplain CompiledCodeDescriptor code} to decompile.
	 * @param outerDeclarations
	 *        The array of outer variable declarations and literal phrases.
	 * @param tempBlock
	 *        A {@linkplain Function transformer} that takes a prefix
	 *        and generates a suitably unique temporary variable name.
	 */
	public L1Decompiler (
		final A_RawFunction aCodeObject,
		final A_Phrase[] outerDeclarations,
		final UnaryOperator<String> tempBlock)
	{
		code = aCodeObject;
		numNybbles = aCodeObject.numNybbles();
		outers = outerDeclarations.clone();
		tempGenerator = tempBlock;
		aCodeObject.setUpInstructionDecoder(instructionDecoder);
		instructionDecoder.pc(1);
		args = new A_Phrase[code.numArgs()];
		final A_Type tupleType = code.functionType().argsTupleType();
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			final A_Token token = newToken(
				stringFrom(tempGenerator.apply("arg")),
				0,
				0,
				KEYWORD);
			args[i - 1] = newArgument(token, tupleType.typeAtIndex(i), nil);
		}
		locals = new A_Phrase[code.numLocals()];
		mentionedLocals = new boolean[code.numLocals()];
		for (int i = 1, end = code.numLocals(); i <= end; i++)
		{
			final A_Token token = newToken(
				stringFrom(tempGenerator.apply("local")),
				0,
				0,
				KEYWORD);
			final A_Phrase declaration = newVariable(
				token, code.localTypeAt(i).writeType(), nil, nil);
			locals[i - 1] = declaration;
			mentionedLocals[i - 1] = false;
		}
		constants = new A_Phrase[code.numConstants()];
		// Don't initialize the constants – we may as well wait until we reach
		// the initialization for each, identified by an L1Ext_doSetSlot().

		final DecompilerDispatcher dispatcher = new DecompilerDispatcher();
		while (!instructionDecoder.atEnd())
		{
			instructionDecoder.getOperation().dispatch(dispatcher);
		}
		// Infallible primitives don't have nybblecodes, except ones marked as
		// Primitive.Flag.SpecialForm.
		if (numNybbles > 0)
		{
			assert instructionDecoder.pc() == numNybbles + 1;
			if (!endsWithPushNil)
			{
				statements.add(popExpression());
			}
			// Otherwise nothing was left on the expression stack.
		}
		assert expressionStack.size() == 0
			: "There should be nothing on the stack after the final return";
		for (final boolean b : mentionedLocals)
		{
			assert b : "Local constant was not mentioned";
		}
		block = newBlockNode(
			tupleFromArray(args),
			code.primitiveNumber(),
			tupleFromList(statements),
			code.functionType().returnType(),
			code.functionType().declaredExceptions(),
			0,
			emptyTuple());
	}

	/**
	 * Look up the declaration for the argument, local, or constant at the
	 * specified one-based offset in what will be the continuation's slots.
	 *
	 * @param index The one-based index into the args/locals/constants.
	 * @return The declaration.
	 */
	@InnerAccess A_Phrase argOrLocalOrConstant (final int index)
	{
		if (index <= args.length)
		{
			return args[index - 1];
		}
		if (index <= args.length + locals.length)
		{
			final int localSubscript = index - args.length - 1;
			if (!mentionedLocals[localSubscript])
			{
				// This was the first mention of the local.  Emit its
				// declaration as a statement.  If this was being looked up
				// for a write, simply set the declaration's initializing
				// expression later instead of emitting a separate
				// assignment statement.
				mentionedLocals[localSubscript] = true;
				statements.add(locals[localSubscript]);
			}
			return locals[localSubscript];
		}
		final int constSubscript = index - args.length - locals.length - 1;
		return constants[constSubscript];
	}

	/**
	 * Answer the {@linkplain BlockPhraseDescriptor block phrase} which is a
	 * decompilation of the previously supplied raw function.
	 *
	 * @return A block phrase which is a decompilation of the raw function.
	 */
	public A_Phrase block ()
	{
		return block;
	}

	/**
	 * Answer the top value of the expression stack without removing it.
	 *
	 * @return The phrase that's still on the top of the stack.
	 */
	@InnerAccess A_Phrase peekExpression ()
	{
		return expressionStack.get(expressionStack.size() - 1);
	}

	/**
	 * Pop one {@linkplain PhraseDescriptor phrase} off the expression
	 * stack and return it.
	 *
	 * @return The phrase popped off the stack.
	 */
	@InnerAccess A_Phrase popExpression ()
	{
		return expressionStack.remove(expressionStack.size() - 1);
	}

	/**
	 * Pop some {@linkplain PhraseDescriptor phrases} off the expression
	 * stack and return them in a {@linkplain List list}.
	 *
	 * @param count The number of phrases to pop.
	 * @return The list of {@code count} phrases, in the order they were
	 *         added to the stack.
	 */
	@InnerAccess List<A_Phrase> popExpressions (final int count)
	{
		final List<A_Phrase> result = new ArrayList<>(count);
		for (int i = 1; i <= count; i++)
		{
			result.add(0, popExpression());
		}
		return result;
	}

	/**
	 * Push the given {@linkplain PhraseDescriptor phrase} onto the
	 * expression stack.
	 *
	 * @param expression The expression to push.
	 */
	void pushExpression (
		final A_Phrase expression)
	{
		expressionStack.add(expression);
	}

	@InnerAccess class DecompilerDispatcher implements L1OperationDispatcher
	{
		@Override
		public void L1_doCall ()
		{
			final A_Bundle bundle = code.literalAt(
				instructionDecoder.getOperand());
			final A_Type type = code.literalAt(instructionDecoder.getOperand());
			final A_Method method = bundle.bundleMethod();
			final int nArgs = method.numArgs();
			@Nullable A_Tuple permutationTuple = null;
			if (nArgs > 1 && peekExpression().equals(PERMUTE.marker))
			{
				// We just hit a permute of the top N items of the stack.  This
				// is due to a permutation of the top-level arguments of a call,
				// not an embedded guillemet expression (otherwise we would have
				// hit an L1_doMakeTuple instead of this call).  A literal
				// phrase containing the permutation to apply was pushed before
				// the marker.
				popExpression();
				final A_Phrase permutationLiteral = popExpression();
				assert permutationLiteral.phraseKindIsUnder(LITERAL_PHRASE);
				permutationTuple = permutationLiteral.token().literal();
			}
			final A_Tuple argsTuple = tupleFromList(popExpressions(nArgs));
			A_Phrase listNode = newListNode(argsTuple);
			if (permutationTuple != null)
			{
				listNode = newPermutedListNode(listNode, permutationTuple);
			}
			final A_Phrase sendNode = newSendNode(
				emptyTuple(), bundle, listNode, type);
			pushExpression(sendNode);
		}

		@Override
		public void L1_doPushLiteral ()
		{
			final AvailObject value = code.literalAt(
				instructionDecoder.getOperand());
			if (value.isInstanceOfKind(mostGeneralFunctionType()))
			{
				final A_Phrase[] functionOuters =
					new A_Phrase[value.numOuterVars()];
				// Due to stub-building primitives, it's possible for a
				// non-clean function to be a literal, so deal with it here.
				for (int i = 1; i <= value.numOuterVars(); i++)
				{
					final AvailObject varObject = value.outerVarAt(i);
					final A_Token token =
						literalToken(
							stringFrom(
								"OuterOfUncleanConstantFunction#"
									+ i
									+ " (with value "
									+ varObject
									+ ")"),
							0,
							0,
							varObject);
					functionOuters[i - 1] = literalNodeFromToken(token);
				}
				final L1Decompiler decompiler = new L1Decompiler(
					value.code(), functionOuters, tempGenerator);
				pushExpression(decompiler.block());
			}
			else
			{
				if (value.equalsNil())
				{
					// The last "statement" may just push nil.
					// Such a statement will be re-synthesized during code
					// generation, so don't bother reconstructing it now.
					assert instructionDecoder.pc() > numNybbles
						: "nil can only be (implicitly) pushed at the "
						+ "end of a sequence of statements";
					endsWithPushNil = true;
				}
				else
				{
					final A_Token token =
						literalToken(
							stringFrom(value.toString()),
							0,
							0,
							value);
					pushExpression(literalNodeFromToken(token));
				}
			}
		}

		@Override
		public void L1_doPushLastLocal ()
		{
			final A_Phrase declaration = argOrLocalOrConstant(
				instructionDecoder.getOperand());
			final A_Phrase use = newUse(declaration.token(), declaration);
			use.isLastUse(true);
			if (declaration.declarationKind().isVariable())
			{
				pushExpression(use);
			}
			else
			{
				pushExpression(referenceNodeFromUse(use));
			}
		}

		@Override
		public void L1_doPushLocal ()
		{
			final A_Phrase declaration = argOrLocalOrConstant(
				instructionDecoder.getOperand());
			final AvailObject use = newUse(declaration.token(), declaration);
			if (declaration.declarationKind().isVariable())
			{
				pushExpression(use);
			}
			else
			{
				pushExpression(referenceNodeFromUse(use));
			}
		}

		@Override
		public void L1_doPushLastOuter ()
		{
			L1_doPushOuter();
		}

		@Override
		public void L1_doClose ()
		{
			final int nOuters = instructionDecoder.getOperand();
			final A_RawFunction theCode = code.literalAt(
				instructionDecoder.getOperand());
			final List<A_Phrase> theOuters = popExpressions(nOuters);
			for (final A_Phrase outer : theOuters)
			{
				final PhraseKind kind = outer.phraseKind();
				assert kind == VARIABLE_USE_PHRASE
					|| kind == REFERENCE_PHRASE
					|| kind == LITERAL_PHRASE;
			}
			final L1Decompiler decompiler = new L1Decompiler(
				theCode,
				theOuters.toArray(new A_Phrase[0]),
				tempGenerator);
			pushExpression(decompiler.block());
		}

		@Override
		public void L1_doSetLocal ()
		{
			final int previousStatementCount = statements.size();
			final int indexInFrame = instructionDecoder.getOperand();
			final A_Phrase declaration = argOrLocalOrConstant(indexInFrame);
			assert declaration.declarationKind().isVariable();
			if (statements.size() > previousStatementCount)
			{
				assert statements.size() == previousStatementCount + 1;
				assert last(statements) == declaration;
				// This was the first use of the variable, so it was written as
				// a statement automatically.  Create a new variable with an
				// initialization expression, and replace the old declaration
				// in the locals list and the last emitted statement, which are
				// the only places that could have a reference to the old
				// declaration.
				assert declaration.initializationExpression().equalsNil();
				final A_Phrase replacementDeclaration = newVariable(
					declaration.token(),
					declaration.declaredType(),
					declaration.typeExpression(),
					popExpression());
				locals[indexInFrame - code.numArgs() - 1] =
					replacementDeclaration;
				statements.set(statements.size() - 1, replacementDeclaration);
				return;
			}
			// This assignment wasn't the first mention of the variable.
			final A_Phrase valueNode = popExpression();
			final A_Phrase variableUse =
				newUse(declaration.token(), declaration);
			final A_Phrase assignmentNode = newAssignment(
				variableUse, valueNode, emptyTuple(), false);
			if (expressionStack.isEmpty()
				|| peekExpression().phraseKind() != MARKER_PHRASE)
			{
				statements.add(assignmentNode);
			}
			else
			{
				// We had better see a dup marker, otherwise the code generator
				// didn't do what we expected.  Remove the marker and replace it
				// with the (embedded) assignment phrase itself.
				final A_Phrase duplicateExpression = popExpression();
				assert duplicateExpression.equals(DUP.marker);
				expressionStack.add(assignmentNode);
			}
		}

		@Override
		public void L1_doGetLocalClearing ()
		{
			L1_doGetLocal();
		}

		@Override
		public void L1_doPushOuter ()
		{
			pushExpression(outers[instructionDecoder.getOperand() - 1]);
		}

		@Override
		public void L1_doPop ()
		{
			if (expressionStack.size() == 1)
			{
				statements.add(popExpression());
			}
			else
			{
				// This is very rare – it's a non-first statement of a
				// FirstOfSequence phrase.  Determine if we're constructing or
				// extending an existing FirstOfSequence.
				final A_Phrase lastExpression = popExpression();
				final A_Phrase penultimateExpression = popExpression();
				final A_Tuple newStatements;
				if (penultimateExpression.phraseKind()
					== FIRST_OF_SEQUENCE_PHRASE)
				{
					// Extend an existing FirstOfSequence phrase.
					newStatements =
						penultimateExpression.statements().appendCanDestroy(
							lastExpression, false);
				}
				else
				{
					// Create a two-element FirstOfSequence phrase.
					newStatements =
						tuple(penultimateExpression, lastExpression);
				}
				pushExpression(newFirstOfSequenceNode(newStatements));
			}
		}

		@Override
		public void L1_doGetOuterClearing ()
		{
			L1_doGetOuter();
		}

		@Override
		public void L1_doSetOuter ()
		{
			final A_Phrase outer = outers[instructionDecoder.getOperand() - 1];
			final A_Phrase declaration;
			if (outer.phraseKindIsUnder(LITERAL_PHRASE))
			{
				// Writing into a synthetic literal (a byproduct of decompiling
				// a block without decompiling its outer scopes).
				final A_Token token = outer.token();
				final A_Variable variableObject = token.literal();
				declaration =
					newModuleVariable(token, variableObject, nil, nil);
			}
			else
			{
				assert outer.phraseKindIsUnder(VARIABLE_USE_PHRASE);
				declaration = outer.declaration();
			}
			final A_Phrase use = newUse(declaration.token(), declaration);
			final A_Phrase valueExpr = popExpression();
			final A_Phrase assignmentNode =
				newAssignment(use, valueExpr, emptyTuple(), false);
			if (expressionStack.isEmpty())
			{
				statements.add(assignmentNode);
			}
			else
			{
				// We had better see a dup marker, otherwise the code generator
				// didn't do what we expected.  Remove that marker and replace
				// it with the (embedded) assignment phrase itself.
				final A_Phrase duplicateExpression = popExpression();
				assert duplicateExpression.equals(DUP.marker);
				expressionStack.add(assignmentNode);
			}
		}

		@Override
		public void L1_doGetLocal ()
		{
			final A_Phrase localDeclaration = argOrLocalOrConstant(
				instructionDecoder.getOperand());
			assert localDeclaration.declarationKind().isVariable();
			final AvailObject useNode =
				newUse(localDeclaration.token(), localDeclaration);
			pushExpression(useNode);
		}

		@Override
		public void L1_doMakeTuple ()
		{
			final int count = instructionDecoder.getOperand();
			@Nullable A_Tuple permutationTuple = null;
			if (count > 1 && peekExpression().equals(PERMUTE.marker))
			{
				// We just hit a permute of the top N items of the stack.  This
				// is due to a permutation within a guillemet expression.  A
				// literal phrase containing the permutation to apply was pushed
				// before the marker.
				popExpression();
				final A_Phrase permutationLiteral = popExpression();
				assert permutationLiteral.phraseKindIsUnder(LITERAL_PHRASE);
				permutationTuple = permutationLiteral.token().literal();
			}
			final List<A_Phrase> expressions = popExpressions(count);
			A_Phrase listNode = newListNode(tupleFromList(expressions));
			if (permutationTuple != null)
			{
				listNode = newPermutedListNode(listNode, permutationTuple);
			}
			pushExpression(listNode);
		}

		@Override
		public void L1_doGetOuter ()
		{
			final A_Phrase outer = outers[instructionDecoder.getOperand() - 1];
			if (outer.phraseKindIsUnder(LITERAL_PHRASE))
			{
				pushExpression(outer);
				return;
			}
			assert outer.phraseKindIsUnder(VARIABLE_USE_PHRASE);
			final A_Phrase declaration = outer.declaration();
			final A_Phrase use = newUse(declaration.token(), declaration);
			pushExpression(use);
		}

		@Override
		public void L1_doExtension ()
		{
			assert false : "Illegal dispatch nybblecode";
		}

		@Override
		public void L1Ext_doPushLabel ()
		{
			final A_Phrase label;
			if (statements.size() > 0
				&& statements.get(0).isInstanceOfKind(
					LABEL_PHRASE.mostGeneralType()))
			{
				label = statements.get(0);
			}
			else
			{
				final A_Token labelToken = newToken(
					stringFrom(tempGenerator.apply("label")),
					0,
					0,
					KEYWORD);
				label = newLabel(
					labelToken,
					nil,
					continuationTypeForFunctionType(code.functionType()));
				statements.add(0, label);
			}
			pushExpression(newUse(label.token(), label));
		}

		@Override
		public void L1Ext_doGetLiteral ()
		{
			final A_Token globalToken = newToken(
				stringFrom("SomeGlobal"),
				0,
				0,
				KEYWORD);
			final A_BasicObject globalVar = code.literalAt(
				instructionDecoder.getOperand());
			final A_Phrase declaration =
				newModuleVariable(globalToken, globalVar, nil, nil);
			pushExpression(newUse(globalToken, declaration));
		}

		@Override
		public void L1Ext_doSetLiteral ()
		{
			final A_Token globalToken = newToken(
				stringFrom("SomeGlobal"),
				0,
				0,
				KEYWORD);
			final AvailObject globalVar = code.literalAt(
				instructionDecoder.getOperand());
			final A_Phrase declaration =
				newModuleVariable(globalToken, globalVar, nil, nil);
			final A_Phrase varUse = newUse(globalToken, declaration);
			final A_Phrase assignmentNode =
				newAssignment(varUse, popExpression(), emptyTuple(), false);
			if (expressionStack.isEmpty())
			{
				statements.add(assignmentNode);
			}
			else
			{
				// We had better see a dup marker, otherwise the code generator
				// didn't do what we expected.  Remove that marker and replace
				// it with the (embedded) assignment phrase itself.
				final A_Phrase duplicateExpression = popExpression();
				assert duplicateExpression.equals(DUP.marker);
				expressionStack.add(assignmentNode);
			}
		}

		/**
		 * The presence of the {@linkplain L1Operation operation} indicates
		 * that
		 * an assignment is being used as a subexpression or final statement
		 * from a non-void valued {@linkplain FunctionDescriptor block}.
		 *
		 * <p>Pop the expression (that represents the right hand side of the
		 * assignment), push a special {@linkplain MarkerPhraseDescriptor
		 * marker phrase} representing the dup, then push the right-hand side
		 * expression back onto the expression stack.</p>
		 */
		@Override
		public void L1Ext_doDuplicate ()
		{
			final A_Phrase rightSide = popExpression();
			pushExpression(DUP.marker);
			pushExpression(rightSide);
		}

		@Override
		public void L1Ext_doPermute ()
		{
			// Note that this applies to any guillemet group, not just the top
			// level implicit list of arguments to a call.  It's also used for
			// permuting both the arguments and their types in the case of a
			// super-call to a bundle containing permutations.
			final A_Tuple permutation = code.literalAt(
				instructionDecoder.getOperand());
			pushExpression(syntheticLiteralNodeFor(permutation));
			pushExpression(PERMUTE.marker);
		}

		@Override
		public void L1Ext_doSuperCall ()
		{
			final A_Bundle bundle = code.literalAt(
				instructionDecoder.getOperand());
			final A_Type type = code.literalAt(instructionDecoder.getOperand());
			final A_Type superUnionType = code.literalAt(
				instructionDecoder.getOperand());

			final A_Method method = bundle.bundleMethod();
			final int nArgs = method.numArgs();
			final A_Phrase argsNode =
				reconstructListWithSuperUnionType(nArgs, superUnionType);
			final A_Phrase sendNode = newSendNode(
				emptyTuple(), bundle, argsNode, type);
			pushExpression(sendNode);
		}

		@Override
		public void L1Ext_doSetSlot ()
		{
			// This instruction is only used to initialize local constants.
			// In fact, the constant declaration isn't even created until we
			// reach this corresponding instruction.  Also note that there is no
			// inline-assignment form of constant declaration, so we don't need
			// to look for a DUP marker.
			final int constSubscript =
				instructionDecoder.getOperand()
					- code.numArgs() - code.numLocals() - 1;
			final A_Token token = newToken(
				stringFrom(tempGenerator.apply("const")),
				0,
				0,
				KEYWORD);
			final A_Phrase constantDeclaration =
				newConstant(token, popExpression());
			constants[constSubscript] = constantDeclaration;
			statements.add(constantDeclaration);
		}
	}

	/**
	 * There are {@code nArgs} values on the stack.  Pop them all, and build
	 * a suitable list phrase (or permuted list phrase if there was also a
	 * permutation literal and permute marker pushed after).  The passed
	 * superUnionType is a tuple type that says how to adjust the lookup by
	 * first producing a type union with it and the actual arguments' types.
	 *
	 * @param nArgs
	 *        The number of arguments to expect on the stack.  This is also
	 *        the size of the resulting list phrase.
	 * @param superUnionType
	 *        The tuple type which will be type unioned with the runtime
	 *        argument types prior to lookup.
	 * @return A list phrase or permuted list phrase containing at least one
	 *         supercast somewhere within the recursive list structure.
	 */
	@InnerAccess A_Phrase reconstructListWithSuperUnionType (
		final int nArgs,
		final A_Type superUnionType)
	{
		@Nullable A_Tuple permutationTuple = null;
		if (nArgs > 1
			&& peekExpression().equals(PERMUTE.marker))
		{
			// We just hit a permute of the top N items of the stack.  This
			// is due to a permutation of the top-level arguments of a call,
			// not an embedded guillemet expression (otherwise we would have
			// hit an L1_doMakeTuple instead of this call).  A literal phrase
			// containing the permutation to apply was pushed before the
			// marker.
			popExpression();
			final A_Phrase permutationLiteral = popExpression();
			assert permutationLiteral.phraseKindIsUnder(LITERAL_PHRASE);
			permutationTuple = permutationLiteral.token().literal();
		}
		final List<A_Phrase> argsList = popExpressions(nArgs);
		final A_Phrase listNode = newListNode(tupleFromList(argsList));
		final A_Phrase argsNode = permutationTuple != null
			? newPermutedListNode(listNode, permutationTuple)
			: listNode;
		return adjustSuperCastsIn(argsNode, superUnionType);
	}

	/**
	 * Convert some of the descendants within a {@link ListPhraseDescriptor
	 * list phrase} into {@link SuperCastPhraseDescriptor supercasts}, based
	 * on the given superUnionType.  Because the phrase is processed
	 * recursively, some invocations will pass a non-list phrase.
	 */
	private static A_Phrase adjustSuperCastsIn (
		final A_Phrase phrase,
		final A_Type superUnionType)
	{
		if (superUnionType.isBottom())
		{
			// No supercasts in this argument.
			return phrase;
		}
		else if (phrase.phraseKindIsUnder(PERMUTED_LIST_PHRASE))
		{
			// Apply the superUnionType's elements to the permuted list.
			final A_Tuple permutation = phrase.permutation();
			final A_Phrase list = phrase.list();
			final int size = list.expressionsSize();
			final A_Phrase[] outputArray = new A_Phrase[size];
			for (int i = 1; i <= size; i++)
			{
				final int index = permutation.tupleIntAt(i);
				final A_Phrase element = list.expressionAt(index);
				outputArray[index - 1] = adjustSuperCastsIn(
					element, superUnionType.typeAtIndex(i));
			}
			return newPermutedListNode(
				newListNode(tupleFromArray(outputArray)), permutation);
		}
		else if (phrase.phraseKindIsUnder(LIST_PHRASE))
		{
			// Apply the superUnionType's elements to the list.
			final int size = phrase.expressionsSize();
			final A_Phrase[] outputArray = new A_Phrase[size];
			for (int i = 1; i <= size; i++)
			{
				final A_Phrase element = phrase.expressionAt(i);
				outputArray[i - 1] = adjustSuperCastsIn(
					element, superUnionType.typeAtIndex(i));
			}
			return newListNode(tupleFromArray(outputArray));
		}
		else
		{
			return newSuperCastNode(phrase, superUnionType);
		}
	}

	/**
	 * Decompile the given {@link A_RawFunction}.  It treats outer variables as
	 * private {@linkplain A_Atom atom} literals.  Answer the resulting
	 * {@linkplain BlockPhraseDescriptor block}.
	 *
	 * @param code
	 *        The {@link A_RawFunction} to decompile.
	 * @return The {@linkplain BlockPhraseDescriptor block} that is the
	 *         decompilation of the provided raw function.
	 */
	public static A_Phrase decompile (final A_RawFunction code)
	{
		final Map<String, Integer> counts = new HashMap<>();
		final UnaryOperator<String> generator =
			prefix ->
			{
				@Nullable Integer newCount = counts.get(prefix);
				newCount = newCount == null ? 1 : newCount + 1;
				counts.put(prefix, newCount);
				return prefix + newCount;
			};
		// Synthesize fake outers to allow decompilation.
		final int outerCount = code.numOuters();
		final A_Phrase[] functionOuters = new A_Phrase[outerCount];
		for (int i = 1; i <= outerCount; i++)
		{
			functionOuters[i - 1] =
				outerPhraseForDecompiler(i, code.outerTypeAt(i));
		}
		final L1Decompiler decompiler = new L1Decompiler(
			code, functionOuters, generator);
		return decompiler.block();
	}

	/**
	 * Create a suitable dummy phrase to indicate an outer variable which is
	 * defined in a scope outside the scope of decompilation.
	 *
	 * @param outerIndex The index of the outer.
	 * @param type The type of the outer.
	 * @return A variable reference phrase.
	 */
	public static A_Phrase outerPhraseForDecompiler (
		final int outerIndex,
		final A_Type type)
	{
		final A_String name = stringFrom("Outer#" + outerIndex);
		final A_Variable var =
			newVariableWithOuterType(variableTypeFor(type));
		final A_Token token = literalToken(name, 0, 0, var);
		return fromTokenForDecompiler(token);
	}
}
