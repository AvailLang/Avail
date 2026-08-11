/*
 * L2Simple_AbstractReifiableInstruction.kt
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

import avail.AvailRuntime.HookType.IMPLICIT_OBSERVE
import avail.AvailRuntime.HookType.READ_UNASSIGNED_VARIABLE
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationExceptFrame
import avail.descriptor.functions.RegisterDumpDescriptor.Companion.createRegisterDump
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_Continuation.Companion.frameAtPut
import avail.descriptor.representation.A_RegisterDump
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Variable
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.StackReifier

/**
 * An abstraction for instructions that can trigger reification during their
 * execution.  This includes instructions that invoke functions, create a label,
 * or read/write a variable (which may fail and invoke handler code that may
 * reify).
 */
abstract class L2Simple_AbstractReifiableInstruction
constructor(
	nextOffset: Offset,
	val reentryOffset: Offset,
	val stateOfL1: StateOfL1,
	val defaultEntryPoint: DefaultEntryPoint
) : L2SimpleInstruction(nextOffset)
{
	final override val canBePostponed get() = false

	/**
	 * Given the complete [RegisterSet], extract the ones listed in my
	 * [StateOfL1.allLiveRegisters] and create an [A_RegisterDump].
	 */
	protected fun makeRegisterDump(
		registers: RegisterSet,
	): AvailObject = createRegisterDump(
		defaultEntryPoint.offset(),
		emptyTuple,
		registers[stateOfL1.allLiveRegisters!!],
		emptyLongArray
	)

	/**
	 * Create a continuation with the given data.  The previously captured
	 * [StateOfL1.liveSlots] indicate which registers to read to populate the
	 * continuation's slots.  A value of `0` indicates that [nil] should be used
	 * instead.
	 */
	protected fun createContinuation(
		caller: A_Continuation,
		registers: RegisterSet,
		thisChunk: L2Chunk,
		expectedType: A_Type?,
		offset: Offset
	): A_Continuation
	{
		val continuation = createContinuationExceptFrame(
			registers.function,
			caller,
			makeRegisterDump(registers),
			stateOfL1.pc,
			stateOfL1.stackp,
			thisChunk,
			offset.value)
		stateOfL1.liveSlots.forEachIndexed { i, read ->
			if (read.value != 0)
				continuation.frameAtPut(i + 1, registers[read])
		}
		if (expectedType != null)
		{
			// Write the expectedType onto the continuation at the current
			// stackp.
			continuation.frameAtPut(
				stateOfL1.stackp,
				expectedType as AvailObject)
		}
		return continuation
	}

	/**
	 * A [VariableGetException] has occurred.  Invoke the
	 * [READ_UNASSIGNED_VARIABLE] hook function, which, because it is ⊥-valued,
	 * can only reify.  Eventually answer that [StackReifier].
	 */
	fun handleVariableGetException(
		e: VariableGetException,
		interpreter: Interpreter,
		registers: RegisterSet)
	{
		// The variable had no value.
		assert(e.numericCode.equals(
			E_CANNOT_READ_UNASSIGNED_VARIABLE.numericCode()))
		val thisChunk = interpreter.chunk!!
		val unassignedVariableFunction =
			interpreter.runtime[READ_UNASSIGNED_VARIABLE]
		interpreter.argsBuffer.clear()
		val valueMustBeNull =
			interpreter.invokeFunction(unassignedVariableFunction)
		assert(valueMustBeNull == null)
		// It's ⊥-valued, so it must eventually reify, not return.
		val reifier = interpreter.currentReifier!!
		if (reifier.actuallyReify)
		{
			reifier.pushAction {
				setReifiedContinuation(
					createContinuation(
						getReifiedContinuation()!!,
						registers,
						thisChunk,
						bottom,
						Offset.UNREACHABLE))
			}
		}
	}

	/**
	 * A [VariableSetException] has occurred.  Run the implicit-observe handler.
	 */
	fun handleVariableSetException(
		e: VariableSetException,
		variable: A_Variable,
		value: AvailObject,
		interpreter: Interpreter,
		registers: RegisterSet
	): Offset
	{
		// The variable had an observer attached.
		assert(e.numericCode.equals(
			E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode()))
		val thisChunk = interpreter.chunk!!
		interpreter.argsBuffer.run {
			clear()
			add(Interpreter.assignmentFunction() as AvailObject)
			add(tuple(variable, value) as AvailObject)
		}
		val valueOrNull =
			interpreter.invokeFunction(interpreter.runtime[IMPLICIT_OBSERVE])
		// It's top-valued, so it *might* reify, or might not.
		if (valueOrNull !== null)
		{
			interpreter.setLatestResult(valueOrNull)
			return nextOffset
		}
		val reifier = interpreter.currentReifier!!
		if (reifier.actuallyReify)
		{
			reifier.pushAction {
				setReifiedContinuation(
					createContinuation(
						getReifiedContinuation()!!,
						registers,
						thisChunk,
						TOP(),
						reentryOffset))
			}
		}
		return REIFY_NOW
	}

	companion object
	{
		/** A Reusable empty [LongArray]. */
		val emptyLongArray = LongArray(0)
	}
}
