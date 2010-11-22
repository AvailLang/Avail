/**
 * interpreter/levelOne/AvailDecompiler.java
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

import com.avail.compiler.AvailAssignmentNode;
import com.avail.compiler.AvailBlockNode;
import com.avail.compiler.AvailLabelNode;
import com.avail.compiler.AvailListNode;
import com.avail.compiler.AvailLiteralNode;
import com.avail.compiler.AvailParseNode;
import com.avail.compiler.AvailReferenceNode;
import com.avail.compiler.AvailSendNode;
import com.avail.compiler.AvailSuperCastNode;
import com.avail.compiler.AvailVariableDeclarationNode;
import com.avail.compiler.AvailVariableSyntheticDeclarationNode;
import com.avail.compiler.AvailVariableUseNode;
import com.avail.compiler.Transformer1;
import com.avail.compiler.scanner.AvailKeywordToken;
import com.avail.compiler.scanner.AvailLiteralToken;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContinuationTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.AvailDecompiler;
import java.lang.Integer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.avail.descriptor.AvailObject.*;

public class AvailDecompiler implements L1OperationDispatcher
{
	AvailObject _code;
	List<? extends AvailParseNode> _outers;
	List<AvailVariableDeclarationNode> _args;
	List<AvailVariableDeclarationNode> _locals;
	AvailObject _nybbles;
	int _pc;
	Transformer1<String, String> _tempGenerator;
	List<AvailParseNode> _expressionStack = new ArrayList<AvailParseNode>();
	List<AvailParseNode> _statements = new ArrayList<AvailParseNode>();
	int _primitive;


	// parsing

	public AvailBlockNode parseWithOuterVarsTempGenerator (
			final AvailObject aCodeObject,
			final List<? extends AvailParseNode> outerVars,
			final Transformer1<String, String> tempBlock)
	{
		//  Parse the given compiled code object.  Its outer vars map to the given Array of
		//  expressions, and temp names are allocated via tempBlock.  The tempBlock takes
		//  a prefix string which can thereby distinguish arguments, locals, and labels.  Answer
		//  the resulting AvailBlockNode.

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
			byte nybble = _nybbles.extractNybbleFromTupleAt(_pc);
			_pc++;
			L1Operation.values()[nybble].dispatch(this);
		}
		assert (_expressionStack.size() == 0) : "There should be nothing on the stack after the final return";
		final AvailBlockNode blockNode = new AvailBlockNode();
		blockNode.arguments(_args);
		blockNode.primitive(_primitive);
		blockNode.statements(_statements);
		blockNode.resultType(aCodeObject.closureType().returnType());
		//  regenerated := blockNode generateOn: AvailCodeGenerator new.
		//
		//  [regenerated = aCodeObject] assert: 'The decompiled code doesn''t compile back to the original'.
		return blockNode;
	}



	// private-helper

	public int getInteger ()
	{
		//  Answer an integer extracted at the current program counter.  The program
		//  counter will be adjusted to skip over the integer.

		final int tag = _nybbles.extractNybbleFromTupleAt(_pc);
		if (tag < 10)
		{
			_pc++;
			return tag;
		}
		int integer;
		if (tag <= 12)
		{
			integer = (((tag * 16) - 150) + _nybbles.extractNybbleFromTupleAt(_pc + 1));
			_pc += 2;
			return integer;
		}
		if (tag == 13)
		{
			integer = (((_nybbles.extractNybbleFromTupleAt(_pc + 1) << 4) + _nybbles.extractNybbleFromTupleAt(_pc + 2)) + 58);
			_pc += 3;
			return integer;
		}
		if (tag == 14)
		{
			integer = 0;
			for (int _count1 = 1; _count1 <= 4; _count1++)
			{
				integer = ((integer << 4) + _nybbles.extractNybbleFromTupleAt(++_pc));
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
				integer = ((integer << 4) + _nybbles.extractNybbleFromTupleAt(++_pc));
			}
			//  making 9 nybbles total
			_pc++;
			return integer;
		}
		error("Impossible nybble");
		return 0;
	}

	AvailParseNode popExpression ()
	{
		return _expressionStack.remove(_expressionStack.size() - 1);
	}

	List<AvailParseNode> popExpressions (
			final int count)
	{
		List<AvailParseNode> result = new ArrayList<AvailParseNode>(count);
		for (int i = 1; i <= count; i++)
		{
			result.add(0, popExpression());
		}
		return result;
	}

	void pushExpression (
			final AvailParseNode expression)
	{
		_expressionStack.add(expression);
	}



	// private-nybblecodes

	@Override
	public void L1Ext_doGetLiteral ()
	{
		//  [n] - Push the value of the variable that's literal number n in the current compiledCode.

		final AvailKeywordToken globalToken = new AvailKeywordToken();
		globalToken.string("SomeGlobal");
		final AvailObject globalVar = _code.literalAt(getInteger());
		final AvailVariableSyntheticDeclarationNode decl = new AvailVariableSyntheticDeclarationNode();
		decl.name(globalToken);
		decl.declaredType(globalVar.type().innerType());
		decl.isArgument(false);
		decl.availVariable(globalVar);
		final AvailVariableUseNode varUse = new AvailVariableUseNode();
		varUse.name(globalToken);
		varUse.associatedDeclaration(decl);
		varUse.isLastUse(false);
		pushExpression(varUse);
	}

	@Override
	public void L1Ext_doGetOuter ()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current closure.

		final AvailVariableUseNode use = new AvailVariableUseNode();
		final AvailParseNode outer = _outers.get((getInteger() - 1));
		if (outer.isLiteralNode())
		{
			final AvailLiteralNode outerLiteral = ((AvailLiteralNode)(outer));
			final AvailKeywordToken synthToken = new AvailKeywordToken();
			synthToken.string(outerLiteral.token().string());
			final AvailVariableSyntheticDeclarationNode synthDecl = new AvailVariableSyntheticDeclarationNode();
			synthDecl.name(synthToken);
			synthDecl.declaredType(outerLiteral.availValue().type().innerType());
			synthDecl.isArgument(false);
			synthDecl.availVariable(outerLiteral.availValue());
			use.associatedDeclaration(synthDecl);
			use.name(synthToken);
		}
		else
		{

			final AvailVariableDeclarationNode outerDecl = ((AvailReferenceNode)(outer)).variable().associatedDeclaration();
			use.associatedDeclaration(outerDecl);
			use.name(outerDecl.name());
		}
		use.isLastUse(false);
		pushExpression(use);
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
	public void L1Ext_doMakeList ()
	{
		//  [n] - Make a list object from n values popped from the stack.  Push the list.

		final int count = getInteger();
		final AvailListNode listNode = new AvailListNode();
		listNode.expressions(popExpressions(count));
		pushExpression(listNode);
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		//  Build a continuation which, when restarted, will be just like restarting the current continuation.

		AvailLabelNode label;
		if (((_statements.size() > 0) && _statements.get(0).isLabel()))
		{
			label = ((AvailLabelNode)(_statements.get(0)));
		}
		else
		{
			final AvailKeywordToken labelToken = new AvailKeywordToken();
			labelToken.string(_tempGenerator.value("label"));
			label = new AvailLabelNode();
			label.name(labelToken);
			label.declaredType(ContinuationTypeDescriptor.continuationTypeForClosureType(_code.closureType()));
			_statements.add(0, label);
		}
		final AvailVariableUseNode useNode = new AvailVariableUseNode();
		useNode.name(label.name());
		useNode.associatedDeclaration(label);
		useNode.isLastUse(false);
		pushExpression(useNode);
	}

	@Override
	public void L1Ext_doReserved ()
	{
		//  An illegal nybblecode.

		error("Illegal extended nybblecode: F+" + (_nybbles.extractNybbleFromTupleAt(_pc - 1)));
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		//  [n] - Pop the stack and assign this value to the variable that's the literal
		//  indexed by n in the current compiledCode.

		final AvailKeywordToken globalToken = new AvailKeywordToken();
		globalToken.string("SomeGlobal");
		final AvailObject globalVar = _code.literalAt(getInteger());
		final AvailVariableSyntheticDeclarationNode decl = new AvailVariableSyntheticDeclarationNode();
		decl.name(globalToken);
		decl.declaredType(globalVar.type().innerType());
		decl.isArgument(false);
		decl.availVariable(globalVar);
		final AvailVariableUseNode varUse = new AvailVariableUseNode();
		varUse.name(globalToken);
		varUse.associatedDeclaration(decl);
		varUse.isLastUse(false);
		final AvailAssignmentNode assignmentNode = new AvailAssignmentNode();
		assignmentNode.variable(varUse);
		assignmentNode.expression(popExpression());
		_statements.add(assignmentNode);
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		//  [n] - Send the message at index n in the compiledCode's literals.  Like the call instruction,
		//  the arguments will have been pushed on the stack in order, but unlike call, each argument's
		//  type will also have been pushed (all arguments are pushed, then all argument types).
		//  These are either the arguments' exact types, or constant types (that must be supertypes
		//  of the arguments' types), or any mixture of the two.  These types will be used for method
		//  lookup, rather than the argument types.  This supports a 'super'-like mechanism in the
		//  presence of multimethods.  Like the call instruction, all arguments (and types) are popped,
		//  then a sentinel void object is pushed, and the looked up method is started.  When the
		//  invoked method returns (via a return instruction), this sentinel will be replaced by the
		//  result of the call.

		final AvailObject impSet = _code.literalAt(getInteger());
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
		final List<AvailParseNode> types = popExpressions(nArgs);
		final List<AvailParseNode> callArgs = popExpressions(nArgs);
		for (int i = 1; i <= nArgs; i++)
		{
			if (types.get(i - 1) != null)
			{
				final AvailSuperCastNode superCast = new AvailSuperCastNode();
				superCast.expression(callArgs.get(i - 1));
				superCast.type(((AvailLiteralNode)(types.get(i - 1))).availValue());
				callArgs.set(i - 1, superCast);
			}
		}
		final AvailSendNode sendNode = new AvailSendNode();
		sendNode.message(cyclicType);
		sendNode.bundle(null);
		sendNode.arguments(callArgs);
		sendNode.returnType(Types.voidType.object());
		pushExpression(sendNode);
	}

	@Override
	public void L1_doCall ()
	{
		//  [n] - Send the message at index n in the compiledCode's literals.  Pop the arguments for
		//  this message off the stack (the message itself knows how many to expect).  The first
		//  argument was pushed first, and is the deepest on the stack.  Use these arguments to
		//  look up the method dynamically.  Before invoking the method, push the void object
		//  onto the stack.  Its presence will help distinguish continuations produced by the
		//  pushLabel instruction from their senders.  When the call completes (if ever), it will use
		//  the return instruction, which will have the effect of replacing this void object with the
		//  result of the call.

		final AvailObject impSet = _code.literalAt(getInteger());
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
		final List<AvailParseNode> callArgs = popExpressions(nArgs);
		final AvailSendNode sendNode = new AvailSendNode();
		sendNode.message(cyclicType);
		sendNode.bundle(null);
		sendNode.arguments(callArgs);
		sendNode.returnType(Types.voidType.object());
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
		final List<AvailParseNode> theOuters = popExpressions(nOuters);
		for (AvailParseNode outer : theOuters)
		{
			assert
				outer instanceof AvailReferenceNode ||
				outer instanceof AvailVariableUseNode ||
				outer instanceof AvailLiteralNode;
		}
		final AvailBlockNode blockNode = new AvailDecompiler().parseWithOuterVarsTempGenerator(
			theCode,
			theOuters,
			_tempGenerator);
		pushExpression(blockNode);
	}

	@Override
	public void L1_doExtension ()
	{
		//  The extension nybblecode was encountered.  Read another nybble and dispatch it through ExtendedSelectors.

		byte nybble = _nybbles.extractNybbleFromTupleAt(_pc);
		_pc++;
		L1Operation.values()[nybble + 16].dispatch(this);
	}

	@Override
	public void L1_doGetLocal ()
	{
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).

		final AvailVariableDeclarationNode localDecl = _locals.get(((getInteger() - _code.numArgs()) - 1));
		final AvailVariableUseNode useNode = new AvailVariableUseNode();
		useNode.name(localDecl.name());
		useNode.associatedDeclaration(localDecl);
		useNode.isLastUse(false);
		pushExpression(useNode);
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).
		//  If the variable itself is mutable, clear it now - nobody will know.

		final AvailVariableDeclarationNode localDecl = _locals.get(((getInteger() - _code.numArgs()) - 1));
		final AvailVariableUseNode useNode = new AvailVariableUseNode();
		useNode.name(localDecl.name());
		useNode.associatedDeclaration(localDecl);
		useNode.isLastUse(true);
		pushExpression(useNode);
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current closure.
		//  If the variable itself is mutable, clear it at this time - nobody will know.

		final AvailVariableUseNode variable = new AvailVariableUseNode();
		final AvailParseNode outer = _outers.get((getInteger() - 1));
		if (outer.isLiteralNode())
		{
			final AvailLiteralNode outerLiteral = ((AvailLiteralNode)(outer));
			final AvailKeywordToken synthToken = new AvailKeywordToken();
			synthToken.string(outerLiteral.token().string());
			final AvailVariableSyntheticDeclarationNode synthDecl = new AvailVariableSyntheticDeclarationNode();
			synthDecl.name(synthToken);
			synthDecl.declaredType(outerLiteral.availValue().type().innerType());
			synthDecl.isArgument(false);
			synthDecl.availVariable(outerLiteral.availValue());
			variable.associatedDeclaration(synthDecl);
			variable.name(synthToken);
		}
		else
		{

			final AvailVariableDeclarationNode outerDecl = ((AvailReferenceNode)(outer)).variable().associatedDeclaration();
			variable.associatedDeclaration(outerDecl);
			variable.name(outerDecl.name());
		}
		variable.isLastUse(false);
		pushExpression(variable);
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
		final AvailVariableDeclarationNode decl = (isArg ? _args.get(index - 1) : _locals.get(((index - _code.numArgs()) - 1)));
		final AvailVariableUseNode use = new AvailVariableUseNode();
		use.name(decl.name());
		use.associatedDeclaration(decl);
		use.isLastUse(true);
		if (isArg)
		{
			pushExpression(use);
		}
		else
		{
			final AvailReferenceNode ref = new AvailReferenceNode();
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

		pushExpression(_outers.get((getInteger() - 1)));
	}

	@Override
	public void L1_doPushLiteral ()
	{
		//  [n] - Push the literal indexed by n in the current compiledCode.

		final AvailObject value = _code.literalAt(getInteger());
		AvailLiteralNode expr;
		if (value.isClosure())
		{
			List<AvailLiteralNode> closureOuters;
			closureOuters = new ArrayList<AvailLiteralNode>(value.numOuterVars());
			for (int i = 1; i <= value.numOuterVars(); i++)
			{
				AvailObject var = value.outerVarAt(i);
				AvailLiteralToken token = new AvailLiteralToken();
				token.string("AnOuter" + Integer.toString(i));
				token.literal(var);
				AvailLiteralNode node = new AvailLiteralNode();
				node.token(token);
				closureOuters.add(node);
			}
			final AvailBlockNode blockNode = new AvailDecompiler().parseWithOuterVarsTempGenerator(
				value.code(),
				closureOuters,
				_tempGenerator);
			pushExpression(blockNode);
		}
		else
		{
			AvailLiteralToken token = new AvailLiteralToken();
			token.string(value.toString());
			token.literal(value);
			expr = new AvailLiteralNode();
			expr.token(token);
			pushExpression(expr);
		}
	}

	@Override
	public void L1_doPushLocal ()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.

		final int index = getInteger();
		final boolean isArg = index <= _code.numArgs();
		final AvailVariableDeclarationNode decl = (isArg ? _args.get(index - 1) : _locals.get(((index - _code.numArgs()) - 1)));
		final AvailVariableUseNode use = new AvailVariableUseNode();
		use.name(decl.name());
		use.associatedDeclaration(decl);
		use.isLastUse(true);
		if (isArg)
		{
			pushExpression(use);
		}
		else
		{
			final AvailReferenceNode ref = new AvailReferenceNode();
			ref.variable(use);
			pushExpression(ref);
		}
	}

	@Override
	public void L1_doPushOuter ()
	{
		//  [n] - Push the outer variable indexed by n in the current closure.

		pushExpression(_outers.get((getInteger() - 1)));
	}

	@Override
	public void L1_doReturn ()
	{
		//  Return to the calling continuation with top of stack.  Must be the last instruction in block.
		//  Note that the calling continuation has automatically pre-pushed a void object as a
		//  sentinel, which should simply be replaced by this value (to avoid manipulating the stackp).

		assert (_pc == (_nybbles.tupleSize() + 1));
		_statements.add(popExpression());
		assert (_expressionStack.size() == 0) : "There should be nothing on the stack after a return";
	}

	@Override
	public void L1_doSetLocal ()
	{
		//  [n] - Pop the stack and assign this value to the local variable (not an argument) indexed by n (index 1 is first argument).

		final AvailVariableDeclarationNode localDecl = _locals.get(((getInteger() - _code.numArgs()) - 1));
		final AvailParseNode value = popExpression();
		final AvailVariableUseNode variable = new AvailVariableUseNode();
		variable.name(localDecl.name());
		variable.associatedDeclaration(localDecl);
		variable.isLastUse(false);
		final AvailAssignmentNode assignment = new AvailAssignmentNode();
		assignment.variable(variable);
		assignment.expression(value);
		_statements.add(assignment);
	}

	@Override
	public void L1_doSetOuter ()
	{
		//  [n] - Pop the stack and assign this value to the outer variable indexed by n in the current closure.

		final AvailVariableUseNode variable = new AvailVariableUseNode();
		final AvailParseNode outer = _outers.get((getInteger() - 1));
		if (outer.isLiteralNode())
		{
			final AvailLiteralNode outerLiteral = ((AvailLiteralNode)(outer));
			final AvailKeywordToken synthToken = new AvailKeywordToken();
			synthToken.string(outerLiteral.token().string());
			final AvailVariableSyntheticDeclarationNode synthDecl = new AvailVariableSyntheticDeclarationNode();
			synthDecl.name(synthToken);
			synthDecl.declaredType(outerLiteral.availValue().type().innerType());
			synthDecl.isArgument(false);
			synthDecl.availVariable(outerLiteral.availValue());
			variable.associatedDeclaration(synthDecl);
			variable.name(synthToken);
		}
		else
		{

			final AvailVariableDeclarationNode outerDecl = ((AvailReferenceNode)(outer)).variable().associatedDeclaration();
			variable.associatedDeclaration(outerDecl);
			variable.name(outerDecl.name());
		}
		variable.isLastUse(false);
		final AvailParseNode value = popExpression();
		final AvailAssignmentNode assignment = new AvailAssignmentNode();
		assignment.variable(variable);
		assignment.expression(value);
		_statements.add(assignment);
	}

	@Override
	public void L1_doVerifyType ()
	{
		//  [n] - Ensure the top of stack's type is a subtype of the type found at
		//  index n in the current compiledCode.  If this is not the case, raise a
		//  special runtime error or exception.
		//
		//  This nybblecode is only supposed to be used for verifying return types after
		//  method calls.

		final AvailParseNode top = popExpression();
		assert top.isSend();
		((AvailSendNode)(top)).returnType(_code.literalAt(getInteger()));
		pushExpression(top);
	}



	// private-variables

	void buildArgsAndLocals ()
	{
		//  Create the appropriate variable declaration nodes.

		_args = new ArrayList<AvailVariableDeclarationNode>(_code.numArgs());
		_locals = new ArrayList<AvailVariableDeclarationNode>(_code.numLocals());
		for (int i = 1, _end1 = _code.numArgs(); i <= _end1; i++)
		{
			final String argName = _tempGenerator.value("arg");
			AvailKeywordToken token = new AvailKeywordToken();
			token.string(argName);
			AvailVariableDeclarationNode decl = new AvailVariableDeclarationNode();
			decl.name(token);
			decl.declaredType(_code.closureType().argTypeAt(i));
			decl.isArgument(true);
			_args.add(decl);
		}
		for (int i = 1, _end2 = _code.numLocals(); i <= _end2; i++)
		{
			final String localName = _tempGenerator.value("local");
			AvailKeywordToken token = new AvailKeywordToken();
			token.string(localName);
			AvailVariableDeclarationNode decl = new AvailVariableDeclarationNode();
			decl.name(token);
			decl.declaredType(_code.localTypeAt(i).innerType());
			decl.isArgument(false);
			_locals.add(decl);
		}
	}





	public static AvailBlockNode parse (AvailObject aClosure)
	{
		// Parse the given statically constructed closure.  It treats outer vars as literals.
		// Answer the resulting AvailBlockNode.

		final Map<String, Integer> counts = new HashMap<String, Integer>();
		Transformer1<String, String> generator = new Transformer1<String, String>()
		{
			@Override
			public String value (String prefix)
			{
				Integer newCount = counts.get(prefix);
				if (newCount == null)
				{
					newCount = 1;
				}
				else
				{
					newCount++;
				};
				counts.put(prefix, newCount);
				return prefix + newCount.toString();
			}
		};

		List<AvailParseNode> closureOuters = new ArrayList<AvailParseNode>(aClosure.numOuterVars());
		for (int i = 1; i <= aClosure.numOuterVars(); i++)
		{
			AvailObject varObject = aClosure.outerVarAt(i);
			AvailLiteralToken token = new AvailLiteralToken();
			token.string("AnOuter" + i);
			token.literal(varObject);
			AvailLiteralNode literalNode = new AvailLiteralNode();
			literalNode.token(token);
			closureOuters.add(literalNode);
		}
		return new AvailDecompiler().parseWithOuterVarsTempGenerator(
			aClosure.code(),
			closureOuters,
			generator);
	}

}
