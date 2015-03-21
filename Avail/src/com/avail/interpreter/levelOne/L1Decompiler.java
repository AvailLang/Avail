/**
 * L1Decompiler.java
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

package com.avail.interpreter.levelOne;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.utility.evaluation.*;

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
	@InnerAccess A_RawFunction code;

	/**
	 * {@linkplain ParseNodeDescriptor Parse nodes} which correspond with the
	 * lexically captured variables.  These can be {@linkplain
	 * DeclarationNodeDescriptor declaration nodes} or {@linkplain
	 * LiteralNodeDescriptor literal nodes}, but the latter may be phased out
	 * in favor of module constants and module variables.
	 */
	@InnerAccess List<A_Phrase> outers;

	/**
	 * The {@linkplain
	 * com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind#ARGUMENT
	 * arguments declarations} for this code.
	 */
	@InnerAccess List<A_Phrase> args;

	/**
	 * The {@linkplain
	 * com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind#
	 * LOCAL_VARIABLE local variables} defined by this code.
	 */
	@InnerAccess List<A_Phrase> locals;

	/**
	 * The tuple of nybblecodes to decode.
	 */
	@InnerAccess A_Tuple nybbles;

	/**
	 * The current position in the instruction stream at which decompilation is
	 * currently occurring.
	 */
	@InnerAccess int pc;

	/**
	 * Something to generate unique variable names from a prefix.
	 */
	@InnerAccess Transformer1<String, String> tempGenerator;

	/**
	 * The stack of expressions roughly corresponding to the subexpressions that
	 * have been parsed but not yet integrated into their parent expressions.
	 */
	@InnerAccess List<A_Phrase> expressionStack =
		new ArrayList<A_Phrase>();

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
	 * @param outerVars
	 *        The list of outer variable declarations and literal nodes.
	 * @param tempBlock
	 *        A {@linkplain Transformer1 transformer} that takes a prefix and
	 *        generates a suitably unique temporary variable name.
	 */
	public L1Decompiler (
		final A_RawFunction aCodeObject,
		final List<A_Phrase> outerVars,
		final Transformer1<String, String> tempBlock)
	{
		code = aCodeObject;
		nybbles = code.nybbles();
		outers = outerVars;
		tempGenerator = tempBlock;
		args = new ArrayList<A_Phrase>(code.numArgs());
		locals = new ArrayList<A_Phrase>(code.numLocals());
		final A_Type tupleType = code.functionType().argsTupleType();
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			final String argName = tempGenerator.value("arg");
			final A_Token token = TokenDescriptor.create(
				StringDescriptor.from(argName),
				TupleDescriptor.empty(),
				TupleDescriptor.empty(),
				0,
				0,
				-1,
				TokenType.KEYWORD);
			final A_Phrase decl = DeclarationNodeDescriptor.newArgument(
				token,
				tupleType.typeAtIndex(i));
			args.add(decl);
		}
		for (int i = 1, end = code.numLocals(); i <= end; i++)
		{
			final String localName = tempGenerator.value("local");
			final A_Token token = TokenDescriptor.create(
				StringDescriptor.from(localName),
				TupleDescriptor.empty(),
				TupleDescriptor.empty(),
				0,
				0,
				-1,
				TokenType.KEYWORD);
			final A_Phrase decl = DeclarationNodeDescriptor.newVariable(
				token,
				code.localTypeAt(i).writeType());
			locals.add(decl);
		}
		statements.addAll(locals);
		//  Add all local declaration statements at the start.
		pc = 1;
		while (pc <= nybbles.tupleSize())
		{
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			final L1Operation op = L1Operation.all()[nybble];
			op.dispatch(dispatcher);
		}
		// Infallible primitives don't have nybblecodes... except the special
		// ones (340-342).
		if (pc > 1)
		{
			dispatcher.L1Implied_doReturn();
		}
		assert expressionStack.size() == 0
		: "There should be nothing on the stack after the final return";

		block = BlockNodeDescriptor.newBlockNode(
			args,
			code.primitiveNumber(),
			statements,
			code.functionType().returnType(),
			code.functionType().declaredExceptions(),
			0);
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
	 * Extract an encoded integer from the nybblecode instruction stream.  The
	 * encoding uses only a nybble for very small operands, and can still
	 * represent up to {@link Integer#MAX_VALUE} if necessary.
	 * <p>
	 * Adjust the {@link #pc program counter} to skip the integer.
	 *
	 * @return The integer extracted from the nybblecode stream.
	 */
	@InnerAccess int getInteger ()
	{
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		int value = 0;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[firstNybble]; count > 0; count--, pc++)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc);
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[firstNybble];
		return value;
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
	static enum MarkerTypes {
		/**
		 * A marker standing for a duplicate of some value that was on the
		 * stack.
		 */
		dup,

		/**
		 * A marker standing for the type of some value that was on the stack.
		 */
		getType,

		/**
		 * A marker indicating the value below it has been permuted, and should
		 * be checked by a subsequent call operation;
		 */
		permute;

		/**
		 * A pre-built marker for this enumeration value.
		 */
		public final A_Phrase marker = MarkerNodeDescriptor.create(
			IntegerDescriptor.fromInt(ordinal()));
	}

	/**
	 * The {@link L1OperationDispatcher} used by the {@linkplain L1Decompiler
	 * decompiler} to dispatch {@link L1Operation}s. This allows {@code
	 * L1Decompiler} to hide the implemented interface methods of {@code
	 * L1OperationDispatcher}.
	 */
	private final L1OperationDispatcher dispatcher = new L1OperationDispatcher()
	{
		@Override
		public void L1_doCall ()
		{
			final A_Bundle bundle = code.literalAt(getInteger());
			final A_Type type = code.literalAt(getInteger());
			final A_Method method = bundle.bundleMethod();
			final int nArgs = method.numArgs();
			@Nullable A_Tuple permutationTuple = null;
			if (nArgs > 1
				&& peekExpression().equals(MarkerTypes.permute.marker))
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
			final A_Tuple argsTuple =
				TupleDescriptor.fromList(popExpressions(nArgs));
			A_Phrase listNode = ListNodeDescriptor.newExpressions(argsTuple);
			if (permutationTuple != null)
			{
				listNode = PermutedListNodeDescriptor.fromListAndPermutation(
					listNode, permutationTuple);
			}
			final A_Phrase sendNode = SendNodeDescriptor.from(
				bundle, listNode, type);
			pushExpression(sendNode);
		}

		@Override
		public void L1_doPushLiteral ()
		{
			final AvailObject value = code.literalAt(getInteger());
			if (value.isInstanceOfKind(
				FunctionTypeDescriptor.mostGeneralType()))
			{
				final List<A_Phrase> functionOuters =
					new ArrayList<>(value.numOuterVars());
				// Due to stub-building primitives, it's possible for a
				// non-clean function to be a literal, so deal with it here.
				for (int i = 1; i <= value.numOuterVars(); i++)
				{
					final AvailObject varObject = value.outerVarAt(i);
					final A_Token token =
						LiteralTokenDescriptor.create(
							StringDescriptor.from(
								"OuterOfUncleanConstantFunction#"
								+ i
								+ " (with value "
								+ varObject.toString()
								+ ")"),
							TupleDescriptor.empty(),
							TupleDescriptor.empty(),
							0,
							0,
							-1,
							TokenType.LITERAL,
							varObject);
					final A_Phrase literalNode =
						LiteralNodeDescriptor.fromToken(token);
					functionOuters.add(literalNode);
				}
				final A_Phrase blockNode =
					new L1Decompiler(
						value.code(),
						functionOuters,
						tempGenerator
					).block();
				pushExpression(blockNode);
			}
			else
			{
				if (value.equalsNil())
				{
					// The last "statement" may just push nil.
					// Such a statement will be re-synthesized during code
					// generation, so don't bother reconstructing it now.
					assert pc > nybbles.tupleSize()
					: "nil can only be (implicitly) pushed at the "
						+ "end of a sequence of statements";
					endsWithPushNil = true;
				}
				else
				{
					final AvailObject token =
						LiteralTokenDescriptor.create(
							StringDescriptor.from(value.toString()),
							TupleDescriptor.empty(),
							TupleDescriptor.empty(),
							0,
							0,
							-1,
							TokenType.LITERAL,
							value);
					final AvailObject literalNode =
						LiteralNodeDescriptor.fromToken(token);
					pushExpression(literalNode);
				}
			}
		}

		@Override
		public void L1_doPushLastLocal ()
		{
			final int index = getInteger();
			final boolean isArg = index <= code.numArgs();
			final A_Phrase decl = isArg
				? args.get(index - 1)
				: locals.get(index - code.numArgs() - 1);
			final A_Phrase use = VariableUseNodeDescriptor.newUse(
				decl.token(), decl);
			use.isLastUse(true);
			if (isArg)
			{
				pushExpression(use);
			}
			else
			{
				final A_Phrase ref = ReferenceNodeDescriptor.fromUse(use);
				pushExpression(ref);
			}
		}

		@Override
		public void L1_doPushLocal ()
		{
			final int index = getInteger();
			final boolean isArg = index <= code.numArgs();
			final A_Phrase decl = isArg
				? args.get(index - 1)
				: locals.get(index - code.numArgs() - 1);
			final AvailObject use = VariableUseNodeDescriptor.newUse(
				decl.token(),
				decl);
			if (isArg)
			{
				pushExpression(use);
			}
			else
			{
				final A_Phrase ref = ReferenceNodeDescriptor.fromUse(use);
				pushExpression(ref);
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
			final int nOuters = getInteger();
			final A_RawFunction theCode = code.literalAt(getInteger());
			final List<A_Phrase> theOuters = popExpressions(nOuters);
			for (final A_Phrase outer : theOuters)
			{
				assert
					outer.isInstanceOfKind(VARIABLE_USE_NODE.mostGeneralType())
					|| outer.isInstanceOfKind(REFERENCE_NODE.mostGeneralType())
					|| outer.isInstanceOfKind(LITERAL_NODE.mostGeneralType());
			}
			final A_Phrase blockNode =
				new L1Decompiler(theCode, theOuters, tempGenerator).block();
			pushExpression(blockNode);
		}

		@Override
		public void L1_doSetLocal ()
		{
			final A_Phrase localDecl = locals.get(
				getInteger() - code.numArgs() - 1);
			final A_Phrase valueNode = popExpression();
			final A_Phrase variableUse = VariableUseNodeDescriptor.newUse(
				localDecl.token(), localDecl);
			final A_Phrase assignmentNode = AssignmentNodeDescriptor.from(
				variableUse, valueNode, false);
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
				assert duplicateExpression.equals(MarkerTypes.dup.marker);
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
			pushExpression(outers.get(getInteger() - 1));
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
					newStatements = TupleDescriptor.from(
						penultimateExpression, lastExpression);
				}
				pushExpression(
					FirstOfSequenceNodeDescriptor.newStatements(
						newStatements));
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
			final A_Phrase outerExpr = outers.get(getInteger() - 1);
			final A_Phrase outerDecl;
			if (outerExpr.isInstanceOfKind(LITERAL_NODE.mostGeneralType()))
			{
				// Writing into a synthetic literal (a byproduct of decompiling
				// a block without decompiling its outer scopes).
				final A_Token token = outerExpr.token();
				final A_BasicObject variableObject = token.literal();
				assert variableObject.isInstanceOfKind(
					VariableTypeDescriptor.mostGeneralType());
				outerDecl = DeclarationNodeDescriptor.newModuleVariable(
					token, variableObject, NilDescriptor.nil());
			}
			else
			{
				assert outerExpr.isInstanceOfKind(
					REFERENCE_NODE.mostGeneralType());
				outerDecl = outerExpr.variable().declaration();
			}
			final A_Phrase variableUse = VariableUseNodeDescriptor.newUse(
				outerDecl.token(), outerDecl);
			final A_Phrase valueExpr = popExpression();
			final A_Phrase assignmentNode =
				AssignmentNodeDescriptor.from(variableUse, valueExpr, false);
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
				assert duplicateExpression.equals(MarkerTypes.dup.marker);
				expressionStack.add(assignmentNode);
			}
		}

		@Override
		public void L1_doGetLocal ()
		{
			final A_Phrase localDecl =
				locals.get(getInteger() - code.numArgs() - 1);
			final AvailObject useNode = VariableUseNodeDescriptor.newUse(
				localDecl.token(), localDecl);
			pushExpression(useNode);
		}

		@Override
		public void L1_doMakeTuple ()
		{
			final int count = getInteger();
			@Nullable A_Tuple permutationTuple = null;
			if (count > 1
				&& peekExpression().equals(MarkerTypes.permute.marker))
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
			A_Phrase listNode = ListNodeDescriptor.newExpressions(
				TupleDescriptor.fromList(expressions));
			if (permutationTuple != null)
			{
				listNode = PermutedListNodeDescriptor.fromListAndPermutation(
					listNode, permutationTuple);
			}
			pushExpression(listNode);
		}

		@Override
		public void L1_doGetOuter ()
		{
			final A_Phrase use;
			final int outerIndex = getInteger();
			final A_Phrase outer = outers.get(outerIndex - 1);
			if (outer.parseNodeKindIsUnder(LITERAL_NODE))
			{
				pushExpression(outer);
				return;
			}
			final A_Phrase outerDecl = outer.variable().declaration();
			use = VariableUseNodeDescriptor.newUse(
				outerDecl.token(),
				outerDecl);
			pushExpression(use);
		}

		@Override
		public void L1_doExtension ()
		{
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.all()[nybble + 16].dispatch(this);
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
				final A_Token labelToken = TokenDescriptor.create(
					StringDescriptor.from(tempGenerator.valueNotNull("label")),
					TupleDescriptor.empty(),
					TupleDescriptor.empty(),
					0,
					0,
					-1,
					TokenType.KEYWORD);
				label = DeclarationNodeDescriptor.newLabel(
					labelToken,
					ContinuationTypeDescriptor.forFunctionType(
						code.functionType()));
				statements.add(0, label);
			}

			final A_Phrase useNode = VariableUseNodeDescriptor.newUse(
				label.token(),
				label);
			pushExpression(useNode);
		}

		@Override
		public void L1Ext_doGetLiteral ()
		{
			final A_Token globalToken = TokenDescriptor.create(
				StringDescriptor.from("SomeGlobal"),
				TupleDescriptor.empty(),
				TupleDescriptor.empty(),
				0,
				0,
				-1,
				TokenType.KEYWORD);
			final A_BasicObject globalVar = code.literalAt(getInteger());

			final A_Phrase decl =
				DeclarationNodeDescriptor.newModuleVariable(
					globalToken, globalVar, NilDescriptor.nil());
			final A_Phrase varUse = VariableUseNodeDescriptor.newUse(
				globalToken, decl);
			pushExpression(varUse);
		}

		@Override
		public void L1Ext_doSetLiteral ()
		{
			final A_Token globalToken = TokenDescriptor.create(
				StringDescriptor.from("SomeGlobal"),
				TupleDescriptor.empty(),
				TupleDescriptor.empty(),
				0,
				0,
				-1,
				TokenType.KEYWORD);
			final AvailObject globalVar = code.literalAt(getInteger());
			final A_Phrase declaration =
				DeclarationNodeDescriptor.newModuleVariable(
					globalToken,
					globalVar,
					NilDescriptor.nil());
			final A_Phrase varUse = VariableUseNodeDescriptor.newUse(
				globalToken, declaration);
			final A_Phrase assignmentNode =
				AssignmentNodeDescriptor.from(varUse, popExpression(), false);
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
				assert duplicateExpression.equals(MarkerTypes.dup.marker);
				expressionStack.add(assignmentNode);
			}
		}

		/**
		 * The presence of the {@linkplain L1Operation operation} indicates that
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
			pushExpression(MarkerTypes.dup.marker);
			pushExpression(rightSide);
		}

		@Override
		public void L1Ext_doPermute ()
		{
			// Note that this applies to any guillemet group, not just the top
			// level implicit list of arguments to a call.  It's also used for
			// permuting both the arguments and their types in the case of a
			// super-call to a bundle containing permutations.
			final A_Tuple permutation = code.literalAt(getInteger());
			pushExpression(LiteralNodeDescriptor.syntheticFrom(permutation));
			pushExpression(MarkerTypes.permute.marker);
		}

		@Override
		public void L1Ext_doGetType ()
		{
			pushExpression(MarkerTypes.getType.marker);
		}

		@Override
		public void L1Ext_doMakeTupleAndType ()
		{
			final int listSize = getInteger();
			final A_Phrase listNode = reconstructListWithSupercasts(listSize);
			pushExpression(listNode);
			pushExpression(MarkerTypes.getType.marker);
		}

		@Override
		public void L1Ext_doSuperCall ()
		{
			final A_Bundle bundle = code.literalAt(getInteger());
			final A_Type type = code.literalAt(getInteger());
			final A_Method method = bundle.bundleMethod();
			final int nArgs = method.numArgs();
			final A_Phrase argsNode = reconstructListWithSupercasts(nArgs);
			final A_Phrase sendNode = SendNodeDescriptor.from(
				bundle, argsNode, type);
			pushExpression(sendNode);
		}

		@Override
		public void L1Ext_doReserved ()
		{
			error("Illegal extended nybblecode: F+"
				+ nybbles.extractNybbleFromTupleAt(pc - 1));
		}

		@Override
		public void L1Implied_doReturn ()
		{
			assert pc == nybbles.tupleSize() + 1;
			if (endsWithPushNil)
			{
				// Nothing was left on the expression stack in this case.
			}
			else
			{
				statements.add(popExpression());
			}
			assert expressionStack.size() == 0
			: "There should be nothing on the stack after a return";
		}

		/**
		 * Given that there are {@code nArgs} values and types interleaved on
		 * the stack, pop all 2*nArgs of them, and build a suitable list phrase
		 * (or permuted list phrase if there was also a permutation literal and
		 * permute marker pushed after).  The list phrase either includes at
		 * least one supercast directly, or contains at least one list phrase
		 * recursively that includes one or more supercasts.  The non-supercast
		 * elements have getType markers instead of type literals.
		 *
		 * @param nArgs
		 *        The number of <argument, type> pairs to expect on the stack.
		 *        This is also the size of the resulting list phrase.
		 * @return A list phrase or permuted list phrase containing at least one
		 *         supercast somewhere within the recursive list structure.
		 */
		private A_Phrase reconstructListWithSupercasts (final int nArgs)
		{
			@Nullable A_Tuple permutationTuple = null;
			if (nArgs > 1
				&& peekExpression().equals(MarkerTypes.permute.marker))
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
				final A_Tuple bigPermutationTuple =
					permutationLiteral.token().literal();
				// Since this is a super call, the permutation tuple was doubled
				// in length to cause the <value,type> pairs (found in
				// consecutive slots) to be permuted as a unit.  Undo the
				// doubling to get the original permutation.
				final int bigPermutationSize = bigPermutationTuple.tupleSize();
				final List<Integer> undoubled =
					new ArrayList<>(bigPermutationSize >> 1);
				for (int i = 2; i <= bigPermutationSize; i += 2)
				{
					undoubled.add(bigPermutationTuple.tupleIntAt(i) >> 1);
				}
				permutationTuple = TupleDescriptor.fromIntegerList(undoubled);
			}
			final List<A_Phrase> argsList = new ArrayList<>(nArgs);
			for (int i = 1; i <= nArgs; i++)
			{
				final A_Phrase argTypeExpr = popExpression();
				final A_Phrase arg = popExpression();
				if (argTypeExpr.equals(MarkerTypes.getType.marker))
				{
					argsList.add(0, arg);
				}
				else
				{
					argsList.add(
						0,
						SuperCastNodeDescriptor.create(
							arg,
							argTypeExpr.token().literal()));
				}
			}
			final A_Phrase listNode = ListNodeDescriptor.newExpressions(
				TupleDescriptor.fromList(argsList));
			final A_Phrase argsNode = permutationTuple != null
				? PermutedListNodeDescriptor.fromListAndPermutation(
					listNode, permutationTuple)
				: listNode;
			return argsNode;
		}
	};

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
		final Transformer1<String, String> generator =
			new Transformer1<String, String>()
			{
				@Override
				public String value (final @Nullable String prefix)
				{
					assert prefix != null;
					Integer newCount = counts.get(prefix);
					newCount = newCount == null ? 1 : newCount + 1;
					counts.put(prefix, newCount);
					return prefix + newCount.toString();
				}
			};
		// Synthesize fake outers as literals to allow decompilation.
		final int outerCount = aFunction.numOuterVars();
		final List<A_Phrase> functionOuters = new ArrayList<>(outerCount);
		for (int i = 1; i <= outerCount; i++)
		{
			final A_BasicObject outerObject = aFunction.outerVarAt(i);
			final A_Token token = LiteralTokenDescriptor.create(
				StringDescriptor.from("Outer#" + i),
				TupleDescriptor.empty(),
				TupleDescriptor.empty(),
				0,
				0,
				-1,
				TokenType.SYNTHETIC_LITERAL,
				outerObject);
			final A_Phrase literalNode =
				LiteralNodeDescriptor.fromTokenForDecompiler(token);
			functionOuters.add(literalNode);
		}
		return new L1Decompiler(
				aFunction.code(),
				functionOuters,
				generator
			).block();
	}
}
