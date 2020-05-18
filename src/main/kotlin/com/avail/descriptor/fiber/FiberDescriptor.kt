/*
 * FiberDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.fiber

import com.avail.AvailRuntime
import com.avail.AvailRuntime.currentRuntime
import com.avail.AvailRuntimeSupport
import com.avail.annotations.AvailMethod
import com.avail.annotations.HideFieldInDebugger
import com.avail.descriptor.Descriptor
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion.PRIORITY
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._BOUND
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._CAN_REJECT_PARSE
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._IS_EVALUATING_MACRO
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._PERMIT_UNAVAILABLE
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._REIFICATION_REQUESTED
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._SCHEDULED
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._TERMINATION_REQUESTED
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._TRACE_VARIABLE_READS_BEFORE_WRITES
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.Companion._TRACE_VARIABLE_WRITES
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.DEBUG_UNIQUE_ID
import com.avail.descriptor.fiber.FiberDescriptor.IntegerSlots.EXECUTION_STATE
import com.avail.descriptor.fiber.FiberDescriptor.ObjectSlots.*
import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor.identityPojo
import com.avail.descriptor.representation.*
import com.avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FiberTypeDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.execution.AvailLoader
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.io.TextInterface
import com.avail.utility.evaluation.Continuation0
import com.avail.utility.evaluation.Continuation1NotNull
import com.avail.utility.json.JSONWriter
import java.util.*
import java.util.Collections.synchronizedMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * An Avail `FiberDescriptor fiber` represents an independently schedulable flow
 * of control. Its simplistic description of its behavior is a continuation
 * which is repeatedly replaced with continuations representing successively
 * more advanced states, thereby effecting execution.
 *
 * Fibers are effectively scheduled via the [AvailRuntime]'s
 * [executor][AvailRuntime.execute], which is a [ThreadPoolExecutor]. A fiber
 * scheduled in this way runs until it acknowledges being interrupted for some
 * reason or it completes its calculation.  If it is interrupted, the [L2Chunk]
 * machinery ensures the fiber first reaches a state representing a consistent
 * level one [continuation][ContinuationDescriptor] before giving up its
 * time-slice.
 *
 * This fiber pooling model allows a huge number of fibers to efficiently and
 * automatically take advantage of the available CPUs and processing cores,
 * leading to a qualitatively different concurrency model than ones which are
 * mapped directly to operating system threads, such as Java, or extreme
 * lightweight models that cannot support simultaneous execution, such as
 * Smalltalk (e.g., VisualWorks). Clearly, the latter does not scale to a modern
 * (2013) computing environment, and the former leaves one at the mercy of the
 * severe limitations and costs imposed by operating systems.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class FiberDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability,
	TypeTag.FIBER_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The advisory interrupt request flags. The flags declared as enumeration
	 * values within this `enum` are the interrupt request flags.
	 *
	 * @constructor
	 *
	 * @property bitField
	 *   The [BitField] that encodes this flag.
	 */
	enum class InterruptRequestFlag(
		@field:Transient val bitField: BitField
	) {
		/**
		 * Termination of the target fiber has been requested.
		 */
		TERMINATION_REQUESTED(_TERMINATION_REQUESTED),

		/**
		 * Another fiber wants to know what this fiber's reified continuation
		 * is.
		 */
		REIFICATION_REQUESTED(_REIFICATION_REQUESTED);

	}

	/**
	 * The synchronization flags. The flags declared as enumeration values
	 * within this `enum` are for synchronization-related conditions.
	 *
	 * @constructor
	 *
	 * @property bitField
	 *   The [BitField] that encodes this flag.
	 */
	enum class SynchronizationFlag(
		@field:Transient val bitField: BitField
	) {
		/**
		 * The fiber is bound to an [interpreter][Interpreter].
		 */
		BOUND(_BOUND),

		/**
		 * The fiber has been scheduled for resumption.
		 */
		SCHEDULED(_SCHEDULED),

		/**
		 * The parking permit is unavailable.
		 */
		PERMIT_UNAVAILABLE(_PERMIT_UNAVAILABLE);
	}

	/**
	 * The trace flags. The flags declared as enumeration values within this
	 * [Enum] are for system tracing modes.
	 *
	 * @constructor
	 *
	 * @property bitField
	 *   The [BitField] that encodes this flag.
	 */
	enum class TraceFlag(
		@field:Transient val bitField: BitField
	) {
		/**
		 * Should the [interpreter][Interpreter] record which
		 * [variables][VariableDescriptor] are read before written while running
		 * this [fiber][FiberDescriptor]?
		 */
		TRACE_VARIABLE_READS_BEFORE_WRITES(_TRACE_VARIABLE_READS_BEFORE_WRITES),

		/**
		 * Should the [interpreter][Interpreter] record which
		 * [variables][VariableDescriptor] are written while running this
		 * [fiber][FiberDescriptor]?
		 */
		TRACE_VARIABLE_WRITES(_TRACE_VARIABLE_WRITES);
	}

	/**
	 * The general flags. These are flags that are not otherwise grouped for
	 * semantic purposes, such as indicating [interrupt][InterruptRequestFlag]
	 * requests or [synchronization][SynchronizationFlag].
	 *
	 * @constructor
	 *
	 * @property bitField
	 *   The [BitField] that encodes this flag.
	 */
	enum class GeneralFlag(
		@field:Transient val bitField: BitField
	) {
		/**
		 * Was the fiber started to apply a semantic restriction?
		 */
		CAN_REJECT_PARSE(_CAN_REJECT_PARSE),

		/**
		 * Was the fiber started to evaluate a macro invocation?
		 */
		IS_EVALUATING_MACRO(_IS_EVALUATING_MACRO);

	}

	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/** The unique id. */
		DEBUG_UNIQUE_ID,

		/** [BitField]s containing the hash, priority, and flags. */
		@HideFieldInDebugger
		FLAGS,

		/**
		 * The [execution&#32;state][ExecutionState] of the fiber, indicating
		 * whether the fiber is [running][ExecutionState.RUNNING],
		 * [suspended][ExecutionState.SUSPENDED] or
		 * [terminated][ExecutionState.TERMINATED].
		 */
		EXECUTION_STATE;

		@Suppress("ObjectPropertyName")
		companion object {
			/**
			 * The hash of this fiber, which is chosen randomly on the first
			 * demand.
			 */
			val HASH_OR_ZERO = BitField(FLAGS, 0, 32)

			/**
			 * The priority of this fiber, where processes with larger values
			 * get at least as much opportunity to run as processes with lower
			 * values.
			 */
			val PRIORITY = BitField(FLAGS, 32, 8)

			/** See [InterruptRequestFlag.TERMINATION_REQUESTED]. */
			val _TERMINATION_REQUESTED = BitField(FLAGS, 40, 1)

			/** See [InterruptRequestFlag.REIFICATION_REQUESTED]. */
			val _REIFICATION_REQUESTED = BitField(FLAGS, 41, 1)

			/** See [SynchronizationFlag.BOUND]. */
			val _BOUND = BitField(FLAGS, 42, 1)

			/** See [SynchronizationFlag.SCHEDULED]. */
			val _SCHEDULED = BitField(FLAGS, 43, 1)

			/** See [SynchronizationFlag.PERMIT_UNAVAILABLE]. */
			val _PERMIT_UNAVAILABLE = BitField(FLAGS, 44, 1)

			/** See [TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES]. */
			val _TRACE_VARIABLE_READS_BEFORE_WRITES = BitField(FLAGS, 45, 1)

			/** See [TraceFlag.TRACE_VARIABLE_WRITES]. */
			val _TRACE_VARIABLE_WRITES = BitField(FLAGS, 46, 1)

			/** See [GeneralFlag.CAN_REJECT_PARSE]. */
			val _CAN_REJECT_PARSE = BitField(FLAGS, 47, 1)

			/** See [GeneralFlag.CAN_REJECT_PARSE]. */
			val _IS_EVALUATING_MACRO = BitField(FLAGS, 48, 1)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The current [state][ContinuationDescriptor] of execution of the
		 * fiber.  This is a [continuation][A_Continuation].
		 */
		CONTINUATION,

		/**
		 * The [A_Function] that suspended this fiber, or [nil] if it's not
		 * suspended.
		 */
		SUSPENDING_FUNCTION,

		/**
		 * The result type of this [fiber][FiberDescriptor]'s
		 * [type][FiberTypeDescriptor].
		 */
		RESULT_TYPE,

		/**
		 * A map from [atoms][AtomDescriptor] to values. Each fiber has its own
		 * unique such map, which allows processes to record fiber-specific
		 * values. The atom identities ensure modularity and non-interference of
		 * these keys.
		 */
		FIBER_GLOBALS,

		/**
		 * A map from [atoms][AtomDescriptor] to heritable values. When a fiber
		 * forks a new fiber, the new fiber inherits this map. The atom
		 * identities ensure modularity and non-interference of these keys.
		 */
		HERITABLE_FIBER_GLOBALS,

		/**
		 * The result of running this [fiber][FiberDescriptor] to completion.
		 */
		RESULT,

		/**
		 * Not yet implemented. This will be a [function][A_Function] that
		 * should be invoked after the fiber executes each nybblecode. Using
		 * [nil] here means run without this special single-stepping mode
		 * enabled.
		 */
		BREAKPOINT_BLOCK,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] wrapping an [AvailLoader]. This
		 * pertains only to load-time fibers, and indicates which loader If
		 * loading is not currently taking place, this should be [nil].
		 */
		LOADER,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] wrapping the
		 * [Continuation1NotNull] that should be called with the
		 * [result][AvailObject] of executing the fiber to its natural
		 * conclusion.
		 */
		RESULT_CONTINUATION,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] wrapping the
		 * [Continuation1NotNull] that should be called with the [Throwable]
		 * responsible for the untimely death of the fiber.
		 */
		FAILURE_CONTINUATION,

		/**
		 * A [set][SetDescriptor] of [fibers][FiberDescriptor] waiting to join
		 * the current fiber.  That is, these are fibers that are waiting for
		 * this fiber to end its execution, in either success or failure.
		 */
		JOINING_FIBERS,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] wrapping the [TimerTask]
		 * responsible for waking up the [sleeping][ExecutionState.ASLEEP]
		 * [fiber][FiberDescriptor].
		 */
		WAKEUP_TASK,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] wrapping a [WeakHashMap] from
		 * [variables][VariableDescriptor] encountered during a variable access
		 * [trace][TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES] to a
		 * [boolean][Boolean] that is `true` iff the variable was read before it
		 * was written.
		 */
		TRACED_VARIABLES,

		/**
		 * A [set][SetDescriptor] of raw [pojos][RawPojoDescriptor], each of
		 * which wraps a [Continuation1NotNull] indicating what to do with the
		 * fiber's reified [CONTINUATION] when the fiber next reaches a suitable
		 * safe point.
		 *
		 * The non-emptiness of this set must agree with the value of the
		 * [InterruptRequestFlag.REIFICATION_REQUESTED] flag.
		 */
		REIFICATION_WAITERS,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] wrapping a [TextInterface].
		 */
		TEXT_INTERFACE,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] holding a [Supplier] of
		 * [A_String]. The supplier should avoid execution of Avail code, as
		 * that could easily lead to deadlocks.
		 */
		NAME_SUPPLIER,

		/**
		 * The name of this fiber.  It's either an Avail [string][A_String] or
		 * `nil`.  If nil, asking for the name should cause the [NAME_SUPPLIER]
		 * to run, and the resulting string to be cached here.
		 */
		NAME_OR_NIL,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] holding a [StringBuilder] in
		 * which logging should take place for this fiber.  This is a very fast
		 * way of doing logging, since it doesn't have to write to disk or
		 * update a user interface component, and garbage collection of a fiber
		 * which has terminated typically also collects that fiber's log.
		 */
		DEBUG_LOG
	}

	/**
	 * These are the possible execution states of a [fiber][FiberDescriptor].
	 *
	 * @constructor
	 *
	 * @param indicatesSuspension
	 *   Whether this state indicates a suspended fiber.
	 * @param indicatesTermination
	 *   Whether this state indicates a terminated fiber.
	 */
	enum class ExecutionState(
		val indicatesSuspension: Boolean,
		val indicatesTermination: Boolean
	) : IntegerEnumSlotDescriptionEnum {
		/**
		 * The fiber has not been started.
		 */
		UNSTARTED(true, false) {
			override fun privateSuccessors(): Set<ExecutionState> {
				return EnumSet.of(RUNNING)
			}
		},

		/**
		 * The fiber is running or waiting for another fiber to yield.
		 */
		RUNNING(false, false) {
			override fun privateSuccessors(): Set<ExecutionState> {
				return EnumSet.of(
					SUSPENDED, INTERRUPTED, PARKED, TERMINATED, ABORTED)
			}
		},

		/**
		 * The fiber has been suspended.
		 */
		SUSPENDED(true, false) {
			override fun privateSuccessors(): Set<ExecutionState> {
				return EnumSet.of(RUNNING, ABORTED, ASLEEP)
			}
		},

		/**
		 * The fiber has been interrupted.
		 */
		INTERRUPTED(true, false) {
			override fun privateSuccessors(): Set<ExecutionState> {
				return EnumSet.of(RUNNING)
			}
		},

		/**
		 * The fiber has been parked.
		 */
		PARKED(true, false) {
			override fun privateSuccessors(): Set<ExecutionState> {
				return EnumSet.of(SUSPENDED)
			}
		},

		/**
		 * The fiber is asleep.
		 */
		ASLEEP(true, false) {
			override fun privateSuccessors(): Set<ExecutionState> {
				return EnumSet.of(SUSPENDED)
			}
		},

		/**
		 * The fiber has terminated successfully.
		 */
		TERMINATED(false, true) {
			override fun privateSuccessors(): Set<ExecutionState> {
				return EnumSet.of(ABORTED, RETIRED)
			}
		},

		/**
		 * The fiber has aborted (due to an exception).
		 */
		ABORTED(false, true) {
			override fun privateSuccessors(): Set<ExecutionState> {
				return EnumSet.of(RETIRED)
			}
		},

		/**
		 * The fiber has run either its
		 * [result&#32continuation][RESULT_CONTINUATION] or its
		 * [failure&#32;continuation][FAILURE_CONTINUATION]. This state is
		 * permanent.
		 */
		RETIRED(false, true);

		override fun fieldName(): String = name

		override fun fieldOrdinal(): Int = ordinal

		/**
		 * The valid successor [states][ExecutionState], encoded as a 1-bit for
		 * each valid successor's 1&nbsp;<<&nbsp;ordinal.  Supports at most 31
		 * values, since -1 is used as a lazy-initialization sentinel.
		 */
		protected var successors = -1

		/**
		 * Determine if this is a valid successor state.
		 *
		 * @param newState
		 *   The proposed successor state.
		 * @return
		 *   Whether the transition is permitted.
		 */
		fun mayTransitionTo(newState: ExecutionState): Boolean {
			if (successors == -1) {
				// No lock - redundant computation in other threads is stable.
				var s = 0
				for (successor in privateSuccessors()) {
					s = s or (1 shl successor.ordinal)
				}
				successors = s
			}
			return successors ushr newState.ordinal and 1 == 1
		}

		/**
		 * Answer my legal successor execution states.  None by default.
		 *
		 * @return
		 *   A [Set] of execution states.
		 */
		protected open fun privateSuccessors(): Set<ExecutionState> {
			return kotlin.collections.emptySet()
		}

		/**
		 * Does this execution state indicate that a [fiber][A_Fiber] is
		 * suspended for some reason?
		 *
		 * @return
		 *   `true` if the execution state represents suspension, `false`
		 *   otherwise.
		 */
		fun indicatesSuspension(): Boolean = indicatesSuspension

		/**
		 * Does this execution state indicate that a [fiber][A_Fiber] has
		 * terminated for some reason?
		 *
		 * @return
		 *   `true` if the execution state represents termination, `false`
		 *   otherwise.
		 */
		fun indicatesTermination(): Boolean = indicatesTermination

		companion object {
			/** An array of all [ExecutionState] enumeration values. */
			private val all = values()

			/**
			 * Answer the `ExecutionState` enum value having the given ordinal.
			 *
			 * @param ordinal
			 *   The ordinal to look up.
			 * @return
			 *   The indicated `ExecutionState`.
			 */
			fun lookup(ordinal: Int): ExecutionState = all[ordinal]
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean {
		// Allow mutable access to all fiber slots.
		return true
	}

	@AvailMethod
	override fun o_ExecutionState(self: AvailObject): ExecutionState =
		ExecutionState.lookup(self.mutableSlot(EXECUTION_STATE).toInt())

	@AvailMethod
	override fun o_ExecutionState(self: AvailObject, value: ExecutionState) =
		synchronized(self) {
			val index = self.mutableSlot(EXECUTION_STATE).toInt()
			val current = ExecutionState.lookup(index)
			assert(current.mayTransitionTo(value))
			self.setSlot(EXECUTION_STATE, value.ordinal.toLong())
		}

	@AvailMethod
	override fun o_Priority(self: AvailObject): Int = self.mutableSlot(PRIORITY)

	@AvailMethod
	override fun o_Priority(self: AvailObject, value: Int) =
		self.setMutableSlot(PRIORITY, value)

	@AvailMethod
	override fun o_UniqueId(self: AvailObject): Long =
		self.slot(DEBUG_UNIQUE_ID)

	@AvailMethod
	override fun o_InterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	): Boolean = synchronized(self) { self.slot(flag.bitField) == 1 }

	@AvailMethod
	override fun o_SetInterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	) = synchronized(self) { self.setSlot(flag.bitField, 1) }

	@AvailMethod
	override fun o_GetAndClearInterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	): Boolean = synchronized(self) {
		val value = self.slot(flag.bitField)
		self.setSlot(flag.bitField, 0)
		value == 1
	}

	@AvailMethod
	override fun o_GetAndSetSynchronizationFlag(
		self: AvailObject,
		flag: SynchronizationFlag,
		value: Boolean
	): Boolean {
		var oldValue: Int
		val newBit = if (value) 1 else 0
		synchronized(self) {
			oldValue = self.slot(flag.bitField)
			self.setSlot(flag.bitField, newBit)
		}
		return oldValue == 1
	}

	@AvailMethod
	override fun o_GeneralFlag(self: AvailObject, flag: GeneralFlag): Boolean {
		val value: Int = synchronized(self) { self.slot(flag.bitField) }
		return value == 1
	}

	@AvailMethod
	override fun o_SetGeneralFlag(
		self: AvailObject,
		flag: GeneralFlag
	) = synchronized(self) { self.setSlot(flag.bitField, 1) }

	@AvailMethod
	override fun o_ClearGeneralFlag(
		self: AvailObject,
		flag: GeneralFlag
	) = synchronized(self) { self.setSlot(flag.bitField, 0) }

	@AvailMethod
	override fun o_TraceFlag(self: AvailObject, flag: TraceFlag): Boolean =
		synchronized(self) { self.slot(flag.bitField) == 1 }

	@AvailMethod
	override fun o_SetTraceFlag(
		self: AvailObject,
		flag: TraceFlag
	) = synchronized(self) { self.setSlot(flag.bitField, 1) }

	@AvailMethod
	override fun o_ClearTraceFlag(
		self: AvailObject,
		flag: TraceFlag
	) = synchronized(self) { self.setSlot(flag.bitField, 0) }

	@AvailMethod
	override fun o_Continuation(self: AvailObject): A_Continuation =
		self.mutableSlot(CONTINUATION)

	/**
	 * Use a special setter mechanism that allows the continuation to be
	 * non-shared, even if the fiber it's to be plugged into is shared.
	 */
	@AvailMethod
	override fun o_Continuation(self: AvailObject, value: A_Continuation) =
		self.setContinuationSlotOfFiber(CONTINUATION, value)

	@AvailMethod
	override fun o_FiberName(self: AvailObject): A_String {
		var name: A_String = self.slot(NAME_OR_NIL)
		if (name.equalsNil()) {
			// Compute it from the generator.
			val pojo = self.mutableSlot(NAME_SUPPLIER)
			val supplier = pojo.javaObjectNotNull<Supplier<A_String>>()
			name = supplier.get()
			// Save it for next time.
			self.setMutableSlot(NAME_OR_NIL, name)
		}
		return name
	}

	@AvailMethod
	override fun o_FiberNameSupplier(
		self: AvailObject,
		supplier: Supplier<A_String>
	) {
		self.setMutableSlot(NAME_SUPPLIER, identityPojo(supplier))
		// And clear the cached name.
		self.setMutableSlot(NAME_OR_NIL, nil)
	}

	@AvailMethod
	override fun o_FiberGlobals(self: AvailObject): AvailObject =
		self.mutableSlot(FIBER_GLOBALS)

	@AvailMethod
	override fun o_FiberGlobals(self: AvailObject, globals: A_Map) =
		self.setMutableSlot(FIBER_GLOBALS, globals)

	@AvailMethod
	override fun o_FiberResult(self: AvailObject): AvailObject =
		self.mutableSlot(RESULT)

	@AvailMethod
	override fun o_FiberResult(self: AvailObject, result: A_BasicObject) =
		self.setMutableSlot(RESULT, result)

	@AvailMethod
	override fun o_HeritableFiberGlobals(self: AvailObject): A_Map =
		self.mutableSlot(HERITABLE_FIBER_GLOBALS)

	@AvailMethod
	override fun o_HeritableFiberGlobals(
		self: AvailObject,
		globals: A_Map
	) = self.setMutableSlot(HERITABLE_FIBER_GLOBALS, globals)

	@AvailMethod
	override fun o_BreakpointBlock(self: AvailObject): AvailObject =
		self.mutableSlot(BREAKPOINT_BLOCK)

	@AvailMethod
	override fun o_BreakpointBlock(self: AvailObject, value: AvailObject) =
		self.setMutableSlot(BREAKPOINT_BLOCK, value)

	@AvailMethod
	override fun o_AvailLoader(self: AvailObject): AvailLoader? {
		val pojo = self.mutableSlot(LOADER)
		return if (!pojo.equalsNil()) {
			pojo.javaObject<AvailLoader>()
		} else null
	}

	@AvailMethod
	override fun o_AvailLoader(
		self: AvailObject,
		loader: AvailLoader?
	) = self.setMutableSlot(
		LOADER,
		if (loader !== null) identityPojo(loader) else nil)

	@AvailMethod
	override fun o_ResultContinuation(
		self: AvailObject
	): Continuation1NotNull<AvailObject> {
		var pojo: AvailObject
		synchronized(self) {
			pojo = self.slot(RESULT_CONTINUATION)
			assert(!pojo.equalsNil()) { "Fiber attempting to succeed twice!" }
			self.setSlot(RESULT_CONTINUATION, nil)
			self.setSlot(FAILURE_CONTINUATION, nil)
		}
		return pojo.javaObjectNotNull()
	}

	@AvailMethod
	override fun o_SetSuccessAndFailureContinuations(
		self: AvailObject,
		onSuccess: Continuation1NotNull<AvailObject>,
		onFailure: Continuation1NotNull<Throwable>
	) = synchronized(self) {
		val oldSuccess = self.slot(RESULT_CONTINUATION)
		assert(oldSuccess === defaultResultContinuation)
		self.setSlot(RESULT_CONTINUATION, identityPojo(onSuccess))
		val oldFailure = self.slot(FAILURE_CONTINUATION)
		assert(oldFailure === defaultFailureContinuation)
		self.setSlot(FAILURE_CONTINUATION, identityPojo(onFailure))
	}

	@AvailMethod
	override fun o_FailureContinuation(
		self: AvailObject
	): Continuation1NotNull<Throwable> {
		var pojo: AvailObject
		synchronized(self) {
			pojo = self.slot(FAILURE_CONTINUATION)
			assert(!pojo.equalsNil())
			self.setSlot(FAILURE_CONTINUATION, nil)
			self.setSlot(RESULT_CONTINUATION, nil)
		}
		return pojo.javaObjectNotNull()
	}

	@AvailMethod
	override fun o_JoiningFibers(self: AvailObject): A_Set =
		self.mutableSlot(JOINING_FIBERS)

	@AvailMethod
	override fun o_JoiningFibers(self: AvailObject, joiners: A_Set) =
		self.setMutableSlot(JOINING_FIBERS, joiners)

	@AvailMethod
	override fun o_WakeupTask(self: AvailObject): TimerTask? {
		val pojo = self.mutableSlot(WAKEUP_TASK)
		return if (!pojo.equalsNil()) {
			pojo.javaObject<TimerTask>()
		} else null
	}

	@AvailMethod
	override fun o_WakeupTask(
		self: AvailObject,
		task: TimerTask?
	) = self.setMutableSlot(
		WAKEUP_TASK,
		if (task == null) nil else identityPojo(task))

	override fun o_TextInterface(self: AvailObject): TextInterface =
		self.mutableSlot(TEXT_INTERFACE).javaObjectNotNull()

	override fun o_TextInterface(
		self: AvailObject,
		textInterface: TextInterface
	) = self.setMutableSlot(TEXT_INTERFACE, identityPojo(textInterface))

	@AvailMethod
	override fun o_RecordVariableAccess(
		self: AvailObject,
		variable: A_Variable,
		wasRead: Boolean
	) {
		assert((self.mutableSlot(_TRACE_VARIABLE_READS_BEFORE_WRITES) == 1)
			xor (self.mutableSlot(_TRACE_VARIABLE_WRITES) == 1))
		val rawPojo = self.slot(TRACED_VARIABLES)
		val map = rawPojo.javaObjectNotNull<MutableMap<A_Variable, Boolean>>()
		if (!map.containsKey(variable)) {
			map[variable] = wasRead
		}
	}

	@AvailMethod
	override fun o_VariablesReadBeforeWritten(self: AvailObject): A_Set {
		assert(self.mutableSlot(_TRACE_VARIABLE_READS_BEFORE_WRITES) != 1)
		val rawPojo = self.slot(TRACED_VARIABLES)
		val map = rawPojo.javaObjectNotNull<MutableMap<A_Variable, Boolean>>()
		var set = emptySet()
		map.forEach { (key, value) ->
			if (value) {
				set = set.setWithElementCanDestroy(key, true)
			}
		}
		map.clear()
		return set
	}

	@AvailMethod
	override fun o_VariablesWritten(self: AvailObject): A_Set {
		assert(self.mutableSlot(_TRACE_VARIABLE_WRITES) != 1)
		val rawPojo = self.slot(TRACED_VARIABLES)
		val map = rawPojo.javaObjectNotNull<MutableMap<A_Variable, Boolean>>()
		val set = setFromCollection(map.keys)
		map.clear()
		return set
	}

	@AvailMethod
	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean {
		// Compare fibers by address (identity).
		return another.traversed().sameAddressAs(self)
	}

	@AvailMethod
	override fun o_Hash(self: AvailObject): Int =
		self.synchronizeIf(isShared) { hash(self) }

	@AvailMethod
	override fun o_Kind(self: AvailObject): A_Type =
		FiberTypeDescriptor.fiberType(self.slot(RESULT_TYPE))

	@AvailMethod
	override fun o_FiberResultType(self: AvailObject): A_Type =
		self.slot(RESULT_TYPE)

	override fun o_WhenContinuationIsAvailableDo(
		self: AvailObject,
		whenReified: Continuation1NotNull<A_Continuation>
	) = self.lock(Continuation0 {
		@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
		when (self.executionState()) {
			ExecutionState.ABORTED,
			ExecutionState.ASLEEP,
			ExecutionState.INTERRUPTED,
			ExecutionState.PARKED,
			ExecutionState.RETIRED,
			ExecutionState.SUSPENDED,
			ExecutionState.TERMINATED,
			ExecutionState.UNSTARTED -> {
				whenReified.value(self.continuation().makeShared())
			}
			ExecutionState.RUNNING -> {
				val pojo: A_BasicObject = identityPojo(whenReified)
				val oldSet: A_Set = self.slot(REIFICATION_WAITERS)
				val newSet = oldSet.setWithElementCanDestroy(pojo, true)
				self.setSlot(REIFICATION_WAITERS, newSet.makeShared())
				self.setInterruptRequestFlag(
					InterruptRequestFlag.REIFICATION_REQUESTED)
			}
		}
	})

	override fun o_GetAndClearReificationWaiters(self: AvailObject): A_Set =
		synchronized(self) {
			val previousSet = self.slot(REIFICATION_WAITERS)
			self.setSlot(REIFICATION_WAITERS, emptySet())
			previousSet
		}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("fiber")
		writer.write("fiber name")
		self.fiberName().writeTo(writer)
		writer.write("execution state")
		writer.write(self.executionState().name.toLowerCase())
		val result = self.mutableSlot(RESULT)
		if (!result.equalsNil()) {
			writer.write("result")
			result.writeSummaryTo(writer)
		}
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("fiber")
		writer.write("fiber name")
		self.fiberName().writeTo(writer)
		writer.write("execution state")
		writer.write(self.executionState().name.toLowerCase())
		writer.endObject()
	}

	override fun o_SuspendingFunction(
		self: AvailObject,
		suspendingFunction: A_Function
	) {
		assert(suspendingFunction.equalsNil()
			|| suspendingFunction.code().primitive()!!.hasFlag(CanSuspend))
		self.setSlot(SUSPENDING_FUNCTION, suspendingFunction)
	}

	override fun o_SuspendingFunction(self: AvailObject): A_Function =
		self.slot(SUSPENDING_FUNCTION)

	override fun o_DebugLog(self: AvailObject): StringBuilder =
		self.mutableSlot(DEBUG_LOG).javaObjectNotNull()

	override fun o_Lock(self: AvailObject, critical: Continuation0) {
		when (val interpreter = Interpreter.currentOrNull()) {
			null -> {
				// It's not running an AvailThread, so don't bother detecting
				// multiple nested fiber locks (which would suggest a deadlock
				// hazard)..
				synchronized(self) { critical.value() }
			}
			else -> {
				interpreter.lockFiberWhile(
					self,
					Supplier {
						synchronized(self) { critical.value() }
					})
			}
		}
	}

	override fun <T> o_Lock(self: AvailObject, supplier: Supplier<T>): T =
		when (val interpreter = Interpreter.currentOrNull()) {
			null -> {
				// It's not running an AvailThread, so don't bother detecting
				// multiple nested fiber locks (which would suggest a deadlock
				// hazard)..
				synchronized(self) { supplier.get() }
			}
			else -> {
				interpreter.lockFiberWhile(
					self,
					Supplier<T> { synchronized(self) { supplier.get() } })
			}
		}

	override fun mutable(): FiberDescriptor = mutable

	override fun immutable(): FiberDescriptor = immutable

	override fun shared(): FiberDescriptor = shared

	companion object {
		/** A simple counter for identifying fibers by creation order. */
		private val uniqueDebugCounter = AtomicInteger(0)

		/** The priority of module tracing tasks. */
		const val tracerPriority = 50

		/** The priority of compilation tasks. */
		const val compilerPriority = 50

		/** The priority of loading tasks. */
		const val loaderPriority = 50

		/** The priority of stringifying objects. */
		const val stringificationPriority = 50

		/** The priority of command execution tasks. */
		const val commandPriority = 50

		/** The priority for invalidating expired L2 chunks in bulk. */
		const val bulkL2InvalidationPriority = 90

		/**
		 * The default result continuation, answered when a [fiber][A_Fiber]'s
		 * result continuation is [nil].
		 */
		private val defaultResultContinuation: A_BasicObject = identityPojo(
			Continuation1NotNull { _: AvailObject -> })

		/**
		 * The default result continuation, answered when a
		 * [fiber][FiberDescriptor]'s result continuation is [nil].
		 */
		private val defaultFailureContinuation: A_BasicObject =
			identityPojo(Continuation1NotNull { _: Throwable -> })

		/**
		 * Lazily compute and install the hash of the specified
		 * [fiber][FiberDescriptor].  This should be protected by a synchronized
		 * section if there's a chance this fiber might be hashed by some other
		 * fiber.  If the fiber is not shared, this shouldn't be a problem.
		 *
		 * @param self
		 *   The fiber.
		 * @return
		 *   The fiber's hash value.
		 */
		private fun hash(self: AvailObject): Int {
			var hash = self.slot(HASH_OR_ZERO)
			if (hash == 0) {
				synchronized(self) {
					hash = self.slot(HASH_OR_ZERO)
					if (hash == 0) {
						hash = AvailRuntimeSupport.nextNonzeroHash()
						self.setSlot(HASH_OR_ZERO, hash)
					}
				}
			}
			return hash
		}

		/**
		 * Look up the [declaration][DeclarationPhraseDescriptor] with the given
		 * name in the current compiler scope.  This information is associated
		 * with the current [Interpreter], and therefore the [fiber][A_Fiber]
		 * that it is executing.  If no such binding exists, answer `null`.  The
		 * module scope is not consulted by this mechanism.
		 *
		 * @param name
		 *   The name of the binding to look up in the current scope.
		 * @return
		 *   The [declaration][DeclarationPhraseDescriptor] that was requested,
		 *   or `null` if there is no binding in scope with that name.
		 */
		fun lookupBindingOrNull(
			name: A_String
		): A_Phrase? {
			val fiber = currentFiber()
			val fiberGlobals = fiber.fiberGlobals()
			val clientData: A_Map =
				fiberGlobals.mapAt(SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom)
			val bindings: A_Map =
				clientData.mapAt(SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom)
			return if (bindings.hasKey(name)) {
				bindings.mapAt(name)
			} else null
		}

		/**
		 * Attempt to add the declaration to the compiler scope information
		 * within the client data stored in the current fiber.  If there is
		 * already a declaration by that name, return it; otherwise return
		 * `null`.
		 *
		 * @param declaration
		 *   A [declaration][DeclarationPhraseDescriptor].
		 * @return
		 *   `null` if successful, otherwise the existing
		 *   [declaration][DeclarationPhraseDescriptor] that was in conflict.
		 */
		fun addDeclaration(
			declaration: A_Phrase
		): A_Phrase? {
			val clientDataGlobalKey = SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom
			val compilerScopeMapKey = SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom
			val fiber = currentFiber()
			var fiberGlobals = fiber.fiberGlobals()
			var clientData: A_Map = fiberGlobals.mapAt(clientDataGlobalKey)
			var bindings: A_Map = clientData.mapAt(compilerScopeMapKey)
			val declarationName = declaration.token().string()
			assert(declarationName.isString)
			if (bindings.hasKey(declarationName)) {
				return bindings.mapAt(declarationName)
			}
			bindings = bindings.mapAtPuttingCanDestroy(
				declarationName, declaration, true)
			clientData = clientData.mapAtPuttingCanDestroy(
				compilerScopeMapKey, bindings, true)
			fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
				clientDataGlobalKey, clientData, true)
			fiber.fiberGlobals(fiberGlobals.makeShared())
			return null
		}

		/** The mutable [FiberDescriptor]. */
		val mutable = FiberDescriptor(Mutability.MUTABLE)

		/** The immutable [FiberDescriptor]. */
		private val immutable = FiberDescriptor(Mutability.IMMUTABLE)

		/** The shared [FiberDescriptor]. */
		private val shared = FiberDescriptor(Mutability.SHARED)

		/**
		 * Construct an [unstarted][ExecutionState.UNSTARTED] [fiber][A_Fiber]
		 * with the specified [result&#32;type][A_Type] and initial priority.
		 *
		 * @param resultType
		 *   The expected result type.
		 * @param priority
		 *   The initial priority.
		 * @param nameSupplier
		 *   A [Supplier] that produces an Avail [string][A_String] to name this
		 *   fiber on demand.  Please don't run Avail code to do so, since if
		 *   this is evaluated during fiber execution it will cause the current
		 *   [Thread]'s execution to block, potentially starving the execution
		 *   pool
		 * @return
		 *   The new fiber.
		 */
		fun newFiber(
			resultType: A_Type,
			priority: Int,
			nameSupplier: ()->A_String
		): A_Fiber = createFiber(
			resultType, priority, nil, nameSupplier, currentRuntime())

		/**
		 * Construct an [unstarted][ExecutionState.UNSTARTED] [fiber][A_Fiber]
		 * with the specified [result&#32;type][A_Type] and [AvailLoader]. The
		 * priority is initially set to [loaderPriority].
		 *
		 * @param resultType
		 *   The expected result type.
		 * @param loader
		 *   An Avail loader.
		 * @param nameSupplier
		 *   A [Supplier] that produces an Avail [string][A_String] to name this
		 *   fiber on demand.  Please don't run Avail code to do so, since if
		 *   this is evaluated during fiber execution it will cause the current
		 *   [Thread]'s execution to block, potentially starving the execution
		 *   pool.
		 * @return
		 *   The new fiber.
		 */
		fun newLoaderFiber(
			resultType: A_Type,
			loader: AvailLoader?,
			nameSupplier: ()->A_String
		): A_Fiber = createFiber(
			resultType,
			loaderPriority,
			identityPojo(loader),
			nameSupplier,
			currentRuntime())

		/**
		 * Construct an [unstarted][ExecutionState.UNSTARTED] [fiber][A_Fiber]
		 * with the specified result [type][A_Type], name [Supplier], and
		 * [AvailLoader]. The priority is initially set to [loaderPriority].
		 *
		 * @param resultType
		 *   The expected result type.
		 * @param priority
		 *   An [Int] between 0 and 255 that affects how much of the CPU time
		 *   will be allocated to the fiber.
		 * @param loaderPojoOrNil
		 *   Either a pojo holding an AvailLoader or [nil].
		 * @param nameSupplier
		 *   A [Supplier] that produces an Avail [string][A_String] to name this
		 *   fiber on demand.  Please don't run Avail code to do so, since if
		 *   this is evaluated during fiber execution it will cause the current
		 *   [Thread]'s execution to block, potentially starving the execution
		 *   pool.
		 * @param runtime
		 *   The [AvailRuntime] that will eventually be given the fiber to run.
		 * @return
		 *   The new fiber.
		 */
		@JvmStatic
		fun createFiber(
			resultType: A_Type,
			priority: Int,
			loaderPojoOrNil: AvailObject?,
			nameSupplier: ()->A_String,
			runtime: AvailRuntime
		): A_Fiber {
			assert(priority and 255.inv() == 0) { "Priority must be [0..255]" }
			val fiber = mutable.create()
			fiber.setSlot(RESULT_TYPE, resultType.makeImmutable())
			fiber.setSlot(NAME_SUPPLIER, identityPojo(nameSupplier))
			fiber.setSlot(NAME_OR_NIL, nil)
			fiber.setSlot(PRIORITY, priority)
			fiber.setSlot(CONTINUATION, nil)
			fiber.setSlot(SUSPENDING_FUNCTION, nil)
			fiber.setSlot(
				EXECUTION_STATE, ExecutionState.UNSTARTED.ordinal.toLong())
			fiber.setSlot(BREAKPOINT_BLOCK, nil)
			fiber.setSlot(FIBER_GLOBALS, emptyMap())
			fiber.setSlot(HERITABLE_FIBER_GLOBALS, emptyMap())
			fiber.setSlot(RESULT, nil)
			fiber.setSlot(LOADER, loaderPojoOrNil!!)
			fiber.setSlot(RESULT_CONTINUATION, defaultResultContinuation)
			fiber.setSlot(FAILURE_CONTINUATION, defaultFailureContinuation)
			fiber.setSlot(JOINING_FIBERS, emptySet())
			fiber.setSlot(WAKEUP_TASK, nil)
			fiber.setSlot(
				TRACED_VARIABLES,
				identityPojo(
					synchronizedMap(WeakHashMap<A_Variable, Boolean>())))
			fiber.setSlot(REIFICATION_WAITERS, emptySet())
			fiber.setSlot(TEXT_INTERFACE, runtime.textInterfacePojo())
			fiber.setSlot(
				DEBUG_UNIQUE_ID, uniqueDebugCounter.incrementAndGet().toLong())
			fiber.setSlot(DEBUG_LOG, identityPojo(StringBuilder()))
			runtime.registerFiber(fiber)
			return fiber
		}

		/**
		 * Answer the [fiber][A_Fiber] currently bound to the current
		 * [Interpreter].
		 *
		 * @return
		 *   A fiber.
		 */
		fun currentFiber(): A_Fiber = Interpreter.current().fiber()

		/**
		 * Set the success and failure actions of the fiber.  The former runs if
		 * the fiber succeeds, passing the resulting [AvailObject], and also
		 * stashing it in the fiber.  The latter runs if the fiber fails,
		 * passing the [Throwable] that caused the failure.
		 *
		 * TODO - Migrate setSuccessAndFailureContinuations to use Kotlin
		 * functions.  This temporarily adapts each Kotlin function to a
		 * Continuation1NotNull.
		 *
		 * @param onSuccess
		 *   The action to invoke with the fiber's result value.
		 * @param onFailure
		 *   The action to invoke with the responsible throwable.
		 */
		fun A_Fiber.setSuccessAndFailure(
			onSuccess: (AvailObject)->Unit,
			onFailure: (Throwable)->Unit
		) = setSuccessAndFailureContinuations(
			Continuation1NotNull(onSuccess),
			Continuation1NotNull(onFailure))
	}
}