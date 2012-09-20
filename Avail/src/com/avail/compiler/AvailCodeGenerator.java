/**
 * AvailCodeGenerator.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.primitive.*;

/**
 * An {@link AvailCodeGenerator} is used to convert a {@linkplain
 * ParseNodeDescriptor parse tree} into the corresponding {@linkplain
 * CompiledCodeDescriptor compiled code}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailCodeGenerator
{
	/**
	 * The {@linkplain List list} of {@linkplain AvailInstruction instructions}
	 * generated so far.
	 */
	List<AvailInstruction> instructions = new ArrayList<AvailInstruction>(10);

	/**
	 * The number of arguments with which the resulting {@linkplain
	 * CompiledCodeDescriptor compiled code} will be invoked.
	 */
	int numArgs;

	/**
	 * A mapping from local variable/constant/argument/label declarations to
	 * index.
	 */
	Map<AvailObject, Integer> varMap;

	/**
	 * A mapping from lexically captured variable/constant/argument/label
	 * declarations to the index within the list of outer variables that must
	 * be provided when creating a function from the compiled code.
	 */
	Map<AvailObject, Integer> outerMap;

	/**
	 * The list of literal objects that have been encountered so far.
	 */
	List<AvailObject> literals = new ArrayList<AvailObject>(10);

	/**
	 * The current stack depth, which is the number of objects that have been
	 * pushed but not yet been popped at this point in the list of instructions.
	 */
	int depth = 0;

	/**
	 * The maximum stack depth that has been encountered so far.
	 */
	int maxDepth = 0;

	/**
	 * A mapping from {@link DeclarationKind#LABEL label} to {@link AvailLabel},
	 * a pseudo-instruction.
	 */
	Map<AvailObject, AvailLabel> labelInstructions;

	/**
	 * The type of result that should be generated by running the code.
	 */
	AvailObject resultType;

	/**
	 * The {@linkplain SetDescriptor set} of {@linkplain ObjectTypeDescriptor
	 * exceptions} that this code may raise.
	 */
	AvailObject exceptionSet;

	/**
	 * Which {@linkplain Primitive primitive VM operation} should be invoked, or
	 * zero if none.
	 */
	int primitive = 0;

	/**
	 * The module in which this code occurs.
	 */
	private AvailObject module = NullDescriptor.nullObject();

	/**
	 * The line number on which this code starts.
	 */
	int lineNumber = 0;

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
		index = literals.indexOf(aLiteral) + 1;
		if (index == 0)
		{
			literals.add(aLiteral);
			index = literals.size();
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
		return numArgs;
	}

	/**
	 * @return The module in which code generation is deemed to take place.
	 */
	public AvailObject module ()
	{
		return module;
	}

	/**
	 * Finish compilation of the block, answering the resulting compiledCode
	 * object.
	 *
	 * @return A {@linkplain CompiledCodeDescriptor compiled code} object.
	 */
	public AvailObject endBlock ()
	{
		fixFinalUses();
		final ByteArrayOutputStream nybbles = new ByteArrayOutputStream(50);
		// Detect blocks that immediately return a constant and mark them with a
		// special primitive number.
		if (primitive == 0 && instructions.size() == 1)
		{
			final AvailInstruction onlyInstruction = instructions.get(0);
			if (onlyInstruction instanceof AvailPushLiteral)
			{
				if (((AvailPushLiteral)onlyInstruction).index() == 1)
				{
					primitive(P_340_PushConstant.instance.primitiveNumber);
				}
			}
			if (numArgs() == 1
				&& onlyInstruction instanceof AvailPushLocalVariable
				&& ((AvailPushLocalVariable)onlyInstruction).index() == 1)
			{
				primitive(P_341_PushArgument.instance.primitiveNumber);
			}
		}
		for (final AvailInstruction instruction : instructions)
		{
			instruction.writeNybblesOn(nybbles);
		}
		final List<Integer> nybblesArray = new ArrayList<Integer>();
		for (final byte nybble : nybbles.toByteArray())
		{
			nybblesArray.add(new Integer(nybble));
		}
		final AvailObject nybbleTuple = TupleDescriptor.fromIntegerList(
			nybblesArray);
		nybbleTuple.makeImmutable();
		assert resultType.isType();
		final AvailObject[] argsArray = new AvailObject[numArgs];
		final AvailObject[] localsArray =
			new AvailObject[varMap.size() - numArgs];
		for (final Map.Entry<AvailObject, Integer> entry : varMap.entrySet())
		{
			final int i = entry.getValue();
			final AvailObject argDeclType = entry.getKey().declaredType();
			if (i <= numArgs)
			{
				assert argDeclType.isType();
				argsArray[i - 1] = argDeclType;
			}
			else
			{
				localsArray[i - numArgs - 1] =
					VariableTypeDescriptor.wrapInnerType(argDeclType);
			}
		}
		final AvailObject argsTuple = TupleDescriptor.from(argsArray);
		final AvailObject localsTuple = TupleDescriptor.from(localsArray);
		final AvailObject [] outerArray = new AvailObject[outerMap.size()];
		for (final Map.Entry<AvailObject, Integer> entry : outerMap.entrySet())
		{
			final int i = entry.getValue();
			final AvailObject argDecl = entry.getKey();
			final AvailObject argDeclType = argDecl.declaredType();
			final DeclarationKind kind = argDecl.declarationKind();
			if (kind == DeclarationKind.ARGUMENT
				|| kind == DeclarationKind.LABEL)
			{
				outerArray[i - 1] = argDeclType;
			}
			else
			{
				outerArray[i - 1] =
					VariableTypeDescriptor.wrapInnerType(argDeclType);
			}
		}
		final AvailObject outerTuple = TupleDescriptor.from(outerArray);
		final AvailObject code = CompiledCodeDescriptor.create(
			nybbleTuple,
			varMap.size() - numArgs,
			maxDepth,
			FunctionTypeDescriptor.create(
				argsTuple,
				resultType,
				exceptionSet),
			primitive,
			TupleDescriptor.fromList(literals),
			localsTuple,
			outerTuple,
			module,
			lineNumber);
		code.makeImmutable();
		return code;
	}


	/**
	 * Start generation of a block.
	 *
	 * @param arguments
	 *        The {@linkplain TupleDescriptor tuple} of
	 *        {@link DeclarationKind#ARGUMENT argument} {@linkplain
	 *        DeclarationNodeDescriptor declaration nodes}.
	 * @param locals
	 *        The {@link List} of {@link DeclarationKind#LOCAL_VARIABLE local
	 *        variable} and {@link DeclarationKind#LOCAL_CONSTANT local
	 *        constant} {@linkplain DeclarationNodeDescriptor declaration
	 *        nodes}.
	 * @param labels
	 *        The {@link List} of {@link DeclarationKind#LABEL label}
	 *        {@linkplain DeclarationNodeDescriptor declaration nodes}.
	 * @param outerVars
	 *        The {@linkplain TupleDescriptor tuple} of lexically captured
	 *        (outer) {@linkplain DeclarationNodeDescriptor declarations}.
	 * @param theResultType
	 *        A {@linkplain CompiledCodeDescriptor compiled code} object.
	 * @param theExceptionSet
	 *        A {@linkplain SetDescriptor set} of {@linkplain
	 *        ObjectTypeDescriptor exception types} that the block is permitted
	 *        to raise.
	 * @param theLineNumber
	 *        The module line number on which this block starts.
	 */
	public void startBlock (
		final AvailObject arguments,
		final List<AvailObject> locals,
		final List<AvailObject> labels,
		final AvailObject outerVars,
		final AvailObject theResultType,
		final AvailObject theExceptionSet,
		final int theLineNumber)
	{
		numArgs = arguments.tupleSize();
		varMap = new HashMap<AvailObject, Integer>(
			arguments.tupleSize() + locals.size());
		for (final AvailObject argumentDeclaration : arguments)
		{
			varMap.put(argumentDeclaration, varMap.size() + 1);
		}
		for (final AvailObject local : locals)
		{
			varMap.put(local, varMap.size() + 1);
		}
		outerMap = new HashMap<AvailObject, Integer>(
				outerVars.tupleSize());
		for (final AvailObject outerVar : outerVars)
		{
			outerMap.put(outerVar, outerMap.size() + 1);
		}
		labelInstructions = new HashMap<AvailObject, AvailLabel>(
				labels.size());
		for (final AvailObject label : labels)
		{
			labelInstructions.put(label, new AvailLabel());
		}
		resultType = theResultType;
		exceptionSet = theExceptionSet;
		lineNumber = theLineNumber;
	}


	/**
	 * Decrease the tracked stack depth by the given amount.
	 *
	 * @param delta The number of things popped off the stack.
	 */
	public void decreaseDepth (
		final int delta)
	{
		depth -= delta;
		if (depth < 0)
		{
			error("Inconsistency - Generated code would pop too much.");
			return;
		}
	}


	/**
	 * Increase the tracked stack depth by the given amount.
	 *
	 * @param delta The number of things pushed onto the stack.
	 */
	public void increaseDepth (
		final int delta)
	{
		depth += delta;
		if (depth > maxDepth)
		{
			maxDepth = depth;
		}
	}


	/**
	 * Verify that the stack is empty at this point.
	 */
	public void stackShouldBeEmpty ()
	{
		assert depth == 0 : "The stack should be empty here";
	}


	/**
	 * Write a regular multimethod call.  I expect my arguments to have been
	 * pushed already.
	 *
	 * @param nArgs The number of arguments that the method accepts.
	 * @param method The method in which to look up the
	 *                          method being invoked.
	 * @param returnType The expected return type of the call.
	 */
	public void emitCall (
		final int nArgs,
		final AvailObject method,
		final AvailObject returnType)
	{
		final int messageIndex = indexOfLiteral(method);
		final int returnIndex = indexOfLiteral(returnType);
		instructions.add(new AvailCall(messageIndex, returnIndex));
		// Pops off arguments.
		decreaseDepth(nArgs);
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth(1);
	}

	/**
	 * Create a function from {@code CompiledCodeDescriptor compiled code} and
	 * the pushed outer (lexically bound) variables.
	 *
	 * @param compiledCode
	 *        The code from which to make a function.
	 * @param neededVariables
	 *        A {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        DeclarationNodeDescriptor declarations} of variables that the code
	 *        needs to access.
	 */
	public void emitCloseCode (
		final AvailObject compiledCode,
		final AvailObject neededVariables)
	{
		for (final AvailObject variableDeclaration : neededVariables)
		{
			emitPushLocalOrOuter(variableDeclaration);
		}
		final int codeIndex = indexOfLiteral(compiledCode);
		instructions.add(new AvailCloseCode(
			neededVariables.tupleSize(),
			codeIndex));
		// Copied variables are popped.
		decreaseDepth(neededVariables.tupleSize());
		// Function is pushed.
		increaseDepth(1);
	}

	/**
	 * Emit code to get the value of a literal variable.
	 *
	 * @param aLiteral
	 *            The {@linkplain VariableDescriptor variable} that should have
	 *            its value extracted.
	 */
	public void emitGetLiteral (
		final AvailObject aLiteral)
	{
		// Push one thing.
		increaseDepth(1);
		final int index = indexOfLiteral(aLiteral);
		instructions.add(new AvailGetLiteralVariable(index));
	}

	/**
	 * Emit code to duplicate the element at the top of the stack.
	 */
	public void emitDuplicate ()
	{
		increaseDepth(1);
		instructions.add(new AvailDuplicate());
	}

	/**
	 * Emit code to get the value of a local or outer (captured) variable.
	 *
	 * @param localOrOuter
	 *            The {@linkplain DeclarationNodeDescriptor declaration} of the
	 *            variable that should have its value extracted.
	 */
	public void emitGetLocalOrOuter (
		final AvailObject localOrOuter)
	{
		// Push one thing.
		increaseDepth(1);
		if (varMap.containsKey(localOrOuter))
		{
			instructions.add(new AvailGetLocalVariable(
				varMap.get(localOrOuter)));
			return;
		}
		if (outerMap.containsKey(localOrOuter))
		{
			instructions.add(new AvailGetOuterVariable(
				outerMap.get(localOrOuter)));
			return;
		}
		if (labelInstructions.containsKey(localOrOuter))
		{
			error("This case should have been handled a different way!");
			return;
		}
		error("Consistency error - unknown variable.");
		return;
	}

	/**
	 * Emit a {@linkplain DeclarationNodeDescriptor declaration} of a {@link
	 * DeclarationKind#LABEL label} for the current block.
	 *
	 * @param labelNode The label declaration.
	 */
	public void emitLabelDeclaration (
		final AvailObject labelNode)
	{
		assert instructions.isEmpty()
		: "Label must be first statement in block";
		// stack is unaffected.
		instructions.add(labelInstructions.get(labelNode));
	}

	/**
	 * Emit code to create a {@linkplain TupleDescriptor tuple} from the top N
	 * items on the stack.
	 *
	 * @param count How many pushed items to pop for the new tuple.
	 */
	public void emitMakeList (
		final int count)
	{
		instructions.add(new AvailMakeTuple(count));
		decreaseDepth(count);
		increaseDepth(1);
	}

	/**
	 * Emit code to pop the top value from the stack.
	 */
	public void emitPop ()
	{
		instructions.add(new AvailPop());
		//  Pop one thing.
		decreaseDepth(1);
	}

	/**
	 * Emit code to push a literal object onto the stack.
	 *
	 * @param aLiteral The object to push.
	 */
	public void emitPushLiteral (
		final AvailObject aLiteral)
	{
		//  Push one thing.
		increaseDepth(1);
		final int index = indexOfLiteral(aLiteral);
		instructions.add(new AvailPushLiteral(index));
	}

	/**
	 * Push a variable.  It can be local to the current block or defined in an
	 * outer scope.
	 *
	 * @param variableDeclaration The variable declaration.
	 */
	public void emitPushLocalOrOuter (
		final AvailObject variableDeclaration)
	{
		//  Push a variable.

		increaseDepth(1);
		if (varMap.containsKey(variableDeclaration))
		{
			instructions.add(
				new AvailPushLocalVariable(varMap.get(variableDeclaration)));
			return;
		}
		if (outerMap.containsKey(variableDeclaration))
		{
			instructions.add(
				new AvailPushOuterVariable(outerMap.get(variableDeclaration)));
			return;
		}
		if (labelInstructions.containsKey(variableDeclaration))
		{
			instructions.add(new AvailPushLabel());
			return;
		}
		error("Consistency error - unknown variable.");
		return;
	}

	/**
	 * Emit code to pop the stack and write the popped value into a literal
	 * variable.
	 *
	 * @param aLiteral The variable in which to write.
	 */
	public void emitSetLiteral (
		final AvailObject aLiteral)
	{
		final int index = indexOfLiteral(aLiteral);
		instructions.add(new AvailSetLiteralVariable(index));
		//  Value to assign has been popped.
		decreaseDepth(1);
	}

	/**
	 * Emit code to pop the stack and write into a local or outer variable.
	 *
	 * @param localOrOuter
	 *            The {@linkplain DeclarationNodeDescriptor declaration} of the
	 *            {@link DeclarationKind#LOCAL_VARIABLE local} or outer variable
	 *            in which to write.
	 */
	public void emitSetLocalOrOuter (
		final AvailObject localOrOuter)
	{
		//  Set a variable to the value popped from the stack.

		decreaseDepth(1);
		if (varMap.containsKey(localOrOuter))
		{
			instructions.add(
				new AvailSetLocalVariable(varMap.get(localOrOuter)));
			return;
		}
		if (outerMap.containsKey(localOrOuter))
		{
			instructions.add(
				new AvailSetOuterVariable(outerMap.get(localOrOuter)));
			return;
		}
		if (labelInstructions.containsKey(localOrOuter))
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
		assert primitive == 0 : "Primitive number was already set";
		primitive = primitiveNumber;
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
				Arrays.asList(new AvailVariableAccessNote[varMap.size()]));
		outerData = new ArrayList<AvailVariableAccessNote>(
				Arrays.asList(new AvailVariableAccessNote[outerMap.size()]));
		for (int index = 1, end = instructions.size(); index <= end; index++)
		{
			final AvailInstruction instruction = instructions.get(index - 1);
			instruction.fixFlagsUsingLocalDataOuterDataCodeGenerator(
				localData,
				outerData,
				this);
		}
		// Prevent clearing of the primitive failure variable. This is necessary
		// for primitive 200, but probably also a good idea in general.
		if (primitive != 0)
		{
			final Primitive prim = Primitive.byPrimitiveNumberOrNull(primitive);
			assert prim != null;
			if (!prim.hasFlag(Flag.CannotFail))
			{
				final AvailInstruction fakeFailureVariableUse =
					new AvailGetLocalVariable(numArgs + 1);
				fakeFailureVariableUse
					.fixFlagsUsingLocalDataOuterDataCodeGenerator(
						localData,
						outerData,
						this);
			}
		}
	}

	/**
	 * Construct a new {@link AvailCodeGenerator}.
	 *
	 * @param module The module being compiled.
	 */
	public AvailCodeGenerator (
		final AvailObject module)
	{
		this.module = module;
	}
}
