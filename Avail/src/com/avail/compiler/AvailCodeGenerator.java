/**
 * compiler/AvailCodeGenerator.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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

import com.avail.compiler.AvailLabelNode;
import com.avail.compiler.AvailVariableDeclarationNode;
import com.avail.compiler.instruction.AvailCall;
import com.avail.compiler.instruction.AvailCloseCode;
import com.avail.compiler.instruction.AvailGetLiteralVariable;
import com.avail.compiler.instruction.AvailGetLocalVariable;
import com.avail.compiler.instruction.AvailGetOuterVariable;
import com.avail.compiler.instruction.AvailGetType;
import com.avail.compiler.instruction.AvailInstruction;
import com.avail.compiler.instruction.AvailLabel;
import com.avail.compiler.instruction.AvailMakeList;
import com.avail.compiler.instruction.AvailPop;
import com.avail.compiler.instruction.AvailPushLabel;
import com.avail.compiler.instruction.AvailPushLiteral;
import com.avail.compiler.instruction.AvailPushLocalVariable;
import com.avail.compiler.instruction.AvailPushOuterVariable;
import com.avail.compiler.instruction.AvailReturn;
import com.avail.compiler.instruction.AvailSetLiteralVariable;
import com.avail.compiler.instruction.AvailSetLocalVariable;
import com.avail.compiler.instruction.AvailSetOuterVariable;
import com.avail.compiler.instruction.AvailSuperCall;
import com.avail.compiler.instruction.AvailVariableAccessNote;
import com.avail.compiler.instruction.AvailVerifyType;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ClosureTypeDescriptor;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.ContainerTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.avail.descriptor.AvailObject.*;

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


	// accessing

	public int indexOfLiteral (
			final AvailObject aLiteral)
	{

		int index;
		index = _literals.indexOf(aLiteral) + 1;
		if ((index == 0))
		{
			_literals.add(aLiteral);
			index = _literals.size();
		}
		return index;
	}

	public int numArgs ()
	{
		//  Answer the number of arguments that the block under construction accepts.

		return _numArgs;
	}



	// code generation

	public AvailObject endBlock ()
	{
		//  Finish compilation of the block, answering the resulting compiledCode object.

		fixFinalUses();
		ByteArrayOutputStream nybbles;
		AvailObject nybbleTuple;
		nybbles = new ByteArrayOutputStream(50);
		for (AvailInstruction instruction : _instructions)
		{
			instruction.writeNybblesOn(nybbles);
		}
		List<Integer> nybbleIntegerArray = new ArrayList<Integer>();
		for (byte nybble : nybbles.toByteArray())
		{
			nybbleIntegerArray.add(new Integer(nybble));
		}
		nybbleTuple = TupleDescriptor.mutableCompressedFromIntegerArray(nybbleIntegerArray);
		nybbleTuple.makeImmutable();
		//  (numArgs = (varMap keys count: [:var | var isArgument])) assert.
		assert _resultType.isType();
		List<AvailObject> outerArray;
		AvailObject outerTuple;
		List<AvailObject> argsArray;
		AvailObject argsTuple;
		List<AvailObject> localsArray;
		AvailObject localsTuple;
		argsArray = new ArrayList<AvailObject>(Arrays.asList(new AvailObject[_numArgs]));
		localsArray = new ArrayList<AvailObject>(Arrays.asList(new AvailObject[_varMap.size() - _numArgs]));
		for (Map.Entry<AvailVariableDeclarationNode, Integer> entry : _varMap.entrySet())
		{
			int i = entry.getValue();
			AvailVariableDeclarationNode argDecl = entry.getKey();
			AvailObject argDeclType = argDecl.declaredType();
			if (i <= _numArgs)
			{
				assert argDeclType.isInstanceOfSubtypeOf(TypeDescriptor.type());
				argsArray.set(i - 1, argDeclType);
			}
			else
			{
				localsArray.set(i - _numArgs - 1, ContainerTypeDescriptor.containerTypeForInnerType(argDeclType));
			}
		}
		argsTuple = TupleDescriptor.mutableObjectFromArray(argsArray);
		localsTuple = TupleDescriptor.mutableObjectFromArray(localsArray);
		outerArray = new ArrayList<AvailObject>(Arrays.asList(new AvailObject[_outerMap.size()]));
		for (Map.Entry<AvailVariableDeclarationNode, Integer> entry : _outerMap.entrySet())
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
				outerArray.set(i - 1, ContainerTypeDescriptor.containerTypeForInnerType(argDeclType));
			}
		}
		outerTuple = TupleDescriptor.mutableObjectFromArray(outerArray);
		final AvailObject code = CompiledCodeDescriptor.newCompiledCodeWithNybblesNumArgsLocalsStackClosureTypePrimitiveLiteralsLocalTypesOuterTypes(
			nybbleTuple,
			_numArgs,
			(_varMap.size() - _numArgs),
			_maxDepth,
			ClosureTypeDescriptor.closureTypeForArgumentTypesReturnType(argsTuple, _resultType),
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
		_varMap = new HashMap<AvailVariableDeclarationNode, Integer>(arguments.size()+locals.size());
		for (AvailVariableDeclarationNode arg : arguments)
		{
			_varMap.put(arg, _varMap.size() + 1);
		}
		for (AvailVariableDeclarationNode local : locals)
		{
			_varMap.put(local, _varMap.size() + 1);
		}
		_outerMap = new HashMap<AvailVariableDeclarationNode, Integer>(outerVars.size());
		for (AvailVariableDeclarationNode outerVar : outerVars)
		{
			_outerMap.put(outerVar, _outerMap.size() + 1);
		}
		_labelInstructions = new HashMap<AvailLabelNode, AvailLabel>(labels.size());
		for (AvailLabelNode label : labels)
		{
			_labelInstructions.put(label, new AvailLabel());
		}
		_resultType = resType;
	}



	// depth tracking

	public void decreaseDepth (
			final int delta)
	{
		_depth -= delta;
		if ((_depth < 0))
		{
			error("Inconsistency - Generated code would pop too much.");
			return;
		}
	}

	public void increaseDepth (
			final int delta)
	{
		_depth += delta;
		if ((_depth > _maxDepth))
		{
			_maxDepth = _depth;
		}
	}

	public void stackShouldBeEmpty ()
	{
		//  Make sure the stack is empty at this point.

		assert (_depth == 0) : "The stack should be empty here";
	}



	// emitting instructions

	public void emitCallMethodByTypesNumArgsLiteral (
			final int nArgs, 
			final AvailObject aLiteral)
	{
		final int index = indexOfLiteral(aLiteral);
		_instructions.add(new AvailSuperCall().index(index));
		decreaseDepth((nArgs + nArgs));
		//  Pops off all types then all values.
		//
		//  Leaves room for result
		increaseDepth(1);
	}

	public void emitCallMethodByValuesNumArgsLiteral (
			final int nArgs, 
			final AvailObject aLiteral)
	{
		final int index = indexOfLiteral(aLiteral);
		_instructions.add(new AvailCall().index(index));
		decreaseDepth(nArgs);
		//  Pops off arguments
		//
		//  Leaves room for result.
		increaseDepth(1);
	}

	public void emitClosedBlockWithCopiedVars (
			final AvailObject anAvailCompiledBlock, 
			final List<AvailVariableDeclarationNode> copiedVars)
	{
		for (int i = 1, _end1 = copiedVars.size(); i <= _end1; i++)
		{
			emitPushLocalOrOuter(copiedVars.get((i - 1)));
		}
		final int index = indexOfLiteral(anAvailCompiledBlock);
		_instructions.add(new AvailCloseCode().numCopiedVarsCodeIndex(copiedVars.size(), index));
		decreaseDepth(copiedVars.size());
		//  Copied vars get popped.
		//
		//  Closure is pushed.
		increaseDepth(1);
	}

	public void emitGetLiteral (
			final AvailObject aLiteral)
	{
		increaseDepth(1);
		//  Push one thing.
		final int index = indexOfLiteral(aLiteral);
		_instructions.add(new AvailGetLiteralVariable().index(index));
	}

	public void emitGetLocalOrOuter (
			final AvailVariableDeclarationNode localOrOuter)
	{
		//  Get the value of a variable.

		increaseDepth(1);
		if (_varMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailGetLocalVariable().index(_varMap.get(localOrOuter)));
			return;
		}
		if (_outerMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailGetOuterVariable().index(_outerMap.get(localOrOuter)));
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

	public void emitGetType (
			final int stackDepth)
	{
		_instructions.add(new AvailGetType().depth(stackDepth));
		decreaseDepth((stackDepth + 1));
		//  make sure there are sufficient elements on the stack by 'testing the water'.
		increaseDepth((stackDepth + 1));
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

	public void emitMakeList (
			final int count)
	{
		_instructions.add(new AvailMakeList().count(count));
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
		_instructions.add(new AvailPushLiteral().index(index));
	}

	public void emitPushLocalOrOuter (
			final AvailVariableDeclarationNode localOrOuter)
	{
		//  Push a variable.

		increaseDepth(1);
		if (_varMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailPushLocalVariable().index(_varMap.get(localOrOuter)));
			return;
		}
		if (_outerMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailPushOuterVariable().index(_outerMap.get(localOrOuter)));
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

	public void emitReturn ()
	{
		_instructions.add(new AvailReturn());
		if (! (_depth == 1))
		{
			error("Depth at return should have been 1.");
			return;
		}
		//  Make it fail if any code (which must be dead) is generated after this.
		_depth = 0;
	}

	public void emitSetLiteral (
			final AvailObject aLiteral)
	{
		final int index = indexOfLiteral(aLiteral);
		_instructions.add(new AvailSetLiteralVariable().index(index));
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
			_instructions.add(new AvailSetLocalVariable().index(_varMap.get(localOrOuter)));
			return;
		}
		if (_outerMap.containsKey(localOrOuter))
		{
			_instructions.add(new AvailSetOuterVariable().index(_outerMap.get(localOrOuter)));
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

	public void emitVerifyReturnedTypeToBe (
			final AvailObject aType)
	{
		//  Emit code which will verify at runtime that the top of stack is a subinstance of aType.
		//  It should leave the item on the stack.

		if (aType.equals(TypeDescriptor.voidType()))
		{
			return;
		}
		//  Don't bother checking a return type of void.
		final int index = indexOfLiteral(aType);
		_instructions.add(new AvailVerifyType().index(index));
		decreaseDepth(1);
		//  Make sure object is there,...
		//
		//  ...but leave it there.
		increaseDepth(1);
	}

	public void primitive (
			final int primitiveNumber)
	{
		//  Set the primitive number that I will always attempt before falling
		//  back to the Avail implementation.

		assert (_primitive == 0) : "Primitive number was already set";
		_primitive = primitiveNumber;
	}



	// private

	public void fixFinalUses ()
	{
		//  Figure out which uses of local and outer variables are final uses.  This interferes with the
		//  concept of labels in the following way.  We now only allow labels as the first statement in
		//  a block, so you can only restart or exit a continuation (not counting the debugger usage,
		//  which shouldn't affect us).  Restarting requires only the arguments and outers to be
		//  preserved, as all local variables are recreated (unassigned) on restart.  Exiting doesn't
		//  require anything of any nonargument locals, so no problem there.  Note that after the last
		//  place in the code where the continuation is constructed we don't even need any arguments
		//  or outers unless the code after this point actually uses the arguments.  We'll be a bit
		//  conservative here and simply clean up those arguments and outers which are used after
		//  the last continuation construction point, at their final use points, while always cleaning up
		//  final uses of local nonargument variables.

		List<AvailVariableAccessNote> localData;
		List<AvailVariableAccessNote> outerData;
		localData = new ArrayList<AvailVariableAccessNote>(Arrays.asList(new AvailVariableAccessNote[_varMap.size()]));
		outerData = new ArrayList<AvailVariableAccessNote>(Arrays.asList(new AvailVariableAccessNote[_outerMap.size()]));
		for (int index = 1, _end1 = _instructions.size(); index <= _end1; index++)
		{
			final AvailInstruction instruction = _instructions.get((index - 1));
			instruction.fixFlagsUsingLocalDataOuterDataCodeGenerator(
				localData,
				outerData,
				this);
		}
	}





}
