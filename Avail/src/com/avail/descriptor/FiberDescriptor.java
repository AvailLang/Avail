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
import static com.avail.descriptor.FiberDescriptor.ExecutionState.*;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import com.avail.*;
import com.avail.annotations.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.*;
import com.avail.utility.*;

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
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class FiberDescriptor
extends Descriptor
{
	/** The priority of compilation tasks. */
	public static final int compilerPriority = 50;

	/** The priority of loading tasks. */
	public static final int loaderPriority = 50;

	/**
	 * The advisory interrupt request flags. Bits [0..3] of a {@linkplain
	 * FiberDescriptor fiber}'s flags slot are reserved for interrupt request
	 * flags.
	 */
	public static enum InterruptRequestFlag
	{
		/**
		 * Termination of the target fiber has been requested.
		 */
		TERMINATION_REQUESTED (0);

		/** The {@linkplain BitField bit field}. */
		final BitField bitField;

		/**
		 * Construct a new {@link InterruptRequestFlag}.
		 *
		 * @param index
		 *        The bit index into the sub-slot containing the interrupt
		 *        request flags.
		 */
		private InterruptRequestFlag (final int index)
		{
			this.bitField = bitField(FLAGS, index, 1);
		}
	}

	/**
	 * The synchronization flags. Bits [4..7] of a {@linkplain FiberDescriptor
	 * fiber}'s flags slot are reserved for synchronization flags.
	 */
	public static enum SynchronizationFlag
	{
		/**
		 * The fiber is bound to an {@linkplain Interpreter interpreter}.
		 */
		BOUND (0),

		/**
		 * The fiber has been scheduled for resumption.
		 */
		SCHEDULED (1),

		/**
		 * The parking permit is unavailable.
		 */
		PERMIT_UNAVAILABLE (2);

		/** The {@linkplain BitField bit field}. */
		final BitField bitField;

		/**
		 * Construct a new {@link SynchronizationFlag}.
		 *
		 * @param index
		 *        The bit index into the sub-slot containing the synchronization
		 *        flags.
		 */
		private SynchronizationFlag (final int index)
		{
			this.bitField = bitField(FLAGS, index + 4, 1);
		}
	}

	/**
	 * The general flags. Bits [8..31] of a {@linkplain FiberDescriptor fiber}'s
	 * flags slot are reserved for general flags.
	 */
	public static enum GeneralFlag
	{
		/**
		 * Was the fiber started to apply a semantic restriction?
		 */
		APPLYING_SEMANTIC_RESTRICTION (0);

		/** The {@linkplain BitField bit field}. */
		final BitField bitField;

		/**
		 * Construct a new {@link GeneralFlag}.
		 *
		 * @param index
		 *        The bit index into the sub-slot containing the synchronization
		 *        flags.
		 */
		private GeneralFlag (final int index)
		{
			this.bitField = bitField(FLAGS, index + 8, 1);
		}
	}

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
		 * The priority of this fiber, where processes with larger values get
		 * at least as much opportunity to run as processes with lower values.
		 */
		PRIORITY,

		/**
		 * Flags for use by Avail code. Includes the advisory termination
		 * requested interrupt flag and the parking permit.
		 */
		FLAGS
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
		 * The client specified name of the {@linkplain FiberDescriptor
		 * fiber}.
		 */
		NAME,

		/**
		 * A map from {@linkplain AtomDescriptor atoms} to values. Each fiber
		 * has its own unique such map, which allows processes to record
		 * fiber-specific values. The atom identities ensure modularity and
		 * non-interference of these keys.
		 */
		FIBER_GLOBALS,

		/**
		 * A map from {@linkplain AtomDescriptor atoms} to heritable values.
		 * When a fiber forks a new fiber, the new fiber inherits this map. The
		 * atom identities ensure modularity and non-interference of these keys.
		 */
		HERITABLE_FIBER_GLOBALS,

		/**
		 * The result of running this {@linkplain FiberDescriptor fiber} to
		 * completion.
		 */
		RESULT,

		/**
		 * Not yet implemented. This will be a block that should be invoked
		 * after the fiber executes each nybblecode. Using {@linkplain
		 * NilDescriptor nil} here means run without this special
		 * single-stepping mode enabled.
		 */
		BREAKPOINT_BLOCK,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping an {@linkplain
		 * AvailLoader Avail loader}. This pertains only to load-time fibers,
		 * and indicates which loader originated the fiber.
		 */
		LOADER,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
		 * Continuation1 continuation} that should be called with the
		 * {@linkplain AvailObject result} of executing the fiber to its
		 * natural conclusion.
		 */
		RESULT_CONTINUATION,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
		 * Continuation1 continuation} that should be called with the
		 * {@linkplain Throwable throwable} responsible for the untimely death
		 * of the fiber.
		 */
		FAILURE_CONTINUATION,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain FiberDescriptor
		 * fibers} waiting to join the current fiber. Access to this field is
		 * synchronized by the {@linkplain Interpreter#joinLock global join
		 * lock}.
		 */
		JOINING_FIBERS,

		/**
		 * The {@linkplain FiberDescriptor fiber} that this fiber is attempting
		 * to join, or {@linkplain NilDescriptor nil} if this fiber is not in
		 * the {@linkplain ExecutionState#JOINING state}. Access to this field
		 * is synchronized by the {@linkplain Interpreter#joinLock global join
		 * lock}.
		 */
		JOINEE,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
		 * TimerTask timer task} responsible for waking up the {@linkplain
		 * ExecutionState#ASLEEP sleeping} {@linkplain FiberDescriptor fiber}.
		 */
		WAKEUP_TASK
	}

	/**
	 * These are the possible execution states of a {@linkplain FiberDescriptor
	 * fiber}.
	 */
	public enum ExecutionState
	implements IntegerEnumSlotDescriptionEnum
	{
		/**
		 * The fiber has not been started.
		 */
		UNSTARTED
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			protected void init ()
			{
				successors = EnumSet.of(RUNNING);
			}
		},

		/**
		 * The fiber is running or waiting for another fiber to yield.
		 */
		RUNNING
		{
			@Override
			protected void init ()
			{
				successors = EnumSet.of(
					SUSPENDED,
					INTERRUPTED,
					PARKED,
					JOINING,
					TERMINATED,
					ABORTED);
			}
		},

		/**
		 * The fiber has been suspended.
		 */
		SUSPENDED
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			protected void init ()
			{
				successors = EnumSet.of(
					RUNNING,
					ABORTED,
					ASLEEP);
			}
		},

		/**
		 * The fiber has been interrupted.
		 */
		INTERRUPTED
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			protected void init ()
			{
				successors = EnumSet.of(RUNNING);
			}
		},

		/**
		 * The fiber has been parked.
		 */
		PARKED
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			public boolean indicatesVoluntarySuspension ()
			{
				return true;
			}

			@Override
			protected void init ()
			{
				successors = EnumSet.of(SUSPENDED);
			}
		},

		/**
		 * The fiber waiting to join another fiber.
		 */
		JOINING
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			public boolean indicatesVoluntarySuspension ()
			{
				return true;
			}

			@Override
			protected void init ()
			{
				successors = EnumSet.of(SUSPENDED);
			}
		},

		/**
		 * The fiber is asleep.
		 */
		ASLEEP
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			public boolean indicatesVoluntarySuspension ()
			{
				return true;
			}

			@Override
			protected void init ()
			{
				successors = EnumSet.of(SUSPENDED);
			}
		},

		/**
		 * The fiber has terminated successfully. This state is permanent.
		 */
		TERMINATED
		{
			@Override
			public boolean indicatesTermination ()
			{
				return true;
			}

			@Override
			protected void init ()
			{
				successors = EnumSet.of(ABORTED);
			}
		},

		/**
		 * The fiber has aborted (due to an exception). This state is permanent.
		 */
		ABORTED
		{
			@Override
			public boolean indicatesTermination ()
			{
				return true;
			}
		};

		/** The valid successor {@linkplain ExecutionState states}. */
		protected Set<ExecutionState> successors;

		// Initialize all of the successors.
		static
		{
			for (final ExecutionState state : values())
			{
				state.init();
			}
		}

		/**
		 * Set up the successor {@linkplain ExecutionState states}.
		 */
		protected void init ()
		{
			// Do nothing.
		}

		/**
		 * Does this {@linkplain ExecutionState execution state} indicate that
		 * a {@linkplain FiberDescriptor fiber} is suspended for some reason?
		 *
		 * @return {@code true} if the execution state represents suspension,
		 *         {@code false} otherwise.
		 */
		public boolean indicatesSuspension ()
		{
			return false;
		}

		/**
		 * Does this {@linkplain ExecutionState execution state} indicate that
		 * a {@linkplain FiberDescriptor fiber} suspended itself voluntarily
		 * for some reason?
		 *
		 * @return {@code true} if the execution state represents voluntary
		 *         suspension, {@code false} otherwise.
		 */
		public boolean indicatesVoluntarySuspension ()
		{
			return false;
		}

		/**
		 * Does this {@linkplain ExecutionState execution state} indicate that
		 * a {@linkplain FiberDescriptor fiber} has terminated for some reason?
		 *
		 * @return {@code true} if the execution state represents termination,
		 *         {@code false} otherwise.
		 */
		public boolean indicatesTermination ()
		{
			return false;
		}
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		// Allow mutable access to all fiber slots.
		return true;
	}

	@Override @AvailMethod
	ExecutionState o_ExecutionState (final AvailObject object)
	{
		return ExecutionState.values()[object.mutableSlot(EXECUTION_STATE)];
	}

	@Override @AvailMethod
	void o_ExecutionState (final AvailObject object, final ExecutionState value)
	{
		if (isShared())
		{
			synchronized (object)
			{
				final ExecutionState current = ExecutionState.values()
					[object.mutableSlot(EXECUTION_STATE)];
				assert current.successors.contains(value);
				object.setSlot(EXECUTION_STATE, value.ordinal());
			}
		}
		else
		{
			final ExecutionState current = ExecutionState.values()
				[object.mutableSlot(EXECUTION_STATE)];
			assert current.successors.contains(value);
			object.setSlot(EXECUTION_STATE, value.ordinal());
		}
	}

	@Override @AvailMethod
	int o_Priority (final AvailObject object)
	{
		return object.mutableSlot(PRIORITY);
	}

	@Override @AvailMethod
	void o_Priority (final AvailObject object, final int value)
	{
		object.setMutableSlot(PRIORITY, value);
	}

	@Override @AvailMethod
	void o_ClearInterruptRequestFlags (final AvailObject object)
	{
		object.setMutableSlot(FLAGS, 0);
	}

	@Override @AvailMethod
	boolean o_InterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		final int value;
		if (isShared())
		{
			synchronized (object)
			{
				value = object.slot(flag.bitField);
			}
		}
		else
		{
			value = object.slot(flag.bitField);
		}
		return value == 1;
	}

	@Override @AvailMethod
	void o_SetInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(flag.bitField, 1);
			}
		}
		else
		{
			object.setSlot(flag.bitField, 1);
		}
	}

	@Override @AvailMethod
	boolean o_GetAndClearInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		final int value;
		if (isShared())
		{
			synchronized (object)
			{
				value = object.slot(flag.bitField);
				object.setSlot(flag.bitField, 0);
			}
		}
		else
		{
			value = object.slot(flag.bitField);
			object.setSlot(flag.bitField, 0);
		}
		return value == 1;
	}

	@Override @AvailMethod
	boolean o_GetAndSetSynchronizationFlag (
		final AvailObject object,
		final SynchronizationFlag flag,
		final boolean newValue)
	{
		final int value;
		final int newBit = newValue ? 1 : 0;
		if (isShared())
		{
			synchronized (object)
			{
				value = object.slot(flag.bitField);
				object.setSlot(flag.bitField, newBit);
			}
		}
		else
		{
			value = object.slot(flag.bitField);
			object.setSlot(flag.bitField, newBit);
		}
		return value == 1;
	}

	@Override @AvailMethod
	boolean o_GeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		final int value;
		if (isShared())
		{
			synchronized (object)
			{
				value = object.slot(flag.bitField);
			}
		}
		else
		{
			value = object.slot(flag.bitField);
		}
		return value == 1;
	}

	@Override @AvailMethod
	void o_SetGeneralFlag (
		final AvailObject object,
		final GeneralFlag flag)
	{
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(flag.bitField, 1);
			}
		}
		else
		{
			object.setSlot(flag.bitField, 1);
		}
	}

	@Override @AvailMethod
	void o_ClearGeneralFlag (
		final AvailObject object,
		final GeneralFlag flag)
	{
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(flag.bitField, 0);
			}
		}
		else
		{
			object.setSlot(flag.bitField, 0);
		}
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
	void o_FiberGlobals (final AvailObject object, final AvailObject globals)
	{
		object.setMutableSlot(FIBER_GLOBALS, globals);
	}

	@Override @AvailMethod
	AvailObject o_HeritableFiberGlobals (final AvailObject object)
	{
		return object.mutableSlot(HERITABLE_FIBER_GLOBALS);
	}

	@Override @AvailMethod
	void o_HeritableFiberGlobals (
		final AvailObject object,
		final AvailObject globals)
	{
		object.setMutableSlot(HERITABLE_FIBER_GLOBALS, globals);
	}

	@Override @AvailMethod
	AvailObject o_FiberResult (final AvailObject object)
	{
		return object.mutableSlot(RESULT);
	}

	@Override @AvailMethod
	void o_FiberResult (final AvailObject object, final AvailObject result)
	{
		object.setMutableSlot(RESULT, result);
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
	@Nullable AvailLoader o_AvailLoader (final AvailObject object)
	{
		final AvailObject pojo = object.mutableSlot(LOADER);
		if (!pojo.equalsNil())
		{
			return (AvailLoader) pojo.javaObject();
		}
		return null;
	}

	@Override @AvailMethod
	void o_AvailLoader (
		final AvailObject object,
		final @Nullable AvailLoader loader)
	{
		object.setMutableSlot(
			LOADER,
			loader == null
			? NilDescriptor.nil()
			: RawPojoDescriptor.identityWrap(loader));
	}

	/**
	 * The default result continuation, answered when a {@linkplain
	 * FiberDescriptor fiber}'s result continuation is {@linkplain
	 * NilDescriptor nil}.
	 */
	private static final Continuation1<AvailObject> defaultResultContinuation =
		new Continuation1<AvailObject>()
		{
			@Override
			public void value (final @Nullable AvailObject ignored)
			{
				// Do nothing.
			}
		};

	@SuppressWarnings("unchecked")
	@Override @AvailMethod
	Continuation1<AvailObject> o_ResultContinuation (
		final AvailObject object)
	{
		final AvailObject pojo = object.mutableSlot(RESULT_CONTINUATION);
		if (!pojo.equalsNil())
		{
			return (Continuation1<AvailObject>) pojo.javaObject();
		}
		return defaultResultContinuation;
	}

	@Override @AvailMethod
	void o_ResultContinuation (
		final AvailObject object,
		final Continuation1<AvailObject> continuation)
	{
		object.setMutableSlot(
			RESULT_CONTINUATION, RawPojoDescriptor.identityWrap(continuation));
	}

	/**
	 * The default result continuation, answered when a {@linkplain
	 * FiberDescriptor fiber}'s result continuation is {@linkplain
	 * NilDescriptor nil}.
	 */
	private static final Continuation1<Throwable> defaultFailureContinuation =
		new Continuation1<Throwable>()
		{
			@Override
			public void value (final @Nullable Throwable ignored)
			{
				// TODO: [TLS] Log something, maybe?
			}
		};

	@SuppressWarnings("unchecked")
	@Override @AvailMethod
	Continuation1<Throwable> o_FailureContinuation (
		final AvailObject object)
	{
		final AvailObject pojo = object.mutableSlot(FAILURE_CONTINUATION);
		if (!pojo.equalsNil())
		{
			return (Continuation1<Throwable>) pojo.javaObject();
		}
		return defaultFailureContinuation;
	}

	@Override @AvailMethod
	void o_FailureContinuation (
		final AvailObject object,
		final Continuation1<Throwable> continuation)
	{
		object.setMutableSlot(
			FAILURE_CONTINUATION, RawPojoDescriptor.identityWrap(continuation));
	}

	@Override @AvailMethod
	AvailObject o_JoiningFibers (final AvailObject object)
	{
		return object.mutableSlot(JOINING_FIBERS);
	}

	@Override @AvailMethod
	void o_JoiningFibers (final AvailObject object, final AvailObject joiners)
	{
		object.setMutableSlot(JOINING_FIBERS, joiners);
	}

	@Override @AvailMethod
	AvailObject o_Joinee (final AvailObject object)
	{
		return object.mutableSlot(JOINEE);
	}

	@Override @AvailMethod
	void o_Joinee (final AvailObject object, final AvailObject joinee)
	{
		object.setMutableSlot(JOINEE, joinee);
	}

	@Override @AvailMethod
	@Nullable TimerTask o_WakeupTask (final AvailObject object)
	{
		final AvailObject pojo = object.mutableSlot(WAKEUP_TASK);
		if (!pojo.equalsNil())
		{
			return (TimerTask) pojo.javaObject();
		}
		return null;
	}

	@Override @AvailMethod
	void o_WakeupTask (final AvailObject object, final @Nullable TimerTask task)
	{
		object.setMutableSlot(
			WAKEUP_TASK,
			task == null
			? NilDescriptor.nil()
			: RawPojoDescriptor.identityWrap(task));
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
				// This guarantees the uniqueness of fiber hashes (modulo 2^32),
				// but makes it play more nicely with sets (to prevent
				// clumping).
				hash = (AvailRuntime.current().nextFiberId()
					* AvailObject.multiplier)
					^ 0x4058A781;
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

	@Override
	void o_Lock (final AvailObject object, final Continuation0 critical)
	{
		// A fiber always needs to acquire a lock, even if it's not mutable, as
		// this prevents races between two threads where one is exiting a fiber
		// and the other is resuming the same fiber.
		synchronized (object)
		{
			critical.value();
		}
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
	static final FiberDescriptor mutable =
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

	/**
	 * Construct an {@linkplain ExecutionState#UNSTARTED unstarted} {@linkplain
	 * FiberDescriptor fiber} with the specified initial priority.
	 *
	 * @param priority
	 *        The initial priority.
	 * @return The new fiber.
	 */
	public static AvailObject newFiber (final int priority)
	{
		final AvailObject fiber = FiberDescriptor.mutable.create();
		fiber.setSlot(
			NAME,
			StringDescriptor.from(String.format(
				"unnamed, creation time = %d, hash = %d",
				System.currentTimeMillis(),
				fiber.hash())));
		fiber.setSlot(PRIORITY, priority);
		fiber.setSlot(CONTINUATION, NilDescriptor.nil());
		fiber.setSlot(EXECUTION_STATE, UNSTARTED.ordinal());
		fiber.setSlot(FLAGS, 0);
		fiber.setSlot(BREAKPOINT_BLOCK, NilDescriptor.nil());
		fiber.setSlot(FIBER_GLOBALS, MapDescriptor.empty());
		fiber.setSlot(HERITABLE_FIBER_GLOBALS, MapDescriptor.empty());
		fiber.setSlot(RESULT, NilDescriptor.nil());
		fiber.setSlot(LOADER, NilDescriptor.nil());
		fiber.setSlot(RESULT_CONTINUATION, NilDescriptor.nil());
		fiber.setSlot(FAILURE_CONTINUATION, NilDescriptor.nil());
		fiber.setSlot(JOINING_FIBERS, SetDescriptor.empty());
		fiber.setSlot(JOINEE, NilDescriptor.nil());
		fiber.setSlot(WAKEUP_TASK, NilDescriptor.nil());
		return fiber;
	}

	/**
	 * Construct an {@linkplain ExecutionState#UNSTARTED unstarted} {@linkplain
	 * FiberDescriptor fiber} with the specified {@linkplain
	 * AvailLoader Avail loader}. The priority is initially set to {@linkplain
	 * #loaderPriority}.
	 *
	 * @param loader
	 *        An Avail loader.
	 * @return The new fiber.
	 */
	public static AvailObject newLoaderFiber (final AvailLoader loader)
	{
		final AvailObject fiber = FiberDescriptor.mutable.create();
		final AvailObject module = loader.module();
		assert module != null;
		fiber.setSlot(
			NAME,
			StringDescriptor.from(String.format(
				"loader fiber #%d for %s",
				AvailRuntime.nextHash(),
				module.name())));
		fiber.setSlot(PRIORITY, FiberDescriptor.loaderPriority);
		fiber.setSlot(CONTINUATION, NilDescriptor.nil());
		fiber.setSlot(EXECUTION_STATE, UNSTARTED.ordinal());
		fiber.setSlot(FLAGS, 0);
		fiber.setSlot(BREAKPOINT_BLOCK, NilDescriptor.nil());
		fiber.setSlot(FIBER_GLOBALS, MapDescriptor.empty());
		fiber.setSlot(HERITABLE_FIBER_GLOBALS, MapDescriptor.empty());
		fiber.setSlot(RESULT, NilDescriptor.nil());
		fiber.setSlot(LOADER, RawPojoDescriptor.identityWrap(loader));
		fiber.setSlot(RESULT_CONTINUATION, NilDescriptor.nil());
		fiber.setSlot(FAILURE_CONTINUATION, NilDescriptor.nil());
		fiber.setSlot(JOINING_FIBERS, SetDescriptor.empty());
		fiber.setSlot(JOINEE, NilDescriptor.nil());
		fiber.setSlot(WAKEUP_TASK, NilDescriptor.nil());
		return fiber;
	}

	/**
	 * Answer the {@linkplain FiberDescriptor fiber} currently bound to this
	 * {@link AvailThread}.
	 *
	 * @return A fiber.
	 */
	public static AvailObject current ()
	{
		return ((AvailThread) Thread.currentThread()).interpreter.fiber;
	}
}
