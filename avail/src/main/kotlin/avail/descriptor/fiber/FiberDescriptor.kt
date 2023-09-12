/*
 * FiberDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.fiber

import avail.AvailDebuggerModel
import avail.AvailRuntime
import avail.AvailRuntimeSupport
import avail.AvailTask
import avail.annotations.HideFieldJustForPrinting
import avail.anvil.icons.structure.SideEffectIcons
import avail.compiler.SideEffectKind
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.IS_STYLING
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.RUNNING_LEXER
import avail.descriptor.fiber.A_Fiber.Companion.continuation
import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.fiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.fiberName
import avail.descriptor.fiber.A_Fiber.Companion.generalFlag
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.setInterruptRequestFlag
import avail.descriptor.fiber.FiberDescriptor.ObjectSlots.BREAKPOINT_BLOCK
import avail.descriptor.fiber.FiberDescriptor.ObjectSlots.CONTINUATION
import avail.descriptor.fiber.FiberDescriptor.ObjectSlots.FIBER_GLOBALS
import avail.descriptor.fiber.FiberDescriptor.ObjectSlots.HERITABLE_FIBER_GLOBALS
import avail.descriptor.fiber.FiberDescriptor.ObjectSlots.JOINING_FIBERS
import avail.descriptor.fiber.FiberDescriptor.ObjectSlots.RESULT
import avail.descriptor.fiber.FiberDescriptor.ObjectSlots.RESULT_TYPE
import avail.descriptor.fiber.FiberDescriptor.ObjectSlots.SUSPENDING_FUNCTION
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.mapAt
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.DeclarationPhraseDescriptor
import avail.descriptor.pojos.RawPojoDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.Mutability.IMMUTABLE
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.representation.Mutability.SHARED
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.A_String
import avail.descriptor.types.A_Type
import avail.descriptor.types.FiberTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TypeTag
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.execution.AvailLoader
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.io.TextInterface
import avail.utility.isNullOr
import org.availlang.json.JSONWriter
import java.util.TimerTask
import java.util.WeakHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.ImageIcon

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
	mutability: Mutability,
	val helper: FiberHelper
) : Descriptor(
	mutability,
	TypeTag.FIBER_TAG,
	ObjectSlots::class.java,
	null)
{
	/**
	 * A helper class, one per [fiber][A_Fiber].  It's referenced referenced
	 * through a pojo from a field of the fiber, so that the [FiberDescriptor]
	 * can be easily switched from mutable to immutable or shared, without
	 * cloning the state.
	 *
	 * @constructor
	 *
	 * @property loader
	 *   An [AvailLoader]. This pertains only to load-time fibers, and indicates
	 *   which loader this fiber is running on behalf of.  If loading is not
	 *   currently taking place, this should be `null``.
	 * @property textInterface
	 *   The [TextInterface] used to handle in, out, and error streams for this
	 *   fiber.
	 *
	 * @param nameSupplier
	 *   A zero-argument Kotlin [Function] that produces the name for this
	 *   fiber.  It's computed lazily at most once, using the Kotlin `lazy`
	 *   mechanism.  Note that after it's computed and cached in the [name]
	 *   property, the nameSupplier function is no longer referenced by the lazy
	 *   mechanism.  The function should avoid execution of Avail code as that
	 *   could easily lead to deadlocks.
	 */
	class FiberHelper constructor(
		internal var loader: AvailLoader?,
		internal var textInterface: TextInterface,
		initialPriority: Int,
		nameSupplier: ()->A_String)
	{
		/** The random, permanent hash value of the fiber. */
		val hash: Int = AvailRuntimeSupport.nextHash()

		/**
		 * The fiber's priority, in `[0..255]`.  Higher priority fibers are
		 * serviced more quickly than lower priority fibers.  255 is the highest
		 * priority and 0 is the lowest.
		 */
		@Volatile
		var priority: Int = initialPriority

		/** The fiber's [Flag]s, encoded as an [AtomicInteger]. */
		var flags = AtomicInteger(0)

		/** Retrieve the given flag as a boolean. */
		fun getFlag(flag: FlagGroup): Boolean = flags.get() and flag.mask != 0

		/**
		 * Atomically replace the given flag with the boolean.
		 */
		fun setFlag(flag: FlagGroup, value: Boolean)
		{
			flags.getAndUpdate { old ->
				when
				{
					value -> old or flag.mask
					else -> old and flag.mask.inv()
				}
			}
		}

		/**
		 * Atomically replace the given flag with the boolean, answering the
		 * boolean that previously occupied that flag.
		 */
		fun getAndSetFlag(flag: FlagGroup, value: Boolean): Boolean =
			flags.getAndUpdate { old ->
				when
				{
					value -> old or flag.mask
					else -> old and flag.mask.inv()
				}
			} and flag.mask != 0

		/**
		 * The [ExecutionState] of the fiber, indicating whether the fiber is
		 * e.g., [running][ExecutionState.RUNNING],
		 * [suspended][ExecutionState.SUSPENDED] or
		 * [terminated][ExecutionState.TERMINATED].
		 */
		@Volatile
		var executionState = ExecutionState.UNSTARTED

		/**
		 * The Kotlin [Function] that should be invoked when the fiber completes
		 * successfully, passing the value produced by the outermost frame.
		 */
		internal var resultContinuation: ((AvailObject)->Unit)? =
			{ _: AvailObject -> }

		/**
		 * The Kotlin [Function] that should be invoked when the fiber aborts,
		 * passing the [Throwable] produced by the failure.
		 */
		internal var failureContinuation: ((Throwable)->Unit)? =
			{ _: Throwable -> }

		/**
		 * The [TimerTask] responsible for waking up this sleeping fiber, or
		 * `null` if the fiber is not sleeping.
		 */
		@Volatile
		internal var wakeupTask: TimerTask? = null

		/**
		 * A [WeakHashMap] holding the [variables][A_Variable] that were
		 * encountered during a variable access trace.  The corresponding values
		 * are `true` iff the variable was read before it was written.
		 *
		 * This map is rarely populated, so create it very small.
		 *
		 * @see [TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES].
		 */
		internal val tracedVariables: WeakHashMap<A_Variable, Boolean> =
			WeakHashMap(2)

		/**
		 * The [Lazy]-wrapped nameSupplier provided to the constructor.
		 */
		private var lazyNameSupplier = lazy { nameSupplier().makeShared() }

		/**
		 * The name of this fiber.  It's computed lazily from the nameSupplier
		 * [Function] provided during creation (or updated later).  The
		 * resulting [A_String] *must* be shared, but the constructor and the
		 * updater ensure that.
		 */
		val name: A_String get() = lazyNameSupplier.value

		/**
		 * Replace the [nameSupplier].  This also clears the cached name.
		 */
		fun nameSupplier(nameSupplier: ()->A_String) {
			lazyNameSupplier = lazy { nameSupplier().makeShared() }
		}

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] holding a [StringBuilder] in
		 * which logging should take place for this fiber.  This is a very fast
		 * way of doing logging, since it doesn't have to write to disk or
		 * update a user interface component, and garbage collection of a fiber
		 * which has terminated typically also collects that fiber's log.
		 */
		val debugLog = StringBuilder()

		/**
		 * A 64-bit unique value for this fiber, allocated from a monotonically
		 * increasing counter.  Since it's only used for debugging, it's safe
		 * even if the counter eventually overflows.
		 */
		val debugUniqueId = uniqueDebugCounter.incrementAndGet().toLong()

		/**
		 * A [set][SetDescriptor] of raw [pojos][RawPojoDescriptor], each of
		 * which wraps an action indicating what to do with the fiber's reified
		 * [CONTINUATION] when the fiber next reaches a suitable safe point.
		 *
		 * The non-emptiness of this set must agree with the value of the
		 * [InterruptRequestFlag.REIFICATION_REQUESTED] flag.
		 */
		val reificationWaiters = mutableSetOf<(A_Continuation) -> Unit>()

		/**
		 * An amount to subtract from readings of the current time for the
		 * purpose of measuring elapsed time *excluding* time when the fiber was
		 * suspended.  This metric is far more useful than raw elapsed time for
		 * measuring performance, especially in the presence of many fibers that
		 * take significant time in suspended primitives or preempting each
		 * other.
		 *
		 * Each suspension/resumption pair causes this field to increase.
		 */
		private var clockBiasNanos = 0L

		/**
		 * The last system clock time, in nanoseconds, that this fiber was
		 * suspended, or blocked in any other way.  It must be zero (`0L`) while
		 * the fiber is running.
		 */
		private var suspensionTimeNanos = AvailRuntimeSupport.captureNanos()

		/**
		 * The fiber has just started running, so do what must be done for the
		 * correct accounting of CPU time by the fiber.
		 */
		fun startCountingCPU()
		{
			val now = AvailRuntimeSupport.captureNanos()
			clockBiasNanos += (now - suspensionTimeNanos)
			suspensionTimeNanos = 0L
		}

		/**
		 * The fiber has just stopped running, either due to completion, an
		 * interrupt, or suspension, so do what must be done for the
		 * correct accounting of CPU time by the fiber.
		 */
		fun stopCountingCPU()
		{
			val now = AvailRuntimeSupport.captureNanos()
			assert (suspensionTimeNanos == 0L)
			suspensionTimeNanos = now
		}

		/**
		 * Answer a [Long], representing nanoseconds, which increases
		 * monotonically at the normal rate of time while the fiber is running,
		 * but stops when it is not.
		 */
		fun fiberTime(): Long = when (val suspended = suspensionTimeNanos)
		{
			// The fiber is not suspended.  Report the current clock
			// adjusted to be a fiber time.
			0L -> AvailRuntimeSupport.captureNanos() - clockBiasNanos
			// The fiber is suspended.  Report the time that it was
			// suspended, adjusted to be a fiber time.
			else -> suspended - clockBiasNanos
		}

		/** The [AvailDebuggerModel] that has captured this fiber, if any. */
		val debugger = AtomicReference<AvailDebuggerModel?>(null)

		/**
		 * A function which checks whether the fiber in the given interpreter
		 * should run, based on what has been set up by the debugger.  This
		 * *must* be non-null whenever the fiber is captured by a debugger.
		 */
		@Volatile
		var debuggerRunCondition: ((Interpreter)->Boolean)? = null

		/**
		 * A function which checks whether the given fiber is allowed to perform
		 * a function invocation without checking with the
		 * [debuggerRunCondition].  Note that (1) both regular and non-local
		 * control flow (e.g., exceptions, time slicing, backtracking) will exit
		 * from the JVM stack frame that observed this to be true and invoked a
		 * function, and (2) user-interface debugger control only happens during
		 * safe points, so it's safe to temporarily replace the
		 * [debuggerRunCondition] before the invocation and restore it after the
		 * JVM-level call returns.
		 *
		 * This flag is only tested if the fiber is bound to a debugger.
		 */
		@Volatile
		var debuggerCanInvoke: Boolean = true
	}

	/** The interpretation of the [FiberHelper]'s [flags][FiberHelper.flags]. */
	enum class Flag constructor (val shift: Int) : FlagGroup
	{
		/** See [InterruptRequestFlag.TERMINATION_REQUESTED]. */
		TERMINATION_REQUESTED(0),

		/** See [InterruptRequestFlag.REIFICATION_REQUESTED]. */
		REIFICATION_REQUESTED(1),

		/** See [SynchronizationFlag.BOUND]. */
		BOUND(2),

		/** See [SynchronizationFlag.SCHEDULED]. */
		SCHEDULED(3),

		/** See [SynchronizationFlag.PERMIT_UNAVAILABLE]. */
		PERMIT_UNAVAILABLE(4),

		/** See [TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES]. */
		TRACE_VARIABLE_READS_BEFORE_WRITES(5),

		/** See [TraceFlag.TRACE_VARIABLE_WRITES]. */
		TRACE_VARIABLE_WRITES(6),

		/** See [GeneralFlag.CAN_REJECT_PARSE]. */
		CAN_REJECT_PARSE(7),

		/** See [GeneralFlag.IS_EVALUATING_MACRO]. */
		IS_EVALUATING_MACRO(8),

		/** See [GeneralFlag.IS_SEMANTIC_RESTRICTION]. */
		IS_SEMANTIC_RESTRICTION(9),

		/** See [GeneralFlag.IS_LEXER]. */
		IS_LEXER(10),

		/** See [GeneralFlag.IS_RUNNING_TOP_STATEMENT]. */
		IS_RUNNING_TOP_STATEMENT(11),

		/** See [GeneralFlag.IS_RUNNING_COMMAND]. */
		IS_RUNNING_COMMAND(12);

		/** The [Int] mask corresponding with the [shift]. */
		override val mask = 1 shl shift

		override val flag: Flag get() = this
	}

	/**
	 * A useful interface for enums to have, if they comprise a collection of
	 * enumerations that have a [flag] field of type [Flag].
	 */
	interface FlagGroup
	{
		/** The [Flag] that this enum represents. */
		val flag: Flag

		val mask: Int get() = flag.mask
	}

	/**
	 * The advisory interrupt request flags. The flags declared as enumeration
	 * values within this `enum` are the interrupt request flags.
	 */
	enum class InterruptRequestFlag(override val flag: Flag) : FlagGroup
	{
		/**
		 * Termination of the target fiber has been requested.
		 */
		TERMINATION_REQUESTED(Flag.TERMINATION_REQUESTED),

		/**
		 * Another fiber wants to know what this fiber's reified continuation
		 * is.
		 */
		REIFICATION_REQUESTED(Flag.REIFICATION_REQUESTED);
	}

	/**
	 * The synchronization flags. The flags declared as enumeration values
	 * within this `enum` are for synchronization-related conditions.
	 */
	enum class SynchronizationFlag(override val flag: Flag) : FlagGroup
	{
		/**
		 * The fiber is bound to an [interpreter][Interpreter].
		 */
		BOUND(Flag.BOUND),

		/**
		 * The fiber has been scheduled for resumption.
		 */
		SCHEDULED(Flag.SCHEDULED),

		/**
		 * The parking permit is unavailable.
		 */
		PERMIT_UNAVAILABLE(Flag.PERMIT_UNAVAILABLE);
	}

	/**
	 * The trace flags. The flags declared as enumeration values within this
	 * [Enum] are for system tracing modes.
	 */
	enum class TraceFlag(override val flag: Flag) : FlagGroup
	{
		/**
		 * Should the [interpreter][Interpreter] record which
		 * [variables][VariableDescriptor] are read before written while running
		 * this [fiber][FiberDescriptor]?
		 */
		TRACE_VARIABLE_READS_BEFORE_WRITES(
			Flag.TRACE_VARIABLE_READS_BEFORE_WRITES),

		/**
		 * Should the [interpreter][Interpreter] record which
		 * [variables][VariableDescriptor] are written while running this
		 * [fiber][FiberDescriptor]?
		 */
		TRACE_VARIABLE_WRITES(Flag.TRACE_VARIABLE_WRITES);
	}

	/**
	 * The general flags. These are flags that are not otherwise grouped for
	 * semantic purposes, such as indicating [interrupt][InterruptRequestFlag]
	 * requests or [synchronization][SynchronizationFlag].
	 */
	enum class GeneralFlag(override val flag: Flag) : FlagGroup
	{
		/** Was the fiber started to apply a semantic restriction? */
		CAN_REJECT_PARSE(Flag.CAN_REJECT_PARSE),

		/**
		 * Was the fiber started to evaluate a macro invocation (or a prefix
		 * function for a macro)?
		 */
		IS_EVALUATING_MACRO(Flag.IS_EVALUATING_MACRO),

		/** Was the fiber started to evaluate a semantic restriction? */
		IS_SEMANTIC_RESTRICTION(Flag.IS_SEMANTIC_RESTRICTION),

		/** Was the fiber started to run a lexer (i.e., to produce a token)? */
		IS_LEXER(Flag.IS_LEXER),

		/** Was the fiber started to run a top-level statement of module? */
		IS_RUNNING_TOP_STATEMENT(Flag.IS_RUNNING_TOP_STATEMENT),

		/** Was the fiber started to run a command or entry point? */
		IS_RUNNING_COMMAND(Flag.IS_RUNNING_COMMAND)
	}

	/**
	 * A [FiberKind] is a broad categorization of [A_Fiber], useful, say, for
	 * filtering which fibers are captured by a debugger.
	 */
	enum class FiberKind
	constructor(
		private val flag: GeneralFlag? = null,
		sideEffectKind: SideEffectKind? = null,
		val icon: ImageIcon? =
			sideEffectKind?.let { SideEffectIcons.icon(16, it) })
	{
		/** The fiber is evaluating a macro or macro prefix. */
		MACRO(
			GeneralFlag.IS_EVALUATING_MACRO,
			SideEffectKind.MACRO_DEFINITION_KIND),

		/** The fiber is evaluating a semantic restriction. */
		RESTRICTION(
			GeneralFlag.IS_SEMANTIC_RESTRICTION,
			SideEffectKind.SEMANTIC_RESTRICTION_KIND),

		/** The fiber is evaluating a lexer (attempting to produce a token). */
		LEXER(
			GeneralFlag.IS_LEXER,
			SideEffectKind.LEXER_KIND),

		/** The fiber is evaluating a top-level statement of a module. */
		STATEMENT(GeneralFlag.IS_RUNNING_TOP_STATEMENT),

		/**
		 * The fiber is evaluating a command, for example an invocation of an
		 * entry point.
		 */
		COMMAND(GeneralFlag.IS_RUNNING_COMMAND),

		/**
		 * The fiber is for some other, unknown purpose.  Note that this has to
		 * be the final entry, and it must be the only enum value with null as
		 * its [flag].
		 */
		OTHER(null);

		/**
		 * Answer a very short name for this [FiberKind], preferably only one
		 * or two characters.
		 */
		val veryShortName: String = name.substring(0..0)

		companion object
		{
			/** A pre-extracted [Array] of each [FiberKind]. */
			val all = entries.toTypedArray()

			/** Extract an [A_Fiber]'s [FiberKind]. */
			val A_Fiber.fiberKind: FiberKind
				get() = all.first { kind ->
					kind.flag.isNullOr { this@fiberKind.generalFlag(this) } }
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The current [state][ContinuationDescriptor] of execution of the
		 * fiber.  This is a [continuation][A_Continuation], or [nil] while the
		 * fiber is running or completed.
		 */
		@HideFieldJustForPrinting
		CONTINUATION,

		/**
		 * The [A_Function] that suspended this fiber, or [nil] if it's not
		 * suspended.
		 */
		@HideFieldJustForPrinting
		SUSPENDING_FUNCTION,

		/**
		 * The result type of this [fiber][FiberDescriptor]'s
		 * [type][FiberTypeDescriptor].
		 */
		@HideFieldJustForPrinting
		RESULT_TYPE,

		/**
		 * A map from [atoms][AtomDescriptor] to values. Each fiber has its own
		 * unique such map, which allows processes to record fiber-specific
		 * values. The atom identities ensure modularity and non-interference of
		 * these keys.
		 */
		@HideFieldJustForPrinting
		FIBER_GLOBALS,

		/**
		 * A map from [atoms][AtomDescriptor] to heritable values. When a fiber
		 * forks a new fiber, the new fiber inherits this map. The atom
		 * identities ensure modularity and non-interference of these keys.
		 */
		@HideFieldJustForPrinting
		HERITABLE_FIBER_GLOBALS,

		/**
		 * The result of having running this [fiber][FiberDescriptor] to
		 * completion.  Always [nil] if the fiber has not yet completed.
		 */
		@HideFieldJustForPrinting
		RESULT,

		/**
		 * Not yet implemented. This will be a [function][A_Function] that
		 * should be invoked after the fiber executes each nybblecode. Using
		 * [nil] here means run without this special single-stepping mode
		 * enabled.
		 */
		@HideFieldJustForPrinting
		BREAKPOINT_BLOCK,

		/**
		 * A [set][SetDescriptor] of [fibers][FiberDescriptor] waiting to join
		 * the current fiber.  That is, these are fibers that are waiting for
		 * this fiber to end its execution, in either success or failure.
		 */
		@HideFieldJustForPrinting
		JOINING_FIBERS;
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
		val indicatesTermination: Boolean,
		private val privateSuccessors: ()->Set<ExecutionState>)
	{
		/**
		 * The fiber has not been started.
		 */
		UNSTARTED(true, false, { setOf(RUNNING) }),

		/**
		 * The fiber is running or waiting for another fiber to yield.
		 */
		RUNNING(
			false,
			false,
			{
				setOf(
					SUSPENDED, INTERRUPTED, PARKED, PAUSED, TERMINATED, ABORTED)
			}),

		/**
		 * The fiber has been suspended.
		 */
		SUSPENDED(true, false, { setOf(RUNNING, ABORTED, ASLEEP) }),

		/**
		 * The fiber has been interrupted.
		 */
		INTERRUPTED(true, false, { setOf(RUNNING) }),

		/**
		 * The fiber has been parked.
		 */
		PARKED(true, false, { setOf(SUSPENDED) }),

		/**
		 * The fiber is asleep.
		 */
		ASLEEP(true, false, { setOf(SUSPENDED) }),

		/**
		 * The fiber was [RUNNING], but was paused by an [AvailDebuggerModel].
		 */
		PAUSED(true, false, { setOf(UNPAUSING) }),

		/**
		 * The fiber was [PAUSED], but the attached [AvailDebuggerModel] is
		 * attempting (while holding the fiber's lock) to queue an [AvailTask]
		 * which will make it execute.
		 */
		UNPAUSING(true, false, { setOf(RUNNING) }),

		/**
		 * The fiber has terminated successfully.
		 */
		TERMINATED(false, true, { setOf(ABORTED, RETIRED) }),

		/**
		 * The fiber has aborted (due to an exception).
		 */
		ABORTED(false, true, { setOf(RETIRED) }),

		/**
		 * The fiber has run either its
		 * [result&#32continuation][o_ResultContinuation] or its
		 * [failure&#32;continuation][o_FailureContinuation]. This state is
		 * permanent.
		 */
		RETIRED(false, true, { setOf() });

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
				successors = privateSuccessors().sumOf { 1 shl it.ordinal }
			}
			return successors ushr newState.ordinal and 1 == 1
		}
	}

	// Allow mutable access to all fiber slots.
	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean = true

	/**
	 * {@inheritDoc}
	 *
	 * Show the types of local variables and outer variables.
	 */
	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper>
	{
		val fields = mutableListOf(*super.o_DescribeForDebugger(self))
		fields.add(
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				-1,
				helper,
				slotName = "(HELPER)"))
		return fields.toTypedArray()
	}

	override fun o_FiberHelper(self: AvailObject): FiberHelper = helper

	override fun o_ExecutionState(self: AvailObject): ExecutionState =
		helper.executionState

	override fun o_SetExecutionState(self: AvailObject, value: ExecutionState)
	{
		assert(helper.executionState.mayTransitionTo(value))
		helper.executionState = value
	}

	override fun o_Priority(self: AvailObject): Int = helper.priority

	override fun o_SetPriority(self: AvailObject, value: Int)
	{
		helper.priority = value
	}

	override fun o_UniqueId(self: AvailObject): Long =
		helper.debugUniqueId

	override fun o_InterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	): Boolean = helper.getFlag(flag)

	override fun o_SetInterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	) = helper.setFlag(flag, true)

	override fun o_GetAndClearInterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	) = helper.getAndSetFlag(flag, false)

	override fun o_GetAndSetSynchronizationFlag(
		self: AvailObject,
		flag: SynchronizationFlag,
		value: Boolean
	): Boolean = helper.getAndSetFlag(flag, value)

	override fun o_GeneralFlag(self: AvailObject, flag: GeneralFlag): Boolean =
		helper.getFlag(flag)

	override fun o_SetGeneralFlag(self: AvailObject, flag: GeneralFlag) =
		helper.setFlag(flag, true)

	override fun o_ClearGeneralFlag(self: AvailObject, flag: GeneralFlag) =
		helper.setFlag(flag, false)

	override fun o_TraceFlag(self: AvailObject, flag: TraceFlag): Boolean =
		helper.getFlag(flag)

	override fun o_SetTraceFlag(self: AvailObject, flag: TraceFlag) =
		helper.setFlag(flag, true)

	override fun o_ClearTraceFlag(self: AvailObject, flag: TraceFlag) =
		helper.setFlag(flag, false)

	override fun o_Continuation(self: AvailObject): A_Continuation =
		self.mutableSlot(CONTINUATION)

	/**
	 * Use a special setter mechanism that allows the continuation to be
	 * non-shared, even if the fiber it's to be plugged into is shared.
	 */
	override fun o_SetContinuation(self: AvailObject, value: A_Continuation) =
		self.setContinuationSlotOfFiber(CONTINUATION, value)

	override fun o_FiberName(self: AvailObject): A_String = helper.name

	override fun o_FiberNameSupplier(
		self: AvailObject,
		supplier: () -> A_String
	) = helper.nameSupplier(supplier)

	override fun o_FiberGlobals(self: AvailObject): A_Map =
		self.mutableSlot(FIBER_GLOBALS)

	override fun o_SetFiberGlobals(self: AvailObject, globals: A_Map) =
		self.setMutableSlot(FIBER_GLOBALS, globals.makeImmutable())

	override fun o_FiberResult(self: AvailObject): AvailObject =
		self.mutableSlot(RESULT)

	override fun o_SetFiberResult(self: AvailObject, result: A_BasicObject) =
		self.setMutableSlot(RESULT, result)

	override fun o_HeritableFiberGlobals(self: AvailObject): A_Map =
		self.mutableSlot(HERITABLE_FIBER_GLOBALS)

	override fun o_SetHeritableFiberGlobals(
		self: AvailObject,
		globals: A_Map
	) = self.setMutableSlot(HERITABLE_FIBER_GLOBALS, globals.makeShared())

	override fun o_AvailLoader(self: AvailObject): AvailLoader? = helper.loader

	override fun o_SetAvailLoader(
		self: AvailObject,
		loader: AvailLoader?)
	{
		helper.loader = loader
	}

	override fun o_ResultContinuation(self: AvailObject): (AvailObject)->Unit =
		synchronized(self) {
			helper.run {
				val result = resultContinuation
				assert(result !== null) { "Fiber attempting to succeed twice!" }
				resultContinuation = null
				failureContinuation = null
				result!!
			}
		}

	override fun o_SetSuccessAndFailure(
		self: AvailObject,
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit
	) = synchronized(self) {
		helper.resultContinuation = onSuccess
		helper.failureContinuation = onFailure
	}

	override fun o_FailureContinuation(self: AvailObject): (Throwable) -> Unit =
		synchronized(self) {
			helper.run {
				val result = failureContinuation
				assert(result !== null) { "Fiber attempting to succeed twice!" }
				resultContinuation = null
				failureContinuation = null
				result!!
			}
		}

	override fun o_JoiningFibers(self: AvailObject): A_Set =
		self.mutableSlot(JOINING_FIBERS)

	override fun o_SetJoiningFibers(self: AvailObject, joiners: A_Set) =
		self.setMutableSlot(JOINING_FIBERS, joiners)

	override fun o_WakeupTask(self: AvailObject): TimerTask? = helper.wakeupTask

	override fun o_SetWakeupTask(
		self: AvailObject,
		task: TimerTask?)
	{
		helper.wakeupTask = task
	}

	override fun o_TextInterface(self: AvailObject): TextInterface =
		helper.textInterface

	override fun o_SetTextInterface(
		self: AvailObject,
		textInterface: TextInterface)
	{
		helper.textInterface = textInterface
	}

	override fun o_RecordVariableAccess(
		self: AvailObject,
		variable: A_Variable,
		wasRead: Boolean)
	{
		assert(helper.getFlag(Flag.TRACE_VARIABLE_READS_BEFORE_WRITES)
			xor helper.getFlag(Flag.TRACE_VARIABLE_WRITES))
		val map = helper.tracedVariables
		synchronized(map) {
			if (!map.containsKey(variable))
			{
				map[variable] = wasRead
			}
		}
	}

	override fun o_VariablesReadBeforeWritten(self: AvailObject): A_Set
	{
		assert(!helper.getFlag(Flag.TRACE_VARIABLE_READS_BEFORE_WRITES))
		val map = helper.tracedVariables
		var set = emptySet
		synchronized(map) {
			map.forEach { (key, value) ->
				if (value) {
					set = set.setWithElementCanDestroy(key, true)
				}
			}
			map.clear()
		}
		return set
	}

	override fun o_VariablesWritten(self: AvailObject): A_Set
	{
		assert(!helper.getFlag(Flag.TRACE_VARIABLE_WRITES))
		val map = helper.tracedVariables
		return synchronized(map) {
			val set = setFromCollection(map.keys)
			map.clear()
			set
		}
	}

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean {
		// Compare fibers by address (identity).
		return another.traversed().sameAddressAs(self)
	}

	override fun o_Hash(self: AvailObject): Int = helper.hash

	override fun o_Kind(self: AvailObject): A_Type =
		FiberTypeDescriptor.fiberType(self[RESULT_TYPE])

	override fun o_FiberResultType(self: AvailObject): A_Type =
		self[RESULT_TYPE]

	override fun o_WhenContinuationIsAvailableDo(
		self: AvailObject,
		whenReified: (A_Continuation) -> Unit
	) = self.lock {
		when (self.executionState) {
			ExecutionState.ABORTED,
			ExecutionState.ASLEEP,
			ExecutionState.INTERRUPTED,
			ExecutionState.PARKED,
			ExecutionState.RETIRED,
			ExecutionState.SUSPENDED,
			ExecutionState.PAUSED,
			ExecutionState.UNPAUSING,
			ExecutionState.TERMINATED,
			ExecutionState.UNSTARTED -> {
				whenReified(self.continuation.makeShared())
			}
			ExecutionState.RUNNING -> {
				helper.reificationWaiters.add(whenReified)
				self.setInterruptRequestFlag(
					InterruptRequestFlag.REIFICATION_REQUESTED)
			}
		}
	}

	override fun o_GetAndClearReificationWaiters(
		self: AvailObject
	): List<(A_Continuation)->Unit> =
		synchronized(self) {
			val previous = helper.reificationWaiters.toList()
			helper.reificationWaiters.clear()
			previous
		}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("fiber") }
			at("fiber name") { self.fiberName.writeTo(writer) }
			at("execution state") {
				write(self.executionState.name.lowercase())
			}
			val result = self.mutableSlot(RESULT)
			if (result.notNil)
			{
				at("result") { result.writeSummaryTo(writer) }
			}
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("fiber") }
			at("fiber name") { self.fiberName.writeTo(writer) }
			at("execution state") {
				write(self.executionState.name.lowercase())
			}
		}

	override fun o_SetSuspendingFunction(
		self: AvailObject,
		suspendingFunction: A_Function
	) {
		assert(
			suspendingFunction.isNil
				|| suspendingFunction.code().codePrimitive()!!
					.hasFlag(CanSuspend))
		self[SUSPENDING_FUNCTION] = suspendingFunction
	}

	override fun o_SuspendingFunction(self: AvailObject): A_Function =
		self[SUSPENDING_FUNCTION]

	override fun o_DebugLog(self: AvailObject): StringBuilder = helper.debugLog

	override fun o_CaptureInDebugger(
		self: AvailObject,
		debugger: AvailDebuggerModel)
	{
		assert(debugger.runtime.runtimeLock.isWriteLockedByCurrentThread)
		if (!helper.debugger.compareAndSet(null, debugger))
		{
			// It lost a race, which means another debugger has already
			// captured it.  This is not really a problem.  Exit silently.
			assert(helper.debugger.get() !== debugger)
			return
		}
		helper.debuggerRunCondition = { false }
		debugger.runtime.resumeIfPausedByDebugger(self)
	}

	override fun o_ReleaseFromDebugger(self: AvailObject)
	{
		val runtime = helper.debugger.get()!!.runtime
		assert(runtime.runtimeLock.isWriteLockedByCurrentThread)
		helper.debugger.set(null)
		helper.debuggerRunCondition = null
		runtime.resumeIfPausedByDebugger(self)
	}

	override fun o_CurrentLexer(self: AvailObject): A_Lexer =
		self.heritableFiberGlobals.mapAtOrNull(RUNNING_LEXER.atom) ?: nil

	override fun <T> o_Lock(self: AvailObject, body: ()->T): T =
		when (val interpreter = Interpreter.currentOrNull()) {
			// It's not running an AvailThread, so don't bother detecting
			// multiple nested fiber locks (which would suggest a deadlock
			// hazard)..
			null -> synchronized(self, body)
			else -> interpreter.lockFiberWhile(self) {
				synchronized(self, body)
			}
		}


	@Deprecated(
		"Not supported",
		ReplaceWith("createFiber()"))
	override fun mutable() = unsupported

	@Deprecated(
		"Not supported",
		ReplaceWith("createFiber()"))
	override fun immutable() = FiberDescriptor(IMMUTABLE, helper)

	@Deprecated(
		"Not supported",
		ReplaceWith("createFiber()"))
	override fun shared() = FiberDescriptor(SHARED, helper)

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

		/** The priority for debugger operations. */
		const val debuggerPriority = 80

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
			val fiberGlobals = fiber.fiberGlobals
			val clientData: A_Map =
				fiberGlobals.mapAt(SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom)
			val bindings: A_Map =
				clientData.mapAt(SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom)
			return bindings.mapAtOrNull(name)
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
			val fiberGlobals = fiber.fiberGlobals
			var clientData: A_Map = fiberGlobals.mapAt(clientDataGlobalKey)
			var bindings: A_Map = clientData.mapAt(compilerScopeMapKey)
			val declarationName = declaration.token.string()
			assert(declarationName.isString)
			bindings.mapAtOrNull(declarationName)?.let { return it }
			bindings = bindings.mapAtPuttingCanDestroy(
				declarationName, declaration, true)
			clientData = clientData.mapAtPuttingCanDestroy(
				compilerScopeMapKey, bindings, true)
			fiber.fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
				clientDataGlobalKey, clientData, true)
			return null
		}

		/**
		 * Construct an [unstarted][ExecutionState.UNSTARTED] [fiber][A_Fiber]
		 * with the specified [result&#32;type][A_Type] and initial priority.
		 *
		 * @param resultType
		 *   The expected result type.
		 * @param runtime
		 *   The [AvailRuntime] that will eventually be given the fiber to run.
		 * @param textInterface
		 *   The [TextInterface] for providing console I/O in this fiber.
		 * @param priority
		 *   The initial priority.
		 * @param setup
		 *   A function to run against the fiber before scheduling it.  The
		 *   function execution happens before any debugger hooks run.
		 * @param nameSupplier
		 *   A supplier that produces an Avail [string][A_String] to name this
		 *   fiber on demand.  Please don't run Avail code to do so, since if
		 *   this is evaluated during fiber execution it will cause the current
		 *   [Thread]'s execution to block, potentially starving the execution
		 *   pool.
		 * @return
		 *   The new fiber.
		 */
		fun newFiber(
			resultType: A_Type,
			runtime: AvailRuntime,
			textInterface: TextInterface,
			priority: Int,
			setup: A_Fiber.()->Unit = { },
			nameSupplier: ()->A_String
		): A_Fiber = createFiber(
			resultType,
			runtime,
			null,
			textInterface,
			priority,
			setup,
			nameSupplier)

		/**
		 * Construct an [unstarted][ExecutionState.UNSTARTED] [fiber][A_Fiber]
		 * with the specified [result&#32;type][A_Type] and [AvailLoader]. The
		 * priority is initially set to [loaderPriority].
		 *
		 * @param resultType
		 *   The expected result type.
		 * @param loader
		 *   An [AvailLoader].
		 * @param nameSupplier
		 *   A supplier that produces an Avail [string][A_String] to name this
		 *   fiber on demand.  Please don't run Avail code to do so, since if
		 *   this is evaluated during fiber execution it will cause the current
		 *   [Thread]'s execution to block, potentially starving the execution
		 *   pool.
		 * @return
		 *   The new fiber.
		 */
		fun newLoaderFiber(
			resultType: A_Type,
			loader: AvailLoader,
			nameSupplier: ()->A_String
		): A_Fiber = createFiber(
			resultType,
			loader.runtime,
			loader,
			loader.textInterface,
			loaderPriority,
			{ },
			nameSupplier)

		/**
		 * Construct an [unstarted][ExecutionState.UNSTARTED] [fiber][A_Fiber]
		 * with the specified [AvailLoader], for the purpose of styling tokens
		 * and phrases.  Such a fiber is the only place that styling is allowed.
		 * Fibers launched from this fiber also allow styling, but they should
		 * be joined by this fiber to ensure they are not making changes after
		 * styling is supposed to have completed.  The priority is initially set
		 * to [loaderPriority].
		 *
		 * @param loader
		 *   An [AvailLoader].
		 * @param nameSupplier
		 *   A supplier that produces an Avail [string][A_String] to name this
		 *   fiber on demand.  Please don't run Avail code to do so, since if
		 *   this is evaluated during fiber execution it will cause the current
		 *   [Thread]'s execution to block, potentially starving the execution
		 *   pool.
		 * @return
		 *   The new fiber.
		 */
		fun newStylerFiber(
			loader: AvailLoader,
			nameSupplier: ()->A_String
		): A_Fiber = createFiber(
			TOP.o,
			loader.runtime,
			loader,
			loader.textInterface,
			loaderPriority,
			setup =
			{
				fiberGlobals = fiberGlobals
					.mapAtPuttingCanDestroy(IS_STYLING.atom, trueObject, true)
					.makeShared()
			},
			nameSupplier = nameSupplier)

		/**
		 * Construct an [unstarted][ExecutionState.UNSTARTED] [fiber][A_Fiber]
		 * with the specified result [type][A_Type], name supplier, and
		 * [AvailLoader]. The priority is initially set to [loaderPriority].
		 *
		 * @param resultType
		 *   The expected result type.
		 * @param runtime
		 *   The [AvailRuntime] that will eventually be given the fiber to run.
		 * @param loader
		 *   Either an AvailLoader or `null`.
		 * @param textInterface
		 *   The [TextInterface] for providing console I/O in this fiber.
		 * @param priority
		 *   An [Int] between 0 and 255 that affects how much of the CPU time
		 *   will be allocated to the fiber.
		 * @param setup
		 *   A function to run against the fiber before scheduling it.  The
		 *   function execution happens before any debugger hooks run.
		 * @param nameSupplier
		 *   A supplier that produces an Avail [string][A_String] to name this
		 *   fiber on demand.  Please don't run Avail code to do so, since if
		 *   this is evaluated during fiber execution it will cause the current
		 *   [Thread]'s execution to block, potentially starving the execution
		 *   pool.
		 * @return
		 *   The new fiber.
		 */
		fun createFiber(
			resultType: A_Type,
			runtime: AvailRuntime,
			loader: AvailLoader?,
			textInterface: TextInterface,
			priority: Int,
			setup: A_Fiber.()->Unit = { },
			nameSupplier: ()->A_String
		): A_Fiber
		{
			assert(priority and 255.inv() == 0) { "Priority must be [0..255]" }
			val helper = FiberHelper(
				loader,
				textInterface,
				priority,
				nameSupplier)
			return FiberDescriptor(MUTABLE, helper).create {
				setSlot(RESULT_TYPE, resultType.makeShared())
				setSlot(CONTINUATION, nil)
				setSlot(SUSPENDING_FUNCTION, nil)
				setSlot(BREAKPOINT_BLOCK, nil)
				setSlot(FIBER_GLOBALS, emptyMap)
				setSlot(HERITABLE_FIBER_GLOBALS, emptyMap)
				setSlot(RESULT, nil)
				setSlot(JOINING_FIBERS, emptySet)
				setup()
				runtime.registerFiber(this)
			}
		}

		/**
		 * Answer the [fiber][A_Fiber] currently bound to the current
		 * [Interpreter].
		 *
		 * @return
		 *   A fiber.
		 */
		fun currentFiber(): A_Fiber = Interpreter.current().fiber()
	}
}
