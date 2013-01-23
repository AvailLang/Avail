/**
 * FiberDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
 * schedulable flow of control. Its primary feature is a continuation which is
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
	public enum IntegerSlots
	implements IntegerSlotsEnum
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
	public enum ObjectSlots
	implements ObjectSlotsEnum
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
		 * fiber-specific values. The atom identities ensure modularity and
		 * non-interference of these keys.
		 */
		FIBER_GLOBALS,

		/**
		 * Not yet implemented. This will be a block that should be invoked
		 * after the fiber executes each nybblecode. Using {@linkplain
		 * NilDescriptor nil} here means run without this special
		 * single-stepping mode enabled.
		 */
		BREAKPOINT_BLOCK
	}

	/**
	 * These are the possible execution states of a {@linkplain FiberDescriptor
	 * fiber}.
	 */
	public enum ExecutionState
	implements IntegerEnumSlotDescriptionEnum
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
		 * The fiber has terminated. This state is permanent.
		 */
		TERMINATED;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return
			e == CONTINUATION
			|| e == NAME
			|| e == PRIORITY
			|| e == FIBER_GLOBALS
			|| e == BREAKPOINT_BLOCK
			|| e == HASH_OR_ZERO
			|| e == EXECUTION_STATE
			|| e == INTERRUPT_REQUEST_FLAGS;
	}

	@Override @AvailMethod
	ExecutionState o_ExecutionState (final AvailObject object)
	{
		return ExecutionState.values()[object.mutableSlot(EXECUTION_STATE)];
	}

	@Override @AvailMethod
	void o_ExecutionState (final AvailObject object, final ExecutionState value)
	{
		object.setMutableSlot(EXECUTION_STATE, value.ordinal());
	}

	@Override
	void o_ClearInterruptRequestFlags (final AvailObject object)
	{
		object.setMutableSlot(INTERRUPT_REQUEST_FLAGS, 0);
	}

	@Override @AvailMethod
	void o_SetInterruptRequestFlag (
		final AvailObject object,
		final BitField field)
	{
		assert field.integerSlot == INTERRUPT_REQUEST_FLAGS;
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(field, 1);
			}
		}
		else
		{
			object.setSlot(field, 1);
		}
	}

	@Override @AvailMethod
	int o_InterruptRequestFlags (final AvailObject object)
	{
		return object.mutableSlot(INTERRUPT_REQUEST_FLAGS);
	}

	@Override @AvailMethod
	AvailObject o_Continuation (final AvailObject object)
	{
		return object.mutableSlot(CONTINUATION);
	}

	@Override @AvailMethod
	void o_Continuation (final AvailObject object, final AvailObject value)
	{
		object.setMutableSlot(CONTINUATION, value);
	}

	@Override @AvailMethod
	AvailObject o_Priority (final AvailObject object)
	{
		return object.mutableSlot(PRIORITY);
	}

	@Override @AvailMethod
	void o_Priority (final AvailObject object, final AvailObject value)
	{
		object.setMutableSlot(PRIORITY, value);
	}

	@Override @AvailMethod
	AvailObject o_Name (final AvailObject object)
	{
		return object.mutableSlot(NAME);
	}

	@Override @AvailMethod
	void o_Name (final AvailObject object, final AvailObject value)
	{
		object.setMutableSlot(NAME, value);
	}

	@Override @AvailMethod
	AvailObject o_FiberGlobals (final AvailObject object)
	{
		return object.mutableSlot(FIBER_GLOBALS);
	}

	@Override @AvailMethod
	void o_FiberGlobals (final AvailObject object, final AvailObject value)
	{
		object.setMutableSlot(FIBER_GLOBALS, value);
	}

	@Override @AvailMethod
	AvailObject o_BreakpointBlock (final AvailObject object)
	{
		return object.mutableSlot(BREAKPOINT_BLOCK);
	}

	@Override @AvailMethod
	void o_BreakpointBlock (final AvailObject object, final AvailObject value)
	{
		object.setMutableSlot(BREAKPOINT_BLOCK, value);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final AvailObject another)
	{
		// Compare fibers by address (identity).
		return another.traversed().sameAddressAs(object);
	}

	/**
	 * Lazily compute and install the hash of the specified {@linkplain
	 * FiberDescriptor object}.
	 *
	 * @param object An object.
	 * @return The hash.
	 */
	private int hash (final AvailObject object)
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
	int o_Hash (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return hash(object);
			}
		}
		return hash(object);
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return Types.FIBER.o();
	}

	/**
	 * Answer an integer extracted at the current program counter from the
	 * continuation. The program counter will be adjusted to skip over the
	 * integer. Use a totally naive implementation for now, with very little
	 * caching.
	 */
	@Override @AvailMethod
	int o_GetInteger (final AvailObject object)
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
	void o_Step (final AvailObject object)
	{
		error("Process stepping is not implemented");
	}

	/**
	 * Create a new fiber.
	 *
	 * @param initialState The initial execution state of the new fiber.
	 * @return The new fiber.
	 */
	public static AvailObject create (
		final ExecutionState initialState)
	{
		final AvailObject fiber = mutable.create();
		fiber.name(StringDescriptor.from(String.format(
			"unnamed, creation time = %d, hash = %d",
			System.currentTimeMillis(),
			fiber.hash())));
		fiber.priority(IntegerDescriptor.fromUnsignedByte((short)50));
		fiber.continuation(NilDescriptor.nil());
		fiber.executionState(initialState);
		fiber.clearInterruptRequestFlags();
		fiber.breakpointBlock(NilDescriptor.nil());
		fiber.fiberGlobals(MapDescriptor.empty());
		fiber.makeImmutable();
		return fiber;
	}


	/**
	 * Construct a new {@link FiberDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private FiberDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link FiberDescriptor}. */
	public static final FiberDescriptor mutable =
		new FiberDescriptor(Mutability.MUTABLE);

	@Override
	FiberDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link FiberDescriptor}. */
	private static final FiberDescriptor immutable =
		new FiberDescriptor(Mutability.IMMUTABLE);

	@Override
	FiberDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link FiberDescriptor}. */
	private static final FiberDescriptor shared =
		new FiberDescriptor(Mutability.SHARED);

	@Override
	FiberDescriptor shared ()
	{
		return shared;
	}
}
