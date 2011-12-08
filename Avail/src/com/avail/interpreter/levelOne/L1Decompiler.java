/**
 * interpreter/levelOne/L1Decompiler.java
 * Copyright (c) 2010, Mark van Gulik.
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
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.utility.Transformer1;

/**
 * The {@link L1Decompiler} converts a {@linkplain CompiledCodeDescriptor compiled
 * code} object into an equivalent {@linkplain ParseNodeDescriptor parse tree}.
 *
 * @author Mark van Gulik &lt;anarakul@gmail.com&gt;
 */
public class L1Decompiler
{
	/**
	 * The {@linkplain CompiledCodeDescriptor compiled code} which is being
	 * decompiled.
	 */
	@InnerAccess AvailObject code;

	/**
	 * {@linkplain ParseNodeDescriptor Parse nodes} which correspond with the
	 * lexically captured variables.  These can be {@linkplain
	 * DeclarationNodeDescriptor declaration nodes} or {@linkplain
	 * LiteralNodeDescriptor literal nodes}, but the latter may be phased out
	 * in favor of module constants and module variables.
	 */
	@InnerAccess List<AvailObject> outers;

	/**
	 * The {@linkplain
	 * com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind#ARGUMENT
	 * arguments declarations} for this code.
	 */
	@InnerAccess List<AvailObject> args;

	/**
	 * The {@linkplain
	 * com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind#
	 * LOCAL_VARIABLE local variables} defined by this code.
	 */
	@InnerAccess List<AvailObject> locals;

	/**
	 * The tuple of nybblecodes to decode.
	 */
	@InnerAccess AvailObject nybbles;

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
	@InnerAccess List<AvailObject> expressionStack =
		new ArrayList<AvailObject>();

	/**
	 * The list of completely decompiled {@linkplain ParseNodeDescriptor
	 * statements}.
	 */
	@InnerAccess List<AvailObject> statements = new ArrayList<AvailObject>();

