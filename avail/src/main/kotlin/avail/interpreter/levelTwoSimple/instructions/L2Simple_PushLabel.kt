/*
 * L2Simple_PushLabel.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwoSimple.instructions

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.registerDump
import avail.descriptor.functions.A_Continuation.Companion.replacingCaller
import avail.descriptor.functions.ContinuationDescriptor.Companion.createDummyContinuation
import avail.descriptor.functions.ContinuationDescriptor.Companion.createLabelContinuation
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.StackReifier
import avail.performance.Statistic
import avail.performance.StatisticReport

/**
 * Construct a label for restarting or exiting the current continuation.  Note
 * that this *may* cause reification to force the call chain to be reified, but
 * the reifier block answers CONTINUE_FIBER, so no invalidation is possible
 * before reentry happens.
 *
 * Write the label (an [A_Continuation]) to [answer], and continue execution
 * where it left off.  If the label is later restarted, its caller will be the
 * same as the current virtual continuation, and its arguments will be the same
 * if using [P_RestartContinuation], or the ones provided explicitly if using
 * [P_RestartContinuationWithArguments].
 */
class L2Simple_PushLabel(
	nextOffset: Offset = Offset.NEXT,
	stateOfL1: StateOfL1,
	val originalArguments: ReadArray,
	val answer: Write
) : L2Simple_AbstractReifiableInstruction(nextOffset, stateOfL1)
{
	override val canBePostponed
		get() = true

	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		val thisChunk = interpreter.chunk as L2SimpleChunk
		val function = registers.function
		if (interpreter.callerIsReified())
		{
			// Skip the reification step, since the caller is already
			// conveniently reified.
			val label = createLabelContinuation(
				function = function,
				caller = interpreter.getReifiedContinuation()!!.makeImmutable(),
				startingChunk = thisChunk,
				// Indicates a label.
				startingOffset = 0,
				args = List(originalArguments.size) {
					registers[originalArguments[it]]
				})
			label.makeSubobjectsImmutable()
			registers[answer] = label
			return nextOffset
		}
		// Slower path.  Reify the caller, allowing the [reenter] to push a
		// label.  If this is a loop, the next pass's label creation will see
		// the caller has already been reified, and be able to use the fast path
		// above.
		val reifier = StackReifier(true, reificationBeforeLabelCreationStat) {
			val caller = interpreter.getReifiedContinuation()!!.makeImmutable()
			// Create a *dummy* continuation capturing the registers, and
			// push it on the frame stack.  When this instruction immediately
			// reenters, it will restore the register state from the dummy
			// continuation's register dump, then push a label.
			var dummyContinuation: A_Continuation = createDummyContinuation(
				function = function,
				registerDump = makeRegisterDump(registers),
				levelTwoChunk = thisChunk,
				levelTwoOffset = nextOffset.value)
			dummyContinuation = dummyContinuation.replacingCaller(caller)
			interpreter.setReifiedContinuation(dummyContinuation)
			// Now we tell the interpreter to reenter the dummy continuation,
			// which will extract the register dump back into the new
			// RegisterSet, and continue running the chunk at the next offset.
			interpreter.function = function
			interpreter.chunk = thisChunk
			interpreter.offset = nextOffset.value
			StackReifier.AfterReification.CONTINUE_FIBER
		}
		interpreter.currentReifier = reifier
		return REIFY_NOW
	}

	/**
	 * The chunk can't actually become invalid during a push-label instruction,
	 * but we still need a dummy value.
	 */
	override fun defaultL1EntryPointIfInvalid() = DefaultEntryPoint.TRANSIENT

	/**
	 * A dummy continuation has resumed *immediately* after a pushLabel caused
	 * reification.  Pop the dummy continuation, using its register dump to
	 * restore the [registers], then create and push a label.  Note that we
	 * don't have to check validity of the reentering chunk, since the
	 * interpreter offered no opportunity to suspend to a safe-point, which is
	 * the only place where invalidation can happen.
	 */
	override fun reenter(
		registers: RegisterSet,
		interpreter: Interpreter
	): Boolean
	{
		assert(interpreter.chunk!!.isValid)
		val con = interpreter.popContinuation()
		restoreFromDump(con.registerDump, registers)
		// Register state has been restored.  Now create and push the label.
		val label = createLabelContinuation(
			function = registers.function,
			caller = interpreter.getReifiedContinuation()!!.makeImmutable(),
			startingChunk = interpreter.chunk!!,
			// A block can't have both a primitive and a label.
			startingOffset = 0,
			args = List(originalArguments.size) {
				registers[originalArguments[it]]
			})
		// Freeze all fields of the new object, including
		// its caller, function, and args.
		label.makeSubobjectsImmutable()

		// Push that label.
		registers[answer] = label as AvailObject
		return true
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_PushLabel(
			nextOffset = target(nextOffset),
			stateOfL1 = state(stateOfL1),
			originalArguments = read(originalArguments),
			answer = write(answer))

	companion object
	{
		/**
		 * Statistic for when a pushLabel in an L2Simple chunk is forced to
		 * perform reification (so that the label's caller is correct).
		 */
		private val reificationBeforeLabelCreationStat = Statistic(
			StatisticReport.REIFICATIONS,
			"L2Simple reification before label creation")
	}
}
