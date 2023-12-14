/*
 * L2SimpleExecutableChunk.kt
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
package avail.interpreter.levelTwoSimple

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.decrementCountdownToReoptimize
import avail.descriptor.functions.A_RawFunction.Companion.localTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.functions.A_RawFunction.Companion.startingChunk
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithOuterType
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L1InstructionStepper
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.optimizer.ExecutableChunk
import avail.optimizer.OptimizationLevel
import avail.optimizer.StackReifier
import avail.utility.Strings.tabs
import java.util.logging.Level
import kotlin.math.max

/**
 * [L2SimpleExecutableChunk] is a subclass of [ExecutableChunk].  It contains a
 * simple sequence of instructions, executed in turn, unless one of them returns
 * a [StackReifier].  If the last instruction in the sequence answers null, the
 * final element of the register array is answered from the execution.  If a
 * reification happens, the registers are used to synthesize an [A_Continuation]
 * that will resume as an *L1* continuation (i.e., using the default [L2Chunk]).
 *
 * Very little optimization is performed at this level.  A bit of type deduction
 * helps eliminate spurious checks that would be necessary if method definitions
 * could be added or removed, but the invalidation mechanism handles that.
 * Calls can often be statically transformed to simple monomorphic invocation,
 * avoiding the dispatch trees.  When the target is proven to be monomorphic,
 * some primitives can be directly embedded (e.g., [P_InvokeWithTuple]), if they
 * can be proven not to fail, skipping unnecessary type safety checks.
 * Similarly, unnecessary return type checks can often be omitted as well.
 *
 * There is no register coloring, no dead code elimination, no special rewriting
 * of most primitives, or reworking into unboxed integer or floating point
 * operations.  Folding is attempted, however, if a monomorphic call indicates
 * it would be a [Primitive] function with the [Flag.CanFold] flag set.
 *
 * Flow is linear, and each instruction is responsible for handling reification,
 * if it can happen.  If the flow of instructions is to be restarted at any
 * point beyond the first, the continuation's slots are transferred to a fresh
 * array,
 *
 * Boxed register values are maintained in an array, in the
 * same manner as for [L1InstructionStepper].  The current function occupies
 * `register[0]`, then the frame slots.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2SimpleExecutableChunk
constructor(
	private val code: A_RawFunction,
	private val instructions: Array<L2SimpleInstruction>,
	private val nextOptimizationLevel: OptimizationLevel
) : ExecutableChunk
{
	/** Capture the primitive, if any, for easy access. */
	private val primitive: Primitive? = code.codePrimitive()

	override fun name(): String = "simple chunk for ${code.methodName}"

	override fun runChunk(interpreter: Interpreter, offset: Int): StackReifier?
	{
		// An offset of 0 is used when invoking the underlying function.  An
		// offset of -1 is used as a sentinel to indicate *not* to attempt the
		// function's corresponding primitive (which must be present), as it has
		// already failed, and we just wish to run the backup Avail nybblecodes.
		var savedArguments: List<AvailObject>? = null
		if (offset == 0 && primitive !== null)
		{
			if (primitive.hasFlag(Flag.CannotFail))
			{
				// Infallible primitive.  No need to save the arguments.
				val reifier = interpreter.attemptThePrimitive(
					interpreter.function!!, primitive)
				// Reified during the primitive invocation, or while setting up
				// a non-inline primitive to be in the right setup to run.
				// Either way, what should happen next is already captured
				// inside the reifier's action.
				return reifier
			}
			else
			{
				// Fallible primitive needs to save the arguments so that it can
				// run the fall-back code.
				// Happiest path first, attempt a primitive.
				savedArguments = interpreter.argsBuffer.toList()
				val reifier = interpreter.attemptThePrimitive(
					interpreter.function!!, primitive)
				// Reified during the primitive invocation, or while setting up a
				// non-inline primitive to be in the right setup to run. Either way,
				// what should happen next is already captured inside the reifier's
				// action.
				reifier?.let { return it }
				// Exit right away if the primitive was successful.
				if (interpreter.returnNow) return null
				// The primitive failed.
				assert(!primitive.hasFlag(Flag.CannotFail))
				// Put the arguments back, for the fallback nybblecodes to use.
				interpreter.argsBuffer.run {
					clear()
					addAll(savedArguments!!)
				}
			}
		}

		if (offset <= 0)
		{
			// Decrement the countdown to reoptimization, possibly reoptimizing.
			val chunkChanged = code.decrementCountdownToReoptimize {
					optimize: Boolean ->
				savedArguments ?: run {
					savedArguments = interpreter.argsBuffer.toList()
				}
				if (optimize)
				{
					OptimizationLevel.optimizationLevel(
						nextOptimizationLevel.ordinal
					).optimize(code, interpreter)
				}
				// Enter the newly constructed chunk, after ensuring the
				// arguments have been handed back to the interpreter.
				val chunk = code.startingChunk
				interpreter.chunk = chunk
				interpreter.setOffset(chunk.offsetAfterInitialTryPrimitive)
			}
			if (chunkChanged)
			{
				interpreter.argsBuffer.run {
					clear()
					addAll(savedArguments!!)
				}
				return null
			}
		}

		val registers = Array(code.numSlots + 1) { nil }
		registers[0] = interpreter.function as AvailObject
		if (offset <= 0)
		{
			// The function represented by the chunk is being invoked.
			// Capture arguments.
			val numArgs = code.numArgs()
			for (i in 1 .. numArgs)
				registers[i] = interpreter.argsBuffer[i - 1]
			// Create locals.
			for (i in 1 .. code.numLocals)
			{
				registers[i + numArgs] =
					newVariableWithOuterType(code.localTypeAt(i))
			}
			if (primitive !== null)
			{
				// The primitive either failed in this method (offset == 0), or
				// in an attempt prior to this method (offset == -1).  Either
				// way, put the failure value into the failure variable.
				try
				{
					val failureValue = interpreter.getLatestResult()
					assert(failureValue.notNil)
					registers[numArgs + 1].setValueNoCheck(failureValue)
				}
				catch (e: Exception)
				{
					assert(false) {
						"Failure variable was not suitable for failure value"
					}
				}
			}
		}
		else
		{
			// A continuation is being resumed at the given offset.
			if (Interpreter.debugL2)
			{
				val continuation = interpreter.getReifiedContinuation()!!
				var depth = 0
				var pointer: A_Continuation = continuation
				while (pointer.notNil)
				{
					depth++
					pointer = pointer.caller()
				}
				val prefix = interpreter.interpreterIndex.toString() +
					"-" + tabs(depth) +
					"|" + tabs(interpreter.unreifiedCallDepth())
				println("$prefix${offset - 1}: (reentering)")
				Interpreter.log(
					Interpreter.loggerDebugL2,
					Level.FINER,
					"{0}L2Simple REENTER: {1}:{2}, unreified={3}, reified={4}",
					interpreter.debugModeString,
					offset - 1,
					instructions[offset - 1],
					interpreter.unreifiedCallDepth(),
					depth)
			}
			// Calls to reenter() will set up the registers from the current
			// frame and pop it, but only if the return type is valid.
			// Otherwise they invoke the wrong-return-type hook function.
			val reifier = instructions[offset - 1].reenter(
				registers, interpreter)
			// The reenter() is allowed to reify, for example if it fetches the
			// returned value from the interpreter and it doesn't satisfy its
			// return type check.
			if (reifier !== null) return reifier
			// Also check if the reentry point noticed that the chunk was
			// invalid, and switched the interpreter's current chunk.  This can
			// also happen when an L2Simple continuation is bypassed by the
			// debugger.
			if (interpreter.chunk?.executableChunk !== this)
				return null
		}
		var off = max(offset, 0)
		val size = instructions.size
		if (Interpreter.debugL2)
		{
			// A hard-coded interpreter loop that can log.
			var depth = 0
			var pointer: A_Continuation = interpreter.getReifiedContinuation()!!
			while (pointer.notNil)
			{
				depth++
				pointer = pointer.caller()
			}
			val prefix = interpreter.interpreterIndex.toString() +
				"-" + tabs(depth) +
				"|" + tabs(interpreter.unreifiedCallDepth())
			while (off < size)
			{
				val instruction = instructions[off++]
				println(
					"$prefix${off - 1}: " +
						instruction.toString().replace(
							"\n",
							"\n" + tabs(
								depth + interpreter.unreifiedCallDepth() + 1)))
				Interpreter.log(
					Interpreter.loggerDebugL1,
					Level.FINER,
					"{0}L2Simple step: {1}",
					interpreter.debugModeString,
					instruction)
				val reifier = instruction.step(registers, interpreter)
				if (reifier !== null)
				{
					println(
						"$prefix${off - 1}: " +
							"(reifying, actual=${reifier.actuallyReify()})")
					return reifier
				}
			}
		}
		else
		{
			// A hard-coded interpreter loop that cannot log.
			while (off < size)
			{
				val reifier = instructions[off++].step(registers, interpreter)
				if (reifier !== null) return reifier
			}
		}
		interpreter.returnNow = true
		interpreter.setLatestResult(registers[registers.size - 1])
		return null
	}
}
