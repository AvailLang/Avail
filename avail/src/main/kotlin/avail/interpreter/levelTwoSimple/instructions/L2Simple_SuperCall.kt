/*
 * L2Simple_SuperCall.kt
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

import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Method.Companion.lookupByTypesFromTuple
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.isAbstractDefinition
import avail.descriptor.methods.A_Sendable.Companion.isForwardDefinition
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.exceptions.MethodDefinitionException
import avail.exceptions.MethodDefinitionException.Companion.abstractMethod
import avail.exceptions.MethodDefinitionException.Companion.forwardMethod
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write

/**
 * Perform a lookup using arguments taken from the stack as for
 * [L2Simple_GeneralCall], but where the lookup is forced to look at or above
 * the constraining tuple type.
 */
class L2Simple_SuperCall(
	nextOffset: Offset = Offset.NEXT,
	stateOfL1: StateOfL1,
	expectedType: A_Type,
	mustCheck: Boolean,
	answer: Write,
	val bundle: A_Bundle,
	val superUnionType: A_Type,
	val arguments: ReadArray
) : L2Simple_AbstractInvokerInstruction(
	nextOffset,
	stateOfL1,
	expectedType,
	mustCheck,
	answer)
{
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
		val typesTuple: A_Tuple =
			generateObjectTupleFrom(args.size) { index: Int ->
				val arg = args[index - 1]
				instanceTypeOrMetaOn(arg)
					.typeUnion(superUnionType.typeAtIndex(index))
			}
		val matching: A_Definition = try
		{
			bundle.bundleMethod
				.lookupByTypesFromTuple(typesTuple)
				.also {
					when
					{
						it.isAbstractDefinition() -> throw abstractMethod()
						it.isForwardDefinition() -> throw forwardMethod()
					}
				}
		}
		catch (e: MethodDefinitionException)
		{
			return handleFailedLookup(interpreter, args, e, registers, bundle)
		}
		// Lookup was successful.  Invoke it, with the arguments that are still
		// in the interpreter's argsBuffer.
		return invocationHelper(interpreter, registers, matching.bodyBlock())
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_SuperCall(
			nextOffset = target(nextOffset),
			stateOfL1 = state(stateOfL1),
			expectedType = expectedType,
			mustCheck = mustCheck,
			answer = write(answer),
			bundle = bundle,
			superUnionType = superUnionType,
			arguments = read(arguments))
}
