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

import static com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind.LABEL;
import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.compiler.node.*;
import com.avail.compiler.scanning.*;
import com.avail.compiler.scanning.TokenDescriptor.TokenType;
import com.avail.descriptor.*;
import com.avail.utility.Transformer1;

/**
 * The {@link L1Decompiler} converts a {@link CompiledCodeDescriptor compiled
 * code} object into an equivalent {@link ParseNodeDescriptor parse tree}.
 *
 * @author Mark van Gulik &lt;anarakul@gmail.com&gt;
 */
public class L1Decompiler implements L1OperationDispatcher
{
	/**
	 * The {@link CompiledCodeDescriptor compiled code} which is being
	 * decompiled.
	 */
	AvailObject _code;

	/**
	 * {@link ParseNodeDescriptor Parse nodes} which correspond with the
	 * lexically captured variables.  These can be {@link
	 * DeclarationNodeDescriptor declaration nodes} or {@link
	 * LiteralNodeDescriptor literal nodes}, but the latter may be phased out
	 * in favor of module constants and module variables.
	 */
	List<AvailObject> _outers;

	/**
	 * The {@link DeclarationNodeDescriptor.DeclarationKind#ARGUMENT arguments
	 * declarations} for this code.
	 */
	List<AvailObject> _args;

	/**
	 * The {@link DeclarationNodeDescriptor.DeclarationKind#LOCAL_VARIABLE}
	 * local variables} defined by this code.
	 */
	List<AvailObject> _locals;

	/**
	 * The tuple of nybblecodes to decode.
	 */
	AvailObject _nybbles;

	/**
	 * The current position in the instruction stream at which decompilation is
	 * currently occurring.
	 */
	int _pc;

	/**
	 * Something to generate unique variable names from a prefix.
	 */
	Transformer1<String, String> _tempGenerator;

	/**
	 * The stack of expressions roughly corresponding to the subexpressions that
	 * have been parsed but not yet integrated into their parent expressions.
	 */
	List<AvailObject> _expressionStack = new ArrayList<AvailObject>();

	/**
	 * The list of completely decompiled {@link ParseNodeDescriptor statements}.
	 */
	List<AvailObject> _statements = new ArrayList<AvailObject>();



	/**
	 * Parse the given compiled code object.  Its outer vars map to the given
	 * Array of expressions, and temp names are allocated via tempBlock.  The
	 * tempBlock takes a prefix string which can thereby distinguish arguments,
	 * locals, and labels.  Answer the resulting AvailBlockNode.
	 *
	 * @param aCodeObject
	 *        The {@link CompiledCodeDescriptor code} to decompile.
	 * @param outerVars
	 *        The list of outer variable declarations and literal nodes.
	 * @param tempBlock
	 *        A {@link Transformer1 transformer} that takes a prefix and
	 *        generates a suitably unique temporary variable name.
	 * @return The {@link BlockNodeDescriptor block node} that is the
	 *         decompilation of the code.
	 */
	public AvailObject parseWithOuterVarsTempGenerator (
		final AvailObject aCodeObject,
		final List<AvailObject> outerVars,
		final Transformer1<String, String> tempBlock)
	{

		_code = aCodeObject;
		_outers = outerVars;
		_tempGenerator = tempBlock;
		buildArgsAndLocals();

		_statements.addAll(_locals);
		//  Add all local declaration statements at the start.
		_nybbles = _code.nybbles();
		_pc = 1;
		while (_pc <= _nybbles.tupleSize())
		{
			final byte nybble = _nybbles.extractNybbleFromTupleAt(_pc);
			_pc++;
			L1Operation.values()[nybble].dispatch(this);
		}
		L1Implied_doReturn();
		assert _expressionStack.size() == 0
		: "There should be nothing on the stack after the final return";

		return BlockNodeDescriptor.newBlockNode(
			_args,
			_code.primitiveNumber(),
			_statements,
			aCodeObject.closureType().returnType());
	}



