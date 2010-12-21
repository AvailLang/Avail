/**
 * compiler/AvailCodeGenerator.java
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

package com.avail.compiler;

import static com.avail.descriptor.AvailObject.error;
import java.io.ByteArrayOutputStream;
import java.util.*;
import com.avail.compiler.instruction.*;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Primitive;

public class AvailCodeGenerator
{
	List<AvailInstruction> _instructions = new ArrayList<AvailInstruction>(10);
	int _numArgs;
	Map<AvailVariableDeclarationNode, Integer> _varMap;
	Map<AvailVariableDeclarationNode, Integer> _outerMap;
	List<AvailObject> _literals = new ArrayList<AvailObject>(10);
	int _depth = 0;
	int _maxDepth = 0;
	Map<AvailLabelNode, AvailLabel> _labelInstructions;
	AvailObject _resultType;
	int _primitive = 0;



	/**
	 * Answer the index of the literal, adding it if not already present.
	 *
	 * @param aLiteral The literal to look up.
	 * @return The index of the literal.
	 */
	public int indexOfLiteral (
		final AvailObject aLiteral)
	{

		int index;
		index = _literals.indexOf(aLiteral) + 1;
		if (index == 0)
		{
			_literals.add(aLiteral);
			index = _literals.size();
		}
		return index;
	}

	/**
	 * Answer the number of arguments that the code under construction accepts.
	 *
	 * @return The code's number of arguments.
	 */
	public int numArgs ()
	{
		return _numArgs;
	}



	/**
	 * Finish compilation of the block, answering the resulting compiledCode
	 * object.
	 *
	 * @return A {@link CompiledCodeDescriptor compiled code} object.
	 */

	public AvailObject endBlock ()
	{
		fixFinalUses();
		ByteArrayOutputStream nybbles;
		AvailObject nybbleTuple;
		nybbles = new ByteArrayOutputStream(50);
		if (_primitive == 0 && _instructions.size() == 1)
		{
			AvailInstruction onlyInstruction = _instructions.get(0);
			if (onlyInstruction instanceof AvailPushLiteral)
			{
				if (((AvailPushLiteral)onlyInstruction).index() == 1)
				{
					primitive(340);
				}
			}
		}
		for (AvailInstruction instruction : _instructions)
		{
			instruction.writeNybblesOn(nybbles);
		}
		List<Integer> nybbleIntegerArray = new ArrayList<Integer>();
		for (byte nybble : nybbles.toByteArray())
		{
			nybbleIntegerArray.add(new Integer(nybble));
		}
		nybbleTuple = TupleDescriptor.mutableCompressedFromIntegerArray(
			nybbleIntegerArray);
		nybbleTuple.makeImmutable();
		assert _resultType.isType();
		List<AvailObject> outerArray;
		AvailObject outerTuple;
		List<AvailObject> argsArray;
		AvailObject argsTuple;
		List<AvailObject> localsArray;
		AvailObject localsTuple;
		argsArray = new ArrayList<AvailObject>(
				Arrays.asList(new AvailObject[_numArgs]));
		localsArray = new ArrayList<AvailObject>(
				Arrays.asList(new AvailObject[_varMap.size() - _numArgs]));
		for (Map.Entry<AvailVariableDeclarationNode, Integer> entry
				: _varMap.entrySet())
		{
			int i = entry.getValue();
			AvailVariableDeclarationNode argDecl = entry.getKey();
			AvailObject argDeclType = argDecl.declaredType();
			if (i <= _numArgs)
			{
				assert argDeclType.isInstanceOfSubtypeOf(Types.type.object());
				argsArray.set(i - 1, argDeclType);
			}
			else
			{
				localsArray.set(
					i - _numArgs - 1,
					ContainerTypeDescriptor.wrapInnerType(
						argDeclType));
			}
		}
		argsTuple = TupleDescriptor.mutableObjectFromArray(argsArray);
		localsTuple = TupleDescriptor.mutableObjectFromArray(localsArray);
		outerArray = new ArrayList<AvailObject>(
				Arrays.asList(new AvailObject[_outerMap.size()]));
		for (Map.Entry<AvailVariableDeclarationNode, Integer> entry
				: _outerMap.entrySet())
		{
			int i = entry.getValue();
			AvailVariableDeclarationNode argDecl = entry.getKey();
			AvailObject argDeclType = argDecl.declaredType();
			if (argDecl.isArgument() || argDecl.isLabel())
			{
				outerArray.set(i - 1, argDeclType);
			}
			else
			{
				outerArray.set(
					i - 1,
					ContainerTypeDescriptor.wrapInnerType(
						argDeclType));
			}
		}
		outerTuple = TupleDescriptor.mutableObjectFromArray(outerArray);
		final AvailObject code = CompiledCodeDescriptor.create(
			nybbleTuple,
			_numArgs,
			(_varMap.size() - _numArgs),
			_maxDepth,
			ClosureTypeDescriptor.closureTypeForArgumentTypesReturnType(
				argsTuple,
				_resultType),
			_primitive,
			TupleDescriptor.mutableObjectFromArray(_literals),
			localsTuple,
			outerTuple);
		code.makeImmutable();
		return code;
	}

	public void startBlockWithArgumentsLocalsLabelsOuterVarsResultType (
		final List<AvailVariableDeclarationNode> arguments,
		final List<AvailVariableDeclarationNode> locals,
		final List<AvailLabelNode> labels,
		final List<AvailVariableDeclarationNode> outerVars,
		final AvailObject resType)
	{
		_numArgs = arguments.size();
		_varMap = new HashMap<AvailVariableDeclarationNode, Integer>(
				arguments.size() + locals.size());
		for (AvailVariableDeclarationNode arg : arguments)
		{
			_varMap.put(arg, _varMap.size() + 1);
		}
		for (AvailVariableDeclarationNode local : locals)
		{
			_varMap.put(local, _varMap.size() + 1);
		}
		_outerMap = new HashMap<AvailVariableDeclarationNode, Integer>(
				outerVars.size());
		for (AvailVariableDeclarationNode outerVar : outerVars)
		{
			_outerMap.put(outerVar, _outerMap.size() + 1);
		}
		_labelInstructions = new HashMap<AvailLabelNode, AvailLabel>(
				labels.size());
		for (AvailLabelNode label : labels)
		{
			_labelInstructions.put(label, new AvailLabel());
		}
		_resultType = resType;
	}


	public void decreaseDepth (
		final int delta)
	{
		_depth -= delta;
		if (_depth < 0)
		{
			error("Inconsistency - Generated code would pop too much.");
			return;
		}
	}


	public void increaseDepth (
		final int delta)
	{
		_depth += delta;
		if (_depth > _maxDepth)
		{
			_maxDepth = _depth;
		}
	}


	/**
	 * Verify that the stack is empty at this point.
	 */
	public void stackShouldBeEmpty ()
	{
		assert _depth == 0 : "The stack should be empty here";
	}



	/**
	 * Write a multimethod super-call.  I expect the arguments to have been
	 * pushed, as well as the types that those arguments should be considered
	 * for the purpose of lookup.
	 *
	 * @param nArgs The number of arguments that the method accepts.
	 * @param implementationSet The implementation set in which to look up the
	 *                          method being invoked.
	 * @param returnType The expected return type of the call.
	 */
	public void emitSuperCall (
		final int nArgs,
		final AvailObject implementationSet,
		final AvailObject returnType)
	{
		final int messageIndex = indexOfLiteral(implementationSet);
		final int returnIndex = indexOfLiteral(returnType);
		_instructions.add(new AvailSuperCall(messageIndex, returnIndex));
		// Pops off all types then all values.
		decreaseDepth(nArgs + nArgs);
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth(1);
	}

	/**
	 * Write a multimethod call.  I expect my arguments to have been pushed.
	 *
	 * @param nArgs The number of arguments that the method accepts.
	 * @param implementationSet The implementation set in which to look up the
	 *                          method being invoked.
	 * @param returnType The expected return type of the call.
	 */
	public void emitCall (
		final int nArgs,
		final AvailObject implementationSet,
		final AvailObject returnType)
	{
		final int messageIndex = indexOfLiteral(implementationSet);
		final int returnIndex = indexOfLiteral(returnType);
		_instructions.add(new AvailCall(messageIndex, returnIndex));
		// Pops off arguments.
		decreaseDepth(nArgs);
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth(1);
	}

	/**
	 * Create a closure from {@code CompiledCodeDescriptor compiled code} and
	 * the pushed outer (lexically bound) variables.
	 *
	 * @param compiledCode The code from which to make a closure.
	 * @param copiedVars A {@link List} of {@link AvailVariableDeclarationNode
	 *                   declarations} of variables that the code needs to
	 *                   access.
	 */
	public void emitCloseCode (
		final AvailObject compiledCode,
		final List<AvailVariableDeclarationNode> copiedVars)
	{
		for (int i = 1, _end1 = copiedVars.size(); i <= _end1; i++)
		{
			emitPushLocalOrOuter(copiedVars.get(i - 1));
		}
		final int codeIndex = indexOfLiteral(compiledCode);
		_instructions.add(new AvailCloseCode(copiedVars.size(), codeIndex));
		// Copied variables are popped.
		decreaseDepth(copiedVars.size());
		// Closure is pushed.
		increaseDepth(1);
	}

	/**
	 * Get the value of a literal variable.
	 *
	 * @param aLiteral The {@link ContainerDescriptor variable} that should have
	 *                 its value extracted.
	 */
	public void emitGetLiteral (
		final AvailObject aLiteral)
	{
		// Push one thing.
		increaseDepth(1);
		final int index = indexOfLiteral(aLiteral);
		_instructions.add(new AvailGetLiteralVariable(index));
	}

	/**
	 * Get the value of a local or outer (captured) variable.
	 *
	 * @param localOrOuter The {@link AvailVariableDeclarationNode declaration}
	 *                     of the variable that should have its value extracted.
	 */
	public void emitGetLocalOrOuter (
		final AvailVariableDeclarationNode localOrOuter)
	{
		// Push one thing.
		increaseDepth(1);
		if (_varMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailGetLocalVariable(
				_varMap.get(localOrOuter)));
			return;
		}
		if (_outerMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailGetOuterVariable(
				_outerMap.get(localOrOuter)));
			return;
		}
		if (_labelInstructions.containsKey(localOrOuter))
		{
			error("This case should have been handled a different way!");
			return;
		}
		error("Consistency error - unknown variable.");
		return;
	}

	/**
	 * Push the type of the value N levels deep in the stack.
	 * @param stackDepth
	 */
	public void emitGetType (
		final int stackDepth)
	{
		_instructions.add(new AvailGetType(stackDepth));
		decreaseDepth(stackDepth + 1);
		//  make sure there are sufficient elements on the stack by 'testing the water'.
		increaseDepth(stackDepth + 1);
		//  undo the effect of 'testing the water'.
		//
		//  push type
		increaseDepth(1);
	}

	public void emitLabelDeclaration (
		final AvailLabelNode labelNode)
	{
		assert _instructions.isEmpty() : "Label must be first statement in block";
		//  stack is unaffected.
		_instructions.add(_labelInstructions.get(labelNode));
	}

	public void emitMakeTuple (
		final int count)
	{
		_instructions.add(new AvailMakeTuple(count));
		decreaseDepth(count);
		increaseDepth(1);
	}

	public void emitPop ()
	{
		_instructions.add(new AvailPop());
		//  Pop one thing.
		decreaseDepth(1);
	}

	public void emitPushLiteral (
		final AvailObject aLiteral)
	{
		increaseDepth(1);
		//  Push one thing.
		final int index = indexOfLiteral(aLiteral);
		_instructions.add(new AvailPushLiteral(index));
	}

	public void emitPushLocalOrOuter (
		final AvailVariableDeclarationNode localOrOuter)
	{
		//  Push a variable.

		increaseDepth(1);
		if (_varMap.containsKey(localOrOuter))
		{
			_instructions.add(
				new AvailPushLocalVariable(_varMap.get(localOrOuter)));
			return;
		}
		if (_outerMap.containsKey(localOrOuter))
		{
			_instructions.add(
				new AvailPushOuterVariable(_outerMap.get(localOrOuter)));
			return;
		}
		if (_labelInstructions.containsKey(localOrOuter))
		{
			_instructions.add(new AvailPushLabel());
			return;
		}
		error("Consistency error - unknown variable.");
		return;
	}

	public void emitSetLiteral (
		final AvailObject aLiteral)
	{
		final int index = indexOfLiteral(aLiteral);
		_instructions.add(new AvailSetLiteralVariable(index));
		//  Value to assign has been popped.
		decreaseDepth(1);
	}

	public void emitSetLocalOrOuter (
		final AvailVariableDeclarationNode localOrOuter)
	{
		//  Set a variable to the value popped from the stack.

		decreaseDepth(1);
		if (_varMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailSetLocalVariable(_varMap.get(localOrOuter)));
			return;
		}
		if (_outerMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailSetOuterVariable(_outerMap.get(localOrOuter)));
			return;
		}
		if (_labelInstructions.containsKey(localOrOuter))
		{
			error("You can't assign to a label!");
			return;
		}
		error("Consistency error - unknown variable.");
		return;
	}

	/**
	 * Set the primitive number to write in the generated code.  A failed
	 * attempt at running the primitive will be followed by running the level
	 * one code (nybblecodes) that this class generates.
	 *
	 * @param primitiveNumber An integer encoding a {@link Primitive}.
	 */
	public void primitive (
		final int primitiveNumber)
	{
		assert _primitive == 0 : "Primitive number was already set";
		_primitive = primitiveNumber;
	}


	/**
	 * Figure out which uses of local and outer variables are final uses.  This
	 * interferes with the concept of labels in the following way.  We now only
	 * allow labels as the first statement in a block, so you can only restart
	 * or exit a continuation (not counting the debugger usage, which shouldn't
	 * affect us).  Restarting requires only the arguments and outer variables
	 * to be preserved, as all local variables are recreated (unassigned) on
	 * restart.  Exiting doesn't require anything of any non-argument locals, so
	 * no problem there.  Note that after the last place in the code where the
	 * continuation is constructed we don't even need any arguments or outer
	 * variables unless the code after this point actually uses the arguments.
	 * We'll be a bit conservative here and simply clean up those arguments and
	 * outer variables which are used after the last continuation construction
	 * point, at their final use points, while always cleaning up final uses of
	 * local non-argument variables.
	 */
	public void fixFinalUses ()
	{
		List<AvailVariableAccessNote> localData;
		List<AvailVariableAccessNote> outerData;
		localData = new ArrayList<AvailVariableAccessNote>(
				Arrays.asList(new AvailVariableAccessNote[_varMap.size()]));
		outerData = new ArrayList<AvailVariableAccessNote>(
				Arrays.asList(new AvailVariableAccessNote[_outerMap.size()]));
		for (int index = 1, _end1 = _instructions.size(); index <= _end1; index++)
		{
			final AvailInstruction instruction = _instructions.get(index - 1);
			instruction.fixFlagsUsingLocalDataOuterDataCodeGenerator(
				localData,
				outerData,
				this);
		}
	}

}
