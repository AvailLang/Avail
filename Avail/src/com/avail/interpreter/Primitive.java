/**
 * interpreter/Primitive.java
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

package com.avail.interpreter;

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.*;
import static java.lang.Math.*;
import java.io.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.*;
import com.avail.compiler.node.AssignmentNodeDescriptor;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.instruction.L2AttemptPrimitiveInstruction;


/**
 * This enumeration represents the interface between Avail's Level One
 * nybblecode interpreter and the underlying interfaces of the built-in objects,
 * providing functionality that is (generally) inexpressible within Level One
 * in terms of other Level One operations.  A conforming Avail implementation
 * must provide these primitives with equivalent semantics.
 *
 * <p>The enumeration defines an {@link #attempt(List, Interpreter)
 * attempt} operation that takes a {@link List list} of arguments of type {@link
 * AvailObject}, as well as the {@link Interpreter} on whose behalf the
 * primitive attempt is being made.  The specific enumeration values override
 * the {@code attempt} method with behavior specific to that primitive.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public enum Primitive
{

	/**
	 * <strong>Primitive 1:</strong> Add two extended integers together.
	 */
	prim1_Addition_a_b(1, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(a.plusCanDestroy(b, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 2:</strong> Subtract extended integer b from a.
	 */
	prim2_Subtraction_a_b(2, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(a.minusCanDestroy(b, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 3:</strong> Multiply extended integers a and b.
	 */
	prim3_Multiplication_a_b(3, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(a.timesCanDestroy(b, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 4:</strong> Compute extended integer a divided by b.
	 */
	prim4_Division_a_b(4, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			if (b.equals(IntegerDescriptor.zero()))
			{
				return FAILURE;
			}
			interpreter.primitiveResult(a.divideCanDestroy(b, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 5:</strong> Compare extended integers a < b.  Answers
	 * an Avail boolean.
	 */
	prim5_LessThan_a_b(5, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(a.lessThan(b)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 6:</strong> Compare extended integers a <= b.  Answers
	 * an Avail boolean.
	 */
	prim6_LessOrEqual_a_b(6, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(a.lessOrEqual(b)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 7:</strong> Answer the integer range {@code
	 * 'min <?1 instance <?2 max'}, where {@code <?1} means {@code <=} when
	 * {@code minInc=true}, else {@code <}, and likewise for {@code <?2}.
	 */
	prim7_CreateIntegerRange_min_minInc_max_maxInc(7, 4, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 4;
			final AvailObject min = args.get(0);
			final AvailObject minInc = args.get(1);
			final AvailObject max = args.get(2);
			final AvailObject maxInc = args.get(3);
			interpreter.primitiveResult(
				IntegerRangeTypeDescriptor.create(
					min,
					minInc.extractBoolean(),
					max,
					maxInc.extractBoolean()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 8:</strong> Answer the lower bound.  Test membership to
	 * determine if it's inclusive or exclusive.
	 */
	prim8_LowerBound_range(8, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject range = args.get(0);
			interpreter.primitiveResult(range.lowerBound());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 9:</strong> Answer the upper bound.  Test membership to
	 * determine if it's inclusive or exclusive.
	 */
	prim9_UpperBound_range(9, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject range = args.get(0);
			interpreter.primitiveResult(range.upperBound());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 10:</strong> There are two possibilities.  The
	 * container is mutable, in which case we want to destroy it, or the
	 * container is immutable, in which case we want to make sure the extracted
	 * value becomes immutable (in case the container is being held onto by
	 * something.  Since the primitive invocation code is going to erase it if
	 * it's mutable anyhow, only the second case requires any real work.
	 */
	prim10_GetValue_var(10, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject var = args.get(0);
			final AvailObject value = var.getValue();
			if (!var.descriptor().isMutable())
			{
				value.makeImmutable();
			}
			interpreter.primitiveResult(value);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 11:</strong> Assign the value to the variable.
	 */
	prim11_SetValue_var_value(11, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject var = args.get(0);
			final AvailObject value = args.get(1);
			var.setValue(value);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 12:</strong> Clear the variable.
	 */
	prim12_ClearValue_var(12, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject var = args.get(0);
			var.clearValue();
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 13:</strong> Create a container type using the given
	 * inner type.
	 */
	prim13_CreateContainerType_type(13, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject type = args.get(0);
			interpreter.primitiveResult(
				ContainerTypeDescriptor.wrapInnerType(type));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 14:</strong> Extract the inner type of a container
	 * type.
	 */
	prim14_InnerType_type(14, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject type = args.get(0);
			interpreter.primitiveResult(type.innerType());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 15:</strong> Swap the contents of two containers.
	 */
	prim15_Swap_var1_var2(15, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject var1 = args.get(0);
			final AvailObject var2 = args.get(1);
			final AvailObject tempObject = var1.getValue();
			var1.setValue(var2.getValue());
			var2.setValue(tempObject);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 16:</strong> Create a container with the given inner
	 * type.
	 */
	prim16_CreateContainer_innerType(16, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject innerType = args.get(0);
			interpreter.primitiveResult(
				ContainerDescriptor.forInnerType(innerType));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 17:</strong> Answer true if the variable is unassigned
	 * (has no value).
	 */
	prim17_HasNoValue_var(17, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject var = args.get(0);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(var.value().equalsVoid()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 18:</strong> Get the value of the variable, clear the
	 * variable, then answer the previously extracted value.  This operation
	 * allows store-back patterns to be efficiently implemented in Level One
	 * code while keeping the interpreter itself thread-safe and debugger-safe.
	 */
	prim18_GetClearing_var(18, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject var = args.get(0);
			final AvailObject valueObject = var.getValue();
			var.clearValue();
			interpreter.primitiveResult(valueObject);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 20:</strong> Get the priority of the given process.
	 */
	prim20_GetPriority_processObject(20, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject processObject = args.get(0);
			interpreter.primitiveResult(
				IntegerDescriptor.fromInt(processObject.priority()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 21:</strong> Set the priority of the given process.
	 */
	prim21_SetPriority_processObject_newPriority(21, 2, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject processObject = args.get(0);
			final AvailObject newPriority = args.get(1);
			processObject.priority(newPriority.extractInt());
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 22:</strong> Suspend the given process.  Ignore if the
	 * process is already suspended.
	 */
	prim22_Suspend_processObject(22, 1, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject processObject = args.get(0);
			error("process suspend is not yet implemented");
			return FAILURE;
		}
	},


	/**
	 * <strong>Primitive 23:</strong> Resume the given process.  Ignore if the
	 * process is already running.
	 */
	prim23_Resume_processObject(23, 1, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject processObject = args.get(0);
			error("process resume is not yet implemented");
			return FAILURE;
		}
	},


	/**
	 * <strong>Primitive 24:</strong> Terminate the given process.  Ignore if
	 * the process is already terminated.
	 */
	prim24_Terminate_processObject(24, 1, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject processObject = args.get(0);
			error("process terminate is not yet implemented");
			return FAILURE;
		}
	},


	/**
	 * <strong>Primitive 25:</strong> Answer the currently running process.
	 */
	prim25_CurrentProcess(25, 0, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 0;
			interpreter.primitiveResult(
				interpreter.process().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 26:</strong> Lookup the given name (key) in the
	 * variables of the given process.
	 */
	prim26_LookupProcessVariable_processObject_key(26, 2, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject processObject = args.get(0);
			final AvailObject key = args.get(1);
			interpreter.primitiveResult(
				processObject.processGlobals().mapAt(key).makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 27:</strong> Associate the given value with the given
	 * name (key) in the variables of the given process.
	 */
	prim27_SetProcessVariable_processObject_key_value(27, 3, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject processObject = args.get(0);
			final AvailObject key = args.get(1);
			final AvailObject value = args.get(2);
			processObject.processGlobals(
				processObject.processGlobals().mapAtPuttingCanDestroy(
					key.makeImmutable(),
					value.makeImmutable(),
					true));
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 28:</strong> Wait for the given semaphore.
	 */
	prim28_SemaphoreWait_semaphore(28, 1, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject semaphore = args.get(0);
			error("This semaphore primitive is not yet implemented");
			return FAILURE;
		}
	},


	/**
	 * <strong>Primitive 29:</strong> Signal the given semaphore.
	 */
	prim29_SemaphoreSignal_semaphore(29, 1, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject semaphore = args.get(0);
			error("This semaphore primitive is not yet implemented");
			return FAILURE;
		}
	},


	/**
	 * <strong>Primitive 30:</strong> Answer the type of the given object.
	 */
	prim30_Type_value(30, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject value = args.get(0);
			interpreter.primitiveResult(value.type());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 31:</strong> This eventually needs to be rewritten as
	 * an invocation of typeUnion:canDestroy:.  For now make result immutable.
	 */
	prim31_TypeUnion_type1_type2(31, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			interpreter.primitiveResult(
				type1.typeUnion(type2).makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 32:</strong> This eventually needs to be rewritten as
	 * an invocation of typeIntersection:canDestroy:.  For now make result
	 * immutable.
	 */
	prim32_TypeIntersection_type1_type2(32, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			interpreter.primitiveResult(
				type1.typeIntersection(type2).makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 33:</strong> Answer whether type1 is a subtype of type2
	 * (or equal).
	 */
	prim33_IsSubtypeOf_type1_type2(33, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(type1.isSubtypeOf(type2)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 34:</strong> Create a closure type from a tuple of
	 * argument types and a return type.
	 */
	prim34_CreateClosureType_argTypes_returnType(34, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject argTypes = args.get(0);
			final AvailObject returnType = args.get(1);
			for (int i = 1, _end1 = argTypes.tupleSize(); i <= _end1; i++)
			{
				assert argTypes.tupleAt(i).isInstanceOfSubtypeOf(
					TYPE.o());
			}
			assert returnType.isInstanceOfSubtypeOf(TYPE.o());
			interpreter.primitiveResult(
				ClosureTypeDescriptor.closureTypeForArgumentTypesReturnType(
					argTypes,
					returnType));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 35:</strong> Answer the number af arguments that this
	 * closureType takes.
	 */
	prim35_ClosureTypeNumArgs_closureType(35, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			interpreter.primitiveResult(
				IntegerDescriptor.fromInt(closureType.numArgs()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 36:</strong> Answer the type of the argument at the
	 * given index within the given closureType.
	 */
	prim36_ArgTypeAt_closureType_index(36, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject closureType = args.get(0);
			final AvailObject index = args.get(1);
			interpreter.primitiveResult(
				closureType.argTypeAt(index.extractInt()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 37:</strong> Answer the return type of the given
	 * closureType.
	 */
	prim37_ReturnType_closureType(37, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			interpreter.primitiveResult(closureType.returnType());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 38:</strong> Answer the union of the types in the given
	 * tuple of types.
	 */
	prim38_UnionOfTupleOfTypes_tupleOfTypes(38, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tupleOfTypes = args.get(0);
			AvailObject unionObject = TERMINATES.o();
			for (int i = 1, _end2 = tupleOfTypes.tupleSize(); i <= _end2; i++)
			{
				unionObject = unionObject.typeUnion(tupleOfTypes.tupleAt(i));
			}
			interpreter.primitiveResult(unionObject);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 39:</strong> Answer a generalized closure type with the
	 * given return type.
	 */
	prim39_CreateGeneralizedClosureType_returnType(39, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject returnType = args.get(0);
			interpreter.primitiveResult(
				GeneralizedClosureTypeDescriptor.forReturnType(returnType));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 40:</strong> Block evaluation, given a tuple of
	 * arguments.  Check the types dynamically to prevent corruption of the type
	 * system.  Fail if the arguments are not of the required types.
	 */
	prim40_InvokeWithTuple_block_argTuple(40, 2, Invokes)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject block = args.get(0);
			final AvailObject argTuple = args.get(1);
			final AvailObject blockType = block.type();
			final int numArgs = argTuple.tupleSize();
			if (blockType.numArgs() != numArgs)
			{
				return FAILURE;
			}
			final List<AvailObject> callArgs =
				new ArrayList<AvailObject>(numArgs);
			for (int i = 1; i <= numArgs; i++)
			{
				final AvailObject anArg = argTuple.tupleAt(i);
				if (!anArg.isInstanceOfSubtypeOf(blockType.argTypeAt(i)))
				{
					return FAILURE;
				}
				//  Transfer the argument into callArgs.
				callArgs.add(anArg);
			}
			return interpreter.invokeClosureArguments(
				block,
				callArgs);
		}
	},


	/**
	 * <strong>Primitive 43:</strong> Invoke either the trueBlock or the
	 * falseBlock, depending on aBoolean.
	 */
	prim43_IfThenElse_aBoolean_trueBlock_falseBlock(43, 3, Invokes)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject aBoolean = args.get(0);
			final AvailObject trueBlock = args.get(1);
			final AvailObject falseBlock = args.get(2);
			assert trueBlock.type().numArgs() == 0;
			assert falseBlock.type().numArgs() == 0;
			if (aBoolean.extractBoolean())
			{
				return interpreter.invokeClosureArguments (
					trueBlock,
					args);
			}
			return interpreter.invokeClosureArguments (
				falseBlock,
				args);
		}
	},


	/**
	 * <strong>Primitive 44:</strong> Invoke the trueBlock if aBoolean is true,
	 * otherwise just answer void.
	 */
	prim44_IfThen_aBoolean_trueBlock(44, 2, Invokes)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject aBoolean = args.get(0);
			final AvailObject trueBlock = args.get(1);
			assert trueBlock.type().numArgs() == 0;
			if (aBoolean.extractBoolean())
			{
				return interpreter.invokeClosureArguments (
					trueBlock,
					args);
			}
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 45:</strong> Run the block, ignoring the leading
	 * boolean argument.  This is used for short-circuit evaluation.
	 */
	prim45_ShortCircuitHelper_ignoredBool_block(45, 2, Invokes)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			@SuppressWarnings("unused")
			final AvailObject ignoredBool = args.get(0);
			final AvailObject block = args.get(1);
			assert block.type().numArgs() == 0;
			return interpreter.invokeClosureArguments (
				block,
				args);
		}
	},


	/**
	 * <strong>Primitive 49:</strong> Create a continuation.  Don't allow
	 * anything about level two to be mentioned.
	 */
	prim49_CreateContinuation_callerHolder_closure_pc_stackp_stack(49, 5, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 5;
			final AvailObject callerHolder = args.get(0);
			final AvailObject closure = args.get(1);
			final AvailObject pc = args.get(2);
			final AvailObject stackp = args.get(3);
			final AvailObject stack = args.get(4);
			final AvailObject theCode = closure.code();
			final AvailObject cont = ContinuationDescriptor.mutable().create(
				theCode.numArgsAndLocalsAndStack());
			cont.caller(callerHolder.value());
			cont.closure(closure);
			cont.pc(pc.extractInt());
			cont.stackp(stackp.extractInt());
			cont.levelTwoChunkIndexOffset(
				L2ChunkDescriptor.indexOfUnoptimizedChunk(),
				L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
			for (int i = 1, _end3 = stack.tupleSize(); i <= _end3; i++)
			{
				cont.localOrArgOrStackAtPut(i, stack.tupleAt(i));
			}
			interpreter.primitiveResult(cont);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 50:</strong> Answer the closure type within the given
	 * continuation type.
	 */
	prim50_ContinuationTypeToClosureType_continuationType(50, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject continuationType = args.get(0);
			interpreter.primitiveResult(
				continuationType.closureType());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 51:</strong> Answer a continuation type that uses the
	 * given closure type.
	 */
	prim51_ClosureTypeToContinuationType_closureType(51, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			interpreter.primitiveResult(
				ContinuationTypeDescriptor.forClosureType(closureType));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 52:</strong> Answer the caller of a continuation.  Fail
	 * if there is no caller.
	 */
	prim52_ContinuationCaller_con(52, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			final AvailObject caller = con.caller();
			if (caller.equalsVoid())
			{
				return FAILURE;
			}
			interpreter.primitiveResult(caller);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 53:</strong> Answer the closure of a continuation.
	 */
	prim53_ContinuationClosure_con(53, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			interpreter.primitiveResult(con.closure());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 54:</strong> Answer the pc of a continuation.  This is
	 * the index of the current instruction in the continuation's closure's
	 * code's tuple of nybblecodes.
	 */
	prim54_ContinuationPC_con(54, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			interpreter.primitiveResult(
				IntegerDescriptor.fromInt(con.pc()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 55:</strong> Answer a continuation's stack pointer.
	 * This is the index of the top-of-stack within the {@link
	 * ContinuationDescriptor.ObjectSlots#FRAME_AT_ frame slots} of the
	 * continuation.  For an empty stack its value equals the number of frame
	 * slots plus one.
	 */
	prim55_ContinuationStackPointer_con(55, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			interpreter.primitiveResult(
				IntegerDescriptor.fromInt(con.stackp()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 56:</strong> Restart the given continuation, but
	 * passing in the given tuple of arguments.  Make sure it's a label-like
	 * continuation rather than a call-like, because a call-like continuation
	 * has the expected return type already pushed on the stack, and requires
	 * the return value, after checking against that type, to overwrite the type
	 * in the stack (without affecting the stack depth).  Fail if the
	 * continuation's closure is not capable of accepting the given arguments.
	 */
	prim56_RestartContinuationWithArguments_con_arguments(56, 2, SwitchesContinuation)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject con = args.get(0);
			final AvailObject code = con.closure().code();
			final AvailObject arguments = args.get(1);
			assert con.stackp() == code.numArgsAndLocalsAndStack() + 1
			: "Outer continuation should have been a label- rather than "
				+ "call-continuation";
			assert con.pc() == 1
			: "Labels must only occur at the start of a block.  "
				+ "Only restart that kind of continuation.";
			// The arguments will be referenced by the continuation.

			// No need to make it immutable because current continuation's
			// reference is lost by this.  We go ahead and make a mutable copy
			// (if necessary) because the interpreter requires the current
			// continuation to always be mutable.
			final AvailObject conCopy = con.ensureMutable();
			final AvailObject itsCode = conCopy.closure().code();
			if (!arguments.isTuple())
			{
				return FAILURE;
			}
			if (itsCode.numArgs() != arguments.tupleSize())
			{
				return FAILURE;
			}
			if (!itsCode.closureType().acceptsTupleOfArguments(arguments))
			{
				return FAILURE;
			}
			for (int i = 1, _end4 = itsCode.numArgs(); i <= _end4; i++)
			{
				conCopy.localOrArgOrStackAtPut(i, arguments.tupleAt(i));
			}
			for (int i = 1, _end5 = itsCode.numLocals(); i <= _end5; i++)
			{
				conCopy.localOrArgOrStackAtPut(
					itsCode.numArgs() + i,
					ContainerDescriptor.forOuterType(itsCode.localTypeAt(i)));
			}
			interpreter.prepareToExecuteContinuation(conCopy);
			return CONTINUATION_CHANGED;
		}
	},


	/**
	 * <strong>Primitive 57:</strong> Exit the given continuation (returning
	 * result to its caller).
	 */
	prim57_ExitContinuationWithResult_con_result(57, 2, SwitchesContinuation)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject con = args.get(0);
			final AvailObject result = args.get(1);
			assert con.stackp() == con.objectSlotsCount() + 1
			: "Outer continuation should have been a label- rather than "
				+ "call- continuation";
			assert con.pc() == 1
			: "Labels must only occur at the start of a block.  "
				+ "Only exit that kind of continuation.";
			// No need to make it immutable because current continuation's
			// reference is lost by this.  We go ahead and make a mutable copy
			// (if necessary) because the interpreter requires the current
			// continuation to always be mutable...
			final AvailObject expectedType = con.closure().type().returnType();
			if (!result.isInstanceOfSubtypeOf(expectedType))
			{
				return FAILURE;
			}
			final AvailObject targetCon = con.caller().ensureMutable();
			targetCon.stackAtPut(targetCon.stackp(), result);
			interpreter.prepareToExecuteContinuation(targetCon);
			return CONTINUATION_CHANGED;
		}
	},


	/**
	 * <strong>Primitive 58:</strong> Restart the given continuation.  Make sure
	 * it's a label-like continuation rather than a call-like, because a
	 * call-like continuation requires a value to be stored on its stack in
	 * order to resume it, something this primitive does not do.
	 */
	prim58_RestartContinuation_con(58, 1, SwitchesContinuation)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			final AvailObject code = con.closure().code();
			assert con.stackp() == code.numArgsAndLocalsAndStack() + 1
			: "Outer continuation should have been a label- rather than "
				+ "call-continuation";
			assert con.pc() == 1
			: "Labels must only occur at the start of a block.  "
				+ "Only restart that kind of continuation.";
			// Funny twist - destroy previous continuation in place of one
			// being restarted

			// No need to make it immutable because current continuation's
			// reference is lost by this.  We go ahead and make a mutable copy
			// (if necessary) because the interpreter requires the current
			// continuation to always be mutable.
			final AvailObject conCopy = con.ensureMutable();
			final AvailObject itsCode = conCopy.closure().code();
			for (int i = 1, _end6 = itsCode.numLocals(); i <= _end6; i++)
			{
				conCopy.localOrArgOrStackAtPut(
					itsCode.numArgs() + i,
					ContainerDescriptor.forOuterType(
						itsCode.localTypeAt(i)));
			}
			interpreter.prepareToExecuteContinuation(conCopy);
			return CONTINUATION_CHANGED;
		}
	},


	/**
	 * <strong>Primitive 59:</strong> Answer a tuple containing the
	 * continuation's stack data.  Substitute the integer zero for any void
	 * values.
	 */
	prim59_ContinuationStackData_con(59, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			final short count = con.closure().code().numArgsAndLocalsAndStack();
			final AvailObject tuple = ObjectTupleDescriptor.mutable().create(
				count);
			for (int i = 1; i <= count; i++)
			{
				AvailObject entry = con.localOrArgOrStackAt(i);
				if (entry.equalsVoid())
				{
					entry = IntegerDescriptor.zero();
				}
				tuple.tupleAtPut(i, entry);
			}
			tuple.makeSubobjectsImmutable();
			interpreter.primitiveResult(tuple);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 60:</strong> Compare for equality.  Answer an Avail
	 * boolean.
	 */
	prim60_Equality_a_b(60, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(a.equals(b)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 61:</strong> Convert a map from fields to values into
	 * an object.
	 */
	prim61_MapToObject_map(61, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(
				ObjectDescriptor.objectFromMap(map));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 62:</strong> Convert an object into a map from fields
	 * to values.
	 */
	prim62_ObjectToMap_object(62, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject object = args.get(0);
			interpreter.primitiveResult(object.fieldMap());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 63:</strong> Convert a map from fields to types into an
	 * object type.
	 */
	prim63_MapToObjectType_map(63, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(
				ObjectTypeDescriptor.objectTypeFromMap(map));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 64:</strong> Convert an object type into a map from
	 * fields to types.
	 */
	prim64_ObjectTypeToMap_objectType(64, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectType = args.get(0);
			interpreter.primitiveResult(objectType.fieldTypeMap());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 65:</strong> Extract an objectType from its type, an
	 * objectMeta.
	 */
	prim65_ObjectMetaInstance_objectMeta(65, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectMeta = args.get(0);
			interpreter.primitiveResult(objectMeta.instance());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 66:</strong> Extract an objectMeta from its type, an
	 * objectMetaMeta.
	 */
	prim66_ObjectMetaMetaInstance_objectMetaMeta(66, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectMetaMeta = args.get(0);
			interpreter.primitiveResult(objectMetaMeta.instance());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 67:</strong> Answer the name of a primitive type.
	 */
	prim67_NameOfPrimitiveType_primType(67, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject primType = args.get(0);
			interpreter.primitiveResult(primType.name());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 68:</strong> Assign a name to a {@linkplain
	 * ObjectTypeDescriptor user-defined object type}. This can be useful for
	 * debugging.
	 */
	prim68_RecordNewTypeName_userType_name(68, 2, CanInline)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;

			final AvailObject userType = args.get(0);
			final AvailObject name = args.get(1);

			userType.makeImmutable();
			name.makeImmutable();
			interpreter.runtime().setNameForType(userType, name);

			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 69:</strong> Answer the user-assigned name of the
	 * specified {@linkplain ObjectTypeDescriptor user-defined object type}.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim69_TypeName_userType(69, 1, CanInline)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject userType = args.get(0);

			final AvailObject name =
				interpreter.runtime().nameForType(userType);
			if (name == null)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(name);
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 70:</strong> Construct a block taking numArgs arguments
	 * (each of type all) and returning constantResult.
	 */
	prim70_CreateConstantBlock_numArgs_constantResult(70, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject numArgs = args.get(0);
			final AvailObject constantResult = args.get(1);
			interpreter.primitiveResult(
				ClosureDescriptor.createStubForNumArgsConstantResult(
					numArgs.extractInt(),
					constantResult));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 71:</strong> Construct a block that takes arguments
	 * whose types are specified in argTypes, and returns the result of invoking
	 * the given message with firstArg as the first argument and the tuple of
	 * arguments as the second argument.  Assume the argument types have already
	 * been tried in each applicable requiresBlock, and that the result type
	 * agrees with each returnsBlock.
	 */
	prim71_CreateStubInvokingWithFirstArgAndCallArgsAsTuple_argTypes_message_firstArg_resultType(71, 4, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 4;
			final AvailObject argTypes = args.get(0);
			final AvailObject message = args.get(1);
			final AvailObject firstArg = args.get(2);
			final AvailObject resultType = args.get(3);
			interpreter.primitiveResult(
				ClosureDescriptor.createStubWithArgTypes(
					argTypes,
					interpreter.runtime().methodsAt(message),
					firstArg,
					resultType));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 72:</strong> Answer the compiledCode within this
	 * closure.
	 */
	prim72_CompiledCodeOfClosure_aClosure(72, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject aClosure = args.get(0);
			interpreter.primitiveResult(aClosure.code());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 73:</strong> Answer the tuple of outer variables
	 * captured by this closure.
	 */
	prim73_OuterVariables_aClosure(73, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject aClosure = args.get(0);
			final AvailObject newTupleObject = ObjectTupleDescriptor.mutable()
				.create(aClosure.numOuterVars());
			newTupleObject.hashOrZero(0);
			CanAllocateObjects(false);
			for (int i = 1, _end7 = aClosure.numOuterVars(); i <= _end7; i++)
			{
				final AvailObject outer = aClosure.outerVarAt(i);
				if (outer.equalsVoid())
				{
					newTupleObject.tupleAtPut(i, IntegerDescriptor.zero());
				}
				else
				{
					newTupleObject.tupleAtPut(i, outer);
				}
			}
			CanAllocateObjects(true);
			interpreter.primitiveResult(newTupleObject);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 74:</strong> Answer a closure built from the
	 * compiledCode and the outer variables.
	 */
	prim74_CreateClosure_compiledCode_outers(74, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject compiledCode = args.get(0);
			final AvailObject outers = args.get(1);
			interpreter.primitiveResult(
				ClosureDescriptor.create(compiledCode, outers));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 80:</strong> Answer the size of the map.  This is the
	 * number of entries, which is also the number of keys.
	 */
	prim80_MapSize_map(80, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(
				IntegerDescriptor.fromInt(map.mapSize()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 81:</strong> Check if the key is present in the map.
	 */
	prim81_MapHasKey_map_key(81, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(map.hasKey(key)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 82:</strong> Look up the key in the map, answering the
	 * corresponding value.
	 */
	prim82_MapAtKey_map_key(82, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			interpreter.primitiveResult(map.mapAt(key));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 83:</strong> Answer a new map like the given map, but
	 * with key -> value in it.  Overwrite any existing value if the key is
	 * already present.
	 */
	prim83_MapReplacingKey_map_key_value(83, 3, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			final AvailObject value = args.get(2);
			interpreter.primitiveResult(map.mapAtPuttingCanDestroy(
				key,
				value,
				true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 84:</strong> Answer a new map, but without the given
	 * key.  Answer the original map if the key does not occur in it.
	 */
	prim84_MapWithoutKey_map_key(84, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			interpreter.primitiveResult(
				map.mapWithoutKeyCanDestroy(key, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 85:</strong> Answer an empty map.
	 */
	prim85_CreateEmptyMap(85, 0, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 0;
			interpreter.primitiveResult(MapDescriptor.empty());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 86:</strong> Answer the keys of this map as a set.
	 */
	prim86_MapKeysAsSet_map(86, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(map.keysAsSet());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 87:</strong> Answer a map type with the given type
	 * constraints.
	 */
	prim87_CreateMapType_Sizes_keyType_valueType(87, 3, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject sizes = args.get(0);
			final AvailObject keyType = args.get(1);
			final AvailObject valueType = args.get(2);
			interpreter.primitiveResult(
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					sizes,
					keyType,
					valueType));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 88:</strong> Answer the size range of a map type.  This
	 * specifies the range of sizes a map can have while being considered an
	 * instance of this map type, assuming the keys' types and values' types
	 * also agree with those specified in the map type.
	 */
	prim88_MapTypeSizes_mapType(88, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			interpreter.primitiveResult(mapType.sizeRange());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 89:</strong> Answer the key type of a map type.
	 */
	prim89_MapTypeKeyType_mapType(89, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			interpreter.primitiveResult(mapType.keyType());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 90:</strong> Answer the value type of a map type.
	 */
	prim90_MapTypeValueType_mapType(90, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			interpreter.primitiveResult(mapType.valueType());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 91:</strong> Answer the values of this map as a tuple,
	 * arbitrarily ordered.
	 */
	prim91_MapValuesAsTuple_map(91, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(map.valuesAsTuple());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 100:</strong> Answer the size of the set.
	 */
	prim100_SetSize_set(100, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject set = args.get(0);
			interpreter.primitiveResult(
				IntegerDescriptor.fromInt(set.setSize()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 101:</strong> Check if the object is an element of the
	 * set.
	 */
	prim101_SetHasElement_set_element(101, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject element = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(set.hasElement(element)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 102:</strong> Answer the union of two sets.
	 */
	prim102_SetUnion_set1_set2(102, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			interpreter.primitiveResult(
				set1.setUnionCanDestroy(set2, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 103:</strong> Answer the intersection of two sets.
	 */
	prim103_SetIntersection_set1_set2(103, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			interpreter.primitiveResult(
				set1.setIntersectionCanDestroy(set2, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 104:</strong> Answer the difference between two sets
	 * ({@code set1 - set2}).
	 */
	prim104_SetDifference_set1_set2(104, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			interpreter.primitiveResult(
				set1.setMinusCanDestroy(set2, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 105:</strong> Answer a new set but with newElement in
	 * it.  If it was already present, answer the original set.
	 */
	prim105_SetWith_set_newElement(105, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject newElement = args.get(1);
			interpreter.primitiveResult(
				set.setWithElementCanDestroy(newElement, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 106:</strong> Answer a new set but without
	 * excludeElement in it.  If it was alreay absent, answer the original set.
	 */
	prim106_SetWithout_set_excludedElement(106, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject excludedElement = args.get(1);
			interpreter.primitiveResult(
				set.setWithoutElementCanDestroy(excludedElement, true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 107:</strong> Check if set1 is a subset of set2.
	 */
	prim107_SetIsSubset_set1_set2(107, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(set1.isSubsetOf(set2)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 108:</strong> Answer the empty set.
	 */
	prim108_CreateEmptySet(108, 0, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 0;
			interpreter.primitiveResult(SetDescriptor.empty());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 109:</strong> Convert a tuple into a set.  Clear from
	 * args to avoid having to make elements immutable.
	 */
	prim109_TupleToSet_tuple(109, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tuple = args.get(0);
			interpreter.primitiveResult(tuple.asSet());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 110:</strong> Convert a set into an arbitrarily ordered
	 * tuple.  The conversion is unstable (two calls may produce different
	 * orderings).  Clear from args to avoid having to make elements immutable.
	 */
	prim110_SetToTuple_set(110, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject set = args.get(0);
			interpreter.primitiveResult(set.asTuple());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 111:</strong> Create a set type.
	 */
	prim111_CreateSetType_sizeRange_contentType(111, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject sizeRange = args.get(0);
			final AvailObject contentType = args.get(1);
			interpreter.primitiveResult(
				SetTypeDescriptor.setTypeForSizesContentType(
					sizeRange,
					contentType));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 112:</strong> Extract a set type's range of sizes.
	 * This is the range of sizes that a set must fall in to be considered a
	 * member of the set type, assuming the elements all satisfy the set type's
	 * element type.
	 */
	prim112_SetTypeSizes_setType(112, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject setType = args.get(0);
			interpreter.primitiveResult(setType.sizeRange());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 113:</strong> Extract a set type's content type.
	 */
	prim113_SetTypeElementType_setType(113, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject setType = args.get(0);
			interpreter.primitiveResult(setType.contentType());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 120:</strong> Create a new cyclic type with the given
	 * name.
	 */
	prim120_CreateCyclicType_name(120, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject name = args.get(0);
			interpreter.primitiveResult(CyclicTypeDescriptor.create(name));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 121:</strong> Answer the name of a cyclicType.
	 */
	prim121_CyclicTypeName_cyclicType(121, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cyclicType = args.get(0);
			interpreter.primitiveResult(cyclicType.name());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 130:</strong> Answer the size of the tuple.
	 */
	prim130_TupleSize_tuple(130, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tuple = args.get(0);
			interpreter.primitiveResult(
				IntegerDescriptor.fromInt(tuple.tupleSize()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 131:</strong> Look up an element in the tuple.
	 */
	prim131_TupleAt_tuple_index(131, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject tuple = args.get(0);
			final AvailObject index = args.get(1);
			interpreter.primitiveResult(tuple.tupleAt(index.extractInt()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 132:</strong> Answer a tuple like the given one, but
	 * with an element changed as indicated.
	 */
	prim132_TupleReplaceAt_tuple_index_value(132, 3, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject tuple = args.get(0);
			final AvailObject index = args.get(1);
			final AvailObject value = args.get(2);
			interpreter.primitiveResult(tuple.tupleAtPuttingCanDestroy(
				index.extractInt(),
				value,
				true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 133:</strong> Build a tuple with one element.
	 */
	prim133_CreateTupleSizeOne_soleElement(133, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject soleElement = args.get(0);
			final AvailObject newTupleObject =
				ObjectTupleDescriptor.mutable().create(1);
			newTupleObject.hashOrZero(0);
			newTupleObject.tupleAtPut(1, soleElement);
			interpreter.primitiveResult(newTupleObject);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 134:</strong> Answer the tuple with no elements.
	 */
	prim134_CreateEmptyTuple(134, 0, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 0;
			interpreter.primitiveResult(TupleDescriptor.empty());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 135:</strong> Extract a subtuple with the given range
	 * of elements.
	 */
	prim135_ExtractSubtuple_tuple_start_end(135, 3, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject tuple = args.get(0);
			final AvailObject start = args.get(1);
			final AvailObject end = args.get(2);
			interpreter.primitiveResult(tuple.copyTupleFromToCanDestroy(
				start.extractInt(),
				end.extractInt(),
				true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 136:</strong> Concatenate a tuple of tuples together
	 * into a single tuple.
	 */
	prim136_ConcatenateTuples_tuples(136, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tuples = args.get(0);
			interpreter.primitiveResult(
				tuples.concatenateTuplesCanDestroy(true));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 137:</strong> Construct a tuple type with the given
	 * parameters.  Canonize the data if necessary.
	 */
	prim137_CreateTupleType_sizeRange_typeTuple_defaultType(137, 3, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject sizeRange = args.get(0);
			final AvailObject typeTuple = args.get(1);
			final AvailObject defaultType = args.get(2);
			interpreter.primitiveResult(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					sizeRange,
					typeTuple,
					defaultType));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 138:</strong> Answer the allowed tuple sizes for this
	 * tupleType.  These are the sizes that a tuple may be and still be
	 * considered instances of the tuple type, assuming the element types are
	 * consistent with those specified by the tuple type.
	 */
	prim138_TupleTypeSizes_tupleType(138, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			interpreter.primitiveResult(tupleType.sizeRange());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 139:</strong> Answer the tuple of leading types that
	 * constrain this tupleType.
	 */
	prim139_TupleTypeLeadingTypes_tupleType(139, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			interpreter.primitiveResult(tupleType.typeTuple());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 140:</strong> Answer the default type for elements past
	 * the leading types.
	 */
	prim140_TupleTypeDefaultType_tupleType(140, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			interpreter.primitiveResult(tupleType.defaultType());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 141:</strong> Answer the type for the given element of
	 * instances of the given tuple type.  Answer terminates if out of range.
	 */
	prim141_TupleTypeAt_tupleType_index(141, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject tupleType = args.get(0);
			final AvailObject index = args.get(1);
			interpreter.primitiveResult(tupleType.typeAtIndex(index.extractInt()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 142:</strong> Answer a tuple of types representing the
	 * types of the given range of indices within the tupleType.  Use terminates
	 * for indices out of range.
	 */
	prim142_TupleTypeSequenceOfTypes_tupleType_startIndex_endIndex(142, 3, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject tupleType = args.get(0);
			final AvailObject startIndex = args.get(1);
			final AvailObject endIndex = args.get(2);
			//  For now, play it safe.
			AvailObject tupleObject = ObjectTupleDescriptor.mutable().create(
				endIndex.extractInt() - startIndex.extractInt() + 1);
			tupleObject.hashOrZero(0);
			for (int i = 1, _end8 = tupleObject.tupleSize(); i <= _end8; i++)
			{
				tupleObject.tupleAtPut(i, VoidDescriptor.voidObject());
			}
			for (int i = 1, _end9 = tupleObject.tupleSize(); i <= _end9; i++)
			{
				tupleObject = tupleObject.tupleAtPuttingCanDestroy(
					i,
					tupleType.typeAtIndex((startIndex.extractInt() + i - 1)),
					true);
			}
			interpreter.primitiveResult(tupleObject);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 143:</strong> Answer the type that is the union of the
	 * types within the given range of indices of the given tupleType.  Answer
	 * terminates if all the indices are out of range.
	 */
	prim143_TupleTypeAtThrough_tupleType_startIndex_endIndex(143, 3, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject tupleType = args.get(0);
			final AvailObject startIndex = args.get(1);
			final AvailObject endIndex = args.get(2);
			interpreter.primitiveResult(tupleType.unionOfTypesAtThrough(startIndex.extractInt(), endIndex.extractInt()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 144:</strong> Answer the type that is the type of all
	 * possible concatenations of instances of the given tupleTypes.  This is
	 * basically the returns clause of the two-argument concatenation operation.
	 */
	prim144_TupleTypeConcatenate_tupleType1_tupleType2(144, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject tupleType1 = args.get(0);
			final AvailObject tupleType2 = args.get(1);
			interpreter.primitiveResult(ConcatenatedTupleTypeDescriptor.concatenatingAnd(tupleType1, tupleType2));
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 160:</strong> Open a {@linkplain RandomAccessFile file}
	 * for reading. Answer a {@linkplain CyclicTypeDescriptor handle} that
	 * uniquely identifies the file.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim160_FileOpenRead_nameString(160, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject filename = args.get(0);
			if (!filename.isString())
			{
				return FAILURE;
			}

			final AvailObject handle =
				CyclicTypeDescriptor.create(filename);
			try
			{
				final RandomAccessFile file = new RandomAccessFile(
					filename.asNativeString(), "r");
				interpreter.runtime().putReadableFile(handle, file);
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(handle);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 161:</strong> Open a {@linkplain RandomAccessFile file}
	 * for writing. Answer a {@linkplain CyclicTypeDescriptor handle} that
	 * uniquely identifies the file.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim161_FileOpenWrite_nameString(161, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;

			final AvailObject filename = args.get(0);
			final AvailObject append = args.get(1);
			if (!filename.isString() || !append.isBoolean())
			{
				return FAILURE;
			}

			final AvailObject handle =
				CyclicTypeDescriptor.create(filename);
			try
			{
				final RandomAccessFile file = new RandomAccessFile(
					filename.asNativeString(), "rw");
				if (append.extractBoolean())
				{
					file.seek(file.length());
				}
				else
				{
					file.setLength(0);
				}
				interpreter.runtime().putWritableFile(handle, file);
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(handle);
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 162:</strong> Open a {@linkplain RandomAccessFile file}
	 * for reading and writing. Answer a {@linkplain CyclicTypeDescriptor
	 * handle} that uniquely identifies the file.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim162_FileOpenReadWrite_nameString (162, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject filename = args.get(0);
			if (!filename.isString())
			{
				return FAILURE;
			}

			final AvailObject handle =
				CyclicTypeDescriptor.create(filename);
			try
			{
				final RandomAccessFile file = new RandomAccessFile(
					filename.asNativeString(), "rw");
				interpreter.runtime().putReadableFile(handle, file);
				interpreter.runtime().putWritableFile(handle, file);
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(handle);
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 163:</strong> Close the {@linkplain RandomAccessFile
	 * file} associated with the specified {@linkplain CyclicTypeDescriptor
	 * handle}. Forget the association between the handle and the file.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim163_FileClose_handle(163, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject handle = args.get(0);
			if (!handle.isCyclicType())
			{
				return FAILURE;
			}

			final RandomAccessFile file =
				interpreter.runtime().getOpenFile(handle);
			if (file == null)
			{
				return FAILURE;
			}

			try
			{
				file.close();
			}
			catch (final IOException e)
			{
				// There isn't much to do about a failed close, especially since
				// we've already forgotten about the handle. There's no reason
				// to fail the primitive.
			}

			interpreter.runtime().forgetReadableFile(handle);
			interpreter.runtime().forgetWritableFile(handle);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 164:</strong> Read the requested number of bytes from
	 * the {@link RandomAccessFile file} associated with the specified
	 * {@linkplain CyclicTypeDescriptor handle} and answer them as a {@linkplain
	 * ByteTupleDescriptor tuple}. If fewer bytes are available, then simply
	 * return a shorter tuple. If the request amount is infinite, then answer a
	 * tuple containing all remaining bytes.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim164_FileRead_handle_size(164, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;

			final AvailObject handle = args.get(0);
			final AvailObject size = args.get(1);
			if (!handle.isCyclicType() || !size.isExtendedInteger())
			{
				return FAILURE;
			}

			final RandomAccessFile file =
				interpreter.runtime().getReadableFile(handle);
			if (file == null)
			{
				return FAILURE;
			}

			final byte[] buffer;
			final int bytesRead;
			try
			{
				buffer = size.isFinite()
				? new byte[size.extractInt()]
				           : new byte[(int) Math.min(
				        	   Integer.MAX_VALUE,
				        	   file.length() - file.getFilePointer())];
				bytesRead = file.read(buffer);
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			final AvailObject tuple;
			if (bytesRead > 0)
			{
				tuple = ByteTupleDescriptor.isMutableSize(
					true, bytesRead).mutableObjectOfSize(bytesRead);
				for (int i = 1, end = tuple.tupleSize(); i <= end; i++)
				{
					tuple.rawByteAtPut(i, (short) (buffer[i - 1] & 0xff));
				}
			}
			else
			{
				tuple = TupleDescriptor.empty();
			}

			interpreter.primitiveResult(tuple);
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 165:</strong> Write the specified {@linkplain
	 * TupleDescriptor tuple} to the {@linkplain RandomAccessFile file}
	 * associated with the {@linkplain CyclicTypeDescriptor handle}. Answer
	 * a {@linkplain ByteTupleDescriptor tuple} containing the bytes that
	 * could not be written.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim165_FileWrite_handle_bytes(165, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;

			final AvailObject handle = args.get(0);
			final AvailObject bytes = args.get(1);
			if (!handle.isCyclicType() || !bytes.isByteTuple())
			{
				return FAILURE;
			}

			final RandomAccessFile file =
				interpreter.runtime().getWritableFile(handle);
			if (file == null)
			{
				return FAILURE;
			}

			final byte[] buffer = new byte[bytes.tupleSize()];
			for (int i = 1, end = bytes.tupleSize(); i <= end; i++)
			{
				buffer[i - 1] = (byte) bytes.tupleAt(i).extractByte();
			}

			try
			{
				file.write(buffer);
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			// Always return an empty tuple since RandomAccessFile writes
			// its buffer transactionally.
			interpreter.primitiveResult(TupleDescriptor.empty());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 166:</strong> Answer the size of the
	 * {@linkplain RandomAccessFile file} associated with the specified
	 * {@linkplain CyclicTypeDescriptor handle}. Supports 64-bit file
	 * sizes.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim166_FileSize_handle(166, 1, CanInline)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject handle = args.get(0);
			if (!handle.isCyclicType())
			{
				return FAILURE;
			}

			final RandomAccessFile file =
				interpreter.runtime().getOpenFile(handle);
			if (file == null)
			{
				return FAILURE;
			}

			final long fileSize;
			try
			{
				fileSize = file.length();
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(
				IntegerDescriptor.objectFromLong(fileSize));
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 167:</strong> Answer the current position of the file
	 * pointer within the readable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain CyclicTypeDescriptor handle}. Supports
	 * 64-bit file sizes and positions.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim167_FilePosition_handle(167, 1, CanInline)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject handle = args.get(0);
			if (!handle.isCyclicType())
			{
				return FAILURE;
			}

			final RandomAccessFile file =
				interpreter.runtime().getReadableFile(handle);
			if (file == null)
			{
				return FAILURE;
			}

			final long filePosition;
			try
			{
				filePosition = file.getFilePointer();
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(
				IntegerDescriptor.objectFromLong(filePosition));
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 168:</strong> Set the current position of the file
	 * pointer within the readable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain CyclicTypeDescriptor handle}. Supports
	 * 64-bit file sizes and positions.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim168_FileSetPosition_handle_newPosition(168, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;

			final AvailObject handle = args.get(0);
			final AvailObject filePosition = args.get(1);
			if (!handle.isCyclicType()
					|| !filePosition.isExtendedInteger()
					|| !filePosition.isFinite())
			{
				return FAILURE;
			}

			final RandomAccessFile file =
				interpreter.runtime().getReadableFile(handle);
			if (file == null)
			{
				return FAILURE;
			}

			try
			{
				file.seek(filePosition.extractLong());
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 168:</strong> Force all system buffers associated with
	 * the writable {@linkplain RandomAccessFile file} to synchronize with the
	 * underlying device.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim169_FileSync_handle(169, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject handle = args.get(0);
			if (!handle.isCyclicType())
			{
				return FAILURE;
			}

			final RandomAccessFile file =
				interpreter.runtime().getWritableFile(handle);
			if (file == null)
			{
				return FAILURE;
			}

			try
			{
				file.getFD().sync();
			}
			catch (final IOException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 170:</strong> Does a {@linkplain File file} exists with
	 * the specified filename?
	 */
	prim170_FileExists_nameString(170, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject filename = args.get(0);
			if (!filename.isString())
			{
				return FAILURE;
			}

			final File file = new File(filename.asNativeString());
			final boolean exists;
			try
			{
				exists = file.exists();
			}
			catch (final SecurityException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(exists));
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 171:</strong> Is the {@linkplain File file} with the
	 * specified filename readable by the OS process?
	 */
	prim171_FileCanRead_nameString(171, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject filename = args.get(0);
			if (!filename.isString())
			{
				return FAILURE;
			}

			final File file = new File(filename.asNativeString());
			final boolean readable;
			try
			{
				readable = file.canRead();
			}
			catch (final SecurityException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(readable));
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 172:</strong> Is the {@linkplain File file} with the
	 * specified filename writable by the OS process?
	 */
	prim172_FileCanWrite_nameString(172, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject filename = args.get(0);
			if (!filename.isString())
			{
				return FAILURE;
			}

			final File file = new File(filename.asNativeString());
			final boolean writable;
			try
			{
				writable = file.canWrite();
			}
			catch (final SecurityException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(writable));
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 173:</strong> Is the {@linkplain File file} with the
	 * specified filename executable by the OS process?
	 */
	prim173_FileCanExecute_nameString(173, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject filename = args.get(0);
			if (!filename.isString())
			{
				return FAILURE;
			}

			final File file = new File(filename.asNativeString());
			final boolean executable;
			try
			{
				executable = file.canExecute();
			}
			catch (final SecurityException e)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(executable));
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 174:</strong> Rename the {@linkplain File file} with
	 * the specified source filename.
	 */
	prim174_FileRename_sourceString_destinationString(174, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;

			final AvailObject source = args.get(0);
			final AvailObject destination = args.get(1);
			if (!source.isString() || !destination.isString())
			{
				return FAILURE;
			}

			final File file = new File(source.asNativeString());
			final boolean renamed;
			try
			{
				renamed = file.renameTo(new File(destination.asNativeString()));
			}
			catch (final SecurityException e)
			{
				return FAILURE;
			}

			if (!renamed)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 175:</strong> Unlink the {@linkplain File file} with
	 * the specified filename from the filesystem.
	 */
	prim175_FileUnlink_nameString(175, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject filename = args.get(0);
			if (!filename.isString())
			{
				return FAILURE;
			}

			final File file = new File(filename.asNativeString());
			final boolean deleted;
			try
			{
				deleted = file.delete();
			}
			catch (final SecurityException e)
			{
				return FAILURE;
			}

			if (!deleted)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 180:</strong> Answer the number of arguments expected
	 * by the compiledCode.
	 */
	prim180_CompiledCodeNumArgs_cc(180, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.fromInt(cc.numArgs()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 181:</strong> Answer the number of locals created by
	 * the compiledCode.
	 */
	prim181_CompiledCodeNumLocals_cc(181, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.fromInt(cc.numLocals()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 182:</strong> Answer the number of outer variables in
	 * closures derived from this compiledCode.
	 */
	prim182_CompiledCodeNumOuters_cc(182, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.fromInt(cc.numOuters()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 183:</strong> Answer the number of stack slots (not
	 * counting arguments and locals) created for the compiledCode.
	 */
	prim183_CompiledCodeNumStackSlots_cc(183, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.fromInt(cc.maxStackDepth()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 184:</strong> Answer the nybblecodes of the
	 * compiledCode.
	 */
	prim184_CompiledCodeNybbles_cc(184, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(cc.nybbles());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 185:</strong> Answer the type of closure this
	 * compiledCode will be closed into.
	 */
	prim185_CompiledCodeClosureType_cc(185, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(cc.closureType());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 186:</strong> Answer the primitive number of this
	 * compiledCode.
	 */
	prim186_CompiledCodePrimitiveNumber_cc(186, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.fromInt(cc.primitiveNumber()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 187:</strong> Answer a tuple with the literals from
	 * this compiledCode.
	 */
	prim187_CompiledCodeLiterals_cc(187, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			AvailObject tupleObject = ObjectTupleDescriptor.mutable().create(
				cc.numLiterals());
			tupleObject.hashOrZero(0);
			for (int i = 1, _end10 = tupleObject.tupleSize(); i <= _end10; i++)
			{
				tupleObject.tupleAtPut(i, VoidDescriptor.voidObject());
			}
			AvailObject literal;
			for (int i = 1, _end11 = tupleObject.tupleSize(); i <= _end11; i++)
			{
				literal = cc.literalAt(i);
				if (literal.equalsVoid())
				{
					literal = IntegerDescriptor.zero();
				}
				tupleObject = tupleObject.tupleAtPuttingCanDestroy(
					i,
					literal,
					true);
			}
			interpreter.primitiveResult(tupleObject);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 188:</strong> Answer a compiledCode with the given
	 * data.
	 */
	prim188_CreateCompiledCode_numArgs_locals_outers_stack_nybs_closureType_prim_literals(188, 8, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 8;
			final AvailObject numArgs = args.get(0);
			final AvailObject locals = args.get(1);
			final AvailObject outers = args.get(2);
			final AvailObject stack = args.get(3);
			final AvailObject nybs = args.get(4);
			final AvailObject closureType = args.get(5);
			final AvailObject prim = args.get(6);
			final AvailObject literals = args.get(7);
			final int nLocals = locals.extractInt();
			final int nOuters = outers.extractInt();
			final int nLiteralsTotal = literals.tupleSize();
			interpreter.primitiveResult(
				CompiledCodeDescriptor.create (
					nybs,
					numArgs.extractInt(),
					nLocals,
					stack.extractInt(),
					closureType,
					prim.extractInt(),
					literals.copyTupleFromToCanDestroy(1, nLiteralsTotal - nLocals - nOuters, false),
					literals.copyTupleFromToCanDestroy(nLiteralsTotal - nLocals + 1, nLiteralsTotal, false),
					literals.copyTupleFromToCanDestroy(nLiteralsTotal - nLocals - nOuters + 1, nLiteralsTotal - nLocals, false)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 200:</strong> The Avail failure code invokes the
	 * bodyBlock.  The handlerBlock is only invoked when an exception is raised
	 * (via primitive 201).
	 */
	prim200_CatchException_bodyBlock_handlerBlock(200, 2, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			@SuppressWarnings("unused") final AvailObject bodyBlock = args.get(0);
			@SuppressWarnings("unused") final AvailObject handlerBlock = args.get(1);
			return FAILURE;
		}
	},


	/**
	 * <strong>Primitive 201:</strong> Raise an exception.  Scan the stack of
	 * continuations until one is found for a closure whose code is the
	 * {@link #prim200_CatchException_bodyBlock_handlerBlock primitive 200}.
	 * Get that continuation's second argument (a handler block of one
	 * argument), and check if that handler block will accept the
	 * exceptionValue.  If not, keep looking.  If it will accept it, unwind the
	 * stack so that the primitive 200 method is the top entry, and invoke the
	 * handler block with exceptionValue.  If there is no suitable handler
	 * block, fail this primitive.
	 */
	prim201_RaiseException_exceptionValue(201, 1, SwitchesContinuation)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject exceptionValue = args.get(0);
			return interpreter.searchForExceptionHandler(exceptionValue, args);
		}
	},


	/**
	 * <strong>Primitive 207:</strong> Answer a collection of all visible
	 * messages inside the current tree that expect no more parts than those
	 * already encountered.  Answer it as a map from cyclicType to messageBundle
	 * (typically only zero or one entry).
	 */
	prim207_CompleteMessages_bundleTree(207, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundleTree = args.get(0);
			interpreter.primitiveResult(bundleTree.complete().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 208:</strong> Answer a collection of all visible
	 * messages inside the current tree that expect more parts than those
	 * already encountered.  Answer it as a map from string to
	 * messageBundleTree.
	 */
	prim208_IncompleteMessages_bundleTree(208, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundleTree = args.get(0);
			interpreter.primitiveResult(bundleTree.incomplete().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 209:</strong> Answer a collection of all visible
	 * methods that start with the given string, and have only one part.  Answer
	 * a map from cyclicType to messageBundle.
	 */
	prim209_CompleteMessagesStartingWith_leadingPart(209, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject leadingPart = args.get(0);
			interpreter.primitiveResult(interpreter.completeBundlesStartingWith(leadingPart).makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 210:</strong> Produce a collection of all visible
	 * methods that start with the given string, and have more than one part.
	 * Answer a map from second part (string) to messageBundleTree.
	 */
	prim210_IncompleteMessagesStartingWith_leadingPart(210, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject leadingPart = args.get(0);
			final AvailObject bundles =
				interpreter.incompleteBundlesStartingWith(leadingPart);
			bundles.makeImmutable();
			interpreter.primitiveResult(bundles);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 211:</strong> Answer a message bundle's message (a
	 * {@link CyclicTypeDescriptor cyclicType}).
	 */
	prim211_BundleMessage_bundle(211, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(bundle.message().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 212:</strong> Answer a message bundle's messageParts (a
	 * tuple of strings).
	 */
	prim212_BundleMessageParts_bundle(212, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(bundle.messageParts().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 213:</strong> Answer a set of all currently defined
	 * signatures for the message represented by bundle.  This includes abstract
	 * signatures and forward signatures.
	 */
	prim213_BundleSignatures_bundle(213, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			final AvailObject implementationSet =
				interpreter.runtime().methodsAt(bundle.message());
			final AvailObject implementations =
				implementationSet.implementationsTuple().asSet();
			implementations.makeImmutable();
			interpreter.primitiveResult(implementations);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 214:</strong> Answer whether precedence restrictions
	 * have been defined (yet) for this bundle.
	 */
	prim214_BundleHasRestrictions_bundle(214, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(BooleanDescriptor.objectFrom(bundle.message().hasRestrictions()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 215:</strong> Answer the current precedence
	 * restrictions for this bundle.
	 */
	prim215_BundleRestrictions_bundle(215, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(bundle.restrictions().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 216:</strong> Answer this signature's body's type (a
	 * {@link ClosureTypeDescriptor closureType}).
	 */
	prim216_SignatureBodyType_sig(216, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			interpreter.primitiveResult(sig.bodySignature().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 217:</strong> Answer this methodSignature's bodyBlock
	 * (a {@link ClosureDescriptor closure}).
	 */
	prim217_SignatureBodyBlock_methSig(217, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject methSig = args.get(0);
			interpreter.primitiveResult(methSig.bodyBlock().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 218:</strong> Answer this signature's requiresBlock (a
	 * {@link ClosureDescriptor closure}).
	 */
	prim218_SignatureRequiresBlock_sig(218, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			interpreter.primitiveResult(sig.requiresBlock().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 219:</strong> Answer this signature's returnsBlock, a
	 * closure.
	 */
	prim219_SignatureReturnsBlock_sig(219, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			interpreter.primitiveResult(sig.returnsBlock().makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 220:</strong> Answer the implementationSet (see
	 * {@link ImplementationSetDescriptor}) associated with the given
	 * cyclicType.  This is generally only used when Avail code is constructing
	 * Avail code in the metacircular compiler.
	 */
	prim220_ImplementationSetFromName_name(220, 1, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject aCyclicType = args.get(0);
			interpreter.primitiveResult(
				interpreter.runtime().methodsAt(aCyclicType).makeImmutable());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 221:</strong> Answer the cyclicType associated with the
	 * given implementationSet (see ImplementationSetDescriptor).  This is
	 * generally only used when Avail code is saving or loading Avail code in
	 * the object dumper / loader.
	 */
	prim221_ImplementationSetName_name(221, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject anImplementationSet = args.get(0);
			interpreter.primitiveResult(anImplementationSet.name());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 240:</strong> Retrieve the {@linkplain
	 * AvailRuntime#specialObject(int) special object} with the specified
	 * ordinal.
	 */
	prim240_SpecialObject_index(240, 1, CanFold)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject ordinal = args.get(0);
			final int i = ordinal.extractInt();

			final AvailObject result;
			try
			{
				result = interpreter.runtime().specialObject(i);
			}
			catch (final ArrayIndexOutOfBoundsException e)
			{
				return FAILURE;
			}

			if (result == null)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(result);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 245:</strong> Look up the {@linkplain
	 * CyclicTypeDescriptor true name} bound to the specified {@linkplain
	 * TupleDescriptor name} in the {@linkplain ModuleDescriptor module}
	 * currently under {@linkplain AvailCompiler compilation}, creating the
	 * true name if necessary.
	 */
	prim245_LookupName_name(245, 1, CanInline)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject name = args.get(0);
			if (!name.isString())
			{
				return FAILURE;
			}

			interpreter.primitiveResult(interpreter.lookupName(name));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 249:</strong> Simple macro definition.
	 */
	prim249_SimpleMacroDeclaration_string_block(249, 2, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject string = args.get(0);
			final AvailObject block = args.get(1);
			final AvailObject blockType = block.type();
			for (int i = 1; i <= blockType.numArgs(); i++)
			{
				assert blockType.argTypeAt(i).isSubtypeOf(
					PARSE_NODE.o());
			}
			assert blockType.returnType().isSubtypeOf(
				PARSE_NODE.o());
			interpreter.atAddMacroBody(interpreter.lookupName(string), block);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 250:</strong> Is there a {@linkplain Primitive
	 * primitive} with the specified ordinal?
	 */
	prim250_IsPrimitiveDefined_index(250, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject ordinal = args.get(0);
			if (!ordinal.isExtendedInteger() || !ordinal.isFinite())
			{
				return FAILURE;
			}

			final int index = ordinal.extractInt();
			if (index < 0 || index > 65535)
			{
				return FAILURE;
			}

			interpreter.primitiveResult(BooleanDescriptor.objectFrom(
				interpreter.supportsPrimitive((short) index)));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 251:</strong> Declare method as abstract.  This
	 * identifies responsibility for subclasses that want to be concrete.
	 */
	prim251_AbstractMethodDeclaration_string_blockSignature_requiresBlock_returnsBlock(251, 4, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 4;
			final AvailObject string = args.get(0);
			final AvailObject blockSignature = args.get(1);
			final AvailObject requiresBlock = args.get(2);
			final AvailObject returnsBlock = args.get(3);
			interpreter.atDeclareAbstractSignatureRequiresBlockReturnsBlock(
				interpreter.lookupName(string),
				blockSignature,
				requiresBlock,
				returnsBlock);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 252:</strong> Declare method without body (for
	 * recursion or mutual recursion).
	 */
	prim252_ForwardMethodDeclaration_string_blockSignature(252, 2, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject string = args.get(0);
			final AvailObject blockSignature = args.get(1);
			interpreter.atAddForwardStubFor(interpreter.lookupName(string), blockSignature);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 253:</strong> Method definition, without type
	 * constraint or result type deduction).
	 */
	prim253_SimpleMethodDeclaration_string_block(253, 2, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject string = args.get(0);
			final AvailObject block = args.get(1);
			interpreter.atAddMethodBody(interpreter.lookupName(string), block);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 254:</strong> Method definition with type constraint
	 * and result type calculation.
	 */
	prim254_MethodDeclaration_string_block_requiresBlock_returnsBlock(254, 4, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 4;
			final AvailObject string = args.get(0);
			final AvailObject block = args.get(1);
			final AvailObject requiresBlock = args.get(2);
			final AvailObject returnsBlock = args.get(3);
			interpreter.atAddMethodBodyRequiresBlockReturnsBlock(
				interpreter.lookupName(string),
				block,
				requiresBlock,
				returnsBlock);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 255:</strong> Message precedence declaration with tuple
	 * of sets of messages to exclude for each argument position.  Note that the
	 * tuple's elements should correspond with occurrences of underscore in the
	 * method names, *not* with the (top-level) arguments of the method.  This
	 * distinction is only apparent when chevron notation is used to accept
	 * tuples of arguments.
	 */
	prim255_PrecedenceDeclaration_stringSet_exclusionsTuple(255, 2, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject stringSet = args.get(0);
			final AvailObject exclusionsTuple = args.get(1);
			AvailObject disallowed = exclusionsTuple;
			for (int i = disallowed.tupleSize(); i >= 1; i--)
			{
				AvailObject setOfCyclics = SetDescriptor.empty();
				for (final AvailObject string : exclusionsTuple.tupleAt(i))
				{
					setOfCyclics = setOfCyclics.setWithElementCanDestroy(
						interpreter.lookupName(string),
						true);
				}
				disallowed = disallowed.tupleAtPuttingCanDestroy(
					i,
					setOfCyclics,
					true);
			}
			disallowed.makeImmutable();
			final AvailObject stringSetAsTuple = stringSet.asTuple();
			for (int i = stringSetAsTuple.tupleSize(); i >= 1; i--)
			{
				final AvailObject string = stringSetAsTuple.tupleAt(i);
				interpreter.atDisallowArgumentMessages(
					interpreter.lookupName(string),
					disallowed);
			}
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 256:</strong> Exit the program.
	 */
	prim256_EmergencyExit_value(256, 1, Unknown)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject errorMessageProducer = args.get(0);
			error("The program has exited: " + errorMessageProducer);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 257:</strong> Pause the VM.
	 */
	prim257_BreakPoint(257, 0, Unknown)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				self currentContinuation
					stackAt: self currentContinuation stackp
					put: VoidDescriptor voidObject.
				self prepareToExecuteContinuation: self currentContinuation debug.
				^#continuationChanged
			 */
		}
	},


	/**
	 * <strong>Primitive 258:</strong> Print an object to System.out.
	 */
	prim258_PrintToConsole_value(258, 1, Unknown)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject objectToPrint = args.get(0);
			System.out.println(objectToPrint);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 259:</strong> Produce a {@linkplain
	 * ByteStringDescriptor string} description of the sole argument.
	 */
	prim259_ToString_value(259, 1, Unknown)
	{
		@Override
		public Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectToPrint = args.get(0);
			final String string = objectToPrint.toString();
			final AvailObject availString = ByteStringDescriptor.from(string);
			interpreter.primitiveResult(availString);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 260:</strong> Create an opaque library object into
	 * which we can load declarations.  We may also open it later (load the
	 * actual DLL) via {@link #prim261_OpenLibrary_handle_filename primitive
	 * 261}.  Answer the handle (an integer).  Associated with the handle is:
	 * <ol>
	 * <li>an ExternalDictionary for accumulating declarations,</li>
	 * <li>a place to store an ExternalLibrary if it is subsequently opened.
	 * </li>
	 * </ol>
	 */
	prim260_CreateLibrarySpec(260, 0, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| lib handle |
				lib := Array with: ExternalDictionary new with: nil.
				openLibraries add: lib.
				handle := openLibraries size.
				^IntegerDescriptor objectFromSmalltalkInteger: handle
			 */
		}
	},


	/**
	 * <strong>Primitive 261:</strong> Open a previously constructed library.
	 * Its handle (into openLibraries) is passed.
	 */
	prim261_OpenLibrary_handle_filename(261, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| opaqueLib externalLib |
				opaqueLib := openLibraries at: libraryHandle extractInt.
				(opaqueLib at: 2) isNil assert.
				externalLib := ExternalLibrary
					named: libraryFileName asNativeString
					owner: nil.
				opaqueLib at: 2 put: externalLib.
				^VoidDescriptor voidObject
			 */
		}
	},


	/**
	 * <strong>Primitive 262:</strong> Declare a function for the given library.
	 * Don't look for the entry point yet, that's actually the job of {@link
	 * #prim263_ExtractEntryPoint_handle_functionName primitive 263}.
	 * Instead, parse the declaration to produce an ExternalMethod.  Create an
	 * opaque handle that secretly contains:
	 * <ol>
	 * <li>the library's handle,</li>
	 * <li>the closureType,</li>
	 * <li>an ExternalProcedure (which can cache the function address when
	 * invoked), and</li>
	 * <li>Java code that accepts and returns Avail objects but invokes
	 * the underlying C function.</li>
	 * </ol>
	 */
	prim262_ParseDeclarations_libraryHandle_declaration(262, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| privateLib externalDictionary |
				privateLib := openLibraries at: libraryHandle extractInt.
				externalDictionary := privateLib at: 1.
				externalDictionary notNil assert.
				CDeclarationParser
					parseWithPreprocess: declaration asNativeString readStream
					as: #Cfile
					declarations: externalDictionary
					includeDirectories: #()
					requestor: nil.
				^VoidDescriptor voidObject
			 */
		}
	},


	/**
	 * <strong>Primitive 263:</strong> Create an entry point handle to deal with
	 * subsequent invocations of the already declared function with given name.
	 * The library does not yet have to be opened.  The resulting entry point
	 * handle encapsulates:
	 * <ol>
	 * <li>an ExternalProcedure</li>
	 * <li>a closureType</li>
	 * <li>the construct that encapsulates the library handle.</li>
	 * </ol>
	 */
	prim263_ExtractEntryPoint_handle_functionName(263, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| opaqueLibrary externals external argTypes argTypesTuple returnType opaqueEntryPoint |
				opaqueLibrary := openLibraries at: libraryHandle extractInt.
				externals := opaqueLibrary at: 1.
				external := externals at: functionName asNativeString asSymbol.
				external owner: ExternalInterface.
				argTypes := external type argumentTypes collect: [:argType |
					self convertExternalArgumentType: argType baseType].
				argTypesTuple := TupleDescriptor mutableObjectFromArray: argTypes.
				returnType := self
					convertExternalResultType: external type resultType baseType.
				opaqueEntryPoint := Array
					with: external
					with: (ClosureTypeDescriptor
						closureTypeForArgumentTypes: argTypesTuple
						returnType: returnType)
					with: opaqueLibrary.
				entryPoints add: opaqueEntryPoint.
				^IntegerDescriptor objectFromSmalltalkInteger: entryPoints size
			 */
		}
	},


	/**
	 * <strong>Primitive 264:</strong> Answer the closure type associated with
	 * the given entry point.
	 */
	prim264_EntryPointClosureType_entryPointHandle(264, 1, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| privateEntryPoint |
				privateEntryPoint := entryPoints at: entryPointHandle extractInt.
				^privateEntryPoint at: 2
			 */
		}
	},


	/**
	 * <strong>Primitive 265:</strong> Invoke the entry point associated with
	 * the given handle, using the specified arguments.
	 */
	prim265_InvokeEntryPoint_arguments(265, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| privateEntryPoint external externalType args result proc libraryArray externalLibrary procType resultType |
				privateEntryPoint := entryPoints at: entryPointHandle extractInt.
				external := privateEntryPoint at: 1.
				externalType := external type.
				args := (1 to: argumentsTuple tupleSize) collect: [:index |
					self
						convertArgument: (argumentsTuple tupleAt: index)
						toExternalArgumentType: (externalType argumentTypes at: index)].
				proc := privateEntryPoint at: 1.
				libraryArray := privateEntryPoint at: 3.
				externalLibrary := libraryArray at: 2.
				proc referentAddress isNil ifTrue: [
					| address |
					address := ExternalMethod mapAddressFor: external library: externalLibrary.
					address isNil ifTrue: [
						self error: 'No such entry point in library'].
					external referentAddress: address].
				procType := proc type baseType.
				resultType := procType resultType.
				result := ExternalMethod
					primCallC: external referentAddress
					specifierCallFlags: procType specifierCallFlags
					arguments: args
					argumentKinds: procType argumentKinds
					structArgSize: procType structArgumentSize
					structReturnSize: (resultType isComposite
						ifTrue: [
							resultType dataSize]
						ifFalse: [0])
					datumClass: (resultType isPointer
						ifTrue: [
							resultType referentType defaultPointerClass]
						ifFalse: [
							resultType defaultDatumClass])
					resultType: resultType.	"plus varargs when supported"
				^self convertExternalResult: result ofType: externalType resultType
			 */
		}
	},


	/**
	 * <strong>Primitive 266:</strong> Read an integer of the specified type
	 * from the memory location specified as an integer.
	 */
	prim266_IntegralType_from(266, 2, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| byteCount int |
				byteCount := (intType upperBound highBit + 7) // 8.
				int := byteCount halt.
				^IntegerDescriptor objectFromSmalltalkInteger: int
			 */
		}
	},


	/**
	 * <strong>Primitive 267:</strong> Write an integer of the specified type to
	 * the memory location specified as an integer.
	 */
	prim267_IntegralType_to_write(267, 3, CanInline, HasSideEffect)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| byteCount int |
				byteCount := (intType upperBound highBit + 7) // 8.
				int := intToWrite rawSmalltalkInteger.
				self halt.
				byteCount yourself.
				int yourself.
				^VoidDescriptor voidObject
			 */
		}
	},


	/**
	 * <strong>Primitive 268:</strong> Answer a boolean indicating if the
	 * current platform is big-endian.
	 */
	prim268_BigEndian(268, 0, CanInline)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 0;
			// PLATFORM-DEPENDENT -- rewrite more portably some day
			interpreter.primitiveResult(BooleanDescriptor.objectFrom(false));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 280:</strong> Add two floats.
	 */
	prim280_FloatAddition_a_b(280, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(FloatDescriptor.objectWithRecycling(
				(a.extractFloat() + b.extractFloat()),
				a,
				b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 281:</strong> Subtract float b from float a.
	 */
	prim281_FloatSubtraction_a_b(281, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(FloatDescriptor.objectWithRecycling(
				(a.extractFloat() - b.extractFloat()),
				a,
				b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 282:</strong> Multiply float a and float b.
	 */
	prim282_FloatMultiplication_a_b(282, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(FloatDescriptor.objectWithRecycling(
				(a.extractFloat() * b.extractFloat()),
				a,
				b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 283:</strong> Divide float a by float b.
	 */
	prim283_FloatDivision_a_b(283, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			if (b.extractFloat() == 0.0)
			{
				return FAILURE;
			}
			interpreter.primitiveResult(FloatDescriptor.objectWithRecycling(
				(a.extractFloat() / b.extractFloat()),
				a,
				b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 284:</strong> Compare float a < float b.  Answers an
	 * Avail boolean.
	 */
	prim284_FloatLessThan_a_b(284, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(
					(a.extractFloat() < b.extractFloat())));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 285:</strong> Compare float a <= float b.  Answers an
	 * Avail boolean.
	 */
	prim285_FloatLessOrEqual_a_b(285, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(
				BooleanDescriptor.objectFrom(
					(a.extractFloat() <= b.extractFloat())));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 286:</strong> Compute the natural logarithm of float a.
	 */
	prim286_FloatLn_a(286, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			interpreter.primitiveResult(
				FloatDescriptor.objectFromFloatRecycling(
					(float)log(a.extractFloat()),
					a));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 287:</strong> Compute e^a, the natural exponential of
	 * the float a.
	 */
	prim287_FloatExp_a(287, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			interpreter.primitiveResult(
				FloatDescriptor.objectFromFloatRecycling(
					(float)exp(a.extractFloat()),
					a));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 288:</strong> Divide float a by float b, but answer
	 * the remainder.
	 */
	prim288_FloatModulus_a_b(288, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			final float fa = a.extractFloat();
			final float fb = b.extractFloat();
			if (fb == 0.0f)
			{
				return FAILURE;
			}
			final float div = fa / fb;
			final float mod = fa - (float)floor(div) * fb;
			interpreter.primitiveResult(
				FloatDescriptor.objectWithRecycling(mod, a, b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 289:</strong> Convert a float to an integer, rounding
	 * towards zero.
	 */
	prim289_FloatTruncatedAsInteger_a(289, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top two 32-bit sections.  That guarantees 33 bits
			// of mantissa, which is more than a float actually captures.
			float f = a.extractFloat();
			if (f >= -0x80000000L && f <= 0x7FFFFFFFL)
			{
				// Common case -- it fits in an int.
				interpreter.primitiveResult(IntegerDescriptor.fromInt((int)f));
				return SUCCESS;
			}
			final boolean neg = f < 0.0f;
			f = abs(f);
			final int exponent = getExponent(f);
			final int slots = exponent + 31 / 32;  // probably needs work
			AvailObject out = IntegerDescriptor.mutable().create(
				slots);
			f = scalb(f, (1 - slots) * 32);
			for (int i = slots; i >= 1; --i)
			{
				final long intSlice = (int) f;
				out.rawUnsignedIntegerAtPut(i, (int)intSlice);
				f -= intSlice;
				f = scalb(f, 32);
			}
			out.trimExcessLongs();
			if (neg)
			{
				out = out.subtractFromIntegerCanDestroy(IntegerDescriptor.zero(), true);
			}
			interpreter.primitiveResult(out);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 290:</strong> Convert an integer to a float, failing
	 * if out of range.
	 */
	prim290_FloatFromInteger_a(290, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top 32 bits and the next-to-top 32 bits.  That guarantees 33 bits
			// of mantissa, which is more than a float actually captures.
			float f;
			final int size = a.integerSlotsCount();
			if (size == 1)
			{
				f = a.extractInt();
			}
			else
			{
				long highInt = a.rawUnsignedIntegerAt(size);
				long lowInt = a.rawUnsignedIntegerAt(size - 1);
				final boolean neg = (highInt & 0x80000000L) != 0;
				highInt = ~highInt;
				lowInt = ~lowInt;
				if ((int)++lowInt == 0)
				{
					highInt++;
				}
				f = scalb(lowInt, (size - 2) * 32);
				f += scalb(highInt, (size - 1) * 32);
				if (neg)
				{
					f = -f;
				}
			}
			interpreter.primitiveResult(FloatDescriptor.objectFromFloat(f));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 291:</strong> Compute a*(2**b) without intermediate
	 * overflow or any precision loss.
	 */
	prim291_FloatTimesTwoPower_a_b(291, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			long scale = b.extractInt();
			scale = max(scale, -0x80000000L);
			scale = min(scale, 0x7FFFFFFFL);
			final float f = scalb(a.extractFloat(), (int)scale);
			interpreter.primitiveResult(
				FloatDescriptor.objectFromFloatRecycling(f, a));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 310:</strong> Add two doubles.
	 */
	prim310_DoubleAddition_a_b(310, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(DoubleDescriptor.objectFromDoubleRecyclingOr(
				(a.extractDouble() + b.extractDouble()),
				a,
				b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 311:</strong> Subtract double b from double a.
	 */
	prim311_DoubleSubtraction_a_b(311, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(DoubleDescriptor.objectFromDoubleRecyclingOr(
				(a.extractDouble() - b.extractDouble()),
				a,
				b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 312:</strong> Multiply double a and double b.
	 */
	prim312_DoubleMultiplication_a_b(312, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(DoubleDescriptor.objectFromDoubleRecyclingOr(
				(a.extractDouble() * b.extractDouble()),
				a,
				b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 313:</strong> Divide double a by double b.
	 */
	prim313_DoubleDivision_a_b(313, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			if (b.extractDouble() == 0.0d)
			{
				return FAILURE;
			}
			interpreter.primitiveResult(DoubleDescriptor.objectFromDoubleRecyclingOr(
				(a.extractDouble() / b.extractDouble()),
				a,
				b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 314:</strong> Compare double a < double b.  Answers an
	 * Avail boolean.
	 */
	prim314_DoubleLessThan_a_b(314, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFrom((a.extractDouble() < b.extractDouble())));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 315:</strong> Compare double a <= double b.  Answers an
	 * Avail boolean.
	 */
	prim315_DoubleLessOrEqual_a_b(315, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFrom((a.extractDouble() <= b.extractDouble())));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 316:</strong> Compute the natural logarithm of the
	 * double a.
	 */
	prim316_DoubleLn_a(316, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDoubleRecycling(log(a.extractDouble()), a));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 317:</strong> Compute e^a, the natural exponential of
	 * the double a.
	 */
	prim317_DoubleExp_a(317, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDoubleRecycling(exp(a.extractDouble()), a));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 318:</strong> Divide double a by double b, but answer
	 * the remainder.
	 */
	prim318_DoubleModulus_a_b(318, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			final double da = a.extractDouble();
			final double db = b.extractDouble();
			if (db == 0.0d)
			{
				return FAILURE;
			}
			final double div = da / db;
			final double mod = da - floor(div) * db;
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDoubleRecyclingOr(mod, a, b));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 319:</strong> Convert a double to an integer, rounding
	 * towards zero.
	 */
	prim319_DoubleTruncatedAsInteger_a(319, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			final AvailObject a = args.get(0);
			assert args.size() == 1;
			// Extract the top three 32-bit sections.  That guarantees 65 bits
			// of mantissa, which is more than a double actually captures.
			double d = a.extractDouble();
			if (d >= -0x80000000L && d <= 0x7FFFFFFFL)
			{
				// Common case -- it fits in an int.
				interpreter.primitiveResult(IntegerDescriptor.fromInt((int)d));
				return SUCCESS;
			}
			final boolean neg = d < 0.0d;
			d = abs(d);
			final int exponent = getExponent(d);
			final int slots = exponent + 31 / 32;  // probably needs work
			AvailObject out = IntegerDescriptor.mutable().create(
				slots);
			d = scalb(d, (1 - slots) * 32);
			for (int i = slots; i >= 1; --i)
			{
				final long intSlice = (int) d;
				out.rawUnsignedIntegerAtPut(i, (int)intSlice);
				d -= intSlice;
				d = scalb(d, 32);
			}
			out.trimExcessLongs();
			if (neg)
			{
				out = out.subtractFromIntegerCanDestroy(IntegerDescriptor.zero(), true);
			}
			interpreter.primitiveResult(out);
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 320:</strong> Convert an integer to a double, failing
	 * if out of range.
	 */
	prim320_DoubleFromInteger_a(320, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top three 32-bit pieces.  That guarantees 65 bits
			// of mantissa, which is more than a double actually captures.
			double d;
			final int size = a.integerSlotsCount();
			if (size == 1)
			{
				d = a.extractInt();
			}
			else
			{
				long highInt = a.rawUnsignedIntegerAt(size);
				long nextInt = a.rawUnsignedIntegerAt(size - 1);
				long lowInt = size >= 3 ? a.rawUnsignedIntegerAt(size - 2) : 0;
				final boolean neg = (highInt & 0x80000000L) != 0;
				if (neg)
				{
					highInt = ~highInt;
					nextInt = ~nextInt;
					lowInt = ~lowInt;
					lowInt++;
					if ((int)lowInt == 0)
					{
						nextInt++;
						if ((int)nextInt == 0)
						{
							highInt++;
						}
					}
				}
				d = scalb(lowInt, (size - 3) * 32);
				d += scalb(nextInt, (size - 2) * 32);
				d += scalb(highInt, (size-1) * 32);
				if (neg)
				{
					d = -d;
				}
			}
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDouble(d));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 321:</strong> Compute the double a*(2**b) without
	 * intermediate overflow or any precision loss.
	 */
	prim321_DoubleTimesTwoPower_a_b(321, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			long scale = b.extractInt();
			scale = max(scale, -0x80000000L);
			scale = min(scale, 0x7FFFFFFFL);
			final double d = scalb(a.extractDouble(), (int)scale);
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDoubleRecycling(d, a));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 330:</strong> Extract the code point (integer) from a
	 * character.
	 */
	prim330_CharacterCodePoint_character(330, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject character = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.fromInt(character.codePoint()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 331:</strong> Convert a code point (integer) into a
	 * character.
	 */
	prim331_CharacterFromCodePoint_codePoint(331, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject codePoint = args.get(0);
			interpreter.primitiveResult(
				CharacterDescriptor.newImmutableCharacterWithCodePoint(codePoint.extractInt()));
			return SUCCESS;
		}
	},


	/**
	 * <strong>Primitive 340:</strong> The first literal is being returned.
	 * FAIL so that the non-primitive code will run instead -- otherwise we
	 * would have to figure out if we were running in level one or two, and if
	 * the latter, where do we even find the code object we're emulating?
	 * Easier to just fail.  But... the dynamic translator should still make a
	 * special effort to fold this.
	 */
	prim340_PushConstant_ignoreArgs(340, -1, SpecialReturnConstant)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			return FAILURE;
		}
	},

	/**
	 * <strong>Primitive 350:</strong>  Transform a variable reference and an
	 * expression into an assignment node.
	 */
	prim350_MacroAssignment_variable_expression(350, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject variable = args.get(0);
			final AvailObject expression = args.get(1);
			if (!variable.isInstanceOfSubtypeOf(VARIABLE_USE_NODE.o())
				|| !expression.isInstanceOfSubtypeOf(PARSE_NODE.o()))
			{
				return FAILURE;
			}
			if (!expression.expressionType().isSubtypeOf(
				variable.expressionType()))
			{
				return FAILURE;
			}
			final AvailObject assignment =
				AssignmentNodeDescriptor.mutable().create();
			assignment.variable(variable);
			assignment.expression(expression);
			interpreter.primitiveResult(assignment);
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 351:</strong>  Extract the result type of a parse node.
	 */
	prim351_ParseNodeExpressionType_parseNode(351, 2, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject parseNode = args.get(0);
			if (!parseNode.isInstanceOfSubtypeOf(PARSE_NODE.o()))
			{
				return FAILURE;
			}
			interpreter.primitiveResult(parseNode.expressionType());
			return SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 352:</strong>  Reject current macro substitution with
	 * the specified error string.
	 */
	prim352_RejectParsing_errorString(352, 1, CanFold)
	{
		@Override
		public Result attempt (
			final List<AvailObject> args,
			final Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject parseNode = args.get(0);
			if (!parseNode.isInstanceOfSubtypeOf(
					TupleTypeDescriptor.stringTupleType()))
			{
				return FAILURE;
			}
			throw new AvailRejectedParseException(parseNode);
		}
	};

	/**
	 * The success state of a primitive attempt.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum Result
	{
		/**
		 * The primitive succeeded, and the result, if any, has been stored for
		 * subsequent use.
		 */
		SUCCESS,

		/**
		 * The primitive failed.  The backup Avail code should be executed
		 * instead.
		 */
		FAILURE,

		/**
		 * The continuation was replaced as a consequence of the primitive.
		 * This is a specific form of success, but no result can be produced due
		 * to the fact that the new continuation does not have a place to write
		 * it.
		 */
		CONTINUATION_CHANGED,

		/**
		 * The current process has been suspended as a consequence of this
		 * primitive executing, so the {@link Interpreter interpreter}
		 * should switch processes now.
		 */
		SUSPENDED;
	};

	/**
	 * These flags are used by the execution machinery and optimizer to indicate
	 * the potential mischief that the corresponding primitives may get into.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum Flag
	{
		/**
		 * The primitive can be attempted by the {@code L2Translator Level Two
		 * translator} at re-optimization time if the arguments are known
		 * constants.  The result should be stable, such that invoking the
		 * primitive again with the same arguments should produce the same
		 * value.  The primitive should not have side-effects.
		 */
		CanFold,

		/**
		 * The primitive can be safely inlined.  In particular, it simply
		 * computes a value or changes the state of something and does not
		 * replace the current continuation in unusual ways.  Thus, it is
		 * suitable for directly embedding in {@link L2Interpreter Level Two}
		 * code by the {@link L2Translator Level Two translator}, without the
		 * need to reify the current continuation.
		 *
		 * <p>The primitive may still fail at runtime, but that's dealt with by
		 * a conditional branch in the {@link L2AttemptPrimitiveInstruction
		 * attempt-primitive wordcode} itself.
		 */
		CanInline,

		/**
		 * UNUSED
		 *
		 * The primitive has a side-effect, such as writing to a file, modifying
		 * a variable, or defining a new method.
		 */
		HasSideEffect,

		/**
		 * UNUSED
		 *
		 * The primitive can invoke a closure.  If the closure is a
		 * non-primitive (or a primitive that fails), the current continuation
		 * must be reified before the call.
		 */
		Invokes,

		/**
		 * UNUSED
		 *
		 * The primitive can replace the current continuation, and care should
		 * be taken to ensure the current continuation is fully reified prior to
		 * attempting this primitive.
		 */
		SwitchesContinuation,

		/**
		 * The primitive returns some constant.  Currently this is only used for
		 * {@link Primitive#prim340_PushConstant_ignoreArgs primitive 340},
		 * which always returns the first literal of the {@link
		 * CompiledCodeDescriptor compiled code}.
		 */
		SpecialReturnConstant,

		/**
		 * The semantics of the primitive fall outside the usual capacity of the
		 * {@link L2Translator Level Two translator}.  The current continuation
		 * should be reified prior to attempting the primitive.  Do not attempt
		 * to fold or inline this primitive.
		 */
		Unknown
	};


	/**
	 * Attempt this primitive with the given arguments, and the {@link
	 * Interpreter interpreter} on whose behalf to attempt the primitive.
	 * If the primitive fails, it should return {@link Result#FAILURE}.
	 * Otherwise it should set the interpreter's {@link
	 * Interpreter#primitiveResult(AvailObject)} and answer either {@link
	 * Result#SUCCESS}.  For unusual primitives that replace the current
	 * continuation, {@link Result#CONTINUATION_CHANGED} is more appropriate,
	 * and the primitiveResult need not be set.  For primitives that need to
	 * cause a context switch, {@link Result#SUSPENDED} should be returned.
	 *
	 * @param args The {@link List list} of arguments to the primitive.
	 * @param interpreter The {@link Interpreter} that is executing.
	 * @return The {@link Result} code indicating success or failure (or special
	 *         circumstance).
	 */
	public abstract Result attempt (List<AvailObject> args, Interpreter interpreter);


	/**
	 * This primitive's number.  The Avail source code refers to this primitive
	 * by number.
	 */
	public final short primitiveNumber;


	/**
	 * The number of arguments this primitive expects.
	 */
	final int argCount;


	/**
	 * The flags that indicate to the {@link L2Translator} how an invocation of
	 * this primitive should be handled.
	 */
	private final EnumSet<Flag> primitiveFlags;


	/**
	 * Test whether the specified {@link Flag} is set for this primitive.
	 *
	 * @param flag The {@code Flag} to test.
	 * @return Whether that {@code Flag} is set for this primitive.
	 */
	public boolean hasFlag(final Flag flag)
	{
		return primitiveFlags.contains(flag);
	}


	/**
	 * The number of arguments this primitive expects.
	 *
	 * @return The count of arguments for this primitive.
	 */
	public int argCount()
	{
		return argCount;
	}


	/**
	 * This static inner class definition forces the ClassLoader to make this
	 * field available at load time.  If we tried to define the static field
	 * in the parent class {@link Primitive}, it would not be accessible until
	 * <em>after</em> the enumeration values had been constructed.
	 */
	private static class PrimitiveCounter
	{
		/**
		 * The maximum allowed primitive number.
		 */
		public static short maxPrimitiveNumber = Short.MIN_VALUE;
	}


	/**
	 * An array of all primitives, indexed by primitive number.
	 */
	private static final Primitive[] byPrimitiveNumber;


	// The enumeration values have been initialized, which means
	// PrimitiveCounter#maxPrimitiveNumber has been computed.  Create a suitable
	// size array and populate it with the primitive enumeration values.
	static
	{
		byPrimitiveNumber = new Primitive[PrimitiveCounter.maxPrimitiveNumber];
		for (final Primitive prim : values())
		{
			assert byPrimitiveNumber[prim.primitiveNumber - 1] == null;
			byPrimitiveNumber[prim.primitiveNumber - 1] = prim;
		}
	}


	/**
	 * Locate the primitive that has the specified primitive number.
	 *
	 * @param primitiveNumber The primitive number for which to search.
	 * @return The primitive with the specified primitive number.
	 */
	public static Primitive byPrimitiveNumber(final short primitiveNumber)
	{
		return byPrimitiveNumber[primitiveNumber - 1];
	}


	/**
	 * Construct a new {@link Primitive}.  The first argument is a primitive
	 * number, the second is the number of arguments with which the primitive
	 * expects to be invoked, and the remaining arguments are {@link Flag
	 * flags}.
	 *
	 * @param primitiveNumber The primitive number being defined.
	 * @param argCount The number of arguments the primitive expects.
	 * @param flags The flags that describe how the {@link L2Translator} should
	 *              deal with this primitive.
	 */
	private Primitive (final int primitiveNumber, final int argCount, final Flag ... flags)
	{
		this.primitiveNumber = (short)primitiveNumber;
		this.argCount = argCount;
		this.primitiveFlags = EnumSet.noneOf(Flag.class);
		assert name().matches("prim" + primitiveNumber + "_.*");
		for (final Flag flag : flags)
		{
			this.primitiveFlags.add(flag);
		}

		if (this.primitiveNumber > PrimitiveCounter.maxPrimitiveNumber)
		{
			PrimitiveCounter.maxPrimitiveNumber = this.primitiveNumber;
		}
	}
}
