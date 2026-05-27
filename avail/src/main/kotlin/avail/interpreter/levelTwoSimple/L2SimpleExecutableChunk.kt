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
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.startingChunk
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.DebugRenderer
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelTwo.L1InstructionStepper
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwoSimple.instructions.L2SimpleInstruction
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.HIGHEST_LEGAL_OFFSET_int
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.RETURN_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.Primitive.Flag
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.ExecutableChunk
import avail.optimizer.OptimizationLevel
import avail.optimizer.StackReifier
import avail.utility.Strings.increaseIndentation
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
 * @constructor
 * Creaate this chunk from the given instructions.
 *
 * @property code
 *   The [A_RawFunction] which was translated.
 * @property instructions
 *   The [Array] of [L2SimpleInstruction]s comprising this chunk.
 * @property optimizationLevel
 *   The [OptimizationLevel] at which this chunk was created.
 * @property registerCount
 *   How big a [RegisterSet] to create for this chunk.  These are zero-based,
 *   but 0 is reserved for the current function.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2SimpleExecutableChunk
constructor(
	private val code: A_RawFunction,
	private val instructions: Array<L2SimpleInstruction>,
	private val optimizationLevel: OptimizationLevel,
	private val registerCount: Int
) : ExecutableChunk, DebugRenderer
{
	/** Capture the primitive, if any, for easy access. */
	private val primitive: Primitive? = code.codePrimitive()

	override fun name(): String = "L2Simple chunk: ${code.methodName}"

	override fun runChunk(interpreter: Interpreter, offset: Int): A_BasicObject?
	{
		// An offset of 0 is used when invoking the underlying function.  An
		// offset of -1 is used as a sentinel to indicate *not* to attempt the
		// function's corresponding primitive (which must be present), as it has
		// already failed, and we just wish to run the backup Avail nybblecodes.
		if (offset == 0 && primitive !== null)
		{
			// Happiest path first, attempt a primitive.
			val value = interpreter.attemptPrimitive(
				interpreter.function!!, primitive)
			// Handle success.
			if (value !== null) return value
			// Handle reification.
			if (interpreter.currentReifier !== null) return value
			// Handle failure.
			assert(!primitive.hasFlag(Flag.CannotFail))
			// Fall through to handle primitive failure.
		}

		if (offset <= 0)
		{
			// Decrement the countdown to reoptimization, possibly reoptimizing.
			if (code.decrementCountdownToReoptimize())
			{
				var savedArguments = interpreter.argsBuffer.toTypedArray()
				OptimizationLevel.optimizationLevel(
					optimizationLevel.ordinal
				).optimize(code, interpreter)
				// Enter the newly constructed chunk, after ensuring the
				// arguments have been handed back to the interpreter.
				val chunk = code.startingChunk
				interpreter.chunk = chunk
				//interpreter.setOffset(chunk.offsetAfterInitialTryPrimitive)
				interpreter.argsBuffer.run {
					clear()
					addAll(savedArguments)
				}
				// Directly invoke the new chunk in its place.
				return chunk.executableChunk.runChunk(
					interpreter,
					chunk.offsetAfterInitialTryPrimitive)
			}
		}

		val registers = RegisterSet(Array(registerCount) { nil })
		registers.function = interpreter.function!! as AvailObject
		if (offset > 0)
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
					pointer = pointer.caller
				}
				val instruction = instructions[offset - 1]
				val instructionText = increaseIndentation(
					instruction.toString(),
					interpreter.unreifiedCallDepth() + 2)
				Interpreter.log(
					Interpreter.loggerDebugL2,
					Level.FINER,
					"{0}L2Simple REENTER: {1}:{2}",
					interpreter.debugModeString,
					offset - 1,
					instructionText)
			}
			// Calls to reenter() will set up the registers from the current
			// frame and pop it, but only if the return type is valid.
			// Otherwise they invoke the wrong-return-type hook function.
			val reentryInstruction = instructions[offset - 1]
			if (!interpreter.checkValidity(
					reentryInstruction.defaultL1EntryPointIfInvalid().offset()))
			{
				// The chunk has become invalid, which can only happen while the
				// fiber is fully reified.  The validity check has switched the
				// chunk and offset already as a convenience.
				assert(interpreter.chunk!! === DefaultL1Chunk)
				return interpreter.runChunk()
			}
			val keepRunning = instructions[offset - 1]
				.reenter(registers, interpreter)
			// The reenter() is allowed to reify, for example if it fetches the
			// returned value from the interpreter and it doesn't satisfy its
			// return type check.
			if (!keepRunning)
			{
				assert(interpreter.currentReifier !== null)
				return null
			}
		}
		var off = Offset(max(offset, 0))
		if (Interpreter.debugL2)
		{
			// A hard-coded interpreter loop that can log.
			var depth = 0
			var pointer: A_Continuation = interpreter.getReifiedContinuation()!!
			while (pointer.notNil)
			{
				depth++
				pointer = pointer.caller
			}
			while (off.value < HIGHEST_LEGAL_OFFSET_int)
			{
				val instruction = instructions[off.value]
				val instructionText = increaseIndentation(
					instruction.toString(off.value),
					interpreter.unreifiedCallDepth() + 2)
				// Extra logging shows what the step affects.
				Interpreter.log(
					Interpreter.loggerDebugL1,
					Level.FINER,
					"{0}L2Simple step: {1}:{2}",
					interpreter.debugModeString,
					off.value,
					instructionText)
				off = instruction.step(registers, interpreter)
			}
			val resultOrNull = when (off)
			{
				REIFY_NOW -> null
				else ->
				{
					// The return value is stored in `interpreter.latestResult`.
					assert(off == RETURN_NOW)
					interpreter.latestResultOrNull()!!
				}
			}
			log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}L2Simple return ({1})",
				interpreter.debugModeString,
				resultOrNull?.typeTag?.name)
			return resultOrNull
		}
		else
		{
			// A hard-coded interpreter loop that cannot log.
			while (off.value <= HIGHEST_LEGAL_OFFSET_int)
			{
				off = instructions[off.value].step(registers, interpreter)
			}
			when (off)
			{
				REIFY_NOW -> return null
				else ->
				{
					// The return value is stored in `interpreter.latestResult`.
					assert(off == RETURN_NOW)
					return interpreter.latestResultOrNull()!!
				}
			}
		}
	}

	override fun nameForDebugger(): String = name()

	override fun describeForDebugger() = buildList<Pair<String, Any?>> {
		// Produce the current function being executed...
		add("code" to code)
		if (primitive !== null) add ("primitive" to primitive)
		add("instructions" to instructions)
		add("Pretty" to instructions.withIndex().joinToString("\n") {
			(i, instr) -> "$i: ${instr.toString(i)}"
		})
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
}