	/**
	 * Parse the given compiled code object.  Its outer vars map to the given
	 * Array of expressions, and temp names are allocated via tempBlock.  The
	 * tempBlock takes a prefix string which can thereby distinguish arguments,
	 * locals, and labels.  Answer the resulting AvailBlockNode.
	 *
	 * @param aCodeObject
	 *        The {@linkplain CompiledCodeDescriptor code} to decompile.
	 * @param outerVars
	 *        The list of outer variable declarations and literal nodes.
	 * @param tempBlock
	 *        A {@linkplain Transformer1 transformer} that takes a prefix and
	 *        generates a suitably unique temporary variable name.
	 * @return The {@linkplain BlockNodeDescriptor block node} that is the
	 *         decompilation of the code.
	 */
	public AvailObject parseWithOuterVarsTempGenerator (
		final AvailObject aCodeObject,
		final List<AvailObject> outerVars,
		final Transformer1<String, String> tempBlock)
	{

		code = aCodeObject;
		outers = outerVars;
		tempGenerator = tempBlock;
		buildArgsAndLocals();

		statements.addAll(locals);
		//  Add all local declaration statements at the start.
		nybbles = code.nybbles();
		pc = 1;
		while (pc <= nybbles.tupleSize())
		{
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.values()[nybble].dispatch(dispatcher);
		}
		dispatcher.L1Implied_doReturn();
		assert expressionStack.size() == 0
		: "There should be nothing on the stack after the final return";

		return BlockNodeDescriptor.newBlockNode(
			args,
			code.primitiveNumber(),
			statements,
			aCodeObject.functionType().returnType(),
			aCodeObject.functionType().checkedExceptions());
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
	 * Pop one {@linkplain ParseNodeDescriptor parse node} off the expression
	 * stack and return it.
	 *
	 * @return The parse node popped off the stack.
	 */
	@InnerAccess AvailObject popExpression ()
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
	@InnerAccess List<AvailObject> popExpressions (final int count)
	{
		final List<AvailObject> result = new ArrayList<AvailObject>(count);
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
		final AvailObject expression)
	{
		expressionStack.add(expression);
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
			final AvailObject impSet = code.literalAt(getInteger());
			final AvailObject type = code.literalAt(getInteger());
			final AvailObject atom = impSet.name();
			int nArgs = 0;
			final AvailObject str = atom.name();
			final AvailObject underscore =
				StringDescriptor.underscore().tupleAt(1);
			for (int i = 1, end = str.tupleSize(); i <= end; i++)
			{
				if (str.tupleAt(i).equals(underscore))
				{
					nArgs++;
				}
			}
			final List<AvailObject> callArgs = popExpressions(nArgs);
			final AvailObject sendNode = SendNodeDescriptor.mutable().create();
			sendNode.arguments(TupleDescriptor.fromCollection(callArgs));
			sendNode.implementationSet(impSet);
			sendNode.returnType(type);

			pushExpression(sendNode);
		}

		@Override
		public void L1_doClose ()
		{
			final int nOuters = getInteger();
			final AvailObject theCode = code.literalAt(getInteger());
			final List<AvailObject> theOuters = popExpressions(nOuters);
			for (final AvailObject outer : theOuters)
			{
				assert
					outer.isInstanceOfKind(VARIABLE_USE_NODE.mostGeneralType())
					|| outer.isInstanceOfKind(REFERENCE_NODE.mostGeneralType())
					|| outer.isInstanceOfKind(LITERAL_NODE.mostGeneralType());
			}
			final AvailObject blockNode =
				new L1Decompiler().parseWithOuterVarsTempGenerator(
					theCode,
					theOuters,
					tempGenerator);
			pushExpression(blockNode);
		}

		@Override
		public void L1_doExtension ()
		{
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.values()[nybble + 16].dispatch(this);
		}

		@Override
		public void L1_doGetLocal ()
		{
			final AvailObject localDecl =
				locals.get(getInteger() - code.numArgs() - 1);
			final AvailObject useNode = VariableUseNodeDescriptor.newUse(
				localDecl.token(),
				localDecl);
			pushExpression(useNode);
		}

		@Override
		public void L1_doGetLocalClearing ()
		{
			L1_doGetLocal();
		}

		@Override
		public void L1_doGetOuter ()
		{
			final AvailObject use;
			final int outerIndex = getInteger();
			final AvailObject outer = outers.get(outerIndex - 1);
			if (outer.kind().parseNodeKindIsUnder(LITERAL_NODE))
			{
				pushExpression(outer);
				return;
			}
			final AvailObject outerDecl = outer.variable().declaration();
			use = VariableUseNodeDescriptor.newUse(
				outerDecl.token(),
				outerDecl);
			pushExpression(use);
		}

		@Override
		public void L1_doGetOuterClearing ()
		{
			L1_doGetOuter();
		}

		@Override
		public void L1_doMakeTuple ()
		{
			final int count = getInteger();
			final List<AvailObject> expressions = popExpressions(count);
			final AvailObject tupleNode = TupleNodeDescriptor.newExpressions(
				TupleDescriptor.fromCollection(expressions));
			pushExpression(tupleNode);
		}

		@Override
		public void L1_doPop ()
		{
			statements.add(popExpression());
			assert expressionStack.isEmpty();
		}

		@Override
		public void L1_doPushLastLocal ()
		{
			final int index = getInteger();
			final boolean isArg = index <= code.numArgs();
			final AvailObject decl = isArg
				? args.get(index - 1)
				: locals.get(index - code.numArgs() - 1);
			final AvailObject use = VariableUseNodeDescriptor.newUse(
				decl.token(),
				decl);
			use.isLastUse(true);
			if (isArg)
			{
				pushExpression(use);
			}
			else
			{
				final AvailObject ref = ReferenceNodeDescriptor.fromUse(use);
				pushExpression(ref);
			}
		}

		@Override
		public void L1_doPushLastOuter ()
		{
			L1_doPushOuter();
		}

		@Override
		public void L1_doPushLiteral ()
		{
			final AvailObject value = code.literalAt(getInteger());
			if (value.isInstanceOfKind(
				FunctionTypeDescriptor.mostGeneralType()))
			{
				final List<AvailObject> functionOuters =
					new ArrayList<AvailObject>(value.numOuterVars());
				// Due to stub-building primitives, it's possible for a
				// non-clean function to be a literal, so deal with it here.
				for (int i = 1; i <= value.numOuterVars(); i++)
				{
					final AvailObject varObject = value.outerVarAt(i);
					final AvailObject token =
						LiteralTokenDescriptor.mutable().create();
					token.tokenType(TokenType.LITERAL);
					token.string(StringDescriptor.from(
						"OuterOfUncleanConstantFunction#" + i));
					token.start(0);
					token.lineNumber(0);
					token.literal(varObject);
					final AvailObject literalNode =
						LiteralNodeDescriptor.fromToken(token);
					functionOuters.add(literalNode);
				}
				final AvailObject blockNode =
					new L1Decompiler().parseWithOuterVarsTempGenerator(
						value.code(),
						functionOuters,
						tempGenerator);
				pushExpression(blockNode);
			}
			else
			{
				final AvailObject token =
					LiteralTokenDescriptor.mutable().create();
				token.tokenType(TokenType.LITERAL);
				token.string(StringDescriptor.from(value.toString()));
				token.start(0);
				token.lineNumber(0);
				token.literal(value);
				final AvailObject literalNode =
					LiteralNodeDescriptor.fromToken(token);
				pushExpression(literalNode);
			}
		}

		@Override
		public void L1_doPushLocal ()
		{
			final int index = getInteger();
			final boolean isArg = index <= code.numArgs();
			final AvailObject decl = isArg
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
				final AvailObject ref = ReferenceNodeDescriptor.fromUse(use);
				pushExpression(ref);
			}
		}