	/**
	 * Extract an encoded integer from the nybblecode instruction stream.  The
	 * encoding uses only a nybble for very small operands, and can still
	 * represent up to {@link Integer#MAX_VALUE} if necessary.
	 * <p>
	 * Adjust the {@link #_pc program counter} to skip the integer.
	 *
	 * @return The integer extracted from the nybblecode stream.
	 */
	public int getInteger ()
	{
		final int nyb = _nybbles.extractNybbleFromTupleAt(_pc);
		_pc++;
		int value = 0;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[nyb]; count > 0; --count)
		{
			value = (value << 4) + _nybbles.extractNybbleFromTupleAt(_pc);
			_pc++;
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[nyb];
		return value;
	}

	/**
	 * Pop one {@link ParseNodeDescriptor parse node} off the expression stack
	 * and return it.
	 *
	 * @return The parse node popped off the stack.
	 */
	AvailObject popExpression ()
	{
		return _expressionStack.remove(_expressionStack.size() - 1);
	}

	/**
	 * Pop some {@link ParseNodeDescriptor parse nodes} off the expression stack
	 * and return them in a {@link List list}.
	 *
	 * @param count The number of parse nodes to pop.
	 * @return The list of {@code #count} parse nodes, in the order they were
	 *         added to the stack.
	 */
	List<AvailObject> popExpressions (
		final int count)
		{
		final List<AvailObject> result = new ArrayList<AvailObject>(count);
		for (int i = 1; i <= count; i++)
		{
			result.add(0, popExpression());
		}
		return result;
		}

	/**
	 * Push the given {@link ParseNodeDescriptor parse node} onto the expression
	 * stack.
	 *
	 * @param expression The expression to push.
	 */
	void pushExpression (
		final AvailObject expression)
	{
		_expressionStack.add(expression);
	}

	@Override
	public void L1_doCall ()
	{
		final AvailObject impSet = _code.literalAt(getInteger());
		final AvailObject type = _code.literalAt(getInteger());
		final AvailObject cyclicType = impSet.name();
		int nArgs = 0;
		final AvailObject str = cyclicType.name();
		final AvailObject underscore =
			TupleDescriptor.underscoreTuple().tupleAt(1);
		for (int i = 1, _end1 = str.tupleSize(); i <= _end1; i++)
		{
			if (str.tupleAt(i).equals(underscore))
			{
				nArgs++;
			}
		}
		final List<AvailObject> callArgs = popExpressions(nArgs);
		final AvailObject sendNode = SendNodeDescriptor.mutable().create();
		sendNode.arguments(TupleDescriptor.fromList(callArgs));
		sendNode.implementationSet(impSet);
		sendNode.returnType(type);

		pushExpression(sendNode);
	}


	@Override
	public void L1_doClose ()
	{
		final int nOuters = getInteger();
		final AvailObject theCode = _code.literalAt(getInteger());
		final List<AvailObject> theOuters = popExpressions(nOuters);
		for (final AvailObject outer : theOuters)
		{
			assert
				outer.type() == REFERENCE_NODE.o()
				|| outer.type() == VARIABLE_USE_NODE.o()
				|| outer.type() == LITERAL_NODE.o();
		}
		final AvailObject blockNode =
			new L1Decompiler().parseWithOuterVarsTempGenerator(
				theCode,
				theOuters,
				_tempGenerator);
		pushExpression(blockNode);
	}


	@Override
	public void L1_doExtension ()
	{
		final byte nybble = _nybbles.extractNybbleFromTupleAt(_pc);
		_pc++;
		L1Operation.values()[nybble + 16].dispatch(this);
	}


