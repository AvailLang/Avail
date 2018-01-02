/**
 * L1Decompiler.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.utility.MutableInt;
import com.avail.utility.evaluation.Transformer1NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.avail.descriptor.AssignmentNodeDescriptor.newAssignment;
import static com.avail.descriptor.BlockNodeDescriptor.newBlockNode;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.continuationTypeForFunctionType;
import static com.avail.descriptor.DeclarationNodeDescriptor.*;
import static com.avail.descriptor.FirstOfSequenceNodeDescriptor
	.newFirstOfSequenceNode;
import static com.avail.descriptor.FunctionTypeDescriptor
	.mostGeneralFunctionType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.ListNodeDescriptor.newListNode;
import static com.avail.descriptor.LiteralNodeDescriptor.*;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.MarkerNodeDescriptor.newMarkerNode;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.PermutedListNodeDescriptor
	.newPermutedListNode;
import static com.avail.descriptor.ReferenceNodeDescriptor.referenceNodeFromUse;
import static com.avail.descriptor.SendNodeDescriptor.newSendNode;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.SuperCastNodeDescriptor.newSuperCastNode;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TokenDescriptor.newToken;
import static com.avail.descriptor.TupleDescriptor.*;
import static com.avail.descriptor.VariableTypeDescriptor
	.mostGeneralVariableType;
import static com.avail.descriptor.VariableUseNodeDescriptor.newUse;
import static com.avail.interpreter.levelOne.L1Decompiler.MarkerTypes.DUP;
import static com.avail.interpreter.levelOne.L1Decompiler.MarkerTypes.PERMUTE;
import static com.avail.utility.PrefixSharingList.last;

/**
 * The {@link L1Decompiler} converts a {@linkplain CompiledCodeDescriptor
 * compiled code} object into an equivalent {@linkplain ParseNodeDescriptor
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
	@InnerAccess
	final A_RawFunction code;

	/**
	 * The number of nybbles in the nybblecodes of the raw function being
	 * decompiled.
	 */
	@InnerAccess
	final int numNybbles;

	/**
	 * {@linkplain ParseNodeDescriptor Parse nodes} which correspond with the
	 * lexically captured variables.  These can be {@linkplain
	 * DeclarationNodeDescriptor declaration nodes} or {@linkplain
	 * LiteralNodeDescriptor literal nodes}, but the latter may be phased out
	 * in favor of module constants and module variables.
	 */
	@InnerAccess
	final A_Phrase[] outers;

	/**
	 * The {@linkplain DeclarationKind#ARGUMENT
	 * arguments declarations} for this code.
	 */
	@InnerAccess
	final A_Phrase[] args;

	/**
	 * The {@linkplain DeclarationKind#LOCAL_VARIABLE local variables} defined
	 * by this code.
	 */
	@InnerAccess
	final A_Phrase[] locals;

	/**
	 * Flags to indicate which local variables have been mentioned.  Upon first
	 * mention, the corresponding local declaration should be emitted.
	 */
	@InnerAccess
	final boolean[] mentionedLocals;

	/**
	 * The {@linkplain DeclarationKind#LOCAL_CONSTANT local constants} defined
	 * by this code.
	 */
	@InnerAccess
	final A_Phrase[] constants;

	/**
	 * The current position in the instruction stream at which decompilation is
	 * currently occurring.
	 */
	@InnerAccess final MutableInt pc = new MutableInt(1);

	/**
	 * Something to generate unique variable names from a prefix.
	 */
	@InnerAccess
	final Transformer1NotNull<String, String> tempGenerator;

	/**
	 * The stack of expressions roughly corresponding to the subexpressions that
	 * have been parsed but not yet integrated into their parent expressions.
	 */
	@InnerAccess
	final List<A_Phrase> expressionStack = new ArrayList<>();

	/**
	 * The list of completely decompiled {@linkplain ParseNodeDescriptor
	 * statements}.
	 */
	@InnerAccess final List<A_Phrase> statements =
		new ArrayList<>();

	/**
	 * A flag to indicate that the last instruction was a push of the null
	 * object.
	 */
	@InnerAccess boolean endsWithPushNil = false;

	/**
	 * The decompiled {@linkplain BlockNodeDescriptor block node}.
	 */
	@InnerAccess A_Phrase block;

	/**
	 * Create a new decompiler suitable for decoding the given raw function,
	 * tuple of outer variable declarations, and temporary name generator.
	 *
	 * @param aCodeObject
	 *        The {@linkplain CompiledCodeDescriptor code} to decompile.
	 * @param outerDeclarations
	 *        The array of outer variable declarations and literal nodes.
	 * @param tempBlock
	 *        A {@linkplain Transformer1NotNull transformer} that takes a prefix
	 *        and generates a suitably unique temporary variable name.
	 */
	public L1Decompiler (
		final A_RawFunction aCodeObject,
		final A_Phrase[] outerDeclarations,
		final Transformer1NotNull<String, String> tempBlock)
	{
		assert pc.value == 1;
		code = aCodeObject;
		numNybbles = aCodeObject.numNybbles();
		outers = outerDeclarations.clone();
		tempGenerator = tempBlock;
		args = new A_Phrase[code.numArgs()];
		final A_Type tupleType = code.functionType().argsTupleType();
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			final A_Token token = newToken(
				stringFrom(tempGenerator.value("arg")),
				emptyTuple(),
				emptyTuple(),
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
				stringFrom(tempGenerator.value("local")),
				emptyTuple(),
				emptyTuple(),
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
		while (pc.value <= numNybbles)
		{
			code.nextNybblecodeOperation(pc).dispatch(dispatcher);
		}
		// Infallible primitives don't have nybblecodes, except ones marked as
		// Primitive.Flag.SpecialForm.
		if (numNybbles > 0)
		{
			assert pc.value == numNybbles + 1;
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
			tuple(args),
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
	 * Answer the {@linkplain BlockNodeDescriptor block node} which is a
	 * decompilation of the previously supplied raw function.
	 *
	 * @return A block node which is a decompilation of the raw function.
	 */
	public A_Phrase block ()
	{
		return block;
	}

	/**
	 * Answer the top value of the expression stack without removing it.
	 *
	 * @return The parse node that's still on the top of the stack.
	 */
	@InnerAccess A_Phrase peekExpression ()
	{
		return expressionStack.get(expressionStack.size() - 1);
	}

	/**
	 * Pop one {@linkplain ParseNodeDescriptor parse node} off the expression
	 * stack and return it.
	 *
	 * @return The parse node popped off the stack.
	 */
	@InnerAccess A_Phrase popExpression ()
	{
		return expressionStack.remove(expressionStack.size() - 1);
	}

	/**
	 * Pop some {@linkplain ParseNodeDescriptor parse nodes} off the expression
	 * stack and return them in a {@linkplain List list}.
	 *
	 * @param count The number of parse nodes to pop.
	 * @return The list of {@code count} parse nodes, in the order they were
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
	 * Push the given {@linkplain ParseNodeDescriptor parse node} onto the
	 * expression stack.
	 *
	 * @param expression The expression to push.
	 */
	void pushExpression (
		final A_Phrase expression)
	{
		expressionStack.add(expression);
	}

	/**
	 * An {@link Enum} whose ordinals can be used as marker values in
	 * {@linkplain MarkerNodeDescriptor marker nodes}.
	 */
	enum MarkerTypes {
		/**
		 * A marker standing for a duplicate of some value that was on the
		 * stack.
		 */
		DUP,

		/**
		 * A marker indicating the value below it has been permuted, and should
		 * be checked by a subsequent call operation;
		 */
		PERMUTE;

		/**
		 * A pre-built marker for this enumeration value.
		 */
		public final A_Phrase marker = newMarkerNode(fromInt(ordinal()));
	}

	private class DecompilerDispatcher implements L1OperationDispatcher
	{
		@Override
		public void L1_doCall ()
		{
			final A_Bundle bundle = code.literalAt(
				code.nextNybblecodeOperand(pc));
			final A_Type type = code.literalAt(code.nextNybblecodeOperand(pc));
			final A_Method method = bundle.bundleMethod();
			final int nArgs = method.numArgs();
			@Nullable A_Tuple permutationTuple = null;
			if (nArgs > 1 && peekExpression().equals(PERMUTE.marker))
			{
				// We just hit a permute of the top N items of the stack.  This
				// is due to a permutation of the top-level arguments of a call,
				// not an embedded guillemet expression (otherwise we would have
				// hit an L1_doMakeTuple instead of this call).  A literal node
				// containing the permutation to apply was pushed before the
				// marker.
				popExpression();
				final A_Phrase permutationLiteral = popExpression();
				assert permutationLiteral.parseNodeKindIsUnder(LITERAL_NODE);
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
				code.nextNybblecodeOperand(pc));
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
							emptyTuple(),
							emptyTuple(),
							0,
							0,
							LITERAL,
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
					assert pc.value > numNybbles
						: "nil can only be (implicitly) pushed at the "
						+ "end of a sequence of statements";
					endsWithPushNil = true;
				}
				else
				{
					final A_Token token =
						literalToken(
							stringFrom(value.toString()),
							emptyTuple(),
							emptyTuple(),
							0,
							0,
							LITERAL,
							value);
					pushExpression(literalNodeFromToken(token));
				}
			}
		}

		@Override
		public void L1_doPushLastLocal ()
		{
			final A_Phrase declaration = argOrLocalOrConstant(
				code.nextNybblecodeOperand(pc));
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
				code.nextNybblecodeOperand(pc));
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
			final int nOuters = code.nextNybblecodeOperand(pc);
			final A_RawFunction theCode = code.literalAt(
				code.nextNybblecodeOperand(pc));
			final List<A_Phrase> theOuters = popExpressions(nOuters);
			for (final A_Phrase outer : theOuters)
			{
				final ParseNodeKind kind = outer.parseNodeKind();
				assert kind == VARIABLE_USE_NODE
					|| kind == REFERENCE_NODE
					|| kind == LITERAL_NODE;
			}
			final L1Decompiler decompiler = new L1Decompiler(
				theCode,
				theOuters.toArray(new A_Phrase[theOuters.size()]),
				tempGenerator);
			pushExpression(decompiler.block());
		}

		@Override
		public void L1_doSetLocal ()
		{
			final int previousStatementCount = statements.size();
			final int indexInFrame = code.nextNybblecodeOperand(pc);
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
				|| peekExpression().parseNodeKind() != MARKER_NODE)
			{
				statements.add(assignmentNode);
			}
			else
			{
				// We had better see a dup marker, otherwise the code generator
				// didn't do what we expected.  Remove the marker and replace it
				// with the (embedded) assignment node itself.
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
			pushExpression(outers[code.nextNybblecodeOperand(pc) - 1]);
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
				// FirstOfSequence node.  Determine if we're constructing or
				// extending an existing FirstOfSequence.
				final A_Phrase lastExpression = popExpression();
				final A_Phrase penultimateExpression = popExpression();
				final A_Tuple newStatements;
				if (penultimateExpression.parseNodeKind()
					== FIRST_OF_SEQUENCE_NODE)
				{
					// Extend an existing FirstOfSequence node.
					newStatements =
						penultimateExpression.statements().appendCanDestroy(
							lastExpression, false);
				}
				else
				{
					// Create a two-element FirstOfSequence node.
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
			final A_Phrase outerExpr =
				outers[code.nextNybblecodeOperand(pc) - 1];
			final A_Phrase outerDeclaration;
			if (outerExpr.isInstanceOfKind(LITERAL_NODE.mostGeneralType()))
			{
				// Writing into a synthetic literal (a byproduct of decompiling
				// a block without decompiling its outer scopes).
				final A_Token token = outerExpr.token();
				final A_BasicObject variableObject = token.literal();
				assert variableObject.isInstanceOfKind(
					mostGeneralVariableType());
				outerDeclaration =
					newModuleVariable(token, variableObject, nil, nil);
			}
			else
			{
				assert outerExpr.isInstanceOfKind(
					REFERENCE_NODE.mostGeneralType());
				outerDeclaration = outerExpr.variable().declaration();
			}
			final A_Phrase variableUse =
				newUse(outerDeclaration.token(), outerDeclaration);
			final A_Phrase valueExpr = popExpression();
			final A_Phrase assignmentNode =
				newAssignment(variableUse, valueExpr, emptyTuple(), false);
			if (expressionStack.isEmpty())
			{
				statements.add(assignmentNode);
			}
			else
			{
				// We had better see a dup marker, otherwise the code generator
				// didn't do what we expected.  Remove that marker and replace
				// it with the (embedded) assignment node itself.
				final A_Phrase duplicateExpression = popExpression();
				assert duplicateExpression.equals(DUP.marker);
				expressionStack.add(assignmentNode);
			}
		}

		@Override
		public void L1_doGetLocal ()
		{
			final A_Phrase localDeclaration = argOrLocalOrConstant(
				code.nextNybblecodeOperand(pc));
			assert localDeclaration.declarationKind().isVariable();
			final AvailObject useNode =
				newUse(localDeclaration.token(), localDeclaration);
			pushExpression(useNode);
		}

		@Override
		public void L1_doMakeTuple ()
		{
			final int count = code.nextNybblecodeOperand(pc);
			@Nullable A_Tuple permutationTuple = null;
			if (count > 1 && peekExpression().equals(PERMUTE.marker))
			{
				// We just hit a permute of the top N items of the stack.  This
				// is due to a permutation within a guillemet expression.  A
				// literal node containing the permutation to apply was pushed
				// before the marker.
				popExpression();
				final A_Phrase permutationLiteral = popExpression();
				assert permutationLiteral.parseNodeKindIsUnder(LITERAL_NODE);
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
			final A_Phrase outer = outers[code.nextNybblecodeOperand(pc) - 1];
			if (outer.parseNodeKindIsUnder(LITERAL_NODE))
			{
				pushExpression(outer);
				return;
			}
			final A_Phrase declaration = outer.variable().declaration();
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
					LABEL_NODE.mostGeneralType()))
			{
				label = statements.get(0);
			}
			else
			{
				final A_Token labelToken = newToken(
					stringFrom(tempGenerator.value("label")),
					emptyTuple(),
					emptyTuple(),
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
				emptyTuple(),
				emptyTuple(),
				0,
				0,
				KEYWORD);
			final A_BasicObject globalVar = code.literalAt(
				code.nextNybblecodeOperand(pc));
			final A_Phrase declaration =
				newModuleVariable(globalToken, globalVar, nil, nil);
			pushExpression(newUse(globalToken, declaration));
		}

		@Override
		public void L1Ext_doSetLiteral ()
		{
			final A_Token globalToken = newToken(
				stringFrom("SomeGlobal"),
				emptyTuple(),
				emptyTuple(),
				0,
				0,
				KEYWORD);
			final AvailObject globalVar = code.literalAt(
				code.nextNybblecodeOperand(pc));
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
				// it with the (embedded) assignment node itself.
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
		 * assignment), push a special {@linkplain MarkerNodeDescriptor
		 * marker node} representing the dup, then push the right-hand side
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
				code.nextNybblecodeOperand(pc));
			pushExpression(syntheticLiteralNodeFor(permutation));
			pushExpression(PERMUTE.marker);
		}

		@Override
		public void L1Ext_doSuperCall ()
		{
			final A_Bundle bundle = code.literalAt(
				code.nextNybblecodeOperand(pc));
			final A_Type type = code.literalAt(code.nextNybblecodeOperand(pc));
			final A_Type superUnionType = code.literalAt(
				code.nextNybblecodeOperand(pc));

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
				code.nextNybblecodeOperand(pc)
					- code.numArgs() - code.numLocals() - 1;
			final A_Token token = newToken(
				stringFrom(tempGenerator.value("const")),
				emptyTuple(),
				emptyTuple(),
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
			// hit an L1_doMakeTuple instead of this call).  A literal node
			// containing the permutation to apply was pushed before the
			// marker.
			popExpression();
			final A_Phrase permutationLiteral = popExpression();
			assert permutationLiteral.parseNodeKindIsUnder(LITERAL_NODE);
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
	 * Convert some of the descendants within a {@link ListNodeDescriptor
	 * list phrase} into {@link SuperCastNodeDescriptor supercasts}, based
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
		else if (phrase.parseNodeKindIsUnder(PERMUTED_LIST_NODE))
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
				newListNode(tuple(outputArray)), permutation);
		}
		else if (phrase.parseNodeKindIsUnder(LIST_NODE))
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
			return newListNode(tuple(outputArray));
		}
		else
		{
			return newSuperCastNode(phrase, superUnionType);
		}
	}

	/**
	 * Parse the given statically constructed function.  It treats outer
	 * variables as literals.  Answer the resulting {@linkplain
	 * BlockNodeDescriptor block}.
	 *
	 * @param aFunction The function to decompile.
	 * @return The {@linkplain BlockNodeDescriptor block} that is the
	 *         decompilation of the provided function.
	 */
	public static A_Phrase parse (final A_Function aFunction)
	{
		final Map<String, Integer> counts = new HashMap<>();
		final Transformer1NotNull<String, String> generator =
			prefix ->
			{
				Integer newCount = counts.get(prefix);
				newCount = newCount == null ? 1 : newCount + 1;
				counts.put(prefix, newCount);
				//noinspection StringConcatenationMissingWhitespace
				return prefix + newCount;
			};
		// Synthesize fake outers as literals to allow decompilation.
		final int outerCount = aFunction.numOuterVars();
		final A_Phrase[] functionOuters = new A_Phrase[outerCount];
		for (int i = 1; i <= outerCount; i++)
		{
			final A_Token token = literalToken(
				stringFrom("Outer#" + i),
				emptyTuple(),
				emptyTuple(),
				0,
				0,
				SYNTHETIC_LITERAL,
				aFunction.outerVarAt(i));
			functionOuters[i - 1] = fromTokenForDecompiler(token);
		}
		final L1Decompiler decompiler = new L1Decompiler(
			aFunction.code(), functionOuters, generator);
		return decompiler.block();
	}
}
