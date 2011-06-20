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
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED;
import static java.lang.Math.*;
import java.io.*;
import java.nio.ByteOrder;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.*;
import com.avail.compiler.node.*;
import com.avail.compiler.scanning.*;
import com.avail.compiler.scanning.TokenDescriptor.TokenType;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
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
	 * <strong>Primitive 1:</strong> Add two {@linkplain
	 * ExtendedNumberDescriptor extended integers}.
	 */
	prim1_Addition_a_b(1, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			try
			{
				return interpreter.primitiveSuccess(a.plusCanDestroy(b, true));
			}
			catch (final ArithmeticException e)
			{
				return interpreter.primitiveFailure(e);
			}
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers(),
					IntegerRangeTypeDescriptor.extendedIntegers()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 2:</strong> Subtract {@linkplain
	 * ExtendedNumberDescriptor extended integer} b from a.
	 */
	prim2_Subtraction_a_b(2, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			try
			{
				return interpreter.primitiveSuccess(a.minusCanDestroy(b, true));
			}
			catch (final ArithmeticException e)
			{
				return interpreter.primitiveFailure(e);
			}
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers(),
					IntegerRangeTypeDescriptor.extendedIntegers()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 3:</strong> Multiply {@linkplain
	 * ExtendedNumberDescriptor extended integers} a and b.
	 */
	prim3_Multiplication_a_b(3, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			try
			{
				return interpreter.primitiveSuccess(a.timesCanDestroy(b, true));
			}
			catch (final ArithmeticException e)
			{
				return interpreter.primitiveFailure(e);
			}
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers(),
					IntegerRangeTypeDescriptor.extendedIntegers()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 4:</strong> Compute {@linkplain
	 * ExtendedNumberDescriptor extended integer} a divided by b.
	 */
	prim4_Division_a_b(4, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			try
			{
				return interpreter.primitiveSuccess(
					a.divideCanDestroy(b, true));
			}
			catch (final ArithmeticException e)
			{
				return interpreter.primitiveFailure(e);
			}
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers(),
					IntegerRangeTypeDescriptor.extendedIntegers()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 5:</strong> Compare {@linkplain
	 * ExtendedNumberDescriptor extended integers} {@code a < b}. Answer
	 * a {@linkplain BooleanDescriptor boolean}.
	 */
	prim5_LessThan_a_b(5, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(a.lessThan(b)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers(),
					IntegerRangeTypeDescriptor.extendedIntegers()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 6:</strong> Compare {@linkplain
	 * ExtendedNumberDescriptor extended integers} {@code a <= b}. Answer
	 * a {@linkplain BooleanDescriptor boolean}.
	 */
	prim6_LessOrEqual_a_b(6, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(a.lessOrEqual(b)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers(),
					IntegerRangeTypeDescriptor.extendedIntegers()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 7:</strong> Answer the {@linkplain
	 * IntegerRangeTypeDescriptor integer range} constrained by the specified
	 * {@linkplain ExtendedNumberDescriptor upper and lower bounds}. The
	 * provided {@linkplain BooleanDescriptor booleans} indicate whether their
	 * corresponding bounds are inclusive ({@code true}) or exclusive
	 * ({@code false}).
	 */
	prim7_CreateIntegerRange_min_minInc_max_maxInc(7, 4, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 4;
			final AvailObject min = args.get(0);
			final AvailObject minInc = args.get(1);
			final AvailObject max = args.get(2);
			final AvailObject maxInc = args.get(3);
			return interpreter.primitiveSuccess(
				IntegerRangeTypeDescriptor.create(
					min,
					minInc.extractBoolean(),
					max,
					maxInc.extractBoolean()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers(),
					BOOLEAN.o(),
					IntegerRangeTypeDescriptor.extendedIntegers(),
					BOOLEAN.o()),
				INTEGER_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 8:</strong> Answer the {@linkplain
	 * ExtendedNumberDescriptor lower bound}. The client can ask the
	 * {@linkplain IntegerRangeTypeDescriptor integer range} if it includes the
	 * answer to determine whether it is inclusive or exclusive.
	 */
	prim8_LowerBound_range(8, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject range = args.get(0);
			return interpreter.primitiveSuccess(range.lowerBound());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					INTEGER_TYPE.o()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 9:</strong> Answer the {@linkplain
	 * ExtendedNumberDescriptor upper bound}. The client can ask the
	 * {@linkplain IntegerRangeTypeDescriptor integer range} if it includes the
	 * answer to determine whether it is inclusive or exclusive.
	 */
	prim9_UpperBound_range(9, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject range = args.get(0);
			return interpreter.primitiveSuccess(range.upperBound());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					INTEGER_TYPE.o()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 10:</strong> There are two possibilities.  The
	 * {@linkplain ContainerDescriptor variable} is mutable, in which case we
	 * want to destroy it, or the variable is immutable, in which case we want
	 * to make sure the extracted value becomes immutable (in case the variable
	 * is being held onto by something). Since the primitive invocation code is
	 * going to erase it if it's mutable anyhow, only the second case requires
	 * any real work.
	 */
	prim10_GetValue_var(10, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject var = args.get(0);
			final AvailObject value = var.value();
			if (value.equalsVoid())
			{
				return interpreter.primitiveFailure(
					E_CANNOT_READ_UNASSIGNED_VARIABLE);
			}
			if (!var.descriptor().isMutable())
			{
				value.makeImmutable();
			}
			return interpreter.primitiveSuccess(value);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(CONTAINER.o()),
				ALL.o());
		}
	},

	/**
	 * <strong>Primitive 11:</strong> Assign the {@linkplain AvailObject value}
	 * to the {@linkplain ContainerDescriptor variable}.
	 */
	prim11_SetValue_var_value(11, 2, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject var = args.get(0);
			final AvailObject value = args.get(1);
			if (!value.isInstanceOfSubtypeOf(var.type().innerType()))
			{
				return interpreter.primitiveFailure(
					E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE_INTO_VARIABLE);
			}
			var.setValue(value);
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTAINER.o(),
					ALL.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 12:</strong> Clear the {@linkplain ContainerDescriptor
	 * variable}.
	 */
	prim12_ClearValue_var(12, 1, CanInline, HasSideEffect, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject var = args.get(0);
			var.clearValue();
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(CONTAINER.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 13:</strong> Create a {@linkplain
	 * ContainerTypeDescriptor variable type} using the given inner type.
	 */
	prim13_CreateContainerType_type(13, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject type = args.get(0);
			return interpreter.primitiveSuccess(
				ContainerTypeDescriptor.wrapInnerType(type));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TYPE.o()),
				CONTAINER_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 14:</strong> Extract the inner type of a {@linkplain
	 * ContainerTypeDescriptor variable type}.
	 */
	prim14_InnerType_type(14, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject type = args.get(0);
			return interpreter.primitiveSuccess(type.innerType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTAINER_TYPE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 15:</strong> Swap the contents of two {@linkplain
	 * ContainerDescriptor variables}.
	 */
	prim15_Swap_var1_var2(15, 2, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject var1 = args.get(0);
			final AvailObject var2 = args.get(1);
			if (!var1.type().equals(var2.type()))
			{
				return interpreter.primitiveFailure(
					E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES);
			}
			final AvailObject value1 = var1.getValue();
			final AvailObject value2 = var2.getValue();
			var1.setValue(value2);
			var2.setValue(value1);
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTAINER.o(),
					CONTAINER.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 16:</strong> Create a {@linkplain
	 * ContainerDescriptor variable} with the given inner
	 * type.
	 */
	prim16_CreateContainer_innerType(16, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject innerType = args.get(0);
			return interpreter.primitiveSuccess(
				ContainerDescriptor.forInnerType(innerType));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TYPE.o()),
				CONTAINER.o());
		}
	},

	/**
	 * <strong>Primitive 17:</strong> Answer {@linkplain TrueDescriptor true} if
	 * the {@linkplain ContainerDescriptor variable} is unassigned (has no
	 * value).
	 */
	prim17_HasNoValue_var(17, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject var = args.get(0);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(var.value().equalsVoid()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTAINER.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 18:</strong> Get the value of the {@linkplain
	 * ContainerDescriptor variable}, clear the variable, then answer the
	 * previously extracted {@linkplain AvailObject value}. This operation
	 * allows store-back patterns to be efficiently implemented in Level One
	 * code while keeping the interpreter itself thread-safe and debugger-safe.
	 */
	prim18_GetClearing_var(18, 1, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject var = args.get(0);
			final AvailObject valueObject = var.value();
			if (valueObject.equalsVoid())
			{
				return interpreter.primitiveFailure(
					E_CANNOT_READ_UNASSIGNED_VARIABLE);

			}
			var.clearValue();
			return interpreter.primitiveSuccess(valueObject);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTAINER.o()),
				ALL.o());
		}

	},

	/**
	 * <strong>Primitive 20:</strong> Get the priority of the given {@linkplain
	 * ProcessDescriptor process}.
	 */
	prim20_GetPriority_processObject(20, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject processObject = args.get(0);
			return interpreter.primitiveSuccess(
				processObject.priority());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PROCESS.o()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 21:</strong> Set the priority of the given {@linkplain
	 * ProcessDescriptor process}.
	 */
	prim21_SetPriority_processObject_newPriority(21, 2, Unknown, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject processObject = args.get(0);
			final AvailObject newPriority = args.get(1);
			processObject.priority(newPriority);
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PROCESS.o(),
					IntegerRangeTypeDescriptor.extendedIntegers()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 22:</strong> Suspend the given {@linkplain
	 * ProcessDescriptor process}. Ignore if the process is already suspended.
	 */
	prim22_Suspend_processObject(22, 1, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject processObject = args.get(0);
			error("process suspend is not yet implemented");
			return interpreter.primitiveFailure("zzz"); //TODO
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PROCESS.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 23:</strong> Resume the given {@linkplain
	 * ProcessDescriptor process}. Ignore if the process is already running.
	 */
	prim23_Resume_processObject(23, 1, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject processObject = args.get(0);
			error("process resume is not yet implemented");
			return interpreter.primitiveFailure("zzz"); //TODO
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PROCESS.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 24:</strong> Terminate the given {@linkplain
	 * ProcessDescriptor process}. Ignore if the process is already terminated.
	 */
	prim24_Terminate_processObject(24, 1, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject processObject = args.get(0);
			error("process terminate is not yet implemented");
			return interpreter.primitiveFailure("zzz"); //TODO
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PROCESS.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 25:</strong> Answer the currently running {@linkplain
	 * ProcessDescriptor process}.
	 */
	prim25_CurrentProcess(25, 0, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 0;
			return interpreter.primitiveSuccess(
				interpreter.process().makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(),
				PROCESS.o());
		}
	},

	/**
	 * <strong>Primitive 26:</strong> Lookup the given {@linkplain
	 * CyclicTypeDescriptor name} (key) in the variables of the given
	 * {@linkplain ProcessDescriptor process}.
	 */
	prim26_LookupProcessVariable_processObject_key(26, 2, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject processObject = args.get(0);
			final AvailObject key = args.get(1);
			final AvailObject globals = processObject.processGlobals();
			if (!globals.hasKey(key))
			{
				return interpreter.primitiveFailure(
					E_NO_SUCH_PROCESS_VARIABLE);
			}
			return interpreter.primitiveSuccess(
				globals.mapAt(key).makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PROCESS.o(),
					CYCLIC_TYPE.o()),
				ALL.o());
		}
	},

	/**
	 * <strong>Primitive 27:</strong> Associate the given value with the given
	 * {@linkplain CyclicTypeDescriptor name} (key) in the variables of the
	 * given {@linkplain ProcessDescriptor process}.
	 */
	prim27_SetProcessVariable_processObject_key_value(
		27, 3, CanInline, HasSideEffect, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
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
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PROCESS.o(),
					CYCLIC_TYPE.o(),
					ALL.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 28:</strong> Wait for the given semaphore.
	 */
	prim28_SemaphoreWait_semaphore(28, 1, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject semaphore = args.get(0);
			error("This semaphore primitive is not yet implemented");
			return interpreter.primitiveFailure(
				"semaphore primitive not implemented");
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SEMAPHORE.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 29:</strong> Signal the given semaphore.
	 */
	prim29_SemaphoreSignal_semaphore(29, 1, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			@SuppressWarnings("unused")
			final AvailObject semaphore = args.get(0);
			error("This semaphore primitive is not yet implemented");
			return interpreter.primitiveFailure(
				"semaphore primitive not implemented");
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SEMAPHORE.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 30:</strong> Answer the type of the given object.
	 */
	prim30_Type_value(30, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject value = args.get(0);
			return interpreter.primitiveSuccess(value.type());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ALL.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 31:</strong> Answer the type union of the specified
	 * {@linkplain TypeDescriptor types}.
	 */
	prim31_TypeUnion_type1_type2(31, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			return interpreter.primitiveSuccess(
				type1.typeUnion(type2).makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TYPE.o(),
					TYPE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 32:</strong> Answer the type intersection of the
	 * specified {@linkplain TypeDescriptor types}.
	 */
	prim32_TypeIntersection_type1_type2(32, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			return interpreter.primitiveSuccess(
				type1.typeIntersection(type2).makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TYPE.o(),
					TYPE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 33:</strong> Answer whether type1 is a subtype of type2
	 * (or equal).
	 */
	prim33_IsSubtypeOf_type1_type2(33, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject type1 = args.get(0);
			final AvailObject type2 = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(type1.isSubtypeOf(type2)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TYPE.o(),
					TYPE.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 34:</strong> Create a closure type from a tuple of
	 * argument types and a return type.
	 */
	prim34_CreateClosureType_argTypes_returnType(34, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject argTypes = args.get(0);
			final AvailObject returnType = args.get(1);
			return interpreter.primitiveSuccess(
				ClosureTypeDescriptor.create(
					argTypes,
					returnType));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						TYPE.o()),
					TYPE.o()),
				CLOSURE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 35:</strong> Answer a tuple type describing the
	 * arguments accepted by the closure type.
	 */
	prim35_ClosureTypeNumArgs_closureType(35, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			return interpreter.primitiveSuccess(
				closureType.argsTupleType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CLOSURE_TYPE.o()),
				TUPLE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 36:</strong> Answer the type of the argument at the
	 * given index within the given closureType.
	 */
	prim36_ArgTypeAt_closureType_index(36, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject closureType = args.get(0);
			final AvailObject indexObject = args.get(1);

			if (!indexObject.isInt())
			{
				return interpreter.primitiveFailure(
					E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			final int index = indexObject.extractInt();
			final AvailObject argumentType =
				closureType.argsTupleType().typeAtIndex(index);
			return interpreter.primitiveSuccess(argumentType);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CLOSURE_TYPE.o(),
					IntegerRangeTypeDescriptor.naturalNumbers()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 37:</strong> Answer the return type of the given
	 * closureType.
	 */
	prim37_ReturnType_closureType(37, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			return interpreter.primitiveSuccess(closureType.returnType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CLOSURE_TYPE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 38:</strong> Answer the union of the types in the given
	 * tuple of types.
	 */
	prim38_UnionOfTupleOfTypes_tupleOfTypes(38, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tupleOfTypes = args.get(0);
			AvailObject unionObject = TERMINATES.o();
			for (final AvailObject aType : tupleOfTypes)
			{
				unionObject = unionObject.typeUnion(aType);
			}
			return interpreter.primitiveSuccess(unionObject);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						TYPE.o())),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 39:</strong> Answer the most general closure type with
	 * the given return type.
	 */
	prim39_CreateGeneralClosureType_returnType(39, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject returnType = args.get(0);
			return interpreter.primitiveSuccess(
				ClosureTypeDescriptor.createWithArgumentTupleType(
					TERMINATES.o(),
					returnType,
					SetDescriptor.empty()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TYPE.o()),
				CLOSURE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 40:</strong> {@linkplain ClosureDescriptor Closure}
	 * evaluation, given a {@linkplain TupleDescriptor tuple} of arguments.
	 * Check the {@linkplain TypeDescriptor types} dynamically to prevent
	 * corruption of the type system. Fail if the arguments are not of the
	 * required types.
	 */
	prim40_InvokeWithTuple_block_argTuple(40, 2, Invokes)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject block = args.get(0);
			final AvailObject argTuple = args.get(1);
			final AvailObject blockType = block.type();
			final int numArgs = argTuple.tupleSize();
			if (block.code().numArgs() != numArgs)
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS);
			}
			final List<AvailObject> callArgs =
				new ArrayList<AvailObject>(numArgs);
			final AvailObject tupleType = blockType.argsTupleType();
			for (int i = 1; i <= numArgs; i++)
			{
				final AvailObject anArg = argTuple.tupleAt(i);
				if (!anArg.isInstanceOfSubtypeOf(tupleType.typeAtIndex(i)))
				{
					return interpreter.primitiveFailure(
						E_INCORRECT_ARGUMENT_TYPE);
				}
				//  Transfer the argument into callArgs.
				callArgs.add(anArg);
			}
			return interpreter.invokeClosureArguments(
				block,
				callArgs);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ClosureTypeDescriptor.forReturnType(VOID_TYPE.o()),
					TupleTypeDescriptor.mostGeneralTupleType()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 43:</strong> Invoke either the {@link
	 * ClosureDescriptor trueBlock} or the {@code falseBlock}, depending on
	 * {@link BooleanDescriptor aBoolean}.
	 */
	prim43_IfThenElse_aBoolean_trueBlock_falseBlock(43, 3, Invokes, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject aBoolean = args.get(0);
			final AvailObject trueBlock = args.get(1);
			final AvailObject falseBlock = args.get(2);
			assert trueBlock.code().numArgs() == 0;
			assert falseBlock.code().numArgs() == 0;
			if (aBoolean.extractBoolean())
			{
				return interpreter.invokeClosureArguments (
					trueBlock,
					Collections.<AvailObject>emptyList());
			}
			return interpreter.invokeClosureArguments (
				falseBlock,
				Collections.<AvailObject>emptyList());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					BOOLEAN.o(),
					ClosureTypeDescriptor.create(
						TupleDescriptor.from(),
						VOID_TYPE.o()),
					ClosureTypeDescriptor.create(
						TupleDescriptor.from(),
						VOID_TYPE.o())),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 44:</strong> Invoke the {@link ClosureDescriptor
	 * trueBlock} if {@link BooleanDescriptor aBoolean} is true, otherwise just
	 * answer {@linkplain VoidDescriptor#voidObject() void}.
	 */
	prim44_IfThen_aBoolean_trueBlock(44, 2, Invokes, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject aBoolean = args.get(0);
			final AvailObject trueBlock = args.get(1);
			assert trueBlock.code().numArgs() == 0;
			if (aBoolean.extractBoolean())
			{
				return interpreter.invokeClosureArguments (
					trueBlock,
					Collections.<AvailObject>emptyList());
			}
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					BOOLEAN.o(),
					ClosureTypeDescriptor.create(
						TupleDescriptor.from(),
						VOID_TYPE.o())),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 45:</strong> Run the zero-argument {@linkplain
	 * ClosureDescriptor closure}, ignoring the leading {@linkplain
	 * BooleanDescriptor boolean} argument. This is used for short-circuit
	 * evaluation.
	 */
	prim45_ShortCircuitHelper_ignoredBool_block(45, 2, Invokes, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			@SuppressWarnings("unused")
			final AvailObject ignoredBool = args.get(0);
			final AvailObject block = args.get(1);
			assert block.code().numArgs() == 0;
			return interpreter.invokeClosureArguments (
				block,
				Collections.<AvailObject>emptyList());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					BOOLEAN.o(),
					ClosureTypeDescriptor.create(
						TupleDescriptor.from(),
						BOOLEAN.o())),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 49:</strong> Create a {@linkplain
	 * ContinuationDescriptor continuation}. Will execute as unoptimized code
	 * via the default Level Two {@linkplain L2ChunkDescriptor chunk}.
	 */
	prim49_CreateContinuation_callerHolder_closure_pc_stackp_stack(
		49, 5, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
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
			cont.levelTwoChunkOffset(
				L2ChunkDescriptor.unoptimizedChunk(),
				L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
			for (int i = 1, end = stack.tupleSize(); i <= end; i++)
			{
				cont.argOrLocalOrStackAtPut(i, stack.tupleAt(i));
			}
			return interpreter.primitiveSuccess(cont);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ContainerTypeDescriptor.wrapInnerType(CONTINUATION.o()),
					ClosureTypeDescriptor.topInstance(),
					IntegerRangeTypeDescriptor.naturalNumbers(),
					IntegerRangeTypeDescriptor.naturalNumbers(),
					TupleTypeDescriptor.mostGeneralTupleType()),
				CONTINUATION.o());
		}
	},

	/**
	 * <strong>Primitive 50:</strong> Answer the {@linkplain
	 * ClosureTypeDescriptor closure type} corresponding to the given
	 * {@linkplain ContinuationTypeDescriptor continuation type}.
	 */
	prim50_ContinuationTypeToClosureType_continuationType(
		50, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject continuationType = args.get(0);
			return interpreter.primitiveSuccess(
				continuationType.closureType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION_TYPE.o()),
				CLOSURE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 51:</strong> Answer a {@linkplain
	 * ContinuationTypeDescriptor continuation type} that uses the
	 * given {@linkplain ClosureTypeDescriptor closure type}.
	 */
	prim51_ClosureTypeToContinuationType_closureType(51, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject closureType = args.get(0);
			return interpreter.primitiveSuccess(
				ContinuationTypeDescriptor.forClosureType(closureType));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CLOSURE_TYPE.o()),
				CONTINUATION_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 52:</strong> Answer the caller of a {@linkplain
	 * ContinuationDescriptor continuation}. Fail if there is no caller.
	 */
	prim52_ContinuationCaller_con(52, 1, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			final AvailObject caller = con.caller();
			if (caller.equalsVoid())
			{
				return interpreter.primitiveFailure(
					E_CONTINUATION_HAS_NO_CALLER);
			}
			return interpreter.primitiveSuccess(caller);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION.o()),
				CONTINUATION.o());
		}
	},

	/**
	 * <strong>Primitive 53:</strong> Answer the {@linkplain ClosureDescriptor
	 * closure} of a {@linkplain ContinuationDescriptor continuation}.
	 */
	prim53_ContinuationClosure_con(53, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			return interpreter.primitiveSuccess(con.closure());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION.o()),
				ClosureTypeDescriptor.topInstance());
		}
	},

	/**
	 * <strong>Primitive 54:</strong> Answer the program counter of a
	 * {@linkplain ContinuationDescriptor continuation}. This is the index of
	 * the current instruction in the continuation's {@linkplain
	 * ClosureDescriptor closure}'s {@linkplain CompiledCodeDescriptor code}'s
	 * {@linkplain TupleDescriptor tuple} of nybblecodes.
	 */
	prim54_ContinuationPC_con(54, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(con.pc()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION.o()),
				IntegerRangeTypeDescriptor.naturalNumbers());
		}
	},

	/**
	 * <strong>Primitive 55:</strong> Answer a {@linkplain
	 * ContinuationDescriptor continuation}'s stack pointer. This is the index
	 * of the top-of-stack within the {@link
	 * ContinuationDescriptor.ObjectSlots#FRAME_AT_ frame slots} of the
	 * continuation. For an empty stack its value equals the number of frame
	 * slots plus one.
	 */
	prim55_ContinuationStackPointer_con(55, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(con.stackp()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION.o()),
				IntegerRangeTypeDescriptor.naturalNumbers());
		}
	},

	/**
	 * <strong>Primitive 56:</strong> Restart the given {@linkplain
	 * ContinuationDescriptor continuation}, but passing in the given
	 * {@linkplain TupleDescriptor tuple} of arguments. Make sure it's a
	 * label-like continuation rather than a call-like, because a call-like
	 * continuation has the expected return type already pushed on the stack,
	 * and requires the return value, after checking against that type, to
	 * overwrite the type in the stack (without affecting the stack depth). Fail
	 * if the continuation's {@linkplain ClosureDescriptor closure} is not
	 * capable of accepting the given arguments.
	 */
	prim56_RestartContinuationWithArguments_con_arguments(
		56, 2, SwitchesContinuation)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
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
			final int numArgs = itsCode.numArgs();
			if (numArgs != arguments.tupleSize())
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS);
			}
			if (!itsCode.closureType().acceptsTupleOfArguments(arguments))
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_ARGUMENT_TYPE);
			}
			for (int i = 1; i <= numArgs; i++)
			{
				conCopy.argOrLocalOrStackAtPut(i, arguments.tupleAt(i));
			}
			final int numLocals = itsCode.numLocals();
			for (int i = 1; i <= numLocals; i++)
			{
				conCopy.argOrLocalOrStackAtPut(
					numArgs + i,
					ContainerDescriptor.forOuterType(itsCode.localTypeAt(i)));
			}
			interpreter.prepareToExecuteContinuation(conCopy);
			return CONTINUATION_CHANGED;
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION.o(),
					TupleTypeDescriptor.mostGeneralTupleType()),
				TERMINATES.o());
		}
	},

	/**
	 * <strong>Primitive 57:</strong> Exit the given {@linkplain
	 * ContinuationDescriptor continuation} (returning result to its caller).
	 */
	prim57_ExitContinuationWithResult_con_result(57, 2, SwitchesContinuation)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
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
			final AvailObject caller = con.caller();
			final AvailObject linkStrengthenedType = caller.stackAt(
				caller.stackp());
			assert linkStrengthenedType.isSubtypeOf(expectedType);
			if (!result.isInstanceOfSubtypeOf(expectedType))
			{
				// Wasn't strong enough to meet the block's declared type.
				return interpreter.primitiveFailure(
					E_CONTINUATION_EXPECTED_STRONGER_TYPE);
			}
			if (!result.isInstanceOfSubtypeOf(linkStrengthenedType))
			{
				// Wasn't strong enough to meet the *call site's* type.
				// A useful distinction when we start to record primitive
				// failure reason codes.
				return interpreter.primitiveFailure(
					E_CONTINUATION_EXPECTED_STRONGER_TYPE);
			}
			final AvailObject targetCon = caller.ensureMutable();
			targetCon.stackAtPut(targetCon.stackp(), result);
			interpreter.prepareToExecuteContinuation(targetCon);
			return CONTINUATION_CHANGED;
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION.o(),
					ALL.o()),
				TERMINATES.o());
		}
	},

	/**
	 * <strong>Primitive 58:</strong> Restart the given {@linkplain
	 * ContinuationDescriptor continuation}. Make sure it's a label-like
	 * continuation rather than a call-like, because a call-like continuation
	 * requires a value to be stored on its stack in order to resume it,
	 * something this primitive does not do.
	 */
	prim58_RestartContinuation_con(58, 1, SwitchesContinuation)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
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
			for (int i = 1, end = itsCode.numLocals(); i <= end; i++)
			{
				conCopy.argOrLocalOrStackAtPut(
					itsCode.numArgs() + i,
					ContainerDescriptor.forOuterType(
						itsCode.localTypeAt(i)));
			}
			interpreter.prepareToExecuteContinuation(conCopy);
			return CONTINUATION_CHANGED;
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION.o()),
				TERMINATES.o());
		}
	},

	/**
	 * <strong>Primitive 59:</strong> Answer a {@linkplain TupleDescriptor
	 * tuple} containing the {@linkplain ContinuationDescriptor continuation}'s
	 * stack data. Substitute the integer {@linkplain IntegerDescriptor#zero()
	 * zero} for any {@linkplain VoidDescriptor#voidObject() void} values.
	 */
	prim59_ContinuationStackData_con(59, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject con = args.get(0);
			final int count = con.closure().code().numArgsAndLocalsAndStack();
			final AvailObject tuple = ObjectTupleDescriptor.mutable().create(
				count);
			for (int i = 1; i <= count; i++)
			{
				AvailObject entry = con.argOrLocalOrStackAt(i);
				if (entry.equalsVoid())
				{
					entry = IntegerDescriptor.zero();
				}
				tuple.tupleAtPut(i, entry);
			}
			tuple.makeSubobjectsImmutable();
			return interpreter.primitiveSuccess(tuple);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CONTINUATION.o()),
				TupleTypeDescriptor.mostGeneralTupleType());
		}
	},

	/**
	 * <strong>Primitive 60:</strong> Compare for equality. Answer a {@linkplain
	 * BooleanDescriptor boolean}.
	 */
	prim60_Equality_a_b(60, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(a.equals(b)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ALL.o(),
					ALL.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 61:</strong> Convert a {@linkplain MapDescriptor map}
	 * from fields to values into an {@linkplain ObjectDescriptor object}.
	 */
	prim61_MapToObject_map(61, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			return interpreter.primitiveSuccess(
				ObjectDescriptor.objectFromMap(map));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						CYCLIC_TYPE.o(),
						ALL.o())),
				ObjectTypeDescriptor.objectTypeFromMap(
					MapDescriptor.empty()));
		}
	},

	/**
	 * <strong>Primitive 62:</strong> Convert an {@linkplain ObjectDescriptor
	 * object} into a {@linkplain MapDescriptor map} from fields to values.
	 */
	prim62_ObjectToMap_object(62, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject object = args.get(0);
			return interpreter.primitiveSuccess(object.fieldMap());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ObjectTypeDescriptor.objectTypeFromMap(
						MapDescriptor.empty())),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					CYCLIC_TYPE.o(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 63:</strong> Convert a {@linkplain MapDescriptor map}
	 * from fields to {@linkplain TypeDescriptor types} into an {@linkplain
	 * ObjectTypeDescriptor object type}.
	 */
	prim63_MapToObjectType_map(63, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			return interpreter.primitiveSuccess(
				ObjectTypeDescriptor.objectTypeFromMap(map));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						CYCLIC_TYPE.o(),
						ALL.o())),
				ObjectMetaDescriptor.fromObjectTypeAndLevel(
					ObjectTypeDescriptor.objectTypeFromMap(
						MapDescriptor.empty()),
					IntegerDescriptor.one().type()));
		}
	},

	/**
	 * <strong>Primitive 64:</strong> Convert an {@linkplain
	 * ObjectTypeDescriptor object type} into a {@linkplain MapDescriptor map}
	 * from fields to types.
	 */
	prim64_ObjectTypeToMap_objectType(64, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectType = args.get(0);
			return interpreter.primitiveSuccess(objectType.fieldTypeMap());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ObjectMetaDescriptor.fromObjectTypeAndLevel(
						ObjectTypeDescriptor.objectTypeFromMap(
							MapDescriptor.empty()),
						IntegerDescriptor.one().type())),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					CYCLIC_TYPE.o(),
					TYPE.o()));
		}
	},

	/**
	 * <strong>Primitive 65:</strong> Answer an {@linkplain ObjectMetaDescriptor
	 * instance} of the specified {@linkplain ObjectMetaDescriptor object
	 * metatype}.
	 */
	prim65_ObjectMetaInstance_objectMeta(65, 1, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectMeta = args.get(0);
			if (objectMeta.objectMetaLevels().lowerBound().equals(
				IntegerDescriptor.one()))
			{
				if (!objectMeta.objectMetaLevels().upperBound().equals(
					IntegerDescriptor.one()))
				{
					return interpreter.primitiveFailure(
						E_AMBIGUOUS_INSTANCE_OF_METATYPE_SMEAR);
				}
			}

			return interpreter.primitiveSuccess(objectMeta.instance());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ObjectMetaDescriptor.fromObjectTypeAndLevel(
						ObjectTypeDescriptor.objectTypeFromMap(
							MapDescriptor.empty()),
						IntegerRangeTypeDescriptor.create(
							IntegerDescriptor.fromInt(1),
							true,
							InfinityDescriptor.positiveInfinity(),
							true))),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 66:</strong> Construct a new {@linkplain
	 * ObjectMetaDescriptor object metatype} with the given base {@linkplain
	 * ObjectTypeDescriptor object type} and {@linkplain
	 * IntegerRangeTypeDescriptor range} of levels.
	 */
	prim66_CreateObjectMeta_objectType_levels(66, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject objectType = args.get(0);
			final AvailObject levels = args.get(1);
			if (levels.lowerBound().lessThan(IntegerDescriptor.one()))
			{
				return interpreter.primitiveFailure(
					E_NONPOSITIVE_METATYPE_LEVEL);
			}
			return interpreter.primitiveSuccess(
				ObjectMetaDescriptor.fromObjectTypeAndLevel(
					objectType,
					levels));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ObjectMetaDescriptor.fromObjectTypeAndLevel(
						ObjectTypeDescriptor.objectTypeFromMap(
							MapDescriptor.empty()),
						IntegerRangeTypeDescriptor.singleInteger(
							IntegerDescriptor.one())),
					INTEGER_TYPE.o()),
				ObjectMetaDescriptor.fromObjectTypeAndLevel(
					ObjectTypeDescriptor.objectTypeFromMap(
						MapDescriptor.empty()),
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(2),
						true,
						InfinityDescriptor.positiveInfinity(),
						true)));
		}
	},

	/**
	 * <strong>Primitive 67:</strong> Answer the name of a {@linkplain
	 * PrimitiveTypeDescriptor primitive type}.
	 */
	prim67_NameOfPrimitiveType_primType(67, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject primType = args.get(0);
			return interpreter.primitiveSuccess(primType.name());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PRIMITIVE_TYPE.o()),
				TupleTypeDescriptor.stringTupleType());
		}
	},

	/**
	 * <strong>Primitive 68:</strong> Assign a name to a {@linkplain
	 * ObjectTypeDescriptor user-defined object type}. This can be useful for
	 * debugging.
	 */
	prim68_RecordNewTypeName_userType_name(68, 2, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;

			final AvailObject userType = args.get(0);
			final AvailObject name = args.get(1);

			userType.makeImmutable();
			name.makeImmutable();
			interpreter.runtime().setNameForType(userType, name);

			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ObjectMetaDescriptor.fromObjectTypeAndLevel(
						ObjectTypeDescriptor.objectTypeFromMap(
							MapDescriptor.empty()),
						IntegerDescriptor.one().type()),
					TupleTypeDescriptor.stringTupleType()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 69:</strong> Answer the user-assigned name of the
	 * specified {@linkplain ObjectTypeDescriptor user-defined object type}.
	 */
	prim69_TypeName_userType(69, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject userType = args.get(0);

			final AvailObject name =
				interpreter.runtime().nameForType(userType);
			if (name == null)
			{
				return interpreter.primitiveFailure(
					E_OBJECT_TYPE_HAS_NO_USER_DEFINED_NAME);
			}
			return interpreter.primitiveSuccess(name);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ObjectMetaDescriptor.fromObjectTypeAndLevel(
						ObjectTypeDescriptor.objectTypeFromMap(
							MapDescriptor.empty()),
						IntegerDescriptor.one().type())),
				TupleTypeDescriptor.stringTupleType());
		}
	},

	/**
	 * <strong>Primitive 70:</strong> Construct a {@linkplain ClosureDescriptor
	 * closure} accepting {@code numArgs} arguments (each of type {@link
	 * PrimitiveTypeDescriptor all}) and returning {@code constantResult}.
	 */
	prim70_CreateConstantBlock_numArgs_constantResult(
		70, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject numArgs = args.get(0);
			final AvailObject constantResult = args.get(1);
			return interpreter.primitiveSuccess(
				ClosureDescriptor.createStubForNumArgsConstantResult(
					numArgs.extractInt(),
					constantResult));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ALL.o()),
				ClosureTypeDescriptor.createWithArgumentTupleType(
					TERMINATES.o(),
					ALL.o(),
					SetDescriptor.empty()));
		}
	},

	/**
	 * <strong>Primitive 71:</strong> Construct a {@linkplain ClosureDescriptor
	 * closure} that takes arguments whose {@linkplain TypeDescriptor types} are
	 * specified in {@code argTypes}, and returns the result of invoking the
	 * given {@linkplain CyclicTypeDescriptor message} with {@code firstArg} as
	 * the first argument and the {@linkplain TupleDescriptor tuple} of
	 * arguments as the second argument. Assume the argument types have already
	 * been tried in each applicable {@code requiresBlock}, and that the result
	 * type agrees with each {@code returnsBlock}.
	 */
	prim71_CreateStubInvokingWithFirstArgAndCallArgsAsTuple_argTypes_message_firstArg_resultType(
		71, 4, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 4;
			final AvailObject argTypes = args.get(0);
			final AvailObject message = args.get(1);
			final AvailObject firstArg = args.get(2);
			final AvailObject resultType = args.get(3);
			final AvailObject impSet = interpreter.runtime().methodsAt(message);
			if (impSet.equalsVoid())
			{
				return interpreter.primitiveFailure(
					E_NO_IMPLEMENTATION_SET);
			}
			return interpreter.primitiveSuccess(
				ClosureDescriptor.createStubWithArgTypes(
					argTypes,
					impSet,
					firstArg,
					resultType));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						TYPE.o()),
					CYCLIC_TYPE.o(),
					ALL.o(),
					TYPE.o()),
				ClosureTypeDescriptor.createWithArgumentTupleType(
					TERMINATES.o(),
					VOID_TYPE.o(),
					SetDescriptor.empty()));
		}
	},

	/**
	 * <strong>Primitive 72:</strong> Answer the {@linkplain
	 * CompiledCodeDescriptor compiled code} within this {@linkplain
	 * ClosureDescriptor closure}.
	 */
	prim72_CompiledCodeOfClosure_aClosure(72, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject aClosure = args.get(0);
			return interpreter.primitiveSuccess(aClosure.code());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ClosureTypeDescriptor.topInstance()),
				COMPILED_CODE.o());
		}
	},

	/**
	 * <strong>Primitive 73:</strong> Answer the {@linkplain TupleDescriptor
	 * tuple} of outer variables captured by this {@linkplain ClosureDescriptor
	 * closure}.
	 */
	prim73_OuterVariables_aClosure(73, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject aClosure = args.get(0);
			final AvailObject newTupleObject =
				ObjectTupleDescriptor.mutable().create(aClosure.numOuterVars());
			newTupleObject.hashOrZero(0);
			CanAllocateObjects(false);
			for (int i = 1, end = aClosure.numOuterVars(); i <= end; i++)
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
			return interpreter.primitiveSuccess(newTupleObject);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ClosureTypeDescriptor.topInstance()),
				TupleTypeDescriptor.mostGeneralTupleType());
		}
	},

	/**
	 * <strong>Primitive 74:</strong> Answer a {@linkplain ClosureDescriptor
	 * closure} built from the {@linkplain CompiledCodeDescriptor compiled code}
	 * and the outer variables.
	 */
	prim74_CreateClosure_compiledCode_outers(74, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject compiledCode = args.get(0);
			final AvailObject outers = args.get(1);
			if (outers.tupleSize() != compiledCode.numOuters())
			{
				return interpreter.primitiveFailure(
					E_WRONG_NUMBER_OF_OUTERS);
			}
			return interpreter.primitiveSuccess(
				ClosureDescriptor.create(compiledCode, outers));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o(),
					TupleTypeDescriptor.mostGeneralTupleType()),
				ClosureTypeDescriptor.topInstance());
		}
	},

	/**
	 * <strong>Primitive 80:</strong> Answer the size of the {@linkplain
	 * MapDescriptor map}. This is the number of entries, which is also the
	 * number of keys.
	 */
	prim80_MapSize_map(80, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(map.mapSize()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o(),
						ALL.o())),
				IntegerRangeTypeDescriptor.wholeNumbers());
		}
	},

	/**
	 * <strong>Primitive 81:</strong> Check if the key is present in the
	 * {@linkplain MapDescriptor map}.
	 */
	prim81_MapHasKey_map_key(81, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(map.hasKey(key)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o(),
						ALL.o()),
					ALL.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 82:</strong> Look up the key in the {@linkplain
	 * MapDescriptor map}, answering the corresponding value.
	 */
	prim82_MapAtKey_map_key(82, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			if (!map.hasKey(key))
			{
				return interpreter.primitiveFailure(E_KEY_NOT_FOUND);
			}
			return interpreter.primitiveSuccess(map.mapAt(key));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o(),
						ALL.o()),
					ALL.o()),
				ALL.o());
		}
	},

	/**
	 * <strong>Primitive 83:</strong> Answer a new {@linkplain MapDescriptor
	 * map} like the given map, but also including the binding between {@code
	 * key} and {@code value}. Overwrite any existing value if the key is
	 * already present.
	 */
	prim83_MapReplacingKey_map_key_value(83, 3, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			final AvailObject value = args.get(2);
			return interpreter.primitiveSuccess(map.mapAtPuttingCanDestroy(
				key,
				value,
				true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o(),
						ALL.o()),
					ALL.o(),
					ALL.o()),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.naturalNumbers(),
					ALL.o(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 84:</strong> Answer a new {@linkplain MapDescriptor
	 * map}, but without the given key. Answer the original map if the key does
	 * not occur in it.
	 */
	prim84_MapWithoutKey_map_key(84, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject map = args.get(0);
			final AvailObject key = args.get(1);
			return interpreter.primitiveSuccess(
				map.mapWithoutKeyCanDestroy(key, true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o(),
						ALL.o()),
					ALL.o()),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ALL.o(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 85:</strong> Answer an empty {@linkplain MapDescriptor
	 * map}.
	 */
	prim85_CreateEmptyMap(85, 0, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 0;
			return interpreter.primitiveSuccess(MapDescriptor.empty());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.singleInteger(
						IntegerDescriptor.zero()),
					TERMINATES.o(),
					TERMINATES.o()));
		}
	},

	/**
	 * <strong>Primitive 86:</strong> Answer the keys of this {@linkplain
	 * MapDescriptor map} as a {@linkplain SetDescriptor set}.
	 */
	prim86_MapKeysAsSet_map(86, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			return interpreter.primitiveSuccess(map.keysAsSet());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o(),
						ALL.o())),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 87:</strong> Answer a {@linkplain MapTypeDescriptor map
	 * type} with the given type constraints.
	 */
	prim87_CreateMapType_Sizes_keyType_valueType(87, 3, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject sizes = args.get(0);
			final AvailObject keyType = args.get(1);
			final AvailObject valueType = args.get(2);
			if (sizes.lowerBound().lessThan(IntegerDescriptor.zero()))
			{
				return interpreter.primitiveFailure(
					E_NEGATIVE_SIZE);
			}
			return interpreter.primitiveSuccess(
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					sizes,
					keyType,
					valueType));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					INTEGER_TYPE.o(),
					TYPE.o(),
					TYPE.o()),
				MAP_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 88:</strong> Answer the {@linkplain
	 * IntegerRangeTypeDescriptor size range} of a {@linkplain MapTypeDescriptor
	 * map type}. This specifies the range of sizes a {@linkplain MapDescriptor
	 * map} can have while being considered an instance of this map type,
	 * assuming the keys' types and values' types also agree with those
	 * specified in the map type.
	 */
	prim88_MapTypeSizes_mapType(88, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			return interpreter.primitiveSuccess(mapType.sizeRange());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MAP_TYPE.o()),
				INTEGER_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 89:</strong> Answer the key {@linkplain TypeDescriptor
	 * type} of a {@linkplain MapTypeDescriptor map type}.
	 */
	prim89_MapTypeKeyType_mapType(89, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			return interpreter.primitiveSuccess(mapType.keyType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MAP_TYPE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 90:</strong> Answer the value {@linkplain
	 * TypeDescriptor type} of a {@linkplain MapTypeDescriptor map type}.
	 */
	prim90_MapTypeValueType_mapType(90, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject mapType = args.get(0);
			return interpreter.primitiveSuccess(mapType.valueType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MAP_TYPE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 91:</strong> Answer the values of this {@linkplain
	 * MapDescriptor map} as a {@linkplain TupleDescriptor tuple}, arbitrarily
	 * ordered.
	 */
	prim91_MapValuesAsTuple_map(91, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject map = args.get(0);
			return interpreter.primitiveSuccess(map.valuesAsTuple());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o(),
						ALL.o())),
				TupleTypeDescriptor.mostGeneralTupleType());
		}
	},

	/**
	 * <strong>Primitive 100:</strong> Answer the size of the {@linkplain
	 * SetDescriptor set}.
	 */
	prim100_SetSize_set(100, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject set = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(set.setSize()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o())),
				IntegerRangeTypeDescriptor.wholeNumbers());
		}
	},

	/**
	 * <strong>Primitive 101:</strong> Check if the {@linkplain AvailObject
	 * object} is an element of the {@linkplain SetDescriptor set}.
	 */
	prim101_SetHasElement_set_element(101, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject element = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(set.hasElement(element)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o()),
					ALL.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 102:</strong> Answer the union of two {@linkplain
	 * SetDescriptor sets}.
	 */
	prim102_SetUnion_set1_set2(102, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			return interpreter.primitiveSuccess(
				set1.setUnionCanDestroy(set2, true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o()),
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o())),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 103:</strong> Answer the intersection of two
	 * {@linkplain SetDescriptor sets}.
	 */
	prim103_SetIntersection_set1_set2(103, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			return interpreter.primitiveSuccess(
				set1.setIntersectionCanDestroy(set2, true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o()),
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o())),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 104:</strong> Answer the difference between two
	 * {@linkplain SetDescriptor sets} ({@code set1 - set2}).
	 */
	prim104_SetDifference_set1_set2(104, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			return interpreter.primitiveSuccess(
				set1.setMinusCanDestroy(set2, true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o()),
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o())),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 105:</strong> Answer a new {@linkplain SetDescriptor
	 * set} like the argument but including the new {@linkplain AvailObject
	 * element}. If it was already present, answer the original set.
	 */
	prim105_SetWith_set_newElement(105, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject newElement = args.get(1);
			return interpreter.primitiveSuccess(
				set.setWithElementCanDestroy(newElement, true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o()),
					ALL.o()),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.naturalNumbers(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 106:</strong> Answer a new {@linkplain SetDescriptor
	 * set} like the argument but without the excluded {@linkplain AvailObject
	 * element}. If it was already absent, answer the original set.
	 */
	prim106_SetWithout_set_excludedElement(106, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set = args.get(0);
			final AvailObject excludedElement = args.get(1);
			return interpreter.primitiveSuccess(
				set.setWithoutElementCanDestroy(excludedElement, true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o()),
					ALL.o()),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 107:</strong> Check if {@link SetDescriptor set1} is a
	 * subset of {@code set2}.
	 */
	prim107_SetIsSubset_set1_set2(107, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject set1 = args.get(0);
			final AvailObject set2 = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(set1.isSubsetOf(set2)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o()),
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o())),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 108:</strong> Answer the empty {@linkplain
	 * SetDescriptor set}.
	 */
	prim108_CreateEmptySet(108, 0, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 0;
			return interpreter.primitiveSuccess(SetDescriptor.empty());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.singleInteger(
						IntegerDescriptor.zero()),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 109:</strong> Convert a {@linkplain TupleDescriptor
	 * tuple} into a {@linkplain SetDescriptor set}.
	 */
	prim109_TupleToSet_tuple(109, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tuple = args.get(0);
			return interpreter.primitiveSuccess(tuple.asSet());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.mostGeneralTupleType()),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 110:</strong> Convert a {@linkplain SetDescriptor set}
	 * into an arbitrarily ordered {@linkplain TupleDescriptor tuple}. The
	 * conversion is unstable (two calls may produce different orderings).
	 */
	prim110_SetToTuple_set(110, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject set = args.get(0);
			return interpreter.primitiveSuccess(set.asTuple());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ALL.o())),
				TupleTypeDescriptor.mostGeneralTupleType());
		}
	},

	/**
	 * <strong>Primitive 111:</strong> Create a {@linkplain SetTypeDescriptor
	 * set type}.
	 */
	prim111_CreateSetType_sizeRange_contentType(111, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject sizeRange = args.get(0);
			final AvailObject contentType = args.get(1);
			if (sizeRange.lowerBound().lessThan(IntegerDescriptor.zero()))
			{
				interpreter.primitiveFailure(
					"size range must be positive");
			}
			return interpreter.primitiveSuccess(
				SetTypeDescriptor.setTypeForSizesContentType(
					sizeRange,
					contentType));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					INTEGER_TYPE.o(),
					TYPE.o()),
				SET_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 112:</strong> Extract a {@linkplain SetTypeDescriptor
	 * set type}'s {@linkplain IntegerRangeTypeDescriptor range} of sizes. This
	 * is the range of sizes that a {@linkplain SetDescriptor set} must fall in
	 * to be considered a member of the set type, assuming the elements all
	 * satisfy the set type's element {@linkplain TypeDescriptor type}.
	 */
	prim112_SetTypeSizes_setType(112, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject setType = args.get(0);
			return interpreter.primitiveSuccess(setType.sizeRange());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SET_TYPE.o()),
				INTEGER_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 113:</strong> Extract a {@linkplain SetTypeDescriptor
	 * set type}'s element {@linkplain TypeDescriptor type}.
	 */
	prim113_SetTypeElementType_setType(113, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject setType = args.get(0);
			return interpreter.primitiveSuccess(setType.contentType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SET_TYPE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 120:</strong> Create a new {@linkplain
	 * CyclicTypeDescriptor cyclic type} with the given name.
	 */
	prim120_CreateCyclicType_name(120, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject name = args.get(0);
			return interpreter.primitiveSuccess(
				CyclicTypeDescriptor.create(name));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				CYCLIC_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 121:</strong> Answer the name of a {@linkplain
	 * CyclicTypeDescriptor cyclic type}.
	 */
	prim121_CyclicTypeName_cyclicType(121, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cyclicType = args.get(0);
			return interpreter.primitiveSuccess(cyclicType.name());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o()),
				TupleTypeDescriptor.stringTupleType());
		}
	},

	/**
	 * <strong>Primitive 130:</strong> Answer the size of the {@linkplain
	 * TupleDescriptor tuple}.
	 */
	prim130_TupleSize_tuple(130, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tuple = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(tuple.tupleSize()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.mostGeneralTupleType()),
				IntegerRangeTypeDescriptor.wholeNumbers());
		}
	},

	/**
	 * <strong>Primitive 131:</strong> Look up an element in the {@linkplain
	 * TupleDescriptor tuple}.
	 */
	prim131_TupleAt_tuple_index(131, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject tuple = args.get(0);
			final AvailObject indexObject = args.get(1);
			if (!indexObject.isInt())
			{
				return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			final int index = indexObject.extractInt();
			if (index > tuple.tupleSize())
			{
				return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			return interpreter.primitiveSuccess(
				tuple.tupleAt(index));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.mostGeneralTupleType(),
					IntegerRangeTypeDescriptor.naturalNumbers()),
				ALL.o());
		}
	},

	/**
	 * <strong>Primitive 132:</strong> Answer a {@linkplain TupleDescriptor
	 * tuple} like the given one, but with an element changed as indicated.
	 */
	prim132_TupleReplaceAt_tuple_index_value(132, 3, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject tuple = args.get(0);
			final AvailObject indexObject = args.get(1);
			final AvailObject value = args.get(2);
			if (!indexObject.isInt())
			{
				return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			final int index = indexObject.extractInt();
			if (index > tuple.tupleSize())
			{
				return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			return interpreter.primitiveSuccess(tuple.tupleAtPuttingCanDestroy(
				index,
				value,
				true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.mostGeneralTupleType(),
					IntegerRangeTypeDescriptor.naturalNumbers(),
					ALL.o()),
				TupleTypeDescriptor.mostGeneralTupleType());
		}
	},

	/**
	 * <strong>Primitive 133:</strong> Build a {@linkplain TupleDescriptor
	 * tuple} with one element.
	 */
	prim133_CreateTupleSizeOne_soleElement(133, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject soleElement = args.get(0);
			final AvailObject newTupleObject =
				ObjectTupleDescriptor.mutable().create(1);
			newTupleObject.hashOrZero(0);
			newTupleObject.tupleAtPut(1, soleElement);
			return interpreter.primitiveSuccess(newTupleObject);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ALL.o()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInteger(
						IntegerDescriptor.one()),
					TupleDescriptor.empty(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 134:</strong> Answer the empty {@linkplain
	 * TupleDescriptor tuple}.
	 */
	prim134_CreateEmptyTuple(134, 0, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 0;
			return interpreter.primitiveSuccess(TupleDescriptor.empty());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ALL.o()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInteger(
						IntegerDescriptor.zero()),
					TupleDescriptor.empty(),
					TERMINATES.o()));
		}
	},

	/**
	 * <strong>Primitive 135:</strong> Extract a {@linkplain TupleDescriptor
	 * subtuple} with the given range of elements.
	 */
	prim135_ExtractSubtuple_tuple_start_end(135, 3, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject tuple = args.get(0);
			final AvailObject start = args.get(1);
			final AvailObject end = args.get(2);
			if (!start.isInt() || !end.isInt())
			{
				return interpreter.primitiveFailure(
					"subscript is out of range");
			}
			final int startInt = start.extractInt();
			final int endInt = end.extractInt();
			if (startInt < 1
				|| startInt > endInt + 1
				|| endInt < 0
				|| endInt > tuple.tupleSize())
			{
				return interpreter.primitiveFailure(
					"subscript is out of range");
			}
			return interpreter.primitiveSuccess(
				tuple.copyTupleFromToCanDestroy(
					startInt,
					endInt,
					true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.mostGeneralTupleType(),
					IntegerRangeTypeDescriptor.naturalNumbers(),
					IntegerRangeTypeDescriptor.wholeNumbers()),
				TupleTypeDescriptor.mostGeneralTupleType());
		}
	},

	/**
	 * <strong>Primitive 136:</strong> Concatenate a {@linkplain TupleDescriptor
	 * tuple} of tuples together into a single tuple.
	 */
	prim136_ConcatenateTuples_tuples(136, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tuples = args.get(0);
			return interpreter.primitiveSuccess(
				tuples.concatenateTuplesCanDestroy(true));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						TupleTypeDescriptor.mostGeneralTupleType())),
				TupleTypeDescriptor.mostGeneralTupleType());
		}
	},

	/**
	 * <strong>Primitive 137:</strong> Construct a {@linkplain
	 * TupleTypeDescriptor tuple type} with the given parameters. Canonize the
	 * data if necessary.
	 */
	prim137_CreateTupleType_sizeRange_typeTuple_defaultType(137, 3, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject sizeRange = args.get(0);
			final AvailObject typeTuple = args.get(1);
			final AvailObject defaultType = args.get(2);
			if (sizeRange.lowerBound().lessThan(IntegerDescriptor.zero()))
			{
				interpreter.primitiveFailure(E_NEGATIVE_SIZE);
			}
			return interpreter.primitiveSuccess(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					sizeRange,
					typeTuple,
					defaultType));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					INTEGER_TYPE.o(),
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						TYPE.o()),
					TYPE.o()),
				TUPLE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 138:</strong> Answer the allowed size {@linkplain
	 * IntegerRangeTypeDescriptor ranges} for this {@linkplain
	 * TupleTypeDescriptor tuple type}. These are the sizes that a {@linkplain
	 * TupleDescriptor tuple} may be and still be considered instances of the
	 * tuple type, assuming the element {@linkplain TypeDescriptor types} are
	 * consistent with those specified by the tuple type.
	 */
	prim138_TupleTypeSizes_tupleType(138, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			return interpreter.primitiveSuccess(tupleType.sizeRange());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TUPLE_TYPE.o()),
				INTEGER_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 139:</strong> Answer the {@linkplain TupleDescriptor
	 * tuple} of leading {@linkplain TypeDescriptor types} that constrain this
	 * {@linkplain TupleTypeDescriptor tuple type}.
	 */
	prim139_TupleTypeLeadingTypes_tupleType(139, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			return interpreter.primitiveSuccess(tupleType.typeTuple());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TUPLE_TYPE.o()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					TYPE.o()));
		}
	},

	/**
	 * <strong>Primitive 140:</strong> Answer the default {@linkplain
	 * TypeDescriptor type} for elements past the leading types.
	 */
	prim140_TupleTypeDefaultType_tupleType(140, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject tupleType = args.get(0);
			return interpreter.primitiveSuccess(tupleType.defaultType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TUPLE_TYPE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 141:</strong> Answer the {@linkplain TypeDescriptor
	 * type} for the given element of {@linkplain TupleDescriptor instances} of
	 * the given {@linkplain TupleTypeDescriptor tuple type}. Answer
	 * {@link TerminatesTypeDescriptor terminates} if out of range.
	 */
	prim141_TupleTypeAt_tupleType_index(141, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject tupleType = args.get(0);
			final AvailObject index = args.get(1);
			if (!index.isInt())
			{
				return interpreter.primitiveSuccess(
					TERMINATES.o());
			}
			return interpreter.primitiveSuccess(
				tupleType.typeAtIndex(index.extractInt()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TUPLE_TYPE.o(),
					IntegerRangeTypeDescriptor.naturalNumbers()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 142:</strong> Answer a {@linkplain TupleDescriptor
	 * tuple} of {@linkplain TypeDescriptor types} representing the types of the
	 * given range of indices within the {@linkplain TupleTypeDescriptor tuple
	 * type}. Use {@link TerminatesTypeDescriptor terminates} for indices out of
	 * range.
	 */
	prim142_TupleTypeSequenceOfTypes_tupleType_startIndex_endIndex(142, 3, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject tupleType = args.get(0);
			final AvailObject startIndex = args.get(1);
			final AvailObject endIndex = args.get(2);
			if (!startIndex.isInt() || !endIndex.isInt())
			{
				return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			final int startInt = startIndex.extractInt();
			final int endInt = endIndex.extractInt();
			final int tupleSize = endInt - startInt + 1;
			if (tupleSize < 0)
			{
				return interpreter.primitiveFailure(E_NEGATIVE_SIZE);
			}
			AvailObject tupleObject =
				ObjectTupleDescriptor.mutable().create(tupleSize);
			tupleObject.hashOrZero(0);
			for (int i = 1; i <= tupleSize; i++)
			{
				tupleObject.tupleAtPut(i, VoidDescriptor.voidObject());
			}
			for (int i = 1; i <= tupleSize; i++)
			{
				tupleObject = tupleObject.tupleAtPuttingCanDestroy(
					i,
					tupleType.typeAtIndex(startInt + i - 1).makeImmutable(),
					true);
			}
			return interpreter.primitiveSuccess(tupleObject);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TUPLE_TYPE.o(),
					IntegerRangeTypeDescriptor.naturalNumbers(),
					IntegerRangeTypeDescriptor.wholeNumbers()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					TYPE.o()));
		}
	},

	/**
	 * <strong>Primitive 143:</strong> Answer the {@linkplain TypeDescriptor
	 * type} that is the union of the types within the given range of indices of
	 * the given {@linkplain TupleTypeDescriptor tuple type}. Answer {@link
	 * TerminatesTypeDescriptor terminates} if all the indices are out of range.
	 */
	prim143_TupleTypeAtThrough_tupleType_startIndex_endIndex(143, 3, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 3;
			final AvailObject tupleType = args.get(0);
			final AvailObject startIndex = args.get(1);
			final AvailObject endIndex = args.get(2);
			if (!startIndex.isInt() || !endIndex.isInt())
			{
				return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			final int startInt = startIndex.extractInt();
			final int endInt = endIndex.extractInt();
			if (startInt > endInt)
			{
				return interpreter.primitiveFailure(E_NEGATIVE_SIZE);
			}
			return interpreter.primitiveSuccess(
				tupleType.unionOfTypesAtThrough(
					startIndex.extractInt(),
					endIndex.extractInt()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TUPLE_TYPE.o(),
					IntegerRangeTypeDescriptor.naturalNumbers(),
					IntegerRangeTypeDescriptor.wholeNumbers()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 144:</strong> Answer the {@linkplain TypeDescriptor
	 * type} that is the type of all possible concatenations of instances of the
	 * given {@linkplain TupleTypeDescriptor tuple types}. This is basically the
	 * returns clause of the two-argument concatenation operation.
	 */
	prim144_TupleTypeConcatenate_tupleType1_tupleType2(144, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject tupleType1 = args.get(0);
			final AvailObject tupleType2 = args.get(1);
			return interpreter.primitiveSuccess(
				ConcatenatedTupleTypeDescriptor.concatenatingAnd(
					tupleType1,
					tupleType2));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TUPLE_TYPE.o(),
					TUPLE_TYPE.o()),
				TUPLE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 160:</strong> Open a {@linkplain RandomAccessFile file}
	 * for reading. Answer a {@linkplain CyclicTypeDescriptor handle} that
	 * uniquely identifies the file.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim160_FileOpenRead_name(160, 1, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject filename = args.get(0);

			final AvailObject handle = CyclicTypeDescriptor.create(filename);
			try
			{
				final RandomAccessFile file = new RandomAccessFile(
					filename.asNativeString(),
					"r");
				interpreter.runtime().putReadableFile(handle, file);
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}
			return interpreter.primitiveSuccess(handle);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				CYCLIC_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 161:</strong> Open a {@linkplain RandomAccessFile file}
	 * for writing. Answer a {@linkplain CyclicTypeDescriptor handle} that
	 * uniquely identifies the file.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim161_FileOpenWrite_name_append(161, 2, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject filename = args.get(0);
			final AvailObject append = args.get(1);

			final AvailObject handle = CyclicTypeDescriptor.create(filename);
			try
			{
				final RandomAccessFile file = new RandomAccessFile(
					filename.asNativeString(),
					"rw");
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
				return interpreter.primitiveFailure(E_IO_ERROR);
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}
			return interpreter.primitiveSuccess(handle);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType(),
					BOOLEAN.o()),
				CYCLIC_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 162:</strong> Open a {@linkplain RandomAccessFile file}
	 * for reading and writing. Answer a {@linkplain CyclicTypeDescriptor
	 * handle} that uniquely identifies the file.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim162_FileOpenReadWrite_name(162, 1, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject filename = args.get(0);

			final AvailObject handle = CyclicTypeDescriptor.create(filename);
			try
			{
				final RandomAccessFile file = new RandomAccessFile(
					filename.asNativeString(),
					"rw");
				interpreter.runtime().putReadableFile(handle, file);
				interpreter.runtime().putWritableFile(handle, file);
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}
			return interpreter.primitiveSuccess(handle);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				CYCLIC_TYPE.o());
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject handle = args.get(0);

			if (!handle.isCyclicType())
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final RandomAccessFile file =
				interpreter.runtime().getOpenFile(handle);
			if (file == null)
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
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
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 164:</strong> Read the requested number of bytes from
	 * the {@link RandomAccessFile file} associated with the specified
	 * {@linkplain CyclicTypeDescriptor handle} and answer them as a {@linkplain
	 * ByteTupleDescriptor tuple}. If fewer bytes are available, then simply
	 * return a shorter tuple. If the request amount is infinite, then answer a
	 * tuple containing all remaining bytes, or a very large buffer size,
	 * whichever is less.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim164_FileRead_handle_size(164, 2, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject handle = args.get(0);
			final AvailObject size = args.get(1);

			if (!handle.isCyclicType())
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final RandomAccessFile file =
				interpreter.runtime().getReadableFile(handle);
			if (file == null)
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final byte[] buffer;
			final int bytesRead;
			try
			{
				if (size.isFinite())
				{
					buffer = new byte[size.extractInt()];
				}
				else
				{
					final int bufferSize = (int) Math.min(
						Integer.MAX_VALUE,
						file.length() - file.getFilePointer());
					buffer = new byte[bufferSize];
				}
				bytesRead = file.read(buffer);
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}

			final AvailObject tuple;
			if (bytesRead > 0)
			{
				tuple = ByteTupleDescriptor.mutableObjectOfSize(bytesRead);
				for (int i = 1; i <= bytesRead; i++)
				{
					tuple.rawByteAtPut(i, (short) (buffer[i - 1] & 0xff));
				}
			}
			else
			{
				tuple = TupleDescriptor.empty();
			}

			return interpreter.primitiveSuccess(tuple);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o(),
					IntegerRangeTypeDescriptor.wholeNumbers()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.bytes()));
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject handle = args.get(0);
			final AvailObject bytes = args.get(1);

			if (!handle.isCyclicType())
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final RandomAccessFile file =
				interpreter.runtime().getWritableFile(handle);
			if (file == null)
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
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
				return interpreter.primitiveFailure(E_IO_ERROR);
			}

			// Always return an empty tuple since RandomAccessFile writes its
			// buffer transactionally.
			return interpreter.primitiveSuccess(TupleDescriptor.empty());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o(),
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						IntegerRangeTypeDescriptor.bytes())),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.bytes()));
		}
	},

	/**
	 * <strong>Primitive 166:</strong> Answer the size of the
	 * {@linkplain RandomAccessFile file} associated with the specified
	 * {@linkplain CyclicTypeDescriptor handle}. Supports 64-bit file sizes.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim166_FileSize_handle(166, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject handle = args.get(0);

			if (!handle.isCyclicType())
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final RandomAccessFile file =
				interpreter.runtime().getOpenFile(handle);
			if (file == null)
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final long fileSize;
			try
			{
				fileSize = file.length();
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}

			return interpreter.primitiveSuccess(
				IntegerDescriptor.objectFromLong(fileSize));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o()),
				IntegerRangeTypeDescriptor.wholeNumbers());
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject handle = args.get(0);

			if (!handle.isCyclicType())
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final RandomAccessFile file =
				interpreter.runtime().getReadableFile(handle);
			if (file == null)
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final long filePosition;
			try
			{
				filePosition = file.getFilePointer();
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}

			return interpreter.primitiveSuccess(
				IntegerDescriptor.objectFromLong(filePosition));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o()),
				IntegerRangeTypeDescriptor.wholeNumbers());
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject handle = args.get(0);
			final AvailObject filePosition = args.get(1);

			if (!handle.isCyclicType())
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final RandomAccessFile file =
				interpreter.runtime().getReadableFile(handle);
			if (file == null)
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			if (!filePosition.isLong())
			{
				return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}

			try
			{
				file.seek(filePosition.extractLong());
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}

			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o(),
					IntegerRangeTypeDescriptor.wholeNumbers()),
				VOID_TYPE.o());
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject handle = args.get(0);

			if (!handle.isCyclicType())
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			final RandomAccessFile file =
				interpreter.runtime().getWritableFile(handle);
			if (file == null)
			{
				return interpreter.primitiveFailure(E_INVALID_HANDLE);
			}

			try
			{
				file.getFD().sync();
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}

			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 170:</strong> Does a {@linkplain File file} exists with
	 * the specified filename?
	 */
	prim170_FileExists_nameString(170, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject filename = args.get(0);

			final File file = new File(filename.asNativeString());
			final boolean exists;
			try
			{
				exists = file.exists();
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}

			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(exists));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 171:</strong> Is the {@linkplain File file} with the
	 * specified filename readable by the OS process?
	 */
	prim171_FileCanRead_nameString(171, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject filename = args.get(0);

			final File file = new File(filename.asNativeString());
			final boolean readable;
			try
			{
				readable = file.canRead();
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}

			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(readable));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 172:</strong> Is the {@linkplain File file} with the
	 * specified filename writable by the OS process?
	 */
	prim172_FileCanWrite_nameString(172, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject filename = args.get(0);

			final File file = new File(filename.asNativeString());
			final boolean writable;
			try
			{
				writable = file.canWrite();
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}

			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(writable));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 173:</strong> Is the {@linkplain File file} with the
	 * specified filename executable by the OS process?
	 */
	prim173_FileCanExecute_nameString(173, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject filename = args.get(0);

			final File file = new File(filename.asNativeString());
			final boolean executable;
			try
			{
				executable = file.canExecute();
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}

			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(executable));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 174:</strong> Rename the {@linkplain File file} with
	 * the specified source filename.
	 */
	prim174_FileRename_sourceString_destinationString(174, 2, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject source = args.get(0);
			final AvailObject destination = args.get(1);

			final File file = new File(source.asNativeString());
			final boolean renamed;
			try
			{
				renamed = file.renameTo(new File(destination.asNativeString()));
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}

			if (!renamed)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}

			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType(),
					TupleTypeDescriptor.stringTupleType()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 175:</strong> Unlink the {@linkplain File file} with
	 * the specified filename from the filesystem.
	 */
	prim175_FileUnlink_nameString(175, 1, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject filename = args.get(0);

			final File file = new File(filename.asNativeString());
			final boolean deleted;
			try
			{
				deleted = file.delete();
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}

			if (!deleted)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}

			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 180:</strong> Answer the number of arguments expected
	 * by the {@linkplain CompiledCodeDescriptor compiled code}.
	 */
	prim180_CompiledCodeNumArgs_cc(180, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(cc.numArgs()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o()),
				IntegerRangeTypeDescriptor.wholeNumbers());
		}
	},

	/**
	 * <strong>Primitive 181:</strong> Answer the number of locals created by
	 * the {@linkplain CompiledCodeDescriptor compiled code}.
	 */
	prim181_CompiledCodeNumLocals_cc(181, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(cc.numLocals()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o()),
				IntegerRangeTypeDescriptor.wholeNumbers());
		}
	},

	/**
	 * <strong>Primitive 182:</strong> Answer the number of outer variables in
	 * {@linkplain ClosureDescriptor closures} derived from this {@linkplain
	 * CompiledCodeDescriptor compiled code}.
	 */
	prim182_CompiledCodeNumOuters_cc(182, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(cc.numOuters()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o()),
				IntegerRangeTypeDescriptor.wholeNumbers());
		}
	},

	/**
	 * <strong>Primitive 183:</strong> Answer the number of stack slots (not
	 * counting arguments and locals) created for the {@linkplain
	 * CompiledCodeDescriptor compiled code}.
	 */
	prim183_CompiledCodeNumStackSlots_cc(183, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(cc.maxStackDepth()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o()),
				IntegerRangeTypeDescriptor.wholeNumbers());
		}
	},

	/**
	 * <strong>Primitive 184:</strong> Answer the {@linkplain TupleDescriptor
	 * nybblecodes} of the {@linkplain CompiledCodeDescriptor compiled code}.
	 */
	prim184_CompiledCodeNybbles_cc(184, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			return interpreter.primitiveSuccess(cc.nybbles());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.nybbles()));
		}
	},

	/**
	 * <strong>Primitive 185:</strong> Answer the {@linkplain
	 * ClosureTypeDescriptor type of closure} this {@linkplain
	 * CompiledCodeDescriptor compiled code} will be closed into.
	 */
	prim185_CompiledCodeClosureType_cc(185, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			return interpreter.primitiveSuccess(cc.closureType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o()),
				CLOSURE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 186:</strong> Answer the primitive number of this
	 * {@linkplain CompiledCodeDescriptor compiled code}.
	 */
	prim186_CompiledCodePrimitiveNumber_cc(186, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(cc.primitiveNumber()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o()),
				IntegerRangeTypeDescriptor.unsignedShorts());
		}
	},

	/**
	 * <strong>Primitive 187:</strong> Answer a {@linkplain TupleDescriptor
	 * tuple} with the literals from this {@linkplain CompiledCodeDescriptor
	 * compiled code}.
	 */
	prim187_CompiledCodeLiterals_cc(187, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject cc = args.get(0);
			AvailObject tupleObject = ObjectTupleDescriptor.mutable().create(
				cc.numLiterals());
			tupleObject.hashOrZero(0);
			final int tupleSize = tupleObject.tupleSize();
			for (int i = 1; i <= tupleSize; i++)
			{
				tupleObject.tupleAtPut(i, VoidDescriptor.voidObject());
			}
			AvailObject literal;
			for (int i = 1; i <= tupleSize; i++)
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
			return interpreter.primitiveSuccess(tupleObject);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					COMPILED_CODE.o()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					ALL.o()));
		}
	},

	/**
	 * <strong>Primitive 188:</strong> Answer a {@linkplain
	 * CompiledCodeDescriptor compiled code} with the given data.
	 */
	prim188_CreateCompiledCode(188, 7, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 7;
			final AvailObject locals = args.get(0);
			final AvailObject outers = args.get(1);
			final AvailObject stack = args.get(2);
			final AvailObject nybs = args.get(3);
			final AvailObject closureType = args.get(4);
			final AvailObject primitive = args.get(5);
			final AvailObject allLiterals = args.get(6);

			final int nLocals = locals.extractInt();
			final int nOuters = outers.extractInt();
			final int primitiveInt = primitive.extractInt();
			final int nLiteralsTotal = allLiterals.tupleSize();

			if (primitiveInt != 0)
			{
				final Primitive prim = Primitive.byPrimitiveNumber(primitiveInt);
				if (prim == null)
				{
					return interpreter.primitiveFailure(
						E_INVALID_PRIMITIVE_NUMBER);
				}
				final AvailObject restrictionSignature =
					prim.blockTypeRestriction();
				if (!restrictionSignature.isSubtypeOf(closureType))
				{
					return interpreter.primitiveFailure(
						E_CLOSURE_DISAGREES_WITH_PRIMITIVE_RESTRICTION);
				}
			}

			final AvailObject localTypes =
				allLiterals.copyTupleFromToCanDestroy(
					nLiteralsTotal - nLocals + 1,
					nLiteralsTotal,
					false);
			for (int i = 1; i < nLocals; i++)
			{
				if (!localTypes.tupleAt(i).isInstanceOfSubtypeOf(TYPE.o()))
				{
					return interpreter.primitiveFailure(
						E_LOCAL_TYPE_LITERAL_IS_NOT_A_TYPE);
				}
			}

			final AvailObject outerTypes =
				allLiterals.copyTupleFromToCanDestroy(
					nLiteralsTotal - nLocals - nOuters + 1,
					nLiteralsTotal - nLocals,
					false);
			for (int i = 1; i < nOuters; i++)
			{
				if (!outerTypes.tupleAt(i).isInstanceOfSubtypeOf(TYPE.o()))
				{
					return interpreter.primitiveFailure(
						E_OUTER_TYPE_LITERAL_IS_NOT_A_TYPE);
				}
			}

			return interpreter.primitiveSuccess(
				CompiledCodeDescriptor.create(
					nybs,
					nLocals,
					stack.extractInt(),
					closureType,
					primitive.extractInt(),
					allLiterals.copyTupleFromToCanDestroy(
						1,
						nLiteralsTotal - nLocals - nOuters,
						false),
					localTypes,
					outerTypes));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					IntegerRangeTypeDescriptor.wholeNumbers(),
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						IntegerRangeTypeDescriptor.nybbles()),
					CLOSURE_TYPE.o(),
					IntegerRangeTypeDescriptor.unsignedShorts(),
					TupleTypeDescriptor.mostGeneralTupleType()),
				COMPILED_CODE.o());
		}
	},

	/**
	 * <strong>Primitive 200:</strong> Always fail. The Avail failure code
	 * invokes the {@linkplain ClosureDescriptor body block}. The handler block
	 * is only invoked when an exception is raised.
	 */
	prim200_CatchException_bodyBlock_handlerBlock(200, 2, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			@SuppressWarnings("unused")
			final AvailObject bodyBlock = args.get(0);
			@SuppressWarnings("unused")
			final AvailObject handlerBlock = args.get(1);
			return interpreter.primitiveFailure(E_REQUIRED_FAILURE);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ClosureTypeDescriptor.create(
						TupleDescriptor.from(),
						VOID_TYPE.o()),
					ClosureTypeDescriptor.create(
						TupleDescriptor.from(
							TERMINATES.o()),
						VOID_TYPE.o())),
				VOID_TYPE.o());
		}

		@Override
		protected AvailObject privateFailureVariableType ()
		{
			return IntegerRangeTypeDescriptor.singleInteger(
				IntegerDescriptor.zero());
		}
	},

	/**
	 * <strong>Primitive 201:</strong> Raise an exception. Scan the stack of
	 * {@linkplain ContinuationDescriptor continuations} until one is found for
	 * a {@linkplain ClosureDescriptor closure} whose {@linkplain
	 * CompiledCodeDescriptor code} is {@linkplain
	 * #prim200_CatchException_bodyBlock_handlerBlock primitive 200}.
	 * Get that continuation's second argument (a handler block of one
	 * argument), and check if that handler block will accept {@code
	 * exceptionValue}. If not, keep looking. If it will accept it, unwind the
	 * stack so that the primitive 200 continuation is the top entry, and invoke
	 * the handler block with {@code exceptionValue}. If there is no suitable
	 * handler block, then fail this primitive.
	 */
	prim201_RaiseException_exceptionValue(201, 1, SwitchesContinuation)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.searchForExceptionHandler(args);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ALL.o()),
				TERMINATES.o());
		}
	},

	/**
	 * <strong>Primitive 207:</strong> Answer a collection of all visible
	 * {@linkplain MessageBundleDescriptor messages} inside the current
	 * {@linkplain MessageBundleTreeDescriptor tree} that expect no more parts
	 * than those already encountered. Answer it as a {@linkplain MapDescriptor
	 * map} from {@linkplain CyclicTypeDescriptor cyclic type} to message bundle
	 * (typically only zero or one entry).
	 */
	prim207_CompleteMessages_bundleTree(207, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundleTree = args.get(0);
			return interpreter.primitiveSuccess(
				bundleTree.complete().makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MESSAGE_BUNDLE_TREE.o()),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					CYCLIC_TYPE.o(),
					MESSAGE_BUNDLE.o()));
		}
	},

	/**
	 * <strong>Primitive 208:</strong> Answer a collection of all visible
	 * {@linkplain MessageBundleDescriptor messages} inside the current
	 * {@linkplain MessageBundleTreeDescriptor tree} that expect more parts than
	 * those already encountered. Answer it as a {@linkplain MapDescriptor map}
	 * from string to message bundle tree.
	 */
	prim208_IncompleteMessages_bundleTree(208, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundleTree = args.get(0);
			return interpreter.primitiveSuccess(
				bundleTree.incomplete().makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MESSAGE_BUNDLE_TREE.o()),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleTypeDescriptor.stringTupleType(),
					MESSAGE_BUNDLE_TREE.o()));
		}
	},

	/**
	 * <strong>Primitive 209:</strong> Answer a collection of all visible
	 * {@linkplain MessageBundleDescriptor messages} that start with the given
	 * string, and have only one part. Answer a {@linkplain MapDescriptor map}
	 * from {@linkplain CyclicTypeDescriptor cyclic type} to message bundle.
	 */
	prim209_CompleteMessagesStartingWith_leadingPart(
		209, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject leadingPart = args.get(0);
			return interpreter.primitiveSuccess(
				interpreter
					.completeBundlesStartingWith(leadingPart)
					.makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					CYCLIC_TYPE.o(),
					MESSAGE_BUNDLE.o()));
		}
	},

	/**
	 * <strong>Primitive 210:</strong> Produce a collection of all visible
	 * {@linkplain MessageBundleDescriptor messages} that start with the given
	 * string, and have more than one part. Answer a {@linkplain MapDescriptor
	 * map} from second part (string) to message bundle tree.
	 */
	prim210_IncompleteMessagesStartingWith_leadingPart(
		210, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject leadingPart = args.get(0);
			final AvailObject bundles =
				interpreter.incompleteBundlesStartingWith(leadingPart);
			bundles.makeImmutable();
			return interpreter.primitiveSuccess(bundles);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleTypeDescriptor.stringTupleType(),
					MESSAGE_BUNDLE_TREE.o()));
		}
	},

	/**
	 * <strong>Primitive 211:</strong> Answer a {@linkplain
	 * MessageBundleDescriptor message bundle}'s message (a
	 * {@linkplain CyclicTypeDescriptor cyclic type}).
	 */
	prim211_BundleMessage_bundle(211, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			return interpreter.primitiveSuccess(
				bundle.message().makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MESSAGE_BUNDLE.o()),
				CYCLIC_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 212:</strong> Answer a {@linkplain
	 * MessageBundleDescriptor message bundle}'s message parts (a {@linkplain
	 * TupleDescriptor tuple} of strings).
	 */
	prim212_BundleMessageParts_bundle(212, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			return interpreter.primitiveSuccess(
				bundle.messageParts().makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MESSAGE_BUNDLE.o()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					TupleTypeDescriptor.stringTupleType()));
		}
	},

	/**
	 * <strong>Primitive 213:</strong> Answer a {@linkplain SetDescriptor set}
	 * of all currently defined {@linkplain SignatureDescriptor signatures} for
	 * the {@linkplain CyclicTypeDescriptor message} represented by {@linkplain
	 * MessageBundleDescriptor bundle}. This includes abstract signatures and
	 * forward signatures.
	 */
	prim213_BundleSignatures_bundle(213, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			final AvailObject implementationSet =
				interpreter.runtime().methodsAt(bundle.message());
			if (implementationSet.equalsVoid())
			{
				return interpreter.primitiveFailure(E_NO_IMPLEMENTATION_SET);
			}
			final AvailObject implementations =
				implementationSet.implementationsTuple().asSet();
			implementations.makeImmutable();
			return interpreter.primitiveSuccess(implementations);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MESSAGE_BUNDLE.o()),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					SIGNATURE.o()));
		}
	},

	/**
	 * <strong>Primitive 214:</strong> Answer whether precedence restrictions
	 * have been defined (yet) for this {@linkplain MessageBundleDescriptor
	 * bundle}.
	 */
	prim214_BundleHasRestrictions_bundle(214, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(
					bundle.message().hasRestrictions()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MESSAGE_BUNDLE.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 215:</strong> Answer the current precedence
	 * restrictions for this {@linkplain MessageBundleDescriptor bundle}.
	 */
	prim215_BundleRestrictions_bundle(215, 1, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject bundle = args.get(0);
			return interpreter.primitiveSuccess(
				bundle.restrictions().makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					MESSAGE_BUNDLE.o()),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						CYCLIC_TYPE.o())));
		}
	},

	/**
	 * <strong>Primitive 216:</strong> Answer this {@linkplain
	 * SignatureDescriptor signature}'s {@linkplain ClosureDescriptor body}'s
	 * {@linkplain ClosureTypeDescriptor type}.
	 */
	prim216_SignatureBodyType_sig(216, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			return interpreter.primitiveSuccess(
				sig.bodySignature().makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SIGNATURE.o()),
				CLOSURE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 217:</strong> Answer this {@linkplain
	 * MethodSignatureDescriptor method signature}'s {@linkplain
	 * ClosureDescriptor body}.
	 */
	prim217_SignatureBodyBlock_methSig(217, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject methSig = args.get(0);
			return interpreter.primitiveSuccess(
				methSig.bodyBlock().makeImmutable());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					METHOD_SIGNATURE.o()),
				ClosureTypeDescriptor.topInstance());
		}
	},

	/**
	 * <strong>Primitive 218:</strong> Answer this {@linkplain
	 * SignatureDescriptor signature}'s {@linkplain ClosureDescriptor requires
	 * closure}.
	 */
	prim218_SignatureRequiresBlock_sig(218, 1, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			if (sig.isInstanceOfSubtypeOf(METHOD_SIGNATURE.o())
				|| sig.isInstanceOfSubtypeOf(ABSTRACT_SIGNATURE.o()))
			{
				// Only method & abstract signatures have a requires block.
				return interpreter.primitiveSuccess(
					sig.requiresBlock().makeImmutable());
			}
			return interpreter.primitiveFailure(
				E_SIGNATURE_DOES_NOT_SUPPORT_REQUIRES_CLOSURE);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SIGNATURE.o()),
				ClosureTypeDescriptor.forReturnType(BOOLEAN.o()));
		}
	},

	/**
	 * <strong>Primitive 219:</strong> Answer this {@linkplain
	 * SignatureDescriptor signature}'s {@linkplain ClosureDescriptor returns
	 * closure}.
	 */
	prim219_SignatureReturnsBlock_sig(219, 1, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject sig = args.get(0);
			if (sig.isInstanceOfSubtypeOf(METHOD_SIGNATURE.o())
				|| sig.isInstanceOfSubtypeOf(ABSTRACT_SIGNATURE.o()))
			{
				// Only method & abstract signatures have a returns block.
				return interpreter.primitiveSuccess(
					sig.returnsBlock().makeImmutable());
			}
			return interpreter.primitiveFailure(
				E_SIGNATURE_DOES_NOT_SUPPORT_RETURNS_CLOSURE);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SIGNATURE.o()),
				ClosureTypeDescriptor.forReturnType(TYPE.o()));
		}
	},

	/**
	 * <strong>Primitive 220:</strong> Answer the {@linkplain
	 * ImplementationSetDescriptor implementation set} associated with the given
	 * {@linkplain CyclicTypeDescriptor cyclic type}. This is generally only
	 * used when Avail code is constructing Avail code in the metacircular
	 * compiler.
	 */
	prim220_ImplementationSetFromName_name(220, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject aCyclicType = args.get(0);
			final AvailObject impSet =
				interpreter.runtime().methodsAt(aCyclicType);
			if (impSet.equalsVoid())
			{
				return interpreter.primitiveFailure(E_NO_IMPLEMENTATION_SET);
			}
			impSet.makeImmutable();
			return interpreter.primitiveSuccess(impSet);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o()),
				IMPLEMENTATION_SET.o());
		}
	},

	/**
	 * <strong>Primitive 221:</strong> Answer the {@linkplain
	 * CyclicTypeDescriptor cyclic type} associated with the given {@linkplain
	 * ImplementationSetDescriptor implementation set}. This is generally only
	 * used when Avail code is saving or loading Avail code in the object dumper
	 * / loader.
	 */
	prim221_ImplementationSetName_name(221, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject anImplementationSet = args.get(0);
			return interpreter.primitiveSuccess(anImplementationSet.name());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IMPLEMENTATION_SET.o()),
				CYCLIC_TYPE.o());
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject ordinal = args.get(0);
			if (!ordinal.isInt())
			{
				return interpreter.primitiveFailure(
					E_INVALID_SPECIAL_OBJECT_NUMBER);
			}
			final int i = ordinal.extractInt();

			final AvailObject result;
			try
			{
				result = interpreter.runtime().specialObject(i);
			}
			catch (final ArrayIndexOutOfBoundsException e)
			{
				return interpreter.primitiveFailure(
					E_INVALID_SPECIAL_OBJECT_NUMBER);
			}

			if (result == null)
			{
				return interpreter.primitiveFailure(
					E_INVALID_SPECIAL_OBJECT_NUMBER);
			}

			return interpreter.primitiveSuccess(result);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.naturalNumbers()),
				ALL.o());
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject name = args.get(0);
			try
			{
				return interpreter.primitiveSuccess(
					interpreter.lookupName(name));
			}
			catch (final AmbiguousNameException e)
			{
				return interpreter.primitiveFailure(e);
			}
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				CYCLIC_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 249:</strong> Simple macro definition.
	 */
	prim249_SimpleMacroDeclaration_string_block(249, 2, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject string = args.get(0);
			final AvailObject block = args.get(1);
			final AvailObject blockType = block.type();
			final AvailObject tupleType = blockType.argsTupleType();
			for (int i = block.code().numArgs(); i >= 1; i--)
			{
				if (!tupleType.typeAtIndex(i).isSubtypeOf(PARSE_NODE.o()))
				{
					return interpreter.primitiveFailure(
						E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE);
				}
			}
			try
			{
				interpreter.atAddMacroBody(
					interpreter.lookupName(string), block);
			}
			catch (final AmbiguousNameException e)
			{
				return interpreter.primitiveFailure(e);
			}
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType(),
					ClosureTypeDescriptor.forReturnType(PARSE_NODE.o())),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 250:</strong> Is there a {@linkplain Primitive
	 * primitive} with the specified ordinal?
	 */
	prim250_IsPrimitiveDefined_index(250, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject ordinal = args.get(0);

			final int index = ordinal.extractInt();
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(
					interpreter.supportsPrimitive(index)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.unsignedShorts()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 251:</strong> Declare method as {@linkplain
	 * AbstractSignatureDescriptor abstract}. This identifies responsibility for
	 * subclasses that want to be concrete.
	 */
	prim251_AbstractMethodDeclaration(251, 4, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 4;
			final AvailObject string = args.get(0);
			final AvailObject blockSignature = args.get(1);
			final AvailObject requiresBlock = args.get(2);
			final AvailObject returnsBlock = args.get(3);

			// TODO: [MvG] Check signature compatibility with requires and
			// returns closures.
			try
			{
				interpreter.atDeclareAbstractSignatureRequiresBlockReturnsBlock(
					interpreter.lookupName(string),
					blockSignature,
					requiresBlock,
					returnsBlock);
			}
			catch (final AmbiguousNameException e)
			{
				return interpreter.primitiveFailure(e);
			}
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType(),
					CLOSURE_TYPE.o(),
					ClosureTypeDescriptor.forReturnType(BOOLEAN.o()),
					ClosureTypeDescriptor.forReturnType(TYPE.o())),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 252:</strong> Forward declare a method (for recursion
	 * or mutual recursion).
	 */
	prim252_ForwardMethodDeclaration(252, 2, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject string = args.get(0);
			final AvailObject blockSignature = args.get(1);

			// TODO: [MvG] Deal with errors more appropriately in
			// atAddForwardStubFor(...).
			try
			{
				interpreter.atAddForwardStubFor(
					interpreter.lookupName(string),
					blockSignature);
			}
			catch (final AmbiguousNameException e)
			{
				return interpreter.primitiveFailure(e);
			}
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType(),
					CLOSURE_TYPE.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 253:</strong> Method definition, without type
	 * constraint or result type deduction).
	 */
	prim253_SimpleMethodDeclaration(253, 2, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject string = args.get(0);
			final AvailObject block = args.get(1);

			// TODO:[MvG] Deal with errors more appropriately in lookupName(...)
			// and atAddMethodBody(...).
			try
			{
				interpreter.atAddMethodBody(
					interpreter.lookupName(string), block);
			}
			catch (final AmbiguousNameException e)
			{
				return interpreter.primitiveFailure(e);
			}
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType(),
					ClosureTypeDescriptor.forReturnType(VOID_TYPE.o())),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 254:</strong> Method definition with type constraint
	 * and result type calculation.
	 */
	prim254_MethodDeclaration_string_block_requiresBlock_returnsBlock(254, 4, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 4;
			final AvailObject string = args.get(0);
			final AvailObject block = args.get(1);
			final AvailObject requiresBlock = args.get(2);
			final AvailObject returnsBlock = args.get(3);
			// TODO: [MvG] Deal with errors more appropriately
			try
			{
				interpreter.atAddMethodBodyRequiresBlockReturnsBlock(
					interpreter.lookupName(string),
					block,
					requiresBlock,
					returnsBlock);
			}
			catch (final AmbiguousNameException e)
			{
				return interpreter.primitiveFailure(e);
			}
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType(),
					ClosureTypeDescriptor.forReturnType(VOID_TYPE.o()),
					ClosureTypeDescriptor.forReturnType(BOOLEAN.o()),
					ClosureTypeDescriptor.forReturnType(TYPE.o())),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 255:</strong> Message precedence declaration with
	 * {@linkplain TupleDescriptor tuple} of {@linkplain SetDescriptor sets} of
	 * messages to exclude for each argument position. Note that the tuple's
	 * elements should correspond with occurrences of underscore in the method
	 * names, *not* with the (top-level) arguments of the method. This
	 * distinction is only apparent when chevron notation is used to accept
	 * tuples of arguments.
	 */
	prim255_PrecedenceDeclaration_stringSet_exclusionsTuple(255, 2, Unknown)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject stringSet = args.get(0);
			final AvailObject exclusionsTuple = args.get(1);
			try
			{
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
			}
			catch (final AmbiguousNameException e)
			{
				return interpreter.primitiveFailure(e);
			}
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleTypeDescriptor.stringTupleType()),
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						SetTypeDescriptor.setTypeForSizesContentType(
							IntegerRangeTypeDescriptor.wholeNumbers(),
							TupleTypeDescriptor.stringTupleType()))),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 256:</strong> Exit the current {@linkplain
	 * ProcessDescriptor process}. The specified argument will be converted
	 * internally into a {@code string} and used to report an error message.
	 */
	prim256_EmergencyExit_value(256, 1, Unknown, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject errorMessageProducer = args.get(0);
			error(String.format(
				"A process (%s) has exited: %s",
				interpreter.process().name(),
				errorMessageProducer));
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ALL.o()),
				TERMINATES.o());
		}
	},

	/**
	 * <strong>Primitive 257:</strong> Pause the VM.
	 */
	prim257_BreakPoint(257, 0, Unknown, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			// Throw and catch a RuntimeException.  A sensibly configured
			// debugger will pause during the throw. There are also ample
			// locations here to insert an explicit breakpoint if you don't want
			// to pause on caught RuntimeExceptions.
			try
			{
				throw new RuntimeException("Breakpoint");
			}
			catch (final RuntimeException e)
			{
				return interpreter.primitiveSuccess(
					VoidDescriptor.voidObject());
			}
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 258:</strong> Print an {@linkplain AvailObject object}
	 * to standard output.
	 */
	prim258_PrintToConsole_value(258, 1, Unknown, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;

			final AvailObject objectToPrint = args.get(0);
			System.out.println(objectToPrint);
			return interpreter.primitiveSuccess(VoidDescriptor.voidObject());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ALL.o()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 259:</strong> Produce a {@linkplain
	 * ByteStringDescriptor string} description of the sole argument.
	 */
	prim259_ToString_value(259, 1, Unknown, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectToPrint = args.get(0);
			final String string = objectToPrint.toString();
			final AvailObject availString = ByteStringDescriptor.from(string);
			return interpreter.primitiveSuccess(availString);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ALL.o()),
				TupleTypeDescriptor.stringTupleType());
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
	prim260_CreateLibrarySpec(260, 0, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
			/* From Smalltalk:
				| lib handle |
				lib := Array with: ExternalDictionary new with: nil.
				openLibraries add: lib.
				handle := openLibraries size.
				^IntegerDescriptor objectFromSmalltalkInteger: handle
			 */
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(),
				CYCLIC_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 261:</strong> Open a previously constructed library.
	 * Its handle (into openLibraries) is passed.
	 */
	prim261_OpenLibrary_handle_filename(261, 2, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
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

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o(),
					TupleTypeDescriptor.stringTupleType()),
				VOID_TYPE.o());
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
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

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o(),
					TupleTypeDescriptor.stringTupleType()),
				VOID_TYPE.o());
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
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
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

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o(),
					TupleTypeDescriptor.stringTupleType()),
				CYCLIC_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 264:</strong> Answer the closure type associated with
	 * the given entry point.
	 */
	prim264_EntryPointClosureType_entryPointHandle(264, 1, CanInline)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
			/* From Smalltalk:
				| privateEntryPoint |
				privateEntryPoint := entryPoints at: entryPointHandle extractInt.
				^privateEntryPoint at: 2
			 */
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o()),
				CLOSURE_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 265:</strong> Invoke the entry point associated with
	 * the given handle, using the specified arguments.
	 */
	prim265_InvokeEntryPoint_arguments(265, 2, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
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

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CYCLIC_TYPE.o(),
					TupleTypeDescriptor.mostGeneralTupleType()),
				ALL.o());
		}
	},

	/**
	 * <strong>Primitive 266:</strong> Read an integer of the specified type
	 * from the memory location specified as an integer.
	 */
	prim266_IntegralType_from(266, 2, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
			/* From Smalltalk:
				| byteCount int |
				byteCount := (intType upperBound highBit + 7) // 8.
				int := byteCount halt.
				^IntegerDescriptor objectFromSmalltalkInteger: int
			 */
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					INTEGER_TYPE.o(),
					IntegerRangeTypeDescriptor.wholeNumbers()),
				IntegerRangeTypeDescriptor.integers());
		}
	},

	/**
	 * <strong>Primitive 267:</strong> Write an integer of the specified type to
	 * the memory location specified as an integer.
	 */
	prim267_IntegralType_to_write(267, 3, CanInline, HasSideEffect)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
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

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					INTEGER_TYPE.o(),
					IntegerRangeTypeDescriptor.integers(),
					IntegerRangeTypeDescriptor.wholeNumbers()),
				VOID_TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 268:</strong> Answer a boolean indicating if the
	 * current platform is big-endian.
	 */
	prim268_BigEndian(268, 0, CanInline, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 0;
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(
					ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 280:</strong> Add two {@linkplain FloatDescriptor
	 * floats}.
	 */
	prim280_FloatAddition_a_b(280, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloatRecycling(
					a.extractFloat() + b.extractFloat(),
					a,
					b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o(),
					FLOAT.o()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 281:</strong> Subtract {@linkplain FloatDescriptor
	 * float} {@code b} from float {@code a}.
	 */
	prim281_FloatSubtraction_a_b(281, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloatRecycling(
					a.extractFloat() - b.extractFloat(),
					a,
					b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o(),
					FLOAT.o()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 282:</strong> Multiply {@linkplain FloatDescriptor
	 * float} {@code a} and float {@code b}.
	 */
	prim282_FloatMultiplication_a_b(282, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloatRecycling(
					a.extractFloat() * b.extractFloat(),
					a,
					b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o(),
					FLOAT.o()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 283:</strong> Divide {@linkplain FloatDescriptor float}
	 * {@code a} by float {@code b}.
	 */
	prim283_FloatDivision_a_b(283, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			if (b.extractFloat() == 0.0)
			{
				return interpreter.primitiveFailure(E_CANNOT_DIVIDE_BY_ZERO);
			}
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloatRecycling(
					a.extractFloat() / b.extractFloat(),
					a,
					b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o(),
					FLOAT.o()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 284:</strong> Compare {@linkplain FloatDescriptor
	 * float} {@code a} < float {@code b}. Answers a {@linkplain
	 * BooleanDescriptor boolean}.
	 */
	prim284_FloatLessThan_a_b(284, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(
					(a.extractFloat() < b.extractFloat())));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o(),
					FLOAT.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 285:</strong> Compare {@linkplain FloatDescriptor
	 * float} {@code a} <= float {@code b}. Answers a {@linkplain
	 * BooleanDescriptor boolean}.
	 */
	prim285_FloatLessOrEqual_a_b(285, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(
					(a.extractFloat() <= b.extractFloat())));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o(),
					FLOAT.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 286:</strong> Compute the natural logarithm of
	 * {@linkplain FloatDescriptor float} {@code a}.
	 */
	prim286_FloatLn_a(286, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloatRecycling(
					(float) log(a.extractFloat()),
					a));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 287:</strong> Compute {@code e^a}, the natural
	 * exponential of the {@linkplain FloatDescriptor float} {@code a}.
	 */
	prim287_FloatExp_a(287, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloatRecycling(
					(float) exp(a.extractFloat()),
					a));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 288:</strong> Divide {@linkplain FloatDescriptor float}
	 * {@code a} by float {@code b}, but answer the remainder.
	 */
	prim288_FloatModulus_a_b(288, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			final float fa = a.extractFloat();
			final float fb = b.extractFloat();
			if (fb == 0.0f)
			{
				return interpreter.primitiveFailure(E_CANNOT_DIVIDE_BY_ZERO);
			}
			final float div = fa / fb;
			final float mod = fa - (float)floor(div) * fb;
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloatRecycling(mod, a, b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o(),
					FLOAT.o()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 289:</strong> Convert a {@linkplain FloatDescriptor
	 * float} to an {@linkplain IntegerDescriptor integer}, rounding towards
	 * zero.
	 */
	prim289_FloatTruncatedAsInteger_a(289, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top two 32-bit sections.  That guarantees 33 bits
			// of mantissa, which is more than a float actually captures.
			float f = a.extractFloat();
			if (f >= -0x80000000L && f <= 0x7FFFFFFFL)
			{
				// Common case -- it fits in an int.
				return interpreter.primitiveSuccess(
					IntegerDescriptor.fromInt((int)f));
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
			out.trimExcessInts();
			if (neg)
			{
				out = IntegerDescriptor.zero().noFailMinusCanDestroy(out, true);
			}
			return interpreter.primitiveSuccess(out);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 290:</strong> Convert an {@linkplain IntegerDescriptor
	 * integer} to a {@linkplain FloatDescriptor float}, failing if out of
	 * range.
	 */
	prim290_FloatFromInteger_a(290, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			// Extract the top 32 bits and the next-to-top 32 bits.  That
			// guarantees 33 bits of mantissa, which is more than a float
			// actually captures.
			float f;
			final int size = a.integerSlotsCount();
			if (a.isLong())
			{
				f = a.extractLong();
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
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloat(f));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 291:</strong> Compute {@linkplain FloatDescriptor
	 * float} {@code a*(2**b)} without intermediate overflow or any precision
	 * loss.
	 */
	prim291_FloatTimesTwoPower_a_b(291, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			long scale = b.extractInt();
			scale = max(scale, -0x80000000L);
			scale = min(scale, 0x7FFFFFFFL);
			final float f = scalb(a.extractFloat(), (int)scale);
			return interpreter.primitiveSuccess(
				FloatDescriptor.objectFromFloatRecycling(f, a));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					FLOAT.o(),
					IntegerRangeTypeDescriptor.integers()),
				FLOAT.o());
		}
	},

	/**
	 * <strong>Primitive 310:</strong> Add two {@linkplain DoubleDescriptor
	 * doubles}.
	 */
	prim310_DoubleAddition_a_b(310, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDoubleRecycling(
					(a.extractDouble() + b.extractDouble()),
					a,
					b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o(),
					DOUBLE.o()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 311:</strong> Subtract {@linkplain DoubleDescriptor
	 * double} {@code b} from double {@code a}.
	 */
	prim311_DoubleSubtraction_a_b(311, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDoubleRecycling(
					(a.extractDouble() - b.extractDouble()),
					a,
					b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o(),
					DOUBLE.o()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 312:</strong> Multiply {@linkplain DoubleDescriptor
	 * double} {@code a} and double {@code b}.
	 */
	prim312_DoubleMultiplication_a_b(312, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDoubleRecycling(
					(a.extractDouble() * b.extractDouble()),
					a,
					b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o(),
					DOUBLE.o()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 313:</strong> Divide {@linkplain DoubleDescriptor
	 * double} {@code a} by double {@code b}.
	 */
	prim313_DoubleDivision_a_b(313, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			if (b.extractDouble() == 0.0d)
			{
				return interpreter.primitiveFailure(E_CANNOT_DIVIDE_BY_ZERO);
			}
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDoubleRecycling(
					(a.extractDouble() / b.extractDouble()),
					a,
					b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o(),
					DOUBLE.o()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 314:</strong> Compare {@linkplain DoubleDescriptor
	 * double} {@code a < b}. Answers a {@linkplain BooleanDescriptor boolean}.
	 */
	prim314_DoubleLessThan_a_b(314, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(
					a.extractDouble() < b.extractDouble()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o(),
					DOUBLE.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 315:</strong> Compare {@linkplain DoubleDescriptor
	 * double} {@code a <= b}. Answers a {@linkplain BooleanDescriptor boolean}.
	 */
	prim315_DoubleLessOrEqual_a_b(315, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			return interpreter.primitiveSuccess(
				BooleanDescriptor.objectFrom(
					a.extractDouble() <= b.extractDouble()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o(),
					DOUBLE.o()),
				BOOLEAN.o());
		}
	},

	/**
	 * <strong>Primitive 316:</strong> Compute the natural logarithm of the
	 * {@linkplain DoubleDescriptor double} {@code a}.
	 */
	prim316_DoubleLn_a(316, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDoubleRecycling(
					log(a.extractDouble()), a));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 317:</strong> Compute {@code e^a}, the natural
	 * exponential of the {@linkplain DoubleDescriptor double} {@code a}.
	 */
	prim317_DoubleExp_a(317, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0);
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDoubleRecycling(
					exp(a.extractDouble()), a));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 318:</strong> Divide {@linkplain DoubleDescriptor
	 * double} {@code a} by double {@code b}, but answer the remainder.
	 */
	prim318_DoubleModulus_a_b(318, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			final double da = a.extractDouble();
			final double db = b.extractDouble();
			if (db == 0.0d)
			{
				return interpreter.primitiveFailure(E_CANNOT_DIVIDE_BY_ZERO);
			}
			final double div = da / db;
			final double mod = da - floor(div) * db;
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDoubleRecycling(mod, a, b));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o(),
					DOUBLE.o()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 319:</strong> Convert a {@linkplain DoubleDescriptor
	 * double} to an {@linkplain IntegerDescriptor integer}, rounding towards
	 * zero.
	 */
	prim319_DoubleTruncatedAsInteger_a(319, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			final AvailObject a = args.get(0);
			assert args.size() == 1;
			// Extract the top three 32-bit sections.  That guarantees 65 bits
			// of mantissa, which is more than a double actually captures.
			double d = a.extractDouble();
			if (d >= -0x80000000L && d <= 0x7FFFFFFFL)
			{
				// Common case -- it fits in an int.
				return interpreter.primitiveSuccess(
					IntegerDescriptor.fromInt((int)d));
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
			out.trimExcessInts();
			if (neg)
			{
				out = IntegerDescriptor.zero().noFailMinusCanDestroy(out, true);
			}
			return interpreter.primitiveSuccess(out);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o()),
				IntegerRangeTypeDescriptor.extendedIntegers());
		}
	},

	/**
	 * <strong>Primitive 320:</strong> Convert an {@linkplain IntegerDescriptor
	 * integer} to a {@linkplain DoubleDescriptor double}, failing if out of
	 * range.
	 */
	prim320_DoubleFromInteger_a(320, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject a = args.get(0).traversed();
			// Extract the top three 32-bit pieces.  That guarantees 65 bits
			// of mantissa, which is more than a double actually captures.
			double d;
			if (a.isLong())
			{
				d = a.extractLong();
			}
			else
			{
				final int size = a.integerSlotsCount();
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
				d = neg ? -d : d;
			}
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDouble(d));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.extendedIntegers()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 321:</strong> Compute the {@linkplain DoubleDescriptor
	 * double} {@code a*(2**b)} without intermediate overflow or any precision
	 * loss.
	 */
	prim321_DoubleTimesTwoPower_a_b(321, 2, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject a = args.get(0);
			final AvailObject b = args.get(1);
			long scale = b.extractInt();
			scale = max(scale, -0x80000000L);
			scale = min(scale, 0x7FFFFFFFL);
			final double d = scalb(a.extractDouble(), (int)scale);
			return interpreter.primitiveSuccess(
				DoubleDescriptor.objectFromDoubleRecycling(d, a));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					DOUBLE.o(),
					IntegerRangeTypeDescriptor.integers()),
				DOUBLE.o());
		}
	},

	/**
	 * <strong>Primitive 330:</strong> Extract the {@linkplain IntegerDescriptor
	 * code point} from a {@linkplain CharacterDescriptor character}.
	 */
	prim330_CharacterCodePoint_character(330, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject character = args.get(0);
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt(
					character.codePoint()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					CHARACTER.o()),
				IntegerRangeTypeDescriptor.characterCodePoints());
		}
	},

	/**
	 * <strong>Primitive 331:</strong> Convert a {@linkplain IntegerDescriptor
	 * code point} into a {@linkplain CharacterDescriptor character}.
	 */
	prim331_CharacterFromCodePoint_codePoint(331, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject codePoint = args.get(0);
			return interpreter.primitiveSuccess(
				CharacterDescriptor.newImmutableCharacterWithCodePoint(
					codePoint.extractInt()));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.characterCodePoints()),
				CHARACTER.o());
		}
	},

	/**
	 * <strong>Primitive 340:</strong> The first literal is being returned.
	 * Extract the first literal from the {@linkplain CompiledCodeDescriptor
	 * compiled code} that the interpreter has squirreled away for this purpose.
	 */
	prim340_PushConstant_ignoreArgs(340, -1, SpecialReturnConstant, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			return interpreter.primitiveSuccess(
				interpreter.primitiveCompiledCodeBeingAttempted().literalAt(1));
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			// This primitive is suitable for any block signature.
			return TERMINATES.o();
		}
	},

	/**
	 * <strong>Primitive 350:</strong> Transform a variable reference and an
	 * expression into an inner {@linkplain AssignmentNodeDescriptor assignment
	 * node}. Such a node also produces the assigned value as its result, so it
	 * can be embedded as a subexpression.
	 */
	prim350_MacroInnerAssignment_variable_expression(350, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject variable = args.get(0);
			final AvailObject expression = args.get(1);
			final AvailObject declarationType =
				variable.declaration().type();
			if (!declarationType.equals(MODULE_VARIABLE_NODE.o())
				&& !declarationType.equals(LOCAL_VARIABLE_NODE.o()))
			{
				return interpreter.primitiveFailure(
					E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT);
			}
			if (!expression.expressionType().isSubtypeOf(
				variable.expressionType()))
			{
				return interpreter.primitiveFailure(
					E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE_INTO_VARIABLE);
			}
			final AvailObject assignment =
				AssignmentNodeDescriptor.mutable().create();
			assignment.variable(variable);
			assignment.expression(expression);
			return interpreter.primitiveSuccess(assignment);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					VARIABLE_USE_NODE.o(),
					PARSE_NODE.o()),
				ASSIGNMENT_NODE.o());
		}
	},

	/**
	 * <strong>Primitive 351:</strong> Extract the result {@linkplain
	 * TypeDescriptor type} of a {@linkplain ParseNodeDescriptor parse node}.
	 */
	prim351_ParseNodeExpressionType_parseNode(351, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject parseNode = args.get(0);
			return interpreter.primitiveSuccess(parseNode.expressionType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					PARSE_NODE.o()),
				TYPE.o());
		}
	},

	/**
	 * <strong>Primitive 352:</strong> Reject current macro substitution with
	 * the specified error string.
	 */
	prim352_RejectParsing_errorString(352, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject parseNode = args.get(0);
			throw new AvailRejectedParseException(parseNode);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					TupleTypeDescriptor.stringTupleType()),
				TERMINATES.o());
		}
	},

	/**
	 * <strong>Primitive 353:</strong> Transform a variable reference and an
	 * expression into an {@linkplain AssignmentNodeDescriptor assignment}
	 * statement. Such a node has type {@link TypeDescriptor.Types#VOID_TYPE
	 * void} and cannot be embedded as a subexpression.
	 */
	prim353_MacroAssignmentStatement_variable_expression(353, 2, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 2;
			final AvailObject variable = args.get(0);
			final AvailObject expression = args.get(1);
			final AvailObject declarationType =
				variable.declaration().type();
			if (!declarationType.equals(MODULE_VARIABLE_NODE.o())
				&& !declarationType.equals(LOCAL_VARIABLE_NODE.o()))
			{
				return interpreter.primitiveFailure(
					E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT);
			}
			if (!expression.expressionType().isSubtypeOf(
				variable.expressionType()))
			{
				return interpreter.primitiveFailure(
					E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE_INTO_VARIABLE);
			}
			final AvailObject assignment =
				AssignmentNodeDescriptor.mutable().create();
			assignment.variable(variable);
			assignment.expression(expression);
			final List<AvailObject> statementsList =
				new ArrayList<AvailObject>(2);
			statementsList.add(assignment);
			final AvailObject token = LiteralTokenDescriptor.mutable().create();
			token.tokenType(TokenType.LITERAL);
			token.string(ByteStringDescriptor.from("VoidAfterAssignment"));
			token.start(0);
			token.lineNumber(0);
			token.literal(VoidDescriptor.voidObject());
			statementsList.add(LiteralNodeDescriptor.fromToken(token));
			final AvailObject statementsTuple =
				TupleDescriptor.fromList(statementsList);
			final AvailObject sequence =
				SequenceNodeDescriptor.newStatements(statementsTuple);
			return interpreter.primitiveSuccess(sequence);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					VARIABLE_USE_NODE.o(),
					PARSE_NODE.o()),
				PARSE_NODE.o());
		}
	},

	/**
	 * <strong>Primitive 354:</strong> Transform a {@linkplain
	 * VariableUseNodeDescriptor variable use} into a {@linkplain
	 * ReferenceNodeDescriptor reference}.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim354_MacroReference_variable(354, 1, CanFold)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject variable = args.get(0);
			final AvailObject declaration = variable.declaration();
			assert declaration != null;
			final AvailObject declarationType = declaration.type();
			if (!declarationType.equals(MODULE_VARIABLE_NODE.o())
				&& !declarationType.equals(LOCAL_VARIABLE_NODE.o()))
			{
				return interpreter.primitiveFailure(
					E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE);
			}
			final AvailObject reference =
				ReferenceNodeDescriptor.mutable().create();
			reference.variable(variable);
			return interpreter.primitiveSuccess(reference);
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					VARIABLE_USE_NODE.o()),
				REFERENCE_NODE.o());
		}
	},

	/**
	 * <strong>Primitive 355:</strong> Extract the base {@linkplain
	 * ObjectTypeDescriptor objectType} from an arbitrary-depth {@linkplain
	 * ObjectMetaDescriptor objectMeta*}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim355_ExtractObjectTypeFromObjectMeta_objectMeta(355, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectMeta = args.get(0);
			return interpreter.primitiveSuccess(objectMeta.myObjectType());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ObjectMetaDescriptor.fromObjectTypeAndLevel(
						ObjectTypeDescriptor.objectTypeFromMap(
							MapDescriptor.empty()),
						IntegerRangeTypeDescriptor.create(
							IntegerDescriptor.fromInt(1),
							true,
							InfinityDescriptor.positiveInfinity(),
							true))),
				ObjectMetaDescriptor.fromObjectTypeAndLevel(
					ObjectTypeDescriptor.objectTypeFromMap(
						MapDescriptor.empty()),
					IntegerDescriptor.one().type()));
		}
	},

	/**
	 * <strong>Primitive 356:</strong> Extract the {@linkplain
	 * IntegerRangeTypeDescriptor level range} from an arbitrary-depth
	 * {@linkplain ObjectMetaDescriptor objectMeta*}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	prim356_ExtractLevelsFromObjectMeta_objectMeta(356, 1, CanFold, CannotFail)
	{
		@Override
		public @NotNull Result attempt (
			final @NotNull List<AvailObject> args,
			final @NotNull Interpreter interpreter)
		{
			assert args.size() == 1;
			final AvailObject objectMeta = args.get(0);
			return interpreter.primitiveSuccess(objectMeta.objectMetaLevels());
		}

		@Override
		protected @NotNull AvailObject privateBlockTypeRestriction ()
		{
			return ClosureTypeDescriptor.create(
				TupleDescriptor.from(
					ObjectMetaDescriptor.fromObjectTypeAndLevel(
						ObjectTypeDescriptor.objectTypeFromMap(
							MapDescriptor.empty()),
						IntegerRangeTypeDescriptor.create(
							IntegerDescriptor.fromInt(1),
							true,
							InfinityDescriptor.positiveInfinity(),
							true))),
				INTEGER_TYPE.o());
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
	}

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
		 * The primitive cannot fail.  Hence, there is no need for Avail code
		 * to run in the event of a primitive failure.  Hence, such code is
		 * forbidden (because it would be unreachable).
		 */
		CannotFail,

		/**
		 * The semantics of the primitive fall outside the usual capacity of the
		 * {@link L2Translator Level Two translator}.  The current continuation
		 * should be reified prior to attempting the primitive.  Do not attempt
		 * to fold or inline this primitive.
		 */
		Unknown
	}


	/**
	 * Attempt this primitive with the given arguments, and the {@link
	 * Interpreter interpreter} on whose behalf to attempt the primitive.
	 * If the primitive fails, it should set the primitive failure code by
	 * calling {@link Interpreter#primitiveFailure(AvailObject)} and returning
	 * its result from the primitive.  Otherwise it should set the interpreter's
	 * primitive result by calling {@link
	 * Interpreter#primitiveSuccess(AvailObject)} and then return its result
	 * from the primitive.  For unusual primitives that replace the current
	 * continuation, {@link Result#CONTINUATION_CHANGED} is more appropriate,
	 * and the primitiveResult need not be set.  For primitives that need to
	 * cause a context switch, {@link Result#SUSPENDED} should be returned.
	 *
	 * @param args The {@link List list} of arguments to the primitive.
	 * @param interpreter The {@link Interpreter} that is executing.
	 * @return The {@link Result} code indicating success or failure (or special
	 *         circumstance).
	 */
	public abstract @NotNull Result attempt (
		List<AvailObject> args,
		Interpreter interpreter);


	/**
	 * Return a closure type that restricts actual primitive blocks defined
	 * using that primitive.  The actual block's argument types must be at least
	 * as specific as this closure type's argument types, and the actual block's
	 * return type must be at least as general as this closure type's return
	 * type.  That's equivalent to the condition that the actual block's type is
	 * a subtype of this closure type.
	 *
	 * @return
	 *             A closure type that restricts the type of a block that uses
	 *             this primitive.
	 */
	protected abstract @NotNull AvailObject privateBlockTypeRestriction ();

	/**
	 * A {@linkplain ClosureTypeDescriptor closure type} that restricts the type
	 * of block that can use this primitive.  This is initialized lazily to the
	 * value provided by {@link #privateBlockTypeRestriction()}, to avoid having
	 * to compute this closure type multiple times.
	 */
	private AvailObject cachedBlockTypeRestriction;

	/**
	 * Return a closure type that restricts actual primitive blocks defined
	 * using that primitive.  The actual block's argument types must be at least
	 * as specific as this closure type's argument types, and the actual block's
	 * return type must be at least as general as this closure type's return
	 * type.  That's equivalent to the condition that the actual block's type is
	 * a subtype of this closure type.
	 *
	 * <p>
	 * Cache the value in this {@linkplain Primitive} so subsequent requests are
	 * fast.
	 * </p>
	 *
	 * @return
	 *            A closure type that restricts the type of a block that uses
	 *            this primitive.
	 */
	public final @NotNull AvailObject blockTypeRestriction ()
	{
		if (cachedBlockTypeRestriction == null)
		{
			cachedBlockTypeRestriction = privateBlockTypeRestriction();
			final AvailObject argsTupleType =
				cachedBlockTypeRestriction.argsTupleType();
			final AvailObject sizeRange = argsTupleType.sizeRange();
			assert cachedBlockTypeRestriction.equals(TERMINATES.o())
				|| (sizeRange.lowerBound().extractInt() == argCount()
					&& sizeRange.upperBound().extractInt() == argCount());
		}
		return cachedBlockTypeRestriction;
	}

	/**
	 * Return an Avail {@linkplain TypeDescriptor type} that a failure variable
	 * must accept in order to be compliant with this primitive.  A more general
	 * type is acceptable for the variable.  This type is cached upon first
	 * request and should be accessed via {@link #failureVariableType()}.
	 *
	 * <p>
	 * By default, expect the primitive to fail with a string.
	 * </p>
	 *
	 * @return
	 *             A type which is at least as specific as the type of the
	 *             failure variable declared in a block using this primitive.
	 */
	protected @NotNull AvailObject privateFailureVariableType ()
	{
		return IntegerRangeTypeDescriptor.naturalNumbers();
	}

	/**
	 * A {@linkplain TypeDescriptor type} to constrain the {@linkplain
	 * ContainerTypeDescriptor#o_InnerType(AvailObject) inner type} of the
	 * variable declaration within the primitive declaration of a block.  The
	 * actual variable must be this type or a supertype.
	 */
	private AvailObject cachedFailureVariableType;

	/**
	 * Return an Avail {@linkplain TypeDescriptor type} that a failure variable
	 * must accept in order to be compliant with this primitive.  A more general
	 * type is acceptable for the variable.  The type is cached for performance.
	 *
	 * @return
	 *             A type which is at least as specific as the type of the
	 *             failure variable declared in a block using this primitive.
	 */
	public final @NotNull AvailObject failureVariableType ()
	{
		if (cachedFailureVariableType == null)
		{
			cachedFailureVariableType = privateFailureVariableType();
			assert cachedFailureVariableType.isType();
		}
		return cachedFailureVariableType;
	}


	/**
	 * Clear all cached block type restrictions and failure variable types.
	 */
	public static void clearCachedData ()
	{
		for (final Primitive primitive : Primitive.values())
		{
			primitive.cachedBlockTypeRestriction = null;
			primitive.cachedFailureVariableType = null;
		}
	}


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
	public final boolean hasFlag(final Flag flag)
	{
		return primitiveFlags.contains(flag);
	}


	/**
	 * The number of arguments this primitive expects.
	 *
	 * @return The count of arguments for this primitive.
	 */
	public final int argCount()
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
		public static int maxPrimitiveNumber = Integer.MIN_VALUE;
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
	public static Primitive byPrimitiveNumber(final int primitiveNumber)
	{
		if (primitiveNumber >= 1
			&& primitiveNumber <= byPrimitiveNumber.length)
		{
			return byPrimitiveNumber[primitiveNumber - 1];
		}
		return null;
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
	private Primitive (
		final int primitiveNumber,
		final int argCount,
		final Flag ... flags)
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
