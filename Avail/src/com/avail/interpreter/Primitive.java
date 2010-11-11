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

import static com.avail.descriptor.AvailObject.CanAllocateObjects;
import static com.avail.descriptor.AvailObject.error;
import static java.lang.Math.abs;
import static java.lang.Math.exp;
import static java.lang.Math.floor;
import static java.lang.Math.getExponent;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.scalb;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BooleanDescriptor;
import com.avail.descriptor.CharacterDescriptor;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.descriptor.ClosureTypeDescriptor;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.ConcatenatedTupleTypeDescriptor;
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.ContainerTypeDescriptor;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.ContinuationTypeDescriptor;
import com.avail.descriptor.CyclicTypeDescriptor;
import com.avail.descriptor.DoubleDescriptor;
import com.avail.descriptor.FloatDescriptor;
import com.avail.descriptor.GeneralizedClosureTypeDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.descriptor.ListTypeDescriptor;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.MapTypeDescriptor;
import com.avail.descriptor.ObjectDescriptor;
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.ObjectTypeDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.SetTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;


public enum Primitive
{
	prim1_Addition_a_b(1, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Add two extended integers together.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(a.plusCanDestroy(b, true));
			return Result.SUCCESS;
		}
	},


	prim2_Subtraction_a_b(2, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Subtract b from a.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(a.minusCanDestroy(b, true));
			return Result.SUCCESS;
		}
	},


	prim3_Multiplication_a_b(3, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Multiply a and b.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(a.timesCanDestroy(b, true));
			return Result.SUCCESS;
		}
	},


	prim4_Division_a_b(4, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compute a divided by b.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			if (b.equals(IntegerDescriptor.zero()))
			{
				return Result.FAILURE;
			}
			interpreter.primitiveResult(a.divideCanDestroy(b, true));
			return Result.SUCCESS;
		}
	},


	prim5_LessThan_a_b(5, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compare for a < b.  Answers an Avail boolean.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(a.lessThan(b)));
			return Result.SUCCESS;
		}
	},


	prim6_LessOrEqual_a_b(6, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compare for a <= b.  Answers an Avail boolean.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(a.lessOrEqual(b)));
			return Result.SUCCESS;
		}
	},


	prim7_CreateIntegerRange_min_minInc_max_maxInc(7, 4, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the integer range 'min <?1 instance <?2 max', where <?1 is '<=' if minInc=true, else '<', and likewise for <?2.

			assert args.size() == 4;
			final AvailObject min = args.get(0);
			final AvailObject minInc = args.get(1);
			final AvailObject max = args.get(2);
			final AvailObject maxInc = args.get(3);
			interpreter.primitiveResult(IntegerRangeTypeDescriptor.lowerBoundInclusiveUpperBoundInclusive(
				min,
				minInc.extractBoolean(),
				max,
				maxInc.extractBoolean()));
			return Result.SUCCESS;
		}
	},


	prim8_LowerBound_range(8, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the lower bound.  Test membership to determine if it's inclusive or exclusive.

			assert args.size() == 1;
			final AvailObject range = args.get(0);
			interpreter.primitiveResult(range.lowerBound());
			return Result.SUCCESS;
		}
	},


	prim9_UpperBound_range(9, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the upper bound.  Test membership to determine if it's inclusive or exclusive.

			assert args.size() == 1;
			final AvailObject range = args.get(0);
			interpreter.primitiveResult(range.upperBound());
			return Result.SUCCESS;
		}
	},


	prim10_GetValue_var(10, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  There are two possiblities.  The container is mutable, in which case we want to
			//  destroy it, or the container is immutable, in which case we want to make sure the
			//  extracted value becomes immutable (in case the container is being held onto
			//  by something.  Since the primitive invocation code is going to erase it if it's
			//  mutable anyhow, only the second case requires any real work.

			assert args.size() == 1;
			final AvailObject var = args.get(0);
			final AvailObject value = var.getValue();
			if (! var.descriptor().isMutable())
			{
				value.makeImmutable();
			}
			interpreter.primitiveResult(value);
			return Result.SUCCESS;
		}
	},


	prim11_SetValue_var_value(11, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Assign the value to the variable.

			assert args.size() == 2;
			final AvailObject var = args.get(0);
			final AvailObject value = args.get(1);
			var.setValue(value);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim12_ClearValue_var(12, 1, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Clear the variable.

			assert args.size() == 1;
			final AvailObject var = args.get(0);
			var.clearValue();
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim13_CreateContainerType_type(13, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Create a container type using the given inner type.

			assert args.size() == 1;
			final AvailObject type = args.get(0);
			interpreter.primitiveResult(ContainerTypeDescriptor.containerTypeForInnerType(type));
			return Result.SUCCESS;
		}
	},


	prim14_InnerType_type(14, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Extract the inner type of a container type.

			assert args.size() == 1;
			final AvailObject type = args.get(0);
			interpreter.primitiveResult(type.innerType());
			return Result.SUCCESS;
		}
	},


	prim15_Swap_var1_var2(15, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Swap the contents of two containers.

			assert args.size() == 2;
			final AvailObject var1 = args.get(0);
			final AvailObject var2 = args.get(1);
			final AvailObject tempObject = var1.getValue();
			var1.setValue(var2.getValue());
			var2.setValue(tempObject);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim16_CreateContainer_innerType(16, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Create a container with the given inner type.

			assert args.size() == 1;
			final AvailObject innerType = args.get(0);
			interpreter.primitiveResult(ContainerDescriptor.newContainerWithInnerType(innerType));
			return Result.SUCCESS;
		}
	},


	prim17_HasNoValue_var(17, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer true if the variable is unassigned (has no value).

			assert args.size() == 1;
			final AvailObject var = args.get(0);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(var.value().equalsVoid()));
			return Result.SUCCESS;
		}
	},


	prim18_GetClearing_var(18, 1, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Get the value of the variable, clear the variable, then answer the previously extracted
			//  value.  This operation allows store-back patterns to be efficiently implemented in
			//  Level One code while keeping the interpreter itself thread-safe and debugger-safe.

			assert args.size() == 1;
			final AvailObject var = args.get(0);
			final AvailObject valueObject = var.getValue();
			var.clearValue();
			interpreter.primitiveResult(valueObject);
			return Result.SUCCESS;
		}
	},


	prim20_GetPriority_processObject(20, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Get the priority of the given process.

			assert args.size() == 1;
			final AvailObject processObject = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(processObject.priority()));
			return Result.SUCCESS;
		}
	},


	prim21_SetPriority_processObject_newPriority(21, 2, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Set the priority of the given process.

			assert args.size() == 2;
			final AvailObject processObject = args.get(0);
			final AvailObject newPriority = args.get(1);
			processObject.priority(newPriority.extractInt());
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim22_Suspend_processObject(22, 1, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Suspend the given process.  Ignore if the process is already suspended.

			assert args.size() == 1;
			@SuppressWarnings("unused") final AvailObject processObject = args.get(0);
			error("process suspend is not yet implemented");
			return Result.FAILURE;
		}
	},


	prim23_Resume_processObject(23, 1, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Resume the given process.  Ignore if the process is already running.

			assert args.size() == 1;
			@SuppressWarnings("unused") final AvailObject processObject = args.get(0);
			error("process resume is not yet implemented");
			return Result.FAILURE;
		}
	},


	prim24_Terminate_processObject(24, 1, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Terminate the given process.  Ignore if the process is already terminated.

			assert args.size() == 1;
			@SuppressWarnings("unused") final AvailObject processObject = args.get(0);
			error("process terminate is not yet implemented");
			return Result.FAILURE;
		}
	},


	prim25_CurrentProcess(25, 0, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the currently running process.

			assert args.size() == 0;
			interpreter.primitiveResult(interpreter.process().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim26_LookupProcessVariable_processObject_key(26, 2, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Lookup the given name (key) in the variables of the given process.

			assert args.size() == 2;
			final AvailObject processObject = args.get(0);
			final AvailObject key = args.get(1);
			interpreter.primitiveResult(processObject.processGlobals().mapAt(key).makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim27_SetProcessVariable_processObject_key_value(27, 3, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Associate the given value with the given name (key) in the variables of the given process.

			assert args.size() == 3;
			final AvailObject processObject = args.get(0);
			final AvailObject key = args.get(1);
			final AvailObject value = args.get(2);
			processObject.processGlobals(processObject.processGlobals().mapAtPuttingCanDestroy(
				key.makeImmutable(),
				value.makeImmutable(),
				true));
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim28_SemaphoreWait_semaphore(28, 1, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Wait for the given semaphore.

			assert args.size() == 1;
			@SuppressWarnings("unused") final AvailObject semaphore = args.get(0);
			error("This semaphore primitive is not yet implemented");
			return Result.FAILURE;
		}
	},


	prim29_SemaphoreSignal_semaphore(29, 1, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Signal the given semaphore.

			assert args.size() == 1;
			@SuppressWarnings("unused") final AvailObject semaphore = args.get(0);
			error("This semaphore primitive is not yet implemented");
			return Result.FAILURE;
		}
	},


	prim30_Type_value(30, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the type of the given object.

			assert args.size() == 1;
			final AvailObject value = args.get(0);
			interpreter.primitiveResult(value.type());
			return Result.SUCCESS;
		}
	},


	prim31_TypeUnion_type1_type2(31, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  This eventually needs to be rewritten as an invocation of typeUnion:canDestroy:.  For now make result immutable.

			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			interpreter.primitiveResult(type1.typeUnion(type2).makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim32_TypeIntersection_type1_type2(32, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  This eventually needs to be rewritten as an invocation of typeIntersection:canDestroy:.  For now make result immutable.

			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			interpreter.primitiveResult(type1.typeIntersection(type2).makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim33_IsSubtypeOf_type1_type2(33, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer whether type1 is a subtype of type2 (or equal).

			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(type1.isSubtypeOf(type2)));
			return Result.SUCCESS;
		}
	},


	prim34_CreateClosureType_argTypes_returnType(34, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer whether type1 is a subtype of type2 (or equal).

			assert args.size() == 2;
			final AvailObject argTypes = args.get(0);
			final AvailObject returnType = args.get(1);
			for (int i = 1, _end1 = argTypes.tupleSize(); i <= _end1; i++)
			{
				assert argTypes.tupleAt(i).isInstanceOfSubtypeOf(TypeDescriptor.type());
			}
			assert returnType.isInstanceOfSubtypeOf(TypeDescriptor.type());
			interpreter.primitiveResult(ClosureTypeDescriptor.closureTypeForArgumentTypesReturnType(argTypes, returnType));
			return Result.SUCCESS;
		}
	},


	prim35_ClosureTypeNumArgs_closureType(35, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the number af arguments that this closureType takes.

			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(closureType.numArgs()));
			return Result.SUCCESS;
		}
	},


	prim36_ArgTypeAt_closureType_index(36, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the type of the argument at the given index within the given closureType.

			assert args.size() == 2;
			final AvailObject closureType = args.get(0);
			final AvailObject index = args.get(1);
			interpreter.primitiveResult(closureType.argTypeAt(index.extractInt()));
			return Result.SUCCESS;
		}
	},


	prim37_ReturnType_closureType(37, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the return type of the given closureType.

			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			interpreter.primitiveResult(closureType.returnType());
			return Result.SUCCESS;
		}
	},


	prim38_UnionOfTupleOfTypes_tupleOfTypes(38, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the union of the types in the given tuple of types.

			assert args.size() == 1;
			final AvailObject tupleOfTypes = args.get(0);
			AvailObject unionObject = TypeDescriptor.terminates();
			for (int i = 1, _end2 = tupleOfTypes.tupleSize(); i <= _end2; i++)
			{
				unionObject = unionObject.typeUnion(tupleOfTypes.tupleAt(i));
			}
			interpreter.primitiveResult(unionObject);
			return Result.SUCCESS;
		}
	},


	prim39_CreateGeneralizedClosureType_returnType(39, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a generalized closure type with the given return type.

			assert args.size() == 1;
			final AvailObject returnType = args.get(0);
			interpreter.primitiveResult(GeneralizedClosureTypeDescriptor.generalizedClosureTypeForReturnType(returnType));
			return Result.SUCCESS;
		}
	},


	prim40_InvokeWithTuple_block_argTuple(40, 2, Flag.Invokes)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Block evaluation, given a tuple of args.  Check the types dynamically to
			//  prevent corruption of type system.  Fail if types won't fit.

			assert args.size() == 2;
			final AvailObject block = args.get(0);
			final AvailObject argTuple = args.get(1);
			AvailObject blockType = block.type();
			int numArgs = argTuple.tupleSize();
			if (blockType.numArgs() != numArgs)
			{
				return Result.FAILURE;
			}
			List<AvailObject> callArgs = new ArrayList<AvailObject>(numArgs);
			for (int i = 1; i <= numArgs; i++)
			{
				final AvailObject anArg = argTuple.tupleAt(i);
				if (! anArg.isInstanceOfSubtypeOf(blockType.argTypeAt(i)))
				{
					return Result.FAILURE;
				}
				//  Transfer the argument into callArgs.
				callArgs.add(anArg);
			}
			return interpreter.invokeClosureArguments(
				block,
				callArgs);
		}
	},


	prim41_InvokeZeroArgs_block(41, 1, Flag.Invokes)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Block evaluation with no arguments.  Don't be confused by the
			//  primArgs parameter which contain the arguments to this very
			//  primitive - i.e., an Array with one item, the block.

			assert args.size() == 1;
			final AvailObject block = args.get(0);
			assert block.type().numArgs() == 0;
			return interpreter.invokeClosureArguments (
				block,
				Arrays.<AvailObject>asList());
		}
	},


	prim42_InvokeOneArg_block_arg1(42, 2, Flag.Invokes)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Block evaluation with one argument.

			assert args.size() == 2;
			final AvailObject block = args.get(0);
			final AvailObject arg1 = args.get(1);
			AvailObject blockType = block.type();
			assert blockType.numArgs() == 1;
			if (! arg1.isInstanceOfSubtypeOf(blockType.argTypeAt(1)))
			{
				return Result.FAILURE;
			}
			args.set(1, VoidDescriptor.voidObject());   // in case we ever add destruction code
			return interpreter.invokeClosureArguments (
				block,
				Arrays.<AvailObject>asList(arg1));
		}
	},


	prim43_IfThenElse_aBoolean_trueBlock_falseBlock(43, 3, Flag.Invokes)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Invoke either the trueBlock or the falseBlock, depending on aBoolean.

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


	prim44_IfThen_aBoolean_trueBlock(44, 2, Flag.Invokes)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Invoke the trueBlock if aBoolean is true, otherwise just answer void.

			assert args.size() == 2;
			final AvailObject aBoolean = args.get(0);
			final AvailObject trueBlock = args.get(1);
			assert(trueBlock.type().numArgs() == 0);
			if (aBoolean.extractBoolean())
			{
				return interpreter.invokeClosureArguments (
					trueBlock,
					args);
			}
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim45_ShortCircuitHelper_ignoredBool_block(45, 2, Flag.Invokes)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Run the block, ignoring the leading boolean argument.  This is used for short-circuit evaluation.

			assert args.size() == 2;
			@SuppressWarnings("unused") final AvailObject ignoredBool = args.get(0);
			final AvailObject block = args.get(1);
			assert(block.type().numArgs() == 0);
			return interpreter.invokeClosureArguments (
				block,
				args);
		}
	},


	prim46_TupleTypeToListType_tupleType(46, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert this tupleType into its associated listType.

			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			interpreter.primitiveResult(ListTypeDescriptor.listTypeForTupleType(tupleType));
			return Result.SUCCESS;
		}
	},


	prim47_ListToTuple_list(47, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the tuple extracted from the list.

			assert args.size() == 1;
			final AvailObject list = args.get(0);
			final AvailObject tupleObject = list.tuple();
			if (list.descriptor().isMutable())
			{
				list.tuple(VoidDescriptor.voidObject());
			}
			interpreter.primitiveResult(tupleObject);
			return Result.SUCCESS;
		}
	},


	prim48_ListTypeToTupleType_listType(48, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert this listType into its associated tupleType.

			assert args.size() == 1;
			final AvailObject listType = args.get(0);
			interpreter.primitiveResult(listType.tupleType());
			return Result.SUCCESS;
		}
	},


	prim49_CreateContinuation_callerHolder_closure_pc_stackp_stack(49, 5, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Create a continuation.  Don't allow anything about level two to be mentioned.

			assert args.size() == 5;
			final AvailObject callerHolder = args.get(0);
			final AvailObject closure = args.get(1);
			final AvailObject pc = args.get(2);
			final AvailObject stackp = args.get(3);
			final AvailObject stack = args.get(4);
			final AvailObject theCode = closure.code();
			final AvailObject cont = AvailObject.newIndexedDescriptor(theCode.numArgsAndLocalsAndStack(), ContinuationDescriptor.mutableDescriptor());
			cont.caller(callerHolder.value());
			cont.closure(closure);
			cont.pc(pc.extractInt());
			cont.stackp(stackp.extractInt());
			cont.levelTwoChunkIndexOffset(L2ChunkDescriptor.indexOfUnoptimizedChunk(), L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
			for (int i = 1, _end3 = stack.tupleSize(); i <= _end3; i++)
			{
				cont.localOrArgOrStackAtPut(i, stack.tupleAt(i));
			}
			interpreter.primitiveResult(cont);
			return Result.SUCCESS;
		}
	},


	prim50_ContinuationTypeToClosureType_continuationType(50, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the closure type within the given continuation type.

			assert args.size() == 1;
			final AvailObject continuationType = args.get(0);
			interpreter.primitiveResult(continuationType.closureType());
			return Result.SUCCESS;
		}
	},


	prim51_ClosureTypeToContinuationType_closureType(51, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a continuation type that uses the given closure type.

			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			interpreter.primitiveResult(ContinuationTypeDescriptor.continuationTypeForClosureType(closureType));
			return Result.SUCCESS;
		}
	},


	prim52_ContinuationCaller_con(52, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the caller of a continuation.  Fail if there is no caller.

			assert args.size() == 1;
			final AvailObject con = args.get(0);
			final AvailObject caller = con.caller();
			if (caller.equalsVoid())
			{
				return Result.FAILURE;
			}
			interpreter.primitiveResult(caller);
			return Result.SUCCESS;
		}
	},


	prim53_ContinuationClosure_con(53, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the closure of a continuation.

			assert args.size() == 1;
			final AvailObject con = args.get(0);
			interpreter.primitiveResult(con.closure());
			return Result.SUCCESS;
		}
	},


	prim54_ContinuationPC_con(54, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the pc of a continuation.

			assert args.size() == 1;
			final AvailObject con = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(con.pc()));
			return Result.SUCCESS;
		}
	},


	prim55_ContinuationStackPointer_con(55, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a continuation's stack pointer.

			assert args.size() == 1;
			final AvailObject con = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(con.stackp()));
			return Result.SUCCESS;
		}
	},


	prim56_RestartContinuationWithArguments_con_arguments(56, 2, Flag.SwitchesContinuation)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Restart the given continuation, but passing in the given tuple of arguments.  Make sure it's
			//  a label-like continuation rather than a call-like, because a call-like continuation requires a
			//  value to be stored on its stack in order to resume it.  Fail if the continuation's closure is not
			//  capable of accepting the given arguments.

			assert args.size() == 2;
			final AvailObject con = args.get(0);
			final AvailObject arguments = args.get(1);
			assert (con.stackp() == (con.objectSlotsCount() + 1)) : "Outer continuation should have been a label- rather than call- continuation";
			assert (con.pc() == 1) : "Labels must only occur at the start of a block.  Only restart that kind of continuation.";
			//  The arguments will be referenced by the continuation.
			//
			//  No need to make it immutable because current continuation's reference is lost by this.  We go ahead
			//  and make a mutable copy (if necessary) because the interpreter requires the current continuation to
			//  always be mutable...
			final AvailObject conCopy = con.ensureMutable();
			final AvailObject itsCode = conCopy.closure().code();
			if (! arguments.isTuple())
			{
				return Result.FAILURE;
			}
			if (! (itsCode.numArgs() == arguments.tupleSize()))
			{
				return Result.FAILURE;
			}
			if (! itsCode.closureType().acceptsTupleOfArguments(arguments))
			{
				return Result.FAILURE;
			}
			for (int i = 1, _end4 = itsCode.numArgs(); i <= _end4; i++)
			{
				conCopy.localOrArgOrStackAtPut(i, arguments.tupleAt(i));
			}
			for (int i = 1, _end5 = itsCode.numLocals(); i <= _end5; i++)
			{
				conCopy.localOrArgOrStackAtPut((itsCode.numArgs() + i), ContainerDescriptor.newContainerWithOuterType(itsCode.localTypeAt(i)));
			}
			interpreter.prepareToExecuteContinuation(conCopy);
			return Result.CONTINUATION_CHANGED;
		}
	},


	prim57_ExitContinuationWithResult_con_result(57, 2, Flag.SwitchesContinuation)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Exit the given continuation (returning result to its caller).

			assert args.size() == 2;
			final AvailObject con = args.get(0);
			final AvailObject result = args.get(1);
			assert (con.stackp() == (con.objectSlotsCount() + 1)) : "Outer continuation should have been a label- rather than call- continuation";
			assert (con.pc() == 1) : "Labels must only occur at the start of a block.  Only exit that kind of continuation.";
			//  No need to make it immutable because current continuation's reference is lost by this.  We go ahead
			//  and make a mutable copy (if necessary) because the interpreter requires the current continuation to
			//  always be mutable...
			final AvailObject expectedType = con.closure().type().returnType();
			if (! result.isInstanceOfSubtypeOf(expectedType))
			{
				return Result.FAILURE;
			}
			final AvailObject targetCon = con.caller().ensureMutable();
			targetCon.stackAtPut(targetCon.stackp(), result);
			interpreter.prepareToExecuteContinuation(targetCon);
			return Result.CONTINUATION_CHANGED;
		}
	},


	prim58_RestartContinuation_con(58, 1, Flag.SwitchesContinuation)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Restart the given continuation.  Make sure it's a label-like continuation rather than a call-like,
			//  because a call-like continuation requires a value to be stored on its stack in order to resume it.

			assert args.size() == 1;
			final AvailObject con = args.get(0);
			assert (con.stackp() == (con.objectSlotsCount() + 1)) : "Outer continuation should have been a label- rather than call- continuation";
			assert (con.pc() == 1) : "Labels must only occur at the start of a block.  Only restart that kind of continuation.";
			//  Funny twist - destroy previous continuation in place of one being restarted
			//
			//  No need to make it immutable because current continuation's reference is lost by this.  We go ahead
			//  and make a mutable copy (if necessary) because the interpreter requires the current continuation to
			//  always be mutable...
			final AvailObject conCopy = con.ensureMutable();
			final AvailObject itsCode = conCopy.closure().code();
			for (int i = 1, _end6 = itsCode.numLocals(); i <= _end6; i++)
			{
				conCopy.localOrArgOrStackAtPut((itsCode.numArgs() + i), ContainerDescriptor.newContainerWithOuterType(itsCode.localTypeAt(i)));
			}
			interpreter.prepareToExecuteContinuation(conCopy);
			return Result.CONTINUATION_CHANGED;
		}
	},


	prim59_ContinuationStackData_con(59, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a tuple containing the continuation's stack data.

			assert args.size() == 1;
			final AvailObject con = args.get(0);
			final short count = con.closure().code().numArgsAndLocalsAndStack();
			final AvailObject tuple = AvailObject.newIndexedDescriptor(count, ObjectTupleDescriptor.mutableDescriptor());
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
			return Result.SUCCESS;
		}
	},


	prim60_Equality_a_b(60, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compare for equality.  Answer is an Avail boolean.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(a.equals(b)));
			return Result.SUCCESS;
		}
	},


	prim61_MapToObject_map(61, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert a map into an object.

			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(ObjectDescriptor.objectFromMap(map));
			return Result.SUCCESS;
		}
	},


	prim62_ObjectToMap_object(62, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert an object into a map.

			assert args.size() == 1;
			final AvailObject object = args.get(0);
			interpreter.primitiveResult(object.fieldMap());
			return Result.SUCCESS;
		}
	},


	prim63_MapToObjectType_map(63, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert a map into an object type.

			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(ObjectTypeDescriptor.objectTypeFromMap(map));
			return Result.SUCCESS;
		}
	},


	prim64_ObjectTypeToMap_objectType(64, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert an object type into a map.

			assert args.size() == 1;
			final AvailObject objectType = args.get(0);
			interpreter.primitiveResult(objectType.fieldTypeMap());
			return Result.SUCCESS;
		}
	},


	prim65_ObjectMetaInstance_objectMeta(65, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Extract an objectType from its type, an objectMeta.

			assert args.size() == 1;
			final AvailObject objectMeta = args.get(0);
			interpreter.primitiveResult(objectMeta.instance());
			return Result.SUCCESS;
		}
	},


	prim66_ObjectMetaMetaInstance_objectMetaMeta(66, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Extract an objectMeta from its type, an objectMetaMeta.

			assert args.size() == 1;
			final AvailObject objectMetaMeta = args.get(0);
			interpreter.primitiveResult(objectMetaMeta.instance());
			return Result.SUCCESS;
		}
	},


	prim67_NameOfPrimitiveType_primType(67, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the name of a primitive type.

			assert args.size() == 1;
			final AvailObject primType = args.get(0);
			interpreter.primitiveResult(primType.name());
			return Result.SUCCESS;
		}
	},


	prim68_RecordNewTypeName_userType_name(68, 2, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Record a name for this user-defined type.  This can be useful for debugging.

			assert args.size() == 2;
			final AvailObject userType = args.get(0);
			final AvailObject name = args.get(1);
			userType.makeImmutable();
			name.makeImmutable();
			interpreter.typeNames(
				interpreter.typeNames().mapAtPuttingCanDestroy(userType, name, true));
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim70_CreateConstantBlock_numArgs_constantResult(70, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Construct a block taking numArgs arguments (each of type all) and returning constantResult.

			assert args.size() == 2;
			final AvailObject numArgs = args.get(0);
			final AvailObject constantResult = args.get(1);
			interpreter.primitiveResult(ClosureDescriptor.newStubForNumArgsConstantResult(numArgs.extractInt(), constantResult));
			return Result.SUCCESS;
		}
	},


	prim71_CreateStubInvokingWithFirstArgAndCallArgsAsList_argTypes_message_firstArg_resultType(71, 4, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Construct a block that takes arguments whose types are specified in argTypes, and returns the
			//  result of invoking the given message with firstArg as the first argument and the list (not tuple)
			//  of arguments as the second argument.  Assume the argument types have already been tried
			//  in each applicable requiresBlock, and that the result type agrees with each returnsBlock.

			assert args.size() == 4;
			final AvailObject argTypes = args.get(0);
			final AvailObject message = args.get(1);
			final AvailObject firstArg = args.get(2);
			final AvailObject resultType = args.get(3);
			interpreter.primitiveResult(ClosureDescriptor.newStubCollectingArgsWithTypesIntoAListAndSendingImplementationSetFirstArgumentResultType(
				argTypes,
				interpreter.methodsAt(message),
				firstArg,
				resultType));
			return Result.SUCCESS;
		}
	},


	prim72_CompiledCodeOfClosure_aClosure(72, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the compiledCode within this closure.

			assert args.size() == 1;
			final AvailObject aClosure = args.get(0);
			interpreter.primitiveResult(aClosure.code());
			return Result.SUCCESS;
		}
	},


	prim73_OuterVariables_aClosure(73, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the tuple of outer variables captured by this closure.

			assert args.size() == 1;
			final AvailObject aClosure = args.get(0);
			final AvailObject newTupleObject = AvailObject.newIndexedDescriptor(aClosure.numOuterVars(), ObjectTupleDescriptor.mutableDescriptor());
			newTupleObject.hashOrZero(0);
			CanAllocateObjects(false);
			for (int i = 1, _end7 = aClosure.numOuterVars(); i <= _end7; i++)
			{
				final AvailObject outer = aClosure.outerVarAt(i);
				if ((outer.equalsVoid() || outer.isList()))
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
			return Result.SUCCESS;
		}
	},


	prim74_CreateClosure_compiledCode_outers(74, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a closure built from the compiledCode and the outer variables.

			assert args.size() == 2;
			final AvailObject compiledCode = args.get(0);
			final AvailObject outers = args.get(1);
			interpreter.primitiveResult(ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple(compiledCode, outers));
			return Result.SUCCESS;
		}
	},


	prim80_MapSize_map(80, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the size of the map.

			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(map.mapSize()));
			return Result.SUCCESS;
		}
	},


	prim81_MapHasKey_map_key(81, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Check if the key is present in the map.

			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(map.hasKey(key)));
			return Result.SUCCESS;
		}
	},


	prim82_MapAtKey_map_key(82, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Look up the key in the map.

			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			interpreter.primitiveResult(map.mapAt(key));
			return Result.SUCCESS;
		}
	},


	prim83_MapReplacingKey_map_key_value(83, 3, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a new map, but with key -> value in it.

			assert args.size() == 3;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			final AvailObject value = args.get(2);
			interpreter.primitiveResult(map.mapAtPuttingCanDestroy(
				key,
				value,
				true));
			return Result.SUCCESS;
		}
	},


	prim84_MapWithoutKey_map_key(84, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a new map, but without the given key.

			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			interpreter.primitiveResult(map.mapWithoutKeyCanDestroy(key, true));
			return Result.SUCCESS;
		}
	},


	prim85_CreateEmptyMap(85, 0, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer an empty map.

			assert args.size() == 0;
			interpreter.primitiveResult(MapDescriptor.empty());
			return Result.SUCCESS;
		}
	},


	prim86_MapKeysAsSet_map(86, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the keys of this map as a set.

			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(map.keysAsSet());
			return Result.SUCCESS;
		}
	},


	prim87_CreateMapType_Sizes_keyType_valueType(87, 3, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a map type with the given constraints.

			assert args.size() == 3;
			final AvailObject sizes = args.get(0);
			final AvailObject keyType = args.get(1);
			final AvailObject valueType = args.get(2);
			interpreter.primitiveResult(MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
				sizes,
				keyType,
				valueType));
			return Result.SUCCESS;
		}
	},


	prim88_MapTypeSizes_mapType(88, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the size range of a map type.

			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			interpreter.primitiveResult(mapType.sizeRange());
			return Result.SUCCESS;
		}
	},


	prim89_MapTypeKeyType_mapType(89, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the key type of a map type.

			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			interpreter.primitiveResult(mapType.keyType());
			return Result.SUCCESS;
		}
	},


	prim90_MapTypeValueType_mapType(90, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the value type of a map type.

			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			interpreter.primitiveResult(mapType.valueType());
			return Result.SUCCESS;
		}
	},


	prim91_MapValuesAsTuple_map(91, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the values of this map as a tuple, arbitrarily ordered.

			assert args.size() == 1;
			final AvailObject map = args.get(0);
			interpreter.primitiveResult(map.valuesAsTuple());
			return Result.SUCCESS;
		}
	},


	prim100_SetSize_set(100, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the size of the set.

			assert args.size() == 1;
			final AvailObject set = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(set.setSize()));
			return Result.SUCCESS;
		}
	},


	prim101_SetHasElement_set_element(101, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Check if the object is an element of the set.

			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject element = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(set.hasElement(element)));
			return Result.SUCCESS;
		}
	},


	prim102_SetUnion_set1_set2(102, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the union of two sets.

			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			interpreter.primitiveResult(set1.setUnionCanDestroy(set2, true));
			return Result.SUCCESS;
		}
	},


	prim103_SetIntersection_set1_set2(103, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the intersection of two sets.

			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			interpreter.primitiveResult(set1.setIntersectionCanDestroy(set2, true));
			return Result.SUCCESS;
		}
	},


	prim104_SetDifference_set1_set2(104, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the difference between two sets (set1 - set2).

			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			interpreter.primitiveResult(set1.setMinusCanDestroy(set2, true));
			return Result.SUCCESS;
		}
	},


	prim105_SetWith_set_newElement(105, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a new set but with newElement in it.

			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject newElement = args.get(1);
			interpreter.primitiveResult(set.setWithElementCanDestroy(newElement, true));
			return Result.SUCCESS;
		}
	},


	prim106_SetWithout_set_excludedElement(106, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a new set but without excludeElement in it.

			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject excludedElement = args.get(1);
			interpreter.primitiveResult(set.setWithoutElementCanDestroy(excludedElement, true));
			return Result.SUCCESS;
		}
	},


	prim107_SetIsSubset_set1_set2(107, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Check if set1 is a subset of set2.

			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(set1.isSubsetOf(set2)));
			return Result.SUCCESS;
		}
	},


	prim108_CreateEmptySet(108, 0, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the empty set.

			assert args.size() == 0;
			interpreter.primitiveResult(SetDescriptor.empty());
			return Result.SUCCESS;
		}
	},


	prim109_TupleToSet_tuple(109, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert a tuple into a set.  Clear from args to avoid having to make elements immutable.

			assert args.size() == 1;
			final AvailObject tuple = args.get(0);
			interpreter.primitiveResult(tuple.asSet());
			return Result.SUCCESS;
		}
	},


	prim110_SetToTuple_set(110, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert a set into an arbitrarily ordered tuple.  The conversion is unstable (two calls may
			//  produce different orderings).  Clear from args to avoid having to make elements immutable.

			assert args.size() == 1;
			final AvailObject set = args.get(0);
			interpreter.primitiveResult(set.asTuple());
			return Result.SUCCESS;
		}
	},


	prim111_CreateSetType_sizeRange_contentType(111, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Create a set type.

			assert args.size() == 2;
			final AvailObject sizeRange = args.get(0);
			final AvailObject contentType = args.get(1);
			interpreter.primitiveResult(SetTypeDescriptor.setTypeForSizesContentType(sizeRange, contentType));
			return Result.SUCCESS;
		}
	},


	prim112_SetTypeSizes_setType(112, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Extract a set type's range of sizes.

			assert args.size() == 1;
			final AvailObject setType = args.get(0);
			interpreter.primitiveResult(setType.sizeRange());
			return Result.SUCCESS;
		}
	},


	prim113_SetTypeElementType_setType(113, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Extract a set type's content type.

			assert args.size() == 1;
			final AvailObject setType = args.get(0);
			interpreter.primitiveResult(setType.contentType());
			return Result.SUCCESS;
		}
	},


	prim120_CreateCyclicType_name(120, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Create a new cyclic type with the given name.

			assert args.size() == 1;
			final AvailObject name = args.get(0);
			interpreter.primitiveResult(CyclicTypeDescriptor.newCyclicTypeWithName(name));
			return Result.SUCCESS;
		}
	},


	prim121_CyclicTypeName_cyclicType(121, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the name of a cyclicType.

			assert args.size() == 1;
			final AvailObject cyclicType = args.get(0);
			interpreter.primitiveResult(cyclicType.name());
			return Result.SUCCESS;
		}
	},


	prim130_TupleSize_tuple(130, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the size of the tuple.

			assert args.size() == 1;
			final AvailObject tuple = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(tuple.tupleSize()));
			return Result.SUCCESS;
		}
	},


	prim131_TupleAt_tuple_index(131, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Look up an element in the tuple.

			assert args.size() == 2;
			final AvailObject tuple = args.get(0);
			final AvailObject index = args.get(1);
			interpreter.primitiveResult(tuple.tupleAt(index.extractInt()));
			return Result.SUCCESS;
		}
	},


	prim132_TupleReplaceAt_tuple_index_value(132, 3, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a tuple like the given one, but with an element changed as indicated.

			assert args.size() == 3;
			final AvailObject tuple = args.get(0);
			final AvailObject index = args.get(1);
			final AvailObject value = args.get(2);
			interpreter.primitiveResult(tuple.tupleAtPuttingCanDestroy(
				index.extractInt(),
				value,
				true));
			return Result.SUCCESS;
		}
	},


	prim133_CreateTupleSizeOne_soleElement(133, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Build a tuple with one element.

			assert args.size() == 1;
			final AvailObject soleElement = args.get(0);
			final AvailObject newTupleObject = AvailObject.newIndexedDescriptor(1, ObjectTupleDescriptor.mutableDescriptor());
			newTupleObject.hashOrZero(0);
			newTupleObject.tupleAtPut(1, soleElement);
			interpreter.primitiveResult(newTupleObject);
			return Result.SUCCESS;
		}
	},


	prim134_CreateEmptyTuple(134, 0, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Build a tuple with no elements.

			assert args.size() == 0;
			interpreter.primitiveResult(TupleDescriptor.empty());
			return Result.SUCCESS;
		}
	},


	prim135_ExtractSubtuple_tuple_start_end(135, 3, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Extract a subtuple with the given range of elements.

			assert args.size() == 3;
			final AvailObject tuple = args.get(0);
			final AvailObject start = args.get(1);
			final AvailObject end = args.get(2);
			interpreter.primitiveResult(tuple.copyTupleFromToCanDestroy(
				start.extractInt(),
				end.extractInt(),
				true));
			return Result.SUCCESS;
		}
	},


	prim136_ConcatenateTuples_tuples(136, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Concatenate a tuple of tuples together into a single tuple.

			assert args.size() == 1;
			final AvailObject tuples = args.get(0);
			interpreter.primitiveResult(tuples.concatenateTuplesCanDestroy(true));
			return Result.SUCCESS;
		}
	},


	prim137_CreateTupleType_sizeRange_typeTuple_defaultType(137, 3, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Construct a tuple type with the given parameters.  Canonize the data if necessary.

			assert args.size() == 3;
			final AvailObject sizeRange = args.get(0);
			final AvailObject typeTuple = args.get(1);
			final AvailObject defaultType = args.get(2);
			interpreter.primitiveResult(TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizeRange,
				typeTuple,
				defaultType));
			return Result.SUCCESS;
		}
	},


	prim138_TupleTypeSizes_tupleType(138, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the allowed tuple sizes for this tupleType.

			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			interpreter.primitiveResult(tupleType.sizeRange());
			return Result.SUCCESS;
		}
	},


	prim139_TupleTypeLeadingTypes_tupleType(139, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the tuple of leading types that constrain this tupleType.

			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			interpreter.primitiveResult(tupleType.typeTuple());
			return Result.SUCCESS;
		}
	},


	prim140_TupleTypeDefaultType_tupleType(140, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the default type for elements past the leading types.

			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			interpreter.primitiveResult(tupleType.defaultType());
			return Result.SUCCESS;
		}
	},


	prim141_TupleTypeAt_tupleType_index(141, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the type for the given element of instances of the given tuple type.
			//  Answer terminates if out of range.

			assert args.size() == 2;
			final AvailObject tupleType = args.get(0);
			final AvailObject index = args.get(1);
			interpreter.primitiveResult(tupleType.typeAtIndex(index.extractInt()));
			return Result.SUCCESS;
		}
	},


	prim142_TupleTypeSequenceOfTypes_tupleType_startIndex_endIndex(142, 3, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a tuple of types representing the types of the given range of indices
			//  within the tupleType.  Use terminates for indices out of range.

			assert args.size() == 3;
			final AvailObject tupleType = args.get(0);
			final AvailObject startIndex = args.get(1);
			final AvailObject endIndex = args.get(2);
			//  For now, play it safe.
			AvailObject tupleObject = AvailObject.newIndexedDescriptor(((endIndex.extractInt() - startIndex.extractInt()) + 1), ObjectTupleDescriptor.mutableDescriptor());
			tupleObject.hashOrZero(0);
			for (int i = 1, _end8 = tupleObject.tupleSize(); i <= _end8; i++)
			{
				tupleObject.tupleAtPut(i, VoidDescriptor.voidObject());
			}
			for (int i = 1, _end9 = tupleObject.tupleSize(); i <= _end9; i++)
			{
				tupleObject = tupleObject.tupleAtPuttingCanDestroy(
					i,
					tupleType.typeAtIndex(((startIndex.extractInt() + i) - 1)),
					true);
			}
			interpreter.primitiveResult(tupleObject);
			return Result.SUCCESS;
		}
	},


	prim143_TupleTypeAtThrough_tupleType_startIndex_endIndex(143, 3, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the type that is the union of the types within the given range of indices
			//  of the given tupleType.  Answer terminates if all the indices are out of range.

			assert args.size() == 3;
			final AvailObject tupleType = args.get(0);
			final AvailObject startIndex = args.get(1);
			final AvailObject endIndex = args.get(2);
			interpreter.primitiveResult(tupleType.unionOfTypesAtThrough(startIndex.extractInt(), endIndex.extractInt()));
			return Result.SUCCESS;
		}
	},


	prim144_TupleTypeConcatenate_tupleType1_tupleType2(144, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the type that is the type of all possible concatenations of instances of
			//  the given tupleTypes.  This is basically the returns clause of the two-argument
			//  concatenation operation.

			assert args.size() == 2;
			final AvailObject tupleType1 = args.get(0);
			final AvailObject tupleType2 = args.get(1);
			interpreter.primitiveResult(ConcatenatedTupleTypeDescriptor.concatenatingAnd(tupleType1, tupleType2));
			return Result.SUCCESS;
		}
	},

	/**
	 * <strong>Primitive 160:</strong> Open a {@linkplain RandomAccessFile file}
	 * for reading. Answer a {@linkplain CyclicTypeDescriptor cycle} that
	 * uniquely identifies the file.
	 * 
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim160_FileOpenRead_nameString(160, 1, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			assert args.size() == 1;
			
			final AvailObject filename = args.get(0);
			if (!filename.isString())
			{
				return Result.FAILURE;
			}
			
			AvailObject handle =
				CyclicTypeDescriptor.newCyclicTypeWithName(filename);
			try
			{
				final RandomAccessFile file = new RandomAccessFile(
					filename.asNativeString(), "r");
				interpreter.putFile(handle, file);
			}
			catch (final IOException e)
			{
				return Result.FAILURE;
			}
			
			interpreter.primitiveResult(handle);
			return Result.SUCCESS;
		}
	},


	prim161_FileOpenWrite_nameString(161, 1, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Open a file for writing.  Answer the OS handle as an integer.

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| stream handle |
				stream := nameString asNativeString asFilename writeStream.
				stream binary.
				handle := openFiles indexOf: 0 ifAbsent: [
					openFiles
						add: stream;
						size].
				^IntegerDescriptor objectFromInt: handle
			 */
		}
	},


	prim162_FileClose_handleInt(162, 1, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Close a file.

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				(openFiles at: handleInt extractInt) close.
				openFiles at: handleInt extractInt put: nil.
				^VoidDescriptor voidObject
			 */
		}
	},


	prim164_FileRead_handleInt_size(163, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Attempt to read size bytes from the stream with handle handleInt.  If fewer bytes are
			//  available, just answer a shorter tuple.  If size is INF, answer all available remaining bytes.

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| stream byteArray |
				stream := openFiles at: handleInt extractInt.
				size isFinite
					ifTrue: [
						byteArray := stream nextAvailable: size extractInt.
						^ByteTupleDescriptor mutableObjectFromByteArray: byteArray]
					ifFalse: [
						size isPositive assert: 'Negative infinity not allowed here'.
						^ByteTupleDescriptor mutableObjectFromByteArray: stream upToEnd].
			 */
		}
	},


	prim165_FileWrite_handleInt_bytes(164, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Attempt to write bytes to the stream with handle handleInt.  Answer the tuple
			//  of bytes that could not be written (usually an empty tuple).  For now, we always
			//  answer the empty tuple.

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| file |
				file := openFiles at: handleInt extractInt.
				1 to: bytes tupleSize do: [:i |
					file nextPut: (bytes tupleAt: i) extractByte].
				^TupleDescriptor empty
			 */
		}
	},


	prim166_FileSize_handleInt(165, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the current size of the stream (usually the size of a file, but sockets
			//  might answer INF).  Currently restricted to 2GB (signed 32).

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				^IntegerDescriptor
					objectFromInt: (openFiles at: handleInt extractInt) size
			 */
		}
	},


	prim167_FilePosition_handleInt(166, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the current position of the stream (usually the position in a file, but sockets
			//  might answer INF).  Currently restricted to 2GB (signed 32).

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				^IntegerDescriptor
					objectFromInt: (openFiles at: handleInt extractInt) position
			 */
		}
	},


	prim168_FileSetPosition_handleInt_newPosition(167, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Set the current position of the stream (fail for sockets).

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				(openFiles at: handleInt extractInt) position: newPosition extractInt.
				^VoidDescriptor voidObject
			 */
		}
	},


	prim180_CompiledCodeNumArgs_cc(180, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the number of arguments expected by the compiledCode.

			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(cc.numArgs()));
			return Result.SUCCESS;
		}
	},


	prim181_CompiledCodeNumLocals_cc(181, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the number of locals created by the compiledCode.

			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(cc.numLocals()));
			return Result.SUCCESS;
		}
	},


	prim182_CompiledCodeNumOuters_cc(182, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the number of outers in closures derived from the compiledCode.

			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(cc.numOuters()));
			return Result.SUCCESS;
		}
	},


	prim183_CompiledCodeNumStackSlots_cc(183, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the number of stack slots (not counting args and locals) created for the compiledCode.

			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(cc.maxStackDepth()));
			return Result.SUCCESS;
		}
	},


	prim184_CompiledCodeNybbles_cc(184, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the nybblecodes of the compiledCode.

			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(cc.nybbles());
			return Result.SUCCESS;
		}
	},


	prim185_CompiledCodeClosureType_cc(185, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the type of closure this compiledCode will be closed into.

			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(cc.closureType());
			return Result.SUCCESS;
		}
	},


	prim186_CompiledCodePrimitiveNumber_cc(186, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the primitive number of this compiledCode.

			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(cc.primitiveNumber()));
			return Result.SUCCESS;
		}
	},


	prim187_CompiledCodeLiterals_cc(187, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a tuple with the literals from this compiledCode.

			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			AvailObject tupleObject = AvailObject.newIndexedDescriptor(cc.numLiterals(), ObjectTupleDescriptor.mutableDescriptor());
			tupleObject.hashOrZero(0);
			for (int i = 1, _end10 = tupleObject.tupleSize(); i <= _end10; i++)
			{
				tupleObject.tupleAtPut(i, VoidDescriptor.voidObject());
			}
			AvailObject literal;
			for (int i = 1, _end11 = tupleObject.tupleSize(); i <= _end11; i++)
			{
				literal = cc.literalAt(i);
				if ((literal.equalsVoid() || literal.isList()))
				{
					literal = IntegerDescriptor.zero();
				}
				tupleObject = tupleObject.tupleAtPuttingCanDestroy(
					i,
					literal,
					true);
			}
			interpreter.primitiveResult(tupleObject);
			return Result.SUCCESS;
		}
	},


	prim188_CreateCompiledCode_numArgs_locals_outers_stack_nybs_closureType_prim_literals(188, 8, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a compiledCode with the given data.

			assert args.size() == 8;
			final AvailObject numArgs = args.get(0);
			final AvailObject locals = args.get(1);
			final AvailObject outers = args.get(2);
			final AvailObject stack = args.get(3);
			final AvailObject nybs = args.get(4);
			final AvailObject closureType = args.get(5);
			final AvailObject prim = args.get(6);
			final AvailObject literals = args.get(7);
			int nLocals = locals.extractInt();
			int nOuters = outers.extractInt();
			int nLiteralsTotal = literals.tupleSize();
			interpreter.primitiveResult(
				CompiledCodeDescriptor.newCompiledCodeWithNybblesNumArgsLocalsStackClosureTypePrimitiveLiteralsLocalTypesOuterTypes (
					nybs,
					numArgs.extractInt(),
					nLocals,
					stack.extractInt(),
					closureType,
					prim.extractInt(),
					literals.copyTupleFromToCanDestroy(1, nLiteralsTotal - nLocals - nOuters, false),
					literals.copyTupleFromToCanDestroy(nLiteralsTotal - nLocals + 1, nLiteralsTotal, false),
					literals.copyTupleFromToCanDestroy(nLiteralsTotal - nLocals - nOuters + 1, nLiteralsTotal - nLocals, false)));
			return Result.SUCCESS;
		}
	},


	prim200_CatchException_bodyBlock_handlerBlock(200, 2, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  The Avail failure code invokes the bodyBlock.  The handlerBlock
			//  is only invoked when an exception is raised (via primitive 201).

			assert args.size() == 2;
			@SuppressWarnings("unused") final AvailObject bodyBlock = args.get(0);
			@SuppressWarnings("unused") final AvailObject handlerBlock = args.get(1);
			return Result.FAILURE;
		}
	},


	prim201_RaiseException_exceptionValue(201, 1, Flag.SwitchesContinuation)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Raise an exception.  Scan the stack of continuations until one
			//  is found for a closure whose code is the primitive 200.  Get that
			//  continuation's second argument (a handler block of one argument),
			//  and check if that handler block will accept the exceptionValue.  If
			//  not, keep looking.  If it will accept it, unwind the stack so that the
			//  primitive 200 method is the top entry, and invoke the handler block
			//  with exceptionValue.  If there is no suitable handler block, fail the
			//  primitive.

			assert args.size() == 1;
			final AvailObject exceptionValue = args.get(0);
			return interpreter.searchForExceptionHandler(exceptionValue, args);
		}
	},


	prim207_CompleteMessages_bundleTree(207, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a collection of all visible messages inside the current tree
			//  that expect no more parts than those already encountered.  Answer
			//  it as a map from cyclicType to messageBundle (typically only
			//  zero or one entry).

			assert args.size() == 1;
			final AvailObject bundleTree = args.get(0);
			interpreter.primitiveResult(bundleTree.complete().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim208_IncompleteMessages_bundleTree(208, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a collection of all visible messages inside the current tree
			//  that expect more parts than those already encountered.  Answer
			//  it as a map from string to messageBundleTree.

			assert args.size() == 1;
			final AvailObject bundleTree = args.get(0);
			interpreter.primitiveResult(bundleTree.incomplete().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim209_CompleteMessagesStartingWith_leadingPart(209, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a collection of all visible methods that start with the given
			//  string, and have only one part.  Answer a map from cyclicType to
			//  messageBundle.

			assert args.size() == 1;
			final AvailObject leadingPart = args.get(0);
			interpreter.primitiveResult(interpreter.completeBundlesStartingWith(leadingPart).makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim210_IncompleteMessagesStartingWith_leadingPart(210, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a collection of all visible methods that start with the given
			//  string, and have more than one part.  Answer a map from second
			//  part (string) to messageBundleTree.

			assert args.size() == 1;
			final AvailObject leadingPart = args.get(0);
			interpreter.primitiveResult(interpreter.incompleteBundlesStartingWith(leadingPart).makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim211_BundleMessage_bundle(211, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a message bundle's message (a cyclicType).

			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(bundle.message().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim212_BundleMessageParts_bundle(212, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a message bundle's messageParts (a tuple of strings).

			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(bundle.messageParts().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim213_BundleSignatures_bundle(213, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a set of all currently defined signatures for the message represented
			//  by bundle.  This includes abstract signatures and forward signatures.

			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(interpreter.methodsAt(bundle.message()).implementationsTuple().asSet().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim214_BundleHasRestrictions_bundle(214, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer whether precedence restrictions have been defined (yet) for this bundle.

			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(bundle.message().hasRestrictions()));
			return Result.SUCCESS;
		}
	},


	prim215_BundleRestrictions_bundle(215, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the current precedence restrictions for this bundle.

			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			interpreter.primitiveResult(bundle.restrictions().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim216_SignatureBodyType_sig(216, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer this signature's body's type (a closureType).

			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			interpreter.primitiveResult(sig.bodySignature().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim217_SignatureBodyBlock_methSig(217, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer this methodSignature's bodyBlock (a closure).

			assert args.size() == 1;
			final AvailObject methSig = args.get(0);
			interpreter.primitiveResult(methSig.bodyBlock().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim218_SignatureRequiresBlock_sig(218, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer this signature's requiresBlock (a closure).

			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			interpreter.primitiveResult(sig.requiresBlock().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim219_SignatureReturnsBlock_sig(219, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer this signature's returnsBlock (a closure).

			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			interpreter.primitiveResult(sig.returnsBlock().makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim220_ImplementationSetFromName_name(220, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the implementationSet (see ImplementationSetDescriptor) associated with
			//  the given cyclicType.  This is generally only used when Avail code is constructing
			//  Avail code in the metacircular compiler.

			assert args.size() == 1;
			final AvailObject aCyclicType = args.get(0);
			interpreter.primitiveResult(interpreter.methodsAt(aCyclicType).makeImmutable());
			return Result.SUCCESS;
		}
	},


	prim221_ImplementationSetName_name(221, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the cyclicType associated with the given implementationSet (see
			//  ImplementationSetDescriptor).  This is generally only used when Avail code
			//  is saving or loading Avail code in the object dumper / loader.

			assert args.size() == 1;
			final AvailObject anImplementationSet = args.get(0);
			interpreter.primitiveResult(anImplementationSet.name());
			return Result.SUCCESS;
		}
	},


	prim240_SpecialObject_index(240, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Access special object with the given index.

			assert args.size() == 1;
			final AvailObject index = args.get(0);
			final int i = index.extractInt();
			final List<AvailObject> list = interpreter.specialObjects();
			if (((i < 1) || (i > list.size())))
			{
				return Result.FAILURE;
			}
			final AvailObject result = list.get((i - 1));
			if (result == null)
			{
				return Result.FAILURE;
			}
			interpreter.primitiveResult(result);
			return Result.SUCCESS;
		}
	},


	prim245_LookupName_name(245, 1, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Lookup a name in the current module.

			assert args.size() == 1;
			final AvailObject name = args.get(0);
			interpreter.primitiveResult(interpreter.lookupName(name));
			return Result.SUCCESS;
		}
	},


	prim250_IsPrimitiveDefined_index(250, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer whether the given primitive number is defined.

			assert args.size() == 1;
			final AvailObject index = args.get(0);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(interpreter.supportsPrimitive(((short)(index.extractInt())))));
			return Result.SUCCESS;
		}
	},


	prim251_AbstractMethodDeclaration_string_blockSignature_requiresBlock_returnsBlock(251, 4, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Declare method as abstract.  This identifies responsibility for subclasses
			//  that want to be concrete.

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
			return Result.SUCCESS;
		}
	},


	prim252_ForwardMethodDeclaration_string_blockSignature(252, 2, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Declare method without body (for recursion / mutual recursion).

			assert args.size() == 2;
			final AvailObject string = args.get(0);
			final AvailObject blockSignature = args.get(1);
			interpreter.atAddForwardStubFor(interpreter.lookupName(string), blockSignature);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim253_SimpleMethodDeclaration_string_block(253, 2, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Method definition (without type constraint or result type deduction).

			assert args.size() == 2;
			final AvailObject string = args.get(0);
			final AvailObject block = args.get(1);
			interpreter.atAddMethodBody(interpreter.lookupName(string), block);
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim254_MethodDeclaration_string_block_requiresBlock_returnsBlock(254, 4, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Method definition (with type constraint and result type calculation).

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
			return Result.SUCCESS;
		}
	},


	prim255_PrecedenceDeclaration_stringSet_exclusionsTuple(255, 2, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Message precedence declaration with tuple of submessage exclusion sets.

			assert args.size() == 2;
			final AvailObject stringSet = args.get(0);
			final AvailObject exclusionsTuple = args.get(1);
			AvailObject disallowed = exclusionsTuple;
			for (int i = 1, _end12 = disallowed.tupleSize(); i <= _end12; i++)
			{
				final AvailObject exclusionSet = exclusionsTuple.tupleAt(i);
				final AvailObject exclusionSetAsTuple = exclusionSet.asTuple();
				AvailObject setOfCyclics = SetDescriptor.empty();
				for (int k = 1, _end13 = exclusionSetAsTuple.tupleSize(); k <= _end13; k++)
				{
					AvailObject string = exclusionSetAsTuple.tupleAt(k);
					setOfCyclics = setOfCyclics.setWithElementCanDestroy(interpreter.lookupName(string), true);
				}
				disallowed = disallowed.tupleAtPuttingCanDestroy(
					i,
					setOfCyclics,
					true);
			}
			disallowed.makeImmutable();
			final AvailObject stringSetAsTuple = stringSet.asTuple();
			for (int i = 1, _end14 = stringSetAsTuple.tupleSize(); i <= _end14; i++)
			{
				AvailObject string = stringSetAsTuple.tupleAt(i);
				interpreter.atDisallowArgumentMessages(interpreter.lookupName(string), disallowed);
			}
			interpreter.primitiveResult(VoidDescriptor.voidObject());
			return Result.SUCCESS;
		}
	},


	prim256_EmergencyExit_value(256, 1, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Emergency exit primitive.

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| reply result |
				result := nil.
				reply := Dialog
					choose: ('Pausing interpreter with:\' withCRs , value printString)
						, '\Debug?' withCRs
					labels: #('Smalltalk debug' 'Avail debug' 'Terminate' 'Proceed')
					values: #(#smalltalk #avail #terminate #proceed)
					default: #proceed.
				reply = #terminate ifTrue: [
					self currentContinuation: VoidDescriptor voidObject.
					AvailCompiler compilerErrorSignal raiseWith: #terminated].
				reply = #smalltalk ifTrue: [
					self halt.
					reply := #avail].
				reply = #avail ifTrue: [
					self currentContinuation
						stackAt: self currentContinuation stackp
						put: VoidDescriptor voidObject.
					self prepareToExecuteContinuation: self currentContinuation debug.
					result := #continuationChanged].
				reply = #proceed ifTrue: [
					result := VoidDescriptor voidObject].
				^result
			 */
		}
	},


	prim257_BreakPoint(257, 0, Flag.Unknown)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Pause the VM.

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


	prim260_CreateLibrarySpec(260, 0, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Create an opaque library object into which we can load declarations.  We may also
			//  open it later (load the actual DLL) via another primitive.  Answer the handle (an integer).
			//  Associated with the handle is an Array containing
			//  (1) an ExternalDictionary for accumulating declarations,
			//  (2) a place to store an ExternalLibrary if it is subsequently opened.

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


	prim261_OpenLibrary_handle_filename(261, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Open a previously constructed library.  Its handle (into openLibraries) is passed.

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


	prim262_ParseDeclarations_libraryHandle_declaration(262, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Declare a function for the given library.  Don't look for the entry point yet, that's actually
			//  the job of primitive 262.  Instead, parse the declaration to produce an ExternalMethod.
			//  Create an opaque handle that secretly contains
			//  (1) the library's handle,
			//  (2) the closureType,
			//  (3) an ExternalProcedure (which can cache the function address when invoked), and
			//  (4) a Smalltalk Block that accepts and returns Avail objects but invokes the C function.

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


	prim263_ExtractEntryPoint_handle_functionName(263, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Create an entry point handle to deal with subsequent invocations of the
			//  already declared function with given name.  The library does not yet have
			//  to be opened.  The resulting entry point handle encapsulates:
			//  (1) an ExternalProcedure
			//  (2) a closureType
			//  (3) the Array that encapsulates the library handle.

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


	prim264_EntryPointClosureType_entryPointHandle(264, 1, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer the closure type associated with the given entry point.

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| privateEntryPoint |
				privateEntryPoint := entryPoints at: entryPointHandle extractInt.
				^privateEntryPoint at: 2
			 */
		}
	},


	prim265_InvokeEntryPoint_arguments(265, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Invoke the entry point associated with the given handle, using the specified arguments.

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


	prim266_IntegralType_from(266, 2, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Read an integer of the specified type from the memory location specified as an integer.

			return interpreter.callBackSmalltalkPrimitive(primitiveNumber, args);
			/* From Smalltalk:
				| byteCount int |
				byteCount := (intType upperBound highBit + 7) // 8.
				int := byteCount halt.
				^IntegerDescriptor objectFromSmalltalkInteger: int
			 */
		}
	},


	prim267_IntegralType_to_write(267, 3, Flag.CanInline, Flag.HasSideEffect)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Write an integer of the specified type to the memory location specified as an integer.

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


	prim268_BigEndian(268, 0, Flag.CanInline)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Answer a boolean indicating if the current platform is big-endian.

			assert args.size() == 0;
			// PLATFORM-DEPENDENT -- rewrite more portably some day
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean(false));
			return Result.SUCCESS;
		}
	},


	prim280_FloatAddition_a_b(280, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Add two floats.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(FloatDescriptor.objectFromFloatRecyclingOr(
				(a.extractFloat() + b.extractFloat()),
				a,
				b));
			return Result.SUCCESS;
		}
	},


	prim281_FloatSubtraction_a_b(281, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Subtract b from a.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(FloatDescriptor.objectFromFloatRecyclingOr(
				(a.extractFloat() - b.extractFloat()),
				a,
				b));
			return Result.SUCCESS;
		}
	},


	prim282_FloatMultiplication_a_b(282, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Multiply a and b.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(FloatDescriptor.objectFromFloatRecyclingOr(
				(a.extractFloat() * b.extractFloat()),
				a,
				b));
			return Result.SUCCESS;
		}
	},


	prim283_FloatDivision_a_b(283, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Divide a by b.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			if ((b.extractFloat() == 0.0))
			{
				return Result.FAILURE;
			}
			interpreter.primitiveResult(FloatDescriptor.objectFromFloatRecyclingOr(
				(a.extractFloat() / b.extractFloat()),
				a,
				b));
			return Result.SUCCESS;
		}
	},


	prim284_FloatLessThan_a_b(284, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compare a < b.  Answers an Avail boolean.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean((a.extractFloat() < b.extractFloat())));
			return Result.SUCCESS;
		}
	},


	prim285_FloatLessOrEqual_a_b(285, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compare a <= b.  Answers an Avail boolean.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean((a.extractFloat() <= b.extractFloat())));
			return Result.SUCCESS;
		}
	},


	prim286_FloatLn_a(286, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compute the natural logarithm of a.

			assert args.size() == 1;
			final AvailObject a = args.get(0);
			interpreter.primitiveResult(
				FloatDescriptor.objectFromFloatRecycling((float)log(a.extractFloat()), a));
			return Result.SUCCESS;
		}
	},


	prim287_FloatExp_a(287, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compute e^a, the natural exponential of a.

			assert args.size() == 1;
			final AvailObject a = args.get(0);
			interpreter.primitiveResult(
				FloatDescriptor.objectFromFloatRecycling((float)exp(a.extractFloat()), a));
			return Result.SUCCESS;
		}
	},


	prim288_FloatModulus_a_b(288, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Divide a by b, but answer the remainder.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			float fa = a.extractFloat();
			float fb = b.extractFloat();
			if (fb == 0.0f) return Result.FAILURE;
			float div = fa / fb;
			float mod = fa - ((float)floor(div) * fb);
			interpreter.primitiveResult(
				FloatDescriptor.objectFromFloatRecyclingOr(mod, a, b));
			return Result.SUCCESS;
		}
	},


	prim289_FloatTruncatedAsInteger_a(289, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert a float to an integer, rounding towards zero.

			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top two 32-bit sections.  That guarantees 33 bits
			// of mantissa, which is more than a float actually captures.
			float f = a.extractFloat();
			if (f >= -0x80000000L && f <= 0x7FFFFFFFL)
			{
				// Common case -- it fits in an int.
				interpreter.primitiveResult(IntegerDescriptor.objectFromInt((int)f));
				return Result.SUCCESS;
			}
			boolean neg = f < 0.0f;
			f = abs(f);
			int exponent = getExponent(f);
			int slots = exponent + 31 / 32;  // probably needs work
			AvailObject out = AvailObject.newIndexedDescriptor(
				slots,
				IntegerDescriptor.mutableDescriptor());
			f = scalb(f, (1 - slots) * 32);
			for (int i = slots; i >= 1; --i)
			{
				long intSlice = (int) f;
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
			return Result.SUCCESS;
		}
	},


	prim290_FloatFromInteger_a(290, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert an integer to a float, failing if out of range.

			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top 32 bits and the next-to-top 32 bits.  That guarantees 33 bits
			// of mantissa, which is more than a float actually captures.
			float f;
			int size = a.integerSlotsCount();
			if (size == 1)
			{
				f = a.extractInt();
			}
			else
			{
				long highInt = a.rawUnsignedIntegerAt(size);
				long lowInt = a.rawUnsignedIntegerAt(size - 1);
				boolean neg = (highInt & (0x80000000L)) != 0;
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
			return Result.SUCCESS;
		}
	},


	prim291_FloatTimesTwoPower_a_b(291, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compute a*(2**b) without intermediate overflow or any precision loss.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			long scale = b.extractInt();
			scale = max(scale, -0x80000000L);
			scale = min(scale, 0x7FFFFFFFL);
			float f = scalb(a.extractFloat(), (int)scale);
			interpreter.primitiveResult(
				FloatDescriptor.objectFromFloatRecycling(f, a));
			return Result.SUCCESS;
		}
	},


	prim310_DoubleAddition_a_b(310, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Add two doubles.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(DoubleDescriptor.objectFromDoubleRecyclingOr(
				(a.extractDouble() + b.extractDouble()),
				a,
				b));
			return Result.SUCCESS;
		}
	},


	prim311_DoubleSubtraction_a_b(311, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Subtract b from a.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(DoubleDescriptor.objectFromDoubleRecyclingOr(
				(a.extractDouble() - b.extractDouble()),
				a,
				b));
			return Result.SUCCESS;
		}
	},


	prim312_DoubleMultiplication_a_b(312, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Multiply a and b.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(DoubleDescriptor.objectFromDoubleRecyclingOr(
				(a.extractDouble() * b.extractDouble()),
				a,
				b));
			return Result.SUCCESS;
		}
	},


	prim313_DoubleDivision_a_b(313, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Divide a by b.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			if ((b.extractDouble() == 0.0d))
			{
				return Result.FAILURE;
			}
			interpreter.primitiveResult(DoubleDescriptor.objectFromDoubleRecyclingOr(
				(a.extractDouble() / b.extractDouble()),
				a,
				b));
			return Result.SUCCESS;
		}
	},


	prim314_DoubleLessThan_a_b(314, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compare a < b.  Answers an Avail boolean.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean((a.extractDouble() < b.extractDouble())));
			return Result.SUCCESS;
		}
	},


	prim315_DoubleLessOrEqual_a_b(315, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compare a <= b.  Answers an Avail boolean.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			interpreter.primitiveResult(BooleanDescriptor.objectFromBoolean((a.extractDouble() <= b.extractDouble())));
			return Result.SUCCESS;
		}
	},


	prim316_DoubleLn_a(316, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compute the natural logarithm of a.

			assert args.size() == 1;
			final AvailObject a = args.get(0);
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDoubleRecycling(log(a.extractDouble()), a));
			return Result.SUCCESS;
		}
	},


	prim317_DoubleExp_a(317, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compute e^a, the natural exponential of a.

			assert args.size() == 1;
			final AvailObject a = args.get(0);
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDoubleRecycling(exp(a.extractDouble()), a));
			return Result.SUCCESS;
		}
	},


	prim318_DoubleModulus_a_b(318, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Divide a by b, but answer the remainder.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			double da = a.extractDouble();
			double db = b.extractDouble();
			if (db == 0.0d) return Result.FAILURE;
			double div = da / db;
			double mod = da - (floor(div) * db);
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDoubleRecyclingOr(mod, a, b));
			return Result.SUCCESS;
		}
	},


	prim319_DoubleTruncatedAsInteger_a(319, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert a double to an integer, rounding towards zero.

			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top three 32-bit sections.  That guarantees 65 bits
			// of mantissa, which is more than a double actually captures.
			double d = a.extractDouble();
			if (d >= -0x80000000L && d <= 0x7FFFFFFFL)
			{
				// Common case -- it fits in an int.
				interpreter.primitiveResult(IntegerDescriptor.objectFromInt((int)d));
				return Result.SUCCESS;
			}
			boolean neg = d < 0.0d;
			d = abs(d);
			int exponent = getExponent(d);
			int slots = exponent + 31 / 32;  // probably needs work
			AvailObject out = AvailObject.newIndexedDescriptor(
				slots,
				IntegerDescriptor.mutableDescriptor());
			d = scalb(d, (1 - slots) * 32);
			for (int i = slots; i >= 1; --i)
			{
				long intSlice = (int) d;
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
			return Result.SUCCESS;
		}
	},


	prim320_DoubleFromInteger_a(320, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert a double to an integer, rounding towards zero.

			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top three 32-bit pieces.  That guarantees 65 bits
			// of mantissa, which is more than a double actually captures.
			double d;
			int size = a.integerSlotsCount();
			if (size == 1)
			{
				d = a.extractInt();
			}
			else
			{
				long highInt = a.rawUnsignedIntegerAt(size);
				long nextInt = a.rawUnsignedIntegerAt(size - 1);
				long lowInt = size >= 3 ? a.rawUnsignedIntegerAt(size - 2) : 0;
				boolean neg = (highInt & (0x80000000L)) != 0;
				if (neg)
				{
					highInt = ~highInt;
					nextInt = ~nextInt;
					lowInt = ~lowInt;
					if ((int)++lowInt == 0)
					{
						if ((int)++nextInt == 0)
						{
							++highInt;
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
			return Result.SUCCESS;
		}
	},


	prim321_DoubleTimesTwoPower_a_b(321, 2, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Compute a*(2**b) without intermediate overflow or any precision loss.

			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			long scale = b.extractInt();
			scale = max(scale, -0x80000000L);
			scale = min(scale, 0x7FFFFFFFL);
			double d = scalb(a.extractDouble(), (int)scale);
			interpreter.primitiveResult(
				DoubleDescriptor.objectFromDoubleRecycling(d, a));
			return Result.SUCCESS;
		}
	},


	prim330_CharacterCodePoint_character(330, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Extract the code point (integer) from a character.

			assert args.size() == 1;
			final AvailObject character = args.get(0);
			interpreter.primitiveResult(IntegerDescriptor.objectFromInt(character.codePoint()));
			return Result.SUCCESS;
		}
	},


	prim331_CharacterFromCodePoint_codePoint(331, 1, Flag.CanFold)
	{
		@Override
		public Result attempt (List<AvailObject> args, AvailInterpreter interpreter)
		{
			//  Convert a code point (integer) into a character.

			assert args.size() == 1;
			final AvailObject codePoint = args.get(0);
			interpreter.primitiveResult(CharacterDescriptor.newImmutableCharacterWithCodePoint(codePoint.extractInt()));
			return Result.SUCCESS;
		}
	};


	public enum Result
	{
		SUCCESS,
		FAILURE,
		CONTINUATION_CHANGED,
		SUSPENDED;
	};

	public enum Flag
	{
		CanFold,
		CanInline,
		HasSideEffect,
		Invokes,
		SwitchesContinuation,
		Unknown
	};

	public abstract Result attempt (List<AvailObject> args, AvailInterpreter interpreter);

	final short primitiveNumber;
	final int argCount;
	private final EnumSet<Flag> primitiveFlags;

	public boolean hasFlag(Flag flag)
	{
		return primitiveFlags.contains(flag);
	}

	public int argCount()
	{
		return argCount;
	}

	// This forces the ClassLoader to make this field available at load time.
	private static class PrimitiveCounter
	{
		public static short maxPrimitiveNumber = Short.MIN_VALUE;
	}

	private static final Primitive[] byPrimitiveNumber;
	static
	{
		byPrimitiveNumber = new Primitive[PrimitiveCounter.maxPrimitiveNumber];
		for (Primitive prim : values())
		{
			byPrimitiveNumber[prim.primitiveNumber - 1] = prim;
		}
	}

	public static Primitive byPrimitiveNumber(short primitiveNumber)
	{
		return byPrimitiveNumber[primitiveNumber - 1];
	}

	// Constructors
	private Primitive (int primitiveNumber, int argCount, Flag ... flags)
	{
		this.primitiveNumber = (short)primitiveNumber;
		this.argCount = argCount;
		this.primitiveFlags = EnumSet.noneOf(Flag.class);
		for (Flag flag : flags)
		{
			this.primitiveFlags.add(flag);
		}

		if (this.primitiveNumber > PrimitiveCounter.maxPrimitiveNumber)
		{
			PrimitiveCounter.maxPrimitiveNumber = this.primitiveNumber;
		}
	}
}
