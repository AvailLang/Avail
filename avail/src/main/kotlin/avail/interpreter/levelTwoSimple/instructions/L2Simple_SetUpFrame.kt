/*
 * L2Simple_SetUpFrame.kt
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

import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.localTypeAt
import avail.descriptor.representation.A_RawFunction.Companion.numArgs
import avail.descriptor.representation.A_RawFunction.Companion.numLocals
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithOuterType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.levelTwoSimple.instructions.registers.WriteArray
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.utility.notNullAnd

/**
 * Set up a frame, populating arguments from [Interpreter.argsBuffer] and
 * creating local variables.  Also, if this chunk is for a primitive (that has
 * already failed if we're here), capture the failure code.
 */
class L2Simple_SetUpFrame(
	nextOffset: Offset = Offset.NEXT,
	val rawFunction: A_RawFunction,
	val argumentsToCapture: WriteArray,
	val localsToPopulate: WriteArray,
	val primitiveFailureCode: Write?
) : L2SimpleInstruction(nextOffset)
{
	val numArgs: Int = rawFunction.numArgs()

	val numLocals: Int = rawFunction.numLocals

	val localVarTypes = (1..numLocals).map {
		rawFunction.localTypeAt(it)
	}.toTypedArray()

	init {
		assert(argumentsToCapture.size == numArgs)
		assert((primitiveFailureCode != null)
			== rawFunction.codePrimitive().notNullAnd {
				!hasFlag(CannotFail)
			})
	}

	override val canBePostponed: Boolean get() = false

	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		assert(interpreter.chunk!!.isValid)

		// Capture the arguments into registers.
		val args = interpreter.argsBuffer
		assert(args.size == numArgs)
		args.forEachIndexed { i, argumentValue ->
			registers[argumentsToCapture[i]] = argumentValue
		}

		// Create locals.
		localsToPopulate.forEachIndexed { i, write ->
			registers[write] =
				newVariableWithOuterType(localVarTypes[i])
		}

		// Capture the primitive failure code, if any.
		primitiveFailureCode?.let {
			registers[it] = interpreter.getLatestResult()
		}
		return nextOffset
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_SetUpFrame(
			nextOffset = target(nextOffset),
			rawFunction = rawFunction,
			argumentsToCapture = write(argumentsToCapture),
			localsToPopulate = write(localsToPopulate),
			primitiveFailureCode = primitiveFailureCode?.let { write(it) })
}