	@Override
	public void L1_doGetLocal ()
	{
		final AvailObject localDecl =
			_locals.get(getInteger() - _code.numArgs() - 1);
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
		final AvailObject outer = _outers.get(outerIndex - 1);
		if (outer.isInstanceOfSubtypeOf(LITERAL_NODE.o()))
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
			TupleDescriptor.fromList(expressions));
		pushExpression(tupleNode);
	}


	@Override
	public void L1_doPop ()
	{
		_statements.add(popExpression());
		assert _expressionStack.isEmpty();
	}


	@Override
	public void L1_doPushLastLocal ()
	{
		final int index = getInteger();
		final boolean isArg = index <= _code.numArgs();
		final AvailObject decl = isArg
			? _args.get(index - 1)
			: _locals.get(index - _code.numArgs() - 1);
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
		final AvailObject value = _code.literalAt(getInteger());
		if (value.isInstanceOfSubtypeOf(CLOSURE.o()))
		{
			final List<AvailObject> closureOuters =
				new ArrayList<AvailObject>(value.numOuterVars());
			// Due to stub-building primitives, it's possible for a non-clean
			// closure to be a literal, so deal with it here.
			for (int i = 1; i <= value.numOuterVars(); i++)
			{
				final AvailObject varObject = value.outerVarAt(i);
				final AvailObject token =
					LiteralTokenDescriptor.mutable().create();
				token.tokenType(TokenType.LITERAL);
				token.string(ByteStringDescriptor.from(
					"OuterOfUncleanConstantClosure#" + i));
				token.start(0);
				token.lineNumber(0);
				token.literal(varObject);
				final AvailObject literalNode =
					LiteralNodeDescriptor.fromToken(token);
				closureOuters.add(literalNode);
			}
			final AvailObject blockNode =
				new L1Decompiler().parseWithOuterVarsTempGenerator(
					value.code(),
					closureOuters,
					_tempGenerator);
			pushExpression(blockNode);
		}
		else
		{
			final AvailObject token = LiteralTokenDescriptor.mutable().create();
			token.tokenType(TokenType.LITERAL);
			token.string(ByteStringDescriptor.from(value.toString()));
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
		final boolean isArg = index <= _code.numArgs();
		final AvailObject decl = isArg
			? _args.get(index - 1)
			: _locals.get(index - _code.numArgs() - 1);
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
		pushExpression(_outers.get(getInteger() - 1));
	}

	@Override
	public void L1_doSetLocal ()
	{
		final AvailObject localDecl = _locals.get(
			getInteger() - _code.numArgs() - 1);
		final AvailObject valueNode = popExpression();
		final AvailObject variableUse = VariableUseNodeDescriptor.newUse(
			localDecl.token(),
			localDecl);
		final AvailObject assignmentNode =
			AssignmentNodeDescriptor.mutable().create();
		assignmentNode.variable(variableUse);
		assignmentNode.expression(valueNode);
		if (_expressionStack.isEmpty())
		{
			_statements.add(assignmentNode);
		}
		else
		{
			// We had better see a marker with a void markerValue, otherwise the
			// code generator didn't do what we expected.  Remove that bogus
			// marker and replace it with the (embedded) assignment node itself.
			final AvailObject duplicateExpression = popExpression();
			assert duplicateExpression.isInstanceOfSubtypeOf(MARKER_NODE.o());
			assert duplicateExpression.markerValue().equalsVoid();
			_expressionStack.add(assignmentNode);
		}
	}

	@Override
	public void L1_doSetOuter ()
	{
		final AvailObject variableUse;
		final AvailObject outerExpr = _outers.get(getInteger() - 1);
		final AvailObject outerDecl;
		if (outerExpr.isInstanceOfSubtypeOf(LITERAL_NODE.o()))
		{
			// Writing into a synthetic literal (a byproduct of decompiling a
			// block without decompiling its outer scopes).
			AvailObject token = outerExpr.token();
			AvailObject variableObject = token.literal();
			assert variableObject.isInstanceOfSubtypeOf(CONTAINER.o());
			outerDecl = DeclarationNodeDescriptor.newModuleVariable(
				token,
				variableObject);
		}
		else
		{
			assert outerExpr.isInstanceOfSubtypeOf(REFERENCE_NODE.o());
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
		if (_expressionStack.isEmpty())
		{
			_statements.add(assignmentNode);
		}
		else
		{
			// We had better see a marker with a void markerValue, otherwise the
			// code generator didn't do what we expected.  Remove that bogus
			// marker and replace it with the (embedded) assignment node itself.
			final AvailObject duplicateExpression = popExpression();
			assert duplicateExpression.isInstanceOfSubtypeOf(MARKER_NODE.o());
			assert duplicateExpression.markerValue().equalsVoid();
			_expressionStack.add(assignmentNode);
		}
	}



	/**
	 * The presence of the {@linkplain L1Operation operation} indicates that
	 * an assignment is being used as a subexpression or final statement from a
	 * non-void valued {@linkplain ClosureDescriptor block}.
	 *
	 * <p>Pop the expression (that represents the right hand side of the
	 * assignment), push a special {@linkplain MarkerNodeDescriptor marker}
	 * whose markerValue is the {@linkplain VoidDescriptor#voidObject() void
	 * object}, then push the right-hand side expression back onto the
	 * expression stack.</p>
	 */
	@Override
	public void L1Ext_doDuplicate ()
	{
		final AvailObject rightSide = popExpression();
		final AvailObject marker = MarkerNodeDescriptor.mutable().create();
		marker.markerValue(VoidDescriptor.voidObject());
		pushExpression(marker);
		pushExpression(rightSide);
	}



	@Override
	public void L1Ext_doGetLiteral ()
	{
		final AvailObject globalToken = TokenDescriptor.mutable().create();
		globalToken.tokenType(TokenType.KEYWORD);
		globalToken.string(ByteStringDescriptor.from("SomeGlobal"));
		globalToken.start(0);
		final AvailObject globalVar = _code.literalAt(getInteger());

		final AvailObject decl = DeclarationNodeDescriptor.newModuleVariable(
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
		if (_statements.size() > 0
			&& _statements.get(0).isInstanceOfSubtypeOf(DECLARATION_NODE.o())
			&& _statements.get(0).declarationKind() == LABEL)
		{
			label = _statements.get(0);
		}
		else
		{
			final AvailObject labelToken = TokenDescriptor.mutable().create();
			labelToken.tokenType(TokenType.KEYWORD);
			labelToken.string(
				ByteStringDescriptor.from(
					_tempGenerator.value("label")));
			labelToken.start(0);

			label = DeclarationNodeDescriptor.newLabel(
				labelToken,
				ContinuationTypeDescriptor.forClosureType(_code.closureType()));

			_statements.add(0, label);
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
			+ _nybbles.extractNybbleFromTupleAt(_pc - 1));
	}



	@Override
	public void L1Ext_doSetLiteral ()
	{
		final AvailObject globalToken = TokenDescriptor.mutable().create();
		globalToken.tokenType(TokenType.KEYWORD);
		globalToken.string(ByteStringDescriptor.from("SomeGlobal"));
		globalToken.start(0);
		final AvailObject globalVar = _code.literalAt(getInteger());

		final AvailObject decl = DeclarationNodeDescriptor.newModuleVariable(
			globalToken,
			globalVar);

		final AvailObject varUse = VariableUseNodeDescriptor.newUse(
			globalToken,
			decl);
		final AvailObject assignmentNode =
			AssignmentNodeDescriptor.mutable().create();
		assignmentNode.variable(varUse);
		assignmentNode.expression(popExpression());
		if (_expressionStack.isEmpty())
		{
			_statements.add(assignmentNode);
		}
		else
		{
			// We had better see a marker with a void markerValue, otherwise the
			// code generator didn't do what we expected.  Remove that bogus
			// marker and replace it with the (embedded) assignment node itself.
			final AvailObject duplicateExpression = popExpression();
			assert duplicateExpression.isInstanceOfSubtypeOf(MARKER_NODE.o());
			assert duplicateExpression.markerValue().equalsVoid();
			_expressionStack.add(assignmentNode);
		}
	}



	@Override
	public void L1Ext_doSuperCall ()
	{
		final AvailObject impSet = _code.literalAt(getInteger());
		final AvailObject type = _code.literalAt(getInteger());
		final AvailObject cyclicType = impSet.name();
		int nArgs = 0;
		final AvailObject str = cyclicType.name();
		final AvailObject underscore =
			TupleDescriptor.underscoreTuple().tupleAt(1);
		for (int i = 1, _end1 = str.tupleSize(); i <= _end1; i++)
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
				assert typeLiteralNode.isInstanceOfSubtypeOf(LITERAL_NODE.o());
				final AvailObject superCast =
					SuperCastNodeDescriptor.mutable().create();
				superCast.expression(callArgs.get(i));
				superCast.superCastType(typeLiteralNode.token().literal());
				callArgs.set(i, superCast);
			}
		}
		final AvailObject sendNode = SendNodeDescriptor.mutable().create();
		sendNode.arguments(TupleDescriptor.fromList(callArgs));
		sendNode.implementationSet(impSet);
		sendNode.returnType(type);
		pushExpression(sendNode);
	}



	@Override
	public void L1Implied_doReturn ()
	{
		assert _pc == _nybbles.tupleSize() + 1;
		_statements.add(popExpression());
		assert _expressionStack.size() == 0
		: "There should be nothing on the stack after a return";
	}



	/**
	 * Create any necessary variable declaration nodes.
	 */
	void buildArgsAndLocals ()
	{
		_args = new ArrayList<AvailObject>(_code.numArgs());
		_locals = new ArrayList<AvailObject>(_code.numLocals());
		for (int i = 1, _end1 = _code.numArgs(); i <= _end1; i++)
		{
			final String argName = _tempGenerator.value("arg");
			final AvailObject token = TokenDescriptor.mutable().create();
			token.tokenType(TokenType.KEYWORD);
			token.string(ByteStringDescriptor.from(argName));
			token.start(0);
			final AvailObject decl = DeclarationNodeDescriptor.newArgument(
				token,
				_code.closureType().argTypeAt(i));
			_args.add(decl);
		}
		for (int i = 1, _end2 = _code.numLocals(); i <= _end2; i++)
		{
			final String localName = _tempGenerator.value("local");
			final AvailObject token = TokenDescriptor.mutable().create();
			token.tokenType(TokenType.KEYWORD);
			token.string(ByteStringDescriptor.from(localName));
			token.start(0);
			final AvailObject decl = DeclarationNodeDescriptor.newVariable(
				token,
				_code.localTypeAt(i).innerType());
			_locals.add(decl);
		}
	}



	/**
	 * Parse the given statically constructed closure.  It treats outer
	 * variables as literals.  Answer the resulting {@link BlockNodeDescriptor
	 * block}.
	 *
	 * @param aClosure The closure to decompile.
	 * @return The {@link BlockNodeDescriptor block} that is the decompilation
	 *         of the provided closure.
	 */
	public static AvailObject parse (final AvailObject aClosure)
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

		final List<AvailObject> closureOuters =
			new ArrayList<AvailObject>(aClosure.numOuterVars());
		for (int i = 1; i <= aClosure.numOuterVars(); i++)
		{
			final AvailObject varObject = aClosure.outerVarAt(i);

			final AvailObject token = LiteralTokenDescriptor.mutable().create();
			token.tokenType(TokenType.LITERAL);
			token.string(ByteStringDescriptor.from("Outer#" + i));
			token.start(0);
			token.lineNumber(0);
			token.literal(varObject);
			final AvailObject literalNode =
				LiteralNodeDescriptor.fromToken(token);
			closureOuters.add(literalNode);
		}
		return new L1Decompiler().parseWithOuterVarsTempGenerator(
			aClosure.code(),
			closureOuters,
			generator);
	}
}
