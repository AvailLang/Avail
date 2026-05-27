/*
 * L2Simple_RunInfalliblePrimitiveNoCheck.kt
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

import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write

/**
 * Invoke a primitive which is infallible for the given arguments, and does not
 * invoke, suspend, or switch continuations.  I.e., it will run to completion
 * and return some value (possibly [nil]).
 *
 * The call from which this is distilled must have an expectedType no stronger
 * than the type that the primitive is guaranteed to produce for these
 * arguments. If the expectedType is too strong for this primitive's guarantees
 * (due to a semantic restriction), an [L2Simple_Invoke] must be generated
 * instead.
 */
class L2Simple_RunInfalliblePrimitiveNoCheck
constructor(
	nextOffset: Offset = Offset.NEXT,
	val function: A_Function,
	val rawFunction: A_RawFunction,
	val arguments: ReadArray,
	val answer: Write
) : L2SimpleInstruction(nextOffset)
{
	val primitive = rawFunction.codePrimitive()!!

	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		val args = interpreter.argsBuffer
		args.clear()
		arguments.forEachIndexed { _, read ->
			args.add(registers[read])
		}
		interpreter.function = function
		val valueOrNull = interpreter.afterAttemptPrimitive(
			primitive,
			interpreter.beforeAttemptPrimitive(primitive),
			primitive.attempt(interpreter))
		registers[answer] = valueOrNull!! as AvailObject
		interpreter.function = registers.function
		return nextOffset
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_RunInfalliblePrimitiveNoCheck(
			nextOffset = target(nextOffset),
			function = function,
			rawFunction = rawFunction,
			arguments = read(arguments),
			answer = write(answer))
}
