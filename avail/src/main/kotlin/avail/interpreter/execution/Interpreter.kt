/*
 * Interpreter.kt
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
package avail.interpreter.execution

import avail.AvailDebuggerModel
import avail.AvailRuntime
import avail.AvailRuntime.HookType
import avail.AvailRuntimeConfiguration.maxInterpreters
import avail.AvailRuntimeSupport.captureNanos
import avail.AvailTask
import avail.AvailThread
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.clearTraceFlag
import avail.descriptor.fiber.A_Fiber.Companion.continuation
import avail.descriptor.fiber.A_Fiber.Companion.debugLog
import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.fiberName
import avail.descriptor.fiber.A_Fiber.Companion.getAndClearInterruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.getAndClearReificationWaiters
import avail.descriptor.fiber.A_Fiber.Companion.getAndSetSynchronizationFlag
import avail.descriptor.fiber.A_Fiber.Companion.interruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.joiningFibers
import avail.descriptor.fiber.A_Fiber.Companion.priority
import avail.descriptor.fiber.A_Fiber.Companion.setFiberResultAndState
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
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag.PERMIT_AVAILABLE
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
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.shortMethodName
import avail.descriptor.functions.A_RawFunction.Companion.startingChunk
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationWithFrame
import avail.descriptor.functions.ContinuationDescriptor.Companion.createElidedVariables
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.RegisterDumpDescriptor
import avail.descriptor.functions.RegisterDumpDescriptor.Companion.emptyRegisterDump
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.DebugRenderer
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_String.Companion.copyStringFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.quoteStringOn
import avail.descriptor.types.A_Type
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.A_Variable.Companion.value
import avail.descriptor.variables.VariableDescriptor
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.Companion.byNumericCode
import avail.interpreter.execution.Interpreter.Companion.maxUnreifiedCallDepth
import avail.interpreter.execution.Interpreter.Companion.timeSliceTicks
import avail.interpreter.execution.Interpreter.Companion.traceL2
import avail.interpreter.execution.Interpreter.SuspendedPrimitiveHelper.Completed
import avail.interpreter.levelTwo.L1InstructionStepper
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operation.L2_INVOKE
import avail.interpreter.levelTwo.operation.L2_REIFY.StatisticCategory
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Invokes
import avail.interpreter.primitive.controlflow.P_CatchException
import avail.interpreter.primitive.fibers.P_AttemptJoinFiber
import avail.interpreter.primitive.fibers.P_ParkCurrentFiber
import avail.interpreter.primitive.variables.P_SetValue
import avail.optimizer.DefaultL1ExecutableChunk
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.UNREACHABLE_ENTRY
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPointCatalog
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.ExecutableChunk
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator
import avail.optimizer.L2SplitCondition
import avail.optimizer.StackReifier
import avail.optimizer.StackReifier.AfterReification
import avail.optimizer.StackReifier.AfterReification.CONTINUE_FIBER
import avail.optimizer.StackReifier.AfterReification.SWITCH_FROM_FIBER
import avail.optimizer.jvm.CheckedField
import avail.optimizer.jvm.CheckedField.Companion.instanceField
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.JVMTranslator.Companion.callTraceL2AfterEveryInstruction
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.performance.Statistic
import avail.performance.StatisticReport
import avail.performance.StatisticReport.TOP_LEVEL_STATEMENTS
import avail.utility.Strings.tab
import avail.utility.iterableWith
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Debug.Renderer
import java.text.MessageFormat
import java.util.concurrent.ForkJoinWorkerThread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.min
import kotlin.reflect.jvm.javaGetter

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
	text = "nameForDebugger()",
	childrenArray = "describeForDebugger()")
final class Interpreter(
	@ReferencedInGeneratedCode
	@JvmField
	val runtime: AvailRuntime
): DebugRenderer
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
			assert(unreifiedCallDepth + delta >= 0)
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
	override fun describeForDebugger() = buildList<Pair<String, Any?>> {
		// Produce the current function being executed...
		add("Current function" to function)

		chunk?.let { activeChunk ->
			// Extract the current L2 chunk info...
			val prefix = if (activeChunk.isValid) "" else "[invalid] "
			add("L2 chunk = $prefix${activeChunk.name}" to activeChunk)
			val entryPointAddendum = when (activeChunk)
			{
				DefaultL1ExecutableChunk ->
				{
					DefaultEntryPointCatalog.allEntryPoints.firstOrNull {
						it.offset() == offset
					}?.let { " (${it.name})" } ?: ""
				}
				else -> ""
			}
			add("L2 offset = $offset$entryPointAddendum" to null)
			// Produce the current chunk's L2 instructions...
			add("L2 instructions" to activeChunk.instructions)
		}

		// Extract the current arguments, which may or may not have been
		// consumed already, or may be in the process of being populated for the
		// next call...
		add("argsBuffer(${argsBuffer.size})" to
			(argsBuffer to argsBuffer.toTypedArray()))
		add("latestResult (or failure) = $latestResult" to latestResult)

		// Build the stack frames...
		val frames = (theReifiedContinuation as? A_Continuation)
			.iterableWith { if (it.isNil) it else { it.caller } }
			.takeWhile(A_Continuation::notNil)
		add("Frames (${frames.size})" to tupleFromList(frames))
		val loader = availLoaderOrNull()
		val loaderSuffix = loader?.run { module.shortModuleNameNative }
		add("Loader ($loaderSuffix)" to loader)
		add("Fiber ${fiber?.fiberName}" to fiber)
		add("currentReifier = $currentReifier" to currentReifier)
	}.map { (name, value) ->
		AvailObjectFieldHelper(
			parentObject = nil,
			slot = DUMMY_DEBUGGER_SLOT,
			subscript = -1,
			value = if (value is Pair<*, *>) value.first else value,
			forcedName = name,
			forcedChildren =
				when (value)
				{
					is Pair<*, *> -> value.second as Array<*>
					null -> emptyArray<Any>()
					else -> null
				})
	}.toTypedArray()

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
	 * A cached snapshot of the bound fiber's
	 * [FiberHelper.clockBiasNanos][FiberDescriptor.FiberHelper.clockBiasNanos],
	 * refreshed on every fiber bind and cleared (to `0L`) on unbind.  The bias
	 * is invariant while the fiber is bound, so this lets [captureNanos] adjust
	 * the wall clock to fiber time without dereferencing the fiber on every
	 * call.
	 */
	var cachedFiberBiasNanos = 0L
		private set

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
	var debuggerRunCondition: ((Interpreter)->Boolean)? = null
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
			cachedFiberBiasNanos = newFiber.fiberHelper.clockBiasNanos
		}
		else
		{
			availLoader = null
			traceVariableReadsBeforeWrites = false
			traceVariableWrites = false
			debugger = null
			debuggerRunCondition = null
			cachedFiberBiasNanos = 0L
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
	 * The latest result by a successful primitive (in some situations), or the
	 * latest [error&#32;code][AvailErrorCode] produced by a failed primitive.
	 */
	private var latestResult: AvailObject? = null

	/**
	 * The amount of time taken by the most recently completed
	 * [invokeInPrimitive] operation.  This is subtracted from the primitive
	 * timing statistics for any [Invokes] primitive.
	 */
	var latestInvokingPrimitiveInnerTime: Long = 0L

	/**
	 * When a primitive requires reification, or when an interrupt or debugger
	 * likewise requires reification, this should be set to a [StackReifier].
	 * It should be cleared automatically just before invoking its
	 * [StackReifier.postReificationAction].
	 */
	@ReferencedInGeneratedCode
	@JvmField
	var currentReifier: StackReifier? = null

	@ReferencedInGeneratedCode
	fun clearLatestResult()
	{
		latestResult = null
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.INFO,
				"[$interpreterIndex] {0}Clear latestResult",
				debugModeString)
		}
	}

	/**
	 * Set the latest result by a successful primitive (in some situations),
	 * or the latest [error&#32;code][AvailErrorCode] produced by a failed
	 * primitive.
	 *
	 * The value may be Java's `null` to indicate this field should be clear,
	 * to detect accidental use.
	 *
	 * @param newResult
	 *   The latest result to record.
	 */
	@ReferencedInGeneratedCode
	fun setLatestResult(newResult: A_BasicObject)
	{
		latestResult = newResult as AvailObject
		if (debugL2)
		{
			val detail = newResult.loggingDetail()
			log(
				loggerDebugL2,
				Level.INFO,
				"[$interpreterIndex] {0}Set latestResult: {1}",
				debugModeString,
				detail)
		}
	}

	/**
	 * Set the latest failure value ([AvailObject]) provided by a failing
	 * primitive.  The value may be Java's `null` to indicate this field should
	 * be clear, to detect accidental use.
	 *
	 * Answer an [AvailObject]`?-typed` null as a convenience.
	 *
	 * @param failureValue
	 *   The latest failure result to record.
	 * @return
	 *   `null` typed as [AvailObject]`?` as a convenience.
	 */
	//@ReferencedInGeneratedCode
	fun fail(failureValue: A_BasicObject?): AvailObject?
	{
		currentReifier = null
		latestResult = failureValue as AvailObject
		if (debugL2)
		{
			val detail = failureValue.loggingDetail()
			// Warning - this can be quite expensive.
			val stack = try {
				throw Exception()
			} catch (e: Exception) {
				e.stackTrace.toList().run { subList(1, min(4, size)) }
			}
			log(
				loggerDebugL2,
				Level.INFO,
				"{0}Set latestFailureValue: {1} {2} stack={3}",
				debugModeString,
				latestResult?.typeTag?.shorterName,
				detail,
				stack.joinToString {
					"${it.className.substringAfterLast('.')}.${it.methodName}"
				})
		}
		return null
	}

	/**
	 * Set the latest failure value ([AvailObject]) to the
	 * [AvailErrorCode.numericCode] provided in an [AvailErrorCode] by a failing
	 * primitive.
	 *
	 * Answer an [AvailObject]`?-typed` null as a convenience.
	 *
	 * @param code
	 *   The [AvailErrorCode] indicating the kind of primitive failure.
	 * @return
	 *   `null` typed as [AvailObject]`?` as a convenience.
	 */
	//@ReferencedInGeneratedCode
	fun fail(code: AvailErrorCode): AvailObject? =
		fail(code.numericCode())

	/**
	 * Set the [currentReifier] to a [StackReifier] that will reify the call
	 * chain out to [Interpreter.run], then invoke the given lambda, which
	 * must succeed or fail the primitive.
	 *
	 * Answer an [A_BasicObject]`?-typed` null as a convenience.
	 *
	 * @param actuallyReify
	 *   Whether to actually accumulate stack frames.
	 * @param continuePrimitive
	 *   The action to perform after reification is complete, which should
	 *   complete the primitive with success or failure.
	 * @param
	 *   `null` as a nullable [A_BasicObject], for convenience.
	 */
	fun reifyForPrimitive(
		actuallyReify: Boolean,
		continuePrimitive: SuspensionHelper.()->Completed
	): A_BasicObject?
	{
		val primitiveFunction = function!!
		val primitive = primitiveFunction.code().codePrimitive()!!
		currentReifier = StackReifier(
			actuallyReify,
			primitive.reificationForNoninlineStat!!
		) {
			val once = AtomicBoolean(false)
			lateinit var afterReificationFlag: AfterReification
			val suspensionHelper = SuspensionHelper(
				toSucceed = { result ->
					// Return from the primitive with the result.
					assert(!once.getAndSet(true))
					assert(this@Interpreter == currentInterpreter) {
						"Use suspendThen for suspended primitives"
					}
					assert(fiber().executionState === RUNNING)
					val caller = getReifiedContinuation()!!
					assert(caller.notNil)
					{
						"Outermost reifying primitive is not allowed."
					}
					function = caller.function
					chunk = caller.levelTwoChunk
					offset = caller.levelTwoOffset
					setLatestResult(result)
					afterReificationFlag = CONTINUE_FIBER
				},
				toFail = { failureValue ->
					assert(!once.getAndSet(true))
					assert(this@Interpreter == currentInterpreter) {
						"Use suspendThen for suspended primitives"
					}
					assert(!primitive.hasFlag(CannotFail))
					assert(currentReifier == null)
					assert(fiber().executionState === RUNNING)
					function = primitiveFunction
					chunk = primitiveFunction.code().startingChunk
					offset = chunk!!.offsetAfterInitialTryPrimitive
					setLatestResult(failureValue)
					afterReificationFlag = CONTINUE_FIBER
				},
				toSuspend = { executionState ->
					assert(unreifiedCallDepth() == 0)
					assert(executionState.indicatesSuspension)
					assert(primitive.hasFlag(CanSuspend))
					function = null // Safety
					val aFiber = fiber()
					aFiber.suspendingFunction = primitiveFunction
					aFiber.lock {
						assert(aFiber.executionState === RUNNING)
						aFiber.executionState = executionState
						aFiber.continuation = getReifiedContinuation()!!
						setReifiedContinuation(null)
						val bound =
							aFiber.getAndSetSynchronizationFlag(BOUND, false)
						aFiber.fiberHelper.stopCountingCPU()
						assert(bound)
						fiber(null, "reifyForPrimitive")
					}
					startTick = -1L
					clearLatestResult()
					afterReificationFlag = SWITCH_FROM_FIBER
				})
			suspensionHelper.continuePrimitive()
			afterReificationFlag
		}
		return null
	}

	/**
	 * Answer the latest result by a successful primitive (in some situations),
	 * or the latest [error&#32;code][AvailErrorCode] produced by a failed
	 * primitive.
	 *
	 * @return
	 *   The latest result.
	 */
	@ReferencedInGeneratedCode
	fun getLatestResult(): AvailObject = latestResult!!

	/**
	 * Answer the latest result by a successful primitive (in some situations),
	 * or the latest [error&#32;code][AvailErrorCode] produced by a failed
	 * primitive.  This might be `null` in some situations.
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
	 * Suspend the current fiber, evaluating the provided action.  The action
	 * has an implicit helper [SuspensionHelper], which exposes `succeed()` and
	 * `fail()` methods, one of which should be invoked later.
	 *
	 * @param action
	 *   The action supplied by the client that itself takes an implicit
	 *   receiver exposing `succeed()` and `fail()` methods for succeeding or
	 *   failing the primitive at a later time.
	 * @return
	 *   The value `null`, typed as a nullable [AvailObject] for convenience,
	 *   after first storing a [StackReifier] in [currentReifier].
	 */
	fun suspendInSafePointThen(
		action: SuspendedPrimitiveHelper.()->Unit
	): AvailObject? = fiber!!.let { theFiber ->
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
	 * Construct a [SuspendedPrimitiveHelper].
	 *
	 * @param toSucceed
	 *   The function to call that accepts a value from the [Primitive] if the
	 *   `Primitive` is successful.
	 * @param toFail
	 *   The function to call that accepts an [A_BasicObject] that provides the
	 *   reason for the [Primitive] failure.
	 */
	open class SuspendedPrimitiveHelper constructor (
		private val toSucceed: (A_BasicObject)->Unit,
		private val toFail: (A_BasicObject)->Unit)
	{
		/**
		 * A type used to *statically* ensure one of the supplied lambdas is
		 * invoked, because there isn't another inconspicuous way to get it,
		 * and the client will require it be produced (i.e., through one of
		 * the calls like [succeed] or [fail]).
		 */
		object Completed

		/**
		 * Succeed from the suspended [Primitive], resuming its fiber.
		 *
		 * @param value
		 *   The value to return from the primitive.
		 */
		fun succeed(value: A_BasicObject): Completed
		{
			toSucceed(value)
			return Completed
		}

		/**
		 * Fail from the suspended [Primitive], resuming its fiber.
		 *
		 * @param errorNumber
		 *   The [A_BasicObject] to provide as the reason for failing the
		 *   primitive.
		 */
		fun fail(errorNumber: A_BasicObject): Completed
		{
			toFail(errorNumber)
			return Completed
		}

		/**
		 * Fail from the suspended [Primitive], resuming its fiber.
		 *
		 * @param errorCode
		 *   The [AvailErrorCode] whose numeric
		 *   [code][AvailErrorCode.numericCode] is used as the reason for
		 *   failing the primitive.
		 */
		fun fail(errorCode: AvailErrorCode): Completed
		{
			toFail(errorCode.numericCode())
			return Completed
		}
	}

	/**
	 * A variation of [SuspendedPrimitiveHelper] that also allows a [suspend]
	 * function to be invoked.  The semantics are caller-specific.
	 */
	class SuspensionHelper constructor (
		toSucceed: (A_BasicObject)->Unit,
		toFail: (A_BasicObject)->Unit,
		private val toSuspend: (executionState: ExecutionState)->Unit
	): SuspendedPrimitiveHelper(toSucceed, toFail)
	{
		/**
		 * Suspend the current fiber.
		 */
		fun suspend(state: ExecutionState): Completed
		{
			toSuspend(state)
			return Completed
		}
	}

	/**
	 * Suspend the interpreter in the middle of running a primitive (which must
	 * be marked as [CanSuspend]).  The supplied action can invoke
	 * [succeed][SuspensionHelper.succeed] or [fail][SuspensionHelper.fail] when
	 * it has determined its fate.
	 *
	 * @param body
	 *   What to do when the fiber has been suspended.
	 * @return
	 *   The value `null` typed as an optional [AvailObject], for convenience.
	 */
	@CheckReturnValue
	fun suspendThen(
		body: SuspendedPrimitiveHelper.()->Unit
	): AvailObject?
	{
		val copiedArgs = argsBuffer.toList()
		val primitiveFunction = function!!
		val prim = primitiveFunction.code().codePrimitive()!!
		assert(prim.hasFlag(CanSuspend))
		val currentFiber = fiber()
		val once = AtomicBoolean(false)
		postExitContinuation = {
			val suspendedPrimitiveHelper = SuspendedPrimitiveHelper(
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
				})
			suspendedPrimitiveHelper.body()
		}
		primitiveSuspend(SUSPENDED, primitiveFunction)
		return null
	}

	/**
	 * An action to run after a [fiber][A_Fiber] exits and is unbound.  The
	 * affected fiber will be locked around the evaluation of this lambda.
	 */
	var postExitContinuation: (()->Unit)? = null
		set(value)
		{
			assert(field === null || value === null)
			field = value
		}

	/**
	 * Suspend the current [A_Fiber] within a [Primitive] invocation.  The
	 * reified [A_Continuation] will be available in [getReifiedContinuation],
	 * and will be installed into the current fiber.
	 *
	 * @param state
	 *   The suspension [state][ExecutionState].
	 * @return
	 *   The value `null` to indicate a suitable [StackReifier] has been
	 *   recorded for suspending the fiber.
	 */
	@CheckReturnValue
	fun primitiveSuspend(
		state: ExecutionState,
		suspendingFunction: A_Function
	): A_BasicObject?
	{
		assert(state.indicatesSuspension)
		val primitive = suspendingFunction.code().codePrimitive()!!
		assert(primitive.hasFlag(CanSuspend))
		function = null // Safety
		val aFiber = fiber()
		aFiber.suspendingFunction = suspendingFunction
		currentReifier = StackReifier(
			true,
			primitive.reificationForNoninlineStat!!
		) {
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
			clearLatestResult()
			SWITCH_FROM_FIBER
		}
		return null
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
		assert(state.indicatesTermination)
		val aFiber = fiber()
		aFiber.lock {
			assert(aFiber.executionState === RUNNING)
			aFiber.continuation = nil
			aFiber.setFiberResultAndState(finalObject, state)
			val bound = aFiber.getAndSetSynchronizationFlag(BOUND, false)
			aFiber.fiberHelper.stopCountingCPU()
			assert(bound)
			fiber(null, "exitFiber")
		}
		startTick = -1L
		// Be tidy.
		clearLatestResult()
		postExitContinuation = {
			val joining = aFiber.lock {
				val temp: A_Set = aFiber.joiningFibers.makeShared()
				aFiber.joiningFibers = nil
				temp
			}
			// Wake up all fibers trying to join this one.
			joining.forEach { joiner ->
				joiner.lock {
					// Restore the permit. Resume the fiber if it was parked.
					joiner.getAndSetSynchronizationFlag(PERMIT_AVAILABLE, true)
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
							joiner.suspendingFunction.code().codePrimitive()!!
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
	 * Attempt the [Primitive].  Answer the resulting [A_BasicObject] if
	 * successful.  Otherwise answer `null`, having set up either a
	 * [StackReifier] in [currentReifier], or a primitive failure code in
	 * [latestResult].
	 *
	 * @param primitiveFunction
	 *   The primitive [A_Function] to invoke.
	 * @param primitive
	 *   The [Primitive] to attempt.
	 * @return
	 *   The result or `null`.
	 */
	@ReferencedInGeneratedCode
	fun attemptPrimitive(
		primitiveFunction: A_Function,
		primitive: Primitive
	): A_BasicObject?
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
		val value = afterAttemptPrimitive(
			primitive,
			beforeAttemptPrimitive(primitive),
			primitive.attempt(this))
		return when
		{
			value !== null ->
			{
				function = null
				returningFunction = primitiveFunction
				value
			}
			currentReifier !== null ->
			{
				null
			}
			latestResult !== null ->
			{
				function = primitiveFunction
				setOffset(chunk!!.offsetAfterInitialTryPrimitive)
				runChunk()
			}
			else ->
			{
				assert(false) {
					"inline primitive didn't succeed, fail, or reify!"
				}
				null
			}
		}
	}

	/**
	 * A [Primitive] wants to change the continuation and return a value into
	 * it.  Store a [StackReifier] in the [currentReifier] and answer `null` as
	 * an optional [A_BasicObject] for convenience.
	 *
	 * @param primitive
	 *   The primitive, used to track reification abandonment statistics.
	 * @param continuation
	 *   The continuation to continue running with a value returned into it.
	 * @param returnedValue
	 *   The value to return into the continuation.  This method does not check
	 *   that it conforms to the expected type.
	 */
	fun returnIntoContinuation(
		primitive: Primitive,
		continuation: A_Continuation,
		returnedValue: A_BasicObject
	): A_BasicObject?
	{
		assert(primitive.hasFlag(CanSwitchContinuations))
		currentReifier = StackReifier(
			actuallyReify = false,
			primitive.reificationAbandonmentStat!!)
		{
			setReifiedContinuation(continuation)
			setLatestResult(returnedValue)
			if (continuation.isNil)
			{
				function = null
				chunk = null
				offset = Int.MAX_VALUE
			}
			else
			{
				function = continuation.function
				chunk = continuation.levelTwoChunk
				offset = continuation.levelTwoOffset
			}
			CONTINUE_FIBER
		}
		return null
	}


	/**
	 * A [Primitive] wants to change the continuation and resume it, without
	 * returning a value into it.  Store a [StackReifier] in the
	 * [currentReifier] and answer `null` as an optional [A_BasicObject] for
	 * convenience.  A resume-kind continuation won't look for a return value
	 * being given to it.
	 *
	 * @param primitive
	 *   The primitive, used to track reification abandonment statistics.
	 * @param continuation
	 *   The continuation to resume.
	 */
	fun resumeContinuation(
		primitive: Primitive,
		continuation: A_Continuation
	): A_BasicObject?
	{
		assert(primitive.hasFlag(CanSwitchContinuations))
		assert(continuation.notNil)
		currentReifier = StackReifier(
			actuallyReify = false,
			primitive.reificationAbandonmentStat!!)
		{
			setReifiedContinuation(continuation)
			function = continuation.function
			chunk = continuation.levelTwoChunk
			offset = continuation.levelTwoOffset
			clearLatestResult()
			CONTINUE_FIBER
		}
		return null
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
			val argsDetail = argsBuffer.joinToString { it.loggingDetail() }
			log(
				loggerDebugPrimitives,
				Level.FINER,
				"{0}attempt {1} ({2})",
				debugModeString,
				primitive.name,
				argsDetail)
		}
		clearLatestResult()
		assert(currentInterpreter == this)
		return captureNanos()
	}

	/**
	 * The given primitive has just executed; do any necessary post-processing.
	 *
	 * @param primitive
	 *   The primitive that just ran.
	 * @param timeBefore
	 *   The time in nanoseconds just prior to the primitive running.
	 * @param valueOrNull
	 *   The result of running the primitive, or `null` to indiacet the
	 *   primitive failed or requested reification.
	 * @return
	 *   The same [valueOrNull] that was passed, to make calling simpler.
	 */
	@ReferencedInGeneratedCode
	fun afterAttemptPrimitive(
		primitive: Primitive,
		timeBefore: Long,
		valueOrNull: A_BasicObject?
	): A_BasicObject?
	{
		val duration =
			captureNanos() - timeBefore - latestInvokingPrimitiveInnerTime
		// Tidy this up so it won't affect other invocation stats.
		latestInvokingPrimitiveInnerTime = 0L
		primitive.addNanosecondsRunning(duration, interpreterIndex)
		if (debugPrimitives)
		{
			// Lifted to another function, to shorten the live path (when not
			// debugging).
			logDebugAfterAttemptPrimitive(primitive, valueOrNull)
		}
		return valueOrNull
	}

	/**
	 * Log debug information about the primitive that just executed.
	 *
	 * @param primitive
	 *   The [Primitive] that just ran.
	 * @param valueOrNull
	 *   The primitive result if it was successful, otherwise `null` if it has
	 *   failed or is requesting reification.
	 */
	private fun logDebugAfterAttemptPrimitive(
		primitive: Primitive,
		valueOrNull: A_BasicObject?)
	{
		if (loggerDebugPrimitives.isLoggable(Level.FINER))
		{
			val status = when
			{
				valueOrNull !== null ->
				{
					val stronger = valueOrNull as AvailObject
					"Success: ${stronger.loggingDetail()}"
				}
				currentReifier !== null -> "Reifying"
				else ->
				{
					val failure = getLatestResult()
					when
					{
						failure.isInt ->
							"Failure: (${byNumericCode(failure.extractInt)})"
						else -> "Failure: (${failure.typeTag.shorterName})"
					}
				}
			}
			log(
				loggerDebugPrimitives,
				Level.FINER,
				"{0}... after primitive {1} => {2}",
				debugModeString,
				primitive.name,
				status)
		}
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
			var off = -999999
			val text = when
			{
				continuation === null -> "null"
				continuation.isNil -> "nil"
				else ->
				{
					val code = continuation.function.code()
					var name = code.methodName.asNativeString()
					name = when (val prim = code.codePrimitive())
					{
						null -> name
						P_CatchException ->
						{
							val guard = continuation.frameAt(
								P_CatchException.slotIndexOfGuardVariable)
							val guardValue = when
							{
								guard.isInstanceOfKind(mostGeneralVariableType)
									-> guard.value().toString()
								else -> "unknown"
							}
							"(${prim.name} guard=$guardValue) $name"
						}
						else -> "(${prim.name}) $name"
					}
					val line = code.codeStartingLineNumber
					if (line != 0) name += ":$line"
					val pc = continuation.pc
					off = continuation.levelTwoOffset
					when
					{
						continuation.levelTwoChunk == DefaultL1Chunk ->
							"(L1) pc=$pc of $name"
						else ->
							"(L2) pc=$pc of $name"
					}
				}
			}
			traceL2(
				(chunk?.executableChunk ?: DefaultL1ExecutableChunk),
				this,
				off,
				"Set continuation = ",
				arrayOf(text))
		}
	}

	/**
	 * Replace the [getReifiedContinuation] with its caller, answering the
	 * one that was removed.
	 */
	@ReferencedInGeneratedCode
	fun popContinuation(): AvailObject
	{
		if (debugL2)
		{
			logPopContinuation()
		}
		val current = getReifiedContinuation()!!
		setReifiedContinuation(current.caller)
		return current
	}

	/**
	 * Extracted to make [popContinuation] smaller for HotSpot.
	 */
	private fun logPopContinuation()
	{
		val text = buildString {
			var ptr: A_Continuation = theReifiedContinuation!!
			var counter = 0
			while (ptr.notNil && ++counter < 5)
			{
				append("\n\t\toffset ")
				append(ptr.levelTwoOffset)
				append(" in ")
				val ch = ptr.levelTwoChunk
				if (ch === DefaultL1Chunk)
				{
					append("(L1) - ")
					append(ptr.function.code().methodName)
				}
				else
				{
					append(ptr.levelTwoChunk.name)
				}
				ptr = ptr.caller
			}
			if (ptr.notNil) append("\n\t\t...")
		}
		traceL2(
			(chunk?.executableChunk ?: DefaultL1ExecutableChunk),
			this,
			-100000,
			"POPPING CONTINUATION from:",
			arrayOf(text)
		)
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
	 * Answer a [Statistic] if an interrupt has been requested, allowing the
	 * statistics for causes of interrupts to be collected. The interrupt may be
	 * specific to the current [fiber] or global to the [runtime][AvailRuntime].
	 * There are several reasons why an interrupt might be requested:
	 *
	 * * The runtime might request a safe point, to ensure no fibers are
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
	@get:JvmName("statisticForRequestedInterrupt")
	val statisticForRequestedInterrupt: Statistic?
		get() = when
		{
			runtime.safePointRequested ->
				safePointInterruptStatistic
			unreifiedCallDepth > maxUnreifiedCallDepth ->
				callDepthInterruptStatistic
			runtime.clock.get() - startTick >= timeSliceTicks ->
				timeSliceInterruptStatistic
			fiber().interruptRequestFlag(REIFICATION_REQUESTED) ->
				reificationRequestedFromOtherFiberStatistic
			else -> null
		}

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
		offset = Int.MAX_VALUE
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}Yielding to process interrupt",
				debugModeString)
		}
		startTick = -1L
		clearLatestResult()
		postExitContinuation = {
			waiters.forEach { action -> action(continuation) }
			runtime.resumeFromInterrupt(aFiber)
		}
	}

	/**
	 * Check if the current chunk is still valid.  If so, return `true`.
	 * Otherwise, set the current chunk to the [DefaultL1Chunk], set the
	 * offset to the specified offset within that chunk, and return `false`.
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
	 *   The offset within the [DefaultL1Chunk] to resume execution at if the
	 *   current chunk is found to be invalid.
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
			theReifiedContinuation!!.run {
				if (notNil) createElidedVariables()
			}
			chunk = DefaultL1Chunk
			offset = offsetInDefaultChunkIfInvalid
			false
		}
	}

	/**
	 * Obtain an appropriate [StackReifier] for restarting the specified
	 * [A_Continuation] with the same arguments it captured when the
	 * continuation was constructed.
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
		return StackReifier(
			false,
			StatisticCategory.ABANDON_BEFORE_RESTART_IN_L2.statistic
		) {
			val whichFunction = continuation.function
			val numArgs = whichFunction.code().numArgs()
			argsBuffer.clear()
			(1 .. numArgs).forEach {
				argsBuffer.add(continuation.frameAt(it))
			}
			setReifiedContinuation(continuation.caller)
			function = whichFunction
			chunk = continuation.levelTwoChunk
			offset = continuation.levelTwoOffset
			clearLatestResult()
			CONTINUE_FIBER
		}
	}

	/**
	 * Answer a [StackReifier] which can be used for reifying the current stack
	 * by returning it out to the [run] loop.  When it reaches there, a lambda
	 * embedded in this reifier will run, performing an action suitable to the
	 * provided flags.
	 *
	 * @param processInterrupt
	 *   Whether a pending interrupt should be processed after reification.
	 * @param statistic
	 *   A [Statistic] to record when a reification happens.
	 * @return
	 *   The new [StackReifier].
	 */
	@ReferencedInGeneratedCode
	fun reify(
		processInterrupt: Boolean,
		statistic: Statistic
	): StackReifier = when
	{
		processInterrupt ->
		{
			// Reify-and-interrupt.
			StackReifier(true, statistic) {
				processInterrupt(getReifiedContinuation()!!)
				CONTINUE_FIBER
			}
		}
		else ->
		{
			// Capture the interpreter's state, reify the frames, and as an
			// after-reification action, restore the interpreter's state.
			val savedFunction = function!!
			val newReturnValue = latestResultOrNull()

			// Reify-and-continue.  The current frame is also reified.
			StackReifier(true, statistic) {
				val continuation = getReifiedContinuation()!!
				function = savedFunction
				chunk = continuation.levelTwoChunk
				offset = continuation.levelTwoOffset
				newReturnValue ?: clearLatestResult()
				newReturnValue?.let(::setLatestResult)
				// Return into the Interpreter's run loop.
				CONTINUE_FIBER
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
		arguments: Iterable<AvailObject>
	): StackReifier
	{
		return StackReifier(
			false,
			StatisticCategory.ABANDON_BEFORE_RESTART_IN_L2.statistic
		) {
			val whichFunction = continuation.function
			val numArgs = whichFunction.code().numArgs()
			argsBuffer.clear()
			argsBuffer.addAll(arguments)
			assert(argsBuffer.size == numArgs)
			setReifiedContinuation(continuation.caller)
			function = whichFunction
			chunk = continuation.levelTwoChunk
			offset = continuation.levelTwoOffset
			clearLatestResult()
			CONTINUE_FIBER
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
	 *   Either the [A_BasicObject] produced by the function, or `null` to
	 *   indicate the function is reifying, in which case [currentReifier] will
	 *   hold the [StackReifier].
	 */
	fun invokeFunction(aFunction: A_Function): A_BasicObject?
	{
		function = aFunction
		val code = aFunction.code()
		assert(code.numArgs() == argsBuffer.size)
		chunk = code.startingChunk
		assert(chunk!!.isValid)
		offset = 0
		adjustUnreifiedCallDepthBy(1)

		try
		{
			if (debugger !== null)
			{
				// Do the call in a debugger-aware way.
				val fiberHelper = fiber!!.fiberHelper
				if (fiberHelper.debuggerCanInvoke)
				{
					// Effectively shut off the debugger until we've returned
					// back to this point in the JVM call stack.
					val savedDebugger = debugger
					val savedRunCondition = debuggerRunCondition
					debugger = null
					debuggerRunCondition = { true }
					try
					{
						// Do the call without the debugger present.
						return runChunk()
					}
					finally
					{
						// Put the debugger back into the picture, whether the
						// invoked function completed or reified.
						debugger = savedDebugger
						debuggerRunCondition = savedRunCondition
					}
				}
				else
				{
					// Don't check whether the debugger allows us to step or
					// not, as that's a question to be asked by the target
					// function being invoked.  Just make sure to drop to L1 to
					// give it that chance.
					chunk = DefaultL1Chunk
					offset = 0
				}
			}
			return runChunk()
		}
		finally
		{
			adjustUnreifiedCallDepthBy(-1)
		}
	}

	/**
	 * A primitive that has [Invokes] is invoking a function.  The arguments
	 * have been set up in [argsBuffer], and the function to call is provided.
	 *
	 * @param aFunction
	 *   The function to invoke.
	 * @return
	 *   The resulting value produced by the called function if it completes,
	 *   otherwise `null` to indicate a reification has been set up.
	 */
	fun invokeInPrimitive(
		aFunction: AvailObject
	): A_BasicObject?
	{
		clearLatestResult()
		val before = captureNanos()
		val valueOrNull = invokeFunction(aFunction)
		latestInvokingPrimitiveInnerTime = captureNanos() - before
		if (valueOrNull == null)
		{
			// Only completion or reification are allowed, not a primitive
			// failure.
			assert(currentReifier !== null)
		}
		return valueOrNull
	}

	/**
	 * Run the interpreter until it completes the fiber, is suspended, or is
	 * interrupted, perhaps by exceeding its time-slice.
	 */
	fun run()
	{
		assert(callerIsReified())
		assert(fiber !== null)
		nanosToExclude = 0L
		startTick = runtime.clock.get()
		if (debugL2)
		{
			debugModeString = when
			{
				debugIntoFiberDebugLog -> ""
				else -> "Fib=" + fiber!!.uniqueId + " "
			}
			log(
				loggerDebugPrimitives,
				Level.FINER,
				"\n\n[$interpreterIndex] {0}Run: ({1})",
				debugModeString,
				fiber!!.fiberName)
		}
		while (true)
		{
			// Run the chunk to completion (dealing with reification). The chunk
			// will do its own invalidation checks and off-ramp to L1 if needed.
			val calledFunction = function!!
			val valueOrNull = runChunk()
			assert(callerIsReified())
			returningFunction = calledFunction
			if (valueOrNull === null)
			{
				// No value was produced, so only reification are allowed.
				val reifier = currentReifier!!
				currentReifier = null
				// Reification has been requested, and the exception has already
				// collected all the reification actions.
				if (reifier.actuallyReify)
				{
					reifier.runActions(this)
				}
				reifier.recordCompletedReification(interpreterIndex)
				// The postReificationAction should set this up, if it intends
				// to run more code.
				chunk = null
				offset = Int.MAX_VALUE
				val afterReification = reifier.postReificationAction()
				// Fall through to accomplish the return.
				when (afterReification)
				{
					CONTINUE_FIBER ->
					{
						// The top frame doesn't get reified – instead, its
						// StackReifier sets up the interpreter to continue
						// running where it left off, without building a
						// continuation.
						if (function !== null)
						{
							assert(chunk !== null)
							continue
						}
						// Somebody did a continuation return off the edge of
						// the world.  Handle it directly here.
						terminateFiber(latestResult!!)
						if (debugL2)
						{
							log(
								loggerDebugL2,
								Level.FINER,
								"{0}Exit2 fiber prim-exited Interpreter.run)\n",
								debugModeString)
						}
						// Prevent dynamic optimizer periodic polling from
						// thinking this interpreter is running any function.
						function = null
						return
					}
					SWITCH_FROM_FIBER ->
					{
						// The fiber has been dealt with.  Exit the interpreter
						// loop.
						assert(fiber === null)
						if (debugL2)
						{
							log(
								loggerDebugL2,
								Level.FINER,
								"{0}Exit1 reifier left fiber\n",
								debugModeString)
						}
						function = null
						return
					}
				}
			}
			val frame: A_Continuation = getReifiedContinuation()!!
			if (frame.isNil)
			{
				// The reified stack is empty, too.  We must have returned from
				// the outermost frame.  The fiber runner will deal with it.
				terminateFiber(valueOrNull)
				if (debugL2)
				{
					log(
						loggerDebugL2,
						Level.FINER,
						"{0}Exit3 fiber fell off outermost function in " +
							"Interpreter.run)\n",
						debugModeString)
				}
				// Prevent dynamic optimizer periodic polling from thinking this
				// interpreter is running any function.
				function = null
				return
			}
			// Resume the top reified frame.  It should be at an on-ramp that
			// can explode the continuation into whatever form the chunk needs
			// (e.g., JVM registers, or perhaps an Array), checking the returned
			// value in [latestResult] if it's expecting a result (e.g., from a
			// previously reified call).
			latestResult = valueOrNull as AvailObject
			function = frame.function
			chunk = frame.levelTwoChunk
			offset = frame.levelTwoOffset
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
	 * the chunk, allowing an orderly off-ramp into the [DefaultL1Chunk]
	 * (which simply interprets the L1 nybblecodes).
	 *
	 * @return
	 *   `null` if returning normally, otherwise a [StackReifier] to effect
	 *   reification.
	 */
	@ReferencedInGeneratedCode
	fun runChunk(): A_BasicObject?
	{
		val currentChunk = chunk!!
		currentChunk.beforeRunChunk(offset)
		val valueOrNull = currentChunk.executableChunk.runChunk(this, offset)
		assert(valueOrNull !== null || currentReifier !== null)
		return valueOrNull
	}

	override fun nameForDebugger() = toString()

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
				append(" [%s]".format(fiber!!.fiberName.asNativeString()))
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
	 * will use the [DefaultL1Chunk]'s [DefaultEntryPoint.UNREACHABLE_ENTRY].
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
	 *   Always `null`, since the invoked handler is ⊥-valued, and therefore
	 *   can't return normally.
	 */
	@ReferencedInGeneratedCode
	fun reportWrongReturnType(
		returnedValueOrNil: A_BasicObject,
		expectedReturnType: A_Type,
		pc: Int,
		stackp: Int,
		vararg slots: A_BasicObject
	): A_BasicObject?
	{
		val returner = returningFunction!!
		val callerFunction = function!!
		val wrappedReturnValue = newVariableWithContentType(
			Types.ANY(),
			returnedValueOrNil)
		argsBuffer.clear()
		argsBuffer.add(returner as AvailObject)
		argsBuffer.add(expectedReturnType as AvailObject)
		argsBuffer.add(wrappedReturnValue)
		val valueOrNull = invokeFunction(
			runtime.resultDisagreedWithExpectedTypeFunction())
		assert(valueOrNull === null) { "return type handler must not return." }
		// Assemble a (non-resumable) stack frame for the reifier.
		val reifier = currentReifier!!
		reifier.pushAction {
			createContinuationWithFrame(
				callerFunction,
				it,
				emptyRegisterDump(UNREACHABLE_ENTRY.offset),
				pc,
				stackp,
				DefaultL1Chunk,
				UNREACHABLE_ENTRY.offset,
				listOf(*slots),
				0)
		}
		return null
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
		var statistic = synchronized(topStatementEvaluationStats) {
			topStatementEvaluationStats.computeIfAbsent(
				module.moduleName
			) {
				Statistic(TOP_LEVEL_STATEMENTS, it.asNativeString())
			}
		}
		statistic.record(sample, interpreterIndex)
	}

	/**
	 * This function can be called by optimized L2 code, and its return
	 * value returned again by the L2 chunk.  It will cause the Interpreter
	 * to continue running at the specified pc and stackp and slots, but
	 * using the [DefaultL1Chunk].  Note that reification of the stack is
	 * not necessary, as the continuation we construct for immediate
	 * resumption uses [theReifiedContinuation] as its caller, even if there
	 * are unreified JVM stack frames.  Any subsequent return or reification
	 * will be handled the same as if it was never running the current L2
	 * chunk and had always be running the unoptimized L1.
	 *
	 * @param pc
	 *   The Level One program counter to set in the continuation.
	 * @param stackp
	 *   The Level One stack pointer to set in the continuation.
	 * @param slots
	 *   The (boxed) [AvailObject]s used to set up the continuation's slots.
	 * @return
	 *   Always `null`, after setting up this interpreter to immediately resume
	 *   the continuation.
	 */
	@ReferencedInGeneratedCode
	fun fallBackToL1(
		pc: Int,
		stackp: Int,
		slots: Array<A_BasicObject>
	) : StackReifier?
	{
		setReifiedContinuation(
			createContinuationWithFrame(
				function = function!!,
				caller = getReifiedContinuation()!!,
				registerDump = nil,
				pc = pc,
				stackp = stackp,
				levelTwoChunk = DefaultL1Chunk,
				levelTwoOffset = DefaultEntryPoint.RESUME.offset,
				frameValues = slots.toList(),
				zeroBasedStartIndex = 0))
		chunk = DefaultL1Chunk
		offset = DefaultEntryPoint.RESUME.offset
		return null
	}

	/**
	 * Used by the [L2SimpleTranslator].  It's fine that it's per-interpreter,
	 * since it doesn't have to perfectly canonicalize the arrays, just reduce
	 * greatly the amount of repetition of equivalent arrays.  The key is a
	 * [List], just to get the right equality and hash semantics.
	 */
	val arraysForL2Simple = mutableMapOf<List<Int>, ReadArray>()

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
		 * If true, annotate the control flow graph with additional information
		 * about which [L2SplitCondition]s are available.
		 */
		@Volatile
		var debugAvailableSplits = true

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

		/**
		 * When set, each time a module is unloaded, a breadth-first scan is
		 * performed, starting at the runtime, attempting to locate the module
		 * that was just unloaded.  It should not be accessible, so finding a
		 * path to it indicates a problem.
		 */
		@Volatile
		var debugCheckAfterUnload = false

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
		val loggerDebugPrimitives = Logger.getLogger(
			Interpreter::class.java.canonicalName + ".debugPrimitives")

		/**
		 * The approximate maximum number of bytes to log per fiber before
		 * throwing away the earliest 25%.
		 */
		private const val maxFiberLogLength = 250_000

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
					AvailThread.currentOrNull?.let { it.interpreter.fiber },
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
				if (debugIntoFiberDebugLog)
				{
					// Write into a StringBuilder in each fiber's debugLog().
					if (runningFiber !== null)
					{
						// Log to the fiber.
						val log = runningFiber.debugLog
						if (interpreter.currentReifier != null)
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
		 * If [callTraceL2AfterEveryInstruction] was true during code
		 * generation, this method is invoked just prior to each L2 instruction.
		 *
		 * @param executableChunk
		 *   The [ExecutableChunk] being executed.
		 * @param interpreter
		 *   The [Interpreter] executing the chunk.
		 * @param offset
		 *   The current L2 offset.
		 * @param description
		 *   A one-line textual description of this instruction.
		 * @param readValues
		 *   The array of values of the read operands, boxed into *Java* boxes
		 *   if needed.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun traceL2(
			executableChunk: ExecutableChunk,
			interpreter: Interpreter,
			offset: Int,
			description: String,
			readValues: Array<Any>)
		{
			if (debugL2)
			{
				if (mainLogger.isLoggable(Level.SEVERE))
				{
					val str = buildString {
						append("[")
						append(interpreter.interpreterIndex)
						append("] L2 = ")
						append(offset)
						append(" of ")
						append(executableChunk.name())
						append(" ")
						append(description)
						readValues.joinTo(
							this@buildString, ", ", "[", "]"
						) {
							shortDebugString(it)
						}
					}
					val fiber = currentInterpreter.fiberOrNull()
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
		 * Produce a short string describing the given value for the debugLog.
		 */
		fun shortDebugString(value: Any): String = buildString {
			when (value)
			{
				is AvailObject ->
				{
					when (value.descriptor)
					{
						is RegisterDumpDescriptor -> {
							append("a RegisterDump(")
							append(value.objectSlotsCount())
							append(" objs / ")
							append(value.integerSlotsCount())
							append(" ints)")
						}
						is VariableDescriptor -> {
							append("a Variable (value = ")
							append(shortDebugString(value.value()))
							append(")")
						}
						is ContinuationDescriptor -> {
							append("a Continuation for ")
							append(value.function.code().shortMethodName)
						}
						is FunctionDescriptor -> {
							append("a Function ")
							append(value.code().shortMethodName)
						}
						is CompiledCodeDescriptor -> {
							append("a RawFunction ")
							append(value.shortMethodName)
						}
						is TupleDescriptor -> {
							if (value.isString)
							{
								quoteStringOn(
									if (value.tupleSize < 50) value
									else value
										.copyStringFromToCanDestroy(
											1, 50, false)
										.appendCanDestroy(
											fromCodePoint('…'.code),
											true,
											false)
										as A_String)
							}
							else
							{
								value.joinTo(
									this@buildString,
									prefix = "<",
									postfix = ">",
									limit = 4,
									transform = { shortDebugString(it) })
							}
						}
						else -> append(value)
					}
				}
				else -> append(value)
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
			Interpreter::class.java,
			Int::class.javaPrimitiveType!!,
			String::class.java,
			Any::class.java.arrayType())

		/**
		 * Answer the Avail interpreter associated with the
		 * [Thread.currentThread].  If this thread is not an [AvailThread], then
		 * fail.
		 *
		 * @return
		 *   The current Level Two interpreter.
		 */
		val currentInterpreter: Interpreter
			get() = AvailThread.current.interpreter

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
			AvailThread.currentOrNull?.interpreter

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
		val setLatestResultMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::setLatestResult.name,
			Void.TYPE,
			A_BasicObject::class.java)

		/** Access the [getLatestResult] method. */
		val getLatestResultMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::getLatestResult.name,
			AvailObject::class.java)

		/** Access the [currentReifier] field. */
		val currentReifierField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::currentReifier.name,
			StackReifier::class.java)

		/** The [CheckedField] for the field argsBuffer. */
		val interpreterReturningFunctionField: CheckedField = instanceField(
			Interpreter::class.java,
			Interpreter::returningFunction.name,
			A_Function::class.java)

		/** The method [beforeAttemptPrimitive]. */
		val beforeAttemptPrimitiveMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::beforeAttemptPrimitive.name,
			Long::class.javaPrimitiveType!!,
			Primitive::class.java)

		/** The method [afterAttemptPrimitive]. */
		val afterAttemptPrimitiveMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::afterAttemptPrimitive.name,
			A_BasicObject::class.java,
			Primitive::class.java,
			Long::class.javaPrimitiveType!!,
			A_BasicObject::class.java)

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
		val popContinuationMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::popContinuation.name,
			AvailObject::class.java)

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
		val chunkField: CheckedField = instanceField(
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

		/** Access the [statisticForRequestedInterrupt] method. */
		val statisticForRequestedInterruptMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::statisticForRequestedInterrupt.javaGetter!!.name,
			Statistic::class.java)

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
			Statistic::class.java)

		/**
		 * The [CheckedMethod] for [reifierToRestartWithArguments].
		 */
		val reifierToRestartWithArgumentsMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::reifierToRestartWithArguments.name,
			StackReifier::class.java,
			A_Continuation::class.java,
			Iterable::class.java)

		/** Access the [preinvoke0] method. */
		val preinvoke0Method = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke0.name,
			AvailObject::class.java,
			A_Function::class.java)

		/** Access the [preinvoke1] method. */
		val preinvoke1Method = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke1.name,
			AvailObject::class.java,
			A_Function::class.java,
			AvailObject::class.java)

		/**
		 * Access the [preinvoke2] method.
		 */
		val preinvoke2Method = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke2.name,
			AvailObject::class.java,
			A_Function::class.java,
			AvailObject::class.java,
			AvailObject::class.java)

		/**
		 * Access the [preinvoke3] method.
		 */
		val preinvoke3Method = instanceMethod(
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
		val preinvokeMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::preinvoke.name,
			AvailObject::class.java,
			A_Function::class.java,
			Array<AvailObject>::class.java)

		/**
		 * Access the [postinvoke] method.
		 */
		val postinvokeMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::postinvoke.name,
			StackReifier::class.java,
			L2Chunk::class.java,
			A_Function::class.java,
			StackReifier::class.java)

		/**
		 * Access the [runChunk] method.
		 */
		val interpreterRunChunkMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::runChunk.name,
			A_BasicObject::class.java)

		/**
		 * The [CheckedMethod] for invoking [attemptPrimitive].
		 */
		val attemptPrimitiveMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::attemptPrimitive.name,
			A_BasicObject::class.java,
			A_Function::class.java,
			Primitive::class.java)

		/**
		 * Access the [reportWrongReturnType] method.
		 */
		val reportWrongReturnTypeMethod = instanceMethod(
			Interpreter::class.java,
			Interpreter::reportWrongReturnType.name,
			A_BasicObject::class.java,
			A_BasicObject::class.java,
			A_Type::class.java,
			Int::class.javaPrimitiveType!!,
			Int::class.javaPrimitiveType!!,
			Array<A_BasicObject>::class.java)

		/**
		 * Access the [fallBackToL1] method.
		 */
		val fallBackToL1Method = instanceMethod(
			Interpreter::class.java,
			Interpreter::fallBackToL1.name,
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

		/**
		 * Dump a limited amount of information about the receiver.
		 *
		 * @receiver
		 *   The [AvailObject] to describe.
		 */
		private fun AvailObject.loggingDetail(): String =
			when (typeTag)
			{
				TypeTag.CHARACTER_TAG,
				TypeTag.INTEGER_TAG,
				TypeTag.WHOLE_NUMBER_TAG,
				TypeTag.NATURAL_NUMBER_TAG,
				TypeTag.NEGATIVE_INFINITY_TAG,
				TypeTag.POSITIVE_INFINITY_TAG,
				TypeTag.FLOAT_TAG,
				TypeTag.DOUBLE_TAG,
				TypeTag.EXTENDED_INTEGER_TYPE_TAG -> "$this"
				TypeTag.TUPLE_TAG -> "tupleSize=$tupleSize" +
					joinToString(", ", " <", ">", 3, "…") {
						it.typeTag.shorterName
					}
				TypeTag.SET_TAG -> "setSize=$setSize"
				TypeTag.MAP_TAG -> "mapSize=$mapSize"
				TypeTag.ATOM_TAG -> atomName.asNativeString()
				TypeTag.BUNDLE_TAG -> message.atomName.asNativeString()
				TypeTag.OBJECT_TAG -> "object: ${nameForDebugger()}"
				TypeTag.OBJECT_TYPE_TAG -> "objectType: ${nameForDebugger()}"
				else -> typeTag.shorterName
			}

		/* Reifications statistics for interrupts. */

		/** Reification for time-slice. */
		val timeSliceInterruptStatistic =
			Statistic(StatisticReport.REIFICATIONS, "Interrupt: time slice")

		/** Reification for safe point. */
		val safePointInterruptStatistic =
			Statistic(StatisticReport.REIFICATIONS, "Interrupt: safe point")

		/** Reification for reaching maximum call depth. */
		val callDepthInterruptStatistic =
			Statistic(StatisticReport.REIFICATIONS, "Interrupt: call depth")

		val reificationRequestedFromOtherFiberStatistic =
			Statistic(
				StatisticReport.REIFICATIONS,
				"Interrupt: reification requested from other fiber")
	}
}
