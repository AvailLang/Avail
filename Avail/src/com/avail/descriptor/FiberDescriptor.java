/**
 * FiberDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.FiberDescriptor.IntegerSlots.*;
import static com.avail.descriptor.FiberDescriptor.ObjectSlots.*;
import java.util.concurrent.ThreadPoolExecutor;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.descriptor.TypeDescriptor.Types;

/**
 * An Avail {@linkplain FiberDescriptor fiber} represents an independently
 * schedulable flow of control.  Its primary feature is a continuation which is
 * repeatedly replaced with continuations representing successively more
 * advanced states, thereby effecting execution.
 *
 * <p>At the moment (2011.02.03), only one fiber can be executing at a time,
 * but the ultimate goal is to support very many Avail processes running on top
 * of a (smaller) {@link ThreadPoolExecutor}, each thread of which will be
 * executing an Avail fiber.</p>
 */
public class FiberDescriptor
extends Descriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash of this fiber, which is chosen randomly on the first demand.
		 */
		HASH_OR_ZERO,

		/**
		 * The {@linkplain ExecutionState execution state} of the fiber,
		 * indicating whether the fiber is {@linkplain ExecutionState#RUNNING
		 * running}, {@linkplain ExecutionState#SUSPENDED suspended} or
		 * {@linkplain ExecutionState#TERMINATED terminated}.
		 */
		@EnumField(describedBy=ExecutionState.class)
		EXECUTION_STATE,

		/**
		 * Flags indicating the reasons for interrupting this fiber.  If the
		 * value is zero then no interrupt is indicated.
		 */
		INTERRUPT_REQUEST_FLAGS;


		/**
		 * Interrupt because this fiber has executed the specified number of
		 * nybblecodes.  This can be used to implement single-stepping.
		 */
		public static final BitField OUT_OF_GAS = bitField(
			INTERRUPT_REQUEST_FLAGS,
			0,
			1);

		/**
		 * Either this fiber's priority has been lowered or another fiber's
		 * priority has been increased.  Either way, a higher priority fiber
		 * than the current one may be ready to schedule, and the fiber
		 * scheduling machinery should have an opportunity for determining this.
		 */
		public static final BitField HIGHER_PRIORITY_READY = bitField(
			INTERRUPT_REQUEST_FLAGS,
			1,
			1);

		/**
		 * Either this fiber's priority has been lowered or another fiber's
		 * priority has been increased.  Either way, a higher priority fiber
		 * than the current one may be ready to schedule, and the fiber
		 * scheduling machinery should have an opportunity for determining this.
		 */
		public static final BitField TERMINATION_REQUESTED = bitField(
			INTERRUPT_REQUEST_FLAGS,
			2,
			1);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The current {@linkplain ContinuationDescriptor state of execution} of
		 * the fiber.
		 */
		CONTINUATION,

		/**
		 * The priority of this fiber, where processes with larger values get
		 * at least as much opportunity to run as processes with lower values.
		 */
		PRIORITY,

		/**
		 * The client specified name of the {@linkplain FiberDescriptor
		 * fiber}.
		 */
		NAME,

		/**
		 * A map from {@linkplain AtomDescriptor atoms} to values.  Each fiber
		 * has its own unique such map, which allows processes to record
		 * fiber-specific values.  The atom identities ensure modularity and
		 * non-interference of these keys.
		 */
		PROCESS_GLOBALS,

		/**
		 * Not yet implement.  This will be a block that should be invoked after
		 * the fiber executes each nybblecode.  Using {@linkplain
		 * TopTypeDescriptor the null object} here means run without this
		 * special single-stepping mode enabled.
		 */
		BREAKPOINT_BLOCK
	}

	/**
	 * These are the possible execution states of a {@linkplain FiberDescriptor
	 * fiber}.
	 */
	public enum ExecutionState implements IntegerEnumSlotDescriptionEnum
	{
		/**
		 * The fiber is running or waiting for another fiber to yield.
		 */
		RUNNING,

		/**
		 * The fiber has been suspended.
		 */
		SUSPENDED,

		/**
		 * The fiber has terminated.  This state is permanent.
		 */
		TERMINATED;
	}

	@Override @AvailMethod
	void o_BreakpointBlock (
		final AvailObject object,
		final AvailObject value)
	{
		object.setSlot(BREAKPOINT_BLOCK, value);
	}

	@Override @AvailMethod
	void o_Continuation (
		final AvailObject object,
		final AvailObject value)
	{
		object.setSlot(CONTINUATION, value);
	}

	@Override @AvailMethod
	void o_ExecutionState (
		final AvailObject object,
		final ExecutionState value)
	{
		object.setSlot(EXECUTION_STATE, value.ordinal());
	}

	@Override @AvailMethod
	void o_HashOrZero (
		final AvailObject object,
		final int value)
	{
		object.setSlot(HASH_OR_ZERO, value);
	}

	@Override
	synchronized void o_ClearInterruptRequestFlags (
		final AvailObject object)
	{
		object.setSlot(INTERRUPT_REQUEST_FLAGS, 0);
	}

	@Override @AvailMethod
	synchronized void o_SetInterruptRequestFlag (
		final AvailObject object,
		final BitField field)
	{
		assert field.integerSlot == INTERRUPT_REQUEST_FLAGS;
		object.setSlot(field, 1);
	}

	@Override @AvailMethod
	void o_Name (
		final AvailObject object,
		final AvailObject value)
	{
		object.setSlot(NAME, value);
	}

	@Override @AvailMethod
	void o_Priority (
		final AvailObject object,
		final AvailObject value)
	{
		object.setSlot(PRIORITY, value);
	}

	@Override @AvailMethod
	void o_FiberGlobals (
		final AvailObject object,
		final AvailObject value)
	{
		object.setSlot(PROCESS_GLOBALS, value);
	}

	@Override @AvailMethod
	AvailObject o_BreakpointBlock (
		final AvailObject object)
	{
		return object.slot(BREAKPOINT_BLOCK);
	}

	@Override @AvailMethod
	AvailObject o_Continuation (
		final AvailObject object)
	{
		return object.slot(CONTINUATION);
	}

	@Override @AvailMethod
	ExecutionState o_ExecutionState (
		final AvailObject object)
	{
		return ExecutionState.values()[object.slot(EXECUTION_STATE)];
	}

	@Override @AvailMethod
	int o_HashOrZero (
		final AvailObject object)
	{
		return object.slot(HASH_OR_ZERO);
	}

	@Override @AvailMethod
	synchronized int o_InterruptRequestFlags (
		final AvailObject object)
	{
		return object.slot(INTERRUPT_REQUEST_FLAGS);
	}

	@Override @AvailMethod
	AvailObject o_Name (final AvailObject object)
	{
		return object.slot(NAME);
	}

	@Override @AvailMethod
	AvailObject o_Priority (
		final AvailObject object)
	{
		return object.slot(PRIORITY);
	}

	@Override @AvailMethod
	AvailObject o_FiberGlobals (
		final AvailObject object)
	{
		return object.slot(PROCESS_GLOBALS);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return
			e == CONTINUATION
			|| e == NAME
			|| e == PRIORITY
			|| e == PROCESS_GLOBALS
			|| e == BREAKPOINT_BLOCK
			|| e == HASH_OR_ZERO
			|| e == EXECUTION_STATE
			|| e == INTERRUPT_REQUEST_FLAGS;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		//  Compare processes by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			do
			{
				hash = AvailRuntime.nextHash();
			}
			while (hash == 0);
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (
		final AvailObject object)
	{
		object.descriptor = immutable();
		return object;
	}

	@Override @AvailMethod
	AvailObject o_Kind (
		final AvailObject object)
	{
		return Types.FIBER.o();
	}

	/**
	 * Answer an integer extracted at the current program counter from the
	 * continuation.  The program counter will be adjusted to skip over the
	 * integer.  Use a totally naive implementation for now, with very little
	 * caching.
	 */
	@Override @AvailMethod
	int o_GetInteger (
		final AvailObject object)
	{
		final AvailObject contObject = object.continuation();
		int pc = contObject.pc();
		final AvailObject nybbles = contObject.nybbles();
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc);
		int value = 0;
		pc++;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[firstNybble]; count > 0; count--, pc++)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc);
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[firstNybble];
		contObject.pc(pc);
		return value;
	}

	@Override @AvailMethod
	void o_Step (
		final AvailObject object)
	{
		error("Process stepping is not implemented");
	}

	/**
	 * Construct a new {@link FiberDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected FiberDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link FiberDescriptor}.
	 */
	private static final FiberDescriptor mutable = new FiberDescriptor(true);

	/**
	 * Answer the mutable {@link FiberDescriptor}.
	 *
	 * @return The mutable {@link FiberDescriptor}.
	 */
	public static FiberDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link FiberDescriptor}.
	 */
	private static final FiberDescriptor immutable = new FiberDescriptor(false);

	/**
	 * Answer the immutable {@link FiberDescriptor}.
	 *
	 * @return The immutable {@link FiberDescriptor}.
	 */
	public static FiberDescriptor immutable ()
	{
		return immutable;
	}
}
