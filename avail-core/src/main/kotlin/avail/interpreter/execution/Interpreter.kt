/*
 * Interpreter.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package avail.interpreter.execution

import avail.AvailDebuggerModel
import avail.AvailRuntime
import avail.AvailRuntime.HookType
import avail.AvailRuntimeConfiguration.maxInterpreters
import avail.AvailRuntimeSupport
import avail.AvailTask
import avail.AvailThread
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.clearTraceFlag
import avail.descriptor.fiber.A_Fiber.Companion.continuation
import avail.descriptor.fiber.A_Fiber.Companion.debugLog
import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.fiberName
import avail.descriptor.fiber.A_Fiber.Companion.fiberResult
import avail.descriptor.fiber.A_Fiber.Companion.getAndClearInterruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.getAndClearReificationWaiters
import avail.descriptor.fiber.A_Fiber.Companion.getAndSetSynchronizationFlag
import avail.descriptor.fiber.A_Fiber.Companion.interruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.joiningFibers
import avail.descriptor.fiber.A_Fiber.Companion.priority
import avail.descriptor.fiber.A_Fiber.Companion.setTraceFlag
import avail.descriptor.fiber.A_Fiber.Companion.suspendingFunction
import avail.descriptor.fiber.A_Fiber.Companion.traceFlag
import avail.descriptor.fiber.A_Fiber.Companion.uniqueId
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.ABORTED
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.INTERRUPTED
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.PARKED
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.RUNNING
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.SUSPENDED
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.TERMINATED
import avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag.REIFICATION_REQUESTED
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag.BOUND
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag.PERMIT_UNAVAILABLE
import avail.descriptor.fiber.FiberDescriptor.TraceFlag
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.functions.A_Continuation.Companion.function
import avail.descriptor.functions.A_Continuation.Companion.levelTwoChunk
import avail.descriptor.functions.A_Continuation.Companion.levelTwoOffset
import avail.descriptor.functions.A_Continuation.Companion.pc
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.startingChunk
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationWithFrame
import avail.descriptor.functions.ContinuationRegisterDumpDescriptor.Companion.createRegisterDump
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.Companion.byNumericCode
import avail.exceptions.AvailErrorCode.E_CANNOT_MARK_HANDLER_FRAME
import avail.exceptions.AvailErrorCode.E_HANDLER_SENTINEL
import avail.exceptions.AvailErrorCode.E_NO_HANDLER_FRAME
import avail.exceptions.AvailErrorCode.E_UNWIND_SENTINEL
import avail.exceptions.AvailException
import avail.exceptions.AvailRuntimeException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Result
import avail.interpreter.Primitive.Result.CONTINUATION_CHANGED
import avail.interpreter.Primitive.Result.FAILURE
import avail.interpreter.Primitive.Result.FIBER_SUSPENDED
import avail.interpreter.Primitive.Result.READY_TO_INVOKE
import avail.interpreter.Primitive.Result.SUCCESS
import avail.interpreter.levelTwo.L1InstructionStepper
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operation.L2_INVOKE
import avail.interpreter.levelTwo.operation.L2_REIFY.StatisticCategory
import avail.interpreter.primitive.controlflow.P_CatchException
import avail.interpreter.primitive.fibers.P_AttemptJoinFiber
import avail.interpreter.primitive.fibers.P_ParkCurrentFiber
import avail.interpreter.primitive.variables.P_SetValue
import avail.optimizer.ExecutableChunk
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator
import avail.optimizer.StackReifier
import avail.optimizer.jvm.CheckedField
import avail.optimizer.jvm.CheckedField.Companion.instanceField
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.JVMChunk
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.performance.Statistic
import avail.performance.StatisticReport.TOP_LEVEL_STATEMENTS
import avail.utility.Strings.tab
import org.jetbrains.annotations.Debug.Renderer
import java.text.MessageFormat
import java.util.concurrent.ForkJoinWorkerThread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.CheckReturnValue

/**
 * This class is used to execute [Level&#32;Two&#32;code][L2Chunk], which is a
 * translation of the Level One nybblecodes found in
 * [raw&#32;functions][A_RawFunction].
 *
 * Level One nybblecodes are designed to be compact and very simple, but not
 * particularly efficiently executable. Level Two is designed for a clean model
 * for optimization, including:
 *
 *  * primitive folding.
 *  * register coloring/allocation.
 *  * inlining.
 *  * common sub-expression elimination.
 *  * side effect analysis.
 *  * object escape analysis.
 *  * a variant of keyhole optimization that involves building the loosest
 *    possible Level Two instruction dependency graph, then "pulling" eligible
 *    instruction sequences that are profitably rewritten.
 *  * further translation to native code – the [L1Translator] and [L2Generator]
 *    produce Level Two code, which is immediately translated to JVM bytecodes.
 *    This leverages the enormous amount of effort that has gone into the
 *    bytecode verifier, concurrency semantics, and HotSpot's low-level
 *    optimizations.
 *
 * To accomplish these goals, the stack-oriented architecture of Level One maps
 * onto a register transfer language for Level Two. At runtime the idealized
 * interpreter has an arbitrarily large bank of pointer registers (that point to
 * [Avail&#32;objects][AvailObject]), plus a separate bank for [Int]s (unboxed
 * 32-bit signed integers), and a similar bank for [Double]s (unboxed
 * double-precision floating point numbers).  We leave it to HotSpot to
 * determine how best to map these registers to CPU registers.
 *
 * One of the less intuitive aspects of the Level One / Level Two mapping is how
 * to handle the call stack. The Level One view is of a chain of continuations,
 * but Level Two doesn't even have a stack! We bridge this disconnect by using a
 * field in the interpreter to hold the _caller_ of the current continuation.
 * The [L1InstructionStepper] holds arrays of pointers, ints, and doubles for
 * the current continuation.
 *
 * We also use a technique called "semi-stackless".  Under this scheme, most
 * continuations run for a while, use local variables (within the call stack),
 * make normal Java-stack calls of their own, and eventually return their
 * result. However, if while running a function, the need arises to empty the
 * Java call stack into a chain of continuations, we return a [StackReifier].
 * When a call to an Avail function returns a `StackReifier`, the caller records
 * information about its local variables within a (heap-allocated) lambda, adds
 * the lambda to a list within the StackReifier, then returns to _its_ caller.
 * When it returns from the outermost Avail call, the StackReifier's list of
 * lambdas are run in the reverse order, each adding a continuation to the
 * chain.
 *
 * Later, when one of those continuations has to be "returned into" (calling in
 * Java but returning in Avail), the JVM entry point for that function is
 * invoked in such a way that it restores the register values from the
 * continuation, then continues executing where it left off.
 *
 * Note that unlike languages like C and C++, optimizations below Level One are
 * always transparent – other than observations about performance and memory
 * use. Also note that this was a design constraint for Avail as far back as
 * 1993, after `Self`, but before its technological successor Java. The way in
 * which this is accomplished (or will be more fully accomplished) in Avail is
 * by allowing the generated level two code itself to define how to maintain the
 * "accurate fiction" of a level one interpreter. If a method is inlined ten
 * layers deep inside an outer method, a non-inlined call from that inner method
 * requires ten layers of continuations to be constructed prior to the call (to
 * accurately maintain the fiction that it was always simply interpreting Level
 * One nybblecodes). There are ways to avoid or at least postpone this phase
 * transition, but I don't have any solid plans for introducing such a mechanism
 * any time soon.
 *
 * Finally, note that the Avail control structures are defined in terms of
 * multimethod dispatch and continuation resumption.  Multimethod dispatch is
 * implemented in terms of type-tests and conditional jumps in Level Two, so
 * conditional control flow ends up being similar to branches in traditional
 * languages.  Loops and exits are accomplished by restarting or exiting
 * continuations.  The Level Two optimizer generally identifies situations where
 * a label is created and then used for a restart within the same function, and
 * rewrites that as a backward jump, usually allowing the continuation creation
 * to be elided entirely.
 *
 * @constructor
 *
 * @property
 *   This interpreter's [Avail&#32;runtime][AvailRuntime].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Renderer(
	text = "toString",
	childrenArray = "nameForDebugger")
class Interpreter(
	@ReferencedInGeneratedCode
	@JvmField
	val runtime: AvailRuntime)
{
	/**
	 * As the system runs, the clock thread periodically wakes up and samples
	 * the running interpreters to get an indication of which [A_RawFunction]s
	 * are taking up time.  Those raw functions have their countdowns decreased
	 * by a big jump, being careful not to reach or cross zero.  That way, the
	 * logic for creating an optimized [L2Chunk]s for it remains within the
	 * execution mechanism.
	 *
	 * This method runs *in a foreign thread*, not the interpreter thread.  It
	 * answers the best estimate of which [A_RawFunction] is currently being run
	 * by this interpreter.  It polls the volatile [function] field to get a
	 * coherent read of the [A_Function] that's currently running, or at least
	 * was recently running.  The cost of having [function] be volatile should
	 * be relatively minor, but it ensures coherent access to the
	 * [A_Function.code] within it, and that [A_RawFunction]'s fields as well.
	 *
	 * TODO Eventually we may rework this, to allow dedicated threads to perform
	 *  the optimization while the execution threads continue to make progress.
	 *  In that case we would allow a zero crossing from either the periodic
	 *  polling or the invocation logic, and it would simply queue a task for
	 *  that raw function in the optimization thread pool.
	 */
	fun pollActiveRawFunction(): A_RawFunction?
	{
		val f: A_Function? = function
		return when
		{
			f === null -> null
			// Don't replace ===nil with .isNil, since that might have to
			// dispatch on an object whose descriptor is in flux.  It's not the
			// case as of 2021-06-17, but this is maintenance-proofing.
			f === nil -> null
			// A running A_RawFunction is always shared, so safe to access from
			// this polling thread.
			else -> f.code()
		}
	}

	/**
	 * The [fiber][FiberDescriptor] that is currently locked for this
	 * interpreter, or `null` if no fiber is currently locked.  This
	 * information is used to prevent multiple fibers from being locked
	 * simultaneously within a thread, which can lead to deadlock.
	 *
	 * This does not have to be volatile or atomic, since only this interpreter
	 * can access the field, and this interpreter can only be accessed from the
	 * single dedicated AvailThread that it's permanently associated with.
	 */
	private var currentlyLockedFiber: A_Fiber? = null

	/**
	 * Lock the specified fiber for the duration of evaluation of the provided
	 * [Supplier].  Answer the result produced by the supplier.
	 *
	 * @param aFiber
	 *   The fiber to lock.
	 * @param supplier
	 *   What to execute while the fiber is locked
	 * @param T
	 *   The type of value that the supplier will return.
	 * @return
	 *   The value produced by the supplier.
	 */
	fun <T> lockFiberWhile(
		aFiber: A_Fiber,
		supplier: ()->T
	): T
	{
		val previousFiber = currentlyLockedFiber
		assert(previousFiber === null || previousFiber === aFiber)
		currentlyLockedFiber = aFiber
		return try
		{
			supplier()
		}
		finally
		{
			currentlyLockedFiber = previousFiber
		}
	}

	/**
	 * Answer how many continuations would be created from Java stack frames at
	 * the current execution point (or the nearest place reification may be
	 * triggered).
	 *
	 * @return
	 *   The current number of unreified frames.
	 */
	fun unreifiedCallDepth(): Int = unreifiedCallDepth

	/**
	 * Answer whether the current frame's caller has been fully reified at this
	 * time, and is therefore at the top of the [getReifiedContinuation] call
	 * stack.
	 *
	 * @return
	 *   Whether the caller is already reified.
	 */
	@ReferencedInGeneratedCode
	fun callerIsReified(): Boolean = unreifiedCallDepth == 0

	/**
	 * Add the delta to the current count of how many frames would be reified
	 * into continuations at the current execution point.
	 *
	 * @param delta
	 *   How much to add.
	 */
	fun adjustUnreifiedCallDepthBy(delta: Int)
	{
		if (debugL1 || debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}Depth: {1} → {2}",
				debugModeString,
				unreifiedCallDepth,
				unreifiedCallDepth + delta)
		}
		unreifiedCallDepth += delta
	}

	/**
	 * Utility method for decomposing this object in the debugger. See
	 * [AvailObjectFieldHelper] for instructions to enable this functionality in
	 * IntelliJ.
	 *
	 * In particular, an Interpreter should present (possibly among other
	 * things) a complete stack trace of the current fiber, converting the deep
	 * continuation structure into a list of continuation substitutes that *do
	 * not* recursively print the caller chain.
	 *
	 * @return
	 *   An array of [AvailObjectFieldHelper] objects that help describe the
	 *   logical structure of the receiver to the debugger.
	 */
	@Suppress("unused")
	fun describeForDebugger(): Array<AvailObjectFieldHelper?>
	{
		val helpers = mutableListOf<AvailObjectFieldHelper>()

		// Produce the current function being executed...
		helpers.add(
			AvailObjectFieldHelper(
				nil, DUMMY_DEBUGGER_SLOT, -1, function, "Current function"))

		// Extract the current L2 offset...
		helpers.add(
			AvailObjectFieldHelper(
				nil,
				DUMMY_DEBUGGER_SLOT,
				-1,
				offset.toLong(),
				slotName = "L2 offset",
				forcedName = "L2 offset = $offset",
				forcedChildren = emptyArray<Any>()))

		// Extract the current arguments, which may or may not have been
		// consumed already, or may be in the process of being populated for the
		// next call...
		helpers.add(
			AvailObjectFieldHelper(
				nil,
				DUMMY_DEBUGGER_SLOT,
				-1,
				argsBuffer,
				slotName = "argsBuffer",
				forcedName = "argsBuffer(${argsBuffer.size})",
				forcedChildren = argsBuffer.toTypedArray()))

		// Produce the current chunk's L2 instructions...
		helpers.add(
			AvailObjectFieldHelper(
				nil,
				DUMMY_DEBUGGER_SLOT,
				-1,
				if (chunk !== null) chunk!!.instructions else emptyList<Any>(),
				slotName = "L2 instructions"))

		// Build the stack frames...
		var frame: A_Continuation? = getReifiedContinuation()
		if (frame !== null)
		{
			val frames = mutableListOf<A_Continuation>()
			while (frame!!.notNil)
			{
				frames.add(frame)
				frame = frame.caller()
			}
			helpers.add(
				AvailObjectFieldHelper(
					nil,
					DUMMY_DEBUGGER_SLOT,
					-1,
					tupleFromList(frames),
					slotName = "Frames"))
		}

		helpers.add(
			AvailObjectFieldHelper(
				nil,
				DUMMY_DEBUGGER_SLOT,
				-1,
				availLoaderOrNull(),
				slotName = "Loader"))

		helpers.add(
			AvailObjectFieldHelper(
				nil, DUMMY_DEBUGGER_SLOT, -1, fiber, slotName = "Fiber"))

		return helpers.toTypedArray()
	}

	/** Capture a unique ID between 0 and [maxInterpreters] minus one. */
	val interpreterIndex = runtime.allocateInterpreterIndex()

	/** Text to show at the starts of lines in debug traces. */
	var debugModeString = ""

	/**
	 * The [AvailLoader] associated with the [fiber][A_Fiber] currently running
	 * on this interpreter.  This is `null` if there is no fiber, or if it is
	 * not associated with an AvailLoader.
	 *
	 * This field is a consistent cache of the AvailLoader found in the fiber,
	 * which is authoritative.  Multiple fibers may share the same AvailLoader.
	 */
	private var availLoader: AvailLoader? = null

	/**
	 * Answer the [AvailLoader] associated with the [fiber][A_Fiber] currently
	 * running on this interpreter.  This interpreter must be bound to a fiber
	 * having an AvailLoader.
	 *
	 * @return
	 *   The current fiber's [AvailLoader].
	 */
	fun availLoader(): AvailLoader = availLoader!!

	/**
	 * Answer the [AvailLoader] associated with the [fiber][A_Fiber]
	 * currently running on this interpreter.  Answer `null` if there is
	 * no AvailLoader for the current fiber.
	 *
	 * @return
	 *   The current fiber's [AvailLoader].
	 */
	fun availLoaderOrNull(): AvailLoader? = availLoader

	/** The [A_Fiber] being executed by this interpreter. */
	private var fiber: A_Fiber? = null

	/**
	 * A fiber's debugger can only change during a safe point, but at that time
	 * no interpreters are bound to fibers, so this can be cached when binding
	 * the fiber to the interpreter, and cleared when unbinding.
	 */
	var debugger: AvailDebuggerModel? = null
		private set

	/**
	 * A fiber's debuggerRunCondition can only change during a safe point, but
	 * at that time no interpreters are bound to fibers, so this can be cached
	 * when binding the fiber to the interpreter, and cleared when unbinding.
	 */
	var debuggerRunCondition: ((A_Fiber)->Boolean)? = null
		private set

	/**
	 * Answer the current [fiber][A_Fiber] bound to this interpreter, or `null`
	 * if there is none.
	 *
	 * @return
	 *   The current fiber or null.
	 */
	fun fiberOrNull(): A_Fiber? = fiber

	/**
	 * Return the current [fiber][FiberDescriptor].
	 *
	 * @return
	 *   The current executing fiber.
	 */
	fun fiber(): A_Fiber = fiber!!

	/**
	 * Bind the specified [running][ExecutionState.RUNNING]
	 * [fiber][FiberDescriptor] to the `Interpreter`, or unbind the current
	 * fiber.
	 *
	 * @param newFiber
	 *   The fiber to run, or `null` to unbind the current fiber.
	 * @param tempDebug
	 *   A string describing the context of this operation.
	 */
	fun fiber(newFiber: A_Fiber?, tempDebug: String?)
	{
		if (debugPrimitives)
		{
			val string = buildString {
				append("[$interpreterIndex] fiber: ")
				append(
					if (fiber === null) "null"
					else "${fiber!!.uniqueId}[${fiber!!.executionState}]")
				append(" -> ")
				append(
					if (newFiber === null) "null"
					else "${newFiber.uniqueId}[${newFiber.executionState}]")
				append(" ($tempDebug)")
			}
			log(
				loggerDebugPrimitives,
				Level.INFO,
				"{0}",
				string)
		}
		assert((fiber === null) xor (newFiber === null))
		assert(newFiber === null || newFiber.executionState === RUNNING)
		fiber = newFiber
		setReifiedContinuation(null)
		if (newFiber !== null)
		{
			availLoader = newFiber.availLoader
			val readsBeforeWrites = newFiber.traceFlag(
				TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES)
			traceVariableReadsBeforeWrites = readsBeforeWrites
			traceVariableWrites = readsBeforeWrites
				|| newFiber.traceFlag(TraceFlag.TRACE_VARIABLE_WRITES)
			debugger = newFiber.fiberHelper.debugger.get()
			debuggerRunCondition = newFiber.fiberHelper.debuggerRunCondition
		}
		else
		{
			availLoader = null
			traceVariableReadsBeforeWrites = false
			traceVariableWrites = false
			debugger = null
			debuggerRunCondition = null
		}
	}

	/**
	 * Should the `Interpreter` record which [A_Variable]s are read before
	 * written while running its current [A_Fiber]?
	 */
	private var traceVariableReadsBeforeWrites = false

	/**
	 * Should the `Interpreter` record which [A_Variable]s are read before
	 * written while running its current [A_Fiber]?
	 *
	 * @return
	 *   `true` if the interpreter should record variable accesses, `false`
	 *   otherwise.
	 */
	fun traceVariableReadsBeforeWrites() = traceVariableReadsBeforeWrites

	/**
	 * Set the variable trace flag.
	 *
	 * @param traceVariableReadsBeforeWrites
	 *   `true` if the `Interpreter` should record which [A_Variable]s are read
	 *   before written while running its current [A_Fiber], `false` otherwise.
	 */
	fun setTraceVariableReadsBeforeWrites(traceVariableReadsBeforeWrites: Boolean)
	{
		if (traceVariableReadsBeforeWrites)
		{
			fiber().setTraceFlag(TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES)
		}
		else
		{
			fiber().clearTraceFlag(TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES)
		}
		this.traceVariableReadsBeforeWrites = traceVariableReadsBeforeWrites
		traceVariableWrites = traceVariableReadsBeforeWrites
	}

	/**
	 * Should the `Interpreter` record which [A_Variable]s are written while
	 * running its current [A_Fiber]?
	 */
	private var traceVariableWrites = false

	/**
	 * Should the `Interpreter` record which [A_Variable]s are written while
	 * running its current [A_Fiber]?
	 *
	 * @return
	 *   `true` if the interpreter should record variable accesses, `false`
	 *   otherwise.
	 */
	fun traceVariableWrites(): Boolean = traceVariableWrites

	/**
	 * Set the variable trace flag.
	 *
	 * @param traceVariableWrites
	 *   `true` if the `Interpreter` should record which [A_Variable]s are
	 *   written while running its current [A_Fiber], `false` otherwise.
	 */
	fun setTraceVariableWrites(traceVariableWrites: Boolean)
	{
		if (traceVariableWrites)
		{
			fiber().setTraceFlag(TraceFlag.TRACE_VARIABLE_WRITES)
		}
		else
		{
			fiber().clearTraceFlag(TraceFlag.TRACE_VARIABLE_WRITES)
		}
		this.traceVariableWrites = traceVariableWrites
	}

	/**
	 * Answer the [A_Module] being loaded by this interpreter's loader. If there
	 * is no [loader][AvailLoader] then answer `nil`.
	 *
	 * @return
	 *   The current loader's module under definition, or `nil` if loading is
	 *   not taking place via this interpreter.
	 */
	fun module(): A_Module = fiber().availLoader?.module ?: nil

	/**
	 * The latest result produced by a [successful][Result.SUCCESS]
	 * [primitive][Primitive], or the latest [error&#32;code][AvailErrorCode]
	 * [A_Number] produced by a [failed][Result.FAILURE] primitive.
	 */
	private var latestResult: AvailObject? = null

	/**
	 * Set the latest result due to a [successful][Result.SUCCESS]
	 * [primitive][Primitive], or the latest [error&#32;code][AvailErrorCode]
	 * [A_Number] produced by a [failed][Result.FAILURE] primitive.
	 *
	 * The value may be Java's `null` to indicate this field should be clear,
	 * to detect accidental use.
	 *
	 * @param newResult
	 *   The latest result to record.
	 */
	@ReferencedInGeneratedCode
	fun setLatestResult(newResult: A_BasicObject?)
	{
		assert(newResult !== null || !returnNow)
		latestResult = newResult as AvailObject?
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.INFO,
				debugModeString + "Set latestResult: " +
					if (latestResult === null) "null"
					else latestResult!!.typeTag.name)
		}
	}

	/**
	 * Answer the latest result produced by a [successful][Result.SUCCESS]
	 * [primitive][Primitive], or the latest [error&#32;code][AvailErrorCode]
	 * number produced by a [failed][Result.FAILURE] primitive.
	 *
	 * @return
	 *   The latest result.
	 */
	@ReferencedInGeneratedCode
	fun getLatestResult(): AvailObject = latestResult!!

	/**
	 * Answer the latest result produced by a [successful][Result.SUCCESS]
	 * [primitive][Primitive], or the latest [error&#32;code][AvailErrorCode]
	 * number produced by a [failed][Result.FAILURE] primitive.  Answer `null`
	 * if no such value is available.  This is useful for saving/restoring
	 * without knowing whether the value is valid.
	 *
	 * @return
	 *   The latest result (or primitive failure value) or `null`.
	 */
	fun latestResultOrNull(): AvailObject? = latestResult

	/**
	 * A field that captures which [A_Function] is returning.  This is
	 * used for statistics collection and reporting errors when returning a
	 * value that disagrees with semantic restrictions.
	 */
	@ReferencedInGeneratedCode
	@JvmField
	var returningFunction: A_Function? = null

	/**
	 * Some operations like [L2_INVOKE] instructions have statistics that
	 * shouldn't include the [L2Instruction]s executed while the invoked
	 * function is running (e.g., other L2_INVOKE instructions).  Accumulate
	 * those here.  When an L2_INVOKE completes its invocation, replace the
	 * portion representing the sub-tasks accumulated during the call with a
	 * value representing the actual elapsed time for the call, but exclude the
	 * prior value from the reported L2_INVOKE.
	 */
	private var nanosToExclude = 0L

	/**
	 * Suspend the current fiber, evaluating the provided action.  The action is
	 * passed two additional actions, one indicating how to resume from the
	 * suspension in the future (taking the result of the primitive), and the
	 * other indicating how to cause the primitive to fail (taking an
	 * AvailErrorCode).
	 *
	 * @param action
	 *   The action supplied by the client that itself takes two actions for
	 *   succeeding and failing the primitive at a later time.
	 * @return
	 *   The value [FIBER_SUSPENDED].
	 */
	fun suspendInSafePointThen(
		action: SuspensionHelper<A_BasicObject>.()->Unit
	): Result = fiber!!.let { theFiber ->
		suspendThen {
			runtime.whenSafePointDo(
				theFiber.priority,
				AvailTask.forUnboundFiber(theFiber) { action() })
		}
	}

	/**
	 * A helper class for making fiber suspension syntax more articulate.  It
	 * provides [succeed] and [fail] methods that client code can invoke.
	 *
	 * @property toSucceed
	 *   The function to call that accepts a value from the [Primitive] if the
	 *   `Primitive` is successful.
	 * @property toFail
	 *   The function to call that accepts an [A_BasicObject] that provides the
	 *   reason for the [Primitive] failure.
	 *
	 * @constructor
	 * Construct a [SuspensionHelper].
	 *
	 * @param toSucceed
	 *   The function to call that accepts a value from the [Primitive] if the
	 *   `Primitive` is successful.
	 * @param toFail
	 *   The function to call that accepts an [A_BasicObject] that provides the
	 *   reason for the [Primitive] failure.
	 */
	class SuspensionHelper<A> constructor (
		private val toSucceed: (A)->Unit,
		private val toFail: (A_BasicObject)->Unit)
	{
		/**
		 * Succeed from the suspended [Primitive], resuming its fiber.
		 *
		 * @param value
		 *   The value to return from the primitive.
		 */
		fun succeed(value: A) = toSucceed(value)

		/**
		 * Fail from the suspended [Primitive], resuming its fiber.
		 *
		 * @param errorNumber
		 *   The [A_BasicObject] to provide as the reason for failing the
		 *   primitive.
		 */
		fun fail(errorNumber: A_BasicObject) = toFail(errorNumber)

		/**
		 * Fail from the suspended [Primitive], resuming its fiber.
		 *
		 * @param errorCode
		 *   The [AvailErrorCode] whose numeric
		 *   [code][AvailErrorCode.numericCode] is used as the reason for
		 *   failing the primitive.
		 */
		fun fail(errorCode: AvailErrorCode) = toFail(errorCode.numericCode())
	}

	/**
	 * Suspend the interpreter in the middle of running a primitive (which must
	 * be marked as [Primitive.Flag.CanSuspend]).  The supplied action can
	 * invoke [succeed][SuspensionHelper.succeed] or
	 * [fail][SuspensionHelper.fail] when it has determined its fate.
	 *
	 * @param body
	 *   What to do when the fiber has been suspended.
	 */
	@CheckReturnValue
	fun suspendThen (body: SuspensionHelper<A_BasicObject>.()->Unit): Result
	{
		val copiedArgs = argsBuffer.map { it }
		val primitiveFunction = function!!
		val prim = primitiveFunction.code().codePrimitive()!!
		assert(prim.hasFlag(CanSuspend))
		val currentFiber = fiber()
		val once = AtomicBoolean(false)
		postExitContinuation {
			SuspensionHelper<A_BasicObject>(
				toSucceed = {
					assert(!once.getAndSet(true))
					runtime.resumeFromSuccessfulPrimitive(
						currentFiber,
						prim,
						it)
				},
				toFail = {
					assert(!once.getAndSet(true))
					runtime.resumeFromFailedPrimitive(
						currentFiber,
						it,
						primitiveFunction,
						copiedArgs)
				}
			).body()
		}
		return primitiveSuspend(primitiveFunction)
	}

	/**
	 * Set the resulting value of a primitive invocation. Answer primitive
	 * [success][Result.SUCCESS].
	 *
	 * @param result
	 *   The result of performing a [primitive][Primitive].
	 * @return
	 *   Primitive [Result.SUCCESS].
	 */
	@CheckReturnValue
	fun primitiveSuccess(result: A_BasicObject): Result
	{
		assert(fiber().executionState === RUNNING)
		setLatestResult(result)
		return SUCCESS
	}

	/**
	 * Set the resulting value of a primitive invocation to the numeric
	 * [code][AvailErrorCode.numericCode] of the specified [AvailErrorCode].
	 * Answer primitive [failure][Result.FAILURE].
	 *
	 * @param code
	 *   An [AvailErrorCode].
	 * @return
	 *   Primitive [Result.FAILURE].
	 */
	@CheckReturnValue
	fun primitiveFailure(code: AvailErrorCode): Result =
		primitiveFailure(code.numericCode())

	/**
	 * Set the resulting value of a primitive invocation to the numeric
	 * [code][AvailErrorCode.numericCode] of the [AvailErrorCode] embedded
	 * within the specified [exception][AvailException].  Answer primitive
	 * [failure][Result.FAILURE].
	 *
	 * @param exception
	 *   An [exception][AvailException].
	 * @return
	 *   Primitive [Result.FAILURE].
	 */
	@CheckReturnValue
	fun primitiveFailure(exception: AvailException): Result =
		primitiveFailure(exception.numericCode())

	/**
	 * Set the resulting value of a primitive invocation to the numeric
	 * [code][AvailErrorCode.numericCode] of the [AvailRuntimeException].
	 * Answer primitive [failure][Result.FAILURE].
	 *
	 * @param exception
	 *   An [AvailRuntimeException].
	 * @return
	 *   Primitive [Result.FAILURE].
	 */
	@CheckReturnValue
	fun primitiveFailure(exception: AvailRuntimeException): Result =
		primitiveFailure(exception.numericCode)

	/**
	 * Set the resulting value of a primitive invocation. Answer primitive
	 * [failure][Result.FAILURE].
	 *
	 * @param result
	 *   The failure value of performing a [primitive][Primitive].
	 * @return
	 *   Primitive [failure][Result.FAILURE].
	 */
	@CheckReturnValue
	fun primitiveFailure(result: A_BasicObject): Result
	{
		assert(fiber().executionState === RUNNING)
		setLatestResult(result)
		return FAILURE
	}

	/**
	 * Should the current executing chunk return to its caller?  The value to
	 * return is in [latestResult].  If the outer interpreter loop detects this,
	 * it should resume the top reified continuation's chunk, giving it an
	 * opportunity to accept the return value and de-reify.
	 */
	@ReferencedInGeneratedCode
	@JvmField
	var returnNow = false

	/**
	 * Should the [Interpreter] exit its [run] loop?  This can happen when the
	 * [fiber][A_Fiber] has completed, failed, or been suspended.
	 */
	var exitNow = true

	/**
	 * An action to run after a [fiber][A_Fiber] exits and is unbound.
	 */
	var postExitContinuation: (()->Unit)? = null

	/**
	 * Set the post-exit continuation. The affected fiber will be locked around
	 * the evaluation of this continuation.
	 *
	 * @param continuation
	 *   What to do after a [fiber][FiberDescriptor] has exited and been
	 *   unbound, or `null` if nothing should be done.
	 */
	fun postExitContinuation(continuation: (()->Unit)?)
	{
		assert(postExitContinuation === null || continuation === null)
		postExitContinuation = continuation
	}

	/**
	 * Suspend the current [A_Fiber] within a [Primitive] invocation.  The
	 * reified [A_Continuation] will be available in [getReifiedContinuation],
	 * and will be installed into the current fiber.
	 *
	 * @param state
	 *   The suspension [state][ExecutionState].
	 * @return
	 *   [Result.FIBER_SUSPENDED], for convenience.
	 */
	@CheckReturnValue
	private fun primitiveSuspend(state: ExecutionState): Result
	{
		assert(!exitNow)
		assert(state.indicatesSuspension)
		assert(unreifiedCallDepth() == 0)
		val aFiber = fiber()
		aFiber.lock {
			assert(aFiber.executionState === RUNNING)
			aFiber.executionState = state
			aFiber.continuation = getReifiedContinuation()!!
			setReifiedContinuation(null)
			val bound = aFiber.getAndSetSynchronizationFlag(BOUND, false)
			aFiber.fiberHelper.stopCountingCPU()
			assert(bound)
			fiber(null, "primitiveSuspend")
		}
		startTick = -1L
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.INFO,
				"{0}Set exitNow (primitiveSuspend), clear latestResult",
				debugModeString)
		}
		exitNow = true
		setLatestResult(null)
		levelOneStepper.wipeRegisters()
		return FIBER_SUSPENDED
	}

	/**
	 * [Suspend][SUSPENDED] the current [A_Fiber] from within a [Primitive]
	 * invocation.  The reified [A_Continuation] will be available in
	 * [getReifiedContinuation], and will be installed into the current fiber.
	 *
	 * @param suspendingFunction
	 *   The primitive [A_Function] causing the fiber suspension.
	 * @return
	 *   [Result.FIBER_SUSPENDED], for convenience.
	 */
	@CheckReturnValue
	fun primitiveSuspend(suspendingFunction: A_Function): Result
	{
		val prim = suspendingFunction.code().codePrimitive()!!
		assert(prim.hasFlag(CanSuspend))
		fiber().suspendingFunction = suspendingFunction
		function = null // Safety
		return primitiveSuspend(SUSPENDED)
	}

	/**
	 * [Park][ExecutionState.PARKED] the current [A_Fiber] from within a
	 * [Primitive] invocation.  The reified [A_Continuation] will be available
	 * in [getReifiedContinuation], and will be installed into the current
	 * fiber.
	 *
	 * @param suspendingFunction
	 *   The primitive [A_Function] parking the fiber.
	 * @return
	 *   [Result.FIBER_SUSPENDED], for convenience.
	 */
	@CheckReturnValue
	fun primitivePark(suspendingFunction: A_Function): Result
	{
		fiber().suspendingFunction = suspendingFunction
		return primitiveSuspend(PARKED)
	}

	/**
	 * Terminate the current [fiber], using the specified [object][AvailObject]
	 * as its final result.
	 *
	 * @param finalObject
	 *   The fiber's result, or [nil] if none.
	 * @param state
	 *   An [ExecutionState] that indicates
	 *   [termination][ExecutionState.indicatesTermination].
	 */
	private fun exitFiber(
		finalObject: A_BasicObject,
		state: ExecutionState)
	{
		assert(!exitNow)
		assert(state.indicatesTermination)
		val aFiber = fiber()
		aFiber.lock {
			assert(aFiber.executionState === RUNNING)
			aFiber.executionState = state
			aFiber.continuation = nil
			aFiber.fiberResult = finalObject as AvailObject
			val bound = aFiber.getAndSetSynchronizationFlag(BOUND, false)
			aFiber.fiberHelper.stopCountingCPU()
			assert(bound)
			fiber(null, "exitFiber")
		}
		startTick = -1L
		exitNow = true
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.INFO,
				debugModeString
					+ "Set exitNow and clear latestResult (exitFiber)")
		}
		setLatestResult(null)
		levelOneStepper.wipeRegisters()
		postExitContinuation {
			val joining = aFiber.lock {
				val temp: A_Set = aFiber.joiningFibers.makeShared()
				aFiber.joiningFibers = nil
				temp
			}
			// Wake up all fibers trying to join this one.
			joining.forEach { joiner ->
				joiner.lock {
					// Restore the permit. Resume the fiber if it was parked.
					joiner.getAndSetSynchronizationFlag(
						PERMIT_UNAVAILABLE, false)
					if (joiner.executionState === PARKED)
					{
						// Unpark it, whether it's still parked because of an
						// attempted join on this fiber, an attempted join on
						// another fiber (due to a spurious wakeup and giving up
						// on the first join), or a park (same).  A retry loop
						// in the public joining methods should normally deal
						// with spurious unparks, but there's no mechanism yet
						// to eject the stale joiner from the set.
						joiner.executionState = SUSPENDED
						val suspended =
							joiner.suspendingFunction.code()
								.codePrimitive()!!
						assert(suspended === P_AttemptJoinFiber
							|| suspended === P_ParkCurrentFiber)
						runtime.resumeFromSuccessfulPrimitive(
							joiner, suspended, nil)
					}
				}
			}
		}
	}

	/**
	 * [Terminate][ExecutionState.TERMINATED] the current [fiber], using the
	 * specified [object][AvailObject] as its final result.
	 *
	 * @param value
	 *   The fiber's result.
	 */
	fun terminateFiber(value: A_BasicObject) =
		exitFiber(value, TERMINATED)

	/**
	 * [Abort][ExecutionState.ABORTED] the current [fiber].
	 */
	fun abortFiber() = exitFiber(nil, ABORTED)

	/**
	 * Attempt the [primitive][Primitive], dynamically checking whether it is an
	 * [inlineable][Primitive.Flag.CanInline] primitive.
	 *
	 * This is used by the [L2Chunk.unoptimizedChunk]'s
	 *
	 * @param primitiveFunction
	 *   The [A_Function].
	 * @param primitive
	 *   The [Primitive].
	 * @return
	 *   The [StackReifier], if any.
	 */
	@ReferencedInGeneratedCode
	fun attemptThePrimitive(
		primitiveFunction: A_Function,
		primitive: Primitive): StackReifier? =
		if (primitive.hasFlag(Primitive.Flag.CanInline))
		{
			attemptInlinePrimitive(primitiveFunction, primitive)
		}
		else
		{
			attemptNonInlinePrimitive(primitiveFunction, primitive)
		}

	/**
	 * Attempt the [inlineable][Primitive.Flag.CanInline]
	 * [primitive][Primitive].
	 *
	 * @param primitiveFunction
	 *   The primitive [A_Function] to invoke.
	 * @param primitive
	 *   The [Primitive] to attempt.
	 * @return
	 *   The [StackReifier], if any.
	 */
	@ReferencedInGeneratedCode
	fun attemptInlinePrimitive(
		primitiveFunction: A_Function,
		primitive: Primitive): StackReifier?
	{
		// It can succeed or fail, but it can't mess with the fiber's stack.
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}          inline prim = {1}",
				debugModeString,
				primitive.name)
		}
		val timeBefore = beforeAttemptPrimitive(primitive)
		val result = primitive.attempt(this)
		afterAttemptPrimitive(primitive, timeBefore, result)
		return when (result)
		{
			SUCCESS ->
			{
				assert(latestResultOrNull() !== null)
				function = null
				returnNow = true
				returningFunction = primitiveFunction
				null
			}
			FAILURE ->
			{
				assert(latestResultOrNull() !== null)
				function = primitiveFunction
				setOffset(chunk!!.offsetAfterInitialTryPrimitive())
				assert(!returnNow)
				null
			}
			READY_TO_INVOKE ->
			{
				assert(primitive.hasFlag(Primitive.Flag.Invokes))
				val stepper = levelOneStepper
				val savedChunk = chunk
				val savedOffset = offset
				val savedPointers = stepper.pointers

				// The invocation did a runChunk, but we need to do another
				// runChunk now (via invokeFunction).  Only one should count
				// as an unreified frame (specifically the inner one we're
				// about to start).
				adjustUnreifiedCallDepthBy(-1)
				val reifier = invokeFunction(function!!)
				adjustUnreifiedCallDepthBy(1)
				function = primitiveFunction
				chunk = savedChunk
				setOffset(savedOffset)
				stepper.pointers = savedPointers
				if (reifier !== null)
				{
					return reifier
				}
				assert(latestResultOrNull() !== null)
				returnNow = true
				returningFunction = function
				null
			}
			CONTINUATION_CHANGED ->
			{
				assert(primitive.hasFlag(Primitive.Flag.CanSwitchContinuations))
				val newContinuation = getReifiedContinuation()!!
				val newFunction = function
				val newChunk = chunk
				val newOffset = offset
				val newReturnNow = returnNow
				val newReturnValue = latestResultOrNull()
				isReifying = true
				StackReifier(false, primitive.reificationAbandonmentStat!!)
				{
					setReifiedContinuation(newContinuation)
					function = newFunction
					chunk = newChunk
					setOffset(newOffset)
					returnNow = newReturnNow
					setLatestResult(newReturnValue)
					isReifying = false
				}
			}
			FIBER_SUSPENDED ->
			{
				assert(false)
				{ "CanInline primitive must not suspend fiber" }
				null
			}
		}
	}

	/**
	 * Attempt the [non-inlineable][Primitive.Flag.CanInline]
	 * [primitive][Primitive].
	 *
	 * @param primitiveFunction
	 *   The [A_Function].
	 * @param primitive
	 *   The [Primitive].
	 * @return
	 *   The [StackReifier], if any.
	 */
	@ReferencedInGeneratedCode
	fun attemptNonInlinePrimitive(
		primitiveFunction: A_Function?,
		primitive: Primitive): StackReifier
	{
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}          reifying for {1}",
				debugModeString,
				primitive.name)
		}
		val stepper = levelOneStepper
		val savedChunk = chunk!!
		val savedOffset = offset
		val savedPointers = stepper.pointers
		// Save the argsBuffer, since we now (Feb 2021) allow infallible
		// primitives to be postponed all the way into the start of a
		// reification zone, and the current calling convention destroys the
		// existing argument list.
		val savedArgs = argsBuffer.toTypedArray()

		// Continue in this frame where it left off, right after the
		// L2_TRY_OPTIONAL_PRIMITIVE instruction. Inline and non-inline
		// primitives are each allowed to change the continuation.  The stack
		// has already been reified here, so just continue in whatever frame was
		// set up by the continuation. The exitNow flag is set to ensure the
		// interpreter will wind down correctly.  It should be in a state where
		// all frames have been reified, so returnNow would be unnecessary.
		isReifying = true
		return StackReifier(true, primitive.reificationForNoninlineStat!!)
		{
			assert(unreifiedCallDepth() == 0) {
				"Should have reified stack for non-inlineable primitive"
			}
			chunk = savedChunk
			setOffset(savedOffset)
			stepper.pointers = savedPointers
			function = primitiveFunction
			if (debugL2)
			{
				log(
					loggerDebugL2,
					Level.FINER,
					"{0}          reified, now starting {1}",
					debugModeString,
					primitive.name)
			}
			argsBuffer.clear()
			argsBuffer.addAll(savedArgs)
			val timeBefore = beforeAttemptPrimitive(primitive)
			val result = primitive.attempt(this)
			afterAttemptPrimitive(primitive, timeBefore, result)
			when (result)
			{
				SUCCESS ->
				{
					assert(latestResultOrNull() !== null)
					returnNow = true
					returningFunction = primitiveFunction
				}
				FAILURE ->
				{
					assert(latestResultOrNull() !== null)
					function = primitiveFunction
					setOffset(chunk!!.offsetAfterInitialTryPrimitive())
					assert(!returnNow)
				}
				READY_TO_INVOKE ->
				{
					assert(false) { "Invoking primitives should be inlineable" }
				}
				CONTINUATION_CHANGED ->
				{
					assert(
						primitive.hasFlag(
							Primitive.Flag.CanSwitchContinuations))
				}
				FIBER_SUSPENDED ->
				{
					assert(exitNow)
					returnNow = false
				}
			}
			isReifying = false
		}
	}

	/**
	 * Prepare to execute the given primitive.  Answer the current time in
	 * nanoseconds.
	 *
	 * @param primitive
	 *   The [Primitive] that is about to run.
	 * @return
	 *   The current time in nanoseconds since the Epoch, as a [Long].
	 */
	@ReferencedInGeneratedCode
	fun beforeAttemptPrimitive(primitive: Primitive): Long
	{
		if (debugPrimitives)
		{
			val builder = StringBuilder()
			var argCount = 1
			for (arg in argsBuffer)
			{
				var argString = arg.toString()
				if (argString.length > 70)
				{
					argString = argString.substring(0, 70) + "..."
				}
				argString = argString.replace("\\n".toRegex(), "\\\\n")
				builder
					.append("\n\t\t")
					.append(debugModeString)
					.append("\t#")
					.append(argCount)
					.append(". ")
					.append(argString)
				argCount++
			}
			log(
				loggerDebugPrimitives,
				Level.FINER,
				"{0}attempt {1}{2}",
				debugModeString,
				primitive.name,
				builder.toString())
		}
		returnNow = false
		setLatestResult(null)
		assert(current() == this)
		return AvailRuntimeSupport.captureNanos()
	}

	/**
	 * The given primitive has just executed; do any necessary post-processing.
	 *
	 * @param primitive
	 *   The primitive that just ran.
	 * @param timeBefore
	 *   The time in nanoseconds just prior to the primitive running.
	 * @param success
	 *   The [Result] of running the primitive, indicating whether it succeeded,
	 *   failed, etc.
	 * @return
	 *   The same [Result] that was passed, to make calling simpler.
	 */
	@ReferencedInGeneratedCode
	fun afterAttemptPrimitive(
		primitive: Primitive,
		timeBefore: Long,
		success: Result
	): Result
	{
		val timeAfter = AvailRuntimeSupport.captureNanos()
		primitive.addNanosecondsRunning(
			timeAfter - timeBefore, interpreterIndex)
		assert(success !== FAILURE || !primitive.hasFlag(CannotFail))
		if (debugPrimitives)
		{
			if (loggerDebugPrimitives.isLoggable(Level.FINER))
			{
				val detailPart = when
				{
					success === SUCCESS ->
					{
						var result = getLatestResult().toString()
						if (result.length > 70)
						{
							result = result.substring(0, 70) + "..."
						}
						" --> $result"
					}
					success === FAILURE && getLatestResult().isInt ->
					{
						val errorInt = getLatestResult().extractInt
						" (${byNumericCode(errorInt)})"
					}
					else -> ""
				}
				log(
					loggerDebugPrimitives,
					Level.FINER,
					"{0}... completed primitive {1} => {2}{3}",
					debugModeString,
					primitive.name,
					success.name,
					detailPart)
				if (success !== SUCCESS)
				{
					log(
						loggerDebugPrimitives,
						Level.FINER,
						"{0}      ({1})",
						debugModeString,
						success.name)
				}
			}
		}
		return success
	}

	/**
	 * The (bottom) portion of the call stack that has been reified. This must
	 * always be either an [A_Continuation], [nil], or `null`.  It's typed as
	 * [AvailObject] to avoid potential JVM runtime checks.
	 */
	private var theReifiedContinuation: AvailObject? = null

	/**
	 * Answer the (bottom) portion of the call stack that has been reified. This
	 * must always be either an [A_Continuation], [nil], or `null`.  It's typed
	 * as [AvailObject] to avoid potential JVM runtime checks.
	 *
	 * @return
	 *   The current reified [A_Continuation].
	 */
	@ReferencedInGeneratedCode
	fun getReifiedContinuation(): AvailObject? = theReifiedContinuation

	/**
	 * Set the current reified [A_Continuation].
	 *
	 * @param continuation
	 *   The [A_Continuation], [nil], or `null`.
	 */
	@ReferencedInGeneratedCode
	fun setReifiedContinuation(continuation: A_Continuation?)
	{
		theReifiedContinuation = continuation as AvailObject?
		if (debugL2)
		{
			val text = when
			{
				continuation === null -> "null"
				continuation.isNil -> continuation.toString()
				else ->
				{
					when (val theChunk = continuation.levelTwoChunk())
					{
						L2Chunk.unoptimizedChunk ->
							continuation.function().code().methodName
								.toString() +
								" (unoptimized)"
						else -> (theChunk.name() + ", offset= " +
							continuation.levelTwoOffset())
					}
				}
			}
			traceL2(
				(chunk?.executableChunk
					?: L2Chunk.unoptimizedChunk.executableChunk),
				-999999,
				"Set continuation = ",
				text)
		}
	}

	/**
	 * Replace the [getReifiedContinuation] with its caller.
	 */
	@ReferencedInGeneratedCode
	fun popContinuation()
	{
		if (debugL2)
		{
			val builder = StringBuilder()
			var ptr: A_Continuation = theReifiedContinuation!!
			while (ptr.notNil)
			{
				builder
					.append("\n\t\toffset ")
					.append(ptr.levelTwoOffset())
					.append(" in ")
				val ch = ptr.levelTwoChunk()
				if (ch == L2Chunk.unoptimizedChunk)
				{
					builder.append("(L1) - ")
						.append(ptr.function().code().methodName)
				}
				else
				{
					builder.append(ptr.levelTwoChunk().name())
				}
				ptr = ptr.caller()
			}
			traceL2(
				(chunk?.executableChunk
					?: L2Chunk.unoptimizedChunk.executableChunk),
				-100000,
				"POPPING CONTINUATION from:",
				builder)
		}
		setReifiedContinuation(getReifiedContinuation()!!.caller())
	}

	/**
	 * The number of stack frames that reification would transform into
	 * continuations.
	 */
	private var unreifiedCallDepth = 0

	/**
	 * The [A_Function] being executed.  This is only volatile so that the
	 * [AvailRuntime.clock] thread can safely [pollActiveRawFunction], then
	 * navigate from the [A_Function] to the [A_RawFunction] inside it.
	 */
	@ReferencedInGeneratedCode
	@JvmField
	@Volatile
	var function: A_Function? = null

	/** The [L2Chunk] being executed. */
	@ReferencedInGeneratedCode
	@JvmField
	var chunk: L2Chunk? = null

	/**
	 * The current zero-based L2 offset within the current L2Chunk's
	 * instructions.
	 */
	@ReferencedInGeneratedCode
	@JvmField
	var offset = 0

	/**
	 * Jump to a new position in the L2 instruction stream.
	 *
	 * @param newOffset
	 *   The new position in the L2 instruction stream.
	 */
	fun setOffset(newOffset: Int)
	{
		offset = newOffset
	}

	/**
	 * A reusable temporary buffer used to hold arguments during method
	 * invocations.
	 */
	@ReferencedInGeneratedCode
	@JvmField
	val argsBuffer = mutableListOf<AvailObject>()

	/**
	 * Assert that the number of arguments in the [argsBuffer] agrees with the
	 * given expected number.
	 *
	 * @param expectedCount
	 *   The exact number of arguments that should be present.
	 */
	fun checkArgumentCount(expectedCount: Int) =
		assert(argsBuffer.size == expectedCount)

	/**
	 * Answer the specified element of argsBuffer.
	 *
	 * @param zeroBasedIndex
	 *   The zero-based index at which to extract an argument being passed in an
	 *   invocation.
	 * @return
	 *   The actual argument.
	 */
	fun argument(zeroBasedIndex: Int): AvailObject = argsBuffer[zeroBasedIndex]

	/**
	 * The [L1InstructionStepper] used to simulate execution of Level One
	 * nybblecodes.
	 */
	@ReferencedInGeneratedCode
	@JvmField
	val levelOneStepper = L1InstructionStepper(this)

	/**
	 * The value of the [clock][AvailRuntime.clock] when the [run] loop started
	 * running.
	 */
	private var startTick = -1L

	/**
	 * Answer true if an interrupt has been requested. The interrupt may be
	 * specific to the current [fiber] or global to the [runtime][AvailRuntime].
	 * There are several reasons why an interrupt might be requested:
	 *
	 * * A safe point might be requested by the runtime, to ensure no fibers are
	 *   executing during a critical operation, such as adding a method
	 *   definition.  This requires more than just a lock, since it will cause
	 *   [L2Chunk]s that rely on that method to be invalidated, which would not
	 *   work if those chunks were running.  Reified continuations that get
	 *   built for such chunks always check for validity when they're resumed,
	 *   allowing them to downgrade safely to the default chunk that runs the L1
	 *   interpreter.
	 * * The stack might be deeper than the [maxUnreifiedCallDepth].  This is
	 *   currently measured by number of Avail function calls.  A reification of
	 *   the frames from the JVM stack effectively resets this to zero without
	 *   affecting program semantics, allowing interpreter stacks to be bounded.
	 *   This is not just to support deep recursion, but to ensure any interrupt
	 *   can reify the stack in a reasonable time.
	 * * The current clock tick counter may indicate that more than
	 *   [timeSliceTicks] have elapsed since starting or resuming the current
	 *   fiber.  In that case, a task to resume the fiber should be queued, and
	 *   the next eligible fiber should be run (which might end up being the
	 *   current fiber again).
	 * * The [REIFICATION_REQUESTED] flag may have been set on the current
	 *   fiber.  This mechanism allows a fiber to efficiently poll another
	 *   fiber's current [A_Continuation] periodically.  Note that the reified
	 *   continuation is always made [Shared][AvailObject.makeShared] in this
	 *   situation, so that both fibers will be able to access the state safely.
	 *
	 * @return
	 *   `true` if an interrupt is pending, `false` otherwise.
	 */
	@get:ReferencedInGeneratedCode
	val isInterruptRequested: Boolean
		get() = (runtime.safePointRequested()
			|| unreifiedCallDepth > maxUnreifiedCallDepth
			|| runtime.clock.get() - startTick >= timeSliceTicks
			|| fiber().interruptRequestFlag(REIFICATION_REQUESTED))

	/**
	 * The current [fiber] has been asked to temporarily cease running for an
	 * inter-nybblecode interrupt for some reason. It has possibly executed
	 * several more L2 instructions since that time, to place the fiber into a
	 * state that's consistent with naive Level One execution semantics. That
	 * is, a naive Level One interpreter should be able to resume the fiber
	 * later (although most of the time the Level Two interpreter will kick in).
	 *
	 * @param continuation
	 *   The reified continuation to save into the current fiber.
	 */
	fun processInterrupt(continuation: A_Continuation)
	{
		assert(!exitNow)
		assert(!returnNow)
		val aFiber = fiber()
		var waiters: List<(A_Continuation)->Unit> = emptyList()
		aFiber.lock {
			synchronized(aFiber) {
				assert(aFiber.executionState === RUNNING)
				aFiber.executionState = INTERRUPTED
				aFiber.continuation = continuation
				if (aFiber.getAndClearInterruptRequestFlag(
						REIFICATION_REQUESTED))
				{
					continuation.makeShared()
					waiters = aFiber.getAndClearReificationWaiters()
					assert(waiters.isNotEmpty())
				}
				val bound = fiber().getAndSetSynchronizationFlag(BOUND, false)
				aFiber.fiberHelper.stopCountingCPU()
				assert(bound)
				fiber(null, "processInterrupt")
			}
		}
		assert(!exitNow)
		returnNow = false
		exitNow = true
		offset = Int.MAX_VALUE
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}Set exitNow (processInterrupt)",
				debugModeString)
		}
		startTick = -1L
		setLatestResult(null)
		levelOneStepper.wipeRegisters()
		postExitContinuation {
			waiters.forEach { action -> action(continuation) }
			runtime.resumeFromInterrupt(aFiber)
		}
	}

	/**
	 * Raise an exception. Scan the stack of continuations (which must have been
	 * reified already) until one is found for a function whose code specifies
	 * [P_CatchException]. Get that continuation's second argument (a handler
	 * block of one argument), and check if that handler block will accept the
	 * exceptionValue. If not, keep looking. If it accepts it, unwind the
	 * continuation stack so that the primitive catch method is the top entry,
	 * and invoke the handler block with exceptionValue. If there is no suitable
	 * handler block, fail the primitive.
	 *
	 * @param
	 *   exceptionValue The exception object being raised.
	 * @return
	 *   The [success&#32;state][Result].
	 */
	fun searchForExceptionHandler(exceptionValue: AvailObject): Result
	{
		// Replace the contents of the argument buffer with "exceptionValue",
		// an exception augmented with stack information.
		assert(argsBuffer.size == 1)
		argsBuffer[0] = exceptionValue
		var continuation = getReifiedContinuation()!!
		var depth = 0
		while (continuation.notNil)
		{
			val code = continuation.function().code()
			if (code.codePrimitive() == P_CatchException)
			{
				assert(code.numArgs() == 3)
				val failureVariable: A_Variable = continuation.frameAt(4)
				// Scan a currently unmarked frame.
				if (failureVariable.value().value().equalsInt(0))
				{
					val handlerTuple: A_Tuple = continuation.frameAt(2)
					assert(handlerTuple.isTuple)
					handlerTuple.forEach { handler ->
						if (exceptionValue.isInstanceOf(
								handler.kind().argsTupleType.typeAtIndex(1)))
						{
							// Mark this frame: we don't want it to handle an
							// exception raised from within one of its handlers.
							if (debugL2)
							{
								log(
									loggerDebugPrimitives,
									Level.FINER,
									"{0}Raised (->handler) at depth {1}",
									debugModeString,
									depth)
							}
							failureVariable.value().setValueNoCheck(
								E_HANDLER_SENTINEL.numericCode())
							// Run the handler.  Since the Java stack has been
							// fully reified, simply jump into the chunk.  Note
							// that the argsBuffer was already set up with just
							// the exceptionValue.
							setReifiedContinuation(continuation)
							function = handler
							chunk = handler.code().startingChunk
							offset = 0 // Invocation
							levelOneStepper.wipeRegisters()
							returnNow = false
							setLatestResult(null)
							return CONTINUATION_CHANGED
						}
					}
				}
			}
			continuation = continuation.caller() as AvailObject
			depth++
		}
		// If no handler was found, then return the unhandled exception.
		return primitiveFailure(exceptionValue)
	}

	/**
	 * Update the guard [A_Variable] with the new marker [number][A_Number]. The
	 * variable is a failure variable of a primitive function for
	 * [P_CatchException], and is used to track exception/unwind states.
	 *
	 * @param guardVariable
	 *   The primitive failure variable to update.
	 * @param marker
	 *   An exception handling state marker (integer).
	 * @return
	 *   The [success&#32;state][Result].
	 */
	fun markGuardVariable(
		guardVariable: A_Variable,
		marker: A_Number
	): Result
	{
		// Only allow certain state transitions.
		val oldState = guardVariable.value().extractInt
		if (marker.equals(E_HANDLER_SENTINEL.numericCode())
			&& oldState != 0)
		{
			return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME)
		}
		if (marker.equals(E_UNWIND_SENTINEL.numericCode())
			&& oldState != E_HANDLER_SENTINEL.nativeCode())
		{
			return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME)
		}
		// Mark this frame.  Depending on the marker, we don't want it to handle
		// exceptions or unwinds anymore.
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}Marked guard var {1}",
				debugModeString,
				marker)
		}
		guardVariable.setValueNoCheck(marker)
		return primitiveSuccess(nil)
	}

	/**
	 * Assume the entire stack has been reified.  Scan the stack of
	 * continuations until one is found for a function whose code specifies
	 * [P_CatchException]. Write the specified marker into its primitive failure
	 * variable to indicate the current exception handling state.
	 *
	 * @param marker
	 *   An exception handling state marker.
	 * @return
	 *   The [success&#32;state][Result].
	 */
	fun markNearestGuard(marker: A_Number): Result
	{
		var continuation: A_Continuation = getReifiedContinuation()!!
		var depth = 0
		while (continuation.notNil)
		{
			val code = continuation.function().code()
			if (code.codePrimitive() == P_CatchException)
			{
				assert(code.numArgs() == 3)
				val failureVariable: A_Variable = continuation.frameAt(4)
				val guardVariable: A_Variable = failureVariable.value()
				val oldState = guardVariable.value().extractInt
				// Only allow certain state transitions.
				when
				{
					marker.equals(E_HANDLER_SENTINEL.numericCode())
						&& oldState != 0 ->
						return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME)
					marker.equals(E_UNWIND_SENTINEL.numericCode())
						&& oldState != E_HANDLER_SENTINEL.nativeCode() ->
						return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME)
				}
				// Mark this frame: we don't want it to handle exceptions
				// anymore.
				guardVariable.setValueNoCheck(marker)
				if (debugL2)
				{
					log(
						loggerDebugL2,
						Level.FINER,
						"{0}Marked {1} at depth {2}",
						debugModeString,
						marker,
						depth)
				}
				return primitiveSuccess(nil)
			}
			continuation = continuation.caller()
			depth++
		}
		return primitiveFailure(E_NO_HANDLER_FRAME)
	}

	/**
	 * Check if the current chunk is still valid.  If so, return `true`.
	 * Otherwise, set the current chunk to the [L2Chunk.unoptimizedChunk], set
	 * the offset to the specified offset within that chunk, and return `false`.
	 *
	 * If there is a debugger active, always treat an optimized chunk as
	 * invalid, allowing precise control for stepping.  Note that this doesn't
	 * have an effect when returning from JVM frames, as no invalidation can
	 * happen inside the JVM call.  If a reification took place and we're now
	 * reentering the continuation either to resume from an interrupt or to
	 * return into a frame, this validity check will force L1 stepping, but just
	 * for that frame (and any other reified frame being returned into).
	 *
	 * @param offsetInDefaultChunkIfInvalid
	 *   The offset within the [L2Chunk.unoptimizedChunk] to resume execution at
	 *   if the current chunk is found to be invalid.
	 * @return
	 *   Whether the current chunk is still [valid][L2Chunk.isValid] (i.e., has
	 *   not been invalidated by a code change).
	 */
	@ReferencedInGeneratedCode
	fun checkValidity(
		offsetInDefaultChunkIfInvalid: Int
	): Boolean = when
	{
		chunk!!.isValid && debugger == null -> true
		else ->
		{
			chunk = L2Chunk.unoptimizedChunk
			offset = offsetInDefaultChunkIfInvalid
			false
		}
	}

	/** An indication that a reification action is running. */
	var isReifying = false

	/**
	 * Obtain an appropriate [StackReifier] for restarting the specified
	 * [continuation][A_Continuation].
	 *
	 * @param continuation
	 *   The [A_Continuation] to restart.
	 * @return
	 *   The requested `StackReifier`.
	 */
	@ReferencedInGeneratedCode
	fun reifierToRestart(
		continuation: A_Continuation
	): StackReifier
	{
		isReifying = true
		return StackReifier(
			false,
			StatisticCategory.ABANDON_BEFORE_RESTART_IN_L2.statistic
		) {
			val whichFunction = continuation.function()
			val numArgs = whichFunction.code().numArgs()
			argsBuffer.clear()
			(1 .. numArgs).forEach {
				argsBuffer.add(continuation.frameAt(it))
			}
			setReifiedContinuation(continuation.caller())
			function = whichFunction
			chunk = continuation.levelTwoChunk()
			offset = continuation.levelTwoOffset()
			returnNow = false
			setLatestResult(null)
			isReifying = false
		}
	}

	/**
	 * Answer a [StackReifier] which can be used for reifying the current stack
	 * by returning it out to the [run] loop.  When it reaches there, a lambda
	 * embedded in this reifier will run, performing an action suitable to the
	 * provided flags.
	 *
	 * @param actuallyReify
	 *   Whether to actually record the stack frames as [A_Continuation]s.  If
	 *   `false`, this state will simply be discarded.
	 * @param processInterrupt
	 *   Whether a pending interrupt should be processed after reification.
	 * @param statistic
	 *   A [Statistic] to record when a reification happens.
	 * @return
	 *   The new [StackReifier].
	 */
	@ReferencedInGeneratedCode
	fun reify(
		actuallyReify: Boolean,
		processInterrupt: Boolean,
		statistic: Statistic
	): StackReifier = when
	{
		processInterrupt ->
		{
			// Reify-and-interrupt.
			isReifying = true
			StackReifier(actuallyReify, statistic) {
				returnNow = false
				isReifying = false
				processInterrupt(getReifiedContinuation()!!)
			}
		}
		else ->
		{
			// Capture the interpreter's state, reify the frames, and as an
			// after-reification action, restore the interpreter's state.
			val savedFunction = function!!
			val newReturnNow = returnNow
			val newReturnValue = latestResultOrNull()

			// Reify-and-continue.  The current frame is also reified.
			isReifying = true
			StackReifier(actuallyReify, statistic) {
				val continuation = getReifiedContinuation()!!
				function = savedFunction
				chunk = continuation.levelTwoChunk()
				offset = continuation.levelTwoOffset()
				returnNow = newReturnNow
				setLatestResult(newReturnValue)
				// Return into the Interpreter's run loop.
				isReifying = false
			}
		}
	}

	/**
	 * Obtain an appropriate [StackReifier] for restarting the specified
	 * [continuation][A_Continuation] with the given arguments.
	 *
	 * @param continuation
	 *   The continuation to restart.
	 * @param arguments
	 *   The arguments with which to restart the continuation.
	 * @return
	 *   The requested `StackReifier`.
	 */
	@ReferencedInGeneratedCode
	fun reifierToRestartWithArguments(
		continuation: A_Continuation,
		arguments: Array<AvailObject>
	): StackReifier
	{
		isReifying = true
		return StackReifier(
			false,
			StatisticCategory.ABANDON_BEFORE_RESTART_IN_L2.statistic
		) {
			val whichFunction = continuation.function()
			val numArgs = whichFunction.code().numArgs()
			assert(arguments.size == numArgs)
			argsBuffer.clear()
			argsBuffer.addAll(arguments)
			setReifiedContinuation(continuation.caller())
			function = whichFunction
			chunk = continuation.levelTwoChunk()
			offset = continuation.levelTwoOffset()
			returnNow = false
			setLatestResult(null)
			isReifying = false
		}
	}

	/**
	 * Prepare to run a [function][A_Function] invocation with zero arguments.
	 *
	 * @param calledFunction
	 *   The function to call.
	 * @return
	 *   The calling [A_Function]
	 */
	@ReferencedInGeneratedCode
	fun preinvoke0(
		calledFunction: A_Function
	): AvailObject
	{
		val savedFunction = function!! as AvailObject
		argsBuffer.clear()
		function = calledFunction
		chunk = calledFunction.code().startingChunk
		offset = 0
		adjustUnreifiedCallDepthBy(1)
		return savedFunction
	}

	/**
	 * Prepare to run a [function][A_Function] invocation with one argument.
	 *
	 * @param calledFunction
	 *   The function to call.
	 * @param arg1
	 *   The sole argument to the function.
	 * @return
	 *   The calling [A_Function]
	 */
	@ReferencedInGeneratedCode
	fun preinvoke1(
		calledFunction: A_Function,
		arg1: AvailObject
	): AvailObject
	{
		val savedFunction = function!! as AvailObject
		argsBuffer.clear()
		argsBuffer.add(arg1)
		function = calledFunction
		chunk = calledFunction.code().startingChunk
		offset = 0
		adjustUnreifiedCallDepthBy(1)
		return savedFunction
	}

	/**
	 * Prepare to run a [function][A_Function] invocation with two arguments.
	 *
	 * @param calledFunction
	 *   The function to call.
	 * @param arg1
	 *   The first argument to the function.
	 * @param arg2
	 *   The second argument to the function.
	 * @return
	 *   The calling [A_Function]
	 */
	@ReferencedInGeneratedCode
	fun preinvoke2(
		calledFunction: A_Function,
		arg1: AvailObject,
		arg2: AvailObject
	): AvailObject
	{
		val savedFunction = function!! as AvailObject
		argsBuffer.clear()
		argsBuffer.add(arg1)
		argsBuffer.add(arg2)
		function = calledFunction
		chunk = calledFunction.code().startingChunk
		offset = 0
		adjustUnreifiedCallDepthBy(1)
		return savedFunction
	}

	/**
	 * Prepare to run a [function][A_Function] invocation with three arguments.
	 *
	 * @param calledFunction
	 *   The function to call.
	 * @param arg1
	 *   The first argument to the function.
	 * @param arg2
	 *   The second argument to the function.
	 * @param arg3
	 *   The third argument to the function.
	 * @return
	 *   The calling [A_Function]
	 */
	@ReferencedInGeneratedCode
	fun preinvoke3(
		calledFunction: A_Function,
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): AvailObject
	{
		val savedFunction = function!! as AvailObject
		argsBuffer.clear()
		argsBuffer.add(arg1)
		argsBuffer.add(arg2)
		argsBuffer.add(arg3)
		function = calledFunction
		chunk = calledFunction.code().startingChunk
		offset = 0
		adjustUnreifiedCallDepthBy(1)
		return savedFunction
	}

	/**
	 * Prepare to run a [function][A_Function] invocation with an array of
	 * arguments.
	 *
	 * @param calledFunction
	 *   The function to call.
	 * @param args
	 *   The [arguments][AvailObject] to the function.
	 * @return
	 *   The calling [A_Function]
	 */
	@ReferencedInGeneratedCode
	fun preinvoke(
		calledFunction: A_Function,
		args: Array<AvailObject>
	): AvailObject
	{
		val savedFunction = function!! as AvailObject
		argsBuffer.clear()
		argsBuffer.addAll(args)
		function = calledFunction
		chunk = calledFunction.code().startingChunk
		offset = 0
		adjustUnreifiedCallDepthBy(1)
		return savedFunction
	}

	/**
	 * Do what's necessary after a function invocation, leaving just the given
	 * [StackReifier] on the stack.
	 *
	 * @param callingChunk
	 *   The chunk to return into.
	 * @param callingFunction
	 *   The function to return into.
	 * @param reifier
	 *   The [StackReifier] produced by the call, if any.
	 * @return
	 *   The given [StackReifier], if any.
	 */
	@ReferencedInGeneratedCode
	fun postinvoke(
		callingChunk: L2Chunk,
		callingFunction: A_Function,
		reifier: StackReifier?
	): StackReifier?
	{
		chunk = callingChunk
		function = callingFunction
		returnNow = false
		assert(!exitNow)
		adjustUnreifiedCallDepthBy(-1)
		return reifier
	}

	/**
	 * Prepare the interpreter to execute the given [A_Function] with the
	 * arguments provided in [argsBuffer].
	 *
	 * @param aFunction
	 *   The function to begin executing.
	 * @return
	 *   Either `null` to indicate the function returned normally, leaving its
	 *   result in the interpreter's latestResult field, or a [StackReifier]
	 *   used to indicate the stack is being unwound (and the Avail function is
	 *   *not* returning).
	 */
	fun invokeFunction(aFunction: A_Function): StackReifier?
	{
		assert(!exitNow)
		function = aFunction
		val code = aFunction.code()
		assert(code.numArgs() == argsBuffer.size)
		chunk = code.startingChunk
		assert(chunk!!.isValid)
		offset = 0
		returnNow = false
		adjustUnreifiedCallDepthBy(1)
		return try
		{
			runChunk()
		}
		finally
		{
			adjustUnreifiedCallDepthBy(-1)
		}
	}

	/**
	 * Run the interpreter until it completes the fiber, is suspended, or is
	 * interrupted, perhaps by exceeding its time-slice.
	 */
	fun run()
	{
		assert(unreifiedCallDepth() == 0)
		assert(fiber !== null)
		assert(!exitNow)
		assert(!returnNow)
		nanosToExclude = 0L
		startTick = runtime.clock.get()
		if (debugL2)
		{
			debugModeString = "Fib=" + fiber!!.uniqueId + " "
			log(
				loggerDebugPrimitives,
				Level.FINER,
				"\n{0}Run: ({1})",
				debugModeString,
				fiber!!.fiberName)
		}
		while (true)
		{
			// Each time we're at the base unreified frame, make sure to
			// deoptimize the continuation.
			// Run the chunk to completion (dealing with reification).
			// The chunk will do its own invalidation checks and off-ramp
			// to L1 if needed.
			val calledFunction = function!!
			val reifier = runChunk()
			assert(unreifiedCallDepth() == 0)
			returningFunction = calledFunction
			if (reifier !== null)
			{
				// Reification has been requested, and the exception has already
				// collected all the reification actions.
				if (reifier.actuallyReify())
				{
					reifier.runActions(this)
				}
				reifier.recordCompletedReification(interpreterIndex)
				chunk = null // The postReificationAction should set this up.
				reifier.postReificationAction()
				if (exitNow)
				{
					// The fiber has been dealt with. Exit the interpreter loop.
					assert(fiber === null)
					if (debugL2)
					{
						log(
							loggerDebugL2,
							Level.FINER,
							"{0}Exit1 run\n",
							debugModeString)
					}
					return
				}
				if (!returnNow)
				{
					continue
				}
				// Fall through to accomplish the return.
			}
			assert(returnNow)
			assert(latestResult !== null)
			returnNow = false
			if (getReifiedContinuation()!!.isNil)
			{
				// The reified stack is empty, too.  We must have returned from
				// the outermost frame.  The fiber runner will deal with it.
				terminateFiber(getLatestResult())
				exitNow = true
				if (debugL2)
				{
					log(
						loggerDebugL2,
						Level.FINER,
						"{0}Exit2 run and set exitNow (fall off " +
							"Interpreter.run)\n",
						debugModeString)
				}
				return
			}
			// Resume the top reified frame.  It should be at an on-ramp that
			// expects nothing of the current registers, but is able to create
			// them and explode the current reified continuation into them
			// (popping the continuation as it does so).
			val frame: A_Continuation? = getReifiedContinuation()
			function = frame!!.function()
			chunk = frame.levelTwoChunk()
			offset = frame.levelTwoOffset()
		}
	}

	/**
	 * Run the current L2Chunk to completion.  Note that a reification request
	 * may cut this short.  Also note that this interpreter indicates the offset
	 * at which to start executing.  For an initial invocation, the argsBuffer
	 * will have been set up for the call.  For a return into this continuation,
	 * the offset will refer to code that will rebuild the register set from the
	 * top reified continuation, using the [latestResult]. For resuming the
	 * continuation, the offset will point to code that also rebuilds the
	 * register set from the top reified continuation, but it won't expect a
	 * return value.  These re-entry points should perform validity checks on
	 * the chunk, allowing an orderly off-ramp into the
	 * [L2Chunk.unoptimizedChunk] (which simply interprets the L1 nybblecodes).
	 *
	 * @return
	 *   `null` if returning normally, otherwise a [StackReifier] to effect
	 *   reification.
	 */
	@ReferencedInGeneratedCode
	fun runChunk(): StackReifier?
	{
		assert(!exitNow)
		var reifier: StackReifier? = null
		while (!returnNow && !exitNow && reifier === null)
		{
			val currentChunk = chunk!!
			currentChunk.beforeRunChunk(offset)
			reifier = currentChunk.executableChunk.runChunk(this, offset)
		}
		return reifier
	}

	/** Present the name in the debugger. */
	@Suppress("unused")
	fun nameForDebugger() = toString()

	override fun toString(): String
	{
		return buildString {
			append(this@Interpreter.javaClass.simpleName)
			append(" #$interpreterIndex")
			if (fiber === null)
			{
				append(" [«unbound»]")
			}
			else
			{
				append(formatString(" [%s]", fiber!!.fiberName))
				if (getReifiedContinuation() === null)
				{
					append(formatString("%n\t«null stack»"))
				}
				else if (getReifiedContinuation()!!.isNil)
				{
					append(formatString("%n\t«empty call stack»"))
				}
				append("\n\n")
			}
		}
	}

	/**
	 * Handle a return value that doesn't satisfy its expected type out-of-line.
	 * This shrinks the control flow graph in L2, which is not just a time
	 * saving during creation and memory saving ongoing, but may also increase
	 * HotSpot's effectiveness.
	 *
	 * This [Interpreter]'s [function] and [returningFunction] are expected to
	 * contain the calling and returning functions, respectively.
	 *
	 * Note that if the handler ([HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE])
	 * asks to reify, this method will construct a continuation representing the
	 * Avail calling function.  The continuation frame can't be resumed, so it
	 * will use the [L2Chunk.unoptimizedChunk]'s [ChunkEntryPoint.UNREACHABLE].
	 *
	 * @param returnedValueOrNil
	 *   The value that was actually returned, which may be [nil].
	 * @param expectedReturnType
	 *   The [A_Type] of value that was expected to be returned.
	 * @param pc
	 *   The level one [A_Continuation.pc] to use in a new continuation, if
	 *   reification happens inside the error handler.
	 * @param stackp
	 *   The level one parameter stack pointer to use in a new continuation, if
	 *   reification happens inside the error handler.
	 * @param slots
	 *   Values that will populate a continuation's frame slots if reification
	 *   happens inside the error handler.
	 * @return
	 *   Either `null` or a [StackReifier] that the calling code should simply
	 *   return.  This method creates a stack frame on behalf of the Avail
	 *   calling function if needed.
	 */
	@ReferencedInGeneratedCode
	fun reportWrongReturnType(
		returnedValueOrNil: A_BasicObject,
		expectedReturnType: A_Type,
		pc: Int,
		stackp: Int,
		vararg slots: A_BasicObject
	): StackReifier
	{
		val returner = returningFunction!!
		val caller = function!!
		val wrappedReturnValue = newVariableWithContentType(Types.ANY.o)
		if (returnedValueOrNil.notNil)
		{
			wrappedReturnValue.setValueNoCheck(returnedValueOrNil)
		}
		argsBuffer.clear()
		argsBuffer.add(returner as AvailObject)
		argsBuffer.add(expectedReturnType as AvailObject)
		argsBuffer.add(wrappedReturnValue)
		val reifier = invokeFunction(
			runtime.resultDisagreedWithExpectedTypeFunction())
		assert(reifier !== null) { "return type handler must not return." }
		// Assemble a (non-resumable) stack frame for the reifier.
		reifier!!.pushAction {
			val continuation = createContinuationWithFrame(
				caller,
				it.getReifiedContinuation() ?: nil,
				createRegisterDump(JVMChunk.noObjects, JVMChunk.noLongs),
				pc,
				stackp,
				L2Chunk.unoptimizedChunk,
				ChunkEntryPoint.UNREACHABLE.offsetInDefaultChunk,
				listOf(*slots),
				0)
			setReifiedContinuation(continuation)
		}
		return reifier
	}

	/**
	 * Handle having attempted to read from a variable that does not currently
	 * have a value. This shrinks the control flow graph in L2, which is not
	 * just a time saving during creation and memory saving ongoing, but may
	 * also increase HotSpot's effectiveness.
	 *
	 * This [Interpreter]'s [function] is expected to contain the current
	 * function.
	 *
	 * Note that if the handler ([HookType.READ_UNASSIGNED_VARIABLE]) asks to
	 * reify, this method will construct a continuation representing the current
	 * function.  The continuation frame can't be resumed, so it will use the
	 * [L2Chunk.unoptimizedChunk]'s [ChunkEntryPoint.UNREACHABLE].
	 *
	 * @param pc
	 *   The level one [A_Continuation.pc] to use in a new continuation, if
	 *   reification happens inside the error handler.
	 * @param stackp
	 *   The level one parameter stack pointer to use in a new continuation, if
	 *   reification happens inside the error handler.
	 * @param slots
	 *   Values that will populate a continuation's frame slots if reification
	 *   happens inside the error handler.
	 * @return
	 *   Either `null` or a [StackReifier] that the calling code should simply
	 *   return.  This method creates a stack frame on behalf of the current
	 *   executing function if needed.
	 */
	@ReferencedInGeneratedCode
	fun reportUnassignedVariableRead(
		pc: Int,
		stackp: Int,
		vararg slots: A_BasicObject
	): StackReifier
	{
		val currentFunction = function!!
		argsBuffer.clear()
		val reifier = invokeFunction(runtime.unassignedVariableReadFunction())
		assert(reifier !== null) {
			"unassigned-variable-read handler must not return."
		}
		// Assemble a (non-resumable) stack frame for the reifier.
		reifier!!.pushAction {
			val continuation = createContinuationWithFrame(
				currentFunction,
				it.getReifiedContinuation() ?: nil,
				createRegisterDump(JVMChunk.noObjects, JVMChunk.noLongs),
				pc,
				stackp,
				L2Chunk.unoptimizedChunk,
				ChunkEntryPoint.UNREACHABLE.offsetInDefaultChunk,
				listOf(*slots),
				0)
			setReifiedContinuation(continuation)
		}
		return reifier
	}

	/**
	 * Record the fact that a statement of the given module just took some
	 * number of nanoseconds to run.
	 *
	 * @param sample
	 *   The number of nanoseconds.
	 * @param module
	 *   The module containing the top-level statement that ran.
	 */
	fun recordTopStatementEvaluation(
		sample: Double,
		module: A_Module)
	{
		var statistic: Statistic
		synchronized(topStatementEvaluationStats) {
			statistic = topStatementEvaluationStats.computeIfAbsent(
				module.moduleName
			) {
				Statistic(TOP_LEVEL_STATEMENTS, it.asNativeString())
			}
		}
		statistic.record(sample, interpreterIndex)
	}

	companion object
	{
		/** Whether to print detailed Level One debug information. */
		@Volatile
		var debugL1 = false

		/** Whether to print detailed Level Two debug information. */
		@Volatile
		var debugL2 = false

		/** Whether to print detailed Primitive debug information. */
		@Volatile
		var debugPrimitives = false

		/**
		 * Whether to print detailed debug information related to compiler/lexer
		 * work unit tracking.
		 */
		@Volatile
		var debugWorkUnits = false

		/**
		 * Whether to divert logging into fibers' [A_Fiber.debugLog], which is
		 * simply a length-bounded StringBuilder.  This is *by far* the fastest
		 * available way to log, although message pattern substitution is still
		 * unnecessarily slow.
		 *
		 * Note that this only has an effect if one of the above debug flags is
		 * set.
		 */
		private const val debugIntoFiberDebugLog = true

		/**
		 * Whether to print debug information related to a specific problem
		 * being debugged with a custom VM.  This is a convenience flag and will
		 * be inaccessible in a production VM.
		 */
		@Volatile
		var debugCustom = false

		/** A [logger][Logger]. */
		private val mainLogger = Logger.getLogger(
			Interpreter::class.java.canonicalName)

		/** A [logger][Logger]. */
		val loggerDebugL1: Logger = Logger.getLogger(
			Interpreter::class.java.canonicalName + ".debugL1")

		/** A [logger][Logger]. */
		val loggerDebugL2: Logger = Logger.getLogger(
			Interpreter::class.java.canonicalName + ".debugL2")

		/** A [logger][Logger]. */
		val loggerDebugJVM: Logger = Logger.getLogger(
			Interpreter::class.java.canonicalName + ".debugJVM")

		/** A [logger][Logger]. */
		private val loggerDebugPrimitives = Logger.getLogger(
			Interpreter::class.java.canonicalName + ".debugPrimitives")

		/**
		 * The approximate maximum number of bytes to log per fiber before
		 * throwing away the earliest 25%.
		 */
		private const val maxFiberLogLength = 50000

		/**
		 * Set the current logging level for interpreters.
		 *
		 * @param level
		 *   The new logging [Level].
		 */
		fun setLoggerLevel(level: Level)
		{
			mainLogger.level = level
			loggerDebugL1.level = level
			loggerDebugL2.level = level
			loggerDebugJVM.level = level
			loggerDebugPrimitives.level = level
		}

		/**
		 * Log a message.
		 *
		 * @param logger
		 *   The logger on which to log.
		 * @param level
		 *   The verbosity level at which to log.
		 * @param message
		 *   The message pattern to log.
		 * @param arguments
		 *   The arguments to fill into the message pattern.
		 */
		fun log(
			logger: Logger,
			level: Level,
			message: String,
			vararg arguments: Any?)
		{
			if (logger.isLoggable(level))
			{
				log(
					if (AvailThread.currentOrNull() !== null) current().fiber
					else null,
					logger,
					level,
					message,
					*arguments)
			}
		}

		/**
		 * Log a message.
		 *
		 * @param affectedFiber
		 *   The affected fiber or null.
		 * @param logger
		 *   The logger on which to log.
		 * @param level
		 *   The verbosity level at which to log.
		 * @param message
		 *   The message pattern to log.
		 * @param arguments
		 *   The arguments to fill into the message pattern.
		 */
		fun log(
			affectedFiber: A_Fiber?,
			logger: Logger,
			level: Level?,
			message: String,
			vararg arguments: Any?)
		{
			if (logger.isLoggable(level))
			{
				val interpreter = currentOrNull()
				val runningFiber = interpreter?.fiberOrNull()
				@Suppress("ConstantConditionIf")
				if (debugIntoFiberDebugLog)
				{
					// Write into a StringBuilder in each fiber's debugLog().
					if (runningFiber !== null)
					{
						// Log to the fiber.
						val log = runningFiber.debugLog
						if (interpreter.isReifying)
						{
							log.append("R! ")
						}
						log.tab(interpreter.unreifiedCallDepth)
						if (log.length > maxFiberLogLength)
						{
							log.delete(
								0, log.length - (maxFiberLogLength shr 2) * 3)
						}
						// Abbreviate potentially long arguments.
						val tidyArguments = arguments.map {
							when
							{
								it !is AvailObject -> it
								it.typeTag == TypeTag.OBJECT_TAG ->
									"(some object)"
								!it.isTuple -> it
								it.isString && it.tupleSize > 200 ->
									it.copyStringFromToCanDestroy(
										1, 200, false
									).asNativeString() + "..."
								!it.isString && it.tupleSize > 20 ->
									it.copyTupleFromToCanDestroy(
										1, 20, false
									) + "..."
								else -> it
							}
						}
						log.append(
							MessageFormat.format(
								message, *tidyArguments.toTypedArray()))
						log.append('\n')
					}
					// Ignore the bit of logging not tied to a specific fiber.
					return
				}
				val builder = StringBuilder()
				builder.append(
					when
					{
						runningFiber !== null ->
							String.format("%6d ", runningFiber.uniqueId)
						else -> "?????? "
					})
				builder.append("→ ")
				builder.append(
					when
					{
						affectedFiber !== null ->
							String.format("%6d ", affectedFiber.uniqueId)
						else -> "?????? "
					})
				logger.log(level, builder.toString() + message, arguments)
			}
		}

		/**
		 * If [JVMTranslator.callTraceL2AfterEveryInstruction] was true during
		 * code generation, this method is invoked just prior to each L2
		 * instruction.
		 *
		 * @param executableChunk
		 *   The [ExecutableChunk] being executed.
		 * @param offset
		 *   The current L2 offset.
		 * @param description
		 *   A one-line textual description of this instruction.
		 * @param firstReadOperandValue
		 *   The value of the first read operand.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun traceL2(
			executableChunk: ExecutableChunk,
			offset: Int,
			description: String,
			firstReadOperandValue: Any)
		{
			if (debugL2)
			{
				if (mainLogger.isLoggable(Level.SEVERE))
				{
					val str = ("L2 = "
						+ offset
						+ " of "
						+ executableChunk.name()
						+ " "
						+ description
						+ " <- " + firstReadOperandValue)
					val fiber = current().fiberOrNull()
					log(
						fiber,
						mainLogger,
						// Force logging when the switches are enabled.
						Level.SEVERE,
						"{0}",
						str)
				}
			}
		}

		/**
		 * The [CheckedMethod] referring to the static method [traceL2].
		 */
		val traceL2Method = staticMethod(
			Interpreter::class.java,
			::traceL2.name,
			Void.TYPE,
			ExecutableChunk::class.java,
			Int::class.javaPrimitiveType!!,
			String::class.java,
			Any::class.java)

		/**
		 * Answer the Avail interpreter associated with the
		 * [Thread.currentThread].  If this thread is not an [AvailThread], then
		 * fail.
		 *
		 * @return
		 *   The current Level Two interpreter.
		 */
		fun current(): Interpreter = AvailThread.current().interpreter

		/**
		 * Answer the unique [interpreterIndex] of the Avail interpreter
		 * associated with the [current][Thread.currentThread] thread, if any.
		 * If this thread is not an [AvailThread], answer `0`.
		 *
		 * @return
		 *   The current Avail `Interpreter`'s unique index, or zero.
		 */
		fun currentIndexOrZero(): Int
		{
			val thread = Thread.currentThread()
			if (thread is AvailThread)
			{
				return thread.interpreter.interpreterIndex
			}
			// If we're running a task in the fork/join pool, use its index
			// mod the number of interpreter threads (to keep it in range).
			if (thread is ForkJoinWorkerThread)
			{
				return thread.poolIndex % maxInterpreters
			}
			return 0
		}

		/**
		 * Answer the Avail interpreter associated with the
		 * [current][Thread.currentThread] thread.  If this thread is not an
		 * [AvailThread], then answer `null`.
		 *
		 * @return
		 *   The current Avail `Interpreter`, or `null` if the current [Thread]
		 *   is not an [AvailThread].
		 */
		fun currentOrNull(): Interpreter? =
			AvailThread.currentOrNull()?.interpreter

		/** Access the [callerIsReified] method. */
		val callerIsReifiedMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::callerIsReified.name,
			Boolean::class.javaPrimitiveType!!)

		/** The [CheckedField] for [runtime]. */
		val runtimeField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::runtime.name,
			AvailRuntime::class.java)

		/** Access the [setLatestResult] method. */
		var setLatestResultMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::setLatestResult.name,
			Void.TYPE,
			A_BasicObject::class.java)

		/** Access the [getLatestResult] method. */
		var getLatestResultMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::getLatestResult.name,
			AvailObject::class.java)

		/** The [CheckedField] for the field argsBuffer. */
		val interpreterReturningFunctionField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::returningFunction.name,
			A_Function::class.java)

		/** Access the [returnNow] field. */
		val returnNowField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::returnNow.name,
			Boolean::class.javaPrimitiveType!!)

		/** The method [beforeAttemptPrimitive]. */
		var beforeAttemptPrimitiveMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::beforeAttemptPrimitive.name,
			Long::class.javaPrimitiveType!!,
			Primitive::class.java)

		/** The method [afterAttemptPrimitive]. */
		var afterAttemptPrimitiveMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::afterAttemptPrimitive.name,
			Result::class.java,
			Primitive::class.java,
			Long::class.javaPrimitiveType!!,
			Result::class.java)

		/** Access the [getReifiedContinuation] method. */
		val getReifiedContinuationMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::getReifiedContinuation.name,
			AvailObject::class.java)

		/** Access the [setReifiedContinuation] method. */
		val setReifiedContinuationMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::setReifiedContinuation.name,
			Void.TYPE,
			A_Continuation::class.java)

		/** Access the [popContinuation] method. */
		var popContinuationMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::popContinuation.name,
			Void.TYPE)

		/**
		 * The maximum depth of the Java call stack, measured in unreified
		 * chunks.
		 */
		private const val maxUnreifiedCallDepth = 50

		/** The [CheckedField] for the field [function]. */
		val interpreterFunctionField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::function.name,
			A_Function::class.java)

		/** Access to the field [chunk]. */
		var chunkField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::chunk.name,
			L2Chunk::class.java)

		/** The [CheckedField] for [offset]. */
		val offsetField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::offset.name,
			Int::class.javaPrimitiveType!!)

		/** The [CheckedField] for the field [argsBuffer]. */
		val argsBufferField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::argsBuffer.name,
			MutableList::class.java)

		/** The [CheckedField] for [levelOneStepper]. */
		val levelOneStepperField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::levelOneStepper.name,
			L1InstructionStepper::class.java)

		/**
		 * The size of a [fiber][FiberDescriptor]'s time slice, in ticks.
		 */
		private const val timeSliceTicks = 20

		/** Access the [isInterruptRequested] method. */
		val isInterruptRequestedMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::isInterruptRequested.name,
			Boolean::class.javaPrimitiveType!!)

		/** A method to access [checkValidity]. */
		val checkValidityMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::checkValidity.name,
			Boolean::class.javaPrimitiveType!!,
			Int::class.javaPrimitiveType!!)

		/**
		 * The [CheckedMethod] for [reifierToRestart].
		 */
		val reifierToRestartMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::reifierToRestart.name,
			StackReifier::class.java,
			A_Continuation::class.java)

		/** The [CheckedMethod] for [reify]. */
		val reifyMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::reify.name,
			StackReifier::class.java,
			Boolean::class.javaPrimitiveType!!,
			Boolean::class.javaPrimitiveType!!,
			Statistic::class.java)

		/**
		 * The [CheckedMethod] for [reifierToRestartWithArguments].
		 */
		val reifierToRestartWithArgumentsMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::reifierToRestartWithArguments.name,
			StackReifier::class.java,
			A_Continuation::class.java,
			Array<AvailObject>::class.java)

		/** Access the [preinvoke0] method. */
		var preinvoke0Method = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke0.name,
			AvailObject::class.java,
			A_Function::class.java)

		/** Access the [preinvoke1] method. */
		var preinvoke1Method = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke1.name,
			AvailObject::class.java,
			A_Function::class.java,
			AvailObject::class.java)

		/**
		 * Access the [preinvoke2] method.
		 */
		var preinvoke2Method = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke2.name,
			AvailObject::class.java,
			A_Function::class.java,
			AvailObject::class.java,
			AvailObject::class.java)

		/**
		 * Access the [preinvoke3] method.
		 */
		var preinvoke3Method = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke3.name,
			AvailObject::class.java,
			A_Function::class.java,
			AvailObject::class.java,
			AvailObject::class.java,
			AvailObject::class.java)

		/**
		 * Access the [preinvoke] method.
		 */
		var preinvokeMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke.name,
			AvailObject::class.java,
			A_Function::class.java,
			Array<AvailObject>::class.java)

		/**
		 * Access the [postinvoke] method.
		 */
		var postinvokeMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::postinvoke.name,
			StackReifier::class.java,
			L2Chunk::class.java,
			A_Function::class.java,
			StackReifier::class.java)

		/**
		 * Access the [runChunk] method.
		 */
		var interpreterRunChunkMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::runChunk.name,
			StackReifier::class.java)

		/**
		 * The [CheckedMethod] for invoking [attemptThePrimitive].
		 */
		val attemptThePrimitiveMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::attemptThePrimitive.name,
			StackReifier::class.java,
			A_Function::class.java,
			Primitive::class.java)

		/**
		 * The [CheckedMethod] for [attemptInlinePrimitive].
		 */
		val attemptTheInlinePrimitiveMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::attemptInlinePrimitive.name,
			StackReifier::class.java,
			A_Function::class.java,
			Primitive::class.java)

		/**
		 * The [CheckedMethod] for [attemptNonInlinePrimitive].
		 */
		val attemptTheNonInlinePrimitiveMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::attemptNonInlinePrimitive.name,
			StackReifier::class.java,
			A_Function::class.java,
			Primitive::class.java)

		/**
		 * Access the [reportWrongReturnType] method.
		 */
		var reportWrongReturnTypeMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::reportWrongReturnType.name,
			StackReifier::class.java,
			A_BasicObject::class.java,
			A_Type::class.java,
			Int::class.javaPrimitiveType!!,
			Int::class.javaPrimitiveType!!,
			Array<A_BasicObject>::class.java)

		/**
		 * Access the [reportUnassignedVariableRead] method.
		 */
		var reportUnassignedVariableReadMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::reportUnassignedVariableRead.name,
			StackReifier::class.java,
			Int::class.javaPrimitiveType!!,
			Int::class.javaPrimitiveType!!,
			Array<A_BasicObject>::class.java)

		/**
		 * Top-level statement evaluation statistics, keyed by module name.
		 */
		private val topStatementEvaluationStats =
			mutableMapOf<A_String, Statistic>()

		/**
		 * Answer the bootstrapped [assignment&#32;function][P_SetValue] used to
		 * restart implicitly observed assignments.
		 *
		 * @return
		 *   The assignment function.
		 */
		fun assignmentFunction(): A_Function =
			VariableDescriptor.bootstrapAssignmentFunction
	}
}
