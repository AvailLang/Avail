/*
 * DefaultL1ExecutableChunk.kt
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
 *   may be used to endorse or promote products derived set this software
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
package avail.optimizer

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.function
import avail.descriptor.functions.A_Continuation.Companion.numSlots
import avail.descriptor.functions.A_Continuation.Companion.pc
import avail.descriptor.functions.A_Continuation.Companion.replacingCaller
import avail.descriptor.functions.A_Continuation.Companion.stackAt
import avail.descriptor.functions.A_Continuation.Companion.stackp
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.localTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationWithFrame
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithOuterType
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelTwo.L2AbstractInstruction
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.operation.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE.UnreachableCodeException
import avail.interpreter.levelTwoSimple.L2SimpleExecutableChunk
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.AFTER_PRIMITIVE_FAILURE
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.AFTER_REIFICATION_FOR_LABEL_CREATION
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.INITIAL_ENTRY
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.REENTRY_FROM_REIFIED_CALL
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.RESUME
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.UNREACHABLE_ENTRY
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk.executableChunk
import avail.optimizer.DefaultL1ExecutableChunk.runChunk
import avail.optimizer.StackReifier.AfterReification.SWITCH_FROM_FIBER
import avail.optimizer.jvm.JVMChunk
import avail.performance.Statistic
import avail.performance.StatisticReport.REIFICATIONS
import avail.utility.notNullAnd
import java.util.logging.Level
import kotlin.reflect.full.isSuperclassOf

/**
 * An [DefaultL1ExecutableChunk] is the chunk that runs Avail [A_RawFunction]
 * until they get translated to an [L2SimpleExecutableChunk] or ultimately
 * [JVMChunk].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object DefaultL1ExecutableChunk : ExecutableChunk
{
	/**
	 * A dummy [L2Chunk] whose [executableChunk] is the
	 * [DefaultL1ExecutableChunk].
	 */
	object DefaultL1Chunk : L2Chunk(
		null,
		AFTER_PRIMITIVE_FAILURE.offset,
		emptySet)
	{
		override val executableChunk: ExecutableChunk
			get() = DefaultL1ExecutableChunk
		override val instructions: List<L2AbstractInstruction>
			get() = emptyList()
		override fun dumpChunk() = "The default L1 chunk"
	}

	/**
	 * Offsets used in the [runChunk] method.
	 */
	sealed class DefaultEntryPoint(private val offsetInt: Int)
	{
		/**
		 * Lookup the offset from the (final) field, which is slower than
		 * hitting the specific const val offset field, due to HotSpot's
		 * limitations caused by (at least) allowing reflective clobbering of
		 * final fields.  And determining when object construction is done.
		 */
		fun offset(): Int = offsetInt

		val name: String = this::class.java.simpleName

		/**
		 * The universal initial entry point for all chunks, representing the
		 * start of execution of a function.
		 */
		object INITIAL_ENTRY : DefaultEntryPoint(0)
		{
			const val offset = 0
		}

		/**
		 * The entry point to jump to when the primitive fails.
		 */
		object AFTER_PRIMITIVE_FAILURE : DefaultEntryPoint(1)
		{
			const val offset = 1
		}

		/**
		 * This entry point is invoked after reification takes place, expecting
		 * the label (a continuation containing only the arguments) to have been
		 * pushed in the frame.
		 */
		object AFTER_REIFICATION_FOR_LABEL_CREATION : DefaultEntryPoint(2)
		{
			const val offset = 2
		}

		object REENTRY_FROM_REIFIED_CALL : DefaultEntryPoint(3)
		{
			const val offset = 3
		}

		object RESUME : DefaultEntryPoint(4)
		{
			const val offset = 4
		}

		object UNREACHABLE_ENTRY : DefaultEntryPoint(5)
		{
			const val offset = 5
		}

		object TRANSIENT : DefaultEntryPoint(-1)
		{
			const val offset = -1
		}
	}

	object DefaultEntryPointCatalog
	{
		const val maxEntryPointOffset = 5

		val allEntryPoints =
			DefaultEntryPoint::class.nestedClasses
				.filter { DefaultEntryPoint::class.isSuperclassOf(it) }
				.map { it.objectInstance as DefaultEntryPoint }
				.also { assert(it.size == maxEntryPointOffset) }
	}

	/**
	 * Answer a descriptive (non-unique) name for the [DefaultL1ExecutableChunk].
	 *
	 * @return
	 *   The effective name of the chunk.
	 */
	override fun name() = "«L1»"

	/**
	 * Run the [DefaultL1ExecutableChunk] to completion. Note that a reification
	 * request may cut this short. For an initial invocation, the
	 * [Interpreter.argsBuffer] will have been set up for the call. For a return
	 * into this continuation, the offset will refer to code that will rebuild
	 * the register set from the top reified continuation, using the
	 * [Interpreter.latestResult]. For resuming the continuation, the offset
	 * will point to code that also rebuilds the register set from the top
	 * reified continuation, but it won't expect a return value. These re-entry
	 * points should perform validity checks on the chunk, allowing an orderly
	 * off-ramp into this default chunk (which simply interprets the L1
	 * nybblecodes).
	 *
	 * @param interpreter
	 *   An interpreter that is appropriately setup to execute the receiver.
	 * @param offset
	 *   The offset at which to begin execution.
	 * @return
	 *   The returned value if returning normally, otherwise `null` to indicate
	 *   the [Interpreter.currentReifier] has been set for reification.
	 */
	override fun runChunk(
		interpreter: Interpreter,
		offset: Int
	): A_BasicObject?
	{
		if (offset == INITIAL_ENTRY.offset)
		{
			val function = interpreter.function!!
			val code = function.code()
			// The chunk is a fresh invocation of the function.  Try the
			// primitive first, if it exists.  Note that this is on the fastest
			// path because it's by far the most common.
			val primitive = code.codePrimitive()
			if (primitive !== null)
			{
				val result = interpreter.attemptPrimitive(function, primitive)
				// Success:
				if (result !== null) return result
				// Reification:
				if (interpreter.currentReifier !== null) return null
				// Failure: continue running the body, with the failure
				// value already stashed in interpreter.latestResult.
			}
		}
		if (offset <= AFTER_PRIMITIVE_FAILURE.offset)
		{
			// Post-primitive or no-primitive.  This is the second-most common
			// path, specifically the no-primitive case, although the
			// post-primitive path for P_CatchException is also important.
			if (L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
				.decrement(interpreter, 0))
			{
				// Run the new replacement chunk instead.
				return interpreter.runChunk()
			}
			// Prepare the new frame.
			val frame = prepareNewFrame(interpreter)

			// Check for an interrupt.
			if (interpreter.isInterruptRequested)
			{
				return reifyForInterrupt(interpreter, frame)
			}
			// No interrupt was requested.  Run the nybblecodes from the
			// beginning, with an initially empty stack.
			return interpreter.levelOneStepper.run(
				frame = frame,
				startingPc = 1,
				startingStackp = frame.size)
		}
		if (offset == AFTER_REIFICATION_FOR_LABEL_CREATION.offset)
		{
			// The call stack was just reified, except for the top frame which
			// was about to create a label (but needed to reify first).  It
			// will have stashed the frame, pc, and stackp necessary to resume
			// execution immediately.  Even better, the pc has been adjusted to
			// point at the push-label instruction again, but this time it will
			// notice that the call stack has been reified, and create the label
			// without a problem.
			val stepper = interpreter.levelOneStepper
			val retrievedFrame = stepper.stashedFrameAtPushLabel!!
			// Safety.
			stepper.stashedFrameAtPushLabel = null
			return stepper.run(
				frame = retrievedFrame,
				startingPc = stepper.stashedPcAtPushLabel,
				startingStackp = stepper.stashedStackpAtPushLabel)
		}
		if (offset == REENTRY_FROM_REIFIED_CALL.offset)
		{
			// An interrupt was just processed.  Run the nybblecodes starting
			// at the beginning.
			if (Interpreter.debugL1)
			{
				log(
					Interpreter.loggerDebugL1,
					Level.FINER,
					"{0}Reenter L1 from call",
					interpreter.debugModeString)
			}
			val continuation = interpreter.popContinuation()
			val frame = frameFromContinuation(continuation)
			val returnValue = interpreter.getLatestResult()
			val returneeFunction = interpreter.function!!
			assert(returneeFunction === continuation.function)
			val pc = continuation.pc
			val stackp = continuation.stackp
			val expectedReturnType = frame[stackp]
			if (!returnValue.isInstanceOf(expectedReturnType))
			{
				val returnCheckReifier =
					interpreter.levelOneStepper.checkReturnType(
						returnValue,
						expectedReturnType,
						returneeFunction,
						frame,
						pc,
						stackp)!!
				interpreter.currentReifier = returnCheckReifier
				return null
			}
			frame[stackp] = returnValue
			return interpreter.levelOneStepper.run(
				frame = frame,
				startingPc = pc,
				startingStackp = stackp)
		}
		if (offset == RESUME.offset)
		{
			// An interrupt was just processed.  Run the nybblecodes starting
			// at the beginning.
			if (Interpreter.debugL1)
			{
				log(
					Interpreter.loggerDebugL1,
					Level.FINER,
					"{0}Reenter L1 from interrupt (or P_ResumeContinuation)",
					interpreter.debugModeString)
			}
			val continuation = interpreter.popContinuation()
			return interpreter.levelOneStepper.run(
				frame = frameFromContinuation(continuation),
				startingPc = continuation.pc,
				startingStackp = continuation.stackp)
		}
		if (offset == UNREACHABLE_ENTRY.offset)
		{
			// Some continuations created by the L1 stepper need to refere to an
			// offset (this one) that should never be reached, say as a place to
			// jump upon return from a call that must not return.
			throw UnreachableCodeException()
		}
		JVMChunk.badOffset(offset)
	}

	private fun prepareNewFrame(
		interpreter: Interpreter
	): Array<AvailObject>
	{
		var function = interpreter.function!!
		val code = function.code()
		val numArgs = code.numArgs()
		val numLocals = code.numLocals
		val numArgsAndLocals = numArgs + numLocals
		val numSlots = code.numSlots
		val frame = Array(numSlots + 1) { i ->
			when
			{
				// The 0th position will never be accessed.
				i == 0 -> nil
				// Populate the arguments from argsBuffer.
				i <= numArgs -> interpreter.argsBuffer[i - 1]
				// Create actual local variables.
				i <= numArgsAndLocals ->
					newVariableWithOuterType(code.localTypeAt(i - numArgs))

				else ->
				{
					// Write nil into the remaining stack slots. These
					// values should not encounter any kind of ordinary use,
					// but they must still be transferred into a
					// continuation during reification.  Therefore, don't
					// use Java nulls here.
					nil
				}
			}
		}
		if (code.codePrimitive().notNullAnd { !hasFlag(CannotFail) })
		{
			frame[numArgs + numLocals + 1] = interpreter.getLatestResult()
		}
		return frame
	}

	/**
	 * Create a fram ([Array]&lt;[AvailObject]&gt;) for continuing the given
	 * [A_Continuation].
	 */
	private fun frameFromContinuation(
		continuation: A_Continuation
	): Array<AvailObject>
	{
		return Array(continuation.numSlots() + 1) {
			when (it)
			{
				0 -> nil
				else -> continuation.stackAt(it)
			}
		}
	}

	/**
	 * Construct a [StackReifier] that will reify the current stack frame for
	 * eventual resumption, process an interrupt after the reification, then
	 * eventually continue running at the interrupt point.
	 */
	fun reifyForInterrupt(
		interpreter: Interpreter,
		frame: Array<AvailObject>
	): A_BasicObject?
	{
		// Build an interrupted continuation, reify the rest of the stack, and
		// push the continuation onto the reified stack. Then process the
		// interrupt, which may or may not suspend the fiber.
		val function = interpreter.function!!
		val code = function.code()
		val numSlots = code.numSlots
		val continuation: A_Continuation = createContinuationWithFrame(
			function = function,
			caller = nil,
			registerDump = nil,
			pc = 1,  // start of function
			stackp = numSlots + 1,  // empty stack
			levelTwoChunk = DefaultL1Chunk,
			levelTwoOffset = DefaultEntryPoint.RESUME.offset,
			frameValues = listOf(*frame),
			zeroBasedStartIndex = 1)
		// Push the continuation from above onto the reified stack.
		interpreter.currentReifier = StackReifier(
			true,
			reificationForInterruptInL1Stat
		) {
			// Push the continuation from above onto the reified stack.
			interpreter.setReifiedContinuation(
				continuation.replacingCaller(
					interpreter.getReifiedContinuation()!!))
			interpreter.processInterrupt(
				interpreter.getReifiedContinuation()!!)
			SWITCH_FROM_FIBER
		}
		return null
	}

	/** [Statistic] for reifying in L1 interrupt-handler preamble. */
	private val reificationForInterruptInL1Stat = Statistic(
		REIFICATIONS, "Reification for interrupt in L1 preamble")
}
