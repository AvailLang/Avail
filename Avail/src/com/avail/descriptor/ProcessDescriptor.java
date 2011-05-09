/**
 * descriptor/ProcessDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.error;
import java.util.Random;
import java.util.concurrent.ThreadPoolExecutor;
import com.avail.annotations.*;
import com.avail.descriptor.TypeDescriptor.Types;

/**
 * An Avail {@linkplain ProcessDescriptor process} represents an independently
 * schedulable flow of control.  Its primary feature is a continuation which is
 * repeatedly replaced with continuations representing successively more
 * advanced states, thereby effecting execution.
 *
 * <p>At the moment (2011.02.03), only one process can be executing at a time,
 * but the ultimate goal is to support very many Avail processes running on top
 * of a (smaller) {@link ThreadPoolExecutor}, each thread of which will be
 * executing an Avail process.</p>
 */
public class ProcessDescriptor
extends Descriptor
{

	/**
	 * These are the possible execution states of a {@link ProcessDescriptor
	 * process}.
	 */
	public enum ExecutionState
	{
		/**
		 * The process is running or waiting for another process to yield.
		 */
		running,

		/**
		 * The process has been suspended (always on a semaphore).
		 */
		suspended,

		/**
		 * The process has terminated.  This state is permanent.
		 */
		terminated;
	}

	/**
	 * Definitions of static flags that indicate why a {@linkplain
	 * ProcessDescriptor process} is being interrupted.  These flags are
	 * single-bit masks that can be set or cleared in the process's {@linkplain
	 * IntegerSlots#INTERRUPT_REQUEST_FLAG interrupt request flags}.  If
	 * <em>any</em> bits are set then an inter-nybblecode interrupt will take
	 * place at the next convenient time.
	 */
	public class InterruptRequestFlag
	{
		/**
		 * Interrupt because this process has executed the specified number of
		 * nybblecodes.  This can be used to implement single-stepping.
		 */
		public static final int outOfGas = 0x0001;

		/**
		 * Either this process's priority has been lowered or another process's
		 * priority has been increased.  Either way, a higher priority process
		 * than the current one may be ready to schedule, and the process
		 * scheduling machinery should have an opportunity for determining this.
		 */
		public static final int higherPriorityReady = 0x0002;
	}

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		/**
		 * The hash of this process, which is chosen randomly on demand.
		 */
		HASH_OR_ZERO,

		/**
		 * The {@link ExecutionState execution state} of the process, indicating
		 * whether the process is {@linkplain ExecutionState#running running},
		 * {@linkplain ExecutionState#suspended suspended} or {@linkplain
		 * ExecutionState#terminated terminated}.
		 */
		@EnumField(describedBy=ExecutionState.class)
		EXECUTION_STATE,

		/**
		 *
		 */
		INTERRUPT_REQUEST_FLAG
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The current {@linkplain ContinuationDescriptor state of execution} of
		 * the process.
		 */
		CONTINUATION,

		/**
		 * The priority of this process, where processes with larger values get
		 * at least as much opportunity to run as processes with lower values.
		 */
		PRIORITY,

		/**
		 * The client specified name of the {@linkplain ProcessDescriptor
		 * process}.
		 */
		NAME,

		/**
		 * A map from {@linkplain CyclicTypeDescriptor cyclic types} to values.
		 * Each process has its own unique such map, which allows processes to
		 * record process-specific values.
		 */
		PROCESS_GLOBALS,

		/**
		 * Not yet implement.  This will be a block that should be invoked after
		 * the process executes each nybblecode.  Using {@linkplain
		 * VoidDescriptor the void object} here means run without this special
		 * single-stepping mode enabled.
		 */
		BREAKPOINT_BLOCK
	}

	@Override
	public void o_BreakpointBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.BREAKPOINT_BLOCK, value);
	}

	@Override
	public void o_Continuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CONTINUATION, value);
	}

	@Override
	public void o_ExecutionState (
		final @NotNull AvailObject object,
		final @NotNull ExecutionState value)
	{
		object.integerSlotPut(IntegerSlots.EXECUTION_STATE, value.ordinal());
	}

	@Override
	public void o_HashOrZero (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH_OR_ZERO, value);
	}

	@Override
	public void o_InterruptRequestFlag (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.INTERRUPT_REQUEST_FLAG, value);
	}

	@Override
	public void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NAME, value);
	}

	@Override
	public void o_Priority (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PRIORITY, value);
	}

	@Override
	public void o_ProcessGlobals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PROCESS_GLOBALS, value);
	}

	@Override
	public @NotNull AvailObject o_BreakpointBlock (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.BREAKPOINT_BLOCK);
	}

	@Override
	public @NotNull AvailObject o_Continuation (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CONTINUATION);
	}

	@Override
	public ExecutionState o_ExecutionState (
		final @NotNull AvailObject object)
	{
		return ExecutionState.values()
			[object.integerSlot(IntegerSlots.EXECUTION_STATE)];
	}

	@Override
	public int o_HashOrZero (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH_OR_ZERO);
	}

	@Override
	public int o_InterruptRequestFlag (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.INTERRUPT_REQUEST_FLAG);
	}

	@Override
	public AvailObject o_Name (final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}

	@Override
	public AvailObject o_Priority (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PRIORITY);
	}

	@Override
	public @NotNull AvailObject o_ProcessGlobals (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PROCESS_GLOBALS);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return
			e == ObjectSlots.CONTINUATION
			|| e == ObjectSlots.NAME
			|| e == ObjectSlots.PRIORITY
			|| e == ObjectSlots.PROCESS_GLOBALS
			|| e == ObjectSlots.BREAKPOINT_BLOCK
			|| e == IntegerSlots.HASH_OR_ZERO
			|| e == IntegerSlots.EXECUTION_STATE
			|| e == IntegerSlots.INTERRUPT_REQUEST_FLAG;
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Compare processes by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return Types.PROCESS.o();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit hash value.

		int hash = object.hashOrZero();
		while (hash == 0)
		{
			hash = hashGenerator.nextInt();
		}
		object.hashOrZero(hash);
		return hash;
	}

	@Override
	public @NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		//  If I am being frozen (a container), I don't need to freeze my current value.
		//  I do, on the other hand, have to freeze my type object.
		//
		//  Do nothing.  My subobjects are all allowed to be mutable even if I'm immutable.

		object.descriptor(ProcessDescriptor.immutable());
		return object;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return Types.PROCESS.o();
	}

	/**
	 * Answer an integer extracted at the current program counter from the
	 * continuation.  The program counter will be adjusted to skip over the
	 * integer.  Use a totally naive implementation for now, with very little
	 * caching.
	 */
	@Override
	public int o_GetInteger (
		final @NotNull AvailObject object)
	{
		AvailObject contObject = object.continuation();
		int pc = contObject.pc();
		AvailObject nybblesObject = contObject.nybbles();
		byte firstNybble = nybblesObject.extractNybbleFromTupleAt(pc);
		int result = firstNybble;
		switch (firstNybble)
		{
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
				pc ++;
				break;
			case 10:
			case 11:
			case 12:
				// (x-10)*16+10+y
				result = (result << 4)
					- 150
					+ nybblesObject.extractNybbleFromTupleAt(pc+1);
				pc += 2;
				break;
			case 13:
				result = (nybblesObject.extractNybbleFromTupleAt(pc+1) << 4)
					+ nybblesObject.extractNybbleFromTupleAt(pc+2) + 58;
				pc += 3;
				break;
			case 14:
				result = 0;
				{
					for (int i = 1; i <= 4; ++ i)
					{
						result = result * 16
							+ nybblesObject.extractNybbleFromTupleAt(pc+i);
					}
				}
				pc += 5;
				break;
			case 15:
				result = 0;
				{
					for (int i = 1; i <= 8; ++ i)
					{
						result = result * 16
							+ nybblesObject.extractNybbleFromTupleAt(pc+i);
					}
				}
				pc += 9;
				break;
			default:
				error("Hey, that's not a nybble!");
		};
		contObject.pc(pc);
		return result;
	}

	@Override
	public void o_Step (
		final @NotNull AvailObject object)
	{
		//  Execute one step of the process.

		error("Process stepping is not implemented");
	}

	/**
	 * A random generator used for creating hash values as needed.
	 */
	private static Random hashGenerator = new Random();

	/**
	 * Construct a new {@link ProcessDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ProcessDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ProcessDescriptor}.
	 */
	private final static ProcessDescriptor mutable = new ProcessDescriptor(true);

	/**
	 * Answer the mutable {@link ProcessDescriptor}.
	 *
	 * @return The mutable {@link ProcessDescriptor}.
	 */
	public static ProcessDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ProcessDescriptor}.
	 */
	private final static ProcessDescriptor immutable = new ProcessDescriptor(false);

	/**
	 * Answer the immutable {@link ProcessDescriptor}.
	 *
	 * @return The immutable {@link ProcessDescriptor}.
	 */
	public static ProcessDescriptor immutable ()
	{
		return immutable;
	}
}