		@Override
		public void L1_doPushOuter ()
		{
			pushExpression(outers.get(getInteger() - 1));
		}

		@Override
		public void L1_doSetLocal ()
		{
			final AvailObject localDecl = locals.get(
				getInteger() - code.numArgs() - 1);
			final AvailObject valueNode = popExpression();
			final AvailObject variableUse = VariableUseNodeDescriptor.newUse(
				localDecl.token(),
				localDecl);
			final AvailObject assignmentNode =
				AssignmentNodeDescriptor.mutable().create();
			assignmentNode.variable(variableUse);
			assignmentNode.expression(valueNode);
			if (expressionStack.isEmpty())
			{
				statements.add(assignmentNode);
			}
			else
			{
				// We had better see a marker with a top markerValue, otherwise
				// the code generator didn't do what we expected.  Remove that
				// bogus marker and replace it with the (embedded) assignment
				// node itself.
				final AvailObject duplicateExpression = popExpression();
				assert duplicateExpression.isInstanceOfKind(
					MARKER_NODE.mostGeneralType());
				assert duplicateExpression.markerValue().equalsNull();
				expressionStack.add(assignmentNode);
			}
		}

		@Override
		public void L1_doSetOuter ()
		{
			final AvailObject variableUse;
			final AvailObject outerExpr = outers.get(getInteger() - 1);
			final AvailObject outerDecl;
			if (outerExpr.isInstanceOfKind(LITERAL_NODE.mostGeneralType()))
			{
				// Writing into a synthetic literal (a byproduct of decompiling
				// a block without decompiling its outer scopes).
				final AvailObject token = outerExpr.token();
				final AvailObject variableObject = token.literal();
				assert variableObject.isInstanceOfKind(
					ContainerTypeDescriptor.mostGeneralType());
				outerDecl = DeclarationNodeDescriptor.newModuleVariable(
					token,
					variableObject);
			}
			else
			{
				assert outerExpr.isInstanceOfKind(
					REFERENCE_NODE.mostGeneralType());
				outerDecl = outerExpr.variable().declaration();
			}
			variableUse = VariableUseNodeDescriptor.newUse(
				outerDecl.token(),
				outerDecl);
			final AvailObject valueExpr = popExpression();
			final AvailObject assignmentNode =
				AssignmentNodeDescriptor.mutable().create();
			assignmentNode.variable(variableUse);
			assignmentNode.expression(valueExpr);
			if (expressionStack.isEmpty())
			{
				statements.add(assignmentNode);
			}
			else
			{
				// We had better see a marker with a top markerValue, otherwise
				// the code generator didn't do what we expected.  Remove that
				// bogus marker and replace it with the (embedded) assignment
				// node itself.
				final AvailObject duplicateExpression = popExpression();
				assert duplicateExpression.isInstanceOfKind(
					MARKER_NODE.mostGeneralType());
				assert duplicateExpression.markerValue().equalsNull();
				expressionStack.add(assignmentNode);
			}
		}

