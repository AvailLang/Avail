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
import static com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind.LABEL;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.compiler.node.*;
import com.avail.compiler.scanning.*;
import com.avail.compiler.scanning.TokenDescriptor.TokenType;
import com.avail.utility.Transformer1;

public class L1Decompiler implements L1OperationDispatcher
{
	AvailObject _code;
	List<AvailObject> _outers;
	List<AvailObject> _args;
	List<AvailObject> _locals;
	AvailObject _nybbles;
	int _pc;
	Transformer1<String, String> _tempGenerator;
	List<AvailObject> _expressionStack = new ArrayList<AvailObject>();
	List<AvailObject> _statements = new ArrayList<AvailObject>();
	short _primitive;



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
		_primitive = _code.primitiveNumber();
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
		assert _expressionStack.size() == 0 : "There should be nothing on the stack after the final return";

		return BlockNodeDescriptor.newBlockNode(
			TupleDescriptor.fromList(_args),
			_primitive,
			TupleDescriptor.fromList(_statements),
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
		final int tag = _nybbles.extractNybbleFromTupleAt(_pc);
		if (tag < 10)
		{
			_pc++;
			return tag;
		}
		int integer;
		if (tag <= 12)
		{
			integer = tag * 16 - 150 + _nybbles.extractNybbleFromTupleAt(_pc + 1);
			_pc += 2;
			return integer;
		}
		if (tag == 13)
		{
			integer = (_nybbles.extractNybbleFromTupleAt(_pc + 1) << 4)
			+ _nybbles.extractNybbleFromTupleAt(_pc + 2)
			+ 58;
			_pc += 3;
			return integer;
		}
		if (tag == 14)
		{
			integer = 0;
			for (int _count1 = 1; _count1 <= 4; _count1++)
			{
				integer <<= 4;
				integer += _nybbles.extractNybbleFromTupleAt(++_pc);
			}
			//  making 5 nybbles total
			_pc++;
			return integer;
		}
		if (tag == 15)
		{
			integer = 0;
			for (int _count2 = 1; _count2 <= 8; _count2++)
			{
				integer <<= 4;
				integer += _nybbles.extractNybbleFromTupleAt(++_pc);
			}
			//  making 9 nybbles total
			_pc++;
			return integer;
		}
		error("Impossible nybble");
		return 0;
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



	// private-nybblecodes

	@Override
	public void L1Ext_doGetLiteral ()
	{
		//  [n] - Push the value of the variable that's literal number n in the current compiledCode.

		final AvailObject globalToken = TokenDescriptor.mutable().create();
		globalToken.tokenType(TokenType.KEYWORD);
		globalToken.string(
			ByteStringDescriptor.from("SomeGlobal"));
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
		//  [n] - Push the (n+1)st stack element's type.  This is only used by the supercast
		//  mechanism to produce types for arguments not being cast.  See #doSuperCall.
		//
		//  A null value is pushed and explicitly checked for when the call-by-types nybblecode
		//  is encountered later.  It should also make other (invalid) attempts to use this value
		//  stand out like a sore thumb.

		getInteger();
		pushExpression(null);
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		//  Build a continuation which, when restarted, will be just like restarting the current continuation.

		AvailObject label;
		if (_statements.size() > 0 && _statements.get(0).declarationKind() == LABEL)
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
		//  An illegal nybblecode.

		error("Illegal extended nybblecode: F+" + _nybbles.extractNybbleFromTupleAt(_pc - 1));
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		//  [n] - Pop the stack and assign this value to the variable that's the literal
		//  indexed by n in the current compiledCode.

		final AvailObject globalToken = TokenDescriptor.mutable().create();
		globalToken.tokenType(TokenType.KEYWORD);
		globalToken.string(
			ByteStringDescriptor.from("SomeGlobal"));
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
		_statements.add(assignmentNode);
	}

	/**
	 * [n] - Send the message at index n in the compiledCode's literals.  Like
	 * the call instruction, the arguments will have been pushed on the stack in
	 * order, but unlike call, each argument's type will also have been pushed
	 * (all arguments are pushed, then all argument types).  These are either
	 * the arguments' exact types, or constant types (that must be supertypes of
	 * the arguments' types), or any mixture of the two.  These types will be
	 * used for method lookup, rather than the argument types.  This supports a
	 * 'super'-like mechanism in the presence of multi-methods.  Like the call
	 * instruction, all arguments (and types) are popped, then the expected
	 * return type is pushed, and the looked up method is started.  When the
	 * invoked method returns via an implied return instruction, the value will
	 * be checked against this type, and the type's slot on the stack will be
	 * replaced by the actual return value.
	 */
	@Override
	public void L1Ext_doSuperCall ()
	{
		final AvailObject impSet = _code.literalAt(getInteger());
		final AvailObject type = _code.literalAt(getInteger());
		final AvailObject cyclicType = impSet.name();
		int nArgs = 0;
		final AvailObject str = cyclicType.name();
		final AvailObject underscore = TupleDescriptor.underscoreTuple().tupleAt(1);
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
				assert typeLiteralNode.traversed().descriptor()
					instanceof LiteralNodeDescriptor;
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
		//  Return to the calling continuation with top of stack.  Must be the last instruction in block.
		//  Note that the calling continuation has automatically pre-pushed a void object as a
		//  sentinel, which should simply be replaced by this value (to avoid manipulating the stackp).

		assert _pc == _nybbles.tupleSize() + 1;
		_statements.add(popExpression());
		assert _expressionStack.size() == 0 : "There should be nothing on the stack after a return";
	}

	/**
	 * [n] - Send the message at index n in the compiledCode's literals.  Pop
	 * the arguments for this message off the stack (the message itself knows
	 * how many to expect).  The first argument was pushed first, and is the
	 * deepest on the stack.  Use these arguments to look up the method
	 * dynamically.  Before invoking the method, push the expected return type
	 * onto the stack.  Its presence will help distinguish continuations
	 * produced by the pushLabel instruction from their senders.  When the call
	 * completes (if ever), it will use the implied return instruction, which
	 * will have the effect of checking the proposed return value against the
	 * type, then replacing the stack slot containing the type with the actual
	 * result of the call.
	 */
	@Override
	public void L1_doCall ()
	{
		final AvailObject impSet = _code.literalAt(getInteger());
		final AvailObject type = _code.literalAt(getInteger());
		final AvailObject cyclicType = impSet.name();
		int nArgs = 0;
		final AvailObject str = cyclicType.name();
		final AvailObject underscore = TupleDescriptor.underscoreTuple().tupleAt(1);
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
		//  [n,m] - Pop the top n items off the stack, and use them as outer variables in the
		//  construction of a closure based on the compiledCode that's the literal at index m
		//  of the current compiledCode.

		final int nOuters = getInteger();
		final AvailObject theCode = _code.literalAt(getInteger());
		final List<AvailObject> theOuters = popExpressions(nOuters);
		for (final AvailObject outer : theOuters)
		{
			assert
				outer.type() == Types.referenceNode.object()
				|| outer.type() == Types.variableUseNode.object()
				|| outer.type() == Types.literalNode.object();
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
		//  The extension nybblecode was encountered.  Read another nybble and dispatch it through ExtendedSelectors.

		final byte nybble = _nybbles.extractNybbleFromTupleAt(_pc);
		_pc++;
		L1Operation.values()[nybble + 16].dispatch(this);
	}


	@Override
	public void L1_doGetLocal ()
	{
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).

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
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).
		//  If the variable itself is mutable, clear it now - nobody will know.

		final AvailObject localDecl =
			_locals.get(getInteger() - _code.numArgs() - 1);
		final AvailObject useNode = VariableUseNodeDescriptor.newUse(
			localDecl.token(),
			localDecl);
		useNode.isLastUse(true);
		pushExpression(useNode);
	}


