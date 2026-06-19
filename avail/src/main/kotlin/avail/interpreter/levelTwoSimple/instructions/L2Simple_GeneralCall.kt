/*
 * L2Simple_GeneralCall.kt
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

import avail.AvailRuntime
import avail.descriptor.methods.MethodDescriptor.Companion.runtimeDispatcher
import avail.descriptor.representation.A_Bundle
import avail.descriptor.representation.A_Definition
import avail.descriptor.representation.A_RawFunction.Companion.lookupStat
import avail.descriptor.representation.A_Sendable.Companion.bodyBlock
import avail.descriptor.representation.A_Sendable.Companion.isAbstractDefinition
import avail.descriptor.representation.A_Sendable.Companion.isForwardDefinition
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Type
import avail.dispatch.LookupStatistics
import avail.dispatch.LookupTree
import avail.exceptions.MethodDefinitionException
import avail.exceptions.MethodDefinitionException.Companion.abstractMethod
import avail.exceptions.MethodDefinitionException.Companion.extractUniqueMethod
import avail.exceptions.MethodDefinitionException.Companion.forwardMethod
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write

/**
 * Extract the arguments from the register set, and use them to look up a method
 * definition in polymorphic method.  If the lookup is successful, invoke that
 * function, otherwise invoke the [AvailRuntime.invalidMessageSendFunction] with
 * suitably packaged arguments and the lookup failure code.
 */
class L2Simple_GeneralCall(
	nextOffset: Offset,
	reentryOffset: Offset,
	stateOfL1: StateOfL1,
	expectedType: A_Type,
	mustCheck: Boolean,
	answer: Write,
	val bundle: A_Bundle,
	val lookupTree: LookupTree<A_Definition, A_Tuple>,
	val dynamicLookupStats: LookupStatistics,
	val arguments: ReadArray,
) : L2Simple_AbstractInvokerInstruction(
	nextOffset,
	reentryOffset,
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
		args.run {
			clear()
			arguments.forEachIndexed { _, read ->
				add(registers[read])
			}
		}
		val definition: A_Definition
		try
		{
			definition = extractUniqueMethod(
				runtimeDispatcher.lookupByValues(
					lookupTree,
					args,
					Unit,
					dynamicLookupStats,
					interpreter.function?.code()?.lookupStat))
			when
			{
				definition.isAbstractDefinition() -> throw abstractMethod()
				definition.isForwardDefinition() -> throw forwardMethod()
			}
		}
		catch (e: MethodDefinitionException)
		{
			return handleFailedLookup(interpreter, args, e, registers, bundle)
		}
		// Lookup was successful.  Invoke it, with the arguments that are still
		// in the interpreter's argsBuffer.
		return invocationHelper(interpreter, registers, definition.bodyBlock())
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_GeneralCall(
			nextOffset = target(nextOffset),
			reentryOffset = target(reentryOffset),
			stateOfL1 = state(stateOfL1),
			expectedType = expectedType,
			mustCheck = mustCheck,
			answer = write(answer),
			bundle = bundle,
			lookupTree = lookupTree,
			dynamicLookupStats = dynamicLookupStats,
			arguments = read(arguments))
}