		/**
		 * The presence of the {@linkplain L1Operation operation} indicates that
		 * an assignment is being used as a subexpression or final statement
		 * from a non-void valued {@linkplain FunctionDescriptor block}.
		 *
		 * <p>Pop the expression (that represents the right hand side of the
		 * assignment), push a special {@linkplain MarkerNodeDescriptor marker}
		 * whose markerValue is the {@linkplain NullDescriptor#nullObject() null
		 * object}, then push the right-hand side expression back onto the
		 * expression stack.</p>
		 */
		@Override
		public void L1Ext_doDuplicate ()
		{
			final AvailObject rightSide = popExpression();
			final AvailObject marker = MarkerNodeDescriptor.mutable().create();
			marker.markerValue(NullDescriptor.nullObject());
			pushExpression(marker);
			pushExpression(rightSide);
		}

		@Override
		public void L1Ext_doGetLiteral ()
		{
			final AvailObject globalToken = TokenDescriptor.mutable().create();
			globalToken.tokenType(TokenType.KEYWORD);
			globalToken.string(StringDescriptor.from("SomeGlobal"));
			globalToken.start(0);
			final AvailObject globalVar = code.literalAt(getInteger());

			final AvailObject decl =
				DeclarationNodeDescriptor.newModuleVariable(
					globalToken,
					globalVar);
			final AvailObject varUse = VariableUseNodeDescriptor.newUse(
				globalToken,
				decl);
			pushExpression(varUse);
		}

		@Override
		public void L1Ext_doGetType ()
		{
			getInteger();
			pushExpression(null);
		}

		@Override
		public void L1Ext_doPushLabel ()
		{
			AvailObject label;
			if (statements.size() > 0
				&& statements.get(0).isInstanceOfKind(
					LABEL_NODE.mostGeneralType()))
			{
				label = statements.get(0);
			}
			else
			{
				final AvailObject labelToken =
					TokenDescriptor.mutable().create();
				labelToken.tokenType(TokenType.KEYWORD);
				labelToken.string(
					StringDescriptor.from(
						tempGenerator.value("label")));
				labelToken.start(0);
				label = DeclarationNodeDescriptor.newLabel(
					labelToken,
					ContinuationTypeDescriptor.forFunctionType(
						code.functionType()));
				statements.add(0, label);
			}

			final AvailObject useNode = VariableUseNodeDescriptor.newUse(
				label.token(),
				label);
			pushExpression(useNode);
		}

		@Override
		public void L1Ext_doReserved ()
		{
			error("Illegal extended nybblecode: F+"
				+ nybbles.extractNybbleFromTupleAt(pc - 1));
		}

		@Override
		public void L1Ext_doSetLiteral ()
		{
			final AvailObject globalToken = TokenDescriptor.mutable().create();
			globalToken.tokenType(TokenType.KEYWORD);
			globalToken.string(StringDescriptor.from("SomeGlobal"));
			globalToken.start(0);
			final AvailObject globalVar = code.literalAt(getInteger());

			final AvailObject decl =
				DeclarationNodeDescriptor.newModuleVariable(
					globalToken,
					globalVar);

			final AvailObject varUse = VariableUseNodeDescriptor.newUse(
				globalToken,
				decl);
			final AvailObject assignmentNode =
				AssignmentNodeDescriptor.mutable().create();
			assignmentNode.variable(varUse);
			assignmentNode.expression(popExpression());
			if (expressionStack.isEmpty())
			{
				statements.add(assignmentNode);
			}
			else
			{
				// We had better see a marker with a top markerValue, otherwise
				// the code generator didn't do what we expected.  Remove that
				// bogus marker and replace it with the (embedded) assignment
				// node itself.
				final AvailObject duplicateExpression = popExpression();
				assert duplicateExpression.isInstanceOfKind(
					MARKER_NODE.mostGeneralType());
				assert duplicateExpression.markerValue().equalsNull();
				expressionStack.add(assignmentNode);
			}
		}