	@Override
	public void L1_doGetOuter ()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current closure.

		final AvailObject use;
		final AvailObject outer = _outers.get(getInteger() - 1);
		if (outer.type().equals(Types.literalNode.object()))
		{
			final AvailObject synthToken = TokenDescriptor.mutable().create();
			synthToken.tokenType(TokenType.KEYWORD);
			synthToken.string(outer.name().string());
			synthToken.start(0);

			final AvailObject synthDecl =
				DeclarationNodeDescriptor.newModuleVariable(
					synthToken,
					outer.token().literal());
			use = VariableUseNodeDescriptor.newUse(synthToken, synthDecl);
		}
		else
		{
			final AvailObject outerDecl = outer.variable().declaration();
			use = VariableUseNodeDescriptor.newUse(
				outerDecl.token(),
				outerDecl);
		}
		pushExpression(use);
	}


	@Override
	public void L1_doGetOuterClearing ()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current closure.
		//  If the variable itself is mutable, clear it at this time - nobody will know.

		final AvailObject use;
		final AvailObject outer = _outers.get(getInteger() - 1);
		if (outer.type().equals(Types.literalNode.object()))
		{
			final AvailObject synthToken = TokenDescriptor.mutable().create();
			synthToken.tokenType(TokenType.KEYWORD);
			synthToken.string(outer.name().string());
			synthToken.start(0);

			final AvailObject synthDecl =
				DeclarationNodeDescriptor.newModuleVariable(
					synthToken,
					outer.token().literal());
			use = VariableUseNodeDescriptor.newUse(synthToken, synthDecl);
		}
		else
		{
			final AvailObject outerDecl = outer.variable().declaration();
			use = VariableUseNodeDescriptor.newUse(
				outerDecl.token(),
				outerDecl);
		}
		pushExpression(use);
	}


	@Override
	public void L1_doMakeTuple ()
	{
		final int count = getInteger();
		final List<AvailObject> expressions = popExpressions(count);
		final AvailObject tupleNode = TupleNodeDescriptor.mutable().create();
		tupleNode.expressionsTuple(TupleDescriptor.fromList(expressions));
		tupleNode.tupleType(VoidDescriptor.voidObject());
		pushExpression(tupleNode);
	}


	@Override
	public void L1_doPop ()
	{
		//  Remove the top item from the stack.

		_statements.add(popExpression());
	}


	@Override
	public void L1_doPushLastLocal ()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.
		//  Since this is known to be the last use (nondebugger) of the argument or local, void that
		//  slot of the current continuation (and the variable's value if appropriate).

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
			final AvailObject ref = ReferenceNodeDescriptor.mutable().create();
			ref.variable(use);
			pushExpression(ref);
		}
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		//  [n] - Push the outer variable indexed by n in the current closure.  If the variable is
		//  mutable, clear it (no one will know).  If the variable and closure are both mutable,
		//  remove the variable from the closure by voiding it.

		pushExpression(_outers.get(getInteger() - 1));
	}

	@Override
	public void L1_doPushLiteral ()
	{
		//  [n] - Push the literal indexed by n in the current compiledCode.

		final AvailObject value = _code.literalAt(getInteger());
		if (value.isInstanceOfSubtypeOf(Types.closure.object()))
		{
			List<AvailObject> closureOuterLiterals;
			closureOuterLiterals = new ArrayList<AvailObject>(
				value.numOuterVars());
			for (int i = 1; i <= value.numOuterVars(); i++)
			{
				final AvailObject var = value.outerVarAt(i);
				final AvailObject token =
					LiteralTokenDescriptor.mutable().create();
				token.tokenType(TokenType.LITERAL);
				token.string(
					ByteStringDescriptor.from(
						"AnOuter" + Integer.toString(i)));
				token.start(0);
				token.literal(var);
				final AvailObject outerLiteral =
					LiteralNodeDescriptor.mutable().create();
				outerLiteral.token(token);
				closureOuterLiterals.add(outerLiteral);
			}
			final AvailObject blockNode =
				new L1Decompiler().parseWithOuterVarsTempGenerator(
					value.code(),
					closureOuterLiterals,
					_tempGenerator);
			pushExpression(blockNode);
		}
		else
		{
			final AvailObject token = LiteralTokenDescriptor.mutable().create();
			token.tokenType(TokenType.LITERAL);
			token.string(
				ByteStringDescriptor.from(
					value.toString()));
			token.start(0);
			token.literal(value);
			final AvailObject literalNode =
				LiteralNodeDescriptor.mutable().create();
			literalNode.token(token);
			pushExpression(literalNode);
		}
	}

	@Override
	public void L1_doPushLocal ()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.

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
			final AvailObject ref = ReferenceNodeDescriptor.mutable().create();
			ref.variable(use);
			pushExpression(ref);
		}
	}

	@Override
	public void L1_doPushOuter ()
	{
		//  [n] - Push the outer variable indexed by n in the current closure.

		pushExpression(_outers.get(getInteger() - 1));
	}

	@Override
	public void L1_doSetLocal ()
	{
		//  [n] - Pop the stack and assign this value to the local variable (not an argument) indexed by n (index 1 is first argument).

		final AvailObject localDecl = _locals.get(
			getInteger() - _code.numArgs() - 1);
		final AvailObject valueNode = popExpression();
		final AvailObject variableUse = VariableUseNodeDescriptor.newUse(
			localDecl.token(),
			valueNode);
		final AvailObject assignment =
			AssignmentNodeDescriptor.mutable().create();
		assignment.variable(variableUse);
		assignment.expression(valueNode);
		_statements.add(assignment);
	}

	@Override
	public void L1_doSetOuter ()
	{
		// [n] - Pop the stack and assign this value to the outer variable
		// indexed by n in the current closure.
		final AvailObject variableUse;
		final AvailObject outerExpr = _outers.get(getInteger() - 1);
		if (outerExpr.isInstanceOfSubtypeOf(Types.literalNode.object()))
		{
			final AvailObject outerLiteral = outerExpr;
			final AvailObject synthToken = TokenDescriptor.mutable().create();
			synthToken.tokenType(TokenType.KEYWORD);
			synthToken.string(outerLiteral.token().string());
			synthToken.start(0);
			final AvailObject moduleVarDecl =
				DeclarationNodeDescriptor.newModuleVariable(
					synthToken,
					outerLiteral.token().literal());
			variableUse = VariableUseNodeDescriptor.newUse(
				synthToken,
				moduleVarDecl);
		}
		else
		{
			final AvailObject referenceNode = outerExpr;
			final AvailObject outerDecl =
				referenceNode.variable().declaration();
			variableUse = VariableUseNodeDescriptor.newUse(
				outerDecl.token(),
				outerDecl);
		}
		final AvailObject valueExpr = popExpression();
		final AvailObject assignment =
			AssignmentNodeDescriptor.mutable().create();
		assignment.variable(variableUse);
		assignment.expression(valueExpr);
		_statements.add(assignment);
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
			token.string(ByteStringDescriptor.from("AnOuter" + i));
			token.start(0);
			token.literal(varObject);
			final AvailObject literalNode =
				LiteralNodeDescriptor.mutable().create();
			literalNode.token(token);
			closureOuters.add(literalNode);
		}
		return new L1Decompiler().parseWithOuterVarsTempGenerator(
			aClosure.code(),
			closureOuters,
			generator);
	}

}
