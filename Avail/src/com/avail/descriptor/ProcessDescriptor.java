/**
 * ProcessDescriptor.java
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
import java.util.Random;
import java.util.concurrent.ThreadPoolExecutor;
import com.avail.AvailRuntime;
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
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash of this process, which is chosen randomly on demand.
		 */
		HASH_OR_ZERO,

		/**
		 * The {@linkplain ExecutionState execution state} of the process, indicating
		 * whether the process is {@linkplain ExecutionState#RUNNING running},
		 * {@linkplain ExecutionState#SUSPENDED suspended} or {@linkplain
		 * ExecutionState#TERMINATED terminated}.
		 */
		@EnumField(describedBy=ExecutionState.class)
		EXECUTION_STATE,

		/**
		 * Flags indicating the reasons for interrupting this process.  If the
		 * value is zero then no interrupt is indicated.
		 */
		INTERRUPT_REQUEST_FLAG;


		/**
		 * Interrupt because this process has executed the specified number of
		 * nybblecodes.  This can be used to implement single-stepping.
		 */
		static final BitField OUT_OF_GAS = bitField(
			INTERRUPT_REQUEST_FLAG,
			0,
			1);

		/**
		 * Either this process's priority has been lowered or another process's
		 * priority has been increased.  Either way, a higher priority process
		 * than the current one may be ready to schedule, and the process
		 * scheduling machinery should have an opportunity for determining this.
		 */
		static final BitField HIGHER_PRIORITY_READY = bitField(
			INTERRUPT_REQUEST_FLAG,
			1,
			1);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
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
		 * A map from {@linkplain AtomDescriptor atoms} to values.  Each process
		 * has its own unique such map, which allows processes to record
		 * process-specific values.  The atom identities ensure modularity and
		 * non-interference of these keys.
		 */
		PROCESS_GLOBALS,

		/**
		 * Not yet implement.  This will be a block that should be invoked after
		 * the process executes each nybblecode.  Using {@linkplain
		 * TopTypeDescriptor the null object} here means run without this
		 * special single-stepping mode enabled.
		 */
		BREAKPOINT_BLOCK
	}

	/**
	 * These are the possible execution states of a {@linkplain ProcessDescriptor
	 * process}.
	 */
	public enum ExecutionState implements IntegerEnumSlotDescriptionEnum
	{
		/**
		 * The process is running or waiting for another process to yield.
		 */
		RUNNING,

		/**
		 * The process has been suspended (always on a semaphore).
		 */
		SUSPENDED,

		/**
		 * The process has terminated.  This state is permanent.
		 */
		TERMINATED;
	}

	@Override @AvailMethod
	void o_BreakpointBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.BREAKPOINT_BLOCK, value);
	}

	@Override @AvailMethod
	void o_Continuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.CONTINUATION, value);
	}

	@Override @AvailMethod
	void o_ExecutionState (
		final @NotNull AvailObject object,
		final @NotNull ExecutionState value)
	{
		object.setSlot(IntegerSlots.EXECUTION_STATE, value.ordinal());
	}

	@Override @AvailMethod
	void o_HashOrZero (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.HASH_OR_ZERO, value);
	}

	@Override @AvailMethod
	void o_InterruptRequestFlag (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.INTERRUPT_REQUEST_FLAG, value);
	}

	@Override @AvailMethod
	void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.NAME, value);
	}

	@Override @AvailMethod
	void o_Priority (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.PRIORITY, value);
	}

	@Override @AvailMethod
	void o_ProcessGlobals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.PROCESS_GLOBALS, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BreakpointBlock (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.BREAKPOINT_BLOCK);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Continuation (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.CONTINUATION);
	}

	@Override @AvailMethod
	ExecutionState o_ExecutionState (
		final @NotNull AvailObject object)
	{
		return ExecutionState.values()
			[object.slot(IntegerSlots.EXECUTION_STATE)];
	}

	@Override @AvailMethod
	int o_HashOrZero (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.HASH_OR_ZERO);
	}

	@Override @AvailMethod
	int o_InterruptRequestFlag (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.INTERRUPT_REQUEST_FLAG);
	}

	@Override @AvailMethod
	AvailObject o_Name (final AvailObject object)
	{
		return object.slot(ObjectSlots.NAME);
	}

	@Override @AvailMethod
	AvailObject o_Priority (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.PRIORITY);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ProcessGlobals (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.PROCESS_GLOBALS);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
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

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Compare processes by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		int hash = object.slot(IntegerSlots.HASH_OR_ZERO);
		if (hash == 0)
		{
			do
			{
				hash = AvailRuntime.nextHash();
			}
			while (hash == 0);
			object.setSlot(IntegerSlots.HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor = immutable();
		return object;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return Types.PROCESS.o();
	}

	/**
	 * Answer an integer extracted at the current program counter from the
	 * continuation.  The program counter will be adjusted to skip over the
	 * integer.  Use a totally naive implementation for now, with very little
	 * caching.
	 */
	@Override @AvailMethod
	int o_GetInteger (
		final @NotNull AvailObject object)
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
		final @NotNull AvailObject object)
	{
		//  Execute one step of the process.

		error("Process stepping is not implemented");
	}

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
	private static final ProcessDescriptor mutable = new ProcessDescriptor(true);

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
	private static final ProcessDescriptor immutable = new ProcessDescriptor(false);

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