		@Override
		public void L1Ext_doSuperCall ()
		{
			final AvailObject impSet = code.literalAt(getInteger());
			final AvailObject type = code.literalAt(getInteger());
			final AvailObject atom = impSet.name();
			int nArgs = 0;
			final AvailObject str = atom.name();
			final AvailObject underscore =
				StringDescriptor.underscore().tupleAt(1);
			for (int i = 1, end = str.tupleSize(); i <= end; i++)
			{
				if (str.tupleAt(i).equals(underscore))
				{
					nArgs++;
				}
			}
			final List<AvailObject> types = popExpressions(nArgs);
			final List<AvailObject> callArgs = popExpressions(nArgs);
			for (int i = 0; i < nArgs; i++)
			{
				final AvailObject typeLiteralNode = types.get(i);
				if (typeLiteralNode != null)
				{
					assert typeLiteralNode.isInstanceOfKind(
						LITERAL_NODE.mostGeneralType());
					final AvailObject superCast =
						SuperCastNodeDescriptor.mutable().create();
					superCast.expression(callArgs.get(i));
					superCast.superCastType(typeLiteralNode.token().literal());
					callArgs.set(i, superCast);
				}
			}
			final AvailObject sendNode = SendNodeDescriptor.mutable().create();
			sendNode.arguments(TupleDescriptor.fromCollection(callArgs));
			sendNode.implementationSet(impSet);
			sendNode.returnType(type);
			pushExpression(sendNode);
		}

		@Override
		public void L1Implied_doReturn ()
		{
			assert pc == nybbles.tupleSize() + 1;
			statements.add(popExpression());
			assert expressionStack.size() == 0
			: "There should be nothing on the stack after a return";
		}
	};

	/**
	 * Create any necessary variable declaration nodes.
	 */
	private void buildArgsAndLocals ()
	{
		args = new ArrayList<AvailObject>(code.numArgs());
		locals = new ArrayList<AvailObject>(code.numLocals());
		final AvailObject tupleType = code.functionType().argsTupleType();
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			final String argName = tempGenerator.value("arg");
			final AvailObject token = TokenDescriptor.mutable().create();
			token.tokenType(TokenType.KEYWORD);
			token.string(StringDescriptor.from(argName));
			token.start(0);
			final AvailObject decl = DeclarationNodeDescriptor.newArgument(
				token,
				tupleType.typeAtIndex(i));
			args.add(decl);
		}
		for (int i = 1, end = code.numLocals(); i <= end; i++)
		{
			final String localName = tempGenerator.value("local");
			final AvailObject token = TokenDescriptor.mutable().create();
			token.tokenType(TokenType.KEYWORD);
			token.string(StringDescriptor.from(localName));
			token.start(0);
			final AvailObject decl = DeclarationNodeDescriptor.newVariable(
				token,
				code.localTypeAt(i).writeType());
			locals.add(decl);
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
	public static AvailObject parse (final AvailObject aFunction)
	{
		final Map<String, Integer> counts = new HashMap<String, Integer>();
		final Transformer1<String, String> generator =
			new Transformer1<String, String>()
			{
				@Override
				public String value (final String prefix)
				{
					Integer newCount = counts.get(prefix);
					newCount = newCount == null ? 1 : newCount + 1;
					counts.put(prefix, newCount);
					return prefix + newCount.toString();
				}
			};

		final List<AvailObject> functionOuters =
			new ArrayList<AvailObject>(aFunction.numOuterVars());
		for (int i = 1; i <= aFunction.numOuterVars(); i++)
		{
			final AvailObject varObject = aFunction.outerVarAt(i);

			final AvailObject token = LiteralTokenDescriptor.mutable().create();
			token.tokenType(TokenType.LITERAL);
			token.string(StringDescriptor.from("Outer#" + i));
			token.start(0);
			token.lineNumber(0);
			token.literal(varObject);
			final AvailObject literalNode =
				LiteralNodeDescriptor.fromToken(token);
			functionOuters.add(literalNode);
		}
		return new L1Decompiler().parseWithOuterVarsTempGenerator(
			aFunction.code(),
			functionOuters,
			generator);
	}
}
